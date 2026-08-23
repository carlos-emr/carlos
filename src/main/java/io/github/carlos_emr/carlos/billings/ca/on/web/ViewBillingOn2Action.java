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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnFormViewModelAssembler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * View-scope gate for the Ontario billing form ({@code billingON.jsp}).
 *
 * <p>Enforces {@code _billing r}, accepts GET / HEAD / POST (the form
 * self-posts), and exposes a {@link BillingOnFormViewModel} at request
 * attribute {@code formModel} for the JSP to consume via EL. Resolves the
 * 1 MB JSP page-buffer overflow that occurred when the form built its
 * data inline via ~24 DAO lookups.</p>
 *
 * @since 2026-04-24
 */
public class ViewBillingOn2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingOnFormViewModelAssembler assembler;

    private BillingOnFormViewModel model;

    public ViewBillingOn2Action(SecurityInfoManager securityInfoManager,
                         BillingOnFormViewModelAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            MiscUtils.getLogger().warn("Denied billingON view: no session");
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            MiscUtils.getLogger().warn(
                    "Denied billingON view: provider={} lacks _billing r",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_billing)");
        }

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"POST".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        this.model = buildModel(loggedInInfo, request);
        // Domain-prefixed key so EL references on the JSP can't collide with
        // Spring MVC's well-known "model" attribute. Symmetric with the other
        // ON billing pages: correctionModel, reviewModel, statusModel,
        // shortcutPg1Model.
        request.setAttribute("formModel", this.model);
        return SUCCESS;
    }

    /**
     * Builds the view model via {@link BillingOnFormViewModelAssembler}. The
     * assembler encapsulates the DAO lookups + scriptlet logic previously
     * inlined at the top of {@code billingON.jsp}. Takes the already-resolved
     * {@link LoggedInInfo} from the caller — re-reading it from the session
     * here would risk a session-state mismatch with the privilege check that
     * just succeeded.
     */
    private BillingOnFormViewModel buildModel(LoggedInInfo loggedInInfo, HttpServletRequest request) {
        return assembler.assemble(request, loggedInInfo);
    }

    public BillingOnFormViewModel getModel() {
        return model;
    }
}
