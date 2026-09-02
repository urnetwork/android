package com.bringyour.network.acceptance;

import static org.junit.Assert.assertEquals;
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
import org.junit.Test;

public final class EgressProbeClientTest {
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
}
