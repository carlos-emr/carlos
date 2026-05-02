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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for billing-history balance arithmetic. */
@DisplayName("BillingOnHistoryBalanceCalculator")
@Tag("unit")
@Tag("billing")
class BillingOnHistoryBalanceCalculatorUnitTest {

    @Test
    void shouldSubtractPaymentsAndDiscounts_thenAddCredits() {
        BigDecimal balance = BillingOnHistoryBalanceCalculator.balance(
                new BigDecimal("200.00"),
                new BigDecimal("50.00"),
                new BigDecimal("25.00"),
                new BigDecimal("10.00"));

        assertThat(balance).isEqualByComparingTo("135.00");
    }

    @Test
    void shouldTreatNullInputs_asZero() {
        BigDecimal balance = BillingOnHistoryBalanceCalculator.balance(
                null, null, null, new BigDecimal("5.00"));

        assertThat(balance).isEqualByComparingTo("5.00");
    }

    @Test
    void shouldNormalizeBalanceToScaleTwo_withHalfUpRounding() {
        BigDecimal balance = BillingOnHistoryBalanceCalculator.balance(
                new BigDecimal("10.005"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(balance).isEqualByComparingTo("10.01");
        assertThat(balance.scale()).isEqualTo(2);
    }
}
