/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.lab.service.LabPatientAccess;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PatientRecordAccess;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code lab/CA/ON/labValuesGraph.jsp}. Enforces {@code _lab}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/lab/} location. Part of the lab module
 * security-hardening migration (2Action gate pattern from #1109, #1629,
 * #1632, #1644, #1662, #1663, #1665, #1666).
 *
 * <p>Global {@code _lab} read does not override a patient lock ({@code _demographic$N} or
 * {@code _eChart$N}) on the patient named by {@code demographic_no}; a malformed value is a 400.
 *
 * @since 2026-04-13
 */
public final class ViewLabValuesGraph2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        // labValuesGraph.jsp reads the requested patient's medications and embeds that patient's
        // lab-value graph. Its demographic lookup enforces only _demographic$N, so refuse an
        // _eChart$N lock too before any value is read.
        if (!LabPatientAccess.requireRequestedPatient(securityInfoManager, loggedInInfo,
                request.getParameter("demographic_no"))) {
            ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        // The page lists the patient's prescriptions (a picker) and can draw them on the graph. That
        // takes the prescription read right on this patient, as the graph image action requires (#6322):
        // a lab-only user still sees the lab graph, without the medication picker (#6325 review).
        // Decide it for the patient the lab gate above accepted, by canonical number: a zero-padded
        // demographic_no such as 00123 is patient 123 there and in the embedded graph, so here too.
        Integer patient = LabPatientAccess.positiveIdentity(request.getParameter("demographic_no"));
        boolean medicationsAllowed = false;
        if (patient != null) {
            try {
                PatientRecordAccess.require(securityInfoManager, loggedInInfo, patient.toString(), "_rx", "r");
                medicationsAllowed = true;
            } catch (SecurityException noPrescriptionAccess) {
                medicationsAllowed = false;
            }
        }
        request.setAttribute(MEDICATIONS_ALLOWED, medicationsAllowed);

        return SUCCESS;
    }

    /** Request attribute (Boolean) the page reads: may it list and draw this patient's medications. */
    public static final String MEDICATIONS_ALLOWED = "labGraphMedicationsAllowed";
}
