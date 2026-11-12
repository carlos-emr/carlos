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
package io.github.carlos_emr.carlos.appointment.reminder;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "Email Reminder" box on Add and Edit Appointment (#5887), shown when the clinic turns reminders on.
 *
 * <p>The box keeps its historical name {@code emailPt}. A hidden {@code emailPtShown} travels with it: a form
 * that did not show the box (reminders off, or a page without it) carries no marker and leaves the stored
 * choice as it is, so an unticked box can be told from a missing one. The service is looked up only when the
 * box was on the form, so other callers of these actions are unaffected.
 *
 * @since 2026-11-12
 */
public final class AppointmentReminderChoice {

    /** The box itself; any value means ticked. */
    public static final String PARAMETER = "emailPt";
    /** Sent with the box, so its absence means "not on this form" rather than "unticked". */
    public static final String SHOWN_PARAMETER = "emailPtShown";

    private static final Logger logger = MiscUtils.getLogger();
    /** Set on the request when a reminder call on the group page failed ({@link #onGroupPage}). */
    static final String NOT_RECORDED_ATTRIBUTE = "appointmentReminderNotRecorded";

    private AppointmentReminderChoice() {
    }

    public static boolean isOnForm(HttpServletRequest request) {
        return request != null && "1".equals(request.getParameter(SHOWN_PARAMETER));
    }

    public static boolean isTicked(HttpServletRequest request) {
        return request != null && request.getParameter(PARAMETER) != null;
    }

    /** {@link #isOnForm(HttpServletRequest)} for a form copied into a map, as the Repeat Booking page passes it. */
    public static boolean isOnCopiedForm(Map<String, String> values) {
        return values != null && "1".equals(values.get(SHOWN_PARAMETER));
    }

    /** {@link #isTicked(HttpServletRequest)} for a form copied into a map. */
    public static boolean isTickedOnCopiedForm(Map<String, String> values) {
        return values != null && values.get(PARAMETER) != null;
    }

    /**
     * Stores the box for {@code appointment} when the form showed it. Call inside the transaction that saves
     * the appointment, after it has its number, so the choice commits with it.
     */
    public static void record(HttpServletRequest request, LoggedInInfo user, Appointment appointment) {
        if (appointment == null || appointment.getId() == null || appointment.getId() <= 0 || !isOnForm(request)) {
            return;
        }
        SpringUtils.getBean(AppointmentReminderService.class).recordRequest(user, appointment.getId(),
                appointment.getDemographicNo(), ReminderChannel.EMAIL, isTicked(request));
    }

    /**
     * Group Appt books the patient with several providers at the same time (#5887). One reminder is enough, so
     * the box is recorded on one booking: the one for the provider the form was opened for, or else the first
     * booking with the patient. The other bookings are not reminded.
     */
    public static void recordForGroup(HttpServletRequest request, LoggedInInfo user, List<Appointment> bookings,
                                      String formProviderNo) {
        if (!isOnForm(request)) {
            return;
        }
        onGroupPage(request, () -> record(request, user, patientBooking(bookings, formProviderNo)));
    }

    static Appointment patientBooking(List<Appointment> bookings, String formProviderNo) {
        Appointment first = null;
        for (Appointment booking : bookings) {
            if (booking == null || booking.getId() == null || booking.getDemographicNo() <= 0) {
                continue;
            }
            if (formProviderNo != null && formProviderNo.equals(booking.getProviderNo())) {
                return booking;
            }
            if (first == null) {
                first = booking;
            }
        }
        return first;
    }

    /**
     * Group Update deletes {@code replacedNo} and books it again as {@code rebooked} (#5887): its reminder rows go
     * with it ({@link AppointmentReminderService#moveReminders}). Only when reminders are on, as the box shows.
     */
    public static void moveReminders(HttpServletRequest request, LoggedInInfo user, Integer replacedNo,
                                     Appointment rebooked) {
        if (replacedNo == null || rebooked == null || rebooked.getId() == null || !isOnForm(request)) {
            return;
        }
        onGroupPage(request, () -> SpringUtils.getBean(AppointmentReminderService.class)
                .moveReminders(user, replacedNo, rebooked.getId()));
    }

    /**
     * {@link #record} for the appointment Group Update was opened for, after the group page saved it. As the
     * page's other reminder calls, it never stops the page ({@link #onGroupPage}).
     */
    public static void recordAfterGroupUpdate(HttpServletRequest request, LoggedInInfo user, Appointment appointment) {
        onGroupPage(request, () -> record(request, user, appointment));
    }

    /**
     * Runs one of the group page's reminder calls. That page saves each booking on its own, with no transaction
     * around them, so every booking is already saved when its reminder is recorded. A reminder that cannot be
     * stored (a database error, an edit racing the reminder job) must not stop a Group Appt or Group Update
     * halfway, or show an error page that invites a second submit and a duplicate booking. So the page carries
     * on, the failure is logged (the class only), and the request is marked ({@link #notRecorded}): the page's
     * result then says the appointment was saved but its reminder was not, and to tick the box again.
     */
    static void onGroupPage(HttpServletRequest request, Runnable reminderCall) {
        try {
            reminderCall.run();
        } catch (RuntimeException e) {
            logger.warn("Appointment reminder choice not stored on the group page ({})", e.getClass().getName());
            if (request != null) {
                request.setAttribute(NOT_RECORDED_ATTRIBUTE, Boolean.TRUE);
            }
        }
    }

    /** Whether a reminder call on the group page failed for this request, so its result page must say so. */
    public static boolean notRecorded(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(NOT_RECORDED_ATTRIBUTE));
    }

    /**
     * The line under the box on Edit Appointment, or {@code null} when there is nothing to say or {@code user}
     * may not read it for this patient ({@link #mayReadStatus}).
     */
    public static ReminderStatusLine statusLine(LoggedInInfo user, Appointment appointment, Locale locale) {
        if (appointment == null || appointment.getId() == null || appointment.getId() <= 0
                || appointment.getDemographicNo() <= 0
                || !mayReadStatus(SpringUtils.getBean(SecurityInfoManager.class), user, appointment.getDemographicNo())) {
            return null;
        }
        return SpringUtils.getBean(AppointmentReminderService.class).statusLine(appointment, locale).orElse(null);
    }

    /**
     * A per-patient check for the status line, as the email pages make it: access to this patient's record,
     * and read on the record and on their email.
     */
    static boolean mayReadStatus(SecurityInfoManager security, LoggedInInfo user, int demographicNo) {
        return user != null
                && security.isAllowedAccessToPatientRecord(user, demographicNo)
                && security.hasPrivilege(user, "_demographic", SecurityInfoManager.READ, demographicNo)
                && security.hasPrivilege(user, "_email", SecurityInfoManager.READ, demographicNo);
    }

    /** Whether Edit Appointment shows the box ticked for {@code appointmentNo}. */
    public static boolean isRequested(Integer appointmentNo) {
        if (appointmentNo == null || appointmentNo <= 0) {
            return false;
        }
        return SpringUtils.getBean(AppointmentReminderService.class).isRequested(appointmentNo, ReminderChannel.EMAIL);
    }
}
