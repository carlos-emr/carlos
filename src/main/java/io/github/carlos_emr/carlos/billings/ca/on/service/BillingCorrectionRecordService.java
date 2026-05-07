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
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.commn.model.BillingStatus;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.billings.ca.on.BillingDates;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
/**
 * Read+write surface for the bill-correction screen: loads the
 * correction-record graph (header + items + ext rows + explanatory /
 * rejection codes), then applies operator edits — header field updates,
 * soft-delete via {@code setInactive}, ext-key updates, and the
 * third-party item updates that drive {@code billingON3rdInv.jsp}.
 *
 * <p>Date strings flow through {@link BillingDates#parseIsoDate(String)};
 * money strings flow through {@link BillingMoney#amount(String)} so a
 * malformed value aborts the {@code @Transactional} unit-of-work rather
 * than silently writing zero.</p>
 *
 * <p>Web security is enforced at the action layer before invocation.</p>
 *
 * @since 2006-12-24
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class BillingCorrectionRecordService {
    private static final Logger _logger = MiscUtils.getLogger();

    private final BillingOnCorrectionPersister correctionPersister;
    private final BillingONCHeader1Dao cheader1Dao;
    private final BillingONItemDao billOnItemDao;
    private final BillingONExtDao billOnExtDao;
    private final BillingOnLookupService lookupService;
    private final BillingThirdPartyService thirdPartyService;
    private final ServiceCodeLoader serviceCodeLoader;
    private final BillingOnClaimPersister claimPersister;
    private final BillingOnClaimLoader claimQueryService;
    private final BillingOnTransactionDao billOnTransDao;
    private final BillingOnItemPaymentDao billOnItemPaymentDao;

    BillingCorrectionRecordService(BillingOnCorrectionPersister correctionPersister,
                          BillingONCHeader1Dao cheader1Dao,
                          BillingONItemDao billOnItemDao,
                          BillingONExtDao billOnExtDao,
                          BillingOnLookupService lookupService,
                          BillingThirdPartyService thirdPartyService,
                          ServiceCodeLoader serviceCodeLoader,
                          BillingOnClaimPersister claimPersister,
                          BillingOnClaimLoader claimQueryService,
                          BillingOnTransactionDao billOnTransDao,
                          BillingOnItemPaymentDao billOnItemPaymentDao) {
        this.correctionPersister = correctionPersister;
        this.cheader1Dao = cheader1Dao;
        this.billOnItemDao = billOnItemDao;
        this.billOnExtDao = billOnExtDao;
        this.lookupService = lookupService;
        this.thirdPartyService = thirdPartyService;
        this.serviceCodeLoader = serviceCodeLoader;
        this.claimPersister = claimPersister;
        this.claimQueryService = claimQueryService;
        this.billOnTransDao = billOnTransDao;
        this.billOnItemPaymentDao = billOnItemPaymentDao;
    }

    /**

     * Returns billing record obj.

     *

     * @param id String

     * @return List

     */

    public List getBillingRecordObj(String id) {
        List ret = correctionPersister.getBillingRecordObj(id);
        return ret;
    }

    // get error code
    /**
     * Returns billing explanatory list.
     *
     * @param id String
     * @return List
     */
    public List getBillingExplanatoryList(String id) {
        List ret = correctionPersister.getBillingExplanatoryList(id);
        return ret;
    }

    // get rejected code
    /**
     * Returns billing reject list.
     *
     * @param id String
     * @return List
     */
    public List getBillingRejectList(String id) {
        List ret = correctionPersister.getBillingRejectList(id);
        return ret;
    }

    // compare the obj first, push old record to repo if needed.
    /**
     * Updates billing claim header.
     *
     * @param ch1Obj BillingClaimHeaderDto
     * @param requestData HttpServletRequest
     * @return boolean
     */
    public boolean updateBillingClaimHeader(BillingClaimHeaderDto ch1Obj,
                                            HttpServletRequest requestData) {
        BillingClaimHeaderDto ch1DataBackup = new BillingClaimHeaderDto(ch1Obj);

        boolean ret = true;
        String status;
        if (isChangedBillingClaimHeader(ch1Obj, requestData)) {

            status = requestData.getParameter("status").substring(0, 1);
            if (status.equals("S") && !ch1Obj.getStatus().equals(status)) {
                this.updateExt("payDate", requestData);
            }
            ch1Obj = ch1Obj.withStatus(status);

            if (requestData.getParameter("status").substring(0, 1).equals("N")) {
                ch1Obj = ch1Obj.withPayProgram("NOT");
            }

            ch1Obj = ch1Obj.withReferralNumber(requestData.getParameter("rdohip"));
            ch1Obj = ch1Obj.withVisitType(requestData.getParameter("visittype"));

            ch1Obj = ch1Obj.withAdmissionDate(requestData.getParameter("xml_vdate"));
            ch1Obj = ch1Obj.withFacilityNumber(requestData.getParameter("clinic_ref_code"));
            ch1Obj = ch1Obj.withManualReview(requestData.getParameter("m_review") == null ? ""
                    : "Y");
            ch1Obj = ch1Obj.withBillingDate(requestData
                    .getParameter("xml_appointment_date"));
            ch1Obj = ch1Obj.withProviderNo(requestData.getParameter("provider_no"));
            ch1Obj = ch1Obj.withComment(requestData.getParameter("comment"));

            // provider_ohip_no update as well
            BillingProviderDto otemp = lookupService
                    .getProviderObj(requestData.getParameter("provider_no"));
            ch1Obj = ch1Obj.withProviderOhipNo(otemp.getOhipNo());
            ch1Obj = ch1Obj.withProviderRmaNo(otemp.getRmaNo());
            LoggedInInfo liCreator = LoggedInInfo.getLoggedInInfoFromSession(requestData);
            ch1Obj = ch1Obj.withCreator(liCreator != null ? liCreator.getLoggedInProviderNo() : null);

            ch1Obj = ch1Obj.withClinic(requestData.getParameter("site"));

            ch1Obj = ch1Obj.withProvince(requestData.getParameter("hc_type"));

            ch1Obj = ch1Obj.withLocation(requestData.getParameter("xml_slicode"));

            String submittedPayProgram = requestData.getParameter("payProgram");
            // "N" is the legacy "do not bill" status; persisting NOT here
            // keeps downstream third-party/private-claim checks from treating a
            // stale UI pay-program selection as still active.
            ch1Obj = ch1Obj.withPayProgram("N".equals(status) ? "NOT" : submittedPayProgram);
            ret = correctionPersister.updateBillingClaimHeader(ch1Obj);

            if (BillingONCHeader1.DELETED.equals(ch1Obj.getStatus())) {
                // Cascade soft-delete to every line item under this header.
                List<BillingONItem> billOnItems = billOnItemDao.getBillingItemByCh1Id(Integer.parseInt(ch1Obj.getId()));
                for (BillingONItem billOnItem : billOnItems) {
                    billOnItem.markDeleted();
                    billOnItemDao.merge(billOnItem);
                }
            }
        }

        // set inactive 3rd party payment record if user switched from 3rd party
        // to some other pay program
        // Re-read the normalized value from the header first so the cleanup
        // logic below uses the same pay-program that was just persisted.
        String payProgram = ch1Obj.payProgram();
        if (payProgram == null) {
            payProgram = requestData.getParameter("payProgram");
        }
        // Context: `BillingONExt` records serve as key-value pairs for extended third-party 
        // billing metadata (e.g., discounts, refunds). If a claim is reverted from third-party 
        // back to provincial (HCP/RMB/WCB), we must explicitly set these keys to inactive 
        // to prevent legacy reporting artifacts.
        if ("thirdParty".equals(requestData.getParameter("oldStatus"))
                && ("HCP".equals(payProgram) || "RMB".equals(payProgram) || "WCB"
                .equals(payProgram))) {
            setInactive(BillingONExtDao.KEY_PAYMENT, requestData);
            setInactive(BillingONExtDao.KEY_TOTAL, requestData);
            setInactive(BillingONExtDao.KEY_DISCOUNT, requestData);
            setInactive(BillingONExtDao.KEY_REFUND, requestData);
            setInactive(BillingONExtDao.KEY_PAY_DATE, requestData);
            setInactive(BillingONExtDao.KEY_CREDIT, requestData);
            setInactive("billTo", requestData);
        }

        // 3rd party elements
        if (payProgram != null && payProgram.matches(BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY)) {
            if (requestData.getParameter("payment") != null) {
                // Accumulate via `&=` so any single update3rdPartyItem failure
                // flips ret to false. Plain `=` would overwrite each prior
                // result and silently drop partial-write failures upstream.
                ret &= update3rdPartyItem(BillingONExtDao.KEY_DISCOUNT, requestData);
                ret &= update3rdPartyItem(BillingONExtDao.KEY_TOTAL, requestData);
                ret &= update3rdPartyItem(BillingONExtDao.KEY_PAYMENT, requestData);
                ret &= update3rdPartyItem(BillingONExtDao.KEY_REFUND, requestData);
                ret &= update3rdPartyItem(BillingONExtDao.KEY_PAY_DATE, requestData);
                ret &= update3rdPartyItem(BillingONExtDao.KEY_CREDIT, requestData);
                ch1Obj = ch1Obj.withPaid(requestData.getParameter("payment"));
                ret &= correctionPersister.updateBillingClaimHeader(ch1Obj);
            }

            if (requestData.getParameter("billTo") != null) {
                ret &= update3rdPartyItem("billTo", requestData);
                ch1Obj = ch1Obj.withBillto(requestData.getParameter("billTo"));
            }
        }

        if (isChangedBillingClaimHeader(ch1DataBackup, ch1Obj)) {
            // add transaction log refer to https://github.com/oscaremr/oscar/issues/233
            LoggedInInfo liTrans = LoggedInInfo.getLoggedInInfoFromSession(requestData);
            BillingOnTransaction billTrans = billOnTransDao.getUpdateCheader1TransTemplate(ch1Obj,
                    liTrans != null ? liTrans.getLoggedInProviderNo() : null);
            billOnTransDao.persist(billTrans);
        }

        return ret;
    }

    /**

     * Updates inactive.

     *

     * @param key String

     * @param request HttpServletRequest

     */

    public void setInactive(String key, HttpServletRequest request) {
        String billingNo = request.getParameter("xml_billing_no");
        thirdPartyService.updateKeyStatus(billingNo, key, BillingThirdPartyService.INACTIVE);
    }

    /*
     * Need to use billing extension table to capture data for invoices in
     * addition to 3rd party bills
     */
    public void updateExt(String key, HttpServletRequest request) {
        update3rdPartyItem(key, request);
    }

    /**

     * Updates rd party item.

     *

     * @param key String

     * @param request HttpServletRequest

     * @return boolean

     */

    public boolean update3rdPartyItem(String key, HttpServletRequest request) {
        boolean ret = true;
        String billingNo = request.getParameter("xml_billing_no");
        if (thirdPartyService.keyExists(billingNo, key)) {
            String val = request.getParameter(key);
            if (val == null && billOnExtDao.isNumberKey(key)) {
                val = "0.00";
            }
            ret = thirdPartyService.updateKeyValue(billingNo, key, val);
        } else {
            ret = thirdPartyService.add3rdBillExt(billingNo, request.getParameter("demoNo"),
                    key, request.getParameter(key));
        }
        return ret;
    }

    /**

     * Updates rd party item.

     *

     * @param request HttpServletRequest

     * @return boolean

     */

    public boolean update3rdPartyItem(HttpServletRequest request) {
        boolean ret = true;
        String billingNo = request.getParameter("xml_billing_no");
        thirdPartyService.updateKeyValue(billingNo, "payment",
                request.getParameter("payment"));
        thirdPartyService.updateKeyValue(billingNo, "refund", request.getParameter("refund"));
        return ret;
    }

    /**

     * Updates billing item.

     *

     * @param lItemObj List

     * @param request HttpServletRequest

     * @return boolean

     */

    public boolean updateBillingItem(List lItemObj, HttpServletRequest request) throws ParseException {
        boolean ret = true; // thirdPartyService.updateBillingClaimHeader(ch1Obj);
        // _logger.info("updateBillingItem(old value = ");

        BillingClaimHeaderDto ch1Obj = (BillingClaimHeaderDto) lItemObj.get(0);
        LoggedInInfo liUpdate = LoggedInInfo.getLoggedInInfoFromSession(request);
        String updateProviderNo = liUpdate != null ? liUpdate.getLoggedInProviderNo() : null;
        lItemObj.remove(0);

        List<CorrectionRow> lines = new ArrayList<>();
        String dx = request.getParameter("xml_diagnostic_detail");
        dx = dx.length() > 2 ? dx.substring(0, 3) : dx;
        String serviceDate = request.getParameter("xml_appointment_date");

        for (int i = 0; i < BillingOnConstants.FIELD_MAX_SERVICE_NUM; i++) {
            String code = request.getParameter("servicecode" + i);
            String unit = (code == null || code.isEmpty())
                    ? null : request.getParameter("billingunit" + i);
            String fee = (code == null || code.isEmpty())
                    ? null : request.getParameter("billingamount" + i);
            String status = request.getParameter("itemStatus" + i) != null ? "S" : ch1Obj.getStatus();
            lines.add(new CorrectionRow(code, unit, fee, status));
        }

        // update item first
        for (int i = 0; i < lItemObj.size(); i++) {
            BillingClaimItemDto iObj = (BillingClaimItemDto) lItemObj.get(i);
            CorrectionRow row = lines.get(i);
            BillingONItem billOnItem = changeItem(ch1Obj, iObj, updateProviderNo, dx, serviceDate,
                    row.code(), row.unit(), row.fee(), row.status());
            if (billOnItem != null) {
                // this condition indicates one service code item was changed
                iObj = iObj.withServiceCode(billOnItem.getServiceCode());
                iObj = iObj.withFee(billOnItem.getFee());
                iObj = iObj.withServiceNumber(billOnItem.getServiceCount());
            }
            _logger.info(LogSanitizer.sanitize(iObj.serviceCode()));
        }

        // add item if possible
        for (int i = 0; i < lines.size(); i++) {
            CorrectionRow row = lines.get(i);
            if (row.code() == null || row.code().isEmpty()) {
                continue;
            }
            String sUnit = row.unit();
            if (sUnit == null || sUnit.trim().isEmpty()) {
                sUnit = "1";
            }
            String sFee = row.fee();
            if (sFee == null || sFee.trim().isEmpty()) {
                sFee = getFee(sFee, sUnit, row.code(), serviceDate);
                lines.set(i, row.withFee(sFee));
            }
            ret = addItem(ch1Obj, lItemObj, updateProviderNo, dx, serviceDate,
                    row.code(), sUnit, sFee, row.status());
            _logger.info("{} lItemObj(value = {})", LogSanitizer.sanitize(row.code()), LogSanitizer.sanitize(String.valueOf(ret)));
        }

        // recalculate amount
        String newAmount = sumFees(lines.stream()
                .map(CorrectionRow::fee)
                .filter(java.util.Objects::nonNull)
                .toList());
        _logger.info(" lItemObj(newAmount = {})", LogSanitizer.sanitize(newAmount));
        updateAmount(newAmount, ch1Obj.getId(), updateProviderNo, dx);

        // update total field in billing_on_ext if pay_program is 3rd party
        if (ch1Obj.payProgram().matches(
                BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY)) {
            // BillingONExtDao billOnExtDao = SpringUtils.getBean(BillingONExtDao.class);
            if (null != billOnExtDao) {
                int billingNo = Integer.parseInt(ch1Obj.getId());
                int demographicNo = Integer
                        .parseInt(ch1Obj.demographicNo());
                BillingONExt billOnExt = billOnExtDao.getClaimExtItem(
                        billingNo, demographicNo, "payDate");
                if (billOnExt != null) {
                    // update total,provider_no payDate field has already been
                    // updated
                    billOnExtDao.setExtItem(billingNo, demographicNo, "total",
                            newAmount, billOnExt.getDateTime(), '1');
                }
            }
        }

        return ret;
    }

    // billing correction
    /**
     * Returns billing code desc.
     *
     * @param codeName String
     * @return String
     */
    public String getBillingCodeDesc(String codeName) {
        java.util.List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute> descL =
                serviceCodeLoader.getBillingCodeAttr(codeName);
        return descL.isEmpty() ? "Unknown" : descL.get(0).description();
    }

    // billing correction
    /**
     * Returns billing code desc.
     *
     * @param codeName List
     * @return Properties
     */
    public Properties getBillingCodeDesc(List codeName) {
        Properties ret = new Properties();
        for (int i = 0; i < codeName.size(); i++) {
            java.util.List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute> descL =
                    serviceCodeLoader.getBillingCodeAttr((String) codeName.get(i));
            String desc = descL.isEmpty() ? "Unknown" : descL.get(0).description();
            ret.setProperty((String) codeName.get(i), desc);
        }
        return ret;
    }

    // from billing correction
    /**
     * Returns billing claim header obj.
     *
     * @param ch1Id String
     * @return List
     */
    public List getBillingClaimHeaderObj(String ch1Id) {
        List recordObj = null;
        recordObj = getBillingRecordObj(ch1Id);
        return recordObj;
    }

    private boolean isChangedBillingClaimHeader(BillingClaimHeaderDto oldData, BillingClaimHeaderDto newData) {
        if (oldData == null || newData == null) {
            return false;
        }
        if (!java.util.Objects.equals(oldData.referralNumber(), newData.referralNumber())) return true;
        if (!java.util.Objects.equals(oldData.getProvince(), newData.getProvince())) return true;
        if (!java.util.Objects.equals(oldData.manualReview(), newData.manualReview())) return true;
        if (!java.util.Objects.equals(oldData.billingDate(), newData.billingDate())) return true;
        if (!java.util.Objects.equals(oldData.getStatus(), newData.getStatus())) return true;
        if (!java.util.Objects.equals(oldData.payProgram(), newData.payProgram())) return true;
        if (!java.util.Objects.equals(oldData.facilityNumber(), newData.facilityNumber())) return true;
        if (!java.util.Objects.equals(oldData.getProviderNo(), newData.getProviderNo())) return true;
        if (!java.util.Objects.equals(oldData.visitType(), newData.visitType())) return true;
        if (!java.util.Objects.equals(oldData.admissionDate(), newData.admissionDate())) return true;
        if (!java.util.Objects.equals(oldData.getLocation(), newData.getLocation())) return true;
        if (!java.util.Objects.equals(oldData.getComment(), newData.getComment())) return true;
        return !java.util.Objects.equals(oldData.getBillto(), newData.getBillto());
    }

    // sql - set key=value, key1=value1, ...
    private boolean isChangedBillingClaimHeader(
            BillingClaimHeaderDto existObj, HttpServletRequest request) {
        boolean ret = false;
        if (existObj == null)
            return ret;
        String temp = request.getParameter("m_review") == null ? "" : "Y";
        if (existObj.getStatus() != null
                && request.getParameter("status") != null
                && !existObj.getStatus().equals(
                request.getParameter("status").substring(0, 1)))
            MiscUtils.getLogger().debug("status");
        if (existObj.payProgram() != null
                && !existObj.payProgram().equals(
                request.getParameter("payProgram")))
            MiscUtils.getLogger().debug("payProgram");
        if (existObj.referralNumber() != null
                && !existObj.referralNumber()
                .equals(request.getParameter("rdohip")))
            MiscUtils.getLogger().debug("rdohip");
        if (existObj.visitType() != null
                && !existObj.visitType().equals(
                request.getParameter("visittype")))
            MiscUtils.getLogger().debug("visittype");
        if (existObj.admissionDate() != null
                && !existObj.admissionDate().equals(
                request.getParameter("xml_vdate")))
            MiscUtils.getLogger().debug("xml_vdate");

        if (existObj.facilityNumber() != null
                && !existObj.facilityNumber().equals(
                request.getParameter("clinic_ref_code")))
            MiscUtils.getLogger().debug("facNum:" + existObj.facilityNumber());
        if (existObj.manualReview() != null
                && !existObj.manualReview().equals(temp))
            MiscUtils.getLogger().debug(
                    "|" + existObj.manualReview() + ":temp:" + temp + "|");
        if (existObj.billingDate() != null
                && !existObj.billingDate().equals(
                request.getParameter("xml_appointment_date")))
            MiscUtils.getLogger().debug("Billing_date");
        if (existObj.getProviderNo() != null
                && !existObj.getProviderNo().equals(
                request.getParameter("provider_no")))
            MiscUtils.getLogger().debug("getProvider_no");

        if ((existObj.getStatus() != null
                && request.getParameter("status") != null && !existObj
                .getStatus().equals(
                        request.getParameter("status").substring(0, 1)))
                || (existObj.payProgram() != null && !existObj
                .payProgram().equals(
                        request.getParameter("payProgram")))
                || (existObj.referralNumber() != null && !existObj.referralNumber()
                .equals(request.getParameter("rdohip")))
                || (existObj.visitType() != null && !existObj.visitType()
                .equals(request.getParameter("visittype")))
                || (existObj.admissionDate() != null && !existObj
                .admissionDate().equals(
                        request.getParameter("xml_vdate")))
                || (existObj.facilityNumber() != null && !existObj
                .facilityNumber().equals(
                        request.getParameter("clinic_ref_code")))
                || (existObj.manualReview() != null && !existObj
                .manualReview().equals(temp))
                || (existObj.billingDate() != null && !existObj
                .billingDate().equals(
                        request.getParameter("xml_appointment_date")))
                || (existObj.getComment() != null && !existObj.getComment()
                .equals(request.getParameter("comment")))
                || (existObj.getProviderNo() != null && !existObj
                .getProviderNo().equals(
                        request.getParameter("provider_no")))
                || (existObj.getLocation() != null && !existObj.getLocation()
                .equals(request.getParameter("xml_slicode")))
                || !StringUtils.nullSafeEquals(existObj.getClinic(),
                request.getParameter("site"))
                || (existObj.getProvince() != null && !existObj.getProvince()
                .equals(request.getParameter("hc_type")))) {
            ret = true;
        }
        return ret;
    }

    private BillingONItem changeItem(BillingClaimHeaderDto ch1Obj,
                                     BillingClaimItemDto oldObj, String updateProviderNo, String sDx,
                                     String serviceDate, String serviceCode, String unit, String fee,
                                     String status) throws ParseException {
        boolean ret = true;
        if (oldObj.serviceCode().equals(serviceCode)) {
            boolean bStatusChange = false;
            String cStatus = status;
            // Status crossed the SETTLED boundary in either direction.
            if ((!BillingONItem.SETTLED.equals(oldObj.getStatus()) && BillingONItem.SETTLED.equals(cStatus))
                    || (BillingONItem.SETTLED.equals(oldObj.getStatus()) && !BillingONItem.SETTLED.equals(cStatus))) {
                bStatusChange = true;
            }

            if (!oldObj.serviceNumber().equals(unit)
                    || !oldObj.getFee().equals(fee)
                    || !oldObj.getDx().equals(sDx)
                    || !oldObj.serviceDate().equals(serviceDate)
                    || bStatusChange) {
                oldObj = oldObj.withServiceNumber(getUnit(unit));
                oldObj = oldObj.withFee(getFee(fee, getUnit(unit), serviceCode, serviceDate));
                oldObj = oldObj.withServiceDate(serviceDate);
                oldObj = oldObj.withDx(sDx);
                oldObj = oldObj.withStatus(cStatus);
                ret = correctionPersister.updateBillingOneItem(oldObj);
                if (ret) {
                    correctionPersister.addUpdateOneBillItemTrans(ch1Obj, oldObj, updateProviderNo);
                }
            }
        } else {
            oldObj = oldObj.withStatus(BillingStatus.DELETED);
            ret = correctionPersister.updateBillingOneItem(oldObj);
            // add one transaction: delete a service code
            BillingONCHeader1 billCheader1 = cheader1Dao.find(Integer.parseInt(oldObj.claimHeaderId()));
            BillingOnTransaction billTrans = newTransactionFromHeader(
                    billCheader1, BillingOnConstants.ACTION_TYPE.D.name(),
                    sDx, updateProviderNo, "D");
            billTrans.setServiceCode(oldObj.serviceCode());
            billTrans.setServiceCodeNum(oldObj.serviceNumber());
            billTrans.setServiceCodeInvoiced(oldObj.getFee());
            billOnTransDao.persist(billTrans);

            if (serviceCode != null && !serviceCode.isEmpty()) {
                // this condition indicates modification
                // persist a new service code item
                BillingONItem newBillItem = addItem(oldObj, updateProviderNo, sDx, serviceDate, serviceCode, unit, fee,
                        status);
                // add one transaction: add a service code
                billTrans = newTransactionFromHeader(
                        billCheader1, BillingOnConstants.ACTION_TYPE.C.name(),
                        sDx, updateProviderNo, status);
                billTrans.setServiceCode(serviceCode);
                billTrans.setServiceCodeNum(unit);
                billTrans.setServiceCodeInvoiced(fee);
                billOnTransDao.persist(billTrans);

                if (ch1Obj.payProgram().matches(BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY)) {
                    // get which item_payments are associated to this service code
                    List<BillingOnItemPayment> billOnItemPaymentList = billOnItemPaymentDao
                            .getAllByItemId(Integer.parseInt(oldObj.getId()));

                    // update item_payments
                    if (billOnItemPaymentList != null && billOnItemPaymentList.size() > 0) {
                        for (BillingOnItemPayment billOnItemPayment : billOnItemPaymentList) {
                            billOnItemPayment.setBillingOnItemId(newBillItem.getId());
                            billOnItemPaymentDao.merge(billOnItemPayment);
                        }
                    }
                }

                return newBillItem;
            }
        }

        return null;
    }

    /**
     * Builds a {@link BillingOnTransaction} pre-populated with the 18 fields
     * that come straight from the header — the caller fills in the four
     * service-code-specific fields (serviceCode, serviceCodeNum,
     * serviceCodeInvoiced, and any per-call overrides) and persists.
     */
    private BillingOnTransaction newTransactionFromHeader(BillingONCHeader1 billCheader1,
                                                          String actionType,
                                                          String sDx,
                                                          String updateProviderNo,
                                                          String status) throws java.text.ParseException {
        BillingOnTransaction t = new BillingOnTransaction();
        t.setActionType(actionType);
        t.setAdmissionDate(billCheader1.getAdmissionDate());
        t.setBillingDate(billCheader1.getBillingDate());
        t.setBillingNotes(billCheader1.getComment());
        t.setCh1Id(billCheader1.getId());
        t.setClinic(billCheader1.getClinic());
        t.setCreator(updateProviderNo);
        t.setDemographicNo(billCheader1.getDemographicNo());
        t.setDxCode(sDx);
        t.setFacilityNum(billCheader1.getFaciltyNum());
        t.setManReview(billCheader1.getManReview());
        t.setProviderNo(billCheader1.getProviderNo());
        t.setProvince(billCheader1.getProvince());
        t.setPayProgram(billCheader1.getPayProgram());
        t.setRefNum(billCheader1.getRefNum());
        t.setSliCode(billCheader1.getLocation());
        t.setUpdateProviderNo(updateProviderNo);
        t.setVisittype(billCheader1.getVisitType());
        t.setUpdateDatetime(new Timestamp(new Date().getTime()));
        t.setStatus(status);
        return t;
    }

    private BillingONItem addItem(BillingClaimItemDto oldObj, String updateProviderNo, String sDx,
                                  String serviceDate, String serviceCode, String unit, String fee,
                                  String status) {
        BillingONItem billOnItem = new BillingONItem();
        billOnItem.setCh1Id(Integer.parseInt(oldObj.claimHeaderId()));
        billOnItem.setDx(sDx);
        billOnItem.setServiceCode(serviceCode);
        billOnItem.setServiceCount(getUnit(unit));
        billOnItem.setFee(getFee(fee, getUnit(unit), serviceCode, serviceDate));
        billOnItem.setStatusStrict(status);
        // Strict parse — silently substituting today on parse failure would
        // record an audit-incorrect service date and mislead OHIP about when
        // the service was provided. Surface the error to the caller so the
        // surrounding @Transactional unit-of-work rolls back.
        billOnItem.setServiceDate(BillingDates.parseIsoDate(serviceDate));
        billOnItem.setRecId(oldObj.recordId());
        billOnItem.setTranscId(oldObj.transactionId());
        billOnItem.setDx1(oldObj.getDx1());
        billOnItem.setDx2(oldObj.getDx2());
        billOnItem.setLastEditDT(new Date());
        billOnItemDao.persist(billOnItem);

        return billOnItem;
    }

    private boolean addItem(BillingClaimHeaderDto ch1Obj, List lItemObj,
                            String updateProviderNo, String sDx, String serviceDate,
                            String sName, String sUnit, String sFee, String sStatus) {
        boolean ret = true;
        BillingClaimItemDto oldObj = null;
        BillingClaimItemDto newObj = null;
        for (int i = 0; i < lItemObj.size(); i++) {
            oldObj = (BillingClaimItemDto) lItemObj.get(i);
            if (sName.equals(oldObj.serviceCode())) {
                ret = false;
                break;
            }
        }
        if (ret) {
            newObj = new BillingClaimItemDto(oldObj);
            newObj = newObj.withServiceCode(sName);
            newObj = newObj.withServiceNumber(getUnit(sUnit));
            newObj = newObj.withFee(getFee(sFee, getUnit(sUnit), sName, serviceDate));
            newObj = newObj.withServiceDate(serviceDate);
            newObj = newObj.withDx(sDx);
            newObj = newObj.withStatus(sStatus);
            int i = claimPersister.addOneItemRecord(newObj);
            if (0 == i) {
                // Throw so the surrounding @Transactional rolls back.
                // Returning false silently would let the caller continue
                // to writeAmount + ext-row writes for an item that was
                // never persisted, dangling amount/ext keys against a
                // non-existent line.
                throw new IllegalStateException(
                        "addItem: claimPersister.addOneItemRecord returned 0 for service code "
                                + io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(sName));
            }
            correctionPersister.addInsertOneBillItemTrans(ch1Obj, newObj, updateProviderNo);
            lItemObj.add(newObj);
        }

        ret = true;
        return ret;
    }

    // for appt unbill; 0 - id, 1 - status
    /**
     * Returns billing no status by appt.
     *
     * @param apptNo String
     * @return List<String>
     */
    public List<String> getBillingNoStatusByAppt(String apptNo) {
        List<String> ret = correctionPersister.getBillingCH1NoStatusByAppt(apptNo);
        return ret;
    }

    /**

     * Returns billing no status by bill no.

     *

     * @param billNo String

     * @return List

     */

    public List getBillingNoStatusByBillNo(String billNo) {
        List ret = correctionPersister.getBillingCH1NoStatusByBillNo(billNo);
        return ret;
    }

    // for appt unbill;
    /**
     * Deletes billing.
     *
     * @param id String
     * @param status String
     * @param providerNo String
     * @return boolean
     */
    public boolean deleteBilling(String id, String status, String providerNo) {
        boolean ret = correctionPersister.updateBillingStatus(id, status, providerNo);
        if (BillingONCHeader1.DELETED.equals(status)) {
            // change status in billing_on_item table

            List<BillingONItem> billOnItems = billOnItemDao.getBillingItemByCh1Id(Integer.parseInt(id));
            for (BillingONItem billOnItem : billOnItems) {
                billOnItem.markDeleted();
                billOnItemDao.merge(billOnItem); // this statement can update billing_on_item table
            }
        }
        return ret;
    }

    /**

     * Returns facilty num.

     * @return List

     */

    public List getFacilty_num() {
        return lookupService.getFacilty_num();
    }

    private String sumFees(List<String> fees) {
        String ret = "";
        BigDecimal fee = BillingMoney.amount("0.00", 4);
        for (int i = 0; i < fees.size(); i++) {
            String temp = fees.get(i);
            if (temp == null || temp.isEmpty()) {
                continue;
            }
            if (temp.indexOf(".") < 0) {
                temp = temp + ".00";
            }
            BigDecimal tFee = BillingMoney.amount(temp, 4);
            fee = fee.add(tFee);
        }
        ret = fee.setScale(2, RoundingMode.HALF_UP).toString();
        return ret;
    }

    private void updateAmount(String newAmount, String claimId, String updateProviderNo, String sDx) {
        String oldTotal = correctionPersister.getBillingTotal(claimId);
        if (!newAmount.equals(oldTotal)) {
            correctionPersister.updateBillingTotal(newAmount, claimId);
        }
    }

    private String getUnit(String unit) {
        String ret = unit;
        if (!ret.matches("\\d+")) {
            ret = "1";
        }
        return ret;
    }

    private String getFee(String fee, String unit, String codeName,
                          String billReferenceDate) {
        String ret = fee;
        if (fee.length() == 0 || fee.equals(" ")) {
            BillingOnClaimLoader.FeeLookupResult feeResult =
                    claimQueryService.getCodeFeeResult(codeName, billReferenceDate);
            if (feeResult.partial()) {
                throw new BillingDataLoadException(
                        "Fee lookup failed while correcting billing item",
                        BillingDataLoadException.Phase.DAO_QUERY,
                        Map.of(
                                "serviceCode", String.valueOf(codeName),
                                "billReferenceDate", String.valueOf(billReferenceDate)));
            }
            fee = feeResult.value();
            if (fee == null) {
                throw new BillingValidationException(
                        String.format("Fee lookup returned no value for service code %s on %s"
                                + " — code may be unknown; check fee table configuration",
                                codeName, billReferenceDate));
            }
            BigDecimal bigCodeFee = new BigDecimal(fee);
            BigDecimal bigCodeUnit = new BigDecimal(unit);
            BigDecimal bigFee = bigCodeFee.multiply(bigCodeUnit);
            bigFee = bigFee.setScale(2, RoundingMode.HALF_UP);
            ret = bigFee.toString();
        }
        return ret;
    }

    /**
     * One service-line row submitted on the correction form: the service
     * code, its unit count, the fee string, and the per-row item status.
     * Replaces the four parallel {@code ArrayList<String>} columns the
     * legacy code maintained in lockstep across every populate / read /
     * mutate site in {@link #updateBillingItem}.
     */
    private record CorrectionRow(String code, String unit, String fee, String status) {
        public CorrectionRow withFee(String newFee) {
            return new CorrectionRow(code, unit, newFee, status);
        }
    }

}
