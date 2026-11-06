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
package io.github.carlos_emr.carlos.dashboard.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ExportResults2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);

    public String execute() throws IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }

        String indicatorId = request.getParameter("indicatorId");
        String indicatorName = request.getParameter("indicatorName");

        String providerNo = dashboardManager.getRequestedProviderNo(loggedInInfo);
        String csvFile;
        if (providerNo != null) {
            csvFile = dashboardManager.exportDrilldownQueryResultsToCSV(loggedInInfo, providerNo, Integer.parseInt(indicatorId));
        } else {
            csvFile = dashboardManager.exportDrilldownQueryResultsToCSV(loggedInInfo, Integer.parseInt(indicatorId));
        }

        if (indicatorName == null || indicatorName.isEmpty()) {
            indicatorName = "indicator_data-" + System.currentTimeMillis() + ".csv";
        } else {
            String baseName = sanitizeBaseFilename(indicatorName);
            indicatorName = baseName + "-" + System.currentTimeMillis() + ".csv";
        }

        if (csvFile == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        byte[] bytes = csvFile.getBytes(StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + indicatorName + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- UTF-8 CSV attachment with nosniff; not an HTML response

        return NONE;
    }

    /**
     * Sanitizes a value to be used in an HTTP header to prevent response splitting attacks.
     * Removes all control characters including carriage return and line feed.
     * 
     * @param filename The value to sanitize
     * @return The sanitized header value safe for use in HTTP headers
     */
    private String sanitizeBaseFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "indicator_data";
        }
        
        // Remove dangerous characters
        String sanitized = filename
            .replaceAll("[\r\n\u0000-\u001F\u007F-\u009F]", "")  // Control characters
            .replaceAll("[\"\\\\;]", "")  // Quotes, backslashes, semicolons
            .replaceAll("[/\\*?<>|:]", "_")  // File system reserved characters
            .replaceAll("\\.csv$", "")  // Remove .csv if already present
            .trim();
        
        // Ensure not empty after sanitization
        if (sanitized.isEmpty()) {
            return "indicator_data";
        }
        
        // Limit length (leaving room for timestamp and .csv)
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        
        return sanitized;
    }
}
