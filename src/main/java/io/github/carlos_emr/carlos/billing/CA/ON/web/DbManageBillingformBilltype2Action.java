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
package io.github.carlos_emr.carlos.billing.CA.ON.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Objects;

/**
 * Struts2 action to manage the bill type association for an Ontario billing service type.
 *
 * <p>Replaces {@code dbManageBillingform_billtype.jsp}. Three cases are handled:
 * <ul>
 *   <li>{@code billtype == "no"} – removes the existing {@link CtlBillingType} entry.</li>
 *   <li>{@code billtype_old == "no"} – creates a new {@link CtlBillingType} entry.</li>
 *   <li>Otherwise – finds the existing entry and updates its bill type value.</li>
 * </ul>
 * Returns {@code success} to forward to the confirmation view JSP.
 *
 * @since 2026-04-05
 */
public class DbManageBillingformBilltype2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CtlBillingTypeDao ctlBillingTypeDao = SpringUtils.getBean(CtlBillingTypeDao.class);

    /**
     * Processes a bill type change for the given service type.
     *
     * @return {@link #SUCCESS} to forward to the confirmation view, or {@link #NONE}
     *         if the request method is not POST
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

        String servicetype = Objects.toString(request.getParameter("servicetype"), "");
        String billtype = Objects.toString(request.getParameter("billtype"), "");
        String billtypeOld = Objects.toString(request.getParameter("billtype_old"), "");

        if (servicetype.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing servicetype parameter");
            return NONE;
        }

        try {
            if (billtype.equals("no")) {
                // Remove the existing billing type entry by its String ID
                ctlBillingTypeDao.remove(servicetype);

            } else if (billtypeOld.equals("no")) {
                // No prior entry existed; create a new one
                CtlBillingType cbt = new CtlBillingType();
                cbt.setId(servicetype);
                cbt.setBillType(billtype);
                ctlBillingTypeDao.persist(cbt);

            } else {
                // Update the existing entry
                CtlBillingType cbt = ctlBillingTypeDao.find(servicetype);
                if (cbt != null) {
                    cbt.setBillType(billtype);
                    ctlBillingTypeDao.merge(cbt);
                } else {
                    MiscUtils.getLogger().error(
                            "DbManageBillingformBilltype2Action: no CtlBillingType found for servicetype={} — update failed",
                            servicetype);
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,
                            "Billing type entry not found for the specified service type");
                    return NONE;
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to update bill type for servicetype={}", servicetype, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update bill type");
            return NONE;
        }

        return SUCCESS;
    }
}
