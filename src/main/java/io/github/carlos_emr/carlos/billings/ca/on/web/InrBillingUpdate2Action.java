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

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.InrBillingUpdateViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.InrBillingUpdateViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * View gate for {@code billing/CA/ON/inr/updateINRbilling.jsp}, the
 * INR-billing update form. Enforces {@code _billing} {@code w} privilege
 * AND POST-only before forwarding to the JSP. Populates an
 * {@link InrBillingUpdateViewModel} as request attribute
 * {@code inrUpdateModel} so the JSP body is pure presentation —
 * replacing the legacy in-JSP {@code SpringUtils.getBean(DemographicDao.class)}
 * lookup.
 *
 * @since 2026-04-13
 */
public class InrBillingUpdate2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final InrBillingUpdateViewModelAssembler assembler;

    public InrBillingUpdate2Action(SecurityInfoManager securityInfoManager,
                               InrBillingUpdateViewModelAssembler assembler) {
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
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

        request.setAttribute("inrUpdateModel", assembler.assemble(request));
        return SUCCESS;
    }
}
