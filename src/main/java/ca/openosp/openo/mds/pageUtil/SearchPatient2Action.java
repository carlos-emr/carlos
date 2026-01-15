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


package ca.openosp.openo.mds.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.ca.on.CommonLabResultData;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class SearchPatient2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public SearchPatient2Action() {
    }

    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String labNo = request.getParameter("segmentID");
        String name = request.getParameter("name");
        String labType = request.getParameter("labType");
        String newURL = "";
        String contextPath = request.getContextPath();

        try {
            String demographicNo = CommonLabResultData.searchPatient(labNo, labType);
            if (!demographicNo.equals("0")) {
                newURL = contextPath + "/oscarMDS/PatientSearch.jsp?search_mode=search_name&amp;limit1=0&amp;limit2=10";
                newURL = newURL + "&demographicNo=" + demographicNo;
            } else {
                newURL = contextPath + "/oscarMDS/OpenEChart.jsp";
            }
        } catch (Exception e) {
            MiscUtils.getLogger().debug("exception in SearchPatient2Action:" + e);
            newURL = contextPath + "/oscarMDS/OpenEChart.jsp";
        }
        
        if (newURL.indexOf("?") == -1) {
            newURL = newURL + "?";
        } else {
            newURL = newURL + "&";
        }
        newURL = newURL + "labNo=" + labNo + "&labType=" + labType + "&keyword=" + java.net.URLEncoder.encode(name, "UTF-8");

        response.sendRedirect(newURL);
        return NONE;
    }
}
