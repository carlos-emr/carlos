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
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;

/**
 * DAO interface for healthcare billing operations.
 *
 * @since 2001
 */

public interface BillingOnItemPaymentDao extends AbstractDao<BillingOnItemPayment> {
    /**
     * Find By Payment Id And Item Id.
     *
     * @param paymentId int the paymentId
     * @param itemId int the itemId
     * @return BillingOnItemPayment
     */
    BillingOnItemPayment findByPaymentIdAndItemId(int paymentId, int itemId);

    /**
     * Get All By Item Id.
     *
     * @param itemId int the itemId
     * @return List<BillingOnItemPayment>
     */
    List<BillingOnItemPayment> getAllByItemId(int itemId);

    /**
     * Get Items By Payment Id.
     *
     * @param paymentId int the paymentId
     * @return List<BillingOnItemPayment>
     */
    List<BillingOnItemPayment> getItemsByPaymentId(int paymentId);

    /**
     * Get Amount Paid By Item Id.
     *
     * @param itemId int the itemId
     * @return BigDecimal
     */
    BigDecimal getAmountPaidByItemId(int itemId);

    /**
     * Get Item Payment By Invoice No Item Id.
     *
     * @param ch1_id Integer the ch1_id
     * @param item_id Integer the item_id
     * @return List<BillingOnItemPayment>
     */
    List<BillingOnItemPayment> getItemPaymentByInvoiceNoItemId(Integer ch1_id, Integer item_id);

    /**
     * Find By Billing No.
     *
     * @param billingNo int the billingNo
     * @return List<BillingOnItemPayment>
     */
    List<BillingOnItemPayment> findByBillingNo(int billingNo);

    public static BigDecimal calculateItemPaymentTotal(List<BillingOnItemPayment> paymentRecords) {

    /**
     * Big Decimal.
     *
     * @return new
     */
        BigDecimal paidTotal = new BigDecimal("0.00");
        for (BillingOnItemPayment bPay : paymentRecords) {
            BigDecimal amtPaid = bPay.getPaid();
            paidTotal = paidTotal.add(amtPaid);
        }

        return paidTotal;
    }

    public static BigDecimal calculateItemRefundTotal(List<BillingOnItemPayment> paymentRecords) {

    /**
     * Big Decimal.
     *
     * @return new
     */
        BigDecimal refundTotal = new BigDecimal("0.00");
        for (BillingOnItemPayment bPay : paymentRecords) {
            BigDecimal amtRefunded = bPay.getRefund();
            refundTotal = refundTotal.add(amtRefunded);
        }

        return refundTotal;
    }
}
