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
package io.github.carlos_emr.carlos.providers.pageUtil;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.service.MyGroupMembershipService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Preferences &#9656; View groups &#9656; Delete ({@code provider/DeleteMyGroupMembers}): removes
 * the selected group memberships, then redirects back to the group list.
 *
 * <p>The delete used to run inside {@code providernewgroup.jsp}, reached through
 * {@code providercontrol.jsp}'s server-side include. A response redirect issued from an included
 * page is ignored by the container, so every Delete left the user on a blank page (issue #4515),
 * and because the include route accepts GET, the delete could be triggered by a link. This action
 * is reached directly, accepts only POST, needs {@code _admin} write (the privilege the old
 * {@code provider/ViewProviderNewGroup} host required) and ends with a POST/redirect/GET to
 * {@code provider/ViewProviderDisplayMyGroup}.</p>
 *
 * @since 2026-10-15
 */
public class DeleteMyGroupMembers2Action extends ActionSupport {

    /** Where the browser lands after a Delete: the group list the form was posted from. */
    static final String GROUP_LIST_ROUTE = "/provider/ViewProviderDisplayMyGroup";

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient MyGroupMembershipService myGroupMembershipService;

    public DeleteMyGroupMembers2Action(SecurityInfoManager securityInfoManager,
            MyGroupMembershipService myGroupMembershipService) {
        this.securityInfoManager = securityInfoManager;
        this.myGroupMembershipService = myGroupMembershipService;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: the target is the context path plus a fixed in-app route; no request value reaches it.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT",
            justification = "the target is the context path plus a fixed in-app route; no request value reaches it")
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // CSRFGuard does not validate GET, so the delete must never run on one.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        myGroupMembershipService.deleteSelectedMembers(request.getParameterMap());
        response.sendRedirect(request.getContextPath() + GROUP_LIST_ROUTE);
        return NONE;
    }
}
