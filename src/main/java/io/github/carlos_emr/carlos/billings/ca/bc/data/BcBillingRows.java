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

/**
 * Detached BC billing projections with no live persistence resources.
 *
 * <p>Strings retain JDBC nulls, blanks and legacy column formats. Claimed fees and private
 * payments are decimal dollar strings; Teleplan amounts retain implied cents and negative
 * overpunch suffixes. No rounding, trimming or date parsing happens in these carriers.
 * Diagnostic descriptions redact all field values.</p>
 *
 * @since 2026-11-02
 */
public final class BcBillingRows {
    private BcBillingRows() {}
    /**
     * Invoice/report columns joined to the billing provider. {@code billAmount} is a decimal
     * dollar string and {@code serviceDate} retains compact {@code yyyyMMdd}. {@code serviceToDay}
     * is the legacy two-digit end-day field. {@code updateDate} and {@code dob} retain their source
     * JDBC/legacy representations.
     *
     * @since 2026-11-02
     */
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
            String lastName) {
        @Override
        public String toString() {
            return "BillRow[redacted]";
        }
    }

    /**
     * MSP payment/refund columns. {@code tPaidamt} and {@code tAja1} through {@code tAja7}
     * retain Teleplan implied-cent strings, including negative overpunch suffixes.
     * Payment and service dates retain compact {@code yyyyMMdd} values.
     *
     * @since 2026-11-02
     */
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
            String tAja7) {
        @Override
        public String toString() {
            return "PaymentRow[redacted]";
        }
    }

    /**
     * Private payment-history columns, excluding internal adjustments in the report query.
     * {@code amountReceived} is a signed decimal dollar string; {@code serviceDate} is compact
     * {@code yyyyMMdd}. {@code creationDate} is an immutable SQL-calendar date ({@code yyyy-MM-dd}),
     * or null when the database value is null.
     *
     * @since 2026-11-02
     */
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
            java.time.LocalDate creationDate,
            String paymentTypeId) {
        @Override
        public String toString() {
            return "PrivatePaymentRow[redacted]";
        }
    }

    /**
     * The fee, fee item and diagnosis used by WCB billing reports. {@code amount} retains
     * the stored decimal dollar string; optional source values remain null or blank.
     *
     * @since 2026-11-02
     */
    public record WcbRow(
            String amount,
            String feeItem,
            String diagnosis) {
        @Override
        public String toString() {
            return "WcbRow[redacted]";
        }
    }

    /**
     * The unchanged 18-field Jasper remittance contract. Snake-case component names match
     * template fields. {@code t_paidamt} retains implied cents and Teleplan overpunch encoding;
     * {@code t_servicedate} and {@code t_payment} retain compact {@code yyyyMMdd} strings.
     * Nullable identifiers and explanation fields remain null.
     *
     * @since 2026-11-02
     */
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
            String t_dataseq) {
        @Override
        public String toString() {
            return "RemittanceRow[redacted]";
        }
    }

    /**
     * Remittance adjustment/report columns in the legacy native query order. Teleplan paid
     * and adjustment amounts retain implied cents/overpunch strings, while {@code bill_amount}
     * retains decimal dollars. Service/payment dates retain {@code yyyyMMdd}; the MSP-received
     * date retains its source Teleplan text.
     * {@code differential} is the native SQL comparison result: {@code +}, {@code -}, or empty.
     *
     * @since 2026-11-02
     */
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
            String t_aja7) {
        @Override
        public String toString() {
            return "AdjustmentRemittanceRow[redacted]";
        }
    }
    /**
     * An eligible extract invoice key and unescaped display name. {@code billingNo} is the
     * non-null numeric primary key; the caller retains the legacy exclusion of negative keys.
     *
     * @since 2026-11-02
     */
    public record ExtractBillRow(Integer billingNo, String demographicName, String billingType) {
        public ExtractBillRow(Integer billingNo, String demographicName) { this(billingNo, demographicName, null); }
        @Override
        public String toString() { return "ExtractBillRow[redacted]"; }
    }

    /**
     * Raw claim columns for the legacy fixed-width Teleplan formatter. {@code billAmount}
     * is the required claimed fee in decimal dollars, not an optional unpaid remittance amount.
     * Service/birth dates retain compact {@code yyyyMMdd}, while {@code serviceToDay} is the
     * two-digit end-day field. Time columns retain legacy fixed-width text. Nulls/blanks and padding
     * decisions stay with the existing formatter and validator.
     *
     * @since 2026-11-02
     */
    public record ExtractClaimRow(
            String billAmount,
            Integer billingmasterNo,
            String phn,
            String serviceDate,
            String billingCode,
            String dxCode1,
            String dxCode2,
            String dxCode3,
            String claimcode,
            String datacenter,
            String payeeNo,
            String practitionerNo,
            String nameVerify,
            String dependentNum,
            String billingUnit,
            String clarificationCode,
            String anatomicalArea,
            String afterHour,
            String newProgram,
            String paymentMode,
            String serviceToDay,
            String submissionCode,
            String serviceLocation,
            String referralFlag1,
            String referralNo1,
            String referralFlag2,
            String referralNo2,
            String timeCall,
            String serviceStartTime,
            String serviceEndTime,
            String birthDate,
            String correspondenceCode,
            String mvaClaimCode,
            String icbcClaimNo,
            String originalClaim,
            String facilityNo,
            String facilitySubNo,
            String oinInsurerCode,
            String oinRegistrationNo,
            String oinBirthdate,
            String oinFirstName,
            String oinSecondName,
            String oinSurname,
            String oinSexCode,
            String oinAddress,
            String oinAddress2,
            String oinAddress3,
            String oinAddress4,
            String oinPostalcode) {
        @Override
        public String toString() { return "ExtractClaimRow[redacted]"; }
    }
}
