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
package io.github.carlos_emr.carlos.sms;

import java.util.Arrays;
import java.util.Optional;

/**
 * The fixed CARLOS codes an SMS provider client maps every definite failure onto. CARLOS stores the code and its
 * fixed message, never the SMS provider's own wording, so the queue view can count failures by reason and nothing
 * the provider says (which may quote a phone number) reaches a text's error fields. A failure whose code is not one
 * of these is recorded as {@link #REJECTED_OTHER}.
 * <p>
 * A permanent failure can never succeed for that text as it stands, so the queue does not retry it. The queue
 * retries every other failure with back-off up to the retry limit, because it may clear once an administrator
 * fixes a setting or the provider recovers; a failure that affects every text (credentials, account, the
 * provider itself) also ends that run's sending, as does an unclear answer, so one outage costs one text per run
 * rather than the whole queue its retries. A direct send, a failed delivery report or a status lookup's answer is
 * final. A timeout or any other unclear outcome is not a failure at all: the client answers "uncertain", and the
 * text is sent again only if the provider's status lookup later says it definitely never arrived.
 *
 * @since 2026-10-08
 */
public enum SmsProviderErrorCode {
    /** The recipient's number cannot receive text messages (not a mobile, not in service, wrong format). */
    INVALID_RECIPIENT(true, false, "The SMS provider says the recipient's number cannot receive text messages."),
    /** The recipient has blocked texts from the clinic's number, for example by replying STOP to the carrier. */
    RECIPIENT_OPTED_OUT(true, false, "The recipient has blocked texts from the clinic's number."),
    /** The SMS provider refused the message text itself (too long, characters it does not accept, content). */
    MESSAGE_REJECTED(true, false, "The SMS provider refused the message text."),
    /** A credential or the sender number the provider needs is not saved. */
    NOT_CONFIGURED(false, true, "The SMS provider is missing a credential or the sender number."),
    /** The SMS provider refused the saved credentials, or the server's address is not on its allow-list. */
    AUTHENTICATION_FAILED(false, true, "The SMS provider refused the saved credentials or this server's address."),
    /** The SMS provider refused the sender number. */
    INVALID_SENDER(false, true, "The SMS provider refused the sender number."),
    /** The account is out of credit or over a daily or monthly limit. */
    ACCOUNT_LIMIT(false, true, "The SMS provider account is out of credit or over its limit."),
    /** The SMS provider asked CARLOS to slow down. */
    RATE_LIMITED(false, true, "The SMS provider asked CARLOS to send more slowly."),
    /** The SMS provider could not be reached or reported an error, and definitely did not send the text. */
    PROVIDER_UNAVAILABLE(false, true, "The SMS provider could not take the message; nothing was sent."),
    /** The SMS provider refused the message for a reason the client does not map. */
    REJECTED_OTHER(false, false, "The SMS provider refused the message for a reason CARLOS does not recognise.");

    private final boolean permanent;
    private final boolean affectsEveryText;
    private final String message;

    SmsProviderErrorCode(boolean permanent, boolean affectsEveryText, String message) {
        this.permanent = permanent;
        this.affectsEveryText = affectsEveryText;
        this.message = message;
    }

    /** @return whether retrying the same text can never succeed */
    public boolean permanent() {
        return permanent;
    }

    /** @return whether the next text would fail the same way, so the queue stops sending for this run */
    public boolean affectsEveryText() {
        return affectsEveryText;
    }

    /** @return the fixed English message stored with the code */
    public String message() {
        return message;
    }

    /**
     * @param code a stored or reported error code
     * @return the CARLOS code it names, or empty for any other code
     */
    public static Optional<SmsProviderErrorCode> fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.name().equals(code)).findFirst();
    }
}
