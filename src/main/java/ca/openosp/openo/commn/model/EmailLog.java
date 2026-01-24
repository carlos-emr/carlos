//CHECKSTYLE:OFF
package ca.openosp.openo.commn.model;

import javax.persistence.*;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;

import java.util.Date;
import java.util.List;

/**
 * Represents an email transaction log entry in the OpenO EMR system.
 * This entity tracks all outbound email communications including clinical correspondence,
 * eforms, consultations, and tickler notifications sent through the system.
 *
 * <p>The EmailLog entity provides comprehensive audit trail functionality for email communications
 * in compliance with healthcare privacy regulations (HIPAA/PIPEDA). It supports encryption for
 * sensitive patient health information (PHI), tracks delivery status, and maintains associations
 * with patient demographics and healthcare providers.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Base64 encoding for email body and internal comments to handle special characters</li>
 *   <li>Optional message encryption with password protection for PHI compliance</li>
 *   <li>Support for multiple recipients via semicolon-delimited email addresses</li>
 *   <li>Transaction type tracking (EFORM, CONSULTATION, TICKLER, DIRECT)</li>
 *   <li>Attachment management with encryption support</li>
 *   <li>Chart display options for clinical documentation integration</li>
 *   <li>Error message logging for failed delivery troubleshooting</li>
 * </ul>
 *
 * @see EmailConfig
 * @see EmailAttachment
 * @see Demographic
 * @see Provider
 * @since 2026-01-23
 */
@Entity
@Table(name = "emailLog")
public class EmailLog extends AbstractModel<Integer> implements Comparable<EmailLog> {

    /**
     * Represents the delivery status of an email transaction.
     *
     * <p>Status transitions typically follow:</p>
     * <ul>
     *   <li>SUCCESS - Email was successfully sent through the mail server</li>
     *   <li>FAILED - Email delivery failed (see errorMessage for details)</li>
     *   <li>RESOLVED - A previously failed email has been manually resolved</li>
     * </ul>
     */
    public enum EmailStatus {
        /** Email was successfully sent through the mail server */
        SUCCESS,
        /** Email delivery failed (see errorMessage for details) */
        FAILED,
        /** A previously failed email has been manually resolved */
        RESOLVED
    }

    /**
     * Determines how email content should be displayed in the patient's clinical chart.
     *
     * <p>This option controls whether email communications are automatically added to the
     * patient's chart as clinical notes for comprehensive documentation of patient interactions.</p>
     */
    public enum ChartDisplayOption {
        /** Do not add email content as a note in the patient chart */
        WITHOUT_NOTE("doNotAddAsNote"),
        /** Add the complete email content as a note in the patient chart */
        WITH_FULL_NOTE("addFullNote");

        private final String value;

        ChartDisplayOption(String value) {
            this.value = value;
        }

        /**
         * Returns the string value of the chart display option.
         *
         * @return String the option value used in form submissions and API calls
         */
        public String getValue() {
            return value;
        }
    }

    /**
     * Categorizes the source or purpose of the email transaction within the OpenO EMR workflow.
     *
     * <p>Transaction types enable tracking and reporting of different email communication
     * patterns within the clinical workflow.</p>
     */
    public enum TransactionType {
        /** Email originated from an electronic form (eForm) submission */
        EFORM,
        /** Email is part of a patient consultation or referral workflow */
        CONSULTATION,
        /** Email was triggered by a tickler reminder or notification */
        TICKLER,
        /** Email sent directly through the system without a specific workflow context */
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
     * Default constructor required by JPA/Hibernate for entity instantiation.
     */
    public EmailLog() {
    }

    /**
     * Constructs a new EmailLog entry with core email transaction details.
     *
     * <p>This constructor automatically Base64-encodes the email body and sets the timestamp
     * to the current date/time. Multiple recipients are joined with semicolons for storage.</p>
     *
     * @param emailConfig EmailConfig the email configuration used for sending
     * @param fromEmail String the sender's email address
     * @param toEmail String[] array of recipient email addresses
     * @param subject String the email subject line
     * @param body String the email body content (will be Base64-encoded)
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
     * Returns the unique identifier for this email log entry.
     *
     * @return Integer the database-generated primary key
     */
    public Integer getId() {
        return id;
    }

    /**
     * Returns the email configuration used for sending this email.
     *
     * @return EmailConfig the email server configuration and credentials
     */
    public EmailConfig getEmailConfig() {
        return emailConfig;
    }

