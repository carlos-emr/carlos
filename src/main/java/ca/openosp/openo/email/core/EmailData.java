//CHECKSTYLE:OFF
package ca.openosp.openo.email.core;

import java.util.Collections;
import java.util.List;

import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailLog.ChartDisplayOption;
import ca.openosp.openo.commn.model.EmailLog.TransactionType;

import ca.openosp.openo.util.StringUtils;

/**
 * Data Transfer Object (DTO) for email composition and transmission in the OpenO EMR system.
 * Encapsulates all email-related data including sender, recipients, content, encryption settings,
 * and healthcare-specific metadata such as patient demographics and chart display options.
 * 
 * <p>This class serves as the primary data container for email operations across the EMR system,
 * supporting both direct email communication and integration with various healthcare workflows
 * including eForm submissions, consultation requests, and tickler notifications. It handles
 * both encrypted and unencrypted email transmission with support for file attachments from
 * multiple document sources (eForms, lab results, clinical documents, HRM documents).</p>
 * 
 * <p>Security and Privacy: This class supports PHI (Protected Health Information) protection
 * through optional email encryption with password protection and encrypted attachments. All
 * email transactions are logged for audit trail purposes and can be linked to specific patient
 * charts and healthcare providers.</p>
 * 
 * <p>Typical usage:</p>
 * <pre>
 * EmailData emailData = new EmailData();
 * emailData.setSender("provider@clinic.ca");
 * emailData.setRecipients(new String[]{"patient@example.com"});
 * emailData.setSubject("Lab Results Available");
 * emailData.setBody("Your recent lab results are attached.");
 * emailData.setDemographicNo(12345);
 * emailData.setProviderNo("999998");
 * emailData.setChartDisplayOption(ChartDisplayOption.WITH_FULL_NOTE);
 * emailData.setTransactionType(TransactionType.DIRECT);
 * </pre>
 * 
 * @see ca.openosp.openo.commn.model.EmailLog
 * @see ca.openosp.openo.commn.model.EmailAttachment
 * @see ca.openosp.openo.managers.EmailManager
 * @see ca.openosp.openo.email.core.EmailSender
 * @since 2026-01-14
 */
public class EmailData {
    private String sender;
    private String[] recipients;
    private String subject;
    private String body;
    private String encryptedMessage;
    private String password;
    private String passwordClue;
    private boolean isEncrypted;
    private boolean isAttachmentEncrypted;
    private ChartDisplayOption chartDisplayOption;
    private String internalComment;
    private TransactionType transactionType;
    private Integer demographicNo;
    private String providerNo;
    private String additionalParams;
    private List<EmailAttachment> attachments;

    /**
     * Default constructor for creating an empty EmailData instance.
     * All fields are initialized to their default values (null for objects, false for booleans).
     * Use setter methods to populate the email data fields.
     */
    public EmailData() {
    }

    /**
     * Gets the email sender address.
     * 
     * @return String the sender's email address, or empty string if not set
     */
    public String getSender() {
        return sender;
    }

    /**
     * Sets the email sender address.
     * 
     * @param sender String the sender's email address; null values are converted to empty string
     */
    public void setSender(String sender) {
        this.sender = sender != null ? sender : "";
    }

    /**
     * Gets the array of email recipient addresses.
     * 
     * @return String[] array of recipient email addresses, or empty array if not set
     */
    public String[] getRecipients() {
        return recipients;
    }

    /**
     * Sets the array of email recipient addresses.
     * 
     * @param recipients String[] array of recipient email addresses; null values are converted to empty array
     */
    public void setRecipients(String[] recipients) {
        this.recipients = recipients != null ? recipients : new String[0];
    }

    /**
     * Gets the email subject line.
     * 
     * @return String the email subject, or empty string if not set
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the email subject line.
     * 
     * @param subject String the email subject; null values are converted to empty string
     */
    public void setSubject(String subject) {
        this.subject = subject != null ? subject : "";
    }

    /**
     * Gets the email body content.
     * 
     * @return String the email body text, or empty string if not set
     */
    public String getBody() {
        return body;
    }

    /**
     * Sets the email body content.
     * 
     * @param body String the email body text; null values are converted to empty string
     */
    public void setBody(String body) {
        this.body = body != null ? body : "";
    }

