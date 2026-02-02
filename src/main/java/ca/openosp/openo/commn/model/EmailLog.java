//CHECKSTYLE:OFF
package ca.openosp.openo.commn.model;

import javax.persistence.*;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;

import java.util.Date;
import java.util.List;

/**
 * Entity representing an email communication log in the healthcare system.
 * 
 * <p>This class tracks all email communications sent through the OpenO EMR system,
 * including clinical communications, patient correspondence, consultation requests,
 * and other healthcare-related notifications. It provides comprehensive audit trail
 * capabilities for regulatory compliance (HIPAA/PIPEDA) and supports encrypted
 * messaging for protected health information (PHI).</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Base64-encoded storage of email body and encrypted messages for security</li>
 *   <li>Support for multiple transaction types (eForm, consultation, tickler, direct)</li>
 *   <li>Attachment management with encryption support</li>
 *   <li>Status tracking (success, failed, resolved) for delivery monitoring</li>
 *   <li>Patient and provider associations for clinical context</li>
 *   <li>Chart display options for clinical note integration</li>
 *   <li>Password-protected encrypted messages with password clues</li>
 * </ul>
 * 
 * <p>The email body and encrypted messages are stored as Base64-encoded byte arrays
 * to ensure proper handling of special characters and to maintain data integrity
 * across different character encodings.</p>
 * 
 * @see EmailConfig Email configuration settings for SMTP/API providers
 * @see EmailAttachment File attachments associated with email logs
 * @see Demographic Patient demographic information
 * @see Provider Healthcare provider information
 * @since 2026-01-14
 */
@Entity
@Table(name = "emailLog")
public class EmailLog extends AbstractModel<Integer> implements Comparable<EmailLog> {

    /**
     * Enumeration of possible email delivery statuses.
     * Used for tracking the lifecycle and delivery state of email communications.
     */
    public enum EmailStatus {
        /** Email was successfully sent and delivered */
        SUCCESS,
        /** Email failed to send due to an error */
        FAILED,
        /** Previously failed email was successfully resent or issue resolved */
        RESOLVED
    }

    /**
     * Enumeration defining how email content should be displayed in patient charts.
     * Controls integration of email correspondence into clinical documentation.
     */
    public enum ChartDisplayOption {
        /** Do not add email content as a clinical note in the patient chart */
        WITHOUT_NOTE("doNotAddAsNote"),
        /** Add the complete email content as a clinical note in the patient chart */
        WITH_FULL_NOTE("addFullNote");

        private final String value;

        ChartDisplayOption(String value) {
            this.value = value;
        }

        /**
         * Gets the string value associated with this chart display option.
         * 
         * @return String the configuration value for this display option
         */
        public String getValue() {
            return value;
        }
    }

    /**
     * Enumeration of transaction types that can generate email communications.
     * Identifies the source workflow or feature that initiated the email.
     */
    public enum TransactionType {
        /** Email originated from an electronic form (eForm) submission */
        EFORM,
        /** Email related to a consultation request or referral */
        CONSULTATION,
        /** Email generated from a tickler or reminder notification */
        TICKLER,
        /** Direct email communication not tied to a specific transaction type */
        DIRECT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String fromEmail;

    private String toEmail;

    private String subject;

    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] body;

    @Enumerated(EnumType.STRING)
    private EmailStatus status;

