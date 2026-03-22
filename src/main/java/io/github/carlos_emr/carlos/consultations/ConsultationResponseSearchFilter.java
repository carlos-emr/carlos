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
package io.github.carlos_emr.carlos.consultations;

import java.util.Date;

/**
 * Filter criteria for searching consultation responses with sorting and pagination support.
 *
 * <p>Encapsulates all search parameters for consultation response queries, including
 * date range filters (referral, response, and appointment dates), status, team,
 * demographic, MRP (Most Responsible Provider), and urgency. Supports configurable
 * sort modes and directions for result ordering. Defaults to sorting by referral
 * date descending.</p>
 *
 * <p>Differs from {@link ConsultationRequestSearchFilter} by including response-specific
 * fields such as response date range and sort columns for referring doctor and response date.</p>
 *
 * @since 2026-03-17
 */
public class ConsultationResponseSearchFilter {

    /**
     * Available sort columns for consultation response search results.
     */
    public static enum SORTMODE {
        Demographic, ReferringDoctor, Team, Status, Provider, AppointmentDate, FollowUpDate, ReferralDate, ResponseDate, Urgency
    }

    /**
     * Sort direction for search results.
     */
    public static enum SORTDIR {
        asc, desc
    }

    private Integer id;
    private Date referralStartDate;
    private Date referralEndDate;
    private Date responseStartDate;
    private Date responseEndDate;
    private Integer status;
    private String team;
    private Date appointmentStartDate;
    private Date appointmentEndDate;
    private Integer demographicNo;
    private Integer mrpNo;
    private String urgency;
    private SORTMODE sortMode = SORTMODE.ReferralDate;
    private SORTDIR sortDir = SORTDIR.desc;
    private int startIndex;
    private int numToReturn;


    /**
     * Returns the consultation response ID filter.
     *
     * @return Integer the consultation response ID, or {@code null} if not set
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the consultation response ID filter.
     *
     * @param id Integer the consultation response ID to filter by
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Returns the start date of the referral date range filter.
     *
     * @return Date the referral range start date, or {@code null} if not set
     */
    public Date getReferralStartDate() {
        return referralStartDate;
    }

    /**
     * Sets the start date of the referral date range filter.
     *
     * @param referralStartDate Date the referral range start date (inclusive)
     */
    public void setReferralStartDate(Date referralStartDate) {
        this.referralStartDate = referralStartDate;
    }

    /**
     * Returns the end date of the referral date range filter.
     *
     * @return Date the referral range end date, or {@code null} if not set
     */
    public Date getReferralEndDate() {
        return referralEndDate;
    }

    /**
     * Sets the end date of the referral date range filter.
     *
     * @param referralEndDate Date the referral range end date (inclusive)
     */
    public void setReferralEndDate(Date referralEndDate) {
        this.referralEndDate = referralEndDate;
    }

    /**
     * Returns the start date of the response date range filter.
     *
     * @return Date the response range start date, or {@code null} if not set
     */
    public Date getResponseStartDate() {
        return responseStartDate;
    }

    /**
     * Sets the start date of the response date range filter.
     *
     * @param responseStartDate Date the response range start date (inclusive)
     */
    public void setResponseStartDate(Date responseStartDate) {
        this.responseStartDate = responseStartDate;
    }

    /**
     * Returns the end date of the response date range filter.
     *
     * @return Date the response range end date, or {@code null} if not set
     */
    public Date getResponseEndDate() {
        return responseEndDate;
    }

    /**
     * Sets the end date of the response date range filter.
     *
     * @param responseEndDate Date the response range end date (inclusive)
     */
    public void setResponseEndDate(Date responseEndDate) {
        this.responseEndDate = responseEndDate;
    }

    /**
     * Returns the consultation response status filter.
     *
     * @return Integer the status code, or {@code null} if not set
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the consultation response status filter.
     *
     * @param status Integer the status code to filter by
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the team filter value.
     *
     * @return String the team name, or {@code null} if not set
     */
    public String getTeam() {
        return team;
    }

