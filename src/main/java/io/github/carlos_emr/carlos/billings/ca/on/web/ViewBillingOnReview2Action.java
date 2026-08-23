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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnReviewViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnReviewViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnReviewDiagPersister;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Mutation gate for {@code billing/CA/ON/billingONReview.jsp}. The JSP
 * historically performed a {@code dxresearchDao.save(dx)} side-effect inside
 * its top scriptlet when {@code addToPatientDx=yes} was posted; that write is
 * now done by {@link BillingOnReviewDiagPersister}, which this action invokes
 * <em>before</em> assembling the view model so audit failures (non-numeric
 * demoNo, DAO outage) propagate through the standard error path rather than
 * rendering a misleadingly successful review page. The JSP remains a
 * presentation layer over the resulting view model.
 *
 * <p>Enforces {@code _billing} w privilege AND POST-only before forwarding to
 * the JSP. GET requests return 405 with {@code Allow: POST}. The class name
 * retains the {@code View...} prefix for consistency with sibling gate actions
 * in this migration, even though the behavior is mutation-gate.</p>
 * @since 2026-04-13
 */
public class ViewBillingOnReview2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingOnReviewDiagPersister dxPersister;
    private final BillingOnReviewViewModelAssembler assembler;

    private BillingOnReviewViewModel reviewModel;

    public ViewBillingOnReview2Action(SecurityInfoManager securityInfoManager,
                               BillingOnReviewDiagPersister dxPersister,
                               BillingOnReviewViewModelAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.dxPersister = dxPersister;
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
            throw new SecurityException("missing session");
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            // RFC 7231 §6.5.5: 405 responses MUST include the Allow header.
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // The optional addToPatientDx persist needs a real provider id so the
        // dxresearch row has a valid audit-trail attribution. Refuse the
        // request before the persister runs if we can't attach to a provider.
        String userNo = loggedInInfo.getLoggedInProviderNo();
        if (userNo == null || userNo.isEmpty()) {
            throw new SecurityException("missing provider in session");
        }
        // Run the optional clinical write FIRST so any failure (audit gap on
        // non-numeric demoNo, DAO outage) propagates through the action's
        // standard error path rather than producing a misleadingly successful
        // review render with the patient unchanged in the registry.
        dxPersister.persistIfRequested(request, userNo);
        this.reviewModel = assembler.assemble(request, loggedInInfo);
        request.setAttribute("reviewModel", this.reviewModel);

        return SUCCESS;
    }

    public BillingOnReviewViewModel getReviewModel() {
        return reviewModel;
    }
}
