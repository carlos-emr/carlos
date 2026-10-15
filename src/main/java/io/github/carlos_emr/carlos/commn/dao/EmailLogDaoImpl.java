package io.github.carlos_emr.carlos.commn.dao;

import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;

/**
 * Data Access Object implementation for managing EmailLog entities in the OpenO EMR system.
 *
 * <p>This DAO provides database operations for email communication logs within the healthcare context,
 * supporting patient-provider communication tracking, audit trails, and email status management.
 * Email logs capture critical metadata about communications including sender, recipient, status,
 * encryption details, and transaction context (e-forms, consultations, ticklers, or direct emails).</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Advanced filtering of email logs by date range, demographic, sender, and status</li>
 *   <li>Efficient status updates without loading Large Object (LOB) fields</li>
 *   <li>Support for encrypted patient communications with PHI protection</li>
 *   <li>Integration with EmailConfig, Demographic, and Provider entities</li>
 *   <li>Comprehensive audit tracking for healthcare compliance (HIPAA/PIPEDA)</li>
 * </ul>
 *
 * <p>This implementation is used primarily from the Admin > Emails > Manage Emails interface
 * to provide administrators with visibility into email communication history and troubleshooting
 * capabilities for failed email deliveries.</p>
 *
 * @see EmailLog
 * @see EmailLogDao
 * @see io.github.carlos_emr.carlos.commn.model.EmailConfig
 * @see io.github.carlos_emr.carlos.commn.model.Demographic
 * @see io.github.carlos_emr.carlos.commn.model.Provider
 * @since 2026-01-24
 */
@Repository
public class EmailLogDaoImpl extends AbstractDaoImpl<EmailLog> implements EmailLogDao {

    /**
     * Whether an email is settled: its transport is known to be over, or (NOT_ARRIVED) its code is known to be
     * dead. Shared by the selection and the update so the two cannot drift apart. SUCCESS and BLOCKED are
     * written by the send itself. FAILED is included only
     * when the portal invitation attempt that names the email ended in state SEND_FAILED with outcome
     * SEND_REFUSED: the send writes that, and only after a definite "not sent" once the code went live
     * (a refusal by the mail server, a refused connection or login, or, rarely, a failure in the commit gate
     * after the attempt was already recorded as committed), and
     * the state is terminal, so no step is still owed. Every attempt naming the email must be in that
     * state. Other FAILED rows stay untouched: staff abandonment, which can be written while the original
     * send is still running, ends its attempt ABANDONED; a permission refusal after the gate leaves it
     * SEND_UNCERTAIN; a refused or unrecorded commit ends it ABANDONED; and an error before the gate leaves
     * no attempt naming the email. RESOLVED counts only when every attempt naming the email ended NOT_ARRIVED;
     * PENDING never does. Rows this rule leaves out are cleared once their code has aged out
     * ({@link #CODE_AGED}).
     *
     * <p>The attempt table is read in a subquery rather than through its DAO so that selection and the
     * conditional update test the same rule atomically in one statement.
     */
    private static final String SETTLED = "(e.status IN :settledStatuses OR (e.status = :failed "
            + "AND EXISTS (SELECT d.id FROM PatientPortalInviteDelivery d WHERE d.emailLogId = e.id "
            + "AND d.state = :refusedState AND d.outcome = :refusedOutcome) "
            + "AND NOT EXISTS (SELECT o.id FROM PatientPortalInviteDelivery o WHERE o.emailLogId = e.id "
            + "AND o.state <> :refusedState)) "
            // Staff confirmed the email never arrived once the portal showed its code dead (NOT_ARRIVED),
            // which resolves the email row. Every attempt naming the email must have ended so.
            + "OR (e.status = :resolved "
            + "AND EXISTS (SELECT n.id FROM PatientPortalInviteDelivery n WHERE n.emailLogId = e.id "
            + "AND n.state = :notArrivedState) "
            + "AND NOT EXISTS (SELECT m.id FROM PatientPortalInviteDelivery m WHERE m.emailLogId = e.id "
            + "AND m.state <> :notArrivedState)))";

