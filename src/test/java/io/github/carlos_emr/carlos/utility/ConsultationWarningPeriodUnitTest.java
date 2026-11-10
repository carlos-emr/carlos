/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ConsultationWarningPeriodUnitTest {
    @Test
    void shouldPreserveMonths_whenValidOrBlank() {
        assertThat(ConsultationWarningPeriod.monthsOrDefault(null)).isEqualTo(1);
        assertThat(ConsultationWarningPeriod.monthsOrDefault(" ")).isEqualTo(1);
        assertThat(ConsultationWarningPeriod.monthsOrDefault(" 6 ")).isEqualTo(6);
    }

    @Test
    void shouldRejectNewValuesAndRecoverStoredValues_whenMalformed() {
        for (String value : new String[]{"6 months", "0", "-1", "1.5", "2147483648", "<script>"}) {
            assertThatThrownBy(() -> ConsultationWarningPeriod.validate(value))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(ConsultationWarningPeriod.monthsOrDefault(value)).isEqualTo(1);
        }
    }
}
