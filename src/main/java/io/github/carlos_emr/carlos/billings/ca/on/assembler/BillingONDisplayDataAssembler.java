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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONDisplayViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService;
import io.github.carlos_emr.carlos.commn.dao.BillingONErrorCodeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONErrorCode;
import io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao;
import io.github.carlos_emr.carlos.commn.model.ClinicNbr;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService;
import io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingONDisplay2Action;

/**
 * Assembles {@link BillingONDisplayViewModel} for {@code billingONDisplay.jsp}
 * (the "Billing Correction" form). Extracted from the previously bare
 * {@link ViewBillingONDisplay2Action} so the action stays a thin gate
 * (security check + assembler invocation) and the parameter-resolution +
 * DB-fan-out logic is testable in isolation. Mirrors the
 * {@link BillingONStatusDataAssembler} shape.
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingONDisplayDataAssembler {

    private final ClinicNbrDao clinicNbrDao;
    private final BillingCorrectionRecordService prep;
    private final BillingONErrorCodeDao errorCodeDao;
    private final BillingONLookupService lookupService;

    public BillingONDisplayDataAssembler(ClinicNbrDao clinicNbrDao,
                                  BillingCorrectionRecordService prep,
                                  BillingONErrorCodeDao errorCodeDao,
                                  BillingONLookupService lookupService) {
        this.clinicNbrDao = clinicNbrDao;
        this.prep = prep;
        this.errorCodeDao = errorCodeDao;
        this.lookupService = lookupService;
    }

    /**
     * Builds the display-page view model from request parameters and the
     * logged-in user's privilege flags. Pure read; no side effects on
     * persisted state.
     */
    public BillingONDisplayViewModel assemble(HttpServletRequest request,
                                              LoggedInInfo loggedInInfo) {
        String rawBillingNo = request.getParameter("billing_no");
        String billingNo = rawBillingNo == null ? "" : rawBillingNo.trim();
        boolean billPresent = !billingNo.isEmpty();

        BillingClaimHeader1Data ch1Obj = new BillingClaimHeader1Data();
        @SuppressWarnings("rawtypes")
        List recordObj = null;
        if (billPresent) {
            recordObj = prep.getBillingRecordObj(billingNo);
            if (recordObj != null && !recordObj.isEmpty()) {
                ch1Obj = (BillingClaimHeader1Data) recordObj.get(0);
            }
        }

        // Resolve header fields (mirrors the original scriptlet)
        String updateDate = "";
        String demoNo = "";
        String demoName = "";
        String demoDOB = "";
        String demoSex = "";
        String hin = "";
        String location = "";
        String billDate = "";
        String provider = "";
        String billType = "";
        String payProgram = "";
        String visitDate = "";
        String visitType = "";
        String hcType = "";
        String hcSex = "";
        String mReview = "";
        String comment = "";
        String rDoctorOhip = "";

        if (billPresent && recordObj != null && !recordObj.isEmpty()) {
            updateDate = ch1Obj.getUpdate_datetime();
            demoNo = ch1Obj.getDemographic_no();
            demoName = ch1Obj.getDemographic_name();
            demoDOB = ch1Obj.getDob();
            String sex = ch1Obj.getSex();
            demoSex = "1".equals(sex) ? "M" : "F";
            hin = nullToEmpty(ch1Obj.getHin()) + nullToEmpty(ch1Obj.getVer());
            location = ch1Obj.getFacilty_num();
            billDate = ch1Obj.getBilling_date();
            provider = ch1Obj.getProviderNo();
            billType = ch1Obj.getStatus();
            payProgram = ch1Obj.getPay_program();
            visitDate = ch1Obj.getAdmission_date();
            visitType = ch1Obj.getVisittype();
            hcType = ch1Obj.getProvince();
            hcSex = ch1Obj.getSex();
            rDoctorOhip = ch1Obj.getRef_num();
            mReview = ch1Obj.getMan_review();
            comment = ch1Obj.getComment();
        }

        // Error / reject codes
        List<BillingONDisplayViewModel.ErrorCode> errorCodes = new ArrayList<>();
        if (billPresent) {
            @SuppressWarnings("rawtypes")
            List lReject = prep.getBillingRejectList(billingNo);
            @SuppressWarnings("rawtypes")
            List lError = prep.getBillingExplanatoryList(billingNo);
            if (lError != null) {
                @SuppressWarnings("unchecked")
                List<Object> mergedError = new ArrayList<>(lError);
                if (lReject != null) {
                    mergedError.addAll(lReject);
                }
                for (Object raw : mergedError) {
                    String codeNo = raw == null ? "" : raw.toString();
                    if (codeNo.isEmpty()) {
                        continue;
                    }
                    BillingONErrorCode found = errorCodeDao.find(codeNo);
                    String desc = found == null ? "Unknown" : found.getDescription();
                    errorCodes.add(new BillingONDisplayViewModel.ErrorCode(codeNo, desc));
                }
            }
        }

        // Payment types (BillingDataHlp.vecPaymentType is value, label, value, label, ...)
        List<BillingONDisplayViewModel.PaymentTypeOption> paymentTypes = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        List<?> vec = BillingDataHlp.vecPaymentType;
        if (vec != null) {
            for (int i = 0; i + 1 < vec.size(); i += 2) {
                String value = stringValue(vec.get(i));
                String label = stringValue(vec.get(i + 1));
                paymentTypes.add(new BillingONDisplayViewModel.PaymentTypeOption(value, label));
            }
        }

        // Locations / facility numbers (returned as flat list: number, label, number, label, ...)
        List<BillingONDisplayViewModel.LocationOption> locations = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        List lLocation = prep.getFacilty_num();
        if (lLocation != null) {
            for (int i = 0; i + 1 < lLocation.size(); i += 2) {
                String number = stringValue(lLocation.get(i));
                String label = stringValue(lLocation.get(i + 1));
                locations.add(new BillingONDisplayViewModel.LocationOption(number, label));
            }
        }

        // Provider list (pipe-delimited entries: provider_no | last | first | ohip_no)
        List<BillingONDisplayViewModel.ProviderOption> providers = new ArrayList<>();
        List<String> pList = lookupService.getCurProviderStr();
        if (pList != null) {
            for (String entry : pList) {
                String[] parts = entry == null ? new String[0] : entry.split("\\|", -1);
                String pNo = parts.length > 0 ? parts[0] : "";
                String last = parts.length > 1 ? parts[1] : "";
                String first = parts.length > 2 ? parts[2] : "";
                providers.add(new BillingONDisplayViewModel.ProviderOption(pNo, last, first));
            }
        }

        // Clinic numbers (rma_enabled visit-type list)
        boolean rmaEnabled = CarlosProperties.getInstance()
                .getBooleanProperty("rma_enabled", "true");
        List<BillingONDisplayViewModel.ClinicNbrOption> clinicNbrs = new ArrayList<>();
        if (rmaEnabled && clinicNbrDao != null) {
            ArrayList<ClinicNbr> nbrs = clinicNbrDao.findAll();
            if (nbrs != null) {
                for (ClinicNbr clinic : nbrs) {
                    String value = nullToEmpty(clinic.getNbrValue());
                    String label = nullToEmpty(clinic.getNbrString());
                    String valueString = String.format("%s | %s", value, label);
                    clinicNbrs.add(new BillingONDisplayViewModel.ClinicNbrOption(value, valueString));
                }
            }
        }

        // Service rows: one row per existing item plus a single trailing empty row
        List<BillingONDisplayViewModel.ServiceItemRow> serviceRows = new ArrayList<>();
        String diagCode = "";
        int rowCount = 0;
        if (billPresent && recordObj != null && recordObj.size() > 1) {
            for (int i = 1; i < recordObj.size(); i++) {
                BillingItemData itemObj = (BillingItemData) recordObj.get(i);
                String serviceCode = nullToEmpty(itemObj.getService_code());
                String serviceDesc = prep.getBillingCodeDesc(serviceCode);
                String billAmount = nullToEmpty(itemObj.getFee());
                String dx = nullToEmpty(itemObj.getDx());
                String unit = nullToEmpty(itemObj.getSer_num());
                boolean settled = "S".equals(itemObj.getStatus());
                rowCount++;
                serviceRows.add(new BillingONDisplayViewModel.ServiceItemRow(
                        rowCount, serviceCode, nullToEmpty(serviceDesc),
                        billAmount, unit, dx, settled));
                diagCode = dx;
            }
        }
        // Trailing empty row index (1-based) — legacy increments rowCount once
        // before rendering the trailing row so the form names use rowCount-1.
        int trailingRowIndex = rowCount + 1;

        // Request param echoes (none consumed at render-time on this JSP today,
        // but the map is kept for future-proof parity with other display models).
        Map<String, String> echoes = new HashMap<>();
        echoes.put("billing_no", billingNo);

        return BillingONDisplayViewModel.builder()
                .billPresent(billPresent)
                .rmaEnabled(rmaEnabled)
                .billingNo(billingNo)
                .updateDate(updateDate)
                .demoNo(demoNo)
                .demoName(demoName)
                .demoDOB(demoDOB)
                .demoSex(demoSex)
                .hcType(hcType)
                .hcSex(hcSex)
                .hin(hin)
                .location(location)
                .billDate(billDate)
                .provider(provider)
                .billType(billType)
                .payProgram(payProgram)
                .visitDate(visitDate)
                .visitType(visitType)
                .mReview(mReview)
                .comment(comment)
                .rDoctor("")
                .rDoctorOhip(rDoctorOhip)
                .diagCode(diagCode)
                .errorCodes(errorCodes)
                .paymentTypes(paymentTypes)
                .locations(locations)
                .providers(providers)
                .clinicNbrs(clinicNbrs)
                .serviceRows(serviceRows)
                .trailingRowIndex(trailingRowIndex)
                .requestParamEchoes(echoes)
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String stringValue(Object o) {
        return o == null ? "" : o.toString();
    }
}
