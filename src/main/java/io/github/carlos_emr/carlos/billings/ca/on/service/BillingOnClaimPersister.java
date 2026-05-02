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

package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.billings.ca.on.BillingDates;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingBatchHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.DiskFilenameRow;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONHeaderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONHeader;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingONRepo;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;
/**
 * Write-side persistence service for Ontario billing claims and related batch,
 * item, payment, and export metadata rows.
 *
 * <p>This class is intentionally broad because the legacy claim-save workflow
 * spans several tables that must stay in sync. The method names mirror the old
 * pageUtil/service vocabulary so callers can be migrated incrementally without
 * losing the original business meaning.</p>
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class BillingOnClaimPersister {
    private static final Logger _logger = MiscUtils.getLogger();
    private final BillingONHeaderDao dao;
    private final BillingONCHeader1Dao cheaderDao;
    private final BillingONItemDao itemDao;
    private final BillingONExtDao extDao;
    private final BillingONDiskNameDao diskNameDao;
    private final BillingONFilenameDao filenameDao;
    private final BillingONRepoDao repoDao;
    private final BillingOnItemPaymentDao billOnItemPaymentDao;
    private final BillingOnTransactionDao billTransDao;
    private final BillingONPaymentDao billingONPaymentDao;
    private final BillingPaymentTypeDao billingPaymentTypeDao;

    public BillingOnClaimPersister(BillingONHeaderDao dao,
                                            BillingONCHeader1Dao cheaderDao,
                                            BillingONItemDao itemDao,
                                            BillingONExtDao extDao,
                                            BillingONDiskNameDao diskNameDao,
                                            BillingONFilenameDao filenameDao,
                                            BillingONRepoDao repoDao,
                                            BillingOnItemPaymentDao billOnItemPaymentDao,
                                            BillingOnTransactionDao billTransDao,
                                            BillingONPaymentDao billingONPaymentDao,
                                            BillingPaymentTypeDao billingPaymentTypeDao) {
        this.dao = dao;
        this.cheaderDao = cheaderDao;
        this.itemDao = itemDao;
        this.extDao = extDao;
        this.diskNameDao = diskNameDao;
        this.filenameDao = filenameDao;
        this.repoDao = repoDao;
        this.billOnItemPaymentDao = billOnItemPaymentDao;
        this.billTransDao = billTransDao;
        this.billingONPaymentDao = billingONPaymentDao;
        this.billingPaymentTypeDao = billingPaymentTypeDao;
    }

    /**
     * Persist one OHIP batch header row built from the DTO and return its
     * generated ID. Used at the start of an OHIP claim file write to record
     * the batch's disk/transaction/record/spec/MOH-office bookkeeping.
     *
     * @param val BillingBatchHeaderDto the batch metadata
     * @return int the generated {@code billing_on_header.id}
     */
    public int addOneBatchHeaderRecord(BillingBatchHeaderDto val) {
        BillingONHeader b = new BillingONHeader();
        b.setDiskId(Integer.parseInt(val.getDiskId()));
        b.setTransactionId(val.getTranscId());
        b.setRecordId(val.getRecId());
        b.setSpecId(val.getSpecId());
        b.setMohOffice(val.getMohOffice());
        b.setBatchId(val.getBatchId());
        b.setOperator(val.getOperator());
        b.setGroupNum(val.getGroupNum());
        b.setProviderRegNum(val.getProviderRegNum());
        b.setSpecialty(val.getSpecialty());
        b.sethCount(val.getHCount());
        b.setrCount(val.getRCount());
        b.settCount(val.getTCount());
        b.setBatchDate(new Date());
        b.setCreateDateTime(new Date());
        b.setUpdateDateTime(new Date());
        b.setCreator(val.getCreator());
        b.setAction(val.getAction());
        b.setComment(val.getComment());

        dao.persist(b);

        return b.getId();
    }

    /**
     * Persist one claim header (billing_on_cheader1) row built from the DTO
     * and return its generated ID. Strict-parses optional date/time fields
     * — a malformed value aborts the surrounding {@code @Transactional}
     * unit-of-work rather than silently substituting today's date.
     *
     * @param val BillingClaimHeaderDto the claim header fields
     * @return int the generated {@code billing_on_cheader1.id}
     * @throws IllegalArgumentException when {@code admission_date},
     *         {@code billing_date}, or {@code billing_time} is non-null and
     *         not a valid ISO date
     */
    public int addOneClaimHeaderRecord(BillingClaimHeaderDto val) {
        BillingONCHeader1 b = new BillingONCHeader1();
        b.setHeaderId(0);
        b.setTranscId(val.transactionId());
        b.setRecId(val.recordId());
        b.setHin(val.getHin());
        b.setVer(val.getVer());
        b.setDob(val.getDob());
        b.setPayProgram(val.payProgram());
        b.setPayee(val.getPayee());
        b.setRefNum(val.referralNumber());
        b.setFaciltyNum(val.facilityNumber());
        // Strict parse — silently nulling on malformed input persisted an
        // audit-incorrect billing_on_cheader1 row. Null/blank stays tolerated
        // (legacy contract) but a malformed date now aborts the @Transactional
        // unit-of-work. The setter is skipped on null because
        // BillingONCHeader1.setAdmissionDate calls format() and NPEs on null.
        Date admissionDate = BillingDates.parseOptionalIsoDate(val.admissionDate(), "admission_date");
        if (admissionDate != null) {
            b.setAdmissionDate(admissionDate);
        }

        b.setRefLabNum(val.referringLabNumber());
        b.setManReview(val.manualReview());
        b.setLocation(val.getLocation());
        b.setDemographicNo(Integer.parseInt(val.demographicNo()));
        b.setProviderNo(val.providerNo());
        String apptNo = StringUtils.trimToNull(val.appointmentNo());

        if (apptNo != null) {
            b.setAppointmentNo(Integer.parseInt(val.appointmentNo()));
        } else {
            b.setAppointmentNo(null);
        }

        b.setDemographicName(val.demographicName());
        b.setSex(val.getSex());
        b.setProvince(val.getProvince());
        // Strict parse — silently logging on ParseException and persisting a
        // row with a default-valued billing_date/billing_time was an
        // audit-trail correctness hole. Null/blank stays tolerated; a
        // malformed value now aborts the @Transactional unit-of-work. Setters
        // are guarded against null because BillingONCHeader1.setBillingDate /
        // setBillingTime call format() and NPE on null.
        Date billingDate = BillingDates.parseOptionalIsoDate(val.billingDate(), "billing_date");
        if (billingDate != null) {
            b.setBillingDate(billingDate);
        }
        Date billingTime = BillingDates.parseOptionalIsoTime(val.billingTime(), "billing_time");
        if (billingTime != null) {
            b.setBillingTime(billingTime);
        }


        // Treat null OR blank as "0.00" for both total and paid — money form
        // inputs may legitimately arrive as empty strings (browser auto-fill,
        // hidden field reset). Asymmetric handling would let an empty total
        // hit the validation page while an empty paid silently defaulted to
        // zero for the same input class.
        String totalRaw = (val.getTotal() == null || val.getTotal().isEmpty()) ? "0.00" : val.getTotal();
        b.setTotal(io.github.carlos_emr.carlos.billings.ca.on.BillingMoney
                .parseNonNegativeAmount(totalRaw, "total"));

        String paidRaw = (val.getPaid() == null || val.getPaid().isEmpty()) ? "0.00" : val.getPaid();
        b.setPaid(io.github.carlos_emr.carlos.billings.ca.on.BillingMoney
                .parseNonNegativeAmount(paidRaw, "paid"));

        b.setStatus(val.getStatus());
        b.setComment(val.getComment());
        b.setVisitType(val.visitType());
        b.setProviderOhipNo(val.providerOhipNo());
        b.setProviderRmaNo(val.providerRmaNo());
        b.setApptProviderNo(val.appointmentProviderNo());
        b.setAsstProviderNo(val.assistantProviderNo());
        b.setCreator(val.getCreator());
        b.setClinic(val.getClinic());

        cheaderDao.persist(b);

        return b.getId();
    }

    /**
     * Persist a list of claim items (billing_on_item rows) for the given
     * claim header ID.
     *
     * @param lVal List a list of {@link BillingClaimItemDto} rows; raw
     *             {@code List} for legacy compatibility
     * @param id   int the parent claim header ID ({@code billing_on_cheader1.id})
     */
    public void addItemRecord(List lVal, int id) {

        for (int i = 0; i < lVal.size(); i++) {
            BillingClaimItemDto val = (BillingClaimItemDto) lVal.get(i);

            BillingONItem b = new BillingONItem();
            b.setCh1Id(id);
            b.setTranscId(val.transactionId());
            b.setRecId(val.recordId());
            b.setServiceCode(val.serviceCode());
            b.setFee(val.getFee());
            b.setServiceCount(val.serviceNumber());
            // Strict parse — same reasoning as addOneClaimHeaderRecord above.
            b.setServiceDate(BillingDates.parseOptionalIsoDate(val.serviceDate(), "service_date"));
            b.setDx(val.getDx());
            b.setDx1(val.getDx1());
            b.setDx2(val.getDx2());
            b.setStatus(val.getStatus());

            itemDao.persist(b);
            lVal.set(i, val.withId(b.getId().toString()));
        }
    }

    /**
     * Persist a list of item-payment join rows ({@code billing_on_item_payment})
     * linking each {@link BillingClaimItemDto} to a payment. Pre-parses
     * amounts strictly so a malformed value aborts the @Transactional
     * unit-of-work before any row is written.
     *
     * @param lVal        List a list of {@link BillingClaimItemDto} rows
     * @param id          int the parent claim header ID
     * @param paymentId   int the parent {@code billing_on_payment.id}
     * @param paymentType int the payment-type ID (legacy passthrough)
     * @return boolean legacy contract — see implementation; treat as success indicator
     */
    public boolean addItemPaymentRecord(List lVal, int id, int paymentId, int paymentType) {
        int retval = 0;
        BillingOnItemPayment billOnItemPayment = null;
        Timestamp ts = new Timestamp(new Date().getTime());
        for (int i = 0; i < lVal.size(); i++) {
            BillingClaimItemDto val = (BillingClaimItemDto) lVal.get(i);
            // Pre-parse before mutating the entity. Strict variants surface a
            // malformed amount BEFORE any billing_on_item_payment row is
            // persisted with a silently-zeroed value.
            BigDecimal discount = amountStrictOrZeroForItemPayment(val.getDiscount(), "discount", id, val);
            BigDecimal paid = amountStrictOrZeroForItemPayment(val.getPaid(), "paid", id, val);
            BigDecimal refund = amountStrictOrZeroForItemPayment(val.getRefund(), "refund", id, val);
            billOnItemPayment = new BillingOnItemPayment();
            billOnItemPayment.setBillingOnItemId(Integer.parseInt(val.getId()));
            billOnItemPayment.setBillingOnPaymentId(paymentId);
            billOnItemPayment.setCh1Id(id);
            billOnItemPayment.setDiscount(discount);
            billOnItemPayment.setPaid(paid);
            billOnItemPayment.setPaymentTimestamp(ts);
            billOnItemPayment.setRefund(refund);
            billOnItemPaymentDao.persist(billOnItemPayment);
            lVal.set(i, val.withId(billOnItemPayment.getId().toString()));
        }
        return (retval != 0);
    }

    private void addCreate3rdInvoiceTrans(BillingClaimHeaderDto billHeader, List<BillingClaimItemDto> billItemList, BillingONPayment billOnPayment) {
        if (billItemList.size() < 1) {
            return;
        }
        // Parse header dates upfront — surfacing failure here aborts the
        // surrounding @Transactional unit-of-work BEFORE any billOnTrans row
        // is persisted with a null/zero placeholder.
        Date admissionDate = BillingDates.parseOptionalIsoDate(billHeader.admissionDate(), "admission_date");
        Date billingDate = BillingDates.parseOptionalIsoDate(billHeader.billingDate(), "billing_date");
        Timestamp updateTs = new Timestamp(new Date().getTime());
        BillingOnTransaction billTrans = null;
        for (BillingClaimItemDto billItem : billItemList) {
            // Pre-parse the per-item amounts before mutating the entity, so a
            // mid-loop parse failure cannot leave a partially-populated
            // BillingOnTransaction in an inconsistent state.
            BigDecimal discount = amountStrictOrZeroForItemPayment(
                    billItem.getDiscount(), "discount", Integer.parseInt(billHeader.getId()), billItem);
            BigDecimal paid = amountStrictOrZeroForItemPayment(
                    billItem.getPaid(), "paid", Integer.parseInt(billHeader.getId()), billItem);
            BigDecimal refund = amountStrictOrZeroForItemPayment(
                    billItem.getRefund(), "refund", Integer.parseInt(billHeader.getId()), billItem);

            billTrans = new BillingOnTransaction();
            billTrans.setActionType(BillingOnConstants.ACTION_TYPE.C.name());
            billTrans.setAdmissionDate(admissionDate);
            billTrans.setBillingDate(billingDate);

            billTrans.setBillingNotes(billHeader.getComment());
            billTrans.setBillingOnItemPaymentId(Integer.parseInt(billItem.getId()));
            billTrans.setCh1Id(Integer.parseInt(billHeader.getId()));
            billTrans.setClinic(billHeader.getClinic());
            billTrans.setCreator(billHeader.getCreator());
            billTrans.setDemographicNo(Integer.parseInt(billHeader.demographicNo()));
            billTrans.setDxCode(billItem.getDx());
            billTrans.setFacilityNum(billHeader.facilityNumber());
            billTrans.setManReview(billHeader.manualReview());
            billTrans.setPaymentDate(billOnPayment.getPaymentDate());
            billTrans.setPaymentId(billOnPayment.getId());
            billTrans.setPaymentType(billOnPayment.getPaymentTypeId());
            billTrans.setPayProgram(billHeader.payProgram());
            billTrans.setProviderNo(billHeader.getProviderNo());
            billTrans.setProvince(billHeader.getProvince());
            billTrans.setRefNum(billHeader.referralNumber());
            billTrans.setServiceCode(billItem.serviceCode());
            billTrans.setServiceCodeInvoiced(billItem.getFee());
            billTrans.setServiceCodeDiscount(discount);
            billTrans.setServiceCodeNum(billItem.serviceNumber());
            billTrans.setServiceCodePaid(paid);
            billTrans.setServiceCodeRefund(refund);
            billTrans.setStatus(billHeader.getStatus());
            billTrans.setSliCode(billHeader.getLocation());
            billTrans.setUpdateDatetime(updateTs);
            billTrans.setUpdateProviderNo(billHeader.getCreator());
            billTrans.setVisittype(billHeader.visitType());
            billTransDao.persist(billTrans);
        }
    }

    private BigDecimal amountStrictOrZeroForItemPayment(
            String amount, String fieldName, int billingNo, BillingClaimItemDto item) {
        try {
            return BillingMoney.amountStrictOrZero(amount);
        } catch (NumberFormatException ex) {
            throw new BillingValidationException(
                    "addItemPaymentRecord: malformed " + fieldName
                            + " amount for billingNo=" + billingNo
                            + ", itemId=" + item.getId(),
                    ex);
        }
    }

    /**
     * Persist one {@code billing_on_transaction} audit row per item at OHIP-claim
     * creation time. Amounts are intentionally zero (no payment received yet).
     * Header dates are strict-parsed up front so any malformed value aborts the
     * surrounding @Transactional unit-of-work.
     *
     * @param billHeader   BillingClaimHeaderDto the claim header
     * @param billItemList List the per-item rows to record audit entries for
     */
    public void addCreateOhipInvoiceTrans(BillingClaimHeaderDto billHeader, List<BillingClaimItemDto> billItemList) {
        if (billItemList.size() < 1) {
            return;
        }
        // Parse header dates upfront — failure aborts the @Transactional
        // unit-of-work BEFORE any billOnTrans row is persisted with a null
        // placeholder. At OHIP-claim creation amounts are intentionally ZERO
        // (no payment received yet) so amount parsing is N/A here.
        Date admissionDate = BillingDates.parseOptionalIsoDate(billHeader.admissionDate(), "admission_date");
        Date billingDate = BillingDates.parseOptionalIsoDate(billHeader.billingDate(), "billing_date");
        Timestamp updateTs = new Timestamp(new Date().getTime());
        BillingOnTransaction billTrans = null;
        for (BillingClaimItemDto billItem : billItemList) {
            billTrans = new BillingOnTransaction();
            billTrans.setActionType(BillingOnConstants.ACTION_TYPE.C.name());
            billTrans.setAdmissionDate(admissionDate);
            billTrans.setBillingDate(billingDate);

            billTrans.setBillingNotes(billHeader.getComment());
            billTrans.setBillingOnItemPaymentId(Integer.parseInt(billItem.getId()));
            billTrans.setCh1Id(Integer.parseInt(billHeader.getId()));
            billTrans.setClinic(billHeader.getClinic());
            billTrans.setCreator(billHeader.getCreator());
            billTrans.setDemographicNo(Integer.parseInt(billHeader.demographicNo()));
            billTrans.setDxCode(billItem.getDx());
            billTrans.setFacilityNum(billHeader.facilityNumber());
            billTrans.setManReview(billHeader.manualReview());
            billTrans.setPaymentDate(null);
            billTrans.setPaymentId(0);
            billTrans.setPaymentType(0);
            billTrans.setPayProgram(billHeader.payProgram());
            billTrans.setProviderNo(billHeader.getProviderNo());
            billTrans.setProvince(billHeader.getProvince());
            billTrans.setRefNum(billHeader.referralNumber());
            billTrans.setServiceCode(billItem.serviceCode());
            billTrans.setServiceCodeInvoiced(billItem.getFee());
            billTrans.setServiceCodeDiscount(BigDecimal.ZERO);
            billTrans.setServiceCodePaid(BigDecimal.ZERO);
            billTrans.setServiceCodeRefund(BigDecimal.ZERO);
            billTrans.setServiceCodeNum(billItem.serviceNumber());
            billTrans.setStatus(billHeader.getStatus());
            billTrans.setSliCode(billHeader.getLocation());
            billTrans.setUpdateDatetime(updateTs);
            billTrans.setUpdateProviderNo(billHeader.getCreator());
            billTrans.setVisittype(billHeader.visitType());
            billTransDao.persist(billTrans);
        }
    }


    /**
     * Persist the 9 BillingONExt rows for a third-party bill (billTo, remitTo,
     * total, payment, discount, provider_no, gst, payDate, payMethod) plus the
     * matching {@code billing_on_payment} parent. Strict-parses {@code payDate}
     * up front so a malformed value aborts before any orphaned ext rows are
     * written.
     *
     * @param mVal   Map form-field value lookup (keyed by field name)
     * @param id     int the parent claim header ID
     * @param claimEnvelope ArrayList positional bag: index 0 = {@link BillingClaimHeaderDto},
     *               index 1 = {@code List<BillingClaimItemDto>}
     * @return boolean {@code true} on success
     * @throws BillingValidationException when {@code payDate} is malformed
     */
    @SuppressWarnings("unchecked")
    public boolean add3rdBillExt(Map<String, String> mVal, int id, ArrayList claimEnvelope) {
        BillingClaimHeaderDto claim1Obj = (BillingClaimHeaderDto) claimEnvelope.get(0);
        String[] temp = {"billTo", "remitTo", "total", "payment", "discount", "provider_no", "gst", "payDate", "payMethod"};
        String demoNo = mVal.get("demographic_no");
        String dateTime = UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss");
        mVal.put("payDate", dateTime);
        String paymentSumParam = mVal.get("total_payment");

        // Parse the payment date upfront so a malformed value aborts the
        // @Transactional unit-of-work BEFORE any BillingONExt rows are written.
        // Earlier shape persisted the 9 ext rows first and then returned silently
        // on ParseException, leaving orphan ext rows with no parent payment.
        Date paymentDate = null;
        if (paymentSumParam != null) {
            try {
                paymentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(dateTime);
            } catch (ParseException ex) {
                throw new BillingValidationException(
                        "add3rdBillExt: malformed payDate [" + dateTime + "] for billingNo=" + id, ex);
            }
        }

        BillingONPayment payment = null;
        int paymentType = 0;
        if (paymentSumParam != null) {
            BillingONCHeader1 ch1 = cheaderDao.find(id);
            String paymentTypeParam = mVal.get("payMethod");
            if (paymentTypeParam == null || paymentTypeParam.isEmpty()) {
                paymentTypeParam = "1";
            }
            paymentType = Integer.parseInt(paymentTypeParam);

            payment = new BillingONPayment();
            payment.setTotal_payment(BillingMoney.amount(paymentSumParam));
            payment.setTotal_discount(BillingMoney.amountStrictOrZero(mVal.get("total_discount")));
            payment.setTotal_refund(BillingMoney.zeroAmount());
            payment.setPaymentDate(paymentDate);
            payment.setBillingOnCheader1(ch1);
            payment.setBillingNo(id);
            payment.setCreator(claim1Obj.getCreator());
            payment.setPaymentTypeId(paymentType);

            billingONPaymentDao.persist(payment);
        }
        Integer paymentId = payment == null ? Integer.valueOf(0) : payment.getId();

        for (int i = 0; i < temp.length; i++) {
            String val = mVal.get(temp[i]);
            if ("discount".equals(temp[i])) {
                val = mVal.get("total_discount"); // 'refund' stands for write off, here totoal_discount is write off
            }
            if ("payment".equals(temp[i])) {
                val = mVal.get("total_payment");
            }
            BillingONExt billingONExt = new BillingONExt();
            billingONExt.setBillingNo(id);
            billingONExt.setDemographicNo(Integer.parseInt(demoNo));
            billingONExt.setKeyVal(temp[i]);
            billingONExt.setValue(val);
            billingONExt.setDateTime(new Date());
            billingONExt.setStatus('1');
            billingONExt.setPaymentId(paymentId);
            extDao.persist(billingONExt);
        }

        if (payment != null) {
            addItemPaymentRecord((List) claimEnvelope.get(1), id, payment.getId(), paymentType);
            addCreate3rdInvoiceTrans((BillingClaimHeaderDto) claimEnvelope.get(0), (List<BillingClaimItemDto>) claimEnvelope.get(1), payment);
        }
        return true;
    }


    /**
     * Persist one item row for an existing claim header. Strict-parses the
     * ISO {@code service_date}; throws on null/blank/malformed input.
     *
     * @param val BillingClaimItemDto the item fields
     * @return int the generated {@code billing_on_item.id}
     * @throws IllegalArgumentException on null / blank / malformed
     *         {@code service_date} (delegated by {@link BillingDates#parseIsoDate(String)})
     */
    public int addOneItemRecord(BillingClaimItemDto val) {
        BillingONItem item = new BillingONItem();
        item.setCh1Id(Integer.parseInt(val.claimHeaderId()));
        item.setTranscId(val.transactionId());
        item.setRecId(val.recordId());
        item.setServiceCode(val.serviceCode());
        item.setFee(val.getFee());
        item.setServiceCount(val.serviceNumber());
        item.setServiceDate(BillingDates.parseIsoDate(val.serviceDate()));
        item.setDx(val.getDx());
        item.setDx1(val.getDx1());
        item.setDx2(val.getDx2());
        item.setStatus(val.getStatus());
        BillingONItem returnItem = itemDao.saveEntity(item);
        return returnItem.getId(); //return ID

    }

    /**
     * Single-arg overload of {@link #add3rdBillExt(Map, int, ArrayList)} that
     * persists the 9 ext rows together with a new {@code billing_on_payment}
     * parent. Used when a third-party bill is paid at creation time without
     * needing the positional vector argument.
     *
     * @param mVal Map form-field values
     * @param id   int the parent claim header ID
     * @return boolean {@code true} on success
     */
    public boolean add3rdBillExt(Map<String, String> mVal, int id) {
        boolean retval = true;
        String[] temp = {"billTo", "remitTo", "total", "payment", "refund", "provider_no", "gst", "payDate", "payMethod"};
        String demoNo = mVal.get("demographic_no");
        String dateTime = UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss");
        mVal.put("payDate", dateTime);

        BillingONPayment newPayment = new BillingONPayment();
        BillingONCHeader1 ch1 = cheaderDao.find(id);
        newPayment.setBillingOnCheader1(ch1);
        newPayment.setPaymentDate(UtilDateUtilities.StringToDate(dateTime));

        for (int i = 0; i < temp.length; i++) {
            BillingONExt b = new BillingONExt();
            b.setBillingNo(id);
            b.setDemographicNo(Integer.valueOf(demoNo));
            b.setKeyVal(temp[i]);
            b.setValue(mVal.get(temp[i]));
            b.setDateTime(new Date());
            b.setStatus('1');
            b.setPaymentId(0);
            newPayment.getBillingONExtItems().add(b);
        }

        billingONPaymentDao.persist(newPayment);

        return retval;
    }

    /**
     * Persist a {@code payee} key on {@code billing_on_ext} for the given
     * billing header. Used by {@code BillingClaimSubmissionService} to keep
     * the payee write inside the same transaction as the header + items —
     * splitting them lets a payee-write failure orphan the header.
     *
     * @param billingNo  generated billing_on_cheader1 id
     * @param payeeValue user-supplied payee name (already validated)
     * @return true when the row was persisted
     */
    public boolean persistPayeeExt(int billingNo, String payeeValue) {
        BillingONCHeader1 ch1 = cheaderDao.find(billingNo);
        if (ch1 == null) {
            return false;
        }
        BillingONExt ext = new BillingONExt();
        ext.setBillingNo(billingNo);
        ext.setDemographicNo(ch1.getDemographicNo());
        ext.setKeyVal("payee");
        ext.setValue(payeeValue);
        ext.setDateTime(ch1.getTimestamp());
        extDao.persist(ext);
        return true;
    }

    /**
     * Persist a {@code billing_on_diskname} row plus, on success, one
     * {@code billing_on_filename} row per provider entry in {@code val}.
     *
     * @param val BillingDiskNameDto the disk metadata + filename arrays
     * @return int the generated {@code billing_on_diskname.id}, or 0 on failure
     */
    public int addBillingDiskName(BillingDiskNameDto val) {
        BillingONDiskName b = new BillingONDiskName();
        b.setMonthCode(val.getMonthCode());
        b.setBatchCount(Integer.parseInt(val.getBatchcount()));
        b.setOhipFilename(val.getOhipfilename());
        b.setGroupNo(val.getGroupno());
        b.setCreator(val.getCreator());
        b.setClaimRecord(val.getClaimrecord());
        b.setCreateDateTime(new Date());
        b.setStatus(val.getStatus());
        b.setTotal(val.getTotal());

        diskNameDao.persist(b);

        int retval = b.getId();

        if (b.getId() > 0) {
            // add filenames, if needed
            List<DiskFilenameRow> rows = val.getFilenames();
            if (rows != null) {
                for (DiskFilenameRow row : rows) {
                    BillingONFilename f = new BillingONFilename();
                    f.setDiskId(b.getId());
                    f.setHtmlFilename(row.htmlFilename());
                    f.setProviderOhipNo(row.providerOhipNo());
                    f.setProviderNo(row.providerNo());
                    f.setClaimRecord(row.claimRecord());
                    f.setStatus(row.status());
                    f.setTotal(row.total());
                    filenameDao.persist(f);
                }
            }
        } else {
            retval = 0;
        }

        return retval;
    }

    /**
     * Persist the diskname (and its filenames) into the {@code billing_on_repo}
     * archive table for OHIP regeneration tracking.
     *
     * @param val BillingDiskNameDto the disk metadata
     * @return int the generated {@code billing_on_repo.id}, or 0 on failure
     */
    public int addRepoDiskName(BillingDiskNameDto val) {
        int retval = 0;
        BillingONRepo b = new BillingONRepo();
        b.sethId(Integer.parseInt(val.getId()));
        b.setCategory("billing_on_diskname");
        b.setContent(val.getMonthCode() + "|" + val.getBatchcount() + "|" + val.getOhipfilename() + "|" + val.getGroupno() + "|" + val.getCreator()
                + "|" + val.getClaimrecord() + "|" + val.getCreatedatetime() + "|" + val.getStatus() + "|" + val.getTotal() + "|"
                + val.getUpdatedatetime());
        b.setCreateDateTime(new Date());

        repoDao.persist(b);
        retval = b.getId();

        if (b.getId() > 0) {
            // add filenames, if needed
            List<DiskFilenameRow> rows = val.getFilenames();
            if (rows != null) {
                for (DiskFilenameRow row : rows) {
                    BillingONRepo r = new BillingONRepo();
                    r.sethId(Integer.valueOf(row.filenameId()));
                    r.setCategory("billing_on_filename");
                    r.setContent(val.getId() + "|" + row.htmlFilename() + "|"
                            + row.providerOhipNo() + "|" + row.providerNo() + "|" + row.claimRecord()
                            + "|" + row.status() + "|" + row.total() + "|" + val.getUpdatedatetime());

                    r.setCreateDateTime(new Date());

                    repoDao.persist(r);
                }
            }
        } else {
            retval = 0;
        }
        return retval;
    }

    /**
     * Update an existing {@code billing_on_diskname} row with the provided
     * status / filename / claim-record snapshot from the DTO.
     *
     * @param val BillingDiskNameDto the updated disk metadata
     * @return boolean {@code true} on success
     */
    public boolean updateDiskName(BillingDiskNameDto val) {
        BillingONDiskName b = diskNameDao.find(Integer.parseInt(val.getId()));
        if (b != null) {
            b.setCreator(val.getCreator());
            diskNameDao.merge(b);
        }
        return true;
    }

    /**
     * Archive a {@code billing_on_header} batch into the {@code billing_on_repo}
     * snapshot table for OHIP regeneration tracking.
     *
     * @param val BillingBatchHeaderDto the batch fields to archive
     * @return int the generated {@code billing_on_repo.id}
     */
    public int addRepoBatchHeader(BillingBatchHeaderDto val) {
        BillingONRepo b = new BillingONRepo();
        b.sethId(Integer.parseInt(val.getId()));
        b.setCategory("billing_on_header");
        b.setContent(val.getDiskId() + "|" + val.getTranscId() + "|" + val.getRecId() + "|" + val.getSpecId() + "|" + val.getMohOffice() + "|"
                + val.getBatchId() + "|" + val.getOperator() + "|" + val.getGroupNum() + "|" + val.getProviderRegNum() + "|"
                + val.getSpecialty() + "|" + val.getHCount() + "|" + val.getRCount() + "|" + val.getTCount() + "|" + val.getBatchDate()
                + "|" + val.getCreatedatetime() + "|" + val.getUpdatedatetime() + "|" + val.getCreator() + "|" + val.getAction() + "|"
                + val.getComment());
        b.setCreateDateTime(new Date());
        repoDao.persist(b);

        return b.getId();
    }

    /**
     * Update an existing batch header row with the operator-modifiable fields
     * (MOH office, batch id, specialty, comment, action). Other fields are
     * preserved.
     *
     * @param val BillingBatchHeaderDto the updated batch metadata
     * @return boolean {@code true} on success (or if the row was not found)
     */
    public boolean updateBatchHeaderRecord(BillingBatchHeaderDto val) {
        BillingONHeader b = dao.find(Integer.parseInt(val.getId()));
        if (b != null) {
            b.setMohOffice(val.getMohOffice());
            b.setBatchId(val.getBatchId());
            b.setSpecialty(val.getSpecialty());
            b.setCreator(val.getCreator());
            b.setUpdateDateTime(new Date());
            b.setAction(val.getAction());
            b.setComment(val.getComment());
            dao.merge(b);
        }

        return true;
    }

}
