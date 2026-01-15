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


package ca.openosp.openo.commn.web;

import java.util.Hashtable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.providers.data.ProviderData;
import ca.openosp.openo.commn.dao.ProviderDataDao;

/**
 * @author jackson
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class SearchProviderAutoComplete2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
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

        List provList = ProviderData.searchProvider(searchStr, true);
        Hashtable d = new Hashtable();
        d.put("results", provList);

        response.setContentType("text/x-json");
        ObjectNode jsonArray = objectMapper.valueToTree(d);
        response.getWriter().write(jsonArray.toString());
        return null;

    }

    public String labSearch() throws Exception {

        String searchStr = request.getParameter("term");
        String firstName, lastName;

        if (searchStr.indexOf(",") != -1) {
            String[] searchParams = searchStr.split(",", -1);
            //note - the -1 is added because split discards a last empty string by default, so "smith,".split(",") returns ["smith"], not ["smith",""].
            //adding the -1 causes split to return the 2 element array in this situation, to avoid an index out of bounds error when setting the firstName
            lastName = searchParams[0].trim();
            firstName = searchParams[1].trim();
        } else {
            lastName = searchStr;
            firstName = null;
        }

        ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);
        List<ca.openosp.openo.commn.model.ProviderData> provList = providerDataDao.findByName(firstName, lastName, true);
        StringBuilder searchResults = new StringBuilder("[");
        int idx = 0;

        for (ca.openosp.openo.commn.model.ProviderData provData : provList) {
            searchResults.append("{\"label\":\"" + provData.getLastName() + ", " + provData.getFirstName() + "\",\"value\":\"" + provData.getId() + "\"}");
            if (idx < provList.size() - 1) {
                searchResults.append(",");
            }
            ++idx;
        }

        searchResults.append("]");
        response.setContentType("text/x-json");
        response.getWriter().write(searchResults.toString());

        return null;
    }
}
