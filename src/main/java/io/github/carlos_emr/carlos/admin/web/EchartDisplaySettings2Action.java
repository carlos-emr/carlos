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
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Security gate for the eChart Display Settings admin page.
 *
 * <p>Requires {@code _admin} read privilege before forwarding to the JSP, matching
 * {@link BillingSettings2Action}. The finer-grained {@code _admin.encounter} object
 * referenced by the eChart nav section in {@code leftNav.jspf} has no grants in seed
 * security data (it gates nav visibility only, in an OR list with {@code _admin}), so
 * gating this action on it would lock out every role, admins included.
 * POST enforcement for save operations is handled by the JSP itself.</p>
 *
 * @since 2026-09-18
 */
public class EchartDisplaySettings2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String requiredPrivilege = "POST".equalsIgnoreCase(request.getMethod()) ? "w" : "r";
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", requiredPrivilege, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        return SUCCESS;
    }
}
