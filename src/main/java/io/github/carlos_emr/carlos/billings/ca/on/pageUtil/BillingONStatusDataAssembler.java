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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONStatusViewModel;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles {@link BillingONStatusViewModel} for {@code billingONStatus.jsp}.
 *
 * <p>Extracted from {@link ViewBillingONStatus2Action} so the action stays a
 * thin gate (security check + assembler invocation) and the parameter-echo +
 * default-resolution logic is testable in isolation. Mirrors the
 * {@link BillingONReviewDataAssembler} / {@link BillingShortcutPg1DataAssembler}
 * shape: production no-arg ctor + package-private mock-injection ctor +
 * {@link #assemble(HttpServletRequest, LoggedInInfo)}.</p>
 *
 * @since 2026-04-25
 */
public final class BillingONStatusDataAssembler {

    private final SecurityInfoManager securityInfoManager;

    /**
     * Production constructor used by Struts; resolves dependencies from the
     * Spring context via {@link SpringUtils#getBean}. Tests use the
     * package-private constructor below to inject mocks directly.
     */
    public BillingONStatusDataAssembler() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    BillingONStatusDataAssembler(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Builds the status-page view model from request parameters and the
     * logged-in user's privilege flags. Pure read; no side effects.
     */
    public BillingONStatusViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        boolean teamBillingOnly = securityInfoManager
                .hasPrivilege(loggedInInfo, "_team_billing_only", "r", null);
        boolean siteAccessPrivacy = securityInfoManager
                .hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null);

        CarlosProperties props = CarlosProperties.getInstance();
        boolean hideName = Boolean.parseBoolean(
                props.getProperty("invoice_reports.print.hide_name", "false"));

        String[] billTypeParam = request.getParameterValues("billType");
        boolean search = billTypeParam != null && billTypeParam.length > 0;
        String[] billTypes = search
                ? billTypeParam
                : BillingONStatusViewModel.DEFAULT_BILL_TYPES.toArray(new String[0]);

        String statusType = firstNonNull(request.getParameter("statusType"), "O");
        String demoNo = firstNonNull(request.getParameter("demographicNo"), "");
        if ("_".equals(statusType)) {
            demoNo = "";
        }
        String filename = demoNo;

        String startDate = firstNonEmpty(request.getParameter("xml_vdate"),
                DateUtils.sumDate("yyyy-MM-dd", "-180"));
        String endDate = firstNonEmpty(request.getParameter("xml_appointment_date"),
                DateUtils.sumDate("yyyy-MM-dd", "0"));

        String providerNo = firstNonNull(request.getParameter("providerview"), "");
        String providerOhipNo = firstNonNull(request.getParameter("provider_ohipNo"), "");
        String raCode = firstNonNull(request.getParameter("raCode"), "");
        String claimNo = firstNonNull(request.getParameter("claimNo"), "");
        String dx = firstNonNull(request.getParameter("dx"), "");
        String visitType = firstNonNull(request.getParameter("visitType"), "-");

        String serviceCode = request.getParameter("serviceCode");
        if (serviceCode == null || serviceCode.isEmpty()) {
            serviceCode = "%";
        }

        // Legacy "any billing form" sentinel is three dashes; a single "-" is a
        // real value in some installations and would mis-filter the search.
        String billingForm = firstNonNull(request.getParameter("billing_form"), "---");
        String visitLocation = firstNonNull(request.getParameter("xml_location"), "");
        String selectedSite = request.getParameter("site");
        String sortName = firstNonNull(request.getParameter("sortName"), "ServiceDate");
        String sortOrder = firstNonNull(request.getParameter("sortOrder"), "asc");
        String paymentStartDate = firstNonNull(request.getParameter("paymentStartDate"), "");
        String paymentEndDate = firstNonNull(request.getParameter("paymentEndDate"), "");

        return BillingONStatusViewModel.builder()
                .teamBillingOnly(teamBillingOnly)
                .siteAccessPrivacy(siteAccessPrivacy)
                .multisites(IsPropertiesOn.isMultisitesEnable())
                .hideName(hideName)
                .search(search)
                .billTypes(billTypes)
                .statusType(statusType)
                .demoNo(demoNo)
                .filename(filename)
                .startDate(startDate)
                .endDate(endDate)
                .providerNo(providerNo)
                .providerOhipNo(providerOhipNo)
                .raCode(raCode)
                .claimNo(claimNo)
                .dx(dx)
                .visitType(visitType)
                .serviceCode(serviceCode)
                .billingForm(billingForm)
                .visitLocation(visitLocation)
                .selectedSite(selectedSite)
                .sortName(sortName)
                .sortOrder(sortOrder)
                .paymentStartDate(paymentStartDate)
                .paymentEndDate(paymentEndDate)
                .build();
    }

    private static String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private static String firstNonEmpty(String primary, String fallback) {
        return primary != null && !primary.isEmpty() ? primary : fallback;
    }
}
