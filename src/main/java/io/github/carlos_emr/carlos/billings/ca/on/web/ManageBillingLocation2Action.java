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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.data.ManageBillingLocationViewModel;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Gate for {@code billing/CA/ON/manageBillingLocation.jsp}. Enforces {@code _admin.billing}
 * {@code w} privilege. When the form arrives with {@code submit=Delete} the
 * action removes the named clinic location (POST-only). The clinic-location
 * list is then resolved server-side and exposed as
 * {@code ${manageLocationModel}} so the JSP body can render pure EL/JSTL.
 *
 * @since 2026-04-13
 */
public final class ManageBillingLocation2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private ClinicLocationDao clinicLocationDao = SpringUtils.getBean(ClinicLocationDao.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        if ("Delete".equals(request.getParameter("submit"))
                && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // Migrated from the legacy JSP scriptlet:
        //     if (request.getParameter("submit") != null
        //         && request.getParameter("submit").equals("Delete")) {
        //         clinicLocationDao.removeByClinicLocationNo(...);
        //     }
        // Performing the mutation here keeps it inside the privilege +
        // method gate above instead of running on every JSP render.
        if ("Delete".equals(request.getParameter("submit"))) {
            String locationNo = request.getParameter("location_no");
            if (locationNo != null && !locationNo.isEmpty()) {
                clinicLocationDao.removeByClinicLocationNo(locationNo);
            }
        }

        // Mirror the legacy fallbacks: defaultView from CarlosProperties
        // when no billingform parameter is supplied, and empty-string
        // defaults for the two echoed parameters.
        String defaultView = CarlosProperties.getInstance().getProperty("default_view", "");
        String billingform = request.getParameter("billingform");
        String reportAction = request.getParameter("reportAction");

        ManageBillingLocationViewModel model = ManageBillingLocationViewModel.builder()
                .locations(clinicLocationDao.findByClinicNo(1))
                .defaultView(defaultView)
                .selectedClinicView(billingform != null ? billingform : defaultView)
                .reportAction(reportAction != null ? reportAction : "")
                .build();
        request.setAttribute("manageLocationModel", model);

        return SUCCESS;
    }
}
