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
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.Objects;

/**
 * Struts2 action to delete all data associated with a generic billing service type.
 *
 * <p>Replaces {@code billing/dbManageBillingform_delete.jsp}. Removes all
 * {@link CtlBillingService} and {@link CtlDiagCode} rows for the given service type.
 * Unlike the Ontario-specific variant, this root version does not remove a
 * {@code CtlBillingType} entry. Returns {@code success} to forward to the confirmation
 * view at {@code /WEB-INF/jsp/billing/dbManageBillingform_delete.jsp}, which renders
 * the JavaScript close/refresh snippet.
 *
 * @since 2026-01-01
 */
public class DbManageBillingformDelete2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CtlBillingServiceDao billingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    private CtlDiagCodeDao diagCodeDao = SpringUtils.getBean(CtlDiagCodeDao.class);

    /**
     * Deletes all billing service and diagnostic code entries for the given service type.
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

        String typeid = Objects.toString(request.getParameter("servicetype"), "");

        if (typeid.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing servicetype parameter");
            return NONE;
        }

        // Remove all billing service entries for this service type
        for (CtlBillingService b : billingServiceDao.findByServiceType(typeid)) {
            billingServiceDao.remove(b.getId());
        }

        // Remove all diagnostic code entries for this service type
        for (CtlDiagCode d : diagCodeDao.findByServiceType(typeid)) {
            diagCodeDao.remove(d.getId());
        }

        // Root version: no CtlBillingType removal (unlike Ontario-specific variant)
        return SUCCESS;
    }
}
