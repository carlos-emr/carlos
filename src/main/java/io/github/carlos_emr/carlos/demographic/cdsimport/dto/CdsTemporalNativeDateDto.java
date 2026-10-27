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

import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporalPrecisionException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native date precision, independent of CDS provenance; original XML remains separate source evidence. */
public final class CdsTemporalNativeDateDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String precision;
    private final String value;
    private final String intervalStart;
    private final String intervalEnd;

    private CdsTemporalNativeDateDto(CdsTemporalDto date) {
        this.precision = date.precision().name();
        this.value = date.original();
        this.intervalStart = date.earliest() == null ? null : date.earliest().toString();
        this.intervalEnd = date.latest() == null ? null : date.latest().toString();
    }

    public static CdsTemporalNativeDateDto from(Date stored, String format) {
        if (format != null && !format.isBlank() && !PartialDate.YEARONLY.equals(format)
                && !PartialDate.YEARMONTH.equals(format)) throw new CdsTemporalPrecisionException();
        if (stored == null) return new CdsTemporalNativeDateDto(CdsTemporalDto.unknown());
        // Preserve the established native calendar-day interpretation, including java.sql.Date.
        String day = stored instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate().toString()
                : new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(stored);
        CdsTemporalDto date = PartialDate.YEARONLY.equals(format)
                ? CdsTemporal.parse(day.substring(0, 4), null, null, null, "nativeDate")
                : PartialDate.YEARMONTH.equals(format)
                    ? CdsTemporal.parse(null, day.substring(0, 7), null, null, "nativeDate")
                    : CdsTemporal.parse(null, null, day, null, "nativeDate");
        return new CdsTemporalNativeDateDto(date);
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    private static CdsTemporalNativeDateDto fromJson(
            @com.fasterxml.jackson.annotation.JsonProperty("precision") String precision,
            @com.fasterxml.jackson.annotation.JsonProperty("value") String value,
            @com.fasterxml.jackson.annotation.JsonProperty("intervalStart") String start,
            @com.fasterxml.jackson.annotation.JsonProperty("intervalEnd") String end) {
        CdsTemporalDto parsed = switch (precision == null ? "" : precision) {
            case "UNKNOWN" -> CdsTemporalDto.unknown();
            case "YEAR" -> CdsTemporal.parse(value, null, null, null, "nativeDate");
            case "MONTH" -> CdsTemporal.parse(null, value, null, null, "nativeDate");
            case "FULL_DATE" -> CdsTemporal.parse(null, null, value, null, "nativeDate");
            default -> throw new CdsTemporalPrecisionException();
        };
        CdsTemporalNativeDateDto result = new CdsTemporalNativeDateDto(parsed);
        if (!java.util.Objects.equals(result.precision, precision)
                || !java.util.Objects.equals(result.value, value)
                || !java.util.Objects.equals(result.intervalStart, start)
                || !java.util.Objects.equals(result.intervalEnd, end)) {
            throw new CdsTemporalPrecisionException();
        }
        return result;
    }

    public String getPrecision() { return precision; }
    public String getValue() { return value; }
    public String getIntervalStart() { return intervalStart; }
    public String getIntervalEnd() { return intervalEnd; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isExact() { return "FULL_DATE".equals(precision); }
    public String display() { return value == null ? "Unknown" : value; }

    /** Null Date already means unknown in the old optional field; partial anchors must never escape. */
    public void requireDateOnlySafe() {
        if (value != null && !isExact()) throw new CdsTemporalPrecisionException();
    }
}
