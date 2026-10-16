/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The prescription pick-up time as the card's 24-hour Pick Up Time box posts it (issue #4799).
 */
@Tag("unit")
@DisplayName("Rx pick-up time")
class RxUtilPickupTimeUnitTest {

    @ParameterizedTest(name = "{0} is read as {1}")
    @CsvSource({
            "00:00, 00:00",
            "00:30, 00:30",
            "9:05, 09:05",
            "11:59, 11:59",
            "12:00, 12:00",
            "12:30, 12:30",
            "12:59, 12:59",
            "13:00, 13:00",
            "17:45, 17:45",
            "23:59, 23:59",
    })
    @DisplayName("should read a 24-hour pick-up time as that time, noon included")
    void shouldReadPickupTime_asTwentyFourHourTime(String typed, String read) {
        Date time = RxUtil.parsePickupTime(typed);

        assertThat(RxUtil.DateToString(time, "HH:mm")).isEqualTo(read);
    }

    @Test
    @DisplayName("should mark a noon pick-up on the static script as afternoon, like 13:00")
    void shouldMarkNoonPickup_asAfternoonOnStaticScript() {
        // The static script prints "hh:mm aa"; the marker's text depends on the locale's data.
        String afternoon = RxUtil.DateToString(RxUtil.parsePickupTime("13:00"), "aa");

        assertThat(RxUtil.DateToString(RxUtil.parsePickupTime("12:30"), "hh:mm aa")).isEqualTo("12:30 " + afternoon);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "noon", "12h30"})
    @DisplayName("should read nothing from a value that is not a time")
    void shouldReadNothing_whenValueIsNotATime(String typed) {
        assertThat(RxUtil.parsePickupTime(typed)).isNull();
    }
}
