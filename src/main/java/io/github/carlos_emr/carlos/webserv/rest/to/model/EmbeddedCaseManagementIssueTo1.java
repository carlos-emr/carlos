/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

/** Detached wire snapshot retaining established member names without persistence relationships. */
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.NONE)
@jakarta.xml.bind.annotation.XmlAccessorOrder(jakarta.xml.bind.annotation.XmlAccessOrder.ALPHABETICAL)
@jakarta.xml.bind.annotation.XmlType(name="embeddedCaseManagementIssueTo1")
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        setterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public class EmbeddedCaseManagementIssueTo1 {
    @jakarta.xml.bind.annotation.XmlElement(name="id")
    private java.lang.Long id;
    @jakarta.xml.bind.annotation.XmlElement(name="demographic_no")
    private java.lang.Integer demographic_no;
    @jakarta.xml.bind.annotation.XmlElement(name="issue_id")
    private long issue_id;
    @jakarta.xml.bind.annotation.XmlElement(name="acute")
    private boolean acute;
    @jakarta.xml.bind.annotation.XmlElement(name="certain")
    private boolean certain;
    @jakarta.xml.bind.annotation.XmlElement(name="major")
    private boolean major;
    @jakarta.xml.bind.annotation.XmlElement(name="resolved")
    private boolean resolved;
    @jakarta.xml.bind.annotation.XmlElement(name="type")
    private java.lang.String type;
    @jakarta.xml.bind.annotation.XmlElement(name="update_date")
    private java.util.Date update_date;
    @jakarta.xml.bind.annotation.XmlElement(name="notes")
    private java.util.Set<NoteTo1> notes;
    @jakarta.xml.bind.annotation.XmlElement(name="issue")
    private EmbeddedIssueTo1 issue;
    @jakarta.xml.bind.annotation.XmlElement(name="program_id")
    private java.lang.Integer program_id;
    public java.lang.Long getId() { return id; }
    public void setId(java.lang.Long value) { id = value; }
    public java.lang.Integer getDemographic_no() { return demographic_no; }
    public void setDemographic_no(java.lang.Integer value) { demographic_no = value; }
    public long getIssue_id() { return issue_id; }
    public void setIssue_id(long value) { issue_id = value; }
    public boolean isAcute() { return acute; }
    public void setAcute(boolean value) { acute = value; }
    public boolean isCertain() { return certain; }
    public void setCertain(boolean value) { certain = value; }
    public boolean isMajor() { return major; }
    public void setMajor(boolean value) { major = value; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean value) { resolved = value; }
    public java.lang.String getType() { return type; }
    public void setType(java.lang.String value) { type = value; }
    public java.util.Date getUpdate_date() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(update_date); }
    public void setUpdate_date(java.util.Date value) { update_date = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.util.Set<NoteTo1> getNotes() { return notes; }
    public void setNotes(java.util.Set<NoteTo1> value) { notes = value; }
    public EmbeddedIssueTo1 getIssue() { return issue; }
    public void setIssue(EmbeddedIssueTo1 value) { issue = value; }
    public java.lang.Integer getProgram_id() { return program_id; }
    public void setProgram_id(java.lang.Integer value) { program_id = value; }
    @Override public String toString() { return "EmbeddedCaseManagementIssueTo1[protected]"; }
}
