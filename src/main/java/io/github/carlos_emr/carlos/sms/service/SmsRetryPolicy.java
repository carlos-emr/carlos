package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Service
public class SmsRetryPolicy {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMinutes(1);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofHours(1);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;

    @Autowired
    public SmsRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY, DEFAULT_MAX_DELAY);
    }

    SmsRetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialDelay = initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()
                ? DEFAULT_INITIAL_DELAY
                : initialDelay;
        this.maxDelay = maxDelay == null || maxDelay.isNegative() || maxDelay.isZero()
                ? DEFAULT_MAX_DELAY
                : maxDelay;
    }

    public boolean canRetry(SmsTransaction transaction) {
        return transaction != null && transaction.getAttemptCount() < maxAttempts;
    }

    public Date nextAttemptAt(SmsTransaction transaction, Date failedAt) {
        Date safeFailedAt = failedAt == null ? new Date() : new Date(failedAt.getTime());
        int attemptCount = transaction == null ? 1 : Math.max(1, transaction.getAttemptCount());
        long multiplier = 1L << Math.min(attemptCount - 1, 10);
        Duration delay = initialDelay.multipliedBy(multiplier);
        if (delay.compareTo(maxDelay) > 0) {
            delay = maxDelay;
        }
        return new Date(safeFailedAt.getTime() + delay.toMillis());
    }
}
