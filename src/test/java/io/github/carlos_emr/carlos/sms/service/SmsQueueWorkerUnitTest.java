package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsQueueWorkerUnitTest {
    @Test
    @DisplayName("processDueMessages sends queued messages when rate limit permits")
    void shouldSendMessage_whenQueueItemIsDue() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.SENT, 1);
        assertThat(transaction.getProviderMessageId()).isEqualTo("provider-1");
    }

    @Test
    @DisplayName("processDueMessages leaves queued messages untouched when rate limited")
    void shouldSkipMessage_whenRateLimitIsExceeded() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
    }

    @Test
    @DisplayName("processDueMessages schedules retry for failed provider attempts")
    void shouldScheduleRetry_whenProviderFailsBeforeMaxAttempts() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new FailingProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.QUEUED, 1, "PROVIDER_ERROR");
        assertThat(transaction.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("processDueMessages marks final failure after retry limit")
    void shouldMarkFailed_whenRetryLimitIsReached() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date());
        transaction.markRetryScheduled(SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message"), new Date());
        transaction.markSending(new Date());
        transaction.markRetryScheduled(SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message"), new Date());
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new FailingProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getNextAttemptAt)
                .containsExactly(SmsStatus.FAILED, 3, null);
    }

    private static SmsTransaction queuedTransaction() {
        return SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
    }

    private static class RecordingSmsTransactionRecorder implements SmsTransactionRecorder {
        private final List<SmsTransaction> transactions;

        private RecordingSmsTransactionRecorder(List<SmsTransaction> transactions) {
            this.transactions = new ArrayList<>(transactions);
        }

        @Override
        public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType) {
            SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision) {
            transaction.markConsentBlocked(decision);
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
        public List<SmsTransaction> findDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
            return transactions.stream()
                    .filter(transaction -> transaction.getStatus() == SmsStatus.QUEUED)
                    .limit(limit)
                    .toList();
        }
    }

    private static class AcceptingProviderClient implements SmsProviderClient {
        @Override
        public SmsProviderType providerType() {
            return SmsProviderType.STUB;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command) {
            return SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT);
        }

        @Override
        public boolean validateCallback(String payload, Map<String, String> headers, String secret) {
            return false;
        }

        @Override
        public Optional<SmsInboundWebhookDto> parseInboundWebhook(String payload, Map<String, String> headers) {
            return Optional.empty();
        }

        @Override
        public Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(String payload, Map<String, String> headers) {
            return Optional.empty();
        }
    }

    private static class FailingProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command) {
            return SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message");
        }
    }
}
