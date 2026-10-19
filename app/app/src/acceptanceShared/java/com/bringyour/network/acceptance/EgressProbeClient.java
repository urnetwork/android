package com.bringyour.network.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Resolves public egress through independent endpoints. A single external DNS
 * timeout cannot decide the acceptance result, while failure of every endpoint
 * still proves the path unusable.
 */
final class EgressProbeClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final int QUERY_TIMEOUT_MILLIS = 20_000;
    // A losing HttpURLConnection is allowed its remaining platform I/O timeout
    // after disconnect. The outer Binder request is 45 seconds, so a 20-second
    // query plus this cleanup allowance fits the caller's deadline. The opt-in
    // stack diagnostic below has no hard VM time/allocation bound and is never
    // part of a qualifying measurement; its enclosing owner remains a backstop.
    private static final int WORKER_SHUTDOWN_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_RESPONSE_BYTES = 256;
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9a-fA-F:]+");
    private static final String[] ENDPOINTS = {
        "https://checkip.amazonaws.com/",
        "https://api.ipify.org/",
    };

    // The bulk endpoint PERF's physical runner already fetches through tunnels.
    private static final String TRAFFIC_ENDPOINT = "https://speed.cloudflare.com/__down?bytes=";
    static final long MAX_TRAFFIC_BYTES = 8L * 1024 * 1024;
    // Below the probe's 40 s bound so the activity always answers the 45 s
    // Binder request, even when the tunnel holds the transfer.
    static final long TRAFFIC_DURATION_MILLIS = 30_000;

    interface ConnectionFactory {
        HttpURLConnection open(String endpoint) throws Exception;
    }

    interface WorkerStackCapture {
        StackTraceElement[] capture(Thread worker);
    }

    interface QuerySetupBarrier {
        void await() throws InterruptedException;
    }

    private static final int MAX_STACK_WORKERS = 2;
    private static final int MAX_STACK_FRAMES_EXAMINED = 32;
    private static final int MAX_STACK_FRAMES_RETAINED = 8;
    // One-based codes, not inferred causes. Only these exact class/method pairs
    // may be retained; no arbitrary names, source paths, line numbers or values.
    // A missing code is unknown, not evidence that a platform operation ended.
    private static final String[][] STACK_FRAME_CODES = {
        {"libcore.io.Linux", "android_getaddrinfo"},
        {"java.net.Inet6AddressImpl", "lookupHostByName"},
        {"java.net.InetAddress", "getAllByName"},
        {"libcore.io.Linux", "connect"},
        {"libcore.io.IoBridge", "connect"},
        {"java.net.PlainSocketImpl", "socketConnect"},
        {"com.android.org.conscrypt.NativeCrypto", "SSL_do_handshake"},
        {"com.android.org.conscrypt.ConscryptFileDescriptorSocket", "startHandshake"},
        {"com.android.org.conscrypt.OpenSSLSocketImpl", "startHandshake"},
        {"com.android.org.conscrypt.NativeCrypto", "SSL_read"},
        {"com.android.org.conscrypt.ConscryptFileDescriptorSocket$SSLInputStream", "read"},
        {"com.android.org.conscrypt.OpenSSLSocketImpl$SSLInputStream", "read"},
        {"java.net.SocketInputStream", "socketRead0"},
        {"libcore.io.Linux", "read"},
        {"com.android.okhttp.internal.http.Http1xStream", "readResponse"},
        {"com.android.okhttp.internal.http.Http1xStream", "readResponseHeaders"},
        {"com.android.okhttp.internal.io.RealConnection", "connect"},
        {"com.android.okhttp.internal.io.RealConnection", "connectTls"},
        {"com.android.okhttp.internal.http.HttpEngine", "readNetworkResponse"},
        {"com.android.okhttp.internal.huc.HttpURLConnectionImpl", "getResponse"},
        {"com.android.okhttp.internal.huc.HttpURLConnectionImpl", "getResponseCode"},
        {"com.android.okhttp.internal.huc.DelegatingHttpsURLConnection", "getResponseCode"},
        {"com.android.okhttp.okio.RealBufferedSource", "readUtf8LineStrict"},
    };

    /** Owns one endpoint connection across cancellation and late publication. */
    static final class Attempt implements Callable<String> {
        // Diagnostic-only numeric phases. RESPONSE_WAIT deliberately combines
        // platform DNS, connect, TLS and response headers; no extra I/O probes.
        private static final int OPEN = 1;
        private static final int RESPONSE_WAIT = 2;
        private static final int BODY_OPEN = 3;
        private static final int BODY_READ = 4;
        private static final int BODY_CLOSE = 5;
        private static final int VALIDATE = 6;
        private static final int DISCONNECT = 7;
        private static final int COMPLETE = 8;

        private final String endpoint;
        private final ConnectionFactory connectionFactory;
        private final Map<String, String> failures;
        private final Object stateLock = new Object();

        private boolean canceled;
        private HttpURLConnection connection;
        private final long diagnosticStartNanos = System.nanoTime();
        private int phase;
        private long phaseStartNanos = diagnosticStartNanos;
        private long progressNanos;
        private boolean progressed;
        private int httpStatus = -1;
        private int acceptedBytes;
        private String frozenDiagnostic;
        private Thread worker;
        // Unlike the frozen failure record, these track late progress. A stack
        // from a different boundary or a completed owner must not be attributed
        // to the original deadline, even when the worker finishes during capture.
        private long boundaryGeneration;
        private long frozenBoundaryGeneration;
        private int livePhase;

        Attempt(
            String endpoint,
            ConnectionFactory connectionFactory,
            Map<String, String> failures
        ) {
            this.endpoint = endpoint;
            this.connectionFactory = connectionFactory;
            this.failures = failures;
        }

        @Override
        public String call() throws Exception {
            HttpURLConnection openedConnection = null;
            try {
                synchronized (stateLock) {
                    if (canceled) {
                        throw new IOException("egress endpoint attempt canceled before open");
                    }
                    worker = Thread.currentThread();
                    boundaryGeneration += 1;
                }

                phase(OPEN);
                openedConnection = connectionFactory.open(endpoint);
                progress();
                boolean cancelOpenedConnection;
                synchronized (stateLock) {
                    cancelOpenedConnection = canceled;
                    if (!cancelOpenedConnection) {
                        connection = openedConnection;
                    }
                }
                if (cancelOpenedConnection) {
                    openedConnection.disconnect();
                    throw new IOException("egress endpoint attempt canceled during open");
                }
                String address = queryEndpoint(openedConnection, this);
                phase(COMPLETE);
                progress();
                return address;
            } catch (Exception error) {
                freezeDiagnostic();
                failures.put(endpoint, failureDetail(error));
                throw error;
            } finally {
                synchronized (stateLock) {
                    worker = null;
                    boundaryGeneration += 1;
                    if (connection == openedConnection) {
                        connection = null;
                    }
                }
            }
        }

        void cancel() {
            HttpURLConnection activeConnection;
            synchronized (stateLock) {
                canceled = true;
                activeConnection = connection;
            }
            if (activeConnection != null) {
                try {
                    activeConnection.disconnect();
                } catch (RuntimeException error) {
                    failures.putIfAbsent(endpoint, failureDetail(error));
                }
            }
        }

        void checkCanceled() throws IOException {
            synchronized (stateLock) {
                if (canceled) {
                    throw new IOException("egress endpoint attempt canceled");
                }
            }
        }

        private void phase(int next) {
            synchronized (stateLock) {
                boundaryGeneration += 1;
                livePhase = next;
                if (frozenDiagnostic == null) {
                    phase = next;
                    phaseStartNanos = System.nanoTime();
                }
            }
        }

        private void progress() {
            synchronized (stateLock) {
                boundaryGeneration += 1;
                if (frozenDiagnostic == null) {
                    progressed = true;
                    progressNanos = System.nanoTime();
                }
            }
        }

        private void responseReceived(int status) {
            synchronized (stateLock) {
                if (frozenDiagnostic == null) {
                    httpStatus = 100 <= status && status <= 599 ? status : -1;
                }
                progress();
            }
        }

        private void bodyReceived(int bytes) {
            synchronized (stateLock) {
                if (frozenDiagnostic == null) {
                    acceptedBytes = Math.max(0, Math.min(MAX_RESPONSE_BYTES, bytes));
                }
                progress();
            }
        }

        // The first failure/deadline owns this immutable snapshot. Subsequent
        // cancellation, late publication and disconnect cannot relabel it.
        String freezeDiagnostic() {
            synchronized (stateLock) {
                if (frozenDiagnostic == null) {
                    frozenBoundaryGeneration = boundaryGeneration;
                    long now = System.nanoTime();
                    frozenDiagnostic = phase + "," + elapsedMillis(diagnosticStartNanos, now)
                        + "," + elapsedMillis(phaseStartNanos, now)
                        + "," + (progressed ? elapsedMillis(diagnosticStartNanos, progressNanos) : -1)
                        + "," + httpStatus + "," + acceptedBytes;
                }
                return frozenDiagnostic;
            }
        }

        // Called only after the query's original verdict and all phase records
        // are frozen, and only in an explicitly unqualified diagnostic session.
        // Never hold stateLock while asking the VM for the exact owned worker.
        // The private executor cannot run any other endpoint task on this thread.
        String captureWorkerStack(WorkerStackCapture capture) {
            Thread target;
            long generation;
            int before;
            synchronized (stateLock) {
                target = worker;
                generation = boundaryGeneration;
                before = livePhase;
                if (target == null) {
                    return stackRow(0, before, before, 0, 0, 0, 0, "0");
                }
                if (frozenDiagnostic == null || generation != frozenBoundaryGeneration) {
                    return stackRow(2, before, before, 0, 0, 0, 0, "0");
                }
            }

            long started = System.nanoTime();
            StackTraceElement[] frames = null;
            boolean failed = false;
            try {
                frames = capture.capture(target);
            } catch (RuntimeException error) {
                // Security/VM access refusal is diagnostic unavailability. Do
                // not copy the exception class/message or replace the verdict.
                failed = true;
            }
            long finished = System.nanoTime();
            int after;
            boolean changed;
            synchronized (stateLock) {
                after = livePhase;
                changed = worker != target || boundaryGeneration != generation;
            }
            long startMillis = elapsedMillis(diagnosticStartNanos, started);
            long durationNanos = Math.max(0, finished - started);
            if (changed || failed || frames == null || frames.length == 0) {
                int state = changed ? 2 : failed ? 4 : 3;
                return stackRow(state, before, after, startMillis, durationNanos, 0, 0, "0");
            }
            int examined = Math.min(frames.length, MAX_STACK_FRAMES_EXAMINED);
            int retained = 0;
            StringBuilder codes = new StringBuilder();
            for (int i = 0; i < examined; i += 1) {
                int code = stackFrameCode(frames[i]);
                if (code != 0 && retained < MAX_STACK_FRAMES_RETAINED) {
                    if (retained != 0) codes.append('.');
                    codes.append(code);
                    retained += 1;
                }
            }
            return stackRow(1, before, after, startMillis, durationNanos, examined,
                frames.length, retained == 0 ? "0" : codes.toString());
        }
    }

    private EgressProbeClient() {
    }

    static long defaultMaximumDurationMillis() {
        return (long) QUERY_TIMEOUT_MILLIS + WORKER_SHUTDOWN_TIMEOUT_MILLIS;
    }

    static long maximumBlockingPhaseDurationMillis() {
        return Math.max(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    static long defaultWorkerShutdownTimeoutMillis() {
        return WORKER_SHUTDOWN_TIMEOUT_MILLIS;
    }

    static String queryPublicIp() throws Exception {
        return queryPublicIp(false);
    }

    static String queryPublicIp(boolean diagnosticWorkerStacks) throws Exception {
        return queryPublicIp(ENDPOINTS,
            endpoint -> (HttpURLConnection) new URL(endpoint).openConnection(),
            QUERY_TIMEOUT_MILLIS, WORKER_SHUTDOWN_TIMEOUT_MILLIS,
            diagnosticWorkerStacks, Thread::getStackTrace
        );
    }

    static String queryPublicIp(String[] endpoints, ConnectionFactory connectionFactory) throws Exception {
        return queryPublicIp(endpoints, connectionFactory, QUERY_TIMEOUT_MILLIS);
    }

    static String queryPublicIp(
        String[] endpoints,
        ConnectionFactory connectionFactory,
        long timeoutMillis
    ) throws Exception {
        return queryPublicIp(
            endpoints,
            connectionFactory,
            timeoutMillis,
            WORKER_SHUTDOWN_TIMEOUT_MILLIS
        );
    }

    static String queryPublicIp(
        String[] endpoints,
        ConnectionFactory connectionFactory,
        long timeoutMillis,
        long workerShutdownTimeoutMillis
    ) throws Exception {
        return queryPublicIp(endpoints, connectionFactory, timeoutMillis,
            workerShutdownTimeoutMillis, false, Thread::getStackTrace);
    }

    static String queryPublicIp(
        String[] endpoints,
        ConnectionFactory connectionFactory,
        long timeoutMillis,
        long workerShutdownTimeoutMillis,
        boolean diagnosticWorkerStacks,
        WorkerStackCapture stackCapture
    ) throws Exception {
        return queryPublicIp(endpoints, connectionFactory, timeoutMillis,
            workerShutdownTimeoutMillis, diagnosticWorkerStacks, stackCapture, () -> {});
    }

    // Package-private fake-I/O seam: tests establish blocking worker boundaries
    // before the query clock starts. Real requests use only the no-op overload.
    static String queryPublicIp(
        String[] endpoints,
        ConnectionFactory connectionFactory,
        long timeoutMillis,
        long workerShutdownTimeoutMillis,
        boolean diagnosticWorkerStacks,
        WorkerStackCapture stackCapture,
        QuerySetupBarrier setupBarrier
    ) throws Exception {
        if (endpoints.length == 0) {
            throw new IllegalArgumentException("at least one egress endpoint is required");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("egress query timeout must be positive");
        }
        if (workerShutdownTimeoutMillis <= 0) {
            throw new IllegalArgumentException("egress worker shutdown timeout must be positive");
        }

        Map<String, String> failures = new ConcurrentHashMap<>();
        List<Attempt> attempts = new ArrayList<>();
        for (String endpoint : endpoints) {
            attempts.add(new Attempt(endpoint, connectionFactory, failures));
        }

        AtomicInteger threadIndex = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(
                runnable,
                "acceptance-egress-endpoint-" + threadIndex.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService executor = Executors.newFixedThreadPool(endpoints.length, threadFactory);
        CompletionService<String> completions = new ExecutorCompletionService<>(executor);
        List<Future<String>> futures = new ArrayList<>();
        for (Attempt attempt : attempts) {
            futures.add(completions.submit(attempt));
        }

        String address = null;
        Throwable queryFailure = null;
        String stackDiagnostic = "";
        Throwable lastEndpointFailure = null;
        try {
            setupBarrier.await();
            long queryDeadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            int remainingAttempts = attempts.size();
            while (0 < remainingAttempts && address == null) {
                long remainingNanos = queryDeadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }
                Future<String> completed = completions.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (completed == null) {
                    break;
                }
                remainingAttempts -= 1;
                try {
                    address = completed.get();
                } catch (ExecutionException error) {
                    lastEndpointFailure = error.getCause();
                } catch (CancellationException error) {
                    lastEndpointFailure = error;
                }
            }
            if (address == null) {
                // Preserve the original outcome/text decision before doing
                // diagnostic work; later worker activity cannot reclassify it.
                boolean allFailed = failures.size() == endpoints.length;
                String details = formatFailures(endpoints, failures);
                String diagnostic = formatPhaseDiagnostics(attempts);
                if (diagnosticWorkerStacks) {
                    stackDiagnostic = formatWorkerStackDiagnostics(attempts, stackCapture);
                }
                if (allFailed) {
                    queryFailure = new IOException(
                        "all egress endpoints failed: " + details + stackDiagnostic + diagnostic,
                        lastEndpointFailure
                    );
                } else {
                    queryFailure = new IOException(
                        "egress query deadline exceeded after " + timeoutMillis + "ms: "
                            + details + stackDiagnostic + diagnostic,
                        new TimeoutException("egress query deadline exceeded")
                    );
                }
            }
        } catch (InterruptedException error) {
            // Owner cancellation takes priority over this optional diagnostic.
            Thread.currentThread().interrupt();
            queryFailure = new IOException("egress query interrupted" + formatPhaseDiagnostics(attempts), error);
        } catch (RuntimeException | Error error) {
            // A broken test barrier or unexpected VM failure must not skip the
            // same connection cancellation and executor join. Preserve it as-is
            // after cleanup; fatal VM failures are not diagnostic availability.
            queryFailure = error;
        }

        // Even a winning query may later fail to join a loser. Capture its
        // decision boundary before cancellation performs any disconnects.
        for (Attempt attempt : attempts) {
            attempt.freezeDiagnostic();
        }
        // Mark every owner canceled before interrupting its Future. A connection
        // published after this point observes canceled under the same lock and
        // disconnects itself instead of escaping both cleanup snapshots.
        for (Attempt attempt : attempts) {
            attempt.cancel();
        }
        for (Future<String> future : futures) {
            future.cancel(true);
        }
        executor.shutdownNow();
        boolean stopped = awaitTerminationRestoringInterrupt(
            executor,
            workerShutdownTimeoutMillis
        );
        for (Attempt attempt : attempts) {
            attempt.cancel();
        }
        if (!stopped) {
            IOException cleanupFailure = new IOException(
                "egress probe workers did not stop after cancellation" + stackDiagnostic
                    + formatPhaseDiagnostics(attempts)
            );
            if (queryFailure != null) {
                cleanupFailure.addSuppressed(queryFailure);
            }
            throw cleanupFailure;
        }
        if (queryFailure instanceof Error) throw (Error) queryFailure;
        if (queryFailure != null) throw (Exception) queryFailure;
        return address;
    }

    /** Waits through caller interruption, then restores its interrupt status. */
    private static boolean awaitTerminationRestoringInterrupt(
        ExecutorService executor,
        long timeoutMillis
    ) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        boolean interrupted = false;
        try {
            while (true) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    return executor.isTerminated();
                }
                try {
                    return executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
                } catch (InterruptedException error) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Bounded bulk download through the system path, so already-open contracts
     * are used up and the client must ask for a new one. It reads at most
     * byteCount bytes and stops at the duration bound with whatever arrived;
     * the result is the byte count, never the body.
     */
    static long downloadTraffic(long byteCount) throws Exception {
        return downloadTraffic(
            byteCount,
            endpoint -> (HttpURLConnection) new URL(endpoint).openConnection(),
            System::nanoTime,
            TRAFFIC_DURATION_MILLIS
        );
    }

    interface NanoClock {
        long nanoTime();
    }

    static long downloadTraffic(
        long byteCount,
        ConnectionFactory connectionFactory,
        NanoClock clock,
        long durationMillis
    ) throws Exception {
        if (byteCount <= 0 || MAX_TRAFFIC_BYTES < byteCount) {
            throw new IllegalArgumentException("traffic byte count must be in (0, " + MAX_TRAFFIC_BYTES + "]");
        }
        long start = clock.nanoTime();
        HttpURLConnection connection = connectionFactory.open(TRAFFIC_ENDPOINT + byteCount);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + statusCode);
            }
            long total = 0;
            byte[] buffer = new byte[16 * 1024];
            try (InputStream input = connection.getInputStream()) {
                while (elapsedMillis(start, clock.nanoTime()) < durationMillis) {
                    int count = input.read(buffer, 0, (int) Math.min(buffer.length, byteCount - total + 1));
                    if (count == -1) {
                        break;
                    }
                    total += count;
                    if (byteCount < total) {
                        throw new IllegalStateException("traffic response exceeds " + byteCount + " bytes");
                    }
                }
            }
            return total;
        } finally {
            connection.disconnect();
        }
    }

    private static String queryEndpoint(HttpURLConnection connection, Attempt attempt) throws Exception {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        try {
            attempt.checkCanceled();
            attempt.phase(Attempt.RESPONSE_WAIT);
            int statusCode = connection.getResponseCode();
            attempt.responseReceived(statusCode);
            // Cancellation may have landed while connecting or reading the
            // response headers. Never enter the next blocking phase after it.
            attempt.checkCanceled();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + statusCode);
            }
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            attempt.phase(Attempt.BODY_OPEN);
            try (InputStream input = connection.getInputStream()) {
                attempt.progress();
                try {
                    byte[] buffer = new byte[64];
                    int count;
                    while (true) {
                        attempt.checkCanceled();
                        attempt.phase(Attempt.BODY_READ);
                        count = input.read(buffer);
                        attempt.checkCanceled();
                        if (count == -1) {
                            attempt.bodyReceived(response.size());
                            break;
                        }
                        if (MAX_RESPONSE_BYTES < response.size() + count) {
                            attempt.progress();
                            throw new IllegalStateException(
                                "address response exceeds " + MAX_RESPONSE_BYTES + " bytes"
                            );
                        }
                        response.write(buffer, 0, count);
                        attempt.bodyReceived(response.size());
                    }
                } catch (Exception error) {
                    attempt.freezeDiagnostic();
                    throw error;
                } finally {
                    attempt.phase(Attempt.BODY_CLOSE);
                }
            }
            attempt.progress();
            attempt.phase(Attempt.VALIDATE);
            String address = new String(response.toByteArray(), StandardCharsets.UTF_8).trim();
            if (!isIpAddress(address)) {
                throw new IllegalStateException("invalid address response");
            }
            return address;
        } catch (Exception error) {
            attempt.freezeDiagnostic();
            throw error;
        } finally {
            attempt.phase(Attempt.DISCONNECT);
            try {
                connection.disconnect();
            } catch (RuntimeException error) {
                attempt.freezeDiagnostic();
                throw error;
            }
        }
    }

    private static long elapsedMillis(long start, long end) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(end - start));
    }

    // This is deliberately not a hard time/allocation bound: getStackTrace may
    // allocate/suspend inside the VM before returning. Only the call count,
    // frames examined, codes retained and formatted output are bounded here.
    // Default-off; the existing diagnostic owner is explicitly non-qualifying.
    private static String formatWorkerStackDiagnostics(
        List<Attempt> attempts,
        WorkerStackCapture capture
    ) {
        StringBuilder result = new StringBuilder("; probe_stack_v1=");
        int count = Math.min(attempts.size(), MAX_STACK_WORKERS);
        for (int i = 0; i < count; i += 1) {
            if (i != 0) result.append('|');
            result.append(i + 1).append(',').append(attempts.get(i).captureWorkerStack(capture));
        }
        return result.toString();
    }

    // Row after endpoint ordinal: state, phase-before, phase-after,
    // capture-start ms from attempt creation, API duration ns, frames examined,
    // returned depth, dot-separated allowlist codes (0 = no recognized frame).
    // States: 0 no running owner; 1 stable observed boundary; 2 boundary changed
    // or already late; 3 empty/unavailable; 4 capture refused. Unknown is not a
    // DNS/connect/TLS/header verdict, and a stable boundary is not a frozen VM.
    private static String stackRow(
        int state, int before, int after, long startMillis, long durationNanos,
        int examined, int depth, String codes
    ) {
        return state + "," + before + "," + after + "," + startMillis + "," + durationNanos
            + "," + examined + "," + depth + "," + codes;
    }

    private static int stackFrameCode(StackTraceElement frame) {
        if (frame == null) return 0;
        for (int i = 0; i < STACK_FRAME_CODES.length; i += 1) {
            if (STACK_FRAME_CODES[i][0].equals(frame.getClassName())
                && STACK_FRAME_CODES[i][1].equals(frame.getMethodName())) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String formatPhaseDiagnostics(List<Attempt> attempts) {
        // Rows: endpoint ordinal, phase, elapsed ms, phase elapsed ms,
        // last completed-boundary ms (-1 unknown), HTTP status (-1 unknown),
        // accepted body bytes. Only fixed labels and bounded numeric state.
        StringBuilder result = new StringBuilder("; probe_phase_v1=");
        for (int i = 0; i < attempts.size(); i += 1) {
            if (i != 0) {
                result.append('|');
            }
            result.append(i + 1).append(',').append(attempts.get(i).freezeDiagnostic());
        }
        return result.toString();
    }

    private static String failureDetail(Exception error) {
        String detail = error.getMessage();
        if (detail == null) {
            detail = "unknown";
        }
        return error.getClass().getSimpleName() + ":" + detail;
    }

    private static String formatFailures(String[] endpoints, Map<String, String> failures) {
        List<String> details = new ArrayList<>();
        for (String endpoint : endpoints) {
            details.add(endpoint + "=" + failures.getOrDefault(endpoint, "unfinished"));
        }
        return String.join("; ", details);
    }

    private static boolean isIpAddress(String address) {
        if (address.indexOf(':') >= 0) {
            if (!IPV6_CHARACTERS.matcher(address).matches()) {
                return false;
            }
            try {
                InetAddress parsed = InetAddress.getByName(address);
                return parsed instanceof Inet6Address;
            } catch (Exception error) {
                return false;
            }
        }

        String[] components = address.split("\\.", -1);
        if (components.length != 4) {
            return false;
        }
        for (String component : components) {
            if (component.isEmpty() || component.length() > 3) {
                return false;
            }
            int value = 0;
            for (int index = 0; index < component.length(); index += 1) {
                char character = component.charAt(index);
                if (character < '0' || '9' < character) {
                    return false;
                }
                value = value * 10 + character - '0';
            }
            if (255 < value) {
                return false;
            }
        }
        return true;
    }
}
