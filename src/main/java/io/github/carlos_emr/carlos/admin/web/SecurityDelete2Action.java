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
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Admin action for permanently deleting a security (user account) record.
 *
 * <p>Requires either {@code _admin w} or {@code _admin.userAdmin w} privilege and POST method.
 * Performs a hard delete via {@link SecurityDao} and logs the action for the audit trail.</p>
 *
 * @since 2026-04-05
 */
public class SecurityDelete2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        String securityNoStr = request.getParameter("keyword");
        if (securityNoStr != null && !securityNoStr.isEmpty()) {
            executeDelete(request, loggedInInfo, securityNoStr);
        } else {
            request.setAttribute("msg", "No security identifier was provided.");
        }

        return SUCCESS;
    }

    /**
     * Parses the security ID, locates the entity, removes it, and sets a feedback message.
     *
     * @param loggedInInfo the current session context
     * @param securityNoStr the raw security-number parameter value
     */
    private void executeDelete(HttpServletRequest request, LoggedInInfo loggedInInfo, String securityNoStr) {
        try {
            int securityNo = Integer.parseInt(securityNoStr);
            Security entity = securityDao.find(securityNo);
            if (entity != null) {
                String userName = entity.getUserName();
                try {
                    securityDao.remove(entity);
                } catch (RuntimeException e) {
                    MiscUtils.getLogger().error("Failed to delete security entry", e);
                    request.setAttribute("msg", "Failed to delete security entry.");
                    return;
                }
                try {
                    LogAction.addLog(
                        loggedInInfo.getLoggedInProviderNo(),
                        LogConst.DELETE,
                        LogConst.CON_SECURITY,
                        securityNoStr,
                        request.getRemoteAddr()
                    );
                } catch (RuntimeException e) {
                    MiscUtils.getLogger().error("Audit log failed after security entry deletion", e);
                }
                request.setAttribute("msg", "Security entry deleted for user: ".concat(userName));
            } else {
                request.setAttribute("msg", "Security entry not found.");
            }
        } catch (NumberFormatException e) {
            request.setAttribute("msg", "Invalid security identifier.");
        }
    }
}
