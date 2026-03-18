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


package io.github.carlos_emr.carlos.commn.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.utility.AppointmentUtil;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData.Provider;

/**
 * Struts 2 action providing JSON autocomplete results for demographic (patient) searches.
 * Used by appointment booking and other patient-lookup flows.
 *
 * @since 2026-02-20
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class SearchDemographicAutoComplete2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);



    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        boolean outOfDomain = false;
        if (request.getParameter("outofdomain") != null && request.getParameter("outofdomain").equals("true")) {
            outOfDomain = true;
        }

        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
        String searchStr = request.getParameter("demographicKeyword");

        if (searchStr == null) {
            searchStr = request.getParameter("query");
        }

        if (searchStr == null) {
            searchStr = request.getParameter("name");
        }

        if (searchStr == null) {
            searchStr = request.getParameter("term");
        }

        boolean activeOnly = false;
        activeOnly = request.getParameter("activeOnly") != null && request.getParameter("activeOnly").equalsIgnoreCase("true");
        boolean jqueryJSON = request.getParameter("jqueryJSON") != null && request.getParameter("jqueryJSON").equalsIgnoreCase("true");
        RxProviderData rx = new RxProviderData();


        List<Demographic> list = null;

        if (searchStr.length() == 8 && searchStr.matches("([0-9]*)")) {
            list = demographicDao.searchDemographicByDOB(searchStr.substring(0, 4) + "-" + searchStr.substring(4, 6) + "-" + searchStr.substring(6, 8), 100, 0, providerNo, outOfDomain);
        } else if (activeOnly) {
            OscarProperties props = OscarProperties.getInstance();
            String pstatus = props.getProperty("inactive_statuses", "IN, DE, IC, ID, MO, FI");
            pstatus = pstatus.replaceAll("'", "").replaceAll("\\s", "");
            List<String> stati = Arrays.asList(pstatus.split(","));

            list = demographicDao.searchDemographicByNameAndNotStatus(searchStr, stati, 100, 0, providerNo, outOfDomain);
            if (list.size() == 100) {
                MiscUtils.getLogger().warn("More results exists than returned");
            }
        } else {
            list = demographicDao.searchDemographicByName(searchStr, 100, 0, providerNo, outOfDomain);
            if (list.size() == 100) {
                MiscUtils.getLogger().warn("More results exists than returned");
            }
        }


        // Hoist DAO lookup outside loop to avoid N+1 bean resolution on every iteration
        DemographicCustDao demographicCustDao = (DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class);

        List<HashMap<String, String>> secondList = new ArrayList<HashMap<String, String>>();
        for (Demographic demo : list) {
            HashMap<String, String> h = new HashMap<String, String>();
            h.put("fomattedDob", demo.getFormattedDob());
            h.put("formattedName", demo.getFormattedName());
            h.put("demographicNo", String.valueOf(demo.getDemographicNo()));
            h.put("status", demo.getPatientStatus() != null ? demo.getPatientStatus() : "");
            h.put("rosterStatus", demo.getRosterStatus() != null ? demo.getRosterStatus() : "");


            Provider p = rx.getProvider(demo.getProviderNo());
            if (demo.getProviderNo() != null) {
                h.put("providerNo", demo.getProviderNo());
            }
            if (p.getSurname() != null && p.getFirstName() != null) {
                h.put("providerName", p.getSurname() + ", " + p.getFirstName());
            }

            DemographicCust demographicCust = demographicCustDao.find(demo.getDemographicNo());

            String alertText = (demographicCust != null && demographicCust.getAlert() != null) ? demographicCust.getAlert() : "";
            h.put("alert", alertText);

            if (OscarProperties.getInstance().isPropertyActive("workflow_enhance")) {
                h.put("nextAppointment", AppointmentUtil.getNextAppointment(demo.getDemographicNo() + ""));

                if (demographicCust != null) {
                    String cust1 = StringUtils.trimToNull(demographicCust.getNurse());
                    String cust2 = StringUtils.trimToNull(demographicCust.getResident());
                    String cust4 = StringUtils.trimToNull(demographicCust.getMidwife());
                    if (cust1 != null) {
                        h.put("cust1", cust1);
                        p = rx.getProvider(cust1);
                        h.put("cust1Name", p.getSurname() + ", " + p.getFirstName());
                    }
                    if (cust2 != null) {
                        h.put("cust2", cust2);
                        p = rx.getProvider(cust2);
                        h.put("cust2Name", p.getSurname() + ", " + p.getFirstName());
                    }
                    if (cust4 != null) {
                        h.put("cust4", cust4);
                        p = rx.getProvider(cust4);
                        h.put("cust4Name", p.getSurname() + ", " + p.getFirstName());
                    }
                }
            }


            // Derived fields required by jQuery autocomplete widget (label/value are the standard autocomplete contract)
            String statusLabel = h.getOrDefault("status", "");
            h.put("label", h.getOrDefault("formattedName", "") + " " + h.getOrDefault("fomattedDob", "") + " (" + statusLabel + ")");
            h.put("value", h.getOrDefault("demographicNo", ""));
            // Alias fields to match the field names accessed by the JS select handler
            h.put("provider", h.getOrDefault("providerName", ""));
            h.put("nextAppt", h.getOrDefault("nextAppointment", ""));
            secondList.add(h);
        }

        HashMap<String, List<HashMap<String, String>>> d = new HashMap<String, List<HashMap<String, String>>>();
        d.put("results", secondList);
        response.setContentType("application/json");
        if (jqueryJSON) {
            response.getWriter().print(formatJSON(secondList));
            response.getWriter().flush();
        } else {
            String jsonString = objectMapper.writeValueAsString(d);
            response.getWriter().write(jsonString);
        }
        return null;

    }

    private String formatJSON(List<HashMap<String, String>> info) {
        try {
            // Use ObjectMapper for proper JSON escaping and null handling
            return objectMapper.writeValueAsString(info);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error serializing autocomplete JSON", e);
            return "[]";
        }
    }

}
