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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.Precision;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class CdsTemporalUnitTest {
    @Test
    void shouldPreserveLeapMonthInterval_whenMonthPrecisionIsSupplied() {
        var value = CdsTemporal.parse(null, "2020-02", null, null, "Date");
        assertThat(value.precision()).isEqualTo(Precision.MONTH);
        assertThat(value.earliest()).isEqualTo(LocalDate.of(2020, 2, 1));
        assertThat(value.latest()).isEqualTo(LocalDate.of(2020, 2, 29));
        assertThat(value.exactCalendarDate()).isFalse();
        assertThat(value.legacyPartialFormat()).isEqualTo("YYYY-MM");
    }

    @Test
    void shouldKeepJanuaryFirstExact_whenFullDateWasActuallySupplied() {
        var value = CdsTemporal.parse(null, null, "2020-01-01", null, "Date");
        assertThat(value.precision()).isEqualTo(Precision.FULL_DATE);
        assertThat(value.exactCalendarDate()).isTrue();
        assertThat(value.legacyPartialFormat()).isNull();
    }

    @Test
    void shouldPreserveOffsetAndCalendarMeaning_whenInstantCrossesUtcDay() {
        var value = CdsTemporal.parse(null, null, null, "2020-01-01T00:15:00+14:00", "Date");
        assertThat(value.earliest()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(value.original()).isEqualTo("2020-01-01T00:15:00+14:00");
        assertThat(value.timestampStorageAnchor(ZoneId.of("America/Toronto")).toInstant())
                .isEqualTo(java.time.Instant.parse("2019-12-31T10:15:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2019-02-29", "2020-02-30", "2020-13-01", "0000-01-01", "2020-01-01+15:00"})
    void shouldRefuseInvalidCalendarValues_whenDateCannotBeRepresented(String text) {
        assertThatThrownBy(() -> CdsTemporal.parse(null, null, text, null, "Date"))
                .isInstanceOf(CdsClinicalException.class);
    }

    @Test
    void shouldRefuseDstAmbiguity_whenLocalTimeHasNoOffset() {
        var value = CdsTemporal.parse(null, null, null, "2020-11-01T01:30:00", "Date");
        assertThatThrownBy(() -> value.timestampStorageAnchor(ZoneId.of("America/Toronto")))
                .hasMessage("CDS_LOCAL_TIME_REQUIRES_REVIEW");
    }

    @Test
    void shouldReturnNoGuessedDate_whenOptionalDateIsAbsent() {
        var value = CdsTemporal.parse(null, null, null, null, "Date");
        assertThat(value.precision()).isEqualTo(Precision.UNKNOWN);
        assertThat(value.earliest()).isNull();
        assertThat(value.latest()).isNull();
        assertThat(value.dateStorageAnchor()).isNull();
    }

    @Test
    void shouldNormalizeRepeatably_whenSameLexicalValueIsReadAgain() {
        var first = CdsTemporal.parse("2020", null, null, null, "Date");
        var second = CdsTemporal.parse("2020", null, null, null, "Date");
        assertThat(first).isEqualTo(second);
        assertThat(first.original()).isEqualTo("2020");
        assertThat(first.latest()).isEqualTo(LocalDate.of(2020, 12, 31));
    }

    @Test
    void shouldPreserveSourceAndEquivalentMidnight_whenXmlSchemaUsesEndOfDayTime() {
        var value = CdsTemporal.parse(null, null, null, "2020-12-31T24:00:00Z", "Date");
        assertThat(value.original()).isEqualTo("2020-12-31T24:00:00Z");
        assertThat(value.earliest()).isEqualTo(LocalDate.of(2021, 1, 1));
        assertThat(value.offsetDateTime().toInstant()).isEqualTo(java.time.Instant.parse("2021-01-01T00:00:00Z"));
    }
}
