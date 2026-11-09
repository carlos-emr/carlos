/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

/** Detached wire snapshot retaining established member names without persistence relationships. */
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.NONE)
@jakarta.xml.bind.annotation.XmlAccessorOrder(jakarta.xml.bind.annotation.XmlAccessOrder.ALPHABETICAL)
@jakarta.xml.bind.annotation.XmlType(name="embeddedMeasurementTo1")
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        setterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public class EmbeddedMeasurementTo1 {
    private java.lang.Integer id;
    @jakarta.xml.bind.annotation.XmlElement(name="type")
    private java.lang.String type;
    @jakarta.xml.bind.annotation.XmlElement(name="demographicId")
    private java.lang.Integer demographicId;
    @jakarta.xml.bind.annotation.XmlElement(name="providerNo")
    private java.lang.String providerNo;
    @jakarta.xml.bind.annotation.XmlElement(name="dataField")
    private java.lang.String dataField;
    @jakarta.xml.bind.annotation.XmlElement(name="measuringInstruction")
    private java.lang.String measuringInstruction;
    @jakarta.xml.bind.annotation.XmlElement(name="comments")
    private java.lang.String comments;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER)
    @jakarta.xml.bind.annotation.XmlElement(name="dateObserved")
    private java.util.Date dateObserved;
    @jakarta.xml.bind.annotation.XmlElement(name="appointmentNo")
    private java.lang.Integer appointmentNo;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER)
    @jakarta.xml.bind.annotation.XmlElement(name="createDate")
    private java.util.Date createDate;
    private boolean persistent;
    public java.lang.Integer getId() { return id; }
    public void setId(java.lang.Integer value) { id = value; }
    public java.lang.String getType() { return type; }
    public void setType(java.lang.String value) { type = value; }
    public java.lang.Integer getDemographicId() { return demographicId; }
    public void setDemographicId(java.lang.Integer value) { demographicId = value; }
    public java.lang.String getProviderNo() { return providerNo; }
    public void setProviderNo(java.lang.String value) { providerNo = value; }
    public java.lang.String getDataField() { return dataField; }
    public void setDataField(java.lang.String value) { dataField = value; }
    public java.lang.String getMeasuringInstruction() { return measuringInstruction; }
    public void setMeasuringInstruction(java.lang.String value) { measuringInstruction = value; }
    public java.lang.String getComments() { return comments; }
    public void setComments(java.lang.String value) { comments = value; }
    public java.util.Date getDateObserved() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(dateObserved); }
    public void setDateObserved(java.util.Date value) { dateObserved = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.Integer getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(java.lang.Integer value) { appointmentNo = value; }
    public java.util.Date getCreateDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(createDate); }
    public void setCreateDate(java.util.Date value) { createDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean value) { persistent = value; }
    @Override public String toString() { return "EmbeddedMeasurementTo1[protected]"; }
}
