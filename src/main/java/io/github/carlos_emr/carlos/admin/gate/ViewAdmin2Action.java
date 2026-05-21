/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code admin/admin.jsp}. Requires {@code r} on any of:
 * <ul>
 *   <li>{@code _admin}</li>
 *   <li>{@code _admin.userAdmin}</li>
 *   <li>{@code _admin.schedule}</li>
 *   <li>{@code _admin.billing}</li>
 *   <li>{@code _admin.invoices}</li>
 *   <li>{@code _admin.resource}</li>
 *   <li>{@code _admin.reporting}</li>
 *   <li>{@code _admin.backup}</li>
 *   <li>{@code _admin.messenger}</li>
 *   <li>{@code _admin.eform}</li>
 *   <li>{@code _admin.encounter}</li>
 *   <li>{@code _admin.misc}</li>
 *   <li>{@code _admin.torontoRfq}</li>
 *   <li>{@code _admin.flowsheet}</li>
 * </ul>
 * (matches the source JSP's {@code <security:oscarSec>} OR-list) before forwarding
 * to the JSP at its {@code /WEB-INF/jsp/admin/} location. Part of the admin module
 * security-hardening migration (defense in depth; matches the 2Action
 * gate pattern from #1109, #1629, #1632, #1644, #1662, #1663).
 *
 * @since 2026-04-13
 */
public final class ViewAdmin2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        boolean authorized = securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.schedule", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.invoices", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.resource", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.reporting", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.backup", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.messenger", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.eform", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.encounter", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.torontoRfq", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.flowsheet", "r", null);

        if (!authorized) {
            throw new SecurityException("missing required security object: _admin or _admin.userAdmin or _admin.schedule or _admin.billing or _admin.invoices or _admin.resource or _admin.reporting or _admin.backup or _admin.messenger or _admin.eform or _admin.encounter or _admin.misc or _admin.torontoRfq or _admin.flowsheet");
        }

        return SUCCESS;
    }
}
