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
import io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao;
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.Objects;

/**
 * Struts2 action to replace the diagnostic codes for a generic billing service type.
 *
 * <p>Replaces {@code billing/dbManageBillingform_dx.jsp}. Deletes all existing
 * {@link CtlDiagCode} rows for the given service type, then iterates over the 45
 * {@code diagcode0}–{@code diagcode44} request parameters and persists a new entry
 * for each non-empty value. Redirects to {@code manageBillingform.jsp} on success.
 *
 * @since 2026-01-01
 */
public class DbManageBillingformDx2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CtlDiagCodeDao ctlDiagCodeDao = SpringUtils.getBean(CtlDiagCodeDao.class);

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

        // Delete all existing diagnostic codes for this service type
        for (CtlDiagCode d : ctlDiagCodeDao.findByServiceType(typeid)) {
            ctlDiagCodeDao.remove(d.getId());
        }

        // Persist each non-empty diagcode parameter (indices 0–44)
        for (int i = 0; i < 45; i++) {
            String diagcode = request.getParameter("diagcode" + i);
            if (diagcode != null && !diagcode.isEmpty()) {
                CtlDiagCode cdc = new CtlDiagCode();
                cdc.setServiceType(typeid);
                cdc.setDiagnosticCode(diagcode);
                cdc.setStatus("A");
                ctlDiagCodeDao.persist(cdc);
            }
        }

        response.sendRedirect(request.getContextPath() + "/billing/manageBillingform.jsp");
        return NONE;
    }
}
