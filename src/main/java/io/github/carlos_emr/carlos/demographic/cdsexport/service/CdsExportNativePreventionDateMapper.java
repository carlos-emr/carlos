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
    private final ZoneId zone;

    public CdsExportNativePreventionDateMapper() {
        this(null);
    }

    public CdsExportNativePreventionDateMapper(ZoneId zone) {
        this.zone = zone;
    }

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
            if (marker == null || marker.isEmpty()) {
                // Native date-only entry uses midnight as a storage anchor, not a clock assertion.
                if (parsed.toLocalTime().equals(LocalTime.MIDNIGHT))
                    return new CdsExportDateDto(
                            CdsExportDateDto.Precision.DATE, parsed.toLocalDate().toString());
                // A stored clock time (OSCAR 19's prevention form saved one; the eForm and REST
                // paths may) is written in the clinic zone with that zone's offset. One the zone
                // gives no single whole-minute offset (inside the hour the autumn change repeats,
                // inside the spring gap, or before the zone's standard time) is its wall-clock
                // value with no offset, the rule appointments and notes follow
                // (LOCAL_WALL_CLOCK_TIME_AT_DAYLIGHT_SAVING_TRANSITION): a receiver reads it as
                // clinic time and no offset is invented. It held the patient. The stored value is
                // exact in the prevention residual either way.
                var offsets =
                        zone == null ? java.util.List.<ZoneOffset>of() : zone.getRules().getValidOffsets(parsed);
                if (offsets.size() != 1 || offsets.getFirst().getTotalSeconds() % 60 != 0)
                    return new CdsExportDateDto(
                            CdsExportDateDto.Precision.DATE_TIME,
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(parsed));
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.DATE_TIME,
                        parsed.atOffset(offsets.getFirst())
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            return new CdsExportNativeDateMapper().map(anchor.substring(0, 10), markers);
        } catch (DateTimeException failed) {
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportMappingReviewException("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
        }
    }

    private static void review() throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
    }
}
