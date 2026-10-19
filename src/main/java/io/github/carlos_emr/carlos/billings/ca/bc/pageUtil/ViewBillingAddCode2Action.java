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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Read-only gate for {@code billing/CA/BC/billingAddCode.jsp}: the form that adds a BC billing code.
 * Requires {@code _billing} read (the writer, BillingAddCode2Action, needs {@code _billing} write) and
 * {@code _admin.billing} or {@code _admin} write (the page's own check).
 *
 * <p>It only checks rights and forwards; the page loads its own data and its forms post to the writer
 * action. The BC billing administration menu and the page's own links open the page through this
 * gate, not through the writer.</p>
 *
 * @since 2026-10-16
 */
public final class ViewBillingAddCode2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public ViewBillingAddCode2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        BcBillingAdminPageAccess.requireSession(loggedInInfo);
        BcBillingAdminPageAccess.requireAny(securityInfoManager, loggedInInfo, "r", "_billing");
        BcBillingAdminPageAccess.requireAny(securityInfoManager, loggedInInfo, "w", "_admin.billing", "_admin");
        return SUCCESS;
    }
}
