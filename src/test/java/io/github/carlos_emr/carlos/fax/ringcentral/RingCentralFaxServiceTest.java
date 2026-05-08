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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for RingCentral fax provider behavior.
 *
 * @since 2026-05-05
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("RingCentralFaxService Unit Tests")
class RingCentralFaxServiceTest extends CarlosUnitTestBase {

    private RingCentralApiConnector apiConnector;
    private RingCentralAuthService authService;
    private RingCentralFaxService service;

    @BeforeEach
    void setUp() {
        apiConnector = mock(RingCentralApiConnector.class);
        authService = mock(RingCentralAuthService.class);
        service = new RingCentralFaxService(apiConnector, authService);
    }

    @Test
    @DisplayName("should return RingCentral provider type")
    void shouldReturnRingCentralProviderType_forTypeContract() {
        assertThat(service.getProviderType()).isEqualTo(FaxConfig.ProviderType.RINGCENTRAL);
    }

    @Test
    @DisplayName("should map delivered provider status to COMPLETE")
    void shouldMapDeliveredStatus_toComplete() {
        assertThat(service.mapStatus("Delivered")).isEqualTo(FaxJob.STATUS.COMPLETE);
    }

    @Test
    @DisplayName("should map sending provider status to SENT")
    void shouldMapSendingStatus_toSent() {
        assertThat(service.mapStatus("Sending")).isEqualTo(FaxJob.STATUS.SENT);
    }

