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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Adds or removes a measurement group's decision support
 * ({@code encounter/oscarMeasurements/MeasurementGroupDecisionSupport}), from the Add and Remove pages' POST forms,
 * then shows the complete page. The group is the one the page showed (its form posts the name), it must still
 * exist, and an add takes only a decision support file the Add page lists.
 *
 * <p>POST only, with the measurement admin write right ({@code _admin.measurements} w), the right the Add, Remove
 * and complete pages require. The complete page itself only shows the result.</p>
 *
 * @since 2026-10-20
 */
public class EctMeasurementGroupDecisionSupport2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final MeasurementManager measurementManager;
    private final Supplier<List<String>> listedFiles;

    public EctMeasurementGroupDecisionSupport2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(MeasurementManager.class),
                MeasurementManager::getFlowsheetDsHTML);
    }

    EctMeasurementGroupDecisionSupport2Action(SecurityInfoManager securityInfoManager,
                                              MeasurementManager measurementManager,
                                              Supplier<List<String>> listedFiles) {
        this.securityInfoManager = securityInfoManager;
        this.measurementManager = measurementManager;
        this.listedFiles = listedFiles;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.measurements", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.measurements)");
        }

        // The group the page showed, not the session's current one: another tab may have opened a different group
        // since. A group deleted in the meantime has no id, and its setting would be stored under "null".
        String groupName = request.getParameter("groupName");
        String groupId = groupName == null || groupName.isBlank() ? null : measurementManager.findGroupId(groupName);
        String htmlName = request.getParameter("htmlName");
        if (groupId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (request.getParameter("add") != null) {
            // Only a file the Add page lists; its "Select Decision Support" placeholder ("-1") is not one.
            if (htmlName == null || !listedFiles.get().contains(htmlName)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            measurementManager.addMeasurementGroupDS(groupName, htmlName);
            request.setAttribute("dsState", "added");
        } else if (request.getParameter("remove") != null) {
            measurementManager.removeMeasurementGroupDS("mgroup.ds.html." + groupId);
            request.setAttribute("dsState", "removed");
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        return SUCCESS;
    }
}
