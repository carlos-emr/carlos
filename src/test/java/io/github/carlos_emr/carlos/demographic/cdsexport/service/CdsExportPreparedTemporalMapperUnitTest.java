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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit") @Tag("cds-export")
class CdsExportPreparedTemporalMapperUnitTest {
    private final CdsExportPreparedTemporalMapper mapper = new CdsExportPreparedTemporalMapper();
    @Test void shouldPreserveOriginalCalendarPrecisionAndTimezoneSuffixes_whenNoOptionTransformedThem() throws Exception {
        assertThat(mapper.map(CdsTemporal.parse("2020+05:45",null,null,null,"Date"),false))
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.YEAR,"2020+05:45"));
        assertThat(mapper.map(CdsTemporal.parse(null,"2020-02Z",null,null,"Date"),false).lexical()).isEqualTo("2020-02Z");
        assertThat(mapper.map(CdsTemporalDto.unknown(),false).precision()).isEqualTo(CdsExportDateDto.Precision.ABSENT);
    }
    @Test void shouldWriteTheShiftedCalendarDay_withoutLosingItsExplicitOffset() throws Exception {
        var prepared = CdsTemporal.shift(CdsTemporal.parse(null,null,"2020-02-29-05:00",null,"Date"),1,"Date");
        assertThat(mapper.map(prepared,false).lexical()).isEqualTo("2020-03-01-05:00");
        assertThat(prepared.original()).isEqualTo("2020-02-29-05:00");
    }
    @Test void shouldKeepClockSecondsFractionOffsetAndEndOfDaySpelling_acrossTheAppliedShift() throws Exception {
        for (String original : java.util.List.of("2020-02-29T12:34:00.1200+05:45","2020-02-29T24:00:00Z","2020-02-29T00:00:00")) {
            var prepared = CdsTemporal.shift(CdsTemporal.parse(null,null,null,original,"Date"),1,"Date");
            var result = mapper.map(prepared,true);
            assertThat(result.precision()).isEqualTo(CdsExportDateDto.Precision.DATE_TIME);
            assertThat(result.lexical()).isEqualTo("2020-03-01" + original.substring(10));
        }
    }
    @Test void shouldRefuseToDiscardAClock_inADateOnlyField() {
        var prepared = CdsTemporal.parse(null,null,null,"2020-02-29T01:02:03-05:00","Date");
        assertThatThrownBy(() -> mapper.map(prepared,false)).hasMessage("CDS_EXPORT_PREPARED_TEMPORAL_REQUIRES_REVIEW");
    }
    @Test void shouldRejectInconsistentAnchorsAndAnInventedShiftForPartialPrecision() {
        var inconsistent = new CdsTemporalDto(CdsTemporalDto.Precision.FULL_DATE,"2020-02-29",LocalDate.of(2020,3,2),LocalDate.of(2020,3,2),null,null,1);
        assertThatThrownBy(() -> mapper.map(inconsistent,false)).hasMessage("CDS_EXPORT_PREPARED_TEMPORAL_REQUIRES_REVIEW");
        var partial = new CdsTemporalDto(CdsTemporalDto.Precision.YEAR,"2020",LocalDate.of(2020,1,2),LocalDate.of(2021,1,1),null,null,1);
        assertThatThrownBy(() -> mapper.map(partial,false)).hasMessage("CDS_EXPORT_PREPARED_TEMPORAL_REQUIRES_REVIEW");
    }
}
