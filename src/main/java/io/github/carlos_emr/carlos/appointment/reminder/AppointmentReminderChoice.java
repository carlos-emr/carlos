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

import java.util.Locale;

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

    private AppointmentReminderChoice() {
    }

    public static boolean isOnForm(HttpServletRequest request) {
        return request != null && "1".equals(request.getParameter(SHOWN_PARAMETER));
    }

    public static boolean isTicked(HttpServletRequest request) {
        return request != null && request.getParameter(PARAMETER) != null;
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
