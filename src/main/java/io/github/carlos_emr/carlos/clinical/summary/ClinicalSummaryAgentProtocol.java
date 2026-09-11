/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Shared strict JSON and bounded loopback transport for agent adapters. */
final class ClinicalSummaryAgentProtocol {
    static final int VERSION = 1;
    static final int MAX_REQUEST_BYTES = 60000;
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ClinicalSummaryAgentProtocol() { }

    static int timeoutMillis(String configured) {
        int seconds = Integer.parseInt(configured);
        if (seconds < 1 || seconds > 1800) {
            throw new IllegalArgumentException("Agent timeout must be between 1 and 1800 seconds");
        }
        return seconds * 1000;
    }

    static void validateConnection(int port, int timeoutMs) {
        if (port < 1 || port > 65535 || timeoutMs < 1 || timeoutMs > 1800000) {
            throw new IllegalArgumentException("Invalid agent connection configuration");
        }
    }

    static JsonNode post(int port, String path, byte[] body, int timeoutMs) throws IOException {
        validateConnection(port, timeoutMs);
        if (!path.matches("/[A-Za-z0-9/_-]+") || body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Invalid agent path or oversized request");
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path)
                .toURL().openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (var output = connection.getOutputStream()) {
                output.write(body);
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Agent request rejected");
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new IOException("Agent response too large");
                }
                return JSON.readTree(bytes);
            }
        } finally {
            connection.disconnect();
        }
    }

    static String resource(String name) throws IOException {
        try (InputStream input = ClinicalSummaryAgentProtocol.class.getResourceAsStream("/clinical/summary/" + name)) {
            if (input == null) throw new IOException("Missing agent contract resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void exactFields(JsonNode object, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        if (object == null || !object.isObject()) throw new IllegalArgumentException("Expected agent object");
        object.fieldNames().forEachRemaining(actual::add);
        if (!expected.equals(actual)) throw new IllegalArgumentException("Unexpected agent fields");
    }
}
