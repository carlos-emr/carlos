package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EmailLog;

import java.util.Date;
import java.util.List;

/**
 * Data Access Object for managing email log records in OpenO EMR.
 * <p>
 * This DAO provides operations for tracking outbound email communications sent from the EMR system,
 * including patient notifications, consultation requests, eForm submissions, and tickler alerts.
 * Email logs maintain audit trails for compliance with healthcare privacy regulations (PIPEDA/HIPAA)
 * and support troubleshooting email delivery issues.
 * </p>
 * <p>
 * Email logs track:
 * <ul>
 *   <li>Email delivery status (PENDING, SUCCESS, FAILED, RESOLVED)</li>
 *   <li>Transaction context (EFORM, CONSULTATION, TICKLER, DIRECT)</li>
 *   <li>Associated patient demographics and healthcare provider</li>
 *   <li>Encryption status for PHI-containing emails</li>
 *   <li>Error messages for failed delivery attempts</li>
 * </ul>
 * </p>
 * <p>
 * This interface extends {@link AbstractDao} to inherit standard CRUD operations and adds
 * specialized query methods for email status tracking and reporting.
 * </p>
 *
 * @see EmailLog
 * @see AbstractDao
 * @see io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus
 * @see io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType
 * @since 2026-01-23
 */
public interface EmailLogDao extends AbstractDao<EmailLog> {

    /**
     * Retrieves email log records filtered by date range, patient, sender, and delivery status.
     * <p>
     * This method supports comprehensive email audit queries for healthcare compliance and troubleshooting.
     * All parameters are optional (nullable) to allow flexible filtering. Passing null for a parameter
     * excludes that criterion from the query.
     * </p>
     * <p>
     * Common use cases:
     * <ul>
     *   <li>Audit all emails sent to a specific patient within a date range</li>
     *   <li>Find failed email deliveries for troubleshooting</li>
     *   <li>Track emails sent from a specific provider's email address</li>
     *   <li>Generate compliance reports for email communications</li>
     * </ul>
     * </p>
     *
     * @param dateBegin Date the start of the date range filter (inclusive); null to ignore start date
     * @param dateEnd Date the end of the date range filter (inclusive); null to ignore end date
     * @param demographicNo String the patient demographic number to filter by; null to include all patients
     * @param senderEmailAddress String the sender's email address to filter by; null to include all senders
     * @param emailStatus String the delivery status to filter by (PENDING, SUCCESS, FAILED, RESOLVED); null to include all statuses
     * @return List&lt;EmailLog&gt; list of email log records matching the specified criteria, ordered by timestamp
     */
    public List<EmailLog> getEmailStatusByDateDemographicSenderStatus(Date dateBegin, Date dateEnd, String demographicNo, String senderEmailAddress, String emailStatus);

    /**
     * Atomically changes an email status only when the persisted row is still in the expected
     * state. Callers must check the returned row count: zero means the record was removed or a
     * concurrent request won the transition.
     *
     * @param id the email log identifier
     * @param expectedStatus the only status from which the transition is allowed
     * @param newStatus the target status
     * @param errorMessage the status detail to persist
     * @param timestamp the timestamp to persist
     * @return one when the transition was applied, otherwise zero
     */
    public int transitionEmailStatus(Integer id, EmailLog.EmailStatus expectedStatus,
            EmailLog.EmailStatus newStatus, String errorMessage, Date timestamp);
}
