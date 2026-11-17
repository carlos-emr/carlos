/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * First step of Customize Measurements ▸ Add Measurement Group: checks the new group's name
 * and hands the name and style sheet on to the type-selection page.
 *
 * <p>Nothing is written here. The group is created together with its first measurement
 * types by {@link EctAddMeasurementGroup2Action}, so closing the type-selection page, or any
 * failure before it, leaves no empty group in the charts' measurement menus (issue #3335).
 * The name is still checked here, including against existing groups, so a duplicate is
 * reported before the administrator chooses types.</p>
 *
 * <p>POST only: it is the target of the Define form.</p>
 */
public class EctDefineNewMeasurementGroup2Action extends ActionSupport {

    private final transient SecurityInfoManager securityInfoManager;
    private final transient MeasurementGroupCreationService creationService;

    public EctDefineNewMeasurementGroup2Action(SecurityInfoManager securityInfoManager,
                                               MeasurementGroupCreationService creationService) {
        this.securityInfoManager = securityInfoManager;
        this.creationService = creationService;
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
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.measurements", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.measurements)");
        }

        // Error-bundle entries contain embedded HTML (<li>...</li>) and the target JSP renders
        // actionErrors unencoded, so the {0} substitution must be HTML-encoded here.
        String safeGroupName = SafeEncode.forHtmlContent(groupName);

        if (!MeasurementGroupCreationService.isValidGroupName(groupName)) {
            return error(request, getText("errors.invalid", new String[]{safeGroupName}));
        }
        if (creationService.groupExists(groupName)) {
            return error(request, getText("error.encounter.addNewMeasurementGroup.duplicateGroupName",
                    new String[]{safeGroupName}));
        }

        // nosemgrep: tainted-session-from-http-request -- groupName passed isValidGroupName ([\w ,.?], at most 100 characters); admin-only action
        request.getSession().setAttribute("groupName", groupName);
        // Carried to the type-selection page and posted back with the chosen types.
        request.setAttribute("styleSheet", MeasurementGroupCreationService.parseCssId(styleSheet));
        return "continue";
    }

    private static String error(HttpServletRequest request, String message) {
        List<String> errors = new ArrayList<>();
        errors.add(message);
        request.setAttribute("actionErrors", errors);
        return "error";
    }

    private String groupName;

    public String getGroupName() {
        return this.groupName;
    }

    @StrutsParameter
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    private String styleSheet;

    public String getStyleSheet() {
        return this.styleSheet;
    }

    @StrutsParameter
    public void setStyleSheet(String styleSheet) {
        this.styleSheet = styleSheet;
    }
}
