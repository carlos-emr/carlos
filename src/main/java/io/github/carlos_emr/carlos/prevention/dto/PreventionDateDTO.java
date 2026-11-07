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
package io.github.carlos_emr.carlos.prevention.dto;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.PartialDate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Calendar precision of a native prevention date, including records predating CDS import.
 * Interval bounds are inclusive ISO calendar dates, never timestamps or clinical event days.
 * {@code originalValue} is the native value at its recorded precision; import source spelling
 * (when different) belongs to source provenance. No precision is inferred from an anchor day.
 */
@XmlJavaTypeAdapter(PreventionDateDTO.JaxbAdapter.class)
public final class PreventionDateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Precision { FULL_DATE, YEAR_MONTH, YEAR, UNKNOWN }

    private final Precision precision;
    private final String originalValue;
    private final String intervalStart;
    private final String intervalEnd;

    private PreventionDateDTO(Precision precision, String originalValue, LocalDate start, LocalDate end) {
        this.precision = precision;
        this.originalValue = originalValue;
        this.intervalStart = start == null ? null : start.toString();
        this.intervalEnd = end == null ? null : end.toString();
    }

    @JsonCreator
    private static PreventionDateDTO fromJson(@JsonProperty("precision") Precision precision,
            @JsonProperty("originalValue") String originalValue,
            @JsonProperty("intervalStart") String start, @JsonProperty("intervalEnd") String end) {
        return new PreventionDateDTO(precision, originalValue,
                start == null ? null : LocalDate.parse(start), end == null ? null : LocalDate.parse(end));
    }

    /** Only the native partial_date tags establish partial precision. Null dates stay unknown. */
    public static PreventionDateDTO from(Date storedDate, String recordedFormat) {
        if (recordedFormat != null && !recordedFormat.isBlank()
                && !PartialDate.YEARONLY.equals(recordedFormat) && !PartialDate.YEARMONTH.equals(recordedFormat)) {
            throw new PreventionDatePrecisionException("Unsupported prevention partial-date format requires review");
        }
        if (storedDate == null) {
            return new PreventionDateDTO(Precision.UNKNOWN, null, null, null);
        }
        // Native prevention timestamps have always represented a calendar day in the EMR zone.
        // Formatting also supports java.sql.Date, whose toInstant() throws.
        LocalDate day = LocalDate.parse(new SimpleDateFormat("yyyy-MM-dd").format(storedDate));
        if (PartialDate.YEARONLY.equals(recordedFormat)) {
            Year year = Year.of(day.getYear());
            return new PreventionDateDTO(Precision.YEAR, day.toString().substring(0, 4), year.atDay(1), year.atMonth(12).atEndOfMonth());
        }
        if (PartialDate.YEARMONTH.equals(recordedFormat)) {
            YearMonth month = YearMonth.from(day);
            return new PreventionDateDTO(Precision.YEAR_MONTH, month.toString(), month.atDay(1), month.atEndOfMonth());
        }
        return new PreventionDateDTO(Precision.FULL_DATE, day.toString(), day, day);
    }

    public Precision getPrecision() { return precision; }
    public String getOriginalValue() { return originalValue; }
    public String getIntervalStart() { return intervalStart; }
    public String getIntervalEnd() { return intervalEnd; }
    @JsonIgnore
    public boolean isExact() { return precision == Precision.FULL_DATE; }

    /** Date-only clients must explicitly upgrade rather than receive a synthetic anchor. */
    public void requireExact() {
        if (!isExact()) {
            throw new PreventionDatePrecisionException();
        }
    }

    /** JAXB uses a transport carrier without weakening the application's immutable date value. */
    public static final class JaxbAdapter extends XmlAdapter<JaxbValue, PreventionDateDTO> {
        /**
         * Reconstructs a prevention date through the existing JSON construction path.
         *
         * @param value the {@link JaxbValue} carrier, or {@code null}
         * @return the immutable prevention date, or {@code null} for a null carrier
         * @throws java.time.format.DateTimeParseException if a non-null interval bound is not an ISO date
         */
        @Override
        public PreventionDateDTO unmarshal(JaxbValue value) {
            return value == null ? null : fromJson(value.precision, value.originalValue,
                    value.intervalStart, value.intervalEnd);
        }

        /**
         * Preserves the prevention date's precision, original value, and inclusive interval bounds.
         *
         * @param value the immutable prevention date, or {@code null}
         * @return a {@link JaxbValue} carrier, or {@code null} for a null date
         */
        @Override
        public JaxbValue marshal(PreventionDateDTO value) {
            if (value == null) return null;
            JaxbValue xml = new JaxbValue();
            xml.precision = value.precision;
            xml.originalValue = value.originalValue;
            xml.intervalStart = value.intervalStart;
            xml.intervalEnd = value.intervalEnd;
            return xml;
        }
    }

    /** JAXB and the JAXB-aware REST mapper retain the existing four-field representation. */
    @XmlType(name = "preventionDate")
    public static final class JaxbValue {
        public Precision precision;
        public String originalValue;
        public String intervalStart;
        public String intervalEnd;
    }
}
