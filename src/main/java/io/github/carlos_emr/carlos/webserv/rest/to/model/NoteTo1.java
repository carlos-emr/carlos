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
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.xml.bind.annotation.XmlRootElement;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;

@XmlRootElement(name = "encounterNote")
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.NONE)
public class NoteTo1 implements Serializable {

    private Integer noteId;
    private Boolean isSigned;
    private Boolean isVerified;
    private Boolean isEditable;
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date observationDate;
    private String revision;
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date updateDate;
    private String providerName;
    private String providerNo;
    private String status;
    private String programName;
    private String location;
    private String roleName;
    private String uuid;
    private Boolean hasHistory;
    private Boolean locked;
    private boolean archived;
    private String note;
    private boolean isDocument;
    private boolean isRxAnnotation;
    private boolean isEformData;
    private boolean isEncounterForm;
    private boolean isInvoice;
    private boolean isTicklerNote;
    private String encounterType = "";
    private Integer appointmentNo;
    private String summaryCode;

    private ArrayList<String> editorNames;
    private ArrayList<String> issueDescriptions;

    //not controlled by note attributes / business logic like "editable".
    //use this for a category of notes - like group notes
    private boolean isReadOnly;
    private boolean isGroupNote;
    private boolean isCpp;
    private String encounterTime;
    private String encounterTransportationTime;

    private Integer position;

    //???
    private Set<EmbeddedCaseManagementIssueTo1> issues = new java.util.LinkedHashSet<>();

    private List<CaseManagementIssueTo1> assignedIssues = new ArrayList<CaseManagementIssueTo1>();

    private NoteExtTo1 noteExt;

    @jakarta.xml.bind.annotation.XmlElement(name="noteId")
    public Integer getNoteId() {
        return noteId;
    }

    public void setNoteId(Integer noteId) {
        this.noteId = noteId;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="isSigned")
    public Boolean getIsSigned() {
        return isSigned;
    }

    public void setIsSigned(Boolean isSigned) {
        this.isSigned = isSigned;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="observationDate")
    public Date getObservationDate() {
        return observationDate;
    }

    public void setObservationDate(Date observationDate) {
        this.observationDate = observationDate;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="revision")
    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="updateDate")
    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="providerName")
    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="providerNo")
    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * deprecated too inefficient and too many dependencies use CaseManagementIssueNotesDao
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.xml.bind.annotation.XmlTransient
    public Set<CaseManagementIssue> getIssues() {
        if (issues == null) return null;
        return issues.stream().map(io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots::caseManagementIssueDomain)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @com.fasterxml.jackson.annotation.JsonProperty("issues")
    public Set<EmbeddedCaseManagementIssueTo1> getIssueSnapshots() { return issues; }
    public void setIssueSnapshots(Set<EmbeddedCaseManagementIssueTo1> value) {
        issues = value == null ? null : new java.util.LinkedHashSet<>(value);
    }

    /**
     * deprecated too inefficient and too many dependencies use CaseManagementIssueNotesDao
     */
    public void setIssues(Set issues) {
        if (issues == null) { this.issues = null; return; }
        if (!org.hibernate.Hibernate.isInitialized(issues)) {
            throw new io.github.carlos_emr.carlos.webserv.rest.conversion.ConversionException("Note issues are not initialized");
        }
        Set<EmbeddedCaseManagementIssueTo1> detached = new java.util.LinkedHashSet<>();
        for (Object value : issues) {
            if (value instanceof CaseManagementIssue issue) detached.add(io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.caseManagementIssue(null, issue));
            else if (value instanceof EmbeddedCaseManagementIssueTo1 issue) detached.add(issue);
            else throw new IllegalArgumentException("Unsupported note issue type");
        }
        this.issues = detached;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="programName")
    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="location")
    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="roleName")
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="uuid")
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="hasHistory")
    public Boolean getHasHistory() {
        return hasHistory;
    }

    public void setHasHistory(Boolean hasHistory) {
        this.hasHistory = hasHistory;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="locked")
    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="archived")
    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="note")
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="document")
    public boolean isDocument() {
        return isDocument;
    }

    public void setDocument(boolean isDocument) {
        this.isDocument = isDocument;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="rxAnnotation")
    public boolean isRxAnnotation() {
        return isRxAnnotation;
    }

    public void setRxAnnotation(boolean isRxAnnotation) {
        this.isRxAnnotation = isRxAnnotation;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="eformData")
    public boolean isEformData() {
        return isEformData;
    }

    public void setEformData(boolean isEformData) {
        this.isEformData = isEformData;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="encounterForm")
    public boolean isEncounterForm() {
        return isEncounterForm;
    }

    public void setEncounterForm(boolean isEncounterForm) {
        this.isEncounterForm = isEncounterForm;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="invoice")
    public boolean isInvoice() {
        return isInvoice;
    }

    public void setInvoice(boolean isInvoice) {
        this.isInvoice = isInvoice;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="ticklerNote")
    public boolean isTicklerNote() {
        return isTicklerNote;
    }

    public void setTicklerNote(boolean isTicklerNote) {
        this.isTicklerNote = isTicklerNote;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="encounterType")
    public String getEncounterType() {
        return encounterType;
    }

    public void setEncounterType(String encounterType) {
        this.encounterType = encounterType;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="editorNames")
    public ArrayList<String> getEditorNames() {
        return editorNames;
    }

    public void setEditorNames(ArrayList<String> editorNames) {
        this.editorNames = editorNames;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="issueDescriptions")
    public ArrayList<String> getIssueDescriptions() {
        return issueDescriptions;
    }

    public void setIssueDescriptions(ArrayList<String> issueDescriptions) {
        this.issueDescriptions = issueDescriptions;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="readOnly")
    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="groupNote")
    public boolean isGroupNote() {
        return isGroupNote;
    }

    public void setGroupNote(boolean isGroupNote) {
        this.isGroupNote = isGroupNote;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="cpp")
    public boolean isCpp() {
        return isCpp;
    }

    public void setCpp(boolean isCpp) {
        this.isCpp = isCpp;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="summaryCode")
    public String getSummaryCode() {
        return summaryCode;
    }

    public void setSummaryCode(String code) {
        this.summaryCode = code;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="encounterTime")
    public String getEncounterTime() {
        return encounterTime;
    }


    public void setEncounterTime(String encounterTime) {
        this.encounterTime = encounterTime;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="encounterTransportationTime")
    public String getEncounterTransportationTime() {
        return encounterTransportationTime;
    }

    public void setEncounterTransportationTime(String encounterTransportationTime) {
        this.encounterTransportationTime = encounterTransportationTime;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="editable")
    public Boolean isEditable() {
        return isEditable;
    }

    public void setIsEditable(Boolean isEditable) {
        this.isEditable = isEditable;
    }

    public void setEditable(Boolean isEditable) {
        this.isEditable = isEditable;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="appointmentNo")
    public Integer getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(Integer appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    /** Returns only the type marker, without clinical text or nested records. */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[protected]";
    }

    @jakarta.xml.bind.annotation.XmlElement(name="isVerified")
    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="assignedIssues")
    public List<CaseManagementIssueTo1> getAssignedIssues() {
        return assignedIssues;
    }

    public void setAssignedIssues(List<CaseManagementIssueTo1> assignedIssues) {
        this.assignedIssues = assignedIssues;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="position")
    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    @jakarta.xml.bind.annotation.XmlElement(name="noteExt")
    public NoteExtTo1 getNoteExt() {
        return noteExt;
    }

    public void setNoteExt(NoteExtTo1 noteExt) {
        this.noteExt = noteExt;
    }
}
