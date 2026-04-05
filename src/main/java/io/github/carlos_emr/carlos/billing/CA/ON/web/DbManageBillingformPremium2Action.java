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
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.Date;

/**
 * Struts2 action to add premium billing service codes for Ontario billing.
 *
 * <p>Replaces {@code dbManageBillingform_premium.jsp}. Iterates over the ten
 * {@code service1}–{@code service10} request parameters and persists a new
 * {@link CtlBillingServicePremium} entry for each non-empty value.
 *
 * @since 2026-01-01
 */
public class DbManageBillingformPremium2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CtlBillingServicePremiumDao ctlBillingServicePremiumDao = SpringUtils.getBean(CtlBillingServicePremiumDao.class);

    @Override
    public String execute() throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        // Persist each non-empty service parameter (indices 1–10)
        for (int i = 1; i < 11; i++) {
            String serviceCode = request.getParameter("service" + i);
            if (serviceCode == null || serviceCode.isEmpty()) {
                continue;
            }

            CtlBillingServicePremium cbsp = new CtlBillingServicePremium();
            cbsp.setServiceTypeName("Office");
            cbsp.setServiceCode(serviceCode);
            cbsp.setStatus("A");
            cbsp.setUpdateDate(new Date());
            ctlBillingServicePremiumDao.persist(cbsp);
        }

        response.sendRedirect(request.getContextPath() + "/billing/CA/ON/manageBillingform.jsp");
        return NONE;
    }
}
