package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;

public interface SmsSendRateLimiter {
    boolean tryAcquire(SmsProviderType providerType);
}
