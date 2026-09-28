/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.PortalDeliveryState;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coordinates the irreversible email send with the recoverable portal publication.
 *
 * <p>The password is created in the patient portal as an unpublished secret, used to encrypt the
 * email, cleared before the mail sender is built, and published only after the provider accepts
 * the email. Every lifecycle step is a compare-and-set on {@link EmailLog#getPortalDeliveryState()},
 * so the original request and a later recovery can never make conflicting decisions. Recovery
 * never encrypts or sends email.</p>
 *
 * <p>Persisted error messages are English diagnostics, like the rest of the outbox; the recovery
 * page renders its own localized text from {@link #classify(EmailLog)}.</p>
 */
@Service
public class PortalEmailDeliveryService {
    public static final String ENABLED_PROPERTY = "patient_portal.email.enabled";
    public static final String UNAVAILABLE = "Email was not sent. The patient portal password could not be prepared.";
    public static final String RECIPIENT_NOT_RECORDED = "Email was not sent. Select one email address recorded for this patient.";
    public static final String ACCOUNT_NOT_READY = "Email was not sent. The patient's portal account is inactive, locked or awaiting a password reset.";
    public static final String ENCRYPTION_FAILED = "Email was not sent. Its encrypted attachment could not be prepared.";
    public static final String UNCERTAIN = "Email delivery could not be confirmed. Do not resend until the mail provider's delivery record has been checked.";
    public static final String PUBLISH_PENDING = "Email was sent, but its password is not yet confirmed available in the patient portal. Retry password publication; do not resend the email.";
    public static final String REVOKED_NOT_SENT = "Email was not sent. Its portal password has been revoked.";
    /** Used when the caller supplied no localized notice; the reference line is always appended. */
    static final String DEFAULT_BODY_NOTICE = "An encrypted PDF from your clinic is attached. Sign in to your usual "
            + "patient portal and open Email passwords to find its password.";

    /** What recovery may do for a stored portal email. The recovery page renders from this. */
    public enum RecoveryView {
        /** A PENDING send may still be running in another request. */
        WAIT,
        /** The transport outcome is unknown; staff must check the provider and confirm it. */
        CONFIRM_OUTCOME,
        /** The email was accepted; only the password publication is outstanding. */
        RETRY_PUBLISH,
        /** The email definitely was not sent; only the password revocation is outstanding. */
        RETRY_REVOKE,
        /** The portal work is finished but the transport status is still PENDING. */
        UPDATE_RECORD,
        PUBLISHED,
        REVOKED,
        /** State and status contradict each other; no automatic action is safe. */
        INCONSISTENT
    }

    /** A recovery the service refused. {@link #messageKey()} names the localized explanation. */
    public static final class RecoveryRefusedException extends RuntimeException {
        private final String messageKey;
        private final boolean conflict;

        public RecoveryRefusedException(String messageKey, boolean conflict) {
            super(messageKey);
            this.messageKey = messageKey;
            this.conflict = conflict;
        }

        public String messageKey() { return messageKey; }

        /** True when the stored state changed or no longer matches, rather than a bad request. */
        public boolean isConflict() { return conflict; }
    }

    private static final String REFUSED_WAIT = "email.portalDelivery.error.stillSending";
    private static final String REFUSED_CONFIRM = "email.portalDelivery.error.confirmRequired";
    private static final String REFUSED_OPERATION = "email.portalDelivery.error.operationNotAvailable";
    private static final String REFUSED_ORIGIN = "email.portalDelivery.error.portalChanged";
    private static final String REFUSED_STATE_CHANGED = "email.portalDelivery.error.stateChanged";

    private final Logger logger = MiscUtils.getLogger();
    private final SecurityInfoManager security;
    private final EmailLogDaoImpl logs;
    private final Supplier<PatientPortalService> portal;
    private final Supplier<PatientPortalSettings> settings;

    @Autowired
    public PortalEmailDeliveryService(SecurityInfoManager security, EmailLogDaoImpl logs) {
        // The portal client is resolved per call: it is only configured when the portal is.
        this(security, logs, () -> SpringUtils.getBean(PatientPortalService.class),
                PatientPortalSettings::fromCarlosProperties);
    }

    PortalEmailDeliveryService(SecurityInfoManager security, EmailLogDaoImpl logs,
            Supplier<PatientPortalService> portal, Supplier<PatientPortalSettings> settings) {
        this.security = security;
        this.logs = logs;
        this.portal = portal;
        this.settings = settings;
    }

    /**
     * Whether encrypted email passwords go to the Portal. A malformed rollout setting must not
     * silently restore legacy password delivery, and neither may a Portal that cannot actually be
     * used: switched off, mistyped, or only partly configured. Every encrypted send would then fail
     * later, one at a time, with a generic "password could not be prepared". All of these are
     * reported as a configuration error, which the compose page and the send action turn into the
     * misconfiguration alert before a draft is used.
     *
     * @throws PatientPortalConfigurationException when the setting is not true/false, or it is
     *         true while the Portal connection settings do not build
     */
    public static boolean isEnabled() {
        return isEnabled(CarlosProperties.getInstance().getProperty(ENABLED_PROPERTY),
                PatientPortalSettings::fromCarlosProperties);
    }

    static boolean isEnabled(String raw, Supplier<PatientPortalSettings> portalSettings) {
        String value = raw == null ? "false" : raw.strip();
        if ("false".equals(value)) return false;
        if (!"true".equals(value)) {
            throw new PatientPortalConfigurationException("patient_portal.email.enabled must be true or false");
        }
        // Build the settings rather than only checking that some are present: a mistyped master
        // switch or a partial connection counts as present but can never send.
        try {
            portalSettings.get();
        } catch (PatientPortalConfigurationException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw new PatientPortalConfigurationException(
                    "patient_portal.email.enabled is true but the Patient Portal settings are not valid", invalid);
        }
        return true;
    }

    @FunctionalInterface
    public interface SendStep { void run() throws EmailSendingException; }

    /** A definite, pre-transport refusal with the outbox message staff should see. */
    private static final class NotSent extends IllegalStateException {
        NotSent(String message) { super(message); }
    }

    /**
     * Runs only after the existing consent gate accepts the send. The outbox row is already
     * committed; no generated password is ever persisted in CARLOS or passed to the mail sender.
     * An uncertain transport outcome may mean the provider accepted the email, so its
     * password remains pending until staff reconcile the provider's record.
     *
     * @throws SecurityException when the user may not send this email; the email was not sent
     */
    // FindSecBugs HARD_CODE_PASSWORD: empty values erase the generated password once it has been used.
    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD", justification = "Empty strings clear secrets; they are not authentication credentials")
    public EmailSendResult send(LoggedInInfo user, EmailLog log, EmailData data, SendStep encrypt, SendStep send) {
        PatientPortalStaffContext staff = null;
        PatientPortalService client = null;
        boolean accepted = false;
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Portal email cannot run inside a database transaction");
            }
            // Bulk lifecycle writes must not later be overwritten by a stale managed EmailLog flush.
            logs.detach(log);
            staff = authorize(user, log);
            validateRecipient(log, data);
            var configuration = settings.get();
            client = portal.get();
            int demographicNo = log.getDemographic().getDemographicNo();
            var account = client.findAccount(demographicNo, staff);
            if (!"active".equals(account.status()) || account.locked() || account.forcePasswordReset()
                    || account.demographicNo() != demographicNo
                    || !configuration.clinicId().equals(account.clinicId())) {
                throw new NotSent(ACCOUNT_NOT_READY);
            }
            String body = portalBody(data.getBody(), log.getId());
            log.setPortalSourceReference("email-" + UUID.randomUUID());
            log.setPortalOrigin(configuration.baseUrl());
            log.setPortalClinicId(configuration.clinicId());
            log.setBody(body);
            if (!logs.initializePortalDelivery(log)) throw new IllegalStateException("Email already prepared");
            log.setPortalDeliveryState(PortalDeliveryState.PREPARING);
            var secret = client.createUnlockSecret(demographicNo, log.getPortalSourceReference(), "Email " + log.getId(), staff);
            move(log, PortalDeliveryState.READY, secret.id());
            data.setPassword(secret.secret().expose());
            data.setPasswordClue("");
            try {
                encrypt.run();
            } catch (EmailSendingException | RuntimeException encryptionFailure) {
                // Report the step that failed, not the transport, which was never reached.
                var notSent = new NotSent(ENCRYPTION_FAILED);
                notSent.initCause(encryptionFailure);
                throw notSent;
            }
            data.setPassword("");
            data.setBody(body);
            move(log, PortalDeliveryState.SENDING, log.getPortalSecretId());
            send.run();
            accepted = true;
            try {
                move(log, PortalDeliveryState.SENT, log.getPortalSecretId());
            } catch (RecoveryRefusedException raced) {
                // Recovery decided while this send was still running and the provider has now
                // accepted it. Record that loudly: the stored state may contradict the delivery.
                logger.error("Portal email accepted after its portal state was changed by recovery; "
                        + "emailLogId={}; portalState={}", log.getId(), log.getPortalDeliveryState());
                audit(user, log, "PortalEmailDeliveryService.send.acceptedAfterRecovery");
                throw raced;
            }
            publish(client, staff, log);
            audit(user, log, "PortalEmailDeliveryService.send.published");
            return EmailSendResult.accepted(log, false);
        } catch (EmailSendingException | RuntimeException failure) {
            // Never expose transport/portal exception messages, which may contain credentials or PHI.
            if (accepted || log.getPortalDeliveryState() == PortalDeliveryState.SENT
                    || log.getPortalDeliveryState() == PortalDeliveryState.PUBLISHED) {
                logFailure("accepted by the mail provider but its portal password is not published", log, failure);
                audit(user, log, "PortalEmailDeliveryService.send.publishPending");
                log.setErrorMessage(PUBLISH_PENDING);
                return EmailSendResult.accepted(log, false, true);
            } else if (failure instanceof SecurityException denied) {
                // EmailSender raises this only from its privilege check, before the transport is
                // reached, so the email was not sent. Withdraw the password and let the caller see
                // the authorization failure, as a non-portal send does.
                logFailure("refused before transport", log, failure);
                cancelBeforeSend(client, staff, log);
                throw denied;
            } else if (log.getPortalDeliveryState() == PortalDeliveryState.SENDING
                    && !(failure instanceof EmailSendingException sendingFailure
                        && !sendingFailure.isDeliveryOutcomeUncertain())) {
                logFailure("transport outcome unknown", log, failure);
                audit(user, log, "PortalEmailDeliveryService.send.unconfirmed");
                log.setErrorMessage(UNCERTAIN);
                return EmailSendResult.unconfirmed(log);
            } else {
                logFailure("not sent", log, failure);
                cancelBeforeSend(client, staff, log);
                log.setErrorMessage(notSentMessage(failure));
                return EmailSendResult.failed(log, false);
            }
        } finally {
            data.setPassword("");
            data.setPasswordClue("");
        }
    }

    /** The persisted, staff-facing reason for a definite failure. Never the raw exception text. */
    private static String notSentMessage(Throwable failure) {
        if (failure instanceof NotSent notSent) {
            return notSent.getMessage();
        }
        if (failure instanceof EmailSendingException sendingFailure) {
            // sendWithArchive already replaced the transport's text with a sanitized category.
            return "Email was not sent. " + sendingFailure.getMessage();
        }
        return UNAVAILABLE;
    }

    private static String portalBody(String notice, Integer emailLogId) {
        String text = notice == null || notice.isBlank() ? DEFAULT_BODY_NOTICE : notice.strip();
        // The portal lists the password under this reference, so it is not translated.
        return text + "\n\nEmail " + emailLogId;
    }

    /**
     * Decides what recovery may do. A single classification drives both {@link #recover} and the
     * recovery page, so the page never offers an operation the service would refuse.
     */
    public static RecoveryView classify(EmailLog log) {
        if (mayStillBeSending(log)) {
            return RecoveryView.WAIT;
        }
        EmailStatus status = log.getStatus();
        PortalDeliveryState state = log.getPortalDeliveryState();
        if (state == null) {
            return RecoveryView.INCONSISTENT;
        }
        switch (state) {
            case PUBLISHED:
                // FAILED here means staff confirmed acceptance while a definite failure was landing.
                if (status == EmailStatus.FAILED) return RecoveryView.INCONSISTENT;
                return status == EmailStatus.PENDING ? RecoveryView.UPDATE_RECORD : RecoveryView.PUBLISHED;
            case REVOKED:
                // SUCCESS here means a slow send was accepted after staff confirmed it was not.
                if (status == EmailStatus.SUCCESS) return RecoveryView.INCONSISTENT;
                return status == EmailStatus.PENDING ? RecoveryView.UPDATE_RECORD : RecoveryView.REVOKED;
            case SENT:
                return status == EmailStatus.FAILED ? RecoveryView.INCONSISTENT : RecoveryView.RETRY_PUBLISH;
            case SENDING:
                // Status carries what the send request learned after it lost the state write:
                // SUCCESS means the provider accepted it, FAILED means it definitely did not.
                if (status == EmailStatus.SUCCESS) return RecoveryView.RETRY_PUBLISH;
                if (status == EmailStatus.FAILED) return RecoveryView.RETRY_REVOKE;
                return RecoveryView.CONFIRM_OUTCOME;
            default:
                // PREPARING, READY and REVOKE_PENDING never reached the transport.
                return status == EmailStatus.SUCCESS ? RecoveryView.INCONSISTENT : RecoveryView.RETRY_REVOKE;
        }
    }

    /**
     * Recovery never encrypts or sends email. IDs and patient scope come from the stored outbox.
     *
     * @param decision {@code retry}, or {@code confirmSent}/{@code confirmNotSent} for an
     *                 unknown transport outcome that staff checked with the provider
     * @throws RecoveryRefusedException when the stored state does not allow the decision
     */
    public EmailLog recover(LoggedInInfo user, int emailLogId, String decision, boolean confirmed) {
        var log = findForRecovery(user, emailLogId);
        var staff = authorize(user, log);
        var configuration = settings.get();
        if (!configuration.baseUrl().equals(log.getPortalOrigin())
                || !configuration.clinicId().equals(log.getPortalClinicId())) {
            throw new RecoveryRefusedException(REFUSED_ORIGIN, true);
        }
        var view = classify(log);
        boolean confirmation = "confirmSent".equals(decision) || "confirmNotSent".equals(decision);
        if (view == RecoveryView.WAIT) {
            throw new RecoveryRefusedException(REFUSED_WAIT, true);
        } else if (confirmation && view == RecoveryView.CONFIRM_OUTCOME && !confirmed) {
            throw new RecoveryRefusedException(REFUSED_CONFIRM, false);
        } else if (confirmation ? view != RecoveryView.CONFIRM_OUTCOME
                : !"retry".equals(decision) || (view != RecoveryView.RETRY_PUBLISH
                        && view != RecoveryView.RETRY_REVOKE && view != RecoveryView.UPDATE_RECORD)) {
            throw new RecoveryRefusedException(REFUSED_OPERATION, false);
        }
        var client = portal.get();
        try {
            if ("confirmSent".equals(decision)) {
                move(log, PortalDeliveryState.SENT, log.getPortalSecretId());
                publish(client, staff, log);
                // Staff asserted the outcome from the provider's record, as manual resolution does.
                status(log, EmailStatus.RESOLVED, "");
            } else if ("confirmNotSent".equals(decision) || view == RecoveryView.RETRY_REVOKE) {
                revoke(client, staff, log);
                status(log, EmailStatus.FAILED, REVOKED_NOT_SENT);
            } else if (view == RecoveryView.RETRY_PUBLISH) {
                if (log.getPortalDeliveryState() == PortalDeliveryState.SENDING) {
                    move(log, PortalDeliveryState.SENT, log.getPortalSecretId());
                }
                publish(client, staff, log);
                status(log, EmailStatus.SUCCESS, "");
                clearPublishPendingNote(log);
            } else if (log.getPortalDeliveryState() == PortalDeliveryState.PUBLISHED) {
                status(log, EmailStatus.SUCCESS, "");
            } else {
                status(log, EmailStatus.FAILED, REVOKED_NOT_SENT);
            }
        } catch (RuntimeException failure) {
            logFailure("recovery " + decision + " did not complete", log, failure);
            throw failure;
        }
        audit(user, log, "PortalEmailDeliveryService.recover." + decision);
        return log;
    }

    /**
     * True while a PENDING send is recent enough that the request which started it may still be
     * running. Recovery waits for the same window as manual resolution instead of racing that
     * request, for example by revoking the password it is about to encrypt with.
     */
    public static boolean mayStillBeSending(EmailLog log) {
        return EmailStatus.PENDING.equals(log.getStatus())
                && (log.getTimestamp() == null || log.getTimestamp().getTime()
                        > System.currentTimeMillis() - EmailManager.PENDING_RESOLUTION_MIN_AGE_MILLIS);
    }

    public EmailLog findForRecovery(LoggedInInfo user, int emailLogId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Portal email recovery cannot run inside a database transaction");
        }
        var log = logs.find(emailLogId);
        if (log == null || log.getPortalDeliveryState() == null) {
            throw new IllegalArgumentException("Portal email not found");
        }
        authorize(user, log);
        logs.detach(log);
        return log;
    }

    private PatientPortalStaffContext authorize(LoggedInInfo user, EmailLog log) {
        Integer demographicNo = log.getDemographic() == null ? null : log.getDemographic().getDemographicNo();
        if (demographicNo == null || demographicNo <= 0) {
            throw new IllegalArgumentException("A patient must be selected");
        }
        if (user == null || !security.isAllowedAccessToPatientRecord(user, demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        String patient = String.valueOf(demographicNo);
        require(user, "_email", SecurityInfoManager.WRITE, patient);
        require(user, "_demographic", SecurityInfoManager.READ, patient);
        require(user, PortalStaffContextResolver.OBJECT_ACCOUNT, SecurityInfoManager.READ, patient);
        require(user, PortalStaffContextResolver.OBJECT_SECRET, SecurityInfoManager.READ, patient);
        require(user, PortalStaffContextResolver.OBJECT_SECRET, SecurityInfoManager.WRITE, patient);
        return new PortalStaffContextResolver(security).resolveForPatient(user,
                Set.of(PortalStaffContextResolver.OBJECT_ACCOUNT, PortalStaffContextResolver.OBJECT_SECRET),
                demographicNo);
    }

    private void require(LoggedInInfo user, String object, String privilege, String patient) {
        if (!security.hasPrivilege(user, object, privilege, patient)) {
            throw new SecurityException("missing required sec object (" + object + ")");
        }
    }

    private void validateRecipient(EmailLog log, EmailData data) {
        String recorded = log.getDemographic().getEmail();
        String[] recipients = data.getRecipients();
        if (recipients == null || recipients.length != 1 || recorded == null
                || data.getDemographicNo() == null
                || !data.getDemographicNo().equals(log.getDemographic().getDemographicNo())
                || !Arrays.asList(recorded.split("[,;\\s()]+")).contains(recipients[0])
                || !EmailValidator.getInstance().isValid(recipients[0])) {
            throw new NotSent(RECIPIENT_NOT_RECORDED);
        }
    }

    private void publish(PatientPortalService client, PatientPortalStaffContext staff, EmailLog log) {
        client.publishUnlockSecret(log.getPortalSecretId(), staff);
        move(log, PortalDeliveryState.PUBLISHED, log.getPortalSecretId());
    }

    private void cancelBeforeSend(PatientPortalService client, PatientPortalStaffContext staff, EmailLog log) {
        if (client == null || log.getPortalDeliveryState() == null) return;
        try {
            revoke(client, staff, log);
        } catch (RuntimeException revokeFailure) {
            // The original failure is what the caller must see. The unpublished password stays
            // invisible to the patient, and the durable state keeps the revocation recoverable.
            logFailure("not sent, and its unpublished portal password could not be revoked yet", log, revokeFailure);
        }
    }

    private void revoke(PatientPortalService client, PatientPortalStaffContext staff, EmailLog log) {
        if (log.getPortalDeliveryState() != PortalDeliveryState.REVOKE_PENDING) {
            move(log, PortalDeliveryState.REVOKE_PENDING, log.getPortalSecretId());
        }
        Long id = log.getPortalSecretId();
        if (id == null) {
            // A create response may have been lost. The stored reference retrieves the same pending password.
            id = client.createUnlockSecret(log.getDemographic().getDemographicNo(),
                    log.getPortalSourceReference(), "Email " + log.getId(), staff).id();
            move(log, PortalDeliveryState.REVOKE_PENDING, id);
        }
        client.revokeUnlockSecret(id, "email_not_sent", staff);
        move(log, PortalDeliveryState.REVOKED, id);
    }

    private void move(EmailLog log, PortalDeliveryState next, Long secretId) {
        if (!logs.transitionPortalDelivery(log, log.getPortalDeliveryState(), next, secretId)) {
            throw new RecoveryRefusedException(REFUSED_STATE_CHANGED, true);
        }
        log.setPortalDeliveryState(next);
        log.setPortalSecretId(secretId);
    }

    private void status(EmailLog log, EmailStatus state, String message) {
        Date now = new Date();
        if (logs.transitionEmailStatus(log.getId(), EmailStatus.PENDING, state, message, now) == 1) {
            log.setStatus(state);
            log.setErrorMessage(message);
            log.setTimestamp(now);
        } else if (log.getStatus() != state) {
            // A terminal transport status (including a concurrent manual resolution) stays intact;
            // portal completion remains visible through portalDeliveryState.
            logger.warn("Portal email transport status left unchanged; emailLogId={}; status={}; wanted={}; portalState={}",
                    log.getId(), log.getStatus(), state, log.getPortalDeliveryState());
        }
    }

    /** The accepted send stored PUBLISH_PENDING with SUCCESS; drop it once the password is out. */
    private void clearPublishPendingNote(EmailLog log) {
        if (log.getStatus() == EmailStatus.SUCCESS && PUBLISH_PENDING.equals(log.getErrorMessage())
                && logs.transitionEmailStatus(log.getId(), EmailStatus.SUCCESS, EmailStatus.SUCCESS, "",
                        log.getTimestamp()) == 1) {
            log.setErrorMessage("");
        }
    }

    private void logFailure(String outcome, EmailLog log, Throwable failure) {
        // Class names only: portal and transport messages may carry credentials or PHI.
        Throwable cause = failure.getCause();
        logger.warn("Portal email {}; emailLogId={}; portalState={}; portalSecretId={}; causeType={}; rootType={}",
                outcome, log.getId(), log.getPortalDeliveryState(), log.getPortalSecretId(),
                failure.getClass().getSimpleName(), cause == null ? "none" : cause.getClass().getSimpleName());
    }

    private void audit(LoggedInInfo user, EmailLog log, String event) {
        try {
            String demographicNo = log.getDemographic() == null ? ""
                    : String.valueOf(log.getDemographic().getDemographicNo());
            LogAction.addLog(user, event, "Email", String.valueOf(log.getId()), demographicNo,
                    "portalState=" + log.getPortalDeliveryState() + "&portalSecretId=" + log.getPortalSecretId());
        } catch (RuntimeException auditFailure) {
            // Auditing is best-effort here: the lifecycle state is already durable.
            logger.warn("Portal email audit entry was not written; emailLogId={}; causeType={}",
                    log.getId(), auditFailure.getClass().getSimpleName());
        }
    }
}
