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
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Atomic save of a third-party payment row + cascading header / ext / per-item
 * writes. Mirrors {@link BillingPaymentDeletionService} for the inverse path.
 *
 * <p>The full ~10-write sequence (header {@code paid} merge, 5 ext-key
 * upserts, payment row, per-item ItemPayment + Transaction rows) runs under
 * one {@code @Transactional} boundary, so any throw aborts the entire save
 * and leaves the DB exactly as it was before the call. The action is a thin
 * gate: parse + validate upfront, build a {@link Command}, hand it off,
 * render the JSON response.</p>
 *
 * @since 2026-04-30
 */
@Service
@Transactional
public class BillingPaymentSaveService {

    private final BillingONCHeader1Dao bCh1Dao;
    private final BillingONExtDao bExtDao;
    private final BillingONItemDao bItemDao;
    private final BillingONPaymentDao bPaymentDao;
    private final BillingOnItemPaymentDao bItemPaymentDao;
    private final BillingOnTransactionDao bTransactionDao;
    private final BillingThirdPartyService thirdPartyService;

    public BillingPaymentSaveService(BillingONCHeader1Dao bCh1Dao,
                                     BillingONExtDao bExtDao,
                                     BillingONItemDao bItemDao,
                                     BillingONPaymentDao bPaymentDao,
                                     BillingOnItemPaymentDao bItemPaymentDao,
                                     BillingOnTransactionDao bTransactionDao,
                                     BillingThirdPartyService thirdPartyService) {
        this.bCh1Dao = bCh1Dao;
        this.bExtDao = bExtDao;
        this.bItemDao = bItemDao;
        this.bPaymentDao = bPaymentDao;
        this.bItemPaymentDao = bItemPaymentDao;
        this.bTransactionDao = bTransactionDao;
        this.thirdPartyService = thirdPartyService;
    }

    /**
     * Atomically apply the payment-save sequence. Any failure rolls back
     * the entire batch. Returns silently on success; the caller writes its
     * JSON response.
     *
     * @throws BillingValidationException if the bill row no longer exists
     *         (a concurrent delete between the action's pre-check and the
     *         transactional re-fetch).
     */
    public void saveThirdPartyPayment(Command cmd) {
        Objects.requireNonNull(cmd, "cmd");

        BillingONCHeader1 cheader1 = bCh1Dao.find(cmd.billNo);
        if (cheader1 == null) {
            throw new BillingValidationException(
                    "Bill " + cmd.billNo + " no longer exists; payment not saved");
        }

        boolean toUpdateChl = false;
        if (cmd.newStatus != null && !cmd.newStatus.equals(cheader1.getStatus())) {
            cheader1.setStatus(cmd.newStatus);
            toUpdateChl = true;
        }

        String demographicNo = cheader1.getDemographicNo().toString();

        // 1. billing_on_ext: payment key (also bumps header.paid)
        if (cmd.sumPaid.compareTo(BigDecimal.ZERO) > 0) {
            toUpdateChl = true;
            BigDecimal newPaid = cmd.sumPaid.add(cheader1.getPaid());
            cheader1.setPaid(newPaid);
            upsertExtKey(cmd.billNo, demographicNo, BillingONExtDao.KEY_PAYMENT, newPaid.toString());
        }
        if (toUpdateChl) {
            bCh1Dao.merge(cheader1);
        }

        // 2. billing_on_ext: discount
        if (cmd.sumDiscount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal extDiscount = bExtDao.getAccountVal(cmd.billNo, BillingONExtDao.KEY_DISCOUNT);
            upsertExtKey(cmd.billNo, demographicNo, BillingONExtDao.KEY_DISCOUNT,
                    cmd.sumDiscount.add(extDiscount).toString());
        }
        // 3. billing_on_ext: refund
        if (cmd.sumRefund.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal extRefund = bExtDao.getAccountVal(cmd.billNo, BillingONExtDao.KEY_REFUND);
            upsertExtKey(cmd.billNo, demographicNo, BillingONExtDao.KEY_REFUND,
                    cmd.sumRefund.add(extRefund).toString());
        }
        // 4. billing_on_ext: credit
        if (cmd.sumCredit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal extCredit = bExtDao.getAccountVal(cmd.billNo, BillingONExtDao.KEY_CREDIT);
            upsertExtKey(cmd.billNo, demographicNo, BillingONExtDao.KEY_CREDIT,
                    cmd.sumCredit.add(extCredit).toString());
        }
        // 5. billing_on_ext: pay-method
        upsertExtKey(cmd.billNo, demographicNo, BillingONExtDao.KEY_PAY_METHOD, cmd.paymentTypeIdRaw);

        // 6. billing_on_payment: parent row
        BillingONPayment billPayment = new BillingONPayment();
        billPayment.setBillingOnCheader1(cheader1);
        billPayment.setBillingNo(cmd.billNo);
        billPayment.setCreator(cmd.creatorProviderNo);
        billPayment.setPaymentDate(cmd.paymentDate);
        billPayment.setPaymentTypeId(cmd.paymentTypeId);
        billPayment.setTotal_payment(cmd.sumPaid);
        billPayment.setTotal_discount(cmd.sumDiscount);
        billPayment.setTotal_refund(cmd.sumRefund);
        billPayment.setTotal_credit(cmd.sumCredit);
        bPaymentDao.persist(billPayment);

        // 7. billing_on_item_payment + billing_on_transaction per row
        Timestamp paymentTimestamp = new Timestamp(cmd.paymentDate.getTime());
        for (Line line : cmd.items) {
            BillingONItem billItem = bItemDao.find(line.itemId);
            if (billItem == null) {
                continue;
            }
            BillingOnItemPayment ip = new BillingOnItemPayment();
            ip.setBillingOnItemId(line.itemId);
            ip.setBillingOnPaymentId(billPayment.getId());
            ip.setCh1Id(cmd.billNo);
            ip.setPaymentTimestamp(paymentTimestamp);

            switch (line.selection) {
                case "payment" -> {
                    if (line.amount.signum() == 0 && line.discount.signum() == 0) continue;
                    ip.setPaid(line.amount);
                    ip.setDiscount(line.discount);
                    bItemPaymentDao.persist(ip);
                    BillingOnTransaction t = bTransactionDao.getTransTemplate(
                            cheader1, billItem, billPayment, cmd.creatorProviderNo, ip.getId());
                    t.setServiceCodePaid(line.amount);
                    t.setServiceCodeDiscount(line.discount);
                    bTransactionDao.persist(t);
                }
                case "refund" -> {
                    if (line.amount.signum() == 0) continue;
                    ip.setRefund(line.amount);
                    bItemPaymentDao.persist(ip);
                    BillingOnTransaction t = bTransactionDao.getTransTemplate(
                            cheader1, billItem, billPayment, cmd.creatorProviderNo, ip.getId());
                    t.setServiceCodeRefund(line.amount);
                    bTransactionDao.persist(t);
                }
                case "credit" -> {
                    if (line.amount.signum() == 0) continue;
                    ip.setCredit(line.amount);
                    bItemPaymentDao.persist(ip);
                    BillingOnTransaction t = bTransactionDao.getTransTemplate(
                            cheader1, billItem, billPayment, cmd.creatorProviderNo, ip.getId());
                    t.setServiceCodeCredit(line.amount);
                    bTransactionDao.persist(t);
                }
                default -> {
                    // Unknown selection — skip rather than fail the whole
                    // batch, but log so a typo in the form (or a future
                    // selection value the switch hasn't caught up with)
                    // doesn't silently drop a payment row out of balance.
                    MiscUtils.getLogger().warn(
                            "Skipping payment-save row for itemId={} with unknown selection [{}]",
                            line.itemId,
                            LogSanitizer.sanitize(line.selection));
                }
            }
        }
    }

