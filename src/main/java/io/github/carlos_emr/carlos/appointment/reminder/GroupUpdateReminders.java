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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * The reminder steps of a Group Update (#5887), held while the group page's transaction is open and run only once
 * it has committed.
 *
 * <p>Group Update, Group Cancel and Group Delete write in one transaction, which is rolled back as a whole when
 * anything in it fails. A reminder call inside that transaction would join it, so a reminder that cannot be stored
 * (a database error, an edit racing the reminder job) would mark it for rollback and undo every booking of the
 * group. The bookings must always be saved. So the page only notes each step here, and runs them after the commit,
 * each in a transaction of its own. A step that fails there is logged and marks the request
 * ({@link AppointmentReminderChoice#notRecorded}); the page then says the appointments were saved but the reminder
 * was not, and nothing is undone.</p>
 *
 * @since 2026-11-16
 */
public final class GroupUpdateReminders {

    private final HttpServletRequest request;
    private final LoggedInInfo user;
    private final List<Runnable> steps = new ArrayList<>();

    /**
     * @param request the group page's request: the form it carries decides whether reminders are on
     * @param user    the user making the group change
     */
    public GroupUpdateReminders(HttpServletRequest request, LoggedInInfo user) {
        this.request = request;
        this.user = user;
    }

    /** Once committed: {@link AppointmentReminderChoice#moveReminders} from the removed booking to its new one. */
    public void moveReminders(Integer replacedNo, Appointment rebooked) {
        steps.add(() -> AppointmentReminderChoice.moveReminders(request, user, replacedNo, rebooked));
    }

    /** Once committed: {@link AppointmentReminderChoice#recordAfterGroupUpdate} for the appointment Edit opened. */
    public void recordAfterGroupUpdate(Appointment edited) {
        steps.add(() -> AppointmentReminderChoice.recordAfterGroupUpdate(request, user, edited));
    }

    /**
     * Runs the held steps in order. Call it only after the group transaction has committed, and never when it
     * rolled back (the bookings the steps name are then gone). A failing step does not stop the next one.
     */
    public void runAfterCommit() {
        List<Runnable> held = List.copyOf(steps);
        steps.clear();
        for (Runnable step : held) {
            AppointmentReminderChoice.onGroupPage(request, step);
        }
    }
}
