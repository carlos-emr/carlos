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


package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.ClinicalReports.ReportEvaluator;
import org.springframework.web.util.WebUtils;

/**
 * Struts2 action for removing individual clinical reports from the session history list,
 * or clearing all reports when the {@code clear=yes} parameter is passed.
 *
 * <p>A report is named by its {@link ReportEvaluator#getKey() key}, not its position, so a repeated post
 * (a refresh, a double click) or a page from a second window removes nothing else. The page posts both
 * buttons as forms; any other method is answered 405.</p>
 *
 * @since 2006-01-12
 */
public class RemoveClinicalReportFromHistory2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        // Removing changes the session's report list: refuse GET/HEAD before reading any parameter.
        if (!"POST".equals(request.getMethod())) {
            try {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException e) {
                MiscUtils.getLogger().warn("Unable to send 405 for a non-POST clinical report removal", e);
            }
            return NONE;
        }

        HttpSession session = request.getSession();
        synchronized (WebUtils.getSessionMutex(session)) {
            if ("yes".equals(request.getParameter("clear"))) {
                session.removeAttribute("ClinicalReports");
                return SUCCESS;
            }
            // Publish a fresh snapshot so an open history page can finish reading the old one.
            String key = request.getParameter("key");
            List<?> reports = (List<?>) session.getAttribute("ClinicalReports");
            if (reports == null || key == null) return SUCCESS;
            List<?> remaining = new ArrayList<>(reports);
            boolean removed = remaining.removeIf(
                    report -> report instanceof ReportEvaluator evaluator && key.equals(evaluator.getKey()));
            if (remaining.isEmpty()) {
                session.removeAttribute("ClinicalReports");
            } else if (removed) {
                session.setAttribute("ClinicalReports", remaining);
            }
        }

        return SUCCESS;
    }
}
