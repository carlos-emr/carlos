/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import io.github.carlos_emr.carlos.appointment.search.SearchConfig;
import io.github.carlos_emr.carlos.appointment.search.TimeSlot;
import io.github.carlos_emr.carlos.appointment.search.AppointmentType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.CalendarScheduleCodePairTransfer;

/**
 * Service interface for searching available appointment slots in the CARLOS EMR
 * scheduling system.
 *
 * <p>Provides operations to query appointment types, retrieve provider search
 * configurations, and find available time slots based on schedule codes and
 * appointment type criteria.</p>
 *
 * @see AppointmentSearchManagerImpl
 * @see io.github.carlos_emr.carlos.appointment.search.SearchConfig
 * @see io.github.carlos_emr.carlos.appointment.search.TimeSlot
 * @since 2026-03-17
 */
public interface AppointmentSearchManager {

    /**
     * Retrieves available appointment types for a patient based on search configuration.
     * Currently returns the same results as the provider variant but may be
     * customized per-demographic in the future.
     *
     * @param config SearchConfig the search configuration
     * @param demographicNo Integer the patient demographic number
     * @return List of AppointmentType available appointment types
     */
    public List<AppointmentType> getAppointmentTypes(SearchConfig config, Integer demographicNo);

    /**
     * Retrieves available appointment types for a provider based on search configuration.
     *
     * @param config SearchConfig the search configuration
     * @param providerNo String the provider number
     * @return List of AppointmentType available appointment types
     */
    public List<AppointmentType> getAppointmentTypes(SearchConfig config, String providerNo);

    /**
     * Retrieves the appointment search configuration for a specific provider.
     *
     * @param providerNo String the provider number
     * @return SearchConfig the provider's search configuration
     */
    public SearchConfig getProviderSearchConfig(String providerNo);

    /**
     * Searches for available appointment time slots matching the given criteria.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param config SearchConfig the search configuration parameters
     * @param demographicNo Integer the patient demographic number
     * @param appointmentTypeId Long the appointment type to search for
     * @param startDate Calendar the date from which to begin searching
     * @return List of TimeSlot available appointment slots
     * @throws ClassNotFoundException if the search handler class cannot be found
     * @throws InstantiationException if the search handler cannot be instantiated
     * @throws IllegalAccessException if the search handler constructor is not accessible
     */
    public List<TimeSlot> findAppointment(LoggedInInfo loggedInInfo, SearchConfig config, Integer demographicNo, Long appointmentTypeId, Calendar startDate) throws java.lang.ClassNotFoundException, java.lang.InstantiationException, java.lang.IllegalAccessException;

    /**
     * Filters a day's work schedule to return only time slots matching the specified
     * schedule codes, sorted by binary search on the code array.
     *
     * @param dayWorkSchedule DayWorkSchedule the provider's daily schedule
     * @param codes Character[] sorted array of schedule code characters to match
     * @param providerNo String the provider number for the resulting time slots
     * @return List of TimeSlot entries matching the allowed schedule codes
     */
    public static List<TimeSlot> getAllowedTimesByType(DayWorkSchedule dayWorkSchedule, Character[] codes, String providerNo) {
        ArrayList<TimeSlot> allowedTimesFilteredByType = new ArrayList<TimeSlot>();
        for (CalendarScheduleCodePairTransfer entry : CalendarScheduleCodePairTransfer.toTransfer(dayWorkSchedule.getTimeSlots())) {
            char c = entry.getScheduleCode();
            if (Arrays.binarySearch(codes, c) >= 0) {
                allowedTimesFilteredByType.add(new TimeSlot(providerNo, null, entry.getDate(), c));
            }
        }
        return allowedTimesFilteredByType;
    }
}
