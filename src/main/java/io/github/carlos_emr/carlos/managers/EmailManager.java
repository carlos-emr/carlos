package io.github.carlos_emr.carlos.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.email.core.EmailStatusResult;
import io.github.carlos_emr.carlos.email.util.EmailNoteUtil;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.OutboundEmailArchiveException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.PDFEncryptionUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.util.StringUtils;

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
/*
 * Authorization-propagation rule for the outbound send path.
 *
 * A SecurityException must always reach the caller. Converting one into
 * EmailSendingException or OutboundEmailArchiveException routes it to sendEmail's
 * recording catch, which returns an EmailLog describing a routine failed send -- so a
 * revoked privilege becomes indistinguishable from a bad SMTP host. The attempt is still
 * recorded as FAILED; the exception is rethrown on top of that, not instead of it.
 *
 * Three catches in the delivery path are subject to this and each rethrows explicitly:
 * preparation, archive storage, and transport. They were fixed one at a time across
 * successive reviews because the rule lived only in whichever branch was being edited;
 * it is stated here so a fourth site cannot quietly diverge. The suppression catches in
 * recordDeliveryFailure and discardPreparedQuietly are deliberately exempt: there a
 * primary failure is already in flight and is the one that propagates.
 */
@Service
public class EmailManager {
    private static final String ARCHIVE_FAILURE_MESSAGE = "Failed to archive outbound email";
    private static final String SEND_FAILURE_MESSAGE = "Failed to send email";

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
    private final OutboundEmailArchiveService outboundEmailArchiveService;

    /**
     * Constructs an EmailManager with the outbound email archive service used by archive-supported sends.
     *
     * @param outboundEmailArchiveService service that persists outbound email archive artifacts before transport
     */
    @Autowired
    public EmailManager(OutboundEmailArchiveService outboundEmailArchiveService) {
        this.outboundEmailArchiveService = outboundEmailArchiveService;
    }

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
        EmailSender emailSender = null;
        // Scoped to delivery only. Post-send bookkeeping lives outside this block: once the
        // message is on the wire, no later failure may reopen that verdict.
        boolean delivered = false;
        try {
            if (emailData.getIsEncrypted()) {
                encryptEmail(emailData);
            }
            emailSender = new EmailSender(loggedInInfo, emailLog.getEmailConfig(), emailData);
            // Unconditional: there is no longer a "does this transport archive?" branch to get
            // wrong. Any configuration that can send resolves to a transport that produces an
            // archive artifact, and one that resolves to no transport throws out of
            // archiveOutboundEmail rather than reaching an unarchived send.
            archiveOutboundEmail(loggedInInfo, emailSender, emailLog);
            emailSender.sendPrepared();
            delivered = true;
        } catch (EmailSendingException e) {
            recordDeliveryFailure(loggedInInfo, emailLog, e);
        } catch (RuntimeException e) {
            // Transport can still fail unchecked -- a revoked _email privilege between this
            // method's entry check and sendPrepared(), or any unchecked fault inside the
            // sender. Record the attempt so the EmailLog does not keep its placeholder text,
            // then rethrow: an authorization failure is the caller's to see, and swallowing
            // it here would turn a security signal into a routine failed send.
            recordDeliveryFailure(loggedInInfo, emailLog, e);
            throw e;
        } finally {
            // Currently a no-op on every path: sendPrepared() discards in its own finally, and
            // archiveOutboundEmail discards on both failure branches. It is kept deliberately
            // as the structural guarantee that
            // no prepared message -- and no PHI-bearing attachment snapshot -- survives this
            // method, so a future branch that forgets to discard cannot leak one. Passing
            // null as the primary failure is correct here: on the success path there is no
            // exception to attach a cleanup problem to, and on the failure paths the real
            // exception has already been handled by the catches above.
            if (emailSender != null) {
                discardPreparedQuietly(emailSender, null);
            }
        }