    @Test
    @DisplayName("should map failed provider status to ERROR")
    void shouldMapFailedStatus_toError() {
        assertThat(service.mapStatus("Delivery Failed")).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should map SendingFailed to ERROR even though it contains 'sending'")
    void shouldMapSendingFailed_toError() {
        assertThat(service.mapStatus("SendingFailed")).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should map undelivered to ERROR even though it contains 'delivered'")
    void shouldMapUndelivered_toError() {
        assertThat(service.mapStatus("Undelivered")).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should map ReceiveFailed to ERROR even though it contains 'received'")
    void shouldMapReceiveFailed_toError() {
        assertThat(service.mapStatus("ReceiveFailed")).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should map cancelled provider status to CANCELLED")
    void shouldMapCancelledStatus_toCancelled() {
        assertThat(service.mapStatus("Cancelled")).isEqualTo(FaxJob.STATUS.CANCELLED);
    }

    @Test
    @DisplayName("should map queued provider status to SENT")
    void shouldMapQueuedStatus_toSent() {
        assertThat(service.mapStatus("Queued")).isEqualTo(FaxJob.STATUS.SENT);
    }

    @Test
    @DisplayName("should map sent provider status to COMPLETE")
    void shouldMapSentStatus_toComplete() {
        assertThat(service.mapStatus("Sent")).isEqualTo(FaxJob.STATUS.COMPLETE);
    }

    @Test
    @DisplayName("should map null provider status to UNKNOWN")
    void shouldMapNullStatus_toUnknown() {
        assertThat(service.mapStatus(null)).isEqualTo(FaxJob.STATUS.UNKNOWN);
    }

    @Test
    @DisplayName("should map unrecognized provider status to UNKNOWN")
    void shouldMapUnrecognizedStatus_toUnknown() {
        assertThat(service.mapStatus("WeirdNewState")).isEqualTo(FaxJob.STATUS.UNKNOWN);
    }

    @Test
    @DisplayName("should map blank or whitespace provider status to UNKNOWN")
    void shouldMapBlankStatus_toUnknown() {
        assertThat(service.mapStatus("")).isEqualTo(FaxJob.STATUS.UNKNOWN);
        assertThat(service.mapStatus("   ")).isEqualTo(FaxJob.STATUS.UNKNOWN);
    }

    @Test
    @DisplayName("should prefer ERROR when status mixes failure and cancel keywords")
    void shouldPreferError_whenStatusMixesFailureAndCancel() {
        assertThat(service.mapStatus("Cancelled-Failed")).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(service.mapStatus("Failed-Cancelled")).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should map Received provider status to COMPLETE")
    void shouldMapReceivedStatus_toComplete() {
        assertThat(service.mapStatus("Received")).isEqualTo(FaxJob.STATUS.COMPLETE);
    }

    @Test
    @DisplayName("should return inbound fax metadata when unread message has attachment")
    void shouldReturnInboundFaxMetadata_whenUnreadMessageHasAttachment() throws Exception {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.RINGCENTRAL);
        when(config.getRingCentralAccountId()).thenReturn("~");
        when(config.getRingCentralExtensionId()).thenReturn("~");
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");

        RingCentralResponse.Attachment attachment =
                new RingCentralResponse.Attachment("456", "incoming.pdf", "application/pdf");
        RingCentralResponse.Message message = new RingCentralResponse.Message();
        message.setId("123");
        message.setAttachments(Collections.singletonList(attachment));
        RingCentralResponse.MessageList messageList = new RingCentralResponse.MessageList();
        messageList.setRecords(Collections.singletonList(message));
        when(apiConnector.getInboundFaxes("token", "~", "~")).thenReturn(messageList);

        List<FaxJob> result = service.listInboundFaxes(config);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getJobId()).isEqualTo(123L);
        assertThat(result.get(0).getFile_name()).isEqualTo("123:456:incoming.pdf");
        assertThat(result.get(0).getStatus()).isEqualTo(FaxJob.STATUS.RECEIVED);
    }

    @Test
    @DisplayName("should emit one FaxJob per attachment when message has multiple attachments")
    void shouldEmitOneFaxJobPerAttachment_whenMessageHasMultipleAttachments() throws Exception {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.RINGCENTRAL);
        when(config.getRingCentralAccountId()).thenReturn("~");
        when(config.getRingCentralExtensionId()).thenReturn("~");
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");

        RingCentralResponse.Message message = new RingCentralResponse.Message();
        message.setId("999");
        message.setAttachments(Arrays.asList(
                new RingCentralResponse.Attachment("a1", "page1.pdf", "application/pdf"),
                new RingCentralResponse.Attachment("a2", "page2.pdf", "application/pdf")));
        RingCentralResponse.MessageList messageList = new RingCentralResponse.MessageList();
        messageList.setRecords(Collections.singletonList(message));
        when(apiConnector.getInboundFaxes("token", "~", "~")).thenReturn(messageList);

        List<FaxJob> result = service.listInboundFaxes(config);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFile_name()).isEqualTo("999:a1:page1.pdf");
        assertThat(result.get(1).getFile_name()).isEqualTo("999:a2:page2.pdf");
    }

    @Test
    @DisplayName("should skip null records when MessageList contains null entries")
    void shouldSkipNullRecords_whenMessageListContainsNullEntry() throws Exception {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.RINGCENTRAL);
        when(config.getRingCentralAccountId()).thenReturn("~");
        when(config.getRingCentralExtensionId()).thenReturn("~");
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");

        RingCentralResponse.Message valid1 = inboundMessage("100", "200", "first.pdf");
        RingCentralResponse.Message valid2 = inboundMessage("101", "201", "second.pdf");
        RingCentralResponse.MessageList messageList = new RingCentralResponse.MessageList();
        messageList.setRecords(Arrays.asList(valid1, null, valid2));
        when(apiConnector.getInboundFaxes("token", "~", "~")).thenReturn(messageList);

        List<FaxJob> result = service.listInboundFaxes(config);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getJobId()).isEqualTo(100L);
        assertThat(result.get(1).getJobId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("should reject malformed phone number in validateSendRequest")
    void shouldRejectMalformedPhoneNumber_whenDestinationLooksWrong() {
        FaxJob job = new FaxJob();
        job.setDestination("not-a-number");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.validateSendRequest(job))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("phone number");
    }

    @Test
    @DisplayName("should accept digit-only phone number in validateSendRequest")
    void shouldAcceptPhoneNumber_whenDestinationIsDigitsOnly() {
        FaxJob job = new FaxJob();
        job.setDestination("4165551234");

        org.assertj.core.api.Assertions.assertThatCode(() -> service.validateSendRequest(job))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject sendFax when provider type does not match RingCentral")
    void shouldRejectSendFax_whenProviderTypeMismatches() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);
        FaxJob job = new FaxJob();
        job.setDestination("4165551234");

