/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.PortalDeliveryState;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Coordinates the irreversible email send with the recoverable portal publication. */
@Service
public class PortalEmailDelivery {
    public static final String ENABLED_PROPERTY = "patient_portal.email.enabled";
    public static final String UNAVAILABLE = "Email was not sent. The patient portal password could not be prepared.";
    public static final String UNCERTAIN = "Email delivery could not be confirmed. Do not resend until the mail provider's delivery record has been checked.";
    public static final String PUBLISH_PENDING = "Email was sent, but its password is not yet confirmed available in the patient portal. Retry password publication; do not resend the email.";
    private final SecurityInfoManager security;
    private final EmailLogDaoImpl logs;
    private final Supplier<PatientPortalService> portal;
    private final Supplier<PatientPortalSettings> settings;

    @Autowired
    public PortalEmailDelivery(SecurityInfoManager security, EmailLogDaoImpl logs) {
        this(security, logs, () -> SpringUtils.getBean(PatientPortalService.class),
                PatientPortalSettings::fromCarlosProperties);
    }

    PortalEmailDelivery(SecurityInfoManager security, EmailLogDaoImpl logs,
            Supplier<PatientPortalService> portal, Supplier<PatientPortalSettings> settings) {
        this.security = security;
        this.logs = logs;
        this.portal = portal;
        this.settings = settings;
    }

    /** A malformed rollout setting must not silently restore legacy password delivery. */
    public static boolean isEnabled() {
        String raw = CarlosProperties.getInstance().getProperty(ENABLED_PROPERTY);
        String value = raw == null ? "false" : raw.strip();
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new PatientPortalConfigurationException("patient_portal.email.enabled must be true or false");
    }

    @FunctionalInterface
    public interface SendStep { void run() throws EmailSendingException; }

