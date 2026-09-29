package com.bringyour.network.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class EgressProbeClientTest {
    @Test(timeout = 5_000)
    public void deadlineRetainsNumericResponseWaitBeforeCancellation() throws Exception {
        String[] endpoints = {"https://first.invalid/", "https://second.invalid/"};
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(2);
        IOException error = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(endpoints, endpoint ->
                blockingConnection(endpoint, entered, release, exited, () -> { }), 1_000)
        );
        assertEquals("both attempts must have reached response I/O", 0, entered.getCount());
        assertTrue("all canceled workers must join", exited.await(1, TimeUnit.SECONDS));
        assertTrue(error.getMessage().startsWith("egress query deadline exceeded after 1000ms: "));
        String marker = "; probe_phase_v1=";
        int markerAt = error.getMessage().indexOf(marker);
        assertTrue("missing finite request-phase evidence", markerAt >= 0);
        String suffix = error.getMessage().substring(markerAt + marker.length());
        assertTrue("response_wait must stay unknown-status/zero-bytes at the deadline", suffix.matches(
            "1,2,[0-9]+,[0-9]+,[0-9]+,-1,0\\|2,2,[0-9]+,[0-9]+,[0-9]+,-1,0"));
        assertTrue("original deadline cause must survive", error.getCause() instanceof java.util.concurrent.TimeoutException);
    }

    @Test(timeout = 5_000)
    public void everyBlockingBoundaryRetainsItsPhaseBeforeCancelAndLateCompletion() throws Exception {
        // OPEN, combined RESPONSE_WAIT, BODY_OPEN, BODY_READ, BODY_CLOSE,
        // DISCONNECT. No additional connect(), DNS or TLS calls are made.
        for (int phase : new int[] {1, 2, 3, 4, 5, 7}) {
            try (BoundaryAttempt fixture = new BoundaryAttempt(phase)) {
                fixture.start();
                assertTrue("missing boundary " + phase, fixture.entered.await(1, TimeUnit.SECONDS));
                String frozen = fixture.attempt.freezeDiagnostic();
                long[] row = diagnosticRow("1," + frozen);
                assertEquals(phase, row[1]);
                assertEquals(phase <= 2 ? -1 : 200, row[5]);
                assertEquals(phase <= 3 ? 0 : phase == 4 ? 4 : fixture.body.length, row[6]);
                if (phase == 1) {
                    assertEquals("unreturned factory is not I/O progress", -1, row[4]);
                }
                fixture.attempt.cancel();
                fixture.release.countDown();
                fixture.join();
                assertEquals("late completion rewrote phase " + phase, frozen,
                    fixture.attempt.freezeDiagnostic());
                assertTrue("owned connection not disconnected", fixture.disconnects.get() > 0);
                assertEquals("opening after cancel must not start I/O", phase == 1 ? 0 : 1,
                    fixture.responseCalls.get());
            }
        }
    }

    @Test
    public void notStartedAndCompletedAreFiniteDistinctStates() throws Exception {
        EgressProbeClient.Attempt unopened = new EgressProbeClient.Attempt(
            "https://unused.invalid/", endpoint -> { throw new AssertionError("unexpected open"); },
            new java.util.concurrent.ConcurrentHashMap<>());
        long[] empty = diagnosticRow("1," + unopened.freezeDiagnostic());
        assertEquals(0, empty[1]);
        assertEquals(-1, empty[4]);
        assertEquals(-1, empty[5]);
        assertEquals(0, empty[6]);

        EgressProbeClient.Attempt complete = new EgressProbeClient.Attempt(
            "https://complete.invalid/", endpoint -> responseConnection(endpoint, 200, "203.0.113.9\n"),
            new java.util.concurrent.ConcurrentHashMap<>());
        assertEquals("203.0.113.9", complete.call());
        long[] done = diagnosticRow("1," + complete.freezeDiagnostic());
        assertEquals(8, done[1]);
        assertEquals(200, done[5]);
        assertEquals(12, done[6]);
    }

    @Test
    public void originalEndpointFailureFreezesBeforeDisconnectAndKeepsCause() throws Exception {
        IOException original = new IOException("synthetic private failure text");
        AtomicReference<EgressProbeClient.Attempt> owner = new AtomicReference<>();
        AtomicReference<String> duringDisconnect = new AtomicReference<>();
        EgressProbeClient.Attempt attempt = new EgressProbeClient.Attempt(
            "https://failure.invalid/", endpoint -> new HttpURLConnection(new URL(endpoint)) {
                @Override public int getResponseCode() throws IOException { throw original; }
                @Override public void disconnect() {
                    duringDisconnect.set(owner.get().freezeDiagnostic());
                }
                @Override public boolean usingProxy() { return false; }
                @Override public void connect() { throw new AssertionError("extra connect"); }
            }, new java.util.concurrent.ConcurrentHashMap<>());
        owner.set(attempt);
        IOException actual = assertThrows(IOException.class, attempt::call);
        assertTrue("original exception was replaced", actual == original);
        long[] row = diagnosticRow("1," + duringDisconnect.get());
        assertEquals("failure must retain response_wait rather than cleanup", 2, row[1]);
        assertEquals(-1, row[5]);
        assertEquals(duringDisconnect.get(), attempt.freezeDiagnostic());

        IOException combined = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(new String[] {"https://failed-open.invalid/"}, endpoint -> {
                throw original;
            }));
        assertTrue(combined.getMessage().startsWith("all egress endpoints failed: "));
        assertTrue("last endpoint cause changed", combined.getCause() == original);
        assertEquals(1, diagnosticRows(combined)[0][1]);
    }

    @Test
    public void statusValidationAndByteCapDoNotExposeEndpointBodyOrErrorText() {
        String endpoint = "https://private.invalid/path?synthetic=secret";
        String privateBody = "synthetic-response-not-an-address";
        IOException invalid = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(new String[] {endpoint}, value ->
                responseConnection(value, 200, privateBody)));
        long[] invalidRow = diagnosticRows(invalid)[0];
        assertEquals(6, invalidRow[1]);
        assertEquals(200, invalidRow[5]);
        assertEquals(privateBody.length(), invalidRow[6]);
        String suffix = diagnosticSuffix(invalid);
        assertFalse(suffix.contains(endpoint));
        assertFalse(suffix.contains(privateBody));
        assertFalse(suffix.contains("invalid address response"));

        for (int statusCode : new int[] {404, -1, 700}) {
            IOException rejected = assertThrows(IOException.class, () ->
                EgressProbeClient.queryPublicIp(new String[] {endpoint}, value ->
                    responseConnection(value, statusCode, privateBody)));
            long[] row = diagnosticRows(rejected)[0];
            assertEquals(2, row[1]);
            assertEquals(statusCode == 404 ? 404 : -1, row[5]);
            assertEquals(0, row[6]);
        }

        IOException oversized = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(new String[] {endpoint}, value ->
                responseConnection(value, 200, "x".repeat(257))));
        long[] capped = diagnosticRows(oversized)[0];
        assertEquals(4, capped[1]);
        assertEquals(256, capped[6]);
        assertTrue(oversized.getMessage().contains("response exceeds 256 bytes"));
    }

    @Test(timeout = 5_000)
    public void cancellationReturningLateHeadersCannotRewriteDeadlineSnapshot() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean disconnected = new AtomicBoolean();
        AtomicBoolean bodyEntered = new AtomicBoolean();
        IOException error = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(new String[] {"https://late-headers.invalid/"}, endpoint ->
                slowCancellationConnection(endpoint, entered, exited, disconnected, bodyEntered, 0), 1_000));
        assertEquals(0, entered.getCount());
        assertTrue(exited.await(1, TimeUnit.SECONDS));
        assertTrue(disconnected.get());
        assertFalse("cancelled response entered a new blocking body phase", bodyEntered.get());
        long[] row = diagnosticRows(error)[0];
        assertEquals(2, row[1]);
        assertEquals("late 200 must not overwrite unknown at deadline", -1, row[5]);
        assertEquals(0, row[6]);
    }

    @Test
    public void bodyCloseFailureRetainsClosePhaseAndOriginalCause() {
        IOException original = new IOException("synthetic stream close failure");
        IOException error = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(new String[] {"https://close.invalid/"}, endpoint ->
                new HttpURLConnection(new URL(endpoint)) {
                    @Override public int getResponseCode() { return 200; }
                    @Override public InputStream getInputStream() {
                        return new ByteArrayInputStream("203.0.113.9\n".getBytes(StandardCharsets.UTF_8)) {
                            @Override public void close() throws IOException { throw original; }
                        };
                    }
                    @Override public void disconnect() { }
                    @Override public boolean usingProxy() { return false; }
                    @Override public void connect() { throw new AssertionError("extra connect"); }
                }));
        assertTrue(error.getCause() == original);
        long[] row = diagnosticRows(error)[0];
        assertEquals(5, row[1]);
        assertEquals(200, row[5]);
        assertEquals(12, row[6]);
    }

    @Test(timeout = 5_000)
    public void lateFactoryFailureCannotRewriteFrozenOpeningState() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IOException original = new IOException("synthetic late private error");
        AtomicReference<Throwable> actual = new AtomicReference<>();
        EgressProbeClient.Attempt attempt = new EgressProbeClient.Attempt(
            "https://late-failure.invalid/", endpoint -> {
                entered.countDown();
                release.await();
                throw original;
            }, new java.util.concurrent.ConcurrentHashMap<>());
        Thread worker = new Thread(() -> {
            try { attempt.call(); } catch (Throwable error) { actual.set(error); }
        }, "acceptance-egress-test-late-failure");
        worker.setDaemon(true);
        worker.start();
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            String frozen = attempt.freezeDiagnostic();
            attempt.cancel();
            release.countDown();
            worker.join(1_000);
            assertFalse(worker.isAlive());
            assertTrue(actual.get() == original);
            assertEquals(frozen, attempt.freezeDiagnostic());
            assertEquals(1, diagnosticRow("1," + frozen)[1]);
        } finally {
            release.countDown();
            worker.interrupt();
            worker.join(1_000);
            assertFalse("late factory worker not joined", worker.isAlive());
        }
    }

    @Test(timeout = 5_000)
    public void snapshotRaceHasOneWinnerAndCannotMixBeforeAndAfterStates() throws Exception {
        for (int iteration = 0; iteration < 20; iteration += 1) {
            try (BoundaryAttempt fixture = new BoundaryAttempt(2)) {
                fixture.start();
                assertTrue(fixture.entered.await(1, TimeUnit.SECONDS));
                CountDownLatch start = new CountDownLatch(1);
                AtomicReference<String> concurrent = new AtomicReference<>();
                Thread reader = new Thread(() -> {
                    try {
                        start.await();
                        concurrent.set(fixture.attempt.freezeDiagnostic());
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }, "acceptance-egress-test-snapshot");
                reader.start();
                try {
                    start.countDown();
                    fixture.release.countDown();
                    String local = fixture.attempt.freezeDiagnostic();
                    reader.join(1_000);
                    assertFalse("snapshot reader did not join", reader.isAlive());
                    fixture.join();
                    assertEquals(local, concurrent.get());
                    assertEquals(local, fixture.attempt.freezeDiagnostic());
                    diagnosticRow("1," + local);
                } finally {
                    start.countDown();
                    reader.interrupt();
                    reader.join(1_000);
                }
            }
        }
    }

    @Test(timeout = 5_000)
    public void exactDefaultTargetsRemainTwoParallelAttemptsWithFirstValidWinner() throws Exception {
        java.lang.reflect.Field field = EgressProbeClient.class.getDeclaredField("ENDPOINTS");
        field.setAccessible(true);
        String[] endpoints = ((String[]) field.get(null)).clone();
        assertEquals(List.of("https://checkip.amazonaws.com/", "https://api.ipify.org/"), List.of(endpoints));
        String[] constants = {"CONNECT_TIMEOUT_MILLIS", "READ_TIMEOUT_MILLIS", "QUERY_TIMEOUT_MILLIS",
            "WORKER_SHUTDOWN_TIMEOUT_MILLIS", "MAX_RESPONSE_BYTES"};
        int[] expected = {10_000, 10_000, 20_000, 20_000, 256};
        for (int i = 0; i < constants.length; i += 1) {
            java.lang.reflect.Field constant = EgressProbeClient.class.getDeclaredField(constants[i]);
            constant.setAccessible(true);
            assertEquals(constants[i], expected[i], constant.getInt(null));
        }
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch releaseLoser = new CountDownLatch(1);
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger responseCalls = new AtomicInteger();
        AtomicInteger explicitConnectCalls = new AtomicInteger();
        String result = EgressProbeClient.queryPublicIp(endpoints, endpoint -> {
            opens.incrementAndGet();
            return new HttpURLConnection(new URL(endpoint)) {
                @Override public int getResponseCode() throws IOException {
                    responseCalls.incrementAndGet();
                    entered.countDown();
                    try {
                        if (!entered.await(1, TimeUnit.SECONDS)) {
                            throw new AssertionError("attempts were not parallel");
                        }
                        if (endpoint.equals(endpoints[0])) { releaseLoser.await(); }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IOException("canceled loser", error);
                    }
                    assertEquals(10_000, getConnectTimeout());
                    assertEquals(10_000, getReadTimeout());
                    assertFalse(getInstanceFollowRedirects());
                    return 200;
                }
                @Override public InputStream getInputStream() {
                    return new ByteArrayInputStream("203.0.113.9\n".getBytes(StandardCharsets.UTF_8));
                }
                @Override public void disconnect() { releaseLoser.countDown(); }
                @Override public boolean usingProxy() { return false; }
                @Override public void connect() {
                    explicitConnectCalls.incrementAndGet();
                    throw new AssertionError("extra connect");
                }
            };
        });
        assertEquals("203.0.113.9", result);
        assertEquals(2, opens.get());
        assertEquals(2, responseCalls.get());
        assertEquals(0, explicitConnectCalls.get());
    }

    private static String diagnosticSuffix(IOException error) {
        String marker = "; probe_phase_v1=";
        int at = error.getMessage().indexOf(marker);
        assertTrue("missing finite diagnostic", at >= 0);
        return error.getMessage().substring(at + marker.length());
    }

    private static long[][] diagnosticRows(IOException error) {
        String[] rows = diagnosticSuffix(error).split("\\|", -1);
        long[][] values = new long[rows.length][];
        for (int i = 0; i < rows.length; i += 1) {
            values[i] = diagnosticRow(rows[i]);
            assertEquals(i + 1, values[i][0]);
        }
        return values;
    }

    private static long[] diagnosticRow(String row) {
        assertTrue("diagnostic must contain seven bounded numeric fields only", row.matches(
            "[1-9][0-9]*,[0-8],[0-9]+,[0-9]+,(-1|[0-9]+),(-1|[1-5][0-9]{2}),[0-9]+"));
        String[] fields = row.split(",", -1);
        long[] values = new long[fields.length];
        for (int i = 0; i < values.length; i += 1) { values[i] = Long.parseLong(fields[i]); }
        assertTrue(values[3] <= values[2]);
        assertTrue(values[4] <= values[2]);
        assertTrue(values[6] <= 256);
        return values;
    }

    /** Fake-only barriers; close releases and joins even after an assertion failure. */
    private static final class BoundaryAttempt implements AutoCloseable {
        final byte[] body = "203.0.113.9\n".getBytes(StandardCharsets.UTF_8);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger disconnects = new AtomicInteger();
        final AtomicInteger responseCalls = new AtomicInteger();
        final AtomicReference<Throwable> workerError = new AtomicReference<>();
        final EgressProbeClient.Attempt attempt;
        final Thread worker;
        final int heldPhase;

        BoundaryAttempt(int heldPhase) {
            this.heldPhase = heldPhase;
            attempt = new EgressProbeClient.Attempt("https://boundary.invalid/", endpoint -> {
                hold(1);
                return new HttpURLConnection(new URL(endpoint)) {
                    @Override public int getResponseCode() throws IOException {
                        responseCalls.incrementAndGet();
                        hold(2);
                        return 200;
                    }
                    @Override public InputStream getInputStream() throws IOException {
                        hold(3);
                        return new ByteArrayInputStream(body) {
                            private int calls;
                            @Override public synchronized int read(byte[] target, int offset, int length) {
                                if (heldPhase == 4 && calls++ == 1) { holdUnchecked(4); }
                                return super.read(target, offset, heldPhase == 4 ? Math.min(4, length) : length);
                            }
                            @Override public void close() throws IOException { hold(5); }
                        };
                    }
                    @Override public void disconnect() {
                        if (disconnects.incrementAndGet() == 1 && heldPhase == 7) {
                            holdUnchecked(7);
                        } else {
                            release.countDown();
                        }
                    }
                    @Override public boolean usingProxy() { return false; }
                    @Override public void connect() { throw new AssertionError("extra connect"); }
                };
            }, new java.util.concurrent.ConcurrentHashMap<>());
            worker = new Thread(() -> {
                try { attempt.call(); } catch (Throwable error) { workerError.set(error); }
            }, "acceptance-egress-test-boundary");
            worker.setDaemon(true);
        }

        void start() { worker.start(); }

        private void hold(int phase) throws IOException {
            if (phase != heldPhase) { return; }
            entered.countDown();
            try { release.await(); } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("fake boundary interrupted", error);
            }
        }

        private void holdUnchecked(int phase) {
            try { hold(phase); } catch (IOException error) { throw new IllegalStateException(error); }
        }

        void join() throws InterruptedException {
            worker.join(1_000);
            assertFalse("boundary worker did not join", worker.isAlive());
            assertFalse("unexpected fixture error: " + workerError.get(), workerError.get() instanceof Error);
        }

        @Override public void close() throws InterruptedException {
            release.countDown();
            attempt.cancel();
            worker.interrupt();
            join();
        }
    }

    @Test
    public void defaultQueryAndCleanupBoundsFitBinderDeadline() {
        // Connect and read can be sequential before cancellation. The client
        // checks cancellation between them and between body reads, so cleanup
        // can reach at most one remaining blocking phase. The allowance covers
        // that phase while query+join still returns before the 45s Binder bound.
        assertEquals(10_000, EgressProbeClient.maximumBlockingPhaseDurationMillis());
        assertTrue(
            EgressProbeClient.defaultWorkerShutdownTimeoutMillis()
                >= EgressProbeClient.maximumBlockingPhaseDurationMillis()
        );
        assertEquals(40_000, EgressProbeClient.defaultMaximumDurationMillis());
        assertTrue(EgressProbeClient.defaultMaximumDurationMillis() < 45_000);
    }

    @Test(timeout = 5_000)
    public void blockedFirstEndpointDoesNotStarveFallback() throws Exception {
        String[] endpoints = {"https://first.invalid/", "https://second.invalid/"};
        CountDownLatch firstEnteredResponse = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstExitedResponse = new CountDownLatch(1);
        AtomicBoolean firstDisconnected = new AtomicBoolean();

        String address = EgressProbeClient.queryPublicIp(endpoints, endpoint -> {
            if (endpoint.equals(endpoints[0])) {
                return blockingConnection(
                    endpoint,
                    firstEnteredResponse,
                    releaseFirst,
                    firstExitedResponse,
                    () -> firstDisconnected.set(true)
                );
            }
            assertTrue("fallback opened before the blocked request started", firstEnteredResponse.await(1, TimeUnit.SECONDS));
            return responseConnection(endpoint, HttpURLConnection.HTTP_OK, "203.0.113.9\n");
        });

        assertEquals("203.0.113.9", address);
        assertTrue("winning the fallback did not disconnect the blocked request", firstDisconnected.get());
        assertTrue("blocked request worker outlived query completion", firstExitedResponse.await(1, TimeUnit.SECONDS));
    }

    @Test(timeout = 5_000)
    public void slowCancellationWithinCleanupBoundDoesNotEraseWinner() throws Exception {
        String[] endpoints = {"https://slow.invalid/", "https://winner.invalid/"};
        CountDownLatch slowEnteredResponse = new CountDownLatch(1);
        CountDownLatch slowExitedResponse = new CountDownLatch(1);
        AtomicBoolean slowDisconnected = new AtomicBoolean();
        AtomicBoolean slowEnteredBody = new AtomicBoolean();

        String address = EgressProbeClient.queryPublicIp(endpoints, endpoint -> {
            if (endpoint.equals(endpoints[0])) {
                return slowCancellationConnection(
                    endpoint,
                    slowEnteredResponse,
                    slowExitedResponse,
                    slowDisconnected,
                    slowEnteredBody,
                    1_250
                );
            }
            assertTrue(
                "winner opened before the slow request started",
                slowEnteredResponse.await(1, TimeUnit.SECONDS)
            );
            return responseConnection(endpoint, HttpURLConnection.HTTP_OK, "203.0.113.9\n");
        });

        assertEquals("203.0.113.9", address);
        assertTrue("slow loser was not disconnected", slowDisconnected.get());
        assertTrue("slow loser outlived the valid cleanup bound", slowExitedResponse.await(1, TimeUnit.SECONDS));
        assertFalse("canceled connect entered a sequential body read", slowEnteredBody.get());
    }

    @Test(timeout = 5_000)
    public void connectionPublishedAfterCancellationIsDisconnectedAndJoined() throws Exception {
        String endpoint = "https://late.invalid/";
        CountDownLatch lateFactoryEntered = new CountDownLatch(1);
        CountDownLatch publishConnection = new CountDownLatch(1);
        CountDownLatch lateEnteredResponse = new CountDownLatch(1);
        CountDownLatch attemptReturned = new CountDownLatch(1);
        AtomicBoolean lateDisconnected = new AtomicBoolean();
        AtomicReference<Throwable> attemptError = new AtomicReference<>();

        EgressProbeClient.Attempt attempt = new EgressProbeClient.Attempt(
            endpoint,
            ignored -> {
                lateFactoryEntered.countDown();
                publishConnection.await();
                return latePublishedConnection(
                    endpoint,
                    lateEnteredResponse,
                    lateDisconnected
                );
            },
            new java.util.concurrent.ConcurrentHashMap<>()
        );
        Thread worker = new Thread(() -> {
            try {
                attempt.call();
            } catch (Throwable error) {
                attemptError.set(error);
            } finally {
                attemptReturned.countDown();
            }
        }, "acceptance-egress-test-late-publication");
        worker.setDaemon(true);
        worker.start();

        assertTrue("factory did not reach its publication barrier", lateFactoryEntered.await(1, TimeUnit.SECONDS));
        attempt.cancel();
        publishConnection.countDown();
        assertTrue("late attempt did not return", attemptReturned.await(1, TimeUnit.SECONDS));
        worker.join(1_000);
        assertFalse("late attempt worker remained live", worker.isAlive());
        assertTrue("late attempt did not report cancellation", attemptError.get() instanceof IOException);
        assertTrue("late-published connection escaped cancellation", lateDisconnected.get());
        assertEquals("canceled late connection entered response I/O", 1, lateEnteredResponse.getCount());
    }

    @Test(timeout = 5_000)
    public void stuckCancellationFailsClosedWithinInjectedCleanupBound() throws Exception {
        String[] endpoints = {"https://stuck.invalid/", "https://winner.invalid/"};
        CountDownLatch stuckEnteredResponse = new CountDownLatch(1);
        CountDownLatch releaseStuck = new CountDownLatch(1);
        CountDownLatch stuckExitedResponse = new CountDownLatch(1);
        AtomicBoolean stuckDisconnected = new AtomicBoolean();

        try {
            IOException error = assertThrows(IOException.class, () ->
                EgressProbeClient.queryPublicIp(
                    endpoints,
                    endpoint -> {
                        if (endpoint.equals(endpoints[0])) {
                            return cancellationResistantConnection(
                                endpoint,
                                stuckEnteredResponse,
                                releaseStuck,
                                stuckExitedResponse,
                                stuckDisconnected
                            );
                        }
                        assertTrue(
                            "winner opened before the stuck request started",
                            stuckEnteredResponse.await(1, TimeUnit.SECONDS)
                        );
                        return responseConnection(
                            endpoint,
                            HttpURLConnection.HTTP_OK,
                            "203.0.113.9\n"
                        );
                    },
                    1_000,
                    100
                )
            );
            assertTrue(error.getMessage().contains("workers did not stop after cancellation"));
            assertTrue("stuck worker was not asked to disconnect", stuckDisconnected.get());
            long[][] rows = diagnosticRows(error);
            assertEquals("cleanup-only failure retains loser's response wait", 2, rows[0][1]);
            assertEquals(-1, rows[0][5]);
            assertEquals("winner stays complete in the pre-cancel snapshot", 8, rows[1][1]);
            assertEquals(200, rows[1][5]);
        } finally {
            releaseStuck.countDown();
        }
        assertTrue("released stuck worker did not exit", stuckExitedResponse.await(1, TimeUnit.SECONDS));
    }

    @Test(timeout = 5_000)
    public void callerInterruptIsRestoredAfterOwnedWorkersJoin() throws Exception {
        String endpoint = "https://blocked.invalid/";
        CountDownLatch enteredResponse = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exitedResponse = new CountDownLatch(1);
        CountDownLatch queryReturned = new CountDownLatch(1);
        AtomicReference<Throwable> queryError = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();

        Thread queryThread = new Thread(() -> {
            try {
                EgressProbeClient.queryPublicIp(
                    new String[] {endpoint},
                    ignored -> blockingConnection(
                        endpoint,
                        enteredResponse,
                        release,
                        exitedResponse,
                        () -> { }
                    ),
                    5_000,
                    1_000
                );
            } catch (Throwable error) {
                queryError.set(error);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            } finally {
                queryReturned.countDown();
            }
        }, "acceptance-egress-test-query-owner");
        queryThread.setDaemon(true);
        queryThread.start();

        assertTrue("probe worker never reached its blocking boundary", enteredResponse.await(1, TimeUnit.SECONDS));
        queryThread.interrupt();
        assertTrue("interrupted query did not return", queryReturned.await(1, TimeUnit.SECONDS));
        assertTrue("owned worker outlived interrupted query", exitedResponse.await(1, TimeUnit.SECONDS));
        assertTrue("query did not report interruption", queryError.get() instanceof IOException);
        assertTrue(queryError.get().getMessage().contains("query interrupted"));
        assertTrue("query owner interrupt status was not restored", interruptRestored.get());
        queryThread.join(1_000);
        assertFalse("query owner thread remained live", queryThread.isAlive());
    }

    @Test
    public void dnsFailureFallsBackToIndependentEndpoint() throws Exception {
        String[] endpoints = {"https://first.invalid/", "https://second.invalid/"};
        List<String> opened = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstAttempted = new CountDownLatch(1);

        String address = EgressProbeClient.queryPublicIp(endpoints, endpoint -> {
            opened.add(endpoint);
            if (endpoint.equals(endpoints[0])) {
                firstAttempted.countDown();
                throw new UnknownHostException("deterministic DNS timeout");
            }
            assertTrue("fallback raced ahead of the intended DNS failure", firstAttempted.await(1, TimeUnit.SECONDS));
            return responseConnection(endpoint, HttpURLConnection.HTTP_OK, "203.0.113.9\n");
        });

        assertEquals("203.0.113.9", address);
        assertEquals(Set.of(endpoints), new HashSet<>(opened));
    }

    @Test(timeout = 5_000)
    public void deadlineCancelsAndJoinsEveryAttempt() throws Exception {
        String[] endpoints = {"https://first.invalid/", "https://second.invalid/"};
        CountDownLatch enteredResponse = new CountDownLatch(endpoints.length);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exitedResponse = new CountDownLatch(endpoints.length);
        AtomicInteger disconnected = new AtomicInteger();

        IOException error = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(
                endpoints,
                endpoint -> blockingConnection(
                    endpoint,
                    enteredResponse,
                    release,
                    exitedResponse,
                    disconnected::incrementAndGet
                ),
                1_000
            )
        );

        assertTrue(error.getMessage().contains("deadline exceeded"));
        assertEquals("not every attempt reached its blocking boundary", 0, enteredResponse.getCount());
        assertEquals("not every blocked connection was disconnected", endpoints.length, disconnected.get());
        assertTrue("a canceled request worker outlived query completion", exitedResponse.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void everyEndpointFailureRemainsAFailure() {
        String[] endpoints = {"https://first.invalid/", "https://second.invalid/"};
        IOException error = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(endpoints, endpoint -> {
                throw new UnknownHostException("deterministic DNS timeout");
            })
        );

        assertTrue(error.getMessage().contains(endpoints[0]));
        assertTrue(error.getMessage().contains(endpoints[1]));
    }

    @Test
    public void invalidFallbackResponseIsRejected() {
        String[] endpoints = {"https://first.invalid/", "https://second.invalid/"};
        IOException error = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(endpoints, endpoint ->
                responseConnection(endpoint, HttpURLConnection.HTTP_OK, "not an address\n")
            )
        );

        assertTrue(error.getMessage().contains("invalid address response"));
    }

    @Test
    public void syntacticallyInvalidAddressShapesAreRejected() {
        String[] invalidAddresses = {":", "dead:beef", "999.1.2.3", "1.2.3"};
        for (String invalidAddress : invalidAddresses) {
            IOException error = assertThrows(IOException.class, () ->
                EgressProbeClient.queryPublicIp(
                    new String[] {"https://invalid.test/"},
                    endpoint -> responseConnection(
                        endpoint,
                        HttpURLConnection.HTTP_OK,
                        invalidAddress + "\n"
                    )
                )
            );
            assertTrue(error.getMessage().contains("invalid address response"));
        }
    }

    @Test
    public void ipv6AddressIsAccepted() throws Exception {
        String address = EgressProbeClient.queryPublicIp(
            new String[] {"https://ipv6.test/"},
            endpoint -> responseConnection(
                endpoint,
                HttpURLConnection.HTTP_OK,
                "2001:db8::1234\n"
            )
        );

        assertEquals("2001:db8::1234", address);
    }

    @Test
    public void oversizedResponseIsRejectedAndDisconnected() {
        AtomicBoolean disconnected = new AtomicBoolean();
        String body = "1".repeat(257);

        IOException error = assertThrows(IOException.class, () ->
            EgressProbeClient.queryPublicIp(
                new String[] {"https://oversized.test/"},
                endpoint -> responseConnection(
                    endpoint,
                    HttpURLConnection.HTTP_OK,
                    body,
                    () -> disconnected.set(true)
                )
            )
        );

        assertTrue(error.getMessage().contains("response exceeds 256 bytes"));
        assertTrue("oversized response connection was not disconnected", disconnected.get());
    }

    private static HttpURLConnection responseConnection(String endpoint, int statusCode, String body)
        throws Exception {
        return responseConnection(endpoint, statusCode, body, () -> { });
    }

    private static HttpURLConnection responseConnection(
        String endpoint,
        int statusCode,
        String body,
        Runnable onDisconnect
    ) throws Exception {
        return new HttpURLConnection(new URL(endpoint)) {
            @Override
            public int getResponseCode() {
                return statusCode;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void disconnect() {
                onDisconnect.run();
            }

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public void connect() {
            }
        };
    }

    private static HttpURLConnection blockingConnection(
        String endpoint,
        CountDownLatch enteredResponse,
        CountDownLatch release,
        CountDownLatch exitedResponse,
        Runnable onDisconnect
    ) throws Exception {
        AtomicBoolean disconnected = new AtomicBoolean();
        return new HttpURLConnection(new URL(endpoint)) {
            @Override
            public int getResponseCode() throws IOException {
                enteredResponse.countDown();
                try {
                    release.await();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", error);
                } finally {
                    exitedResponse.countDown();
                }
                throw new IOException("released without a response");
            }

            @Override
            public InputStream getInputStream() {
                throw new AssertionError("blocked connection has no response body");
            }

            @Override
            public void disconnect() {
                if (disconnected.compareAndSet(false, true)) {
                    onDisconnect.run();
                }
                release.countDown();
            }

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public void connect() {
            }
        };
    }

    private static HttpURLConnection slowCancellationConnection(
        String endpoint,
        CountDownLatch enteredResponse,
        CountDownLatch exitedResponse,
        AtomicBoolean disconnected,
        AtomicBoolean enteredBody,
        long releaseDelayMillis
    ) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean releaseStarted = new AtomicBoolean();
        return new HttpURLConnection(new URL(endpoint)) {
            @Override
            public int getResponseCode() throws IOException {
                enteredResponse.countDown();
                try {
                    while (true) {
                        try {
                            release.await();
                            return HttpURLConnection.HTTP_OK;
                        } catch (InterruptedException error) {
                            // Model Android's HttpURLConnection/native I/O: a
                            // Future interrupt alone does not stop the request.
                        }
                    }
                } finally {
                    exitedResponse.countDown();
                }
            }

            @Override
            public InputStream getInputStream() {
                enteredBody.set(true);
                return new ByteArrayInputStream("203.0.113.10\n".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void disconnect() {
                disconnected.set(true);
                if (releaseStarted.compareAndSet(false, true)) {
                    Thread releaseThread = new Thread(() -> {
                        try {
                            Thread.sleep(releaseDelayMillis);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                        } finally {
                            release.countDown();
                        }
                    }, "acceptance-egress-test-delayed-release");
                    releaseThread.setDaemon(true);
                    releaseThread.start();
                }
            }

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public void connect() {
            }
        };
    }

    private static HttpURLConnection cancellationResistantConnection(
        String endpoint,
        CountDownLatch enteredResponse,
        CountDownLatch release,
        CountDownLatch exitedResponse,
        AtomicBoolean disconnected
    ) throws Exception {
        return new HttpURLConnection(new URL(endpoint)) {
            @Override
            public int getResponseCode() throws IOException {
                enteredResponse.countDown();
                try {
                    while (true) {
                        try {
                            release.await();
                            throw new IOException("released stuck request");
                        } catch (InterruptedException error) {
                            // Deliberately resist interrupt so the injected
                            // cleanup bound, not scheduler luck, decides failure.
                        }
                    }
                } finally {
                    exitedResponse.countDown();
                }
            }

            @Override
            public InputStream getInputStream() {
                throw new AssertionError("stuck connection has no response body");
            }

            @Override
            public void disconnect() {
                disconnected.set(true);
            }

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public void connect() {
            }
        };
    }

    private static HttpURLConnection latePublishedConnection(
        String endpoint,
        CountDownLatch enteredResponse,
        AtomicBoolean disconnected
    ) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        return new HttpURLConnection(new URL(endpoint)) {
            @Override
            public int getResponseCode() throws IOException {
                enteredResponse.countDown();
                while (true) {
                    try {
                        release.await();
                        throw new IOException("late connection released");
                    } catch (InterruptedException error) {
                        // Future cancellation can precede factory publication.
                        // Only owner cancellation/disconnect may release it.
                    }
                }
            }

            @Override
            public InputStream getInputStream() {
                throw new AssertionError("late connection has no response body");
            }

            @Override
            public void disconnect() {
                disconnected.set(true);
                release.countDown();
            }

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public void connect() {
            }
        };
    }
}