    /**
     * Sets the team filter value.
     *
     * @param team String the team name to filter by
     */
    public void setTeam(String team) {
        this.team = team;
    }

    /**
     * Returns the start date of the appointment date range filter.
     *
     * @return Date the appointment range start date, or {@code null} if not set
     */
    public Date getAppointmentStartDate() {
        return appointmentStartDate;
    }

    /**
     * Sets the start date of the appointment date range filter.
     *
     * @param appointmentStartDate Date the appointment range start date (inclusive)
     */
    public void setAppointmentStartDate(Date appointmentStartDate) {
        this.appointmentStartDate = appointmentStartDate;
    }

    /**
     * Returns the end date of the appointment date range filter.
     *
     * @return Date the appointment range end date, or {@code null} if not set
     */
    public Date getAppointmentEndDate() {
        return appointmentEndDate;
    }

    /**
     * Sets the end date of the appointment date range filter.
     *
     * @param appointmentEndDate Date the appointment range end date (inclusive)
     */
    public void setAppointmentEndDate(Date appointmentEndDate) {
        this.appointmentEndDate = appointmentEndDate;
    }

    /**
     * Returns the patient demographic number filter.
     *
     * @return Integer the demographic number, or {@code null} if not set
     */
    public Integer getDemographicNo() {
        return demographicNo;
    }

    /**
     * Sets the patient demographic number filter.
     *
     * @param demographicNo Integer the patient demographic number to filter by
     */
    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    /**
     * Returns the Most Responsible Provider (MRP) number filter.
     *
     * @return Integer the MRP provider number, or {@code null} if not set
     */
    public Integer getMrpNo() {
        return mrpNo;
    }

    /**
     * Sets the Most Responsible Provider (MRP) number filter.
     *
     * @param mrpNo Integer the MRP provider number to filter by
     */
    public void setMrpNo(Integer mrpNo) {
        this.mrpNo = mrpNo;
    }

    /**
     * Returns the urgency level filter.
     *
     * @return String the urgency level, or {@code null} if not set
     */
    public String getUrgency() {
        return urgency;
    }

    /**
     * Sets the urgency level filter.
     *
     * @param urgency String the urgency level to filter by
     */
    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    /**
     * Returns the current sort column for search results.
     *
     * @return SORTMODE the active sort column, defaults to {@link SORTMODE#ReferralDate}
     */
    public SORTMODE getSortMode() {
        return sortMode;
    }

    /**
     * Sets the sort column for search results.
     *
     * @param sortMode SORTMODE the column to sort results by
     */
    public void setSortMode(SORTMODE sortMode) {
        this.sortMode = sortMode;
    }

    /**
     * Returns the current sort direction.
     *
     * @return SORTDIR the sort direction, defaults to {@link SORTDIR#desc}
     */
    public SORTDIR getSortDir() {
        return sortDir;
    }

    /**
     * Sets the sort direction for search results.
     *
     * @param sortDir SORTDIR the sort direction (ascending or descending)
     */
    public void setSortDir(SORTDIR sortDir) {
        this.sortDir = sortDir;
    }

    /**
     * Returns the zero-based start index for pagination.
     *
     * @return int the starting index of results to retrieve
     */
    public int getStartIndex() {
        return startIndex;
    }

    /**
     * Sets the zero-based start index for pagination.
     *
     * @param startIndex int the starting index of results to retrieve
     */
    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    /**
     * Returns the maximum number of results to return per page.
     *
     * @return int the page size limit
     */
    public int getNumToReturn() {
        return numToReturn;
    }

    /**
     * Sets the maximum number of results to return per page.
     *
     * @param numToReturn int the page size limit
     */
    public void setNumToReturn(int numToReturn) {
        this.numToReturn = numToReturn;
    }
}
