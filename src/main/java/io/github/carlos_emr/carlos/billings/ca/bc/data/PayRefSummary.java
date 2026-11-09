/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.bc.data;

import java.text.NumberFormat;
import java.util.Locale;


import io.github.carlos_emr.carlos.billing.CA.BC.model.BcBillingConstants;

/**
 * <p>Title: PayRefSummary</p>
 *
 * <p>Description: </p>
 * Represent a Summary for the payments and refunds report.
 * This class is just for convenience to avoid the awkward
 * grouping calculations that would have been necessary in the report design
 *
 * <p>Copyright: Copyright (c) 2005</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class PayRefSummary {
    private java.math.BigDecimal cash = java.math.BigDecimal.ZERO;
    private java.math.BigDecimal cheque = java.math.BigDecimal.ZERO;
    private java.math.BigDecimal visa = java.math.BigDecimal.ZERO;
    private java.math.BigDecimal mc = java.math.BigDecimal.ZERO;
    private java.math.BigDecimal amex = java.math.BigDecimal.ZERO;
    private java.math.BigDecimal electronic = java.math.BigDecimal.ZERO;
    private java.math.BigDecimal debit = java.math.BigDecimal.ZERO;
    private java.math.BigDecimal other = java.math.BigDecimal.ZERO;
    private double adjustmentAmountTotal = 0.0;
    String line = "";

    public PayRefSummary() {
    }

    /**
     * Increments the value of the specified payment type, with the supplied
     * String reprentation of a double value
     *
     * @param paymentMethod String
     * @param value         double
     */
    public void addIncValue(String paymentMethod, double value) {
        paymentMethod = paymentMethod == null ? "" : paymentMethod;

            if (paymentMethod.equals(BcBillingConstants.PAYTYPE_CASH)) {
                this.cash = this.cash.add(java.math.BigDecimal.valueOf(value));
            } else if (paymentMethod.equals(BcBillingConstants.PAYTYPE_CHEQUE)) {
                this.cheque = this.cheque.add(java.math.BigDecimal.valueOf(value));
            } else if (paymentMethod.equals(BcBillingConstants.PAYTYPE_VISA)) {
                this.visa = this.visa.add(java.math.BigDecimal.valueOf(value));
            } else if (paymentMethod.equals(BcBillingConstants.PAYTYPE_MC)) {
                this.mc = this.mc.add(java.math.BigDecimal.valueOf(value));
            } else if (paymentMethod.equals(BcBillingConstants.PAYTYPE_AMEX)) {
                this.amex = this.amex.add(java.math.BigDecimal.valueOf(value));
            } else if (paymentMethod.equals(BcBillingConstants.PAYTYPE_ELECTRONIC)) {

                this.electronic = this.electronic.add(java.math.BigDecimal.valueOf(value));
                line += String.valueOf(electronic) + " " + String.valueOf(value) + "\n";
            } else if (paymentMethod.equals(BcBillingConstants.PAYTYPE_DEBIT)) {
                this.debit = this.debit.add(java.math.BigDecimal.valueOf(value));
            } else if (paymentMethod.equals(BcBillingConstants.PAYTYPE_OTHER)) {
                this.other = this.other.add(java.math.BigDecimal.valueOf(value));
            } else {
                this.other = this.other.add(java.math.BigDecimal.valueOf(value));
            }
    }

    // The service runs under C.UTF-8 (language-only en). A host-default formatter prints the generic
    // currency sign there, or a different currency on other hosts; BC report amounts are always CAD.
    public String getCash() {
        return formatMoney(cash);
    }

    public String getCheque() {
        return formatMoney(cheque);
    }

    public String getVisa() {
        return formatMoney(visa);
    }

    public String getMc() {
        return formatMoney(mc);
    }

    public String getAmex() {
        return formatMoney(amex);
    }

    public String getElectronic() {
        return formatMoney(electronic);
    }

    public String getDebit() {
        return formatMoney(debit);
    }

    public String getOther() {
        return formatMoney(other);
    }

    public String getTotal() {
        return formatMoney(exactTotal());
    }

    public double getRawTotal() {
        return exactTotal().doubleValue();
    }

    /**
     * @param adjAmt String
     */
    public void addAdjustmentAmount(String amount) {
        if (amount != null && !amount.isBlank()) electronic = electronic.add(new java.math.BigDecimal(amount));
    }

    private java.math.BigDecimal exactTotal() {
        return cash.add(cheque).add(visa).add(mc).add(amex).add(electronic).add(debit).add(other);
    }

    private static String formatMoney(java.math.BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.CANADA);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return format.format(amount);
    }

    public void setAdjustmentAmountTotal(double adjustmentAmountTotal) {
        this.adjustmentAmountTotal = adjustmentAmountTotal;
    }

    public double getAdjustmentAmountTotal() {
        return adjustmentAmountTotal;
    }

}
