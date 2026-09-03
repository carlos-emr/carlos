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
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HTTP transport tests for {@link SRFaxProviderClient} against a local mock server.
 *
 * <p>Uses the JDK's {@code com.sun.net.httpserver.HttpServer} bound to 127.0.0.1 on an
 * ephemeral port, and the package-private URL-override constructor so the client talks to the
 * mock instead of the real srfax.com endpoint (the production URL resolver only ever allows
 * srfax.com HTTPS hosts). Exercises the real {@code postForm()} path: form encoding, response
 * parsing, HTTP error handling, malformed-body handling, and transient-failure classification.
 * Only dummy credentials are used.</p>
 *
 * @since 2026-08-21
 * @see SRFaxProviderClient
 */
@Tag("unit")
@Tag("fax")
@Tag("srfax")
@DisplayName("SRFaxProviderClient HTTP transport")
class SRFaxProviderClientHttpTransportUnitTest extends CarlosUnitTestBase {

    private static final String DUMMY_ACCESS_ID = "test-access-id";
    private static final String DUMMY_ACCESS_PWD = "test-access-pwd";

    private HttpServer server;
    private SRFaxProviderClient client;
    private FaxConfig config;

    // Written by the server handler thread, read by the test thread after the client call returns.
    private volatile String recordedContentType;
    private volatile String recordedBody;
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/srfax", exchange -> {
            recordedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            recordedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();

        // Package-private test constructor points getSrfaxApiUrl() at the local mock.
        client = new SRFaxProviderClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/srfax");

        // Mocked FaxConfig bypasses EncryptionUtils in getFaxPasswd(). Dummy credentials only.
        config = mock(FaxConfig.class);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);
        when(config.getFaxUser()).thenReturn(DUMMY_ACCESS_ID);
        when(config.getFaxPasswd()).thenReturn(DUMMY_ACCESS_PWD);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("should parse a success response for fetchFaxStatus")
    void shouldParseSuccessResponse_forFetchFaxStatus() throws Exception {
        // Given - SRFax "Sent" means delivery confirmed -> COMPLETE
        responseBody = "{\"Status\":\"Success\",\"Result\":{\"SentStatus\":\"Sent\"}}";
        FaxJob faxJob = new FaxJob();
        faxJob.setJobId(12345678L);

        // When
        FaxJob updated = client.fetchFaxStatus(config, faxJob);

        // Then
        assertThat(updated.getStatus()).isEqualTo(FaxJob.STATUS.COMPLETE);
        assertThat(updated.getStatusString()).isEqualTo("Sent");
    }

    @Test
    @DisplayName("should send a form-encoded body carrying the auth params")
    void shouldCaptureFormEncodedBody_withAuthParams() throws Exception {
        // Given
        responseBody = "{\"Status\":\"Success\",\"Result\":{\"SentStatus\":\"Sent\"}}";
        FaxJob faxJob = new FaxJob();
        faxJob.setJobId(12345678L);

        // When
        client.fetchFaxStatus(config, faxJob);

        // Then - postForm() sends URL-encoded form data with the standard SRFax auth trio
        assertThat(recordedContentType).contains("application/x-www-form-urlencoded");
        assertThat(recordedBody)
                .contains("access_id=" + DUMMY_ACCESS_ID)
                .contains("access_pwd=" + DUMMY_ACCESS_PWD)
                .contains("sResponseFormat=JSON")
                .contains("sFaxDetailsID=12345678");
    }

    @Test
    @DisplayName("should throw FaxProviderException when the server returns HTTP 500")
    void shouldThrowFaxProviderException_whenServerReturnsHttp500() {
        // Given
        responseStatus = 500;
        responseBody = "internal error";
        FaxJob faxJob = new FaxJob();
        faxJob.setJobId(12345678L);

        // Then
        assertThatThrownBy(() -> client.fetchFaxStatus(config, faxJob))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    @DisplayName("should throw FaxProviderException when the response body is not JSON")
    void shouldThrowFaxProviderException_whenResponseBodyIsNotJson() {
        // Given - HTTP 200 with a non-JSON payload (e.g. an HTML error page). Jackson's
        // readTree() throws JsonProcessingException (an IOException), which postForm() wraps
        // as a non-transient FaxProviderException.
        responseBody = "<html><body>Service temporarily unavailable</body></html>";
        FaxJob faxJob = new FaxJob();
        faxJob.setJobId(12345678L);

        // Then
        assertThatThrownBy(() -> client.fetchFaxStatus(config, faxJob))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("communication failure")
                .hasCauseInstanceOf(IOException.class)
                .satisfies(e -> assertThat(((FaxProviderException) e).isTransient())
                        .as("a parse failure is not a retryable network fault")
                        .isFalse());
    }

    @Test
    @DisplayName("should flag the failure transient when the connection is refused")
    void shouldFlagTransientCause_whenConnectionRefused() {
        // Given - stop the server so the just-freed ephemeral port refuses connections;
        // per FaxProviderException.isTransientNetworkCause(), ConnectException is transient
        server.stop(0);
        FaxJob faxJob = new FaxJob();
        faxJob.setJobId(12345678L);

        // Then
        assertThatThrownBy(() -> client.fetchFaxStatus(config, faxJob))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("communication failure")
                .satisfies(e -> assertThat(((FaxProviderException) e).isTransient())
                        .as("connection refused must be classified transient for retry")
                        .isTrue());
    }

    @Test
    @DisplayName("should send a read-only inbox probe with the credentials for verifyConnection")
    void shouldSendInboxProbe_forVerifyConnection() throws Exception {
        // Given - a success response with an empty inbox is enough to prove the credentials
        responseBody = "{\"Status\":\"Success\",\"Result\":[]}";

        // When
        client.verifyConnection(config);

        // Then - the probe is the same unread-only inbox listing the scheduler uses
        assertThat(recordedBody)
                .contains("action=Get_Fax_Inbox")
                .contains("sViewedStatus=UNREAD")
                .contains("access_id=" + DUMMY_ACCESS_ID)
                .contains("access_pwd=" + DUMMY_ACCESS_PWD)
                .contains("sResponseFormat=JSON");
    }

    @Test
    @DisplayName("should surface the provider failure text when verifyConnection is rejected")
    void shouldThrowWithProviderText_whenVerifyConnectionRejected() {
        // Given - SRFax reports a bad access_id/access_pwd pair as a failed Status
        responseBody = "{\"Status\":\"Failed\",\"Result\":\"Invalid Access Code / Password\"}";

        // Then - fail closed with the provider reason, which carries no credential
        assertThatThrownBy(() -> client.verifyConnection(config))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("rejected the account number or password")
                .hasMessageContaining("Invalid Access Code / Password");
    }

    @Test
    @DisplayName("should explain a bare HTTP 403 as rejected credentials for verifyConnection")
    void shouldExplainForbidden_asRejectedCredentialsForVerifyConnection() {
        // Given - SRFax answers a wrong access_id/access_pwd with a bare 403, not a JSON body
        responseStatus = 403;
        responseBody = "Forbidden";

        // Then - the admin sees what a 403 means here, not just the transport status
        assertThatThrownBy(() -> client.verifyConnection(config))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("rejected the account number or password")
                .hasMessageContaining("HTTP 403")
                .hasMessageContaining("not your login email")
                .satisfies(e -> assertThat(((FaxProviderException) e).isTransient()).isFalse());
    }
}
