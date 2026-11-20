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
package io.github.carlos_emr.carlos.casemgmt.service;

import java.time.Instant;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * Whole calendar days selected in the chart print dialog, with an exclusive upper bound.
 * The next local midnight includes the entire final day even across daylight-saving changes.
 *
 * @param startInclusive first instant to include
 * @param endExclusive first instant to exclude
 * @since 2026-10-03
 */
// Calendar/Date are compatibility boundaries for existing print callers and DAO entities;
// all day-boundary calculations use java.time.
@SuppressWarnings("java:S2143")
public record ChartPrintDateRange(Instant startInclusive, Instant endExclusive) {
    /**
     * Parses one complete dialog date, accepting the one-digit day emitted by Today.
     *
     * @param value date in d-MMM-yyyy or dd-MMM-yyyy format
     * @param locale resolved chart locale (the calendar month language)
     * @return the selected local day
     * @throws IllegalArgumentException for missing, impossible or partially parsed dates
     */
    public static Calendar parseDialogDate(String value, Locale locale) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Print date is required");
        }
        // Flatpickr omits the terminal dot used by CLDR for French/Portuguese months.
        String[] parts = value.split("-", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid print date");
        }
        String month = parts[1].replaceFirst("\\.$", "").replaceAll("(?i)^sept$", "sep");
        String normalized = parts[0] + "-" + month + "-" + parts[2];
        try {
            return parseLocalizedDialogDate(normalized, locale);
        } catch (DateTimeException _) {
            // Today is supplied by ChartNotes.jsp in the JVM format locale. Preserve that
            // existing wire format when it differs from the negotiated calendar language.
            try {
                return parseLocalizedDialogDate(normalized, Locale.getDefault(Locale.Category.FORMAT));
            } catch (DateTimeException second) {
                throw new IllegalArgumentException("Invalid print date", second);
            }
        }
    }

    private static Calendar parseLocalizedDialogDate(String value, Locale locale) {
        Map<Long, String> months = new HashMap<>();
        for (Month month : Month.values()) {
            months.put((long) month.getValue(), month.getDisplayName(TextStyle.SHORT, locale).replace(".", "").replaceAll("(?i)^sept$", "sep"));
        }
        var formatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                .appendLiteral('-').appendText(ChronoField.MONTH_OF_YEAR, months)
                .appendLiteral('-').appendValue(ChronoField.YEAR, 4)
                .toFormatter(locale).withResolverStyle(ResolverStyle.STRICT);
        return GregorianCalendar.from(LocalDate.parse(value, formatter)
                .atStartOfDay(ZoneId.systemDefault()));
    }

    /**
     * Builds the interval in the calendars' time zones without mutating either calendar.
     *
     * @param start first selected calendar day
     * @param end last selected calendar day
     * @return the complete, inclusive range of calendar days
     * @throws IllegalArgumentException if the end precedes the start
     */
    public static ChartPrintDateRange from(Calendar start, Calendar end) {
        Objects.requireNonNull(start, "Print start date is required");
        Objects.requireNonNull(end, "Print end date is required");
        ZoneId zone = start.getTimeZone().toZoneId();
        LocalDate first = start.toInstant().atZone(zone).toLocalDate();
        LocalDate last = end.toInstant().atZone(end.getTimeZone().toZoneId()).toLocalDate();
        if (last.isBefore(first)) {
            throw new IllegalArgumentException("Print end date precedes start date");
        }
        return new ChartPrintDateRange(first.atStartOfDay(zone).toInstant(),
                last.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /**
     * Tests an observation timestamp against the selected days.
     *
     * @param observation observation timestamp, or null for an undated note
     * @return true if the observation falls within the selected days
     */
    public boolean contains(Date observation) {
        if (observation == null) return false;
        Instant instant = observation.toInstant();
        return !instant.isBefore(startInclusive) && instant.isBefore(endExclusive);
    }
}
