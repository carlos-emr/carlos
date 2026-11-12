/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.rx;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The static script hides the pick-up time of a pick-up date saved without one (stored at midnight). It used to
 * compare the formatted time with "12:00 AM", which never matched where the locale formats midnight as
 * "12:00 a.m." (en_CA), so a made-up time was printed.
 */
@DisplayName("StaticScriptBean pick-up time")
@Tag("unit")
@Tag("rx")
class StaticScriptBeanPickupTimeUnitTest extends CarlosUnitTestBase {

    // StaticScriptBean looks its DrugDao up from Spring when the class loads.
    @BeforeEach
    void registerDrugDao() {
        createAndRegisterMock(DrugDao.class);
    }

    private static Date at(int hour, int minute, int second) {
        return Date.from(LocalDateTime.of(2026, 10, 20, hour, minute, second).atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("should report no pick-up time when there is no pick-up date")
    void shouldReportNoPickupTime_forNullDate() {
        assertThat(StaticScriptBean.hasPickupTime(null)).isFalse();
    }

    @ParameterizedTest(name = "{0}:{1}:{2} -> {3}")
    @CsvSource({
            "0, 0, 0, false",
            "0, 0, 30, false",
            "0, 1, 0, true",
            "0, 30, 0, true",
            "12, 0, 0, true",
            "12, 30, 0, true",
            "23, 59, 0, true",
    })
    @DisplayName("should treat only midnight as no pick-up time")
    void shouldTreatOnlyMidnightAsNoTime_forStoredPickupDateTime(int hour, int minute, int second, boolean expected) {
        assertThat(StaticScriptBean.hasPickupTime(at(hour, minute, second))).isEqualTo(expected);
    }
}
