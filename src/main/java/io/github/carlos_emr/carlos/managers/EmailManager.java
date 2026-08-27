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
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.core.EmailConfigSecrets;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailConsentResult;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.email.core.EmailStatusResult;
import io.github.carlos_emr.carlos.email.util.EmailNoteUtil;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.PDFEncryptionUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Service
public class EmailManager {
    private final Logger logger = MiscUtils.getLogger();
    /** Keep recovery controls away from sends that may still be executing in another request. */
    static final long PENDING_RESOLUTION_MIN_AGE_MILLIS = 15L * 60L * 1000L;
    static final String SENDER_CONFIG_MISCONFIGURATION_ERROR = "Email sender account is not configured or is inactive.";
    private static final String UNKNOWN_NAME_PART = "Unknown";
    private static final String SENDER_NAME_PART = "Sender";
    private static final String PATIENT_NAME_PART = "Patient";
    private static final String PROVIDER_NAME_PART = "Provider";

    public enum EmailResolutionResult {
        RESOLVED,
        NOT_FOUND,
        NOT_RESOLVABLE,
        PENDING_TOO_RECENT,
        CONFLICT
    }

    @Autowired
    private EmailConfigDaoImpl emailConfigDao;
    @Autowired
    private EmailLogDaoImpl emailLogDao;
    @Autowired
    private OscarLogDao oscarLogDao;
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
    private final EmailConsentResolver emailConsentResolver;
    private final EmailSenderFactory emailSenderFactory;

    /**
     * Creates an email manager with the consent gate and sender factory used by the send path.
     * Remaining legacy collaborators are injected into their existing fields by Spring.
     *
     * @param emailConsentResolver resolves current patient email consent
     * @param emailSenderFactory creates the outbound sender after consent is accepted
     */
    public EmailManager(EmailConsentResolver emailConsentResolver, EmailSenderFactory emailSenderFactory) {
        this.emailConsentResolver = emailConsentResolver;
        this.emailSenderFactory = emailSenderFactory;
    }

    /**
     * Sends an email with optional encryption and returns the email send result.
     *
     * This method validates access, sanitizes the email data, resolves the active sender
     * configuration, persists a {@code PENDING} outbox log for valid sender configurations,
     * optionally encrypts the content, sends the message, and updates the persisted log to
     * {@code SUCCESS} or {@code FAILED}. A log left {@code PENDING} means no conclusive transport
     * outcome was recorded; that is deliberately distinct from {@code FAILED}, so an interrupted
     * send is not mistaken for one that definitely did not reach the patient.
     * If configured to display in the patient chart, it also creates a case management note
     * documenting the email communication.
     *
     * If the sender configuration is missing or inactive, this method returns a transient
     * FAILED EmailLog with a safe error message. That failure result is not persisted and does
     * not have a database id.
     *
     * The method performs the following steps:
     * 1. Validates user has _email WRITE privilege
     * 2. Sanitizes email data fields
     * 3. Resolves current patient consent and records it on the email log
     * 4. Returns the log in BLOCKED status without creating a sender when consent denies the send
     * 5. Encrypts message and/or attachments if requested
     * 6. Sends email via configured email server
     * 7. Updates log status to SUCCESS or FAILED
     * 8. Creates a chart note for successful sends configured for WITH_FULL_NOTE display
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param emailData EmailData containing email subject, body, recipients, attachments, and configuration options
     * @return EmailLog the persisted email log entry for normal send attempts, or a transient failed result for sender configuration failures
     * @throws RuntimeException if user lacks _email WRITE privilege
     */
    public EmailLog sendEmail(LoggedInInfo loggedInInfo, EmailData emailData) {
        return sendEmailWithResult(loggedInInfo, emailData).getEmailLog();
    }

    /**
     * Sends an email while keeping the transport outcome distinct from persistence state.
     *
     * <p>This is the preferred API for interactive callers. The compatibility
     * {@link #sendEmail(LoggedInInfo, EmailData)} method remains for existing integrations that
     * consume only the log entity.</p>
     */
    public EmailSendResult sendEmailWithResult(LoggedInInfo loggedInInfo, EmailData emailData) {
        try {
            return sendEmailInternal(loggedInInfo, emailData);
        } finally {
            if (emailData != null && emailData.getWorkingDirectory() != null) {
                emailData.getWorkingDirectory().close();
            }
        }
    }