    /**
     * Gets the encrypted version of the email message.
     * Used when email encryption is enabled to store the encrypted content.
     * 
     * @return String the encrypted email message, or empty string if not set
     */
    public String getEncryptedMessage() {
        return encryptedMessage;
    }

    /**
     * Sets the encrypted version of the email message.
     * This field stores the encrypted content when email encryption is enabled.
     * 
     * @param encryptedMessage String the encrypted email message; null values are converted to empty string
     */
    public void setEncryptedMessage(String encryptedMessage) {
        this.encryptedMessage = encryptedMessage != null ? encryptedMessage : "";
    }

    /**
     * Gets the password used to encrypt the email.
     * 
     * @return String the encryption password, or empty string if not set
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password used to encrypt the email.
     * This password is required by the recipient to decrypt and read the encrypted email content.
     * 
     * @param password String the encryption password; null values are converted to empty string
     */
    public void setPassword(String password) {
        this.password = password != null ? password : "";
    }

    /**
     * Gets the password hint/clue for the encrypted email.
     * 
     * @return String the password clue, or empty string if not set
     */
    public String getPasswordClue() {
        return passwordClue;
    }

    /**
     * Sets the password hint/clue for the encrypted email.
     * This clue is sent to the recipient to help them remember or derive the password
     * needed to decrypt the email content.
     * 
     * @param passwordClue String the password hint/clue; null values are converted to empty string
     */
    public void setPasswordClue(String passwordClue) {
        this.passwordClue = passwordClue != null ? passwordClue : "";
    }

    /**
     * Checks whether the email content is encrypted.
     * 
     * @return boolean true if the email is encrypted, false otherwise
     */
    public boolean getIsEncrypted() {
        return isEncrypted;
    }

    /**
     * Sets whether the email content should be encrypted.
     * 
     * @param isEncrypted boolean true to encrypt the email, false otherwise
     */
    public void setIsEncrypted(boolean isEncrypted) {
        this.isEncrypted = isEncrypted;
    }

    /**
     * Sets whether the email content should be encrypted from a string value.
     * Convenience method for parsing request parameters.
     * 
     * @param isEncrypted String "true" to encrypt the email, any other value (including null) for false
     */
    public void setIsEncrypted(String isEncrypted) {
        if (isEncrypted == null) {
            isEncrypted = "false";
        }
        this.isEncrypted = "true".equals(isEncrypted);
    }

    /**
     * Checks whether the email attachments are encrypted.
     * 
     * @return boolean true if attachments are encrypted, false otherwise
     */
    public boolean getIsAttachmentEncrypted() {
        return isAttachmentEncrypted;
    }

    /**
     * Sets whether the email attachments should be encrypted.
     * 
     * @param isAttachmentEncrypted boolean true to encrypt attachments, false otherwise
     */
    public void setIsAttachmentEncrypted(boolean isAttachmentEncrypted) {
        this.isAttachmentEncrypted = isAttachmentEncrypted;
    }

    /**
     * Sets whether the email attachments should be encrypted from a string value.
     * Convenience method for parsing request parameters.
     * 
     * @param isAttachmentEncrypted String "true" to encrypt attachments, any other value (including null) for false
     */
    public void setIsAttachmentEncrypted(String isAttachmentEncrypted) {
        if (isAttachmentEncrypted == null) {
            isAttachmentEncrypted = "false";
        }
        this.isAttachmentEncrypted = "true".equals(isAttachmentEncrypted);
    }

    /**
     * Gets the chart display option that determines how the email is recorded in the patient chart.
     * 
     * @return ChartDisplayOption the chart display setting (WITH_FULL_NOTE or WITHOUT_NOTE)
     */
    public ChartDisplayOption getChartDisplayOption() {
        return chartDisplayOption;
    }

    /**
     * Sets the chart display option from an enum value.
     * 
     * @param chartDisplayOption ChartDisplayOption the chart display setting
     */
    public void setChartDisplayOption(ChartDisplayOption chartDisplayOption) {
        this.chartDisplayOption = chartDisplayOption;
    }

