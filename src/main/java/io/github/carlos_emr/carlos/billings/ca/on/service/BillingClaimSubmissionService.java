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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingServiceLine;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
/**
 * Orchestrates the save side of OHIP claim entry: turns a request payload into
 * a {@code BillingONCHeader1} + {@code BillingONItem} graph, persists it via
 * {@link BillingOnClaimPersister}, optionally creates a private-bill
 * {@code BillingONExt} payment record, and updates the source appointment's
 * billing status. {@link #addBillingRecord(BillingClaimSubmission)} is the typed entry point
 * and returns {@link SaveResult} so the caller can distinguish "saved with id"
 * from "rejected".
 *
 * <p>Web security is enforced at the action layer before invocation; this
 * service trusts the caller to have run {@code SecurityInfoManager.hasPrivilege}.
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class BillingClaimSubmissionService {
    private static final Logger _logger = MiscUtils.getLogger();
    private final BillingOnClaimPersister claimPersister;
    private final BillingOnLookupService lookupService;

    BillingClaimSubmissionService(BillingOnClaimPersister claimPersister, BillingOnLookupService lookupService) {
        this.claimPersister = claimPersister;
        this.lookupService = lookupService;
    }

    public record SaveResult(boolean saved, int billingId) {}

    /**
     * Typed wrapper around the {@code [BillingClaimHeaderDto, List<BillingClaimItemDto>]}
     * 2-tuple this service has historically passed around as {@code ArrayList}. Defends
     * the invariants the loose tuple did not: non-null header, defensively-copied items.
     *
     * <p>The legacy {@code ArrayList}-shaped methods remain on this service for
     * back-compat with existing callers; new code should construct a
     * {@code BillingClaimSubmission} directly via
     * {@link #getSubmission(jakarta.servlet.http.HttpServletRequest)} and call the
     * record-typed overloads.</p>
     */
    public record BillingClaimSubmission(BillingClaimHeaderDto header,
                                         List<BillingClaimItemDto> items) {
        public BillingClaimSubmission {
            java.util.Objects.requireNonNull(header, "header");
            items = items == null ? List.of() : List.copyOf(items);
        }

        /** Convert to the legacy ArrayList shape consumed by the deeper persister. */
        @SuppressWarnings({"rawtypes", "unchecked"})
        public ArrayList toLegacyArrayList() {
            ArrayList legacy = new ArrayList();
            legacy.add(header);
            legacy.add(new ArrayList<>(items));
            return legacy;
        }

        /** Lift the legacy ArrayList shape into the typed record. */
        @SuppressWarnings({"rawtypes", "unchecked"})
        public static BillingClaimSubmission fromLegacy(ArrayList claimEnvelope) {
            return new BillingClaimSubmission(
                    (BillingClaimHeaderDto) claimEnvelope.get(0),
                    claimEnvelope.size() > 1 ? (List<BillingClaimItemDto>) claimEnvelope.get(1) : List.of());
        }
    }

    /** Typed-record alternative to {@link #getBillingClaimObj}; preferred for new code. */
    public BillingClaimSubmission getSubmission(HttpServletRequest requestData) {
        return BillingClaimSubmission.fromLegacy(getBillingClaimObj(requestData));
    }

    /** Typed-record alternative to {@link #getBillingClaimHospObj}; preferred for new code. */
    public BillingClaimSubmission getHospitalSubmission(HttpServletRequest requestData,
                                                        String serviceDate,
                                                        String total,
                                                        List<BillingServiceLine> lines) {
        return BillingClaimSubmission.fromLegacy(
                getBillingClaimHospObj(requestData, serviceDate, total, lines));
    }

    /**
     * Persists one typed claim header and its item rows.
     */
    public SaveResult addBillingRecord(BillingClaimSubmission submission) {
        return addABillingRecord(submission.toLegacyArrayList());
    }

    /**
     * Persists one claim header and its item rows.
     *
     * @param val legacy envelope containing {@link BillingClaimHeaderDto} at
     *            index 0 and a {@code List<BillingClaimItemDto>} at index 1
     * @return save status and generated billing id
     */
    @SuppressWarnings("rawtypes")
    SaveResult addABillingRecord(ArrayList val) {
        BillingClaimHeaderDto claim1Obj = (BillingClaimHeaderDto) val.get(0);
        int billingNo = claimPersister.addOneClaimHeaderRecord(claim1Obj);
        if (billingNo == 0)
            return new SaveResult(false, 0);
        claim1Obj = claim1Obj.withId(Integer.toString(billingNo));
        val.set(0, claim1Obj);
        if (val.size() > 1) {
            claimPersister.addItemRecord((List) val.get(1), billingNo);
            return new SaveResult(true, billingNo);
        } else {
            _logger.error("No billing item for billing # " + billingNo);
        }

        return new SaveResult(false, billingNo);
    }

    /**
     * Persists the third-party/private-bill extension row for an existing
     * billing record using request parameters as the source.
     */
    public boolean addPrivateBillExtRecord(HttpServletRequest requestData, int billingId) {
        boolean ret = false;
        Map<String, String> val = getPrivateBillExtObj(requestData);
        ret = claimPersister.add3rdBillExt(val, billingId);
        if (!ret)
            _logger.error("addPrivateBillExtRecord " + billingId);

        return ret;
    }

    /**
     * Persists the third-party/private-bill extension row and updates the
     * claim envelope with private billing values required by downstream
     * callers.
     */
    boolean addPrivateBillExtRecord(HttpServletRequest requestData, ArrayList claimEnvelope, int billingId) {
        boolean ret = false;

        Map<String, String> val = getPrivateBillExtObj(requestData);
        ret = claimPersister.add3rdBillExt(val, billingId, claimEnvelope);
        if (!ret)
            _logger.error("addPrivateBillExtRecord " + billingId);

        return ret;
    }


    /** Creates the OHIP invoice transaction row for a saved claim envelope. */
    @SuppressWarnings("unchecked")
    void addOhipInvoiceTrans(ArrayList claimEnvelope) {
        claimPersister.addCreateOhipInvoiceTrans((BillingClaimHeaderDto) claimEnvelope.get(0), (List<BillingClaimItemDto>) claimEnvelope.get(1));
    }

    /**
     * Record-typed atomic save of header, items, third-party/OHIP transaction,
     * and payee extension data. Preferred for new callers because it defends
     * the header/items envelope invariants before delegating to the legacy
     * envelope overload.
     */
    public SaveResult saveBillingWithExtAndPayee(BillingClaimSubmission submission,
                                                 HttpServletRequest requestData,
                                                 String xmlBillType,
                                                 String payeeValue) {
        return saveBillingWithExtAndPayee(submission.toLegacyArrayList(),
                requestData, xmlBillType, payeeValue);
    }

    /**
     * Atomic save of header, items, third-party/OHIP transaction, and payee ext.
     * All four writes run inside one Spring transaction; any throw rolls
     * the entire save back, preventing orphan {@code billing_on_cheader1}
     * rows with missing payee keys.
     *
     * @param claimEnvelope      header + items pre-built via {@link #getBillingClaimObj}
     * @param requestData live request — needed by the third-party-ext path
     * @param xmlBillType raw {@code xml_billtype} param; selects 3rd-party vs OHIP path
     * @param payeeValue  user-entered payee name (may be empty)
     * @return SaveResult mirroring {@link #addABillingRecord}'s contract
     * @throws BillingValidationException if any sub-write fails — entire tx rolls back
     */
    @SuppressWarnings("rawtypes")
    SaveResult saveBillingWithExtAndPayee(ArrayList claimEnvelope,
                                          HttpServletRequest requestData,
                                          String xmlBillType,
                                          String payeeValue) {
        SaveResult headerResult = addABillingRecord(claimEnvelope);
        if (!headerResult.saved()) {
            return headerResult;
        }
        int billingNo = headerResult.billingId();

        if (xmlBillType != null
                && xmlBillType.length() >= 3
                && xmlBillType.substring(0, 3).matches(BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY)) {
            boolean extOk = addPrivateBillExtRecord(requestData, claimEnvelope, billingNo);
            if (!extOk) {
                throw new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException(
                        "Save rejected: third-party ext write failed; transaction rolled back");
            }
        } else {
            addOhipInvoiceTrans(claimEnvelope);
        }

        if (payeeValue != null) {
            boolean payeeOk = claimPersister.persistPayeeExt(billingNo, payeeValue);
            if (!payeeOk) {
                throw new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException(
                        "Save rejected: payee ext write failed; transaction rolled back");
            }
        }

        return headerResult;
    }

    /** Updates the source appointment's billing status after claim save/delete work. */
    public boolean updateApptStatus(String apptNo, String status, String userNo) {
        return lookupService.updateApptStatus(apptNo, status, userNo);
    }

    // get appt status
    public String getApptStatus(String apptNo) {
        return lookupService.getApptStatus(apptNo);
    }

    // ret - ArrayList claimheader1data, itemdata
    ArrayList getBillingClaimObj(HttpServletRequest requestData) {
        ArrayList ret = new ArrayList();
        BillingClaimHeaderDto claim1Header = getClaimHeader1Obj(requestData);
        ret.add(claim1Header);
        BillingClaimItemDto[] itemData = getItemObj(requestData);

        List aL = new ArrayList();
        for (int i = 0; i < itemData.length; i++) {
            aL.add(itemData[i]);
        }
        ret.add(aL);
        return ret;
    }

    // ret - ArrayList claimheader1data, itemdata
    ArrayList getBillingClaimHospObj(HttpServletRequest requestData, String service_date, String total,
                                     List<BillingServiceLine> lines) {
        ArrayList ret = new ArrayList();
        BillingClaimHeaderDto claim1Header = getClaimHeader1HospObj(requestData, service_date, total);
        ret.add(claim1Header);
        BillingClaimItemDto[] itemData = getItemHospObj(requestData, lines, service_date);

        List aL = new ArrayList();
        for (int i = 0; i < itemData.length; i++) {
            aL.add(itemData[i]);
        }
        ret.add(aL);
        return ret;
    }


    private BillingClaimHeaderDto getClaimHeader1Obj(HttpServletRequest val) {
        String billtype = val.getParameter("xml_billtype");

        BillingClaimHeaderDto claim1Header = new BillingClaimHeaderDto();

        claim1Header = claim1Header.withTransactionId(BillingOnConstants.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        claim1Header = claim1Header.withRecordId(BillingOnConstants.CLAIMHEADER1_REORDIDENTIFICATION);

        if (!billtype.substring(0, 3).equals("BON")) {
            claim1Header = claim1Header.withHin(val.getParameter("hin"));
            claim1Header = claim1Header.withVer(val.getParameter("ver"));
            claim1Header = claim1Header.withDob(val.getParameter("demographic_dob"));
            claim1Header = claim1Header.withAppointmentNo(val.getParameter("appointment_no"));
            claim1Header = claim1Header.withDemographicName(val.getParameter("demographic_name"));
            String temp[] = getPatientLF(val.getParameter("demographic_name"));
            claim1Header = claim1Header.withLastName(temp[0]);
            claim1Header = claim1Header.withFirstName(temp[1]);
            claim1Header = claim1Header.withSex(val.getParameter("sex"));
            claim1Header = claim1Header.withProvince(val.getParameter("hc_type"));
        } else {
            claim1Header = claim1Header.withHin("");
            claim1Header = claim1Header.withVer("");
            claim1Header = claim1Header.withDob("");
            claim1Header = claim1Header.withAppointmentNo("");
            claim1Header = claim1Header.withDemographicName("");
            claim1Header = claim1Header.withLastName("");
            claim1Header = claim1Header.withFirstName("");
            claim1Header = claim1Header.withSex("");
            claim1Header = claim1Header.withProvince("ON");
        }

        // acc_num - billing no
        claim1Header = claim1Header.withPayProgram(getPayProgram(val.getParameter("xml_billtype"), val.getParameter("hc_type")));
        claim1Header = claim1Header.withPayee(val.getParameter("payMethod") != null ? val.getParameter("payMethod")
                : BillingOnConstants.CLAIMHEADER1_PAYEE);
        claim1Header = claim1Header.withReferralNumber(val.getParameter("referralCode"));

        claim1Header = claim1Header.withFacilityNumber(val.getParameter("xml_location").substring(0, 4));
        claim1Header = claim1Header.withAdmissionDate(val.getParameter("xml_vdate"));

        claim1Header = claim1Header.withReferringLabNumber("");
        claim1Header = claim1Header.withManualReview(val.getParameter("m_review") != null ? val.getParameter("m_review") : "");

        if (val.getParameter("xml_slicode") != null) {
            claim1Header = claim1Header.withLocation(val.getParameter("xml_slicode").trim());
        }

        claim1Header = claim1Header.withDemographicNo(val.getParameter("demographic_no"));
        claim1Header = claim1Header.withProviderNo(val.getParameter("xml_provider").substring(0,
                val.getParameter("xml_provider").indexOf("|")));

        claim1Header = claim1Header.withBillingDate(val.getParameter("service_date"));
        claim1Header = claim1Header.withBillingTime(val.getParameter("start_time"));
        claim1Header = claim1Header.withUpdateDateTime(UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"));
        claim1Header = claim1Header.withTotal(val.getParameter("total"));
        String paid = "";
        if (val.getParameter("submit").equalsIgnoreCase("Settle")) {
            paid = val.getParameter("total");
        } else if (val.getParameter("submit").equalsIgnoreCase("Save & Print Invoice")
                || val.getParameter("submit").equalsIgnoreCase("Settle & Print Invoice")
                || val.getParameter("submit").equalsIgnoreCase("Save")
                || val.getParameter("submit").equalsIgnoreCase("Save & Add Another Bill")) {
            paid = val.getParameter("total_payment");
        }
        claim1Header = claim1Header.withPaid(paid);
        claim1Header = claim1Header.withStatus(getStatus(val.getParameter("submit"), val.getParameter("xml_billtype")));
        claim1Header = claim1Header.withComment(val.getParameter("comment") != null ? val.getParameter("comment") : "");
        claim1Header = claim1Header.withVisitType(val.getParameter("xml_visittype").substring(0, 2));
        claim1Header = claim1Header.withProviderOhipNo(val.getParameter("xml_provider").substring(
                val.getParameter("xml_provider").indexOf("|") + 1));
        claim1Header = claim1Header.withProviderRmaNo("");
        claim1Header = claim1Header.withAppointmentProviderNo(val.getParameter("apptProvider_no"));
        claim1Header = claim1Header.withAssistantProviderNo("");
        claim1Header = claim1Header.withCreator((String) val.getSession().getAttribute("user"));

        claim1Header = claim1Header.withClinic(val.getParameter("site"));

        return claim1Header;
    }

    private BillingClaimItemDto[] getItemObj(HttpServletRequest val) {
        int itemNum = Integer.parseInt(val.getParameter("totalItem"));
        BillingClaimItemDto[] claimItem = new BillingClaimItemDto[itemNum];
        // _logger.info("No billing item for billing # " + itemNum);

        for (int i = 0; i < itemNum; i++) {
            claimItem[i] = new BillingClaimItemDto();
            claimItem[i] = claimItem[i].withTransactionId(BillingOnConstants.ITEM_TRANSACTIONIDENTIFIER);
            claimItem[i] = claimItem[i].withRecordId(BillingOnConstants.ITEM_REORDIDENTIFICATION);
            claimItem[i] = claimItem[i].withServiceCode(val.getParameter("xserviceCode_" + i));
            if (val.getParameter("xsliCode_" + i) != null) {
                claimItem[i] = claimItem[i].withLocation(val.getParameter("xsliCode_" + i));
            }
            claimItem[i] = claimItem[i].withFee(val.getParameter("percCodeSubtotal_" + i));
            claimItem[i] = claimItem[i].withServiceNumber(getDefaultUnit(val.getParameter("xserviceUnit_" + i)));
            claimItem[i] = claimItem[i].withServiceDate(val.getParameter("service_date"));
            claimItem[i] = claimItem[i].withDx(val.getParameter("dxCode"));
            claimItem[i] = claimItem[i].withDx1(val.getParameter("dxCode1"));
            claimItem[i] = claimItem[i].withDx2(val.getParameter("dxCode2"));
            if (val.getParameter("paid_" + i) != null) {
                claimItem[i] = claimItem[i].withPaid(val.getParameter("paid_" + i));
            } else {
                claimItem[i] = claimItem[i].withPaid("0.00");
            }
            //claimItem[i] = claimItem[i].withRefund(val.getParameter("refund"));
            if (val.getParameter("discount_" + i) != null) {
                claimItem[i] = claimItem[i].withDiscount(val.getParameter("discount_" + i));
            } else {
                claimItem[i] = claimItem[i].withDiscount("0.00");
            }
            if (val.getParameter("xml_billtype").substring(0, 3).matches(BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY)) {
                claimItem[i] = claimItem[i].withStatus("P");
            } else {
                claimItem[i] = claimItem[i].withStatus("O");
            }
        }

        return claimItem;
    }

    private BillingClaimHeaderDto getClaimHeader1HospObj(HttpServletRequest val, String service_date, String total) {
        BillingClaimHeaderDto claim1Header = new BillingClaimHeaderDto();

        claim1Header = claim1Header.withTransactionId(BillingOnConstants.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        claim1Header = claim1Header.withRecordId(BillingOnConstants.CLAIMHEADER1_REORDIDENTIFICATION);
        String hin = getHinVer(val.getParameter("hin"))[0];
        String ver = getHinVer(val.getParameter("hin"))[1];
        claim1Header = claim1Header.withHin(hin);
        claim1Header = claim1Header.withVer(ver);

        claim1Header = claim1Header.withDob(val.getParameter("demographic_dob"));
        // acc_num - billing no
        claim1Header = claim1Header.withPayProgram(getPayProgram(val.getParameter("xml_billtype"), val.getParameter("hc_type")));
        claim1Header = claim1Header.withPayee(val.getParameter("payMethod") != null ? val.getParameter("payMethod")
                : BillingOnConstants.CLAIMHEADER1_PAYEE);
        claim1Header = claim1Header.withReferralNumber(val.getParameter("referralCode"));

        claim1Header = claim1Header.withFacilityNumber(val.getParameter("xml_location").substring(0, 4));
        claim1Header = claim1Header.withAdmissionDate(val.getParameter("xml_vdate"));

        claim1Header = claim1Header.withReferringLabNumber("");
        claim1Header = claim1Header.withManualReview("");

        claim1Header = claim1Header.withLocation(val.getParameter("xml_slicode").trim());

        claim1Header = claim1Header.withDemographicNo(val.getParameter("demographic_no"));
        if (IsPropertiesOn.isMultisitesEnable()) {
            claim1Header = claim1Header.withProviderNo(val.getParameter("xml_provider").substring(0, val.getParameter("xml_provider").indexOf("|")));
        } else {
            claim1Header = claim1Header.withProviderNo(val.getParameter("xml_provider"));
        }

        claim1Header = claim1Header.withAppointmentNo(val.getParameter("appointment_no"));
        claim1Header = claim1Header.withDemographicName(val.getParameter("demographic_name"));
        String temp[] = getPatientLF(val.getParameter("demographic_name"));
        claim1Header = claim1Header.withLastName(temp[0]);
        claim1Header = claim1Header.withFirstName(temp[1]);
        claim1Header = claim1Header.withSex(val.getParameter("sex"));
        claim1Header = claim1Header.withProvince(val.getParameter("hc_type"));

        claim1Header = claim1Header.withBillingDate(service_date);
        claim1Header = claim1Header.withBillingTime(val.getParameter("start_time"));
        claim1Header = claim1Header.withUpdateDateTime(UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"));
        claim1Header = claim1Header.withTotal(total);
        claim1Header = claim1Header.withPaid("");
        claim1Header = claim1Header.withStatus(getStatus("", val.getParameter("xml_billtype")));
        claim1Header = claim1Header.withComment(val.getParameter("comment") != null ? val.getParameter("comment") : "");
        claim1Header = claim1Header.withVisitType(val.getParameter("xml_visittype").substring(0, 2));
        claim1Header = claim1Header.withProviderOhipNo(val.getParameter("proOHIPNO"));
        claim1Header = claim1Header.withProviderRmaNo("");
        claim1Header = claim1Header.withAppointmentProviderNo(val.getParameter("apptProvider_no"));
        claim1Header = claim1Header.withAssistantProviderNo("");
        claim1Header = claim1Header.withCreator((String) val.getSession().getAttribute("user"));
        claim1Header = claim1Header.withClinic(val.getParameter("site"));

        return claim1Header;
    }

    private BillingClaimItemDto[] getItemHospObj(HttpServletRequest val, List<BillingServiceLine> lines,
                                             String service_date) {
        BillingClaimItemDto[] claimItem = new BillingClaimItemDto[lines.size()];

        for (int i = 0; i < lines.size(); i++) { // recordCount
            BillingServiceLine line = lines.get(i);
            BigDecimal bdEachPrice = BillingMoney.amount(line.price());
            BigDecimal bdEachUnit = BillingMoney.amount(line.unit());
            BigDecimal bdEachTotal = bdEachPrice.multiply(bdEachUnit).setScale(2, RoundingMode.HALF_UP);

            claimItem[i] = new BillingClaimItemDto();
            claimItem[i] = claimItem[i].withTransactionId(BillingOnConstants.ITEM_TRANSACTIONIDENTIFIER);
            claimItem[i] = claimItem[i].withRecordId(BillingOnConstants.ITEM_REORDIDENTIFICATION);

            claimItem[i] = claimItem[i].withServiceCode(line.code());
            claimItem[i] = claimItem[i].withFee("" + bdEachTotal);
            claimItem[i] = claimItem[i].withServiceNumber(getDefaultUnit(line.unit()));
            claimItem[i] = claimItem[i].withServiceDate(service_date);
            claimItem[i] = claimItem[i].withDx(getDefaultSpace(val.getParameter("dxCode")));
            claimItem[i] = claimItem[i].withDx1(getDefaultSpace(val.getParameter("dxCode1")));
            claimItem[i] = claimItem[i].withDx2(getDefaultSpace(val.getParameter("dxCode2")));
            claimItem[i] = claimItem[i].withPaid(getDefaultSpace(val.getParameter("payment")));
            claimItem[i] = claimItem[i].withRefund(getDefaultSpace(val.getParameter("refund")));
            claimItem[i] = claimItem[i].withDiscount(getDefaultSpace(val.getParameter("discount")));
            claimItem[i] = claimItem[i].withStatus("O");
        }
        return claimItem;
    }

    private Map getPrivateBillExtObj(HttpServletRequest val) {
        Map<String, String> valsMap = new HashMap<String, String>();
        valsMap.put("demographic_no", val.getParameter("demographic_no"));
        valsMap.put("billTo", val.getParameter("billto"));
        valsMap.put("total_discount", val.getParameter("total_discount"));
        valsMap.put("remitTo", val.getParameter("remitto"));
        valsMap.put("total", val.getParameter("gstBilledTotal"));
        if (val.getParameter("submit").equalsIgnoreCase("Settle & Print Invoice")) {
            valsMap.put("total_payment", val.getParameter("total_payment"));
        } else {
            valsMap.put("total_payment", val.getParameter("total_payment"));
        }
        valsMap.put("refund", val.getParameter("refund"));
        valsMap.put("provider_no", val.getParameter("provider_no"));
        valsMap.put("gst", val.getParameter("gst"));

        if (val.getParameter("payMethod") != null) {
            valsMap.put("payMethod", val.getParameter("payMethod"));
        } else {
            valsMap.put("payMethod", "1");
        }
        return valsMap;
    }

    // HCP/WCB/RMB/NOT/PAT/...
    private String getPayProgram(String val, String hcType) {
        String ret = val.substring(0, 3);
        if (val.startsWith("PAT")) {
            ret = BillingOnConstants.CLAIMHEADER1_PAYMENTPROGRAM_PRIVATE;
        } else if (val.startsWith("ODP")) {
            ret = hcType.equals("ON") ? "HCP" : "RMB";
        } else if (val.startsWith("BON")) {
            ret = "HCP";
        }
        return ret;
    }

    private String getStatus(String submit, String payProg) {
        String ret = "O";
        if (submit.startsWith("Settle")) {
            ret = "S";
        } else if (payProg.startsWith("NOT")) {
            ret = "N";
        } else if (payProg.startsWith("BON")) {
            ret = "I";
        } else if (payProg.startsWith("PAT")) {
            ret = "P";
        } else if (payProg.startsWith("WCB")) {
            ret = "W";
        }
        return ret;
    }

    // 1-last name 9, 2-first name 5
    private String[] getPatientLF(String val) {
        String[] ret = new String[2];
        if (val.indexOf(",") >= 0) {
            ret = val.split(",");
            ret[0] = ret[0].replaceAll("\\W", "");
            ret[0] = ret[0].length() > 9 ? ret[0].substring(0, 9) : ret[0];
            ret[1] = ret[1].replaceAll("\\W", "");
            ret[1] = ret[1].length() > 5 ? ret[1].substring(0, 5) : ret[1];
        }

        return ret;
    }

    // 1-default
    private String getDefaultUnit(String val) {
        String ret = "".equals(val) ? "1" : val;
        return ret;
    }

    // ""-default
    private String getDefaultSpace(String val) {
        String ret = val == null ? "" : val;
        return ret;
    }

    // 1-hin 2-ver
    private String[] getHinVer(String val) {
        String[] ret = {"", ""};
        if (val != null) {
            for (int i = 0; i < val.length(); i++) {
                if (("" + val.charAt(i)).matches("\\d")) {
                    ret[0] += val.charAt(i);
                } else {
                    ret[1] += val.charAt(i);
                }
            }
        }
        return ret;
    }

}
