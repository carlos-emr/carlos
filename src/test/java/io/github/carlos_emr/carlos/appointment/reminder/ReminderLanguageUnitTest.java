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
package io.github.carlos_emr.carlos.appointment.reminder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** #5887: the reminder is written in the patient's language where CARLOS has it, else English. */
@Tag("unit")
@Tag("appointment")
@DisplayName("Appointment reminder language")
class ReminderLanguageUnitTest {

    @Test
    void shouldPreferTheSpokenLanguage_whenCarlosHasItsTranslation() {
        assertThat(ReminderLanguage.of("English", "Polish")).isEqualTo(Locale.of("pl"));
        assertThat(ReminderLanguage.of("English", "Spanish; Castilian")).isEqualTo(Locale.of("es"));
        assertThat(ReminderLanguage.of("French", "Portuguese")).isEqualTo(Locale.of("pt", "BR"));
        assertThat(ReminderLanguage.of("English", " French ")).isEqualTo(Locale.FRENCH);
    }

    @Test
    void shouldFallBackToTheOfficialLanguage_thenEnglish() {
        assertThat(ReminderLanguage.of("French", "Tagalog")).isEqualTo(Locale.FRENCH);
        assertThat(ReminderLanguage.of("French", null)).isEqualTo(Locale.FRENCH);
        assertThat(ReminderLanguage.of("Other", "Tagalog")).isEqualTo(Locale.ENGLISH);
        assertThat(ReminderLanguage.of(null, null)).isEqualTo(Locale.ENGLISH);
        assertThat(ReminderLanguage.of("french", "polish")).as("stored names match exactly").isEqualTo(Locale.ENGLISH);
    }
}
