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
import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@Isolated("Temporal storage tests temporarily select the actual legacy reader timezone")
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
                .isInstanceOf(CdsClinicalException.class).hasMessageContaining("CDS_LOCAL_TIME_REQUIRES_REVIEW");
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
    @ParameterizedTest
    @ValueSource(strings = {"0001-01-01", "0999-12-31", "1000-01-01", "1500-01-01", "1582-10-15", "1800-01-01", "2020-02-29", "9999-12-31"})
    void shouldKeepActualLegacyReaderDayForCalendarOnlyAnchors_withoutInventingInstant(String source) {
        inZone("America/Toronto", () -> {
            var value = CdsTemporal.parse(null, null, source, null, "Date");
            var anchor = value.timestampStorageAnchor(ZoneId.systemDefault());
            assertThat(PreventionDateDTO.from(anchor, null).getOriginalValue()).isEqualTo(source);
            assertThat(value.dateStorageAnchor()).isInstanceOf(java.sql.Date.class);
            assertThat(((java.sql.Date) value.dateStorageAnchor()).toLocalDate()).isEqualTo(LocalDate.parse(source));
            assertThat(value.original()).isEqualTo(source);
            assertThat(value.precision()).isEqualTo(Precision.FULL_DATE);
        });
    }

    @Test void shouldRetainIntervalsAndSourcePrecision_forEarlyPartialDateAnchors() {
        inZone("America/Toronto", () -> {
            var year = CdsTemporal.parse("1500", null, null, null, "Date");
            var month = CdsTemporal.parse(null, "1500-02", null, null, "Date");
            var yearInfo = PreventionDateDTO.from(year.timestampStorageAnchor(ZoneId.systemDefault()), year.legacyPartialFormat());
            var monthInfo = PreventionDateDTO.from(month.timestampStorageAnchor(ZoneId.systemDefault()), month.legacyPartialFormat());
            assertThat(yearInfo.getOriginalValue()).isEqualTo("1500");
            assertThat(yearInfo.getIntervalEnd()).isEqualTo("1500-12-31");
            assertThat(monthInfo.getOriginalValue()).isEqualTo("1500-02");
            assertThat(monthInfo.getIntervalEnd()).isEqualTo("1500-02-28");
            assertThat(year.calendarAnchorAdjusted(ZoneId.systemDefault())).isTrue();
            assertThat(month.calendarAnchorAdjusted(ZoneId.systemDefault())).isTrue();
        });
    }

    @Test void shouldRequireReviewForUnrepresentableCutoverDay_inBothDateAndTimestampStorage() {
        inZone("America/Toronto", () -> {
            var value = CdsTemporal.parse(null, null, "1582-10-10", null, "Date");
            assertThat(catchThrowableOfType(value::dateStorageAnchor, CdsClinicalException.class).code())
                    .isEqualTo("CDS_NATIVE_DATE_REQUIRES_REVIEW");
            assertThat(catchThrowableOfType(() -> value.timestampStorageAnchor(ZoneId.systemDefault()), CdsClinicalException.class).code())
                    .isEqualTo("CDS_NATIVE_DATE_REQUIRES_REVIEW");
            assertThat(value.original()).isEqualTo("1582-10-10");
        });
    }

    @Test void shouldUseFirstValidSameDayClock_forCalendarDayWithMidnightGap() {
        inZone("America/Sao_Paulo", () -> {
            var value = CdsTemporal.parse(null, null, "2018-11-04", null, "Date");
            var anchor = value.timestampStorageAnchor(ZoneId.systemDefault());
            assertThat(PreventionDateDTO.from(anchor, null).getOriginalValue()).isEqualTo("2018-11-04");
            assertThat(value.calendarAnchorAdjusted(ZoneId.systemDefault())).isTrue();
            var explicitClock = CdsTemporal.parse(null, null, null, "2018-11-04T00:00:00", "Date");
            assertThat(catchThrowableOfType(() -> explicitClock.timestampStorageAnchor(ZoneId.systemDefault()), CdsClinicalException.class).code())
                    .isEqualTo("CDS_LOCAL_TIME_REQUIRES_REVIEW");
        });
    }

    @Test void shouldNotNormalizeWhollySkippedCivilDay_intoFollowingDay() {
        inZone("Pacific/Apia", () -> {
            var value = CdsTemporal.parse(null, null, "2011-12-30", null, "Date");
            assertThat(catchThrowableOfType(() -> value.timestampStorageAnchor(ZoneId.systemDefault()), CdsClinicalException.class).code())
                    .isEqualTo("CDS_NATIVE_DATE_REQUIRES_REVIEW");
            assertThat(value.original()).isEqualTo("2011-12-30");
        });
    }

    @Test void shouldPreserveExplicitOffset_throughReverseLegacyDatetimeFold() {
        inZone("America/Toronto", () -> {
            var early = CdsTemporal.parse(null, null, null, "2020-11-01T01:30:00-04:00", "Date");
            var late = CdsTemporal.parse(null, null, null, "2020-11-01T01:30:00-05:00", "Date");
            assertThat(catchThrowableOfType(() -> early.timestampStorageAnchor(ZoneId.systemDefault()), CdsClinicalException.class).code())
                    .isEqualTo("CDS_NATIVE_TIMESTAMP_REQUIRES_REVIEW");
            assertThat(late.timestampStorageAnchor(ZoneId.systemDefault()).toInstant())
                    .isEqualTo(java.time.Instant.parse("2020-11-01T06:30:00Z"));
            assertThat(early.original()).isEqualTo("2020-11-01T01:30:00-04:00");
            assertThat(early.offsetDateTime().toInstant()).isEqualTo(java.time.Instant.parse("2020-11-01T05:30:00Z"));
        });
    }

    @Test void shouldNotRewriteExactHistoricalTimestamps_asSyntheticCivilAnchors() {
        inZone("America/Toronto", () -> {
            var value = CdsTemporal.parse(null, null, null, "1500-01-01T00:00:00Z", "Date");
            assertThat(catchThrowableOfType(() -> value.timestampStorageAnchor(ZoneId.systemDefault()), CdsClinicalException.class).code())
                    .isEqualTo("CDS_NATIVE_TIMESTAMP_REQUIRES_REVIEW");
            assertThat(value.offsetDateTime().toInstant()).isEqualTo(java.time.Instant.parse("1500-01-01T00:00:00Z"));
        });
    }

    private static void inZone(String zone, Runnable work) {
        TimeZone previous = TimeZone.getDefault();
        try { TimeZone.setDefault(TimeZone.getTimeZone(zone)); work.run(); }
        finally { TimeZone.setDefault(previous); }
    }
}
