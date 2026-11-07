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
import java.util.Set;
import java.util.logging.Level;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The renderer's bounded W3C WebDriver transport to the separately confined loopback service.
 * Chrome still speaks CDP over its private pipe; no debugging TCP port is opened. Keep the
 * command set small: navigation, scripts, logs, native printing and session cleanup only.
 *
 * <p>Failure messages are part of the PHI boundary. A command that fails reports its HTTP status
 * and the W3C error code, and the code only when it is one of the fixed protocol strings below:
 * the rest of the reply can carry page content and capability URLs. Session creation is the one
 * exception, because no patient page has been loaded yet and its driver message (version mismatch,
 * missing binary, sandbox crash) is what an operator needs; even that is URL/path-redacted,
 * flattened to one line and truncated.</p>
 */
final class RenderBrowser implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private static final String VALUE = "value";
    private static final String NEW_SESSION_PATH = "/session";

    /**
     * Every error code the W3C WebDriver specification defines ("Errors" table), plus the few fixed
     * status strings chromedriver itself emits. Anything else is reported as {@code unrecognized}
     * so that free text can never ride into a log line in the code slot.
     */
    static final Set<String> KNOWN_ERROR_CODES = Set.of(
            "detached shadow root", "element click intercepted", "element not interactable",
            "insecure certificate", "invalid argument", "invalid cookie domain", "invalid element state",
            "invalid selector", "invalid session id", "javascript error", "move target out of bounds",
            "no such alert", "no such cookie", "no such element", "no such frame", "no such shadow root",
            "no such window", "script timeout", "session not created", "stale element reference",
            "timeout", "unable to capture screen", "unable to set cookie", "unexpected alert open",
            "unknown command", "unknown error", "unknown method", "unsupported operation",
            // chromedriver's own status names (chrome/test/chromedriver/chrome/status.cc)
            "chrome not reachable", "disconnected", "no such execution context", "tab crashed",
            "target frame detached");
    static final String UNRECOGNIZED_ERROR = "unrecognized";
    /** Upper bound on the redacted session-creation driver message carried in an exception. */
    static final int SESSION_DETAIL_LIMIT = 300;
    /** Bound on the raw text the redaction regexes ever see; chromedriver replies are unbounded. */
    private static final int SESSION_DETAIL_SCAN_LIMIT = 4096;
    private static final java.util.regex.Pattern LINE_BREAKS =
            java.util.regex.Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]+");

    private final URI serviceUri;
    private final HttpClient client;
    private final Duration readTimeout;
    private final String sessionId;

    RenderBrowser(URI serviceUri, Map<String, Object> capabilities) {
        this.serviceUri = EFormBrowserPdfService.validateBrowserServiceUrl(serviceUri.toString());
        this.readTimeout = EFormBrowserPdfService.WEBDRIVER_COMMAND_READ_TIMEOUT;
        // This is a validated local control channel, never an HTTP egress destination. Routing
        // it through an inherited system proxy could expose the url-base capability token.
        // HTTP/1.1 is pinned because the JDK's default (HTTP/2) offers an h2c upgrade on every
        // cleartext request, which chromedriver's server neither needs nor is tested against.
        this.client = HttpClient.newBuilder()
                .proxy(ProxySelector.of(null))
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(EFormBrowserPdfService.WEBDRIVER_CONNECTION_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            JsonNode session = command("POST", NEW_SESSION_PATH,
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
            entries.add(new LogEntry(logLevel(entry.path("level").asText()),
                    entry.path("timestamp").asLong(), entry.path("message").asText()));
        }
        return List.copyOf(entries);
    }

    /**
     * Maps chromedriver's log level names (ALL, DEBUG, INFO, WARNING, SEVERE, OFF) onto
     * {@link Level}. {@code Level.parse} rejects DEBUG, which is not a java.util.logging name, so it
     * would fail a whole log drain over one verbose entry. An unknown name maps to INFO: below the
     * SEVERE console gate, and never an exception on the render path.
     */
    static Level logLevel(String name) {
        return switch (name) {
            case "ALL" -> Level.ALL;
            case "DEBUG" -> Level.FINE;
            case "WARNING" -> Level.WARNING;
            case "SEVERE" -> Level.SEVERE;
            case "OFF" -> Level.OFF;
            default -> Level.INFO;
        };
    }

    /**
     * Deletes the session, even from a thread whose interrupt flag is set.
     *
     * <p>{@link HttpClient#send} gives up at once on an interrupted thread, and a failed command
     * (including an interrupted one) leaves the flag set. Sending the DELETE with the flag still set
     * would therefore always fail and leave a browser holding a rendered patient page running. The
     * flag is cleared for the one bounded DELETE and restored afterwards, so the caller still sees
     * the interrupt.</p>
     */
    void quit() {
        boolean interrupted = Thread.interrupted();
        try {
            sessionCommand("DELETE", "", null);
        } finally {
            try {
                client.close();
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override public void close() { quit(); }

    private JsonNode sessionCommand(String method, String path, Object body) {
        return command(method, "/session/" + sessionId + path, body);
    }

    private JsonNode command(String method, String path, Object body) {
        HttpResponse<String> response;
        try {
            // Validation already rejects a trailing slash, including on capability prefixes.
            String base = serviceUri.toString();
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(readTimeout).header("Content-Type", "application/json; charset=utf-8")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Browser command interrupted");
        } catch (IOException failure) {
            // Keep the cause for typed connect-failure classification; callers redact logs.
            throw new IllegalStateException("Browser command transport failed", failure);
        }
        if (response.statusCode() != 200) {
            // Only a failed reply is read for value.error: a successful script may legitimately
            // return an object with an "error" key, and that is the page's data, not a failure.
            throw commandFailure(response.statusCode(), readEnvelopeQuietly(response.body()),
                    "POST".equals(method) && NEW_SESSION_PATH.equals(path));
        }
        JsonNode envelope = readEnvelope(response.body());
        if (envelope == null || !envelope.has(VALUE)) {
            throw new IllegalStateException("Invalid browser response");
        }
        return envelope.get(VALUE);
    }

    /**
     * Builds the exception for a non-200 reply. Browser replies can contain clinical page content
     * and capability URLs, so the reply body is never copied: the message carries the status, the
     * allow-listed W3C code and, for session creation only, the redacted driver message.
     */
    static IllegalStateException commandFailure(int status, JsonNode envelope, boolean sessionCreation) {
        JsonNode error = envelope == null ? null : envelope.path(VALUE);
        String code = error != null && error.path("error").isTextual()
                && KNOWN_ERROR_CODES.contains(error.path("error").asText())
                ? error.path("error").asText() : UNRECOGNIZED_ERROR;
        StringBuilder message = new StringBuilder("Browser command failed (HTTP ")
                .append(status).append(", ").append(code).append(')');
        if (sessionCreation && error != null && error.path("message").isTextual()) {
            String detail = sessionCreationDetail(error.path("message").asText());
            if (!detail.isEmpty()) {
                message.append(": ").append(detail);
            }
        }
        return new IllegalStateException(message.toString());
    }

    /**
     * The new-session driver message as one redacted line of at most {@link #SESSION_DETAIL_LIMIT}
     * characters. Redaction runs before truncation so a cut can only drop text, never expose the
     * remainder of a URL or path the redaction would have removed whole.
     */
    static String sessionCreationDetail(String driverMessage) {
        String bounded = driverMessage.length() > SESSION_DETAIL_SCAN_LIMIT
                ? driverMessage.substring(0, SESSION_DETAIL_SCAN_LIMIT) : driverMessage;
        // One line: chromedriver separates "Current browser version is ..." with a newline, and a
        // raw newline in a log message lets reply text forge a log record.
        String flattened = LINE_BREAKS.matcher(RenderLogRedaction.redactUrls(bounded))
                .replaceAll(" ").strip();
        if (flattened.length() <= SESSION_DETAIL_LIMIT) {
            return flattened;
        }
        int end = SESSION_DETAIL_LIMIT;
        if (Character.isHighSurrogate(flattened.charAt(end - 1))) {
            end--;
        }
        return flattened.substring(0, end) + "...";
    }

    private static JsonNode readEnvelope(String body) {
        try {
            return JSON.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException _) {
            throw new IllegalStateException("Invalid browser response");
        }
    }

    /** A failed reply may be an HTML error page from something that is not chromedriver. */
    private static JsonNode readEnvelopeQuietly(String body) {
        try {
            return JSON.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException _) {
            return null;
        }
    }

    record LogEntry(Level level, long timestamp, String message) {
        Level getLevel() { return level; }
        String getMessage() { return message; }
    }
}