    private EmailSendResult sendEmailInternal(LoggedInInfo loggedInInfo, EmailData emailData) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        sanitizeEmailFields(emailData);
        EmailConfig emailConfig = findActiveSenderEmailConfig(emailData);
        if (emailConfig == null) {
            logger.warn("Email send failed before transport: sender email configuration is missing or inactive for senderConfigId={}", emailData.getSenderConfigId());
            EmailLog failedEmailLog = createFailedEmailLog(
                    emailData, SENDER_CONFIG_MISCONFIGURATION_ERROR);
            persistPreTransportFailureAuditEvent(loggedInInfo, emailData,
                    "sender_configuration_unavailable");
            return EmailSendResult.failed(failedEmailLog, false);
        }

        EmailConsentResult consentResult = emailConsentResolver.resolve(loggedInInfo, emailData.getDemographicNo());
        EmailLog emailLog = prepareEmailForOutbox(loggedInInfo, emailData, emailConfig);
        upgradeConfigCredentialsAtRest(emailLog.getEmailConfig());
        applyConsentSnapshot(emailLog, consentResult, emailData);
        logPreparedEmail(loggedInInfo, emailLog);
        if (isBlockedByConsent(consentResult, emailData)) {
            String errorMessage = getConsentBlockMessage(consentResult);
            updateEmailStatus(loggedInInfo, emailLog, EmailStatus.BLOCKED, errorMessage);
            LogAction.addLog(loggedInInfo, "EmailManager.sendEmail.blocked", "Email",
                    "emailLogId=" + emailLog.getId() + "&consentStatus=" + consentResult.getStatus(),
                    String.valueOf(emailLog.getDemographic().getDemographicNo()), "");
            return EmailSendResult.failed(emailLog, true);
        }

