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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit coverage for Ontario new-report paid/unpaid money totals. */
@DisplayName("BillingOnNewReportTotalsCalculator")
@Tag("unit")
@Tag("billing")
class BillingOnNewReportTotalsCalculatorUnitTest {

    @Test
    void shouldTotalPaidClaimAndPaidAmounts_fromRaDetailRows() {
        BillingOnNewReportTotalsCalculator calculator = new BillingOnNewReportTotalsCalculator();

        calculator.addPaidRow("80.00", "30.00");
        calculator.addPaidRow("20.00", "5.00");

        assertThat(calculator.paidTotalRow())
                .containsExactly("Total", "", "", "100.00", "35.00", "");
    }

    @Test
    void shouldTreatBlankAndNullAmountsAsZero_whenBuildingReportTotals() {
        BillingOnNewReportTotalsCalculator calculator = new BillingOnNewReportTotalsCalculator();

        calculator.addPaidRow("", null);
        calculator.addUnpaidClaim("");

        assertThat(calculator.paidTotalRow())
                .containsExactly("Total", "", "", "0.00", "0.00", "");
        assertThat(calculator.unpaidTotalRow())
                .containsExactly("Total", "", "", "0.00", "", "", "");
    }

    @Test
    void shouldFormatAddedPaidAmountToTwoDecimals_whenCombiningDuplicateBillingRows() {
        BillingOnNewReportTotalsCalculator calculator = new BillingOnNewReportTotalsCalculator();

        assertThat(calculator.addPaidAmount("30.00", "5.005")).isEqualTo("35.01");
    }

    @Test
    void shouldThrowNumberFormatException_whenMoneyIsMalformed() {
        BillingOnNewReportTotalsCalculator calculator = new BillingOnNewReportTotalsCalculator();

        assertThatThrownBy(() -> calculator.addUnpaidClaim("not-money"))
                .isInstanceOf(NumberFormatException.class);
    }
}
