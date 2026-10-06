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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks outgoing email fields against the storage limits of the {@code emailLog} table.
 *
 * <p>Every sent email is first persisted to {@code emailLog}, so the table columns are the real
 * limits. CARLOS connects with {@code jdbcCompliantTruncation=false}, which means an over-length
 * value is either cut short by MariaDB with only a warning (non-strict {@code sql_mode}) or fails
 * the insert (strict mode). Neither is acceptable for clinical correspondence, so callers must
 * reject over-length input with a visible error instead of letting it reach the DAO. Earlier code
 * silently truncated the subject to 200 and the body to 10,000 characters well below these limits
 * (issue #3905).</p>
 *
 * <p>Limits, taken from {@code V1__baseline_schema.sql}:</p>
 * <ul>
 *   <li>{@code subject varchar(1024)}, {@code passwordClue varchar(1024)},
 *       {@code additionalParams varchar(1000)} and {@code password varchar(50)} count characters
 *       (Unicode code points, utf8mb4).</li>
 *   <li>{@code body}, {@code encryptedMessage} and {@code internalComment} are {@code BLOB}
 *       (65,535 bytes) and {@link io.github.carlos_emr.carlos.commn.model.EmailLog} stores them
 *       as Base64 of the UTF-8 bytes, so the text may use at most 49,149 UTF-8 bytes
 *       (4 * ceil(49,149 / 3) = 65,532).</li>
 * </ul>
 *
 * <p>SMTP imposes no tighter limit. Jakarta Mail folds a subject only at whitespace, so
 * {@code SMTPEmailSender.setSubject} writes a subject with an unbroken run too long for the RFC 5322
 * 998-character line as RFC 2047 encoded-words, which fold safely. Jakarta Mail chooses a transfer
 * encoding for body lines over 998 octets, and the SendGrid API carries both as JSON.</p>
 *
 * <p>The compose page mirrors these limits in JavaScript so providers see the problem before
 * submitting; this class is the authoritative server-side check.</p>
 *
 * @since 2026-09-24
 */
public final class EmailFieldLengthValidator {

    /** {@code emailLog.subject varchar(1024)}, in characters. */
    public static final int MAX_SUBJECT_CHARS = 1024;

    /** {@code emailLog.password varchar(50)}, in characters. */
    public static final int MAX_PASSWORD_CHARS = 50;

    /** {@code emailLog.passwordClue varchar(1024)}, in characters. */
    public static final int MAX_PASSWORD_CLUE_CHARS = 1024;

    /** {@code emailLog.additionalParams varchar(1000)}, in characters. */
    public static final int MAX_ADDITIONAL_PARAMS_CHARS = 1000;

    /**
     * Largest UTF-8 byte count whose Base64 form still fits a 65,535-byte {@code BLOB}; applies to
     * {@code body}, {@code encryptedMessage} and {@code internalComment}.
     */
    public static final int MAX_BLOB_TEXT_UTF8_BYTES = 49149;

    private EmailFieldLengthValidator() {
    }

    /**
     * Returns every provider-entered text field of {@code emailData} that would not fit its
     * {@code emailLog} column. Recipients ({@code toEmail varchar(255)}, joined with {@code ;}) are
     * not checked here: they are validated addresses chosen from the chart, and that column's
     * limit predates issue #3905.
     *
     * <p>Call this after {@code EmailManager} has cleared fields that will not be stored (for
     * example the password when encryption is off), so unused fields are not reported.</p>
     *
     * @param emailData the outgoing email; null fields are treated as empty
     * @return an unmodifiable list of violations, empty when every field fits
     */
    public static List<Violation> validate(EmailData emailData) {
        List<Violation> violations = new ArrayList<>();
        checkChars(violations, "email.compose.msg.subjectTooLong", emailData.getSubject(), MAX_SUBJECT_CHARS);
        checkBytes(violations, "email.compose.msg.bodyTooLong", emailData.getBody());
        checkBytes(violations, "email.compose.msg.encryptedMessageTooLong", emailData.getEncryptedMessage());
        checkChars(violations, "email.compose.msg.passwordTooLong", emailData.getPassword(), MAX_PASSWORD_CHARS);
        checkChars(violations, "email.compose.msg.clueTooLong", emailData.getPasswordClue(), MAX_PASSWORD_CLUE_CHARS);
        checkBytes(violations, "email.compose.msg.internalCommentTooLong", emailData.getInternalComment());
        checkChars(violations, "email.compose.msg.additionalParamsTooLong", emailData.getAdditionalParams(),
                MAX_ADDITIONAL_PARAMS_CHARS);
        return Collections.unmodifiableList(violations);
    }

    private static void checkChars(List<Violation> violations, String messageKey, String value, int limit) {
        if (value == null) {
            return;
        }
        // varchar length in utf8mb4 counts code points, not UTF-16 units.
        int length = value.codePointCount(0, value.length());
        if (length > limit) {
            violations.add(new Violation(messageKey, length, limit));
        }
    }

    private static void checkBytes(List<Violation> violations, String messageKey, String value) {
        if (value == null) {
            return;
        }
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length > MAX_BLOB_TEXT_UTF8_BYTES) {
            violations.add(new Violation(messageKey, length, MAX_BLOB_TEXT_UTF8_BYTES));
        }
    }

    /**
     * One over-length field. Carries only the message key and sizes, never the field content, so
     * it is safe to log or render. A class with getters (rather than a record) keeps it readable
     * from JSP EL.
     */
    public static final class Violation {
        private final String messageKey;
        private final int actual;
        private final int limit;

        Violation(String messageKey, int actual, int limit) {
            this.messageKey = messageKey;
            this.actual = actual;
            this.limit = limit;
        }

        /** @return the {@code oscarResources} key; its {0} is the actual size and {1} the limit */
        public String getMessageKey() {
            return messageKey;
        }

        /** @return the submitted size, in characters or UTF-8 bytes depending on the field */
        public int getActual() {
            return actual;
        }

        /** @return the maximum size the column accepts, in the same unit as {@link #getActual()} */
        public int getLimit() {
            return limit;
        }

        @Override
        public String toString() {
            return messageKey + " (" + actual + " > " + limit + ")";
        }
    }
}
