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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.tickler.TicklerData;

/**
 * @author Jay Gallagher
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class AddTickler2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public AddTickler2Action() {
    }

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_tickler", "w", null)) {
            throw new RuntimeException("missing required sec object (_tickler)");
        }

        String[] demos = request.getParameterValues("demo");
        String message = request.getParameter("message");
        String status = request.getParameter("status");
        String service_date = request.getParameter("date");
        String creator = (String) request.getSession().getAttribute("user");
        String priority = request.getParameter("priority");
        String task_assigned_to = request.getParameter("assignedTo");

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

        Tickler.STATUS tStatus = Tickler.STATUS.A;
        if (status.equals(TicklerData.COMPLETED)) {
            tStatus = Tickler.STATUS.C;
        }
        if (status.equals(TicklerData.DELETED)) {
            tStatus = Tickler.STATUS.D;
        }

        Tickler.PRIORITY tPriority = Tickler.PRIORITY.Normal;
        if (priority.equals(TicklerData.HIGH)) {
            tPriority = Tickler.PRIORITY.High;
        }
        if (priority.equals(TicklerData.LOW)) {
            tPriority = Tickler.PRIORITY.Low;
        }

        if (demos != null) {
            for (int i = 0; i < demos.length; i++) {

                ticklerManager.addTickler(demos[i], message, tStatus, service_date, creator, tPriority, task_assigned_to);
            }
        }
        return "close";
    }
}
