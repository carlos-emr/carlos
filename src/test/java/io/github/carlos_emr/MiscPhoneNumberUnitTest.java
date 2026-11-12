/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional WCB phone fields must not log caught substring exceptions. */
@Tag("unit")
class MiscPhoneNumberUnitTest {

    @Test
    void missingAndShortPhoneNumbersKeepTheirLegacyValuesWithoutLoggingErrors() {
        try (LogCapture logs = LogCapture.forLogger(Misc.class)) {
            for (String input : new String[]{null, "", " ", "N/A", "1", "(12)"}) {
                assertThat(Misc.areaCode(input)).isEqualTo(input);
                assertThat(Misc.phoneNumber(input)).isEqualTo(input);
            }
            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    void formattedPhoneNumbersStillSplitAfterTheFirstThreeDigits() {
        assertThat(Misc.areaCode("(604) 555-0123")).isEqualTo("604");
        assertThat(Misc.phoneNumber("(604) 555-0123")).isEqualTo("5550123");
        assertThat(Misc.areaCode("604")).isEqualTo("604");
        assertThat(Misc.phoneNumber("604")).isEmpty();
    }
}
