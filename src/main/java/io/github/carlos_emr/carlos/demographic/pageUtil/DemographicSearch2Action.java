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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for demographic search. Replaces the {@code demographiccontrol.jsp}
 * {@code displaymode=Search} route. Performs security validation and routes to the
 * appropriate result JSP. The target JSPs handle their own data loading via DAOs.
 *
 * <p>Routes to {@code demographicsearchresults.jsp} for general search, or
 * {@code demographicsearch2apptresults.jsp} for appointment-context search
 * (distinguished by a trailing space in the {@code displaymode} parameter, a
 * convention still used by appointment-context callers).</p>
 *
 * @since 2026-04-04
 */
public class DemographicSearch2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing required session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        // "Search " (with trailing space) is passed by appointment-context callers
        // (appointmentcontrol.jsp, ticklerAdd.jsp, PatientSearch.jsp, etc.)
        // to distinguish appointment search from general demographic search.
        String displaymode = request.getParameter("displaymode");
        if ("Search ".equals(displaymode)) {
            return "apptResults";
        }
        return SUCCESS;
    }
}
