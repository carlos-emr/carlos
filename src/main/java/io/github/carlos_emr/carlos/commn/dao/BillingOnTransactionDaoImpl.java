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

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;

@Repository
public class BillingOnTransactionDaoImpl extends AbstractDaoImpl<BillingOnTransaction> implements BillingOnTransactionDao {

    public BillingOnTransactionDaoImpl() {
        super(BillingOnTransaction.class);
    }

    public BillingOnTransaction getTransTemplate(BillingONCHeader1 cheader1, BillingONItem billItem, BillingONPayment billPayment, String curProviderNo, int itempaymentId) {
        // Symmetric to getUpdateCheader1TransTemplate's strict-parse: the
        // ch1 id is a required FK back to BillingONCHeader1 — a transient
        // (still-null id) header here would NPE on the unboxing below and
        // produce an audit row with a meaningless billNo. Fail loudly so
        // the surrounding @Transactional unit-of-work can roll back.
        if (cheader1.getId() == null) {
            throw new BillingValidationException(
                    "Cannot build BillingOnTransaction: BillingONCHeader1 id is null (transient header)");
        }
        int billNo = cheader1.getId();
        //Date curDate1 = billPayment.getPaymentDate();
        Date curDate = new Date();
        String staus = "P";
        SimpleDateFormat admissionDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        BillingOnTransaction billTrans = new BillingOnTransaction();
        billTrans.setActionType(BillingOnConstants.ACTION_TYPE.C.name());
        // BillingONCHeader1.getAdmissionDate() parses an internal String column that is nullable
        // for non-admission claims. Surface the parse failure (malformed legacy data) but treat
        // it as null on the audit row rather than aborting transaction-row creation entirely.
        try {
            billTrans.setAdmissionDate(cheader1.getAdmissionDate());
        } catch (ParseException e) {
            MiscUtils.getLogger().error("Malformed stored admission_date on ch1 {}: storing null on transaction row",
                    billNo, e);
            billTrans.setAdmissionDate(null);
        }
        billTrans.setBillingDate(cheader1.getBillingDate());
        billTrans.setBillingNotes(cheader1.getComment());
        billTrans.setCh1Id(billNo);
        billTrans.setClinic(cheader1.getClinic());
        billTrans.setCreator(cheader1.getCreator());
        billTrans.setDemographicNo(cheader1.getDemographicNo());
        billTrans.setDxCode(billItem.getDx());
        billTrans.setFacilityNum(cheader1.getFaciltyNum());
        billTrans.setManReview(cheader1.getManReview());
        billTrans.setPaymentDate(curDate);
        billTrans.setPaymentId(billPayment.getId());
        billTrans.setPaymentType(billPayment.getPaymentTypeId());
        billTrans.setPayProgram(cheader1.getPayProgram());
        billTrans.setProviderNo(cheader1.getProviderNo());
        billTrans.setProvince(cheader1.getProvince());
        billTrans.setRefNum(cheader1.getRefNum());
        billTrans.setServiceCode(billItem.getServiceCode());
        billTrans.setServiceCodeInvoiced(billItem.getFee());
        billTrans.setServiceCodeNum(billItem.getServiceCount());
        billTrans.setSliCode(cheader1.getLocation());
        billTrans.setStatus(staus);
        billTrans.setUpdateDatetime(new Timestamp(curDate.getTime()));
        billTrans.setUpdateProviderNo(curProviderNo);
        billTrans.setVisittype(cheader1.getVisitType());
        billTrans.setBillingOnItemPaymentId(itempaymentId);

        return billTrans;
    }

    public BillingOnTransaction getUpdateCheader1TransTemplate(BillingClaimHeaderDto cheader1, String curProviderNo) {
        Date curDate = new Date();
        SimpleDateFormat admissionDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        BillingOnTransaction billTrans = new BillingOnTransaction();
        billTrans.setActionType(BillingOnConstants.ACTION_TYPE.UH.name());

        // Admission date is nullable on the DTO; only attempt to parse when present.
        String admission = cheader1.admissionDate();
        if (admission == null || admission.isEmpty()) {
            billTrans.setAdmissionDate(null);
        } else {
            try {
                billTrans.setAdmissionDate(admissionDateFormat.parse(admission));
            } catch (ParseException e) {
                MiscUtils.getLogger().error("Malformed admission_date {} on ch1 {}: storing null on transaction row",
                        LogSanitizer.sanitize(admission), LogSanitizer.sanitize(cheader1.getId()), e);
                billTrans.setAdmissionDate(null);
            }
        }

        // Billing date is required for OHIP submission — a malformed value would silently null
        // the transaction's billing_date and produce a useless audit row. Fail loudly instead.
        try {
            billTrans.setBillingDate(admissionDateFormat.parse(cheader1.billingDate()));
        } catch (ParseException | NullPointerException e) {
            MiscUtils.getLogger().error("Malformed billing_date {} on ch1 {}: aborting transaction-row build",
                    LogSanitizer.sanitize(cheader1.billingDate()), LogSanitizer.sanitize(cheader1.getId()), e);
            throw new BillingValidationException(
                    "Cannot build BillingOnTransaction: billing_date is missing or not yyyy-MM-dd", e);
        }

        billTrans.setBillingNotes(cheader1.getComment());

        // ch1Id is the FK back to BillingONCHeader1 — persisting -1 would create an orphan
        // audit row that cannot be reconciled. Surface the malformed input instead.
        try {
            billTrans.setCh1Id(Integer.parseInt(cheader1.getId()));
        } catch (NumberFormatException | NullPointerException e) {
            MiscUtils.getLogger().error("Non-numeric ch1 id {} supplied for transaction-row build",
                    LogSanitizer.sanitize(cheader1.getId()), e);
            throw new BillingValidationException(
                    "Cannot build BillingOnTransaction: ch1 id is missing or non-numeric", e);
        }
        billTrans.setClinic(cheader1.getClinic());
        billTrans.setCreator(cheader1.getCreator());

        // demographic_no FK must point at a real patient — -1 sentinel would mask a tampered
        // form post and break downstream patient-history queries.
        try {
            billTrans.setDemographicNo(Integer.parseInt(cheader1.demographicNo()));
        } catch (NumberFormatException | NullPointerException e) {
            MiscUtils.getLogger().error("Non-numeric demographic_no {} supplied for ch1 {} transaction-row build",
                    LogSanitizer.sanitize(cheader1.demographicNo()),
                    LogSanitizer.sanitize(cheader1.getId()), e);
            throw new BillingValidationException(
                    "Cannot build BillingOnTransaction: demographic_no is missing or non-numeric", e);
        }
        billTrans.setFacilityNum(cheader1.facilityNumber());
        billTrans.setManReview(cheader1.manualReview());
        billTrans.setPayProgram(cheader1.payProgram());
        billTrans.setProviderNo(cheader1.getProviderNo());
        billTrans.setProvince(cheader1.getProvince());
        billTrans.setRefNum(cheader1.referralNumber());
        billTrans.setSliCode(cheader1.getLocation());
        billTrans.setStatus(cheader1.getStatus());
        billTrans.setUpdateDatetime(new Timestamp(curDate.getTime()));
        billTrans.setUpdateProviderNo(curProviderNo);
        billTrans.setVisittype(cheader1.visitType());

        return billTrans;
    }
}
