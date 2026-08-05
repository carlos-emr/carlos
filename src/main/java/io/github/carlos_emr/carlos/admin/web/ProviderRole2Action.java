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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Security gate for the Provider Role admin page.
 *
 * <p>Viewing requires either {@code _admin} or {@code _admin.userAdmin} read
 * privilege. The page uses separate parameter names for add/delete, role updates,
 * and primary-role updates, so mutation intent is detected by parameter presence
 * rather than by localized button labels. Write intents additionally require POST
 * (non-POST receives HTTP 405) and <em>write</em> privilege on one of those
 * security objects, because they change role assignments. Read-only display
 * requests are forwarded directly to the JSP.</p>
 *
 * @since 2026-04-05
 */
public class ProviderRole2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        boolean isWriteOperation = request.getParameter("submit") != null
                || request.getParameter("buttonUpdate") != null
                || request.getParameter("buttonSetPrimaryRole") != null;

        if (isWriteOperation) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
                return NONE;
            }

            /* Read rights are enough to browse the provider/role roster, but the JSP behind
             * this gate adds, updates and deletes secUserRole rows and rewrites
             * program_provider.role_id — privilege changes that a read-only administrator
             * must not be able to make on themselves. hasPrivilege("r") is satisfied by
             * r|u|w|x, so the write intents need their own write-rights check, matching
             * ProviderPrivilege2Action.
             */
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                    && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null)) {
                throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
            }
        }

        return SUCCESS;
    }
}
