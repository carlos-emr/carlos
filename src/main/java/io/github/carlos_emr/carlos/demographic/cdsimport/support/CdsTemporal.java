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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.Precision;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Strict, side-effect-free interpretation of the actual XML date choice branches. */
public final class CdsTemporal {
    private CdsTemporal() { }

    public static CdsTemporalDto parse(String year, String month, String date, String dateTime,
            String field) {
        var supplied = java.util.stream.Stream.of(year, month, date, dateTime)
                .filter(java.util.Objects::nonNull).toList();
        if (supplied.isEmpty()) return CdsTemporalDto.unknown();
        if (supplied.size() != 1) throw new CdsClinicalException("CDS_DATE_CHOICE_CONFLICT", field);
        String lexical = supplied.get(0);
        if (lexical.isBlank()) throw new CdsClinicalException("CDS_DATE_EMPTY", field);
        try {
            if (year != null) return CdsTemporalDto.calendar(Precision.YEAR, year, field);
            if (month != null) return CdsTemporalDto.calendar(Precision.MONTH, month, field);
            if (date != null) return CdsTemporalDto.calendar(Precision.FULL_DATE, date, field);
            String interpretedTime = endOfDay(dateTime);
            if (interpretedTime.endsWith("Z") || interpretedTime.matches(".*[+-]\\d{2}:\\d{2}$")) {
                OffsetDateTime parsed = OffsetDateTime.parse(interpretedTime,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withResolverStyle(ResolverStyle.STRICT));
                if (Math.abs(parsed.getOffset().getTotalSeconds()) > 14 * 3600) {
                    throw new CdsClinicalException("CDS_DATE_INVALID", field);
                }
                requireClinicalYear(parsed.getYear(), field);
                return new CdsTemporalDto(Precision.OFFSET_DATE_TIME, lexical, parsed.toLocalDate(),
                        parsed.toLocalDate(), null, parsed);
            }
            LocalDateTime parsed = LocalDateTime.parse(interpretedTime,
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withResolverStyle(ResolverStyle.STRICT));
            requireClinicalYear(parsed.getYear(), field);
            return new CdsTemporalDto(Precision.LOCAL_DATE_TIME, lexical, parsed.toLocalDate(),
                    parsed.toLocalDate(), parsed, null);
        } catch (DateTimeException failure) {
            throw new CdsClinicalException("CDS_DATE_INVALID", field);
        }
    }

    /** Synthetic teaching copies shift only dates whose exact calendar day is established. */
    public static CdsTemporalDto shift(CdsTemporalDto value, int days, String field) {
        if (days == 0 || value.precision() == Precision.UNKNOWN) return value;
        if (value.appliedShiftDays() != 0) {
            throw new CdsClinicalException("CDS_SYNTHETIC_DATE_ALREADY_SHIFTED", field);
        }
        if (!value.exactCalendarDate()) {
            throw new CdsClinicalException("CDS_SYNTHETIC_PARTIAL_DATE_SHIFT_REQUIRES_REVIEW", field);
        }
        try {
            LocalDate earliest = value.earliest().plusDays(days);
            LocalDate latest = value.latest().plusDays(days);
            requireClinicalYear(earliest.getYear(), field);
            requireClinicalYear(latest.getYear(), field);
            return new CdsTemporalDto(value.precision(), value.original(), earliest, latest,
                    value.localDateTime() == null ? null : value.localDateTime().plusDays(days),
                    value.offsetDateTime() == null ? null : value.offsetDateTime().plusDays(days), days);
        } catch (DateTimeException invalid) {
            throw new CdsClinicalException("CDS_SYNTHETIC_DATE_OUTSIDE_TARGET_CALENDAR", field);
        }
    }

    private static void requireClinicalYear(int year, String field) {
        if (year < 1 || year > 9999) {
            throw new CdsClinicalException("CDS_DATE_OUTSIDE_TARGET_CALENDAR", field);
        }
    }

    /** XML Schema permits exactly midnight as 24:00:00; this is the following day's 00:00:00. */
    private static String endOfDay(String lexical) {
        var match = java.util.regex.Pattern.compile(
                "^([0-9]{4}-[0-9]{2}-[0-9]{2})T24:00:00(?:\\.0+)?(Z|[+-][0-9]{2}:[0-9]{2})?$")
                .matcher(lexical);
        if (!match.matches()) return lexical;
        LocalDate next = LocalDate.parse(match.group(1)).plusDays(1);
        return next + "T00:00:00" + (match.group(2) == null ? "" : match.group(2));
    }

}
