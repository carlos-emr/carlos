package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.hibernate.Timeouts;
import org.hibernate.jpa.SpecHints;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SmsTransactionDaoImpl extends AbstractDaoImpl<SmsTransaction> implements SmsTransactionDao {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final String PARAM_DEMOGRAPHIC_NO = "demographicNo";
    private static final String PARAM_DIRECTION = "direction";
    private static final String PARAM_PROVIDER_TYPE = "providerType";
    private static final String PARAM_STATUS = "status";

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
        query.setParameter(PARAM_DEMOGRAPHIC_NO, demographicNo);
        query.setMaxResults(safeLimit(limit));
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsTransaction> findByDemographicNo(Integer demographicNo, int offset, int limit) {
        if (demographicNo == null) {
            return List.of();
        }
        TypedQuery<SmsTransaction> query = entityManager.createQuery(
                "SELECT t FROM SmsTransaction t WHERE t.demographicNo = :demographicNo "
                        + "ORDER BY t.createdAt DESC, t.id DESC",
                SmsTransaction.class
        );
        query.setParameter(PARAM_DEMOGRAPHIC_NO, demographicNo);
        query.setFirstResult(Math.max(0, offset));
        query.setMaxResults(safeLimit(limit));
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByDemographicNo(Integer demographicNo) {
        if (demographicNo == null) {
            return 0L;
        }
        return entityManager.createQuery(
                        "SELECT COUNT(t) FROM SmsTransaction t WHERE t.demographicNo = :demographicNo", Long.class)
                .setParameter(PARAM_DEMOGRAPHIC_NO, demographicNo)
                .getSingleResult();
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
        query.setParameter(PARAM_PROVIDER_TYPE, providerType);
        query.setParameter("providerMessageId", providerMessageId);
        query.setMaxResults(1);
        return query.getResultList().stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SmsTransaction> findByClientReferenceId(SmsProviderType providerType, String clientReferenceId) {
        if (providerType == null || clientReferenceId == null || clientReferenceId.isBlank()) {
            return Optional.empty();
        }
        TypedQuery<SmsTransaction> query = entityManager.createQuery(
                "SELECT t FROM SmsTransaction t WHERE t.providerType = :providerType "
                        + "AND t.clientReferenceId = :clientReferenceId ORDER BY t.createdAt DESC",
                SmsTransaction.class
        );
        query.setParameter(PARAM_PROVIDER_TYPE, providerType);
        query.setParameter("clientReferenceId", clientReferenceId);
        query.setMaxResults(1);
        return query.getResultList().stream().findFirst();
    }

    @Override
    @Transactional
    public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date claimAt, int limit) {
        if (providerType == null || claimAt == null) {
            return List.of();
        }
        // Portable claim: lock the due rows with a pessimistic write (SELECT ... FOR UPDATE SKIP LOCKED)
        // ordered and capped, then mutate the managed entities. This works on both MariaDB/MySQL and the
        // H2 test database, unlike a single "UPDATE ... ORDER BY ... LIMIT" which H2 does not support. A
        // row is claimed by exactly one worker; see lockSkippingRowsHeldElsewhere for why others skip it.
        TypedQuery<SmsTransaction> query = entityManager.createQuery(
                "SELECT t FROM SmsTransaction t "
                        + "WHERE t.direction = :direction "
                        + "AND t.providerType = :providerType "
                        + "AND t.status = :status "
                        + "AND (t.nextAttemptAt IS NULL OR t.nextAttemptAt <= :claimAt) "
                        + "ORDER BY t.createdAt ASC",
                SmsTransaction.class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_PROVIDER_TYPE, providerType);
        query.setParameter(PARAM_STATUS, SmsStatus.QUEUED);
        query.setParameter("claimAt", claimAt);
        query.setMaxResults(safeLimit(limit));
        lockSkippingRowsHeldElsewhere(query);

        List<SmsTransaction> due = query.getResultList();
        String claimToken = newClaimToken();
        for (SmsTransaction transaction : due) {
            ensureClientReferenceId(transaction);
            transaction.markSending(claimAt);
            transaction.assignClaimToken(claimToken);
        }
        return due;
    }

    @Override
    @Transactional
    public List<SmsTransaction> claimStaleOutboundSendingForRecovery(
            SmsProviderType providerType,
            Date staleBefore,
            Date recoveryAt,
            int limit
    ) {
        if (providerType == null || staleBefore == null || recoveryAt == null) {
            return List.of();
        }
        TypedQuery<SmsTransaction> query = entityManager.createQuery(
                "SELECT t FROM SmsTransaction t "
                        + "WHERE t.direction = :direction "
                        + "AND t.providerType = :providerType "
                        + "AND t.status = :status "
                        + "AND t.lastAttemptAt IS NOT NULL "
                        + "AND t.lastAttemptAt < :staleBefore "
                        + "ORDER BY t.lastAttemptAt ASC",
                SmsTransaction.class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_PROVIDER_TYPE, providerType);
        query.setParameter(PARAM_STATUS, SmsStatus.SENDING);
        query.setParameter("staleBefore", staleBefore);
        query.setMaxResults(safeLimit(limit));
        lockSkippingRowsHeldElsewhere(query);

        List<SmsTransaction> stale = query.getResultList();
        String claimToken = newClaimToken();
        for (SmsTransaction transaction : stale) {
            ensureClientReferenceId(transaction);
            transaction.markStaleRecoveryStarted(recoveryAt);
            transaction.assignClaimToken(claimToken);
        }
        return stale;
    }

    private void ensureClientReferenceId(SmsTransaction transaction) {
        if (transaction.getId() == null || !isBlank(transaction.getClientReferenceId())) {
            return;
        }
        transaction.assignClientReferenceId(SmsTransaction.clientReferenceIdFor(transaction.getId()));
    }

    /**
     * Locks the claim query's rows for update, skipping any row another transaction already holds.
     *
     * <p>{@code provider_type} leads both unique indexes, so MariaDB can reach the same row through a
     * different index in each of two concurrent claims. Those claims deadlocked InnoDB (#3913): one
     * waited for the row while holding the index record that the other's UPDATE needed. With SKIP
     * LOCKED the claim's locking read no longer waits on a row lock; on MariaDB 11.8 the reproduction
     * went from a deadlock in every round to none.
     *
     * <p>It skips rows locked by any transaction, not only another claim: a direct send, a delivery
     * callback writing the row. Neither index returns due rows already in
     * {@code created_at} order, so the scan locks every due row it examines, not only the first. A
     * claim can therefore come back empty while rows are still due, which ends that provider's drain
     * for the run; a later claim or run picks the rows up. Requires MariaDB 10.6 or later; CARLOS
     * requires 11.4.
     */
    private void lockSkippingRowsHeldElsewhere(TypedQuery<SmsTransaction> query) {
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        query.setHint(SpecHints.HINT_SPEC_LOCK_TIMEOUT, Timeouts.SKIP_LOCKED_MILLI);
    }

    private String newClaimToken() {
        return UUID.randomUUID().toString();
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
