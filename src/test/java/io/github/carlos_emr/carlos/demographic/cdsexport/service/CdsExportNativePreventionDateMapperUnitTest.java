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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.util.*;
import org.junit.jupiter.api.Test;

class CdsExportNativePreventionDateMapperUnitTest {
    private final CdsExportNativePreventionDateMapper mapper =
            new CdsExportNativePreventionDateMapper();

    @Test
    void shouldExportTheDateOnlyNativeAnchor_withoutInventingARecordedClock() throws Exception {
        var value = mapper.map("2024-02-29 00:00:00", List.of());
        assertThat(value.precision()).isEqualTo(CdsExportDateDto.Precision.DATE);
        assertThat(value.lexical()).isEqualTo("2024-02-29");
    }

    @Test
    void shouldRequireAnExplicitUnambiguousZone_forANativeEventClock() throws Exception {
        assertThatThrownBy(() -> mapper.map("2024-02-29 12:34:56", List.of()))
                .hasMessage("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
        var zoned = new CdsExportNativePreventionDateMapper(java.time.ZoneId.of("America/Toronto"));
        assertThat(zoned.map("2024-02-29 12:34:56", List.of()).lexical())
                .isEqualTo("2024-02-29T12:34:56-05:00");
        assertThat(zoned.map("2024-07-01 12:34:56", List.of()).lexical())
                .isEqualTo("2024-07-01T12:34:56-04:00");
        for (String ambiguous : List.of("2024-03-10 02:30:00", "2024-11-03 01:30:00"))
            assertThatThrownBy(() -> zoned.map(ambiguous, List.of()))
                    .hasMessage("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
    }

    @Test
    void
            shouldVerifyNativePartialMarkersSuppressAnchorClockAndMissingCalendarParts_forNativePreventionDateMapperContract()
                    throws Exception {
        assertThat(mapper.map("2024-02-29 12:34:56", List.of("YYYY")).lexical()).isEqualTo("2024");
        assertThat(mapper.map("2024-02-29 12:34:56", List.of("YYYY-MM")).lexical())
                .isEqualTo("2024-02");
    }

    @Test
    void
            shouldVerifyUnknownDateDoesNotAcceptAPopulatedPrecisionMarker_forNativePreventionDateMapperContract()
                    throws Exception {
        assertThat(mapper.map(null, List.of()).precision())
                .isEqualTo(CdsExportDateDto.Precision.ABSENT);
        assertThatThrownBy(() -> mapper.map(null, List.of("YYYY")))
                .hasMessage("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
    }

    @Test
    void shouldUseNativeDatePrecision_whenOptionalMarkersAreCleared() throws Exception {
        assertThat(mapper.map("2024-02-29 00:00:00", Arrays.asList((String) null)).precision())
                .isEqualTo(CdsExportDateDto.Precision.DATE);
        assertThat(mapper.map("2024-02-29 00:00:00", List.of("")).precision())
                .isEqualTo(CdsExportDateDto.Precision.DATE);
    }

    @Test
    void
            shouldVerifyInvalidCalendarClockFractionOffsetOrPrecisionCannotBeSilentlyNormalized_forNativePreventionDateMapperContract() {
        for (String date :
                List.of(
                        "2023-02-29 12:34:56",
                        "0000-01-01 00:00:00",
                        "2024-02-29 24:00:00",
                        "2024-02-29 12:34:60",
                        "2024-02-29 12:34:56.000",
                        "2024-02-29T12:34:56Z",
                        "2024-02-29",
                        ""))
            assertThatThrownBy(() -> mapper.map(date, List.of()))
                    .hasMessage("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
        assertThatThrownBy(() -> mapper.map("2024-02-29 12:34:56", List.of("YYYY-MM-DD")))
                .isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> mapper.map("2024-02-29 12:34:56", List.of("YYYY", "YYYY")))
                .isInstanceOf(java.io.IOException.class);
    }
}
