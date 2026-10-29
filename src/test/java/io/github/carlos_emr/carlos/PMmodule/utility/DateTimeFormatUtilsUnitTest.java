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
package io.github.carlos_emr.carlos.PMmodule.utility;

import io.github.carlos_emr.carlos.test.support.ConcurrentRounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DateTimeFormatUtils} is called from concurrent requests (the Population Report and new
 * demographic records), so its formatters must not be shared mutable state (issue #5301). Dates
 * are the clinic's: the JVM default zone, which the packaged service sets to the clinic's zone.
 */
@Tag("unit")
@DisplayName("DateTimeFormatUtils")
class DateTimeFormatUtilsUnitTest {

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

    /** The output the short Canadian time format has always produced, from a call-local instance. */
    private static String legacyShortTime(Date time) {
        return DateFormat.getTimeInstance(DateFormat.SHORT, Locale.CANADA).format(time);
    }

    @Test
    void shouldFormatAndParseEveryDate_whenCalledConcurrently() throws Exception {
        LocalDate first = LocalDate.of(1990, 1, 1);

        var failures = ConcurrentRounds.run(8, 1500, (thread, round) -> {
            // Every call works on a date no other call uses, so a crossed result cannot pass.
            LocalDate date = first.plusDays(thread * 2000L + round);
            Date midnight = clinicMidnight(date);

            assertThat(DateTimeFormatUtils.getStringFromDate(midnight)).isEqualTo(date.toString());
            assertThat(DateTimeFormatUtils.getDateFromString(date.toString())).isEqualTo(midnight);
            assertThat(DateTimeFormatUtils.getDateFromDate(new Date(midnight.getTime() + 13 * 3_600_000L + round)))
                    .isEqualTo(midnight);
        });

        assertThat(failures).isEmpty();
    }

    @Test
    void shouldFormatAndParseEveryTime_whenCalledConcurrently() throws Exception {
        var failures = ConcurrentRounds.run(8, 1500, (thread, round) -> {
            LocalTime time = LocalTime.MIDNIGHT.plusMinutes((thread * 181L + round) % (24 * 60));
            Date onEpochDay = Date.from(LocalDate.EPOCH.atTime(time).atZone(ZoneId.systemDefault()).toInstant());
            Date sameTimeToday = Date.from(LocalDate.of(2026, 10, 29).atTime(time).plusSeconds(round % 60)
                    .atZone(ZoneId.systemDefault()).toInstant());

            String formatted = DateTimeFormatUtils.getStringFromTime(sameTimeToday);
            assertThat(formatted).isEqualTo(legacyShortTime(sameTimeToday));
            assertThat(DateTimeFormatUtils.getTimeFromString(formatted)).isEqualTo(onEpochDay);
            assertThat(DateTimeFormatUtils.getTimeFromDate(sameTimeToday)).isEqualTo(onEpochDay);
        });

        assertThat(failures).isEmpty();
    }

    @Test
    void shouldFormatEveryDateWithTheCallersPattern_whenCalledConcurrently() throws Exception {
        // The Population Report's day/month/year pattern, shared by every request.
        DateTimeFormatter reportDate = DateTimeFormatter.ofPattern("dd/MM/uuuu");
        LocalDate first = LocalDate.of(2001, 1, 1);

        var failures = ConcurrentRounds.run(8, 1500, (thread, round) -> {
            LocalDate date = first.plusDays(thread * 2000L + round);
            Date lateEvening = Date.from(date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant());

            assertThat(DateTimeFormatUtils.getStringFromDate(lateEvening, reportDate))
                    .isEqualTo("%02d/%02d/%04d".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear()));
        });

        assertThat(failures).isEmpty();
    }

    @Test
    void shouldReturnEmptyText_forMissingDateOrTime() {
        assertThat(DateTimeFormatUtils.getStringFromDate(null, DateTimeFormatter.ofPattern("dd/MM/uuuu"))).isEmpty();
        assertThat(DateTimeFormatUtils.getStringFromDate(null)).isEmpty();
        assertThat(DateTimeFormatUtils.getStringFromTime(null)).isEmpty();
        assertThat(DateTimeFormatUtils.getDateFromDate(null)).isNull();
    }

    @Test
    void shouldReturnNoDate_forMissingText() {
        assertThat(DateTimeFormatUtils.getDateFromString(null)).isNull();
        assertThat(DateTimeFormatUtils.getDateFromString("")).isNull();
        assertThat(DateTimeFormatUtils.getTimeFromString(null)).isNull();
        assertThat(DateTimeFormatUtils.getTimeFromString("")).isNull();
    }

    @Test
    void shouldThrowIllegalState_forTextThatIsNotADate() {
        assertThatThrownBy(() -> DateTimeFormatUtils.getDateFromString("2026/05/01"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DateTimeFormatUtils.getTimeFromString("noon"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectImpossibleCalendarDates_insteadOfRollingThemForward() {
        // The shared lenient formatter read 30 February as 2 March and month 13 as the next January.
        assertThatThrownBy(() -> DateTimeFormatUtils.getDateFromString("2026-02-30"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DateTimeFormatUtils.getDateFromString("2026-13-01"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DateTimeFormatUtils.getDateFromString("2026-05-01 then"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldParseLeapDayAndSingleDigitFields_forValidDates() throws Exception {
        inZone(CLINIC_ZONE, () -> {
            assertThat(DateTimeFormatUtils.getDateFromString("2028-02-29"))
                    .isEqualTo(clinicMidnight(LocalDate.of(2028, 2, 29)));
            assertThat(DateTimeFormatUtils.getDateFromString("2026-5-1"))
                    .isEqualTo(clinicMidnight(LocalDate.of(2026, 5, 1)));
            assertThat(DateTimeFormatUtils.getDateFromString("2100-01-01"))
                    .isEqualTo(clinicMidnight(LocalDate.of(2100, 1, 1)));
        });
    }

    @Test
    void shouldParseDatesAtClinicMidnight_onDaylightSavingChangeDays() throws Exception {
        inZone(CLINIC_ZONE, () -> {
            // Clocks spring forward at 02:00 on 8 March 2026 and fall back at 02:00 on 1 November.
            assertThat(DateTimeFormatUtils.getDateFromString("2026-03-08").toInstant())
                    .isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
            assertThat(DateTimeFormatUtils.getDateFromString("2026-11-01").toInstant())
                    .isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
        });
    }

    @Test
    void shouldFormatTheClinicDate_whenTheUtcDateHasAlreadyChanged() throws Exception {
        inZone(CLINIC_ZONE, () -> {
            Date lateEvening = Date.from(Instant.parse("2026-03-01T04:30:00Z"));

            assertThat(DateTimeFormatUtils.getStringFromDate(lateEvening)).isEqualTo("2026-02-28");
            assertThat(DateTimeFormatUtils.getDateFromDate(lateEvening).toInstant())
                    .isEqualTo(Instant.parse("2026-02-28T05:00:00Z"));
            assertThat(DateTimeFormatUtils.getStringFromTime(lateEvening)).isEqualTo(legacyShortTime(lateEvening));
        });
        inZone("UTC", () -> assertThat(DateTimeFormatUtils.getStringFromDate(
                Date.from(Instant.parse("2026-03-01T04:30:00Z")))).isEqualTo("2026-03-01"));
    }

    @Test
    void shouldFormatClinicTime_afterTheSpringForwardGap() throws Exception {
        inZone(CLINIC_ZONE, () -> {
            Date afterGap = Date.from(Instant.parse("2026-03-08T07:30:00Z"));

            assertThat(DateTimeFormatUtils.getStringFromTime(afterGap)).isEqualTo(legacyShortTime(afterGap));
            assertThat(DateTimeFormatUtils.getTimeFromDate(afterGap)).isEqualTo(
                    Date.from(LocalDate.EPOCH.atTime(3, 30).atZone(ZoneId.systemDefault()).toInstant()));
        });
    }

    @Test
    void shouldParseTimes_inTheFormsTheLegacyParserAccepted() throws Exception {
        inZone(CLINIC_ZONE, () -> {
            Date eightAm = Date.from(LocalDate.EPOCH.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant());
            Date fivePastEightPm = Date.from(LocalDate.EPOCH.atTime(20, 5).atZone(ZoneId.systemDefault()).toInstant());

            assertThat(DateTimeFormatUtils.getTimeFromString("8:00 a.m.")).isEqualTo(eightAm);
            assertThat(DateTimeFormatUtils.getTimeFromString("8:00 a.m.")).isEqualTo(eightAm);
            assertThat(DateTimeFormatUtils.getTimeFromString("8:00 A.M.")).isEqualTo(eightAm);
            assertThat(DateTimeFormatUtils.getTimeFromString("08:05 p.m.")).isEqualTo(fivePastEightPm);
        });
    }
}
