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

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;


import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class RxDeleteAllergy2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Changes the allergy list of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _allergy} update, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * Deletes or re-activates one allergy, which must belong to that patient (403 otherwise).
     *
     * @return the result for the allergy page, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not update the patient's allergies
     */
    public String execute()
            throws IOException, ServletException {
        // Deleting or re-activating an allergy changes the chart: POST-only, refused before
        // anything else (#3908). ShowAllergies2's $.ajax posts it.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_allergy", "u", null)) {
            throw new SecurityException("missing required sec object (_allergy)");
        }


        // Setup variables
        // Add allergy

        String idParam = request.getParameter("ID");
        if (StringUtils.isBlank(idParam)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing ID parameter");
            return NONE;
        }

        int id;
        try {
            id = Integer.parseInt(idParam);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid ID parameter");
            return NONE;
        }
        String action = request.getParameter("action");

        // Deleting or re-activating an allergy changes the chart: act only for the patient the
        // request explicitly names, never the session's last-opened Rx patient (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_allergy", "u");
        RxPatientData.Patient patient = bean == null
                ? null
                : RxSessionBeanResolver.resolvePatient(request, bean.getDemographicNo());
        if (patient == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        Allergy allergy = patient.getAllergy(id);
        if (allergy == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }
        if (action != null && action.equals("activate")) {
            patient.activateAllergy(id);
            String ip = request.getRemoteAddr();
            LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), "Activate", LogConst.CON_ALLERGY, "" + id, ip, "" + patient.getDemographicNo(), allergy.getAuditString());
        } else {
            patient.deleteAllergy(id);
            String ip = request.getRemoteAddr();
            LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.DELETE, LogConst.CON_ALLERGY, "" + id, ip, "" + patient.getDemographicNo(), allergy.getAuditString());
        }

        // Echo the patient actually written, not the raw request value.
        request.setAttribute("demographicNo", String.valueOf(patient.getDemographicNo()));

        return SUCCESS;
    }
}
