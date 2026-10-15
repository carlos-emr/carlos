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
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctTypeDisplayNameBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService.Outcome;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Second step of Customize Measurements ▸ Add Measurement Group: creates the group named in
 * the Define step together with the measurement types chosen for it.
 *
 * <p>The group and its members are written by {@link MeasurementGroupCreationService} in one
 * transaction, so the charts' measurement menus never list a group without types. A submit
 * with no type chosen, or for a name that has meanwhile been taken, writes nothing and shows
 * the page again with the reason. On success the editor for the new group opens, as before.</p>
 *
 * <p>POST only. The page offers only "add": a group that does not exist yet has no member to
 * remove, and members of existing groups are edited through {@code EditMeasurementGroup}.</p>
 */
public class EctAddMeasurementGroup2Action extends ActionSupport {

    private final transient SecurityInfoManager securityInfoManager;
    private final transient MeasurementGroupCreationService creationService;

    public EctAddMeasurementGroup2Action(SecurityInfoManager securityInfoManager,
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

        // The Define step accepted only such names, so anything else here was not typed into
        // that form. Refused before any use or session storage (CWE-501).
        if (!MeasurementGroupCreationService.isValidGroupName(groupName)) {
            throw new SecurityException("Invalid measurement group name");
        }
        HttpSession session = request.getSession();
        // nosemgrep: tainted-session-from-http-request -- groupName passed isValidGroupName ([\w ,.?], at most 100 characters); admin-only action
        session.setAttribute("groupName", groupName);
        int cssId = MeasurementGroupCreationService.parseCssId(styleSheet);

        // Anything but the Add button (the form's default forward is "error") writes nothing.
        Outcome outcome = Outcome.NO_TYPES;
        if ("add".equals(forward)) {
            List<String> types = selectedAddTypes == null ? List.of() : Arrays.asList(selectedAddTypes);
            outcome = creationService.create(groupName, cssId, types);
        }
        switch (outcome) {
            case CREATED:
                return SUCCESS;
            case DUPLICATE_NAME:
                return showAgain(request, session, cssId,
                        "error.encounter.addNewMeasurementGroup.duplicateGroupName");
            default:
                return showAgain(request, session, cssId,
                        "error.encounter.addNewMeasurementGroup.noTypesSelected");
        }
    }

    /**
     * Shows the type-selection page again with one error. The type list is reloaded because
     * the session copy is shared with the group editor, which stores a different list there.
     */
    private String showAgain(HttpServletRequest request, HttpSession session, int cssId, String errorKey) {
        // Error-bundle entries contain embedded HTML (<li>...</li>) and the JSP renders
        // actionErrors unencoded, so the {0} substitution must be HTML-encoded here.
        List<String> errors = new ArrayList<>();
        errors.add(getText(errorKey, new String[]{SafeEncode.forHtmlContent(groupName)}));
        request.setAttribute("actionErrors", errors);
        request.setAttribute("styleSheet", cssId);
        // nosemgrep: tainted-session-from-http-request -- DAO result list from EctTypeDisplayNameBeanHandler (all types)
        session.setAttribute("allTypeDisplayNames", new EctTypeDisplayNameBeanHandler().getTypeDisplayNameVector());
        return INPUT;
    }

    private String[] selectedAddTypes;
    private String forward;
    private String groupName;
    private String styleSheet;

    public String[] getSelectedAddTypes() {
        return selectedAddTypes;
    }

    @StrutsParameter
    public void setSelectedAddTypes(String[] selectedAddTypes) {
        this.selectedAddTypes = selectedAddTypes;
    }

    public String getForward() {
        return forward;
    }

    @StrutsParameter
    public void setForward(String forward) {
        this.forward = forward;
    }

    public String getGroupName() {
        return groupName;
    }

    @StrutsParameter
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getStyleSheet() {
        return styleSheet;
    }

    @StrutsParameter
    public void setStyleSheet(String styleSheet) {
        this.styleSheet = styleSheet;
    }
}
