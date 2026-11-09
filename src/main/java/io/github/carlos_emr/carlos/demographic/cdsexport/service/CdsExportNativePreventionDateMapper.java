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
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

/** Exact civil DATETIME(0) source with explicit native year/month markers; never a creation/import/modification clock. */
public final class CdsExportNativePreventionDateMapper {
    public CdsExportDateDto map(String anchor, List<String> markers) throws IOException {
        if (markers == null || markers.size() > 1) review();
        String marker = markers.isEmpty() ? null : markers.getFirst();
        if (anchor == null) {
            if (marker != null && !marker.isEmpty()) review();
            return new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        }
        if (!anchor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) review();
        try {
            var parsed =
                    LocalDateTime.parse(
                            anchor,
                            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                                    .withResolverStyle(ResolverStyle.STRICT));
            if (parsed.getYear() < 1) review();
            if (marker == null || marker.isEmpty())
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.DATE_TIME, anchor.replace(' ', 'T'));
            return new CdsExportNativeDateMapper().map(anchor.substring(0, 10), markers);
        } catch (DateTimeException failed) {
            throw new IOException("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
        }
    }

    private static void review() throws IOException {
        throw new IOException("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
    }
}
