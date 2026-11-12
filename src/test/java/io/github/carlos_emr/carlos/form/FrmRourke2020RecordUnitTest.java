/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Month;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class FrmRourke2020RecordUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"-1,0", "0,0", "6,0", "7,1", "279,39", "280,40", "283,40", "287,41"})
    void shouldCountCompletedWeeks_atCalendarBoundaries(int days, int weeks) {
        LocalDate start = LocalDate.of(2024, Month.FEBRUARY, 1);
        assertThat(FrmRourke2020Record.calculateGestationalAgeInWeeks(Date.valueOf(start),
                Date.valueOf(start.plusDays(days)))).isEqualTo(weeks);
    }
}
