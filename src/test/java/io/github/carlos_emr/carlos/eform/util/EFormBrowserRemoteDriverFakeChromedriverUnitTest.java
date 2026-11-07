/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Exercises the JDK transport and session teardown against a local W3C protocol fixture.
 *
 * <p>The fixture records every request (method, path, headers and body), so these tests pin the
 * wire protocol a real chromedriver depends on, not just which paths were hit. Replies are
 * synthetic: no real patient value appears anywhere, and the "clinical" strings in error replies
 * stand in for page content that must never reach an exception message.</p>
 */
@DisplayName("JDK render browser against a fake chromedriver")
@Tag("unit")
@Tag("eform")
class EFormBrowserRemoteDriverFakeChromedriverUnitTest {

    private static final String SESSION_ID = "fake-session-0123456789abcdef";
    private static final String SESSION = "/session/" + SESSION_ID;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One request as the fake chromedriver received it. */
    private record Recorded(String method, String path, Map<String, List<String>> headers, String body) {
        String key() {
            return method + " " + path;
        }

        JsonNode json() throws IOException {
            return JSON.readTree(body);
        }
    }

    /** A canned reply for one "METHOD path" key. */
    private record Reply(int status, String body, Map<String, String> headers) {
        static Reply json(int status, String body) {
            return new Reply(status, body, Map.of());
        }
    }

    private HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();

