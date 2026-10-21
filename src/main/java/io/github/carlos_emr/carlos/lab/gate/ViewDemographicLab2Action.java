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
package io.github.carlos_emr.carlos.lab.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.lab.service.LabPatientAccess;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code lab/DemographicLab.jsp}. Enforces {@code _lab}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/lab/} location. Part of the lab module
 * security-hardening migration (2Action gate pattern from #1109, #1629,
 * #1632, #1644, #1662, #1663, #1665, #1666).
 *
 * @since 2026-04-13
 */
public final class ViewDemographicLab2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String demographic = request.getParameter("demographicNo");
        if (demographic != null && !demographic.isEmpty() && !"0".equals(demographic)) {
            Integer demographicNo = LabPatientAccess.positiveIdentity(demographic);
            if (demographicNo == null) {
                ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            LabPatientAccess.requirePatient(securityInfoManager, loggedInInfo, demographicNo);
        }

        return SUCCESS;
    }
}
