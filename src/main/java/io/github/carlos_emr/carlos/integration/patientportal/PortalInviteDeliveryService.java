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
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Channel;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Outcome;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.State;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException.Reason;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
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
 * PREPARED | QUEUED            -> ABANDONED (the prepared token is revoked)
 * PREPARING, portal refused    -> ABANDONED (nothing was prepared)
 * PREPARING, outcome unknown   stays PREPARING until staff withdraw it
 * staff, before COMMITTED      -> ABANDONED (the token, if any, is found and revoked)
 * staff, COMMITTED | SEND_UNCERTAIN -> SENT ("it arrived") | REVOKED ("it did not arrive")
 * </pre>
 *
 * <p>The ordering is the point. Committing before the email is durable could activate a token that
 * nothing will ever deliver; sending before committing would deliver a token that cannot activate an
 * account. The commit therefore runs inside {@link EmailManager.DispatchGate}, once the email row
 * exists and the message is built and archived, immediately before the transport; a failed commit
 * stops the send.
 *
 * <p>Why an attempt stopped is recorded as an {@link Outcome} code. The chart checks live in
 * {@link PortalInviteContact} and the email itself in {@link PortalInviteEmailComposer}.
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
    // The status and endpoint named when a prepared code fails the format check.
    private static final int HTTP_CREATED = 201;
    private static final String PREPARE_TEMPLATE = "/internal/carlos/patients/{id}/invites/prepare";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PREPARED = "prepared";
    private static final String STATUS_ACCEPTED = "accepted";
    private static final String STATUS_SUPERSEDED = "superseded";
    private static final String STATUS_REVOKED = "revoked";

    /** The email layer's reason for not sending, recorded on the outbox row when the gate stops a send. */
    static final String NOT_SENT_BEFORE_COMMIT = "The invitation email was not sent: the portal did not activate it.";

    // What a staff resolution records on the email outbox row, in the outbox's own English convention.
    static final String EMAIL_CONFIRMED_SENT = "Staff confirmed the invitation email arrived.";
    static final String EMAIL_CONFIRMED_NOT_SENT =
            "Staff confirmed the invitation email did not arrive; it was revoked.";
    static final String EMAIL_ABANDONED_BY_STAFF =
            "Staff stopped this delivery before the invitation was activated.";

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
     * @param withdrawStale whether staff confirmed withdrawing this patient's stuck earlier attempts
     */
    public record InviteRequest(Channel channel, boolean confirmReplace, boolean consentOverride,
            String consentOverrideReason, boolean withdrawStale) {
    }

    private final PatientPortalService portal;
    private final PatientPortalSettings portalSettings;
    private final PortalInviteEmailComposer emails;
    private final EmailManager emailManager;
    private final PatientPortalInviteDeliveryDao deliveries;
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
        this.emails = new PortalInviteEmailComposer(inviteSettings, emailConfigs);
        this.emailManager = emailManager;
        this.deliveries = deliveries;
        this.emailLogs = emailLogs;
        this.clock = clock;
    }

    /**
     * Invites a patient. When a pending invitation exists, {@code confirmReplace} turns this into a
     * resend of it; without confirmation the request is refused, because replacing an invitation
     * silently would strand a code the patient may already hold. A stuck earlier attempt is withdrawn
     * first when staff confirm it; see {@link #withdrawStaleAttempts}.
     *
     * @return the attempt as recorded; its state says how far delivery got
     * @throws PortalInviteException when the invitation is refused before any portal call
     * @throws PatientPortalException when the portal refuses or cannot be reached
     */
    public PatientPortalInviteDelivery invite(LoggedInInfo user, Demographic patient,
            PatientPortalStaffContext staff, InviteRequest request) {
        requireNoTransaction();
        requireEmailChannel(request);
        PortalInviteContact contact = PortalInviteContact.from(patient);
        EmailData email = emails.request(user, patient.getDemographicNo(), contact.email(), request);
        requireConsent(user, email);
        withdrawStaleAttempts(patient, staff, request.withdrawStale());
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
        PortalInviteContact contact = PortalInviteContact.from(patient);
        EmailData email = emails.request(user, patient.getDemographicNo(), contact.email(), request);
        requireConsent(user, email);
        withdrawStaleAttempts(patient, staff, request.withdrawStale());
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
        if (!onCurrentConnection(row)) {
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
            case CONFIRM_SENT -> {
                PatientPortalInviteDelivery sent =
                        confirm(row, State.SENT, Outcome.CONFIRMED_SENT, EMAIL_CONFIRMED_SENT);
                Integer emailLogId = sent.getEmailLogId();
                EmailLog emailLog = emailLogId == null ? null : emailLogs.find(emailLogId.intValue());
                yield recordOnChart(user, sent, emailLog, true);
            }
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

    private PatientPortalInviteDelivery deliver(LoggedInInfo user, int demographicNo,
            PortalInviteContact contact, PatientPortalStaffContext staff, EmailData email, Long supersededInviteId) {
        String operationId = OPERATION_PREFIX + UUID.randomUUID();
        PatientPortalInviteDelivery row = deliveries.claim(new PatientPortalInviteDelivery(
                operationId, demographicNo, portalSettings.clinicId(), portalSettings.baseUrl(),
                Channel.EMAIL, supersededInviteId, user.getLoggedInProviderNo()));

        PatientPortalIssuedInviteDto issued;
        try {
            issued = prepare(demographicNo, contact, supersededInviteId, operationId, staff).issuedInvite();
        } catch (PatientPortalException exception) {
            if (outcomeUnknown(exception)) {
                // The portal may hold a prepared code whose id CARLOS never learned, and a live
                // preparation blocks every new invitation for this patient until it expires. Leave the
                // attempt unfinished so staff can resolve it: recovery asks the portal again with the
                // same operation id, learns the id, and withdraws the code.
                deliveries.advance(row.getId(), State.PREPARING, State.PREPARING,
                        r -> r.setOutcome(Outcome.PREPARE_UNCONFIRMED));
            } else {
                // The portal refused outright, so it prepared nothing and there is nothing to withdraw.
                abandon(row.getId(), State.PREPARING, Outcome.PREPARE_REFUSED, null, staff, demographicNo);
            }
            throw exception;
        }
        long inviteId = issued.invite().id();
        row = advance(row.getId(), State.PREPARING, State.PREPARED, r -> r.setPortalInviteId(inviteId));

        String code = issued.inviteToken().expose();
        if (!PortalInviteEmailComposer.isPlausibleCode(code)) {
            // The code goes verbatim into an email from the clinic's own address, so anything but the
            // portal's URL-safe token format (line breaks, links, prose) is refused and withdrawn.
            abandon(row.getId(), State.PREPARED, Outcome.PREPARE_REFUSED, inviteId, staff, demographicNo);
            throw PatientPortalException.ofMalformedResponse(HTTP_CREATED, PREPARE_TEMPLATE,
                    new PortalContractException("portal invitation code has an unexpected format"));
        }
        email.setBody(emails.body(code));
        // The outbound archive is a permanent patient document; it keeps the email without the code.
        email.setArchiveRedactions(List.of(code));
        CommitGate gate = new CommitGate(row.getId(), inviteId, operationId, staff);
        EmailSendResult result;
        try {
            result = emailManager.sendEmailWithResult(user, email, gate);
        } catch (RuntimeException exception) {
            settleAfterFailure(row.getId(), inviteId, staff, demographicNo);
            throw exception;
        } finally {
            email.setBody("");
            email.setArchiveRedactions(List.of());
        }
        return settle(user, row.getId(), result, inviteId, staff, demographicNo);
    }

    private PatientPortalPreparedInviteDto prepare(int demographicNo, PortalInviteContact contact,
            Long supersededInviteId, String operationId, PatientPortalStaffContext staff) {
        try {
            return prepareOnce(demographicNo, contact, supersededInviteId, operationId, staff);
        } catch (PatientPortalException exception) {
            if (!outcomeUnknown(exception)) {
                throw exception;
            }
            // The request may have reached the portal. The same operation id returns the same token, so
            // one retry recovers a lost response without preparing a second invitation.
            try {
                return prepareOnce(demographicNo, contact, supersededInviteId, operationId, staff);
            } catch (PatientPortalException retry) {
                // A refused retry does not undo the first request, which may still have prepared a code:
                // the outcome stays unknown, so the attempt stays open for staff to resolve.
                throw outcomeUnknown(retry) ? retry : exception;
            }
        }
    }

    private PatientPortalPreparedInviteDto prepareOnce(int demographicNo, PortalInviteContact contact,
            Long supersededInviteId, String operationId, PatientPortalStaffContext staff) {
        if (supersededInviteId != null) {
            return portal.prepareInviteResend(supersededInviteId, operationId, staff);
        }
        return portal.prepareInvite(demographicNo, contact.email(), contact.dateOfBirth(),
                contact.healthCardNumber(), operationId, staff);
    }

    /**
     * Commits delivery on the portal once the email is durable, built and archived, immediately before
     * the transport sends it. Any failure stops the send; the row records whether the portal may still
     * have committed.
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
                    committed = commit(emailLogId);
                } catch (PatientPortalException exception) {
                    Outcome outcome = outcomeUnknown(exception) ? Outcome.COMMIT_UNCONFIRMED : Outcome.COMMIT_REFUSED;
                    deliveries.advance(deliveryId, State.QUEUED, State.QUEUED, r -> r.setOutcome(outcome));
                    throw new EmailSendingException(NOT_SENT_BEFORE_COMMIT);
                }
                Date expiresAt = committed.expiresAt() == null ? null : Date.from(committed.expiresAt());
                if (deliveries.advance(deliveryId, State.QUEUED, State.COMMITTED, r -> {
                    r.setExpiresAt(expiresAt);
                    r.setOutcome(null);
                }) == null) {
                    throw new EmailSendingException(NOT_SENT_BEFORE_COMMIT);
                }
            } catch (RuntimeException exception) {
                // EmailManager treats only EmailSendingException from the gate as a definite "not sent";
                // any other exception would be recorded as an unconfirmed send. Nothing was sent here, so
                // it is converted.
                logger.warn("patient portal invite commit gate failed: {}", exception.getClass().getSimpleName());
                throw new EmailSendingException(NOT_SENT_BEFORE_COMMIT);
            }
        }

        /**
         * Commits, retrying once when the first answer was lost. The portal treats a repeated commit with
         * the same operation id and reference as the same commit, so the retry cannot activate twice. It
         * matters most for a resend: the portal retires the old code as it commits the new one, so
         * withdrawing a new code whose commit merely went unconfirmed can leave the patient with neither.
         */
        private PatientPortalInviteDto commit(Integer emailLogId) {
            String reference = REFERENCE_PREFIX + emailLogId;
            try {
                return portal.commitInviteDelivery(inviteId, operationId, reference, staff);
            } catch (PatientPortalException exception) {
                if (!outcomeUnknown(exception)) {
                    throw exception;
                }
                try {
                    return portal.commitInviteDelivery(inviteId, operationId, reference, staff);
                } catch (PatientPortalException retry) {
                    // As for prepare: a refused retry does not make the first commit's outcome known.
                    throw outcomeUnknown(retry) ? retry : exception;
                }
            }
        }
    }

    private PatientPortalInviteDelivery settle(LoggedInInfo user, Long deliveryId, EmailSendResult result,
            long inviteId, PatientPortalStaffContext staff, int demographicNo) {
        PatientPortalInviteDelivery row = deliveries.find(deliveryId);
        if (row == null) {
            throw new PortalInviteException(Reason.STATE_CHANGED);
        }
        // Scrubbed through the result's row, which exists even when the gate never ran and so never
        // recorded its id on the attempt.
        forgetCode(result.getEmailLog() != null ? result.getEmailLog().getId() : row.getEmailLogId());
        return switch (row.getState()) {
            case COMMITTED -> {
                if (result.isTransportAccepted()) {
                    yield recordSent(user, deliveryId, result.getEmailLog());
                }
                if (result.isDeliveryUnconfirmed()) {
                    yield advance(deliveryId, State.COMMITTED, State.SEND_UNCERTAIN,
                            r -> r.setOutcome(Outcome.SEND_UNCONFIRMED));
                }
                yield advance(deliveryId, State.COMMITTED, State.SEND_FAILED, r -> r.setOutcome(Outcome.SEND_REFUSED));
            }
            // The gate never ran: consent blocked the email, or building, archiving or redacting it failed.
            case PREPARED -> abandon(deliveryId, State.PREPARED, Outcome.SEND_BLOCKED, inviteId, staff, demographicNo);
            // The gate stopped before or at the commit. Withdraw the token; nothing reached the patient.
            case QUEUED -> abandon(deliveryId, State.QUEUED, row.getOutcome() == null
                    ? Outcome.COMMIT_REFUSED : row.getOutcome(), inviteId, staff, demographicNo);
            default -> row;
        };
    }

    /**
     * Records an email the transport accepted. The patient has it now, so bookkeeping that fails here must
     * not reach staff as a failure: that invites a resend, which would retire the code just delivered.
     * The attempt is answered as far as it got, usually still activated and awaiting confirmation, which
     * staff resolve with "it arrived".
     */
    private PatientPortalInviteDelivery recordSent(LoggedInInfo user, Long deliveryId, EmailLog emailLog) {
        try {
            PatientPortalInviteDelivery sent =
                    advance(deliveryId, State.COMMITTED, State.SENT, r -> r.setOutcome(null));
            return recordOnChart(user, sent, emailLog, false);
        } catch (RuntimeException exception) {
            logger.warn("patient portal invitation was sent but could not be recorded as sent: {}",
                    exception.getClass().getSimpleName());
            PatientPortalInviteDelivery row = deliveries.find(deliveryId);
            if (row == null) {
                throw exception;
            }
            return row;
        }
    }

    private void settleAfterFailure(Long deliveryId, long inviteId, PatientPortalStaffContext staff,
            int demographicNo) {
        PatientPortalInviteDelivery row = deliveries.find(deliveryId);
        if (row == null) {
            return;
        }
        forgetCode(row.getEmailLogId());
        if (row.getState() == State.PREPARED || row.getState() == State.QUEUED) {
            abandon(deliveryId, row.getState(), Outcome.SEND_BLOCKED, inviteId, staff, demographicNo);
        } else if (row.getState() == State.COMMITTED) {
            // The token is live and the send's fate is unknown. Never revoke on uncertainty.
            deliveries.advance(deliveryId, State.COMMITTED, State.SEND_UNCERTAIN,
                    r -> r.setOutcome(Outcome.SEND_UNCONFIRMED));
        }
    }

    /**
     * Records a sent invitation on the patient's chart, without its code (see
     * {@link PortalInviteEmailComposer#chartNote}). The email has already gone, so a failure here never
     * undoes the send; the attempt records it, and the page asks staff to add the note by hand.
     */
    private PatientPortalInviteDelivery recordOnChart(LoggedInInfo user, PatientPortalInviteDelivery row,
            EmailLog emailLog, boolean confirmedByStaff) {
        if (emailLog != null && emailLog.getToEmail() != null && emailLog.getToEmail().length > 0) {
            try {
                emailManager.addEmailNote(user, emailLog, emails.chartNote(emailLog.getToEmail()[0],
                        row.getSupersededInviteId() != null, confirmedByStaff));
                return row;
            } catch (RuntimeException exception) {
                logger.warn("patient portal invitation chart note could not be written: {}",
                        exception.getClass().getSimpleName());
            }
        }
        PatientPortalInviteDelivery updated = deliveries.advance(row.getId(), State.SENT, State.SENT,
                r -> r.setOutcome(Outcome.CHART_NOTE_FAILED));
        return updated != null ? updated : row;
    }

    // --- recovery --------------------------------------------------------------------------------

    /**
     * Withdraws this patient's attempts that stopped before their code was activated and have been idle
     * for {@link #RECOVERY_MIN_AGE}. Such an attempt usually leaves a prepared code on the portal, which
     * blocks every new invitation for the patient until it expires, so it is cleared at the moment staff
     * next try to invite. Staff confirm it first: without {@code withdraw} the request is refused.
     * An attempt on a different portal connection is left alone; it cannot block this one.
     *
     * @throws PortalInviteException {@link Reason#STALE_ATTEMPT_EXISTS} when one exists and
     *     {@code withdraw} is false
     */
    private void withdrawStaleAttempts(Demographic patient, PatientPortalStaffContext staff, boolean withdraw) {
        List<PatientPortalInviteDelivery> stale = unfinishedFor(patient.getDemographicNo()).stream()
                .filter(row -> decisionsFor(row.getState()).contains(Decision.ABANDON))
                .filter(this::onCurrentConnection)
                .filter(this::isRecoverable)
                .toList();
        if (stale.isEmpty()) {
            return;
        }
        if (!withdraw) {
            throw new PortalInviteException(Reason.STALE_ATTEMPT_EXISTS);
        }
        stale.forEach(row -> abandonByStaff(row, patient, staff));
    }

    private List<PatientPortalInviteDelivery> unfinishedFor(int demographicNo) {
        List<PatientPortalInviteDelivery> rows = deliveries.findUnfinishedByDemographic(demographicNo);
        return rows == null ? List.of() : rows;
    }

    private boolean onCurrentConnection(PatientPortalInviteDelivery row) {
        return portalSettings.baseUrl().equals(row.getPortalOrigin())
                && portalSettings.clinicId().equals(row.getClinicId());
    }

    private PatientPortalInviteDelivery abandonByStaff(PatientPortalInviteDelivery row, Demographic patient,
            PatientPortalStaffContext staff) {
        Long inviteId = row.getPortalInviteId();
        if (inviteId == null && row.getState() == State.PREPARING) {
            inviteId = findLostPreparation(row, patient, staff);
        }
        PatientPortalInviteDelivery abandoned = tryAbandon(row.getId(), row.getState(),
                Outcome.ABANDONED_BY_STAFF, inviteId, staff, row.getDemographicNo());
        if (abandoned == null) {
            // A colleague resolved it first, perhaps by withdrawing it too; their decision stands.
            throw new PortalInviteException(Reason.STATE_CHANGED);
        }
        forgetCode(abandoned.getEmailLogId());
        if (abandoned.getEmailLogId() != null) {
            // In these states the send never started, so the email row can be closed as not sent.
            emailLogs.transitionEmailStatus(abandoned.getEmailLogId(), EmailStatus.PENDING, EmailStatus.FAILED,
                    EMAIL_ABANDONED_BY_STAFF, Date.from(clock.instant()));
        }
        return abandoned;
    }

    /**
     * Names the preparation a lost prepare response may have left on the portal, so it can be withdrawn.
     *
     * <p>The portal discloses a preparation again only to the staff member who asked for it, with the same
     * chart details, so repeating the request works only for them and only while the chart is unchanged.
     * Otherwise the preparation is found in the patient's invitation list: the portal holds at most one
     * preparation per patient, so a prepared invitation that no other unfinished attempt claims is this
     * one.
     *
     * @return the invitation's id, or {@code null} when the portal holds no such preparation
     * @throws PatientPortalException when the portal cannot say, so the attempt stays open rather than be
     *     reported as cleanly withdrawn while a code may still block the patient
     */
    private Long findLostPreparation(PatientPortalInviteDelivery row, Demographic patient,
            PatientPortalStaffContext staff) {
        try {
            PortalInviteContact contact =
                    row.getSupersededInviteId() == null ? PortalInviteContact.from(patient) : null;
            return prepareOnce(row.getDemographicNo(), contact, row.getSupersededInviteId(),
                    row.getDeliveryOperationId(), staff).issuedInvite().invite().id();
        } catch (PatientPortalException exception) {
            if (outcomeUnknown(exception)) {
                throw exception;
            }
            // Refused: another staff member, or chart details that changed. Look it up instead.
        } catch (PortalInviteException exception) {
            // The chart no longer holds what the original request sent. Look it up instead.
        }
        Set<Long> claimed = new HashSet<>();
        for (PatientPortalInviteDelivery other : unfinishedFor(row.getDemographicNo())) {
            if (other.getId().equals(row.getId())) {
                continue;
            }
            if (!isRecoverable(other)) {
                // Another attempt is still running; a preparation seen now may be its own, not yet
                // recorded. Guessing could withdraw a colleague's live invitation.
                throw new PortalInviteException(Reason.STATE_CHANGED);
            }
            if (other.getPortalInviteId() != null) {
                claimed.add(other.getPortalInviteId());
            }
        }
        return portal.listInvites(row.getDemographicNo(), staff).stream()
                .filter(invite -> STATUS_PREPARED.equals(invite.status()) && !claimed.contains(invite.id()))
                .map(PatientPortalInviteDto::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Revokes the code of an email staff say never arrived. The attempt is claimed first, so a colleague
     * answering "it arrived" at the same moment cannot win after the code is already revoked. Unless the
     * code is confirmed dead, the claim is released in one place, whatever failed, and the attempt is
     * left exactly as it was, idle time included, for staff to act on again.
     */
    private PatientPortalInviteDelivery confirmNotSent(PatientPortalInviteDelivery row,
            PatientPortalStaffContext staff) {
        State previous = row.getState();
        Outcome previousOutcome = row.getOutcome();
        Date previousUpdatedAt = row.getUpdatedAt();
        PatientPortalInviteDelivery claimed =
                advance(row.getId(), previous, State.REVOKED, r -> r.setOutcome(Outcome.CONFIRMED_NOT_SENT));
        CodeFate fate = null;
        try {
            fate = revokeCode(row, staff);
        } finally {
            if (fate != CodeFate.DEAD) {
                deliveries.release(row.getId(), State.REVOKED, previous, previousOutcome, previousUpdatedAt);
            }
        }
        if (fate == CodeFate.USED) {
            throw new PortalInviteException(Reason.INVITE_ALREADY_USED);
        }
        return closeEmail(claimed, EMAIL_CONFIRMED_NOT_SENT);
    }

    /** What revoking an attempt's code found. */
    private enum CodeFate {
        /** Revoked now, or already replaced or revoked on the portal: nobody can use it. */
        DEAD,
        /** The patient already activated an account with it, so the email did arrive. */
        USED
    }

    /**
     * Revokes the attempt's code. The portal refuses to revoke an invitation that was replaced, revoked
     * or used; the first two leave the code dead, which is what staff asked for. A used one means the
     * email did arrive, so staff are told, rather than left with only a false "it arrived" to close the
     * attempt.
     *
     * @throws PatientPortalException when the portal cannot revoke it or say why
     */
    private CodeFate revokeCode(PatientPortalInviteDelivery row, PatientPortalStaffContext staff) {
        try {
            portal.revokeInvite(row.getDemographicNo(), row.getPortalInviteId(), staff);
            return CodeFate.DEAD;
        } catch (PatientPortalException exception) {
            if (exception.kind() != PatientPortalException.Kind.CONFLICT) {
                throw exception;
            }
            String status = portal.listInvites(row.getDemographicNo(), staff).stream()
                    .filter(invite -> invite.id() == row.getPortalInviteId())
                    .map(PatientPortalInviteDto::status)
                    .findFirst()
                    .orElse(null);
            if (STATUS_ACCEPTED.equals(status)) {
                return CodeFate.USED;
            }
            if (STATUS_SUPERSEDED.equals(status) || STATUS_REVOKED.equals(status)) {
                return CodeFate.DEAD;
            }
            throw exception;
        }
    }

    /** Records the staff confirmation and closes the email row, which a stuck send left pending. */
    private PatientPortalInviteDelivery confirm(PatientPortalInviteDelivery row, State next, Outcome outcome,
            String emailMessage) {
        return closeEmail(advance(row.getId(), row.getState(), next, r -> r.setOutcome(outcome)), emailMessage);
    }

    /** Drops the code from the stored email and resolves the email row the stuck send left pending. */
    private PatientPortalInviteDelivery closeEmail(PatientPortalInviteDelivery updated, String emailMessage) {
        forgetCode(updated.getEmailLogId());
        if (updated.getEmailLogId() != null) {
            emailLogs.transitionEmailStatus(updated.getEmailLogId(), EmailStatus.PENDING, EmailStatus.RESOLVED,
                    emailMessage, Date.from(clock.instant()));
        }
        return updated;
    }

    // --- shared ----------------------------------------------------------------------------------

    /**
     * Marks an attempt abandoned and withdraws its prepared token. A live preparation blocks every new
     * invitation for the patient until it expires, so leaving one behind would lock staff out for days.
     */
    private PatientPortalInviteDelivery abandon(Long deliveryId, State expected, Outcome outcome, Long inviteId,
            PatientPortalStaffContext staff, int demographicNo) {
        PatientPortalInviteDelivery row = tryAbandon(deliveryId, expected, outcome, inviteId, staff, demographicNo);
        return row != null ? row : deliveries.find(deliveryId);
    }

    /** As {@link #abandon}, but {@code null} when the attempt was no longer in {@code expected}. */
    private PatientPortalInviteDelivery tryAbandon(Long deliveryId, State expected, Outcome outcome,
            Long inviteId, PatientPortalStaffContext staff, int demographicNo) {
        boolean revokeFailed = inviteId != null && !withdraw(demographicNo, inviteId, staff);
        return deliveries.advance(deliveryId, expected, State.ABANDONED, r -> {
            r.setOutcome(outcome);
            r.setRevokeFailed(revokeFailed);
            if (inviteId != null) {
                // Recovery of a lost prepare response learns the id only here; record which invitation
                // was withdrawn, so the attempt names it like every other.
                r.setPortalInviteId(inviteId);
            }
        });
    }

    /** @return whether the portal withdrew the invitation; a failure leaves it to expire on its own */
    private boolean withdraw(int demographicNo, long inviteId, PatientPortalStaffContext staff) {
        try {
            portal.revokeInvite(demographicNo, inviteId, staff);
            return true;
        } catch (PatientPortalException exception) {
            return false;
        }
    }

    /**
     * Drops the invitation code from the stored email now that the send has resolved either way.
     *
     * <p>Best effort: the row is the record that the email existed, and failing to rewrite its body must
     * not turn a delivered invitation into a reported failure.
     */
    private void forgetCode(Integer emailLogId) {
        if (emailLogId == null) {
            return;
        }
        try {
            emailLogs.replaceBody(emailLogId, PortalInviteEmailComposer.CODE_FORGOTTEN);
        } catch (RuntimeException exception) {
            logger.warn("patient portal invitation code could not be cleared from the outbox: {}",
                    exception.getClass().getSimpleName());
        }
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

    private void requireConsent(LoggedInInfo user, EmailData email) {
        // Checked before the portal is asked for anything: a blocked email must not leave a prepared token.
        String blocked = emailManager.consentBlockMessage(user, email);
        if (blocked != null) {
            throw new PortalInviteException(Reason.CONSENT_BLOCKED, blocked);
        }
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
}
