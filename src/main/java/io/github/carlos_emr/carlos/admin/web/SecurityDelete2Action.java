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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.owasp.encoder.Encode;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Admin action for permanently deleting a security (user account) record.
 *
 * <p>Requires either {@code _admin w} or {@code _admin.userAdmin w} privilege and POST method.
 * Performs a hard delete via {@link SecurityDao} and logs the action for the audit trail.</p>
 *
 * @since 2026-05-01
 */
public class SecurityDelete2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);

    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(405);
            return NONE;
        }

        String securityNoStr = request.getParameter("security_no");
        if (securityNoStr != null && !securityNoStr.isEmpty()) {
            try {
                int securityNo = Integer.parseInt(securityNoStr);
                Security record = securityDao.find(securityNo);
                if (record != null) {
                    String userName = record.getUserName();
                    securityDao.remove(record);

                    LogAction.addLog(
                        loggedInInfo.getLoggedInProviderNo(),
                        LogConst.DELETE,
                        LogConst.CON_PRIVILEGE,
                        securityNoStr,
                        request.getRemoteAddr()
                    );
                    request.setAttribute("msg", "Security record deleted for user: " + Encode.forHtml(userName));
                } else {
                    request.setAttribute("msg", "Security record not found.");
                }
            } catch (NumberFormatException e) {
                request.setAttribute("msg", "Invalid security record identifier.");
            }
        }

        return SUCCESS;
    }
}
