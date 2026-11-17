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
package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.email.core.EmailFieldLengthValidator;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SMTPEmailSender#setSubject}: a subject up to the {@code emailLog} limit
 * must never produce a header line over the RFC 5322 998-character limit, and must decode back to
 * the original text (issue #3905).
 *
 * @since 2026-09-24
 */
@DisplayName("SMTPEmailSender subject header")
@Tag("unit")
@Tag("fast")
@Tag("email")
class SMTPEmailSenderSubjectUnitTest {

    private static MimeMessage messageWithSubject(String subject) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        SMTPEmailSender.setSubject(helper, message, subject);
        helper.setText("body", false);
        message.saveChanges();
        return message;
    }

    private static int longestHeaderLine(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        String raw = out.toString(StandardCharsets.US_ASCII);
        String headers = raw.substring(0, raw.indexOf("\r\n\r\n"));
        return Arrays.stream(headers.split("\r\n")).mapToInt(String::length).max().orElse(0);
    }

    @Test
    @DisplayName("should keep header lines within 998 characters for an unbroken ASCII subject at the storage limit")
    void shouldFoldWithinLineLimit_forUnbrokenAsciiSubjectAtStorageLimit() throws Exception {
        String subject = "x".repeat(EmailFieldLengthValidator.MAX_SUBJECT_CHARS);

        MimeMessage message = messageWithSubject(subject);

        assertThat(longestHeaderLine(message)).isLessThanOrEqualTo(998);
        assertThat(message.getSubject()).isEqualTo(subject);
    }

    @Test
    @DisplayName("should preserve spacing and supplementary characters when encoding is forced")
    void shouldRoundTripExactly_withMixedTextAndSupplementaryCharacters() throws Exception {
        String subject = "Re:  " + "😀éa".repeat(10) + "y".repeat(1000);

        MimeMessage message = messageWithSubject(subject);

        assertThat(longestHeaderLine(message)).isLessThanOrEqualTo(998);
        assertThat(message.getSubject()).isEqualTo(subject);
    }

    @Test
    @DisplayName("should leave an ordinary subject unencoded")
    void shouldNotEncode_forOrdinarySubject() throws Exception {
        MimeMessage message = messageWithSubject("Lab results for your review");

        assertThat(message.getHeader("Subject", null)).isEqualTo("Lab results for your review");
    }

    @Test
    @DisplayName("should measure the longest run of non-whitespace characters")
    void shouldReturnLongestRun_forWhitespaceSeparatedText() {
        assertThat(SMTPEmailSender.longestUnbrokenRun("ab  cdef g")).isEqualTo(4);
        assertThat(SMTPEmailSender.longestUnbrokenRun("")).isZero();
    }
}
