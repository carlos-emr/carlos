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
package io.github.carlos_emr.carlos.commn.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The emailLog BLOB columns are nullable. A row written outside {@link EmailLog} (a legacy or
 * adopted database, an import) can hold NULL, and resending such an email from Manage Emails
 * returned a 500. Found on a packaged install by the email-field-length browser check.
 */
@Tag("unit")
@Tag("email")
@DisplayName("EmailLog BLOB column decoding")
class EmailLogBlobUnitTest {

    @Test
    @DisplayName("should read NULL BLOB columns as empty text")
    void shouldReturnEmptyText_whenBlobColumnsAreNull() {
        EmailLog emailLog = new EmailLog();
        ReflectionTestUtils.setField(emailLog, "body", null);
        ReflectionTestUtils.setField(emailLog, "encryptedMessage", null);
        ReflectionTestUtils.setField(emailLog, "internalComment", null);

        assertThat(emailLog.getBody()).isEmpty();
        assertThat(emailLog.getEncryptedMessage()).isEmpty();
        assertThat(emailLog.getInternalComment()).isEmpty();
    }

    @Test
    @DisplayName("should round-trip stored text through the Base64 BLOB columns")
    void shouldDecodeStoredText_whenBlobColumnsAreSet() {
        EmailLog emailLog = new EmailLog();
        emailLog.setBody("Body é");
        emailLog.setEncryptedMessage("Secret");
        emailLog.setInternalComment("Staff note");

        assertThat(emailLog.getBody()).isEqualTo("Body é");
        assertThat(emailLog.getEncryptedMessage()).isEqualTo("Secret");
        assertThat(emailLog.getInternalComment()).isEqualTo("Staff note");
    }
}
