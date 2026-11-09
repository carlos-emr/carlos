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

/** No guessed overnight date, rounded minutes, invented offset or ambiguous daylight-saving interpretation. */
public final class CdsExportAppointmentTimingMapper {
    public CdsExportAppointmentTimingDto map(
            String date, String start, String end, ZoneId clinicalZone) throws IOException {
        if (clinicalZone == null)
            throw new IllegalArgumentException("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
        try {
            if (date == null
                    || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                    || start == null
                    || end == null
                    || !start.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]")
                    || !end.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]")) review();
            var day = LocalDate.parse(date);
            var from = LocalTime.parse(start);
            var to = LocalTime.parse(end);
            long seconds = Duration.between(from, to).getSeconds();
            if (day.getYear() < 1 || day.getYear() > 9999 || seconds < 60 || seconds % 60 != 0)
                review();
            var rules = clinicalZone.getRules();
            var before = rules.getValidOffsets(day.atTime(from));
            var after = rules.getValidOffsets(day.atTime(to));
            if (before.size() != 1
                    || after.size() != 1
                    || !before.getFirst().equals(after.getFirst())) review();
            var offset = before.getFirst();
            if (offset.getTotalSeconds() % 60 != 0
                    || Math.abs(offset.getTotalSeconds()) > 14 * 3600) review();
            return new CdsExportAppointmentTimingDto(
                    new CdsExportDateDto(CdsExportDateDto.Precision.DATE, date),
                    start,
                    Math.toIntExact(seconds / 60),
                    offset);
        } catch (DateTimeException | ArithmeticException invalid) {
            review();
            return null;
        }
    }

    private static void review() throws IOException {
        throw new IOException("CDS_EXPORT_APPOINTMENT_TIMING_REQUIRES_REVIEW");
    }
}
