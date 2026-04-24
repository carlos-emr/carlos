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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/ON/billingONStatus.jsp}. Enforces
 * {@code _team_billing_only r} and exposes a {@link BillingONStatusViewModel}
 * on the request at attribute {@code statusModel} so the JSP can read request
 * parameter echoes + default-value resolution via EL.
 *
 * @since 2026-04-13 (security gate)
 *        2026-04-24 (view model)
 */
public final class ViewBillingONStatus2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    private BillingONStatusViewModel statusModel;

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // _team_billing_only is a filter flag in the JSP (does the user see only
        // their team's bills?), NOT a page-access gate. The page access privilege
        // matches the companion BillingONStatusERUpdateStatus2Action: _billing.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        this.statusModel = buildStatusModel(request, loggedInInfo);
        request.setAttribute("statusModel", this.statusModel);
        return SUCCESS;
    }

    public BillingONStatusViewModel getStatusModel() {
        return statusModel;
    }

    private BillingONStatusViewModel buildStatusModel(HttpServletRequest request, LoggedInInfo loggedInInfo) {
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

        String billingForm = firstNonNull(request.getParameter("billing_form"), "-");
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
