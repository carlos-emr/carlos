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
package io.github.carlos_emr.carlos.report.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * View gate for {@code report/reportecharthistory.jsp} (ECHART HISTORY), which lists one
 * patient's eChart entries: their dates, subjects and providers.
 *
 * <p>The shared {@link ViewReport2Action} checks only {@code _report r}. This page is about one
 * patient, so it also enforces that patient's record restrictions: access to the patient record
 * ({@code _demographic$N} / {@code _eChart$N}) and {@code _demographic r} for that patient, the
 * same pair the entries' print page enforces when it loads the patient.
 *
 * @since 2026-10-17
 */
public final class ViewReportEChartHistory2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "GET or HEAD required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report r)");
        }

        String demographicNo = request.getParameter("demographic_no");
        if (demographicNo == null || !demographicNo.matches("[1-9][0-9]{0,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        int patient;
        try {
            patient = Integer.parseInt(demographicNo);
        } catch (NumberFormatException outOfRange) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, patient)) {
            throw new SecurityException("access to patient record denied");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        return SUCCESS;
    }
}
