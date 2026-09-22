/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDao;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientPortalInviteDeliveryDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Channel;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.State;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException.Reason;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.Logger;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Delivers patient portal invitations through the portal's two-phase contract.
 *
 * <p>The portal prepares an inactive token, CARLOS stores the email that carries it, the portal
 * activates the token only when CARLOS commits delivery, and only then does the email leave. Each
 * step is recorded on a {@link PatientPortalInviteDelivery} row, committed before the next network
 * call, so an interruption at any point leaves a state that says what happened:
 *
 * <pre>
 * PREPARING -> PREPARED -> QUEUED -> COMMITTED -> SENT
 *                                              -> SEND_FAILED | SEND_UNCERTAIN
 * any step before COMMITTED                    -> ABANDONED (the prepared token is revoked)
 * </pre>
 *
 * <p>The ordering is the point. Committing before the email is durable could activate a token that
 * nothing will ever deliver; sending before committing would deliver a token that cannot activate an
 * account. The commit therefore runs inside {@link EmailManager.DispatchGate}, after the email row
 * exists and before any transport work, and a failed commit stops the send.
 *
 * <p>The invite code is never stored by CARLOS outside the email itself. A lost prepare response is
 * recovered by retrying with the same operation id, which the portal answers with the same token.
 *
 * <p>Recovery is by staff, as for every other CARLOS email: a stuck attempt is shown as incomplete and
 * can be resolved after {@link #RECOVERY_MIN_AGE}. Nothing here runs in the background.
 *
 * @since 2026-09-22
 */
public class PortalInviteDeliveryService {

    /** How long an attempt must be idle before staff may resolve it, matching stuck-email resolution. */
    public static final Duration RECOVERY_MIN_AGE = Duration.ofMinutes(15);

    /** How many recent attempts the panel shows per patient. */
    public static final int RECENT_LIMIT = 10;

    static final String OPERATION_PREFIX = "inv-";
    static final String REFERENCE_PREFIX = "emaillog:";
    private static final int MIN_HEALTH_CARD_LENGTH = 4;
    private static final String STATUS_PENDING = "pending";

    static final String SUBJECT = "Your patient portal invitation";
    static final String PREPARE_FAILED = "The portal did not prepare the invitation. Nothing was sent.";
    static final String COMMIT_REFUSED = "The portal refused to activate the invitation. Nothing was sent.";
    static final String COMMIT_UNKNOWN =
            "The portal did not confirm activating the invitation. Nothing was sent.";
    static final String NOT_SENT_BEFORE_COMMIT = "The invitation email was not sent: the portal did not activate it.";
    static final String CONSENT_OR_SETUP_BLOCKED = "The invitation email could not be sent. Nothing was delivered.";
    static final String SEND_REFUSED = "The mail server refused the invitation email. Resend to issue a new code.";
    static final String SEND_UNKNOWN =
            "The mail server did not confirm sending the invitation email. Check whether it arrived.";
    static final String REVOKE_FAILED =
            "The unused invitation could not be withdrawn on the portal; it expires on its own.";
    static final String CONFIRMED_SENT = "Staff confirmed the invitation email arrived.";
    static final String CONFIRMED_NOT_SENT = "Staff confirmed the invitation email did not arrive; it was revoked.";
    static final String ABANDONED_BY_STAFF = "Staff abandoned this delivery before the invitation was activated.";

    private static final Logger logger = MiscUtils.getLogger();

    /** A staff decision on an attempt that did not finish. */
    public enum Decision {
        /** Stop an attempt that never activated its token; withdraw the prepared token on the portal. */
        ABANDON("abandon"),
        /** The patient received the email. */
        CONFIRM_SENT("confirmSent"),
        /** The email did not arrive; revoke the live token so a new invitation can be issued. */
        CONFIRM_NOT_SENT("confirmNotSent");

        private final String requestValue;

        Decision(String requestValue) {
            this.requestValue = requestValue;
        }

        /** @return the {@code decision} request value that selects this decision */
        public String requestValue() {
            return requestValue;
        }

        /** Parses the {@code decision} request value, or returns {@code null} when it is not a decision. */
        public static Decision parse(String value) {
            for (Decision decision : values()) {
                if (decision.requestValue.equals(value)) {
                    return decision;
                }
            }
            return null;
        }
    }

    /**
     * What staff asked for.
     *
     * @param channel how to deliver the invitation
     * @param confirmReplace whether an existing pending invitation may be replaced
     * @param consentOverride whether staff documented consent that the chart records as unknown
     * @param consentOverrideReason the documented reason, required with {@code consentOverride}
     */
    public record InviteRequest(
            Channel channel, boolean confirmReplace, boolean consentOverride, String consentOverrideReason) {
    }

    private final PatientPortalService portal;
    private final PatientPortalSettings portalSettings;
    private final PortalInviteSettings inviteSettings;
    private final EmailManager emailManager;
    private final PatientPortalInviteDeliveryDao deliveries;
    private final EmailConfigDao emailConfigs;
    private final EmailLogDao emailLogs;
    private final Clock clock;

    public PortalInviteDeliveryService(
            PatientPortalService portal,
            PatientPortalSettings portalSettings,
            PortalInviteSettings inviteSettings,
            EmailManager emailManager,
            PatientPortalInviteDeliveryDao deliveries,
            EmailConfigDao emailConfigs,
            EmailLogDao emailLogs) {
        this(portal, portalSettings, inviteSettings, emailManager, deliveries, emailConfigs, emailLogs,
                Clock.systemUTC());
    }

    PortalInviteDeliveryService(
            PatientPortalService portal,
            PatientPortalSettings portalSettings,
            PortalInviteSettings inviteSettings,
            EmailManager emailManager,
            PatientPortalInviteDeliveryDao deliveries,
            EmailConfigDao emailConfigs,
            EmailLogDao emailLogs,
            Clock clock) {
        this.portal = portal;
        this.portalSettings = portalSettings;
        this.inviteSettings = inviteSettings;
        this.emailManager = emailManager;
        this.deliveries = deliveries;
        this.emailConfigs = emailConfigs;
        this.emailLogs = emailLogs;
        this.clock = clock;
    }

    /**
     * Invites a patient. When a pending invitation exists, {@code confirmReplace} turns this into a
     * resend of it; without confirmation the request is refused, because replacing an invitation
     * silently would strand a code the patient may already hold.
     *
     * @return the attempt as recorded; its state says how far delivery got
     * @throws PortalInviteException when the invitation is refused before any portal call
     * @throws PatientPortalException when the portal refuses or cannot be reached
     */
    public PatientPortalInviteDelivery invite(LoggedInInfo user, Demographic patient,
            PatientPortalStaffContext staff, InviteRequest request) {
        requireNoTransaction();
        requireEmailChannel(request);
        Contact contact = contactFor(patient);
        EmailData email = emailFor(user, patient.getDemographicNo(), contact.email(), request);
        requireConsent(user, email);
        Optional<PatientPortalInviteDto> pending = pendingInvite(patient.getDemographicNo(), staff);
        if (pending.isPresent()) {
            if (!request.confirmReplace()) {
                throw new PortalInviteException(Reason.PENDING_INVITE_EXISTS);
            }
            return deliver(user, patient.getDemographicNo(), contact, staff, email, pending.get().id());
        }
        return deliver(user, patient.getDemographicNo(), contact, staff, email, null);
    }

    /**
     * Replaces a pending invitation with a new code. The old code keeps working until the new email is
     * durable and the portal commits the replacement.
     */
    public PatientPortalInviteDelivery resend(LoggedInInfo user, Demographic patient, long inviteId,
            PatientPortalStaffContext staff, InviteRequest request) {
        requireNoTransaction();
        requireEmailChannel(request);
        Contact contact = contactFor(patient);
        EmailData email = emailFor(user, patient.getDemographicNo(), contact.email(), request);
        requireConsent(user, email);
        boolean pending = portal.listInvites(patient.getDemographicNo(), staff).stream()
                .anyMatch(invite -> invite.id() == inviteId && STATUS_PENDING.equals(invite.status()));
        if (!pending) {
            throw new PortalInviteException(Reason.INVITE_NOT_PENDING);
        }
        return deliver(user, patient.getDemographicNo(), contact, staff, email, inviteId);
    }

    /**
     * Resolves an attempt that did not finish. Re-authorisation comes from the stored row: the patient
     * and the portal connection must match what the attempt was started with.
     */
    public PatientPortalInviteDelivery recover(LoggedInInfo user, Demographic patient, long deliveryId,
            Decision decision, PatientPortalStaffContext staff) {
        requireNoTransaction();
        PatientPortalInviteDelivery row = deliveries.find(deliveryId);
        if (row == null || row.getDemographicNo() == null
                || row.getDemographicNo().intValue() != patient.getDemographicNo()) {
            throw new PortalInviteException(Reason.DELIVERY_NOT_FOUND);
        }
        if (!portalSettings.baseUrl().equals(row.getPortalOrigin())
                || !portalSettings.clinicId().equals(row.getClinicId())) {
            throw new PortalInviteException(Reason.PORTAL_CONNECTION_CHANGED);
        }
        if (!decisionsFor(row.getState()).contains(decision)) {
            throw new PortalInviteException(Reason.RECOVERY_NOT_ALLOWED);
        }
        if (!isRecoverable(row)) {
            throw new PortalInviteException(Reason.RECOVERY_TOO_EARLY);
        }
        return switch (decision) {
            case ABANDON -> abandonByStaff(row, patient, staff);
            case CONFIRM_SENT -> confirm(row, State.SENT, CONFIRMED_SENT, EmailStatus.RESOLVED);
            case CONFIRM_NOT_SENT -> confirmNotSent(row, staff);
        };
    }

    /** @return the patient's recent attempts, newest first; read from CARLOS, so available offline */
    public List<PatientPortalInviteDelivery> recentFor(int demographicNo) {
        List<PatientPortalInviteDelivery> rows = deliveries.findRecentByDemographic(demographicNo, RECENT_LIMIT);
        return rows == null ? Collections.emptyList() : rows;
    }

    /** @return whether staff may resolve the attempt now: unfinished and idle for {@link #RECOVERY_MIN_AGE} */
    public boolean isRecoverable(PatientPortalInviteDelivery row) {
        if (row.getState().isTerminal()) {
            return false;
        }
        Date updated = row.getUpdatedAt();
        return updated != null
                && !updated.toInstant().plus(RECOVERY_MIN_AGE).isAfter(clock.instant());
    }

    /** @return the decisions that apply to the attempt's current state */
    public static List<Decision> decisionsFor(State state) {
        return switch (state) {
            case PREPARING, PREPARED, QUEUED -> List.of(Decision.ABANDON);
            case COMMITTED, SEND_UNCERTAIN -> List.of(Decision.CONFIRM_SENT, Decision.CONFIRM_NOT_SENT);
            default -> List.of();
        };
    }

    // --- delivery --------------------------------------------------------------------------------

    private PatientPortalInviteDelivery deliver(LoggedInInfo user, int demographicNo, Contact contact,
            PatientPortalStaffContext staff, EmailData email, Long supersededInviteId) {
        String operationId = OPERATION_PREFIX + UUID.randomUUID();
        PatientPortalInviteDelivery row = deliveries.claim(new PatientPortalInviteDelivery(
                operationId, demographicNo, portalSettings.clinicId(), portalSettings.baseUrl(),
                Channel.EMAIL, supersededInviteId, user.getLoggedInProviderNo()));

        PatientPortalIssuedInviteDto issued;
        try {
            issued = prepare(demographicNo, contact, supersededInviteId, operationId, staff).issuedInvite();
        } catch (PatientPortalException exception) {
            // The portal may have prepared a token before the failure. Nothing stops a patient from being
            // invited later except a live preparation, so try to withdraw one before giving up.
            abandon(row.getId(), State.PREPARING, PREPARE_FAILED, null, staff, demographicNo);
            throw exception;
        }
        long inviteId = issued.invite().id();
        row = advance(row.getId(), State.PREPARING, State.PREPARED, r -> r.setPortalInviteId(inviteId));

        email.setBody(body(issued.inviteToken().expose()));
        CommitGate gate = new CommitGate(row.getId(), inviteId, operationId, staff);
        EmailSendResult result;
        try {
            result = emailManager.sendEmailWithResult(user, email, gate);
        } catch (RuntimeException exception) {
            settleAfterFailure(row.getId(), inviteId, staff, demographicNo);
            throw exception;
        } finally {
            email.setBody("");
        }
        return settle(row.getId(), result, inviteId, staff, demographicNo);
    }

    private PatientPortalPreparedInviteDto prepare(int demographicNo, Contact contact, Long supersededInviteId,
            String operationId, PatientPortalStaffContext staff) {
        try {
            return prepareOnce(demographicNo, contact, supersededInviteId, operationId, staff);
        } catch (PatientPortalException exception) {
            if (!outcomeUnknown(exception)) {
                throw exception;
            }
            // The request may have reached the portal. The same operation id returns the same token, so
            // one retry recovers a lost response without preparing a second invitation.
            return prepareOnce(demographicNo, contact, supersededInviteId, operationId, staff);
        }
    }

    private PatientPortalPreparedInviteDto prepareOnce(int demographicNo, Contact contact, Long supersededInviteId,
            String operationId, PatientPortalStaffContext staff) {
        if (supersededInviteId != null) {
            return portal.prepareInviteResend(supersededInviteId, operationId, staff);
        }
        return portal.prepareInvite(demographicNo, contact.email(), contact.dateOfBirth(),
                contact.healthCardNumber(), operationId, staff);
    }

    /**
     * Commits delivery on the portal between the durable email write and the send. Any failure stops the
     * send; the row records whether the portal may still have committed.
     */
    private final class CommitGate implements EmailManager.DispatchGate {

        private final Long deliveryId;
        private final long inviteId;
        private final String operationId;
        private final PatientPortalStaffContext staff;

        private CommitGate(Long deliveryId, long inviteId, String operationId, PatientPortalStaffContext staff) {
            this.deliveryId = deliveryId;
            this.inviteId = inviteId;
            this.operationId = operationId;
            this.staff = staff;
        }

        @Override
        public void beforeDispatch(EmailLog emailLog) throws EmailSendingException {
            Integer emailLogId = emailLog.getId();
            try {
                if (deliveries.advance(deliveryId, State.PREPARED, State.QUEUED,
                        r -> r.setEmailLogId(emailLogId)) == null) {
                    throw new EmailSendingException(NOT_SENT_BEFORE_COMMIT);
                }
                PatientPortalInviteDto committed;
                try {
                    committed = portal.commitInviteDelivery(
                            inviteId, operationId, REFERENCE_PREFIX + emailLogId, staff);
                } catch (PatientPortalException exception) {
                    // Commit retries are idempotent, but a retry here would hold the send open with no
                    // bound. Record whether the portal may have committed and stop.
                    String message = outcomeUnknown(exception) ? COMMIT_UNKNOWN : COMMIT_REFUSED;
                    deliveries.advance(deliveryId, State.QUEUED, State.QUEUED, r -> r.setErrorMessage(message));
                    throw new EmailSendingException(NOT_SENT_BEFORE_COMMIT);
                }
                Date expiresAt = committed.expiresAt() == null ? null : Date.from(committed.expiresAt());
                if (deliveries.advance(deliveryId, State.QUEUED, State.COMMITTED, r -> {
                    r.setExpiresAt(expiresAt);
                    r.setErrorMessage(null);
                }) == null) {
                    throw new EmailSendingException(NOT_SENT_BEFORE_COMMIT);
                }
            } catch (RuntimeException exception) {
                // EmailManager treats only EmailSendingException from the gate as "not sent". Anything else
                // would escape with the outbox row still pending, so it is converted here.
                logger.warn("patient portal invite commit gate failed: {}", exception.getClass().getSimpleName());
                throw new EmailSendingException(NOT_SENT_BEFORE_COMMIT);
            }
        }
    }

    private PatientPortalInviteDelivery settle(Long deliveryId, EmailSendResult result, long inviteId,
            PatientPortalStaffContext staff, int demographicNo) {
        PatientPortalInviteDelivery row = deliveries.find(deliveryId);
        return switch (row.getState()) {
            case COMMITTED -> {
                if (result.isTransportAccepted()) {
                    yield advance(deliveryId, State.COMMITTED, State.SENT, r -> r.setErrorMessage(null));
                }
                if (result.isDeliveryUnconfirmed()) {
                    yield advance(deliveryId, State.COMMITTED, State.SEND_UNCERTAIN, r -> r.setErrorMessage(SEND_UNKNOWN));
                }
                yield advance(deliveryId, State.COMMITTED, State.SEND_FAILED, r -> r.setErrorMessage(SEND_REFUSED));
            }
            // The gate never ran: consent blocked the email or the sender could not be used.
            case PREPARED -> abandon(deliveryId, State.PREPARED, CONSENT_OR_SETUP_BLOCKED, inviteId, staff, demographicNo);
            // The gate stopped before or at the commit. Withdraw the token; nothing reached the patient.
            case QUEUED -> abandon(deliveryId, State.QUEUED, row.getErrorMessage() == null
                    ? COMMIT_REFUSED : row.getErrorMessage(), inviteId, staff, demographicNo);
            default -> row;
        };
    }

    private void settleAfterFailure(Long deliveryId, long inviteId, PatientPortalStaffContext staff,
            int demographicNo) {
        PatientPortalInviteDelivery row = deliveries.find(deliveryId);
        if (row == null) {
            return;
        }
        if (row.getState() == State.PREPARED || row.getState() == State.QUEUED) {
            abandon(deliveryId, row.getState(), CONSENT_OR_SETUP_BLOCKED, inviteId, staff, demographicNo);
        } else if (row.getState() == State.COMMITTED) {
            // The token is live and the send's fate is unknown. Never revoke on uncertainty.
            deliveries.advance(deliveryId, State.COMMITTED, State.SEND_UNCERTAIN, r -> r.setErrorMessage(SEND_UNKNOWN));
        }
    }

    // --- recovery --------------------------------------------------------------------------------

    private PatientPortalInviteDelivery abandonByStaff(PatientPortalInviteDelivery row, Demographic patient,
            PatientPortalStaffContext staff) {
        Long inviteId = row.getPortalInviteId();
        if (inviteId == null && row.getState() == State.PREPARING) {
            // The prepare response was lost. Asking again with the same operation id names the invitation
            // so it can be withdrawn; an answer that it no longer exists means there is nothing to withdraw.
            try {
                Contact contact = row.getSupersededInviteId() == null ? contactFor(patient) : null;
                inviteId = prepareOnce(row.getDemographicNo(), contact, row.getSupersededInviteId(),
                        row.getDeliveryOperationId(), staff).issuedInvite().invite().id();
            } catch (PatientPortalException | PortalInviteException exception) {
                inviteId = null;
            }
        }
        PatientPortalInviteDelivery abandoned =
                abandon(row.getId(), row.getState(), ABANDONED_BY_STAFF, inviteId, staff, row.getDemographicNo());
        if (abandoned.getEmailLogId() != null) {
            // In these states the send never started, so the email row can be closed as not sent.
            emailLogs.transitionEmailStatus(abandoned.getEmailLogId(), EmailStatus.PENDING, EmailStatus.FAILED,
                    ABANDONED_BY_STAFF, Date.from(clock.instant()));
        }
        return abandoned;
    }

    private PatientPortalInviteDelivery confirmNotSent(PatientPortalInviteDelivery row, PatientPortalStaffContext staff) {
        // Revoke first: if the portal refuses, the row keeps its state and staff can try again.
        portal.revokeInvite(row.getDemographicNo(), row.getPortalInviteId(), staff);
        return confirm(row, State.REVOKED, CONFIRMED_NOT_SENT, EmailStatus.RESOLVED);
    }

    private PatientPortalInviteDelivery confirm(PatientPortalInviteDelivery row, State next, String message,
            EmailStatus emailStatus) {
        PatientPortalInviteDelivery updated = advance(row.getId(), row.getState(), next, r -> r.setErrorMessage(message));
        if (updated.getEmailLogId() != null) {
            emailLogs.transitionEmailStatus(updated.getEmailLogId(), EmailStatus.PENDING, emailStatus, message,
                    Date.from(clock.instant()));
        }
        return updated;
    }

    // --- shared ----------------------------------------------------------------------------------

    /**
     * Marks an attempt abandoned and withdraws its prepared token. A live preparation blocks every new
     * invitation for the patient until it expires, so leaving one behind would lock staff out for days.
     */
    private PatientPortalInviteDelivery abandon(Long deliveryId, State expected, String message, Long inviteId,
            PatientPortalStaffContext staff, int demographicNo) {
        String recorded = message;
        if (inviteId != null) {
            try {
                portal.revokeInvite(demographicNo, inviteId, staff);
            } catch (PatientPortalException exception) {
                recorded = message + " " + REVOKE_FAILED;
            }
        }
        String finalMessage = recorded;
        PatientPortalInviteDelivery row = deliveries.advance(deliveryId, expected, State.ABANDONED,
                r -> r.setErrorMessage(finalMessage));
        return row != null ? row : deliveries.find(deliveryId);
    }

    private PatientPortalInviteDelivery advance(Long deliveryId, State expected, State next,
            Consumer<PatientPortalInviteDelivery> change) {
        PatientPortalInviteDelivery row = deliveries.advance(deliveryId, expected, next, change);
        if (row == null) {
            throw new PortalInviteException(Reason.STATE_CHANGED);
        }
        return row;
    }

    private Optional<PatientPortalInviteDto> pendingInvite(int demographicNo, PatientPortalStaffContext staff) {
        return portal.listInvites(demographicNo, staff).stream()
                .filter(invite -> STATUS_PENDING.equals(invite.status()))
                .findFirst();
    }

    private EmailData emailFor(LoggedInInfo user, int demographicNo, String recipient, InviteRequest request) {
        if (inviteSettings.activationUrl() == null || inviteSettings.senderEmail() == null) {
            throw new PortalInviteException(Reason.NOT_CONFIGURED);
        }
        EmailConfig sender = emailConfigs.findActiveEmailConfig(inviteSettings.senderEmail());
        if (sender == null) {
            throw new PortalInviteException(Reason.NOT_CONFIGURED);
        }
        EmailData email = new EmailData();
        email.setSenderConfigId(sender.getId());
        email.setRecipients(new String[] {recipient});
        email.setSubject(SUBJECT);
        email.setBody("");
        email.setEncryptedMessage("");
        email.setPassword("");
        email.setPasswordClue("");
        email.setIsEncrypted(false);
        email.setIsAttachmentEncrypted(false);
        email.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        email.setInternalComment("");
        email.setTransactionType(TransactionType.PORTAL_INVITE);
        email.setDemographicNo(demographicNo);
        email.setProviderNo(user.getLoggedInProviderNo());
        email.setAdditionalParams("");
        email.setAttachments(Collections.emptyList());
        email.setConsentOverride(request.consentOverride());
        email.setConsentOverrideReason(request.consentOverrideReason());
        return email;
    }

    private void requireConsent(LoggedInInfo user, EmailData email) {
        // Checked before the portal is asked for anything: a blocked email must not leave a prepared token.
        String blocked = emailManager.consentBlockMessage(user, email);
        if (blocked != null) {
            throw new PortalInviteException(Reason.CONSENT_BLOCKED, blocked);
        }
    }

    String body(String inviteCode) {
        return "Hello,\n\n"
                + "You have been invited to create an account on your clinic's patient portal.\n\n"
                + "1. Open " + inviteSettings.activationUrl() + "\n"
                + "2. Enter this invitation code: " + inviteCode + "\n"
                + "3. Confirm your email address, date of birth and health card number, then choose a "
                + "username and password.\n\n"
                + "The code works once and expires 7 days after this email was sent. If you did not expect "
                + "this email, you can ignore it: no account is created unless the code is used.\n\n"
                + "This message was sent by your clinic. Please do not reply to it.\n";
    }

    private static void requireEmailChannel(InviteRequest request) {
        if (request.channel() != Channel.EMAIL) {
            throw new PortalInviteException(Reason.CHANNEL_UNAVAILABLE);
        }
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Each step must commit before the network call after it; a caller transaction would hold
            // every step back until the end and turn an interruption into lost history.
            throw new IllegalStateException("invite delivery must not run inside a transaction");
        }
    }

    private static boolean outcomeUnknown(PatientPortalException exception) {
        return switch (exception.kind()) {
            case TRANSPORT_FAILURE, MALFORMED_RESPONSE, UNEXPECTED_STATUS -> true;
            default -> false;
        };
    }

    record Contact(String email, LocalDate dateOfBirth, String healthCardNumber) {
    }

    /** Reads and checks what the portal will ask the patient for, before any portal call. */
    static Contact contactFor(Demographic patient) {
        String email = patient.getEmail() == null ? "" : patient.getEmail().strip();
        if (email.isEmpty()) {
            throw new PortalInviteException(Reason.MISSING_EMAIL);
        }
        if (!EmailValidator.getInstance().isValid(email)) {
            throw new PortalInviteException(Reason.INVALID_EMAIL);
        }
        LocalDate dateOfBirth;
        try {
            dateOfBirth = LocalDate.of(Integer.parseInt(strip(patient.getYearOfBirth())),
                    Integer.parseInt(strip(patient.getMonthOfBirth())), Integer.parseInt(strip(patient.getDateOfBirth())));
        } catch (NumberFormatException | DateTimeException exception) {
            throw new PortalInviteException(Reason.INCOMPLETE_DATE_OF_BIRTH);
        }
        String healthCard = strip(patient.getHin()).replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (healthCard.length() < MIN_HEALTH_CARD_LENGTH) {
            throw new PortalInviteException(Reason.MISSING_HEALTH_CARD);
        }
        return new Contact(email, dateOfBirth, healthCard);
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
