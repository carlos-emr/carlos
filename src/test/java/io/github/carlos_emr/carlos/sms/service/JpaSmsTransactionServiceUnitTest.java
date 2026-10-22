package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.event.SmsSendFailedEvent;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Field;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class JpaSmsTransactionServiceUnitTest {
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);

        SmsTransaction transaction = recorder.recordOutboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB,
                SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, 4321, Instant.parse("2026-09-01T14:30:00Z"))
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
    @DisplayName("recordOutboundAttempt persists nothing for a permitted send that names no consent state")
    void shouldPersistNothing_whenPermittedDecisionHasNoConsentStatus() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsSendCommand command = SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998");

        SmsConsentDecisionDto permitWithoutConsentState = SmsConsentDecisionDto.permitted(null, null, null);

        assertThatThrownBy(() -> recorder.recordOutboundAttempt(
                command, SmsProviderType.STUB, permitWithoutConsentState))
                .isInstanceOf(IllegalArgumentException.class);

        verify(smsTransactionDao, never()).persist(any());
    }

    @Test
    @DisplayName("recordOutboundAttempt stores the consent record a permitted send relied on")
    void shouldStoreConsentSnapshot_whenRecordingPermittedOutboundAttempt() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        Instant editedAt = Instant.parse("2026-09-01T14:30:00Z");

        SmsTransaction transaction = recorder.recordOutboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB,
                SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, 4321, editedAt)
        );

        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getConsentStatus,
                        SmsTransaction::getConsentId,
                        SmsTransaction::getConsentLastUpdateDate
                )
                .containsExactly(SmsStatus.QUEUED, SmsConsentStatus.OPT_IN, 4321, Date.from(editedAt));
    }

    @Test
    @DisplayName("recordConsentDecision rewrites the snapshot of a claimed row without changing its status")
    void shouldRewriteConsentSnapshot_whenRecordingDispatchDecision() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        SmsTransaction current = claimedRowWithConsentSnapshot(42L, 3L);
        when(smsTransactionDao.find(42L)).thenReturn(current);
        Instant reconsentedAt = Instant.parse("2026-09-10T09:00:00Z");

        SmsTransaction recorded = recorder.recordConsentDecision(
                claimed, SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, 9, reconsentedAt));

        assertThat(recorded).isSameAs(current);
        assertThat(current)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getConsentId,
                        SmsTransaction::getConsentLastUpdateDate
                )
                .containsExactly(SmsStatus.SENDING, 9, Date.from(reconsentedAt));
    }

    @Test
    @DisplayName("recordConsentDecision refuses to report success when the row changed under the claim")
    void shouldRejectSnapshotRewrite_whenRowChangedUnderClaim() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        SmsTransaction current = claimedRowWithConsentSnapshot(42L, 4L);
        when(smsTransactionDao.find(42L)).thenReturn(current);
        SmsConsentDecisionDto decision = SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 9, Instant.parse("2026-09-10T09:00:00Z"));

        assertThatThrownBy(() -> recorder.recordConsentDecision(claimed, decision))
                .isInstanceOf(SmsTransactionClaimConflictException.class);

        assertThat(current.getConsentId()).isEqualTo(5);
    }

    @Test
    @DisplayName("recordConsentDecision refuses to report success when the claimed row no longer exists")
    void shouldRejectSnapshotRewrite_whenClaimedRowIsMissing() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        when(smsTransactionDao.find(42L)).thenReturn(null);
        SmsConsentDecisionDto decision = SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 9, Instant.parse("2026-09-10T09:00:00Z"));

        try (LogCapture logs = LogCapture.forLogger(JpaSmsTransactionService.class)) {
            assertThatThrownBy(() -> recorder.recordConsentDecision(claimed, decision))
                    .isInstanceOf(SmsTransactionClaimConflictException.class);

            // The worker stays silent on a claim conflict, so the recorder must say why the write was dropped.
            assertThat(logs.messages()).anySatisfy(message -> assertThat(message).contains("42"));
        }
    }

    @Test
    @DisplayName("renewClaim restarts the stale-send clock of a claimed row without counting another attempt")
    void shouldRestartStaleClock_whenRenewingClaim() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        SmsTransaction current = claimedRowWithConsentSnapshot(42L, 3L);
        when(smsTransactionDao.find(42L)).thenReturn(current);
        Date renewedAt = Date.from(Instant.parse("2026-09-10T09:12:00Z"));

        SmsTransaction renewed = recorder.renewClaim(claimed, renewedAt);

        assertThat(renewed).isSameAs(current);
        assertThat(current)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getLastAttemptAt)
                .containsExactly(SmsStatus.SENDING, 1, renewedAt);
        verify(smsTransactionDao).flush();
    }

    @Test
    @DisplayName("renewClaim refuses when stale recovery or another writer changed the row under the claim")
    void shouldRejectRenewal_whenRowChangedUnderClaim() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        SmsTransaction current = claimedRowWithConsentSnapshot(42L, 4L);
        Date recoveryClaimedAt = current.getLastAttemptAt();
        when(smsTransactionDao.find(42L)).thenReturn(current);
        Date renewedAt = Date.from(Instant.parse("2026-09-10T09:12:00Z"));

        assertThatThrownBy(() -> recorder.renewClaim(claimed, renewedAt))
                .isInstanceOf(SmsTransactionClaimConflictException.class);

        assertThat(current.getLastAttemptAt()).isEqualTo(recoveryClaimedAt);
        verify(smsTransactionDao, never()).flush();
    }

    @Test
    @DisplayName("renewClaim refuses a row that is no longer sending or no longer exists")
    void shouldRejectRenewal_whenRowIsNotSendingOrMissing() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        SmsTransaction released = claimedRowWithConsentSnapshot(42L, 3L);
        released.markClaimReleased(Date.from(Instant.parse("2026-09-10T09:06:00Z")));
        Date renewedAt = Date.from(Instant.parse("2026-09-10T09:12:00Z"));

        when(smsTransactionDao.find(42L)).thenReturn(released);
        assertThatThrownBy(() -> recorder.renewClaim(claimed, renewedAt))
                .isInstanceOf(SmsTransactionClaimConflictException.class);
        assertThat(released.getStatus()).isEqualTo(SmsStatus.QUEUED);

        when(smsTransactionDao.find(42L)).thenReturn(null);
        assertThatThrownBy(() -> recorder.renewClaim(claimed, renewedAt))
                .isInstanceOf(SmsTransactionClaimConflictException.class);
        verify(smsTransactionDao, never()).flush();
    }

    @Test
    @DisplayName("releaseClaim with a reason hands the claim back, gives the attempt back and says why the text waits")
    void shouldRecordReason_whenReleasingClaimForRateLimit() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        SmsTransaction current = claimedRowWithConsentSnapshot(42L, 3L);
        when(smsTransactionDao.find(42L)).thenReturn(current);
        Date dueAt = Date.from(Instant.parse("2026-09-10T09:06:00Z"));

        SmsTransaction released = recorder.releaseClaim(claimed, dueAt, SmsProviderErrorCode.RATE_LIMITED);

        assertThat(released).isSameAs(current);
        assertThat(current)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount,
                        SmsTransaction::getNextAttemptAt, SmsTransaction::getErrorCode, SmsTransaction::getErrorMessage)
                .containsExactly(SmsStatus.QUEUED, 0, dueAt, "RATE_LIMITED",
                        SmsProviderErrorCode.RATE_LIMITED.message());
    }

    @Test
    @DisplayName("releaseClaim with a reason leaves a row another writer changed under the claim as it is")
    void shouldKeepNewerRow_whenReleasingClaimForRateLimitAfterRowChanged() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        SmsTransaction current = claimedRowWithConsentSnapshot(42L, 4L);
        when(smsTransactionDao.find(42L)).thenReturn(current);

        SmsTransaction released = recorder.releaseClaim(claimed, new Date(), SmsProviderErrorCode.RATE_LIMITED);

        assertThat(released).isSameAs(current);
        assertThat(current)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.SENDING, 1, null);
    }

    @Test
    @DisplayName("releaseClaim without a reason keeps the error the row already had")
    void shouldKeepExistingError_whenReleasingClaimWithoutReason() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = claimedRowWithConsentSnapshot(42L, 3L);
        SmsTransaction current = claimedRowWithConsentSnapshot(42L, 3L);
        current.markRetryScheduled(SmsProviderSendResultDto.failed(SmsProviderErrorCode.ACCOUNT_LIMIT), new Date());
        current.markSending(new Date());
        assignVersion(current, 3L);
        when(smsTransactionDao.find(42L)).thenReturn(current);

        recorder.releaseClaim(claimed, new Date());

        assertThat(current)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode,
                        SmsTransaction::getErrorMessage)
                .containsExactly(SmsStatus.QUEUED, 1, "ACCOUNT_LIMIT", SmsProviderErrorCode.ACCOUNT_LIMIT.message());
    }

    @Test
    @DisplayName("markConsentBlocked merges the blocked transaction state")
    void shouldMergeTransaction_whenConsentIsBlocked() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        recorder.markConsentBlocked(transaction, SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED,
                "SMS_CONSENT_UNKNOWN",
                "No SMS consent is recorded for this patient."
        ));

        verify(smsTransactionDao).merge(transaction);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getConsentReasonCode)
                .containsExactly(SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN");
    }

    @Test
    @DisplayName("markSending increments attempts and merges sending state")
    void shouldMergeTransaction_whenMarkingSending() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction claimed = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(claimed, 42L);
        SmsTransaction current = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(current, 42L);
        current.markSending(Date.from(Instant.parse("2026-06-08T11:59:00Z")));
        when(smsTransactionDao.find(42L)).thenReturn(current);

        Date attemptedAt = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        assertThatThrownBy(() -> recorder.markSending(claimed, attemptedAt)).isInstanceOf(SmsTransactionClaimConflictException.class);

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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        recorder.markProviderResult(transaction, SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));

        verify(smsTransactionDao).merge(transaction);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderMessageId)
                .containsExactly(SmsStatus.SENT, "provider-1");
    }

    @Test
    @DisplayName("markProviderResult publishes a failure event when the send lands terminal FAILED")
    void shouldPublishFailedEvent_whenProviderResultIsTerminalFailure() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);

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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction existing = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
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
    @DisplayName("recordDeliveryEvent publishes a failure event when the carrier reports a failed delivery")
    void shouldPublishFailedEvent_whenDeliveryWebhookReportsFailure() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction sent = acceptedOutboundRow();
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(sent));

        SmsTransaction transaction = recorder.recordDeliveryEvent(
                failedDelivery(Instant.parse("2026-09-22T10:00:00Z")));

        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.FAILED);
        ArgumentCaptor<SmsSendFailedEvent> captor = ArgumentCaptor.forClass(SmsSendFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .extracting(SmsSendFailedEvent::demographicNo, SmsSendFailedEvent::errorCode)
                .containsExactly(123, "CARRIER_REJECTED");
    }

    @Test
    @DisplayName("recordDeliveryEvent does not republish when a failed delivery callback is replayed")
    void shouldNotRepublishFailedEvent_whenDeliveryCallbackIsReplayed() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction sent = acceptedOutboundRow();
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(sent));
        SmsDeliveryWebhookDto callback = failedDelivery(Instant.parse("2026-09-22T10:00:00Z"));

        recorder.recordDeliveryEvent(callback);
        recorder.recordDeliveryEvent(callback);

        assertThat(sent.getStatus()).isEqualTo(SmsStatus.FAILED);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent publishes nothing when the carrier confirms delivery")
    void shouldNotPublishFailedEvent_whenDeliveryWebhookConfirmsDelivery() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction sent = acceptedOutboundRow();
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(sent));

        recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.DELIVERED,
                Instant.parse("2026-09-22T10:00:00Z"), null, null, null));

        assertThat(sent.getStatus()).isEqualTo(SmsStatus.DELIVERED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent publishes nothing when a failed callback arrives after delivery")
    void shouldNotPublishFailedEvent_whenFailedCallbackArrivesAfterDelivery() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction sent = acceptedOutboundRow();
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(sent));
        recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.DELIVERED,
                Instant.parse("2026-09-22T10:00:00Z"), null, null, null));

        // DELIVERED is final, so the entity ignores the late receipt and the row never becomes FAILED.
        recorder.recordDeliveryEvent(failedDelivery(Instant.parse("2026-09-22T10:05:00Z")));

        assertThat(sent.getStatus()).isEqualTo(SmsStatus.DELIVERED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent publishes nothing for a failed callback that matches no stored message")
    void shouldNotPublishFailedEvent_whenFailedCallbackMatchesNoStoredMessage() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.empty());

        SmsTransaction transaction = recorder.recordDeliveryEvent(
                failedDelivery(Instant.parse("2026-09-22T10:00:00Z")));

        // The row carries no patient, provider or appointment, so the event would say nothing useful.
        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.FAILED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent publishes once when sent and failed callbacks are replayed in turn")
    void shouldPublishOnce_whenSentAndFailedCallbacksAlternate() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction sent = acceptedOutboundRow();
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(sent));
        Instant eventAt = Instant.parse("2026-09-22T10:00:00Z");
        SmsDeliveryWebhookDto sentCallback = new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.SENT, eventAt, null, null, null);

        recorder.recordDeliveryEvent(failedDelivery(eventAt));
        recorder.recordDeliveryEvent(sentCallback);
        recorder.recordDeliveryEvent(failedDelivery(eventAt));

        assertThat(sent.getStatus()).isEqualTo(SmsStatus.FAILED);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent publishes nothing when a failed callback follows an unmatched sent callback")
    void shouldNotPublishFailedEvent_whenPlaceholderRowFails() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction placeholder = SmsTransaction.deliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.SENT,
                Instant.parse("2026-09-22T09:59:00Z"), null, null, null));
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(placeholder));

        recorder.recordDeliveryEvent(failedDelivery(Instant.parse("2026-09-22T10:00:00Z")));

        // The placeholder names no patient, provider or appointment, so nobody could act on the event.
        assertThat(placeholder.getStatus()).isEqualTo(SmsStatus.FAILED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent publishes nothing when a failed callback names a message that was never sent")
    void shouldNotPublishFailedEvent_whenMessageWasNeverSent() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction queued = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(queued));

        recorder.recordDeliveryEvent(failedDelivery(Instant.parse("2026-09-22T10:00:00Z")));

        // No carrier has the message, so the callback is ignored and the message still goes out.
        assertThat(queued.getStatus()).isEqualTo(SmsStatus.QUEUED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent leaves a consent-blocked message as it is")
    void shouldKeepConsentBlock_whenCallbackNamesBlockedMessage() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction blocked = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        blocked.markConsentBlocked(SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", "No SMS consent is recorded for this patient."));
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(blocked));

        recorder.recordDeliveryEvent(failedDelivery(Instant.parse("2026-09-22T10:00:00Z")));

        assertThat(blocked.getStatus()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(blocked.getConsentReasonCode()).isEqualTo("SMS_CONSENT_UNKNOWN");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent keeps a scheduled retry when a late failure report arrives, but stops it on delivery")
    void shouldKeepRetry_whenLateFailureArrives_andStopIt_whenDelivered() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction retrying = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        ReflectionTestUtils.setField(retrying, "attemptCount", 1);
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(retrying));

        recorder.recordDeliveryEvent(failedDelivery(Instant.parse("2026-09-22T10:00:00Z")));
        assertThat(retrying.getStatus()).isEqualTo(SmsStatus.QUEUED);

        recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.DELIVERED,
                Instant.parse("2026-09-22T10:01:00Z"), null, null, null));
        assertThat(retrying.getStatus()).isEqualTo(SmsStatus.DELIVERED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent applies a sent report that is newer than the recorded failure")
    void shouldApplySent_whenItIsNewerThanTheFailure() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction sent = acceptedOutboundRow();
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(sent));
        recorder.recordDeliveryEvent(failedDelivery(Instant.parse("2026-09-22T10:00:00Z")));

        recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.SENT,
                Instant.parse("2026-09-22T10:05:00Z"), null, null, null));

        assertThat(sent.getStatus()).isEqualTo(SmsStatus.SENT);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recordDeliveryEvent applies a sent report to a failure CARLOS recorded itself")
    void shouldApplySent_whenFailureDidNotComeFromTheCarrier() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction failedByCarlos = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        failedByCarlos.markSending(new Date());
        failedByCarlos.markProviderResult(
                SmsProviderSendResultDto.failed("QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE", "outcome unknown"));
        when(smsTransactionDao.findByClientReferenceId(SmsProviderType.STUB, "sms-transaction-7"))
                .thenReturn(Optional.of(failedByCarlos));

        recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.SENT, null, null, null, "sms-transaction-7", null));

        assertThat(failedByCarlos.getStatus()).isEqualTo(SmsStatus.SENT);
    }

    @Test
    @DisplayName("recordDeliveryEvent ignores a sent report with no time after a carrier-reported failure")
    void shouldIgnoreUndatedSent_afterCarrierReportedFailure() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction sent = acceptedOutboundRow();
        when(smsTransactionDao.findByProviderMessageId(SmsProviderType.STUB, "provider-1"))
                .thenReturn(Optional.of(sent));
        recorder.recordDeliveryEvent(failedDelivery(Instant.parse("2026-09-22T10:00:00Z")));

        recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.SENT, null, null, null, null));

        assertThat(sent.getStatus()).isEqualTo(SmsStatus.FAILED);
    }

    @Test
    @DisplayName("recordDeliveryEvent publishes when the failure report arrives while the send is still in progress")
    void shouldPublishFailedEvent_whenCallbackArrivesWhileSending() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction sending = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        sending.markSending(new Date());
        when(smsTransactionDao.findByClientReferenceId(SmsProviderType.STUB, "sms-transaction-7"))
                .thenReturn(Optional.of(sending));

        recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, null, SmsStatus.FAILED, Instant.parse("2026-09-22T10:00:00Z"),
                "CARRIER_REJECTED", null, "sms-transaction-7", null));

        assertThat(sending.getStatus()).isEqualTo(SmsStatus.FAILED);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    private static SmsTransaction acceptedOutboundRow() {
        SmsTransaction sent = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        sent.markProviderResult(SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));
        return sent;
    }

    private static SmsDeliveryWebhookDto failedDelivery(Instant eventAt) {
        return new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "provider-1", SmsStatus.FAILED, eventAt,
                "CARRIER_REJECTED", "Carrier rejected the message", null);
    }

    @Test
    @DisplayName("recordDeliveryEvent updates an outbound row matched by client reference")
    void shouldMergeTransaction_whenDeliveryEventMatchesClientReference() {
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction existing = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        SmsTransaction existing = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        Date now = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
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
        JpaSmsTransactionService recorder = new JpaSmsTransactionService(smsTransactionDao, eventPublisher);
        Date staleBefore = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date recoveryAt = Date.from(Instant.parse("2026-06-08T12:05:00Z"));
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
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

    /**
     * The durable cleanup of a retired provider's waiting texts: a text that certainly never went out is queued
     * again, as a new row, for the provider selected now; anything else is failed for staff to review.
     */
    @Nested
    @DisplayName("settling a retired provider's waiting texts")
    class RetiredQueueCleanup {
        private static final long CUTOFF = 50L;
        private static final Instant CONSENT_EDITED_AT = Instant.parse("2026-09-01T14:30:00Z");

        @Mock
        private SmsProviderRetirementService retirementService;

        private final List<SmsTransaction> inserted = new ArrayList<>();

        @Test
        @DisplayName("a never-tried text is queued again for the selected provider and its retired row names the new one")
        void shouldQueueNewRow_whenRetiredTextWasNeverTried() {
            SmsTransaction retired = retiredRow(41L);
            retiredQueue(SmsProviderType.CLOUDLI, retired);

            assertThat(recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10)).isEqualTo(1);

            SmsTransaction moved = singleInsertedRow();
            assertThat(moved).extracting(SmsTransaction::getProviderType, SmsTransaction::getStatus,
                            SmsTransaction::getAttemptCount, SmsTransaction::getProviderMessageId,
                            SmsTransaction::getClientReferenceId)
                    .containsExactly(SmsProviderType.CLOUDLI, SmsStatus.QUEUED, 0, null, "sms-transaction-101");
            assertThat(moved).extracting(SmsTransaction::getDemographicNo,
                            SmsTransaction::getRequestedByHealthcareProviderNo, SmsTransaction::getRequestedBySecurityNo,
                            SmsTransaction::getAppointmentNo, SmsTransaction::getToPhoneNumber,
                            SmsTransaction::getRecipientPhoneType, SmsTransaction::getMessagePurpose,
                            SmsTransaction::getMessageBodyLength, SmsTransaction::getMessageBodySha256)
                    .containsExactly(retired.getDemographicNo(), "999998", 7, 8801, "+14165551212",
                            SmsRecipientPhoneType.CELL, SmsMessagePurpose.APPOINTMENT_REMINDER,
                            retired.getMessageBodyLength(), retired.getMessageBodySha256());
            assertThat(moved.toSendCommand().body()).isEqualTo("FAKE reminder for tomorrow");
            assertThat(moved).as("the consent snapshot the text was queued on; the worker checks it again")
                    .extracting(SmsTransaction::getConsentStatus, SmsTransaction::getConsentId)
                    .containsExactly(SmsConsentStatus.OPT_IN, 5);
            assertThat(moved.getConsentLastUpdateDate()).isEqualTo(Date.from(CONSENT_EDITED_AT));
            assertThat(moved.getNextAttemptAt()).as("due now").isBeforeOrEqualTo(new Date());
            assertThat(moved.getErrorCode()).isNull();

            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode,
                            SmsTransaction::getErrorMessage, SmsTransaction::getProviderType,
                            SmsTransaction::getNextAttemptAt)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_MOVED", "Moved to CLOUDLI as text 101.",
                            SmsProviderType.STUB, null);
            assertThat(retired.getClientReferenceId()).isEqualTo("sms-transaction-41");
            verify(eventPublisher, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("a text the provider definitely refused, waiting for its retry, is moved and keeps its attempts")
        void shouldMoveRow_whenRetiredTextWaitsAfterDefiniteRefusal() {
            SmsTransaction retired = retiredRow(42L);
            retired.markSending(new Date());
            retired.markRetryScheduled(SmsProviderSendResultDto.failed("QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED",
                    "SMS queued provider failure recorded; retry scheduled."), Date.from(Instant.now().plusSeconds(600)));
            retiredQueue(SmsProviderType.CLOUDLI, retired);

            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(singleInsertedRow()).extracting(SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                    .containsExactly(0, null);
            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode,
                            SmsTransaction::getAttemptCount)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_MOVED", 1);
        }

        @ParameterizedTest
        @EnumSource(value = SmsProviderErrorCode.class, mode = EnumSource.Mode.EXCLUDE,
                names = {"INVALID_RECIPIENT", "RECIPIENT_OPTED_OUT", "MESSAGE_REJECTED"})
        @DisplayName("a text waiting for its retry under a fixed provider code is moved")
        void shouldMoveRow_whenRetiredTextWaitsUnderFixedProviderCode(SmsProviderErrorCode code) {
            SmsTransaction retired = retiredRow(43L);
            retired.markSending(new Date());
            retired.markRetryScheduled(SmsProviderSendResultDto.failed(code.name(), code.message()),
                    Date.from(Instant.now().plusSeconds(600)));
            retiredQueue(SmsProviderType.CLOUDLI, retired);

            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(singleInsertedRow()).extracting(SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                    .containsExactly(0, null);
            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_MOVED");
        }

        @Test
        @DisplayName("a text handed back because its provider asked CARLOS to slow down is moved")
        void shouldMoveRow_whenRetiredTextWaitsAfterRateLimitHandBack() {
            SmsTransaction retired = retiredRow(45L);
            retired.markSending(new Date());
            retired.markRetryScheduled(SmsProviderSendResultDto.failed(SmsProviderErrorCode.PROVIDER_UNAVAILABLE),
                    new Date());
            retired.markSending(new Date());
            retired.markClaimReleased(Date.from(Instant.now().plusSeconds(60)), SmsProviderErrorCode.RATE_LIMITED);
            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount,
                    SmsTransaction::getErrorCode).containsExactly(SmsStatus.QUEUED, 1, "RATE_LIMITED");
            assertThat(SmsProviderRetirementService.movable(retired)).isTrue();
            retiredQueue(SmsProviderType.CLOUDLI, retired);

            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(singleInsertedRow()).extracting(SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                    .containsExactly(0, null);
            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_MOVED");
        }

        @Test
        @DisplayName("every fixed code the queue retries counts as definitely unsent; no permanent code does")
        void shouldTreatOnlyRetriedFixedCodes_asDefinitelyUnsent() {
            for (SmsProviderErrorCode code : SmsProviderErrorCode.values()) {
                SmsTransaction row = retiredRow(70L + code.ordinal());
                row.markSending(new Date());
                row.markRetryScheduled(SmsProviderSendResultDto.failed(code.name(), code.message()), new Date());
                assertThat(SmsProviderRetirementService.movable(row)).as(code.name()).isEqualTo(!code.permanent());
            }
        }

        @Test
        @DisplayName("a text whose consent check failed before any provider call is moved")
        void shouldMoveRow_whenRetiredTextWaitsAfterConsentCheckFailure() {
            SmsTransaction retired = retiredRow(43L);
            retired.markSending(new Date());
            retired.markRetryScheduled(SmsProviderSendResultDto.failed("QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED",
                    "SMS consent could not be checked before sending; retry scheduled."), new Date());
            retiredQueue(SmsProviderType.CLOUDLI, retired);

            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(inserted).hasSize(1);
            assertThat(retired.getErrorCode()).isEqualTo("QUEUE_PROVIDER_MOVED");
        }

        @Test
        @DisplayName("a text rescheduled after a stale send the provider did not find is failed for review, not moved")
        void shouldFailRow_whenRetiredTextWasRescheduledAfterNotFoundLookup() {
            SmsTransaction retired = retiredRow(44L);
            retired.markSending(new Date());
            retired.markStaleRecoveryStarted(new Date());
            retired.markRetryScheduled(SmsProviderSendResultDto.failed("QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED",
                    "SMS stale send was not found by SMS provider status lookup; retry scheduled."), new Date());
            retiredQueue(SmsProviderType.CLOUDLI, retired);

            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(inserted).isEmpty();
            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_NOT_ACTIVE");
            verify(eventPublisher).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("a text that went unanswered and was then refused stays marked, and is failed for review, not moved")
        void shouldFailRow_whenNotFoundTextWasRefusedLater() {
            SmsTransaction retired = retiredRow(45L);
            retired.markSending(new Date());
            retired.markStaleRecoveryStarted(new Date());
            retired.markRetryScheduled(SmsProviderSendResultDto.failed("QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED",
                    "SMS stale send was not found by SMS provider status lookup; retry scheduled."), new Date());
            retired.markSending(new Date());
            when(smsTransactionDao.find(45L)).thenReturn(retired);
            Date nextAttemptAt = Date.from(Instant.now().plusSeconds(600));

            recorder().markRetryScheduled(retired, SmsProviderSendResultDto.failed(
                    "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED",
                    "SMS queued provider failure recorded; retry scheduled."), nextAttemptAt);

            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode,
                            SmsTransaction::getErrorMessage, SmsTransaction::getNextAttemptAt)
                    .containsExactly(SmsStatus.QUEUED, "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED",
                            "SMS stale send was not found by SMS provider status lookup; retry scheduled.",
                            nextAttemptAt);

            retiredQueue(SmsProviderType.CLOUDLI, retired);
            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(inserted).isEmpty();
            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_NOT_ACTIVE");
        }

        @Test
        @DisplayName("a rate-limited hand-back keeps the not-found mark, and the text is failed for review, not moved")
        void shouldKeepNotFoundMark_whenRateLimitedHandBackFollowsIt() {
            SmsTransaction retired = retiredRow(47L);
            retired.markSending(new Date());
            retired.markStaleRecoveryStarted(new Date());
            retired.markRetryScheduled(SmsProviderSendResultDto.failed("QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED",
                    "SMS stale send was not found by SMS provider status lookup; retry scheduled."), new Date());
            retired.markSending(new Date());
            int attemptsBeforeClaim = retired.getAttemptCount() - 1;
            when(smsTransactionDao.find(47L)).thenReturn(retired);
            Date dueAt = Date.from(Instant.now().plusSeconds(60));

            recorder().releaseClaim(retired, dueAt, SmsProviderErrorCode.RATE_LIMITED);

            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode,
                            SmsTransaction::getAttemptCount, SmsTransaction::getNextAttemptAt)
                    .containsExactly(SmsStatus.QUEUED, "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED",
                            attemptsBeforeClaim, dueAt);
            assertThat(SmsProviderRetirementService.movable(retired)).isFalse();

            retiredQueue(SmsProviderType.CLOUDLI, retired);
            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(inserted).isEmpty();
            assertThat(retired).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_NOT_ACTIVE");
        }

        @Test
        @DisplayName("a text that was only ever refused is moved after the refusal is recorded")
        void shouldMoveRow_whenRefusalIsRecordedWithoutNotFoundTry() {
            SmsTransaction retired = retiredRow(46L);
            retired.markSending(new Date());
            when(smsTransactionDao.find(46L)).thenReturn(retired);

            recorder().markRetryScheduled(retired, SmsProviderSendResultDto.failed(
                    "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED",
                    "SMS queued provider failure recorded; retry scheduled."), new Date());
            assertThat(retired.getErrorCode()).isEqualTo("QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED");

            retiredQueue(SmsProviderType.CLOUDLI, retired);
            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(inserted).hasSize(1);
            assertThat(retired.getErrorCode()).isEqualTo("QUEUE_PROVIDER_MOVED");
        }

        @Test
        @DisplayName("a held system test of the retired provider keeps its own failure and is never moved")
        void shouldFailSystemTest_whenItsProviderWasRetired() {
            SmsTransaction systemTest = SmsTransaction.outboundAttempt(new SmsSendCommand(null, "416-555-1212",
                    SmsRecipientPhoneType.CELL, SmsSendService.SYSTEM_TEST_BODY, SmsMessagePurpose.SYSTEM_TEST,
                    "999998", 1, null), SmsProviderType.STUB);
            assignId(systemTest, 45L);
            retiredQueue(SmsProviderType.CLOUDLI, systemTest);

            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            assertThat(inserted).isEmpty();
            assertThat(systemTest).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_SYSTEM_TEST_PROVIDER_CHANGED");
        }

        @Test
        @DisplayName("a text of a provider reselected before cleanup ran still gets a new row above the boundary")
        void shouldQueueNewRow_whenRetiredProviderIsSelectedAgain() {
            SmsTransaction retired = retiredRow(46L);
            retiredQueue(SmsProviderType.STUB, retired);

            recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10);

            SmsTransaction moved = singleInsertedRow();
            assertThat(moved.getProviderType()).isEqualTo(SmsProviderType.STUB);
            assertThat(moved.getId()).isGreaterThan(CUTOFF);
            assertThat(retired.getErrorMessage()).isEqualTo("Moved to STUB as text 101.");
        }

        @Test
        @DisplayName("a run settles no more retired rows than its limit")
        void shouldSettleAtMostLimit_whenBacklogIsLarger() {
            SmsTransaction first = retiredRow(47L);
            SmsTransaction second = retiredRow(48L);
            SmsTransaction third = retiredRow(49L);
            retiredQueue(SmsProviderType.CLOUDLI, first, second, third);

            assertThat(recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 2)).isEqualTo(2);

            assertThat(inserted).hasSize(2);
            assertThat(List.of(first, second)).extracting(SmsTransaction::getErrorCode)
                    .containsOnly("QUEUE_PROVIDER_MOVED");
            assertThat(third.getStatus()).isEqualTo(SmsStatus.QUEUED);
        }

        @Test
        @DisplayName("a failed insert leaves the retired row waiting, and the next row is still moved")
        void shouldLeaveRowQueued_whenNewRowCannotBeWritten() {
            SmsTransaction faulty = retiredRow(47L);
            SmsTransaction healthy = retiredRow(48L);
            retiredQueue(SmsProviderType.CLOUDLI, faulty, healthy);
            doThrow(new IllegalStateException("FAKE insert failure")).doAnswer(invocation -> {
                insert(invocation.getArgument(0));
                return null;
            }).when(smsTransactionDao).persist(any());

            assertThat(recorder().moveOrFailRetiredOutboundQueue(SmsProviderType.STUB, 10)).isEqualTo(1);

            assertThat(faulty).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                    .containsExactly(SmsStatus.QUEUED, null);
            assertThat(healthy.getErrorCode()).isEqualTo("QUEUE_PROVIDER_MOVED");
        }

        @Test
        @DisplayName("a text that may already have gone out is never moved")
        void shouldNotMoveRow_whenTextMayHaveGoneOut() {
            SmsTransaction sending = retiredRow(60L);
            sending.markSending(new Date());
            SmsTransaction accepted = retiredRow(61L);
            accepted.markSending(new Date());
            accepted.markRetryScheduled(new SmsProviderSendResultDto(false, "FAKE-provider-id", SmsStatus.FAILED,
                    "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED", "FAKE"), new Date());
            SmsTransaction reported = retiredRow(62L);
            // A provider callback was applied to it, so the provider has seen the text.
            ReflectionTestUtils.setField(reported, "providerEventAt", Date.from(Instant.parse("2026-10-01T12:00:00Z")));
            SmsTransaction unrecognized = retiredRow(63L);
            unrecognized.markSending(new Date());
            unrecognized.markRetryScheduled(SmsProviderSendResultDto.failed("FAKE_UNKNOWN_RETRY", "FAKE"), new Date());
            SmsTransaction noCode = retiredRow(64L);
            ReflectionTestUtils.setField(noCode, "attemptCount", 1);

            assertThat(List.of(sending, accepted, reported, unrecognized, noCode))
                    .noneMatch(SmsProviderRetirementService::movable);
            assertThat(SmsProviderRetirementService.movable(retiredRow(65L))).as("a never-tried text").isTrue();
        }

        private JpaSmsTransactionService recorder() {
            return new JpaSmsTransactionService(smsTransactionDao, eventPublisher, NOOP_TRANSACTION_MANAGER,
                    retirementService);
        }

        /** The retired STUB queue, in id order, as the locked DAO query returns it: one waiting row at a time. */
        private void retiredQueue(SmsProviderType selected, SmsTransaction... rows) {
            when(retirementService.lockSelection()).thenReturn(Optional.empty());
            when(retirementService.retiredThrough(SmsProviderType.STUB)).thenReturn(CUTOFF);
            when(retirementService.selectedProvider(any())).thenReturn(selected);
            when(smsTransactionDao.findRetiredQueuedForUpdate(eq(SmsProviderType.STUB), eq(CUTOFF), anyBoolean(),
                    any(), any())).thenAnswer(invocation -> {
                        List<Long> excluded = invocation.getArgument(4);
                        return java.util.Arrays.stream(rows)
                                .filter(row -> row.getStatus() == SmsStatus.QUEUED && !excluded.contains(row.getId()))
                                .limit(1).toList();
                    });
            org.mockito.Mockito.lenient().doAnswer(invocation -> {
                insert(invocation.getArgument(0));
                return null;
            }).when(smsTransactionDao).persist(any());
        }

        /** As the IDENTITY insert does: the new row gets the next id, above every boundary. */
        private void insert(SmsTransaction row) {
            ReflectionTestUtils.setField(row, "id", 101L + inserted.size());
            inserted.add(row);
        }

        private SmsTransaction singleInsertedRow() {
            assertThat(inserted).hasSize(1);
            return inserted.get(0);
        }

        private SmsTransaction retiredRow(long id) {
            SmsTransaction row = SmsTransaction.outboundAttempt(new SmsSendCommand(123, "416-555-1212",
                    SmsRecipientPhoneType.CELL, "FAKE reminder for tomorrow", SmsMessagePurpose.APPOINTMENT_REMINDER,
                    "999998", 7, 8801), SmsProviderType.STUB);
            row.recordConsentDecision(SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, 5, CONSENT_EDITED_AT));
            assignId(row, id);
            return row;
        }
    }

    private static SmsTransaction claimedRowWithConsentSnapshot(long id, long version) {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(transaction, id);
        assignVersion(transaction, version);
        transaction.recordConsentDecision(SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 5, Instant.parse("2026-09-01T14:30:00Z")));
        transaction.markSending(Date.from(Instant.parse("2026-09-10T09:05:00Z")));
        return transaction;
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

    private static void assignVersion(SmsTransaction transaction, long version) {
        try {
            Field versionField = SmsTransaction.class.getDeclaredField("version");
            versionField.setAccessible(true);
            versionField.set(transaction, version);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to assign SMS transaction version for test", e);
        }
    }
}
