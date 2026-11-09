/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

/** Detached wire snapshot retaining established member names without persistence relationships. */
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.NONE)
@jakarta.xml.bind.annotation.XmlAccessorOrder(jakarta.xml.bind.annotation.XmlAccessOrder.ALPHABETICAL)
@jakarta.xml.bind.annotation.XmlType(name="embeddedProviderTo1")
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        setterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public class EmbeddedProviderTo1 {
    @jakarta.xml.bind.annotation.XmlElement(name="providerNo")
    private java.lang.String providerNo;
    @jakarta.xml.bind.annotation.XmlElement(name="comments")
    private java.lang.String comments;
    @jakarta.xml.bind.annotation.XmlElement(name="phone")
    private java.lang.String phone;
    @jakarta.xml.bind.annotation.XmlElement(name="billingNo")
    private java.lang.String billingNo;
    @jakarta.xml.bind.annotation.XmlElement(name="workPhone")
    private java.lang.String workPhone;
    @jakarta.xml.bind.annotation.XmlElement(name="address")
    private java.lang.String address;
    @jakarta.xml.bind.annotation.XmlElement(name="team")
    private java.lang.String team;
    @jakarta.xml.bind.annotation.XmlElement(name="status")
    private java.lang.String status;
    @jakarta.xml.bind.annotation.XmlElement(name="lastName")
    private java.lang.String lastName;
    @jakarta.xml.bind.annotation.XmlElement(name="providerType")
    private java.lang.String providerType;
    @jakarta.xml.bind.annotation.XmlElement(name="sex")
    private java.lang.String sex;
    @jakarta.xml.bind.annotation.XmlElement(name="ohipNo")
    private java.lang.String ohipNo;
    @jakarta.xml.bind.annotation.XmlElement(name="specialty")
    private java.lang.String specialty;
    @jakarta.xml.bind.annotation.XmlElement(name="dob")
    private java.util.Date dob;
    @jakarta.xml.bind.annotation.XmlElement(name="hsoNo")
    private java.lang.String hsoNo;
    @jakarta.xml.bind.annotation.XmlElement(name="providerActivity")
    private java.lang.String providerActivity;
    @jakarta.xml.bind.annotation.XmlElement(name="firstName")
    private java.lang.String firstName;
    @jakarta.xml.bind.annotation.XmlElement(name="rmaNo")
    private java.lang.String rmaNo;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER)
    @jakarta.xml.bind.annotation.XmlElement(name="signedConfidentiality")
    private java.util.Date signedConfidentiality;
    @jakarta.xml.bind.annotation.XmlElement(name="practitionerNo")
    private java.lang.String practitionerNo;
    @jakarta.xml.bind.annotation.XmlElement(name="practitionerNoType")
    private java.lang.String practitionerNoType;
    @jakarta.xml.bind.annotation.XmlElement(name="email")
    private java.lang.String email;
    @jakarta.xml.bind.annotation.XmlElement(name="title")
    private java.lang.String title;
    @jakarta.xml.bind.annotation.XmlElement(name="lastUpdateUser")
    private java.lang.String lastUpdateUser;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER)
    @jakarta.xml.bind.annotation.XmlElement(name="lastUpdateDate")
    private java.util.Date lastUpdateDate;
    @jakarta.xml.bind.annotation.XmlElement(name="supervisor")
    private java.lang.String supervisor;
    private java.lang.String id;
    private java.lang.String fullName;
    private java.lang.String formattedName;
    private boolean persistent;
    public java.lang.String getProviderNo() { return providerNo; }
    public void setProviderNo(java.lang.String value) { providerNo = value; }
    public java.lang.String getComments() { return comments; }
    public void setComments(java.lang.String value) { comments = value; }
    public java.lang.String getPhone() { return phone; }
    public void setPhone(java.lang.String value) { phone = value; }
    public java.lang.String getBillingNo() { return billingNo; }
    public void setBillingNo(java.lang.String value) { billingNo = value; }
    public java.lang.String getWorkPhone() { return workPhone; }
    public void setWorkPhone(java.lang.String value) { workPhone = value; }
    public java.lang.String getAddress() { return address; }
    public void setAddress(java.lang.String value) { address = value; }
    public java.lang.String getTeam() { return team; }
    public void setTeam(java.lang.String value) { team = value; }
    public java.lang.String getStatus() { return status; }
    public void setStatus(java.lang.String value) { status = value; }
    public java.lang.String getLastName() { return lastName; }
    public void setLastName(java.lang.String value) { lastName = value; }
    public java.lang.String getProviderType() { return providerType; }
    public void setProviderType(java.lang.String value) { providerType = value; }
    public java.lang.String getSex() { return sex; }
    public void setSex(java.lang.String value) { sex = value; }
    public java.lang.String getOhipNo() { return ohipNo; }
    public void setOhipNo(java.lang.String value) { ohipNo = value; }
    public java.lang.String getSpecialty() { return specialty; }
    public void setSpecialty(java.lang.String value) { specialty = value; }
    public java.util.Date getDob() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(dob); }
    public void setDob(java.util.Date value) { dob = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getHsoNo() { return hsoNo; }
    public void setHsoNo(java.lang.String value) { hsoNo = value; }
    public java.lang.String getProviderActivity() { return providerActivity; }
    public void setProviderActivity(java.lang.String value) { providerActivity = value; }
    public java.lang.String getFirstName() { return firstName; }
    public void setFirstName(java.lang.String value) { firstName = value; }
    public java.lang.String getRmaNo() { return rmaNo; }
    public void setRmaNo(java.lang.String value) { rmaNo = value; }
    public java.util.Date getSignedConfidentiality() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(signedConfidentiality); }
    public void setSignedConfidentiality(java.util.Date value) { signedConfidentiality = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getPractitionerNo() { return practitionerNo; }
    public void setPractitionerNo(java.lang.String value) { practitionerNo = value; }
    public java.lang.String getPractitionerNoType() { return practitionerNoType; }
    public void setPractitionerNoType(java.lang.String value) { practitionerNoType = value; }
    public java.lang.String getEmail() { return email; }
    public void setEmail(java.lang.String value) { email = value; }
    public java.lang.String getTitle() { return title; }
    public void setTitle(java.lang.String value) { title = value; }
    public java.lang.String getLastUpdateUser() { return lastUpdateUser; }
    public void setLastUpdateUser(java.lang.String value) { lastUpdateUser = value; }
    public java.util.Date getLastUpdateDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(lastUpdateDate); }
    public void setLastUpdateDate(java.util.Date value) { lastUpdateDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getSupervisor() { return supervisor; }
    public void setSupervisor(java.lang.String value) { supervisor = value; }
    public java.lang.String getId() { return id; }
    public void setId(java.lang.String value) { id = value; }
    public java.lang.String getFullName() { return fullName; }
    public void setFullName(java.lang.String value) { fullName = value; }
    public java.lang.String getFormattedName() { return formattedName; }
    public void setFormattedName(java.lang.String value) { formattedName = value; }
    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean value) { persistent = value; }
    @Override public String toString() { return "EmbeddedProviderTo1[protected]"; }
}