    /**
     * Runs only after the existing consent gate accepts the send. The outbox row is already
     * committed; no generated password is ever persisted in CARLOS or passed to the mail sender.
     * A transport exception is ambiguous (the provider may have accepted the email), so its
     * password remains pending until staff reconcile the provider's record.
     */
    public EmailSendResult send(LoggedInInfo user, EmailLog log, EmailData data, SendStep encrypt, SendStep send) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Portal email cannot run inside a database transaction");
        }
        // Bulk lifecycle writes must not later be overwritten by a stale managed EmailLog flush.
        logs.detach(log);
        PatientPortalStaffContext staff = authorize(user, log);
        PatientPortalService client = null;
        boolean accepted = false;
        try {
            validateRecipient(log, data);
            var configuration = settings.get();
            client = portal.get();
            var account = client.findAccount(log.getDemographic().getDemographicNo(), staff);
            if (!"active".equals(account.status()) || account.locked() || account.forcePasswordReset()
                    || account.demographicNo() != log.getDemographic().getDemographicNo()
                    || !configuration.clinicId().equals(account.clinicId())) {
                throw new IllegalStateException("Patient portal account is not ready");
            }
            log.setPortalSourceReference("email-" + UUID.randomUUID());
            log.setPortalOrigin(configuration.baseUrl());
            log.setPortalClinicId(configuration.clinicId());
            if (!logs.initializePortalDelivery(log)) throw new IllegalStateException("Email already prepared");
            log.setPortalDeliveryState(PortalDeliveryState.PREPARING);
            var secret = client.createUnlockSecret(log.getDemographic().getDemographicNo(),
                    log.getPortalSourceReference(), "Email " + log.getId(), staff);
            move(log, PortalDeliveryState.READY, secret.id());
            data.setPassword(secret.secret().expose());
            data.setPasswordClue("");
            encrypt.run();
            data.setPassword("");
            data.setBody("An encrypted PDF from your clinic is attached. Sign in to your usual patient portal "
                    + "and open Email passwords. Password reference: Email " + log.getId() + ".");
            move(log, PortalDeliveryState.SENDING, log.getPortalSecretId());
            send.run();
            accepted = true;
            move(log, PortalDeliveryState.SENT, log.getPortalSecretId());
            publish(client, staff, log);
            return EmailSendResult.accepted(log, false);
        } catch (EmailSendingException | RuntimeException failure) {
            // Never expose transport/portal exception messages, which may contain credentials or PHI.
            if (accepted || log.getPortalDeliveryState() == PortalDeliveryState.SENT
                    || log.getPortalDeliveryState() == PortalDeliveryState.PUBLISHED) {
                log.setErrorMessage(PUBLISH_PENDING);
                return EmailSendResult.accepted(log, false, true);
            } else if (log.getPortalDeliveryState() == PortalDeliveryState.SENDING
                    && !(failure instanceof EmailSendingException sendingFailure
                        && !sendingFailure.isDeliveryOutcomeUncertain())) {
                log.setErrorMessage(UNCERTAIN);
                return EmailSendResult.unconfirmed(log);
            } else {
                cancelBeforeSend(client, staff, log);
                log.setErrorMessage(UNAVAILABLE);
                return EmailSendResult.failed(log, false);
            }
        } finally {
            data.setPassword("");
            data.setPasswordClue("");
        }
    }

    /** Recovery never encrypts or sends email. IDs and patient scope come from the stored outbox. */
    public EmailLog recover(LoggedInInfo user, int emailLogId, String decision, boolean confirmed) {
        var log = findForRecovery(user, emailLogId);
        var staff = authorize(user, log);
        var configuration = settings.get();
        if (!configuration.baseUrl().equals(log.getPortalOrigin())
                || !configuration.clinicId().equals(log.getPortalClinicId())) {
            throw new IllegalStateException("Restore the original portal connection before recovering this email");
        }
        var client = portal.get();
        var state = log.getPortalDeliveryState();
        if ("confirmSent".equals(decision) || "confirmNotSent".equals(decision)) {
            if (!confirmed || state != PortalDeliveryState.SENDING
                    || log.getTimestamp() == null
                    || log.getTimestamp().getTime() > System.currentTimeMillis() - 15L * 60L * 1000L) {
                throw new IllegalArgumentException("Check the mail provider delivery record and explicitly confirm its outcome");
            }
            move(log, "confirmSent".equals(decision) ? PortalDeliveryState.SENT
                    : PortalDeliveryState.REVOKE_PENDING, log.getPortalSecretId());
        } else if (!"retry".equals(decision)) {
            throw new IllegalArgumentException("Unsupported portal recovery operation");
        }
        io.github.carlos_emr.carlos.log.LogAction.addLog(user, "PortalEmailDelivery.recover", "Email",
                "emailLogId=" + log.getId() + "&operation=" + decision, "", "");
        state = log.getPortalDeliveryState();
        if (state == PortalDeliveryState.SENT) {
            publish(client, staff, log);
            status(log, EmailStatus.SUCCESS, "");
        } else if (state == PortalDeliveryState.PREPARING || state == PortalDeliveryState.READY
                || state == PortalDeliveryState.REVOKE_PENDING) {
            revoke(client, staff, log);
            status(log, EmailStatus.FAILED, "Email was not sent. Its portal password has been revoked.");
        } else if (state == PortalDeliveryState.PUBLISHED) {
            status(log, EmailStatus.SUCCESS, "");
        } else if (state == PortalDeliveryState.REVOKED) {
            status(log, EmailStatus.FAILED, "Email was not sent. Its portal password has been revoked.");
        } else {
            throw new IllegalStateException(UNCERTAIN);
        }
        return log;
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
        if (log.getDemographic() == null || log.getDemographic().getDemographicNo() <= 0) {
            throw new IllegalArgumentException("A patient must be selected");
        }
        if (user == null || !security.isAllowedAccessToPatientRecord(user, log.getDemographic().getDemographicNo())) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        String patient = String.valueOf(log.getDemographic().getDemographicNo());
        require(user, "_email", SecurityInfoManager.WRITE, patient);
        require(user, "_demographic", SecurityInfoManager.READ, patient);
        require(user, PortalStaffContextResolver.OBJECT_ACCOUNT, SecurityInfoManager.READ, patient);
        require(user, PortalStaffContextResolver.OBJECT_SECRET, SecurityInfoManager.READ, patient);
        require(user, PortalStaffContextResolver.OBJECT_SECRET, SecurityInfoManager.WRITE, patient);
        return new PortalStaffContextResolver(security).resolveForPatient(user,
                Set.of(PortalStaffContextResolver.OBJECT_ACCOUNT, PortalStaffContextResolver.OBJECT_SECRET),
                log.getDemographic().getDemographicNo());
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
                || !org.apache.commons.validator.routines.EmailValidator.getInstance().isValid(recipients[0])) {
            throw new IllegalArgumentException("Select one email address recorded for this patient");
        }
    }

    private void publish(PatientPortalService client, PatientPortalStaffContext staff, EmailLog log) {
        client.publishUnlockSecret(log.getPortalSecretId(), staff);
        move(log, PortalDeliveryState.PUBLISHED, log.getPortalSecretId());
    }

    private void cancelBeforeSend(PatientPortalService client, PatientPortalStaffContext staff, EmailLog log) {
        if (client == null || log.getPortalDeliveryState() == null) return;
        try { revoke(client, staff, log); }
        catch (RuntimeException ignored) { /* Durable intent remains recoverable; never log secret material. */ }
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
            throw new IllegalStateException("Email state changed; reload before recovery");
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
        }
        // A terminal transport status (including a concurrent manual resolution) stays intact.
        // Portal completion remains independently visible through portalDeliveryState.
    }
}
