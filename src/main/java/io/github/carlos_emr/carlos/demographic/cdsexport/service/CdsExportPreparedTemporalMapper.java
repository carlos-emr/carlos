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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.io.IOException;
import java.time.LocalDate;

/** Converts a verified post-option preparation without undoing its date shift or discarding an offset, clock or precision. */
public final class CdsExportPreparedTemporalMapper {
    public CdsExportDateDto map(CdsTemporalDto value, boolean permitsClock) throws IOException {
        if (value == null) invalid();
        try {
            CdsTemporalDto original = switch (value.precision()) {
                case UNKNOWN -> CdsTemporalDto.unknown();
                case YEAR -> CdsTemporal.parse(value.original(), null, null, null, "Date");
                case MONTH -> CdsTemporal.parse(null, value.original(), null, null, "Date");
                case FULL_DATE -> CdsTemporal.parse(null, null, value.original(), null, "Date");
                case LOCAL_DATE_TIME, OFFSET_DATE_TIME -> CdsTemporal.parse(null, null, null, value.original(), "Date");
            };
            var expected = CdsTemporal.shift(original, value.appliedShiftDays(), "Date");
            if (!expected.equals(value)) invalid();
            var precision = switch (value.precision()) {
                case UNKNOWN -> CdsExportDateDto.Precision.ABSENT;
                case YEAR -> CdsExportDateDto.Precision.YEAR;
                case MONTH -> CdsExportDateDto.Precision.YEAR_MONTH;
                case FULL_DATE -> CdsExportDateDto.Precision.DATE;
                case LOCAL_DATE_TIME, OFFSET_DATE_TIME -> CdsExportDateDto.Precision.DATE_TIME;
            };
            if (precision == CdsExportDateDto.Precision.DATE_TIME && !permitsClock) invalid();
            String lexical = value.original();
            if (value.appliedShiftDays() != 0) {
                // Change only the established calendar day. Preserve the original offset,
                // fractional precision and legal 24:00 spelling rather than round-tripping
                // through a formatter that may omit seconds or normalize the clock.
                lexical = LocalDate.parse(lexical.substring(0, 10)).plusDays(value.appliedShiftDays()) + lexical.substring(10);
                CdsTemporalDto check = precision == CdsExportDateDto.Precision.DATE_TIME
                        ? CdsTemporal.parse(null, null, null, lexical, "Date") : CdsTemporal.parse(null, null, lexical, null, "Date");
                if (!java.util.Objects.equals(check.earliest(), value.earliest()) || !java.util.Objects.equals(check.latest(), value.latest())
                        || !java.util.Objects.equals(check.localDateTime(), value.localDateTime()) || !java.util.Objects.equals(check.offsetDateTime(), value.offsetDateTime())) invalid();
            }
            return new CdsExportDateDto(precision, lexical);
        } catch (RuntimeException invalid) { throw new IOException("CDS_EXPORT_PREPARED_TEMPORAL_REQUIRES_REVIEW"); }
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_PREPARED_TEMPORAL_REQUIRES_REVIEW"); }
}
