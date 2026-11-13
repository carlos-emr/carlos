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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code rx/ShowPreviousPrints.jsp} (formerly reachable at
 * {@code /rx/ShowPreviousPrints.jsp}). Enforces {@code _rx} {@code r}
 * privilege before forwarding to the JSP at its {@code /WEB-INF/jsp/rx/}
 * location. Part of the oscarRx -> rx rebrand migration.
 *
 * @since 2026-04-13
 */
public final class ViewShowPreviousPrints2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Admits the request to its view only with global {@code _rx} read and, when the request names a
     * patient ({@code demographicNo} / {@code demographic_no}), the same privilege for that patient plus access to
     * the patient's record ({@link RxRequestedPatientAccess#require}). A malformed or conflicting patient is refused.
     *
     * @return {@code success} to render the view
     * @throws SecurityException when the caller may not view the module or the named patient
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        // The JSP renders the patient the request names; authorise that patient too (#3875).
        RxRequestedPatientAccess.require(securityInfoManager, loggedInInfo, request, "_rx", "r");

        return SUCCESS;
    }
}
