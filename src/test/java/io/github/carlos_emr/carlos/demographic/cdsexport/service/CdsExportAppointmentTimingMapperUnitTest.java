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

import static org.assertj.core.api.Assertions.*;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportAppointmentTimingMapperUnitTest {
    private final CdsExportAppointmentTimingMapper mapper = new CdsExportAppointmentTimingMapper();
    private final ZoneId zone = ZoneId.of("America/Toronto");

    @Test
    void shouldKeepTheExactLocalDateAndSeconds_andDeriveWholeMinutes() throws Exception {
        var result = mapper.map("2024-02-29", "09:00:30", "10:15:30", zone);
        assertThat(result.date().lexical()).isEqualTo("2024-02-29");
        assertThat(result.time()).isEqualTo("09:00:30");
        assertThat(result.durationMinutes()).isEqualTo(75);
    }

    @Test
    void wireTimeCarriesTheExactSourceOffsetWithoutChangingItsLocalClock() throws Exception {
        var winter = mapper.map("2024-02-29", "09:00:30", "10:15:30", zone);
        assertThat(winter.time()).isEqualTo("09:00:30");
        assertThat(winter.wireTime()).isEqualTo("09:00:30-05:00");
        var summer = mapper.map("2024-07-01", "09:00:30", "10:15:30", zone);
        assertThat(summer.wireTime()).isEqualTo("09:00:30-04:00");
        var utc = mapper.map("2024-02-29", "09:00:30", "10:15:30", ZoneId.of("UTC"));
        assertThat(utc.wireTime()).isEqualTo("09:00:30Z");
    }

    @Test
    void historicalSecondOffsetsAndOffsetsOutsideTheWireRangeNeedReview() {
        assertThatThrownBy(() -> mapper.map("1800-01-01", "09:00:00", "10:00:00", zone))
                .hasMessage("CDS_EXPORT_APPOINTMENT_TIMING_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        "2024-02-29",
                                        "09:00:00",
                                        "10:00:00",
                                        java.time.ZoneOffset.ofHours(15)))
                .hasMessage("CDS_EXPORT_APPOINTMENT_TIMING_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseOvernightZeroOrFractionalMinuteSlots_insteadOfChangingDatesOrRounding() {
        for (var span :
                List.of(
                        List.of("23:59:30", "00:00:30"),
                        List.of("09:00:00", "09:00:00"),
                        List.of("09:00:00", "09:00:30"),
                        List.of("09:00:00", "09:01:01"))) {
            assertThatThrownBy(() -> mapper.map("2024-02-29", span.get(0), span.get(1), zone))
                    .hasMessage("CDS_EXPORT_APPOINTMENT_TIMING_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldRefuseAmbiguousMissingAndOffsetChangingDaylightSavingSlots() {
        for (var slot :
                List.of(
                        List.of("2026-11-01", "01:15:00", "01:45:00"),
                        List.of("2026-03-08", "02:15:00", "02:45:00"),
                        List.of("2026-03-08", "01:30:00", "03:30:00"))) {
            assertThatThrownBy(() -> mapper.map(slot.get(0), slot.get(1), slot.get(2), zone))
                    .hasMessage("CDS_EXPORT_APPOINTMENT_TIMING_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldRefuseZeroInvalidOrNormalizedDateTimeLexicals() {
        for (String date :
                List.of("0000-00-00", "1900-02-29", "2024-2-29", "2024-02-30", " 2024-02-29")) {
            assertThatThrownBy(() -> mapper.map(date, "09:00:00", "10:00:00", zone))
                    .hasMessage("CDS_EXPORT_APPOINTMENT_TIMING_REQUIRES_REVIEW");
        }
        for (String time : List.of("24:00:00", "-01:00:00", "09:00", "09:00:00.1")) {
            assertThatThrownBy(() -> mapper.map("2024-02-29", time, "10:00:00", zone))
                    .hasMessage("CDS_EXPORT_APPOINTMENT_TIMING_REQUIRES_REVIEW");
        }
        assertThatThrownBy(() -> mapper.map("2024-02-29", "09:00:00", "10:00:00", null))
                .hasMessage("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
    }
}
