/**
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for
 * CloudPractice Inc.
 * Victoria, British Columbia
 * Canada
 *
 * Ported to CARLOS EMR from JunoEMR (2026).
 */
package io.github.carlos_emr.carlos.fax.connector.srfax.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * JSON model representing a single inbox entry from the SRFax Get_Fax_Inbox API response.
 *
 * Each field in this class maps directly to a JSON property returned by the SRFax API.
 * The rawFileName field (mapped from the SRFax "FileName" property) is parsed in setRawFileName()
 * into two separate components: fileName (the actual filename) and detailsId (a unique identifier
 * appended after a pipe character in the raw value).
 *
 * This model is used to deserialize API responses from the SRFax fax service connector, providing
 * details about received faxes in the user's inbox including transmission status, timestamps,
 * page counts, and viewing status.
 *
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetFaxInboxResult {

    @JsonProperty("FileName")
    private String rawFileName;
    @JsonIgnore
    private String fileName;
    @JsonIgnore
    private String detailsId;
    @JsonProperty("ReceiveStatus")
    private String receiveStatus;
    @JsonProperty("Date")
    private String date;
    @JsonProperty("EpochTime")
    private String epochTime;
    @JsonProperty("CallerID")
    private String callerId;
    @JsonProperty("RemoteID")
    private String remoteId;
    @JsonProperty("Pages")
    private String pages;
    @JsonProperty("Size")
    private String size;
    @JsonProperty("ViewedStatus")
    private String viewedStatus;
    @JsonProperty("User_ID")
    private String userId;
    @JsonProperty("User_FaxNumber")
    private String userFaxNumber;

    /**
     * Gets the raw filename value from the SRFax API response.
     *
     * This value contains both the filename and details ID in "filename|detailsId" format
     * as returned by the SRFax API. Use getFileName() or getDetailsId() to access the
     * parsed components.
     *
     * @return String the raw FileName value from the SRFax API, or null if not set
     */
    public String getRawFileName() {
        return rawFileName;
    }

    /**
     * Parses the raw SRFax filename in "filename|detailsId" format.
     *
     * @param rawFileName String the raw FileName value from the SRFax API
     */
    public void setRawFileName(String rawFileName) {
        this.rawFileName = rawFileName;
        if (rawFileName == null || rawFileName.trim().isEmpty()) {
            this.fileName = null;
            this.detailsId = null;
            return;
        }
        // SRFax returns filenames in "filename|detailsId" format
        String[] parts = rawFileName.split("\\|");
        this.fileName = parts[0];
        this.detailsId = (parts.length > 1) ? parts[1] : null;
    }

    /**
     * Gets the parsed filename extracted from the raw SRFax filename.
     *
     * This is the first part of the rawFileName value, extracted before the pipe character.
     * It is populated by setRawFileName() during JSON deserialization.
     *
     * @return String the parsed filename component, or null if rawFileName was not set
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Gets the details ID extracted from the raw SRFax filename.
     *
     * This is the second part of the rawFileName value, extracted after the pipe character.
     * It is populated by setRawFileName() during JSON deserialization. The details ID uniquely
     * identifies this fax entry for API operations like retrieving detailed fax information.
     *
     * @return String the details ID component, or null if rawFileName contained no pipe character
     */
    public String getDetailsId() {
        return detailsId;
    }

    /**
     * Gets the reception status of the received fax from the SRFax API.
     *
     * Common values include "Success" for successfully received faxes.
     *
     * @return String the ReceiveStatus value from the SRFax API, or null if not set
     */
    public String getReceiveStatus() {
        return receiveStatus;
    }

    /**
     * Sets the reception status of the received fax.
     *
     * Typically set by the JSON deserialization process from the SRFax API response.
     *
     * @param receiveStatus String the ReceiveStatus value from the SRFax API
     */
    public void setReceiveStatus(String receiveStatus) {
        this.receiveStatus = receiveStatus;
    }

    /**
     * Gets the date the fax was received from the SRFax API.
     *
     * The exact date format is determined by the SRFax API response format.
     *
     * @return String the Date value representing the fax reception date, or null if not set
     */
    public String getDate() {
        return date;
    }

    /**
     * Sets the date the fax was received.
     *
     * Typically set by the JSON deserialization process from the SRFax API response.
     *
     * @param date String the Date value representing the fax reception date
     */
    public void setDate(String date) {
        this.date = date;
    }

    /**
     * Gets the epoch time (Unix timestamp) when the fax was received from the SRFax API.
     *
     * This is a numeric timestamp representing the number of seconds since January 1, 1970 UTC.
     *
     * @return String the EpochTime value representing the fax reception timestamp, or null if not set
     */
    public String getEpochTime() {
        return epochTime;
    }

    /**
     * Sets the epoch time (Unix timestamp) when the fax was received.
     *
     * Typically set by the JSON deserialization process from the SRFax API response.
     *
     * @param epochTime String the EpochTime value representing the fax reception timestamp
     */
    public void setEpochTime(String epochTime) {
        this.epochTime = epochTime;
    }

    /**
     * Gets the caller ID (originating phone number) of the incoming fax from the SRFax API.
     *
     * This is the telephone number of the fax machine or system that sent the fax.
     *
     * @return String the CallerID value representing the originating phone number, or null if not set
     */
    public String getCallerId() {
        return callerId;
    }

    /**
     * Sets the caller ID (originating phone number) of the incoming fax.
     *
     * Typically set by the JSON deserialization process from the SRFax API response.
     *
     * @param callerId String the CallerID value representing the originating phone number
     */
    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    /**
     * Gets the remote identification (CSID - Called Subscriber ID) of the sending fax machine.
     *
     * The remote ID identifies the fax device that sent the transmission from the receiver's perspective.
     *
     * @return String the RemoteID value identifying the sending fax machine, or null if not set
     */
    public String getRemoteId() {
        return remoteId;
    }

    /**
     * Sets the remote identification (CSID) of the sending fax machine.
     *
     * Typically set by the JSON deserialization process from the SRFax API response.
     *
     * @param remoteId String the RemoteID value identifying the sending fax machine
     */
    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    /**
     * Gets the number of pages in the received fax from the SRFax API.
     *
     * This is a numeric value indicating how many pages were transmitted.
     *
     * @return String the Pages value representing the page count, or null if not set
     */
    public String getPages() {
        return pages;
    }

    /**
     * Sets the number of pages in the received fax.
     *
     * Typically set by the JSON deserialization process from the SRFax API response.
     *
     * @param pages String the Pages value representing the page count
     */
    public void setPages(String pages) {
        this.pages = pages;
    }

    /**
     * Gets the file size of the received fax from the SRFax API.
     *
     * The size is typically measured in bytes.
     *
     * @return String the Size value representing the file size in bytes, or null if not set
     */
    public String getSize() {
        return size;
    }

    /**
     * Sets the file size of the received fax.
     *
     * Typically set by the JSON deserialization process from the SRFax API response.
     *
     * @param size String the Size value representing the file size in bytes
     */
    public void setSize(String size) {
        this.size = size;
    }

    /**
     * Gets the viewing status of the fax from the SRFax API.
     *
     * Indicates whether the fax has been viewed or read by the user.
     *
     * @return String the ViewedStatus value indicating if the fax has been viewed, or null if not set
     */
    public String getViewedStatus() {
        return viewedStatus;
    }

    /**
     * Sets the viewing status of the fax.
     *
     * Typically set by the JSON deserialization process from the SRFax API response.
     *
     * @param viewedStatus String the ViewedStatus value indicating if the fax has been viewed
     */
    public void setViewedStatus(String viewedStatus) {
        this.viewedStatus = viewedStatus;
    }

    /**
     * Gets the SRFax user identifier associated with this inbox entry.
     *
     * @return String the User_ID value from the SRFax API, or null if not set
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the SRFax user identifier for this inbox entry.
     *
     * @param userId String the User_ID value from the SRFax API
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Gets the fax number of the user account that received this fax.
     *
     * @return String the User_FaxNumber value from the SRFax API, or null if not set
     */
    public String getUserFaxNumber() {
        return userFaxNumber;
    }

    /**
     * Sets the fax number of the user account that received this fax.
     *
     * @param userFaxNumber String the User_FaxNumber value from the SRFax API
     */
    public void setUserFaxNumber(String userFaxNumber) {
        this.userFaxNumber = userFaxNumber;
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }
}
