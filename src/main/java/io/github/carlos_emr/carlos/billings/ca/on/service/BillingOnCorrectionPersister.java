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

import java.text.ParseException;
import java.math.BigDecimal;
import java.util.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.ArrayList;
import io.github.carlos_emr.carlos.billings.ca.on.BillingDates;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONEAReport;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.commn.model.RaDetail;

/**
 * Persister for the ON billing correction workflow. Owns every
 * {@code billing_on_cheader1} / {@code billing_on_item} mutation the
 * correction page can perform — header updates, item updates, status
 * flips, total/paid adjustments, plus the {@code billing_on_transaction}
 * audit-trail rows that mirror each item-level change.
 *
 * <p>The class also exposes the loader methods the correction page calls
 * to populate its view model ({@link #getBillingRecordObj},
 * {@link #getBillingRejectList}, {@link #getBillingExplanatoryList},
 * etc.) — these reads sit alongside the writes because the same callsite
 * typically loads → mutates → persists, and the loader's DTO shape is
 * shared with the persist path. Splitting the reads into a sibling
 * {@code *Loader} is a candidate follow-up if either side grows further.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class BillingOnCorrectionPersister {

    private final BillingONCHeader1Dao billingHeaderDao;
    private final BillingONItemDao billingItemDao;
    private final BillingONEAReportDao billingEaReportDao;
    private final RaDetailDao raDetailDao;
    private final BillingOnItemPaymentDao itemPaymentDao;
    private final BillingONExtDao billExtDao;
    private final BillingOnTransactionDao billOnTransDao;
    private final BillingOnAuditLogService auditLog;

    public BillingOnCorrectionPersister(BillingONCHeader1Dao billingHeaderDao,
                                        BillingONItemDao billingItemDao,
                                        BillingONEAReportDao billingEaReportDao,
                                        RaDetailDao raDetailDao,
                                        BillingOnItemPaymentDao itemPaymentDao,
                                        BillingONExtDao billExtDao,
                                        BillingOnTransactionDao billOnTransDao,
                                        BillingOnAuditLogService auditLog) {
        this.billingHeaderDao = billingHeaderDao;
        this.billingItemDao = billingItemDao;
        this.billingEaReportDao = billingEaReportDao;
        this.raDetailDao = raDetailDao;
        this.itemPaymentDao = itemPaymentDao;
        this.billExtDao = billExtDao;
        this.billOnTransDao = billOnTransDao;
        this.auditLog = auditLog;
    }

    // Formatting and parsing delegate to BillingDates' DateTimeFormatter
    // value-typed singletons — immutable and thread-safe by design.

    /**
     * Apply the DTO's correction-form fields to an existing
     * {@code billing_on_cheader1} row identified by {@code ch1Obj.getId()}.
     *
     * @param ch1Obj BillingClaimHeaderDto the correction-form snapshot
     * @return boolean {@code true} on success
     * @throws IllegalArgumentException when a date / time field is malformed
     *         (delegated by {@link BillingDates#parseIsoDate(String)} and
     *         {@link BillingDates#parseIsoTime(String)} — unchecked, so a
     *         {@code @Transactional} caller rolls back rather than committing
     *         partial setter state).
     */
    public boolean updateBillingClaimHeader(BillingClaimHeaderDto ch1Obj) {
        BillingONCHeader1 c = billingHeaderDao.find(ch1Obj.getId());
        c.setTranscId(ch1Obj.transactionId());
        c.setRecId(ch1Obj.recordId());
        c.setHin(ch1Obj.getHin());
        c.setVer(ch1Obj.getVer());
        c.setDob(ch1Obj.getDob());
        c.setPayProgram(ch1Obj.payProgram());
        c.setPayee(ch1Obj.getPayee());
        c.setRefNum(ch1Obj.referralNumber());
        c.setFaciltyNum(ch1Obj.facilityNumber());
        c.setAdmissionDate(BillingDates.parseIsoDate(ch1Obj.admissionDate()));
        c.setRefLabNum(ch1Obj.referringLabNumber());
        c.setManReview(ch1Obj.manualReview());
        c.setLocation(ch1Obj.getLocation());
        c.setDemographicNo(Integer.parseInt(ch1Obj.demographicNo()));
        c.setProviderNo(ch1Obj.providerNo());
        c.setAppointmentNo(Integer.parseInt(ch1Obj.appointmentNo()));
        c.setDemographicName(ch1Obj.demographicName());
        c.setSex(ch1Obj.getSex());
        c.setProvince(ch1Obj.getProvince());
        c.setBillingDate(BillingDates.parseIsoDate(ch1Obj.billingDate()));
        c.setBillingTime(BillingDates.parseIsoTime(ch1Obj.billingTime()));
        c.setTotal(io.github.carlos_emr.carlos.billings.ca.on.BillingMoney
                .parseNonNegativeAmount(ch1Obj.getTotal(), "total"));
        c.setPaid(io.github.carlos_emr.carlos.billings.ca.on.BillingMoney
                .parseNonNegativeAmount(ch1Obj.getPaid(), "paid"));
        c.setStatusStrict(ch1Obj.getStatus());
        c.setComment(ch1Obj.getComment());
        c.setVisitType(ch1Obj.visitType());
        c.setProviderOhipNo(ch1Obj.providerOhipNo());
        c.setProviderRmaNo(ch1Obj.providerRmaNo());
        c.setApptProviderNo(ch1Obj.appointmentProviderNo());
        c.setAsstProviderNo(ch1Obj.assistantProviderNo());
        c.setCreator(ch1Obj.getCreator());
        c.setClinic(ch1Obj.getClinic() == null ? "" : ch1Obj.getClinic());

        billingHeaderDao.merge(c);

        return true;
    }

    /**
     * Apply the DTO's correction-form fields to an existing
     * {@code billing_on_item} row identified by {@code val.getId()}.
     *
     * @param val BillingClaimItemDto the correction-form snapshot
     * @return boolean {@code true} on success
     * @throws IllegalArgumentException when a date field is malformed
     *         (delegated by {@link BillingDates#parseIsoDate(String)}).
     */
    public boolean updateBillingOneItem(BillingClaimItemDto val) {
        BillingONItem b = billingItemDao.find(val.getId());
        if (b != null) {
            b.setTranscId(val.transactionId());
            b.setRecId(val.recordId());
            b.setServiceCode(val.serviceCode());
            b.setFee(val.getFee());
            b.setServiceCount(val.serviceNumber());
            b.setServiceDate(BillingDates.parseIsoDate(val.serviceDate()));
            b.setDx(val.getDx());
            b.setDx1(val.getDx1());
            b.setDx2(val.getDx2());
            b.setStatusStrict(val.getStatus());

            billingItemDao.merge(b);
        }
        return true;
    }


    /**
     * Set the status of a {@code billing_on_cheader1} row.
     *
     * @param id         String the claim header ID
     * @param status     String one of the BillingONCHeader1 status constants
     * @param providerNo String the provider performing the update
     * @return boolean {@code true} on success
     */
    public boolean updateBillingStatus(String id, String status, String providerNo) {
        BillingONCHeader1 h = billingHeaderDao.find(Integer.valueOf(id));
        if (h != null) {
            h.setStatusStrict(status);
            billingHeaderDao.merge(h);

            List<BillingONItem> items = billingItemDao.getBillingItemByCh1Id(Integer.valueOf(id));
            for (BillingONItem i : items) {
                // Keep header/item status transitions in lockstep so later
                // history, review, and invoice totals do not mix active header
                // state with stale line-item state.
                i.setStatusStrict(status);
                billingItemDao.merge(i);
            }
            auditLog.addBillingLog(providerNo, "updateBillingStatus", "", id);
            auditLog.addBillingLog(providerNo, "updateBillingStatus-items", "", id);
            return true;
        } else {
            return false;
        }
    }


    public List<String> getBillingCH1NoStatusByAppt(String id) {
        List<String> obj = new ArrayList<String>();
        List<BillingONCHeader1> headers = billingHeaderDao.findByAppointmentNo(Integer.parseInt(id));
        for (BillingONCHeader1 h : headers) {
            obj.add(h.getId().toString());
            obj.add(h.getStatus());
        }

        return obj;
    }


    public List<String> getBillingCH1NoStatusByBillNo(String id) {
        List<String> obj = new ArrayList<String>();
        BillingONCHeader1 header = billingHeaderDao.find(Integer.parseInt(id));
        obj.add(header.getId().toString());
        obj.add(header.getStatus());

        return obj;
    }

    @SuppressWarnings("rawtypes")
    /**
     * @param id String the claim header ID
     * @return List the {@code pay_program} value(s) for the row
     */
    public List getPayprogramByBillNo(String id) {
        BillingONCHeader1 b = billingHeaderDao.find(id);
        List obj = new ArrayList();
        if (b != null) {
            obj.add(b.getPayProgram());
        }
        return obj;
    }

    // 0-cheader1 obj, 1 - item1obj, 2 - item2obj, ...
    /**
     * Load the full correction-form snapshot. Returns a positional bag:
     * index 0 = {@link BillingClaimHeaderDto}, index 1 =
     * {@code List<BillingClaimItemDto>}.
     *
     * @param id String the claim header ID
     * @return List the snapshot, or empty when not found
     */
    public List getBillingRecordObj(String id) {
        List obj = new ArrayList();
        BillingClaimHeaderDto ch1Obj = null;
        BillingClaimItemDto itemObj = null;

        BillingONCHeader1 h = billingHeaderDao.find(Integer.parseInt(id));
        if (h != null) {
            ch1Obj = new BillingClaimHeaderDto();
            ch1Obj = ch1Obj.withId(h.getId().toString());
            ch1Obj = ch1Obj.withTransactionId(h.getTranscId());
            ch1Obj = ch1Obj.withRecordId(h.getRecId());
            ch1Obj = ch1Obj.withHin(h.getHin());
            ch1Obj = ch1Obj.withVer(h.getVer());
            ch1Obj = ch1Obj.withDob(h.getDob());

            ch1Obj = ch1Obj.withPayProgram(h.getPayProgram());
            ch1Obj = ch1Obj.withPayee(h.getPayee());
            ch1Obj = ch1Obj.withReferralNumber(h.getRefNum());
            ch1Obj = ch1Obj.withFacilityNumber(h.getFaciltyNum());
            try {
                if (h.getAdmissionDate() != null)
                    ch1Obj = ch1Obj.withAdmissionDate(BillingDates.formatIsoDate(h.getAdmissionDate()));
                else
                    ch1Obj = ch1Obj.withAdmissionDate("");
            } catch (ParseException e) {
                // BillingONCHeader1.getAdmissionDate() parses the persisted
                // string column and throws ParseException on malformed
                // legacy data — the entity getter is the actual throw site,
                // not the BillingDates.formatIsoDate(Date) call. Log with bill id
                // so the operator can distinguish "missing" from "corrupt"
                // admission_date in the audit trail.
                MiscUtils.getLogger().warn(
                        "BillingOnCorrectionPersister: unparseable admission_date on ch1.id={}; refusing to render blank",
                        h.getId(), e);
                throw new BillingValidationException(
                        "Bill " + h.getId() + " has an unparseable admission date; refusing to render a blank correction field",
                        e);
            }
            ch1Obj = ch1Obj.withReferringLabNumber(h.getRefLabNum());
            ch1Obj = ch1Obj.withManualReview(h.getManReview());
            ch1Obj = ch1Obj.withLocation(h.getLocation());
            ch1Obj = ch1Obj.withClinic(h.getClinic());

            ch1Obj = ch1Obj.withDemographicNo(h.getDemographicNo().toString());
            ch1Obj = ch1Obj.withProviderNo(h.getProviderNo());

            if (h.getAppointmentNo() != null) {
                ch1Obj = ch1Obj.withAppointmentNo(h.getAppointmentNo().toString());
            } else {
                ch1Obj = ch1Obj.withAppointmentNo(null);
            }

            ch1Obj = ch1Obj.withDemographicName(h.getDemographicName());
            ch1Obj = ch1Obj.withSex(h.getSex());
            ch1Obj = ch1Obj.withProvince(h.getProvince());

            if (h.getBillingDate() != null)
                ch1Obj = ch1Obj.withBillingDate(BillingDates.formatIsoDate(h.getBillingDate()));
            else
                ch1Obj = ch1Obj.withBillingDate("");

            if (h.getBillingTime() != null)
                ch1Obj = ch1Obj.withBillingTime(BillingDates.formatIsoTime(h.getBillingTime()));
            else
                ch1Obj = ch1Obj.withBillingTime("");

            ch1Obj = ch1Obj.withTotal(String.valueOf(h.getTotal().doubleValue()));
            ch1Obj = ch1Obj.withPaid(String.valueOf(h.getPaid().doubleValue()));
            ch1Obj = ch1Obj.withStatus(h.getStatus());
            ch1Obj = ch1Obj.withComment(h.getComment());
            ch1Obj = ch1Obj.withVisitType(h.getVisitType());
            ch1Obj = ch1Obj.withProviderOhipNo(h.getProviderOhipNo());
            ch1Obj = ch1Obj.withProviderRmaNo(h.getProviderRmaNo());
            ch1Obj = ch1Obj.withAssistantProviderNo(h.getAsstProviderNo());
            ch1Obj = ch1Obj.withCreator(h.getCreator());
            if (h.getTimestamp() != null)
                ch1Obj = ch1Obj.withUpdateDateTime(BillingDates.formatIsoTimestamp(h.getTimestamp()));
            else
                ch1Obj = ch1Obj.withUpdateDateTime("");

            ch1Obj = ch1Obj.withClinic(h.getClinic());

            // get billTo from billing_on_ext
            BillingONExt ext = billExtDao.getClaimExtItem(Integer.parseInt(ch1Obj.getId()), Integer.parseInt(ch1Obj.demographicNo()), "billTo");
            if (ext != null) {
                ch1Obj = ch1Obj.withBillto(ext.getValue());
            }

            obj.add(ch1Obj);
        }

        List<BillingONItem> items = billingItemDao.getActiveBillingItemByCh1Id(Integer.parseInt(id));
        for (BillingONItem i : items) {
            itemObj = new BillingClaimItemDto();
            itemObj = itemObj.withId(i.getId().toString());
            itemObj = itemObj.withClaimHeaderId(i.getCh1Id().toString());
            itemObj = itemObj.withTransactionId(i.getTranscId());
            itemObj = itemObj.withRecordId(i.getRecId());
            itemObj = itemObj.withServiceCode(i.getServiceCode());
            itemObj = itemObj.withFee(i.getFee());
            itemObj = itemObj.withServiceNumber(i.getServiceCount());
            if (i.getServiceDate() != null)
                itemObj = itemObj.withServiceDate(BillingDates.formatIsoDate(i.getServiceDate()));
            else
                itemObj = itemObj.withServiceDate("");

            String diagcode = i.getDx();
            diagcode = ":::".equals(diagcode) ? "   " : diagcode;
            itemObj = itemObj.withDx(diagcode);
            itemObj = itemObj.withDx1(i.getDx1());
            itemObj = itemObj.withDx2(i.getDx2());
            itemObj = itemObj.withStatus(i.getStatus());
            if (i.getLastEditDT() != null)
                itemObj = itemObj.withTimestamp(BillingDates.formatIsoTimestamp(i.getLastEditDT()));
            else
                itemObj = itemObj.withTimestamp("");

            List<BillingOnItemPayment> itemPayList = itemPaymentDao.getAllByItemId(Integer.parseInt(itemObj.getId()));
            BigDecimal sumPaid = BigDecimal.ZERO;
            BigDecimal sumDiscount = BigDecimal.ZERO;
            BigDecimal sumRefund = BigDecimal.ZERO;
            if (itemPayList != null) {
                for (BillingOnItemPayment itemPay : itemPayList) {
                    sumPaid = sumPaid.add(itemPay.getPaid());
                    sumDiscount = sumDiscount.add(itemPay.getDiscount());
                    sumRefund = sumRefund.add(itemPay.getRefund());
                }
            }
            itemObj = itemObj.withPaid(sumPaid.toString());
            itemObj = itemObj.withDiscount(sumDiscount.toString());
            itemObj = itemObj.withRefund(sumRefund.toString());


            obj.add(itemObj);
        }


        return obj;
    }

    public List<String> getBillingRejectList(String id) {
        List<String> obj = new ArrayList<String>();
        List<BillingONEAReport> reports = billingEaReportDao.findByBillingNo(Integer.parseInt(id));
        for (BillingONEAReport report : reports) {
            String error = report.getClaimError().trim();
            if (error.length() > 2) {
                String temp[] = error.split("\\s");
                for (int i = 0; i < temp.length; i++) {
                    obj.add(temp[i]);
                }
            }
            error = report.getCodeError().trim();
            if (error.length() > 1) {
                String temp[] = error.split("\\s");
                for (int i = 0; i < temp.length; i++) {
                    obj.add(temp[i]);
                }
            }
        }

        return obj;
    }

    public List<String> getBillingExplanatoryList(String id) {
        List<String> obj = new ArrayList<String>();
        List<RaDetail> rds = raDetailDao.findByBillingNo(Integer.parseInt(id));

        String tHeaderNo = "";

        for (RaDetail rad : rds) {
            if ("".equals(tHeaderNo)) {
                tHeaderNo = String.valueOf(rad.getId());
            } else if (!tHeaderNo.equals(rad.getId().toString())) {
                break;
            }
            obj.add(rad.getErrorCode());
        }

        return obj;
    }

    /**
     * @param id String the claim header ID
     * @return String the {@code total} field, or empty if not found
     */
    public String getBillingTotal(String id) {
        BillingONCHeader1 b = billingHeaderDao.find(id);
        if (b != null) {
            return String.valueOf(b.getTotal().doubleValue());
        }
        return "";

    }


    /**
     * @param id String the claim header ID
     * @return String the {@code paid} field, or empty if not found
     */
    public String getBillingPaid(String id) {
        BillingONCHeader1 b = billingHeaderDao.find(id);
        if (b != null) {
            return String.valueOf(b.getPaid().doubleValue());
        }
        return "";

    }

    /**
     * @param fee String the new total as BigDecimal-parseable string
     * @param id  String the claim header ID
     * @return boolean {@code true} on success
     */
    public boolean updateBillingTotal(String fee, String id) {
        BillingONCHeader1 b = billingHeaderDao.find(id);
        if (b != null) {
            b.setTotal(io.github.carlos_emr.carlos.billings.ca.on.BillingMoney
                    .parseNonNegativeAmount(fee, "total"));
            billingHeaderDao.merge(b);
            return true;
        }
        throw new BillingValidationException(
                "BillingOnCorrectionPersister: billing header not found for id=" + id);

    }

    /**
     * @param fee String the new paid amount as BigDecimal-parseable string
     * @param id  String the claim header ID
     * @return boolean {@code true} on success
     */
    public boolean updateBillingPaid(String fee, String id) {
        BillingONCHeader1 b = billingHeaderDao.find(id);
        if (b != null) {
            b.setPaid(io.github.carlos_emr.carlos.billings.ca.on.BillingMoney
                    .parseNonNegativeAmount(fee, "paid"));
            billingHeaderDao.merge(b);
            return true;
        }
        throw new BillingValidationException(
                "BillingOnCorrectionPersister: billing header not found for id=" + id);
    }

    /**
     * Persist one {@code billing_on_transaction} audit row recording the
     * insert of a new bill item during correction.
     *
     * @param billHeader        BillingClaimHeaderDto the parent header
     * @param billItem          BillingClaimItemDto the inserted item
     * @param updateProviderNo  String the provider performing the correction
     */
    public void addInsertOneBillItemTrans(BillingClaimHeaderDto billHeader, BillingClaimItemDto billItem, String updateProviderNo) {
        // Strict-parse header dates upfront — silently nulling on malformed
        // input persisted an audit-incorrect billing_on_transaction row.
        // Null/blank stays tolerated; malformed aborts the @Transactional
        // unit-of-work via IllegalArgumentException.
        Date admissionDate = BillingDates.parseOptionalIsoDate(billHeader.admissionDate(), "admission_date");
        Date billingDate = BillingDates.parseOptionalIsoDate(billHeader.billingDate(), "billing_date");
        BillingOnTransaction billTrans = new BillingOnTransaction();
        billTrans.setActionType(BillingOnConstants.ACTION_TYPE.C.name());
        billTrans.setAdmissionDate(admissionDate);
        billTrans.setBillingDate(billingDate);
        billTrans.setBillingNotes(billHeader.getComment());
        billTrans.setCh1Id(Integer.parseInt(billHeader.getId()));
        billTrans.setClinic(billHeader.getClinic());
        billTrans.setCreator(billHeader.getCreator());
        billTrans.setDemographicNo(Integer.parseInt(billHeader.demographicNo()));
        billTrans.setDxCode(billItem.getDx());
        billTrans.setFacilityNum(billHeader.facilityNumber());
        billTrans.setManReview(billHeader.manualReview());
        billTrans.setProviderNo(billHeader.getProviderNo());
        billTrans.setProvince(billHeader.getProvince());
        billTrans.setPayProgram(billHeader.payProgram());
        billTrans.setRefNum(billHeader.referralNumber());

        billTrans.setPaymentDate(null);
        billTrans.setPaymentId(0);
        billTrans.setPaymentType(0);

        billTrans.setServiceCode(billItem.serviceCode());
        billTrans.setServiceCodeInvoiced(billItem.getFee());
        billTrans.setServiceCodePaid(BigDecimal.ZERO);
        billTrans.setServiceCodeDiscount(BigDecimal.ZERO);
        billTrans.setServiceCodeRefund(BigDecimal.ZERO);
        billTrans.setServiceCodeNum(billItem.serviceNumber());

        billTrans.setSliCode(billHeader.getLocation());
        billTrans.setUpdateProviderNo(updateProviderNo);
        billTrans.setVisittype(billHeader.visitType());
        billTrans.setUpdateDatetime(new Timestamp(new Date().getTime()));
        billTrans.setStatus(billItem.getStatus());

        billOnTransDao.persist(billTrans);
    }

    /**
     * Persist one {@code billing_on_transaction} audit row recording the
     * update of an existing bill item during correction.
     *
     * @param billHeader        BillingClaimHeaderDto the parent header
     * @param billItem          BillingClaimItemDto the updated item
     * @param updateProviderNo  String the provider performing the correction
     */
    public void addUpdateOneBillItemTrans(BillingClaimHeaderDto billHeader, BillingClaimItemDto billItem, String updateProviderNo) {
        // Strict-parse — same reasoning as addInsertOneBillItemTrans above.
        Date admissionDate = BillingDates.parseOptionalIsoDate(billHeader.admissionDate(), "admission_date");
        Date billingDate = BillingDates.parseOptionalIsoDate(billHeader.billingDate(), "billing_date");
        BillingOnTransaction billTrans = new BillingOnTransaction();
        billTrans.setActionType(BillingOnConstants.ACTION_TYPE.U.name());
        billTrans.setAdmissionDate(admissionDate);
        billTrans.setBillingDate(billingDate);
        billTrans.setBillingNotes(billHeader.getComment());
        billTrans.setCh1Id(Integer.parseInt(billHeader.getId()));
        billTrans.setClinic(billHeader.getClinic());
        billTrans.setCreator(billHeader.getCreator());
        billTrans.setDemographicNo(Integer.parseInt(billHeader.demographicNo()));
        billTrans.setFacilityNum(billHeader.facilityNumber());
        billTrans.setManReview(billHeader.manualReview());
        billTrans.setProviderNo(billHeader.getProviderNo());
        billTrans.setProvince(billHeader.getProvince());
        billTrans.setPayProgram(billHeader.payProgram());
        billTrans.setRefNum(billHeader.referralNumber());

        billTrans.setPaymentDate(null);
        billTrans.setPaymentId(0);
        billTrans.setPaymentType(0);
        billTrans.setServiceCode(billItem.serviceCode());
        billTrans.setServiceCodeInvoiced(billItem.getFee());
        billTrans.setServiceCodeNum(billItem.serviceNumber());
        billTrans.setDxCode(billItem.getDx());
        billTrans.setStatus(billItem.getStatus());

        billTrans.setServiceCodePaid(BigDecimal.ZERO);
        billTrans.setServiceCodeDiscount(BigDecimal.ZERO);
        billTrans.setServiceCodeRefund(BigDecimal.ZERO);

        billTrans.setSliCode(billHeader.getLocation());
        billTrans.setUpdateProviderNo(updateProviderNo);
        billTrans.setVisittype(billHeader.visitType());
        billTrans.setUpdateDatetime(new Timestamp(new Date().getTime()));

        billOnTransDao.persist(billTrans);
    }

}
