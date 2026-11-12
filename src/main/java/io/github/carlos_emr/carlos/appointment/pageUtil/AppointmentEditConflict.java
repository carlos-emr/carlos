/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;

/**
 * The version the classic Edit Appointment form was rendered from, and the refusal of a form that
 * is no longer current.
 *
 * <p>The edit popup posts the whole appointment. A popup opened before another user's change used
 * to write its stale fields back, so a status letter clicked on the day sheet or a cancellation
 * saved from a second popup was silently undone. The form now carries
 * {@link Appointment#getEditVersion()} as {@value #PARAMETER}; every route the form posts to that
 * writes its fields (Update, Update &amp; Receipt, Cancel, No Show, Delete, Cut, the group and
 * repeat pages) compares it with the row it has just locked and refuses a mismatch with HTTP 409
 * and {@code appointmentEditConflict.jsp} before anything is written. This is the token the REST
 * edit already uses ({@code ScheduleManager.updateAppointment}); no second version scheme exists.</p>
 *
 * <p>A post without the parameter (an older page, a hand-built request) keeps the previous
 * last-writer-wins behaviour, as the REST edit does for clients that send no version.</p>
 *
 * @since 2026-11-12
 */
public final class AppointmentEditConflict {

    /** Hidden field of the edit form: the {@link Appointment#getEditVersion()} it was rendered from. */
    public static final String PARAMETER = "appointmentVersion";

    /**
     * Prefix of the group page's per-row field: {@code appointment_version<N>} is the version of
     * the row named by {@code appointment_no<N>} when that page listed it.
     */
    public static final String ROW_PARAMETER_PREFIX = "appointment_version";

    /** Struts result the form's write actions map to {@link #VIEW}. */
    public static final String RESULT = "editConflict";

    /** The refusal page; the group page forwards to it directly. */
    public static final String VIEW = "/WEB-INF/jsp/appointment/appointmentEditConflict.jsp";

    private static final String ROW_NUMBER_PREFIX = "appointment_no";

    private AppointmentEditConflict() { }

    /**
     * @param request the form post
     * @return the version the form carried, or {@code null} when it carried none (unversioned post)
     */
    public static String submittedVersion(HttpServletRequest request) {
        String version = request.getParameter(PARAMETER);
        return version == null || version.isBlank() ? null : version.trim();
    }

    /**
     * Answers a stale form: HTTP 409, never cached, and the appointment the refusal page offers to
     * reopen. Only the appointment number (digits) and a well-formed provider number are passed on;
     * nothing else the stale form posted is echoed.
     *
     * @param request the refused post
     * @param response its response
     * @return {@link #RESULT}
     */
    public static String refuse(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setHeader("Cache-Control", "no-store");
        request.setAttribute("appointmentConflictNo", digits(request.getParameter("appointment_no")));
        String providerNo = request.getParameter("provider_no");
        request.setAttribute("appointmentConflictProviderNo",
                providerNo != null && providerNo.matches("[A-Za-z0-9]{1,6}") ? providerNo : null);
        return RESULT;
    }

    /**
     * Locks every appointment the posted form names with a version and says whether any of them
     * changed, or is gone, since the form was rendered. Must be called inside the transaction that
     * performs the write, before the first write, so the comparison and the write see the same
     * rows: the locks are held until that transaction ends.
     *
     * <p>Checked are the form's own appointment ({@code appointment_no} with {@value #PARAMETER})
     * and, on the group page, each selected row ({@code one<N>} or {@code two<N>} ticked) that
     * carries {@code appointment_no<N>} and {@code appointment_version<N>}. Rows without a version
     * are not checked.</p>
     *
     * @param appointments the DAO whose {@code findForUpdate} takes the row locks
     * @param request the form post
     * @return {@code true} when at least one versioned appointment no longer matches
     */
    public static boolean staleUnderLock(OscarAppointmentDao appointments, HttpServletRequest request) {
        String version = submittedVersion(request);
        if (version != null && changed(appointments, request.getParameter("appointment_no"), version)) {
            return true;
        }
        for (Enumeration<String> names = request.getParameterNames(); names.hasMoreElements(); ) {
            String name = names.nextElement();
            if (!name.startsWith(ROW_PARAMETER_PREFIX)) {
                continue;
            }
            String row = name.substring(ROW_PARAMETER_PREFIX.length());
            String rowVersion = request.getParameter(name);
            if (digits(row) == null || rowVersion == null || rowVersion.isBlank()
                    || (request.getParameter("one" + row) == null && request.getParameter("two" + row) == null)) {
                continue;
            }
            if (changed(appointments, request.getParameter(ROW_NUMBER_PREFIX + row), rowVersion.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean changed(OscarAppointmentDao appointments, String appointmentNo, String version) {
        String id = digits(appointmentNo);
        if (id == null) {
            // A version with no usable appointment number cannot be verified; the write routes
            // answer the malformed number themselves.
            return false;
        }
        Appointment current;
        try {
            current = appointments.findForUpdate(Integer.valueOf(id));
        } catch (NumberFormatException overflow) {
            return false;
        }
        return current == null || !version.equals(current.getEditVersion());
    }

    private static String digits(String value) {
        return value != null && value.matches("\\d{1,10}") ? value : null;
    }
}
