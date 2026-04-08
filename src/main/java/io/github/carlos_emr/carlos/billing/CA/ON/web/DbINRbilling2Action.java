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

import java.util.Date;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action for adding an INR billing record.
 *
 * <p>Migrated from {@code billing/CA/ON/inr/dbINRbilling.jsp}. Accepts POST only,
 * enforces {@code _admin.billing} write privilege, builds a {@link BillingInr} entity
 * from request parameters and persists it via {@link BillingInrDao}.
 *
 * @since 2026-04-05
 */
public class DbINRbilling2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private final HttpServletRequest request = ServletActionContext.getRequest();
    private final HttpServletResponse response = ServletActionContext.getResponse();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final BillingInrDao billingInrDao = SpringUtils.getBean(BillingInrDao.class);

    /**
     * Validates the request, builds and persists a new {@link BillingInr} record.
     *
     * @return {@link #SUCCESS} on success, {@link #ERROR} for validation failures, or {@link #NONE} if the method is not POST
     * @throws Exception if an unexpected error occurs during persistence
     */
    @Override
    public String execute() throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required security object: _admin.billing");
        }

        String demoid = request.getParameter("demoid");
        if (demoid == null || demoid.trim().isEmpty()) {
            addActionError("Missing demographic ID.");
            return ERROR;
        }

        int demoIdInt;
        try {
            demoIdInt = Integer.parseInt(demoid.trim());
        } catch (NumberFormatException e) {
            addActionError("Invalid demographic ID — must be numeric.");
            return ERROR;
        }

        BillingInr bi = new BillingInr();
        bi.setDemographicNo(demoIdInt);
        bi.setDemographicName(Objects.toString(request.getParameter("demo_name"), ""));
        bi.setHin(Objects.toString(request.getParameter("demo_hin"), ""));
        bi.setDob(Objects.toString(request.getParameter("demo_dob"), ""));
        bi.setProviderNo(Objects.toString(request.getParameter("provider_no"), ""));
        bi.setProviderOhipNo(Objects.toString(request.getParameter("provider_ohip_no"), ""));
        bi.setProviderRmaNo(Objects.toString(request.getParameter("provider_rma_no"), ""));
        bi.setCreator(Objects.toString(request.getParameter("doccreator"), ""));
        bi.setDiagnosticCode(Objects.toString(request.getParameter("diag_code"), ""));
        bi.setServiceCode(Objects.toString(request.getParameter("service_code"), ""));
        bi.setServiceDesc(Objects.toString(request.getParameter("service_desc"), ""));
        bi.setBillingAmount(Objects.toString(request.getParameter("service_amount"), ""));
        bi.setBillingUnit(Objects.toString(request.getParameter("service_unit"), ""));
        bi.setCreateDateTime(new Date());
        bi.setStatus("N");

        billingInrDao.persist(bi);

        request.setAttribute("billSaved", true);
        return SUCCESS;
    }
}
