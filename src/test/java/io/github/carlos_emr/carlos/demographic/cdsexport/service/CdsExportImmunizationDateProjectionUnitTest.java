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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CdsExportImmunizationDateProjectionUnitTest {
    @Test void unknownDateDoesNotInventAnAnchorOrOriginalValue()throws Exception{var value=CdsExportImmunizationDateProjection.expected(CdsTemporalDto.unknown()); assertThat(value.kind()).isEqualTo(CdsExportImmunizationDateProjection.Kind.ABSENT); assertThat(value.nativeAnchor()).isNull(); assertThat(value.originalHint()).isNull(); assertThat(value.precisionHint()).isEqualTo("UNKNOWN"); assertThat(value.partialFormat()).isNull(); }
    @Test void yearAndMonthUseOnlyTheOriginalPersistersExplicitPartialMarkers()throws Exception{
        var year=CdsExportImmunizationDateProjection.expected(CdsTemporal.parse("2024", null, null, null, "Date")); assertThat(year.nativeAnchor()).isEqualTo("2024-01-01 00:00:00"); assertThat(year.partialFormat()).isEqualTo("YYYY");
        var month=CdsExportImmunizationDateProjection.expected(CdsTemporal.parse(null, "2024-02", null, null, "Date")); assertThat(month.nativeAnchor()).isEqualTo("2024-02-01 00:00:00"); assertThat(month.partialFormat()).isEqualTo("YYYY-MM");
    }
    @Test void dateShiftAffectsTheNativeCandidateWhileTheProvenanceHintStaysOriginal()throws Exception{
        var source=CdsTemporal.shift(CdsTemporal.parse(null, null, "2024-02-29", null, "Date"), 1, "Date"); var value=CdsExportImmunizationDateProjection.expected(source); assertThat(value.nativeAnchor()).isEqualTo("2024-03-01 00:00:00"); assertThat(value.originalHint()).isEqualTo("2024-02-29"); assertThat(value.precisionHint()).isEqualTo("FULL_DATE");
    }
    @Test void localFractionIsFlooredOnlyForTheNativeDatetimeZeroWitness()throws Exception{
        var source=CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.9999999", "Date"); var value=CdsExportImmunizationDateProjection.expected(source); assertThat(value.kind()).isEqualTo(CdsExportImmunizationDateProjection.Kind.LOCAL_CLOCK); assertThat(value.nativeAnchor()).isEqualTo("2024-02-29 12:34:56"); assertThat(value.originalHint()).isEqualTo("2024-02-29T12:34:56.9999999"); assertThat(new CdsExportPreparedTemporalMapper().map(source, true).lexical()).isEqualTo(value.originalHint());
    }
    @Test void endOfDayNativeAnchorAndOriginalSpellingRemainDistinct()throws Exception{
        var source=CdsTemporal.parse(null, null, null, "2024-02-29T24:00:00", "Date"); var value=CdsExportImmunizationDateProjection.expected(source); assertThat(value.nativeAnchor()).isEqualTo("2024-03-01 00:00:00"); assertThat(value.originalHint()).isEqualTo("2024-02-29T24:00:00");
    }
    @Test void exactOffsetInstantsRequireHistoricalCivilZoneEvidenceEvenWhenTheCurrentZoneLooksPlausible()throws Exception{
        var source=CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200-05:00", "Date"); var value=CdsExportImmunizationDateProjection.expected(source); assertThat(value.kind()).isEqualTo(CdsExportImmunizationDateProjection.Kind.HISTORIC_ZONE_REQUIRED); assertThat(value.nativeAnchor()).isNull(); assertThat(value.originalHint()).isEqualTo("2024-02-29T12:34:56.1200-05:00");
    }
    @Test void calendarMidnightIsOnlyACandidateAndCannotAuthorizeAGapAdjustedHistoricalClock()throws Exception{
        var source=CdsTemporal.parse(null, null, "2018-11-04", null, "Date"); assertThat(java.time.LocalDate.of(2018, 11, 4).atStartOfDay(ZoneId.of("America/Sao_Paulo")).getHour()).isEqualTo(1); var value=CdsExportImmunizationDateProjection.expected(source); assertThat(value.kind()).isEqualTo(CdsExportImmunizationDateProjection.Kind.CALENDAR_MIDNIGHT_CANDIDATE); assertThat(value.nativeAnchor()).isEqualTo("2018-11-04 00:00:00");
    }
    @Test void expectedProjectionCannotHideAContradictoryPreparedTemporalValue(){var invalid=new CdsTemporalDto(CdsTemporalDto.Precision.FULL_DATE, "2024-02-29", java.time.LocalDate.of(2024, 3, 1), java.time.LocalDate.of(2024, 3, 1), null, null, 0); assertThatThrownBy(()-> CdsExportImmunizationDateProjection.expected(invalid)).hasMessage("CDS_EXPORT_PREPARED_TEMPORAL_REQUIRES_REVIEW"); }
}
