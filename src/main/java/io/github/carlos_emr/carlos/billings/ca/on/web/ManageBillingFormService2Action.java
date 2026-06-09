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
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
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
 * Struts2 action to replace all service codes for an Ontario billing service type.
 *
 * <p>Replaces {@code dbManageBillingform_service.jsp}. Deletes all existing
 * {@link CtlBillingService} rows for the given service type, then iterates over the
 * three groups ({@code Group1}–{@code Group3}) and up to 20 service entries per group,
 * persisting a new row for each non-empty {@code group{j}_service{i}} parameter.
 *
 * @since 2026-04-05
 */
public class ManageBillingFormService2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingFormConfigurationService billingFormConfigurationService =
            SpringUtils.getBean(BillingFormConfigurationService.class);

    /**
     * Replaces all service codes for the given Ontario billing service type.
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
        String type = Objects.toString(request.getParameter("type"), "");

        if (typeid.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing typeid parameter");
            return NONE;
        }

        List<CtlBillingService> replacement = new ArrayList<>();
        // Collect parse failures so a fat-fingered order field aborts the
        // whole save instead of silently persisting serviceOrder=0 across
        // every code in the affected group (which leaves the codes
        // visually unordered with no operator banner).
        List<String> orderParseFailures = new ArrayList<>();
        for (int j = 1; j < 4; j++) {
            String groupName = Objects.toString(request.getParameter("group" + j), "");
            for (int i = 0; i < 20; i++) {
                String serviceCode = request.getParameter("group" + j + "_service" + i);
                if (serviceCode == null || serviceCode.isEmpty()) {
                    continue;
                }
                String orderStr = request.getParameter("group" + j + "_service" + i + "_order");
                int serviceOrder = 0;
                if (orderStr != null && !orderStr.isEmpty()) {
                    try {
                        serviceOrder = Integer.parseInt(orderStr);
                    } catch (NumberFormatException e) {
                        MiscUtils.getLogger().warn(
                                "Invalid serviceOrder value [{}] for group{}_service{} — aborting save",
                                LogSafe.sanitize(orderStr), j, i, e);
                        orderParseFailures.add("group" + j + "_service" + i + "=" + orderStr);
                        continue;
                    }
                }

                CtlBillingService cbs = new CtlBillingService();
                cbs.setServiceTypeName(type);
                cbs.setServiceType(typeid);
                cbs.setServiceCode(serviceCode);
                cbs.setServiceGroupName(groupName);
                cbs.setServiceGroup("Group" + j);
                cbs.setStatus("A");
                cbs.setServiceOrder(serviceOrder);
                replacement.add(cbs);
            }
        }

        if (!orderParseFailures.isEmpty()) {
            // Fail before replaceServiceCodes is called: persisting half
            // the form (the parseable rows) would corrupt the group's
            // ordering as silently as the prior zero-default did.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid service order: " + String.join(", ", orderParseFailures));
            return NONE;
        }

        try {
            billingFormConfigurationService.replaceServiceCodes(typeid, replacement);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to replace service codes for typeid={} — transaction rolled back",
                    LogSafe.sanitize(typeid), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update service codes");
            return NONE;
        }

        response.sendRedirect(request.getContextPath() + "/billing/CA/ON/ManageBillingform");
        return NONE;
    }
}
