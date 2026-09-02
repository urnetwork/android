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
import java.util.List;
import org.junit.Test;

public final class EgressProbeClientTest {
    @Test
    public void dnsFailureFallsBackToIndependentEndpoint() throws Exception {
        String[] endpoints = {"https://first.invalid/", "https://second.invalid/"};
        List<String> opened = new ArrayList<>();

        String address = EgressProbeClient.queryPublicIp(endpoints, endpoint -> {
            opened.add(endpoint);
            if (endpoint.equals(endpoints[0])) {
                throw new UnknownHostException("deterministic DNS timeout");
            }
            return responseConnection(endpoint, HttpURLConnection.HTTP_OK, "203.0.113.9\n");
        });

        assertEquals("203.0.113.9", address);
        assertEquals(List.of(endpoints), opened);
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

    private static HttpURLConnection responseConnection(String endpoint, int statusCode, String body)
        throws Exception {
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
