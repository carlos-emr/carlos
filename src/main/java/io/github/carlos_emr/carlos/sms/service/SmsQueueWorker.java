package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class SmsQueueWorker {
    private static final SmsProviderType DEFAULT_PROVIDER_TYPE = SmsProviderType.STUB;
    // Per-run cap for worker calls without an explicit limit; tune with provider throughput and queue volume.
    private static final int DEFAULT_BATCH_SIZE = 60;
    private static final Duration DEFAULT_STALE_SENDING_TIMEOUT = Duration.ofMinutes(5);
    private static final String QUEUE_PROVIDER_EXCEPTION_CODE = "QUEUE_PROVIDER_EXCEPTION";
    private static final String QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_CODE =
            "QUEUE_STALE_STATUS_LOOKUP_EXCEPTION";
    private static final String QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_CODE =
            "QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_CODE =
            "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_CODE =
            "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED";
    private static final String QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED_CODE =
            "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED";
    private static final String QUEUE_PROVIDER_EXCEPTION_RETRY_SCHEDULED_CODE =
            "QUEUE_PROVIDER_EXCEPTION_RETRY_SCHEDULED";
    private static final String QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED_CODE =
            "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED";
    private static final String QUEUE_PROVIDER_EXCEPTION_RETRY_EXHAUSTED_CODE =
            "QUEUE_PROVIDER_EXCEPTION_RETRY_EXHAUSTED";
    private static final String QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED_MESSAGE =
            "SMS queued provider failure recorded; retry scheduled.";
    private static final String QUEUE_PROVIDER_EXCEPTION_RETRY_SCHEDULED_MESSAGE =
            "SMS queued provider exception recorded; retry scheduled.";
    private static final String QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED_MESSAGE =
            "SMS queued provider failure reached retry limit; no further retry scheduled.";
    private static final String QUEUE_PROVIDER_EXCEPTION_RETRY_EXHAUSTED_MESSAGE =
            "SMS queued provider exception reached retry limit; no further retry scheduled.";
    private static final String QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_MESSAGE =
            "SMS stale send status lookup threw an exception; marked failed for manual review.";
    private static final String QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_MESSAGE =
            "SMS stale send status lookup was unavailable; marked failed for manual review.";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_MESSAGE =
            "SMS stale send was not found by provider status lookup; retry scheduled.";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_MESSAGE =
            "SMS stale send was not found by provider status lookup and retry limit was reached.";

    private final SmsTransactionRecorder transactionRecorder;
    private final SmsProviderResolver providerResolver;
    private final SmsRetryPolicy retryPolicy;
    private final SmsSendRateLimiter rateLimiter;

    public SmsQueueWorker(
            SmsTransactionRecorder transactionRecorder,
            SmsProviderResolver providerResolver,
            SmsRetryPolicy retryPolicy,
            SmsSendRateLimiter rateLimiter
    ) {
        this.transactionRecorder = transactionRecorder;
        this.providerResolver = providerResolver;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
    }

    public int processDueMessages() {
        return processDueMessages(DEFAULT_BATCH_SIZE);
    }

    public int processDueMessages(int limit) {
        int processed = 0;
        int safeLimit = Math.max(1, limit);
        recoverStaleSending(safeLimit);
        boolean shouldContinue = true;
        while (processed < safeLimit && shouldContinue) {
            if (!rateLimiter.tryAcquire(DEFAULT_PROVIDER_TYPE)) {
                shouldContinue = false;
            } else {
                List<SmsTransaction> transactions = transactionRecorder.claimDueOutboundQueue(
                        DEFAULT_PROVIDER_TYPE,
                        new Date(),
                        1
                );
                if (transactions.isEmpty()) {
                    shouldContinue = false;
                } else {
                    processTransaction(transactions.get(0));
                    processed++;
                }
            }
        }
        return processed;
    }

    private void recoverStaleSending(int limit) {
        Date recoveryAt = new Date();
        Date staleBefore = new Date(recoveryAt.getTime() - DEFAULT_STALE_SENDING_TIMEOUT.toMillis());
        List<SmsTransaction> transactions = transactionRecorder.claimStaleSendingForRecovery(
                DEFAULT_PROVIDER_TYPE,
                staleBefore,
                recoveryAt,
                limit
        );
        transactions.forEach(this::recoverStaleTransaction);
    }

    private void recoverStaleTransaction(SmsTransaction transaction) {
        SmsProviderMessageStatusDto status = lookupProviderStatus(transaction);
        if (status.isFound()) {
            transactionRecorder.markProviderResult(transaction, status.providerResult());
            return;
        }

        if (status.isNotFound()) {
            handleStaleProviderNotFound(transaction);
            return;
        }

        transactionRecorder.markProviderResult(
                transaction,
                SmsProviderSendResultDto.failed(
                        status.errorCode() == null ? QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_CODE : status.errorCode(),
                        status.errorMessage() == null
                                ? QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_MESSAGE
                                : status.errorMessage()
                )
        );
    }

    private SmsProviderMessageStatusDto lookupProviderStatus(SmsTransaction transaction) {
        try {
            SmsProviderClient providerClient = providerResolver.resolve(transaction.getProviderType());
            return providerClient.lookupMessageStatus(
                    clientReferenceId(transaction),
                    transaction.getProviderMessageId()
            );
        } catch (RuntimeException e) {
            return SmsProviderMessageStatusDto.unavailable(
                    QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_CODE,
                    QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_MESSAGE
            );
        }
    }

    private void handleStaleProviderNotFound(SmsTransaction transaction) {
        if (!retryPolicy.canRetry(transaction)) {
            transactionRecorder.markProviderResult(
                    transaction,
                    SmsProviderSendResultDto.failed(
                            QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_CODE,
                            QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_MESSAGE
                    )
            );
            return;
        }

        Date nextAttemptAt = retryPolicy.nextAttemptAt(transaction, new Date());
        transactionRecorder.markRetryScheduled(
                transaction,
                SmsProviderSendResultDto.failed(
                        QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_CODE,
                        QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_MESSAGE
                ),
                nextAttemptAt
        );
    }

    private void processTransaction(SmsTransaction transaction) {
        SmsProviderSendResultDto providerResult;
        try {
            SmsProviderClient providerClient = providerResolver.resolve(transaction.getProviderType());
            providerResult = providerClient.send(transaction.toSendCommand(), clientReferenceId(transaction));
        } catch (RuntimeException e) {
            providerResult = SmsProviderSendResultDto.failed(QUEUE_PROVIDER_EXCEPTION_CODE, null);
        }

        if (providerResult.accepted()) {
            transactionRecorder.markProviderResult(transaction, providerResult);
            return;
        }

        if (!retryPolicy.canRetry(transaction)) {
            transactionRecorder.markProviderResult(transaction, retryExhaustedResult(providerResult));
            return;
        }

        Date nextAttemptAt = retryPolicy.nextAttemptAt(transaction, new Date());
        transactionRecorder.markRetryScheduled(transaction, retryScheduledResult(providerResult), nextAttemptAt);
    }

    private SmsProviderSendResultDto retryScheduledResult(SmsProviderSendResultDto providerResult) {
        if (isProviderException(providerResult)) {
            return SmsProviderSendResultDto.failed(
                    QUEUE_PROVIDER_EXCEPTION_RETRY_SCHEDULED_CODE,
                    QUEUE_PROVIDER_EXCEPTION_RETRY_SCHEDULED_MESSAGE
            );
        }
        return SmsProviderSendResultDto.failed(
                QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED_CODE,
                QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED_MESSAGE
        );
    }

    private SmsProviderSendResultDto retryExhaustedResult(SmsProviderSendResultDto providerResult) {
        if (isProviderException(providerResult)) {
            return SmsProviderSendResultDto.failed(
                    QUEUE_PROVIDER_EXCEPTION_RETRY_EXHAUSTED_CODE,
                    QUEUE_PROVIDER_EXCEPTION_RETRY_EXHAUSTED_MESSAGE
            );
        }
        return SmsProviderSendResultDto.failed(
                QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED_CODE,
                QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED_MESSAGE
        );
    }

    private boolean isProviderException(SmsProviderSendResultDto providerResult) {
        return QUEUE_PROVIDER_EXCEPTION_CODE.equals(providerResult.errorCode());
    }

    private String clientReferenceId(SmsTransaction transaction) {
        Long transactionId = Objects.requireNonNull(
                transaction.getId(),
                "sms_transaction id is required before provider send"
        );
        return "sms-transaction-" + transactionId;
    }
}
