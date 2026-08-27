package io.github.carlos_emr.carlos.email.core;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;

/**
 * Represents the result of an email status query in the OpenO EMR email system.
 *
 * <p>This class encapsulates comprehensive information about an email transaction including
 * sender details, recipient information, healthcare provider context, encryption status,
 * delivery status, and timestamps. It is primarily used for tracking and displaying
 * email communication history within the healthcare environment.</p>
 *
 * <p>The class supports natural ordering based on email creation timestamps through
 * the Comparable interface, allowing chronological sorting of email status records.</p>
 *
 * <p><strong>Healthcare Context:</strong> Email communications in OpenO EMR may contain
 * Protected Health Information (PHI) and require encryption and audit logging. This class
 * tracks encryption status and maintains comprehensive audit information including provider
 * associations and delivery status.</p>
 *
 * @see io.github.carlos_emr.carlos.commn.model.EmailLog
 * @see io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus
 * @since 2026-01-23
 */
public class EmailStatusResult implements Comparable<EmailStatusResult> {
    private Integer logId;
    private String subject;
    private String senderFirstName;
    private String senderLastName;
    private String senderEmail;
    private String recipientFirstName;
    private String recipientLastName;
    private String providerFirstName;
    private String providerLastName;
    private String recipientEmail;
    private boolean isEncrypted;
    private EmailStatus status;
    private String errorMessage;
    private Date created;
    private boolean resolvable;
    private EmailConsentStatus consentStatus;
    private Integer consentId;
    private Date consentLastUpdateDate;
    private boolean consentOverride;
    private String consentOverrideReason;

    /**
     * Default constructor for creating an empty EmailStatusResult instance.
     */
    public EmailStatusResult() {
    }

    /**
     * Constructs a fully populated EmailStatusResult with all email transaction details.
     *
     * @param logId Integer the unique identifier for the email log entry
     * @param subject String the email subject line
     * @param senderFirstName String the first name of the email sender
     * @param senderLastName String the last name of the email sender
     * @param senderEmail String the email address of the sender
     * @param recipientFirstName String the first name of the email recipient
     * @param recipientLastName String the last name of the email recipient
     * @param recipientEmail String the email address of the recipient (may contain multiple addresses separated by semicolons)
     * @param providerFirstName String the first name of the associated healthcare provider
     * @param providerLastName String the last name of the associated healthcare provider
     * @param isEncrypted boolean flag indicating whether the email was encrypted
     * @param status EmailStatus the current delivery status of the email
     * @param errorMessage String any error message associated with failed delivery (may be null)
     * @param created Date the timestamp when the email was created/sent
     */
    public EmailStatusResult(Integer logId, String subject, String senderFirstName, String senderLastName, String senderEmail,
                             String recipientFirstName, String recipientLastName, String recipientEmail, String providerFirstName,
                             String providerLastName, boolean isEncrypted, EmailStatus status,
                             String errorMessage, Date created) {
        this.logId = logId;
        this.subject = subject;
        this.senderFirstName = senderFirstName;
        this.senderLastName = senderLastName;
        this.senderEmail = senderEmail;
        this.recipientFirstName = recipientFirstName;
        this.recipientLastName = recipientLastName;
        this.providerFirstName = providerFirstName;
        this.providerLastName = providerLastName;
        this.recipientEmail = recipientEmail;
        this.isEncrypted = isEncrypted;
        this.status = status;
        this.errorMessage = errorMessage;
        this.created = created;
    }

    /**
     * Gets the unique identifier for the email log entry.
     *
     * @return Integer the log ID
     */
    public Integer getLogId() {
        return logId;
    }

