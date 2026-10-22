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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodePersister;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnEditPrivateCodeViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnEditPrivateCodeViewModelAssembler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * View gate for {@code billing/CA/ON/billingONEditPrivateCode.jsp}. Enforces
 * {@code _admin.billing w} privilege, applies private-code writes on POST,
 * and assembles a {@link BillingOnEditPrivateCodeViewModel} so the JSP body
 * is pure presentation.
 *
 * @since 2026-04-13
 */
public class ViewBillingOnEditPrivateCode2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingOnEditPrivateCodeViewModelAssembler billingONEditPrivateCodeAssembler;
    private final ServiceCodePersister serviceCodePersister;

    public ViewBillingOnEditPrivateCode2Action(SecurityInfoManager securityInfoManager,
                                               BillingOnEditPrivateCodeViewModelAssembler billingONEditPrivateCodeAssembler,
                                               ServiceCodePersister serviceCodePersister) {
        this.securityInfoManager = securityInfoManager;
        this.billingONEditPrivateCodeAssembler = billingONEditPrivateCodeAssembler;
        this.serviceCodePersister = serviceCodePersister;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        ServiceCodePersister.PrivateCodeMutationResult mutationResult = null;
        if (isMutationSubmission(request)) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            mutationResult = serviceCodePersister.saveOrDeletePrivateCode(privateCodeMutationRequestFrom(request));
        }

        BillingOnEditPrivateCodeViewModel model = billingONEditPrivateCodeAssembler
                .assemble(request, loggedInInfo, mutationResult);
        request.setAttribute("privateCodeModel", model);

        return SUCCESS;
    }

    private static boolean isMutationSubmission(HttpServletRequest request) {
        String submit = request.getParameter("submit");
        return "Save".equals(submit) || "Delete".equals(submit);
    }

    private static ServiceCodePersister.PrivateCodeMutationRequest privateCodeMutationRequestFrom(
            HttpServletRequest request) {
        return new ServiceCodePersister.PrivateCodeMutationRequest(
                request.getParameter("submit"),
                request.getParameter("action"),
                request.getParameter("service_code"),
                request.getParameter("description"),
                request.getParameter("value"),
                request.getParameter("billingservice_date"),
                request.getParameter("gstFlag"));
    }
}
