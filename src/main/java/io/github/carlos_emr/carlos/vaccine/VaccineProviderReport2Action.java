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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.vaccine;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.service.ClientManager;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action that generates a vaccine provider report for a specific patient.
 *
 * <p>Retrieves patient demographic information (name, date of birth, health card number)
 * and prepares it as request attributes for the report view. Used within the program
 * management module for vaccine administration tracking.</p>
 *
 * @see ClientManager
 * @since 2026-03-17
 */
public class VaccineProviderReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    private ClientManager clientManager = SpringUtils.getBean(ClientManager.class);

    /**
     * Posts a localized action message with a parameter value.
     *
     * @param request HttpServletRequest the current HTTP request
     * @param key String the message key for localization
     * @param val String the parameter value to include in the message
     */
    protected void postMessage(HttpServletRequest request, String key, String val) {
        addActionMessage(getText(key, val));
    }

    /**
     * Posts a localized action message.
     *
     * @param request HttpServletRequest the current HTTP request
     * @param key String the message key for localization
     */
    protected void postMessage(HttpServletRequest request, String key) {
        addActionMessage(getText(key));
    }

    /**
     * Retrieves the current provider number from the HTTP session.
     *
     * @param request HttpServletRequest the current HTTP request
     * @return String the provider number stored in the session
     */
    protected String getProviderNo(HttpServletRequest request) {
        return (String) request.getSession().getAttribute("user");
    }

    /**
     * Delegates to {@link #show_report()}.
     *
     * @return String the Struts2 result name
     */
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
    /**
     * Generates the vaccine provider report by loading patient demographics and setting
     * them as request attributes for the report view.
     *
     * @return String "report" on success, "error" if the client is not found
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
