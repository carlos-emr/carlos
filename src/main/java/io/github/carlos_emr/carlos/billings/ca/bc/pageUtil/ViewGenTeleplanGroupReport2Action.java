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
 * Mutation gate for {@code billing/CA/BC/genTeleplanGroupReport.jsp}. The JSP
 * performs {@code billActivityDao.persist()} calls when generating the group
 * report in a scriptlet. Enforces POST-only AND {@code _report} {@code r}
 * privilege (matching the JSP's original {@code security:oscarSec} taglib,
 * which accepts {@code _report}, {@code _admin.reporting}, or {@code _admin}
 * at read level) before forwarding to the JSP. GET requests return 405.
 * <p>
 * Class name retains the {@code View...} prefix for consistency with
 * sibling gate actions in this migration; behavior is mutation-gate.
 *
 * @since 2026-04-13
 */
public final class ViewGenTeleplanGroupReport2Action extends ActionSupport {

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

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // JSP taglib allows _report OR _admin.reporting OR _admin (read). The JSP also performs
        // billActivityDao.persist(); we keep POST-only for CSRF protection but match the JSP's
        // documented read-privilege OR chain rather than escalating to _admin.billing w.
        boolean hasReport = securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null);
        boolean hasAdminReporting = securityInfoManager.hasPrivilege(loggedInInfo, "_admin.reporting", "r", null);
        boolean hasAdmin = securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null);
        if (!hasReport && !hasAdminReporting && !hasAdmin) {
            throw new SecurityException("missing required sec object (_report, _admin.reporting, or _admin)");
        }

        return SUCCESS;
    }
}
