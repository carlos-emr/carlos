/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.billing.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Struts2 action to add a new generic billing form service type.
 *
 * <p>Replaces {@code billing/dbManageBillingform_add.jsp}. Validates that all required
 * fields are non-empty, then persists three {@link CtlBillingService} entries (one per
 * group) and a seed {@link CtlDiagCode} entry. Unlike the Ontario-specific variant, this
 * root version performs no duplicate-ID check and does not persist a
 * {@code CtlBillingType} record. On validation failure the browser is redirected back
 * to {@code manageBillingform.jsp} with error details in the query string.
 *
 * @since 2026-04-05
 */
public class DbManageBillingformAdd2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    private CtlDiagCodeDao ctlDiagCodeDao = SpringUtils.getBean(CtlDiagCodeDao.class);

    /**
     * Creates a new generic billing service type with three groups and a diagnostic
     * code seed.
     *
     * @return {@link #NONE} after redirecting, or if the request method is not POST
     * @throws SecurityException if the user lacks {@code _admin.billing} write privilege
     */
    @Override
    public String execute() throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
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

        // Validate required fields
        if (type.isEmpty() || group1.isEmpty() || group2.isEmpty() || group3.isEmpty()) {
            String errMessage = "Error: Type Description, Groups Description must be entered.";
            redirectWithError(errMessage, typeid, type, group1, group2, group3);
            return NONE;
        }

        try {
            // Persist three CtlBillingService entries, one per group
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
                ctlBillingServiceDao.persist(cbs);
            }

            // Persist seed diagnostic code entry
            CtlDiagCode cdc = new CtlDiagCode();
            cdc.setServiceType(typeid);
            cdc.setDiagnosticCode("000");
            cdc.setStatus("A");
            ctlDiagCodeDao.persist(cdc);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to add billing form for typeid={}", typeid, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to add billing form");
            return NONE;
        }

        response.sendRedirect(request.getContextPath() + "/billing/manageBillingform.jsp");
        return NONE;
    }

    /**
     * Redirects back to {@code manageBillingform.jsp} with the error message and current
     * field values encoded as query parameters so the UI can repopulate the form.
     *
     * @param errMessage human-readable validation error
     * @param typeid     service type ID entered by the user
     * @param type       service type description
     * @param group1     group 1 description
     * @param group2     group 2 description
     * @param group3     group 3 description
     * @throws Exception if the redirect fails
     */
    private void redirectWithError(String errMessage, String typeid, String type,
                                   String group1, String group2, String group3) throws Exception {
        String url = request.getContextPath() + "/billing/manageBillingform.jsp"
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
