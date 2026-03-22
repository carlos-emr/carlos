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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.appointment.search.SearchConfig;
import io.github.carlos_emr.carlos.appointment.search.TimeSlot;
import io.github.carlos_emr.carlos.managers.DayWorkSchedule;


/**
 * Filters time slots to ensure multi-unit (multi-slot) appointments have enough
 * contiguous consecutive slots available.
 *
 * <p>When the required appointment duration exceeds a single time slot, this filter
 * checks that sufficient consecutive available slots exist before including the
 * starting slot in the results.</p>
 *
 * @since 2026-03-17
 */
public class MultiUnitFilter implements AvailableTimeSlotFilter {

    @Override
    public List<TimeSlot> filterAvailableTimeSlots(SearchConfig clinic, String mrp, String providerId, Long appointmentTypeId, DayWorkSchedule dayWorkScheduleTransfer, List<TimeSlot> currentlyAllowedTimeSlots, Calendar date, Map<String, String> params) {
        int timeSlotLen = dayWorkScheduleTransfer.getTimeSlotDurationMin();
        // allowed time codes
        ArrayList<TimeSlot> filteredResults = new ArrayList<TimeSlot>();
        for (int i = 0; i < currentlyAllowedTimeSlots.size(); i++) {
            TimeSlot entry = currentlyAllowedTimeSlots.get(i);
            Calendar apptStartTime = entry.getAvailableApptTime();
            Character code = entry.getCode();
            int apptLength = clinic.getAppointmentDuration(mrp, providerId, appointmentTypeId, code);

            if (apptLength > timeSlotLen) {
                Calendar nextAppt = (Calendar) apptStartTime.clone();
                int apptBlockLen = timeSlotLen;
                for (int j = (i + 1); j < currentlyAllowedTimeSlots.size(); j++) {
                    apptBlockLen = apptBlockLen + timeSlotLen;

                    TimeSlot nextTimeSlot = currentlyAllowedTimeSlots.get(j);
                    nextAppt.add(Calendar.MINUTE, timeSlotLen);
                    if (nextAppt.equals(nextTimeSlot.getAvailableApptTime()) && (apptLength <= apptBlockLen)) {
                        filteredResults.add(entry);
                        i = j;  // Move the loop index to end of the multi slot appt
                        break;
                    }
                }
            } else {
                filteredResults.add(entry);
            }
        }
        return (filteredResults);
    }

    /**
     * Alternative implementation of multi-unit filtering that checks only the immediately
     * following slot for contiguous availability. This is a simpler version that only
     * verifies one additional consecutive slot.
     *
     * @param clinic SearchConfig the booking search configuration
     * @param mrp String the Most Responsible Provider number
     * @param providerId String the provider number
     * @param appointmentTypeId Long the appointment type being booked
     * @param dayWorkScheduleTransfer DayWorkSchedule the provider's work schedule
     * @param currentlyAllowedTimeSlots List&lt;TimeSlot&gt; the candidate time slots
     * @param date Calendar the date being searched
     * @param params Map&lt;String, String&gt; the filter parameters
     * @return List&lt;TimeSlot&gt; the filtered time slots
     */
    public List<TimeSlot> filterAvailableTimeSlots2(SearchConfig clinic, String mrp, String providerId, Long appointmentTypeId, DayWorkSchedule dayWorkScheduleTransfer, List<TimeSlot> currentlyAllowedTimeSlots, Calendar date, Map<String, String> params) {
        int timeSlotLen = dayWorkScheduleTransfer.getTimeSlotDurationMin();
        // allowed time codes
        ArrayList<TimeSlot> filteredResults = new ArrayList<TimeSlot>();
        for (int i = 0; i < currentlyAllowedTimeSlots.size(); i++) {
            TimeSlot entry = currentlyAllowedTimeSlots.get(i);
            Calendar apptStartTime = entry.getAvailableApptTime();
            Character code = entry.getCode();
            int apptLength = clinic.getAppointmentDuration(mrp, providerId, appointmentTypeId, code);

            if (apptLength > timeSlotLen) {
                if (i + 1 < currentlyAllowedTimeSlots.size()) {
                    TimeSlot nextTimeSlot = currentlyAllowedTimeSlots.get(i + 1);
                    Calendar nextAppt = (Calendar) apptStartTime.clone();
                    nextAppt.add(Calendar.MINUTE, timeSlotLen);

                    if (nextAppt.equals(nextTimeSlot.getAvailableApptTime())) {
                        filteredResults.add(entry);
                    }
                    nextTimeSlot.getCode();
                }
            }
        }
        return (filteredResults);
    }
}
