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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONCorrectionDataAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionService;

/**
 * Thin Struts2 gate for the Ontario billing-correction <em>update</em>
 * workflow. Mapped at {@code billing/CA/ON/UpdateBillingONCorrection}.
 *
 * <p>This is the POST endpoint that {@code billingONCorrection.jsp}'s
 * Save / Save&amp;Correct Another / admin-submit form posts to. The
 * action enforces the security gate, builds the read-side view model
 * (so any result that re-renders the JSP sees a populated
 * {@code correctionModel}), then delegates the actual mutation logic
 * to {@link BillingCorrectionService#updateInvoice(LoggedInInfo,
 * HttpServletRequest)}.</p>
 *
 * <p>Replaces the legacy method-param dispatch on
 * {@link BillingCorrection2Action} where the JSP posted to
 * {@code /BillingONCorrection} with a hidden {@code method=updateInvoice}
 * field. Splitting the URL gives each HTTP-level operation its own
 * action class and removes the manual {@code request.getParameter("method")}
 * string-switching.</p>
 *
 * @since 2026-04-25
 */
public final class UpdateBillingONCorrection2Action extends ActionSupport {

    // Dual-constructor DI: SpringUtils.getBean confined to the no-arg ctor.
    private final SecurityInfoManager securityInfoManager;
    private final BillingONCorrectionDataAssembler assembler;
    private final BillingCorrectionService service;

    /** Production constructor used by Struts2's Spring object factory. */
    public UpdateBillingONCorrection2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             new BillingONCorrectionDataAssembler(),
             new BillingCorrectionService());
    }

    /** Test-friendly constructor — call with mocks. Package-private. */
    UpdateBillingONCorrection2Action(SecurityInfoManager securityInfoManager,
                                     BillingONCorrectionDataAssembler assembler,
                                     BillingCorrectionService service) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
        this.service = service;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Reject sessionless requests up front.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // POST-only — the update is a state mutation. Everything else gets
        // 405 with `Allow: POST` per RFC 7231 §6.5.5.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (java.io.IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

        // Build the view model up front so any result that forwards back to
        // billingONCorrection.jsp (success, closeReload, adminReload, loadOnly)
        // sees a populated correctionModel for EL bindings.
        request.setAttribute("correctionModel", assembler.assemble(loggedInInfo, request));

        return service.updateInvoice(loggedInInfo, request);
    }
}
