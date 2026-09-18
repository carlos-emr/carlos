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
package io.github.carlos_emr.carlos.appointment.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * View gate for {@code appointment/editappointment}. Extends the shared
 * {@code _appointment w} gate with the request validation the edit form needs.
 *
 * <p>{@code editappointment.jsp} parses {@code appointment_no} and looks the record up
 * unguarded, so a request with no {@code appointment_no}, a non-numeric one, or one that
 * matches no row answered HTTP 500 out of the compiled JSP (issue #3729). The three cases
 * are a malformed or stale link rather than a server fault, so they are answered here —
 * 400 for a missing or non-numeric id, 404 for one that does not resolve — and the JSP is
 * reached only with an appointment it can actually render.
 *
 * <p>The sibling routes on the base gate ({@code addappointment},
 * {@code appointmentaddrecordcard}, {@code appointmentcopyrecord}) legitimately have no
 * {@code appointment_no}, which is why this check lives on the edit route alone.
 *
 * <p>On rejection this action writes the error response itself and returns {@link #NONE}
 * so Struts does not also resolve the {@code success} JSP forward, per the
 * direct-response contract in CLAUDE.md.
 */
public final class ViewEditAppointmentWrite2Action extends ViewAppointmentWrite2Action {

    @Override
    protected String afterPrivilegeGranted(HttpServletRequest request,
                                           HttpServletResponse response) throws Exception {
        String appointmentNo = request.getParameter("appointment_no");
        if (appointmentNo == null || appointmentNo.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing appointment_no");
            return NONE;
        }

        int parsed;
        try {
            parsed = Integer.parseInt(appointmentNo.trim());
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid appointment_no");
            return NONE;
        }
        if (parsed <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid appointment_no");
            return NONE;
        }

        Appointment appointment = SpringUtils.getBean(OscarAppointmentDao.class).find(parsed);
        if (appointment == null) {
            // The identifier is not echoed back: it is a PHI-correlating operational id and
            // the caller already knows what it asked for.
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "appointment not found");
            return NONE;
        }
        return SUCCESS;
    }
}
