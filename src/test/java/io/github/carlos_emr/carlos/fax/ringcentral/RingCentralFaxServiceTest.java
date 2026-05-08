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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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

    private RingCentralResponse.Message inboundMessage(String messageId, String attachmentId, String fileName) {
        RingCentralResponse.Attachment attachment =
                new RingCentralResponse.Attachment(attachmentId, fileName, "application/pdf");
        RingCentralResponse.Message message = new RingCentralResponse.Message();
        message.setId(messageId);
        message.setAttachments(Collections.singletonList(attachment));
        return message;
    }
}
