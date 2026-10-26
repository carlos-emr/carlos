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
package io.github.carlos_emr.carlos.sms.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmsQueueWindow}: only the listed {@code window} parameter values are accepted, and everything else
 * gives the 30-day default.
 *
 * @since 2026-09-29
 */
@Tag("unit")
@Tag("security")
@DisplayName("SMS queue time period")
class SmsQueueWindowUnitTest {
    private static final Instant NOW = Instant.parse("2026-09-28T14:00:00Z");

    @ParameterizedTest(name = "{0} gives {1}")
    @CsvSource({"7d,LAST_7_DAYS", "30d,LAST_30_DAYS", "90d,LAST_90_DAYS", "all,ALL_TIME"})
    @DisplayName("should return the matching time period for each allowed value")
    void shouldReturnMatchingWindow_whenParameterIsAllowed(String parameter, SmsQueueWindow expected) {
        assertThat(SmsQueueWindow.fromParameter(parameter)).isEqualTo(expected);
        assertThat(expected.parameterValue()).isEqualTo(parameter);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "\t", "junk", "7", "d", "60d", "365d", "-7d", "7d ", " 7d", "7D", "ALL", "All",
            "LAST_7_DAYS", "ALL_TIME", "7d,30d", "7d' OR '1'='1", "all; DROP TABLE sms_transaction; --",
            "<script>alert(1)</script>", "${window}"})
    @DisplayName("should fall back to 30 days when the parameter is missing, blank or not an allowed value")
    void shouldReturnDefault_whenParameterIsNotAllowed(String parameter) {
        assertThat(SmsQueueWindow.fromParameter(parameter)).isEqualTo(SmsQueueWindow.LAST_30_DAYS);
    }

    @Test
    @DisplayName("should use 30 days as the default")
    void shouldDefaultToThirtyDays() {
        assertThat(SmsQueueWindow.DEFAULT).isEqualTo(SmsQueueWindow.LAST_30_DAYS);
    }

    @Test
    @DisplayName("should start each time period that many days before now, and have no start for all time")
    void shouldComputeStart_fromNow() {
        assertThat(SmsQueueWindow.LAST_7_DAYS.since(NOW)).isEqualTo(NOW.minus(Duration.ofDays(7)));
        assertThat(SmsQueueWindow.LAST_30_DAYS.since(NOW)).isEqualTo(NOW.minus(Duration.ofDays(30)));
        assertThat(SmsQueueWindow.LAST_90_DAYS.since(NOW)).isEqualTo(NOW.minus(Duration.ofDays(90)));
        assertThat(SmsQueueWindow.ALL_TIME.since(NOW)).isNull();
    }

    @Test
    @DisplayName("should list the parameter values in the order the page shows them")
    void shouldListParameterValues_inDisplayOrder() {
        assertThat(SmsQueueWindow.parameterValues()).containsExactly("7d", "30d", "90d", "all");
    }
}
