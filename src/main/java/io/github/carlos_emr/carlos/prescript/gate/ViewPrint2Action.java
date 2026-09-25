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
 * View gate for {@code rx/Print.jsp} (formerly reachable at
 * {@code /rx/Print.jsp}). Enforces {@code _rx} {@code r}
 * privilege before forwarding to the JSP at its {@code /WEB-INF/jsp/rx/}
 * location. Part of the oscarRx -> rx rebrand migration.
 *
 * @since 2026-04-13
 */
public final class ViewPrint2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Admits the Rx patient chooser (Print.jsp) with global {@code _rx} read. The chooser needs no open Rx
     * patient; when the request does name one, that patient is authorised as in the other Rx view gates.
     *
     * @return {@code success} to render the chooser
     * @throws SecurityException when the caller may not use Rx or the named patient
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        // The chooser does not render the active Rx patient. Authorise only a patient explicitly
        // named by this request, without letting malformed identifiers bypass that check.
        int demographicNo = RxSessionBeanResolver.requestedDemographicNo(request);
        if (demographicNo == RxSessionBeanResolver.INVALID) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        if (demographicNo > 0) {
            RxRequestedPatientAccess.requirePatient(securityInfoManager, loggedInInfo, demographicNo, "_rx", "r");
        }

        return SUCCESS;
    }
}
