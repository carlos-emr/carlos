package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
class SmsQueueServiceUnitTest {
    private static final SmsConsentDecisionDto CONSENTED = SmsConsentDecisionDto.permitted(
            SmsConsentStatus.OPT_IN, 4321, Instant.parse("2026-09-01T14:30:00Z"));

    @Test
    @DisplayName("enqueue returns queued after validation and consent pass")
    void shouldQueueMessage_whenConsentAllows() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsQueueProcessingService worker = mock(SmsQueueProcessingService.class);
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> CONSENTED,
                recorder,
                worker,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.enqueue(
                SmsSendCommand.patientMessage(
                        123,
                        "416-555-1212",
                        SmsRecipientPhoneType.HOME,
                        "Appointment reminder",
                        "999998"
                )
        );

        assertThat(result)
                .extracting(SmsSendResultDto::accepted, SmsSendResultDto::status)
                .containsExactly(true, SmsStatus.QUEUED);
        assertThat(recorder.transactions()).singleElement()
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getProviderType,
                        SmsTransaction::getRecipientPhoneType
                )
                .containsExactly(SmsStatus.QUEUED, SmsProviderType.STUB, SmsRecipientPhoneType.HOME);
        verify(worker, never()).processDueMessages(anyInt());
    }

    @Test
    @DisplayName("enqueueAndProcessNow wakes the queue worker after queuing")
    void shouldWakeWorker_whenMessageIsQueuedForImmediateProcessing() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsQueueProcessingService worker = mock(SmsQueueProcessingService.class);
        when(worker.processDueMessages(1)).thenReturn(1);
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> CONSENTED,
                recorder,
                worker,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.enqueueAndProcessNow(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998")
        );

        assertThat(result)
                .extracting(SmsSendResultDto::accepted, SmsSendResultDto::status)
                .containsExactly(true, SmsStatus.QUEUED);
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderType)
                .containsExactly(SmsStatus.QUEUED, SmsProviderType.STUB);
        verify(worker).processDueMessages(1);
    }

    @Test
    @DisplayName("enqueueAndProcessNow keeps queued result when the immediate worker wake fails")
    void shouldReturnQueued_whenImmediateWorkerWakeFails() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsQueueProcessingService worker = mock(SmsQueueProcessingService.class);
        when(worker.processDueMessages(1)).thenThrow(new IllegalStateException("worker unavailable"));
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> CONSENTED,
                recorder,
                worker,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.enqueueAndProcessNow(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998")
        );

        assertThat(result)
                .extracting(SmsSendResultDto::accepted, SmsSendResultDto::status)
                .containsExactly(true, SmsStatus.QUEUED);
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus)
                .isEqualTo(SmsStatus.QUEUED);
        verify(worker).processDueMessages(1);
    }

    @Test
    @DisplayName("enqueue records consent blocks without sending")
    void shouldBlockMessage_whenConsentDeniesQueue() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsQueueProcessingService worker = mock(SmsQueueProcessingService.class);
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.blocked(
                        SmsStatus.CONSENT_BLOCKED,
                        "SMS_CONSENT_UNKNOWN",
                        "No SMS consent is recorded for this patient."
                ),
                recorder,
                worker,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.enqueue(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998")
        );

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getConsentReasonCode)
                .containsExactly(SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN");
        verify(worker, never()).processDueMessages(anyInt());
    }

    @Test
    @DisplayName("enqueueAndProcessNow does not wake worker when consent blocks")
    void shouldSkipWorkerWake_whenConsentDeniesImmediateQueue() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsQueueProcessingService worker = mock(SmsQueueProcessingService.class);
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.blocked(
                        SmsStatus.CONSENT_BLOCKED,
                        "SMS_CONSENT_UNKNOWN",
                        "No SMS consent is recorded for this patient."
                ),
                recorder,
                worker,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.enqueueAndProcessNow(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998")
        );

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        verify(worker, never()).processDueMessages(anyInt());
    }

    @Test
    @DisplayName("enqueue does not create transactions for invalid commands")
    void shouldSkipTransactionRecord_whenQueueValidationFails() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsQueueProcessingService worker = mock(SmsQueueProcessingService.class);
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> CONSENTED,
                recorder,
                worker,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.enqueue(SmsSendCommand.patientMessage(0, "not-a-phone", " ", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.FAILED);
        assertThat(recorder.transactions()).isEmpty();
        verify(worker, never()).processDueMessages(anyInt());
    }

    @Test
    @DisplayName("enqueueAndProcessNow does not wake worker after validation failures")
    void shouldSkipWorkerWake_whenImmediateQueueValidationFails() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsQueueProcessingService worker = mock(SmsQueueProcessingService.class);
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> CONSENTED,
                recorder,
                worker,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.enqueueAndProcessNow(
                SmsSendCommand.patientMessage(0, "not-a-phone", " ", "999998")
        );

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.FAILED);
        assertThat(recorder.transactions()).isEmpty();
        verify(worker, never()).processDueMessages(anyInt());
    }

    @Test
    @DisplayName("queue admission evaluates consent before persisting a claimable row")
    void shouldNotExposeQueuedWork_whenConsentEvaluationThrows() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsQueueProcessingService worker = mock(SmsQueueProcessingService.class);
        SmsQueueService service = new SmsQueueService(new SmsSendValidator(), command -> {
            assertThat(recorder.transactions()).isEmpty();
            throw new IllegalStateException("synthetic consent outage");
        }, recorder, worker, new SmsDefaultProviderResolver(() -> "STUB"));

        SmsSendCommand command = SmsSendCommand.patientMessage(123, "416-555-1212", "Synthetic message", "999998");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enqueueAndProcessNow(command))
                .isInstanceOf(IllegalStateException.class);
        assertThat(recorder.transactions()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(worker);
    }

    private static class RecordingSmsTransactionService implements SmsTransactionService {
        private final List<SmsTransaction> transactions = new ArrayList<>();

        @Override
        public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType,
                                                    SmsConsentDecisionDto decision) {
            SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
            transactions.add(transaction);
            if (decision.allowed()) {
                transaction.recordConsentDecision(decision);
            } else {
                transaction.markConsentBlocked(decision);
            }
            return transaction;
        }

        @Override
        public SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision) {
            transaction.markConsentBlocked(decision);
            return transaction;
        }

        @Override
        public SmsTransaction recordConsentDecision(SmsTransaction transaction, SmsConsentDecisionDto decision) {
            transaction.recordConsentDecision(decision);
            return transaction;
        }

        @Override
        public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
            transaction.markSending(attemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
            transaction.markProviderResult(providerResult);
            return transaction;
        }

        @Override
        public SmsTransaction markRetryScheduled(
                SmsTransaction transaction,
                SmsProviderSendResultDto providerResult,
                Date nextAttemptAt
        ) {
            transaction.markRetryScheduled(providerResult, nextAttemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
            transaction.markClaimReleased(dueAt);
            return transaction;
        }

        @Override
        public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
            SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook) {
            SmsTransaction transaction = SmsTransaction.deliveryEvent(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
            return List.of();
        }

        @Override
        public List<SmsTransaction> claimStaleSendingForRecovery(
                SmsProviderType providerType,
                Date staleBefore,
                Date recoveryAt,
                int limit
        ) {
            return List.of();
        }

        private List<SmsTransaction> transactions() {
            return transactions;
        }
    }
}
