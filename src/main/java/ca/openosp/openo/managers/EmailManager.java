//CHECKSTYLE:OFF
package ca.openosp.openo.managers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteLink;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.EmailConfigDaoImpl;
import ca.openosp.openo.commn.dao.EmailLogDaoImpl;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailConfig;
import ca.openosp.openo.commn.model.EmailLog;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.EmailLog.ChartDisplayOption;
import ca.openosp.openo.commn.model.EmailLog.EmailStatus;
import ca.openosp.openo.commn.model.SecRole;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.documentManager.ConvertToEdoc;
import ca.openosp.openo.documentManager.DocumentAttachmentManager;
import ca.openosp.openo.email.core.EmailData;
import ca.openosp.openo.email.core.EmailSender;
import ca.openosp.openo.email.core.EmailStatusResult;
import ca.openosp.openo.email.util.EmailNoteUtil;
import ca.openosp.openo.utility.EmailSendingException;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PDFEncryptionUtil;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.encounter.data.EctProgram;
import ca.openosp.openo.util.StringUtils;

/**
 * Email management service for the OpenO EMR healthcare system.
 *
 * This manager provides comprehensive email functionality for healthcare providers,
 * including secure email transmission, encryption support for PHI (Protected Health Information),
 * attachment handling, and integration with patient charts through case management notes.
 *
 * Key Features:
 * - Secure email sending with role-based access control (_email privilege)
 * - Optional PDF encryption for messages and attachments containing PHI
 * - Email status tracking and audit logging
 * - Integration with patient demographic records and provider profiles
 * - Automatic chart note creation with configurable display options
 * - Email outbox management with status monitoring
 *
 * Security Considerations:
 * - All operations require _email security privilege (READ or WRITE)
 * - PHI content can be encrypted using password-protected PDFs
 * - Email activity is logged for audit compliance
 * - User inputs are sanitized using OWASP encoding
 *
 * @see EmailLog
 * @see EmailConfig
 * @see EmailData
 * @see EmailSender
 * @see CaseManagementNote
 * @since 2026-01-24
 */
@Service
public class EmailManager {
    private final Logger logger = MiscUtils.getLogger();

    @Autowired
    private EmailConfigDaoImpl emailConfigDao;
    @Autowired
    private EmailLogDaoImpl emailLogDao;
    @Autowired
    private CaseManagementManager caseManagementManager;
    @Autowired
    private DemographicManager demographicManager;
    @Autowired
    private DocumentAttachmentManager documentAttachmentManager;
    @Autowired
    private ProgramManager programManager;
    @Autowired
    private ProviderManager2 providerManager;
    @Autowired
    private SecurityInfoManager securityInfoManager;

