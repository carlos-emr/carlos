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
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Value object representing a provider's work schedule configuration for a single day.
 *
 * <p>Contains holiday status, the duration of each time slot, and a sorted map of
 * time slots to their corresponding {@link io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode}
 * characters. Built by {@link ScheduleManagerImpl#getDayWorkSchedule(String, java.util.Calendar)}
 * from schedule date, template, and holiday data.</p>
 *
 * @since 2026-03-17
 */
public final class DayWorkSchedule {
    private boolean isHoliday;

    /**
     * is null if there's no schedule for the given day.
     */
    private Integer timeSlotDurationMin;

    /**
     * This treemap holds and orders the start of the time slot and the time code for that slot, i.e. scheduleTemplateCode
     */
    private TreeMap<Calendar, Character> timeSlots = new TreeMap<Calendar, Character>();

    /**
     * Gets the duration of each time slot in minutes.
     *
     * @return Integer the time slot duration in minutes, or null if no schedule is configured
     */
    public Integer getTimeSlotDurationMin() {
        return (timeSlotDurationMin);
    }

    /**
     * Sets the duration of each time slot in minutes.
     *
     * @param timeSlotDurationMin Integer the time slot duration in minutes
     */
    public void setTimeSlotDurationMin(Integer timeSlotDurationMin) {
        this.timeSlotDurationMin = timeSlotDurationMin;
    }

    /**
     * Checks whether this day is a holiday.
     *
     * @return boolean true if this day is a holiday
     */
    public boolean isHoliday() {
        return (isHoliday);
    }

    /**
     * Sets whether this day is a holiday.
     *
     * @param isHoliday boolean true if this day is a holiday
     */
    public void setHoliday(boolean isHoliday) {
        this.isHoliday = isHoliday;
    }

    /**
     * Gets the ordered map of time slot start times to schedule template code characters.
     *
     * <p>Entries are sorted chronologically. Each key is the start time of a slot,
     * and each value is the single-character code from the schedule template.</p>
     *
     * @return TreeMap mapping Calendar start times to Character schedule codes
     */
    public TreeMap<Calendar, Character> getTimeSlots() {
        return (timeSlots);
    }

    /**
     * Sets the ordered map of time slot start times to schedule template code characters.
     *
     * @param timeSlots TreeMap mapping Calendar start times to Character schedule codes
     */
    public void setTimeSlots(TreeMap<Calendar, Character> timeSlots) {
        this.timeSlots = timeSlots;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("isHoliday=");
        sb.append(isHoliday);
        sb.append(", timeSlotDurationMin=");
        sb.append(timeSlotDurationMin);
        sb.append(", timeBlocks=(");
        for (Entry<Calendar, Character> entry : timeSlots.entrySet()) {
            sb.append('[');
            sb.append(entry.getKey().getTime());
            sb.append('=');
            sb.append(entry.getValue());
            sb.append(']');
        }
        sb.append(")");

        return (sb.toString());
    }
}
