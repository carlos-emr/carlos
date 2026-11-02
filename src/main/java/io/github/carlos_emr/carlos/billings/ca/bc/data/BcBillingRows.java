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
package io.github.carlos_emr.carlos.billings.ca.bc.data;

/** Detached BC billing projections. JDBC values retain the legacy money and date representation. */
public final class BcBillingRows {
    private BcBillingRows() {}
    public record BillRow(
            String billingtype,
            String billingNo,
            String demographicNo,
            String billingUnit,
            String demographicName,
            String updateDate,
            String billingstatus,
            String serviceEndTime,
            String serviceStartTime,
            String serviceToDay,
            String billingmasterNo,
            String billAmount,
            String billingCode,
            String dxCode1,
            String serviceDate,
            String mvaClaimCode,
            String phn,
            String serviceLocation,
            String dob,
            String oinSexCode,
            String apptProviderNo,
            String providerNo,
            String firstName,
            String lastName) {}

    public record PaymentRow(
            String billingtype,
            String tPayment,
            String paymentMethod,
            String demographicNo,
            String demographicName,
            String tPaidamt,
            String billingmasterNo,
            String serviceDate,
            String tDataseq,
            String tExp1,
            String tExp2,
            String tPractitionerno,
            String providerNo,
            String payeeNo,
            String tAjc1,
            String tAja1,
            String tAja2,
            String tAja3,
            String tAja4,
            String tAja5,
            String tAja6,
            String tAja7) {}

    public record PrivatePaymentRow(
            String billingmasterNo,
            String billingtype,
            String demographicNo,
            String demographicName,
            String serviceDate,
            String apptProviderNo,
            String providerNo,
            String payeeNo,
            String amountReceived,
            java.sql.Date creationDate,
            String paymentTypeId) {}

    public record WcbRow(
            String amount,
            String feeItem,
            String diagnosis) {}

    public record RemittanceRow(
            String billing_code,
            String first_name,
            String last_name,
            String t_practitionerno,
            String t_s00type,
            String t_servicedate,
            String t_payment,
            String t_datacenter,
            String demographic_name,
            Integer demographic_no,
            String t_paidamt,
            String t_exp1,
            String t_exp2,
            String t_exp3,
            String t_exp4,
            String t_exp5,
            String t_exp6,
            String t_dataseq) {}

    public record AdjustmentRemittanceRow(
            Integer billing_no,
            Integer billingmaster_no,
            String billing_code,
            String first_name,
            String last_name,
            String t_practitionerno,
            String t_s00type,
            String t_msprcddate,
            String service_date,
            String t_payment,
            String t_datacenter,
            String demographic_name,
            Integer demographic_no,
            String differential,
            String t_paidamt,
            String bill_amount,
            String t_exp1,
            String t_exp2,
            String t_exp3,
            String t_exp4,
            String t_exp5,
            String t_exp6,
            String t_dataseq,
            String t_ajc1,
            String t_aja1,
            String t_ajc2,
            String t_aja2,
            String t_ajc3,
            String t_aja3,
            String t_ajc4,
            String t_aja4,
            String t_ajc5,
            String t_aja5,
            String t_ajc6,
            String t_aja6,
            String t_ajc7,
            String t_aja7) {}
}
