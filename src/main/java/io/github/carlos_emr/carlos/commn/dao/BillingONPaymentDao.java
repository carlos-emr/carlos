/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.carlos.commn.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;

/**
 * DAO for Ontario payment rows, including third-party invoice payment history
 * and aggregate totals.
 */
public interface BillingONPaymentDao extends AbstractDao<BillingONPayment> {
    /**
     * Loads a {@link BillingONPayment} together with its
     * {@code billingONExtItems} collection in a single {@code LEFT JOIN FETCH}
     * query. Use this in code paths that touch
     * {@link BillingONPayment#getBillingONExtItems()} <em>outside</em> an
     * open Hibernate session — the plain {@link AbstractDao#find} returns a
     * payment whose collection is uninitialised under
     * {@code FetchType.LAZY}.
     *
     * @return the payment (with ext items) or {@code null} if no row matches.
     * @since 2026-04-29
     */
    BillingONPayment findWithExtItems(Integer paymentId);

    /**
     * Variant of {@link #find3rdPartyPaymentsByBillingNo(Integer)} that also
     * fetches each row's {@code billingONExtItems} collection in one query —
     * for callers that post-process the ext items outside a Hibernate
     * session.
     *
     * @since 2026-04-29
     */
    List<BillingONPayment> find3rdPartyPaymentsByBillingNoWithExtItems(Integer billingNo);

    /** Load all payment rows tied to one billing number. */
    List<BillingONPayment> listPaymentsByBillingNo(Integer billingNo);

    /** Load payment rows newest-first for UI/history display. */
    List<BillingONPayment> listPaymentsByBillingNoDesc(Integer billingNo);

    /** Sum payment amounts for one billing number. */
    BigDecimal getPaymentsSumByBillingNo(Integer billingNo);

    /** Sum refund amounts for one billing number. */
    BigDecimal getPaymentsRefundByBillingNo(Integer billingNo);

    /** Sum discount amounts for one billing number. */
    BigDecimal getPaymentsDiscountByBillingNo(Integer billingNo);

    /** Legacy web helper that returns the payment total formatted as text for JSP callers. */
    String getTotalSumByBillingNoWeb(String billingNo);

    /** Legacy web helper that returns the refund total formatted as text for JSP callers. */
    String getPaymentsRefundByBillingNoWeb(String billingNo);

    /** Return one payment id associated with the invoice, primarily for older action flows. */
    int getPaymentIdByBillingNo(int billingNo);

    /** Count references to a payment type before edit/delete maintenance operations. */
    int getCountOfPaymentByPaymentTypeId(int paymentTypeId);

    /** Resolve a payment-type id back to its stored display name/value. */
    String getPaymentTypeById(int paymentTypeId);

    /** Load third-party payment rows for one billing header. */
    List<BillingONPayment> find3rdPartyPayRecordsByBill(BillingONCHeader1 bCh1);

    /** Return only the ids of third-party payment rows tied to one billing number. */
    List<Integer> find3rdPartyPayments(Integer billingNo);

    /** Load the third-party payment rows tied to one billing number. */
    List<BillingONPayment> find3rdPartyPaymentsByBillingNo(Integer billingNo);

    /** Date-filtered third-party payment history for one billing header. */
    List<BillingONPayment> find3rdPartyPayRecordsByBill(BillingONCHeader1 bCh1, Date startDate, Date endDate);

    /** Date-filtered third-party payment history for several invoices at once. */
    List<BillingONPayment> find3rdPartyPayRecordsByBills(List<Integer> billingNos, Date startDate, Date endDate);

    /** Create and persist a payment row using the older action-layer parameter contract. */
    void createPayment(BillingONCHeader1 bCh1, Locale locale, String payType, BigDecimal paidAmt, String payMethod, String providerNo);

    /** Utility used by legacy callers and tests to total payment amounts in memory. */
    static BigDecimal calculatePaymentTotal(List<BillingONPayment> paymentRecords) {
        BigDecimal paidTotal = new BigDecimal("0.00");
        for (BillingONPayment bPay : paymentRecords) {
            BigDecimal amtPaid = bPay.getTotal_payment();
            paidTotal = paidTotal.add(amtPaid);
        }
        return paidTotal;
    }

    /** Utility used by legacy callers and tests to total refund amounts in memory. */
    static BigDecimal calculateRefundTotal(List<BillingONPayment> paymentRecords) {
        BigDecimal refundTotal = new BigDecimal("0.00");
        for (BillingONPayment bPay : paymentRecords) {
            BigDecimal amtRefunded = bPay.getTotal_refund();
            refundTotal = refundTotal.add(amtRefunded);
        }
        return refundTotal;
    }
}
