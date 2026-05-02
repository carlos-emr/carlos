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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * Money parser/formatter and accumulator for the paid/unpaid rows rendered by
 * {@link BillingOnNewReportViewModelAssembler}.
 */
final class BillingOnNewReportTotalsCalculator {

    private BigDecimal paidClaimTotal = BigDecimal.ZERO;
    private BigDecimal paidAmountTotal = BigDecimal.ZERO;
    private BigDecimal unpaidClaimTotal = BigDecimal.ZERO;

    void addPaidRow(String claimAmount, String paidAmount) {
        paidClaimTotal = paidClaimTotal.add(parseMoney(claimAmount));
        paidAmountTotal = paidAmountTotal.add(parseMoney(paidAmount));
    }

    void addUnpaidClaim(String claimAmount) {
        unpaidClaimTotal = unpaidClaimTotal.add(parseMoney(claimAmount));
    }

    String addPaidAmount(String currentAmount, String amountToAdd) {
        return formatMoney(parseMoney(currentAmount).add(parseMoney(amountToAdd)));
    }

    List<String> paidTotalRow() {
        return Arrays.asList("Total", "", "",
                formatMoney(paidClaimTotal),
                formatMoney(paidAmountTotal),
                "");
    }

    List<String> unpaidTotalRow() {
        return Arrays.asList("Total", "", "",
                formatMoney(unpaidClaimTotal),
                "", "", "");
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private static String formatMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
