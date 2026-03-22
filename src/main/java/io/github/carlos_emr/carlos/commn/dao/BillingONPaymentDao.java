/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;

/**
 * DAO interface for healthcare billing operations.
 *
 * @since 2001
 */

public interface BillingONPaymentDao extends AbstractDao<BillingONPayment> {
    /**
     * Set Billing O N Ext Dao.
     *
     * @param billingONExtDao BillingONExtDao the billingONExtDao
     */
    void setBillingONExtDao(BillingONExtDao billingONExtDao);

    /**
     * Set Billing O N C Header1 Dao.
     *
     * @param billingONCHeader1Dao BillingONCHeader1Dao the billingONCHeader1Dao
     */
    void setBillingONCHeader1Dao(BillingONCHeader1Dao billingONCHeader1Dao);

    /**
     * Get Billing O N Ext Dao.
     * @return BillingONExtDao
     */
    BillingONExtDao getBillingONExtDao();

    /**
     * Get Billing O N C Header1 Dao.
     * @return BillingONCHeader1Dao
     */
    BillingONCHeader1Dao getBillingONCHeader1Dao();

    /**
     * List Payments By Billing No.
     *
     * @param billingNo Integer the billingNo
     * @return List<BillingONPayment>
     */
    List<BillingONPayment> listPaymentsByBillingNo(Integer billingNo);

    /**
     * List Payments By Billing No Desc.
     *
     * @param billingNo Integer the billingNo
     * @return List<BillingONPayment>
     */
    List<BillingONPayment> listPaymentsByBillingNoDesc(Integer billingNo);

    /**
     * Get Payments Sum By Billing No.
     *
     * @param billingNo Integer the billingNo
     * @return BigDecimal
     */
    BigDecimal getPaymentsSumByBillingNo(Integer billingNo);

    /**
     * Get Payments Refund By Billing No.
     *
     * @param billingNo Integer the billingNo
     * @return BigDecimal
     */
    BigDecimal getPaymentsRefundByBillingNo(Integer billingNo);

    /**
     * Get Payments Discount By Billing No.
     *
     * @param billingNo Integer the billingNo
     * @return BigDecimal
     */
    BigDecimal getPaymentsDiscountByBillingNo(Integer billingNo);

    /**
     * Get Total Sum By Billing No Web.
     *
     * @param billingNo String the billingNo
     * @return String
     */
    String getTotalSumByBillingNoWeb(String billingNo);

    /**
     * Get Payments Refund By Billing No Web.
     *
     * @param billingNo String the billingNo
     * @return String
     */
    String getPaymentsRefundByBillingNoWeb(String billingNo);

    /**
     * Get Payment Id By Billing No.
     *
     * @param billingNo int the billingNo
     * @return int
     */
    int getPaymentIdByBillingNo(int billingNo);

    /**
     * Get Count Of Payment By Payment Type Id.
     *
     * @param paymentTypeId int the paymentTypeId
     * @return int
     */
    int getCountOfPaymentByPaymentTypeId(int paymentTypeId);

    /**
     * Get Payment Type By Id.
     *
     * @param paymentTypeId int the paymentTypeId
     * @return String
     */
    String getPaymentTypeById(int paymentTypeId);

    /**
     * Find3rd Party Pay Records By Bill.
     *
     * @param bCh1 BillingONCHeader1 the bCh1
     * @return List<BillingONPayment>
     */
    List<BillingONPayment> find3rdPartyPayRecordsByBill(BillingONCHeader1 bCh1);

    /**
     * Find3rd Party Payments.
     *
     * @param billingNo Integer the billingNo
     * @return List<Integer>
     */
    List<Integer> find3rdPartyPayments(Integer billingNo);

    /**
     * Find3rd Party Payments By Billing No.
     *
     * @param billingNo Integer the billingNo
     * @return List<BillingONPayment>
     */
    List<BillingONPayment> find3rdPartyPaymentsByBillingNo(Integer billingNo);

    /**
     * Find3rd Party Pay Records By Bill.
     *
     * @param bCh1 BillingONCHeader1 the bCh1
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return List<BillingONPayment>
     */
    List<BillingONPayment> find3rdPartyPayRecordsByBill(BillingONCHeader1 bCh1, Date startDate, Date endDate);

    /**
     * Create Payment.
     *
     * @param bCh1 BillingONCHeader1 the bCh1
     * @param locale Locale the locale
     * @param payType String the payType
     * @param paidAmt BigDecimal the paidAmt
     * @param payMethod String the payMethod
     * @param providerNo String the providerNo
     */
    void createPayment(BillingONCHeader1 bCh1, Locale locale, String payType, BigDecimal paidAmt, String payMethod, String providerNo);

    static BigDecimal calculatePaymentTotal(List<BillingONPayment> paymentRecords) {
    /**
     * Big Decimal.
     *
     * @return new
     */
        BigDecimal paidTotal = new BigDecimal("0.00");
        for (BillingONPayment bPay : paymentRecords) {
            BigDecimal amtPaid = bPay.getTotal_payment();
            paidTotal = paidTotal.add(amtPaid);
        }
        return paidTotal;
    }

    static BigDecimal calculateRefundTotal(List<BillingONPayment> paymentRecords) {
    /**
     * Big Decimal.
     *
     * @return new
     */
        BigDecimal refundTotal = new BigDecimal("0.00");
        for (BillingONPayment bPay : paymentRecords) {
            BigDecimal amtRefunded = bPay.getTotal_refund();
            refundTotal = refundTotal.add(amtRefunded);
        }
        return refundTotal;
    }
}
