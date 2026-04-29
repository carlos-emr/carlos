/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ON billing money parsing")
@Tag("unit")
@Tag("billing")
class BillingMoneyUnitTest {

    @Test
    void shouldParseFixedWidthOhipFee_withoutBinaryFloatingPointRounding() {
        assertThat(BillingMoney.ohipFeeAmount("00000010050")).isEqualByComparingTo("1.01");
    }

    @Test
    void shouldRoundStoredAmount_withDecimalHalfUpSemantics() {
        assertThat(BillingMoney.amount("1.005")).isEqualByComparingTo("1.01");
    }

    @Test
    void shouldRoundStoredAmount_atRequestedScale() {
        assertThat(BillingMoney.amount("1.00005", 4)).isEqualByComparingTo("1.0001");
    }

    @Test
    void shouldComparePositiveAmounts_withoutDoubleConversion() {
        assertThat(BillingMoney.isPositive("0.0001")).isTrue();
        assertThat(BillingMoney.isPositive("0.0000")).isFalse();
    }

    @Test
    void shouldReturnZero_forMissingAmount_andRejectInvalid_withNumberFormatException() {
        assertThat(BillingMoney.amountOrZero(null)).isEqualByComparingTo("0.00");
        assertThat(BillingMoney.amountOrZero(" ")).isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> BillingMoney.amount("not-money"))
                .isInstanceOf(NumberFormatException.class);
    }
}
