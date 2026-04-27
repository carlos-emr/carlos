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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Cross-DAO financial calculations on Ontario billing invoices.
 *
 * <p>Houses operations that need data beyond a single header — currently
 * only {@link #calculateBalanceOwing}, which loads payments via
 * {@link BillingONPaymentDao}. The simpler "sum the active items"
 * recompute moved to {@link BillingONCHeader1#recomputeTotalFromItems()}
 * since it's pure arithmetic on the entity's own collection.</p>
 *
 * <p>Read-only and side-effect-free. Callers decide whether to mutate
 * the entity and persist; this class never sets fields or merges.</p>
 *
 * @since 2026-04-27
 */
@Service
public class BillingONInvoiceTotalsCalculator {

    private final BillingONCHeader1Dao headerDao;
    private final BillingONPaymentDao paymentDao;

    public BillingONInvoiceTotalsCalculator(BillingONCHeader1Dao headerDao,
                                            BillingONPaymentDao paymentDao) {
        this.headerDao = headerDao;
        this.paymentDao = paymentDao;
    }

    /**
     * @return {@code total - paidTotal + refundTotal} for the invoice, or
     *         {@code null} if the invoice doesn't resolve.
     */
    public BigDecimal calculateBalanceOwing(Integer invoiceNo) {
        BillingONCHeader1 header = headerDao.find(invoiceNo);
        if (header == null) {
            MiscUtils.getLogger().error("Cannot find BillingONCHeader1 JPA Entity for Invoice No." + invoiceNo);
            return null;
        }
        List<BillingONPayment> paymentRecords = paymentDao.find3rdPartyPayRecordsByBill(header);
        BigDecimal paidTotal = BillingONPaymentDao.calculatePaymentTotal(paymentRecords);
        BigDecimal refundTotal = BillingONPaymentDao.calculateRefundTotal(paymentRecords);
        return header.getTotal().subtract(paidTotal).add(refundTotal);
    }

}
