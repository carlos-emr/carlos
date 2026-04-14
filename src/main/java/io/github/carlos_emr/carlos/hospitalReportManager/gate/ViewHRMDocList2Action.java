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
package io.github.carlos_emr.carlos.hospitalReportManager.gate;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Read-only view gate for {@code displayHRMDocList.jsp} -- the per-patient
 * HRM document popup opened from the encounter left navbar. Previously
 * reached via a direct GET to the public JSP URL with only the in-JSP
 * {@code <security:oscarSec>} taglib as protection. Now routed through
 * {@code /hospitalReportManager/ViewDocList.do} which enforces {@code _hrm r}
 * at the action layer before the container forwards.
 */
public final class ViewHRMDocList2Action extends ActionSupport {

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        SecurityInfoManager sim = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !sim.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            throw new SecurityException("missing required sec object (_hrm r)");
        }
        return SUCCESS;
    }
}
