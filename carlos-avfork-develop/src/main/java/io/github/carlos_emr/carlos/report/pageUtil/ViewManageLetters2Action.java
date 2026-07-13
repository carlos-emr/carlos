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

package io.github.carlos_emr.carlos.report.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Setup action that renders the Manage Letters page for viewing and uploading
 * report letter templates. Separates form rendering from upload processing
 * handled by {@link ManagePatientLetters2Action}.
 *
 * @since 2026-04-04
 */
public final class ViewManageLetters2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Verifies the logged-in user has report read privileges,
     * then forwards to the Manage Letters JSP.
     *
     * @return String {@link ActionSupport#SUCCESS} on successful authorization
     * @throws SecurityException if the user lacks {@code _report} read privilege
     */
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required security object (_report)");
        }

        return SUCCESS;
    }
}
