package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class SmsTransactionDaoImpl extends AbstractDaoImpl<SmsTransaction> implements SmsTransactionDao {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    public SmsTransactionDaoImpl() {
        super(SmsTransaction.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsTransaction> findByDemographicNo(Integer demographicNo, int limit) {
        if (demographicNo == null) {
            return List.of();
        }
        TypedQuery<SmsTransaction> query = entityManager.createQuery(
                "SELECT t FROM SmsTransaction t WHERE t.demographicNo = :demographicNo ORDER BY t.createdAt DESC",
                SmsTransaction.class
        );
        query.setParameter("demographicNo", demographicNo);
        query.setMaxResults(safeLimit(limit));
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SmsTransaction> findByProviderMessageId(SmsProviderType providerType, String providerMessageId) {
        if (providerType == null || providerMessageId == null || providerMessageId.isBlank()) {
            return Optional.empty();
        }
        TypedQuery<SmsTransaction> query = entityManager.createQuery(
                "SELECT t FROM SmsTransaction t WHERE t.providerType = :providerType "
                        + "AND t.providerMessageId = :providerMessageId ORDER BY t.createdAt DESC",
                SmsTransaction.class
        );
        query.setParameter("providerType", providerType);
        query.setParameter("providerMessageId", providerMessageId);
        query.setMaxResults(1);
        return query.getResultList().stream().findFirst();
    }

    @Override
    @Transactional
    public List<SmsTransaction> findDueOutboundQueueForUpdate(SmsProviderType providerType, Date now, int limit) {
        if (providerType == null || now == null) {
            return List.of();
        }
        TypedQuery<SmsTransaction> query = entityManager.createQuery(
                "SELECT t FROM SmsTransaction t WHERE t.direction = :direction "
                        + "AND t.providerType = :providerType "
                        + "AND t.status = :status "
                        + "AND (t.nextAttemptAt IS NULL OR t.nextAttemptAt <= :now) "
                        + "ORDER BY t.createdAt ASC",
                SmsTransaction.class
        );
        query.setParameter("direction", SmsDirection.OUTBOUND);
        query.setParameter("providerType", providerType);
        query.setParameter("status", SmsStatus.QUEUED);
        query.setParameter("now", now);
        query.setMaxResults(safeLimit(limit));
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
