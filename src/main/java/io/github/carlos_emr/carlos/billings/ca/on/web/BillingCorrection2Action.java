/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * GET-only view gate for the Ontario billing correction page. Mapped at
 * {@code billing/CA/ON/BillingONCorrection} — the URL the
 * {@code popupPage(...)} callers in {@code billingONStatus.jsp},
 * {@code billingONHistory.jsp}, and {@code editappointment.jsp} hit
 * with {@code ?billing_no=N} to render the correction form.
 *
 * <p>State-mutating POSTs go to dedicated sibling actions:</p>
 * <ul>
 *   <li>{@link UpdateBillingOnCorrection2Action} —
 *       {@code billing/CA/ON/UpdateBillingONCorrection} (Save /
 *       Save&amp;Correct Another / admin-submit)</li>
 *   <li>{@link AddThirdPartyPayment2Action} —
 *       {@code billing/CA/ON/Add3rdPartyPayment} (record a 3rd-party
 *       payment or refund)</li>
 * </ul>
 *
 * <p>Pre-refactor, this class held all five concerns (gate, assemble,
 * updateInvoice, add3rdPartyPayment, plus three private helpers) and
 * dispatched on a {@code request.getParameter("method")} string switch
 * — 753 lines. The current shape is a 90-line view-only gate; mutation
 * logic lives in {@link BillingCorrectionService}, called from the two
 * sibling action classes above.</p>
 *
 * @since 2026-04-25
 */
public class BillingCorrection2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingOnCorrectionViewModelAssembler assembler;

    public BillingCorrection2Action(SecurityInfoManager securityInfoManager,
                             BillingOnCorrectionViewModelAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
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

        // GET / HEAD only — mutations live on the sibling action URLs.
        // Anything else gets 405 with an Allow header per RFC 7231 §6.5.5.
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (java.io.IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

        // Build the view model and render the form.
        request.setAttribute("correctionModel", assembler.assemble(request, loggedInInfo));
        return SUCCESS;
    }
}