    /**
     * Sets the unique identifier for the email log entry.
     *
     * @param logId Integer the log ID to set
     */
    public void setLogId(Integer logId) {
        this.logId = logId;
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
     * @param subject String the email subject to set
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Gets the sender's first name.
     *
     * @return String the sender's first name
     */
    public String getSenderFirstName() {
        return senderFirstName;
    }

    /**
     * Sets the sender's first name.
     *
     * @param senderFirstName String the sender's first name to set
     */
    public void setSenderFirstName(String senderFirstName) {
        this.senderFirstName = senderFirstName;
    }

    /**
     * Gets the sender's last name.
     *
     * @return String the sender's last name
     */
    public String getSenderLastName() {
        return senderLastName;
    }

    /**
     * Sets the sender's last name.
     *
     * @param senderLastName String the sender's last name to set
     */
    public void setSenderLastName(String senderLastName) {
        this.senderLastName = senderLastName;
    }

    /**
     * Gets the sender's full name formatted as "FirstName LastName" in camel case.
     *
     * @return String the formatted sender's full name
     */
    public String getSenderFullName() {
        return formatFullName(senderFirstName, senderLastName);
    }

    /**
     * Sets both the sender's first and last names.
     *
     * @param senderFirstName String the sender's first name to set
     * @param senderLastName String the sender's last name to set
     */
    public void setSenderFullName(String senderFirstName, String senderLastName) {
        this.senderFirstName = senderFirstName;
        this.senderLastName = senderLastName;
    }

    /**
     * Gets the sender's email address.
     *
     * @return String the sender's email address
     */
    public String getSenderEmail() {
        return senderEmail;
    }

    /**
     * Sets the sender's email address.
     *
     * @param senderEmail String the sender's email address to set
     */
    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    /**
     * Gets the recipient's first name.
     *
     * @return String the recipient's first name
     */
    public String getRecipientFirstName() {
        return recipientFirstName;
    }

    /**
     * Sets the recipient's first name.
     *
     * @param recipientFirstName String the recipient's first name to set
     */
    public void setRecipientFirstName(String recipientFirstName) {
        this.recipientFirstName = recipientFirstName;
    }

    /**
     * Gets the recipient's last name.
     *
     * @return String the recipient's last name
     */
    public String getRecipientLastName() {
        return recipientLastName;
    }

    /**
     * Sets the recipient's last name.
     *
     * @param recipientLastName String the recipient's last name to set
     */
    public void setRecipientLastName(String recipientLastName) {
        this.recipientLastName = recipientLastName;
    }

    /**
     * Gets the recipient's full name formatted as "FirstName LastName" in camel case.
     *
     * @return String the formatted recipient's full name
     */
    public String getRecipientFullName() {
        return formatFullName(recipientFirstName, recipientLastName);
    }

    /**
     * Sets both the recipient's first and last names.
     *
     * @param recipientFirstName String the recipient's first name to set
     * @param recipientLastName String the recipient's last name to set
     */
    public void setRecipientFullName(String recipientFirstName, String recipientLastName) {
        this.recipientFirstName = recipientFirstName;
        this.recipientLastName = recipientLastName;
    }

    /**
     * Gets the associated healthcare provider's first name.
     *
     * @return String the provider's first name
     */
    public String getProviderFirstName() {
        return providerFirstName;
    }

    /**
     * Sets the associated healthcare provider's first name.
     *
     * @param providerFirstName String the provider's first name to set
     */
    public void setProviderFirstName(String providerFirstName) {
        this.providerFirstName = providerFirstName;
    }

    /**
     * Gets the associated healthcare provider's last name.
     *
     * @return String the provider's last name
     */
    public String getProviderLastName() {
        return providerLastName;
    }

    /**
     * Sets the associated healthcare provider's last name.
     *
     * @param providerLastName String the provider's last name to set
     */
    public void setProviderLastName(String providerLastName) {
        this.providerLastName = providerLastName;
    }

    /**
     * Gets the provider's full name formatted as "LastName, FirstName" in camel case.
     *
     * @return String the formatted provider's full name
     */
    public String getProviderFullName() {
        return toCamelCase(providerLastName) + ", " + toCamelCase(providerFirstName);
    }

    /**
     * Sets both the provider's first and last names.
     *
     * @param providerFirstName String the provider's first name to set
     * @param providerLastName String the provider's last name to set
     */
    public void setProviderFullName(String providerFirstName, String providerLastName) {
        this.providerFirstName = providerFirstName;
        this.providerLastName = providerLastName;
    }

    /**
     * Gets the recipient's email address.
     *
     * @return String the recipient's email address (may contain multiple addresses separated by commas)
     */
    public String getRecipientEmail() {
        return recipientEmail;
    }

    /**
     * Sets the recipient's email address, converting semicolon separators to comma-space format.
     *
     * <p>This method automatically converts semicolon-separated email addresses to
     * comma-separated format for display purposes.</p>
     *
     * <p><strong>Note:</strong> This normalization behavior differs from the constructor,
     * which assigns the recipientEmail value directly without conversion. When using the
     * constructor, ensure email addresses are pre-formatted or call this setter afterward
     * if normalization is required.</p>
     *
     * @param recipientEmail String the recipient's email address to set (may contain semicolon-separated addresses)
     * @throws NullPointerException if recipientEmail is null
     */
    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail.replace(";", ", ");
    }

    /**
     * Gets the encryption status of the email.
     *
     * @return boolean true if the email was encrypted, false otherwise
     */
    public boolean getIsEncrypted() {
        return isEncrypted;
    }

    /**
     * Sets the encryption status of the email.
     *
     * @param isEncrypted boolean true if the email is encrypted, false otherwise
     */
    public void setIsEncrypted(boolean isEncrypted) {
        this.isEncrypted = isEncrypted;
    }

    /** @return the consent state captured for the displayed send attempt */
    public EmailConsentStatus getConsentStatus() {
        return consentStatus;
    }

    /**
     * Copies the persisted consent audit fields into this display result. The mutable consent date
     * is defensively copied and absent fields remain {@code null} or empty.
     *
     * @param emailLog the persisted email log containing the consent snapshot
     */
    public void applyConsentSnapshot(EmailLog emailLog) {
        this.consentStatus = emailLog.getConsentStatus();
        this.consentId = emailLog.getConsentId();
        this.consentLastUpdateDate = copyDate(emailLog.getConsentLastUpdateDate());
        this.consentOverride = emailLog.getConsentOverride();
        this.consentOverrideReason = emailLog.getConsentOverrideReason();
    }

