package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.event.SmsSendFailedEvent;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class JpaSmsTransactionRecorderUnitTest {
    private static final PlatformTransactionManager NOOP_TRANSACTION_MANAGER = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // No-op test transaction manager.
        }

        @Override
        public void rollback(TransactionStatus status) {
            // No-op test transaction manager.
        }
    };

    @Mock
    private SmsTransactionDao smsTransactionDao;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("recordOutboundAttempt persists and flushes the SMS transaction")
    void shouldPersistTransaction_whenRecordingOutboundAttempt() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);

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
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
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
    @DisplayName("markSending increments attempts and merges sending state")
    void shouldMergeTransaction_whenMarkingSending() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        Date attemptAt = Date.from(Instant.parse("2026-06-08T12:00:00Z"));

        recorder.markSending(transaction, attemptAt);

        verify(smsTransactionDao).merge(transaction);
        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getLastAttemptAt
                )
                .containsExactly(SmsStatus.SENDING, 1, attemptAt);
    }

    @Test
    @DisplayName("markSending rejects rows already claimed by another sender")
    void shouldRejectClaim_whenRowIsAlreadyClaimed() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(claimed, 42L);
        SmsTransaction current = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(current, 42L);
        current.markSending(Date.from(Instant.parse("2026-06-08T11:59:00Z")));
        when(smsTransactionDao.find(42L)).thenReturn(current);

        assertThatThrownBy(() -> recorder.markSending(
                claimed,
                Date.from(Instant.parse("2026-06-08T12:00:00Z"))
        )).isInstanceOf(SmsTransactionClaimConflictException.class);

        verify(smsTransactionDao).find(42L);
        verify(smsTransactionDao, never()).merge(any());
        verify(smsTransactionDao, never()).flush();
        assertThat(current)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getLastAttemptAt
                )
                .containsExactly(
                        SmsStatus.SENDING,
                        1,
                        Date.from(Instant.parse("2026-06-08T11:59:00Z"))
                );
    }

    @Test
    @DisplayName("markProviderResult merges the SMS provider transaction state")
    void shouldMergeTransaction_whenProviderResultIsRecorded() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        recorder.markProviderResult(transaction, SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));

        verify(smsTransactionDao).merge(transaction);
        verify(eventPublisher, never()).publishEvent(any());
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderMessageId)
                .containsExactly(SmsStatus.SENT, "provider-1");
    }

    @Test
    @DisplayName("markProviderResult publishes a failure event when the send lands terminal FAILED")
    void shouldPublishFailedEvent_whenProviderResultIsTerminalFailure() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        recorder.markProviderResult(
                transaction,
                SmsProviderSendResultDto.failed("PROVIDER_EXHAUSTED", "Provider rejected after retries")
        );

        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.FAILED);
        ArgumentCaptor<SmsSendFailedEvent> captor = ArgumentCaptor.forClass(SmsSendFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .extracting(SmsSendFailedEvent::demographicNo, SmsSendFailedEvent::errorCode)
                .containsExactly(123, "PROVIDER_EXHAUSTED");
    }

    @Test
    @DisplayName("markRetryScheduled re-queues a failed SMS provider attempt")
    void shouldMergeTransaction_whenRetryIsScheduled() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        Date nextAttemptAt = Date.from(Instant.parse("2026-06-08T12:05:00Z"));

        recorder.markRetryScheduled(
                transaction,
                SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message"),
                nextAttemptAt
        );

        verify(smsTransactionDao).merge(transaction);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode, SmsTransaction::getNextAttemptAt)
                .containsExactly(SmsStatus.QUEUED, "PROVIDER_ERROR", nextAttemptAt);
    }

    @Test
    @DisplayName("recordInboundMessage persists inbound webhook transactions")
    void shouldPersistTransaction_whenRecordingInboundMessage() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);

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
    @DisplayName("recordInboundMessage returns the existing row for a redelivered inbound webhook")
    void shouldReturnExistingRow_whenInboundWebhookIsRedelivered() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsInboundWebhookDto webhook = new SmsInboundWebhookDto(
                SmsProviderType.VOIPMS,
                "provider-1",
                "416-555-1212",
                "647-555-1000",
                "Reply text",
                Instant.EPOCH,
                null
        );
        SmsTransaction existing = SmsTransaction.inboundMessage(webhook);
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.VOIPMS, "provider-1"))
                .thenReturn(Optional.of(existing));

        SmsTransaction result = recorder.recordInboundMessage(webhook);

        assertThat(result).isSameAs(existing);
        verify(smsTransactionDao, never()).persist(any());
        verify(smsTransactionDao, never()).flush();
    }

    @Test
    @DisplayName("recordInboundMessage refetches when a concurrent redelivery wins the unique key")
    void shouldReturnExistingRow_whenConcurrentInboundRedeliveryWinsInsertRace() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(
                smsTransactionDao,
                eventPublisher,
                NOOP_TRANSACTION_MANAGER
        );
        SmsInboundWebhookDto webhook = new SmsInboundWebhookDto(
                SmsProviderType.VOIPMS,
                "provider-1",
                "416-555-1212",
                "647-555-1000",
                "Reply text",
                Instant.EPOCH,
                null
        );
        SmsTransaction existing = SmsTransaction.inboundMessage(webhook);
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.VOIPMS, "provider-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        doThrow(new RuntimeException("sms_transaction_provider_message_uidx"))
                .when(smsTransactionDao)
                .flush();

        SmsTransaction result = recorder.recordInboundMessage(webhook);

        assertThat(result).isSameAs(existing);
        verify(smsTransactionDao).persist(any(SmsTransaction.class));
    }

    @Test
    @DisplayName("recordInboundMessage rejects blank SMS provider message ids")
    void shouldRejectInboundMessage_whenProviderMessageIdIsBlank() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsInboundWebhookDto webhook = new SmsInboundWebhookDto(
                SmsProviderType.VOIPMS,
                " ",
                "416-555-1212",
                "647-555-1000",
                "Reply text",
                Instant.EPOCH,
                null
        );

        assertThatThrownBy(() -> recorder.recordInboundMessage(webhook))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerMessageId");
        verifyNoInteractions(smsTransactionDao);
    }

    @Test
    @DisplayName("recordDeliveryEvent updates an existing SMS provider transaction")
    void shouldMergeTransaction_whenDeliveryEventMatchesExistingRecord() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
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
    @DisplayName("recordDeliveryEvent updates an outbound row matched by client reference")
    void shouldMergeTransaction_whenDeliveryEventMatchesClientReference() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsTransaction existing = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        existing.assignClientReferenceId("sms-transaction-42");
        when(smsTransactionDao.findByClientReferenceId(SmsProviderType.STUB, "sms-transaction-42"))
                .thenReturn(Optional.of(existing));

        SmsTransaction transaction = recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                SmsStatus.DELIVERED,
                Instant.EPOCH,
                null,
                null,
                "sms-transaction-42",
                null
        ));

        verify(smsTransactionDao).merge(existing);
        verify(smsTransactionDao, never()).findByProviderMessageId(SmsProviderType.STUB, "provider-1");
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderMessageId)
                .containsExactly(SmsStatus.DELIVERED, "provider-1");
    }

    @Test
    @DisplayName("recordDeliveryEvent defaults missing SMS provider type before lookup")
    void shouldMergeTransaction_whenDeliveryProviderTypeIsMissing() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
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
    @DisplayName("recordDeliveryEvent rejects blank delivery correlation ids")
    void shouldRejectDeliveryEvent_whenCorrelationIdsAreBlank() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        SmsDeliveryWebhookDto webhook = new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                " ",
                SmsStatus.DELIVERED,
                Instant.EPOCH,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> recorder.recordDeliveryEvent(webhook))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerMessageId or clientReferenceId");
        verifyNoInteractions(smsTransactionDao);
    }

    @Test
    @DisplayName("recordDeliveryEvent creates a record when no SMS provider transaction exists")
    void shouldPersistTransaction_whenDeliveryEventIsUnmatched() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
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

    @Test
    @DisplayName("claimDueOutboundQueue delegates atomic claiming to the DAO")
    void shouldMarkTransactionsSending_whenClaimingDueOutboundQueue() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        Date now = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        transaction.markSending(now);
        when(smsTransactionDao.claimDueOutboundQueue(SmsProviderType.STUB, now, 25))
                .thenReturn(List.of(transaction));

        List<SmsTransaction> transactions = recorder.claimDueOutboundQueue(SmsProviderType.STUB, now, 25);

        assertThat(transactions).singleElement().isSameAs(transaction);
        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getLastAttemptAt
                )
                .containsExactly(SmsStatus.SENDING, 1, now);
        verify(smsTransactionDao).claimDueOutboundQueue(SmsProviderType.STUB, now, 25);
    }

    @Test
    @DisplayName("claimStaleSendingForRecovery delegates atomic claiming to the DAO")
    void shouldMarkRecoveryStarted_whenSendingRowsAreStale() {
        JpaSmsTransactionRecorder recorder = new JpaSmsTransactionRecorder(smsTransactionDao, eventPublisher);
        Date staleBefore = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date recoveryAt = Date.from(Instant.parse("2026-06-08T12:05:00Z"));
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        transaction.markSending(Date.from(Instant.parse("2026-06-08T11:00:00Z")));
        transaction.markStaleRecoveryStarted(recoveryAt);
        when(smsTransactionDao.claimStaleOutboundSendingForRecovery(
                SmsProviderType.STUB,
                staleBefore,
                recoveryAt,
                25
        ))
                .thenReturn(List.of(transaction));

        List<SmsTransaction> transactions = recorder.claimStaleSendingForRecovery(
                SmsProviderType.STUB,
                staleBefore,
                recoveryAt,
                25
        );

        assertThat(transactions).singleElement().isSameAs(transaction);
        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getLastAttemptAt
                )
                .containsExactly(SmsStatus.SENDING, 1, recoveryAt);
        verify(smsTransactionDao).claimStaleOutboundSendingForRecovery(
                SmsProviderType.STUB,
                staleBefore,
                recoveryAt,
                25
        );
    }

    private static void assignId(SmsTransaction transaction, long id) {
        try {
            Field idField = SmsTransaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, id);
            transaction.assignClientReferenceId(SmsTransaction.clientReferenceIdFor(id));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to assign SMS transaction id for test", e);
        }
    }
}
