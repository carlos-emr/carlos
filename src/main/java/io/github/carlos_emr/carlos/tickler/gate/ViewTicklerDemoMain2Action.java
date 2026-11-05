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
package io.github.carlos_emr.carlos.tickler.gate;

import io.github.carlos_emr.carlos.demographic.PatientReadAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code tickler/ticklerDemoMain.jsp}. Enforces {@code _tickler}
 * {@code w} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/tickler/} location. Part of the tickler-module JSP-gating migration (PR #1670).
 *
 * <p>The page lists one patient's ticklers, selected by the {@code demoview} parameter. The
 * global {@code _tickler w} right does not honour a patient-specific record lock
 * ({@code _demographic$<id>} / {@code _eChart$<id>}), so a positive {@code demoview} must also
 * pass {@link PatientReadAccess#requirePatient} for {@code _tickler r} before the view renders.
 * The authorized id is published as {@link PatientReadAccess#AUTHORIZED_PATIENT_ATTRIBUTE}, and
 * the JSP queries only that id.
 *
 * @since 2026-04-13
 */
public final class ViewTicklerDemoMain2Action extends ActionSupport {

    /**
     * The page's own "no patient" value. ticklerDemoMain.jsp defaults an absent {@code demoview}
     * to it and re-submits it through its filter form, so it must keep rendering the empty list.
     */
    private static final String NO_PATIENT_SELECTION = "all";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the privilege checks and forwards to the gated JSP.
     *
     * @return {@code SUCCESS} when the caller holds {@code _tickler w} and, for a selected
     *         patient, may read that patient's ticklers; {@code NONE} with HTTP 400 when
     *         {@code demoview} is malformed or conflicting. The package-level
     *         {@code global-exception-mappings} in {@code struts-scheduling.xml} routes the
     *         {@link SecurityException} thrown on denial to {@code /securityError}.
     * @throws SecurityException when the caller lacks {@code _tickler w}, or the selected
     *         patient is locked or not readable for {@code _tickler}
     * @throws Exception if the underlying {@code ActionSupport.execute} contract
     *         declares one (not thrown by this implementation)
     * @since 2026-04-13
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "w", null)) {
            throw new SecurityException("missing required sec object (_tickler)");
        }

        final int patient;
        try {
            patient = requestedDemoviewPatient(request);
        } catch (IllegalArgumentException invalid) {
            ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (patient > 0) {
            PatientReadAccess.requirePatient(securityInfoManager, loggedInInfo, patient, "_tickler", SecurityInfoManager.READ);
            request.setAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE, Integer.toString(patient));
        }
        return SUCCESS;
    }

    /**
     * Resolves every {@code demoview} value to one patient, or {@code 0} for no patient context.
     * Absent, empty, {@code all} and {@code 0} are the existing "no patient" selections (a
     * schedule tickler alert on an appointment with no linked patient sends {@code demoview=0}).
     * Anything else must be a non-negative decimal int, and repeated values must agree, so the
     * view can never list a patient other than the one authorized here.
     *
     * @throws IllegalArgumentException when a value is malformed, overflows an int, or conflicts
     */
    static int requestedDemoviewPatient(HttpServletRequest request) {
        String[] values = request.getParameterValues("demoview");
        if (values == null) return 0;
        Integer selected = null;
        for (String value : values) {
            int patient = 0;
            if (value == null) throw new IllegalArgumentException("Invalid patient identity");
            if (!value.isEmpty() && !NO_PATIENT_SELECTION.equals(value)) {
                if (!value.matches("[0-9]+")) throw new IllegalArgumentException("Invalid patient identity");
                try {
                    patient = Integer.parseInt(value);
                } catch (NumberFormatException overflow) {
                    throw new IllegalArgumentException("Invalid patient identity");
                }
            }
            if (selected != null && selected != patient) throw new IllegalArgumentException("Invalid patient identity");
            selected = patient;
        }
        return selected == null ? 0 : selected;
    }
}
