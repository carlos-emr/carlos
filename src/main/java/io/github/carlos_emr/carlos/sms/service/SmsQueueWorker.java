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
        Date now = new Date();
        List<SmsTransaction> transactions = transactionRecorder.findDueOutboundQueue(DEFAULT_PROVIDER_TYPE, now, limit);
        int processed = 0;
        for (SmsTransaction transaction : transactions) {
            if (!rateLimiter.tryAcquire(transaction.getProviderType())) {
                break;
            }
            processTransaction(transaction, now);
            processed++;
        }
        return processed;
    }

    private void processTransaction(SmsTransaction transaction, Date attemptAt) {
        transactionRecorder.markSending(transaction, attemptAt);
        SmsProviderClient providerClient = providerResolver.resolve(transaction.getProviderType());
        SmsProviderSendResultDto providerResult;
        try {
            providerResult = providerClient.send(transaction.toSendCommand());
        } catch (RuntimeException e) {
            providerResult = SmsProviderSendResultDto.failed("PROVIDER_EXCEPTION", "SMS provider send failed.");
        }

        if (providerResult.accepted() || !retryPolicy.canRetry(transaction)) {
            transactionRecorder.markProviderResult(transaction, providerResult);
            return;
        }

        Date nextAttemptAt = retryPolicy.nextAttemptAt(transaction, new Date());
        transactionRecorder.markRetryScheduled(transaction, providerResult, nextAttemptAt);
    }
}
