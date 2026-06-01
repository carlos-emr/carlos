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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingShortcutPg1ViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingShortcutPg1ViewModelAssembler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
public class ViewBillingShortcutPg1View2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingShortcutPg1ViewModelAssembler assembler;

    private BillingShortcutPg1ViewModel shortcutPg1Model;

    public ViewBillingShortcutPg1View2Action(SecurityInfoManager securityInfoManager,
                                  BillingShortcutPg1ViewModelAssembler assembler) {
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
