/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.service;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class FieldNoteDateRangeUnitTest {
    @ParameterizedTest
    @CsvSource({"2026-02-30,2026-03-02", "2026-03-02,2026-03-01",
            "2026-03-01junk,2026-03-02", "2026-13-01,2026-12-01",
            ",2026-03-02", "2026-03-01,"})
    void shouldRejectInvalidRanges_beforeQueryingReports(String start, String end) {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldNoteDateRange.parse(start, end));
    }

    @Test
    void shouldIncludeEntireEndDay_whenRangeIsOneLeapDay() {
        var range = FieldNoteDateRange.parse("2024-02-29", "2024-02-29");
        var zone = ZoneId.systemDefault();
        assertThat(range.startDate().toInstant()).isEqualTo(LocalDate.of(2024, 2, 29).atStartOfDay(zone).toInstant());
        assertThat(range.endExclusiveDate().toInstant()).isEqualTo(LocalDate.of(2024, 3, 1).atStartOfDay(zone).toInstant());
    }
}
