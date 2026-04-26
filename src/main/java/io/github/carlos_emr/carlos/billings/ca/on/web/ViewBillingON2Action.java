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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONFormDataAssembler;

/**
 * View-scope gate for the Ontario billing form ({@code billingON.jsp}).
 *
 * <p>Enforces {@code _billing r}, accepts GET / HEAD / POST (the form
 * self-posts), and exposes a {@link BillingONFormViewModel} at request
 * attribute {@code formModel} for the JSP to consume via EL. Resolves the
 * 1 MB JSP page-buffer overflow that occurred when the form built its
 * data inline via ~24 DAO lookups.</p>
 *
 * @since 2026-04-24
 */
public final class ViewBillingON2Action extends ActionSupport {

    // Dual-constructor DI: SpringUtils.getBean is confined to the no-arg
    // production constructor; tests use the package-private constructor with
    // mocks to avoid the static service-locator path.
    private final SecurityInfoManager securityInfoManager;
    private final BillingONFormDataAssembler assembler;

    private BillingONFormViewModel model;

    /** Production constructor used by Struts2's Spring object factory. */
    public ViewBillingON2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             new BillingONFormDataAssembler());
    }

    /** Test-friendly constructor — call with mocks. Package-private. */
    ViewBillingON2Action(SecurityInfoManager securityInfoManager,
                         BillingONFormDataAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            MiscUtils.getLogger().warn("Denied billingON view: no session");
            throw new SecurityException("missing required sec object (_billing)");
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
     * Builds the view model via {@link BillingONFormDataAssembler}. The
     * assembler encapsulates the DAO lookups + scriptlet logic previously
     * inlined at the top of {@code billingON.jsp}. Takes the already-resolved
     * {@link LoggedInInfo} from the caller — re-reading it from the session
     * here would risk a session-state mismatch with the privilege check that
     * just succeeded.
     */
    private BillingONFormViewModel buildModel(LoggedInInfo loggedInInfo, HttpServletRequest request) {
        return assembler.assemble(loggedInInfo, request);
    }

    public BillingONFormViewModel getModel() {
        return model;
    }
}
