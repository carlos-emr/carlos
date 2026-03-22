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


package io.github.carlos_emr.carlos.appointment.web;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Represents a single result from a next-available-appointment search.
 *
 * <p>Contains the provider details, appointment date/time, and duration. Provides
 * convenience methods for extracting formatted year, month, day, start time, and
 * calculated end time strings.</p>
 *
 * @since 2026-03-17
 */
public class NextAppointmentSearchResult {
    private String providerNo;
    private Date date;
    private int duration;
    private Provider provider;


    /**
     * Returns the provider number for this result.
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
     * Returns the appointment date and start time.
     *
     * @return Date the appointment date/time
     */
    public Date getDate() {
        return date;
    }

    /**
     * Sets the appointment date and start time.
     *
     * @param date Date the appointment date/time
     */
    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * Returns the appointment duration in minutes.
     *
     * @return int the duration in minutes
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Sets the appointment duration in minutes.
     *
     * @param duration int the duration in minutes
     */
    public void setDuration(int duration) {
        this.duration = duration;
    }

    /**
     * Returns the provider entity for this appointment result.
     *
     * @return Provider the healthcare provider
     */
    public Provider getProvider() {
        return provider;
    }

    /**
     * Sets the provider entity.
     *
     * @param provider Provider the healthcare provider
     */
    public void setProvider(Provider provider) {
        this.provider = provider;
    }


    /**
     * Returns the four-digit year of the appointment date.
     *
     * @return String the year (e.g., "2026")
     */
    public String getYear() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy");
        return formatter.format(getDate());
    }

    /**
     * Returns the two-digit month of the appointment date.
     *
     * @return String the month (e.g., "03" for March)
     */
    public String getMonth() {
        SimpleDateFormat formatter = new SimpleDateFormat("MM");
        return formatter.format(getDate());
    }

    /**
     * Returns the two-digit day of the appointment date.
     *
     * @return String the day (e.g., "22")
     */
    public String getDay() {
        SimpleDateFormat formatter = new SimpleDateFormat("dd");
        return formatter.format(getDate());
    }

    /**
     * Returns the appointment start time in HH:mm format.
     *
     * @return String the formatted start time
     */
    public String getStartTime() {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
        return formatter.format(getDate());
    }

    /**
     * Returns the calculated appointment end time in HH:mm format,
     * computed by adding (duration - 1) minutes to the start time.
     *
     * @return String the formatted end time
     */
    public String getEndTime() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(getDate());
        if (duration > 0) {
            cal.add(Calendar.MINUTE, duration - 1);
        }
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
        return formatter.format(cal.getTime());
    }

}
