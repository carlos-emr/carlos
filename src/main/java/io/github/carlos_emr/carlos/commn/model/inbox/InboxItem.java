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
package io.github.carlos_emr.carlos.commn.model.inbox;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import java.util.Date;

@Entity
public class InboxItem {

    @EmbeddedId
    private InboxItemPK id;
    @Column(name = "lab_patient_id")
    private String labPatientId;
    @Column(name = "status")
    private String status;
    @Column(name = "accession_number")
    private String accessionNumber;
    @Column(name = "health_number")
    private String healthNumber;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "sex")
    private String sex;
    @Column(name = "result_status")
    private String resultStatus;
    @Column(name = "final_results_count")
    private String finalResultsCount;
    @Column(name = "last_update_date")
    private String lastUpdateDate;
    @Column(name = "date_time")
    private Date dateTime;
    @Column(name = "date_received")
    private Date dateReceived;
    @Column(name = "priority")
    private String priority;
    @Column(name = "requesting_client")
    private String requestingClient;
    @Column(name = "discipline")
    private String discipline;
    @Column(name = "report_status")
    private String reportStatus;
    @Column(name = "report_description")
    private String reportDescription;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "final_res")
    private String finalRes;
    @Column(name = "is_matched_to_patient")
    private Boolean isMatchedToPatient;
    @Column(name = "label")
    private String label;
    @Column(name = "is_abnormal")
    private Boolean isAbnormal;
    @Column(name = "acknowledge_count")
    private Integer acknowledgeCount;
    @Column(name = "past_acknowledge_count")
    private Integer pastAcknowledgeCount;
    @Column(name = "read")
    private Boolean isRead;

    public InboxItemPK getId() {
        return id;
    }

    public void setId(InboxItemPK id) {
        this.id = id;
    }

    public String getLabPatientId() {
        return labPatientId;
    }

    public void setLabPatientId(String labPatientId) {
        this.labPatientId = labPatientId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getHealthNumber() {
        return healthNumber;
    }

    public void setHealthNumber(String healthNumber) {
        this.healthNumber = healthNumber;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getFinalResultsCount() {
        return finalResultsCount;
    }

    public void setFinalResultsCount(String finalResultsCount) {
        this.finalResultsCount = finalResultsCount;
    }

    public Boolean getMatchedToPatient() {
        return isMatchedToPatient;
    }

    public void setMatchedToPatient(Boolean matchedToPatient) {
        isMatchedToPatient = matchedToPatient;
    }

    public String getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(String lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public Date getDateTime() {
        return dateTime;
    }

    public void setDateTime(Date dateTime) {
        this.dateTime = dateTime;
    }

    public Date getDateReceived() {
        return dateReceived;
    }

    public void setDateReceived(Date dateReceived) {
        this.dateReceived = dateReceived;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getRequestingClient() {
        return requestingClient;
    }

    public void setRequestingClient(String requestingClient) {
        this.requestingClient = requestingClient;
    }

    public String getDiscipline() {
        return discipline;
    }

    public void setDiscipline(String discipline) {
        this.discipline = discipline;
    }

    public String getReportStatus() {
        return reportStatus;
    }

    public void setReportStatus(String reportStatus) {
        this.reportStatus = reportStatus;
    }

    public String getReportDescription() {
        return reportDescription;
    }

    public void setReportDescription(String reportDescription) {
        this.reportDescription = reportDescription;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFinalRes() {
        return finalRes;
    }

    public void setFinalRes(String finalRes) {
        this.finalRes = finalRes;
    }

    public Boolean getAbnormal() {
        return isAbnormal;
    }

    public void setAbnormal(Boolean abnormal) {
        isAbnormal = abnormal;
    }

    public Integer getAcknowledgeCount() {
        return acknowledgeCount;
    }

    public void setAcknowledgeCount(Integer acknowledgeCount) {
        this.acknowledgeCount = acknowledgeCount;
    }

    public Integer getPastAcknowledgeCount() {
        return pastAcknowledgeCount;
    }

    public void setPastAcknowledgeCount(Integer pastAcknowledgeCount) {
        this.pastAcknowledgeCount = pastAcknowledgeCount;
    }

    public Boolean isRead() {
        return isRead;
    }

    public void setRead(Boolean read) {
        isRead = read;
    }
}
