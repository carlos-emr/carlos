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
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.AddEditServiceCodeViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.AddEditServiceCodeViewModelAssembler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Conditional-POST gate for {@code billing/CA/ON/addEditServiceCode.jsp}. The
 * JSP is a self-posting form (initial GET renders the picker; the form
 * submits to the same URL with an {@code action} parameter to add / edit /
 * delete). Enforces {@code _admin.billing} w. Requires POST only when the
 * mutation-intent {@code action} parameter is present; allows GET / POST
 * for plain form display. Add/edit mutations are executed here through
 * {@link ServiceCodePersister}; the assembler only builds the read-only view
 * model for the JSP.
 *
 * @since 2026-04-13
 *        2026-04-24 (relax strict POST-only - GET form display was 405'd
 *                    via the admin menu's popupPage call)
 */
public class AddEditServiceCode2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final AddEditServiceCodeViewModelAssembler addEditServiceCodeAssembler;
    private final ServiceCodePersister serviceCodePersister;

    public AddEditServiceCode2Action(SecurityInfoManager securityInfoManager,
                                      AddEditServiceCodeViewModelAssembler addEditServiceCodeAssembler,
                                      ServiceCodePersister serviceCodePersister) {
        this.securityInfoManager = securityInfoManager;
        this.addEditServiceCodeAssembler = addEditServiceCodeAssembler;
        this.serviceCodePersister = serviceCodePersister;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Explicit null-session guard matches the sibling 2Actions in this
        // PR. Without it, hasPrivilege(null, ...) reaches SecurityInfoManagerImpl
        // and emits an internal ERROR before returning false — fail fast with
        // a clear signal instead.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        // Self-posting form: require POST only when the mutation-intent
        // 'action' param is present (form submit). Plain GETs render the form.
        // isBlank vs isEmpty: a whitespace-only "action" param value (e.g.
        // ?action=%20) would otherwise bypass POST enforcement here. Real form
        // submissions never carry blank intent, so isBlank closes that corner.
        String mutationAction = request.getParameter("action");
        if (mutationAction != null && !mutationAction.isBlank()
                && !"POST".equalsIgnoreCase(request.getMethod())) {
            // RFC 7231 §6.5.5 — 405 responses MUST include Allow. Matches the
            // sibling 2Actions' 405 paths in this module.
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        ServiceCodePersister.AddEditServiceCodeResult mutationResult = null;
        if (isSaveOrAdd(request.getParameter("submitFrm"))) {
            mutationResult = serviceCodePersister.saveOrAdd(addEditRequestFrom(request));
        }

        AddEditServiceCodeViewModel model = addEditServiceCodeAssembler.assemble(request, loggedInInfo, mutationResult);
        request.setAttribute("addEditModel", model);

        return SUCCESS;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static boolean isSaveOrAdd(String submitFrm) {
        return "Save".equals(submitFrm)
                || (submitFrm != null && "Add Service Code".equalsIgnoreCase(submitFrm));
    }

    private static ServiceCodePersister.AddEditServiceCodeRequest addEditRequestFrom(HttpServletRequest request) {
        return new ServiceCodePersister.AddEditServiceCodeRequest(
                request.getParameter("submitFrm"),
                request.getParameter("action"),
                request.getParameter("service_code"),
                request.getParameter("billingservice_no"),
                request.getParameter("description"),
                request.getParameter("value"),
                request.getParameter("percentage"),
                request.getParameter("billingservice_date"),
                request.getParameter("termination_date"),
                "true".equals(request.getParameter("sliFlag")),
                request.getParameter("servicecode_style"),
                request.getParameter("min"),
                request.getParameter("max"));
    }
}
