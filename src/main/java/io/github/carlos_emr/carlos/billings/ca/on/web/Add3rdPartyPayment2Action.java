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
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionService;

/**
 * Thin Struts2 gate for the Ontario billing-correction <em>3rd-party
 * payment</em> workflow. Mapped at {@code billing/CA/ON/Add3rdPartyPayment}.
 *
 * <p>POST-only endpoint: records a single payment or refund against an
 * existing bill. Validates {@code billing_no}, {@code amtPaid},
 * {@code payMethod}, {@code payType} via
 * {@link BillingCorrectionService#addThirdPartyPayment(LoggedInInfo,
 * HttpServletRequest)}; throws {@link BillingValidationException} on any
 * rejection, which the package-level {@code <global-exception-mappings>}
 * routes to {@code billingValidationError.jsp}.</p>
 *
 * <p>Replaces the {@code method=add3rdPartyPayment} branch of the legacy
 * dispatch on {@link BillingCorrection2Action}. Has its own URL so future
 * AJAX callers can post directly without the hidden-method-input legacy
 * convention.</p>
 *
 * @since 2026-04-25
 */
public final class Add3rdPartyPayment2Action extends ActionSupport {

    // Dual-constructor DI: SpringUtils.getBean confined to the no-arg ctor.
    private final SecurityInfoManager securityInfoManager;
    private final BillingONCorrectionDataAssembler assembler;
    private final BillingCorrectionService service;

    /** Production constructor used by Struts2's Spring object factory. */
    public Add3rdPartyPayment2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             new BillingONCorrectionDataAssembler(),
             SpringUtils.getBean(BillingCorrectionService.class));
    }

    /** Test-friendly constructor — call with mocks. Package-private. */
    Add3rdPartyPayment2Action(SecurityInfoManager securityInfoManager,
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

        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // POST-only — recording a payment is a state mutation.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (java.io.IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

        // Build the view model up front so re-render results see a populated
        // correctionModel (mirrors UpdateBillingONCorrection2Action).
        request.setAttribute("correctionModel", assembler.assemble(request, loggedInInfo));

        return service.addThirdPartyPayment(loggedInInfo, request);
    }
}
