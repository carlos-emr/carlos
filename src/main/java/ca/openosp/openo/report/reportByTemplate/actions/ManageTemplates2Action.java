//CHECKSTYLE:OFF
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
 */

//Action that takes place when adding or editing template XML
/*
 * GenerateReport.java
 *
 * Created on March 02/2007, 10:47 PM
 *
 */

package ca.openosp.openo.report.reportByTemplate.actions;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.LoggedInInfo;

import ca.openosp.openo.report.reportByTemplate.ReportManager;

/**
 * @author apavel (Paul)
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ManageTemplates2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String action = request.getParameter("action");
        String templateId = request.getParameter("templateid");
        String xmltext = request.getParameter("xmltext");
        String uuid = request.getParameter("uuid");
        ReportManager reportManager = new ReportManager();
        String message = "Error: Improper request - Action param missing";
        if ("delete".equals(action)) {
            message = reportManager.deleteTemplate(templateId);
            if (message.equals("")) return "deleted";
        } else if ("add".equals(action)) {
            message = reportManager.addTemplate(uuid, xmltext, loggedInInfo);
        } else if ("edit".equals(action)) {
            message = reportManager.updateTemplate(uuid, templateId, xmltext, loggedInInfo);
        }
        request.setAttribute("message", message);
        request.setAttribute("action", action);
        request.setAttribute("templateid", request.getParameter("templateid"));
        request.setAttribute("opentext", request.getParameter("opentext"));

        if (request.getParameter("done") != null
                && "done".equalsIgnoreCase(request.getParameter("done"))
                && !message.toLowerCase().startsWith("error")
                && !message.toLowerCase().startsWith("exception")) {
            return "done";
        }

        return SUCCESS;
    }

}
