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
import java.util.concurrent.atomic.AtomicReference;
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

    /**
     * For tests about behaviour, not timing. Their timeouts are generous so a loaded build host
     * cannot turn a slow first request into a false failure.
     */
    private PatientPortalHttpClientExchange exchange() {
        return new PatientPortalHttpClientExchange(
                Duration.ofSeconds(5), Duration.ofSeconds(10), java.util.Set.of(PortalTestKeys.UNUSED_TLS_PIN));
    }

    /** For tests that assert a timeout fires; {@link #QUICK} keeps them short. */
    private PatientPortalHttpClientExchange quickExchange() {
        return new PatientPortalHttpClientExchange(QUICK, QUICK, java.util.Set.of(PortalTestKeys.UNUSED_TLS_PIN));
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

    @Test
    @DisplayName("should not carry a portal cookie into a later staff request")
    void shouldNotRetainCookies_betweenRequests() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> laterCookie = new AtomicReference<>();
        server.createContext(
                "/cookie",
                httpExchange -> {
                    if (requestCount.getAndIncrement() == 0) {
                        httpExchange
                                .getResponseHeaders()
                                .add("Set-Cookie", "portal-session=synthetic; Path=/");
                    } else {
                        laterCookie.set(httpExchange.getRequestHeaders().getFirst("Cookie"));
                    }
                    httpExchange.sendResponseHeaders(200, -1);
                    httpExchange.close();
                });

        try (PatientPortalHttpClientExchange transport = exchange()) {
            assertThat(transport.send(get("/cookie")).statusCode()).isEqualTo(200);
            assertThat(transport.send(get("/cookie")).statusCode()).isEqualTo(200);
        }

        assertThat(laterCookie.get()).isNull();
    }

    /** Network smoke test only: routing may refuse TEST-NET before a connect timeout elapses. */
    @Test
    @DisplayName("should fail promptly for an unreachable endpoint")
    void shouldFailPromptly_whenTheEndpointIsUnreachable() throws Exception {
        ClassicHttpRequest request = ClassicRequestBuilder.get("http://192.0.2.1:9/blackhole").build();

        try (PatientPortalHttpClientExchange transport =
                new PatientPortalHttpClientExchange(QUICK, QUICK, java.util.Set.of(PortalTestKeys.UNUSED_TLS_PIN))) {
            long startedAt = System.nanoTime();

            assertThatThrownBy(() -> transport.send(request)).isInstanceOf(IOException.class);

            // A small multiple of the 750 ms connect timeout: a regression to the multi-minute
            // default fails here, and fails quickly.
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        }
    }

    @Test
    @DisplayName("should bound a stalled response by the configured read timeout")
    void shouldApplyReadTimeout_whenTheServerNeverAnswers() throws Exception {
        server.createContext(
                "/slow",
                httpExchange -> {
                    try {
                        // Well past the 750 ms read timeout, but short enough that this handler
                        // thread, which server.stop(0) does not cancel, is gone soon after.
                        Thread.sleep(Duration.ofSeconds(3).toMillis());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    httpExchange.sendResponseHeaders(200, -1);
                    httpExchange.close();
                });

        try (PatientPortalHttpClientExchange transport = quickExchange()) {
            long startedAt = System.nanoTime();

            assertThatThrownBy(() -> transport.send(get("/slow"))).isInstanceOf(IOException.class);

            // Under the handler's 3 s sleep, so only the read timeout can have ended the call.
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofMillis(2500));
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
            assertThatThrownBy(() -> transport.send(get("/flood")))
                    .isInstanceOf(PortalResponseTooLargeException.class);
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
        java.util.Set<Integer> ports = java.util.concurrent.ConcurrentHashMap.newKeySet();
        server.createContext("/twice", connection -> {
            ports.add(connection.getRemoteAddress().getPort());
            byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            connection.sendResponseHeaders(200, body.length);
            try (var output = connection.getResponseBody()) { output.write(body); }
        });

        try (PatientPortalHttpClientExchange transport = exchange()) {
            assertThat(transport.send(get("/twice")).statusCode()).isEqualTo(200);
            assertThat(transport.send(get("/twice")).statusCode()).isEqualTo(200);
            assertThat(ports).hasSize(1);
        }
    }
    @Test
    @DisplayName("should refuse a call beyond the concurrency limit without sending it")
    void shouldRefuseWithoutSending_whenEveryTransportSlotIsBusy() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();
        // Its own server: holding several requests open at once needs a handler thread pool.
        HttpServer holding = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var handlers = java.util.concurrent.Executors.newCachedThreadPool();
        holding.setExecutor(handlers);
        holding.start();
        String holdingOrigin = "http://127.0.0.1:" + holding.getAddress().getPort();
        holding.createContext("/held", exchange -> {
            received.incrementAndGet();
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        int slots = PatientPortalHttpClientExchange.MAX_CONCURRENT_REQUESTS;
        var callers = java.util.concurrent.Executors.newFixedThreadPool(slots);
        try (PatientPortalHttpClientExchange transport = new PatientPortalHttpClientExchange(
                Duration.ofSeconds(10), Duration.ofSeconds(10), java.util.Set.of(PortalTestKeys.UNUSED_TLS_PIN))) {
            for (int slot = 0; slot < slots; slot++) {
                callers.submit(() -> transport.send(get(holdingOrigin, "/held")));
            }
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (received.get() < slots && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(received.get()).isEqualTo(slots);

            assertThatThrownBy(() -> transport.send(get(holdingOrigin, "/held")))
                    .isInstanceOf(PortalRequestNotSentException.class);
            assertThat(received.get()).isEqualTo(slots);
        } finally {
            release.countDown();
            callers.shutdownNow();
            holding.stop(0);
            handlers.shutdownNow();
        }
    }

    /**
     * The portal (uvicorn, or a proxy in front of it) closes a kept-alive connection once it has
     * been idle briefly. Retries are disabled on purpose, so reusing that closed connection would
     * fail the next call outright: one email or invite refused for a reason nobody can act on.
     */
    @Test
    @DisplayName("should not reuse a kept-alive connection the portal has since closed")
    void shouldSucceed_whenThePortalClosedTheIdleConnection() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        try (java.net.ServerSocket idleClosing = new java.net.ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread acceptor = new Thread(() -> {
                while (!idleClosing.isClosed()) {
                    try {
                        java.net.Socket socket = idleClosing.accept();
                        connections.incrementAndGet();
                        new Thread(() -> answerOnceThenCloseWhenIdle(socket)).start();
                    } catch (IOException closed) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            String idleOrigin = "http://127.0.0.1:" + idleClosing.getLocalPort();

            try (PatientPortalHttpClientExchange transport = exchange()) {
                assertThat(transport.send(get(idleOrigin, "/first")).statusCode()).isEqualTo(200);
                Thread.sleep(400); // the server has closed the pooled connection by now
                assertThat(transport.send(get(idleOrigin, "/second")).statusCode()).isEqualTo(200);
            }
        }
        assertThat(connections.get()).isEqualTo(2);
    }

    /** Answers one request with a kept-alive response, then closes the socket after 100 ms idle. */
    private static void answerOnceThenCloseWhenIdle(java.net.Socket socket) {
        try (socket) {
            java.io.InputStream in = socket.getInputStream();
            int matched = 0;
            byte[] end = {'\r', '\n', '\r', '\n'};
            while (matched < end.length) {
                int next = in.read();
                if (next < 0) return;
                matched = next == end[matched] ? matched + 1 : (next == end[0] ? 1 : 0);
            }
            OutputStream out = socket.getOutputStream();
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}"
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Thread.sleep(100);
        } catch (IOException | InterruptedException ignored) {
            // The socket closes either way, which is the behaviour under test.
        }
    }

    private static ClassicHttpRequest get(String base, String path) {
        return ClassicRequestBuilder.get(base + path)
                .setHeader("Authorization", "Bearer test-token-value")
                .build();
    }

    @Test
    void shouldAcceptResponse_whenExactlyAtLimit() throws Exception {
        String body = "x".repeat(PatientPortalHttpClientExchange.MAX_RESPONSE_CHARS);
        respond("/exact", 200, body);
        // This checks body size, not timing. Allow scheduling delays on a loaded build host.
        try (PatientPortalHttpClientExchange transport = new PatientPortalHttpClientExchange(
                Duration.ofSeconds(5), Duration.ofSeconds(10), java.util.Set.of(PortalTestKeys.UNUSED_TLS_PIN))) {
            assertThat(transport.send(get("/exact")).body()).isEqualTo(body);
        }
    }
}