        try {
            if (emailData.getIsEncrypted()) {
                encryptEmail(emailData);
            }
            EmailSender emailSender = emailSenderFactory.create(loggedInInfo, emailLog.getEmailConfig(), emailData);
            emailSender.send();
            boolean outcomeRecorded;
            try {
                emailLog = updateEmailStatus(
                        loggedInInfo, emailLog, EmailStatus.SUCCESS, "");
                outcomeRecorded = EmailStatus.SUCCESS.equals(emailLog.getStatus());
            } catch (RuntimeException statusUpdateFailure) {
                // Transport has already accepted the message. Propagating a 500 would invite the
                // sender to retry immediately and could deliver a duplicate. Leave the durable row
                // PENDING, return the conclusive transport outcome, and make the persistence problem
                // operator-visible.
                logger.error("Email transport accepted the message but its SUCCESS status could not be recorded for emailLogId={}",
                        emailLog.getId(), statusUpdateFailure);
                persistTransportOutcomeBestEffort(loggedInInfo, emailLog,
                        "transportOutcome=SUCCESS; statusRecorded=false");
                outcomeRecorded = false;
            }
            if (ChartDisplayOption.WITH_FULL_NOTE.equals(emailLog.getChartDisplayOption())) {
                try {
                    addEmailNote(loggedInInfo, emailLog);
                } catch (RuntimeException noteFailure) {
                    // The message is already accepted. A secondary chart-note failure must not
                    // turn the response into a retryable send failure.
                    logger.error("Email transport accepted the message but its chart note could not be created for emailLogId={}",
                            emailLog.getId(), noteFailure);
                    persistTransportOutcomeBestEffort(loggedInInfo, emailLog,
                            "transportOutcome=SUCCESS; chartNoteRecorded=false");
                }
            }
            return EmailSendResult.accepted(emailLog, outcomeRecorded);
        } catch (EmailSendingException e) {
            if (e.isDeliveryOutcomeUncertain()) {
                // A timeout or connection loss after dispatch does not prove rejection. Keep the
                // durable PENDING state so neither the sender nor an administrator is told that a
                // possibly delivered clinical message definitely failed.
                emailLog.setErrorMessage(safeDiagnostic(e));
                persistTransportOutcomeBestEffort(loggedInInfo, emailLog,
                        "transportOutcome=UNCONFIRMED; diagnosticPresent="
                                + !StringUtils.isNullOrEmpty(e.getMessage()));
                logTransportFailure("UNCONFIRMED", e);
                return EmailSendResult.unconfirmed(emailLog);
            }
            try {
                emailLog = updateEmailStatus(
                        loggedInInfo, emailLog, EmailStatus.FAILED, safeDiagnostic(e));
            } catch (RuntimeException statusUpdateFailure) {
                logger.error("Email transport failed but its FAILED status could not be recorded for emailLogId={}",
                        emailLog.getId(), statusUpdateFailure);
                // This value is only returned to the current request. The durable row remains
                // PENDING, correctly signalling that no outcome was recorded.
                emailLog.setStatus(EmailStatus.FAILED);
                emailLog.setErrorMessage(safeDiagnostic(e));
                persistTransportOutcomeBestEffort(loggedInInfo, emailLog,
                        "transportOutcome=FAILED; statusRecorded=false");
                return EmailSendResult.failed(emailLog, false);
            }
            logTransportFailure("FAILED", e);
            return EmailSendResult.failed(
                    emailLog, EmailStatus.FAILED.equals(emailLog.getStatus()));
        }
    }

    private String safeDiagnostic(EmailSendingException exception) {
        return StringUtils.isNullOrEmpty(exception.getMessage())
                ? "Email transport did not accept the message."
                : exception.getMessage();
    }

    private void logTransportFailure(String outcome, EmailSendingException exception) {
        // Nested parser and transport exception messages can echo configuration values or remote
        // content. Retain an actionable safe diagnostic and the cause type without logging the
        // potentially sensitive cause message or stack trace.
        Throwable cause = exception.getCause();
        logger.error("Email transport outcome={}; diagnostic={}; causeType={}",
                outcome, safeDiagnostic(exception),
                cause == null ? "none" : cause.getClass().getName());
    }

    /**
     * Transparently upgrades an email configuration's transport secrets to at-rest encryption on
     * first use (the send path), so hand-inserted plaintext {@code emailConfig.configDetails} rows
     * are migrated the first time they are used to send.
     *
     * <p>The upgrade is best-effort: if the encryption key is unavailable, or the persistence of the
     * re-encrypted row fails, the row is left as-is and the send proceeds with the existing
     * (plaintext) value rather than blocking outbound mail. Already-encrypted rows are detected by
     * {@link EmailConfigSecrets} and produce no database write. Neither the secret nor the raw
     * {@code configDetails} JSON is ever logged.</p>
     *
     * @param emailConfig the configuration whose secrets should be encrypted at rest, may be null
     */
    private void upgradeConfigCredentialsAtRest(EmailConfig emailConfig) {
        if (emailConfig == null) {
            return;
        }
        try {
            String original = emailConfig.getConfigDetailsJson();
            String encrypted = EmailConfigSecrets.encryptSecrets(original);
            if (!java.util.Objects.equals(original, encrypted)) {
                emailConfig.setConfigDetailsJson(encrypted);
                emailConfigDao.merge(emailConfig);
            }
        } catch (EmailSendingException | RuntimeException e) {
            // Best-effort: neither a missing key (EmailSendingException) nor a persistence failure
            // from merge (RuntimeException, e.g. DataAccessException) may block outbound mail. The
            // send proceeds with the existing value. The logged cause aids diagnosis and carries no
            // plaintext secret or raw config JSON: encryptSecrets fails before the value is set, and
            // by the time merge runs the stored value is already ciphertext.
            logger.warn("Unable to encrypt email transport credentials at rest for config id={}",
                    emailConfig.getId(), e);
        }
    }

    public boolean hasActiveEmailConfig(int senderConfigId) {
        return emailConfigDao.findActiveEmailConfigById(senderConfigId) != null;
    }

    /**
     * Prepares an email for sending by creating and persisting an email log entry in the outbox.
     *
     * This method creates a comprehensive email log record that captures all email metadata,
     * configuration, and content. The email log is initially created with {@code PENDING} status
     * and no failure detail, then updated to {@code SUCCESS} once the configured transport accepts
     * the send, or to {@code FAILED} if the send raises. The initial state is deliberately not
     * {@code FAILED}: the post-send status write can itself fail, and a row left saying FAILED
     * for a message that actually went out invites a duplicate resend.
     *
     * The method:
     * 1. Retrieves active email configuration for the sender
     * 2. Loads demographic and provider information
     * 3. Creates EmailLog entity with all email data
     * 4. Persists the email log to database
     * The caller records the consent snapshot and compliance audit entry after this method returns.
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

        EmailConfig emailConfig = findActiveSenderEmailConfig(emailData);
        if (emailConfig == null) {
            throw new IllegalArgumentException("sender email configuration is missing or inactive");
        }
        return prepareEmailForOutbox(loggedInInfo, emailData, emailConfig);
    }

    private EmailLog prepareEmailForOutbox(LoggedInInfo loggedInInfo, EmailData emailData, EmailConfig emailConfig) {
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, emailData.getDemographicNo());
        Provider provider = providerManager.getProvider(loggedInInfo, emailData.getProviderNo());

        EmailLog emailLog = new EmailLog(emailConfig, emailConfig.getSenderEmail(), emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailStatus.PENDING);
        setEmailAttachments(emailLog, emailData.getAttachments());
        emailLog.setEncryptedMessage(emailData.getEncryptedMessage());
        emailLog.setPassword("");
        emailLog.setPasswordClue("");
        emailLog.setIsEncrypted(emailData.getIsEncrypted());
        emailLog.setIsAttachmentEncrypted(emailData.getIsAttachmentEncrypted());
        emailLog.setChartDisplayOption(emailData.getChartDisplayOption());
        emailLog.setInternalComment(emailData.getInternalComment());
        emailLog.setTransactionType(emailData.getTransactionType());
        // PENDING is self-describing; the admin UI supplies a localized explanation. Do not store
        // prose that becomes stale or falsely claims an interrupted send is still in progress.
        emailLog.setErrorMessage(null);
        emailLog.setAdditionalParams(emailData.getAdditionalParams());
        emailLog.setDemographic(demographic);
        emailLog.setProvider(provider);
        emailLogDao.persist(emailLog);

        return emailLog;
    }

    private EmailConfig findActiveSenderEmailConfig(EmailData emailData) {
        if (emailData.getSenderConfigId() == null) {
            return null;
        }
        return emailConfigDao.findActiveEmailConfigById(emailData.getSenderConfigId());
    }

    private EmailLog createFailedEmailLog(EmailData emailData, String errorMessage) {
        EmailLog emailLog = new EmailLog();
        emailLog.setFromEmail("");
        emailLog.setToEmail(emailData.getRecipients());
        emailLog.setSubject(nullToEmpty(emailData.getSubject()));
        emailLog.setBody(nullToEmpty(emailData.getBody()));
        emailLog.setStatus(EmailStatus.FAILED);
        emailLog.setErrorMessage(errorMessage);
        emailLog.setEncryptedMessage(nullToEmpty(emailData.getEncryptedMessage()));
        emailLog.setPassword("");
        emailLog.setPasswordClue("");
        emailLog.setIsEncrypted(emailData.getIsEncrypted());
        emailLog.setIsAttachmentEncrypted(emailData.getIsAttachmentEncrypted());
        emailLog.setChartDisplayOption(emailData.getChartDisplayOption());
        emailLog.setInternalComment(nullToEmpty(emailData.getInternalComment()));
        emailLog.setTransactionType(emailData.getTransactionType());
        emailLog.setAdditionalParams(nullToEmpty(emailData.getAdditionalParams()));
        setEmailAttachments(emailLog, emailData.getAttachments());
        return emailLog;
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    /**
     * Manually resolves a failed or stale-unconfirmed email without destroying its original
     * diagnostic or send timestamp. The compare-and-set update prevents a stale admin page from
     * overwriting a transport result committed by another request.
     */
    @Transactional
    public EmailResolutionResult resolveEmailStatus(LoggedInInfo loggedInInfo, Integer emailLogId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        EmailLog emailLog = emailLogDao.find(emailLogId);
        if (emailLog == null) {
            return EmailResolutionResult.NOT_FOUND;
        }
        if (EmailStatus.PENDING.equals(emailLog.getStatus()) && !isManuallyResolvable(emailLog)) {
            return EmailResolutionResult.PENDING_TOO_RECENT;
        }
        if (!isManuallyResolvable(emailLog)) {
            return EmailResolutionResult.NOT_RESOLVABLE;
        }

        EmailStatus previousStatus = emailLog.getStatus();
        int updatedRows = emailLogDao.transitionEmailStatus(
                emailLog.getId(), previousStatus, EmailStatus.RESOLVED,
                emailLog.getErrorMessage(), emailLog.getTimestamp());
        if (updatedRows != 1) {
            return EmailResolutionResult.CONFLICT;
        }

        emailLog.setStatus(EmailStatus.RESOLVED);
        persistEmailAuditEvent(loggedInInfo, "EmailManager.resolveEmailStatus", emailLog,
                "previousStatus=" + previousStatus + "; diagnosticPreserved="
                        + (emailLog.getErrorMessage() != null));
        return EmailResolutionResult.RESOLVED;
    }

    /**
     * Failed sends are immediately reviewable. PENDING sends become reviewable only after a
     * conservative delay, which avoids racing the normal synchronous transport request.
     */
    public boolean isManuallyResolvable(EmailLog emailLog) {
        if (emailLog == null) {
            return false;
        }
        if (EmailStatus.FAILED.equals(emailLog.getStatus())) {
            return true;
        }
        return EmailStatus.PENDING.equals(emailLog.getStatus())
                && emailLog.getTimestamp() != null
                && emailLog.getTimestamp().getTime()
                        <= System.currentTimeMillis() - PENDING_RESOLUTION_MIN_AGE_MILLIS;
    }

    /**
     * Updates the status of an email log entry with new status and error message.
     *
     * This method completes a transport lifecycle transition using a compare-and-set database
     * update. Only PENDING to SUCCESS or FAILED is valid; a concurrent manual resolution is not
     * overwritten.
     *
     * Common status values:
     * - SUCCESS: Email sent successfully
     * - FAILED: Email transmission failed
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information
     * @param emailLog EmailLog the email log entry to update
     * @param emailStatus EmailStatus the new status to set
     * @param errorMessage String the error message to store, empty string if no error
     * @return EmailLog the updated email log entry with new status and timestamp
     * @throws RuntimeException if user lacks _email WRITE privilege
     * @throws IllegalStateException if the requested lifecycle transition is invalid or the
     *         persisted row disappeared during a concurrent update
     */
    public EmailLog updateEmailStatus(LoggedInInfo loggedInInfo, EmailLog emailLog, EmailStatus emailStatus, String errorMessage) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        if (emailLog == null) {
            throw new IllegalArgumentException("emailLog must not be null");
        }
        EmailStatus previousStatus = emailLog.getStatus();
        if (!EmailStatus.PENDING.equals(previousStatus)
                || !(EmailStatus.SUCCESS.equals(emailStatus)
                || EmailStatus.FAILED.equals(emailStatus)
                || EmailStatus.BLOCKED.equals(emailStatus))) {
            throw new IllegalStateException("Invalid email transport status transition from "
                    + previousStatus + " to " + emailStatus);
        }

        Date newTimestamp = new Date();
        int updatedRows = emailLogDao.transitionEmailStatus(
                emailLog.getId(), previousStatus, emailStatus, errorMessage, newTimestamp);
        if (updatedRows != 1) {
            EmailLog persistedEmailLog = emailLogDao.find(emailLog.getId());
            if (persistedEmailLog == null) {
                throw new IllegalStateException("Email log disappeared during status transition: "
                        + emailLog.getId());
            }
            logger.warn("Email status transition {} -> {} lost a concurrent update for emailLogId={}; persisted status is {}",
                    previousStatus, emailStatus, emailLog.getId(), persistedEmailLog.getStatus());
            if (EmailStatus.RESOLVED.equals(persistedEmailLog.getStatus())) {
                // Admin-audit-only for now: keep the conclusive transport result without
                // overwriting the administrator's RESOLVED decision or its original diagnostic.
                // TODO: If operators need this in the email-management workflow, expose these
                // events through EmailStatusResult and render a separate outcome/history field.
                persistEmailAuditEvent(loggedInInfo,
                        "EmailManager.transportOutcomeAfterResolution", persistedEmailLog,
                        "transportOutcome=" + emailStatus + "; diagnosticPresent="
                                + !StringUtils.isNullOrEmpty(errorMessage));
            }
            emailLog.setStatus(persistedEmailLog.getStatus());
            emailLog.setErrorMessage(persistedEmailLog.getErrorMessage());
            emailLog.setTimestamp(persistedEmailLog.getTimestamp());
            return emailLog;
        }

        // Update object in memory so caller still has the right values
        emailLog.setStatus(emailStatus);
        emailLog.setErrorMessage(errorMessage);
        emailLog.setTimestamp(newTimestamp);

        return emailLog;
    }

    /**
     * Persists audit events synchronously. Callers that mutate an email in a transaction rely on
     * failures propagating so the mutation cannot commit without its corresponding audit record.
     */
    private void persistEmailAuditEvent(LoggedInInfo loggedInInfo, String action,
            EmailLog emailLog, String data) {
        OscarLog auditLog = createActorAuditLog(loggedInInfo);
        auditLog.setAction(action);
        auditLog.setContent("Email");
        auditLog.setContentId(String.valueOf(emailLog.getId()));
        if (emailLog.getDemographic() != null
                && emailLog.getDemographic().getDemographicNo() != null) {
            auditLog.setDemographicId(emailLog.getDemographic().getDemographicNo());
        }
        auditLog.setData(data);
        oscarLogDao.persist(auditLog);
    }

    private void persistPreTransportFailureAuditEvent(LoggedInInfo loggedInInfo,
            EmailData emailData, String reason) {
        OscarLog auditLog = createActorAuditLog(loggedInInfo);
        auditLog.setAction("EmailManager.preTransportFailure");
        auditLog.setContent("Email");
        auditLog.setContentId("unpersisted");
        if (emailData.getDemographicNo() != null && emailData.getDemographicNo() > 0) {
            auditLog.setDemographicId(emailData.getDemographicNo());
        }
        auditLog.setData("reason=" + reason + "; senderConfigId="
                + String.valueOf(emailData.getSenderConfigId()));
        try {
            oscarLogDao.persist(auditLog);
        } catch (RuntimeException auditFailure) {
            // Preserve the user-facing graceful failure even if the audit database is unavailable;
            // the application log remains an operator-visible fallback.
            logger.error("Could not persist pre-transport email failure audit event", auditFailure);
        }
    }

    private OscarLog createActorAuditLog(LoggedInInfo loggedInInfo) {
        OscarLog auditLog = new OscarLog();
        if (loggedInInfo.getLoggedInSecurity() != null) {
            auditLog.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
        }
        if (loggedInInfo.getLoggedInProvider() != null) {
            auditLog.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        }
        auditLog.setIp(loggedInInfo.getIp());
        return auditLog;
    }

    private void persistTransportOutcomeBestEffort(LoggedInInfo loggedInInfo,
            EmailLog emailLog, String data) {
        try {
            persistEmailAuditEvent(loggedInInfo,
                    "EmailManager.transportOutcomeNotRecorded", emailLog, data);
        } catch (RuntimeException auditFailure) {
            logger.error("Could not persist unrecorded email transport outcome audit event for emailLogId={}",
                    emailLog.getId(), auditFailure);
        }
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
     * @param emailStatus String the email status to filter by (PENDING, SUCCESS, FAILED, RESOLVED), or null for all statuses
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
        if (emailAttachments != null) {
            for (EmailAttachment emailAttachment : emailAttachments) {
                emailAttachmentList.add(new EmailAttachment(emailLog, emailAttachment.getFileName(), emailAttachment.getFilePath(), emailAttachment.getDocumentType(), emailAttachment.getDocumentId()));
            }
        }
        emailLog.setEmailAttachments(emailAttachmentList);
    }

    private void applyConsentSnapshot(EmailLog emailLog, EmailConsentResult consentResult, EmailData emailData) {
        emailLog.setConsentStatus(consentResult.getStatus());
        emailLog.setConsentId(consentResult.getConsentId());
        emailLog.setConsentLastUpdateDate(consentResult.getConsentLastUpdateDate());
        emailLog.setConsentOverride(isValidUnknownConsentOverride(consentResult, emailData));
        emailLog.setConsentOverrideReason(emailLog.getConsentOverride() ? emailData.getConsentOverrideReason() : "");
        emailLogDao.merge(emailLog);
    }

    private void logPreparedEmail(LoggedInInfo loggedInInfo, EmailLog emailLog) {
        String logData = "emailLogId=" + emailLog.getId()
                + "&consentStatus=" + emailLog.getConsentStatus()
                + "&override=" + emailLog.getConsentOverride();
        LogAction.addLog(loggedInInfo, "EmailManager.prepareEmailForOutbox", "Email", logData,
                String.valueOf(emailLog.getDemographic().getDemographicNo()), "");
    }

    private boolean isBlockedByConsent(EmailConsentResult consentResult, EmailData emailData) {
        return consentResult.getStatus() != EmailConsentStatus.OPT_IN
                && (consentResult.getStatus() != EmailConsentStatus.UNKNOWN
                || !isValidUnknownConsentOverride(consentResult, emailData));
    }

    private boolean isValidUnknownConsentOverride(EmailConsentResult consentResult, EmailData emailData) {
        return consentResult.getStatus() == EmailConsentStatus.UNKNOWN
                && emailData.getConsentOverride()
                && !StringUtils.isNullOrEmpty(emailData.getConsentOverrideReason());
    }

    private String getConsentBlockMessage(EmailConsentResult consentResult) {
        if (consentResult.getStatus() == EmailConsentStatus.OPT_OUT) {
            return "Email blocked: patient has explicitly opted out of email communication.";
        }
        if (consentResult.getStatus() == EmailConsentStatus.NOT_CONFIGURED) {
            return "Email blocked: patient email consent is not configured.";
        }
        return "Email blocked: patient email consent is unknown and no override reason was provided.";
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
     * the encrypted message to a PDF attachment and encrypts selected attachments.
     *
     * Encryption workflow:
     * 1. Convert encrypted message text to PDF attachment (if present)
     * 2. Collect attachments to encrypt based on isAttachmentEncrypted flag
     * 3. Encrypt all selected attachments with the provided password
     * 4. Update email attachments list with encrypted files
     *
     * @param emailData EmailData the email data containing content to encrypt
     * @throws EmailSendingException if PDF encryption fails
     */
    private void encryptEmail(EmailData emailData) throws EmailSendingException {
        ensureWorkingDirectory(emailData);
        // Encrypt message and attachment
        List<EmailAttachment> encryptableAttachments = new ArrayList<>();
        if (!StringUtils.isNullOrEmpty(emailData.getEncryptedMessage())) {
            encryptableAttachments.add(createMessageAttachment(emailData));
        }
        if (emailData.getIsAttachmentEncrypted() && !emailData.getAttachments().isEmpty()) {
            encryptableAttachments.addAll(emailData.getAttachments());
        }
        encryptAttachments(encryptableAttachments, emailData);

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        emailAttachments.addAll(encryptableAttachments);
        if (!emailData.getIsAttachmentEncrypted() && !emailData.getAttachments().isEmpty()) {
            emailAttachments.addAll(emailData.getAttachments());
        }
        emailData.setAttachments(emailAttachments);
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
    private EmailAttachment createMessageAttachment(EmailData emailData) throws EmailSendingException {
        if (StringUtils.isNullOrEmpty(emailData.getEncryptedMessage())) {
            return null;
        }
        String htmlSafeMessage = Encode.forHtmlContent(emailData.getEncryptedMessage()).replace("\n", "<br>");
        emailData.setEncryptedMessage(htmlSafeMessage);
        Path encryptedMessagePDF = ConvertToEdoc.saveAsTempPDF(emailData);
        try {
            encryptedMessagePDF = emailData.getWorkingDirectory().adoptGeneratedPdf(encryptedMessagePDF);
        } catch (IOException e) {
            logger.error("Failed to secure generated email message PDF", e);
            throw new EmailSendingException("Failed to create encrypted email message", e);
        }
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
    private void encryptAttachments(
            List<EmailAttachment> encryptableAttachments,
            EmailData emailData
    ) throws EmailSendingException {
        for (EmailAttachment attachment : encryptableAttachments) {
            try {
                Path attachmentPDFPath = PathValidationUtils.resolveTrustedPath(new File(attachment.getFilePath())).toPath();
                attachmentPDFPath = PDFEncryptionUtil.encryptPDF(attachmentPDFPath, emailData.getPassword());
                attachmentPDFPath = emailData.getWorkingDirectory().adoptGeneratedPdf(attachmentPDFPath);
                attachment.setFilePath(attachmentPDFPath.toString());
            } catch (IOException e) {
                logger.error("Failed to create encrypted email attachments", e);
                throw new EmailSendingException("Failed to create encrypted email attachments", e);
            }
        }
    }

    private static void ensureWorkingDirectory(EmailData emailData) throws EmailSendingException {
        if (emailData.getWorkingDirectory() != null) {
            return;
        }
        try {
            emailData.setWorkingDirectory(EmailComposeWorkingDirectory.create());
        } catch (IOException e) {
            throw new EmailSendingException("Unable to create secure email working directory", e);
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
            EmailStatusResult emailStatusResult = new EmailStatusResult(result.getId(), result.getSubject(), getSenderFirstName(emailConfig),
                    getSenderLastName(emailConfig), nullToEmpty(result.getFromEmail()), getDemographicFirstName(demographic),
                    getDemographicLastName(demographic), String.join(", ", result.getToEmail()), getProviderFirstName(provider), getProviderLastName(provider),
                    result.getIsEncrypted(), result.getStatus(), result.getErrorMessage(), result.getTimestamp());
            emailStatusResult.setResolvable(isManuallyResolvable(result));
            emailStatusResult.applyConsentSnapshot(result);
            emailStatusResults.add(emailStatusResult);
        }
        Collections.sort(emailStatusResults);
        return emailStatusResults;
    }

    private String getSenderFirstName(EmailConfig emailConfig) {
        return getDisplayNamePart(emailConfig != null ? emailConfig.getSenderFirstName() : null, UNKNOWN_NAME_PART);
    }

    private String getSenderLastName(EmailConfig emailConfig) {
        return getDisplayNamePart(emailConfig != null ? emailConfig.getSenderLastName() : null, SENDER_NAME_PART);
    }

    private String getDemographicFirstName(Demographic demographic) {
        if (getAliasOnlyDemographicName(demographic) != null) {
            return "";
        }
        return getDisplayNamePart(demographic != null ? demographic.getFirstName() : null, UNKNOWN_NAME_PART);
    }

    private String getDemographicLastName(Demographic demographic) {
        String aliasOnlyName = getAliasOnlyDemographicName(demographic);
        if (aliasOnlyName != null) {
            return aliasOnlyName;
        }
        String lastName = getDisplayNamePart(demographic != null ? demographic.getLastName() : null, PATIENT_NAME_PART);
        String aliasName = getDemographicAliasName(demographic);
        return aliasName != null ? lastName + " " + aliasName : lastName;
    }

    private String getProviderFirstName(Provider provider) {
        return getDisplayNamePart(provider != null ? provider.getFirstName() : null, UNKNOWN_NAME_PART);
    }

    private String getProviderLastName(Provider provider) {
        return getDisplayNamePart(provider != null ? provider.getLastName() : null, PROVIDER_NAME_PART);
    }

    private String getAliasOnlyDemographicName(Demographic demographic) {
        if (demographic == null || !isBlank(demographic.getFirstName()) || !isBlank(demographic.getLastName())) {
            return null;
        }

        return getDemographicAliasName(demographic);
    }

    private String getDemographicAliasName(Demographic demographic) {
        if (demographic == null) {
            return null;
        }
        String alias = trimToNull(demographic.getAlias());
        return alias != null ? "(" + alias + ")" : null;
    }

    private String getDisplayNamePart(String value, String fallback) {
        String trimmedValue = trimToNull(value);
        return trimmedValue != null ? trimmedValue : fallback;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String trimToNull(String value) {
        if (StringUtils.isNullOrEmpty(value)) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
