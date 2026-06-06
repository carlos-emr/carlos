/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFormConfigurationService;
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action to replace the diagnostic codes for an Ontario billing service type.
 *
 * <p>Replaces {@code dbManageBillingform_dx.jsp}. Deletes all existing
 * {@link CtlDiagCode} rows for the given service type, then iterates over the 45
 * {@code diagcode0}–{@code diagcode44} request parameters and persists a new entry
 * for each non-empty value.
 *
 * @since 2026-04-05
 */
public class ManageBillingFormDiag2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingFormConfigurationService billingFormConfigurationService =
            SpringUtils.getBean(BillingFormConfigurationService.class);

    /**
     * Replaces all diagnostic codes for the given Ontario billing service type.
     *
     * @return {@link #NONE} after redirecting, or if the request method is not POST
     * @throws SecurityException if the user lacks {@code _admin.billing} write privilege
     */
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    @Override
    public String execute() throws Exception {
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        String typeid = Objects.toString(request.getParameter("typeid"), "");

        if (typeid.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing typeid parameter");
            return NONE;
        }

        List<CtlDiagCode> replacement = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            String diagcode = request.getParameter("diagcode" + i);
            if (diagcode != null && !diagcode.isEmpty()) {
                CtlDiagCode cdc = new CtlDiagCode();
                cdc.setServiceType(typeid);
                cdc.setDiagnosticCode(diagcode);
                cdc.setStatus("A");
                replacement.add(cdc);
            }
        }

        try {
            billingFormConfigurationService.replaceDiagCodes(typeid, replacement);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to replace diagnostic codes for typeid={} — transaction rolled back", LogSafe.sanitize(typeid), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update diagnostic codes");
            return NONE;
        }

        response.sendRedirect(request.getContextPath() + "/billing/CA/ON/ManageBillingform");
        return NONE;
    }
}