    /**
     * Whether the email's invitation code is past its life plus a margin, whatever the email's status
     * (#4083, option B). When every attempt naming the email recorded the portal's expiry, that stored
     * expiry decides. Otherwise, as when the code never went live or CARLOS never learned its expiry, or no
     * attempt names the email, the email row's timestamp does: it is the row's creation time until its
     * status changes and later after that, so the cutoff it gives can only come later, never earlier. The
     * caller sets both cutoffs (see {@code PortalInviteCodeSweeper}).
     */
    private static final String CODE_AGED = "((EXISTS (SELECT x.id FROM PatientPortalInviteDelivery x "
            + "WHERE x.emailLogId = e.id AND x.expiresAt IS NOT NULL) "
            + "AND NOT EXISTS (SELECT y.id FROM PatientPortalInviteDelivery y WHERE y.emailLogId = e.id "
            + "AND (y.expiresAt IS NULL OR y.expiresAt >= :expiredBefore))) "
            + "OR (NOT EXISTS (SELECT z.id FROM PatientPortalInviteDelivery z WHERE z.emailLogId = e.id "
            + "AND z.expiresAt IS NOT NULL) AND e.timestamp < :agedBefore))";

    /** Settled and idle, or aged: the rule the selection and the conditional update share. */
    private static final String CODE_NOT_NEEDED = "((" + SETTLED + " AND e.timestamp < :changedBefore) OR "
            + CODE_AGED + ")";

    /** Commit lifecycle intent before a network operation, even if a caller has a transaction. */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean initializePortalDelivery(EmailLog log) {
        return entityManager.createQuery("UPDATE EmailLog e SET e.portalDeliveryState = :state, "
                + "e.portalSourceReference = :source, e.portalOrigin = :origin, e.portalClinicId = :clinic, "
                + "e.body = :body, e.password = '', e.passwordClue = '' "
                + "WHERE e.id = :id AND e.portalDeliveryState IS NULL")
                .setParameter("state", EmailLog.PortalDeliveryState.PREPARING)
                // Same encoding as EmailLog.setBody, so the stored body reads back as the one sent.
                .setParameter("body", Base64.encodeBase64(log.getBody().getBytes(StandardCharsets.UTF_8)))
                .setParameter("source", log.getPortalSourceReference())
                .setParameter("origin", log.getPortalOrigin()).setParameter("clinic", log.getPortalClinicId())
                .setParameter("id", log.getId()).executeUpdate() == 1;
    }

    /** Compare-and-set prevents recovery and the original sender making conflicting decisions. */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean transitionPortalDelivery(EmailLog log, EmailLog.PortalDeliveryState expected,
            EmailLog.PortalDeliveryState next, Long secretId) {
        return entityManager.createQuery("UPDATE EmailLog e SET e.portalDeliveryState = :next, "
                + "e.portalSecretId = :secret WHERE e.id = :id AND e.portalSourceReference = :source "
                + "AND e.portalDeliveryState = :expected")
                .setParameter("next", next).setParameter("secret", secretId).setParameter("id", log.getId())
                .setParameter("source", log.getPortalSourceReference()).setParameter("expected", expected)
                .executeUpdate() == 1;
    }

    /**
     * Constructs a new EmailLogDaoImpl with the EmailLog entity class.
     *
     * <p>This constructor initializes the AbstractDaoImpl superclass with the EmailLog
     * entity type, enabling standard CRUD operations and custom query methods for
     * email log management.</p>
     */
    public EmailLogDaoImpl() {
        super(EmailLog.class);
    }

