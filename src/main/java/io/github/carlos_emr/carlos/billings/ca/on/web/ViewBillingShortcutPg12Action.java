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
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingShortcutPg1ViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingShortcutPg1DataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingShortcutPg1.jsp}.
 *
 * <p>The shortcut page-1 JSP previously had no dedicated view action — it was
 * reached only via {@code BillingShortcutPg2Save2Action.backToEdit}, which
 * forwarded directly at the WEB-INF JSP, and so the JSP bore the full burden
 * of session validation, logout-redirect on missing user, and DAO prep.</p>
 *
 * <p>This gate enforces {@code _billing} read privilege, allows GET / HEAD /
 * POST (the shortcut form self-posts), and exposes a
 * {@link BillingShortcutPg1ViewModel} as request attribute
 * {@code shortcutPg1Model}. Both the new {@code billing/CA/ON/billingShortcutPg1View}
 * mapping and the {@code BillingShortcutPg2Save} {@code backToEdit} result
 * chain through this action so the JSP no longer needs its session-null
 * redirect scriptlet.</p>
 *
 * @since 2026-04-24
 */
public class ViewBillingShortcutPg12Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingShortcutPg1DataAssembler assembler;

    private BillingShortcutPg1ViewModel shortcutPg1Model;

    /** Constructor injection used by Spring + Struts2's SpringObjectFactory. */
    @Autowired
    public ViewBillingShortcutPg12Action(SecurityInfoManager securityInfoManager,
                                  BillingShortcutPg1DataAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"POST".equalsIgnoreCase(method)) {
            // RFC 7231 §6.5.5: 405 responses MUST include the Allow header.
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        this.shortcutPg1Model = assembler.assemble(request, loggedInInfo);
        request.setAttribute("shortcutPg1Model", this.shortcutPg1Model);
        return SUCCESS;
    }

    public BillingShortcutPg1ViewModel getShortcutPg1Model() {
        return shortcutPg1Model;
    }
}
