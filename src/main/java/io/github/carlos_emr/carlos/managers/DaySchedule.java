/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.managers;

import java.util.Calendar;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Appointment;

/**
 * Immutable value object representing a provider's complete schedule for a single day,
 * including the time range, slot duration, and associated appointments.
 *
 * @since 2026-03-17
 */
public final class DaySchedule {
    private Calendar startTime;
    private Calendar endTime;
    private int timeSlotDurationMin;
    private List<Appointment> appointments;

    /**
     * Gets the start time of the day's schedule.
     *
     * @return Calendar the schedule start time
     */
    public Calendar getStartTime() {
        return (startTime);
    }

    /**
     * Sets the start time of the day's schedule.
     *
     * @param startTime Calendar the schedule start time to set
     */
    public void setStartTime(Calendar startTime) {
        this.startTime = startTime;
    }

    /**
     * Gets the end time of the day's schedule.
     *
     * @return Calendar the schedule end time
     */
    public Calendar getEndTime() {
        return (endTime);
    }

    /**
     * Sets the end time of the day's schedule.
     *
     * @param endTime Calendar the schedule end time to set
     */
    public void setEndTime(Calendar endTime) {
        this.endTime = endTime;
    }

    /**
     * Gets the duration of each time slot in minutes.
     *
     * @return int the time slot duration in minutes
     */
    public int getTimeSlotDurationMin() {
        return (timeSlotDurationMin);
    }

    /**
     * Sets the duration of each time slot in minutes.
     *
     * @param timeSlotDurationMin int the time slot duration in minutes to set
     */
    public void setTimeSlotDurationMin(int timeSlotDurationMin) {
        this.timeSlotDurationMin = timeSlotDurationMin;
    }

    /**
     * Gets the list of appointments scheduled for this day.
     *
     * @return List of Appointment objects for the day
     */
    public List<Appointment> getAppointments() {
        return (appointments);
    }

    /**
     * Sets the list of appointments scheduled for this day.
     *
     * @param appointments List of Appointment objects to set
     */
    public void setAppointments(List<Appointment> appointments) {
        this.appointments = appointments;
    }
}
