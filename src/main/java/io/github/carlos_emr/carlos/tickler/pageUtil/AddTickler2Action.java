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


package io.github.carlos_emr.carlos.tickler.pageUtil;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.tickler.TicklerData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 Action for adding new ticklers.
 * Handles the HTTP request to create one or more ticklers based on user input.
 * Supports creating ticklers for multiple demographics simultaneously.
 *
 * @since 2026-05-05
 */
public class AddTickler2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Default constructor.
     *
     * @since 2026-05-05
     */
    public AddTickler2Action() {
    }

    /**
     * Executes the action to add a new tickler.
     * Validates user permissions, extracts tickler details from the request,
     * applies defaults if necessary, and delegates creation to the TicklerManager.
     *
     * @return {@code "close"} on success, or {@link ActionSupport#NONE} when the request method is rejected
     * @throws IOException if sending the HTTP 405 response fails
     * @since 2026-05-05
     */
    @Override
    public String execute() throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        // Verify the user has write access to the _tickler security object
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "w", null)) {
            throw new SecurityException("missing required sec object (_tickler)");
        }

        // Extract parameters from the HTTP request
        String[] demos = request.getParameterValues("demo"); // Can create for multiple patients at once
        String message = request.getParameter("message");
        String status = request.getParameter("status");
        String service_date = request.getParameter("date");
        String creator = (String) request.getSession().getAttribute("user");
        String priority = request.getParameter("priority");
        String task_assigned_to = request.getParameter("assignedTo");

        // Apply default values for missing parameters
        if (status == null) {
            status = TicklerData.ACTIVE;
        }
        if (service_date == null) {
            service_date = "now()";
        }
        if (priority == null) {
            priority = TicklerData.NORMAL;
        }
        if (task_assigned_to == null) {
            task_assigned_to = creator;
        }

        // Map string status to the internal Enum representation
        Tickler.STATUS tStatus = Tickler.STATUS.A;
        if (status.equals(TicklerData.COMPLETED)) {
            tStatus = Tickler.STATUS.C;
        }
        if (status.equals(TicklerData.DELETED)) {
            tStatus = Tickler.STATUS.D;
        }

        // Map string priority to the internal Enum representation
        Tickler.PRIORITY tPriority = Tickler.PRIORITY.Normal;
        if (priority.equals(TicklerData.HIGH)) {
            tPriority = Tickler.PRIORITY.High;
        }
        if (priority.equals(TicklerData.LOW)) {
            tPriority = Tickler.PRIORITY.Low;
        }

        // Create a tickler for each provided demographic number
        if (demos != null) {
            for (int i = 0; i < demos.length; i++) {
                ticklerManager.addTickler(demos[i], message, tStatus, service_date, creator, tPriority, task_assigned_to);
            }
        }
        
        // Return "close" result to trigger UI closing logic
        return "close";
    }
}
