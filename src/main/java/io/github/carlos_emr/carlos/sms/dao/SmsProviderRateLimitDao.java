package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;

import java.util.Date;
import java.util.Optional;

public interface SmsProviderRateLimitDao extends AbstractDao<SmsProviderRateLimit> {
    void insertIfMissing(SmsProviderType providerType, Date now);

    Optional<SmsProviderRateLimit> findByProviderTypeForUpdate(SmsProviderType providerType);
}
