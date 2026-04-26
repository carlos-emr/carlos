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

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingReportCenterViewModel;
import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ReportProvider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/ON/billingReportCenter.jsp}. Enforces {@code _report}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location. Created as part of the ON billing migration
 * to gate direct-access paths behind Struts2 actions (same pattern as
 * PR #1632 for BC billing).
 *
 * <p>Admin / doctor roles are redirected to the new-report dashboard to
 * preserve the legacy {@code request.getRequestDispatcher("...").include}
 * behaviour the JSP body used to perform inline.</p>
 *
 * <p>Resolves the provider-list select rows + the three echoed parameters
 * ({@code xml_vdate}, {@code xml_appointment_date}, {@code providerview})
 * into a {@link BillingReportCenterViewModel} so the JSP body renders pure
 * EL/JSTL with no inline DAO lookups or parameter scriptlets.</p>
 *
 * @since 2026-04-13
 */
public final class ViewBillingReportCenter2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private ReportProviderDao reportProviderDao = SpringUtils.getBean(ReportProviderDao.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        // Mirror the legacy admin/doctor early-redirect to the new-report
        // dashboard. The original JSP did this via
        // request.getRequestDispatcher("...").include before any other body
        // rendering; we replace it with a struts result so the redirect
        // happens before forwarding to the JSP at all.
        String userRole = (String) request.getSession().getAttribute("userrole");
        if (userRole != null && (userRole.indexOf("admin") >= 0 || userRole.indexOf("doctor") >= 0)) {
            return "newReport";
        }

        // Provider rows from the legacy "billingreport" report scope.
        List<BillingReportCenterViewModel.ProviderRow> rows = new ArrayList<>();
        List<Object[]> joined = reportProviderDao.search_reportprovider("billingreport");
        if (joined != null) {
            for (Object[] res : joined) {
                Provider p = (Provider) res[1];
                rows.add(new BillingReportCenterViewModel.ProviderRow(
                        p.getProviderNo(), p.getFirstName(), p.getLastName()));
            }
        }

        BillingReportCenterViewModel model = BillingReportCenterViewModel.builder()
                .providerRows(rows)
                .selectedProviderView(request.getParameter("providerview"))
                .xmlVdate(request.getParameter("xml_vdate"))
                .xmlAppointmentDate(request.getParameter("xml_appointment_date"))
                .build();

        request.setAttribute("reportCenterModel", model);

        return SUCCESS;
    }
}
