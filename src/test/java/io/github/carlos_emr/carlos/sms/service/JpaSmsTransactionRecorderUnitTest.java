package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class JpaSmsTransactionRecorderUnitTest {
    @Mock
    private SmsTransactionDao smsTransactionDao;

    @Test
    @DisplayName("recordOutboundAttempt persists and flushes the SMS transaction")
    void shouldPersistTransaction_whenRecordingOutboundAttempt() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao);

        SmsTransaction transaction = recorder.recordOutboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        ArgumentCaptor<SmsTransaction> captor = ArgumentCaptor.forClass(SmsTransaction.class);
        verify(smsTransactionDao).persist(captor.capture());
        verify(smsTransactionDao).flush();
        assertThat(captor.getValue()).isSameAs(transaction);
        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getProviderType,
                        SmsTransaction::getDemographicNo
                )
                .containsExactly(SmsStatus.QUEUED, SmsProviderType.STUB, 123);
    }

    @Test
    @DisplayName("markConsentBlocked merges the blocked transaction state")
    void shouldMergeTransaction_whenConsentIsBlocked() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        recorder.markConsentBlocked(transaction, SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED,
                "CONSENT_MODEL_PENDING",
                "SMS consent integration is pending"
        ));

        verify(smsTransactionDao).merge(transaction);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getConsentReasonCode)
                .containsExactly(SmsStatus.CONSENT_BLOCKED, "CONSENT_MODEL_PENDING");
    }

    @Test
    @DisplayName("markProviderResult merges the provider transaction state")
    void shouldMergeTransaction_whenProviderResultIsRecorded() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        recorder.markProviderResult(transaction, SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));

        verify(smsTransactionDao).merge(transaction);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderMessageId)
                .containsExactly(SmsStatus.SENT, "provider-1");
    }

    @Test
    @DisplayName("recordInboundMessage persists inbound webhook transactions")
    void shouldPersistTransaction_whenRecordingInboundMessage() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao);

        SmsTransaction transaction = recorder.recordInboundMessage(new SmsInboundWebhookDto(
                SmsProviderType.VOIPMS,
                "provider-1",
                "416-555-1212",
                "647-555-1000",
                "Reply text",
                Instant.EPOCH,
                null
        ));

        verify(smsTransactionDao).persist(transaction);
        verify(smsTransactionDao).flush();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderType)
                .containsExactly(SmsStatus.RECEIVED, SmsProviderType.VOIPMS);
    }

    @Test
    @DisplayName("recordDeliveryEvent updates an existing provider transaction")
    void shouldMergeTransaction_whenDeliveryEventMatchesExistingRecord() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao);
        SmsTransaction existing = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        existing.markProviderResult(SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(existing));

        SmsTransaction transaction = recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                SmsStatus.DELIVERED,
                Instant.EPOCH,
                null,
                null,
                null
        ));

        verify(smsTransactionDao).merge(existing);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderMessageId)
                .containsExactly(SmsStatus.DELIVERED, "provider-1");
    }

    @Test
    @DisplayName("recordDeliveryEvent defaults missing provider type before lookup")
    void shouldMergeTransaction_whenDeliveryProviderTypeIsMissing() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao);
        SmsTransaction existing = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        existing.markProviderResult(SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(existing));

        SmsTransaction transaction = recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                null,
                "provider-1",
                SmsStatus.DELIVERED,
                Instant.EPOCH,
                null,
                null,
                null
        ));

        verify(smsTransactionDao).merge(existing);
        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.DELIVERED);
    }

    @Test
    @DisplayName("recordDeliveryEvent creates a record when no provider transaction exists")
    void shouldPersistTransaction_whenDeliveryEventIsUnmatched() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao);
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.empty());

        SmsTransaction transaction = recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                SmsStatus.FAILED,
                Instant.EPOCH,
                "PROVIDER_ERROR",
                "Provider rejected message",
                null
        ));

        verify(smsTransactionDao).persist(transaction);
        verify(smsTransactionDao).flush();
        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getProviderMessageId,
                        SmsTransaction::getErrorCode
                )
                .containsExactly(SmsStatus.FAILED, "provider-1", "PROVIDER_ERROR");
    }
}
