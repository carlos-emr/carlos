/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONReviewViewModel;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles {@link BillingONReviewViewModel} for {@code billingONReview.jsp}.
 *
 * <p>Encapsulates the demographic + provider DAO lookups, the diagnostic
 * description lookup, and the validation message construction that previously
 * lived in the JSP top scriptlet (lines 60-276 of the legacy file). Also
 * applies the {@code addToPatientDx} side-effect via {@link DxresearchDAO}
 * before returning the model — moving that database write out of the JSP
 * (where it had no transactional boundary, no return-status surface, and
 * raised the security-gate cost of the JSP itself).</p>
 *
 * <p>This is an incremental scaffold. The vector-driven service-code summary
 * (handled by {@link BillingReviewPrep} and friends) still lives in the JSP
 * and will move in a follow-up step.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONReviewDataAssembler {

    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final DxresearchDAO dxresearchDAO;
    private final BillingReviewPrep reviewPrep;

    public BillingONReviewDataAssembler() {
        this(SpringUtils.getBean(DemographicDao.class),
             SpringUtils.getBean(ProviderDao.class),
             SpringUtils.getBean(DxresearchDAO.class),
             new BillingReviewPrep());
    }

    BillingONReviewDataAssembler(DemographicDao demographicDao,
                                 ProviderDao providerDao,
                                 DxresearchDAO dxresearchDAO,
                                 BillingReviewPrep reviewPrep) {
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.dxresearchDAO = dxresearchDAO;
        this.reviewPrep = reviewPrep;
    }

    /**
     * Applies the {@code addToPatientDx} side-effect, if requested, and assembles
     * the view model. The side-effect runs before the read so any save error
     * propagates via the action's normal exception handling.
     *
     * @param request   the current request (parameter source)
     * @param userNo    the logged-in provider number (saved as {@code dxresearch.providerNo})
     * @return the populated view model
     */
    public BillingONReviewViewModel assemble(HttpServletRequest request, String userNo) {
        String dxCode = nullToEmpty(request.getParameter("dxCode"));
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));

        applyAddToPatientDxIfRequested(request, dxCode, demoNo, userNo);

        String dxDesc = reviewPrep.getDxDescription(dxCode);
        BillingONReviewViewModel.Builder b = BillingONReviewViewModel.builder()
                .dxCode(dxCode)
                .dxDesc(dxDesc == null ? "" : dxDesc);

        loadProvider(request, b);
        loadDemographic(demoNo, request.getParameter("DemoSex"), b);

        return b.build();
    }

    private void applyAddToPatientDxIfRequested(HttpServletRequest request,
                                                String dxCode,
                                                String demoNo,
                                                String userNo) {
        if (!"yes".equals(request.getParameter("addToPatientDx"))) {
            return;
        }
        if (demoNo.isEmpty()) {
            return;
        }
        String dxCodeMatch = nullToEmpty(request.getParameter("codeMatchToPatientDx"));
        String dxCodeAdd = dxCodeMatch.isEmpty() ? dxCode : dxCodeMatch;
        if (dxCodeAdd.isEmpty()) {
            return;
        }

        Date now = new Date();
        Dxresearch dx = new Dxresearch(
                Integer.valueOf(demoNo),
                now,
                now,
                'A',
                dxCodeAdd,
                "icd9",
                (byte) 0,
                userNo);
        dxresearchDAO.save(dx);
    }

    private void loadProvider(HttpServletRequest request, BillingONReviewViewModel.Builder b) {
        String providerView = nullToEmpty(request.getParameter("providerview"));
        String xmlProvider = request.getParameter("xml_provider");
        if (xmlProvider != null) {
            providerView = xmlProvider;
        }
        b.providerView(providerView);

        Provider p = (xmlProvider != null) ? providerDao.getProvider(xmlProvider) : null;
        if (p != null) {
            b.providerOhip(nullToEmpty(p.getOhipNo()));
            b.providerRma(nullToEmpty(p.getRmaNo()));
        }
    }

    private void loadDemographic(String demoNo, String demoSexParam, BillingONReviewViewModel.Builder b) {
        if (demoNo.isEmpty()) {
            b.demoSex(nullToEmpty(demoSexParam));
            return;
        }
        Demographic demo = demographicDao.getDemographic(demoNo);
        if (demo == null) {
            b.demoSex(nullToEmpty(demoSexParam));
            return;
        }

        StringBuilder errorMessage = new StringBuilder();
        StringBuilder warningMessage = new StringBuilder();
        String errorFlag = "";

        b.demoFirst(nullToEmpty(demo.getFirstName()))
                .demoLast(nullToEmpty(demo.getLastName()))
                .demoHin(nullToEmpty(demo.getHin()))
                .demoVer(nullToEmpty(demo.getVer()))
                .assignedProviderNo(nullToEmpty(demo.getProviderNo()))
                .patientAddress(buildPatientAddress(demo));

        String demoSex = nullToEmpty(demo.getSex());
        if ("M".equals(demoSex)) {
            demoSex = "1";
        } else if ("F".equals(demoSex)) {
            demoSex = "2";
        }
        b.demoSex(demoSex);

        String demoHcType = nullToEmpty(demo.getHcType());
        if (demoHcType.length() < 2) {
            demoHcType = "ON";
        } else {
            demoHcType = demoHcType.substring(0, 2).toUpperCase();
        }
        b.demoHcType(demoHcType);

        String demoDobYy = nullToEmpty(demo.getYearOfBirth());
        String demoDobMm = padTwo(nullToEmpty(demo.getMonthOfBirth()));
        String demoDobDd = padTwo(nullToEmpty(demo.getDateOfBirth()));
        String demoDob = demoDobYy + demoDobMm + demoDobDd;
        b.demoDobYy(demoDobYy).demoDobMm(demoDobMm).demoDobDd(demoDobDd).demoDob(demoDob);

        String referralDoctorName = "";
        String referralDoctorOhip = "";
        if (demo.getFamilyDoctor() == null) {
            referralDoctorName = "N/A";
            referralDoctorOhip = "000000";
        } else {
            referralDoctorName = nullToEmpty(SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rd"));
            referralDoctorOhip = nullToEmpty(SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rdohip"));
        }
        b.referralDoctorName(referralDoctorName).referralDoctorOhip(referralDoctorOhip);

        if (demo.getHin() == null) {
            errorFlag = "1";
            errorMessage.append("<br><div class='alert alert-danger'>Error: The patient does not have a HIN </div><br>");
        } else if (demo.getHin().isEmpty()) {
            warningMessage.append("<br><div class='alert alert-danger'>Warning: The patient does not have a HIN </div><br>");
        }
        if (!referralDoctorOhip.isEmpty() && referralDoctorOhip.length() != 6) {
            warningMessage.append("<br><div class='alert alert-danger'>Warning: the referral doctor's no is wrong. </div><br>");
        }
        if (demoDob.length() != 8) {
            errorFlag = "1";
            errorMessage.append("<br><div class='alert alert-danger'>Error: The patient does not have a valid DOB. </div><br>");
        }

        b.errorFlag(errorFlag)
                .errorMessage(errorMessage.toString())
                .warningMessage(warningMessage.toString());
    }

    private static String buildPatientAddress(Demographic demo) {
        return nullToEmpty(demo.getFirstName()) + " " + nullToEmpty(demo.getLastName()) + "\n"
                + nullToEmpty(demo.getAddress()) + "\n"
                + nullToEmpty(demo.getCity()) + ", " + nullToEmpty(demo.getProvince()) + "\n"
                + nullToEmpty(demo.getPostal()) + "\n"
                + "Tel: " + nullToEmpty(demo.getPhone());
    }

    private static String padTwo(String v) {
        return (v != null && v.length() == 1) ? "0" + v : v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
