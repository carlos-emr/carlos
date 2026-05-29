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
package io.github.carlos_emr.carlos.appointment.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Write-scope view gate for appointment forms (add / edit / copy /
 * add-record-card). Requires {@code _appointment w}. Accepts GET, HEAD, and
 * POST — POST is required for the demographic-search handoff forms
 * ({@code demographicsearch2apptresults.jsp},
 * {@code demographicsearch2reportresults.jsp},
 * {@code demographicaddarecord.jsp}) that submit to {@code addappointment}
 * to open the add-appointment screen pre-populated with the selected patient.
 * The forms themselves submit to separate AddRecord / UpdateRecord /
 * CutRecord endpoints.
 *
 * @since 2026-04-14
 */
public final class ViewAppointmentWrite2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Checks {@code _appointment w} then permits GET, HEAD, or POST.
     *
     * @return {@link #SUCCESS} when authorized and the method is allowed;
     *         {@link #NONE} after sending 405 for unsupported methods
     * @throws SecurityException when the session is missing or the caller
     *         lacks {@code _appointment w}
     * @throws Exception propagated from Struts I/O
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            MiscUtils.getLogger().warn("Denied appointment write: no session");
            throw new SecurityException("missing required sec object (_appointment w)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
            MiscUtils.getLogger().warn("Denied appointment write: provider={} lacks _appointment w",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_appointment w)");
        }

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"POST".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        return SUCCESS;
    }
}
