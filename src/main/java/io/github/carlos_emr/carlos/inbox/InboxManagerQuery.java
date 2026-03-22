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
package io.github.carlos_emr.carlos.inbox;

import java.util.Date;

/**
 * Query parameter object for inbox document retrieval operations.
 * Encapsulates search criteria including provider, patient demographics,
 * document status, date range, and pagination settings used to filter
 * and retrieve inbox items such as lab results and scanned documents.
 *
 * @since 2026-03-17
 */
public class InboxManagerQuery {

    private String providerNo;
    private String searchProviderNo;
    private String status;
    private Integer demographicNo;
    private String scannedDocStatus = "I";
    private String patientLastName;
    private String patientFirstName;
    private String patientHIN;

    private int page = 0;
    private int pageSize = 20;
    private Date startDate;
    private Date endDate;

    private String view;

    private Date newestDate;

    /**
     * Returns the provider number of the inbox owner.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number of the inbox owner.
     *
     * @param providerNo String the provider number
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Returns the provider number used for search filtering,
     * which may differ from the inbox owner's provider number.
     *
     * @return String the search provider number
     */
    public String getSearchProviderNo() {
        return searchProviderNo;
    }

    /**
     * Sets the provider number used for search filtering.
     *
     * @param searchProviderNo String the provider number to filter by
     */
    public void setSearchProviderNo(String searchProviderNo) {
        this.searchProviderNo = searchProviderNo;
    }

    /**
     * Returns the document acknowledgement status filter.
     *
     * @return String the status filter value
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the document acknowledgement status filter.
     *
     * @param status String the status to filter by
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the demographic (patient) number filter.
     *
     * @return Integer the demographic number, or {@code null} if not filtering by patient
     */
    public Integer getDemographicNo() {
        return demographicNo;
    }

    /**
     * Sets the demographic (patient) number to filter inbox results for a specific patient.
     *
     * @param demographicNo Integer the demographic number
     */
    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    /**
     * Returns the scanned document status filter. Defaults to "I" (incoming).
     *
     * @return String the scanned document status
     */
    public String getScannedDocStatus() {
        return scannedDocStatus;
    }

    /**
     * Sets the scanned document status filter.
     *
     * @param scannedDocStatus String the scanned document status
     */
    public void setScannedDocStatus(String scannedDocStatus) {
        this.scannedDocStatus = scannedDocStatus;
    }

    /**
     * Returns the patient last name filter for searching inbox by patient name.
     *
     * @return String the patient last name
     */
    public String getPatientLastName() {
        return patientLastName;
    }

    /**
     * Sets the patient last name filter.
     *
     * @param patientLastName String the patient last name to filter by
     */
    public void setPatientLastName(String patientLastName) {
        this.patientLastName = patientLastName;
    }

    /**
     * Returns the patient first name filter for searching inbox by patient name.
     *
     * @return String the patient first name
     */
    public String getPatientFirstName() {
        return patientFirstName;
    }

    /**
     * Sets the patient first name filter.
     *
     * @param patientFirstName String the patient first name to filter by
     */
    public void setPatientFirstName(String patientFirstName) {
        this.patientFirstName = patientFirstName;
    }

    /**
     * Returns the patient Health Insurance Number (HIN) filter.
     *
     * @return String the patient HIN
     */
    public String getPatientHIN() {
        return patientHIN;
    }

    /**
     * Sets the patient Health Insurance Number (HIN) filter.
     *
     * @param patientHIN String the patient HIN to filter by
     */
    public void setPatientHIN(String patientHIN) {
        this.patientHIN = patientHIN;
    }

    /**
     * Returns the zero-based page number for paginated results.
     *
     * @return int the current page number (defaults to 0)
     */
    public int getPage() {
        return page;
    }

    /**
     * Sets the zero-based page number for paginated results.
     *
     * @param page int the page number
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * Returns the number of results per page.
     *
     * @return int the page size (defaults to 20)
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Sets the number of results per page.
     *
     * @param pageSize int the page size
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Returns the start date for the date range filter.
     *
     * @return Date the start date, or {@code null} if no lower date bound is set
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date for the date range filter (inclusive).
     *
     * @param startDate Date the start date
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Returns the end date for the date range filter.
     *
     * @return Date the end date, or {@code null} if no upper date bound is set
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date for the date range filter (inclusive).
     *
     * @param endDate Date the end date
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Returns the view type identifier that controls how inbox results are displayed.
     *
     * @return String the view type
     */
    public String getView() {
        return view;
    }

    /**
     * Sets the view type identifier that controls how inbox results are displayed.
     *
     * @param view String the view type
     */
    public void setView(String view) {
        this.view = view;
    }

    /**
     * Returns the newest date threshold used for filtering or sorting inbox items.
     *
     * @return Date the newest date, or {@code null} if not set
     */
    public Date getNewestDate() {
        return newestDate;
    }

    /**
     * Sets the newest date threshold used for filtering or sorting inbox items.
     *
     * @param newestDate Date the newest date
     */
    public void setNewestDate(Date newestDate) {
        this.newestDate = newestDate;
    }
}