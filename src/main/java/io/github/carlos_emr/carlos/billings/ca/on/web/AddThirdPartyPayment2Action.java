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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnCorrectionViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
public class AddThirdPartyPayment2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingOnCorrectionViewModelAssembler assembler;
    private final BillingCorrectionService service;

    public AddThirdPartyPayment2Action(SecurityInfoManager securityInfoManager,
                              BillingOnCorrectionViewModelAssembler assembler,
                              BillingCorrectionService service) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
        this.service = service;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
        // correctionModel (mirrors UpdateBillingOnCorrection2Action).
        request.setAttribute("correctionModel", assembler.assemble(request, loggedInInfo));

        return service.addThirdPartyPayment(loggedInInfo, request);
    }
}
