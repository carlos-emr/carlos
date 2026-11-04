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


package io.github.carlos_emr.carlos.encounter.immunization.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.Immunizations;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.github.carlos_emr.carlos.encounter.immunization.data.EctImmImmunizationData;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.util.UtilXML;

/**
 * @author Jay Gallagher
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctImmDeleteImmSchedule2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Creates a new instance of EctImmDeleteImmSchedule2Action
     */
    public EctImmDeleteImmSchedule2Action() {
    }

    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        String method = request.getParameter("method");
        String id = request.getParameter("tblSet");
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        // Schedule.jsp posts the patient it shows as demographic_no. It used to post demoNo, a name
        // the redisplayed schedule never read, so after a delete or restore the page fell back to
        // the login-wide EctSessionBean and showed whichever chart was entered last (issue #4673).
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo;
        try {
            demographicNo = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidPatient.getMessage());
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", demographicNo)
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(demographicNo))) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (!"delete".equals(method) && !"restore".equals(method)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        int setnum;
        int expectedVersion;
        try {
            setnum = Integer.parseInt(id);
            expectedVersion = Integer.parseInt(request.getParameter("scheduleVersion"));
            if (setnum < 0 || expectedVersion <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A schedule version and set are required. Reload the schedule.");
            return NONE;
        }


        try {
            EctImmImmunizationData immData = new EctImmImmunizationData();
            Immunizations current = immData.getCurrentSchedule(demographicNo);
            if (current == null || current.getId() != expectedVersion) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "The immunization schedule changed. Reload it before saving, deleting, or restoring.");
                return NONE;
            }
            Document doc = UtilXML.parseXML(current.getImmunizations());
            NodeList sets = doc.getElementsByTagName("immunizationSet");
            if (setnum >= sets.getLength()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            Element set = (Element) sets.item(setnum);

            if (method.equals("delete")) {
                set.setAttribute("status", "deleted");
            } else {
                set.removeAttribute("status");
            }


            String sXML = UtilXML.toXML(doc);

            //EctImmImmunizationData imm = new EctImmImmunizationData();
            if (!immData.saveImmunizations(demographicNo, providerNo, sXML, expectedVersion)) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "The immunization schedule changed. Reload it before saving, deleting, or restoring.");
                return NONE;
            }
        } catch (Exception ex) {
            throw new ServletException("Exception occurred in EctImmDeleteImmSchedule2Action", ex);
        }

        return SUCCESS;
    }


}
