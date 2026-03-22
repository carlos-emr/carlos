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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.appointment.search.SearchConfig;
import io.github.carlos_emr.carlos.appointment.search.TimeSlot;
import io.github.carlos_emr.carlos.managers.DayWorkSchedule;
import io.github.carlos_emr.carlos.utility.MiscUtils;


/**
 * Filters out time slots that are in the past or within a configurable buffer period
 * from the current time.
 *
 * <p>Supports a {@code buffer} parameter (in minutes) that shifts the cutoff time forward,
 * and a {@code nowDate} parameter for testing purposes that overrides the current time.</p>
 *
 * @since 2026-03-17
 */
public class FutureApptFilter implements AvailableTimeSlotFilter {

    private static Logger logger = MiscUtils.getLogger();

    @Override
    public List<TimeSlot> filterAvailableTimeSlots(SearchConfig clinic, String mrp, String providerId, Long appointmentTypeId, DayWorkSchedule dayWorkScheduleTransfer, List<TimeSlot> currentlyAllowedTimeSlots, Calendar date, Map<String, String> params) {
        ArrayList<TimeSlot> allowedTimesFilteredByFutureTimes = new ArrayList<TimeSlot>();
        Calendar now = new GregorianCalendar();

        setNowDate(params, now);
        setBuffer(params, now);

        for (TimeSlot startTime : currentlyAllowedTimeSlots) {
            if (startTime.getAvailableApptTime().after(now)) {
                allowedTimesFilteredByFutureTimes.add(startTime);
            }
        }

        if (allowedTimesFilteredByFutureTimes.size() == 0) {
            //BookingLearningManager.recommendDayToBeSkipped(clinic, providerId, date, appointmentTypeId, this.getClass().getName());
        }

        return allowedTimesFilteredByFutureTimes;


    }

    /**
     * Adds the configured buffer (in minutes) to the given calendar.
     *
     * @param params Map&lt;String, String&gt; the filter parameters containing the "buffer" key
     * @param now Calendar the calendar to advance by the buffer amount
     */
    public void setBuffer(Map<String, String> params, Calendar now) {
        String buffer = null;
        if (params != null && params.get("buffer") != null) {
            try {
                buffer = params.get("buffer");
                now.add(Calendar.MINUTE, Integer.parseInt(buffer));
            } catch (Exception e) {
                logger.error("buffer " + buffer + " is not parsing correctly. Needs to be an int in minutes ", e);
            }
        }
    }

    /**
     * Overrides the "now" time using the {@code nowDate} parameter if present.
     * Used for testing to simulate a specific current time.
     *
     * @param params Map&lt;String, String&gt; the filter parameters containing the optional "nowDate" key
     *               in {@code yyyy-MM-dd HH:mm:ss} format
     * @param now Calendar the calendar to set to the specified date
     */
    public void setNowDate(Map<String, String> params, Calendar now) {
        String nowDate = null;
        if (params != null && params.get("nowDate") != null) {
            try {
                nowDate = params.get("nowDate");
                logger.debug("nowDate = " + nowDate);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                now.setTime(sdf.parse(nowDate));
            } catch (Exception e) {
                logger.error("Now Date " + nowDate + " is not parsing correctly. Needs to be in format yyyy-MM-dd HH:mm:ss ", e);
            }
        }
    }
}