    /**
     * Sends an email with optional encryption and creates a corresponding email log entry.
     *
     * This method orchestrates the complete email sending workflow including field sanitization,
     * outbox preparation, optional encryption, transmission, and status tracking. If configured
     * to display in the patient chart, it also creates a case management note documenting the
     * email communication.
     *
     * The method performs the following steps:
     * 1. Validates user has _email WRITE privilege
     * 2. Sanitizes email data fields
     * 3. Creates email log entry in FAILED status
     * 4. Encrypts message and/or attachments if requested
     * 5. Sends email via configured email server
     * 6. Updates log status to SUCCESS or FAILED
     * 7. Creates chart note if configured for WITH_FULL_NOTE display
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param emailData EmailData containing email subject, body, recipients, attachments, and configuration options
     * @return EmailLog the persisted email log entry with final status and metadata
     * @throws RuntimeException if user lacks _email WRITE privilege
     */
    public EmailLog sendEmail(LoggedInInfo loggedInInfo, EmailData emailData) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        sanitizeEmailFields(emailData);
        EmailLog emailLog = prepareEmailForOutbox(loggedInInfo, emailData);
        try {
            if (emailData.getIsEncrypted()) {
                encryptEmail(emailData);
            }
            EmailSender emailSender = new EmailSender(loggedInInfo, emailLog.getEmailConfig(), emailData);
            emailSender.send();
            updateEmailStatus(loggedInInfo, emailLog, EmailStatus.SUCCESS, "");
            if (emailLog.getChartDisplayOption().equals(ChartDisplayOption.WITH_FULL_NOTE)) {
                addEmailNote(loggedInInfo, emailLog);
            }
        } catch (EmailSendingException e) {
            updateEmailStatus(loggedInInfo, emailLog, EmailStatus.FAILED, e.getMessage());
            logger.error("Failed to send email", e);
        }
        return emailLog;
    }

    /**
     * Prepares an email for sending by creating and persisting an email log entry in the outbox.
     *
     * This method creates a comprehensive email log record that captures all email metadata,
     * configuration, and content. The email log is initially created with FAILED status and
     * a default error message, which is updated to SUCCESS after successful transmission.
     *
     * The method:
     * 1. Retrieves active email configuration for the sender
     * 2. Loads demographic and provider information
     * 3. Creates EmailLog entity with all email data
     * 4. Persists the email log to database
     * 5. Creates audit log entry for compliance tracking
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param emailData EmailData containing email content, recipients, and configuration
     * @return EmailLog the persisted email log entry ready for transmission
     * @throws RuntimeException if user lacks _email WRITE privilege
     */
    public EmailLog prepareEmailForOutbox(LoggedInInfo loggedInInfo, EmailData emailData) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        EmailConfig emailConfig = emailConfigDao.findActiveEmailConfig(emailData.getSender());
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, emailData.getDemographicNo());
        Provider provider = providerManager.getProvider(loggedInInfo, emailData.getProviderNo());

        EmailLog emailLog = new EmailLog(emailConfig, emailData.getSender(), emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailStatus.FAILED);
        setEmailAttachments(emailLog, emailData.getAttachments());
        emailLog.setEncryptedMessage(emailData.getEncryptedMessage());
        emailLog.setPassword(emailData.getPassword());
        emailLog.setPasswordClue(emailData.getPasswordClue());
        emailLog.setIsEncrypted(emailData.getIsEncrypted());
        emailLog.setIsAttachmentEncrypted(emailData.getIsAttachmentEncrypted());
        emailLog.setChartDisplayOption(emailData.getChartDisplayOption());
        emailLog.setInternalComment(emailData.getInternalComment());
        emailLog.setTransactionType(emailData.getTransactionType());
        emailLog.setErrorMessage("Email was not sent successfully for unknown reasons.");
        emailLog.setAdditionalParams(emailData.getAdditionalParams());
        emailLog.setDemographic(demographic);
        emailLog.setProvider(provider);
        emailLogDao.persist(emailLog);

        LogAction.addLog(loggedInInfo, "EmailManager.prepareEmailForOutbox", "Email", "emailLogId=" + emailLog.getId(), String.valueOf(emailLog.getDemographic().getDemographicNo()), "");

        return emailLog;
    }

    /**
     * Updates the status of an email log entry by ID.
     *
     * This is a convenience method that loads the email log by ID and delegates to the
     * main status update method. It is useful when only the email log ID is available.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param emailLogId Integer the unique identifier of the email log entry to update
     * @param emailStatus EmailStatus the new status to set (SUCCESS, FAILED, RESOLVED, etc.)
     * @param errorMessage String the error message to store, empty string if no error
     * @return EmailLog the updated email log entry with new status and timestamp
     * @throws RuntimeException if user lacks _email WRITE privilege
     */
    public EmailLog updateEmailStatus(LoggedInInfo loggedInInfo, Integer emailLogId, EmailStatus emailStatus, String errorMessage) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        EmailLog emailLog = emailLogDao.find(emailLogId);
        return updateEmailStatus(loggedInInfo, emailLog, emailStatus, errorMessage);
    }

    /**
     * Updates the status of an email log entry with new status and error message.
     *
     * This method updates the email log status in both the database and the in-memory object.
     * The timestamp is updated to the current time for status changes, but preserved when
     * resolving an issue (RESOLVED status) to maintain the original send time.
     *
     * Common status values:
     * - SUCCESS: Email sent successfully
     * - FAILED: Email transmission failed
     * - RESOLVED: Issue with email has been resolved by user
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param emailLog EmailLog the email log entry to update
     * @param emailStatus EmailStatus the new status to set
     * @param errorMessage String the error message to store, empty string if no error
     * @return EmailLog the updated email log entry with new status and timestamp
     * @throws RuntimeException if user lacks _email WRITE privilege
     */
    public EmailLog updateEmailStatus(LoggedInInfo loggedInInfo, EmailLog emailLog, EmailStatus emailStatus, String errorMessage) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required security object (_email)");
        }

        Date newTimestamp = (!emailStatus.equals(EmailStatus.RESOLVED)) ? new Date() : emailLog.getTimestamp();

        emailLogDao.updateEmailStatus(emailLog.getId(), emailStatus, errorMessage, newTimestamp);

        // Update object in memory so caller still has the right values
        emailLog.setStatus(emailStatus);
        emailLog.setErrorMessage(errorMessage);
        emailLog.setTimestamp(newTimestamp);

        return emailLog;
    }

    /**
     * Retrieves email status results filtered by date range, demographic, sender, and status.
     *
     * This method provides comprehensive email log querying for reporting and monitoring purposes.
     * All filter parameters are optional (can be null) to allow flexible searching. Results are
     * converted to EmailStatusResult DTOs for UI display and sorted by timestamp.
     *
     * Date parameters are parsed in yyyy-MM-dd format:
     * - dateBeginStr is set to 00:00:00 on the specified date
     * - dateEndStr is set to 23:59:59 on the specified date
     *
     * If date parsing fails, an empty list is returned.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param dateBeginStr String the start date in yyyy-MM-dd format, or null for no start date
     * @param dateEndStr String the end date in yyyy-MM-dd format, or null for no end date
     * @param demographic_no String the patient demographic number to filter by, or null for all patients
     * @param senderEmailAddress String the sender email address to filter by, or null for all senders
     * @param emailStatus String the email status to filter by (SUCCESS, FAILED, etc.), or null for all statuses
     * @return List&lt;EmailStatusResult&gt; list of email status results matching the filter criteria, sorted by timestamp
     * @throws RuntimeException if user lacks _email READ privilege
     */
    public List<EmailStatusResult> getEmailStatusByDateDemographicSenderStatus(LoggedInInfo loggedInInfo, String dateBeginStr, String dateEndStr, String demographic_no, String senderEmailAddress, String emailStatus) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        Date dateBegin = parseDate(dateBeginStr, "yyyy-MM-dd", "00:00:00");
        Date dateEnd = parseDate(dateEndStr, "yyyy-MM-dd", "23:59:59");
        if (dateBegin == null || dateEnd == null) {
            return Collections.emptyList();
        }

        List<EmailLog> resultList = emailLogDao.getEmailStatusByDateDemographicSenderStatus(dateBegin, dateEnd, demographic_no, senderEmailAddress, emailStatus);
        return retriveEmailStatusResultList(resultList);
    }

    /**
     * Retrieves the email log associated with a case management note.
     *
     * This method enables bidirectional navigation between chart notes and email communications.
     * When an email is configured to display in the patient chart (WITH_FULL_NOTE option),
     * a case management note is created and linked to the email log. This method retrieves
     * the original email log from the note ID.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param noteId Long the unique identifier of the case management note
     * @return EmailLog the email log associated with the note, or null if no email link exists
     * @throws RuntimeException if user lacks _email READ privilege
     */
    public EmailLog getEmailLogByCaseManagementNoteId(LoggedInInfo loggedInInfo, Long noteId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        CaseManagementNoteLink caseManagementNoteLink = caseManagementManager.getLatestLinkByNote(noteId);
        if (caseManagementNoteLink == null || !caseManagementNoteLink.getTableName().equals(CaseManagementNoteLink.EMAIL)) {
            return null;
        }
        Long emailLogId = caseManagementNoteLink.getTableId();
        return emailLogDao.find(emailLogId.intValue());
    }

    /**
     * Creates a case management note in the patient chart documenting an email communication.
     *
     * This method is called when an email is configured with ChartDisplayOption.WITH_FULL_NOTE.
     * It creates a formatted chart note containing email metadata (subject, recipients, timestamp)
     * and links it to the email log for bidirectional navigation.
     *
     * The note is automatically:
     * - Signed by the current provider
     * - Associated with the current program
     * - Linked to the email log via CaseManagementNoteLink
     * - Created with doctor role (or program-specific role if available)
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param emailLog EmailLog the email log to document in the chart
     * @throws RuntimeException if user lacks _email READ privilege
     */
    public void addEmailNote(LoggedInInfo loggedInInfo, EmailLog emailLog) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        EmailNoteUtil emailNoteUtil = new EmailNoteUtil(loggedInInfo, emailLog);
        String emailNote = emailNoteUtil.createNote();

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String programId = new EctProgram(loggedInInfo.getSession()).getProgram(providerNo);
        Date creationDate = new Date();

        ProgramProvider programProvider = programManager.getProgramProvider(providerNo, programId);
        SecRole doctorRole = caseManagementManager.getSecRoleByRoleName("doctor");
        String role = programProvider != null ? String.valueOf(programProvider.getRoleId()) : String.valueOf(doctorRole.getId());

        CaseManagementNote caseManagementNote = new CaseManagementNote();
        caseManagementNote.setUpdate_date(creationDate);
        caseManagementNote.setObservation_date(creationDate);
        caseManagementNote.setDemographic_no(String.valueOf(emailLog.getDemographic().getDemographicNo()));
        caseManagementNote.setProviderNo(providerNo);
        caseManagementNote.setNote(emailNote);
        caseManagementNote.setSigned(true);
        caseManagementNote.setSigning_provider_no(providerNo);
        caseManagementNote.setProgram_no(programId);
        caseManagementNote.setReporter_caisi_role(role);
        caseManagementNote.setReporter_program_team("0");
        caseManagementNote.setHistory(emailNote);
        Long noteId = caseManagementManager.saveNoteSimpleReturnID(caseManagementNote);

        CaseManagementNoteLink caseManagementNoteLink = new CaseManagementNoteLink(CaseManagementNoteLink.EMAIL, Long.valueOf(emailLog.getId()), noteId);
        caseManagementManager.saveNoteLink(caseManagementNoteLink);
    }

    /**
     * Creates and associates email attachments with an email log entry.
     *
     * This helper method creates new EmailAttachment instances linked to the email log,
     * copying metadata from the source attachments. Each attachment includes file name,
     * file path, document type, and optional document ID for referenced documents.
     *
     * @param emailLog EmailLog the email log to attach files to
     * @param emailAttachments List&lt;EmailAttachment&gt; the source attachments to copy
     */
    private void setEmailAttachments(EmailLog emailLog, List<EmailAttachment> emailAttachments) {
        List<EmailAttachment> emailAttachmentList = new ArrayList<>();
        for (EmailAttachment emailAttachment : emailAttachments) {
            emailAttachmentList.add(new EmailAttachment(emailLog, emailAttachment.getFileName(), emailAttachment.getFilePath(), emailAttachment.getDocumentType(), emailAttachment.getDocumentId()));
        }
        emailLog.setEmailAttachments(emailAttachmentList);
    }

    /**
     * Sanitizes and normalizes email data fields based on encryption settings.
     *
     * This method ensures encryption-related fields are consistent with the selected
     * encryption options. It clears encryption fields when encryption is not needed,
     * and clears the internal comment when no chart note will be created.
     *
     * Sanitization rules:
     * - If no encrypted message and no attachments: disable encryption entirely
     * - If no encrypted message and unencrypted attachments: disable encryption
     * - If no attachments: disable attachment encryption
     * - If encryption disabled: clear all encryption-related fields
     * - If no chart note: clear internal comment
     *
     * @param emailData EmailData the email data to sanitize
     */
    private void sanitizeEmailFields(EmailData emailData) {
        if (StringUtils.isNullOrEmpty(emailData.getEncryptedMessage()) && emailData.getAttachments().isEmpty()) {
            emailData.setIsEncrypted(false);
            emailData.setIsAttachmentEncrypted(false);
            emailData.setPassword("");
            emailData.setPasswordClue("");
        } else if (StringUtils.isNullOrEmpty(emailData.getEncryptedMessage()) && emailData.getAttachments().size() > 0 && !emailData.getIsAttachmentEncrypted()) {
            emailData.setIsEncrypted(false);
            emailData.setIsAttachmentEncrypted(false);
            emailData.setPassword("");
            emailData.setPasswordClue("");
        } else if (emailData.getAttachments().isEmpty()) {
            emailData.setIsAttachmentEncrypted(false);
        } else if (!emailData.getIsEncrypted()) {
            emailData.setEncryptedMessage("");
            emailData.setIsAttachmentEncrypted(false);
            emailData.setPassword("");
            emailData.setPasswordClue("");
        }

        if (emailData.getChartDisplayOption().equals(ChartDisplayOption.WITHOUT_NOTE)) {
            emailData.setInternalComment("");
        }
    }

    /**
     * Encrypts the email message and/or attachments as password-protected PDFs.
     *
     * This method handles encryption of PHI content for secure transmission. It converts
     * the encrypted message to a PDF attachment and encrypts selected attachments, then
     * appends the password clue to the email body.
     *
     * Encryption workflow:
     * 1. Convert encrypted message text to PDF attachment (if present)
     * 2. Collect attachments to encrypt based on isAttachmentEncrypted flag
     * 3. Encrypt all selected attachments with the provided password
     * 4. Update email attachments list with encrypted files
     * 5. Append password clue to email body
     *
     * @param emailData EmailData the email data containing content to encrypt
     * @throws EmailSendingException if PDF encryption fails
     */
    private void encryptEmail(EmailData emailData) throws EmailSendingException {
        // Encrypt message and attachment
        List<EmailAttachment> encryptableAttachments = new ArrayList<>();
        if (!StringUtils.isNullOrEmpty(emailData.getEncryptedMessage())) {
            encryptableAttachments.add(createMessageAttachment(emailData));
        }
        if (emailData.getIsAttachmentEncrypted() && !emailData.getAttachments().isEmpty()) {
            encryptableAttachments.addAll(emailData.getAttachments());
        }
        encryptAttachments(encryptableAttachments, emailData.getPassword());

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        emailAttachments.addAll(encryptableAttachments);
        if (!emailData.getIsAttachmentEncrypted() && !emailData.getAttachments().isEmpty()) {
            emailAttachments.addAll(emailData.getAttachments());
        }
        emailData.setAttachments(emailAttachments);

        //append password clue
        emailData.setBody(emailData.getBody() + "\n\n*****\n" + emailData.getPasswordClue().trim() + "\n*****\n");
    }

    /**
     * Creates a PDF attachment from the encrypted message text.
     *
     * This method converts plain text message content to an HTML-formatted PDF for encryption.
     * The text is OWASP-encoded for security and newlines are converted to HTML breaks.
     *
     * @param emailData EmailData containing the encrypted message text
     * @return EmailAttachment a new attachment with the message PDF, or null if message is empty
     */
    private EmailAttachment createMessageAttachment(EmailData emailData) {
        if (StringUtils.isNullOrEmpty(emailData.getEncryptedMessage())) {
            return null;
        }
        String htmlSafeMessage = Encode.forHtmlContent(emailData.getEncryptedMessage()).replace("\n", "<br>");
        emailData.setEncryptedMessage(htmlSafeMessage);
        Path encryptedMessagePDF = ConvertToEdoc.saveAsTempPDF(emailData);
        EmailAttachment emailAttachment = new EmailAttachment("message.pdf", encryptedMessagePDF.toString(), DocumentType.DOC, -1);
        return emailAttachment;
    }

    /**
     * Encrypts a list of PDF attachments with password protection.
     *
     * This method iterates through attachments and encrypts each PDF file using the
     * provided password. The encrypted file replaces the original file path in the
     * attachment metadata.
     *
     * @param encryptableAttachments List&lt;EmailAttachment&gt; the attachments to encrypt
     * @param password String the password to protect the PDFs with
     * @throws EmailSendingException if PDF encryption fails for any attachment
     */
    private void encryptAttachments(List<EmailAttachment> encryptableAttachments, String password) throws EmailSendingException {
        for (EmailAttachment attachment : encryptableAttachments) {
            try {
                Path attachmentPDFPath = Paths.get(attachment.getFilePath());
                attachmentPDFPath = PDFEncryptionUtil.encryptPDF(attachmentPDFPath, password);
                attachment.setFilePath(attachmentPDFPath.toString());
            } catch (IOException e) {
                logger.error("Failed to create encrypted email attachments", e);
                throw new EmailSendingException("Failed to create encrypted email attachments", e);
            }
        }
    }

    /**
     * Parses a date string with optional time component into a Date object.
     *
     * This utility method handles date parsing with configurable format and time.
     * If time is not provided or empty, the date is set to start of day (00:00:00).
     *
     * @param date String the date string to parse
     * @param format String the date format pattern (e.g., "yyyy-MM-dd")
     * @param time String the time string in HH:mm:ss format, or null/empty for start of day
     * @return Date the parsed date with time in system default timezone, or null if parsing fails
     */
    private Date parseDate(String date, String format, String time) {
        if (date == null) {
            return null;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            LocalDate localDate = LocalDate.parse(date, formatter);
            if (time == null || time.isEmpty()) {
                return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            }
            LocalTime localTime = LocalTime.parse(time);
            LocalDateTime localDateTime = localDate.atTime(localTime);
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            logger.error("UNPARSEABLE DATE " + date);
            return null;
        }
    }

    /**
     * Converts a list of EmailLog arrays into a list of EmailStatusResult DTOs.
     * This method facilitates easy transfer of data to the UI layer.
     *
     * @param resultList The list of EmailLog arrays containing email log data, demographic name, and provider name.
     * @return List of EmailStatusResult DTOs representing email status information.
     */
    private List<EmailStatusResult> retriveEmailStatusResultList(List<EmailLog> resultList) {
        List<EmailStatusResult> emailStatusResults = new ArrayList<>();
        for (EmailLog result : resultList) {
            EmailConfig emailConfig = result.getEmailConfig();
            Demographic demographic = result.getDemographic();
            Provider provider = result.getProvider();
            EmailStatusResult emailStatusResult = new EmailStatusResult(result.getId(), result.getSubject(), emailConfig.getSenderFirstName(),
                    emailConfig.getSenderLastName(), result.getFromEmail(), demographic.getFirstName(),
                    demographic.getLastName(), String.join(", ", result.getToEmail()), provider.getFirstName(), provider.getLastName(),
                    result.getIsEncrypted(), result.getPassword(), result.getStatus(), result.getErrorMessage(), result.getTimestamp());
            emailStatusResults.add(emailStatusResult);
        }
        Collections.sort(emailStatusResults);
        return emailStatusResults;
    }
}
