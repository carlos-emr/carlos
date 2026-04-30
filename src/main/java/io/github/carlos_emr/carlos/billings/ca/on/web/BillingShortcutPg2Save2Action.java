/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingShortcutPg2ViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingShortcutPg2Service;

/**
 * Struts 2Action gate for the Ontario billing shortcut confirmation page (page 2).
 *
 * <p>Enforces {@code _billing w} authorization, routes "Back to Edit" requests
 * back to {@code billingShortcutPg1View}, and otherwise delegates the
 * read-DAO data prep + bill persistence + post-save navigation directive to
 * {@link BillingShortcutPg2Service}. The JSP renders the resulting
 * {@link BillingShortcutPg2ViewModel} as a pure presentation layer — the 6
 * inline {@code SpringUtils.getBean} lookups it used to perform
 * (BillingDao, BillingDetailDao, ProviderDao, DemographicDao,
 * BillingServiceDao, BillingPercLimitDao) are now owned by the assembler.</p>
 *
 * @since 2026
 */
public class BillingShortcutPg2Save2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingShortcutPg2Service billingShortcutPg2Assembler;

    public BillingShortcutPg2Save2Action(SecurityInfoManager securityInfoManager,
                                          BillingShortcutPg2Service billingShortcutPg2Assembler) {
        this.securityInfoManager = securityInfoManager;
        this.billingShortcutPg2Assembler = billingShortcutPg2Assembler;
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

        // The assembler's persist branch writes Billing + BillingDetail rows.
        // Without a POST gate a forged GET URL would drive the persist path,
        // sidestepping CSRFGuard's body-token validation (default config only
        // validates non-GET request bodies). The "Back to Edit" branch below
        // is read-only and runs after we've confirmed POST.
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        if ("Back to Edit".equals(request.getParameter("button"))) {
            return "backToEdit";
        }

        BillingShortcutPg2ViewModel model = billingShortcutPg2Assembler
                .assemble(request, loggedInInfo);
        request.setAttribute("shortcutPg2Model", model);

        return SUCCESS;
    }
}
