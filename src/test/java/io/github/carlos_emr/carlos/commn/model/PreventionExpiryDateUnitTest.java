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

import io.github.carlos_emr.carlos.commn.interfaces.Immunization.ImmunizationProperty;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.support.ConcurrentRounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An immunization's expiry date is kept as {@code yyyy-MM-dd} text in the prevention's extension
 * rows and read back as the clinic's midnight on that date, for the FHIR immunization export.
 * Concurrent requests convert it at once, so the conversion must not share mutable formatters, and
 * text that is not a calendar date reads as no date rather than as a neighbouring one (issue #5301).
 */
@Tag("unit")
@Tag("prevention")
@DisplayName("Prevention immunization expiry date")
class PreventionExpiryDateUnitTest {

    private static final String CLINIC_ZONE = "America/Toronto";

    @FunctionalInterface
    private interface ZoneCheck {
        void run() throws Exception;
    }

    /** Runs {@code check} with {@code zone} as the JVM default, as the packaged service runs. */
    private static void inZone(String zone, ZoneCheck check) throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            check.run();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static Date clinicMidnight(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** A prevention loaded with {@code text} as its stored expiry date extension. */
    private static Prevention withStoredExpiry(String text) {
        PreventionExt ext = new PreventionExt();
        ext.setKeyval(ImmunizationProperty.expiryDate.name());
        ext.setVal(text);
        Prevention prevention = new Prevention();
        prevention.setPreventionExtendedProperty(ext);
        return prevention;
    }

    /** The text {@link Prevention#setExpiryDate} stored as the expiry extension row. */
    private static String storedExpiry(Prevention prevention) {
        return prevention.getPreventionExts().stream()
                .filter(ext -> ImmunizationProperty.expiryDate.name().equals(ext.getkeyval()))
                .map(PreventionExt::getVal)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    @Test
    void shouldStoreAndReadEveryExpiryDate_whenCalledConcurrently() throws Exception {
        LocalDate first = LocalDate.of(2020, 1, 1);

        var failures = ConcurrentRounds.run(8, 1500, (thread, round) -> {
            // Every call works on a date no other call uses, so a crossed result cannot pass.
            LocalDate expiry = first.plusDays(thread * 2000L + round);
            Prevention prevention = new Prevention();

            prevention.setExpiryDate(new Date(clinicMidnight(expiry).getTime() + 9 * 3_600_000L + round));
            assertThat(storedExpiry(prevention)).isEqualTo(expiry.toString());

            prevention.setPreventionExtendedProperties();
            assertThat(prevention.getExpiryDate()).isEqualTo(clinicMidnight(expiry));
        });

        assertThat(failures).isEmpty();
    }

    @Test
    void shouldStoreEmptyText_whenTheExpiryDateIsCleared() {
        Prevention prevention = new Prevention();

        prevention.setExpiryDate(null);

        assertThat(storedExpiry(prevention)).isEmpty();
    }

    @Test
    void shouldReadNoDateWithoutWarning_whenNoExpiryWasRecorded() {
        try (LogCapture logs = LogCapture.forLogger(Prevention.class)) {
            assertThat(new Prevention().getExpiryDate()).isNull();
            assertThat(withStoredExpiry("").getExpiryDate()).isNull();
            assertThat(withStoredExpiry("   ").getExpiryDate()).isNull();

            assertThat(logs.messages()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026/05/01", "May 2026", "01-05-2026", "26-05-01", "2026-05"})
    void shouldReadNoDateAndWarn_forTextThatIsNotADate(String text) {
        try (LogCapture logs = LogCapture.forLogger(Prevention.class)) {
            assertThat(withStoredExpiry(text).getExpiryDate()).isNull();

            assertThat(logs.messages()).singleElement().asString().contains("expiry date");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "2026-00-10", "2026-05-01junk", "2026-05-01T00:00"})
    void shouldReadNoDate_forImpossibleOrTrailingTextInsteadOfANeighbouringDate(String text) {
        // The shared lenient formatter read 30 February as 2 March and ignored trailing text.
        try (LogCapture logs = LogCapture.forLogger(Prevention.class)) {
            assertThat(withStoredExpiry(text).getExpiryDate()).isNull();

            assertThat(logs.messages()).hasSize(1);
        }
    }

    @Test
    void shouldReadHandTypedDates_withSingleDigitFieldsOrSurroundingSpace() throws Exception {
        inZone(CLINIC_ZONE, () -> {
            assertThat(withStoredExpiry("2026-5-1").getExpiryDate()).isEqualTo(clinicMidnight(LocalDate.of(2026, 5, 1)));
            assertThat(withStoredExpiry(" 2026-05-01 ").getExpiryDate()).isEqualTo(clinicMidnight(LocalDate.of(2026, 5, 1)));
            assertThat(withStoredExpiry("2028-02-29").getExpiryDate()).isEqualTo(clinicMidnight(LocalDate.of(2028, 2, 29)));
        });
    }

    @Test
    void shouldStoreTheClinicDate_whenTheUtcDateHasAlreadyChanged() throws Exception {
        Date lateEvening = Date.from(Instant.parse("2026-03-01T04:30:00Z"));

        inZone(CLINIC_ZONE, () -> {
            Prevention prevention = new Prevention();
            prevention.setExpiryDate(lateEvening);
            assertThat(storedExpiry(prevention)).isEqualTo("2026-02-28");
        });
        inZone("UTC", () -> {
            Prevention prevention = new Prevention();
            prevention.setExpiryDate(lateEvening);
            assertThat(storedExpiry(prevention)).isEqualTo("2026-03-01");
        });
    }

    @Test
    void shouldReadTheExpiryAtClinicMidnight_onDaylightSavingChangeDays() throws Exception {
        inZone(CLINIC_ZONE, () -> {
            assertThat(withStoredExpiry("2026-03-08").getExpiryDate().toInstant())
                    .isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
            assertThat(withStoredExpiry("2026-11-01").getExpiryDate().toInstant())
                    .isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
        });
    }

    @Test
    void shouldStoreTheCalendarDate_forASqlDateExpiry() throws Exception {
        // java.sql.Date refuses toInstant(); a JDBC-loaded value must still store its date.
        inZone(CLINIC_ZONE, () -> {
            Prevention prevention = new Prevention();
            prevention.setExpiryDate(java.sql.Date.valueOf(LocalDate.of(2027, 6, 30)));
            assertThat(storedExpiry(prevention)).isEqualTo("2027-06-30");
        });
    }

    @Test
    void shouldKeepTheWarningOnOneLine_whenTheStoredTextHasLineBreaks() {
        try (LogCapture logs = LogCapture.forLogger(Prevention.class)) {
            assertThat(withStoredExpiry("2026-05-01\nFORGED log line").getExpiryDate()).isNull();

            assertThat(logs.messages()).singleElement().asString().doesNotContain("\n");
        }
    }
}
