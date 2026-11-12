/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

/** The carlos.properties reader shared by the Report by Template and configured-report limits. */
@Tag("unit")
@Tag("report")
@DisplayName("Report limit settings")
class ReportLimitSettingUnitTest {

    @Test
    @DisplayName("should keep the default when the setting is missing or blank")
    void shouldKeepDefault_whenSettingIsMissingOrBlank() {
        Map<String, String> settings = new HashMap<>();
        settings.put("limit.blank", "   ");
        settings.put("limit.empty", "");
        try (LogCapture logs = LogCapture.forLogger(ReportLimitSetting.class)) {
            assertThat(ReportLimitSetting.read(settings::get, "limit.missing", 10_000, 100_000)).isEqualTo(10_000);
            assertThat(ReportLimitSetting.read(settings::get, "limit.blank", 10_000, 100_000)).isEqualTo(10_000);
            assertThat(ReportLimitSetting.read(settings::get, "limit.empty", 10_000, 100_000)).isEqualTo(10_000);
            // Nothing is wrong with an unset limit, so nothing is logged.
            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    @DisplayName("should use a whole number from 1 to the maximum, raised or lowered")
    void shouldUseConfiguredValue_whenItIsWithinRange() {
        Map<String, String> settings = Map.of("limit.raised", "50000", "limit.lowered", " 1 ",
                "limit.maximum", "100000", "limit.plus", "+250");
        assertThat(ReportLimitSetting.read(settings::get, "limit.raised", 10_000, 100_000)).isEqualTo(50_000);
        assertThat(ReportLimitSetting.read(settings::get, "limit.lowered", 10_000, 100_000)).isEqualTo(1);
        assertThat(ReportLimitSetting.read(settings::get, "limit.maximum", 10_000, 100_000)).isEqualTo(100_000);
        assertThat(ReportLimitSetting.read(settings::get, "limit.plus", 10_000, 100_000)).isEqualTo(250);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "-99999999999999999999", "2.5", "1e4", "10 000", "ten thousand", "0x10", "unlimited"})
    @DisplayName("should keep the default for a value that is not a whole number of at least 1")
    void shouldKeepDefault_whenValueIsNotAPositiveWholeNumber(String value) {
        // No value switches a limit off: zero, negative numbers and words all keep the default.
        assertThat(ReportLimitSetting.read(key -> value, "limit.invalid." + value.hashCode(), 10_000, 100_000))
                .isEqualTo(10_000);
    }

    @Test
    @DisplayName("should lower a value above the maximum to the maximum")
    void shouldCapAtMaximum_whenValueIsTooLarge() {
        assertThat(ReportLimitSetting.read(key -> "100001", "limit.over", 10_000, 100_000)).isEqualTo(100_000);
        // More digits than a long holds is still a number above the maximum, not an invalid word.
        assertThat(ReportLimitSetting.read(key -> "99999999999999999999999", "limit.huge", 10_000, 100_000))
                .isEqualTo(100_000);
    }

    @Test
    @DisplayName("should log one warning for an invalid value however often the limit is read")
    void shouldWarnOnce_whenAnInvalidValueIsReadRepeatedly() {
        try (LogCapture logs = LogCapture.forLogger(ReportLimitSetting.class)) {
            for (int run = 0; run < 5; run++) {
                assertThat(ReportLimitSetting.read(key -> "secret-word", "limit.warn_once_invalid", 30, 300)).isEqualTo(30);
            }
            assertThat(logs.events()).hasSize(1);
            assertThat(logs.events().get(0).getLevel()).isEqualTo(Level.WARN);
            // The setting's name and the default, never the raw value.
            assertThat(logs.messages().get(0))
                    .contains("limit.warn_once_invalid").contains("using the default 30").doesNotContain("secret-word");
        }
    }

    @Test
    @DisplayName("should log one warning when a value above the maximum is capped")
    void shouldWarnOnce_whenAValueAboveTheMaximumIsCapped() {
        try (LogCapture logs = LogCapture.forLogger(ReportLimitSetting.class)) {
            for (int run = 0; run < 3; run++) {
                assertThat(ReportLimitSetting.read(key -> "301", "limit.warn_once_capped", 30, 300)).isEqualTo(300);
            }
            assertThat(logs.events()).hasSize(1);
            assertThat(logs.messages().get(0)).contains("limit.warn_once_capped").contains("maximum of 300");
        }
    }

    @Test
    @DisplayName("should read the server's properties at every call, without warning about an unset key")
    void shouldReadServerProperties_atEveryCall() {
        String key = "report_limit_setting_unit_test.max_rows";
        CarlosProperties properties = CarlosProperties.getInstance();
        try (LogCapture propertyLogs = LogCapture.forLogger(CarlosProperties.class)) {
            assertThat(ReportLimitSetting.configured(key)).isNull();
            assertThat(ReportLimitSetting.read(key, 10_000, 100_000)).isEqualTo(10_000);
            // CarlosProperties warns about every missing key it is asked for; an optional limit must not.
            assertThat(propertyLogs.messages()).noneMatch(message -> message.contains(key));
            properties.setProperty(key, "20000");
            assertThat(ReportLimitSetting.read(key, 10_000, 100_000)).isEqualTo(20_000);
            properties.setProperty(key, "30000");
            assertThat(ReportLimitSetting.read(key, 10_000, 100_000)).isEqualTo(30_000);
        } finally {
            properties.remove(key);
        }
        assertThat(ReportLimitSetting.read(key, 10_000, 100_000)).isEqualTo(10_000);
    }
}
