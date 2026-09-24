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
package io.github.carlos_emr.carlos.prescript.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code StaticScript2.jsp} (formerly reachable at
 * {@code /oscarRx/StaticScript2.jsp}). Renders a read-only static prescription
 * view identified by {@code regionalIdentifier}/{@code cn} query parameters.
 * Enforces {@code _rx} read privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/rx/} location. The page lists, and offers to re-prescribe, the saved drugs
 * of the patient named by {@code demographicNo}, so that patient is also authorised here
 * (patient-level {@code _rx} read and chart access) before the JSP opens Rx for them.
 *
 * @since 2026-04-13
 */
public final class ViewStaticScript2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        // The JSP activates and renders the requested patient's Rx. Global _rx read alone must not
        // let a caller pick any patient by URL: authorise the named patient first. A request that
        // names no valid patient is left to the JSP, which refuses it without rendering anything.
        int demographicNo = RxSessionBeanResolver.requestedDemographicNo(request);
        if (demographicNo > 0
                && (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", demographicNo)
                    || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo))) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        return SUCCESS;
    }
}
