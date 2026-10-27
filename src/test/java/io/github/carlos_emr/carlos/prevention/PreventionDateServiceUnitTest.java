/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prevention;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PreventionDateServiceUnitTest {
    private final PartialDateDao dates = mock(PartialDateDao.class);
    private final PreventionDateService service = new PreventionDateService(dates);

    @Test
    void shouldPreserveExactJanuaryFirst_whenNoPartialMetadataExists() {
        var prevention = record("2019-01-01");
        var result = service.getDate(prevention);
        assertThat(result.getPrecision()).isEqualTo(PreventionDateDTO.Precision.FULL_DATE);
        assertThat(result.getOriginalValue()).isEqualTo("2019-01-01");
        assertThat(result.getIntervalStart()).isEqualTo(result.getIntervalEnd());
        assertThatCode(result::requireExact).doesNotThrowAnyException();
        verify(dates).getFormat(4, 41, 7);
    }

    @Test
    void shouldRetainYearPrecision_forExistingNativeMetadata() {
        when(dates.getFormat(4, 41, 7)).thenReturn(PartialDate.YEARONLY);
        var result = service.getDate(record("2019-01-01"));
        assertThat(result.getPrecision()).isEqualTo(PreventionDateDTO.Precision.YEAR);
        assertThat(result.getOriginalValue()).isEqualTo("2019");
        assertThat(result.getIntervalStart()).isEqualTo("2019-01-01");
        assertThat(result.getIntervalEnd()).isEqualTo("2019-12-31");
        assertThatThrownBy(result::requireExact).isInstanceOf(PreventionDatePrecisionException.class);
    }

    @Test
    void shouldIncludeLeapDay_whenPrecisionIsYearMonth() {
        when(dates.getFormat(4, 41, 7)).thenReturn(PartialDate.YEARMONTH);
        var result = service.getDate(record("2020-02-01"));
        assertThat(result.getPrecision()).isEqualTo(PreventionDateDTO.Precision.YEAR_MONTH);
        assertThat(result.getOriginalValue()).isEqualTo("2020-02");
        assertThat(result.getIntervalStart()).isEqualTo("2020-02-01");
        assertThat(result.getIntervalEnd()).isEqualTo("2020-02-29");
    }

    @Test
    void shouldRepresentUnknownTiming_whenNativeDateIsAbsent() {
        var result = service.getDate(record(null));
        assertThat(result.getPrecision()).isEqualTo(PreventionDateDTO.Precision.UNKNOWN);
        assertThat(result.getOriginalValue()).isNull();
        assertThat(result.getIntervalStart()).isNull();
        assertThat(result.getIntervalEnd()).isNull();
        assertThatThrownBy(result::requireExact).isInstanceOf(PreventionDatePrecisionException.class);
    }

    @Test
    void shouldRefuseExactness_whenNativeFormatIsUnsupported() {
        when(dates.getFormat(4, 41, 7)).thenReturn("UNKNOWN");
        assertThatThrownBy(() -> service.getDate(record("2019-01-01")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requires review");
    }

    @Test
    void shouldRoundTripPrecisionMetadata_withExplicitCalendarBounds() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var original = PreventionDateDTO.from(Date.valueOf("2020-02-01"), "YYYY-MM");
        var restored = mapper.readValue(mapper.writeValueAsString(original), PreventionDateDTO.class);
        assertThat(restored.getPrecision()).isEqualTo(PreventionDateDTO.Precision.YEAR_MONTH);
        assertThat(restored.getOriginalValue()).isEqualTo("2020-02");
        assertThat(restored.getIntervalEnd()).isEqualTo("2020-02-29");
    }

    private static io.github.carlos_emr.carlos.commn.model.Prevention record(String day) {
        var prevention = new io.github.carlos_emr.carlos.commn.model.Prevention();
        ReflectionTestUtils.setField(prevention, "id", 41);
        prevention.setPreventionDate(day == null ? null : Date.valueOf(day));
        return prevention;
    }
}
