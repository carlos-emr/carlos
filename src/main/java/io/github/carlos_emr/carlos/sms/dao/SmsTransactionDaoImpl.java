package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueCountDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueRowDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.UUID;

@Repository
public class SmsTransactionDaoImpl extends AbstractDaoImpl<SmsTransaction> implements SmsTransactionDao {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final String PARAM_DIRECTION = "direction";
    private static final String PARAM_PROVIDER_TYPE = "providerType";
    private static final String PARAM_STATUS = "status";
    private static final String PARAM_STATUSES = "statuses";
    private static final String PARAM_DUE_BEFORE = "dueBefore";
    private static final String PARAM_STALE_BEFORE = "staleBefore";
    private static final String PARAM_SINCE = "since";
    private static final String PARAM_DEMOGRAPHIC_NOS = "demographicNos";
    // Followed by the part's position: excluded0, excluded1, ...
    private static final String PARAM_EXCLUDED_PREFIX = "excluded";
    // The queue view's time period. Added to a query only when a start time is given; the time itself is
    // always a bound parameter.
    private static final String UPDATED_SINCE = "AND t.updatedAt >= :since ";
    // Queue-view row projection. It names only operational columns: the message body, sender number,
    // operator/provider messages and provider metadata are never selected, so they are never loaded.
    // SmsQueueRowDto documents the column order that toQueueRow reads.
    private static final String QUEUE_ROW_SELECT = "SELECT t.id, t.providerType, t.status, t.demographicNo, "
            + "t.toPhoneNumber, t.attemptCount, t.errorCode, t.consentReasonCode, "
            + "t.createdAt, t.updatedAt, t.nextAttemptAt, t.lastAttemptAt "
            + "FROM SmsTransaction t ";
    // A message that has never been attempted has been due since it was created: its next_attempt_at is
    // either empty or the time of its last release. The worker resets it to "now" each time the rate limit
    // holds the message back, so going by next_attempt_at would hide the longest-waiting message for as
    // long as the rate limit lasts. A message that has been attempted is due at its scheduled retry.
    private static final String QUEUED_DUE_AT =
            "CASE WHEN t.attemptCount = 0 THEN t.createdAt ELSE COALESCE(t.nextAttemptAt, t.createdAt) END";
    private static final List<SmsStatus> CONSENT_BLOCKED_STATUSES =
            List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED);

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
        // Portable claim: lock the due rows with a pessimistic write (SELECT ... FOR UPDATE) ordered and
        // capped, then mutate the managed entities. This works on both MariaDB/MySQL and the H2 test
        // database, unlike a single "UPDATE ... ORDER BY ... LIMIT" which H2 does not support. The row
        // locks serialize concurrent workers so a row is claimed by exactly one.
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
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);

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
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);

        List<SmsTransaction> stale = query.getResultList();
        String claimToken = newClaimToken();
        for (SmsTransaction transaction : stale) {
            ensureClientReferenceId(transaction);
            transaction.markStaleRecoveryStarted(recoveryAt);
            transaction.assignClaimToken(claimToken);
        }
        return stale;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsQueueCountDto> countOutboundByProviderAndStatus() {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT t.providerType, t.status, COUNT(t) FROM SmsTransaction t "
                        + "WHERE t.direction = :direction "
                        + "GROUP BY t.providerType, t.status",
                Object[].class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        return query.getResultList().stream()
                .map(row -> new SmsQueueCountDto(
                        (SmsProviderType) row[0],
                        row[1] == null ? null : ((SmsStatus) row[1]).name(),
                        toLong(row[2])
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<SmsProviderType, Long> countOverdueQueuedOutboundByProvider(Date dueBefore) {
        if (dueBefore == null) {
            return Map.of();
        }
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT t.providerType, COUNT(t) FROM SmsTransaction t "
                        + "WHERE t.direction = :direction "
                        + "AND t.status = :status "
                        + "AND " + QUEUED_DUE_AT + " < :dueBefore "
                        + "GROUP BY t.providerType",
                Object[].class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_STATUS, SmsStatus.QUEUED);
        query.setParameter(PARAM_DUE_BEFORE, dueBefore);
        return toProviderCounts(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<SmsProviderType, Long> countStaleSendingOutboundByProvider(Date staleBefore) {
        if (staleBefore == null) {
            return Map.of();
        }
        // Same predicate as claimStaleOutboundSendingForRecovery, so "stale" on the page means exactly the
        // rows the worker's next stale recovery would pick up.
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT t.providerType, COUNT(t) FROM SmsTransaction t "
                        + "WHERE t.direction = :direction "
                        + "AND t.status = :status "
                        + "AND t.lastAttemptAt IS NOT NULL "
                        + "AND t.lastAttemptAt < :staleBefore "
                        + "GROUP BY t.providerType",
                Object[].class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_STATUS, SmsStatus.SENDING);
        query.setParameter(PARAM_STALE_BEFORE, staleBefore);
        return toProviderCounts(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsQueueCountDto> countFailedOutboundByProviderAndErrorCode(Date since) {
        return countFailedOutboundByProviderAndErrorCode(since, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsQueueCountDto> countFailedOutboundByProviderAndErrorCode(
            Date since, Collection<Integer> excludedDemographicNos) {
        List<List<Integer>> excluded = inParts(excludedDemographicNos);
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT t.providerType, t.errorCode, COUNT(t) FROM SmsTransaction t "
                        + "WHERE t.direction = :direction "
                        + "AND t.status = :status "
                        + updatedSince(since)
                        + notOfPatients(excluded.size())
                        + "GROUP BY t.providerType, t.errorCode",
                Object[].class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_STATUS, SmsStatus.FAILED);
        bindSince(query, since);
        bindExcluded(query, excluded);
        return toCodeCounts(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsQueueCountDto> countConsentBlockedOutboundByProviderAndReason(Date since) {
        return countConsentBlockedOutboundByProviderAndReason(since, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsQueueCountDto> countConsentBlockedOutboundByProviderAndReason(
            Date since, Collection<Integer> excludedDemographicNos) {
        List<List<Integer>> excluded = inParts(excludedDemographicNos);
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT t.providerType, t.consentReasonCode, COUNT(t) FROM SmsTransaction t "
                        + "WHERE t.direction = :direction "
                        + "AND t.status IN (:statuses) "
                        + updatedSince(since)
                        + notOfPatients(excluded.size())
                        + "GROUP BY t.providerType, t.consentReasonCode",
                Object[].class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_STATUSES, CONSENT_BLOCKED_STATUSES);
        bindSince(query, since);
        bindExcluded(query, excluded);
        return toCodeCounts(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Integer> findPatientsWithFailedOutbound(Date since, Collection<Integer> demographicNos) {
        return findPatientsWithOutbound(List.of(SmsStatus.FAILED), since, demographicNos);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Integer> findPatientsWithConsentBlockedOutbound(Date since, Collection<Integer> demographicNos) {
        return findPatientsWithOutbound(CONSENT_BLOCKED_STATUSES, since, demographicNos);
    }

    /**
     * @param statuses the statuses of the section's own count query; they, the time and the patients are
     *                 bound parameters
     */
    private Set<Integer> findPatientsWithOutbound(
            List<SmsStatus> statuses, Date since, Collection<Integer> demographicNos) {
        Set<Integer> found = new TreeSet<>();
        for (List<Integer> part : inParts(demographicNos)) {
            TypedQuery<Integer> query = entityManager.createQuery(
                    "SELECT DISTINCT t.demographicNo FROM SmsTransaction t "
                            + "WHERE t.direction = :direction "
                            + "AND t.status IN (:statuses) "
                            + "AND t.demographicNo IN (:demographicNos) "
                            + updatedSince(since),
                    Integer.class
            );
            query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
            query.setParameter(PARAM_STATUSES, statuses);
            query.setParameter(PARAM_DEMOGRAPHIC_NOS, part);
            bindSince(query, since);
            found.addAll(query.getResultList());
        }
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsQueueRowDto> findOverdueQueuedOutbound(SmsProviderType providerType, Date dueBefore, int limit) {
        if (providerType == null || dueBefore == null) {
            return List.of();
        }
        TypedQuery<Object[]> query = entityManager.createQuery(
                QUEUE_ROW_SELECT
                        + "WHERE t.direction = :direction "
                        + "AND t.providerType = :providerType "
                        + "AND t.status = :status "
                        + "AND " + QUEUED_DUE_AT + " < :dueBefore "
                        + "ORDER BY " + QUEUED_DUE_AT + " ASC, t.id ASC",
                Object[].class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_PROVIDER_TYPE, providerType);
        query.setParameter(PARAM_STATUS, SmsStatus.QUEUED);
        query.setParameter(PARAM_DUE_BEFORE, dueBefore);
        query.setMaxResults(safeLimit(limit));
        return query.getResultList().stream().map(SmsTransactionDaoImpl::toQueueRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsQueueRowDto> findStaleSendingOutbound(SmsProviderType providerType, Date staleBefore, int limit) {
        if (providerType == null || staleBefore == null) {
            return List.of();
        }
        TypedQuery<Object[]> query = entityManager.createQuery(
                QUEUE_ROW_SELECT
                        + "WHERE t.direction = :direction "
                        + "AND t.providerType = :providerType "
                        + "AND t.status = :status "
                        + "AND t.lastAttemptAt IS NOT NULL "
                        + "AND t.lastAttemptAt < :staleBefore "
                        + "ORDER BY t.lastAttemptAt ASC, t.id ASC",
                Object[].class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_PROVIDER_TYPE, providerType);
        query.setParameter(PARAM_STATUS, SmsStatus.SENDING);
        query.setParameter(PARAM_STALE_BEFORE, staleBefore);
        query.setMaxResults(safeLimit(limit));
        return query.getResultList().stream().map(SmsTransactionDaoImpl::toQueueRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsQueueRowDto> findRecentOutboundByStatuses(
            SmsProviderType providerType,
            Collection<SmsStatus> statuses,
            Date since,
            int limit
    ) {
        if (providerType == null || statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        TypedQuery<Object[]> query = entityManager.createQuery(
                QUEUE_ROW_SELECT
                        + "WHERE t.direction = :direction "
                        + "AND t.providerType = :providerType "
                        + "AND t.status IN (:statuses) "
                        + updatedSince(since)
                        + "ORDER BY t.updatedAt DESC, t.id DESC",
                Object[].class
        );
        query.setParameter(PARAM_DIRECTION, SmsDirection.OUTBOUND);
        query.setParameter(PARAM_PROVIDER_TYPE, providerType);
        query.setParameter(PARAM_STATUSES, List.copyOf(statuses));
        bindSince(query, since);
        query.setMaxResults(safeLimit(limit));
        return query.getResultList().stream().map(SmsTransactionDaoImpl::toQueueRow).toList();
    }

    /** The time-period clause, or nothing when there is no start time (all time). */
    private static String updatedSince(Date since) {
        return since == null ? "" : UPDATED_SINCE;
    }

    private static void bindSince(TypedQuery<?> query, Date since) {
        if (since != null) {
            query.setParameter(PARAM_SINCE, since);
        }
    }

    /**
     * The demographic numbers each once, in order and without gaps, split into parts of at most
     * {@link #PATIENT_COUNT_CHUNK_SIZE}. No patient is in two parts. Empty for an empty or {@code null} list.
     */
    private static List<List<Integer>> inParts(Collection<Integer> demographicNos) {
        if (demographicNos == null) {
            return List.of();
        }
        TreeSet<Integer> distinct = new TreeSet<>();
        for (Integer demographicNo : demographicNos) {
            if (demographicNo != null) {
                distinct.add(demographicNo);
            }
        }
        List<Integer> patients = List.copyOf(distinct);
        List<List<Integer>> parts = new ArrayList<>();
        for (int from = 0; from < patients.size(); from += PATIENT_COUNT_CHUNK_SIZE) {
            parts.add(List.copyOf(patients.subList(from, Math.min(from + PATIENT_COUNT_CHUNK_SIZE, patients.size()))));
        }
        return parts;
    }

    /**
     * The clause that leaves out the messages of the patients in that many parts, or nothing when there are
     * no parts. A message without a patient is always kept: {@code NOT IN} alone would drop it, since a
     * missing number is neither in nor out of a list. The parameter names are made here from the part's
     * position, never from data; the numbers themselves are bound by {@link #bindExcluded}.
     */
    private static String notOfPatients(int parts) {
        if (parts == 0) {
            return "";
        }
        StringJoiner clause = new StringJoiner(" AND ", "AND (t.demographicNo IS NULL OR (", ")) ");
        for (int part = 0; part < parts; part++) {
            clause.add("t.demographicNo NOT IN (:" + PARAM_EXCLUDED_PREFIX + part + ")");
        }
        return clause.toString();
    }

    private static void bindExcluded(TypedQuery<?> query, List<List<Integer>> parts) {
        for (int part = 0; part < parts.size(); part++) {
            query.setParameter(PARAM_EXCLUDED_PREFIX + part, parts.get(part));
        }
    }

    private static Map<SmsProviderType, Long> toProviderCounts(List<Object[]> rows) {
        Map<SmsProviderType, Long> counts = new EnumMap<>(SmsProviderType.class);
        for (Object[] row : rows) {
            if (row[0] instanceof SmsProviderType providerType) {
                counts.merge(providerType, toLong(row[1]), Long::sum);
            }
        }
        return counts;
    }

    private static List<SmsQueueCountDto> toCodeCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new SmsQueueCountDto((SmsProviderType) row[0], (String) row[1], toLong(row[2])))
                .toList();
    }

    /** Maps a {@link #QUEUE_ROW_SELECT} result; the indexes follow its column order. */
    private static SmsQueueRowDto toQueueRow(Object[] row) {
        return new SmsQueueRowDto(
                (Long) row[0],
                (SmsProviderType) row[1],
                (SmsStatus) row[2],
                (Integer) row[3],
                (String) row[4],
                row[5] == null ? 0 : ((Number) row[5]).intValue(),
                (String) row[6],
                (String) row[7],
                toInstant(row[8]),
                toInstant(row[9]),
                toInstant(row[10]),
                toInstant(row[11])
        );
    }

    private static long toLong(Object count) {
        return count == null ? 0L : ((Number) count).longValue();
    }

    // Loaded timestamps are java.sql.Timestamp; getTime() works for every Date subclass, unlike toInstant()
    // on java.sql.Date.
    private static Instant toInstant(Object value) {
        return value instanceof Date date ? Instant.ofEpochMilli(date.getTime()) : null;
    }

    private void ensureClientReferenceId(SmsTransaction transaction) {
        if (transaction.getId() == null || !isBlank(transaction.getClientReferenceId())) {
            return;
        }
        transaction.assignClientReferenceId(SmsTransaction.clientReferenceIdFor(transaction.getId()));
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
