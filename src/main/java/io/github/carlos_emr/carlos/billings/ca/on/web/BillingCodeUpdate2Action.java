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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeUpdateViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCodeUpdateViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodePersister;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Mutation gate for {@code billing/CA/ON/billingCodeUpdate.jsp}. Enforces
 * {@code _billing w} privilege AND POST-only (the JSP-era scriptlet
 * persisted BillingService description edits during render).
 *
 * <p>Also assembles a {@link BillingCodeUpdateViewModel} via
 * {@link BillingCodeUpdateViewModelAssembler}, which both runs the persist
 * mutation when applicable and tells the JSP which client-side script
 * branch to emit.</p>
 *
 * @since 2026-04-13
 */
public class BillingCodeUpdate2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingCodeUpdateViewModelAssembler billingCodeUpdateAssembler;

    private final ServiceCodePersister serviceCodePersister;

    public BillingCodeUpdate2Action(SecurityInfoManager securityInfoManager,
                                     BillingCodeUpdateViewModelAssembler billingCodeUpdateAssembler,
                                     ServiceCodePersister serviceCodePersister) {
        this.securityInfoManager = securityInfoManager;
        this.billingCodeUpdateAssembler = billingCodeUpdateAssembler;
        this.serviceCodePersister = serviceCodePersister;
    }
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        persistDescriptionUpdateIfRequested(request);

        BillingCodeUpdateViewModel model = billingCodeUpdateAssembler.assemble(request, loggedInInfo);
        request.setAttribute("codeUpdateModel", model);

        return SUCCESS;
    }

    /**
     * Trigger the {@code BillingService} description-merge when the form was
     * submitted via the "update &lt;code&gt;" branch (legacy JSP cleaved the
     * last 5 chars off the submit value). The Confirm branch is read-only
     * and skipped here.
     */
    private void persistDescriptionUpdateIfRequested(HttpServletRequest request) {
        String update = request.getParameter("update");
        if (update == null || update.length() < 5 || "Confirm".equals(update)) {
            return;
        }
        String code = update.substring(update.length() - 5);
        String newDescription = request.getParameter(code);
        if (newDescription == null) {
            return;
        }
        serviceCodePersister.updateDescriptionByServiceCode(code, newDescription);
    }
}
