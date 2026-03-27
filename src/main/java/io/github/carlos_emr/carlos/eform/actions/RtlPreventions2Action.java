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
 * Struts2 action that returns OWASP-encoded HTML-formatted prevention/immunization
 * data for a given patient. Called via AJAX from the Rich Text Letter eForm's
 * "Preventions" sidebar button.
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Requires {@code _eform} read privilege via {@link SecurityInfoManager}</li>
 *   <li>Input validated: {@code demographic_no} must match {@code \d+} regex</li>
 *   <li>Output encoded: all prevention types and dates use {@link Encode#forHtml(String)}</li>
 * </ul>
 *
 * <h3>Why This Exists</h3>
 * <p>Prior to 2026.3.0, the RTL eForm's {@code fpreventions()} JavaScript function
 * built a raw SQL string on the client side and sent it to {@code RptByExample.do},
 * which executed it directly against the database — a critical SQL injection
 * vulnerability. This action replaces that pattern with a safe server-side endpoint
 * that uses {@link PreventionManager} and returns pre-rendered, encoded HTML.</p>
 *
 * <h3>Response</h3>
 * <p>Returns {@code text/html} containing either an HTML table of prevention
 * records or the text "No preventions on file." The response is inserted directly
 * into the RTL editor iframe via the {@code doHtml()} JavaScript function.</p>
 *
 * <h3>Struts Mapping</h3>
 * <p>Mapped as {@code eform/rtlPreventions} in {@code struts.xml}. The RTL
 * eForm calls it via {@code $.ajax({url: "../eform/rtlPreventions.do", ...})}.</p>
 *
 * @see io.github.carlos_emr.carlos.managers.PreventionManager#getPreventionsByDemographicNo
 * @since 2026-03-22
 */
public class RtlPreventions2Action extends ActionSupport {

    private static final Logger logger = LogManager.getLogger(RtlPreventions2Action.class);

    private final HttpServletRequest request = ServletActionContext.getRequest();
    private final HttpServletResponse response = ServletActionContext.getResponse();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);

    /** Thread-safe date formatter for prevention dates (java.time, not SimpleDateFormat). */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Handles the AJAX request from the RTL eForm's Preventions button.
     *
     * <p>Writes HTML directly to the response and returns {@code null} to bypass
     * Struts result dispatch (no JSP view — the response IS the view).</p>
     *
     * @return String always {@code null} (response written directly)
     * @throws IOException if the response stream cannot be written to
     */
    @Override
    public String execute() throws IOException {
        // Mandatory security check — same _eform privilege used by all eForm endpoints
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)) {
            throw new SecurityException("missing required security object _eform");
        }

        // Validate demographic_no: must be digits only. The regex check prevents
        // SQL injection and the parseInt handles overflow (values > Integer.MAX_VALUE).
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
            // PreventionManager enforces circle-of-care access via loggedInInfo
            List<Prevention> preventions = preventionManager.getPreventionsByDemographicNo(loggedInInfo, demographicNo);

            // Build HTML table of active preventions. This HTML is inserted directly
            // into the RTL editor iframe by the client-side doHtml() function.
            StringBuilder html = new StringBuilder();
            if (preventions != null && !preventions.isEmpty()) {
                html.append("<table border='1' cellpadding='2' cellspacing='0'>");
                html.append("<tr><th>Prevention</th><th>Date</th></tr>");
                for (Prevention p : preventions) {
                    // Skip soft-deleted preventions
                    if (p.isDeleted()) {
                        continue;
                    }
                    String type = p.getPreventionType() != null ? p.getPreventionType() : "";
                    // Convert java.util.Date to LocalDate for formatting
                    String date = p.getPreventionDate() != null
                        ? DATE_FORMAT.format(p.getPreventionDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                        : "";
                    // OWASP-encode both values before inserting into HTML
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
            // Guard against IllegalStateException if response was partially written
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to retrieve prevention data");
            }
        }
        return null;
    }
}
