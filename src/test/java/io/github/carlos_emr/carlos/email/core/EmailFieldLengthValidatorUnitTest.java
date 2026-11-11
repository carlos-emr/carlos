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
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Unit tests for {@link EmailFieldLengthValidator}, which rejects email fields that would not fit
 * the {@code emailLog} columns instead of letting them be truncated (issue #3905).
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailFieldLengthValidator")
class EmailFieldLengthValidatorUnitTest {

    private static EmailData emailData() {
        EmailData data = new EmailData();
        data.setSubject("Subject");
        data.setBody("Body");
        data.setEncryptedMessage("");
        data.setPassword("");
        data.setPasswordClue("");
        data.setInternalComment("");
        data.setAdditionalParams("");
        return data;
    }

    @Test
    @DisplayName("should accept every field at its storage limit")
    void shouldReturnNoViolations_whenFieldsAreAtLimits() {
        EmailData data = emailData();
        data.setSubject("s".repeat(EmailFieldLengthValidator.MAX_SUBJECT_CHARS));
        data.setBody("b".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES));
        data.setEncryptedMessage("e".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES));
        data.setPassword("p".repeat(EmailFieldLengthValidator.MAX_PASSWORD_CHARS));
        data.setPasswordClue("c".repeat(EmailFieldLengthValidator.MAX_PASSWORD_CLUE_CHARS));
        data.setInternalComment("i".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES));
        data.setAdditionalParams("a".repeat(EmailFieldLengthValidator.MAX_ADDITIONAL_PARAMS_CHARS));

        assertThat(EmailFieldLengthValidator.validate(data)).isEmpty();
    }

    @Test
    @DisplayName("should reject a subject one character over the limit")
    void shouldRejectSubject_whenOverLimit() {
        EmailData data = emailData();
        data.setSubject("s".repeat(EmailFieldLengthValidator.MAX_SUBJECT_CHARS + 1));

        List<EmailFieldLengthValidator.Violation> violations = EmailFieldLengthValidator.validate(data);

        assertThat(violations)
                .extracting("messageKey", "actual", "limit")
                .containsExactly(tuple("email.compose.msg.subjectTooLong",
                        EmailFieldLengthValidator.MAX_SUBJECT_CHARS + 1, EmailFieldLengthValidator.MAX_SUBJECT_CHARS));
        // The value itself is left alone: rejection, never truncation.
        assertThat(data.getSubject()).hasSize(EmailFieldLengthValidator.MAX_SUBJECT_CHARS + 1);
    }

    @Test
    @DisplayName("should count subject characters as code points like utf8mb4 varchar")
    void shouldCountCodePoints_forSubjectWithSupplementaryCharacters() {
        EmailData data = emailData();
        // Each emoji is two UTF-16 units but one character to MariaDB.
        data.setSubject("😀".repeat(EmailFieldLengthValidator.MAX_SUBJECT_CHARS));

        assertThat(EmailFieldLengthValidator.validate(data)).isEmpty();
    }

    @Test
    @DisplayName("should accept a body far longer than the old 10,000 character cut-off")
    void shouldAcceptBody_whenLongerThanOldCutOff() {
        EmailData data = emailData();
        data.setBody("x".repeat(40000));

        assertThat(EmailFieldLengthValidator.validate(data)).isEmpty();
    }

    @Test
    @DisplayName("should measure the body in UTF-8 bytes and reject it over the BLOB limit")
    void shouldRejectBody_whenUtf8BytesExceedBlobLimit() {
        EmailData data = emailData();
        // 'é' is 2 bytes in UTF-8, so this fits by characters but not by bytes.
        int characters = EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES / 2 + 1;
        data.setBody("é".repeat(characters));

        assertThat(EmailFieldLengthValidator.validate(data))
                .extracting("messageKey", "actual")
                .containsExactly(tuple("email.compose.msg.bodyTooLong", characters * 2));
    }

    @Test
    @DisplayName("should keep the Base64 form of a limit-sized body within a 65,535 byte BLOB")
    void shouldFitBlobColumn_forBodyAtLimit() {
        byte[] raw = "b".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES).getBytes(StandardCharsets.UTF_8);
        byte[] oneMore = "b".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES + 1).getBytes(StandardCharsets.UTF_8);

        assertThat(Base64.getEncoder().encode(raw).length).isLessThanOrEqualTo(65535);
        assertThat(Base64.getEncoder().encode(oneMore).length).isGreaterThan(65535);
    }

    @Test
    @DisplayName("should report every over-length field at once")
    void shouldReportAllViolations_whenSeveralFieldsAreTooLong() {
        EmailData data = emailData();
        data.setEncryptedMessage("e".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES + 1));
        data.setPassword("p".repeat(EmailFieldLengthValidator.MAX_PASSWORD_CHARS + 1));
        data.setPasswordClue("c".repeat(EmailFieldLengthValidator.MAX_PASSWORD_CLUE_CHARS + 1));
        data.setInternalComment("i".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES + 1));
        data.setAdditionalParams("a".repeat(EmailFieldLengthValidator.MAX_ADDITIONAL_PARAMS_CHARS + 1));

        assertThat(EmailFieldLengthValidator.validate(data))
                .extracting("messageKey")
                .containsExactly(
                        "email.compose.msg.encryptedMessageTooLong",
                        "email.compose.msg.passwordTooLong",
                        "email.compose.msg.clueTooLong",
                        "email.compose.msg.internalCommentTooLong",
                        "email.compose.msg.additionalParamsTooLong");
    }

    @Test
    @DisplayName("should treat unset fields as empty")
    void shouldReturnNoViolations_whenFieldsAreUnset() {
        assertThat(EmailFieldLengthValidator.validate(new EmailData())).isEmpty();
    }

    @Test
    @DisplayName("should keep field content out of the violation text")
    void shouldOmitFieldContent_fromViolationText() {
        EmailData data = emailData();
        data.setSubject("SECRET-DIAGNOSIS ".repeat(100));

        List<EmailFieldLengthValidator.Violation> violations = EmailFieldLengthValidator.validate(data);

        assertThat(new EmailFieldLengthException(violations).getMessage()).doesNotContain("SECRET-DIAGNOSIS");
    }
}
