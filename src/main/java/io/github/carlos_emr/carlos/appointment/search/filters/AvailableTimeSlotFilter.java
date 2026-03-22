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
package io.github.carlos_emr.carlos.appointment.search.filters;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.appointment.search.SearchConfig;
import io.github.carlos_emr.carlos.appointment.search.TimeSlot;
import io.github.carlos_emr.carlos.managers.DayWorkSchedule;


/**
 * Strategy interface for filtering available appointment time slots during the booking search.
 *
 * <p>Implementations apply specific criteria (e.g., existing appointments, future-only,
 * open access, contiguous time) to narrow down the list of candidate time slots. Filters
 * are chained together via the {@link SearchConfig} and {@link FilterDefinition} pipeline.</p>
 *
 * @since 2026-03-17
 */
public interface AvailableTimeSlotFilter {
    /**
     * Filters the given list of available time slots according to this filter's criteria
     * and returns a new list containing only the qualifying slots.
     *
     * <p>Implementations should not modify the passed-in list; instead, they should create
     * a new list and copy qualifying time slots into it.</p>
     *
     * @param clinic SearchConfig the booking search configuration
     * @param mrp String the Most Responsible Provider number
     * @param providerId String the provider number whose schedule is being searched
     * @param appointmentTypeId Long the appointment type being booked
     * @param dayWorkScheduleTransfer DayWorkSchedule the provider's work schedule for the day
     * @param currentlyAllowedTimeSlots List&lt;TimeSlot&gt; the time slots remaining after prior filters
     * @param date Calendar the date being searched
     * @param params Map&lt;String, String&gt; the filter-specific parameters from the configuration
     * @return List&lt;TimeSlot&gt; the filtered list of available time slots
     */
    public List<TimeSlot> filterAvailableTimeSlots(SearchConfig clinic, String mrp, String providerId, Long appointmentTypeId, DayWorkSchedule dayWorkScheduleTransfer, List<TimeSlot> currentlyAllowedTimeSlots, Calendar date, Map<String, String> params);
}
