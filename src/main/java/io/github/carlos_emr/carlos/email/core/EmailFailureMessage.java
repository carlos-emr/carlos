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
package io.github.carlos_emr.carlos.email.core;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * The generic message staff see when an email's attachments could not be prepared.
 *
 * <p>The underlying exception's text can name files or repeat document content, so it goes
 * neither to the page nor to the log. Instead each failure gets a reference: the log line records
 * it with the exception's class, and the page shows it, so an administrator can match the two.</p>
 *
 * @since 2026-10-08
 */
public final class EmailFailureMessage {

    /** For an eForm whose email attachments could not be prepared; {0} is the reference. */
    public static final String EFORM_ATTACHMENTS_KEY = "email.compose.msg.attachmentFailed";
    /** For a sent email whose attachments could not be prepared again for resending; {0} is the reference. */
    public static final String RESEND_ATTACHMENTS_KEY = "email.compose.msg.resendAttachmentFailed";

    private static final String BUNDLE = "oscarResources";
    private static final String FALLBACK = "The email attachments could not be prepared. Reference for your administrator: {0}";

    private EmailFailureMessage() {
    }

    /** @return a fresh reference for one failure: random, so it carries nothing from the request */
    public static String newReference() {
        return UUID.randomUUID().toString();
    }

    /**
     * @param locale the reader's locale
     * @param key {@link #EFORM_ATTACHMENTS_KEY} or {@link #RESEND_ATTACHMENTS_KEY}
     * @param reference the failure's reference, from {@link #newReference()}
     * @return the message in that locale, naming the reference
     */
    public static String format(Locale locale, String key, String reference) {
        String pattern;
        try {
            pattern = ResourceBundle.getBundle(BUNDLE, locale == null ? Locale.ENGLISH : locale).getString(key);
        } catch (MissingResourceException e) {
            pattern = FALLBACK;
        }
        return new MessageFormat(pattern, locale == null ? Locale.ENGLISH : locale).format(new Object[] {reference});
    }
}
