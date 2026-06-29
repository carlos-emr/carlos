package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Repository
public class SmsProviderRateLimitDaoImpl extends AbstractDaoImpl<SmsProviderRateLimit>
        implements SmsProviderRateLimitDao {
    public SmsProviderRateLimitDaoImpl() {
        super(SmsProviderRateLimit.class);
    }

    @Override
    @Transactional
    public void insertIfMissing(SmsProviderType providerType, Date now) {
        if (providerType == null) {
            return;
        }
        Date safeNow = now == null ? new Date() : new Date(now.getTime());
        entityManager.createNativeQuery(
                        "INSERT IGNORE INTO sms_provider_rate_limit "
                                + "(provider_type, send_count, window_started_at, created_at, updated_at) "
                                + "VALUES (?1, 0, ?2, ?3, ?4)"
                )
                .setParameter(1, providerType.name())
                .setParameter(2, safeNow)
                .setParameter(3, safeNow)
                .setParameter(4, safeNow)
                .executeUpdate();
    }

    @Override
    @Transactional
    public Optional<SmsProviderRateLimit> findByProviderTypeForUpdate(SmsProviderType providerType) {
        if (providerType == null) {
            return Optional.empty();
        }
        TypedQuery<SmsProviderRateLimit> query = entityManager.createQuery(
                "SELECT r FROM SmsProviderRateLimit r WHERE r.providerType = :providerType",
                SmsProviderRateLimit.class
        );
        query.setParameter("providerType", providerType);
        query.setMaxResults(1);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList().stream().findFirst();
    }
}
