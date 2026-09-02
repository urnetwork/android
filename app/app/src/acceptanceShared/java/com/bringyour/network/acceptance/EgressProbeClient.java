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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final int WORKER_SHUTDOWN_TIMEOUT_MILLIS = 1_000;
    private static final int MAX_RESPONSE_BYTES = 256;
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9a-fA-F:]+");
    private static final String[] ENDPOINTS = {
        "https://checkip.amazonaws.com/",
        "https://api.ipify.org/",
    };

    interface ConnectionFactory {
        HttpURLConnection open(String endpoint) throws Exception;
    }

    private EgressProbeClient() {
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
        if (endpoints.length == 0) {
            throw new IllegalArgumentException("at least one egress endpoint is required");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("egress query timeout must be positive");
        }

        Map<String, String> failures = new ConcurrentHashMap<>();
        Set<HttpURLConnection> activeConnections = ConcurrentHashMap.newKeySet();
        List<Callable<String>> attempts = new ArrayList<>();
        for (String endpoint : endpoints) {
            attempts.add(() -> {
                HttpURLConnection connection = null;
                try {
                    connection = connectionFactory.open(endpoint);
                    activeConnections.add(connection);
                    return queryEndpoint(connection);
                } catch (Exception error) {
                    failures.put(endpoint, failureDetail(error));
                    throw error;
                } finally {
                    if (connection != null) {
                        activeConnections.remove(connection);
                    }
                }
            });
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

        String address = null;
        Exception queryFailure = null;
        try {
            address = executor.invokeAny(attempts, timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            queryFailure = new IOException(
                "egress query deadline exceeded after " + timeoutMillis + "ms: "
                    + formatFailures(endpoints, failures),
                error
            );
        } catch (ExecutionException error) {
            queryFailure = new IOException(
                "all egress endpoints failed: " + formatFailures(endpoints, failures),
                error.getCause()
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            queryFailure = new IOException("egress query interrupted", error);
        }

        for (HttpURLConnection connection : activeConnections) {
            connection.disconnect();
        }
        executor.shutdownNow();
        boolean stopped;
        try {
            stopped = executor.awaitTermination(WORKER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            stopped = false;
            if (queryFailure == null) {
                queryFailure = new IOException("interrupted while stopping egress probe workers", error);
            } else {
                queryFailure.addSuppressed(error);
            }
        }
        for (HttpURLConnection connection : activeConnections) {
            connection.disconnect();
        }
        if (!stopped) {
            IOException cleanupFailure = new IOException("egress probe workers did not stop after cancellation");
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

    private static String queryEndpoint(HttpURLConnection connection) throws Exception {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + statusCode);
            }
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[64];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (MAX_RESPONSE_BYTES < response.size() + count) {
                        throw new IllegalStateException(
                            "address response exceeds " + MAX_RESPONSE_BYTES + " bytes"
                        );
                    }
                    response.write(buffer, 0, count);
                }
            }
            String address = new String(response.toByteArray(), StandardCharsets.UTF_8).trim();
            if (!isIpAddress(address)) {
                throw new IllegalStateException("invalid address response");
            }
            return address;
        } finally {
            connection.disconnect();
        }
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
