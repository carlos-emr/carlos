package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsQueueWorkerUnitTest {
    private static final Instant FIRST_ATTEMPT_AT = Instant.parse("2026-06-08T12:00:00Z");
    private static final Instant RETRY_SCHEDULED_AT = Instant.parse("2026-06-08T12:05:00Z");
    private static final Instant SECOND_ATTEMPT_AT = Instant.parse("2026-06-08T12:10:00Z");

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
    @DisplayName("processDueMessages drains queued rows for a non-STUB provider")
    void shouldSendMessage_whenQueueItemIsForNonStubProvider() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.VOIPMS
        );
        assignId(transaction, 1L);
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new VoipMsAcceptingProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderType)
                .containsExactly(SmsStatus.SENT, SmsProviderType.VOIPMS);
        assertThat(transaction.getProviderMessageId()).isEqualTo("provider-voipms-1");
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
    @DisplayName("processDueMessages schedules retry for failed SMS provider attempts")
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
                .containsExactly(SmsStatus.QUEUED, 1, "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED");
        assertThat(transaction.getErrorMessage())
                .isEqualTo("SMS queued provider failure recorded; retry scheduled.");
        assertThat(transaction.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("processDueMessages marks final failure after retry limit")
    void shouldMarkFailed_whenRetryLimitIsReached() {
        SmsTransaction transaction = queuedTransaction();
        scheduleTwoFailedAttempts(transaction);
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
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getErrorCode,
                        SmsTransaction::getErrorMessage,
                        SmsTransaction::getNextAttemptAt
                )
                .containsExactly(
                        SmsStatus.FAILED,
                        3,
                        "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED",
                        "SMS queued provider failure reached retry limit; no further retry scheduled.",
                        null
                );
    }

    @Test
    @DisplayName("processDueMessages records queue SMS provider exceptions distinctly")
    void shouldScheduleRetry_whenQueuedProviderThrows() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new ThrowingProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.QUEUED, 1, "QUEUE_PROVIDER_EXCEPTION_RETRY_SCHEDULED");
        assertThat(transaction.getErrorMessage())
                .isEqualTo("SMS queued provider exception recorded; retry scheduled.");
    }

    @Test
    @DisplayName("processDueMessages records SMS provider resolution exceptions distinctly")
    void shouldScheduleRetry_whenQueuedProviderResolutionThrows() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of()),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.QUEUED, 1, "QUEUE_PROVIDER_EXCEPTION_RETRY_SCHEDULED");
        assertThat(transaction.getErrorMessage())
                .isEqualTo("SMS queued provider exception recorded; retry scheduled.");
    }

    @Test
    @DisplayName("processDueMessages marks final queue SMS provider exceptions distinctly")
    void shouldMarkFailed_whenQueuedProviderThrowsAtRetryLimit() {
        SmsTransaction transaction = queuedTransaction();
        scheduleTwoFailedAttempts(transaction);
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new ThrowingProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getErrorCode,
                        SmsTransaction::getErrorMessage,
                        SmsTransaction::getNextAttemptAt
                )
                .containsExactly(
                        SmsStatus.FAILED,
                        3,
                        "QUEUE_PROVIDER_EXCEPTION_RETRY_EXHAUSTED",
                        "SMS queued provider exception reached retry limit; no further retry scheduled.",
                        null
                );
    }

    @Test
    @DisplayName("processDueMessages reconciles stale sending rows when SMS provider status is found")
    void shouldMarkSent_whenStaleProviderStatusIsFound() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date(0));
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new FoundStatusProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.SENT, 1, null);
        assertThat(transaction.getProviderMessageId()).isEqualTo("provider-1");
    }

    @Test
    @DisplayName("processDueMessages retries stale sending rows when SMS provider status is not found")
    void shouldScheduleRetry_whenStaleProviderStatusIsNotFound() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date(0));
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new NotFoundStatusProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.QUEUED, 1, "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED");
        assertThat(transaction.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("processDueMessages fails stale sending rows when SMS provider status is not found at retry limit")
    void shouldMarkFailed_whenStaleProviderStatusIsNotFoundAtRetryLimit() {
        SmsTransaction transaction = queuedTransaction();
        scheduleTwoFailedAttempts(transaction);
        transaction.markSending(new Date(0));
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of(new NotFoundStatusProviderClient())),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, 3, "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED");
    }

    @Test
    @DisplayName("processDueMessages fails stale sending rows when SMS provider status lookup is unavailable")
    void shouldMarkFailed_whenStaleProviderStatusLookupIsUnavailable() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date(0));
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
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, 1, "PROVIDER_STATUS_LOOKUP_UNSUPPORTED");
    }

    @Test
    @DisplayName("processDueMessages fails stale sending rows when SMS provider resolution fails")
    void shouldMarkFailed_whenStaleProviderResolutionThrows() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date(0));
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder(List.of(transaction));
        SmsQueueWorker worker = new SmsQueueWorker(
                recorder,
                new SmsProviderResolver(List.of()),
                new SmsRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, 1, "QUEUE_STALE_STATUS_LOOKUP_EXCEPTION");
    }

    private static void scheduleTwoFailedAttempts(SmsTransaction transaction) {
        transaction.markSending(dateAt(FIRST_ATTEMPT_AT));
        transaction.markRetryScheduled(
                SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message"),
                dateAt(RETRY_SCHEDULED_AT)
        );
        transaction.markSending(dateAt(SECOND_ATTEMPT_AT));
        transaction.markRetryScheduled(
                SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message"),
                dateAt(RETRY_SCHEDULED_AT)
        );
    }

    private static SmsTransaction queuedTransaction() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(transaction, 1L);
        return transaction;
    }

    private static Date dateAt(Instant instant) {
        return Date.from(instant);
    }

    private static class RecordingSmsTransactionRecorder implements SmsTransactionRecorder {
        private final List<SmsTransaction> transactions;

        private RecordingSmsTransactionRecorder(List<SmsTransaction> transactions) {
            this.transactions = new ArrayList<>(transactions);
        }

        @Override
        public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType) {
            SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
            assignId(transaction, transactions.size() + 1L);
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
            return transactions.stream()
                    .filter(transaction -> transaction.getProviderType() == providerType)
                    .filter(transaction -> transaction.getStatus() == SmsStatus.QUEUED)
                    .filter(transaction ->
                            transaction.getNextAttemptAt() == null || !transaction.getNextAttemptAt().after(now)
                    )
                    .peek(transaction -> transaction.markSending(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<SmsTransaction> claimStaleSendingForRecovery(
                SmsProviderType providerType,
                Date staleBefore,
                Date recoveryAt,
                int limit
        ) {
            List<SmsTransaction> staleTransactions = transactions.stream()
                    .filter(transaction -> transaction.getProviderType() == providerType)
                    .filter(transaction -> transaction.getStatus() == SmsStatus.SENDING)
                    .filter(transaction -> transaction.getLastAttemptAt() != null)
                    .filter(transaction -> transaction.getLastAttemptAt().before(staleBefore))
                    .limit(limit)
                    .toList();
            staleTransactions.forEach(transaction -> transaction.markStaleRecoveryStarted(recoveryAt));
            return staleTransactions;
        }
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

    private static class VoipMsAcceptingProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderType providerType() {
            return SmsProviderType.VOIPMS;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command) {
            return SmsProviderSendResultDto.accepted("provider-voipms-1", SmsStatus.SENT);
        }
    }

    private static class FailingProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command) {
            return SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message");
        }
    }

    private static class ThrowingProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command) {
            throw new IllegalStateException("provider unavailable");
        }
    }

    private static class FoundStatusProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderMessageStatusDto lookupMessageStatus(String clientReferenceId, String providerMessageId) {
            return SmsProviderMessageStatusDto.found(
                    SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT)
            );
        }
    }

    private static class NotFoundStatusProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderMessageStatusDto lookupMessageStatus(String clientReferenceId, String providerMessageId) {
            return SmsProviderMessageStatusDto.notFound();
        }
    }
}
