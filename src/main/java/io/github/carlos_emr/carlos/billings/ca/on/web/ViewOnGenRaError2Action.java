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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaErrorViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.OnRaErrorViewModelAssembler;

/**
 * View gate for {@code billing/CA/ON/onGenRAError.jsp}. Enforces {@code _billing}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location. Created as part of the ON billing migration
 * to gate direct-access paths behind Struts2 actions (same pattern as
 * PR #1632 for BC billing).
 *
 * <p>Also assembles the {@link OnRaErrorViewModel} the JSP renders: the
 * provider dropdown options and (when a specific provider is selected) the
 * per-provider error rows. The view model is exposed as request attribute
 * {@code onGenRAErrorModel}; the JSP body became 100% EL on 2026-04-25.</p>
 *
 * @since 2026-04-13
 */
public class ViewOnGenRaError2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final OnRaErrorViewModelAssembler onGenRAErrorAssembler;

    public ViewOnGenRaError2Action(SecurityInfoManager securityInfoManager,
                                    OnRaErrorViewModelAssembler onGenRAErrorAssembler) {
        this.securityInfoManager = securityInfoManager;
        this.onGenRAErrorAssembler = onGenRAErrorAssembler;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        OnRaErrorViewModel model = onGenRAErrorAssembler.assemble(
                request.getParameter("rano"),
                request.getParameter("proNo"));
        request.setAttribute("onGenRAErrorModel", model);

        return SUCCESS;
    }
}
