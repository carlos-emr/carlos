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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeDateMapperUnitTest {
    private final CdsExportNativeDateMapper mapper = new CdsExportNativeDateMapper();

    @Test void shouldPreserveExplicitYearPrecision_withoutEmittingAnInventedJanuaryFirst() throws Exception {
        var result = mapper.map("1965-01-01", List.of("YYYY"));
        assertThat(result.precision()).isEqualTo(CdsExportDateDto.Precision.YEAR); assertThat(result.lexical()).isEqualTo("1965");
        assertThat(mapper.map("1965-00-00", List.of("YYYY")).lexical()).isEqualTo("1965");
        assertThat(mapper.map("2019-12-31", List.of("YYYY")).lexical()).isEqualTo("2019");
    }
    @Test void shouldPreserveExplicitMonthPrecision_withoutEmittingAStorageAnchorDay() throws Exception {
        var result = mapper.map("2024-02-01", List.of("YYYY-MM"));
        assertThat(result.precision()).isEqualTo(CdsExportDateDto.Precision.YEAR_MONTH); assertThat(result.lexical()).isEqualTo("2024-02");
        assertThat(mapper.map("2024-02-00", List.of("YYYY-MM")).lexical()).isEqualTo("2024-02");
    }
    @Test void shouldKeepAnExactCalendarDate_whenTheNativePartialMarkerIsAbsentOrCleared() throws Exception {
        for (List<String> metadata : Arrays.asList(List.<String>of(), Arrays.asList((String) null), List.of(""))) {
            var result = mapper.map("2000-02-29", metadata);
            assertThat(result.precision()).isEqualTo(CdsExportDateDto.Precision.DATE); assertThat(result.lexical()).isEqualTo("2000-02-29");
        }
    }
    @Test void shouldKeepAnUnknownOptionalDateAbsent_withoutTurningItIntoAnImportTimeOrDefaultDate() throws Exception {
        var result = mapper.map(null, List.of());
        assertThat(result.precision()).isEqualTo(CdsExportDateDto.Precision.ABSENT); assertThat(result.lexical()).isNull();
        assertThat(mapper.map(null, Arrays.asList((String) null)).precision()).isEqualTo(CdsExportDateDto.Precision.ABSENT);
        assertThatThrownBy(() -> mapper.map(null, List.of("YYYY"))).hasMessage("CDS_EXPORT_NATIVE_DATE_PRECISION_REQUIRES_REVIEW");
    }
    @Test void shouldRequireReviewForDuplicateOrUnknownPrecision_insteadOfSilentlyChoosingAnArbitraryMarker() {
        for (List<String> metadata : List.of(List.of("YYYY", "YYYY"), List.of("YYYY", "YYYY-MM"), List.of("unknown"), List.of("yyyy"))) {
            assertThatThrownBy(() -> mapper.map("1965-01-01", metadata)).hasMessage("CDS_EXPORT_NATIVE_DATE_PRECISION_REQUIRES_REVIEW");
        }
    }
    @Test void shouldRejectInvalidActualPrecision_withoutTrimmingCoercingOrFixingTheCalendar() {
        for (String anchor : List.of("", " 2000-02-29", "1900-02-29", "2000-00-00", "0000-01-01", "2000-02-29T00:00:00")) {
            assertThatThrownBy(() -> mapper.map(anchor, List.of())).hasMessage("CDS_EXPORT_NATIVE_DATE_PRECISION_REQUIRES_REVIEW");
        }
        assertThatThrownBy(() -> mapper.map("0000-01-01", List.of("YYYY"))).hasMessage("CDS_EXPORT_NATIVE_DATE_PRECISION_REQUIRES_REVIEW");
        assertThatThrownBy(() -> mapper.map("1965-00-01", List.of("YYYY-MM"))).hasMessage("CDS_EXPORT_NATIVE_DATE_PRECISION_REQUIRES_REVIEW");
    }
}
