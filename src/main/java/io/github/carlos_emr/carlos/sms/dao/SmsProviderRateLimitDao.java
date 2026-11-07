package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;

import java.util.Date;
import java.util.Optional;

public interface SmsProviderRateLimitDao extends AbstractDao<SmsProviderRateLimit> {
    /**
     * Creates a missing row or takes an exclusive lock on the existing row without changing its count.
     * Call in the same transaction as {@link #findByProviderTypeForUpdate} and the permit update.
     */
    void ensureExists(SmsProviderType providerType, Date now);

    Optional<SmsProviderRateLimit> findByProviderTypeForUpdate(SmsProviderType providerType);
}
