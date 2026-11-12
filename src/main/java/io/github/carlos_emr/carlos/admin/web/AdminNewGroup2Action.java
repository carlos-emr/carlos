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

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.service.MyGroupMembershipService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Administration &#9656; Schedule Management: the Add a Group form, and the Delete of
 * Search/Edit/Delete Groups.
 *
 * <p>Requires {@code _admin.schedule.groupCreate} write privilege. Opening the form exposes the
 * create workflow, so it intentionally uses the same write-scoped object as the save action
 * ({@code AdminSaveMyGroup}), which is where new groups are posted.</p>
 *
 * <p>The group list ({@code admin/ViewAdminDisplayMyGroup}) posts its Delete here: the request
 * carries the {@code submit} button and one checkbox per selected membership. That is a mutation,
 * so it must be a POST; a delete intent on any other method is refused with 405 before anything is
 * removed. The memberships are read and removed by {@link MyGroupMembershipService}, and the page
 * then shows the deletion notice. Without {@code submit} the action only renders the form.</p>
 *
 * @since 2026-04-05
 */
public class AdminNewGroup2Action extends ActionSupport {

    /** Request attribute holding the number of rows a Delete removed; set only after a Delete. */
    public static final String MEMBERS_DELETED_ATTRIBUTE = "adminNewGroupMembersDeleted";

    /** The group list's Delete button; its presence is the delete intent. */
    static final String DELETE_INTENT_PARAMETER = "submit";

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient MyGroupMembershipService myGroupMembershipService;

    public AdminNewGroup2Action(SecurityInfoManager securityInfoManager,
            MyGroupMembershipService myGroupMembershipService) {
        this.securityInfoManager = securityInfoManager;
        this.myGroupMembershipService = myGroupMembershipService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.schedule.groupCreate", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.schedule.groupCreate)");
        }

        if (request.getParameter(DELETE_INTENT_PARAMETER) == null) {
            return SUCCESS;
        }

        // CSRFGuard does not validate GET, so a delete must never run on one.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        int deleted = myGroupMembershipService.deleteSelectedMembers(request.getParameterMap());
        request.setAttribute(MEMBERS_DELETED_ATTRIBUTE, deleted);
        return SUCCESS;
    }
}
