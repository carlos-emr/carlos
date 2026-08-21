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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.hc.core5.http.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for SRFax API request construction in {@link SRFaxProviderClient}.
 *
 * <p>Uses a test subclass that overrides the {@code protected postForm(String, List)} seam to
 * capture the endpoint and form parameters each operation builds, and to return canned JSON
 * responses. No sockets are opened; only dummy credentials are used.</p>
 *
 * <p>Pins the SRFax API contract per operation: {@code Queue_Fax} with normalized caller ID /
 * destination numbers, unread-only {@code Get_Fax_Inbox} polling, {@code Retrieve_Fax} download
 * without a mark-as-viewed side effect, {@code Update_Viewed_Status} for mark-as-read, and
 * {@code Stop_Fax} cancellation semantics (including the already-completed outcome).</p>
 *
 * @since 2026-08-21
 * @see SRFaxProviderClient
 */
@Tag("unit")
@Tag("fax")
@Tag("srfax")
@DisplayName("SRFaxProviderClient request construction")
class SRFaxProviderClientRequestConstructionUnitTest extends CarlosUnitTestBase {

    private static final String DUMMY_ACCESS_ID = "test-access-id";
    private static final String DUMMY_ACCESS_PWD = "test-access-pwd";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CapturingSRFaxProviderClient client;
    private FaxConfig config;

    /**
     * Test subclass that intercepts the postForm() transport seam, recording the request that
     * production code built and returning a canned parsed response instead of doing HTTP.
     */
    private static final class CapturingSRFaxProviderClient extends SRFaxProviderClient {
        private String capturedEndpoint;
        private List<NameValuePair> capturedParams;
        private JsonNode cannedResponse;

        @Override
        protected JsonNode postForm(String endpoint, List<NameValuePair> params) {
            this.capturedEndpoint = endpoint;
            this.capturedParams = params;
            return cannedResponse;
        }
    }