    private String errorMessage;

    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp = new Date();

    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] encryptedMessage;

    private String password;

    private String passwordClue;

    private boolean isEncrypted;

    private boolean isAttachmentEncrypted;

    @Enumerated(EnumType.STRING)
    private ChartDisplayOption chartDisplayOption;

    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] internalComment;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private String additionalParams;

    @ManyToOne
    @JoinColumn(name = "DemographicNo")
    private Demographic demographic;

    @ManyToOne
    @JoinColumn(name = "ProviderNo")
    private Provider provider;

    @ManyToOne
    @JoinColumn(name = "configId")
    private EmailConfig emailConfig;

    @OneToMany(mappedBy = "emailLog", fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<EmailAttachment> emailAttachments;

    /**
     * Default constructor for JPA entity instantiation.
     */
    public EmailLog() {
    }

    /**
     * Constructs a new EmailLog with essential email details.
     * The email body is automatically Base64-encoded for secure storage.
     * 
     * @param emailConfig EmailConfig the email configuration used to send this email
     * @param fromEmail String the sender's email address
     * @param toEmail String[] array of recipient email addresses
     * @param subject String the email subject line
     * @param body String the email message body (will be Base64-encoded)
     * @param status EmailStatus the initial delivery status of the email
     */
    public EmailLog(EmailConfig emailConfig, String fromEmail, String[] toEmail, String subject, String body, EmailStatus status) {
        this.emailConfig = emailConfig;
        this.fromEmail = fromEmail;
        this.toEmail = toEmail != null ? String.join(";", toEmail) : "";
        this.subject = subject;
        this.body = Base64.encodeBase64(body.getBytes(StandardCharsets.UTF_8));
        this.status = status;
        this.timestamp = new Date();
    }

    /**
     * Gets the unique identifier for this email log entry.
     * 
     * @return Integer the email log ID
     */
    public Integer getId() {
        return id;
    }

    /**
     * Gets the email configuration used to send this email.
     * 
     * @return EmailConfig the email configuration containing SMTP/API settings
     */
    public EmailConfig getEmailConfig() {
        return emailConfig;
    }

    /**
     * Sets the email configuration for this email log.
     * 
     * @param emailConfig EmailConfig the email configuration to associate with this log
     */
    public void setEmailConfig(EmailConfig emailConfig) {
        this.emailConfig = emailConfig;
    }

    /**
     * Gets the sender's email address.
     * 
     * @return String the from email address
     */
    public String getFromEmail() {
        return fromEmail;
    }

    /**
     * Sets the sender's email address.
     * 
     * @param fromEmail String the from email address
     */
    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    /**
     * Gets the recipient email addresses as an array.
     * Multiple recipients are stored internally as semicolon-separated values
     * and returned as an array.
     * 
     * @return String[] array of recipient email addresses
     */
    public String[] getToEmail() {
        return toEmail.split(";");
    }

    /**
     * Sets the recipient email addresses.
     * Multiple recipients are joined with semicolons for storage.
     * 
     * @param toEmail String[] array of recipient email addresses
     */
    public void setToEmail(String[] toEmail) {
        this.toEmail = toEmail != null ? String.join(";", toEmail) : "";
    }

    /**
     * Gets the email subject line.
     * 
     * @return String the email subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the email subject line.
     * 
     * @param subject String the email subject
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Gets the email body content.
     * The body is stored as Base64-encoded bytes and automatically decoded when retrieved.
     * 
     * @return String the decoded email body content
     */
    public String getBody() {
        return new String(Base64.decodeBase64(body), StandardCharsets.UTF_8);
    }

    /**
     * Sets the email body content.
     * The body is automatically Base64-encoded before storage.
     * 
     * @param body String the email body content to encode and store
     */
    public void setBody(String body) {
        this.body = Base64.encodeBase64(body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the current delivery status of the email.
     * 
     * @return EmailStatus the email delivery status (SUCCESS, FAILED, or RESOLVED)
     */
    public EmailStatus getStatus() {
        return status;
    }

    /**
     * Sets the delivery status of the email.
     * 
     * @param status EmailStatus the email delivery status
     */
    public void setStatus(EmailStatus status) {
        this.status = status;
    }

    /**
     * Gets the error message if the email failed to send.
     * 
     * @return String the error message, or null if no error occurred
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message for failed email delivery.
     * 
     * @param errorMessage String the error message describing the failure
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Gets the timestamp when this email log entry was created.
     * 
     * @return Date the creation timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp for this email log entry.
     * 
     * @param timestamp Date the timestamp to set
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the encrypted message content.
     * The encrypted message is stored as Base64-encoded bytes and automatically decoded.
     * 
     * @return String the decoded encrypted message content
     */
    public String getEncryptedMessage() {
        return new String(Base64.decodeBase64(encryptedMessage), StandardCharsets.UTF_8);
    }

    /**
     * Sets the encrypted message content.
     * The message is automatically Base64-encoded before storage.
     * 
     * @param encryptedMessage String the encrypted message content to encode and store
     */
    public void setEncryptedMessage(String encryptedMessage) {
        this.encryptedMessage = Base64.encodeBase64(encryptedMessage.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the password used for message encryption.
     * 
     * @return String the encryption password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password for message encryption.
     * 
     * @param password String the password to use for encryption
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Gets the password hint/clue for encrypted messages.
     * Helps recipients remember the password without storing it directly.
     * 
     * @return String the password clue
     */
    public String getPasswordClue() {
        return passwordClue;
    }

    /**
     * Sets the password hint/clue for encrypted messages.
     * 
     * @param passwordClue String the password clue to help recipients
     */
    public void setPasswordClue(String passwordClue) {
        this.passwordClue = passwordClue;
    }

    /**
     * Checks if the email message is encrypted.
     * 
     * @return boolean true if the message content is encrypted, false otherwise
     */
    public boolean getIsEncrypted() {
        return isEncrypted;
    }

    /**
     * Sets whether the email message is encrypted.
     * 
     * @param isEncrypted boolean true to mark the message as encrypted
     */
    public void setIsEncrypted(boolean isEncrypted) {
        this.isEncrypted = isEncrypted;
    }

    /**
     * Checks if email attachments are encrypted.
     * 
     * @return boolean true if attachments are encrypted, false otherwise
     */
    public boolean getIsAttachmentEncrypted() {
        return isAttachmentEncrypted;
    }

    /**
     * Sets whether email attachments are encrypted.
     * 
     * @param isAttachmentEncrypted boolean true to mark attachments as encrypted
     */
    public void setIsAttachmentEncrypted(boolean isAttachmentEncrypted) {
        this.isAttachmentEncrypted = isAttachmentEncrypted;
    }

    /**
     * Gets the chart display option for this email.
     * Determines how/if this email should be added to the patient's clinical chart.
     * 
     * @return ChartDisplayOption the chart display setting
     */
    public ChartDisplayOption getChartDisplayOption() {
        return chartDisplayOption;
    }

    /**
     * Sets the chart display option for this email.
     * 
     * @param chartDisplayOption ChartDisplayOption the chart display setting
     */
    public void setChartDisplayOption(ChartDisplayOption chartDisplayOption) {
        this.chartDisplayOption = chartDisplayOption;
    }

    /**
     * Gets the internal comment for this email log.
     * Internal comments are stored as Base64-encoded bytes and automatically decoded.
     * Used for staff notes that are not part of the clinical record.
     * 
     * @return String the decoded internal comment
     */
    public String getInternalComment() {
        return new String(Base64.decodeBase64(internalComment), StandardCharsets.UTF_8);
    }

    /**
     * Sets the internal comment for this email log.
     * The comment is automatically Base64-encoded before storage.
     * 
     * @param internalComment String the internal comment to encode and store
     */
    public void setInternalComment(String internalComment) {
        this.internalComment = Base64.encodeBase64(internalComment.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the transaction type that generated this email.
     * 
     * @return TransactionType the source transaction type (EFORM, CONSULTATION, TICKLER, or DIRECT)
     */
    public TransactionType getTransactionType() {
        return transactionType;
    }

    /**
     * Sets the transaction type that generated this email.
     * 
     * @param transactionType TransactionType the source transaction type
     */
    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    /**
     * Gets the patient demographic associated with this email.
     * Links the email to a specific patient record when applicable.
     * 
     * @return Demographic the patient demographic record, or null if not patient-specific
     */
    public Demographic getDemographic() {
        return demographic;
    }

    /**
     * Sets the patient demographic associated with this email.
     * 
     * @param demographic Demographic the patient demographic record
     */
    public void setDemographic(Demographic demographic) {
        this.demographic = demographic;
    }

    /**
     * Gets the healthcare provider associated with this email.
     * Identifies the provider who sent or is responsible for the email.
     * 
     * @return Provider the healthcare provider record, or null if not provider-specific
     */
    public Provider getProvider() {
        return provider;
    }

    /**
     * Sets the healthcare provider associated with this email.
     * 
     * @param provider Provider the healthcare provider record
     */
    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    /**
     * Gets additional parameters as a string.
     * Used for storing transaction-type-specific metadata or custom parameters.
     * 
     * @return String the additional parameters
     */
    public String getAdditionalParams() {
        return additionalParams;
    }

    /**
     * Sets additional parameters for this email log.
     * 
     * @param additionalParams String the additional parameters to store
     */
    public void setAdditionalParams(String additionalParams) {
        this.additionalParams = additionalParams;
    }

    /**
     * Gets the list of file attachments associated with this email.
     * 
     * @return List&lt;EmailAttachment&gt; the list of email attachments
     */
    public List<EmailAttachment> getEmailAttachments() {
        return emailAttachments;
    }

    /**
     * Sets the list of file attachments for this email.
     * 
     * @param emailAttachments List&lt;EmailAttachment&gt; the list of attachments to associate
     */
    public void setEmailAttachments(List<EmailAttachment> emailAttachments) {
        this.emailAttachments = emailAttachments;
    }

    /**
     * Compares this email log to another based on timestamp.
     * Enables chronological sorting of email logs.
     * 
     * @param other EmailLog the email log to compare to
     * @return int negative if this is earlier, positive if later, zero if equal
     */
    @Override
    public int compareTo(EmailLog other) {
        // Compare based on the timestamp
        return this.timestamp.compareTo(other.timestamp);
    }
}
