/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.eform.actions;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action that returns OWASP-encoded HTML-formatted prevention data
 * for a given demographic. Used by the Rich Text Letter eForm to safely
 * insert prevention summaries without client-side SQL.
 *
 * <p>Replaces the old {@code fpreventions()} JavaScript function that passed
 * raw SQL to {@code RptByExample.do}.</p>
 *
 * @since 2026-03-22
 */
public class RtlPreventions2Action extends ActionSupport {

    private static final Logger logger = LogManager.getLogger(RtlPreventions2Action.class);

    private final HttpServletRequest request = ServletActionContext.getRequest();
    private final HttpServletResponse response = ServletActionContext.getResponse();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)) {
            throw new SecurityException("missing required security object _eform");
        }

        String demoNoParam = request.getParameter("demographic_no");
        if (demoNoParam == null || !demoNoParam.matches("\\d+")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic_no");
            return null;
        }

        Integer demographicNo;
        try {
            demographicNo = Integer.parseInt(demoNoParam);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic_no");
            return null;
        }
        try {
            List<Prevention> preventions = preventionManager.getPreventionsByDemographicNo(loggedInInfo, demographicNo);

            StringBuilder html = new StringBuilder();
            if (preventions != null && !preventions.isEmpty()) {
                html.append("<table border='1' cellpadding='2' cellspacing='0'>");
                html.append("<tr><th>Prevention</th><th>Date</th></tr>");
                for (Prevention p : preventions) {
                    if (p.isDeleted()) {
                        continue;
                    }
                    String type = p.getPreventionType() != null ? p.getPreventionType() : "";
                    String date = p.getPreventionDate() != null
                        ? DATE_FORMAT.format(p.getPreventionDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                        : "";
                    html.append("<tr><td>").append(Encode.forHtml(type))
                        .append("</td><td>").append(Encode.forHtml(date))
                        .append("</td></tr>");
                }
                html.append("</table>");
            } else {
                html.append("No preventions on file.");
            }

            response.setContentType("text/html; charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.print(html.toString());
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve preventions for demographic_no={}", demographicNo, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to retrieve prevention data");
        }
        return null;
    }
}
