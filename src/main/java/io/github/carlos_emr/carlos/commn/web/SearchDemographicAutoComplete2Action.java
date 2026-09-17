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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.provider.dto.ProviderSummaryDTO;
import io.github.carlos_emr.carlos.utility.AppointmentUtil;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Struts 2 action providing JSON autocomplete results for demographic (patient) searches.
 * Used by appointment booking and other patient-lookup flows.
 *
 * @since 2026-02-20
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class SearchDemographicAutoComplete2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);



    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = {"XSS_SERVLET", "IMPROPER_UNICODE"}, justification = "XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink. case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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


        if (searchStr == null || searchStr.trim().isEmpty()) {
            response.setContentType("application/json");
            response.getWriter().write("[]");
            return null;
        }

        String searchType = request.getParameter("searchType");
        if (searchType == null) {
            searchType = "name";
        }

        // Parse inactive statuses once for activeOnly filtering across all search types
        List<String> stati = null;
        if (activeOnly) {
            CarlosProperties props = CarlosProperties.getInstance();
            String pstatus = props.getProperty("inactive_statuses", "IN, DE, IC, ID, MO, FI");
            pstatus = pstatus.replaceAll("'", "").replaceAll("\\s", "");
            stati = Arrays.asList(pstatus.split(","));
        }

        List<Demographic> list = null;

        if (searchStr.length() == 8 && searchStr.matches("([0-9]*)")) {
            list = demographicDao.searchDemographicByDOB(searchStr.substring(0, 4) + "-" + searchStr.substring(4, 6) + "-" + searchStr.substring(6, 8), 100, 0, providerNo, outOfDomain);
        } else if ("hin".equals(searchType)) {
            if (activeOnly) {
                list = demographicDao.searchDemographicByHINAndNotStatus(searchStr, stati, 20, 0, providerNo, outOfDomain);
            } else {
                list = demographicDao.searchDemographicByHIN(searchStr, 20, 0, providerNo, outOfDomain);
            }
        } else if ("phone".equals(searchType)) {
            if (activeOnly) {
                list = demographicDao.searchDemographicByPhoneAndNotStatus(searchStr, stati, 20, 0, providerNo, outOfDomain);
            } else {
                list = demographicDao.searchDemographicByPhone(searchStr, 20, 0, providerNo, outOfDomain);
            }
        } else if ("address".equals(searchType)) {
            if (activeOnly) {
                list = demographicDao.searchDemographicByAddressAndNotStatus(searchStr, stati, 20, 0, providerNo, outOfDomain);
            } else {
                list = demographicDao.searchDemographicByAddress(searchStr, 20, 0, providerNo, outOfDomain);
            }
        } else if (activeOnly) {
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


        // Hoist DAO lookups outside loop to avoid N+1 bean resolution on every iteration
        DemographicCustDao demographicCustDao = (DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class);
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

        // Batch-load all referenced provider numbers in a single query instead of N+1 RxProviderData lookups
        Set<String> providerNos = new LinkedHashSet<>();
        for (Demographic demo : list) {
            if (demo.getProviderNo() != null && !demo.getProviderNo().isEmpty()) {
                providerNos.add(demo.getProviderNo());
            }
        }
        boolean workflowEnhance = CarlosProperties.getInstance().isPropertyActive("workflow_enhance");
        // Pre-load all DemographicCust records to avoid N+1 queries in the render loop
        Map<Integer, DemographicCust> dcMap = new HashMap<>();
        for (Demographic demo : list) {
            DemographicCust dc = demographicCustDao.find(demo.getDemographicNo());
            dcMap.put(demo.getDemographicNo(), dc);
            if (workflowEnhance && dc != null) {
                String n = StringUtils.trimToNull(dc.getNurse());
                String r = StringUtils.trimToNull(dc.getResident());
                String m = StringUtils.trimToNull(dc.getMidwife());
                if (n != null) providerNos.add(n);
                if (r != null) providerNos.add(r);
                if (m != null) providerNos.add(m);
            }
        }
        Map<String, ProviderSummaryDTO> providerMap = providerNos.isEmpty()
                ? new HashMap<>()
                : providerDao.getProviderSummariesByIds(providerNos);

        List<HashMap<String, String>> secondList = new ArrayList<HashMap<String, String>>();
        for (Demographic demo : list) {
            HashMap<String, String> h = new HashMap<String, String>();
            h.put("formattedDob", demo.getFormattedDob());
            h.put("fomattedDob", demo.getFormattedDob()); // backward compat: legacy misspelled key still used by 4+ JSPs
            h.put("formattedName", demo.getFormattedName());
            h.put("demographicNo", String.valueOf(demo.getDemographicNo()));
            h.put("status", demo.getPatientStatus() != null ? demo.getPatientStatus() : "");
            h.put("rosterStatus", demo.getRosterStatus() != null ? demo.getRosterStatus() : "");
            h.put("cellPhone", demo.getCellPhone() != null ? demo.getCellPhone() : "");
            h.put("phone", demo.getPhone() != null ? demo.getPhone() : "");
            h.put("email", demo.getEmail() != null ? demo.getEmail() : "");
            h.put("hin", demo.getHin() != null ? demo.getHin() : "");
            h.put("address", demo.getAddress() != null ? demo.getAddress() : "");

            if (demo.getProviderNo() != null && !demo.getProviderNo().isEmpty()) {
                h.put("providerNo", demo.getProviderNo());
                ProviderSummaryDTO prov = providerMap.get(demo.getProviderNo());
                if (prov != null) {
                    h.put("providerName", prov.getFormattedName());
                }
            }

            // Reuse pre-loaded DemographicCust from the cache
            DemographicCust demographicCust = dcMap.get(demo.getDemographicNo());

            String alertText = (demographicCust != null && demographicCust.getAlert() != null) ? demographicCust.getAlert() : "";
            h.put("alert", alertText);

            if (workflowEnhance) {
                h.put("nextAppointment", AppointmentUtil.getNextAppointment(demo.getDemographicNo() + ""));

                if (demographicCust != null) {
                    String cust1 = StringUtils.trimToNull(demographicCust.getNurse());
                    String cust2 = StringUtils.trimToNull(demographicCust.getResident());
                    String cust4 = StringUtils.trimToNull(demographicCust.getMidwife());
                    putCustProvider(h, "cust1", cust1, providerMap);
                    putCustProvider(h, "cust2", cust2, providerMap);
                    putCustProvider(h, "cust4", cust4, providerMap);
                }
            }


            // Derived fields required by jQuery autocomplete widget (label/value are the standard autocomplete contract)
            String statusLabel = h.getOrDefault("status", "");
            h.put("label", h.getOrDefault("formattedName", "") + " " + h.getOrDefault("formattedDob", "") + " (" + statusLabel + ")");
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

    private static void putCustProvider(HashMap<String, String> h, String key, String providerNo,
                                           Map<String, ProviderSummaryDTO> providerMap) {
        if (providerNo == null) return;
        h.put(key, providerNo);
        ProviderSummaryDTO prov = providerMap.get(providerNo);
        if (prov != null) h.put(key + "Name", prov.getFormattedName());
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
