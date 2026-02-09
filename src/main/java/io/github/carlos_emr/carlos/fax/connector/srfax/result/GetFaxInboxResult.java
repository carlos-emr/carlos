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
 * JSON model for SRFax Get_Fax_Inbox API responses.
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

    public String getRawFileName() {
        return rawFileName;
    }

    public void setRawFileName(String rawFileName) {
        this.rawFileName = rawFileName;
        this.fileName = rawFileName.split("\\|")[0];
        this.detailsId = rawFileName.split("\\|")[1];
    }

    public String getFileName() {
        return fileName;
    }

    public String getDetailsId() {
        return detailsId;
    }

    public String getReceiveStatus() {
        return receiveStatus;
    }

    public void setReceiveStatus(String receiveStatus) {
        this.receiveStatus = receiveStatus;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getEpochTime() {
        return epochTime;
    }

    public void setEpochTime(String epochTime) {
        this.epochTime = epochTime;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public String getPages() {
        return pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getViewedStatus() {
        return viewedStatus;
    }

    public void setViewedStatus(String viewedStatus) {
        this.viewedStatus = viewedStatus;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserFaxNumber() {
        return userFaxNumber;
    }

    public void setUserFaxNumber(String userFaxNumber) {
        this.userFaxNumber = userFaxNumber;
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }
}