        if (delivered) {
            // Deliberately outside the catches above. A failure here means bookkeeping broke
            // after a message actually went out; marking that send FAILED would be false and
            // would invite a retry that sends the patient a second copy.
            recordDeliverySuccess(loggedInInfo, emailLog);
            if (emailLog.getChartDisplayOption().equals(ChartDisplayOption.WITH_FULL_NOTE)) {
                addEmailNote(loggedInInfo, emailLog);
            }
        }
        return emailLog;
    }

    /**
     * Flips a delivered message from its as-created FAILED state to SUCCESS.
     *
     * <p>{@code prepareEmailForOutbox} creates every EmailLog as FAILED with placeholder
     * text, so this update is not cosmetic: it is the only thing that stops a message that
     * actually went out from sitting in the outbox looking retryable. If it throws, the row
     * stays FAILED and a user re-sending from the outbox mails the patient a second copy,
     * so the failure is logged explicitly in those terms rather than as a generic
     * persistence error, and rethrown so the caller cannot treat the send as fully done.</p>
     *
     * <p>This does not eliminate the hazard -- a database that cannot accept the write
     * cannot be made to -- it makes it loud. Closing it properly needs a neutral initial
     * state (the EmailStatus enum currently offers only SUCCESS, FAILED and RESOLVED, and
     * the column carries a matching check constraint), which is a schema change beyond this
     * PR's scope.</p>
     */
    private void recordDeliverySuccess(LoggedInInfo loggedInInfo, EmailLog emailLog) {
        try {
            updateEmailStatus(loggedInInfo, emailLog, EmailStatus.SUCCESS, "");
        } catch (RuntimeException statusFailure) {
            logger.error(
                    "Outbound email was delivered but its status could not be recorded; emailLogId={} remains FAILED "
                            + "and re-sending it from the outbox would deliver a duplicate: {}",
                    emailLog.getId(), statusFailure.getClass().getSimpleName());
            throw statusFailure;
        }
    }

    /**
     * Persists and logs a delivery failure without letting bookkeeping replace it.
     *
     * <p>{@code updateEmailStatus} touches the database, so it can throw. Called
     * unguarded from a catch block it would propagate in place of the real failure: the
     * caller would see a persistence error, the log would never record why the send
     * failed, and in the rethrow path the original exception would be lost outright. The
     * status failure is attached as suppressed and reported separately instead.</p>
     */
    private void recordDeliveryFailure(LoggedInInfo loggedInInfo, EmailLog emailLog, Throwable failure) {
        String persistedMessage = safePersistedFailureMessage(failure);
        try {
            updateEmailStatus(loggedInInfo, emailLog, EmailStatus.FAILED, persistedMessage);
        } catch (RuntimeException statusFailure) {
            // updateEmailStatus re-checks _email WRITE. When the failure being recorded IS a
            // revoked _email privilege, that check refuses the write and the diagnostic
            // category is lost -- the row keeps the placeholder text from
            // prepareEmailForOutbox, so an operator sees "unknown reasons" for what was
            // actually an authorization failure. Fall back to the DAO directly: the row
            // already exists, this call already passed the entry privilege check, and
            // recording what the system just did is not a new privileged action.
            try {
                // Mirror updateEmailStatus's contract exactly: it writes status, message and
                // timestamp, then syncs all three onto the in-memory object so the EmailLog
                // returned to the caller matches the persisted row. Setting only two of the
                // three would hand back a log whose timestamp disagrees with the database.
                Date fallbackTimestamp = new Date();
                emailLogDao.updateEmailStatus(emailLog.getId(), EmailStatus.FAILED, persistedMessage, fallbackTimestamp);
                emailLog.setStatus(EmailStatus.FAILED);
                emailLog.setErrorMessage(persistedMessage);
                emailLog.setTimestamp(fallbackTimestamp);
            } catch (RuntimeException daoFailure) {
                failure.addSuppressed(statusFailure);
                failure.addSuppressed(daoFailure);
                logger.error("Failed to persist outbound email failure status: {}", daoFailure.getClass().getSimpleName());
            }
        }
        logger.error(safeFailureOperationMessage(failure), sanitizedDiagnostic(failure, 0));
    }

    /**
     * Releases a prepared message without ever replacing the failure being handled.
     *
     * <p>Cleanup runs on failure paths, so a throwing {@code discardPrepared()} would
     * propagate in place of the real fault: the archive exception would be lost, and with
     * it the FAILED status update in {@code sendEmail}. A cleanup problem must never be
     * the reason an operator cannot see why an email failed, so it is attached to the
     * original exception as suppressed rather than raised.</p>
     *
     * @param emailSender sender holding the prepared message, may be null
     * @param primaryFailure failure already in flight, or null when cleaning up a success path
     */
    private void discardPreparedQuietly(EmailSender emailSender, Throwable primaryFailure) {
        if (emailSender == null) {
            return;
        }
        try {
            emailSender.discardPrepared();
        } catch (RuntimeException cleanupFailure) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
            logger.warn("Prepared outbound email cleanup failed: {}", cleanupFailure.getClass().getSimpleName());
        }
    }

    private String safePersistedFailureMessage(Throwable failure) {
        return safeFailureOperationMessage(failure) + " (" + safeDiagnosticCategory(failure) + ")";
    }

    /**
     * Classifies a failure as archive or send by exception <em>type</em>.
     *
     * <p>Deliberately not a message comparison. {@code SMTPEmailSender} rethrows with
     * {@code e.getMessage()} from JavaMail, so the text is provider-controlled: a remote
     * server whose error string happened to contain the archive constant would have been
     * misreported as an archive failure, making the classification depend on a third
     * party's wording.</p>
     */
    private String safeFailureOperationMessage(Throwable failure) {
        return failure instanceof OutboundEmailArchiveException
                ? ARCHIVE_FAILURE_MESSAGE
                : SEND_FAILURE_MESSAGE;
    }

    private Throwable sanitizedDiagnostic(Throwable failure, int depth) {
        RuntimeException diagnostic = new RuntimeException(
                failure.getClass().getName() + " [" + safeDiagnosticCategory(failure) + "]");
        diagnostic.setStackTrace(failure.getStackTrace());
        Throwable cause = failure.getCause();
        if (cause != null && cause != failure && depth < 8) {
            diagnostic.initCause(sanitizedDiagnostic(cause, depth + 1));
        }
        return diagnostic;
    }

    private String safeDiagnosticCategory(Throwable failure) {
        String category = searchDiagnosticCategory(failure, 0);
        return category != null ? category : "uncategorized delivery failure";
    }

    /**
     * Finds the most specific category for a failure, searching both the cause chain
     * and Spring's aggregated per-message exceptions.
     *
     * <p>{@code MailSendException} needs special handling because
     * {@code JavaMailSenderImpl} does not put the real fault on the cause chain. For a
     * per-recipient failure it collects each message's exception and throws
     * {@code new MailSendException(failedMessages)} with a {@code null} cause, so the
     * {@code SendFailedException} that says what actually went wrong is reachable only
     * through {@link org.springframework.mail.MailSendException#getMessageExceptions()}.
     * Walking {@code getCause()} alone therefore always degraded to the generic
     * "SMTP send failure" label.</p>
     *
     * <p>The generic label is now a fallback applied only after both the nested message
     * exceptions and the cause chain come back empty. Previously it matched eagerly and
     * short-circuited the whole search, which also swallowed the connection-failure case
     * where Spring <em>does</em> supply a cause
     * ({@code new MailSendException("Mail server connection failed", ex)}).</p>
     *
     * <p>Only the category label is derived here. Message subjects, recipients, and the
     * failed {@code MimeMessage} keys of {@code getFailedMessages()} are never read, so
     * no PHI can reach the log or the persisted {@code EmailLog} error text.</p>
     */
    private String searchDiagnosticCategory(Throwable failure, int depth) {
        if (failure == null || depth >= 8) {
            return null;
        }
        String specific = safeDiagnosticCategoryFor(failure);
        if (specific != null) {
            return specific;
        }
        // Descend before settling for a wrapper's own generic label.
        if (failure instanceof org.springframework.mail.MailSendException mailSendFailure) {
            Exception[] messageExceptions = mailSendFailure.getMessageExceptions();
            if (messageExceptions != null) {
                for (Exception messageException : messageExceptions) {
                    String nested = searchDiagnosticCategory(messageException, depth + 1);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }
        String fromCause = searchCauseCategory(failure, depth);
        return fromCause != null ? fromCause : genericDiagnosticCategoryFor(failure);
    }

    private String searchCauseCategory(Throwable failure, int depth) {
        Throwable cause = failure.getCause();
        return cause != null && cause != failure ? searchDiagnosticCategory(cause, depth + 1) : null;
    }

    /**
     * Labels for exception types that describe a layer rather than a fault.
     *
     * <p>Applied only once the search has exhausted the nested message exceptions and
     * the cause chain. Matching these eagerly is what made the specific network
     * categories unreachable: a refused connection arrives as
     * {@code MailSendException -> MessagingException -> ConnectException}, so both outer
     * frames would answer before anything looked at the {@code ConnectException}, and
     * "connection failure" could never be reported for a real SMTP send.</p>
     */
    private String genericDiagnosticCategoryFor(Throwable failure) {
        if (failure instanceof jakarta.mail.MessagingException) {
            return "SMTP messaging failure";
        }
        if (failure instanceof org.springframework.mail.MailSendException) {
            return "SMTP send failure";
        }
        return null;
    }

    private String safeDiagnosticCategoryFor(Throwable failure) {
        if (failure instanceof SecurityException) {
            return "authorization failure";
        }
        if (failure instanceof java.net.SocketTimeoutException) {
            return "network timeout";
        }
        if (failure instanceof java.net.UnknownHostException) {
            return "host lookup failure";
        }
        if (failure instanceof java.net.ConnectException) {
            return "connection failure";
        }
        if (failure instanceof jakarta.mail.AuthenticationFailedException
                || failure instanceof org.springframework.mail.MailAuthenticationException) {
            return "SMTP authentication failure";
        }
        if (failure instanceof jakarta.mail.SendFailedException) {
            return "SMTP recipient failure";
        }
        // MessagingException and MailSendException are handled in
        // genericDiagnosticCategoryFor, not here: they name a layer, not a fault, and
        // matching them at this point would hide the specific cause they wrap.
        if (failure instanceof IOException) {
            return "I/O failure";
        }
        return null;
    }

    private void discardAfterPreparationFailure(EmailSender emailSender, Exception failure) {
        discardPreparedQuietly(emailSender, failure);
        logger.warn("Outbound email preparation failed: {}", failure.getClass().getSimpleName());
    }

    private void archiveOutboundEmail(LoggedInInfo loggedInInfo, EmailSender emailSender, EmailLog emailLog) throws EmailSendingException {
        OutboundEmailArchiveDto archiveRequest;
        try {
            // Message preparation, NOT archive storage. This validates SMTP configuration
            // (host, port, credentials) and builds the MIME message, so a failure here is a
            // send-configuration problem. Reporting it as an archive fault would send an
            // operator to inspect the archive subsystem over a mistyped SMTP password.
            archiveRequest = emailSender.prepareOutboundArchive(emailLog);
        } catch (EmailSendingException e) {
            discardAfterPreparationFailure(emailSender, e);
            throw e;
        } catch (SecurityException e) {
            // Authorization is the caller's to see. Converting this would route it to
            // sendEmail's non-rethrow catch, which returns an EmailLog and reports a routine
            // failed send -- turning a revoked _email privilege into something that looks
            // like a bad SMTP host. Transport already rethrows SecurityException; preparation
            // must match, or the same revocation behaves differently depending on which side
            // of the archive call it happens on.
            discardAfterPreparationFailure(emailSender, e);
            throw e;
        } catch (RuntimeException e) {
            // Other unchecked preparation failures -- config JSON parsing, MIME construction
            // -- are converted so they reach sendEmail's non-rethrow catch: the attempt is
            // recorded as FAILED and the caller gets an EmailLog rather than a raw stack.
            // sendEmail does also catch RuntimeException now, but that path rethrows, which
            // is the wrong outcome for an ordinary preparation fault.
            discardAfterPreparationFailure(emailSender, e);
            throw new EmailSendingException(SEND_FAILURE_MESSAGE, e);
        }

        try {
            // Preserve the exact attempted message before transport. ARCHIVED describes successful
            // capture of that immutable artifact; EmailLog remains the source of truth for whether
            // delivery subsequently succeeded or failed, so failed attempts retain their audit record.
            outboundEmailArchiveService.archive(loggedInInfo, archiveRequest);
        } catch (SecurityException e) {
            // Third and last site subject to the authorization-propagation rule above.
            // OutboundEmailArchiveService.archive throws SecurityException for a missing
            // _edoc w right and for patient-record access denial; wrapping either as an
            // archive fault would tell the operator storage broke when access was refused.
            discardPreparedQuietly(emailSender, e);
            logger.warn("Outbound email archive authorization failed: {}", e.getClass().getSimpleName());
            throw e;
        } catch (IOException | RuntimeException e) {
            discardPreparedQuietly(emailSender, e);
            logger.warn("Outbound email archive failed: {}", e.getClass().getSimpleName());
            throw new OutboundEmailArchiveException(ARCHIVE_FAILURE_MESSAGE, e);
        }
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

        if (emailData.getSenderConfigId() == null) {
            throw new IllegalArgumentException("Sender email configuration ID is required");
        }
        EmailConfig emailConfig = emailConfigDao.findActiveEmailConfigById(emailData.getSenderConfigId());
        if (emailConfig == null) {
            throw new IllegalArgumentException("No active email configuration found for ID " + emailData.getSenderConfigId());
        }
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, emailData.getDemographicNo());
        Provider provider = providerManager.getProvider(loggedInInfo, emailData.getProviderNo());

        EmailLog emailLog = new EmailLog(emailConfig, emailConfig.getSenderEmail(), emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailStatus.FAILED);
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
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private void encryptAttachments(List<EmailAttachment> encryptableAttachments, String password) throws EmailSendingException {
        for (EmailAttachment attachment : encryptableAttachments) {
            try {
                Path attachmentPDFPath = PathValidationUtils.resolveTrustedPath(new File(attachment.getFilePath())).toPath();
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
