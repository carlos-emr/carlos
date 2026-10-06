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

/**
 * An invitation the workflow refused to attempt, or a recovery it refused to perform.
 *
 * <p>Every message is fixed text written for staff; none carries a patient value, a token, or a
 * portal response. Refusals raised before a portal call guarantee that nothing was prepared.
 *
 * @since 2026-09-22
 */
public class PortalInviteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why the request was refused. The code is the machine-readable {@code reason} in JSON replies. */
    public enum Reason {
        CHANNEL_UNAVAILABLE("channel_unavailable",
                "Text message invitations are not available yet. Use email."),
        MISSING_EMAIL("missing_email",
                "This patient has no email address on their chart. Add one before inviting them."),
        INVALID_EMAIL("invalid_email",
                "The email address on this patient's chart is not valid. Correct it before inviting them."),
        INCOMPLETE_DATE_OF_BIRTH("incomplete_date_of_birth",
                "This patient's date of birth is incomplete. The portal asks for it at activation."),
        INVALID_DATE_OF_BIRTH("invalid_date_of_birth",
                "This patient's date of birth is outside the range the portal accepts. Correct it before inviting them."),
        MISSING_HEALTH_CARD("missing_health_card",
                "This patient has no health card number on their chart. The portal asks for it at activation."),
        INVALID_HEALTH_CARD("invalid_health_card",
                "This patient's health card number is not valid for the portal. Correct it before inviting them."),
        CONSENT_BLOCKED("consent_blocked", "Email consent does not permit this invitation."),
        STALE_ATTEMPT_EXISTS("stale_attempt_exists",
                "An earlier invitation attempt for this patient did not finish. Withdraw it to send a new "
                        + "invitation."),
        PENDING_INVITE_EXISTS("pending_invite_exists",
                "This patient already has a pending invitation. Resend it, or confirm that it should be replaced."),
        INVITE_ALREADY_USED("invite_already_used",
                "The patient already used this invitation, so the email did arrive. Choose that it arrived."),
        INVITE_NOT_PENDING("invite_not_pending",
                "Only a pending invitation can be resent. Refresh the panel."),
        NOT_CONFIGURED("invite_not_configured",
                "Portal invitations are not configured on this server. Ask an administrator to set "
                        + PortalInviteSettings.PUBLIC_BASE_URL_KEY + " and "
                        + PortalInviteSettings.SENDER_EMAIL_KEY + "."),
        SENDER_UNAVAILABLE("invite_sender_unavailable",
                "The portal invitation sender address has no active email account in CARLOS. Ask an administrator "
                        + "to add or activate the account set in " + PortalInviteSettings.SENDER_EMAIL_KEY + "."),
        DELIVERY_NOT_FOUND("delivery_not_found",
                "That invitation delivery was not found for this patient. Refresh the panel."),
        RECOVERY_TOO_EARLY("recovery_too_early",
                "This delivery changed less than 15 minutes ago. Wait before resolving it, in case it is "
                        + "still in progress."),
        RECOVERY_NOT_ALLOWED("recovery_not_allowed",
                "That action does not apply to this delivery's current state. Refresh the panel."),
        PORTAL_CONNECTION_CHANGED("portal_connection_changed",
                "This delivery was started against a different portal connection. Restore that connection "
                        + "to resolve it."),
        INVITE_STILL_LIVE("invite_still_live",
                "The portal does not show this invitation's code as replaced or expired, so the email cannot "
                        + "be recorded as not arrived: it may still be sent."),
        STATE_CHANGED("state_changed",
                "This delivery changed while the request was running. Refresh the panel.");

        private final String code;
        private final String message;

        Reason(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String code() {
            return code;
        }
    }

    private final Reason reason;

    public PortalInviteException(Reason reason) {
        this(reason, reason.message);
    }

    PortalInviteException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
