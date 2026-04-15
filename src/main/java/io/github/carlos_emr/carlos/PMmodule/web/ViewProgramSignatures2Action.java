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
package io.github.carlos_emr.carlos.PMmodule.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code PMmodule/Admin/ProgramSignatures.jsp}. The PMmodule tree is filter-gated
 * by {@link PMMFilter} for session presence, but the JSPs themselves have no
 * privilege check. This action adds {@code _admin} read enforcement and
 * forwards to the JSP at its {@code /WEB-INF/jsp/} location.
 * <p>
 * The HTTP method is not enforced here because the gate performs no state
 * mutation; only {@code _admin} read is required.
 * <p>
 * Created as part of the PMmodule security-hardening migration (defense in
 * depth; matches the 2Action gate pattern from PR #1109, #1629, #1632, #1644).
 *
 * @since 2026-04-13
 */
public final class ViewProgramSignatures2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Validates the current user's {@code _admin} read privilege before
     * forwarding to the program signatures JSP via the Struts {@code success}
     * result.
     *
     * @return {@link #SUCCESS} when access is authorized
     * @throws Exception never thrown here; declared to satisfy the overridden signature
     * @throws SecurityException if the current user lacks {@code _admin} read privilege
     * @since 2026-04-13
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        return SUCCESS;
    }
}