    private void upsertExtKey(int billNo, String demoNo, String key, String value) {
        String billNoStr = Integer.toString(billNo);
        if (thirdPartyService.keyExists(billNoStr, key)) {
            thirdPartyService.updateKeyValue(billNoStr, key, value);
        } else {
            thirdPartyService.add3rdBillExt(billNoStr, demoNo, key, value);
        }
    }

    /**
     * One row from the payment form. Already parsed and validated by the
     * action; the service only persists.
     */
    public static final class Line {
        public final int itemId;
        /** "payment", "refund", or "credit". Other values are ignored. */
        public final String selection;
        /** payment / refund / credit value (>= 0). */
        public final BigDecimal amount;
        /** discount value (>= 0); meaningful only when selection == "payment". */
        public final BigDecimal discount;

        public Line(int itemId, String selection, BigDecimal amount, BigDecimal discount) {
            this.itemId = itemId;
            this.selection = Objects.requireNonNull(selection, "selection");
            this.amount = Objects.requireNonNull(amount, "amount");
            this.discount = Objects.requireNonNull(discount, "discount");
        }
    }

    /**
     * Pre-validated, pre-parsed payment-save input. Built by the action layer
     * after upfront parsing. Holds primitive / immutable values only — no
     * Hibernate-managed entities — so the caller cannot accidentally hand the
     * service a stale entity attached to a different session.
     */
    public static final class Command {
        public final int billNo;
        public final Date paymentDate;
        public final String creatorProviderNo;
        public final int paymentTypeId;
        public final String paymentTypeIdRaw;
        public final BigDecimal sumPaid;
        public final BigDecimal sumDiscount;
        public final BigDecimal sumRefund;
        public final BigDecimal sumCredit;
        /** New header status; null means "leave unchanged". */
        public final String newStatus;
        public final List<Line> items;

        public Command(int billNo,
                       Date paymentDate,
                       String creatorProviderNo,
                       int paymentTypeId,
                       String paymentTypeIdRaw,
                       BigDecimal sumPaid,
                       BigDecimal sumDiscount,
                       BigDecimal sumRefund,
                       BigDecimal sumCredit,
                       String newStatus,
                       List<Line> items) {
            this.billNo = billNo;
            this.paymentDate = Objects.requireNonNull(paymentDate, "paymentDate");
            this.creatorProviderNo = creatorProviderNo;
            this.paymentTypeId = paymentTypeId;
            this.paymentTypeIdRaw = Objects.requireNonNull(paymentTypeIdRaw, "paymentTypeIdRaw");
            this.sumPaid = Objects.requireNonNull(sumPaid, "sumPaid");
            this.sumDiscount = Objects.requireNonNull(sumDiscount, "sumDiscount");
            this.sumRefund = Objects.requireNonNull(sumRefund, "sumRefund");
            this.sumCredit = Objects.requireNonNull(sumCredit, "sumCredit");
            this.newStatus = newStatus;
            this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }
}
