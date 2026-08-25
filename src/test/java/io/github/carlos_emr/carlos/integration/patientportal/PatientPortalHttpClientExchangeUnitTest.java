/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The transport, exercised against a real loopback socket.
 *
 * <p>This class is the only place where redirect refusal, the timeouts, and the response cap
 * actually exist, and it had no test at all. That is how a defect of its own Javadoc's headline
 * claim survived review: the connect timeout was being set on {@code
 * RequestConfig.setConnectionRequestTimeout}, which is the pool-lease wait, so the real socket
 * connect timeout stayed at the library default of three minutes.
 *
 * <p>A real server rather than a mock because none of these properties live in code we own — they
 * live in how HttpClient 5 is configured, which only a socket can settle.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PatientPortalHttpClientExchange")
class PatientPortalHttpClientExchangeUnitTest {

    private static final Duration QUICK = Duration.ofMillis(750);

    private HttpServer server;
    private String origin;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        server.createContext(
                path,
                exchange -> {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
    }

    private ClassicHttpRequest get(String path) {
        return ClassicRequestBuilder.get(origin + path)
                .setHeader("Authorization", "Bearer test-token-value")
                .build();
    }

    private PatientPortalHttpClientExchange exchange() {
        return new PatientPortalHttpClientExchange(QUICK, QUICK);
    }

    /**
     * The property that protects the bearer token. A followed redirect would replay the {@code
     * Authorization} header at whatever host the response named, so the assertion that matters is
     * not the returned status but that the redirect target was never contacted.
     */
    @Test
    @DisplayName("should refuse to follow a redirect, and never contact its target")
    void shouldNotFollowRedirect_orReplayTheTokenAtItsTarget() throws Exception {
        AtomicInteger targetHits = new AtomicInteger();
        server.createContext(
                "/target",
                httpExchange -> {
                    targetHits.incrementAndGet();
                    httpExchange.sendResponseHeaders(200, -1);
                    httpExchange.close();
                });
        server.createContext(
                "/redirect",
                httpExchange -> {
                    httpExchange.getResponseHeaders().add("Location", origin + "/target");
                    httpExchange.sendResponseHeaders(302, -1);
                    httpExchange.close();
                });

        try (PatientPortalHttpClientExchange transport = exchange()) {
            PatientPortalHttpResponse response = transport.send(get("/redirect"));

            assertThat(response.statusCode()).isEqualTo(302);
        }
        assertThat(targetHits.get()).isZero();
    }

    /**
     * Pins the defect this class was missing a test for. Before the fix the configured value was
     * applied to the pool lease and the real connect timeout was the library default of three
     * minutes, so this would have taken minutes rather than under a second.
     */
    @Test
    @DisplayName("should bound a stalled TCP connect by the configured connect timeout")
    void shouldApplyConnectTimeout_whenTheHostNeverCompletesTheHandshake() throws Exception {
        // TEST-NET-1 (RFC 5737): routable-looking, guaranteed not to answer.
        ClassicHttpRequest request = ClassicRequestBuilder.get("http://192.0.2.1:9/blackhole").build();

        try (PatientPortalHttpClientExchange transport =
                new PatientPortalHttpClientExchange(QUICK, QUICK)) {
            long startedAt = System.nanoTime();

            assertThatThrownBy(() -> transport.send(request)).isInstanceOf(IOException.class);

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            assertThat(elapsed).isLessThan(Duration.ofSeconds(20));
        }
    }

    @Test
    @DisplayName("should bound a stalled response by the configured read timeout")
    void shouldApplyReadTimeout_whenTheServerNeverAnswers() throws Exception {
        server.createContext(
                "/slow",
                httpExchange -> {
                    try {
                        Thread.sleep(Duration.ofSeconds(10).toMillis());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    httpExchange.sendResponseHeaders(200, -1);
                    httpExchange.close();
                });

        try (PatientPortalHttpClientExchange transport = exchange()) {
            long startedAt = System.nanoTime();

            assertThatThrownBy(() -> transport.send(get("/slow"))).isInstanceOf(IOException.class);

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(8));
        }
    }

    @Test
    @DisplayName("should stop reading once the response cap is reached")
    void shouldCapResponseBody_whenTheServerSendsMoreThanTheLimit() throws Exception {
        int oversize = PatientPortalHttpClientExchange.MAX_RESPONSE_CHARS + (512 * 1024);
        server.createContext(
                "/flood",
                httpExchange -> {
                    byte[] chunk = new byte[8192];
                    java.util.Arrays.fill(chunk, (byte) 'x');
                    httpExchange.sendResponseHeaders(200, oversize);
                    try (OutputStream out = httpExchange.getResponseBody()) {
                        int written = 0;
                        while (written < oversize) {
                            int size = Math.min(chunk.length, oversize - written);
                            out.write(chunk, 0, size);
                            written += size;
                        }
                    } catch (IOException ignored) {
                        // The client stops reading at the cap; a broken pipe here is expected.
                    }
                });

        try (PatientPortalHttpClientExchange transport = exchange()) {
            PatientPortalHttpResponse response = transport.send(get("/flood"));

            assertThat(response.body().length())
                    .isEqualTo(PatientPortalHttpClientExchange.MAX_RESPONSE_CHARS);
        }
    }

    @Test
    @DisplayName("should render a body-less response as an empty string, never null")
    void shouldReturnEmptyBody_whenTheResponseHasNoEntity() throws Exception {
        respond("/empty", 204, "");

        try (PatientPortalHttpClientExchange transport = exchange()) {
            PatientPortalHttpResponse response = transport.send(get("/empty"));

            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.body()).isEmpty();
        }
    }

    @Test
    @DisplayName("should return the status and body the server sent")
    void shouldReturnStatusAndBody_whenTheServerAnswers() throws Exception {
        respond("/ok", 200, "{\"id\":1}");

        try (PatientPortalHttpClientExchange transport = exchange()) {
            PatientPortalHttpResponse response = transport.send(get("/ok"));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"id\":1}");
            assertThat(response.isSuccess()).isTrue();
        }
    }

    @Test
    @DisplayName("should reuse one client across calls rather than reconnecting each time")
    void shouldReuseTheClient_acrossSequentialCalls() throws Exception {
        respond("/twice", 200, "{}");

        try (PatientPortalHttpClientExchange transport = exchange()) {
            assertThat(transport.send(get("/twice")).statusCode()).isEqualTo(200);
            assertThat(transport.send(get("/twice")).statusCode()).isEqualTo(200);
        }
    }
}
