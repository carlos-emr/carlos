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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingONFavouriteViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONFavouriteViewModelAssembler;

/**
 * Gate for {@code billing/CA/ON/billingONfavourite.jsp}. Enforces {@code _billing}
 * {@code r} privilege for read-only views; mutating paths (add/edit/delete via
 * the {@code action} parameter) additionally require POST to prevent CSRF-ish
 * state-change over GET.
 *
 * <p>Assembles a {@link BillingONFavouriteViewModel} via
 * {@link BillingONFavouriteViewModelAssembler} so the JSP body is pure
 * presentation: the data assembler runs the Save/Search/Delete branches
 * and dropdown population that the legacy JSP did inline.</p>
 *
 * @since 2026-04-13
 */
public class ViewBillingONFavourite2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingONFavouriteViewModelAssembler billingONFavouriteAssembler;

    public ViewBillingONFavourite2Action(SecurityInfoManager securityInfoManager,
                                          BillingONFavouriteViewModelAssembler billingONFavouriteAssembler) {
        this.securityInfoManager = securityInfoManager;
        this.billingONFavouriteAssembler = billingONFavouriteAssembler;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String action = request.getParameter("action");
        if (action != null) {
            String lower = action.toLowerCase();
            if ((lower.startsWith("add") || lower.startsWith("edit") || lower.startsWith("delete"))
                    && !"POST".equalsIgnoreCase(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
        }

        BillingONFavouriteViewModel model = billingONFavouriteAssembler
                .assemble(request, loggedInInfo);
        request.setAttribute("favouriteModel", model);

        return SUCCESS;
    }
}
