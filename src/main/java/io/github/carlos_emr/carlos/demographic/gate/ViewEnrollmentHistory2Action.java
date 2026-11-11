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
package io.github.carlos_emr.carlos.demographic.gate;

import io.github.carlos_emr.carlos.demographic.PatientReadAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code demographic/EnrollmentHistory.jsp}. Enforces {@code _demographic}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/demographic/} location. Part of the demographic
 * module security-hardening migration (2Action gate pattern from #1109,
 * #1629, #1632, #1644, #1662, #1663, #1665, #1666, #1667).
 *
 * @since 2026-04-13
 */
public final class ViewEnrollmentHistory2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        final int patient;
        try {
            patient = PatientReadAccess.requestedPatient(request, true);
        } catch (IllegalArgumentException invalid) {
            ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (patient > 0) {
            PatientReadAccess.requirePatient(securityInfoManager, loggedInInfo, patient, "_demographic", SecurityInfoManager.READ);
            request.setAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE, Integer.toString(patient));
        }
        return SUCCESS;
    }
}
