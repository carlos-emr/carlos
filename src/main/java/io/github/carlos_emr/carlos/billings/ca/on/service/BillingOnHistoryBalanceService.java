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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * DAO-backed balance service for the billing-history view.
 *
 * <p>Coordinates the claim header and payment DAOs, returning a balance
 * {@link BillingOnHistoryBalanceCalculator.Result} so callers can surface
 * partial-load warnings when one row cannot be calculated reliably.</p>
 */
@org.springframework.stereotype.Service
public final class BillingOnHistoryBalanceService {

    private final BillingONPaymentDao billingOnPaymentDao;
    private final BillingONCHeader1Dao bCh1Dao;

    public BillingOnHistoryBalanceService(BillingONPaymentDao billingOnPaymentDao,
                                          BillingONCHeader1Dao bCh1Dao) {
        this.billingOnPaymentDao = billingOnPaymentDao;
        this.bCh1Dao = bCh1Dao;
    }

    public BillingOnHistoryBalanceCalculator.Result calculate(String billingId) {
        try {
            int billingNo = Integer.parseInt(billingId);
            BillingONCHeader1 bCh1 = bCh1Dao.find(billingNo);
            if (bCh1 == null || bCh1.getTotal() == null) {
                MiscUtils.getLogger().warn(
                        "BillingOnHistory: bill id [{}] has no claim header total; rendering partial balance=0.00",
                        LogSanitizer.sanitize(billingId));
                return new BillingOnHistoryBalanceCalculator.Result(BigDecimal.ZERO.setScale(2), true);
            }
            BigDecimal sumOfPay = BigDecimal.ZERO;
            BigDecimal sumOfDiscount = BigDecimal.ZERO;
            BigDecimal sumOfCredit = BigDecimal.ZERO;
            for (BillingONPayment payment : billingOnPaymentDao.find3rdPartyPaymentsByBillingNo(billingNo)) {
                sumOfPay = sumOfPay.add(nullToZero(payment.getTotal_payment()));
                sumOfDiscount = sumOfDiscount.add(nullToZero(payment.getTotal_discount()));
                sumOfCredit = sumOfCredit.add(nullToZero(payment.getTotal_credit()));
            }
            return new BillingOnHistoryBalanceCalculator.Result(
                    BillingOnHistoryBalanceCalculator.balance(
                            bCh1.getTotal(), sumOfPay, sumOfDiscount, sumOfCredit),
                    false);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn(
                    "BillingOnHistory: bill id [{}] is not numeric; rendering balance=0.00",
                    LogSanitizer.sanitize(billingId), e);
            return new BillingOnHistoryBalanceCalculator.Result(BigDecimal.ZERO.setScale(2), true);
        }
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
