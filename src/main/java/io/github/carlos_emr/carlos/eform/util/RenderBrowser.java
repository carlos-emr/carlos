/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The renderer's bounded W3C WebDriver transport to the separately confined loopback service.
 * Chrome still speaks CDP over its private pipe; no debugging TCP port is opened. Keep the
 * command set small: navigation, scripts, logs, native printing and session cleanup only.
 */
class RenderBrowser implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private final URI serviceUri;
    private final HttpClient client;
    private final Duration readTimeout;
    private final String sessionId;

    RenderBrowser(URI serviceUri, Map<String, Object> capabilities) {
        this.serviceUri = EFormBrowserPdfService.validateBrowserServiceUrl(serviceUri.toString());
        this.readTimeout = EFormBrowserPdfService.WEBDRIVER_COMMAND_READ_TIMEOUT;
        // This is a validated local control channel, never an HTTP egress destination. Routing
        // it through an inherited system proxy could expose the url-base capability token.
        this.client = HttpClient.newBuilder()
                .proxy(ProxySelector.of(null))
                .connectTimeout(EFormBrowserPdfService.WEBDRIVER_CONNECTION_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            JsonNode session = command("POST", "/session",
                    Map.of("capabilities", Map.of("alwaysMatch", capabilities)));
            String id = session.path("sessionId").asText();
            if (!id.matches("[A-Za-z0-9_-]{1,256}")) {
                throw new IllegalStateException("Invalid browser session response");
            }
            sessionId = id;
        } catch (RuntimeException failure) {
            client.close();
            throw failure;
        }
    }

    String getSessionId() { return sessionId; }

    void setTimeouts(Duration pageLoad, Duration script) {
        sessionCommand("POST", "/timeouts",
                Map.of("pageLoad", pageLoad.toMillis(), "script", script.toMillis()));
    }

    void get(String url) { sessionCommand("POST", "/url", Map.of("url", url)); }

    String getCurrentUrl() { return sessionCommand("GET", "/url", null).asText(); }

    Object executeScript(String script, Object... args) { return script("sync", script, args); }

    Object executeAsyncScript(String script, Object... args) { return script("async", script, args); }

    private Object script(String mode, String script, Object[] args) {
        return JSON.convertValue(sessionCommand("POST", "/execute/" + mode,
                Map.of("script", script, "args", Arrays.asList(args))), Object.class);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> executeCdpCommand(String name, Map<String, Object> params) {
        JsonNode result = sessionCommand("POST", "/goog/cdp/execute", Map.of("cmd", name, "params", params));
        if (!result.isObject()) throw new IllegalStateException("Invalid browser command response");
        return JSON.convertValue(result, Map.class);
    }

    List<LogEntry> logs(String type) {
        JsonNode result = sessionCommand("POST", "/se/log", Map.of("type", type));
        if (!result.isArray()) throw new IllegalStateException("Invalid browser log response");
        java.util.ArrayList<LogEntry> entries = new java.util.ArrayList<>();
        for (JsonNode entry : result) {
            if (!entry.path("message").isTextual() || !entry.path("level").isTextual()) {
                throw new IllegalStateException("Invalid browser log entry");
            }
            entries.add(new LogEntry(Level.parse(entry.path("level").asText()),
                    entry.path("timestamp").asLong(), entry.path("message").asText()));
        }
        return List.copyOf(entries);
    }

    void quit() {
        try {
            sessionCommand("DELETE", "", null);
        } finally {
            client.close();
        }
    }

    @Override public void close() { quit(); }

    private JsonNode sessionCommand(String method, String path, Object body) {
        return command(method, "/session/" + sessionId + path, body);
    }

    private JsonNode command(String method, String path, Object body) {
        try {
            String base = serviceUri.toString().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(readTimeout).header("Content-Type", "application/json; charset=utf-8")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope;
            try {
                envelope = JSON.readTree(response.body());
            } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
                throw new IllegalStateException("Invalid browser response");
            }
            if (response.statusCode() != 200 || envelope == null || !envelope.has("value")
                    || envelope.path("value").has("error")) {
                // Browser replies can contain clinical page content and capability URLs. Never
                // put their body or message into an exception, even on transport failure.
                throw new IllegalStateException("Browser command failed (HTTP " + response.statusCode() + ")");
            }
            return envelope.get("value");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Browser command interrupted");
        } catch (IOException failure) {
            // Keep the cause for typed connect-failure classification; callers redact logs.
            throw new IllegalStateException("Browser command transport failed", failure);
        }
    }

    record LogEntry(Level level, long timestamp, String message) {
        Level getLevel() { return level; }
        String getMessage() { return message; }
    }
}
