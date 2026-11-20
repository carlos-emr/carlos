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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAppointmentTimingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DateTimeException;

/**
 * The schedule's date, start clock and slot length as CDS Appointments can hold them. Nothing is
 * invented and no ordinary booking is held:
 *
 * <ul>
 *   <li>AppointmentDate and AppointmentTime are the stored date and start clock, seconds included.
 *   <li>Duration is the slot's length in whole minutes. A slot whose end is not on a minute counts
 *       its started minute ({@link Length#PARTIAL_MINUTE}). A stored end earlier than the start is
 *       an end on the next day, as the add form stores 23:30 for 60 minutes
 *       ({@link Length#NEXT_DAY}). The form's duration 0 (an end one minute before the start, which
 *       also is what a full 24 hours stores), an imported end equal to its start, or an end that
 *       is not a time of day states no positive length: Duration is left out
 *       ({@link Length#NOT_STATED}).
 *   <li>A start that has no single whole-minute offset in the clinical zone (inside the
 *       spring-forward gap or the fall-back overlap, a slot spanning a transition, or a date
 *       before standard time) is written as the wall-clock time with no offset, which
 *       {@code xs:time} allows.
 * </ul>
 *
 * <p>In every case but {@link Length#EXACT} the exact stored start and end travel in
 * {@code CARLOS.Appointment.Source.v1}. Only a stored date or start that is not a calendar date or
 * a time of day at all (a zero date, {@code 838:59:59}) is held, as {@value #NOT_A_CALENDAR_TIME}:
 * AppointmentDate and AppointmentTime are required and no value could be written for them.
 */
public final class CdsExportAppointmentTimingMapper {
    /** Hold code of a booking whose stored date or start is not a calendar date or time of day. */
    public static final String NOT_A_CALENDAR_TIME =
            "CDS_EXPORT_APPOINTMENT_START_NOT_A_CALENDAR_TIME";

    private static final String CLOCK = "(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]";

    public enum EndConvention {
        NATIVE_INCLUSIVE,
        IMPORTED_EXCLUSIVE
    }

    /** How Duration was derived from the stored end. */
    public enum Length {
        /** Same day, whole minutes: Duration is exactly the slot. */
        EXACT("EXACT_SAME_DAY_WHOLE_MINUTE_DURATION"),
        /** The end is not on a minute: the started minute is counted. */
        PARTIAL_MINUTE("STARTED_MINUTE_COUNTED_EXACT_END_IN_APPOINTMENT_SOURCE_RECORD"),
        /** The stored end is earlier than the start: the slot ends on the next day. */
        NEXT_DAY("END_ON_NEXT_DAY_EXACT_END_IN_APPOINTMENT_SOURCE_RECORD"),
        /** No positive length is stored: Duration is left out. */
        NOT_STATED("NO_POSITIVE_LENGTH_DURATION_LEFT_OUT_EXACT_END_IN_APPOINTMENT_SOURCE_RECORD");
        private final String rule;

        Length(String rule) {
            this.rule = rule;
        }

        /** The field-decision rule of {@code end_time}. */
        public String rule() {
            return rule;
        }
    }

    /** The timing and the rule its Duration follows. */
    public record Mapped(CdsExportAppointmentTimingDto value, Length length) {
        public Mapped {
            java.util.Objects.requireNonNull(value);
            java.util.Objects.requireNonNull(length);
        }

        @Override
        public String toString() {
            return "CdsExportAppointmentTimingMapper.Mapped[protected]";
        }
    }

    public CdsExportAppointmentTimingDto map(
            String date, String start, String end, ZoneId clinicalZone) throws IOException {
        return map(date, start, end, clinicalZone, EndConvention.NATIVE_INCLUSIVE);
    }

    public CdsExportAppointmentTimingDto map(
            String date, String start, String end, ZoneId clinicalZone, EndConvention convention)
            throws IOException {
        return plan(date, start, end, clinicalZone, convention).value();
    }

    /**
     * @throws IOException {@value #NOT_A_CALENDAR_TIME} when the stored date or start is not a
     *     calendar date or a time of day
     */
    public Mapped plan(
            String date, String start, String end, ZoneId clinicalZone, EndConvention convention)
            throws IOException {
        java.util.Objects.requireNonNull(convention);
        if (clinicalZone == null)
            throw new IllegalArgumentException("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
        try {
            if (date == null
                    || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                    || start == null
                    || !start.matches(CLOCK)) impossible();
            var day = LocalDate.parse(date);
            var from = LocalTime.parse(start);
            if (day.getYear() < 1 || day.getYear() > 9999) impossible();
            boolean inclusive = convention == EndConvention.NATIVE_INCLUSIVE;
            LocalTime to = end != null && end.matches(CLOCK) ? LocalTime.parse(end) : null;
            // The native scheduler stores start + duration - one minute. Existing
            // imported receipts prove the older exclusive-end representation instead. The
            // importer recomputes the same rule to bind an appointment's source record.
            int minutes =
                    to == null
                            ? 0
                            : io.github.carlos_emr.carlos.demographic.cds.contract
                                    .CdsAppointmentSourceContract.minutes(from, to, inclusive);
            var length = Length.EXACT;
            if (minutes == 0) {
                length = Length.NOT_STATED;
                to = null;
            } else {
                long seconds = Duration.between(from, to).getSeconds();
                if (seconds < 0) length = Length.NEXT_DAY;
                else if (seconds % 60 != 0) length = Length.PARTIAL_MINUTE;
            }
            var lexical = new CdsExportDateDto(CdsExportDateDto.Precision.DATE, date);
            var rules = clinicalZone.getRules();
            var before = rules.getValidOffsets(day.atTime(from));
            var after =
                    to == null
                            ? before
                            : rules.getValidOffsets(
                                    (length == Length.NEXT_DAY ? day.plusDays(1) : day).atTime(to));
            if (before.size() != 1
                    || after.size() != 1
                    || !before.getFirst().equals(after.getFirst())
                    || before.getFirst().getTotalSeconds() % 60 != 0
                    || Math.abs(before.getFirst().getTotalSeconds()) > 14 * 3600)
                return new Mapped(
                        new CdsExportAppointmentTimingDto(lexical, start, minutes, null), length);
            return new Mapped(
                    new CdsExportAppointmentTimingDto(lexical, start, minutes, before.getFirst()),
                    length);
        } catch (DateTimeException | ArithmeticException invalid) {
            impossible();
            return null;
        }
    }

    private static void impossible() throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException(NOT_A_CALENDAR_TIME);
    }
}
