/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.prescript.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("prescript")
class DrugPriceEstimateUnitTest {

    @ParameterizedTest
    @CsvSource({
        ".0817, 30, $2.45",
        "0.0817, 60, $4.90",
        "' .0817 ', 60, $4.90",
        "2.6741, 30, $80.22",
        "2.6741, 12.5, $33.43",
        "2.6741, .5, $1.34",
        "0, 30, $0.00"
    })
    void shouldRoundAfterMultiplication_whenPriceAndQuantityAreDecimal(String price, String quantity, String amount) {
        assertThat(DrugPriceEstimate.from(price, quantity)).isEqualTo(new DrugPriceEstimate(amount, quantity));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "-.08", "1.2.3", "1e2", "$2", "1,25", "NA"})
    void shouldOmitEstimate_whenUnitPriceIsInvalid(String price) {
        assertThat(DrugPriceEstimate.from(price, "30")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "0.00", "-1", "30 days", "30 tabs", "NaN"})
    void shouldQuoteUnitPrice_whenQuantityIsNotAPositiveDecimal(String quantity) {
        assertThat(DrugPriceEstimate.from(".0817", quantity)).isEqualTo(new DrugPriceEstimate("$0.08", null));
    }

    @ParameterizedTest
    @CsvSource({
        ", Tablet,",
        "' 30 ', , 30",
        "0, Tablet,",
        "'30 tabs', Tablet, 30",
        "'30 TABLETS', Tablet (extended-release), 30",
        "'30 caps.', Capsule, 30",
        "'0.5 tablet', Tablet, 0.5",
        "'30 days', Tablet,",
        "'2 wk', Tablet,",
        "'1-2 tabs', Tablet,",
        "'30 30 tabs', Tablet,",
        "'-30 tabs', Tablet,",
        "'30 mL', Solution,",
        "'30 tabs', Capsule,",
        "'30 tabs', ,"
    })
    void shouldResolveDispenseCount_whenPlainOrInMatchingDosageUnits(String text, String form, String quantity) {
        assertThat(DrugPriceEstimate.dispenseQuantity(text, form)).isEqualTo(quantity);
    }

    @ParameterizedTest
    @CsvSource({
        "30, null, 30",
        "30, ' ', 30",
        "30, tabs, 30",
        "30, days,",
        "12, '.5 tabs',",
        "30, '30 tabs',",
        "0, ,"
    })
    void shouldResolveDispenseCount_whenQuantityAndUnitAreStoredSeparately(String count, String unit, String quantity) {
        assertThat(DrugPriceEstimate.dispenseQuantity(count, unit, "Tablet")).isEqualTo(quantity);
    }
}
