/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

/** Detached invoice line. The Java-only Optional amount becomes a nullable numeric wire value. */
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.PUBLIC_MEMBER)
@jakarta.xml.bind.annotation.XmlAccessorOrder(jakarta.xml.bind.annotation.XmlAccessOrder.ALPHABETICAL)
@jakarta.xml.bind.annotation.XmlType(name="embeddedBillingONItemTo1")
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        setterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public class EmbeddedBillingONItemTo1 implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Integer ch1Id;
    private String transcId;
    private String recId;
    private String serviceCode;
    private String fee;
    private String serviceCount;
    private java.util.Date serviceDate;
    private String dx;
    private String dx1;
    private String dx2;
    private String status;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER)
    private java.util.Date lastEditDT;
    private java.math.BigDecimal feeAsBigDecimal;
    private boolean defunct;
    private boolean active;
    private boolean deleted;
    private boolean persistent;
    public Integer getId() { return id; }
    public void assignId(Integer value) { id = value; }
    public Integer getCh1Id() { return ch1Id; }
    public void setCh1Id(Integer value) { ch1Id = value; }
    public String getTranscId() { return transcId; }
    public void setTranscId(String value) { transcId = value; }
    public String getRecId() { return recId; }
    public void setRecId(String value) { recId = value; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String value) { serviceCode = value; }
    public String getFee() { return fee; }
    public void setFee(String value) { fee = value; }
    public String getServiceCount() { return serviceCount; }
    public void setServiceCount(String value) { serviceCount = value; }
    public java.util.Date getServiceDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(serviceDate); }
    public void setServiceDate(java.util.Date value) { serviceDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public String getDx() { return dx; }
    public void setDx(String value) { dx = value; }
    public String getDx1() { return dx1; }
    public void setDx1(String value) { dx1 = value; }
    public String getDx2() { return dx2; }
    public void setDx2(String value) { dx2 = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public java.util.Date getLastEditDT() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(lastEditDT); }
    public void setLastEditDT(java.util.Date value) { lastEditDT = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.math.BigDecimal feeAsBigDecimal() { return feeAsBigDecimal; }
    public void assignFeeAsBigDecimal(java.math.BigDecimal value) { feeAsBigDecimal = value; }
    public boolean isDefunct() { return defunct; }
    public void assignDefunct(boolean value) { defunct = value; }
    public boolean isActive() { return active; }
    public void assignActive(boolean value) { active = value; }
    public boolean isDeleted() { return deleted; }
    public void assignDeleted(boolean value) { deleted = value; }
    public boolean isPersistent() { return persistent; }
    public void assignPersistent(boolean value) { persistent = value; }
    @Override public String toString() { return "EmbeddedBillingONItemTo1[protected]"; }
}
