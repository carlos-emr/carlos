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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDao;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService.InviteRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException.Reason;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Duration;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Composes the invitation email: the outbox request, the body that carries the code, and the text
 * that replaces the code once the send has resolved.
 *
 * <p>The request is built without a body so consent can be checked before the portal is asked for a
 * code; {@link #body(String)} is filled in only once the code exists.
 *
 * @since 2026-09-22
 */
class PortalInviteEmailComposer {

    static final String SUBJECT = "Your patient portal invitation";

    /**
     * How long an activated code works. This is the portal's fixed policy ({@code DEFAULT_INVITE_TTL} in
     * the portal's {@code invites.py}), not a CARLOS setting, and the email is written before the portal
     * reports the exact deadline, so the email states the policy.
     */
    static final Duration CODE_LIFETIME = Duration.ofDays(7);

    /**
     * Replaces the invitation code in the stored email once the send has resolved. CARLOS never re-sends
     * that email - a replacement is a fresh code through resend - so keeping a live account credential in
     * the outbox, where any reader of the patient's email history could reopen it, buys nothing.
     */
    static final String CODE_FORGOTTEN =
            "This invitation's code is not kept by CARLOS. Resend the invitation to issue a new code.";

    /**
     * The portal's codes are {@code secrets.token_urlsafe(32)}: 43 URL-safe base64 characters. The range
     * leaves room for a longer token without accepting anything that could carry text or a link.
     */
    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Za-z0-9_-]{20,128}");

    private final PortalInviteSettings settings;
    private final EmailConfigDao emailConfigs;

    PortalInviteEmailComposer(PortalInviteSettings settings, EmailConfigDao emailConfigs) {
        this.settings = settings;
        this.emailConfigs = emailConfigs;
    }

    /**
     * Builds the outbox request with an empty body.
     *
     * @throws PortalInviteException {@link Reason#NOT_CONFIGURED} when the activation URL or sender is
     *     not set, or the sender has no active email account
     */
    // The empty password fields mark the email as unencrypted; they are not credentials.
    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD",
            justification = "Empty strings mark the email unencrypted; they are not authentication credentials")
    EmailData request(LoggedInInfo user, int demographicNo, String recipient, InviteRequest invite) {
        if (settings.activationUrl() == null || settings.senderEmail() == null) {
            throw new PortalInviteException(Reason.NOT_CONFIGURED);
        }
        EmailConfig sender = emailConfigs.findActiveEmailConfig(settings.senderEmail());
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
        email.setConsentOverride(invite.consentOverride());
        email.setConsentOverrideReason(invite.consentOverrideReason());
        return email;
    }

    /**
     * The chart note recording a sent invitation. It never holds the code: a chart note is permanent,
     * and the code activates the patient's account.
     *
     * @param recipient the address the invitation went to
     * @param replacement whether the invitation replaced an earlier one
     * @param confirmedByStaff whether staff confirmed the arrival of an email whose send was uncertain
     */
    String chartNote(String recipient, boolean replacement, boolean confirmedByStaff) {
        StringBuilder note = new StringBuilder("Patient portal invitation emailed to ").append(recipient).append('.');
        if (replacement) {
            note.append(" It replaced an earlier invitation, whose code no longer works.");
        }
        if (confirmedByStaff) {
            note.append(" Staff confirmed the email arrived.");
        }
        return note.append("\nThe invitation code is not recorded in CARLOS. If the patient loses the email, ")
                .append("resend the invitation from the Patient portal page.").toString();
    }

    /** @return whether {@code code} has the portal's token format, so it is safe to put in an email */
    static boolean isPlausibleCode(String code) {
        return code != null && CODE_FORMAT.matcher(code).matches();
    }

    /** @return the plain-text body carrying {@code inviteCode}; the code is text, never part of a URL */
    String body(String inviteCode) {
        return "Hello,\n\n"
                + "You have been invited to create an account on your clinic's patient portal.\n\n"
                + "1. Open " + settings.activationUrl() + "\n"
                + "2. Enter this invitation code: " + inviteCode + "\n"
                + "3. Confirm your email address, date of birth and health card number, then choose a "
                + "username and password.\n\n"
                + "The code works once and expires " + CODE_LIFETIME.toDays() + " days after this email was "
                + "sent. If you did not expect this email, you can ignore it: no account is created unless "
                + "the code is used.\n\n"
                + "This message was sent by your clinic. Please do not reply to it.\n";
    }
}
