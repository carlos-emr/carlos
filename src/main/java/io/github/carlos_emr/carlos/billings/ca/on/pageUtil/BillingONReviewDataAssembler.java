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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONReviewViewModel;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles {@link BillingONReviewViewModel} for {@code billingONReview.jsp}.
 *
 * <p>Pure read of request state into a view model. Encapsulates the
 * demographic + provider DAO lookups, the diagnostic description lookup,
 * and the validation message construction that previously lived in the
 * JSP's top scriptlet block.</p>
 *
 * <p>The optional {@code addToPatientDx} clinical write that used to live
 * here was extracted to {@link BillingONReviewDxPersister} so this class
 * stays read-only and easier to test in isolation.</p>
 *
 * <p>The vector-driven service-code summary (handled by
 * {@link BillingReviewPrep} and friends) is currently consumed inline by
 * the JSP rather than through this assembler.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONReviewDataAssembler {

    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final BillingReviewPrep reviewPrep;

    /**
     * Production constructor used by Struts; resolves dependencies from the
     * Spring context via {@link SpringUtils#getBean}. Tests use the
     * package-private constructor below to inject mocks directly without
     * standing up a Spring context.
     */
    public BillingONReviewDataAssembler() {
        this(SpringUtils.getBean(DemographicDao.class),
             SpringUtils.getBean(ProviderDao.class),
             new BillingReviewPrep());
    }

    BillingONReviewDataAssembler(DemographicDao demographicDao,
                                 ProviderDao providerDao,
                                 BillingReviewPrep reviewPrep) {
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.reviewPrep = reviewPrep;
    }

    /**
     * Pure read: assembles the view model from request state. The optional
     * {@code addToPatientDx} clinical write is no longer the assembler's
     * responsibility — see {@link BillingONReviewDxPersister}, which the
     * action runs before this method.
     *
     * @param request the current request (parameter source)
     * @return the populated view model
     */
    public BillingONReviewViewModel assemble(HttpServletRequest request) {
        String dxCode = nullToEmpty(request.getParameter("dxCode"));
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));

        String dxDesc = reviewPrep.getDxDescription(dxCode);
        BillingONReviewViewModel.Builder b = BillingONReviewViewModel.builder()
                .dxCode(dxCode)
                .dxDesc(dxDesc == null ? "" : dxDesc);

        loadProvider(request, b);
        loadDemographic(demoNo, request.getParameter("DemoSex"), b);

        return b.build();
    }

    private void loadProvider(HttpServletRequest request, BillingONReviewViewModel.Builder b) {
        // xml_provider carries "providerNo|ohipNo" from the picker; strip the
        // suffix before passing to the DAO. See BillingONRequestParams.
        String providerNo = BillingONRequestParams.extractProviderNo(
                request.getParameter("xml_provider"),
                request.getParameter("providerview"));
        b.providerView(providerNo);

        Provider p = providerNo.isEmpty() ? null : providerDao.getProvider(providerNo);
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
