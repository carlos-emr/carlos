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

    public List<BillingONExt> find(String key, String value);

    public List<BillingONExt> findByBillingNoAndKey(Integer billingNo, String key);

    public List<BillingONExt> findByBillingNoAndPaymentIdAndKey(Integer billingNo, Integer paymentId, String key);

    public String getPayMethodDesc(BillingONExt bExt);

    public BigDecimal getPayment(BillingONPayment paymentRecord);

    public BigDecimal getRefund(BillingONPayment paymentRecord);

    public BillingONExt getRemitTo(BillingONCHeader1 bCh1);

    public BillingONExt getBillTo(BillingONCHeader1 bCh1);

    public BillingONExt getBillToInactive(BillingONCHeader1 bCh1);

    public BillingONExt getDueDate(BillingONCHeader1 bCh1);

    public BillingONExt getUseBillTo(BillingONCHeader1 bCh1);

    public List<BillingONExt> find(Integer billingNo, String key, Date start, Date end);

    public List<BillingONExt> findByBillingNoAndPaymentNo(int billingNo, int paymentId);

    public List<BillingONExt> getClaimExtItems(int billingNo);

    public List<BillingONExt> getBillingExtItems(String billingNo);

    public List<BillingONExt> getInactiveBillingExtItems(String billingNo);

    public BigDecimal getAccountVal(int billingNo, String key);

    public BillingONExt getClaimExtItem(Integer billingNo, Integer demographicNo, String keyVal);

    public void setExtItem(int billingNo, int demographicNo, String keyVal, String value, Date dateTime, char status);

    public boolean isNumberKey(String key);
}
