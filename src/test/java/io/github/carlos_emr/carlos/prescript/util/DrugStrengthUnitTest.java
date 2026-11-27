/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prescript.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link DrugStrength} parsing and identity-text normalization.
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("DrugStrength unit tests")
class DrugStrengthUnitTest {

    @ParameterizedTest
    @CsvSource({
            "'amoxicillin 250.0mg capsule', 'amoxicillin 250mg capsule'",
            "'amoxicillin 250.0 mg/5.0 ml', 'amoxicillin 250 mg/5 ml'",
            "'amoxicillin 250.0mg/5.0mL', 'amoxicillin 250mg/5mL'",
            "'amoxicillin/clavulanate 500.0mg/125.0mg', 'amoxicillin/clavulanate 500mg/125mg'",
            "'drug 100.0mg + 25.0mg', 'drug 100mg + 25mg'",
            "'levothyroxine 0.050mg', 'levothyroxine 0.05mg'",
            "'fentanyl 25.0\u00b5g/h', 'fentanyl 25\u00b5g/h'",
            "'fentanyl 25.0\u03bcg/h', 'fentanyl 25\u03bcg/h'",
            "'Hydrocortisone 1.0%', 'hydrocortisone 1%'"
    })
    @DisplayName("should treat equivalent strength spellings as the same identity text")
    void shouldTreatEquivalentStrengthSpellings_asSameIdentityText(String first, String second) {
        assertThat(DrugStrength.sameIdentityText(first, second)).isTrue();
        assertThat(DrugStrength.sameIdentityText(second, first)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "'1/2 mg', '0.5 mg'",
            "'5-10 mg', '5 - 10 mg'",
            "'1,000 mg', '1000 mg'",
            "'1:1000 mg', '1:1000.0 mg'",
            "'-5.0 mg', '-5 mg'",
            "'amoxicillin 250mg', 'amoxicillin 500mg'"
    })
    @DisplayName("should preserve fractions, ranges, grouped numbers, ratios, signed values, and distinct strengths")
    void shouldNotCollapseDistinctOrNonPlainNumericForms_whenComparingIdentityText(String first, String second) {
        assertThat(DrugStrength.sameIdentityText(first, second)).isFalse();
    }

    @Test
    @DisplayName("should handle null and blank text safely")
    void shouldHandleNullAndBlankText_whenComparingIdentityText() {
        assertThat(DrugStrength.sameIdentityText(null, null)).isTrue();
        assertThat(DrugStrength.sameIdentityText("   ", "")).isTrue();
        assertThat(DrugStrength.sameIdentityText("   ", null)).isFalse();
        assertThat(DrugStrength.sameIdentityText("250 mg", null)).isFalse();
    }

    @Test
    @DisplayName("should parse single numeric dosage and leave ambiguous leading numbers unstructured")
    void shouldParseSingleDosage_andLeaveAmbiguousLeadingNumbersUnstructured() {
        DrugStrength single = DrugStrength.fromDosage("250.0 mg");
        assertThat(single.amount()).isEqualTo(250f);
        assertThat(single.unit()).isEqualTo("mg");
        assertThat(single.dosageText()).isEqualTo("250 mg");

        DrugStrength range = DrugStrength.fromDosage("5-10 mg");
        assertThat(range.amount()).isNull();
        assertThat(range.unit()).isNull();

        DrugStrength grouped = DrugStrength.fromDosage("1,000 mg");
        assertThat(grouped.amount()).isNull();
        assertThat(grouped.unit()).isNull();
    }

    @Test
    @DisplayName("should normalize long adversarial input in linear time")
    void shouldNormalizeLongInputInLinearTime_withoutBacktracking() {
        String repeated = "1".repeat(20_000) + "mg";
        assertThat(DrugStrength.sameIdentityText(repeated, repeated)).isTrue();
        String spaced = " ".repeat(20_000) + "250.0mg";
        assertThat(DrugStrength.sameIdentityText(spaced, "250mg")).isTrue();
    }

    @Test
    @DisplayName("should normalize leading-dot decimals in DoseText.withPlainNumbers without mangling versions or words")
    void shouldNormalizeLeadingDotDecimals_whenComparingPlainNumbers() {
        assertThat(io.github.carlos_emr.carlos.utility.DoseText.withPlainNumbers(".50mg"))
                .isEqualTo("0.5mg")
                .isEqualTo(io.github.carlos_emr.carlos.utility.DoseText.withPlainNumbers("0.5mg"));
        assertThat(io.github.carlos_emr.carlos.utility.DoseText.withPlainNumbers("digoxin .125mg"))
                .isEqualTo("digoxin 0.125mg");
        assertThat(io.github.carlos_emr.carlos.utility.DoseText.withPlainNumbers("250.0mg"))
                .isEqualTo("250mg");
        assertThat(io.github.carlos_emr.carlos.utility.DoseText.withPlainNumbers("v.5 1.2.5 ..5"))
                .isEqualTo("v.5 1.2.5 ..5");
    }
}