    /**
     * Sets the chart display option from a string value.
     * Convenience method for parsing request parameters.
     * 
     * @param chartDisplayOption String "doNotAddAsNote" to exclude from chart, 
     *                          any other value (including null) defaults to WITH_FULL_NOTE
     */
    public void setChartDisplayOption(String chartDisplayOption) {
        if (chartDisplayOption == null) {
            chartDisplayOption = "addFullNote";
        }
        this.chartDisplayOption = "doNotAddAsNote".equalsIgnoreCase(chartDisplayOption) ? ChartDisplayOption.WITHOUT_NOTE : ChartDisplayOption.WITH_FULL_NOTE;
    }

    /**
     * Gets the internal comment associated with this email transaction.
     * This is typically used for provider notes that are not sent with the email.
     * 
     * @return String the internal comment, or empty string if not set
     */
    public String getInternalComment() {
        return internalComment;
    }

    /**
     * Sets the internal comment for this email transaction.
     * This comment is for internal tracking and is not sent to the email recipient.
     * 
     * @param internalComment String the internal comment; null values are converted to empty string
     */
    public void setInternalComment(String internalComment) {
        this.internalComment = internalComment != null ? internalComment : "";
    }

    /**
     * Gets the transaction type that categorizes the source or purpose of this email.
     * 
     * @return TransactionType the transaction type (EFORM, CONSULTATION, TICKLER, or DIRECT)
     */
    public TransactionType getTransactionType() {
        return transactionType;
    }

    /**
     * Sets the transaction type from an enum value.
     * 
     * @param transactionType TransactionType the transaction type
     */
    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    /**
     * Sets the transaction type from a string value.
     * Convenience method for parsing request parameters.
     * 
     * @param transactionType String one of "EFORM", "CONSULTATION", "TICKLER", or any other value
     *                       (including null) which defaults to DIRECT
     */
    public void setTransactionType(String transactionType) {
        if (transactionType == null) {
            transactionType = "DIRECT";
        }
        switch (transactionType.toUpperCase()) {
            case "EFORM":
                this.transactionType = TransactionType.EFORM;
                break;
            case "CONSULTATION":
                this.transactionType = TransactionType.CONSULTATION;
                break;
            case "TICKLER":
                this.transactionType = TransactionType.TICKLER;
                break;
            default:
                this.transactionType = TransactionType.DIRECT;
                break;
        }
    }

    /**
     * Gets the demographic number (patient ID) associated with this email.
     * 
     * @return Integer the demographic number, or null if not associated with a patient
     */
    public Integer getDemographicNo() {
        return demographicNo;
    }

    /**
     * Sets the demographic number (patient ID) from an Integer value.
     * 
     * @param demographicNo Integer the demographic number
     */
    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    /**
     * Sets the demographic number (patient ID) from a string value.
     * Convenience method for parsing request parameters.
     * 
     * @param demographicNo String the demographic number; null or empty string defaults to -1
     */
    public void setDemographicNo(String demographicNo) {
        this.demographicNo = (StringUtils.isNullOrEmpty(demographicNo)) ? -1 : Integer.parseInt(demographicNo);
    }

    /**
     * Gets the provider number (healthcare provider ID) associated with this email.
     * 
     * @return String the provider number, or "-1" if not associated with a provider
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number (healthcare provider ID) for this email.
     * 
     * @param providerNo String the provider number; null values are converted to "-1"
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo == null ? "-1" : providerNo;
    }

    /**
     * Gets additional parameters for the email transaction.
     * Used to store extra metadata or configuration specific to certain transaction types.
     * 
     * @return String the additional parameters, or empty string if not set
     */
    public String getAdditionalParams() {
        return additionalParams;
    }

    /**
     * Sets additional parameters for the email transaction.
     * 
     * @param additionalParams String the additional parameters; null or empty values are converted to empty string
     */
    public void setAdditionalParams(String additionalParams) {
        this.additionalParams = StringUtils.isNullOrEmpty(additionalParams) ? "" : additionalParams;
    }

    /**
     * Gets the list of email attachments.
     * Attachments can include documents, lab results, eForms, HRM documents, and other clinical files.
     * 
     * @return List&lt;EmailAttachment&gt; the list of attachments, or empty list if none are attached
     */
    public List<EmailAttachment> getAttachments() {
        return attachments;
    }

    /**
     * Sets the list of email attachments.
     * 
     * @param attachments List&lt;EmailAttachment&gt; the list of attachments; null values are converted to empty list
     */
    public void setAttachments(List<EmailAttachment> attachments) {
        this.attachments = attachments != null ? attachments : Collections.emptyList();
    }
}


