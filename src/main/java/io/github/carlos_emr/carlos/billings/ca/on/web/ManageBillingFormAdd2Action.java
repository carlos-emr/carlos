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
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action to add a new Ontario billing form service type.
 *
 * <p>Replaces {@code dbManageBillingform_add.jsp}. Validates that required fields are
 * non-empty, checks for duplicate service type IDs, then persists three
 * {@link CtlBillingService} entries (one per group), a {@link CtlDiagCode} seed entry,
 * and optionally a {@link CtlBillingType}. On validation failure the browser is
 * redirected back to {@code manageBillingform.jsp} with error parameters in the URL.
 *
 * @since 2026-04-05
 */
public class ManageBillingFormAdd2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    private BillingFormConfigurationService billingFormConfigurationService =
            SpringUtils.getBean(BillingFormConfigurationService.class);

    /**
     * Creates a new Ontario billing service type with three groups, a diagnostic code
     * seed, and optionally a bill type entry.
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
        String group1 = Objects.toString(request.getParameter("group1"), "");
        String group2 = Objects.toString(request.getParameter("group2"), "");
        String group3 = Objects.toString(request.getParameter("group3"), "");
        String billtype = Objects.toString(request.getParameter("billtype"), "");

        // Validate required fields
        if (type.isEmpty() || group1.isEmpty() || group2.isEmpty() || group3.isEmpty()) {
            String errMessage = "Error: Type Description, Groups Description must be entered.";
            redirectWithError(errMessage, typeid, type, group1, group2, group3);
            return NONE;
        }

        // Check for duplicate service type ID
        if (ctlBillingServiceDao.findByServiceTypeId(typeid).size() > 0) {
            String errMessage = "Error: Service Type ID '" + typeid + "' already exists.";
            redirectWithError(errMessage, typeid, type, group1, group2, group3);
            return NONE;
        }

        List<CtlBillingService> services = new ArrayList<>();
        String[] groupNames = { group1, group2, group3 };
        for (int i = 0; i < groupNames.length; i++) {
            CtlBillingService cbs = new CtlBillingService();
            cbs.setServiceTypeName(type);
            cbs.setServiceType(typeid);
            cbs.setServiceCode("A007A");
            cbs.setServiceGroupName(groupNames[i]);
            cbs.setServiceGroup("Group" + (i + 1));
            cbs.setStatus("A");
            cbs.setServiceOrder(1);
            services.add(cbs);
        }

        CtlDiagCode seedDiagCode = new CtlDiagCode();
        seedDiagCode.setServiceType(typeid);
        seedDiagCode.setDiagnosticCode("000");
        seedDiagCode.setStatus("A");

        CtlBillingType optionalBillingType = null;
        if (!billtype.equals("no")) {
            optionalBillingType = new CtlBillingType();
            optionalBillingType.setId(typeid);
            optionalBillingType.setBillType(billtype);
        }

        try {
            billingFormConfigurationService.addBillingForm(services, seedDiagCode, optionalBillingType);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to add billing form for typeid={}", LogSafe.sanitize(typeid), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to add billing form");
            return NONE;
        }

        response.sendRedirect(request.getContextPath() + "/billing/CA/ON/ManageBillingform");
        return NONE;
    }

    /**
     * Redirects back to {@code manageBillingform.jsp} with the error message and
     * current field values encoded as query parameters so the UI can repopulate them.
     *
     * @param errMessage human-readable validation error
     * @param typeid     service type ID entered by the user
     * @param type       service type description
     * @param group1     group 1 description
     * @param group2     group 2 description
     * @param group3     group 3 description
     * @throws Exception if the redirect fails
     */
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    private void redirectWithError(String errMessage, String typeid, String type,
                                   String group1, String group2, String group3) throws Exception {
        String url = request.getContextPath() + "/billing/CA/ON/ManageBillingform"
                + "?errorMessage=" + URLEncoder.encode(errMessage, StandardCharsets.UTF_8)
                + "&type=" + URLEncoder.encode(type, StandardCharsets.UTF_8)
                + "&typeid=" + URLEncoder.encode(typeid, StandardCharsets.UTF_8)
                + "&group1=" + URLEncoder.encode(group1, StandardCharsets.UTF_8)
                + "&group2=" + URLEncoder.encode(group2, StandardCharsets.UTF_8)
                + "&group3=" + URLEncoder.encode(group3, StandardCharsets.UTF_8)
                + "&billingform=000";
        response.sendRedirect(url);
    }
}
