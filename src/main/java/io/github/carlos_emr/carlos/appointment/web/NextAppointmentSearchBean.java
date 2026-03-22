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

/**
 * Form bean that captures the search criteria for finding the next available appointment.
 *
 * <p>Holds the provider number, day-of-week filter, start/end time-of-day window,
 * schedule template code filter, and the desired number of results.</p>
 *
 * @since 2026-03-17
 */
public class NextAppointmentSearchBean {
    private String providerNo;
    private String dayOfWeek;
    private String startTimeOfDay;
    private String endTimeOfDay;
    private String code;
    private int numResults;

    /**
     * Returns the provider number to search appointments for.
     *
     * @return String the provider number, or empty string for all providers
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number to search for.
     *
     * @param providerNo String the provider number
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Returns the day-of-week filter (e.g., "daily" for weekdays, or a Calendar day-of-week integer).
     *
     * @return String the day-of-week filter
     */
    public String getDayOfWeek() {
        return dayOfWeek;
    }

    /**
     * Sets the day-of-week filter.
     *
     * @param dayOfWeek String the day-of-week filter
     */
    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    /**
     * Returns the earliest hour of day to include in the search (24-hour format).
     *
     * @return String the start hour
     */
    public String getStartTimeOfDay() {
        return startTimeOfDay;
    }

    /**
     * Sets the earliest hour of day to include in the search.
     *
     * @param startTimeOfDay String the start hour
     */
    public void setStartTimeOfDay(String startTimeOfDay) {
        this.startTimeOfDay = startTimeOfDay;
    }

    /**
     * Returns the latest hour of day to include in the search (24-hour format, exclusive).
     *
     * @return String the end hour
     */
    public String getEndTimeOfDay() {
        return endTimeOfDay;
    }

    /**
     * Sets the latest hour of day for the search.
     *
     * @param endTimeOfDay String the end hour
     */
    public void setEndTimeOfDay(String endTimeOfDay) {
        this.endTimeOfDay = endTimeOfDay;
    }

    /**
     * Returns the schedule template code to filter by.
     *
     * @return String the code filter, or empty string for no code filtering
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the schedule template code filter.
     *
     * @param code String the code to filter by
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Returns the desired maximum number of search results.
     *
     * @return int the number of results requested
     */
    public int getNumResults() {
        return numResults;
    }

    /**
     * Sets the desired maximum number of search results.
     *
     * @param numResults int the number of results requested
     */
    public void setNumResults(int numResults) {
        this.numResults = numResults;
    }


}