    @BeforeEach
    void setUp() {
        client = new CapturingSRFaxProviderClient();

        // Mocked FaxConfig bypasses the EncryptionUtils dependency in getFaxPasswd()
        // (setFaxPasswd() encrypts; the getter decrypts). Dummy credentials only.
        config = mock(FaxConfig.class);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);
        when(config.getFaxUser()).thenReturn(DUMMY_ACCESS_ID);
        when(config.getFaxPasswd()).thenReturn(DUMMY_ACCESS_PWD);
        when(config.getFaxNumber()).thenReturn("1(905)555-1234");
        when(config.getSenderEmail()).thenReturn("fax-test@example.com");
    }

    private void respondWith(String json) throws Exception {
        client.cannedResponse = objectMapper.readTree(json);
    }

    /** Returns the first captured form parameter value for the given name, or null. */
    private String param(String name) {
        assertThat(client.capturedParams).as("captured form parameters").isNotNull();
        return client.capturedParams.stream()
                .filter(p -> p.getName().equals(name))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean hasParam(String name) {
        return client.capturedParams != null
                && client.capturedParams.stream().anyMatch(p -> p.getName().equals(name));
    }

    // --- Queue_Fax (sendFax) ---

    @Test
    @DisplayName("should send Queue_Fax params with normalized caller ID and destination")
    void shouldSendQueueFaxParams_withNormalizedNumbers(@TempDir Path tempDir) throws Exception {
        // Given
        Path document = tempDir.resolve("test-fax.pdf");
        byte[] documentBytes = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
        Files.write(document, documentBytes);

        FaxJob faxJob = new FaxJob();
        faxJob.setDestination("905-555-9999");
        faxJob.setFile_name("test-fax.pdf");

        respondWith("{\"Status\":\"Success\",\"Result\":\"12345678\"}");

        // When
        FaxJob result = client.sendFax(config, faxJob, document);

        // Then - request contract
        assertThat(param("action")).isEqualTo("Queue_Fax");
        assertThat(param("access_id")).isEqualTo(DUMMY_ACCESS_ID);
        assertThat(param("access_pwd")).isEqualTo(DUMMY_ACCESS_PWD);
        assertThat(param("sResponseFormat")).isEqualTo("JSON");
        assertThat(param("sFaxType")).isEqualTo("SINGLE");
        // Config number "1(905)555-1234" normalizes to a 10-digit caller ID
        assertThat(param("sCallerID")).isEqualTo("9055551234");
        // Destination "905-555-9999" normalizes to an 11-digit dialable number
        assertThat(param("sToFaxNumber")).isEqualTo("19055559999");
        assertThat(param("sFileName_1")).isEqualTo("test-fax.pdf");
        assertThat(param("sFileContent_1"))
                .isEqualTo(Base64.getEncoder().encodeToString(documentBytes));

        // And the provider job id from Result is carried into the returned job
        assertThat(result.getJobId()).isEqualTo(12345678L);
        assertThat(result.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
    }

    @Test
    @DisplayName("should throw FaxProviderException when Queue_Fax Result is non-numeric")
    void shouldThrowFaxProviderException_whenQueueFaxResultIsNonNumeric(@TempDir Path tempDir) throws Exception {
        // Given - SRFax claims Success but the job id cannot be parsed; the job would be
        // untrackable, so the client must fail (non-transient) instead of reporting SENT
        Path document = tempDir.resolve("doc.pdf");
        Files.write(document, "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        FaxJob faxJob = new FaxJob();
        faxJob.setDestination("905-555-9999");
        faxJob.setFile_name("doc.pdf");

        respondWith("{\"Status\":\"Success\",\"Result\":\"abc\"}");

        // Then
        assertThatThrownBy(() -> client.sendFax(config, faxJob, document))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("non-numeric");
    }

    @Test
    @DisplayName("should throw FaxProviderException when Queue_Fax Result is missing")
    void shouldThrowFaxProviderException_whenQueueFaxResultMissing(@TempDir Path tempDir) throws Exception {
        // Given - Success status but no Result: no job id was returned at all
        Path document = tempDir.resolve("doc.pdf");
        Files.write(document, "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        FaxJob faxJob = new FaxJob();
        faxJob.setDestination("905-555-9999");
        faxJob.setFile_name("doc.pdf");

        respondWith("{\"Status\":\"Success\"}");

        // Then
        assertThatThrownBy(() -> client.sendFax(config, faxJob, document))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("no job id");
    }

    // --- Get_Fax_Inbox (listInboundFaxes) ---

    @Test
    @DisplayName("should request unread-only faxes when listing the inbox")
    void shouldRequestUnreadOnly_whenListingInbox() throws Exception {
        // Given - unread-only pull is the SRFax duplicate-prevention policy
        respondWith("{\"Status\":\"Success\",\"Result\":[]}");

        // When
        List<FaxJob> faxes = client.listInboundFaxes(config);

        // Then
        assertThat(param("action")).isEqualTo("Get_Fax_Inbox");
        assertThat(param("sViewedStatus")).isEqualTo("UNREAD");
        assertThat(param("access_id")).isEqualTo(DUMMY_ACCESS_ID);
        assertThat(faxes).isEmpty();
    }

    // --- Retrieve_Fax (downloadFax) ---

    @Test
    @DisplayName("should not send mark-as-viewed when downloading a fax")
    void shouldNotSendMarkAsViewed_whenDownloadingFax() throws Exception {
        // Given - download must be side-effect free; mark-as-read is a separate later phase
        FaxJob fax = new FaxJob();
        fax.setFile_name("20260821124500-1234-1_1|555777");

        respondWith("{\"Status\":\"Success\",\"Result\":\"QkFTRTY0LURPQw==\"}");

        // When
        FaxJob downloaded = client.downloadFax(config, fax);

        // Then
        assertThat(param("action")).isEqualTo("Retrieve_Fax");
        assertThat(param("sFaxFileName")).isEqualTo("20260821124500-1234-1_1|555777");
        assertThat(param("sDirection")).isEqualTo("IN");
        assertThat(param("sFaxFormat")).isEqualTo("PDF");
        assertThat(hasParam("sMarkasViewed")).as("download must not flip the viewed flag").isFalse();
        assertThat(downloaded.getDocument()).isEqualTo("QkFTRTY0LURPQw==");
    }

    // --- Update_Viewed_Status (markFaxAsRead) ---

    @Test
    @DisplayName("should use Update_Viewed_Status action when marking fax as read")
    void shouldUseUpdateViewedStatusAction_whenMarkingFaxAsRead() throws Exception {
        // Given - mark-as-read flips the flag without re-downloading the document
        FaxJob fax = new FaxJob();
        fax.setFile_name("20260821124500-1234-1_1|555777");

        respondWith("{\"Status\":\"Success\",\"Result\":\"true\"}");

        // When
        client.markFaxAsRead(config, fax);

        // Then
        assertThat(param("action")).isEqualTo("Update_Viewed_Status");
        assertThat(param("sFaxFileName")).isEqualTo("20260821124500-1234-1_1|555777");
        assertThat(param("sDirection")).isEqualTo("IN");
        assertThat(param("sMarkasViewed")).isEqualTo("Y");
    }

    // --- Stop_Fax (cancelFax) ---

    @Test
    @DisplayName("should send Stop_Fax with sFaxDetailsID when cancelling")
    void shouldSendStopFaxWithFaxDetailsId_whenCancelling() throws Exception {
        // Given
        FaxJob faxJob = new FaxJob();
        faxJob.setJobId(987654L);

        respondWith("{\"Status\":\"Success\",\"Result\":\"Fax Cancelled\"}");

        // When
        FaxJob cancelled = client.cancelFax(config, faxJob);

        // Then
        assertThat(param("action")).isEqualTo("Stop_Fax");
        assertThat(param("sFaxDetailsID")).isEqualTo("987654");
        assertThat(cancelled.getStatus()).isEqualTo(FaxJob.STATUS.CANCELLED);
        assertThat(cancelled.getStatusString()).isEqualTo("Fax Cancelled");
    }

    @Test
    @DisplayName("should return SENT when cancel reports the transmission already completed")
    void shouldReturnSentStatus_whenCancelReportsTransmissionCompleted() throws Exception {
        // Given - SRFax reports Status=Success even when the fax already went out and could
        // NOT be cancelled; the job must not be falsely recorded as cancelled
        FaxJob faxJob = new FaxJob();
        faxJob.setJobId(987654L);

        respondWith("{\"Status\":\"Success\",\"Result\":\"Fax transmission completed\"}");

        // When
        FaxJob result = client.cancelFax(config, faxJob);

        // Then
        assertThat(result.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
        assertThat(result.getStatusString()).isEqualTo("Fax transmission completed");
    }

    @Test
    @DisplayName("should throw FaxProviderException when cancelling a job without a provider job id")
    void shouldThrowFaxProviderException_whenCancellingJobWithoutProviderJobId() {
        // Given - a job never queued with SRFax has no FaxDetailsID to cancel
        FaxJob faxJob = new FaxJob();

        // Then - fails before any transport call is made
        assertThatThrownBy(() -> client.cancelFax(config, faxJob))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("no provider job id");
        assertThat(client.capturedEndpoint).as("no request may be sent without a job id").isNull();
    }

    // --- Credential hygiene ---

    @Test
    @DisplayName("should not leak credentials in exception messages")
    void shouldNotLeakCredentials_inExceptionMessages() throws Exception {
        // Given - a provider-reported failure; its message surfaces in Manage Faxes / logs
        respondWith("{\"Status\":\"Failed\",\"Result\":\"Access denied\"}");

        // Then - neither access_id nor access_pwd may appear in the exception text
        assertThatThrownBy(() -> client.listInboundFaxes(config))
                .isInstanceOf(FaxProviderException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(DUMMY_ACCESS_ID);
                    assertThat(e.getMessage()).doesNotContain(DUMMY_ACCESS_PWD);
                });
    }

    // --- Number normalization helpers ---

    /**
     * Direct tests of the package-visible normalization helpers used by sendFax():
     * {@code toCallerId10} (SRFax requires a 10-digit caller ID) and {@code toDialableNumber}
     * (North American destinations normalize to 11 digits; longer international digit
     * strings pass through for provider-side validation).
     */
    @Nested
    @DisplayName("number normalization helpers")
    class NumberNormalizationTests {

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @CsvSource({
                "9055551234,       9055551234",
                "19055551234,      9055551234",
                "1(905)555-1234,   9055551234",
                "905-555-1234,     9055551234",
                "+1 905 555 1234,  9055551234"
        })
        @DisplayName("should normalize caller ID to ten digits")
        void shouldNormalizeCallerId_toTenDigits(String raw, String expected) throws Exception {
            assertThat(client.toCallerId10(raw)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\" rejected")
        @ValueSource(strings = {"12345", "905555123", "123456789012", "29055551234", "", "abc"})
        @DisplayName("should throw FaxProviderException for caller ID that cannot normalize to ten digits")
        void shouldThrowFaxProviderException_forUnnormalizableCallerId(String raw) {
            assertThatThrownBy(() -> client.toCallerId10(raw))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("10 digits");
        }

        @Test
        @DisplayName("should throw FaxProviderException for null caller ID")
        void shouldThrowFaxProviderException_forNullCallerId() {
            assertThatThrownBy(() -> client.toCallerId10(null))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("10 digits");
        }

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @CsvSource({
                "9055559999,          19055559999",
                "19055559999,         19055559999",
                "905-555-9999,        19055559999",
                "(905) 555-9999,      19055559999",
                "442071234567,        442071234567",
                "+44 20 7123 4567,    442071234567",
                "011442071234567,     011442071234567"
        })
        @DisplayName("should normalize NA destinations and pass through international digit strings")
        void shouldNormalizeDestination_withInternationalPassThrough(String raw, String expected) throws Exception {
            assertThat(client.toDialableNumber(raw)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\" rejected")
        @ValueSource(strings = {"905555", "1234567890123456", "", "abc"})
        @DisplayName("should throw FaxProviderException for destination outside the dialable digit range")
        void shouldThrowFaxProviderException_forUnnormalizableDestination(String raw) {
            assertThatThrownBy(() -> client.toDialableNumber(raw))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("10-15 digits");
        }

        @Test
        @DisplayName("should throw FaxProviderException for null destination")
        void shouldThrowFaxProviderException_forNullDestination() {
            assertThatThrownBy(() -> client.toDialableNumber(null))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("10-15 digits");
        }
    }
}
