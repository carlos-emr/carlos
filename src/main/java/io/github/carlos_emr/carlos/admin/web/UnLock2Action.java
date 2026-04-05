/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.admin.web;

import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.owasp.encoder.Encode;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.login.LoginCheckLogin;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Admin action for unlocking locked user accounts.
 *
 * <p>Requires any of {@code _admin r}, {@code _admin.userAdmin r}, or
 * {@code _admin.unlockAccount r} privilege. On POST with a {@code submit} parameter,
 * unlocks the specified username via {@link LoginCheckLogin} and adds an audit log entry.
 * Always loads the current lock list into the {@code lockList} request attribute.</p>
 *
 * @since 2026-05-01
 */
public class UnLock2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.unlockAccount", "r", null)) {
            throw new SecurityException("missing required sec object (_admin, _admin.userAdmin, or _admin.unlockAccount)");
        }

        String submit = request.getParameter("submit");
        if ("POST".equalsIgnoreCase(request.getMethod()) && submit != null) {
            String userName = request.getParameter("userName");
            if (userName != null && !userName.isEmpty()) {
                LoginCheckLogin loginCheckLogin = new LoginCheckLogin();
                loginCheckLogin.unlock(userName);

                String providerNo = loggedInInfo.getLoggedInProviderNo();
                String ip = request.getRemoteAddr();
                LogAction.addLog(providerNo, "unlock", "adminUnlock", userName, ip);

                request.setAttribute("msg", "Account unlocked: " + Encode.forHtml(userName));
            }
        }

        // Expose whether the site-access privacy feature is active for the JSP
        boolean isSiteAccessPrivacy = securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null);
        request.setAttribute("isSiteAccessPrivacy", isSiteAccessPrivacy);

        // Always load the current lock list
        LoginCheckLogin loginCheckLogin = new LoginCheckLogin();
        @SuppressWarnings("unchecked")
        Vector<String> lockList = loginCheckLogin.findLockList();
        request.setAttribute("lockList", lockList);

        return SUCCESS;
    }
}
