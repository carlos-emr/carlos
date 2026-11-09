/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

/** Detached wire snapshot retaining established member names without persistence relationships. */
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.NONE)
@jakarta.xml.bind.annotation.XmlAccessorOrder(jakarta.xml.bind.annotation.XmlAccessOrder.ALPHABETICAL)
@jakarta.xml.bind.annotation.XmlType(name="embeddedProfessionalSpecialistTo1")
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        setterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public class EmbeddedProfessionalSpecialistTo1 {
    private java.lang.Integer id;
    @jakarta.xml.bind.annotation.XmlElement(name="firstName")
    private java.lang.String firstName;
    @jakarta.xml.bind.annotation.XmlElement(name="lastName")
    private java.lang.String lastName;
    @jakarta.xml.bind.annotation.XmlElement(name="professionalLetters")
    private java.lang.String professionalLetters;
    @jakarta.xml.bind.annotation.XmlElement(name="streetAddress")
    private java.lang.String streetAddress;
    @jakarta.xml.bind.annotation.XmlElement(name="phoneNumber")
    private java.lang.String phoneNumber;
    @jakarta.xml.bind.annotation.XmlElement(name="faxNumber")
    private java.lang.String faxNumber;
    @jakarta.xml.bind.annotation.XmlElement(name="webSite")
    private java.lang.String webSite;
    @jakarta.xml.bind.annotation.XmlElement(name="emailAddress")
    private java.lang.String emailAddress;
    @jakarta.xml.bind.annotation.XmlElement(name="specialtyType")
    private java.lang.String specialtyType;
    @jakarta.xml.bind.annotation.XmlElement(name="annotation")
    private java.lang.String annotation;
    @jakarta.xml.bind.annotation.XmlElement(name="referralNo")
    private java.lang.String referralNo;
    @jakarta.xml.bind.annotation.XmlElement(name="institutionId")
    private java.lang.Integer institutionId;
    @jakarta.xml.bind.annotation.XmlElement(name="departmentId")
    private java.lang.Integer departmentId;
    @jakarta.xml.bind.annotation.XmlElement(name="privatePhoneNumber")
    private java.lang.String privatePhoneNumber;
    @jakarta.xml.bind.annotation.XmlElement(name="cellPhoneNumber")
    private java.lang.String cellPhoneNumber;
    @jakarta.xml.bind.annotation.XmlElement(name="pagerNumber")
    private java.lang.String pagerNumber;
    @jakarta.xml.bind.annotation.XmlElement(name="salutation")
    private java.lang.String salutation;
    @jakarta.xml.bind.annotation.XmlElement(name="hideFromView")
    private boolean hideFromView;
    @jakarta.xml.bind.annotation.XmlElement(name="province")
    private java.lang.String province;
    @jakarta.xml.bind.annotation.XmlElement(name="deleted")
    private boolean deleted;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER)
    @jakarta.xml.bind.annotation.XmlElement(name="lastUpdated")
    private java.util.Date lastUpdated;
    @jakarta.xml.bind.annotation.XmlElement(name="eformId")
    private java.lang.Integer eformId;
    @jakarta.xml.bind.annotation.XmlElement(name="contactRelationship")
    private io.github.carlos_emr.carlos.integration.fhir.resources.constants.ContactRelationship contactRelationship;
    @jakarta.xml.bind.annotation.XmlElement(name="contactType")
    private io.github.carlos_emr.carlos.integration.fhir.resources.constants.ContactType contactType;
    @jakarta.xml.bind.annotation.XmlElement(name="addressArray")
    private java.lang.String[] addressArray;
    @jakarta.xml.bind.annotation.XmlElement(name="address")
    private java.lang.String address;
    @jakarta.xml.bind.annotation.XmlElement(name="city")
    private java.lang.String city;
    @jakarta.xml.bind.annotation.XmlElement(name="postal")
    private java.lang.String postal;
    @jakarta.xml.bind.annotation.XmlElement(name="phone")
    private java.lang.String phone;
    private java.lang.String formattedTitle;
    @jakarta.xml.bind.annotation.XmlElement(name="locationCode")
    private java.lang.String locationCode;
    @jakarta.xml.bind.annotation.XmlElement(name="organizationName")
    private java.lang.String organizationName;
    @jakarta.xml.bind.annotation.XmlElement(name="address2")
    private java.lang.String address2;
    @jakarta.xml.bind.annotation.XmlElement(name="fax")
    private java.lang.String fax;
    @jakarta.xml.bind.annotation.XmlElement(name="providerCpso")
    private java.lang.String providerCpso;
    @jakarta.xml.bind.annotation.XmlElement(name="workPhone")
    private java.lang.String workPhone;
    private java.lang.String formattedName;
    private boolean persistent;
    public java.lang.Integer getId() { return id; }
    public void setId(java.lang.Integer value) { id = value; }
    public java.lang.String getFirstName() { return firstName; }
    public void setFirstName(java.lang.String value) { firstName = value; }
    public java.lang.String getLastName() { return lastName; }
    public void setLastName(java.lang.String value) { lastName = value; }
    public java.lang.String getProfessionalLetters() { return professionalLetters; }
    public void setProfessionalLetters(java.lang.String value) { professionalLetters = value; }
    public java.lang.String getStreetAddress() { return streetAddress; }
    public void setStreetAddress(java.lang.String value) { streetAddress = value; }
    public java.lang.String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(java.lang.String value) { phoneNumber = value; }
    public java.lang.String getFaxNumber() { return faxNumber; }
    public void setFaxNumber(java.lang.String value) { faxNumber = value; }
    public java.lang.String getWebSite() { return webSite; }
    public void setWebSite(java.lang.String value) { webSite = value; }
    public java.lang.String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(java.lang.String value) { emailAddress = value; }
    public java.lang.String getSpecialtyType() { return specialtyType; }
    public void setSpecialtyType(java.lang.String value) { specialtyType = value; }
    public java.lang.String getAnnotation() { return annotation; }
    public void setAnnotation(java.lang.String value) { annotation = value; }
    public java.lang.String getReferralNo() { return referralNo; }
    public void setReferralNo(java.lang.String value) { referralNo = value; }
    public java.lang.Integer getInstitutionId() { return institutionId; }
    public void setInstitutionId(java.lang.Integer value) { institutionId = value; }
    public java.lang.Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(java.lang.Integer value) { departmentId = value; }
    public java.lang.String getPrivatePhoneNumber() { return privatePhoneNumber; }
    public void setPrivatePhoneNumber(java.lang.String value) { privatePhoneNumber = value; }
    public java.lang.String getCellPhoneNumber() { return cellPhoneNumber; }
    public void setCellPhoneNumber(java.lang.String value) { cellPhoneNumber = value; }
    public java.lang.String getPagerNumber() { return pagerNumber; }
    public void setPagerNumber(java.lang.String value) { pagerNumber = value; }
    public java.lang.String getSalutation() { return salutation; }
    public void setSalutation(java.lang.String value) { salutation = value; }
    public boolean isHideFromView() { return hideFromView; }
    public void setHideFromView(boolean value) { hideFromView = value; }
    public java.lang.String getProvince() { return province; }
    public void setProvince(java.lang.String value) { province = value; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean value) { deleted = value; }
    public java.util.Date getLastUpdated() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(lastUpdated); }
    public void setLastUpdated(java.util.Date value) { lastUpdated = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.Integer getEformId() { return eformId; }
    public void setEformId(java.lang.Integer value) { eformId = value; }
    public io.github.carlos_emr.carlos.integration.fhir.resources.constants.ContactRelationship getContactRelationship() { return contactRelationship; }
    public void setContactRelationship(io.github.carlos_emr.carlos.integration.fhir.resources.constants.ContactRelationship value) { contactRelationship = value; }
    public io.github.carlos_emr.carlos.integration.fhir.resources.constants.ContactType getContactType() { return contactType; }
    public void setContactType(io.github.carlos_emr.carlos.integration.fhir.resources.constants.ContactType value) { contactType = value; }
    public java.lang.String[] getAddressArray() { return addressArray; }
    public void setAddressArray(java.lang.String[] value) { addressArray = value; }
    public java.lang.String getAddress() { return address; }
    public void setAddress(java.lang.String value) { address = value; }
    public java.lang.String getCity() { return city; }
    public void setCity(java.lang.String value) { city = value; }
    public java.lang.String getPostal() { return postal; }
    public void setPostal(java.lang.String value) { postal = value; }
    public java.lang.String getPhone() { return phone; }
    public void setPhone(java.lang.String value) { phone = value; }
    public java.lang.String getFormattedTitle() { return formattedTitle; }
    public void setFormattedTitle(java.lang.String value) { formattedTitle = value; }
    public java.lang.String getLocationCode() { return locationCode; }
    public void setLocationCode(java.lang.String value) { locationCode = value; }
    public java.lang.String getOrganizationName() { return organizationName; }
    public void setOrganizationName(java.lang.String value) { organizationName = value; }
    public java.lang.String getAddress2() { return address2; }
    public void setAddress2(java.lang.String value) { address2 = value; }
    public java.lang.String getFax() { return fax; }
    public void setFax(java.lang.String value) { fax = value; }
    public java.lang.String getProviderCpso() { return providerCpso; }
    public void setProviderCpso(java.lang.String value) { providerCpso = value; }
    public java.lang.String getWorkPhone() { return workPhone; }
    public void setWorkPhone(java.lang.String value) { workPhone = value; }
    public java.lang.String getFormattedName() { return formattedName; }
    public void setFormattedName(java.lang.String value) { formattedName = value; }
    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean value) { persistent = value; }
    @Override public String toString() { return "EmbeddedProfessionalSpecialistTo1[protected]"; }
}
