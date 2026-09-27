/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.web;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.demographic.data.DemographicMergeSearch;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Security gate for the Demographic Merge Record admin page.
 *
 * <p>Enforces {@code _demographic w} privilege before forwarding to the JSP.
 * Validates search options and obtains a domain-filtered, sorted page before forwarding.
 * The JSP initiates merge/unmerge operations via the separate {@code MergeRecords} action.</p>
 *
 * @since 2026-04-05
 */
public class DemographicMergeRecord2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (loggedInInfo == null || !securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        DemographicMergeSearch search;
        try {
            search = new DemographicMergeSearch(request.getParameter("search_mode"), request.getParameter("keyword"),
                    request.getParameter("orderby"), integerParameter(request, "limit1", 0),
                    integerParameter(request, "limit2", 10),
                    "demographic_search_merged".equals(request.getParameter("dboperation")));
        } catch (IllegalArgumentException invalidOptions) {
            ServletActionContext.getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid patient search options");
            return NONE;
        }
        CarlosProperties properties = CarlosProperties.getInstance();
        boolean outOfDomain = !properties.getProperty("ModuleNames", "").contains("Caisi")
                || "true".equals(properties.getProperty("pmm.client.search.outside.of.domain.enabled", "true"));
        if (!outOfDomain && "true".equals(request.getParameter("outofdomain"))) {
            outOfDomain = securityInfoManager.hasPrivilege(loggedInInfo, "_search.outofdomain", "r", null);
        }
        request.setAttribute("mergeSearch", search);
        request.setAttribute("mergeSearchResults", search.keyword() == null ? java.util.List.of()
                : demographicDao.searchForMerge(search, loggedInInfo.getLoggedInProviderNo(), outOfDomain));
        return SUCCESS;
    }
    private static int integerParameter(HttpServletRequest request, String name, int fallback) {
        String value = request.getParameter(name);
        return value == null ? fallback : Integer.parseInt(value);
    }

}
