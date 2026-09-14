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

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingCodeData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Mutation gate for deleting a private billing service code. Replaces the
 * scriptlet previously in {@code billing/CA/BC/deletePrivateCode.jsp}.
 * <p>
 * The JSP was reachable at the public path with only a taglib privilege check.
 * This action enforces POST-only AND {@code _admin.billing} or {@code _admin}
 * write privilege (OR, matching the legacy taglib {@code
 * objectName="_admin.billing,_admin"}) before invoking
 * {@link BillingCodeData#deleteBillingCode(String)}, then redirects to the
 * private code admin page via the Struts {@code success} result.
 *
 * @since 2026-04-13
 */
public final class DeletePrivateCode2Action extends ActionSupport {

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

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        boolean hasBillingAdmin = securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null);
        boolean hasAdmin = securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null);
        if (!hasBillingAdmin && !hasAdmin) {
            throw new SecurityException("missing required sec object (_admin.billing or _admin)");
        }

        String serviceCode = request.getParameter("code");
        if (serviceCode == null || serviceCode.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "code is required");
            return NONE;
        }

        final String normalizedServiceCode = serviceCode.trim();
        final int billingServiceId;
        try {
            billingServiceId = Integer.parseInt(normalizedServiceCode);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "code must be numeric");
            return NONE;
        }

        new BillingCodeData().deleteBillingCode(billingServiceId);

        return SUCCESS;
    }
}
