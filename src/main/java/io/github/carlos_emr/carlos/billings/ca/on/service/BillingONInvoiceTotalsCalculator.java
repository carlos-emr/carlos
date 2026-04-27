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
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Pure financial calculations on Ontario billing invoices. Replaces the
 * two calculator methods that lived on the legacy {@code BillingONService}
 * catch-all (which has been deleted; {@code getNonDeletedInvoices} moved
 * to {@link BillingONCHeader1Dao#findActiveItems}).
 *
 * <p>Both methods are read-only and side-effect-free except for an info
 * log on {@link #recomputeTotal}. Callers decide whether to mutate the
 * entity and persist; this class never sets fields or merges.</p>
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

    /**
     * Recomputes the header total from the sum of its non-deleted items'
     * fees. Returns the new total, or {@link Optional#empty()} if any
     * item's fee can't be parsed (the caller should treat that as a
     * validation failure and refuse to persist).
     *
     * <p>Pure: does not call {@code header.setTotal(...)}. The caller
     * decides whether to mutate the entity based on the returned value.</p>
     */
    public Optional<BigDecimal> recomputeTotal(BillingONCHeader1 header) {
        if (header == null || header.getBillingItems() == null) {
            return Optional.empty();
        }
        BigDecimal feeTotal = BigDecimal.ZERO;
        for (BillingONItem bItem : header.getBillingItems()) {
            if (BillingONItem.DELETED.equals(bItem.getStatus())) {
                continue;
            }
            String feeStr = bItem.getFee();
            if (feeStr == null) {
                MiscUtils.getLogger().error("Fee is null on active item; refusing recompute");
                return Optional.empty();
            }
            try {
                feeTotal = feeTotal.add(new BigDecimal(feeStr));
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Fee not valid amount:" + feeStr, e);
                return Optional.empty();
            }
        }
        return Optional.of(feeTotal);
    }
}
