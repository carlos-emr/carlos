/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.carlos.eform.util.RenderBrowser.LogEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** Exercises the JDK transport and session teardown against a local W3C protocol fixture. */
@DisplayName("JDK render browser against a fake chromedriver")
@Tag("unit")
@Tag("eform")
class EFormBrowserRemoteDriverFakeChromedriverUnitTest {

    private static final String SESSION_ID = "fake-session-0123456789abcdef";

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    /** Response the fake driver returns for the CDP execute command. */
    private volatile String cdpResponseJson =
            "{\"value\":{\"data\":\"ZmFrZS1wZGY=\"}}";
    private volatile int deleteSessionStatus = 200;

    @BeforeEach
    void startFakeChromedriver() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            requests.add(key);
            exchange.getRequestBody().readAllBytes();
            String body;
            if (key.equals("POST /session")) {
                // A real W3C new-session reply, without any Selenium-specific capabilities.
                body = "{\"value\":{\"sessionId\":\"" + SESSION_ID + "\","
                        + "\"capabilities\":{\"browserName\":\"chrome\",\"browserVersion\":\"120\","
                        + "\"goog:chromeOptions\":{\"debuggerAddress\":\"127.0.0.1:1\"}}}}";
            } else if (key.startsWith("POST /session/" + SESSION_ID + "/goog/cdp/execute")) {
                body = cdpResponseJson;
            } else if (key.startsWith("DELETE /session/" + SESSION_ID)) {
                respond(exchange, deleteSessionStatus, "{\"value\":null}");
                return;
            } else {
                // Anything else the client sends during construction/quit gets a null success.
                body = "{\"value\":null}";
            }
            respond(exchange, 200, body);
        });
        server.start();
    }

    @AfterEach
    void stopFakeChromedriver() {
        if (server != null) {
            server.stop(0);
        }
        requests.clear();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
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
            assertThat(requests)
                    .anyMatch(r -> r.startsWith("POST /session/" + SESSION_ID + "/goog/cdp/execute"));
        } finally {
            driver.quit();
        }
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

        assertThat(requests).contains("DELETE /session/" + SESSION_ID);
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

        assertThat(requests).noneMatch(r -> r.startsWith("DELETE /session/"));
    }
}
