/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

package io.github.carlos_emr.carlos.billings.ca.on.dto;

import java.math.BigDecimal;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;

/**
 * Immutable data transfer object for a single Ontario claim header row.
 * Carries the fixed-width MOH export fields plus UI/reporting totals while
 * keeping the string-shaped accessors used by JSP and service code.
 */
public record BillingClaimHeaderDto(
        String id,
        String batchHeaderId,
        String transactionId,
        String recordId,
        String hin,
        String ver,
        String dob,
        String accountNumber,
        String payProgram,
        String payee,
        String referralNumber,
        String facilityNumber,
        String admissionDate,
        String referringLabNumber,
        String manualReview,
        String location,
        String clinic,
        String billto,
        String demographicNo,
        String providerNo,
        String appointmentNo,
        String demographicName,
        String lastName,
        String firstName,
        String sex,
        String province,
        String billingDate,
        String billingTime,
        String settleDate,
        String total,
        String paid,
        String status,
        String comment,
        String visitType,
        String providerOhipNo,
        String providerRmaNo,
        String appointmentProviderNo,
        String assistantProviderNo,
        String creator,
        String updateDateTime,
        String billingOnItemId,
        String serviceNumber,
        BigDecimal cashTotal,
        BigDecimal debitTotal,
        String providerName,
        int numItems) {

    public BillingClaimHeaderDto {
        cashTotal = cashTotal == null ? BillingMoney.zeroAmount() : cashTotal;
        debitTotal = debitTotal == null ? BillingMoney.zeroAmount() : debitTotal;
    }

    public BillingClaimHeaderDto() {
        this(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, BillingMoney.zeroAmount(), BillingMoney.zeroAmount(), null, 0);
    }

    public BillingClaimHeaderDto(BillingClaimHeaderDto copy) {
        this(copy.id, copy.batchHeaderId, copy.transactionId, copy.recordId,
                copy.hin, copy.ver, copy.dob, copy.accountNumber,
                copy.payProgram, copy.payee, copy.referralNumber,
                copy.facilityNumber, copy.admissionDate, copy.referringLabNumber,
                copy.manualReview, copy.location, copy.clinic, copy.billto,
                copy.demographicNo, copy.providerNo, copy.appointmentNo,
                copy.demographicName, copy.lastName, copy.firstName, copy.sex,
                copy.province, copy.billingDate, copy.billingTime,
                copy.settleDate, copy.total, copy.paid, copy.status,
                copy.comment, copy.visitType, copy.providerOhipNo,
                copy.providerRmaNo, copy.appointmentProviderNo,
                copy.assistantProviderNo, copy.creator, copy.updateDateTime,
                copy.billingOnItemId, copy.serviceNumber, copy.cashTotal,
                copy.debitTotal, copy.providerName, copy.numItems);
    }

    public BillingClaimHeaderDto withId(String id) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withBatchHeaderId(String batchHeaderId) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withTransactionId(String transactionId) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withRecordId(String recordId) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withHin(String hin) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withVer(String ver) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withDob(String dob) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withAccountNumber(String accountNumber) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withPayProgram(String payProgram) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withPayee(String payee) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withReferralNumber(String referralNumber) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withFacilityNumber(String facilityNumber) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withAdmissionDate(String admissionDate) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withReferringLabNumber(String referringLabNumber) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withManualReview(String manualReview) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withLocation(String location) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withClinic(String clinic) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withBillto(String billto) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withDemographicNo(String demographicNo) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withProviderNo(String providerNo) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withAppointmentNo(String appointmentNo) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withDemographicName(String demographicName) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withLastName(String lastName) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withFirstName(String firstName) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withSex(String sex) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withProvince(String province) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withBillingDate(String billingDate) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withBillingTime(String billingTime) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withSettleDate(String settleDate) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withTotal(String total) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withPaid(String paid) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withStatus(String status) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withComment(String comment) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withVisitType(String visitType) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withProviderOhipNo(String providerOhipNo) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withProviderRmaNo(String providerRmaNo) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withAppointmentProviderNo(String appointmentProviderNo) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withAssistantProviderNo(String assistantProviderNo) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withCreator(String creator) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withUpdateDateTime(String updateDateTime) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withBillingOnItemId(String billingOnItemId) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withServiceNumber(String serviceNumber) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withCashTotal(BigDecimal cashTotal) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withDebitTotal(BigDecimal debitTotal) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withProviderName(String providerName) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }

    public BillingClaimHeaderDto withNumItems(int numItems) {
        return new BillingClaimHeaderDto(id, batchHeaderId, transactionId, recordId, hin, ver, dob, accountNumber, payProgram, payee, referralNumber, facilityNumber, admissionDate, referringLabNumber, manualReview, location, clinic, billto, demographicNo, providerNo, appointmentNo, demographicName, lastName, firstName, sex, province, billingDate, billingTime, settleDate, total, paid, status, comment, visitType, providerOhipNo, providerRmaNo, appointmentProviderNo, assistantProviderNo, creator, updateDateTime, billingOnItemId, serviceNumber, cashTotal, debitTotal, providerName, numItems);
    }


    public String getId() { return id; }
    public String getHin() { return hin; }
    public String getVer() { return ver; }
    public String getDob() { return dob; }
    public String getPayee() { return payee; }
    public String getLocation() { return location; }
    public String getClinic() { return clinic; }
    public String getBillto() { return billto; }
    public String getSex() { return sex; }
    public String getProvince() { return province; }
    public String getTotal() { return total; }
    public String getPaid() { return paid; }
    public String getStatus() { return status; }
    public String getComment() { return comment; }
    public String getCreator() { return creator; }
    public String getProviderNo() { return providerNo; }
    public BigDecimal getCashTotal() { return cashTotal; }
    public BigDecimal getDebitTotal() { return debitTotal; }
    public String getProviderName() { return providerName; }
    public int getNumItems() { return numItems; }
}
