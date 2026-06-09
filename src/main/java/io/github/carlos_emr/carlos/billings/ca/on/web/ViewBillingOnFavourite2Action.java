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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFavouriteViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnFavouriteViewModelAssembler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Gate for {@code billing/CA/ON/billingONfavourite.jsp}. Enforces {@code _billing}
 * {@code r} privilege for read-only views; mutating paths (add/edit/delete via
 * the {@code action} parameter) additionally require POST to prevent CSRF-ish
 * state-change over GET.
 *
 * <p>Assembles a {@link BillingOnFavouriteViewModel} via
 * {@link BillingOnFavouriteViewModelAssembler} so the JSP body is pure
 * presentation. The action applies favourite-list writes before invoking the
 * read-only assembler.</p>
 *
 * @since 2026-04-13
 */
public class ViewBillingOnFavourite2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingOnFavouriteViewModelAssembler billingONFavouriteAssembler;
    private final BillingOnLookupService lookupService;

    public ViewBillingOnFavourite2Action(SecurityInfoManager securityInfoManager,
                                         BillingOnFavouriteViewModelAssembler billingONFavouriteAssembler,
                                         BillingOnLookupService lookupService) {
        this.securityInfoManager = securityInfoManager;
        this.billingONFavouriteAssembler = billingONFavouriteAssembler;
        this.lookupService = lookupService;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String action = request.getParameter("action");
        boolean mutationIntent = isMutationSubmission(request);
        if (mutationIntent && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        if (action != null) {
            String lower = action.toLowerCase();
            if ((lower.startsWith("add") || lower.startsWith("edit") || lower.startsWith("delete"))
                    && !"POST".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
        }
        if (mutationIntent && !securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingOnLookupService.FavouriteMutationResult mutationResult = null;
        if (mutationIntent) {
            mutationResult = lookupService.saveOrDeleteFavourite(favouriteMutationRequestFrom(request, loggedInInfo));
        }
        BillingOnFavouriteViewModel model = billingONFavouriteAssembler
                .assemble(request, loggedInInfo, mutationResult);
        request.setAttribute("favouriteModel", model);

        return SUCCESS;
    }

    private static boolean isMutationSubmission(HttpServletRequest request) {
        String submit = request.getParameter("submit");
        String action = request.getParameter("action");
        return "Save".equals(submit) || ("Search".equals(submit) && "Delete".equals(action));
    }

    private static BillingOnLookupService.FavouriteMutationRequest favouriteMutationRequestFrom(
            HttpServletRequest request, LoggedInInfo loggedInInfo) {
        Map<String, String> fields = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, value) -> {
            if (value != null && value.length > 0) {
                fields.put(key, value[0]);
            }
        });
        String providerNo = loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null
                ? "" : loggedInInfo.getLoggedInProviderNo();
        return new BillingOnLookupService.FavouriteMutationRequest(
                request.getParameter("submit"),
                request.getParameter("action"),
                request.getParameter("name"),
                providerNo,
                fields);
    }
}
