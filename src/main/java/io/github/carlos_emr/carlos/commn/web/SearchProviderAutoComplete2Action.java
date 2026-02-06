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

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;

/**
 * @author jackson
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

@SuppressWarnings("deprecation")
public class SearchProviderAutoComplete2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_provider", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required security object (_provider)");
        }

        if ("labSearch".equals(request.getParameter("method"))) {
            return labSearch();
        }
        String searchStr = request.getParameter("providerKeyword");
        if (searchStr == null) {
            searchStr = request.getParameter("query");
        }
        if (searchStr == null) {
            searchStr = request.getParameter("name");
        }

        // Handle null or empty search string
        if (searchStr == null || searchStr.trim().isEmpty()) {
            response.setContentType("text/x-json");
            response.getWriter().write("{\"results\":[]}");
            return null;
        }

        // Parse search string for firstName and lastName (replaces deprecated
        // ProviderData.searchProvider)
        String firstName = null;
        String lastName;
        if (searchStr.indexOf(",") != -1) {
            String[] array = searchStr.split(",");
            lastName = array[0].trim();
            firstName = array[1].trim();
        } else {
            lastName = searchStr.trim();
        }

        // Use the DAO directly with the newer model class
        List<io.github.carlos_emr.carlos.commn.model.ProviderData> providers = providerDataDao.findByName(firstName,
                lastName, true);

        // Convert to the expected map format for JSON response
        List<java.util.Map<String, String>> provList = new java.util.ArrayList<>();
        for (io.github.carlos_emr.carlos.commn.model.ProviderData p : providers) {
            java.util.Map<String, String> result = new java.util.HashMap<>();
            result.put("providerNo", p.getId());
            result.put("firstName", p.getFirstName());
            result.put("lastName", p.getLastName());
            result.put("ohipNo", p.getOhipNo());
            provList.add(result);
        }

        java.util.HashMap<String, Object> d = new java.util.HashMap<>();
        d.put("results", provList);

        response.setContentType("text/x-json");
        ObjectNode jsonArray = objectMapper.valueToTree(d);
        response.getWriter().write(jsonArray.toString());
        return null;

    }

    public String labSearch() throws Exception {

        String searchStr = request.getParameter("term");

        // Handle null or empty search string
        if (searchStr == null || searchStr.trim().isEmpty()) {
            response.setContentType("text/x-json");
            response.getWriter().write("[]");
            return null;
        }

        String firstName, lastName;

        if (searchStr.indexOf(",") != -1) {
            String[] searchParams = searchStr.split(",", -1);
            // note - the -1 is added because split discards a last empty string by default,
            // so "smith,".split(",") returns ["smith"], not ["smith",""].
            // adding the -1 causes split to return the 2 element array in this situation,
            // to avoid an index out of bounds error when setting the firstName
            lastName = searchParams[0].trim();
            firstName = searchParams[1].trim();
        } else {
            lastName = searchStr;
            firstName = null;
        }

        ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);
        List<io.github.carlos_emr.carlos.commn.model.ProviderData> provList = providerDataDao.findByName(firstName,
                lastName, true);
        List<java.util.LinkedHashMap<String, String>> searchResults = new java.util.ArrayList<>();

        for (io.github.carlos_emr.carlos.commn.model.ProviderData provData : provList) {
            java.util.LinkedHashMap<String, String> node = new java.util.LinkedHashMap<>();
            String provLastName = provData.getLastName() != null ? provData.getLastName() : "";
            String provFirstName = provData.getFirstName() != null ? provData.getFirstName() : "";
            node.put("label", provLastName + ", " + provFirstName);
            node.put("value", provData.getId());
            searchResults.add(node);
        }

        response.setContentType("text/x-json");
        response.getWriter().write(objectMapper.writeValueAsString(searchResults));

        return null;
    }
}
