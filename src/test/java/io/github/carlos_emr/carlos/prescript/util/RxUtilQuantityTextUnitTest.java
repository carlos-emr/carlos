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
 * A quantity typed with a unit ("2.5 mL") is split into the amount and the unit before it is saved
 * and written into the instruction text as {@code Qty:<amount> <unit>}. The split took the first
 * run of digits as the amount and deleted those digits from the text to find the unit, so "2.5 mL"
 * was saved and printed as {@code Qty:2 .5 mL}: the wrong amount, followed by a decimal with no
 * leading zero, which is on ISMP Canada's <i>Do Not Use</i> list (#6185).
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("RxUtil quantity text")
class RxUtilQuantityTextUnitTest {

    @ParameterizedTest(name = "\"{0}\" -> amount {1}, unit {2}")
    @CsvSource({
        "2.5 mL, 2.5, mL",
        "0.5 mL, 0.5, mL",
        "0.0625 mL, 0.0625, mL",
        "2.25 mL, 2.25, mL",
        "1.1 mL, 1.1, mL",
        "22.5 g, 22.5, g",
        "3.5g, 3.5, g",
        "500 mL, 500, mL",
        "1 mL, 1, mL",
        "30 tabs, 30, tabs",
        "100g, 100, g"
    })
    @DisplayName("should keep the whole decimal amount and leave only the unit")
    void shouldKeepDecimalAmount_forQuantityTypedWithUnit(String typed, String amount, String unit) {
        assertAmountAndUnit(typed, amount, unit);
    }

    @ParameterizedTest(name = "\"{0}\" -> amount {1}, unit {2}")
    @CsvSource({
        ".5 mL, 0.5, mL",
        "2.50 mL, 2.5, mL",
        "30.0 mL, 30, mL",
        "1.0 bottle, 1, bottle"
    })
    @DisplayName("should write the amount with a leading zero and no trailing zero")
    void shouldWritePlainDecimalAmount_forDoNotUseFormsTypedWithUnit(String typed, String amount, String unit) {
        assertAmountAndUnit(typed, amount, unit);
    }

    private static void assertAmountAndUnit(String typed, String amount, String unit) {
        assertThat(RxUtil.getQuantityFromQuantityText(typed)).isEqualTo(amount);
        assertThat(RxUtil.getUnitNameFromQuantityText(typed)).isEqualTo(unit);
    }

    @Test
    @DisplayName("should remove only the amount from the unit, not the same digits later in the text")
    void shouldRemoveOnlyTheAmount_whenUnitRepeatsItsDigits() {
        // Deleting every "1" turned this unit into "bottle (20 mL)".
        assertThat(RxUtil.getQuantityFromQuantityText("1 bottle (120 mL)")).isEqualTo("1");
        assertThat(RxUtil.getUnitNameFromQuantityText("1 bottle (120 mL)")).isEqualTo("bottle (120 mL)");
    }

    @Test
    @DisplayName("should keep text before the amount as part of the unit")
    void shouldKeepLeadingText_whenAmountFollowsWords() {
        assertThat(RxUtil.getQuantityFromQuantityText("tube of 15 g")).isEqualTo("15");
        assertThat(RxUtil.getUnitNameFromQuantityText("tube of 15 g"))
                .startsWith("tube of").endsWith("g").doesNotContain("15");
    }

    @Test
    @DisplayName("should report no unit for a bare amount")
    void shouldReturnNullUnit_forAmountWithoutUnit() {
        assertThat(RxUtil.getQuantityFromQuantityText("100")).isEqualTo("100");
        assertThat(RxUtil.getUnitNameFromQuantityText("100")).isNull();
        assertThat(RxUtil.getQuantityFromQuantityText("2.5")).isEqualTo("2.5");
        assertThat(RxUtil.getUnitNameFromQuantityText("2.5")).isNull();
    }

    @Test
    @DisplayName("should report neither amount nor unit for text without a number, or no text")
    void shouldReturnNull_forTextWithoutNumber() {
        assertThat(RxUtil.getQuantityFromQuantityText("one bottle")).isNull();
        assertThat(RxUtil.getUnitNameFromQuantityText("one bottle")).isNull();
        assertThat(RxUtil.getQuantityFromQuantityText(null)).isNull();
        assertThat(RxUtil.getUnitNameFromQuantityText(null)).isNull();
        assertThat(RxUtil.getQuantityFromQuantityText("")).isNull();
        assertThat(RxUtil.getUnitNameFromQuantityText("")).isNull();
    }

    @Test
    @DisplayName("should not read a sentence's full stop as a decimal point")
    void shouldNotTakeTrailingFullStop_asPartOfAmount() {
        assertThat(RxUtil.getQuantityFromQuantityText("30. tabs")).isEqualTo("30");
        assertThat(RxUtil.getUnitNameFromQuantityText("30. tabs")).isEqualTo(". tabs");
    }
}
