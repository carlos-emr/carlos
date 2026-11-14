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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto.Precision;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/** Maps captured native DATE anchors together with their independently captured precision. */
public final class CdsExportNativeDateMapper {
    /**
     * No metadata is the native full-date convention. A single explicit YYYY/ YYYY-MM
     * marker expresses the actual clinical precision; a storage anchor never supplies a
     * missing clinical month/day. Null/empty metadata is the native cleared-marker convention.
     * Duplicate or unrecognized metadata requires review.
     */
    public CdsExportDateDto map(String anchor, List<String> precisionRows) throws IOException {
        Objects.requireNonNull(precisionRows);
        if (precisionRows.size() > 1) review();
        String format = precisionRows.isEmpty() ? null : precisionRows.getFirst();
        Precision precision;
        if (format == null || format.isEmpty()) precision = Precision.DATE;
        else if (format.equals("YYYY")) precision = Precision.YEAR;
        else if (format.equals("YYYY-MM")) precision = Precision.YEAR_MONTH;
        else {
            review();
            return null;
        }
        if (anchor == null) {
            if (precision != Precision.DATE) review();
            return new CdsExportDateDto(Precision.ABSENT, null);
        }
        if (!anchor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) review();
        try {
            int year = Integer.parseInt(anchor.substring(0, 4));
            if (year < 1) review();
            return switch (precision) {
                case YEAR -> new CdsExportDateDto(Precision.YEAR, anchor.substring(0, 4));
                case YEAR_MONTH ->
                        new CdsExportDateDto(
                                Precision.YEAR_MONTH,
                                YearMonth.of(year, Integer.parseInt(anchor.substring(5, 7)))
                                        .toString());
                case DATE ->
                        new CdsExportDateDto(Precision.DATE, LocalDate.parse(anchor).toString());
                case ABSENT, DATE_TIME ->
                        throw new IllegalStateException("CDS_EXPORT_NATIVE_DATE_INVALID");
            };
        } catch (DateTimeException | NumberFormatException invalid) {
            review();
            return null;
        }
    }

    private static void review() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_DATE_PRECISION_REQUIRES_REVIEW");
    }
}
