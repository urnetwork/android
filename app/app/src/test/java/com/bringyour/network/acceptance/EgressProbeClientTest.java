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
