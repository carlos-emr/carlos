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
package io.github.carlos_emr.carlos.fax.ringcentral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HTTP transport tests for {@link RingCentralApiConnector} driven against a local fixture server.
 *
 * <p>Pins the connector's behaviour for: 200/JSON, 200/malformed-JSON, 401, 503, empty 204, and
 * multi-page inbox pagination via the {@code navigation.nextPage} cursor. Uses
 * {@link com.sun.net.httpserver.HttpServer} so tests stay self-contained without a WireMock
 * dependency.</p>
 *
 * @since 2026-05-07
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("RingCentralApiConnector HTTP Transport Tests")
class RingCentralApiConnectorHttpTest extends CarlosUnitTestBase {

    private HttpServer server;
    private String baseUrl;
    private RingCentralApiConnector connector;
    private final Deque<QueuedResponse> queuedResponses = new ArrayDeque<>();
    private final AtomicReference<HttpExchange> lastExchange = new AtomicReference<>();
    private final AtomicReference<byte[]> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void startFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new QueueingHandler());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        connector = new RingCentralApiConnector(() -> baseUrl);
    }

    @AfterEach
    void stopFixture() throws IOException {
        connector.close();
        server.stop(0);
        queuedResponses.clear();
    }

    @Test
    @DisplayName("should return parsed Token when authenticate receives 200 JSON")
    void shouldReturnParsedToken_whenAuthenticateReceives200Json() throws Exception {
        enqueue(200, "application/json",
                "{\"access_token\":\"abc-123\",\"expires_in\":7200}");

        RingCentralResponse.Token token = connector.authenticate("client", "secret", "jwt");

        assertThat(token.accessToken()).isEqualTo("abc-123");
        assertThat(token.expiresIn()).isEqualTo(7200);
    }

    @Test
    @DisplayName("should throw RingCentralException when authenticate receives 200 with malformed JSON")
    void shouldThrowRingCentralException_whenResponseJsonIsMalformed() {
        enqueue(200, "application/json", "{not-json");

        assertThatThrownBy(() -> connector.authenticate("client", "secret", "jwt"))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("invalid JSON response");
    }

    @Test
    @DisplayName("should throw RingCentralException with HTTP status when server returns 401")
    void shouldThrowRingCentralException_whenServerReturns401() {
        enqueue(401, "application/json", "{\"errorCode\":\"InvalidToken\"}");

        assertThatThrownBy(() -> connector.authenticate("client", "secret", "jwt"))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    @DisplayName("should throw RingCentralException with HTTP status when server returns 502")
    void shouldThrowRingCentralException_whenServerReturns502() {
        // 502 BadGateway is NOT in HttpClient5's default retryable-status list (429/503),
        // so a single enqueue exhausts the request without an automatic retry. This isolates
        // the connector's status-code mapping from HttpClient5's retry strategy.
        enqueue(502, "application/json", "{\"errorCode\":\"BadGateway\"}");

        assertThatThrownBy(() -> connector.authenticate("client", "secret", "jwt"))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("HTTP 502");
    }

    @Test
    @DisplayName("should surface 503 after HttpClient retries are exhausted")
    void shouldSurface503_afterRetriesAreExhausted() {
        // HttpClient5's default DefaultHttpRequestRetryStrategy retries 503 once. Enqueue twice
        // so both attempts see the same status and the final error reaches the caller.
        enqueue(503, "application/json", "{\"errorCode\":\"ServiceUnavailable\"}");
        enqueue(503, "application/json", "{\"errorCode\":\"ServiceUnavailable\"}");

        assertThatThrownBy(() -> connector.authenticate("client", "secret", "jwt"))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    @DisplayName("should accept empty body when markFaxAsRead receives 204")
    void shouldAcceptEmptyBody_whenMarkAsReadReceives204() throws Exception {
        enqueue(204, null, null);

        connector.markFaxAsRead(new RingCentralAccount("token", "~", "~"), "12345");

        assertThat(lastExchange.get().getRequestMethod()).isEqualTo("PUT");
        assertThat(lastExchange.get().getRequestURI().getPath())
                .endsWith("/message-store/12345");
    }

    @Test
    @DisplayName("should send multipart payload when sendFax is called")
    void shouldSendMultipartPayload_whenSendFaxIsCalled() throws Exception {
        enqueue(200, "application/json", "{\"id\":\"99\",\"messageStatus\":\"Queued\"}");

        RingCentralResponse.Message response = connector.sendFax(
                new RingCentralAccount("token", "~", "~"),
                "4165551234", "fake-pdf-bytes".getBytes(StandardCharsets.UTF_8), "outgoing.pdf");

        assertThat(response.getId()).isEqualTo("99");
        HttpExchange exchange = lastExchange.get();
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        String contentType = exchange.getRequestHeaders().getFirst("Content-type");
        assertThat(contentType).startsWith("multipart/form-data");
        String body = new String(lastRequestBody.get(), StandardCharsets.UTF_8);
        assertThat(body).contains("4165551234");
        assertThat(body).contains("outgoing.pdf");
        assertThat(body).contains("application/pdf");
    }

    @Test
    @DisplayName("should follow nextPage cursor and aggregate inbox records")
    void shouldFollowNextPageCursor_andAggregateInboxRecords() throws Exception {
        // Page one: a single record + cursor pointing to page two.
        enqueue(200, "application/json",
                "{\"records\":[{\"id\":\"100\",\"attachments\":[{\"id\":\"a1\"}]}],"
                + "\"navigation\":{\"nextPage\":{\"uri\":\"" + baseUrl
                + "/restapi/v1.0/account/~/extension/~/message-store?page=2\"}}}");
        // Page two: a single record, no cursor.
        enqueue(200, "application/json",
                "{\"records\":[{\"id\":\"200\",\"attachments\":[{\"id\":\"a2\"}]}]}");

        RingCentralResponse.MessageList list = connector.getInboundFaxes(new RingCentralAccount("token", "~", "~"));

        assertThat(list.getRecords()).hasSize(2);
        assertThat(list.getRecords().get(0).getId()).isEqualTo("100");
        assertThat(list.getRecords().get(1).getId()).isEqualTo("200");
    }

    @Test
    @DisplayName("should terminate inbox pagination at MAX_INBOX_PAGES when cursor self-loops")
    void shouldTerminateInboxPagination_whenCursorSelfLoops() throws Exception {
        // Pin the safety bound documented on RingCentralApiConnector.MAX_INBOX_PAGES (50).
        // A regression that removed the cap (or set it to Integer.MAX_VALUE) would let a
        // self-loop nextPage cursor hang the scheduler thread until the connection pool
        // exhausts. Queue 51 responses and assert the loop terminates before consuming the
        // 51st — the unconsumed response stays in the queue for tearDown to clear.
        String selfLoopBody = "{\"records\":[{\"id\":\"loop-id\",\"attachments\":[{\"id\":\"a\"}]}],"
                + "\"navigation\":{\"nextPage\":{\"uri\":\"" + baseUrl
                + "/restapi/v1.0/account/~/extension/~/message-store?page=loop\"}}}";
        for (int i = 0; i < 51; i++) {
            enqueue(200, "application/json", selfLoopBody);
        }

        RingCentralResponse.MessageList list = connector.getInboundFaxes(new RingCentralAccount("token", "~", "~"));

        assertThat(list.getRecords())
                .as("MAX_INBOX_PAGES caps aggregation at 50 pages * 1 record/page")
                .hasSize(50);
        assertThat(queuedResponses)
                .as("loop must not consume the 51st queued response")
                .hasSize(1);
    }

    @Test
    @DisplayName("should refuse to follow nextPage cursor pointing to a foreign host")
    void shouldRefuseNextPageCursor_pointingToForeignHost() throws Exception {
        enqueue(200, "application/json",
                "{\"records\":[{\"id\":\"100\",\"attachments\":[{\"id\":\"a1\"}]}],"
                + "\"navigation\":{\"nextPage\":{\"uri\":\"http://attacker.example.com/page2\"}}}");

        RingCentralResponse.MessageList list = connector.getInboundFaxes(new RingCentralAccount("token", "~", "~"));

        assertThat(list.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("should round-trip PDF bytes when downloadFax receives 200")
    void shouldRoundTripPdfBytes_whenDownloadFaxReceives200() throws Exception {
        // Use ASCII-only fixture so UTF-8 encoding in the handler is byte-identical to the
        // expected payload. Real PDFs may contain high bytes but this test only pins the
        // byte-exact round-trip behaviour of executeBytes.
        String pdfFixture = "%PDF-1.4\nfake fixture content\n%%EOF";
        enqueue(200, "application/pdf", pdfFixture);

        byte[] downloaded = connector.downloadFax(new RingCentralAccount("token", "~", "~"), "12345", "att-9");

        assertThat(downloaded).isEqualTo(pdfFixture.getBytes(StandardCharsets.UTF_8));
        assertThat(lastExchange.get().getRequestMethod()).isEqualTo("GET");
        assertThat(lastExchange.get().getRequestURI().getPath())
                .endsWith("/message-store/12345/content/att-9");
    }

    @Test
    @DisplayName("should send Bearer Authorization header on inbox fetch")
    void shouldSendBearerAuthorization_onInboxFetch() throws Exception {
        enqueue(200, "application/json", "{\"records\":[]}");

        connector.getInboundFaxes(new RingCentralAccount("my-access-token", "~", "~"));

        assertThat(lastExchange.get().getRequestHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer my-access-token");
    }

    @Test
    @DisplayName("should send Basic Authorization header on authenticate")
    void shouldSendBasicAuthorization_onAuthenticate() throws Exception {
        enqueue(200, "application/json", "{\"access_token\":\"x\",\"expires_in\":1}");

        connector.authenticate("client-id", "client-secret", "jwt");

        String authorization = lastExchange.get().getRequestHeaders().getFirst("Authorization");
        assertThat(authorization).startsWith("Basic ");
        String decoded = new String(java.util.Base64.getDecoder()
                .decode(authorization.substring("Basic ".length())), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("client-id:client-secret");
    }

    @Test
    @DisplayName("should send JWT bearer grant body when authenticate is called")
    void shouldSendJwtBearerGrantBody_onAuthenticate() throws Exception {
        // Pin the OAuth grant type and assertion field. A regression that swapped the grant
        // type to "password" (or dropped the assertion entirely) would still pass header-only
        // assertions, so decode the form body and check both parameters explicitly.
        enqueue(200, "application/json", "{\"access_token\":\"x\",\"expires_in\":1}");

        connector.authenticate("client-id", "client-secret", "the-jwt-token");

        String body = new String(lastRequestBody.get(), StandardCharsets.UTF_8);
        assertThat(body)
                .as("authenticate must request the JWT bearer grant per RingCentral OAuth spec")
                .contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer");
        assertThat(body)
                .as("authenticate must carry the configured JWT as the assertion parameter")
                .contains("assertion=the-jwt-token");
        // Negative pin: ensure no foreign grant type slipped in (e.g., password / client_credentials).
        assertThat(body).doesNotContain("grant_type=password");
        assertThat(body).doesNotContain("grant_type=client_credentials");
    }

    @Test
    @DisplayName("should classify 503 as transient and carry HTTP status on the exception")
    void shouldClassify503AsTransient_andCarryHttpStatus() {
        // Two enqueues so the HttpClient5 default retry doesn't run out the queue.
        enqueue(503, "application/json", "{\"errorCode\":\"ServiceUnavailable\"}");
        enqueue(503, "application/json", "{\"errorCode\":\"ServiceUnavailable\"}");

        try {
            connector.authenticate("client", "secret", "jwt");
            org.assertj.core.api.Assertions.fail("Expected RingCentralException");
        } catch (RingCentralException e) {
            assertThat(e.getHttpStatus()).isEqualTo(503);
            assertThat(e.isTransient()).isTrue();
            assertThat(e.getMessage()).contains("ServiceUnavailable");
        }
    }

    @Test
    @DisplayName("should classify 400 as non-transient and include vendor body excerpt in message")
    void shouldClassify400AsNonTransient_andIncludeBodyExcerpt() {
        enqueue(400, "application/json",
                "{\"errorCode\":\"PhoneNumberNotAuthorized\",\"message\":\"Phone number not authorized\"}");

        try {
            connector.authenticate("client", "secret", "jwt");
            org.assertj.core.api.Assertions.fail("Expected RingCentralException");
        } catch (RingCentralException e) {
            assertThat(e.getHttpStatus()).isEqualTo(400);
            assertThat(e.isTransient()).isFalse();
            assertThat(e.getMessage()).contains("PhoneNumberNotAuthorized");
            assertThat(e.getMessage()).contains("Phone number not authorized");
        }
    }

    @Test
    @DisplayName("should cap error response body excerpt at 1024 bytes")
    void shouldCapBodyExcerpt_at1024Bytes() {
        // Build a 4KB body so the cap actually fires. The connector should keep the message
        // bounded so a misbehaving vendor or proxy can't blow up operator logs / error UIs.
        StringBuilder oversized = new StringBuilder(4096);
        for (int i = 0; i < 4096; i++) {
            oversized.append('A');
        }
        enqueue(400, "application/json", oversized.toString());

        try {
            connector.authenticate("client", "secret", "jwt");
            org.assertj.core.api.Assertions.fail("Expected RingCentralException");
        } catch (RingCentralException e) {
            // Header ("...with HTTP 400: ") plus 1024 body chars; full 4KB must NOT appear.
            assertThat(e.getMessage()).contains("HTTP 400");
            assertThat(e.getMessage().length()).isLessThan(2048);
            // Spot-check: the truncated portion (4096-th char) is absent; the cap kept us short.
            assertThat(e.getMessage()).doesNotContain(oversized.substring(1500));
        }
    }

    @Test
    @DisplayName("should classify 429 as transient")
    void shouldClassify429AsTransient_carryingHttpStatus() {
        // HttpClient5's default retry strategy retries 429 once. Enqueue twice so both attempts
        // see the same status and the final classification reaches the caller.
        enqueue(429, "application/json", "{\"errorCode\":\"TooManyRequests\"}");
        enqueue(429, "application/json", "{\"errorCode\":\"TooManyRequests\"}");

        try {
            connector.authenticate("client", "secret", "jwt");
            org.assertj.core.api.Assertions.fail("Expected RingCentralException");
        } catch (RingCentralException e) {
            assertThat(e.getHttpStatus()).isEqualTo(429);
            assertThat(e.isTransient()).isTrue();
        }
    }

    private void enqueue(int statusCode, String contentType, String body) {
        queuedResponses.add(new QueuedResponse(statusCode, contentType, body));
    }

    private final class QueueingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                lastRequestBody.set(is.readAllBytes());
            }
            lastExchange.set(exchange);
            QueuedResponse response = queuedResponses.poll();
            if (response == null) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            byte[] payload = response.body == null ? new byte[0] : response.body.getBytes(StandardCharsets.UTF_8);
            if (response.contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", response.contentType);
            }
            if (payload.length == 0) {
                exchange.sendResponseHeaders(response.statusCode, -1);
            } else {
                exchange.sendResponseHeaders(response.statusCode, payload.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(payload);
                }
            }
        }
    }

    private static final class QueuedResponse {
        final int statusCode;
        final String contentType;
        final String body;

        QueuedResponse(int statusCode, String contentType, String body) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.body = body;
        }
    }
}
