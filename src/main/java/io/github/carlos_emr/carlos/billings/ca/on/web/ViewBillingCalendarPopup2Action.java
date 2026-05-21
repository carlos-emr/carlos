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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCalendarPopupViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCalendarPopupViewModelAssembler;

/**
 * View gate for {@code billing/CA/ON/billingCalendarPopup.jsp}. This is a
 * general-purpose date-picker popup used by multiple modules, not only billing:
 * it is also launched from the oscarReport Age/Sex report and the Tickler
 * demographic view. The gate therefore accepts any of {@code _billing},
 * {@code _report}, or {@code _tickler} read privileges — users who can reach
 * the parent screen must be able to use the date picker on it.
 *
 * <p>Also assembles the {@link BillingCalendarPopupViewModel} the JSP renders
 * (resolved year/month + week-by-week date grid + type echo). The view model
 * is exposed as request attribute {@code billingCalendarPopupModel}; the
 * JSP body became 100% EL on 2026-04-25.</p>
 *
 * @since 2026-04-13
 */
public class ViewBillingCalendarPopup2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public ViewBillingCalendarPopup2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        boolean allowed =
                securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)
                        || securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)
                        || securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", null);
        if (!allowed) {
            throw new SecurityException("missing required security object: _billing | _report | _tickler");
        }

        BillingCalendarPopupViewModel model;
        try {
            model = new BillingCalendarPopupViewModelAssembler().assemble(
                    request.getParameter("year"),
                    request.getParameter("month"),
                    request.getParameter("delta"),
                    request.getParameter("type"));
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return NONE;
        }
        request.setAttribute("billingCalendarPopupModel", model);

        return SUCCESS;
    }
}
