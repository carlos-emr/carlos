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
import java.math.RoundingMode;
import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;

/**
 * Atomic deletion of a third-party payment row, its item-payment allocation
 * rows, and the cascading header / ext updates. These writes
 * (item-payment delete, payment.remove, header.merge, ext payment-key update,
 * ext refund-key update) used to live inline in
 * {@code BillingOnPayments2Action.deletePayment} with no @Transactional
 * boundary — a mid-sequence failure left the header `paid` total stale, the
 * item-payment allocations orphaned, and the ext keys out of sync with the
 * underlying payment table.
 *
 * <p>Lifting them into a single {@code @Transactional} method gives us
 * rollback semantics: if any of the writes fails, none of them takes
 * effect.</p>
 *
 * @since 2026-04-30
 */
@Service
@Transactional
public class BillingPaymentDeletionService {

    private final BillingONPaymentDao billingONPaymentDao;
    private final BillingONCHeader1Dao billingClaimDao;
    private final BillingONExtDao billingONExtDao;
    private final BillingOnItemPaymentDao billingOnItemPaymentDao;

    public BillingPaymentDeletionService(BillingONPaymentDao billingONPaymentDao,
                                         BillingONCHeader1Dao billingClaimDao,
                                         BillingONExtDao billingONExtDao,
                                         BillingOnItemPaymentDao billingOnItemPaymentDao) {
        this.billingONPaymentDao = billingONPaymentDao;
        this.billingClaimDao = billingClaimDao;
        this.billingONExtDao = billingONExtDao;
        this.billingOnItemPaymentDao = billingOnItemPaymentDao;
    }

    /**
     * Atomically delete a third-party payment and rebalance the header /
     * ext-keys. Throws (so the surrounding tx rolls back) if the payment id
     * is unknown.
     *
     * @throws PaymentNotFoundException when no payment row exists for {@code paymentId}
     */
    public void deletePayment(int paymentId) {
        Date now = new Date();
        BillingONPayment payment = billingONPaymentDao.find(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException(paymentId);
        }
        Integer billingNo = payment.getBillingNo();
        if (billingNo == null && payment.getBillingONCheader1() != null) {
            billingNo = payment.getBillingONCheader1().getId();
        }
        if (billingNo == null) {
            throw new IllegalStateException("BillingONPayment paymentId=" + paymentId
                    + " is missing its parent billing number");
        }
        BillingONCHeader1 ch1 = billingClaimDao.findForUpdate(billingNo);
        if (ch1 == null) {
            throw new IllegalStateException("BillingONCHeader1 billingNo=" + billingNo
                    + " not found for paymentId=" + paymentId);
        }

        billingOnItemPaymentDao.deleteByPaymentId(paymentId);
        billingONPaymentDao.remove(paymentId);

        BigDecimal paid = money(billingONPaymentDao.getPaymentsSumByBillingNo(billingNo));
        BigDecimal refund = money(billingONPaymentDao.getPaymentsRefundByBillingNo(billingNo));
        BigDecimal netPaid = paid.subtract(refund).setScale(2, RoundingMode.HALF_UP);
        ch1.setPaid(netPaid);
        billingClaimDao.merge(ch1);

        // Use BigDecimal.toPlainString() rather than NumberFormat.getCurrencyInstance().
        // The legacy code used the platform-default currency formatter and stripped
        // a literal "$" — on a non-CA-locale JVM that stored "€1,234.56" into
        // billing_on_ext, then the read path's `new BigDecimal(ext.getValue())`
        // threw NumberFormatException. Plain decimal with 2-place scaling matches
        // the format other paths use to write into this column.
        billingONExtDao.setExtItem(billingNo, ch1.getDemographicNo(),
                BillingONExtDao.KEY_PAYMENT,
                netPaid.toPlainString(),
                now, '1');
        billingONExtDao.setExtItem(billingNo, ch1.getDemographicNo(),
                BillingONExtDao.KEY_REFUND,
                refund.toPlainString(),
                now, '1');
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Distinct exception so the calling action can render the up-to-date
     * payment list (a likely concurrent-edit / stale-page scenario) rather
     * than the generic failure page that any other DAO error would produce.
     * {@link io.github.carlos_emr.carlos.billings.ca.on.web.BillingOnPayments2Action#deletePayment()}
     * catches this explicitly.
     */
    public static class PaymentNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public PaymentNotFoundException(int paymentId) {
            super(String.format("BillingONPayment paymentId=%d not found", paymentId));
        }
    }
}
