package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SmsQueueWorker {
    private static final SmsProviderType DEFAULT_PROVIDER_TYPE = SmsProviderType.STUB;
    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final String QUEUE_PROVIDER_EXCEPTION_CODE = "QUEUE_PROVIDER_EXCEPTION";
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
        while (processed < safeLimit) {
            if (!rateLimiter.tryAcquire(DEFAULT_PROVIDER_TYPE)) {
                break;
            }
            List<SmsTransaction> transactions = transactionRecorder.claimDueOutboundQueue(
                    DEFAULT_PROVIDER_TYPE,
                    new Date(),
                    1
            );
            if (transactions.isEmpty()) {
                break;
            }
            processTransaction(transactions.get(0));
            processed++;
        }
        return processed;
    }

    private void processTransaction(SmsTransaction transaction) {
        SmsProviderClient providerClient = providerResolver.resolve(transaction.getProviderType());
        SmsProviderSendResultDto providerResult;
        try {
            providerResult = providerClient.send(transaction.toSendCommand());
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
}
