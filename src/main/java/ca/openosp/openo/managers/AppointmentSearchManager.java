//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.openosp.openo.appointment.search.SearchConfig;
import ca.openosp.openo.appointment.search.TimeSlot;
import ca.openosp.openo.appointment.search.AppointmentType;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.webserv.transfer_objects.CalendarScheduleCodePairTransfer;

public interface AppointmentSearchManager {
    //Right now these two methods return the same but in the future this could be customized based on the demographic
    public List<AppointmentType> getAppointmentTypes(SearchConfig config, Integer demographicNo);

    public List<AppointmentType> getAppointmentTypes(SearchConfig config, String providerNo);

    public SearchConfig getProviderSearchConfig(String providerNo);

    public List<TimeSlot> findAppointment(LoggedInInfo loggedInInfo, SearchConfig config, Integer demographicNo, Long appointmentTypeId, Calendar startDate) throws java.lang.ClassNotFoundException, java.lang.InstantiationException, java.lang.IllegalAccessException;

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
