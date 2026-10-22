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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnPaymentViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnPaymentViewModelAssembler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Conditional-POST gate for {@code billing/CA/ON/billingONPayment.jsp}. The
 * JSP renders the Payment Received Report (RA billing, premium payments,
 * 3rd-party billing). The original JSP enforced {@code _tasks r} via
 * {@code <security:oscarSec reverse="true">} and computed
 * {@code isTeamBillingOnly} / {@code isThisProviderOnly} flags inline; this
 * action mirrors those checks and additionally builds the
 * {@link BillingOnPaymentViewModel} via
 * {@link BillingOnPaymentViewModelAssembler} so the JSP can read pre-resolved
 * records instead of doing 9 inline {@code SpringUtils.getBean} lookups.
 *
 * @since 2026-04-13
 */
public class BillingOnPayment2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingOnPaymentViewModelAssembler billingONPaymentAssembler;

    public BillingOnPayment2Action(SecurityInfoManager securityInfoManager,
                                    BillingOnPaymentViewModelAssembler billingONPaymentAssembler) {
        this.securityInfoManager = securityInfoManager;
        this.billingONPaymentAssembler = billingONPaymentAssembler;
    }
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tasks", "r", null)) {
            throw new SecurityException("missing required sec object (_tasks)");
        }

        // Self-posting form: require POST when submit-action params are present;
        // allow GET for initial form display.
        String submit = request.getParameter("save");
        if (submit == null) submit = request.getParameter("savePayment");
        if (submit != null && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        boolean isTeamBillingOnly = securityInfoManager.hasPrivilege(loggedInInfo, "_team_billing_only", "r", null);
        // _admin / _admin.billing override _admin.invoices, mirroring the
        // <security:oscarSec> stacking the JSP used: invoices grants
        // single-provider-only mode, but the broader admin objects re-open
        // the dropdown to all billable providers.
        boolean isThisProviderOnly = securityInfoManager.hasPrivilege(loggedInInfo, "_admin.invoices", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "r", null);

        try {
            BillingOnPaymentViewModel model = billingONPaymentAssembler.assemble(
                    request, loggedInInfo,
                    isThisProviderOnly, isTeamBillingOnly);
            request.setAttribute("paymentModel", model);
        } catch (SecurityException secEx) {
            // assembler throws SecurityException when isThisProviderOnly is set
            // but the user has no OHIP number — preserve the legacy
            // /noRights.html redirect path.
            MiscUtils.getLogger().warn("BillingOnPayment2Action: redirecting to noRights — {}", secEx.getMessage());
            response.sendRedirect(request.getContextPath() + "/noRights.html");
            return NONE;
        }

        return SUCCESS;
    }
}