    /**
     * Sets the email configuration for this email transaction.
     *
     * @param emailConfig EmailConfig the email server configuration and credentials
     */
    public void setEmailConfig(EmailConfig emailConfig) {
        this.emailConfig = emailConfig;
    }

    /**
     * Returns the sender's email address.
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
     * Returns the recipient email addresses as an array.
     *
     * <p>Multiple recipients are stored internally as a semicolon-delimited string
     * but returned as a String array for convenient iteration.</p>
     *
     * @return String[] array of recipient email addresses
     */
    public String[] getToEmail() {
        return toEmail.split(";");
    }

    /**
     * Sets the recipient email addresses from an array.
     *
     * <p>The array is joined with semicolons for database storage.</p>
     *
     * @param toEmail String[] array of recipient email addresses
     */
    public void setToEmail(String[] toEmail) {
        this.toEmail = toEmail != null ? String.join(";", toEmail) : "";
    }

    /**
     * Returns the email subject line.
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
     * Returns the decoded email body content.
     *
     * <p>The body is stored as Base64-encoded bytes in the database
     * and automatically decoded to UTF-8 string when retrieved.</p>
     *
     * @return String the decoded email body content
     */
    public String getBody() {
        return new String(Base64.decodeBase64(body), StandardCharsets.UTF_8);
    }

