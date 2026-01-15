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


/*
 * DemographicMergeRecord2Action.java
 *
 * Created on September 11, 2007, 3:52 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.demographic.pageUtil;

import java.util.ArrayList;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.demographic.data.DemographicMerged;

/**
 * @author wrighd
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DemographicMergeRecord2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public DemographicMergeRecord2Action() {

    }

    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (request.getParameterValues("records") == null) {
            return "failure";
        }
        String outcome = "success";
        ArrayList<String> records = new ArrayList<String>(Arrays.asList(request.getParameterValues("records")));
        String head = request.getParameter("head");
        String action = request.getParameter("mergeAction");
        String provider_no = request.getParameter("provider_no");
        DemographicMerged dmDAO = new DemographicMerged();

        if (action.equals("merge") && head != null && records.size() > 1 && records.contains(head)) {

            for (int i = 0; i < records.size(); i++) {
                if (!(records.get(i)).equals(head))
                    dmDAO.Merge(loggedInInfo, records.get(i), head);

            }

        } else if (action.equals("unmerge") && records.size() > 0) {
            outcome = "successUnMerge";
            for (int i = 0; i < records.size(); i++) {
                String demographic_no = records.get(i);
                dmDAO.UnMerge(loggedInInfo, demographic_no, provider_no);

            }

        } else {
            outcome = "failure";
        }
        request.setAttribute("mergeoutcome", outcome);

        if (request.getParameter("caisiSearch") != null && request.getParameter("caisiSearch").equalsIgnoreCase("yes")) {
            outcome = "caisiSearch";
        }

        return outcome;
    }
}
