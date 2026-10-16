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
package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link NoteObservationDate}, the one format the eChart encounter-date field is
 * rendered in and read back from (issue #4711).
 *
 * <p>The field used to be rendered by {@code <fmt:formatDate>} in the browser's language and read
 * back in English only. A browser language with no date format made JSTL print
 * {@code java.sql.Timestamp.toString()} ("2026-10-15 19:14:08.0") instead, and the save of an
 * edited signed note answered HTTP 500.</p>
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("NoteObservationDate")
class NoteObservationDateUnitTest {

    private static Date at(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void shouldFormatInEnglishCanonicalPattern_forStoredDate() {
        assertThat(NoteObservationDate.format(at(2026, 10, 15, 9, 4))).isEqualTo("15-Oct-2026 09:04");
    }

    @Test
    void shouldFormatHibernateTimestampWithoutSeconds_forStoredNote() {
        // Hibernate loads observation_date as java.sql.Timestamp; its toString() is what leaked.
        Timestamp stored = Timestamp.valueOf("2026-10-15 19:14:08.0");
        assertThat(NoteObservationDate.format(stored)).isEqualTo("15-Oct-2026 19:14");
    }

    @Test
    void shouldFormatEmpty_forNullDate() {
        assertThat(NoteObservationDate.format(null)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldReturnNull_forBlankText(String text) throws ParseException {
        assertThat(NoteObservationDate.parse(text, Locale.ENGLISH)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "15-Oct-2026 19:14, en",
            "15-OCT-2026 19:14, en",
            "15-oct-2026 19:14, fr",
            "15-Oct-2026 19:14, fr-CA",
            "15-Oct-2026 19:14, de-DE",
            "' 15-Oct-2026  19:14 ', en"
    })
    void shouldParseCanonicalEnglishText_whateverTheInterfaceLanguage(String text, String languageTag) throws ParseException {
        assertThat(NoteObservationDate.parse(text, Locale.forLanguageTag(languageTag))).isEqualTo(at(2026, 10, 15, 19, 14));
    }

    @Test
    void shouldRoundTripFormattedValue_forEveryMonth() throws ParseException {
        for (int month = 1; month <= 12; month++) {
            Date date = at(2026, month, 1, 7, 30);
            assertThat(NoteObservationDate.parse(NoteObservationDate.format(date), Locale.CANADA_FRENCH)).isEqualTo(date);
        }
    }

    @ParameterizedTest
    @CsvSource({
            // The localized calendar writes the interface language's month names, with or without
            // the abbreviation period the JDK uses.
            "15-févr.-2026 08:00, fr, 2",
            "15-févr-2026 08:00, fr, 2",
            "15-juil-2026 08:00, fr-CA, 7",
            "15-juill.-2026 08:00, fr-CA, 7",
            "15-Okt.-2026 08:00, de-DE, 10",
            "15-ene-2026 08:00, es, 1",
            "15-Set-2026 08:00, pt-BR, 9",
            "15-janvier-2026 08:00, fr, 1"
    })
    void shouldParseInterfaceLanguageMonths_whenCalendarWroteThem(String text, String languageTag, int month) throws ParseException {
        assertThat(NoteObservationDate.parse(text, Locale.forLanguageTag(languageTag))).isEqualTo(at(2026, month, 15, 8, 0));
    }

    @Test
    void shouldAcceptSingleDigitDayAndHour_forLegacyPattern() throws ParseException {
        assertThat(NoteObservationDate.parse("5-Oct-2026 9:05", Locale.ENGLISH)).isEqualTo(at(2026, 10, 5, 9, 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-10-15 19:14:08.0",   // java.sql.Timestamp.toString(), the value from #4711
            "2026-10-15 19:14",
            "15-Oct-2026",
            "15-Oct-2026 19:14:08",
            "15-Oct-2026 19:14 extra",
            "31-Feb-2026 10:00",
            "32-Oct-2026 10:00",
            "15-Oct-2026 24:00",
            "15-Oct-2026 10:60",
            "15-Okt-2026 10:00",       // German, but the interface is English
            "15-Foo-2026 10:00",
            "15-10-2026 10:00"
    })
    void shouldRejectText_whenNotAnEncounterDate(String text) {
        assertThatThrownBy(() -> NoteObservationDate.parse(text, Locale.ENGLISH)).isInstanceOf(ParseException.class);
    }

    @Test
    void shouldParseEnglishOnly_whenInterfaceLocaleIsNull() throws ParseException {
        assertThat(NoteObservationDate.parse("15-Oct-2026 19:14", null)).isEqualTo(at(2026, 10, 15, 19, 14));
        assertThatThrownBy(() -> NoteObservationDate.parse("15-févr.-2026 19:14", null)).isInstanceOf(ParseException.class);
    }
}
