//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package ca.openosp.openo.vaccine;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.Spring;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.service.ClientManager;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class VaccineProviderReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    private ClientManager clientManager = SpringUtils.getBean(ClientManager.class);

    protected void postMessage(HttpServletRequest request, String key, String val) {
        addActionMessage(getText(key, val));
    }

    protected void postMessage(HttpServletRequest request, String key) {
        addActionMessage(getText(key));
    }

    protected String getProviderNo(HttpServletRequest request) {
        return (String) request.getSession().getAttribute("user");
    }

    public String execute() {
        return show_report();
    }

    /*
     * Client Name
     * DOB
     * Health Card
     * List of current Service programs (+contact info)
     * list of current issues
     * list of medications
     *
     */
    public String show_report() {
        String clientId = request.getParameter("id");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        Demographic client = clientManager.getClientByDemographicNo(clientId);

        if (client == null) {
            postMessage(request, "client.missing");
            log.warn("client not found");
            return "error";
        }

        String name = client.getFormattedName();
        String dob = client.getFormattedDob();
        String healthCard = client.getHin() + " " + client.getVer();


        request.setAttribute("demographicNo", clientId);
        request.setAttribute("client_name", name);
        request.setAttribute("client_dob", dob);
        request.setAttribute("client_healthCard", healthCard);

        //List allergies = this.caseManagementManager.getAllergies(clientId);
        //request.setAttribute("allergies",allergies);

        // No intake data available - functionality removed
        request.setAttribute("allergies", "N/A - Intake data not available");
        request.setAttribute("intakeMap", new java.util.HashMap<String, String>());


        return "report";
    }
}
