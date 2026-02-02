//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@XmlRootElement
public class DocumentTo1 implements Serializable {
    private Integer id;
    private String type = "";
    private String docClass = "";
    private String subClass = "";
    private String fileName;
    private String description = "";
    private String xml = "";
    private String creator;
    private String responsible = "";
    private String source = "";
    private String sourceFacility = "";
    private Integer programId = -1;
    private Date updateDateTime = new Date();
    private char status = 'A';
    private String contentType;
    private Date contentDateTime = new Date();
    private String reportMedia;
    private Date sentDateTime;
    private int public1 = 0;
    private Date observationDate = new Date();
    private Integer numberOfPages;
    private Integer appointmentNo = -1;
    private Boolean abnormal = false;
    private Boolean restrictToProgram = false;
    private Integer queue;

    private String providerNo;
    private Integer demographicNo;

    private byte[] fileContents;

    private List<DocumentReviewTo1> reviews = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDocClass() {
        return docClass;
    }

    public void setDocClass(String docClass) {
        this.docClass = docClass;
    }

    public String getSubClass() {
        return subClass;
    }

    public void setSubClass(String subClass) {
        this.subClass = subClass;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getResponsible() {
        return responsible;
    }

    public void setResponsible(String responsible) {
        this.responsible = responsible;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceFacility() {
        return sourceFacility;
    }

    public void setSourceFacility(String sourceFacility) {
        this.sourceFacility = sourceFacility;
    }

    public Integer getProgramId() {
        return programId;
    }

    public void setProgramId(Integer programId) {
        this.programId = programId;
    }

    public Date getUpdateDateTime() {
        return updateDateTime;
    }

    public void setUpdateDateTime(Date updateDateTime) {
        this.updateDateTime = updateDateTime;
    }

    public char getStatus() {
        return status;
    }

    public void setStatus(char status) {
        this.status = status;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Date getContentDateTime() {
        return contentDateTime;
    }

    public void setContentDateTime(Date contentDateTime) {
        this.contentDateTime = contentDateTime;
    }

    public String getReportMedia() {
        return reportMedia;
    }

    public void setReportMedia(String reportMedia) {
        this.reportMedia = reportMedia;
    }

    public Date getSentDateTime() {
        return sentDateTime;
    }

    public void setSentDateTime(Date sentDateTime) {
        this.sentDateTime = sentDateTime;
    }

    public int getPublic1() {
        return public1;
    }

    public void setPublic1(int public1) {
        this.public1 = public1;
    }

    public Date getObservationDate() {
        return observationDate;
    }

    public void setObservationDate(Date observationDate) {
        this.observationDate = observationDate;
    }

    public Integer getNumberOfPages() {
        return numberOfPages;
    }

    public void setNumberOfPages(Integer numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    public Integer getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(Integer appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    public Boolean getAbnormal() {
        return abnormal;
    }

    public void setAbnormal(Boolean abnormal) {
        this.abnormal = abnormal;
    }

    public Boolean getRestrictToProgram() {
        return restrictToProgram;
    }

    public void setRestrictToProgram(Boolean restrictToProgram) {
        this.restrictToProgram = restrictToProgram;
    }

    public List<DocumentReviewTo1> getReviews() {
        return reviews;
    }

    public void setReviews(List<DocumentReviewTo1> reviews) {
        this.reviews = reviews;
    }

    public byte[] getFileContents() {
        return fileContents;
    }

    public void setFileContents(byte[] fileContents) {
        this.fileContents = fileContents;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public Integer getQueue() {
        return queue;
    }
    public void setQueue(Integer queue) {
        this.queue = queue;
    }
}
