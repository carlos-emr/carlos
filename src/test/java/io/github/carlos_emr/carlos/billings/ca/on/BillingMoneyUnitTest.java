/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
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

/** Unit coverage for {@code BillingMoney} parsing, formatting, and validation rules. */
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
    void shouldReturnZero_whenAmountOrZeroReceivesNullOrBlank() {
        assertThat(BillingMoney.amountOrZero(null)).isEqualByComparingTo("0.00");
        assertThat(BillingMoney.amountOrZero(" ")).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldReturnZero_whenAmountOrZeroReceivesMalformed() {
        // Behavior contract: amountOrZero must NOT throw on malformed input —
        // it logs and returns ZERO. Mutation paths must use parseNonNegativeAmount.
        assertThat(BillingMoney.amountOrZero("not-money")).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldThrow_whenAmountReceivesMalformed() {
        assertThatThrownBy(() -> BillingMoney.amount("not-money"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldThrowValidation_whenAmountReceivesNull() {
        assertThatThrownBy(() -> BillingMoney.amount(null))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("amount")
                .hasMessageContaining("null or blank");
    }

    @Test
    void shouldThrowValidation_whenOhipFeeAmountReceivesNull() {
        assertThatThrownBy(() -> BillingMoney.ohipFeeAmount(null))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("amount")
                .hasMessageContaining("null or blank");
    }

    @Test
    void shouldThrowValidation_whenIsPositiveReceivesNull() {
        assertThatThrownBy(() -> BillingMoney.isPositive(null))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("amount")
                .hasMessageContaining("null or blank");
    }

    @Test
    void shouldReturnAmount_whenOptionalNonNegativeAmountReceivesValid() {
        assertThat(BillingMoney.parseOptionalNonNegativeAmount("12.34", "fee"))
                .isEqualByComparingTo("12.34");
        assertThat(BillingMoney.parseOptionalNonNegativeAmount("0.005", "fee"))
                .isEqualByComparingTo("0.01");
    }

    @Test
    void shouldReturnZero_whenOptionalNonNegativeAmountReceivesNullOrBlank() {
        assertThat(BillingMoney.parseOptionalNonNegativeAmount(null, "discount"))
                .isEqualByComparingTo("0.00");
        assertThat(BillingMoney.parseOptionalNonNegativeAmount("   ", "discount"))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void shouldThrowValidation_whenOptionalNonNegativeAmountReceivesNegative() {
        assertThatThrownBy(() -> BillingMoney.parseOptionalNonNegativeAmount("-0.01", "discount"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("discount")
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void shouldThrowValidation_whenOptionalNonNegativeAmountReceivesMalformed() {
        assertThatThrownBy(() -> BillingMoney.parseOptionalNonNegativeAmount("1OO", "discount"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("discount")
                .hasMessageContaining("malformed")
                .hasMessageContaining("1OO");
    }

    // ---- parseNonNegativeAmount: routes operator-input errors to the
    //      validation-error JSP instead of the raw IAE that
    //      BillingONCHeader1.setTotal would produce on negative input ------

    @Test
    void shouldParse_whenNonNegativeAmountIsValid() {
        assertThat(BillingMoney.parseNonNegativeAmount("33.70", "total"))
                .isEqualByComparingTo(new java.math.BigDecimal("33.70"));
    }

    @Test
    void shouldParse_whenNonNegativeAmountIsZero() {
        assertThat(BillingMoney.parseNonNegativeAmount("0", "total"))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    void shouldThrowValidation_whenNonNegativeAmountIsNegative() {
        assertThatThrownBy(() -> BillingMoney.parseNonNegativeAmount("-50.00", "total"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("total")
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void shouldThrowValidation_whenNonNegativeAmountIsMalformed() {
        assertThatThrownBy(() -> BillingMoney.parseNonNegativeAmount("not-a-number", "fee"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("fee")
                .hasMessageContaining("not-a-number")
                .hasMessageContaining("malformed")
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldSanitizeRawInput_whenNonNegativeAmountIsMalformed() {
        assertThatThrownBy(() -> BillingMoney.parseNonNegativeAmount("1.00\nWARN forged", "fee"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("fee")
                .hasMessageNotContaining("\n")
                .hasMessageNotContaining("\r");
    }

    @Test
    void shouldSanitizeRawInput_whenStoredCentsIsMalformed() {
        assertThatThrownBy(() -> BillingMoney.storedCents("100\nWARN forged", "total"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("total")
                .hasMessageNotContaining("\n")
                .hasMessageNotContaining("\r");
    }

    @Test
    void shouldThrowValidation_whenNonNegativeAmountIsNull() {
        assertThatThrownBy(() -> BillingMoney.parseNonNegativeAmount(null, "paid"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("paid");
    }

    @Test
    void shouldCreateCadValueType_withScaleAndEquality() {
        BillingMoney oneDollar = BillingMoney.cad(new java.math.BigDecimal("1"));
        BillingMoney sameDollar = BillingMoney.cad(new java.math.BigDecimal("1.00"));

        assertThat(oneDollar).isEqualTo(sameDollar);
        assertThat(oneDollar.amount()).isEqualByComparingTo("1.00");
        assertThat(oneDollar.currency()).isEqualTo(BillingMoney.CAD);
        assertThat(oneDollar.format()).isEqualTo("1.00");
    }

    @Test
    void shouldAddSubtractAndCompareCadValues_withValueType() {
        BillingMoney subtotal = BillingMoney.cad("10.00");
        BillingMoney adjustment = BillingMoney.cad("2.50");

        assertThat(subtotal.plus(adjustment).format()).isEqualTo("12.50");
        assertThat(subtotal.minus(adjustment).format()).isEqualTo("7.50");
        assertThat(subtotal.compareTo(adjustment)).isPositive();
    }

    @Test
    void shouldThrowValidationWithMinusMessage_whenSubtractWouldGoNegative() {
        BillingMoney subtotal = BillingMoney.cad("2.50");
        BillingMoney adjustment = BillingMoney.cad("10.00");

        assertThatThrownBy(() -> subtotal.minus(adjustment))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("subtraction result cannot be negative")
                .hasMessageContaining("2.50")
                .hasMessageContaining("10.00");
    }

    @Test
    void shouldRoundTripStoredCentStrings_throughValueType() {
        BillingMoney amount = BillingMoney.storedCents("3000", "total");

        assertThat(amount.amount()).isEqualByComparingTo("30.00");
        assertThat(amount.toStoredCents()).isEqualTo("3000");
    }

    @Test
    void shouldExposeExplicitBigDecimalZeroFactoryName_forJspContract() {
        assertThat(BillingMoney.zeroAmount()).isEqualByComparingTo("0.00");
        assertThat(java.util.Arrays.stream(BillingMoney.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("zero")))
                .isTrue();
    }

    @Test
    void shouldRejectNegativeValueTypeAmounts_forInvalidInput() {
        assertThatThrownBy(() -> BillingMoney.cad(new java.math.BigDecimal("-0.01")))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void shouldRejectNegativeStoredCentStrings_forInvalidInput() {
        assertThatThrownBy(() -> BillingMoney.storedCents("-1", "total"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("total")
                .hasMessageContaining("cannot be negative");
    }
}
