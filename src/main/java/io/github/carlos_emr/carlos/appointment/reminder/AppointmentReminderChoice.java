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
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "Email Reminder" box on Add and Edit Appointment (#5887), and beside it the "Text Reminder" box (#884),
 * each shown when the clinic turns that channel on.
 *
 * <p>The email box keeps its historical name {@code emailPt}; the text box is {@code smsPt}. A hidden marker
 * travels with each ({@code emailPtShown}, {@code smsPtShown}): a form that did not show a box (that channel
 * off, or a page without it) carries no marker and leaves that channel's stored choice as it is, so an
 * unticked box can be told from a missing one. The service is looked up only when some box was on the form,
 * so other callers of these actions are unaffected.
 *
 * @since 2026-11-12
 */
public final class AppointmentReminderChoice {

    /** The box itself; any value means ticked. */
    public static final String PARAMETER = "emailPt";
    /** Sent with the box, so its absence means "not on this form" rather than "unticked". */
    public static final String SHOWN_PARAMETER = "emailPtShown";
    /** The Text Reminder box (#884), shown when the clinic has text reminders on. */
    public static final String SMS_PARAMETER = "smsPt";
    /** Sent with the Text Reminder box, as {@link #SHOWN_PARAMETER} is with the email one. */
    public static final String SMS_SHOWN_PARAMETER = "smsPtShown";

    private AppointmentReminderChoice() {
    }

    /** The Email Reminder box was on the form. */
    public static boolean isOnForm(HttpServletRequest request) {
        return isOnForm(request, ReminderChannel.EMAIL);
    }

    /** The Email Reminder box was ticked. */
    public static boolean isTicked(HttpServletRequest request) {
        return isTicked(request, ReminderChannel.EMAIL);
    }

    /** {@code channel}'s box was on the form (#884: each channel has its own). */
    public static boolean isOnForm(HttpServletRequest request, ReminderChannel channel) {
        return request != null && "1".equals(request.getParameter(shownParameter(channel)));
    }

    public static boolean isTicked(HttpServletRequest request, ReminderChannel channel) {
        return request != null && request.getParameter(parameter(channel)) != null;
    }

    /** Some reminder box was on the form; without one, nothing about reminders is touched. */
    static boolean isAnyOnForm(HttpServletRequest request) {
        for (ReminderChannel channel : ReminderChannel.values()) {
            if (isOnForm(request, channel)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #isOnForm(HttpServletRequest, ReminderChannel)} for a form copied into a map, as Repeat Booking passes it. */
    public static boolean isOnCopiedForm(Map<String, String> values, ReminderChannel channel) {
        return values != null && "1".equals(values.get(shownParameter(channel)));
    }

    /** {@link #isTicked(HttpServletRequest, ReminderChannel)} for a form copied into a map. */
    public static boolean isTickedOnCopiedForm(Map<String, String> values, ReminderChannel channel) {
        return values != null && values.get(parameter(channel)) != null;
    }

    /** The Email Reminder box was on a form copied into a map. */
    public static boolean isOnCopiedForm(Map<String, String> values) {
        return isOnCopiedForm(values, ReminderChannel.EMAIL);
    }

    /** The Email Reminder box was ticked on a form copied into a map. */
    public static boolean isTickedOnCopiedForm(Map<String, String> values) {
        return isTickedOnCopiedForm(values, ReminderChannel.EMAIL);
    }

    static String parameter(ReminderChannel channel) {
        return channel == ReminderChannel.SMS ? SMS_PARAMETER : PARAMETER;
    }

    static String shownParameter(ReminderChannel channel) {
        return channel == ReminderChannel.SMS ? SMS_SHOWN_PARAMETER : SHOWN_PARAMETER;
    }

    /**
     * Stores the box for {@code appointment} when the form showed it. Call inside the transaction that saves
     * the appointment, after it has its number, so the choice commits with it.
     */
    public static void record(HttpServletRequest request, LoggedInInfo user, Appointment appointment) {
        if (appointment == null || appointment.getId() == null || appointment.getId() <= 0 || !isAnyOnForm(request)) {
            return;
        }
        AppointmentReminderService reminders = SpringUtils.getBean(AppointmentReminderService.class);
        for (ReminderChannel channel : ReminderChannel.values()) {
            if (isOnForm(request, channel)) {
                reminders.recordRequest(user, appointment.getId(), appointment.getDemographicNo(), channel,
                        isTicked(request, channel));
            }
        }
    }

    /**
     * Group Appt books the patient with several providers at the same time (#5887). One reminder is enough, so
     * the box is recorded on one booking: the one for the provider the form was opened for, or else the first
     * booking with the patient. The other bookings are not reminded.
     */
    public static void recordForGroup(HttpServletRequest request, LoggedInInfo user, List<Appointment> bookings,
                                      String formProviderNo) {
        if (!isAnyOnForm(request)) {
            return;
        }
        record(request, user, patientBooking(bookings, formProviderNo));
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
        if (replacedNo == null || rebooked == null || rebooked.getId() == null || !isAnyOnForm(request)) {
            return;
        }
        SpringUtils.getBean(AppointmentReminderService.class).moveReminders(user, replacedNo, rebooked.getId());
    }

    /**
     * The line under the box on Edit Appointment, or {@code null} when there is nothing to say or {@code user}
     * may not read it for this patient ({@link #mayReadStatus}).
     */
    public static ReminderStatusLine statusLine(LoggedInInfo user, Appointment appointment, Locale locale) {
        return statusLine(user, appointment, ReminderChannel.EMAIL, locale);
    }

    /** The line under {@code channel}'s box (#884: the Text Reminder box has its own). */
    public static ReminderStatusLine statusLine(LoggedInInfo user, Appointment appointment, ReminderChannel channel,
                                                Locale locale) {
        if (appointment == null || appointment.getId() == null || appointment.getId() <= 0
                || appointment.getDemographicNo() <= 0
                || !mayReadStatus(SpringUtils.getBean(SecurityInfoManager.class), user, appointment.getDemographicNo())) {
            return null;
        }
        return SpringUtils.getBean(AppointmentReminderService.class).statusLine(appointment, channel, locale)
                .orElse(null);
    }

    /**
     * A per-patient check for the status line: whoever may open this patient's appointment sees it, as the owner
     * decided (#5887), front desk included. That is the Edit Appointment page's own right, {@code _appointment}
     * write, asked for this patient, with the patient's locks: access to their record ({@code _eChart$N} and
     * {@code _demographic$N}) and read on it. No right on the patient's email is needed: the line holds only a
     * time or a fixed reason, never an address or message text.
     */
    static boolean mayReadStatus(SecurityInfoManager security, LoggedInInfo user, int demographicNo) {
        return user != null
                && security.isAllowedAccessToPatientRecord(user, demographicNo)
                && security.hasPrivilege(user, "_demographic", SecurityInfoManager.READ, demographicNo)
                && security.hasPrivilege(user, "_appointment", SecurityInfoManager.WRITE, demographicNo);
    }

    /** Whether Edit Appointment shows the Email Reminder box ticked for {@code appointmentNo}. */
    public static boolean isRequested(Integer appointmentNo) {
        return isRequested(appointmentNo, ReminderChannel.EMAIL);
    }

    /** Whether Edit Appointment shows {@code channel}'s box ticked for {@code appointmentNo}. */
    public static boolean isRequested(Integer appointmentNo, ReminderChannel channel) {
        if (appointmentNo == null || appointmentNo <= 0) {
            return false;
        }
        return SpringUtils.getBean(AppointmentReminderService.class).isRequested(appointmentNo, channel);
    }
}
