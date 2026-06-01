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
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
public class UpdateBillingOnCorrection2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingOnCorrectionViewModelAssembler assembler;
    private final BillingCorrectionService service;

    public UpdateBillingOnCorrection2Action(SecurityInfoManager securityInfoManager,
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
        request.setAttribute("correctionModel", assembler.assemble(request, loggedInInfo));

        return service.updateInvoice(loggedInInfo, request);
    }
}
