/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ExactGraphObservationUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"<0.1", ">100", "<=5", ">=60", "Negative", "Pending", "2+", "1:160", "12/05/2026", "10-20", "NaN", "Infinity", "1e999", "12,34", "100 mmol"})
    void shouldExcludeNonExactValues_withoutInventingObservations(String value) {
        assertThat(MeasurementGraphAction22Action.numericResult(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-12.5", "+12.5", ".125", "1.25e7", "1,250", " 99 "})
    void shouldPreserveExactNumbers_withValidNumericSyntax(String value) {
        assertThat(MeasurementGraphAction22Action.numericResult(value)).isNotNull();
    }
}
