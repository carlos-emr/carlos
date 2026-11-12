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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prevention;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Prevention#getCalendarDaysSinceLast(String)} counts calendar dates in the server's time zone, so the time of
 * day a record was saved never moves the count, and it accepts the {@code java.sql} date types the database returns
 * (#5048).
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("prevention")
@DisplayName("Prevention.getCalendarDaysSinceLast")
class PreventionCalendarDaysUnitTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static Date at(LocalDateTime when) {
        return Date.from(when.atZone(ZONE).toInstant());
    }

    private static Prevention withRotaDoseOn(Date given) {
        Prevention prev = new Prevention("F", at(LocalDate.now(ZONE).minusMonths(5).atStartOfDay()));
        prev.addPreventionItem(new PreventionItem("Rota-1", given));
        return prev;
    }

    @Test
    void shouldReturnMinusOne_whenThereIsNoRecordOfTheType() {
        assertThat(withRotaDoseOn(at(LocalDate.now(ZONE).minusDays(30).atStartOfDay())).getCalendarDaysSinceLast("Rota-5"))
                .isEqualTo(-1);
    }

    @Test
    void shouldCountCalendarDates_whenTheDoseWasSavedLateInTheDay() {
        Date lateInTheDay = at(LocalDate.now(ZONE).minusDays(28).atTime(23, 59));
        assertThat(withRotaDoseOn(lateInTheDay).getCalendarDaysSinceLast("Rota-1")).isEqualTo(28);
    }

    @Test
    void shouldReturnANegativeCount_forADoseDatedInTheFuture() {
        Date inThreeDays = at(LocalDate.now(ZONE).plusDays(3).atTime(9, 0));
        assertThat(withRotaDoseOn(inThreeDays).getCalendarDaysSinceLast("Rota-1")).isEqualTo(-3);
    }

    @Test
    void shouldAcceptTheTimestampTheDatabaseReturns_forARecordedDose() {
        Timestamp stored = Timestamp.valueOf(LocalDate.now(ZONE).minusDays(10).atTime(18, 30));
        assertThat(withRotaDoseOn(stored).getCalendarDaysSinceLast("Rota-1")).isEqualTo(10);
    }

    @Test
    void shouldAcceptASqlDate_forARecordedDose() {
        java.sql.Date stored = java.sql.Date.valueOf(LocalDate.now(ZONE).minusDays(5));
        assertThat(withRotaDoseOn(stored).getCalendarDaysSinceLast("Rota-1")).isEqualTo(5);
    }
}
