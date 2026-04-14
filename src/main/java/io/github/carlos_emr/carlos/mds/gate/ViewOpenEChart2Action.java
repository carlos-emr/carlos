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
package io.github.carlos_emr.carlos.mds.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code oscarMDS/OpenEChart.jsp}. Enforces {@code _lab}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/oscarMDS/} location. Used by {@code SearchPatient2Action}
 * and {@code PatientMatch2Action} as the post-match redirect target so those
 * actions do not redirect to themselves. Part of the oscarMDS security-
 * hardening migration (2Action gate pattern from #1109, #1629, #1632, #1644,
 * #1662, #1663, #1665, #1666, #1667, #1668).
 *
 * <p>HTTP method is not enforced; only {@code _lab} read privilege is required.
 *
 * @since 2026-04-14
 */
public final class ViewOpenEChart2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)) {
            throw new SecurityException("missing required sec object: _lab");
        }

        return SUCCESS;
    }
}