    /**
     * Retrieves email logs based on multiple filter criteria including date range, demographic,
     * sender email address, and email status.
     *
     * <p>This method executes a complex JPQL query joining EmailLog with related entities
     * (EmailConfig, Demographic, Provider) to provide comprehensive filtering capabilities.
     * Optional filter parameters treat null, blank, and invalid demographic/status values as
     * wildcards that match all records for that criterion.</p>
     *
     * <p>Primary use case: Administrative email management interface at
     * 'Admin > Emails > Manage Emails' for troubleshooting failed deliveries,
     * auditing patient communications, and tracking email history by provider or patient.</p>
     *
     * <p>Query behavior:</p>
     * <ul>
     *   <li>Date filtering: Matches DATE portion only (time component ignored)</li>
     *   <li>Demographic filtering: Uses DemographicNo for patient identification</li>
     *   <li>Status filtering: Matches EmailStatus enum (PENDING, SUCCESS, FAILED, RESOLVED)</li>
     *   <li>Sender filtering: Matches fromEmail field exactly</li>
     *   <li>Results ordered by timestamp descending (newest first)</li>
     * </ul>
     *
     * @param dateBegin Date the start date for filtering email logs (required, matches DATE portion only)
     * @param dateEnd Date the end date for filtering email logs (required, matches DATE portion only)
     * @param demographicNo String the demographic number for filtering by patient (null, blank, or invalid matches all)
     * @param senderEmailAddress String the sender email address for filtering (null matches all)
     * @param emailStatus String the email status for filtering (PENDING/SUCCESS/FAILED/RESOLVED; null, blank, or invalid matches all)
     * @return List&lt;EmailLog&gt; list of email logs matching the specified filters, ordered by timestamp descending;
     *         empty list if no matches found
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<EmailLog> getEmailStatusByDateDemographicSenderStatus(Date dateBegin, Date dateEnd, String demographicNo, String senderEmailAddress, String emailStatus) {
        String hql = "SELECT el FROM EmailLog el LEFT JOIN el.emailConfig ec LEFT JOIN el.demographic d LEFT JOIN el.provider p " +
                "WHERE 1=1 " +
                "AND (?1 IS NULL OR d.demographicNo = ?1) " +
                "AND (?2 IS NULL OR el.status = ?2) " +
                "AND (?3 IS NULL OR el.fromEmail = ?3) " +
                "AND el.timestamp >= ?4 AND el.timestamp < ?5 " +
                "ORDER BY el.timestamp DESC";

        Date startDate = org.apache.commons.lang3.time.DateUtils.truncate(dateBegin, Calendar.DAY_OF_MONTH);
        Date exclusiveEndDate = org.apache.commons.lang3.time.DateUtils.addDays(
                org.apache.commons.lang3.time.DateUtils.truncate(dateEnd, Calendar.DAY_OF_MONTH), 1);
        Integer demographicId = parseDemographicNo(demographicNo);
        EmailLog.EmailStatus status = parseEmailStatus(emailStatus);

        Query query = entityManager.createQuery(hql);
        query.setParameter(1, demographicId);
        query.setParameter(2, status);
        query.setParameter(3, senderEmailAddress);
        query.setParameter(4, startDate);
        query.setParameter(5, exclusiveEndDate);

        return query.getResultList();
    }

    private Integer parseDemographicNo(String demographicNo) {
        if (demographicNo == null || demographicNo.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(demographicNo.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private EmailLog.EmailStatus parseEmailStatus(String emailStatus) {
        if (emailStatus == null || emailStatus.isBlank()) {
            return null;
        }
        try {
            return EmailLog.EmailStatus.valueOf(emailStatus.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Updates the status, error message, and timestamp of an email log without loading or modifying
     * Large Object (LOB) fields.
     *
     * <p>This method provides an optimized update operation that avoids loading large binary fields
     * (email body, encrypted message, internal comments, attachments) into memory. This is critical
     * for performance when processing email status changes in bulk or when email bodies contain
     * large amounts of PHI (Protected Health Information).</p>
     *
     * <p>The update executes directly via JPQL UPDATE statement, bypassing the entity lifecycle
     * and avoiding LOB field hydration. This makes it suitable for:</p>
     * <ul>
     *   <li>Batch status updates after email processing jobs</li>
     *   <li>Error recording for failed email deliveries</li>
     *   <li>Status transitions (for example, PENDING to SUCCESS or FAILED to RESOLVED)</li>
     *   <li>Compare-and-set protection against concurrent status changes</li>
     * </ul>
     *
     * <p><strong>Important:</strong> Setting errorMessage to {@code null} explicitly clears the
     * database column. Manual resolution therefore passes through the existing diagnostic instead
     * of clearing it.</p>
     *
     * @param id Integer the unique identifier of the EmailLog record to update
     * @param expectedStatus EmailLog.EmailStatus the status the row must currently have
     * @param newStatus EmailLog.EmailStatus the new email status (SUCCESS, FAILED, or RESOLVED)
     * @param errorMessage String the error message to record, or {@code null} to clear existing error message
     * @param timestamp Date the timestamp to set, typically current time or email processing time
     * @return int the number of database rows updated (1 if record exists and was updated, 0 if not found)
     */
    @Override
    public int transitionEmailStatus(Integer id, EmailLog.EmailStatus expectedStatus,
            EmailLog.EmailStatus newStatus, String errorMessage, Date timestamp) {
        String hql = "UPDATE EmailLog e SET e.status = :newStatus, e.errorMessage = :msg, e.timestamp = :ts "
                + "WHERE e.id = :id AND e.status = :expectedStatus";
        Query query = entityManager.createQuery(hql);
        query.setParameter("id", id);
        query.setParameter("expectedStatus", expectedStatus);
        query.setParameter("newStatus", newStatus);
        query.setParameter("msg", errorMessage);
        query.setParameter("ts", timestamp);
        int updatedRows = query.executeUpdate();
        // Bulk JPQL bypasses the persistence context. Refresh this row so a competing
        // transition cannot be hidden by a previously loaded PENDING entity.
        EmailLog current = entityManager.find(EmailLog.class, id);
        if (current != null) {
            entityManager.refresh(current);
        }
        return updatedRows;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int replaceBody(Integer id, String replacement) {
        // Only the body column: an entity write would also write back the status and timestamp it had
        // read, undoing a status change another request made in between.
        return entityManager.createQuery("UPDATE EmailLog e SET e.body = :body WHERE e.id = :id")
                .setParameter("body", encodeBody(Objects.requireNonNull(replacement, "replacement")))
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<Integer> findIdsByTransactionTypeChangedBeforeWithOtherBody(EmailLog.TransactionType type,
            Date changedBefore, Date expiredBefore, Date agedBefore, String body, int afterId, int limit) {
        return withSettledParameters(entityManager.createQuery("SELECT e.id FROM EmailLog e WHERE "
                        + "e.transactionType = :type AND " + CODE_NOT_NEEDED
                        + " AND e.id > :afterId AND (e.body IS NULL OR e.body <> :body) ORDER BY e.id", Integer.class),
                changedBefore, expiredBefore, agedBefore)
                .setParameter("type", type)
                .setParameter("afterId", afterId)
                .setParameter("body", encodeBody(Objects.requireNonNull(body, "body")))
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int replaceBodyIfUnchangedBefore(Integer id, EmailLog.TransactionType type, Date changedBefore,
            Date expiredBefore, Date agedBefore, String replacement) {
        return withSettledParameters(entityManager.createQuery("UPDATE EmailLog e SET e.body = :body "
                        + "WHERE e.id = :id AND e.transactionType = :type AND " + CODE_NOT_NEEDED
                        + " AND (e.body IS NULL OR e.body <> :body)"),
                changedBefore, expiredBefore, agedBefore)
                .setParameter("id", id)
                .setParameter("type", type)
                .setParameter("body", encodeBody(Objects.requireNonNull(replacement, "replacement")))
                .executeUpdate();
    }

    private static <Q extends Query> Q withSettledParameters(Q query, Date changedBefore, Date expiredBefore,
            Date agedBefore) {
        query.setParameter("settledStatuses", List.of(EmailLog.EmailStatus.SUCCESS, EmailLog.EmailStatus.BLOCKED));
        query.setParameter("failed", EmailLog.EmailStatus.FAILED);
        query.setParameter("refusedState", PatientPortalInviteDelivery.State.SEND_FAILED);
        query.setParameter("refusedOutcome", PatientPortalInviteDelivery.Outcome.SEND_REFUSED);
        query.setParameter("resolved", EmailLog.EmailStatus.RESOLVED);
        query.setParameter("notArrivedState", PatientPortalInviteDelivery.State.NOT_ARRIVED);
        query.setParameter("changedBefore", Objects.requireNonNull(changedBefore, "changedBefore"));
        query.setParameter("expiredBefore", Objects.requireNonNull(expiredBefore, "expiredBefore"));
        query.setParameter("agedBefore", Objects.requireNonNull(agedBefore, "agedBefore"));
        return query;
    }

    /** The stored form of a body, as {@link EmailLog#setBody(String)} writes it. */
    private static byte[] encodeBody(String body) {
        return Base64.encodeBase64(body.getBytes(StandardCharsets.UTF_8));
    }
}
