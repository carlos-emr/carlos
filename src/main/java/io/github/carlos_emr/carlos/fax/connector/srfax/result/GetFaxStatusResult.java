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
 * JSON model representing the result of an SRFax Get_FaxStatus API call.
 *
 * This class deserialization maps each field from the SRFax API JSON response to the corresponding
 * domain object property. Each field is annotated with @JsonProperty to specify the exact JSON
 * property name returned by the SRFax API.
 *
 * The rawFileName field receives a special parsing treatment in {@link #setRawFileName(String)},
 * which parses the pipe-delimited "filename|detailsId" format and splits it into separate
 * fileName and detailsId components for convenient access.
 *
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetFaxStatusResult {

    /**
     * Raw filename from SRFax API in "filename|detailsId" format.
     * Maps to the SRFax API "FileName" property. This value is parsed into
     * separate fileName and detailsId components by {@link #setRawFileName(String)}.
     */
    @JsonProperty("FileName")
    private String rawFileName;

    /**
     * Parsed filename component extracted from rawFileName.
     * Contains the filename portion before the pipe delimiter.
     */
    @JsonIgnore
    private String fileName;

    /**
     * Parsed details ID extracted from rawFileName.
     * Contains the details ID portion after the pipe delimiter, or null if not present.
     */
    @JsonIgnore
    private String detailsId;

    /**
     * Transmission status of the fax.
     * Maps to the SRFax API "SentStatus" property.
     */
    @JsonProperty("SentStatus")
    private String sentStatus;

    /**
     * Date and time when the fax was queued for transmission.
     * Maps to the SRFax API "DateQueued" property.
     */
    @JsonProperty("DateQueued")
    private String dateQueued;

    /**
     * Date and time when the fax was successfully sent.
     * Maps to the SRFax API "DateSent" property.
     */
    @JsonProperty("DateSent")
    private String dateSent;

    /**
     * Timestamp in epoch format representing the fax transmission time.
     * Maps to the SRFax API "EpochTime" property.
     */
    @JsonProperty("EpochTime")
    private String epochTime;

    /**
     * Recipient fax number to which the fax was sent.
     * Maps to the SRFax API "ToFaxNumber" property.
     */
    @JsonProperty("ToFaxNumber")
    private String toFaxNumber;

    /**
     * Number of pages in the transmitted fax.
     * Maps to the SRFax API "Pages" property.
     */
    @JsonProperty("Pages")
    private String pages;

    /**
     * Duration of the fax transmission in seconds.
     * Maps to the SRFax API "Duration" property.
     */
    @JsonProperty("Duration")
    private String duration;

    /**
     * Remote fax equipment identifier reported during transmission.
     * Maps to the SRFax API "RemoteID" property.
     */
    @JsonProperty("RemoteID")
    private String remoteId;

    /**
     * Error code if the fax transmission failed.
     * Maps to the SRFax API "ErrorCode" property. A value indicates a transmission error.
     */
    @JsonProperty("ErrorCode")
    private String errorCode;

    /**
     * File size of the transmitted fax in bytes.
     * Maps to the SRFax API "Size" property.
     */
    @JsonProperty("Size")
    private String size;

    /**
     * Account code used for billing purposes on the transmitted fax.
     * Maps to the SRFax API "AccountCode" property.
     */
    @JsonProperty("AccountCode")
    private String accountCode;

    /**
     * Gets the raw filename value from the SRFax API response.
     * The raw value is in "filename|detailsId" format.
     * For parsed components, use {@link #getFileName()} and {@link #getDetailsId()} instead.
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
     * Gets the parsed filename component extracted from rawFileName.
     * This is the portion before the pipe delimiter in "filename|detailsId" format.
     *
     * @return String the parsed filename, or null if rawFileName was not set
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Gets the parsed details ID component extracted from rawFileName.
     * This is the portion after the pipe delimiter in "filename|detailsId" format.
     *
     * @return String the parsed details ID, or null if not present in the raw filename
     */
    public String getDetailsId() {
        return detailsId;
    }

    /**
     * Gets the transmission status of the fax.
     *
     * @return String the sent status from the SRFax API, or null if not set
     */
    public String getSentStatus() {
        return sentStatus;
    }

    /**
     * Sets the transmission status of the fax.
     *
     * @param sentStatus String the sent status from the SRFax API
     */
    public void setSentStatus(String sentStatus) {
        this.sentStatus = sentStatus;
    }

    /**
     * Gets the date and time when the fax was queued for transmission.
     *
     * @return String the date queued from the SRFax API, or null if not set
     */
    public String getDateQueued() {
        return dateQueued;
    }

    /**
     * Sets the date and time when the fax was queued for transmission.
     *
     * @param dateQueued String the date queued from the SRFax API
     */
    public void setDateQueued(String dateQueued) {
        this.dateQueued = dateQueued;
    }

    /**
     * Gets the date and time when the fax was successfully sent.
     *
     * @return String the date sent from the SRFax API, or null if not set
     */
    public String getDateSent() {
        return dateSent;
    }

    /**
     * Sets the date and time when the fax was successfully sent.
     *
     * @param dateSent String the date sent from the SRFax API
     */
    public void setDateSent(String dateSent) {
        this.dateSent = dateSent;
    }

    /**
     * Gets the timestamp in epoch format representing the fax transmission time.
     *
     * @return String the epoch time from the SRFax API, or null if not set
     */
    public String getEpochTime() {
        return epochTime;
    }

    /**
     * Sets the timestamp in epoch format representing the fax transmission time.
     *
     * @param epochTime String the epoch time from the SRFax API
     */
    public void setEpochTime(String epochTime) {
        this.epochTime = epochTime;
    }

    /**
     * Gets the recipient fax number to which the fax was sent.
     *
     * @return String the destination fax number from the SRFax API, or null if not set
     */
    public String getToFaxNumber() {
        return toFaxNumber;
    }

    /**
     * Sets the recipient fax number to which the fax was sent.
     *
     * @param toFaxNumber String the destination fax number from the SRFax API
     */
    public void setToFaxNumber(String toFaxNumber) {
        this.toFaxNumber = toFaxNumber;
    }

    /**
     * Gets the number of pages in the transmitted fax.
     *
     * @return String the page count from the SRFax API, or null if not set
     */
    public String getPages() {
        return pages;
    }

    /**
     * Sets the number of pages in the transmitted fax.
     *
     * @param pages String the page count from the SRFax API
     */
    public void setPages(String pages) {
        this.pages = pages;
    }

    /**
     * Gets the duration of the fax transmission.
     *
     * @return String the transmission duration in seconds from the SRFax API, or null if not set
     */
    public String getDuration() {
        return duration;
    }

    /**
     * Sets the duration of the fax transmission.
     *
     * @param duration String the transmission duration in seconds from the SRFax API
     */
    public void setDuration(String duration) {
        this.duration = duration;
    }

    /**
     * Gets the remote fax equipment identifier reported during transmission.
     *
     * @return String the remote ID from the SRFax API, or null if not set
     */
    public String getRemoteId() {
        return remoteId;
    }

    /**
     * Sets the remote fax equipment identifier reported during transmission.
     *
     * @param remoteId String the remote ID from the SRFax API
     */
    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    /**
     * Gets the error code if the fax transmission failed.
     * A non-null value indicates that transmission encountered an error.
     *
     * @return String the error code from the SRFax API, or null if no error occurred
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Sets the error code if the fax transmission failed.
     *
     * @param errorCode String the error code from the SRFax API
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Gets the file size of the transmitted fax.
     *
     * @return String the file size in bytes from the SRFax API, or null if not set
     */
    public String getSize() {
        return size;
    }

    /**
     * Sets the file size of the transmitted fax.
     *
     * @param size String the file size in bytes from the SRFax API
     */
    public void setSize(String size) {
        this.size = size;
    }

    /**
     * Gets the account code used for billing purposes on the transmitted fax.
     *
     * @return String the account code from the SRFax API, or null if not set
     */
    public String getAccountCode() {
        return accountCode;
    }

    /**
     * Sets the account code used for billing purposes on the transmitted fax.
     *
     * @param accountCode String the account code from the SRFax API
     */
    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }
}
