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


package io.github.carlos_emr.carlos.report.pageUtil;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.data.PatientLetterBatchService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Generates a complete letter batch before filing documents and follow-ups. */
public class GeneratePatientLetters2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    private static final Logger log = MiscUtils.getLogger();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(info, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        String[] demos = request.getParameterValues("demos");
        if (demos == null || demos.length == 0) {
            request.setAttribute(GenerateEnvelopes2Action.NO_PATIENTS_SELECTED_ATTRIBUTE, Boolean.TRUE);
            return INPUT;
        }
        byte[] pdf;
        try {
            ManagePatientLetters2Action.configureJasperCompileClasspath(request);
            pdf = SpringUtils.getBean(PatientLetterBatchService.class).generate(info,
                    request.getParameter("reportLetter"), demos, "ON".equals(request.getParameter("addFollowUp")),
                    request.getParameter("followupType"), request.getParameter("followupValue"),
                    request.getParameter("message"));
        } catch (SecurityException denied) {
            throw denied;
        } catch (PatientLetterBatchService.OutcomeUncertainException uncertain) {
            log.error("Patient letter commit outcome is unknown; generated files retained for reconciliation");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute("letterGenerationUncertain", Boolean.TRUE);
            return INPUT;
        } catch (IllegalArgumentException invalid) {
            return generationFailure(HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception failure) {
            // Jasper exceptions can contain template expressions or patient values.
            log.error("Patient letter batch failed ({})", failure.getClass().getSimpleName());
            return generationFailure(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        response.setHeader("Content-Disposition", "inline; filename=GeneratedLetters.pdf");
        response.setHeader("Cache-Control", "no-store");
        response.setDateHeader("Expires", 0);
        response.setContentType("application/pdf");
        response.setContentLength(pdf.length);
        try {
            response.getOutputStream().write(pdf);
        } catch (IOException failure) {
            // Clinical records are already committed; do not claim they were rolled back.
            log.warn("Saved patient letters could not be downloaded ({})", failure.getClass().getSimpleName());
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
        return NONE;
    }

    private String generationFailure(int status) {
        response.setStatus(status);
        request.setAttribute("letterGenerationFailed", Boolean.TRUE);
        return INPUT;
    }
}
