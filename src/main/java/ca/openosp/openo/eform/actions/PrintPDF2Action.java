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


// form_class - a part of class name
// c_lastVisited, formId - if the form has multiple pages
package ca.openosp.openo.eform.actions;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.eform.util.EFormPrintPDFUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class PrintPDF2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private Logger log = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws ServletException, IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "r", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        int newID = 0;
        String where = null;

        try {
            Properties props = new Properties();

            for (Enumeration<?> e = request.getParameterNames(); e.hasMoreElements(); ) {
                String name = (String) e.nextElement();
                props.setProperty(name, request.getParameter(name));
            }

            log.info("SUBMIT " + request.getParameter("submit"));
            //if we are graphing, we need to grab info from db and add it to request object
            if (request.getParameter("submit").equals("graph")) {
                props = EFormPrintPDFUtil.getFrmRourkeGraph(loggedInInfo, props);

                for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
                    String name = (String) e.nextElement();
                    request.setAttribute(name, props.getProperty(name));
                }
            }
            //if we are printing all pages of form, grab info from db and merge with current page info
            else if (request.getParameter("submit").equals("printAll")) {
                String name;
                for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
                    name = (String) e.nextElement();
                    if (request.getParameter(name) == null)
                        request.setAttribute(name, props.getProperty(name));
                }
            }

            String strAction = findActionValue(request.getParameter("submit"));
            if ("printAll".equals(strAction)) {
                where = "/eform/createpdf";
            } else if ("graph".equals(strAction)) {
                where = "/eform/createpdf";
            }
            where = createActionURL(where, strAction, request.getParameter("demographic_no"), "" + newID);
            response.sendRedirect(where);
        } catch (Exception ex) {
            throw new ServletException(ex);
        }

        return NONE;
    }


    private String findActionValue(String submit) {
        if (submit != null && submit.equalsIgnoreCase("graph")) {
            return "graph";
        } else if (submit != null && submit.equalsIgnoreCase("printall")) {
            return "printAll";
        } else {
            return "failure";
        }
    }

    private String createActionURL(String where, String action, String demoId, String formId) {
        String temp = null;

        if (action.equals("printAll")) {
            temp = where + "?demographic_no=" + demoId + "&formId=" + formId;
        } else {
            temp = where;
        }

        return temp;
    }

}
