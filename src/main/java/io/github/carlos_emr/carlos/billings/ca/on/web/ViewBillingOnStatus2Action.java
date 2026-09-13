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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnStatusViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnStatusViewModelAssembler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * View gate for {@code billing/CA/ON/billingONStatus.jsp}. Enforces
 * {@code _billing r} for page access and exposes a
 * {@link BillingOnStatusViewModel} on the request at attribute
 * {@code statusModel} so the JSP can read request parameter echoes +
 * default-value resolution via EL.
 *
 * <p>{@code _team_billing_only} is consulted separately as a *filter flag*
 * (does this user see only their team's bills?) and is captured on the view
 * model rather than being a page-access gate. Earlier docs/code on this gate
 * incorrectly used {@code _team_billing_only} as the access privilege; that
 * was relaxed to {@code _billing r} to match the companion mutation action
 * {@code BillingOnStatusErUpdateStatus2Action}.</p>
 *
 * <p>Parameter parsing + default resolution is delegated to
 * {@link BillingOnStatusViewModelAssembler} so this action stays a thin gate.</p>
 *
 * @since 2026-04-13
 */
public class ViewBillingOnStatus2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingOnStatusViewModelAssembler assembler;

    private BillingOnStatusViewModel statusModel;

    public ViewBillingOnStatus2Action(SecurityInfoManager securityInfoManager,
                               BillingOnStatusViewModelAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Reject sessionless requests up front. SecurityInfoManagerImpl.hasPrivilege
        // dereferences loggedInInfo and emits an internal ERROR log on null, so
        // null-checking here keeps the log signal clean for real privilege denials.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        // _team_billing_only is a filter flag in the JSP (does the user see only
        // their team's bills?), NOT a page-access gate. The page access privilege
        // matches the companion BillingOnStatusErUpdateStatus2Action: _billing.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // The status page accepts GET / HEAD / POST (the search form on the
        // page self-posts). Anything else gets the 405 + Allow contract that
        // sibling ON view actions enforce — pattern parity, not a security
        // gate (the privilege check above is the actual access boundary).
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"POST".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // Status page rendering must not be cached — the underlying data
        // (RA error rows, settled bills) changes whenever a clinic edits a
        // claim, and stale views cause stale-state confusion. Sets are the
        // exact pre-existing contract from the legacy JSP scriptlet.
        applyNoCacheHeaders(response);

        this.statusModel = assembler.assemble(request, loggedInInfo);
        request.setAttribute("statusModel", this.statusModel);
        return SUCCESS;
    }

    private static void applyNoCacheHeaders(HttpServletResponse response) {
        // Preserves the exact legacy header sequence from the
        // billingONStatus.jsp scriptlet. NB: the Servlet API contract for
        // setHeader replaces (not appends), so only the final Cache-Control
        // value (max-stale=0) actually survives — the no-cache/private/
        // no-store directives are overwritten. Pragma + Expires DO get
        // through. Tightening the cache contract is out of scope for the
        // JSP-to-action move and tracked separately.
        response.setHeader("Pragma", "no-cache");           // HTTP 1.0
        response.setHeader("Cache-Control", "no-cache");    // HTTP 1.1
        response.setDateHeader("Expires", 0);               // proxy-side
        response.setHeader("Cache-Control", "private");     // HTTP 1.1
        response.setHeader("Cache-Control", "no-store");    // HTTP 1.1
        response.setHeader("Cache-Control", "max-stale=0"); // HTTP 1.1
    }

    public BillingOnStatusViewModel getStatusModel() {
        return statusModel;
    }
}
