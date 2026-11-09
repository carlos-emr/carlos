/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("prescription")
@DisplayName("Renal dosing observation validation")
class RenalDosingInputUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"100,umol/L,100", "100,µmol/L,100", "100,μmol/L,100", "1,mg/dL,88.4"})
    void shouldNormalizeCreatinine_whenUnitsAreExplicit(String value, String units, double expected) {
        assertThat(RenalDosingInput.creatinineMicromoles(value, units)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "mg/L", "null", "mmol/L"})
    void shouldRefuseCreatinine_whenUnitsAreMissingOrUnsupported(String units) {
        assertThat(RenalDosingInput.creatinineMicromoles("1", units)).isNaN();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-number", "0", "-1", "NaN", "Infinity", "-Infinity", "1e309",
            "0x1p6", "80f", "80d", "80kg", "1,000", "+", "."})
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

    @ParameterizedTest
    @CsvSource({",01,01", "1966,,01", "1966,01,", "' ',01,01", "text,01,01",
            "1966,xx,01", "1966,01,xx", "0,01,01", "99999,01,01", "2025,02,29",
            "1966,13,01", "1966,01,00", "1966,01,32"})
    @DisplayName("should refuse incomplete, malformed and impossible birth dates")
    void shouldRefuse_whenBirthDateCannotSupportAge(String year, String month, String day) {
        assertThat(RenalDosingInput.validBirthDate(year, month, day)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"1966,01,01", "2000,02,29", "2026,11,04", "2026,1,1"})
    @DisplayName("should accept complete valid birth dates including leap days")
    void shouldAccept_whenBirthDateIsValid(String year, String month, String day) {
        assertThat(RenalDosingInput.validBirthDate(year, month, day)).isTrue();
    }
}
