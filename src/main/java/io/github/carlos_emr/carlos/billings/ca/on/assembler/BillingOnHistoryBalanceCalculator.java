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

/**
 * Pure arithmetic for patient-bill balances in the billing-history view.
 */
final class BillingOnHistoryBalanceCalculator {

    record Result(BigDecimal balance, boolean partial) {
        static final Result ZERO = new Result(BigDecimal.ZERO, false);
    }

    private BillingOnHistoryBalanceCalculator() {
    }

    static BigDecimal balance(BigDecimal total,
                              BigDecimal sumOfPay,
                              BigDecimal sumOfDiscount,
                              BigDecimal sumOfCredit) {
        // Credits move the balance in the opposite direction from payments and
        // discounts because they represent money still owed back onto the bill.
        return nullToZero(total)
                .subtract(nullToZero(sumOfPay))
                .subtract(nullToZero(sumOfDiscount))
                .add(nullToZero(sumOfCredit));
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
