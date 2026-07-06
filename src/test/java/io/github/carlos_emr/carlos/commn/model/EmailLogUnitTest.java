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

/**
 * Unit tests for {@link EmailLog} recipient handling.
 *
 * <p>Focus: {@link EmailLog#getToEmail()} must stay null-safe so a legacy row with a NULL
 * {@code toEmail} column cannot NPE the Manage Emails view (issue #3112 follow-up).</p>
 */
@Tag("unit")
class EmailLogUnitTest {

    @Test
    @DisplayName("should return an empty array when the recipient field was never populated")
    void shouldReturnEmptyArray_whenToEmailNeverSet() {
        // A no-arg (JPA/legacy) EmailLog leaves the toEmail field null.
        EmailLog emailLog = new EmailLog();

        assertThat(emailLog.getToEmail()).isEmpty();
    }

    @Test
    @DisplayName("should round-trip the recipient list through the semicolon-joined field")
    void shouldReturnRecipients_whenToEmailSet() {
        EmailLog emailLog = new EmailLog();
        emailLog.setToEmail(new String[] {"a@example.com", "b@example.com"});

        assertThat(emailLog.getToEmail()).containsExactly("a@example.com", "b@example.com");
    }
}
