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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;



import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public final class RxClearPending2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise): clears every staged card of the named patient.
     *
     * @return {@code close}, {@code success}, {@code NONE} after a 405, or {@code null} after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String execute()
            throws IOException, ServletException {
        // Clearing the stash discards the patient's staged prescriptions. CSRFGuard does not check
        // GET, so a link or image tag could otherwise clear it; ViewScript2's form POSTs (#3908).
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }



        // Setup variables

        // Clears staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }


        bean.clearStash();
        // "Create a new Rx" also ends a pending reprint of this patient; otherwise ViewScript2
        // would still render the reprinted script after the next save (#3908).
        RxReprintWorkspace.clear(request.getSession(), bean.getDemographicNo());

        if ("close".equals(action)) {
            return "close";
        }

        return SUCCESS;
    }

    private String action = null;

    public String getAction() {
        return this.action;
    }

    @StrutsParameter
    public void setAction(String RHS) {
        this.action = RHS;
    }
}
