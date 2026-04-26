/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.data.AddEditServiceCodeViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.AddEditServiceCodeDataAssembler;

/**
 * Conditional-POST gate for {@code billing/CA/ON/addEditServiceCode.jsp}. The
 * JSP is a self-posting form (initial GET renders the picker; the form
 * submits to the same URL with an {@code action} parameter to add / edit /
 * delete). Enforces {@code _admin.billing} w. Requires POST only when the
 * mutation-intent {@code action} parameter is present; allows GET / POST
 * for plain form display.
 *
 * @since 2026-04-13
 *        2026-04-24 (relax strict POST-only - GET form display was 405'd
 *                    via the admin menu's popupPage call)
 */
public final class AddEditServiceCode2Action extends ActionSupport {

    // Dual-constructor DI: SpringUtils.getBean confined to the no-arg ctor.
    private final SecurityInfoManager securityInfoManager;

    /** Production constructor used by Struts2's Spring object factory. */
    public AddEditServiceCode2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    /** Test-friendly constructor — call with mock. Package-private. */
    AddEditServiceCode2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

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
            // sibling 2Actions' 405 paths in this PR.
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        AddEditServiceCodeViewModel model = new AddEditServiceCodeDataAssembler().assemble(request);
        request.setAttribute("addEditModel", model);

        return SUCCESS;
    }
}
