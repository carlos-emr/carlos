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

import io.github.carlos_emr.carlos.lab.service.PathnetPatientLinkService;
import io.github.carlos_emr.carlos.lab.service.PathnetPatientLinkService.Selection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Conditional-POST gate for {@code lab/CA/BC/index}. An explicit POST links the selected patient through a transactional service. Enforces
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

        boolean hasMutationTrigger = hasMutationTrigger(request);
        if (hasMutationTrigger && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if (hasMutationTrigger) {
            try {
                linkSelectedPatients(request, loggedInInfo.getLoggedInProviderNo());
            } catch (IllegalArgumentException _) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Select a valid lab and patient");
                return NONE;
            }
        }
        return SUCCESS;
    }

    /** Recognizes both explicit picker submissions and checkbox confirmations. */
    private static boolean hasMutationTrigger(HttpServletRequest request) {
        return request.getParameter("submit") != null
                || request.getParameter("action") != null
                || request.getParameter("method") != null
                || request.getParameter("linkChoice") != null
                || request.getParameter("buttonAction") != null
                || request.getParameterValues("chk") != null
                || (request.getParameter("demo_id") != null && request.getParameter("pid") != null);
    }

    private static void linkSelectedPatients(HttpServletRequest request, String providerNo) {
        var service = SpringUtils.getBean(PathnetPatientLinkService.class);
        var selections = new java.util.ArrayList<Selection>();
        String pid = request.getParameter("pid");
        String demographic = request.getParameter("demo_id");
        if (pid != null && !pid.isBlank() && demographic != null && !demographic.isBlank()) {
            selections.add(new Selection(
                    parseIdentifier(pid), parseIdentifier(demographic)));
        }
        String[] checked = request.getParameterValues("chk");
        if (checked != null) {
            for (String id : checked) selections.add(new Selection(
                    parseIdentifier(id), null));
        }
        service.linkAll(selections, providerNo);
    }

    private static int parseIdentifier(String value) {
        int id = Integer.parseInt(value);
        if (id <= 0) throw new IllegalArgumentException("Identifier must be positive");
        return id;
    }
}
