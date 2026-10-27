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
import java.util.Date;

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
        return earliest == null ? null : java.sql.Date.valueOf(earliest);
    }

    /** TIMESTAMP storage requires an explicit clinic/source zone when the source has no offset. */
    public Date timestampStorageAnchor(ZoneId sourceZone) {
        if (precision == Precision.UNKNOWN) return null;
        if (offsetDateTime != null) return Date.from(offsetDateTime.toInstant());
        LocalDateTime value = localDateTime == null ? earliest.atStartOfDay() : localDateTime;
        var offsets = sourceZone.getRules().getValidOffsets(value);
        if (offsets.size() != 1) {
            throw new IllegalArgumentException("CDS_LOCAL_TIME_REQUIRES_REVIEW");
        }
        return Date.from(value.toInstant(offsets.get(0)));
    }

    public String legacyPartialFormat() {
        return switch (precision) {
            case YEAR -> "YYYY";
            case MONTH -> "YYYY-MM";
            default -> null;
        };
    }
}
