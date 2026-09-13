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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Conditional-POST gate for {@code lab/CA/BC/index}. The JSP's scriptlet
 * performs DB mutations (linkDao.merge/persist or LabRequestReportLink save/update/delete) when a submit/action parameter is present. Enforces
 * {@code _lab} w privilege always, plus POST-only when the mutation
 * trigger is supplied (CSRF hardening). GET without trigger params is
 * allowed so the initial form renders via popup/navigation.
 *
 * @since 2026-04-13
 */
public final class ViewLabBcIndex2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        // Require POST when mutation-trigger params are present. index.jsp
        // writes Hl7Link rows when chk[] is submitted or when the demo_id/pid
        // pair is supplied (post-back from demo_select popup), so those are
        // the real CSRF-relevant triggers in addition to conventional names.
        boolean hasMutationTrigger = request.getParameter("submit") != null
                || request.getParameter("action") != null
                || request.getParameter("method") != null
                || request.getParameter("linkChoice") != null
                || request.getParameter("buttonAction") != null
                || request.getParameterValues("chk") != null
                || (request.getParameter("demo_id") != null && request.getParameter("pid") != null);
        if (hasMutationTrigger && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        return SUCCESS;
    }
}
