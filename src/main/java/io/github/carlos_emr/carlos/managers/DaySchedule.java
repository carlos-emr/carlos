//CHECKSTYLE:OFF
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
 */


package io.github.carlos_emr.carlos.managers;

import java.util.Calendar;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Appointment;

public final class DaySchedule {
    private Calendar startTime;
    private Calendar endTime;
    private int timeSlotDurationMin;
    private List<Appointment> appointments;

    public Calendar getStartTime() {
        return (startTime);
    }

    public void setStartTime(Calendar startTime) {
        this.startTime = startTime;
    }

    public Calendar getEndTime() {
        return (endTime);
    }

    public void setEndTime(Calendar endTime) {
        this.endTime = endTime;
    }

    public int getTimeSlotDurationMin() {
        return (timeSlotDurationMin);
    }

    public void setTimeSlotDurationMin(int timeSlotDurationMin) {
        this.timeSlotDurationMin = timeSlotDurationMin;
    }

    public List<Appointment> getAppointments() {
        return (appointments);
    }

    public void setAppointments(List<Appointment> appointments) {
        this.appointments = appointments;
    }
}
