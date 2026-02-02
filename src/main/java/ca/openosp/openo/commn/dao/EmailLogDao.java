//CHECKSTYLE:OFF
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.EmailLog;

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
 *   <li>Email delivery status (SUCCESS, FAILED, RESOLVED)</li>
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
 * @see ca.openosp.openo.commn.model.EmailLog.EmailStatus
 * @see ca.openosp.openo.commn.model.EmailLog.TransactionType
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
     * @param emailStatus String the delivery status to filter by (SUCCESS, FAILED, RESOLVED); null to include all statuses
     * @return List&lt;EmailLog&gt; list of email log records matching the specified criteria, ordered by timestamp
     */
    public List<EmailLog> getEmailStatusByDateDemographicSenderStatus(Date dateBegin, Date dateEnd, String demographicNo, String senderEmailAddress, String emailStatus);

    /**
     * Updates the delivery status and error information for an email log record.
     * <p>
     * This method is used by email sending services to update the status of email delivery attempts.
     * It supports tracking both successful deliveries and failed attempts with error diagnostics.
     * For failed emails, the errorMessage parameter should contain specific SMTP error codes or
     * exception messages to aid in troubleshooting.
     * </p>
     * <p>
     * Typical workflow:
     * <ol>
     *   <li>Email log created with initial status (typically FAILED for new records)</li>
     *   <li>Email sending service attempts delivery</li>
     *   <li>This method updates status to SUCCESS or maintains FAILED with error details</li>
     *   <li>Status may be updated to RESOLVED when issues are addressed</li>
     * </ol>
     * </p>
     *
     * @param id Integer the unique identifier of the email log record to update
     * @param status EmailLog.EmailStatus the new delivery status (SUCCESS, FAILED, or RESOLVED)
     * @param errorMessage String the error message for failed deliveries; null for successful deliveries
     * @param timestamp Date the timestamp of the status update; typically the current time
     * @return int the number of records updated (should be 1 for successful update, 0 if ID not found)
     */
    public int updateEmailStatus(Integer id, EmailLog.EmailStatus status, String errorMessage, Date timestamp);
}