    /**
     * Sets the email body content with automatic Base64 encoding.
     *
     * <p>The body string is automatically encoded to Base64 before storage
     * to handle special characters and preserve formatting.</p>
     *
     * @param body String the email body content to encode and store
     */
    public void setBody(String body) {
        this.body = Base64.encodeBase64(body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the current delivery status of the email.
     *
     * @return EmailStatus the delivery status (SUCCESS, FAILED, or RESOLVED)
     */
    public EmailStatus getStatus() {
        return status;
    }

    /**
     * Sets the delivery status of the email.
     *
     * @param status EmailStatus the delivery status (SUCCESS, FAILED, or RESOLVED)
     */
    public void setStatus(EmailStatus status) {
        this.status = status;
    }

    /**
     * Returns the error message if email delivery failed.
     *
     * @return String the error message or null if no error occurred
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message for failed email delivery.
     *
     * @param errorMessage String the error message describing the delivery failure
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the timestamp when the email was sent or attempted.
     *
     * @return Date the email transaction timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp for this email transaction.
     *
     * @param timestamp Date the email transaction timestamp
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the decrypted encrypted message content.
     *
     * <p>The encrypted message is stored as Base64-encoded bytes in the database
     * and automatically decoded to UTF-8 string when retrieved. This field contains
     * the encrypted version of PHI content when email encryption is enabled.</p>
     *
     * @return String the decoded encrypted message content or null if not encrypted
     */
    public String getEncryptedMessage() {
        return new String(Base64.decodeBase64(encryptedMessage), StandardCharsets.UTF_8);
    }

    /**
     * Sets the encrypted message content with automatic Base64 encoding.
     *
     * <p>The encrypted message string is automatically encoded to Base64 before storage.
     * This should contain the encrypted version of PHI content when email encryption is enabled.</p>
     *
     * @param encryptedMessage String the encrypted message content to encode and store
     */
    public void setEncryptedMessage(String encryptedMessage) {
        this.encryptedMessage = Base64.encodeBase64(encryptedMessage.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the password used for message encryption.
     *
     * <p>WARNING: This password is stored in plain text for email encryption purposes.
     * It should be a temporary password shared with the recipient, not a system password.</p>
     *
     * @return String the encryption password or null if not encrypted
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password for message encryption.
     *
     * <p>WARNING: This password will be stored in plain text. Use only temporary
     * passwords intended for email encryption, not system passwords.</p>
     *
     * @param password String the encryption password for this email
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns the password hint or clue for encrypted emails.
     *
     * @return String the password hint or null if not provided
     */
    public String getPasswordClue() {
        return passwordClue;
    }

    /**
     * Sets the password hint or clue to help recipients decrypt the email.
     *
     * @param passwordClue String the password hint or clue
     */
    public void setPasswordClue(String passwordClue) {
        this.passwordClue = passwordClue;
    }

    /**
     * Returns whether the email message content is encrypted.
     *
     * @return boolean true if the message is encrypted, false otherwise
     */
    public boolean getIsEncrypted() {
        return isEncrypted;
    }

    /**
     * Sets whether the email message content is encrypted.
     *
     * @param isEncrypted boolean true if the message is encrypted, false otherwise
     */
    public void setIsEncrypted(boolean isEncrypted) {
        this.isEncrypted = isEncrypted;
    }

    /**
     * Returns whether the email attachments are encrypted.
     *
     * @return boolean true if attachments are encrypted, false otherwise
     */
    public boolean getIsAttachmentEncrypted() {
        return isAttachmentEncrypted;
    }

    /**
     * Sets whether the email attachments are encrypted.
     *
     * @param isAttachmentEncrypted boolean true if attachments are encrypted, false otherwise
     */
    public void setIsAttachmentEncrypted(boolean isAttachmentEncrypted) {
        this.isAttachmentEncrypted = isAttachmentEncrypted;
    }

    /**
     * Returns how this email should be displayed in the patient's chart.
     *
     * @return ChartDisplayOption the chart display option (WITH_FULL_NOTE or WITHOUT_NOTE)
     */
    public ChartDisplayOption getChartDisplayOption() {
        return chartDisplayOption;
    }

    /**
     * Sets how this email should be displayed in the patient's chart.
     *
     * @param chartDisplayOption ChartDisplayOption the chart display option
     */
    public void setChartDisplayOption(ChartDisplayOption chartDisplayOption) {
        this.chartDisplayOption = chartDisplayOption;
    }

    /**
     * Returns the decoded internal comment for this email transaction.
     *
     * <p>Internal comments are stored as Base64-encoded bytes and automatically
     * decoded to UTF-8 string. These comments are for internal staff use only
     * and not sent to email recipients.</p>
     *
     * @return String the decoded internal comment or null if not set
     */
    public String getInternalComment() {
        return new String(Base64.decodeBase64(internalComment), StandardCharsets.UTF_8);
    }

    /**
     * Sets the internal comment with automatic Base64 encoding.
     *
     * <p>Internal comments are for staff use only and not sent to email recipients.
     * The comment is automatically Base64-encoded before storage.</p>
     *
     * @param internalComment String the internal comment to encode and store
     */
    public void setInternalComment(String internalComment) {
        this.internalComment = Base64.encodeBase64(internalComment.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the transaction type that triggered this email.
     *
     * @return TransactionType the transaction type (EFORM, CONSULTATION, TICKLER, or DIRECT)
     */
    public TransactionType getTransactionType() {
        return transactionType;
    }

    /**
     * Sets the transaction type that triggered this email.
     *
     * @param transactionType TransactionType the transaction type
     */
    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    /**
     * Returns the patient demographic record associated with this email.
     *
     * @return Demographic the patient demographic or null if not patient-related
     */
    public Demographic getDemographic() {
        return demographic;
    }

    /**
     * Sets the patient demographic record associated with this email.
     *
     * @param demographic Demographic the patient demographic
     */
    public void setDemographic(Demographic demographic) {
        this.demographic = demographic;
    }

    /**
     * Returns the healthcare provider who sent or initiated this email.
     *
     * @return Provider the provider or null if system-generated
     */
    public Provider getProvider() {
        return provider;
    }

    /**
     * Sets the healthcare provider who sent or initiated this email.
     *
     * @param provider Provider the provider
     */
    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    /**
     * Returns additional parameters specific to the transaction type.
     *
     * <p>This field stores transaction-specific metadata such as eForm IDs,
     * consultation reference numbers, or tickler identifiers.</p>
     *
     * @return String the additional parameters as a string or null if not set
     */
    public String getAdditionalParams() {
        return additionalParams;
    }

    /**
     * Sets additional parameters specific to the transaction type.
     *
     * @param additionalParams String the additional parameters
     */
    public void setAdditionalParams(String additionalParams) {
        this.additionalParams = additionalParams;
    }

    /**
     * Returns the list of email attachments for this email.
     *
     * @return List&lt;EmailAttachment&gt; list of email attachments or null if no attachments
     */
    public List<EmailAttachment> getEmailAttachments() {
        return emailAttachments;
    }

    /**
     * Sets the list of email attachments for this email.
     *
     * @param emailAttachments List&lt;EmailAttachment&gt; list of email attachments
     */
    public void setEmailAttachments(List<EmailAttachment> emailAttachments) {
        this.emailAttachments = emailAttachments;
    }

    /**
     * Compares this EmailLog with another EmailLog based on timestamp.
     *
     * <p>This implementation allows EmailLog objects to be sorted chronologically
     * by their timestamp field in natural ascending order (earliest first).</p>
     *
     * @param other EmailLog the other EmailLog to compare to
     * @return int negative if this timestamp is before other, zero if equal, positive if after
     */
    @Override
    public int compareTo(EmailLog other) {
        // Compare based on the timestamp
        return this.timestamp.compareTo(other.timestamp);
    }
}
