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

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmailLog} recipient handling and consent audit snapshots.
 *
 * <p>Focus: {@link EmailLog#getToEmail()} must stay null-safe so a legacy row with a NULL
 * {@code toEmail} column cannot NPE the Manage Emails view (issue #3112 follow-up).</p>
 *
 * @since 2026-07-06
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailLog")
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

    @Test
    @DisplayName("should return an empty array when recipients are explicitly set to an empty list")
    void shouldReturnEmptyArray_whenToEmailSetToEmptyArray() {
        // The setter coalesces an empty array to "", which must still read back as an empty array
        // (not a stray one-element [""]).
        EmailLog emailLog = new EmailLog();
        emailLog.setToEmail(new String[0]);

        assertThat(emailLog.getToEmail()).isEmpty();
    }

    @Test
    @DisplayName("should defensively copy consent last update date")
    void shouldDefensivelyCopy_whenConsentLastUpdateDateIsMutated() {
        Date sourceDate = new Date(1_000L);
        EmailLog emailLog = new EmailLog();
        emailLog.setConsentLastUpdateDate(sourceDate);

        sourceDate.setTime(2_000L);
        Date returnedDate = emailLog.getConsentLastUpdateDate();
        returnedDate.setTime(3_000L);

        assertThat(emailLog.getConsentLastUpdateDate()).isEqualTo(new Date(1_000L));
    }

    @Test
    @DisplayName("should localize every consent status in supported bundles")
    void shouldLocalizeEveryConsentStatus_forSupportedBundles() {
        List<Locale> supportedLocales = List.of(
                Locale.ENGLISH,
                Locale.FRENCH,
                Locale.forLanguageTag("es"),
                Locale.forLanguageTag("pl"),
                Locale.forLanguageTag("pt-BR"));

        for (Locale locale : supportedLocales) {
            ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale);
            for (EmailLog.EmailConsentStatus status : EmailLog.EmailConsentStatus.values()) {
                assertThat(bundle.getString(status.getMessageKey())).isNotBlank();
            }
        }
    }
}
