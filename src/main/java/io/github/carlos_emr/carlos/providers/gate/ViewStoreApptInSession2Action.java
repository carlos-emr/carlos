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
package io.github.carlos_emr.carlos.providers.gate;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Remembers which appointment a patient was opened for from the day sheet. The day sheet posts
 * here ({@code storeApptNo} in {@code schedulePage.js.jsp}) when its encounter, master record or
 * prescription link is clicked; Health Tracker saves and the multisite prescription page read it
 * back through {@code EncounterPatientContext.currentAppointment}.
 *
 * <p>The appointment is filed under the patient it is booked for, which this action reads from the
 * appointment itself rather than from the request. It used to be one login-wide value, so with two
 * patients' windows open, work in the first window was tied to the visit last opened for the
 * second (issue #4673).</p>
 *
 * <p>Enforces {@code _appointment r} and POST-only (GET returns 405). Read privilege is correct:
 * the action changes only this login's session state, for an appointment the caller can already
 * see on the day sheet; POST is required so a forged GET cannot change that state. It answers
 * 204 with no body; a malformed appointment number is a 400, and an unknown appointment or one
 * booked without a patient is accepted and ignored.</p>
 *
 * @since 2026-04-13
 */
public final class ViewStoreApptInSession2Action extends BaseProviderViewGate2Action {

    private final OscarAppointmentDao appointmentDao;

    /** Struts object-factory constructor. */
    public ViewStoreApptInSession2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(OscarAppointmentDao.class));
    }

    ViewStoreApptInSession2Action(SecurityInfoManager securityInfoManager, OscarAppointmentDao appointmentDao) {
        super(securityInfoManager);
        this.appointmentDao = appointmentDao;
    }

    @Override
    protected String getSecurityObject() {
        return "_appointment";
    }

    @Override
    protected String getAccessRight() {
        return "r";
    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    /**
     * Files the posted appointment under its own patient and answers 204. Always ends the request
     * here: this route has no view.
     */
    @Override
    protected String checkRequest(HttpServletRequest request, HttpServletResponse response,
                                  LoggedInInfo loggedInInfo) throws IOException {
        String appointmentNo = request.getParameter("appointment_no");
        if (appointmentNo == null || !appointmentNo.matches("[0-9]{1,9}") || Integer.parseInt(appointmentNo) <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        Appointment appointment = appointmentDao.find(Integer.parseInt(appointmentNo));
        if (appointment != null && appointment.getDemographicNo() > 0) {
            // The key's patient and the stored number both come from the appointment row: the
            // request only names an appointment, as digits checked above.
            String key = ChartSessionKeys.currentAppointment(Integer.toString(appointment.getDemographicNo()));
            String storedAppointmentNo = Integer.toString(appointment.getId());
            request.getSession().setAttribute(key, storedAppointmentNo); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- key and value come from the appointment row
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return NONE;
    }
}
