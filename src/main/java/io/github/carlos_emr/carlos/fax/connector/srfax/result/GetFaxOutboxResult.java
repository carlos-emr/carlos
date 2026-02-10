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
 * JSON model representing a single entry from the SRFax Get_Fax_Outbox API response.
 *
 * This class deserializes outbound fax entries from SRFax API responses. Each field
 * corresponds to a JSON property from the API response (mapped via @JsonProperty annotations).
 *
 * <p>Notable field processing:
 * <ul>
 *   <li>The {@code rawFileName} field is automatically parsed into separate {@code fileName}
 *       and {@code detailsId} components in "filename|detailsId" format</li>
 *   <li>All fields are String types as returned by the SRFax API</li>
 *   <li>Unknown JSON properties are ignored during deserialization</li>
 * </ul>
 *
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetFaxOutboxResult {

    @JsonProperty("FileName")
    private String rawFileName;
    @JsonIgnore
    private String fileName;
    @JsonIgnore
    private String detailsId;
    @JsonProperty("SentStatus")
    private String sentStatus;
    @JsonProperty("DateQueued")
    private String dateQueued;
    @JsonProperty("DateSent")
    private String dateSent;
    @JsonProperty("EpochTime")
    private String epochTime;
    @JsonProperty("ToFaxNumber")
    private String toFaxNumber;
    @JsonProperty("Pages")
    private String pages;
    @JsonProperty("Duration")
    private String duration;
    @JsonProperty("RemoteID")
    private String remoteId;
    @JsonProperty("ErrorCode")
    private String errorCode;
    @JsonProperty("AccountCode")
    private String accountCode;
    @JsonProperty("Subject")
    private String subject;
    @JsonProperty("Size")
    private String size;
    @JsonProperty("SubmittedFiles")
    private String submittedFiles;
    @JsonProperty("User_ID")
    private String userId;
    @JsonProperty("User_FaxNumber")
    private String userFaxNumber;

    /**
     * Gets the raw filename from the SRFax API in "filename|detailsId" format.
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
     * Gets the parsed filename from the rawFileName field.
     *
     * The filename is extracted from the "filename|detailsId" format by splitting on the pipe character.
     *
     * @return String the parsed filename, or null if rawFileName has not been set
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Gets the parsed details identifier from the rawFileName field.
     *
     * The details ID is extracted from the "filename|detailsId" format by splitting on the pipe character.
     *
     * @return String the parsed details identifier, or null if not present in the raw filename
     */
    public String getDetailsId() {
        return detailsId;
    }

    /**
     * Gets the transmission status of the fax.
     *
     * @return String the SentStatus value from the SRFax API (e.g., "Sent", "Failed", "Pending")
     */
    public String getSentStatus() {
        return sentStatus;
    }

    /**
     * Sets the transmission status of the fax.
     *
     * @param sentStatus String the transmission status value
     */
    public void setSentStatus(String sentStatus) {
        this.sentStatus = sentStatus;
    }

    /**
     * Gets the date and time when the fax was queued for transmission.
     *
     * @return String the DateQueued timestamp from the SRFax API
     */
    public String getDateQueued() {
        return dateQueued;
    }

    /**
     * Sets the date and time when the fax was queued for transmission.
     *
     * @param dateQueued String the queued timestamp value
     */
    public void setDateQueued(String dateQueued) {
        this.dateQueued = dateQueued;
    }

    /**
     * Gets the date and time when the fax was successfully transmitted.
     *
     * @return String the DateSent timestamp from the SRFax API, or null if not yet sent
     */
    public String getDateSent() {
        return dateSent;
    }

    /**
     * Sets the date and time when the fax was successfully transmitted.
     *
     * @param dateSent String the transmission timestamp value
     */
    public void setDateSent(String dateSent) {
        this.dateSent = dateSent;
    }

    /**
     * Gets the transmission time as a Unix epoch timestamp.
     *
     * @return String the EpochTime value from the SRFax API
     */
    public String getEpochTime() {
        return epochTime;
    }

    /**
     * Sets the transmission time as a Unix epoch timestamp.
     *
     * @param epochTime String the epoch timestamp value
     */
    public void setEpochTime(String epochTime) {
        this.epochTime = epochTime;
    }

    /**
     * Gets the recipient fax number.
     *
     * @return String the ToFaxNumber value from the SRFax API
     */
    public String getToFaxNumber() {
        return toFaxNumber;
    }

    /**
     * Sets the recipient fax number.
     *
     * @param toFaxNumber String the recipient fax number
     */
    public void setToFaxNumber(String toFaxNumber) {
        this.toFaxNumber = toFaxNumber;
    }

    /**
     * Gets the number of pages in the transmitted fax.
     *
     * @return String the Pages value from the SRFax API
     */
    public String getPages() {
        return pages;
    }

    /**
     * Sets the number of pages in the transmitted fax.
     *
     * @param pages String the page count
     */
    public void setPages(String pages) {
        this.pages = pages;
    }

    /**
     * Gets the duration of the fax transmission.
     *
     * @return String the Duration value from the SRFax API (typically in seconds)
     */
    public String getDuration() {
        return duration;
    }

    /**
     * Sets the duration of the fax transmission.
     *
     * @param duration String the transmission duration value
     */
    public void setDuration(String duration) {
        this.duration = duration;
    }

    /**
     * Gets the remote fax machine identifier.
     *
     * @return String the RemoteID value from the SRFax API
     */
    public String getRemoteId() {
        return remoteId;
    }

    /**
     * Sets the remote fax machine identifier.
     *
     * @param remoteId String the remote fax machine identifier
     */
    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    /**
     * Gets the error code if the fax transmission failed.
     *
     * @return String the ErrorCode value from the SRFax API, or null if transmission succeeded
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Sets the error code for a failed fax transmission.
     *
     * @param errorCode String the error code value
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Gets the account code associated with the fax transmission.
     *
     * @return String the AccountCode value from the SRFax API
     */
    public String getAccountCode() {
        return accountCode;
    }

    /**
     * Sets the account code associated with the fax transmission.
     *
     * @param accountCode String the account code
     */
    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }

    /**
     * Gets the subject line or description of the fax.
     *
     * @return String the Subject value from the SRFax API
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the subject line or description of the fax.
     *
     * @param subject String the subject or description
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Gets the file size of the transmitted fax.
     *
     * @return String the Size value from the SRFax API (typically in bytes)
     */
    public String getSize() {
        return size;
    }

    /**
     * Sets the file size of the transmitted fax.
     *
     * @param size String the file size value
     */
    public void setSize(String size) {
        this.size = size;
    }

    /**
     * Gets the list of files that were submitted as part of this fax transmission.
     *
     * @return String the SubmittedFiles value from the SRFax API
     */
    public String getSubmittedFiles() {
        return submittedFiles;
    }

    /**
     * Sets the list of files that were submitted as part of this fax transmission.
     *
     * @param submittedFiles String the submitted files list
     */
    public void setSubmittedFiles(String submittedFiles) {
        this.submittedFiles = submittedFiles;
    }

    /**
     * Gets the SRFax user identifier who submitted the fax.
     *
     * @return String the User_ID value from the SRFax API
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the SRFax user identifier who submitted the fax.
     *
     * @param userId String the user identifier
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Gets the fax number of the user who submitted the fax.
     *
     * @return String the User_FaxNumber value from the SRFax API
     */
    public String getUserFaxNumber() {
        return userFaxNumber;
    }

    /**
     * Sets the fax number of the user who submitted the fax.
     *
     * @param userFaxNumber String the user's fax number
     */
    public void setUserFaxNumber(String userFaxNumber) {
        this.userFaxNumber = userFaxNumber;
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }
}
