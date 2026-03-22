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

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;

/**
 * @author mweston4
 */
public interface BillingONExtDao extends AbstractDao<BillingONExt> {

    public final static String KEY_PAYMENT = "payment";
    public final static String KEY_REFUND = "refund";
    public final static String KEY_DISCOUNT = "discount";
    public final static String KEY_CREDIT = "credit";
    public final static String KEY_PAY_DATE = "payDate";
    public final static String KEY_PAY_METHOD = "payMethod";
    public final static String KEY_TOTAL = "total";
    public final static String KEY_GST = "gst";

    /**
     * Find.
     *
     * @param key String the key
     * @param value String the value
     * @return List<BillingONExt>
     */
    public List<BillingONExt> find(String key, String value);

    /**
     * Find By Billing No And Key.
     *
     * @param billingNo Integer the billingNo
     * @param key String the key
     * @return List<BillingONExt>
     */
    public List<BillingONExt> findByBillingNoAndKey(Integer billingNo, String key);

    /**
     * Find By Billing No And Payment Id And Key.
     *
     * @param billingNo Integer the billingNo
     * @param paymentId Integer the paymentId
     * @param key String the key
     * @return List<BillingONExt>
     */
    public List<BillingONExt> findByBillingNoAndPaymentIdAndKey(Integer billingNo, Integer paymentId, String key);

    /**
     * Get Pay Method Desc.
     *
     * @param bExt BillingONExt the bExt
     * @return String
     */
    public String getPayMethodDesc(BillingONExt bExt);

    /**
     * Get Payment.
     *
     * @param paymentRecord BillingONPayment the paymentRecord
     * @return BigDecimal
     */
    public BigDecimal getPayment(BillingONPayment paymentRecord);

    /**
     * Get Refund.
     *
     * @param paymentRecord BillingONPayment the paymentRecord
     * @return BigDecimal
     */
    public BigDecimal getRefund(BillingONPayment paymentRecord);

    /**
     * Get Remit To.
     *
     * @param bCh1 BillingONCHeader1 the bCh1
     * @return BillingONExt
     */
    public BillingONExt getRemitTo(BillingONCHeader1 bCh1);

    /**
     * Get Bill To.
     *
     * @param bCh1 BillingONCHeader1 the bCh1
     * @return BillingONExt
     */
    public BillingONExt getBillTo(BillingONCHeader1 bCh1);

    /**
     * Get Bill To Inactive.
     *
     * @param bCh1 BillingONCHeader1 the bCh1
     * @return BillingONExt
     */
    public BillingONExt getBillToInactive(BillingONCHeader1 bCh1);

    /**
     * Get Due Date.
     *
     * @param bCh1 BillingONCHeader1 the bCh1
     * @return BillingONExt
     */
    public BillingONExt getDueDate(BillingONCHeader1 bCh1);

    /**
     * Get Use Bill To.
     *
     * @param bCh1 BillingONCHeader1 the bCh1
     * @return BillingONExt
     */
    public BillingONExt getUseBillTo(BillingONCHeader1 bCh1);

    /**
     * Find.
     *
     * @param billingNo Integer the billingNo
     * @param key String the key
     * @param start Date the start
     * @param end Date the end
     * @return List<BillingONExt>
     */
    public List<BillingONExt> find(Integer billingNo, String key, Date start, Date end);

    /**
     * Find By Billing No And Payment No.
     *
     * @param billingNo int the billingNo
     * @param paymentId int the paymentId
     * @return List<BillingONExt>
     */
    public List<BillingONExt> findByBillingNoAndPaymentNo(int billingNo, int paymentId);

    /**
     * Get Claim Ext Items.
     *
     * @param billingNo int the billingNo
     * @return List<BillingONExt>
     */
    public List<BillingONExt> getClaimExtItems(int billingNo);

    /**
     * Get Billing Ext Items.
     *
     * @param billingNo String the billingNo
     * @return List<BillingONExt>
     */
    public List<BillingONExt> getBillingExtItems(String billingNo);

    /**
     * Get Inactive Billing Ext Items.
     *
     * @param billingNo String the billingNo
     * @return List<BillingONExt>
     */
    public List<BillingONExt> getInactiveBillingExtItems(String billingNo);

    /**
     * Get Account Val.
     *
     * @param billingNo int the billingNo
     * @param key String the key
     * @return BigDecimal
     */
    public BigDecimal getAccountVal(int billingNo, String key);

    /**
     * Get Claim Ext Item.
     *
     * @param billingNo Integer the billingNo
     * @param demographicNo Integer the demographicNo
     * @param keyVal String the keyVal
     * @return BillingONExt
     */
    public BillingONExt getClaimExtItem(Integer billingNo, Integer demographicNo, String keyVal);

    /**
     * Set Ext Item.
     *
     * @param billingNo int the billingNo
     * @param demographicNo int the demographicNo
     * @param keyVal String the keyVal
     * @param value String the value
     * @param dateTime Date the dateTime
     * @param status char the status
     */
    public void setExtItem(int billingNo, int demographicNo, String keyVal, String value, Date dateTime, char status);

    // public static boolean isNumberKey(String key);
    /**
     * Is Number Key.
     *
     * @param key String the key
     * @return boolean
     */
    public boolean isNumberKey(String key);
}
