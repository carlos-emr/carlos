/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

/** Detached wire snapshot retaining established member names without persistence relationships. */
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.NONE)
@jakarta.xml.bind.annotation.XmlAccessorOrder(jakarta.xml.bind.annotation.XmlAccessOrder.ALPHABETICAL)
@jakarta.xml.bind.annotation.XmlType(name="embeddedDemographicExtTo1")
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        setterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public class EmbeddedDemographicExtTo1 {
    @jakarta.xml.bind.annotation.XmlElement(name="id")
    private java.lang.Integer id;
    @jakarta.xml.bind.annotation.XmlElement(name="demographicNo")
    private java.lang.Integer demographicNo;
    @jakarta.xml.bind.annotation.XmlElement(name="providerNo")
    private java.lang.String providerNo;
    @jakarta.xml.bind.annotation.XmlElement(name="key")
    private java.lang.String key;
    @jakarta.xml.bind.annotation.XmlElement(name="value")
    private java.lang.String value;
    @jakarta.xml.bind.annotation.XmlElement(name="dateCreated")
    private java.util.Date dateCreated;
    @jakarta.xml.bind.annotation.XmlElement(name="hidden")
    private boolean hidden;
    @jakarta.xml.bind.annotation.XmlElement(name="demographic")
    private EmbeddedDemographicTo1 demographic;
    private boolean persistent;
    public java.lang.Integer getId() { return id; }
    public void setId(java.lang.Integer value) { id = value; }
    public java.lang.Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(java.lang.Integer value) { demographicNo = value; }
    public java.lang.String getProviderNo() { return providerNo; }
    public void setProviderNo(java.lang.String value) { providerNo = value; }
    public java.lang.String getKey() { return key; }
    public void setKey(java.lang.String value) { key = value; }
    public java.lang.String getValue() { return value; }
    public void setValue(java.lang.String value) { value = value; }
    public java.util.Date getDateCreated() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(dateCreated); }
    public void setDateCreated(java.util.Date value) { dateCreated = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean value) { hidden = value; }
    public EmbeddedDemographicTo1 getDemographic() { return demographic; }
    public void setDemographic(EmbeddedDemographicTo1 value) { demographic = value; }
    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean value) { persistent = value; }
    @Override public String toString() { return "EmbeddedDemographicExtTo1[protected]"; }
}
