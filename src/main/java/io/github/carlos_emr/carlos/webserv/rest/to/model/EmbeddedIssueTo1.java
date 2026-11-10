/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

/** Detached wire snapshot retaining established member names without persistence relationships. */
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.PUBLIC_MEMBER)
@jakarta.xml.bind.annotation.XmlAccessorOrder(jakarta.xml.bind.annotation.XmlAccessOrder.ALPHABETICAL)
@jakarta.xml.bind.annotation.XmlType(name="embeddedIssueTo1")
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        setterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public class EmbeddedIssueTo1 implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private java.lang.Long id;
    private java.lang.String code;
    private java.lang.String description;
    private java.lang.String role;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER)
    private java.util.Date update_date;
    private java.lang.String priority;
    private java.lang.String type;
    private java.lang.Integer sortOrderId;
    private java.lang.String update_date_web;
    public java.lang.Long getId() { return id; }
    public void setId(java.lang.Long value) { id = value; }
    public java.lang.String getCode() { return code; }
    public void setCode(java.lang.String value) { code = value; }
    public java.lang.String getDescription() { return description; }
    public void setDescription(java.lang.String value) { description = value; }
    public java.lang.String getRole() { return role; }
    public void setRole(java.lang.String value) { role = value; }
    public java.util.Date getUpdate_date() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(update_date); }
    public void setUpdate_date(java.util.Date value) { update_date = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getPriority() { return priority; }
    public void setPriority(java.lang.String value) { priority = value; }
    public java.lang.String getType() { return type; }
    public void setType(java.lang.String value) { type = value; }
    public java.lang.Integer getSortOrderId() { return sortOrderId; }
    public void setSortOrderId(java.lang.Integer value) { sortOrderId = value; }
    public java.lang.String getUpdate_date_web() { return update_date_web; }
    public void setUpdate_date_web(java.lang.String value) { update_date_web = value; }
    @Override public String toString() { return "EmbeddedIssueTo1[protected]"; }
}
