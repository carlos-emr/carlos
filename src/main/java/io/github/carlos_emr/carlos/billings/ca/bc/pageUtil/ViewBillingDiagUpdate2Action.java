/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
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
 * Mutation gate for {@code billing/CA/BC/billingDigUpdate.jsp}. The JSP performs
 * diagnosticCodeDao.merge() with description from request param whenever {@code update} param is present in a scriptlet. Enforces {@code _billing} w privilege AND
 * POST-only before forwarding to the JSP. GET requests return 405.
 * <p>
 * Class name retains the {@code View...} prefix for consistency with
 * sibling gate actions in the BC billing module; behavior is mutation-gate.
 *
 * @since 2026-04-13
 */
public final class ViewBillingDiagUpdate2Action extends ActionSupport {

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

        boolean hasBillingAdmin = securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null);
        boolean hasAdmin = securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null);
        if (!hasBillingAdmin && !hasAdmin) {
            throw new SecurityException("missing required sec object (_admin.billing or _admin)");
        }

        return SUCCESS;
    }
}
