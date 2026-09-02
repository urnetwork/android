package com.bringyour.network.acceptance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves public egress through independent endpoints. A single external DNS
 * timeout cannot decide the acceptance result, while failure of every endpoint
 * still proves the path unusable.
 */
final class EgressProbeClient {
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
        List<String> failures = new ArrayList<>();
        for (String endpoint : endpoints) {
            try {
                return queryEndpoint(connectionFactory.open(endpoint));
            } catch (Exception error) {
                String detail = error.getMessage();
                if (detail == null) {
                    detail = "unknown";
                }
                failures.add(endpoint + "=" + error.getClass().getSimpleName() + ":" + detail);
            }
        }
        throw new IOException("all egress endpoints failed: " + String.join("; ", failures));
    }

    private static String queryEndpoint(HttpURLConnection connection) throws Exception {
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setInstanceFollowRedirects(false);
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + statusCode);
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(),
                StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            String address = response.toString().trim();
            if (!isIpAddress(address)) {
                throw new IllegalStateException("invalid address response");
            }
            return address;
        } finally {
            connection.disconnect();
        }
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
