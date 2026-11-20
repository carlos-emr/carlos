/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.appointment.service;

import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class RecurrenceDatesUnitTest {
    private final LocalDate start = LocalDate.of(2027, 1, 31);

    @Test void shouldKeepOriginalDay_forMonthlyDatesAfterShortMonths() {
        assertThat(RecurrenceDates.between(start, start.plusMonths(3), 1, "month"))
                .containsExactly(start, LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
    }

    @Test void shouldRecoverLeapDay_forYearlyDates() {
        LocalDate leap = LocalDate.of(2024, 2, 29);
        assertThat(RecurrenceDates.between(leap, leap.plusYears(4), 1, "year")).hasSize(5)
                .endsWith(LocalDate.of(2028, 2, 29));
    }

    @Test void shouldTreatRangeAsInclusive_forWeeklyDatesWithoutRoundingUp() {
        assertThat(RecurrenceDates.between(start, start.plusDays(15), 1, "week"))
                .containsExactly(start, start.plusDays(7), start.plusDays(14));
    }

    @Test void shouldRejectIntervals_whenZeroNegativeOrUnknownUnit() {
        for (int interval : new int[]{-1, 0, 12}) {
            assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceDates.between(start, start, interval, "day"));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceDates.between(start, start, 1, "invalid"));
    }

    @Test void shouldRejectRanges_whenReversedOrExceedingBounds() {
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceDates.between(start, start.minusDays(1), 1, "day"));
        assertThat(RecurrenceDates.between(start, start.plusDays(365), 1, "day")).hasSize(366);
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceDates.between(start, start.plusDays(366), 1, "day"));
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceDates.between(start, start.plusYears(11), 1, "year"));
    }
}
