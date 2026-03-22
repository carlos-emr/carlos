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
package io.github.carlos_emr.carlos.appointment.search;

import java.util.Calendar;
import java.util.Comparator;

/**
 * Represents an available time slot in a provider's schedule for appointment booking.
 *
 * <p>Contains the provider information, the available date/time, the schedule template
 * code character, the appointment type, and the associated demographic (patient) number.
 * Instances are comparable by time via {@link #getTimeSlotComparator()}.</p>
 *
 * @since 2026-03-17
 */
public class TimeSlot {
    String providerNo = null;
    private Integer demographicNo = null;
    String providerName = null;
    Calendar availableApptTime = null;
    Character code = null;
    Long appointmentType = null;

    /**
     * Constructs an empty time slot.
     */
    public TimeSlot() {
    }

    /**
     * Constructs a time slot for a given provider and time.
     *
     * @param providerNo String the provider number
     * @param providerName String the provider display name
     * @param cal Calendar the available appointment time
     */
    public TimeSlot(String providerNo, String providerName, Calendar cal) {
        this.providerNo = providerNo;
        this.providerName = providerName;
        this.availableApptTime = cal;
    }

    /**
     * Constructs a time slot for a given provider, time, and schedule template code.
     *
     * @param providerNo String the provider number
     * @param providerName String the provider display name
     * @param cal Calendar the available appointment time
     * @param code Character the schedule template code for this slot
     */
    public TimeSlot(String providerNo, String providerName, Calendar cal, Character code) {
        this.providerNo = providerNo;
        this.providerName = providerName;
        this.availableApptTime = cal;
        this.code = code;
    }

    /**
     * Returns the provider number.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number.
     *
     * @param providerNo String the provider number
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Returns the provider display name.
     *
     * @return String the provider name
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Sets the provider display name.
     *
     * @param providerName String the provider name
     */
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    /**
     * Returns the available appointment time for this slot.
     *
     * @return Calendar the available time
     */
    public Calendar getAvailableApptTime() {
        return availableApptTime;
    }

    /**
     * Sets the available appointment time for this slot.
     *
     * @param availableApptTime Calendar the available time
     */
    public void setAvailableApptTime(Calendar availableApptTime) {
        this.availableApptTime = availableApptTime;
    }

    /**
     * Returns the schedule template code character for this time slot.
     *
     * @return Character the schedule code
     */
    public Character getCode() {
        return code;
    }

    /**
     * Sets the schedule template code character for this time slot.
     *
     * @param code Character the schedule code
     */
    public void setCode(Character code) {
        this.code = code;
    }

    /**
     * Returns the appointment type ID associated with this time slot.
     *
     * @return Long the appointment type ID
     */
    public Long getAppointmentType() {
        return appointmentType;
    }

    /**
     * Sets the appointment type ID associated with this time slot.
     *
     * @param appointmentType Long the appointment type ID
     */
    public void setAppointmentType(Long appointmentType) {
        this.appointmentType = appointmentType;
    }

    private static final Comparator<TimeSlot> TIMESLOT_DATE_COMPARATOR = new Comparator<TimeSlot>() {
        @Override
        public int compare(TimeSlot arg0, TimeSlot arg1) {
            if (arg0 == null) return 1;
            if (arg1 == null) return -1;

            if (arg0.getAvailableApptTime().before(arg1.getAvailableApptTime())) return -1;
            if (arg1.getAvailableApptTime().before(arg0.getAvailableApptTime())) return 1;
            return 0;
        }
    };

    public static Comparator<TimeSlot> getTimeSlotComparator() {
        return TIMESLOT_DATE_COMPARATOR;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

}