    @BeforeEach
    void startFakeChromedriver() throws IOException {
        // A real W3C new-session reply, without any Selenium-specific capabilities.
        replies.put("POST /session", Reply.json(200, "{\"value\":{\"sessionId\":\"" + SESSION_ID + "\","
                + "\"capabilities\":{\"browserName\":\"chrome\",\"browserVersion\":\"120\","
                + "\"goog:chromeOptions\":{\"debuggerAddress\":\"127.0.0.1:1\"}}}}"));
        replies.put("POST " + SESSION + "/goog/cdp/execute", Reply.json(200, "{\"value\":{\"data\":\"ZmFrZS1wZGY=\"}}"));
        replies.put("DELETE " + SESSION, Reply.json(200, "{\"value\":null}"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Recorded request = new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    Map.copyOf(exchange.getRequestHeaders()), body);
            requests.add(request);
            // Anything else the client sends gets a null success.
            respond(exchange, replies.getOrDefault(request.key(), Reply.json(200, "{\"value\":null}")));
        });
        server.start();
    }

    @AfterEach
    void stopFakeChromedriver() {
        // Never leak an interrupt into the next test on this worker thread.
        Thread.interrupted();
        if (server != null) {
            server.stop(0);
        }
        requests.clear();
        replies.clear();
    }

    private static void respond(HttpExchange exchange, Reply reply) throws IOException {
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        reply.headers().forEach(exchange.getResponseHeaders()::set);
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private URL serverUrl() throws IOException {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort()).toURL();
    }

    private RenderBrowser connect() throws IOException {
        return new RenderBrowser(
                URI.create(serverUrl().toString()), new BrowserOptions().asMap());
    }

    private Recorded only(String key) {
        List<Recorded> matching = requests.stream().filter(r -> r.key().equals(key)).toList();
        assertThat(matching).as("requests for %s", key).hasSize(1);
        return matching.get(0);
    }

    private List<String> keys() {
        return requests.stream().map(Recorded::key).toList();
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    @Test
    @DisplayName("should issue the CDP execute command and return its non-empty result")
    void shouldIssueCdpExecuteCommand_andReturnNonEmptyResult() throws IOException {
        RenderBrowser driver = connect();
        try {
            Map<String, Object> result = driver.executeCdpCommand("Page.printToPDF", Map.of());

            // Exercise the actual CDP bridge: both the PDF data and its request path must survive
            // transport changes. A canned/empty result must not stand in for a browser command.
            assertThat(result).isNotEmpty();
            assertThat(result).containsKey("data");
            JsonNode body = only("POST " + SESSION + "/goog/cdp/execute").json();
            assertThat(body.path("cmd").asText()).isEqualTo("Page.printToPDF");
            assertThat(body.path("params").isObject()).isTrue();
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should send W3C alwaysMatch capabilities with goog:chromeOptions on new session")
    void shouldSendAlwaysMatchChromeOptions_whenCreatingSession() throws IOException {
        BrowserOptions options = new BrowserOptions();
        options.addArguments("--headless=new", "--disable-gpu");
        options.setBinary("/opt/synthetic/chrome");
        options.setCapability("goog:loggingPrefs", Map.of("performance", "ALL", "browser", "SEVERE"));

        RenderBrowser driver = new RenderBrowser(URI.create(serverUrl().toString()), options.asMap());
        try {
            Recorded request = only("POST /session");
            assertThat(request.headers().get("Content-type")).containsExactly("application/json; charset=utf-8");
            JsonNode body = request.json();
            // W3C only: a legacy desiredCapabilities body would start a non-W3C session.
            assertThat(body.fieldNames()).toIterable().containsExactly("capabilities");
            assertThat(body.path("capabilities").fieldNames()).toIterable().containsExactly("alwaysMatch");
            JsonNode alwaysMatch = body.path("capabilities").path("alwaysMatch");
            assertThat(alwaysMatch.path("browserName").asText()).isEqualTo("chrome");
            JsonNode chromeOptions = alwaysMatch.path("goog:chromeOptions");
            assertThat(texts(chromeOptions.path("args"))).containsExactly("--headless=new", "--disable-gpu");
            assertThat(chromeOptions.path("binary").asText()).isEqualTo("/opt/synthetic/chrome");
            assertThat(alwaysMatch.path("goog:loggingPrefs").path("performance").asText()).isEqualTo("ALL");
            assertThat(driver.getSessionId()).isEqualTo(SESSION_ID);
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should send page-load and script timeouts as W3C millisecond numbers")
    void shouldSendMillisecondTimeouts_whenSettingTimeouts() throws IOException {
        RenderBrowser driver = connect();
        try {
            driver.setTimeouts(Duration.ofSeconds(30), Duration.ofMillis(45_500));

            JsonNode body = only("POST " + SESSION + "/timeouts").json();
            assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder("pageLoad", "script");
            assertThat(body.path("pageLoad").isIntegralNumber()).isTrue();
            assertThat(body.path("pageLoad").asLong()).isEqualTo(30_000L);
            assertThat(body.path("script").asLong()).isEqualTo(45_500L);
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should send script and args for synchronous and asynchronous execution")
    void shouldSendScriptAndArgs_forSyncAndAsyncExecution() throws IOException {
        RenderBrowser driver = connect();
        try {
            driver.executeScript("return arguments[0] + arguments[1];", 7, "synthetic");
            driver.executeAsyncScript("arguments[arguments.length - 1](null);");

            JsonNode sync = only("POST " + SESSION + "/execute/sync").json();
            assertThat(sync.path("script").asText()).isEqualTo("return arguments[0] + arguments[1];");
            assertThat(sync.path("args").isArray()).isTrue();
            assertThat(sync.path("args").size()).isEqualTo(2);
            assertThat(sync.path("args").get(0).isIntegralNumber()).isTrue();
            assertThat(sync.path("args").get(0).asInt()).isEqualTo(7);
            assertThat(sync.path("args").get(1).asText()).isEqualTo("synthetic");
            JsonNode async = only("POST " + SESSION + "/execute/async").json();
            assertThat(async.path("script").asText()).isEqualTo("arguments[arguments.length - 1](null);");
            // W3C requires args even when empty; chromedriver rejects a missing array.
            assertThat(async.path("args").isArray()).isTrue();
            assertThat(async.path("args").size()).isZero();
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should send the log type and map chromedriver log level names, including DEBUG")
    void shouldMapChromedriverLogLevels_whenReadingLogs() throws IOException {
        replies.put("POST " + SESSION + "/se/log", Reply.json(200, "{\"value\":["
                + "{\"level\":\"DEBUG\",\"timestamp\":1700000000001,\"message\":\"debug entry\"},"
                + "{\"level\":\"INFO\",\"timestamp\":1700000000002,\"message\":\"info entry\"},"
                + "{\"level\":\"WARNING\",\"timestamp\":1700000000003,\"message\":\"warning entry\"},"
                + "{\"level\":\"SEVERE\",\"timestamp\":1700000000004,\"message\":\"severe entry\"},"
                + "{\"level\":\"ALL\",\"timestamp\":1700000000005,\"message\":\"all entry\"},"
                + "{\"level\":\"OFF\",\"timestamp\":1700000000006,\"message\":\"off entry\"},"
                + "{\"level\":\"VERBOSE\",\"timestamp\":1700000000007,\"message\":\"unknown entry\"}]}"));
        RenderBrowser driver = connect();
        try {
            List<RenderBrowser.LogEntry> entries = driver.logs("browser");

            assertThat(only("POST " + SESSION + "/se/log").json().path("type").asText()).isEqualTo("browser");
            assertThat(entries).extracting(RenderBrowser.LogEntry::getLevel).containsExactly(
                    Level.FINE, Level.INFO, Level.WARNING, Level.SEVERE, Level.ALL, Level.OFF, Level.INFO);
            assertThat(entries.get(0).timestamp()).isEqualTo(1_700_000_000_001L);
            assertThat(entries.get(0).getMessage()).isEqualTo("debug entry");
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should carry the allow-listed W3C error code but never the driver message for a page command")
    void shouldReportAllowListedErrorCode_whenPageCommandFails() throws IOException {
        replies.put("POST " + SESSION + "/url", Reply.json(500, "{\"value\":{\"error\":\"unknown error\","
                + "\"message\":\"unknown error: net::ERR_FAILED at http://127.0.0.1:8080/carlos/x?renderToken=t0k "
                + "SYNTHETIC-CLINICAL-TEXT\",\"stacktrace\":\"#0 0x55 <unknown>\"}}"));
        replies.put("GET " + SESSION + "/url", Reply.json(404, "{\"value\":{\"error\":\"no such window\","
                + "\"message\":\"no such window: target window already closed SYNTHETIC-CLINICAL-TEXT\"}}"));
        RenderBrowser driver = connect();
        try {
            assertThatThrownBy(() -> driver.get("http://127.0.0.1:8080/carlos/x?renderToken=t0k"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Browser command failed (HTTP 500, unknown error)");
            assertThatThrownBy(driver::getCurrentUrl)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Browser command failed (HTTP 404, no such window)")
                    .hasNoCause();
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should report an unrecognized code when the error slot holds anything but a W3C code")
    void shouldReportUnrecognizedCode_whenErrorSlotIsNotW3c() throws IOException {
        replies.put("POST " + SESSION + "/execute/sync", Reply.json(500,
                "{\"value\":{\"error\":\"SYNTHETIC-CLINICAL-TEXT\",\"message\":\"m\"}}"));
        replies.put("POST " + SESSION + "/execute/async", Reply.json(502, "<html>Bad Gateway</html>"));
        RenderBrowser driver = connect();
        try {
            assertThatThrownBy(() -> driver.executeScript("return 1;"))
                    .hasMessage("Browser command failed (HTTP 500, unrecognized)");
            // A non-JSON failure body (something other than chromedriver answered) is still a
            // command failure with its status, not a JSON parse error.
            assertThatThrownBy(() -> driver.executeAsyncScript("return 1;"))
                    .hasMessage("Browser command failed (HTTP 502, unrecognized)");
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should include the redacted one-line driver message when session creation fails")
    void shouldIncludeRedactedDriverMessage_whenSessionCreationFails() throws IOException {
        replies.put("POST /session", Reply.json(500, "{\"value\":{\"error\":\"session not created\","
                + "\"message\":\"session not created: This version of ChromeDriver only supports Chrome version 147\\n"
                + "Current browser version is 153.0.8010.12 with binary path /opt/synthetic/chrome-linux64/chrome; "
                + "service http://127.0.0.1:9515/url-base-secret\",\"stacktrace\":\"#0 0x55 <unknown>\"}}"));
        URI service = URI.create(serverUrl().toString());
        Map<String, Object> capabilities = new BrowserOptions().asMap();

        assertThatThrownBy(() -> new RenderBrowser(service, capabilities))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Browser command failed (HTTP 500, session not created): ")
                .hasMessageContaining("only supports Chrome version 147 Current browser version is 153.0.8010.12")
                .hasMessageContaining("[redacted-path]")
                .hasMessageContaining("[redacted-url]")
                .hasMessageNotContaining("/opt/synthetic")
                .hasMessageNotContaining("url-base-secret")
                .hasMessageNotContaining("\n")
                .hasMessageNotContaining("stacktrace")
                .hasMessageNotContaining("0x55");
        assertThat(keys()).containsExactly("POST /session");
    }

    @Test
    @DisplayName("should truncate a long session-creation driver message")
    void shouldTruncateDriverMessage_whenSessionCreationMessageIsLong() throws IOException {
        replies.put("POST /session", Reply.json(500, "{\"value\":{\"error\":\"session not created\","
                + "\"message\":\"" + "x".repeat(5000) + "\"}}"));
        URI service = URI.create(serverUrl().toString());
        Map<String, Object> capabilities = new BrowserOptions().asMap();

        assertThatThrownBy(() -> new RenderBrowser(service, capabilities))
                .satisfies(failure -> {
                    String detail = failure.getMessage().substring(
                            "Browser command failed (HTTP 500, session not created): ".length());
                    assertThat(detail).hasSize(RenderBrowser.SESSION_DETAIL_LIMIT + "...".length()).endsWith("...");
                });
    }

    @Test
    @DisplayName("should keep surrogate pairs whole when truncating the session-creation detail")
    void shouldNotSplitSurrogatePair_whenTruncatingSessionDetail() {
        String detail = RenderBrowser.sessionCreationDetail(
                "x".repeat(RenderBrowser.SESSION_DETAIL_LIMIT - 1) + "😀tail");

        assertThat(detail).isEqualTo("x".repeat(RenderBrowser.SESSION_DETAIL_LIMIT - 1) + "...");
    }

    @Test
    @DisplayName("should return a successful script result even when it contains an error key")
    void shouldReturnScriptResult_whenSuccessfulReplyHasErrorKey() throws IOException {
        replies.put("POST " + SESSION + "/execute/sync", Reply.json(200,
                "{\"value\":{\"error\":\"x\",\"message\":\"page-defined\"}}"));
        RenderBrowser driver = connect();
        try {
            Object result = driver.executeScript("return {error: 'x', message: 'page-defined'};");

            assertThat(result).isEqualTo(Map.of("error", "x", "message", "page-defined"));
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should never follow a redirect from the driver service")
    void shouldNotFollowRedirect_whenDriverServiceRedirects() throws IOException {
        replies.put("GET " + SESSION + "/url", new Reply(307, "{}",
                Map.of("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/elsewhere")));
        RenderBrowser driver = connect();
        try {
            assertThatThrownBy(driver::getCurrentUrl)
                    .hasMessage("Browser command failed (HTTP 307, unrecognized)");
            assertThat(keys()).noneMatch(key -> key.endsWith("/elsewhere"));
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("should never consult the system proxy selector for the driver or the teardown backstop")
    void shouldBypassSystemProxySelector_forDriverAndBackstop() throws IOException {
        List<URI> consulted = new CopyOnWriteArrayList<>();
        ProxySelector previous = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                consulted.add(uri);
                return List.of(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
                // recording only
            }
        });
        try {
            RenderBrowser driver = connect();
            driver.get("about:blank");
            driver.quit();
            RenderBrowser failingDriver = mock(RenderBrowser.class);
            doThrow(new IllegalStateException("wedged")).when(failingDriver).quit();
            EFormBrowserPdfService.teardownQuietly(new EFormBrowserPdfService.RendererBrowser(
                    failingDriver, URI.create(serverUrl().toString()), SESSION_ID));
        } finally {
            ProxySelector.setDefault(previous);
        }

        assertThat(keys()).contains("POST /session", "POST " + SESSION + "/url");
        assertThat(keys()).filteredOn(key -> key.equals("DELETE " + SESSION)).hasSize(2);
        assertThat(consulted).isEmpty();
    }

    @Test
    @DisplayName("should speak plain HTTP/1.1 without an h2c upgrade offer to the driver and backstop")
    void shouldNotOfferH2cUpgrade_forDriverAndBackstop() throws IOException {
        RenderBrowser driver = connect();
        driver.get("about:blank");
        driver.quit();
        RenderBrowser failingDriver = mock(RenderBrowser.class);
        doThrow(new IllegalStateException("wedged")).when(failingDriver).quit();
        EFormBrowserPdfService.teardownQuietly(new EFormBrowserPdfService.RendererBrowser(
                failingDriver, URI.create(serverUrl().toString()), SESSION_ID));

        assertThat(requests).hasSize(4);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.headers()).doesNotContainKeys("Upgrade", "Http2-settings");
            assertThat(request.headers().getOrDefault("Connection", List.of()))
                    .noneMatch(value -> value.toLowerCase(java.util.Locale.ROOT).contains("upgrade"));
        });
    }

    @Test
    @DisplayName("should still delete the session when quit runs on an interrupted thread")
    void shouldSendDelete_whenQuitRunsOnInterruptedThread() throws IOException {
        RenderBrowser driver = connect();
        // The real sequence: a command fails because the thread was interrupted, which restores
        // the flag, and the render's finally then tears the session down on that same thread.
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> driver.get("about:blank")).hasMessage("Browser command interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        driver.quit();

        assertThat(keys()).contains("DELETE " + SESSION);
        // The caller must still see the interrupt after teardown.
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("should still force-delete the session when quit fails on an interrupted thread")
    void shouldForceDeleteSession_whenQuitFailsOnInterruptedThread() throws IOException {
        RenderBrowser failingDriver = mock(RenderBrowser.class);
        doThrow(new IllegalStateException("Browser command interrupted")).when(failingDriver).quit();
        EFormBrowserPdfService.RendererBrowser browser = new EFormBrowserPdfService.RendererBrowser(
                failingDriver, URI.create(serverUrl().toString()), SESSION_ID);
        Thread.currentThread().interrupt();

        EFormBrowserPdfService.teardownQuietly(browser);

        assertThat(keys()).containsExactly("DELETE " + SESSION);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("should force-delete the session when quit fails")
    void shouldForceDeleteSession_whenQuitFails() throws IOException {
        // teardownQuietly's escalation contract: quitQuietly returning false must trigger a
        // targeted DELETE /session/{id} against the CAPTURED serviceUri — the wedged-browser
        // backstop that replaced the old owned-process kill.
        RenderBrowser failingDriver = mock(RenderBrowser.class);
        doThrow(new IllegalStateException("wedged")).when(failingDriver).quit();
        EFormBrowserPdfService.RendererBrowser browser = new EFormBrowserPdfService.RendererBrowser(
                failingDriver,
                URI.create(serverUrl().toString()),
                SESSION_ID);

        EFormBrowserPdfService.teardownQuietly(browser);

        assertThat(keys()).contains("DELETE " + SESSION);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    @DisplayName("should not force-delete when quit succeeds")
    void shouldNotForceDelete_whenQuitSucceeds() throws IOException {
        // The inverse guard: an inverted escalation condition would force-delete after EVERY
        // successful quit, hammering chromedriver with deletes for sessions already gone.
        RenderBrowser cleanDriver = mock(RenderBrowser.class);
        EFormBrowserPdfService.RendererBrowser browser = new EFormBrowserPdfService.RendererBrowser(
                cleanDriver,
                URI.create(serverUrl().toString()),
                SESSION_ID);

        EFormBrowserPdfService.teardownQuietly(browser);

        assertThat(keys()).noneMatch(r -> r.startsWith("DELETE /session/"));
    }
}
