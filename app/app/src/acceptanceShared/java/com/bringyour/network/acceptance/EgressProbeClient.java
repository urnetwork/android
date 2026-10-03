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
    // query plus this cleanup bound still returns a result or a cleanup failure
    // before the caller's deadline.
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
                if (openedConnection != null) {
                    synchronized (stateLock) {
                        if (connection == openedConnection) {
                            connection = null;
                        }
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
                if (frozenDiagnostic == null) {
                    phase = next;
                    phaseStartNanos = System.nanoTime();
                }
            }
        }

        private void progress() {
            synchronized (stateLock) {
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
                    progress();
                }
            }
        }

        private void bodyReceived(int bytes) {
            synchronized (stateLock) {
                if (frozenDiagnostic == null) {
                    acceptedBytes = Math.max(0, Math.min(MAX_RESPONSE_BYTES, bytes));
                    progress();
                }
            }
        }

        // The first failure/deadline owns this immutable snapshot. Subsequent
        // cancellation, late publication and disconnect cannot relabel it.
        String freezeDiagnostic() {
            synchronized (stateLock) {
                if (frozenDiagnostic == null) {
                    long now = System.nanoTime();
                    frozenDiagnostic = phase + "," + elapsedMillis(diagnosticStartNanos, now)
                        + "," + elapsedMillis(phaseStartNanos, now)
                        + "," + (progressed ? elapsedMillis(diagnosticStartNanos, progressNanos) : -1)
                        + "," + httpStatus + "," + acceptedBytes;
                }
                return frozenDiagnostic;
            }
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
        return queryPublicIp(ENDPOINTS, endpoint ->
            (HttpURLConnection) new URL(endpoint).openConnection()
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
        Exception queryFailure = null;
        Throwable lastEndpointFailure = null;
        long queryDeadlineNanos = System.nanoTime()
            + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
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
                if (allFailed) {
                    queryFailure = new IOException(
                        "all egress endpoints failed: " + details + diagnostic,
                        lastEndpointFailure
                    );
                } else {
                    queryFailure = new IOException(
                        "egress query deadline exceeded after " + timeoutMillis + "ms: "
                            + details + diagnostic,
                        new TimeoutException("egress query deadline exceeded")
                    );
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            queryFailure = new IOException("egress query interrupted" + formatPhaseDiagnostics(attempts), error);
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
                "egress probe workers did not stop after cancellation" + formatPhaseDiagnostics(attempts)
            );
            if (queryFailure != null) {
                cleanupFailure.addSuppressed(queryFailure);
            }
            throw cleanupFailure;
        }
        if (queryFailure != null) {
            throw queryFailure;
        }
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
