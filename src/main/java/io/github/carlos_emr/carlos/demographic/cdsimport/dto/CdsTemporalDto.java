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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;

/** Source temporal meaning. A partial date is an interval, never an asserted first day. */
public record CdsTemporalDto(Precision precision, String original, LocalDate earliest,
        LocalDate latest, LocalDateTime localDateTime, OffsetDateTime offsetDateTime, int appliedShiftDays) {
    public enum Precision { UNKNOWN, YEAR, MONTH, FULL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME }

    public CdsTemporalDto(Precision precision, String original, LocalDate earliest,
            LocalDate latest, LocalDateTime localDateTime, OffsetDateTime offsetDateTime) {
        this(precision, original, earliest, latest, localDateTime, offsetDateTime, 0);
    }

    public CdsTemporalDto {
        if (precision == null || (precision != Precision.UNKNOWN && (original == null
                || earliest == null || latest == null || earliest.isAfter(latest)))) {
            throw new IllegalArgumentException("Invalid CDS temporal representation");
        }
    }

    public static CdsTemporalDto unknown() {
        return new CdsTemporalDto(Precision.UNKNOWN, null, null, null, null, null);
    }

    public boolean exactCalendarDate() {
        return precision == Precision.FULL_DATE || precision == Precision.LOCAL_DATE_TIME
                || precision == Precision.OFFSET_DATE_TIME;
    }

    /** Legacy DATE storage anchor. Its precision must be written in the same clinical unit. */
    public Date dateStorageAnchor() {
        return earliest == null ? null : dateStorageAnchor(earliest);
    }

    /** SQL DATE has civil meaning; the legacy Calendar cutover must not normalize another day. */
    public static java.sql.Date dateStorageAnchor(LocalDate value) {
        java.sql.Date result = java.sql.Date.valueOf(value);
        if (!result.toLocalDate().equals(value)
                || !legacyDay(result, TimeZone.getDefault(), Locale.ROOT).equals(value.toString())
                || !legacyDay(result, TimeZone.getDefault(), Locale.getDefault(Locale.Category.FORMAT)).equals(value.toString()))
            throw unrepresentable(false);
        return result;
    }

    /** TIMESTAMP storage requires an explicit clinic/source zone when the source has no offset. */
    public Date timestampStorageAnchor(ZoneId sourceZone) {
        if (precision == Precision.UNKNOWN) return null;
        if (offsetDateTime != null) return timestampStorageInstant(offsetDateTime.toInstant(), sourceZone);
        if (localDateTime == null) {
            // A date supplies no clock. Use the first valid clock on that same civil day;
            // midnight gaps must not discard an otherwise representable calendar date.
            LocalDateTime anchor = earliest.atStartOfDay(sourceZone).toLocalDateTime();
            if (!anchor.toLocalDate().equals(earliest)) throw unrepresentable(false);
            Date result = legacyCivil(anchor, sourceZone, false);
            if (!legacyDay(result, TimeZone.getTimeZone(sourceZone), Locale.ROOT).equals(earliest.toString())
                    || !legacyDay(result, TimeZone.getTimeZone(sourceZone), Locale.getDefault(Locale.Category.FORMAT)).equals(earliest.toString()))
                throw unrepresentable(false);
            return result;
        }
        LocalDateTime value = localDateTime;
        var offsets = sourceZone.getRules().getValidOffsets(value);
        if (offsets.size() != 1) {
            throw new CdsClinicalException("CDS_LOCAL_TIME_REQUIRES_REVIEW", "Date");
        }
        return timestampStorageInstant(value.toInstant(offsets.get(0)), sourceZone);
    }

    /** An exact instant must survive the native unqualified DATETIME/legacy Calendar round trip. */
    public static Date timestampStorageInstant(Instant instant, ZoneId nativeZone) {
        LocalDateTime expected = LocalDateTime.ofInstant(instant, nativeZone);
        Date actual = Date.from(instant);
        Date reversed = legacyCivil(expected, nativeZone, true);
        if (reversed.getTime() != actual.getTime()
                || !legacyDay(actual, TimeZone.getTimeZone(nativeZone), Locale.ROOT).equals(expected.toLocalDate().toString())
                || !legacyDay(actual, TimeZone.getTimeZone(nativeZone), Locale.getDefault(Locale.Category.FORMAT)).equals(expected.toLocalDate().toString()))
            throw unrepresentable(true);
        return actual;
    }

    /** A synthetic calendar anchor is explicitly distinct from an asserted clinical timestamp. */
    public boolean calendarAnchorAdjusted(ZoneId zone) {
        if (precision != Precision.YEAR && precision != Precision.MONTH && precision != Precision.FULL_DATE) return false;
        var first = earliest.atStartOfDay(zone);
        return !first.toLocalDate().equals(earliest)
                || !first.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                || !timestampStorageAnchor(zone).toInstant().equals(first.toInstant());
    }

    private static Date legacyCivil(LocalDateTime value, ZoneId zone, boolean timestamp) {
        if (value.getYear() < 1 || value.getYear() > 9999) throw unrepresentable(timestamp);
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone(zone), Locale.ROOT);
        calendar.clear(); calendar.setLenient(false);
        calendar.set(value.getYear(), value.getMonthValue() - 1, value.getDayOfMonth(),
                value.getHour(), value.getMinute(), value.getSecond());
        calendar.set(Calendar.MILLISECOND, value.getNano() / 1_000_000);
        try { return calendar.getTime(); }
        catch (IllegalArgumentException invalid) { throw unrepresentable(timestamp); }
    }

    private static String legacyDay(Date date, TimeZone zone, Locale locale) {
        var formatter = new java.text.SimpleDateFormat("yyyy-MM-dd", locale);
        formatter.setTimeZone(zone);
        return formatter.format(date);
    }

    private static CdsClinicalException unrepresentable(boolean timestamp) {
        return new CdsClinicalException(timestamp ? "CDS_NATIVE_TIMESTAMP_REQUIRES_REVIEW"
                : "CDS_NATIVE_DATE_REQUIRES_REVIEW", "Date");
    }

    public String legacyPartialFormat() {
        return switch (precision) {
            case YEAR -> "YYYY";
            case MONTH -> "YYYY-MM";
            default -> null;
        };
    }
}
