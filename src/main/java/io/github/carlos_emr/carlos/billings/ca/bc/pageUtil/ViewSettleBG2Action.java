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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Conditional-POST gate for {@code billing/CA/BC/settleBG.jsp}. The JSP has
 * three states driven by the {@code settled} request parameter:
 * <ul>
 *   <li>{@code settled} absent  — initial form display (GET allowed)</li>
 *   <li>{@code settled=false}   — mutation trigger: {@code MSPReconcile.settleBGBills()}
 *       runs, then forwards with {@code settled=true}. Requires POST.</li>
 *   <li>{@code settled=true}    — result display (GET allowed)</li>
 * </ul>
 * Enforces {@code _billing} w privilege for all entries. When the request
 * would trigger the DB mutation, HTTP method must be POST; otherwise 405.
 *
 * @since 2026-04-13
 */
public final class ViewSettleBG2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Enforces the gate contract (privilege and POST-only where applicable) and
     * forwards to the JSP configured as the Struts {@code success} result. See the
     * class-level Javadoc for the exact privilege requirement.
     *
     * @return the Struts result string
     * @throws Exception if the underlying Struts framework signals an error
     * @since 2026-04-13
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // Only the settled=false branch mutates; require POST for that branch only
        // so the initial form display and the post-mutation result page still work via GET.
        if ("false".equals(request.getParameter("settled"))
                && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        return SUCCESS;
    }
}
