/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("prescription")
@DisplayName("Renal dosing observation validation")
class RenalDosingInputUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-number", "0", "-1", "NaN", "Infinity", "-Infinity", "1e309"})
    @DisplayName("should refuse missing, malformed, nonpositive and nonfinite observations")
    void shouldRefuse_whenObservationCannotSupportCalculation(String observation) {
        assertThat(RenalDosingInput.positiveNumber(observation)).isNaN();
    }

    @ParameterizedTest
    @ValueSource(strings = {"80", " 80.0 ", "8e1"})
    @DisplayName("should accept positive finite observations including surrounding whitespace")
    void shouldParse_whenObservationIsPositiveAndFinite(String observation) {
        assertThat(RenalDosingInput.positiveNumber(observation)).isEqualTo(80.0);
    }
}