    /** @return the source consent-record identifier, or {@code null} */
    public Integer getConsentId() {
        return consentId;
    }

    /** @return a defensive copy of the source consent record's update time */
    public Date getConsentLastUpdateDate() {
        return copyDate(consentLastUpdateDate);
    }

    /** @return whether a documented unknown-consent override permitted the send */
    public boolean getConsentOverride() {
        return consentOverride;
    }

    /** @return the recorded override reason, or {@code null} */
    public String getConsentOverrideReason() {
        return consentOverrideReason;
    }

    /** @return the resource-bundle key for the consent status, or an empty string */
    public String getConsentMessageKey() {
        EmailConsentStatus displayStatus = getConsentStatus();
        return displayStatus != null ? displayStatus.getMessageKey() : "";
    }

    /**
     * Formats the snapshotted consent update date as {@code yyyy-MM-dd}, or returns an empty string
     * when the snapshot has no update date.
     *
     * @return the formatted consent update date
     */
    public String getConsentLastUpdateDisplay() {
        Date lastUpdate = getConsentLastUpdateDate();
        if (lastUpdate == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(lastUpdate);
    }

    private static Date copyDate(Date date) {
        return date != null ? new Date(date.getTime()) : null;
    }

    /**
     * Gets the current delivery status of the email.
     *
     * @return EmailStatus the email delivery status
     */
    public EmailStatus getStatus() {
        return status;
    }

    /**
     * Sets the delivery status of the email.
     *
     * @param status EmailStatus the email delivery status to set
     */
    public void setStatus(EmailStatus status) {
        this.status = status;
    }

    /**
     * Gets any error message associated with failed email delivery.
     *
     * @return String the error message, or null if no error occurred
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message for failed email delivery.
     *
     * @param errorMessage String the error message to set
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Gets the timestamp when the email was created/sent.
     *
     * @return Date the creation timestamp
     */
    public Date getCreated() {
        return created;
    }

    /**
     * Sets the timestamp when the email was created/sent.
     *
     * @param created Date the creation timestamp to set
     */
    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * Indicates whether the current status may be manually resolved. Fresh PENDING records are
     * intentionally not actionable while their transport request may still be running.
     */
    public boolean isResolvable() {
        return resolvable;
    }

    public void setResolvable(boolean resolvable) {
        this.resolvable = resolvable;
    }

    /**
     * Gets the creation date formatted as "yyyy-MM-dd".
     *
     * @return String the formatted creation date
     * @throws NullPointerException if the created timestamp is null
     */
    public String getCreatedDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(created);
    }

    /**
     * Gets the creation time formatted as "HH:mm:ss".
     *
     * @return String the formatted creation time
     * @throws NullPointerException if the created timestamp is null
     */
    public String getCreatedTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        return timeFormat.format(created);
    }

    /**
     * Gets the creation timestamp formatted as an ISO 8601 local date-time string.
     *
     * @return String the ISO 8601 formatted creation timestamp
     * @throws NullPointerException if the created timestamp is null
     */
    public String getCreatedStringDate() {
        LocalDateTime createdLocalDateTime = created.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return createdLocalDateTime.format(formatter);
    }

    /**
     * Formats first and last name parts as a display name.
     *
     * @param firstName String the first name part to format
     * @param lastName String the last name part to format
     * @return String the formatted full name
     */
    private String formatFullName(String firstName, String lastName) {
        String formattedFirstName = toCamelCase(firstName);
        String formattedLastName = toCamelCase(lastName);
        if (formattedFirstName.isEmpty()) {
            return formattedLastName;
        }
        if (formattedLastName.isEmpty()) {
            return formattedFirstName;
        }
        return formattedFirstName + " " + formattedLastName;
    }

    private String toCamelCase(String inputString) {
        if (inputString == null || inputString.isEmpty()) {
            return "";
        }
        if (inputString.startsWith("(") && inputString.endsWith(")")) {
            return inputString;
        }
        int aliasStart = inputString.indexOf(" (");
        if (aliasStart > 0 && inputString.endsWith(")")) {
            return toCamelCaseName(inputString.substring(0, aliasStart)) + inputString.substring(aliasStart);
        }
        return toCamelCaseName(inputString);
    }

    private String toCamelCaseName(String inputString) {
        return Character.toUpperCase(inputString.charAt(0)) + inputString.substring(1).toLowerCase();
    }

    /**
     * Compares this EmailStatusResult with another based on creation timestamp.
     *
     * <p>This method enables chronological sorting of email status records,
     * with earlier emails comparing as less than later emails.</p>
     *
     * @param other EmailStatusResult the EmailStatusResult to compare with
     * @return int a negative integer, zero, or a positive integer as this email
     *         was created before, at the same time as, or after the specified email
     * @throws NullPointerException if other is null, or if either this.created or other.created is null
     */
    @Override
    public int compareTo(EmailStatusResult other) {
        // Compare based on the timestamp
        return this.created.compareTo(other.created);
    }
}
