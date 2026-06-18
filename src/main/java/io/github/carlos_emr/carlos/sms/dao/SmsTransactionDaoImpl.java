package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
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
    @SuppressWarnings("unchecked")
    public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date claimAt, int limit) {
        if (providerType == null || claimAt == null) {
            return List.of();
        }
        String claimToken = newClaimToken();
        Query update = entityManager.createNativeQuery(
                "UPDATE sms_transaction "
                        + "SET status = ?1, "
                        + "attempt_count = attempt_count + 1, "
                        + "last_attempt_at = ?2, "
                        + "next_attempt_at = NULL, "
                        + "updated_at = ?3, "
                        + "claim_token = ?4 "
                        + "WHERE direction = ?5 "
                        + "AND provider_type = ?6 "
                        + "AND status = ?7 "
                        + "AND (next_attempt_at IS NULL OR next_attempt_at <= ?8) "
                        + "ORDER BY created_at ASC "
                        + "LIMIT ?9"
        );
        update.setParameter(1, SmsStatus.SENDING.name());
        update.setParameter(2, claimAt);
        update.setParameter(3, claimAt);
        update.setParameter(4, claimToken);
        update.setParameter(5, SmsDirection.OUTBOUND.name());
        update.setParameter(6, providerType.name());
        update.setParameter(7, SmsStatus.QUEUED.name());
        update.setParameter(8, claimAt);
        update.setParameter(9, safeLimit(limit));
        int claimed = update.executeUpdate();
        if (claimed == 0) {
            return List.of();
        }
        return findByClaimTokenOrderByCreatedAt(claimToken);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<SmsTransaction> claimStaleOutboundSendingForRecovery(
            SmsProviderType providerType,
            Date staleBefore,
            Date recoveryAt,
            int limit
    ) {
        if (providerType == null || staleBefore == null || recoveryAt == null) {
            return List.of();
        }
        String claimToken = newClaimToken();
        Query update = entityManager.createNativeQuery(
                "UPDATE sms_transaction "
                        + "SET last_attempt_at = ?1, "
                        + "updated_at = ?2, "
                        + "claim_token = ?3 "
                        + "WHERE direction = ?4 "
                        + "AND provider_type = ?5 "
                        + "AND status = ?6 "
                        + "AND last_attempt_at IS NOT NULL "
                        + "AND last_attempt_at < ?7 "
                        + "ORDER BY last_attempt_at ASC "
                        + "LIMIT ?8"
        );
        update.setParameter(1, recoveryAt);
        update.setParameter(2, recoveryAt);
        update.setParameter(3, claimToken);
        update.setParameter(4, SmsDirection.OUTBOUND.name());
        update.setParameter(5, providerType.name());
        update.setParameter(6, SmsStatus.SENDING.name());
        update.setParameter(7, staleBefore);
        update.setParameter(8, safeLimit(limit));
        int claimed = update.executeUpdate();
        if (claimed == 0) {
            return List.of();
        }
        return findByClaimTokenOrderById(claimToken);
    }

    @SuppressWarnings("unchecked")
    private List<SmsTransaction> findByClaimTokenOrderByCreatedAt(String claimToken) {
        Query query = entityManager.createNativeQuery(
                "SELECT * FROM sms_transaction WHERE claim_token = ?1 ORDER BY created_at ASC",
                SmsTransaction.class
        );
        query.setParameter(1, claimToken);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<SmsTransaction> findByClaimTokenOrderById(String claimToken) {
        Query query = entityManager.createNativeQuery(
                "SELECT * FROM sms_transaction WHERE claim_token = ?1 ORDER BY id ASC",
                SmsTransaction.class
        );
        query.setParameter(1, claimToken);
        return query.getResultList();
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
}