        assertThatThrownBy(() -> service.sendFax(config, job, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authService, apiConnector);
    }

    @Test
    @DisplayName("should queue RingCentral fax and clear document when send succeeds")
    void shouldQueueRingCentralFax_whenSendSucceeds(@TempDir Path tempDir) throws Exception {
        FaxConfig config = ringCentralConfig();
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");

        Path doc = tempDir.resolve("fax.pdf");
        Files.write(doc, "%PDF-fixture".getBytes(StandardCharsets.UTF_8));
        FaxJob job = new FaxJob();
        job.setDestination("4165551234");
        job.setFile_name("fax.pdf");

        RingCentralResponse.Message response = new RingCentralResponse.Message();
        response.setId("789");
        response.setMessageStatus("Queued");
        when(apiConnector.sendFax(eq("token"), eq("~"), eq("~"),
                eq("4165551234"), any(byte[].class), eq("fax.pdf"))).thenReturn(response);

        FaxJob result = service.sendFax(config, job, doc);

        assertThat(result.getJobId()).isEqualTo(789L);
        assertThat(result.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
        assertThat(job.getDocument()).isNull();
        verify(apiConnector, times(1)).sendFax(eq("token"), eq("~"), eq("~"),
                eq("4165551234"), any(byte[].class), eq("fax.pdf"));
    }

    @Test
    @DisplayName("should decode base64 document when sendFax has no file path")
    void shouldDecodeBase64Document_whenFilePathIsNull() throws Exception {
        FaxConfig config = ringCentralConfig();
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");

        byte[] expected = "%PDF-base64".getBytes(StandardCharsets.UTF_8);
        FaxJob job = new FaxJob();
        job.setDestination("4165551234");
        job.setDocument(Base64.getEncoder().encodeToString(expected));

        RingCentralResponse.Message response = new RingCentralResponse.Message();
        response.setId("42");
        when(apiConnector.sendFax(eq("token"), eq("~"), eq("~"), eq("4165551234"),
                any(byte[].class), any())).thenReturn(response);

        service.sendFax(config, job, null);

        verify(apiConnector).sendFax(eq("token"), eq("~"), eq("~"), eq("4165551234"),
                eq(expected), any());
    }

    @Test
    @DisplayName("should reject sendFax when filePath does not exist")
    void shouldRejectSendFax_whenFilePathDoesNotExist(@TempDir Path tempDir) {
        FaxConfig config = ringCentralConfig();
        FaxJob job = new FaxJob();
        job.setDestination("4165551234");

        assertThatThrownBy(() -> service.sendFax(config, job, tempDir.resolve("missing.pdf")))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("not found");
        // The connector must never receive a partial / empty-bytes call when document
        // resolution fails — surfacing the error to the caller is the only correct behavior.
        verifyNoInteractions(apiConnector);
    }

    @Test
    @DisplayName("should reject fetchFaxStatus when job id is missing")
    void shouldRejectFetchFaxStatus_whenJobIdMissing() {
        FaxConfig config = ringCentralConfig();
        FaxJob job = new FaxJob();

        assertThatThrownBy(() -> service.fetchFaxStatus(config, job))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("provider message id");
        verifyNoInteractions(apiConnector);
    }

    @Test
    @DisplayName("should fetch and map fax status when provider responds with faxStatus")
    void shouldFetchFaxStatus_whenProviderReturnsFaxStatus() throws Exception {
        FaxConfig config = ringCentralConfig();
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");
        FaxJob job = new FaxJob();
        job.setJobId(555L);

        RingCentralResponse.Message response = new RingCentralResponse.Message();
        response.setFaxStatus("Sent");
        when(apiConnector.getFaxStatus("token", "~", "~", "555")).thenReturn(response);

        FaxJob updated = service.fetchFaxStatus(config, job);

        assertThat(updated.getStatus()).isEqualTo(FaxJob.STATUS.COMPLETE);
        assertThat(updated.getStatusString()).isEqualTo("Sent");
    }

    @Test
    @DisplayName("should noop deleteFax without calling the connector")
    void shouldNoopDeleteFax_withoutCallingConnector() throws Exception {
        FaxConfig config = ringCentralConfig();
        FaxJob job = new FaxJob();

        service.deleteFax(config, job);

        verifyNoInteractions(apiConnector, authService);
    }

    @Test
    @DisplayName("should mark RingCentral fax as read using parsed download reference")
    void shouldMarkFaxAsRead_byParsedDownloadReference() throws Exception {
        FaxConfig config = ringCentralConfig();
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");
        FaxJob fax = new FaxJob();
        fax.setFile_name("321:att-9:label.pdf");

        service.markFaxAsRead(config, fax);

        verify(apiConnector).markFaxAsRead("token", "~", "~", "321");
    }

    @Test
    @DisplayName("should download fax bytes and base64-encode them onto the FaxJob")
    void shouldDownloadFax_andBase64EncodeContent() throws Exception {
        FaxConfig config = ringCentralConfig();
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");
        FaxJob fax = new FaxJob();
        fax.setFile_name("321:att-9:label.pdf");

        byte[] pdfBytes = "%PDF-content".getBytes(StandardCharsets.UTF_8);
        when(apiConnector.downloadFax("token", "~", "~", "321", "att-9")).thenReturn(pdfBytes);

        FaxJob downloaded = service.downloadFax(config, fax);

        assertThat(downloaded.getDocument()).isEqualTo(Base64.getEncoder().encodeToString(pdfBytes));
        assertThat(downloaded.getStatus()).isEqualTo(FaxJob.STATUS.RECEIVED);
    }

    @Test
    @DisplayName("should invalidate cached token and retry once when sendFax returns HTTP 401")
    void shouldInvalidateTokenAndRetryOnce_whenSendFaxReturns401() throws Exception {
        FaxConfig config = ringCentralConfig();
        when(config.getId()).thenReturn(7);
        when(authService.getAccessToken(config, apiConnector))
                .thenReturn("stale-token")
                .thenReturn("fresh-token");

        Path doc = Files.createTempFile("fax", ".pdf");
        try {
            Files.write(doc, "%PDF-".getBytes(StandardCharsets.UTF_8));
            FaxJob job = new FaxJob();
            job.setDestination("4165551234");

            RingCentralException unauthorized =
                    new RingCentralException("RingCentral fax send failed with HTTP 401", 401, false);
            RingCentralResponse.Message success = new RingCentralResponse.Message();
            success.setId("999");
            when(apiConnector.sendFax(eq("stale-token"), anyString(), anyString(),
                    anyString(), any(byte[].class), any())).thenThrow(unauthorized);
            when(apiConnector.sendFax(eq("fresh-token"), anyString(), anyString(),
                    anyString(), any(byte[].class), any())).thenReturn(success);

            FaxJob result = service.sendFax(config, job, doc);

            assertThat(result.getJobId()).isEqualTo(999L);
            verify(authService, times(1)).invalidateToken(config);
            verify(authService, times(2)).getAccessToken(config, apiConnector);
        } finally {
            Files.deleteIfExists(doc);
        }
    }

    @Test
    @DisplayName("should not invalidate token when connector throws non-401 RingCentralException")
    void shouldNotInvalidateToken_whenConnectorThrowsNon401() throws Exception {
        FaxConfig config = ringCentralConfig();
        when(authService.getAccessToken(config, apiConnector)).thenReturn("token");

        RingCentralException badRequest =
                new RingCentralException("RingCentral fax send failed with HTTP 400", 400, false);
        when(apiConnector.sendFax(anyString(), anyString(), anyString(), anyString(),
                any(byte[].class), any())).thenThrow(badRequest);

        Path doc = Files.createTempFile("fax", ".pdf");
        try {
            Files.write(doc, "%PDF-".getBytes(StandardCharsets.UTF_8));
            FaxJob job = new FaxJob();
            job.setDestination("4165551234");

            assertThatThrownBy(() -> service.sendFax(config, job, doc))
                    .isInstanceOf(RingCentralException.class);
            verify(authService, never()).invalidateToken(any(FaxConfig.class));
        } finally {
            Files.deleteIfExists(doc);
        }
    }

    @Test
    @DisplayName("should rethrow when retry after 401 also returns 401")
    void shouldRethrow_whenRetryAlsoReturns401() throws Exception {
        FaxConfig config = ringCentralConfig();
        when(config.getId()).thenReturn(7);
        when(authService.getAccessToken(config, apiConnector))
                .thenReturn("stale-token")
                .thenReturn("still-bad-token");

        RingCentralException unauthorized =
                new RingCentralException("RingCentral fax send failed with HTTP 401", 401, false);
        when(apiConnector.sendFax(anyString(), anyString(), anyString(), anyString(),
                any(byte[].class), any())).thenThrow(unauthorized);

        Path doc = Files.createTempFile("fax", ".pdf");
        try {
            Files.write(doc, "%PDF-".getBytes(StandardCharsets.UTF_8));
            FaxJob job = new FaxJob();
            job.setDestination("4165551234");

            assertThatThrownBy(() -> service.sendFax(config, job, doc))
                    .isInstanceOf(RingCentralException.class);
            verify(authService, times(1)).invalidateToken(config);
            // Capture the access tokens used so we prove the retry actually used the refreshed
            // token, not just that two calls happened. Otherwise a regression where the cache
            // isn't evicted (or evict happens but stale token is still retried) would pass.
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(apiConnector, times(2)).sendFax(tokenCaptor.capture(), anyString(),
                    anyString(), anyString(), any(byte[].class), any());
            assertThat(tokenCaptor.getAllValues()).containsExactly("stale-token", "still-bad-token");
        } finally {
            Files.deleteIfExists(doc);
        }
    }

    @Test
    @DisplayName("should propagate exception when token refresh after 401 itself fails")
    void shouldPropagateException_whenTokenRefreshAfter401Fails() throws Exception {
        FaxConfig config = ringCentralConfig();
        when(config.getId()).thenReturn(7);
        RingCentralException unauthorized =
                new RingCentralException("RingCentral fax send failed with HTTP 401", 401, false);
        RingCentralException refreshFailed =
                new RingCentralException("RingCentral OAuth response did not include an access token");
        when(authService.getAccessToken(config, apiConnector))
                .thenReturn("stale-token")
                .thenThrow(refreshFailed);
        when(apiConnector.sendFax(anyString(), anyString(), anyString(), anyString(),
                any(byte[].class), any())).thenThrow(unauthorized);

        Path doc = Files.createTempFile("fax", ".pdf");
        try {
            Files.write(doc, "%PDF-".getBytes(StandardCharsets.UTF_8));
            FaxJob job = new FaxJob();
            job.setDestination("4165551234");

            assertThatThrownBy(() -> service.sendFax(config, job, doc))
                    .isInstanceOf(RingCentralException.class)
                    .hasMessageContaining("OAuth response");
            // Eviction happened before the refresh attempt; the connector was only called once
            // (the original 401) — there's no second sendFax because re-auth itself failed.
            verify(authService, times(1)).invalidateToken(config);
            verify(apiConnector, times(1)).sendFax(anyString(), anyString(), anyString(),
                    anyString(), any(byte[].class), any());
        } finally {
            Files.deleteIfExists(doc);
        }
    }

    private FaxConfig ringCentralConfig() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.RINGCENTRAL);
        when(config.getRingCentralAccountId()).thenReturn("~");
        when(config.getRingCentralExtensionId()).thenReturn("~");
        return config;
    }

    private RingCentralResponse.Message inboundMessage(String messageId, String attachmentId, String fileName) {
        RingCentralResponse.Attachment attachment =
                new RingCentralResponse.Attachment(attachmentId, fileName, "application/pdf");
        RingCentralResponse.Message message = new RingCentralResponse.Message();
        message.setId(messageId);
        message.setAttachments(Collections.singletonList(attachment));
        return message;
    }
}
