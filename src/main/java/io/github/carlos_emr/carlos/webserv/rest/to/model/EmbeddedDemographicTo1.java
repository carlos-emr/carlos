/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

/** Detached wire snapshot retaining established member names without persistence relationships. */
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.NONE)
@jakarta.xml.bind.annotation.XmlAccessorOrder(jakarta.xml.bind.annotation.XmlAccessOrder.ALPHABETICAL)
@jakarta.xml.bind.annotation.XmlType(name="embeddedDemographicTo1")
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        setterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public class EmbeddedDemographicTo1 {
    @jakarta.xml.bind.annotation.XmlElement(name="demographicNo")
    private java.lang.Integer demographicNo;
    @jakarta.xml.bind.annotation.XmlElement(name="phone")
    private java.lang.String phone;
    @jakarta.xml.bind.annotation.XmlElement(name="patientStatus")
    private java.lang.String patientStatus;
    @jakarta.xml.bind.annotation.XmlElement(name="patientStatusDate")
    private java.util.Date patientStatusDate;
    @jakarta.xml.bind.annotation.XmlElement(name="rosterStatus")
    private java.lang.String rosterStatus;
    @jakarta.xml.bind.annotation.XmlElement(name="providerNo")
    private java.lang.String providerNo;
    @jakarta.xml.bind.annotation.XmlElement(name="hin")
    private java.lang.String hin;
    @jakarta.xml.bind.annotation.XmlElement(name="address")
    private java.lang.String address;
    @jakarta.xml.bind.annotation.XmlElement(name="province")
    private java.lang.String province;
    @jakarta.xml.bind.annotation.XmlElement(name="monthOfBirth")
    private java.lang.String monthOfBirth;
    @jakarta.xml.bind.annotation.XmlElement(name="ver")
    private java.lang.String ver;
    @jakarta.xml.bind.annotation.XmlElement(name="dateOfBirth")
    private java.lang.String dateOfBirth;
    @jakarta.xml.bind.annotation.XmlElement(name="sex")
    private java.lang.String sex;
    @jakarta.xml.bind.annotation.XmlElement(name="sexDesc")
    private java.lang.String sexDesc;
    @jakarta.xml.bind.annotation.XmlElement(name="dateJoined")
    private java.util.Date dateJoined;
    @jakarta.xml.bind.annotation.XmlElement(name="familyDoctor")
    private java.lang.String familyDoctor;
    @jakarta.xml.bind.annotation.XmlElement(name="familyPhysician")
    private java.lang.String familyPhysician;
    @jakarta.xml.bind.annotation.XmlElement(name="city")
    private java.lang.String city;
    @jakarta.xml.bind.annotation.XmlElement(name="firstName")
    private java.lang.String firstName;
    @jakarta.xml.bind.annotation.XmlElement(name="postal")
    private java.lang.String postal;
    @jakarta.xml.bind.annotation.XmlElement(name="hcRenewDate")
    private java.util.Date hcRenewDate;
    @jakarta.xml.bind.annotation.XmlElement(name="phone2")
    private java.lang.String phone2;
    @jakarta.xml.bind.annotation.XmlElement(name="pcnIndicator")
    private java.lang.String pcnIndicator;
    @jakarta.xml.bind.annotation.XmlElement(name="endDate")
    private java.util.Date endDate;
    @jakarta.xml.bind.annotation.XmlElement(name="lastName")
    private java.lang.String lastName;
    @jakarta.xml.bind.annotation.XmlElement(name="hcType")
    private java.lang.String hcType;
    @jakarta.xml.bind.annotation.XmlElement(name="chartNo")
    private java.lang.String chartNo;
    @jakarta.xml.bind.annotation.XmlElement(name="email")
    private java.lang.String email;
    @jakarta.xml.bind.annotation.XmlElement(name="consentToUseEmailForCare")
    private java.lang.Boolean consentToUseEmailForCare;
    @jakarta.xml.bind.annotation.XmlElement(name="yearOfBirth")
    private java.lang.String yearOfBirth;
    @jakarta.xml.bind.annotation.XmlElement(name="effDate")
    private java.util.Date effDate;
    @jakarta.xml.bind.annotation.XmlElement(name="rosterDate")
    private java.util.Date rosterDate;
    @jakarta.xml.bind.annotation.XmlElement(name="rosterTerminationDate")
    private java.util.Date rosterTerminationDate;
    @jakarta.xml.bind.annotation.XmlElement(name="rosterTerminationReason")
    private java.lang.String rosterTerminationReason;
    @jakarta.xml.bind.annotation.XmlElement(name="links")
    private java.lang.String links;
    @jakarta.xml.bind.annotation.XmlElement(name="extras")
    private EmbeddedDemographicExtTo1[] extras;
    @jakarta.xml.bind.annotation.XmlElement(name="alias")
    private java.lang.String alias;
    @jakarta.xml.bind.annotation.XmlElement(name="previousAddress")
    private java.lang.String previousAddress;
    @jakarta.xml.bind.annotation.XmlElement(name="children")
    private java.lang.String children;
    @jakarta.xml.bind.annotation.XmlElement(name="sourceOfIncome")
    private java.lang.String sourceOfIncome;
    @jakarta.xml.bind.annotation.XmlElement(name="citizenship")
    private java.lang.String citizenship;
    @jakarta.xml.bind.annotation.XmlElement(name="sin")
    private java.lang.String sin;
    @jakarta.xml.bind.annotation.XmlElement(name="headRecord")
    private java.lang.Integer headRecord;
    @jakarta.xml.bind.annotation.XmlElement(name="subRecord")
    private java.util.Set<Integer> subRecord;
    @jakarta.xml.bind.annotation.XmlElement(name="anonymous")
    private java.lang.String anonymous;
    @jakarta.xml.bind.annotation.XmlElement(name="spokenLanguage")
    private java.lang.String spokenLanguage;
    @jakarta.xml.bind.annotation.XmlElement(name="activeCount")
    private int activeCount;
    @jakarta.xml.bind.annotation.XmlElement(name="hsAlertCount")
    private int hsAlertCount;
    @jakarta.xml.bind.annotation.XmlElement(name="displayName")
    private java.lang.String displayName;
    @jakarta.xml.bind.annotation.XmlElement(name="provider")
    private EmbeddedProviderTo1 provider;
    @jakarta.xml.bind.annotation.XmlElement(name="lastUpdateUser")
    private java.lang.String lastUpdateUser;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER)
    @jakarta.xml.bind.annotation.XmlElement(name="lastUpdateDate")
    private java.util.Date lastUpdateDate;
    @jakarta.xml.bind.annotation.XmlElement(name="title")
    private java.lang.String title;
    @jakarta.xml.bind.annotation.XmlElement(name="officialLanguage")
    private java.lang.String officialLanguage;
    @jakarta.xml.bind.annotation.XmlElement(name="countryOfOrigin")
    private java.lang.String countryOfOrigin;
    @jakarta.xml.bind.annotation.XmlElement(name="newsletter")
    private java.lang.String newsletter;
    @jakarta.xml.bind.annotation.XmlElement(name="cellPhone")
    private java.lang.String cellPhone;
    @jakarta.xml.bind.annotation.XmlElement(name="phoneComment")
    private java.lang.String phoneComment;
    @jakarta.xml.bind.annotation.XmlElement(name="middleNames")
    private java.lang.String middleNames;
    @jakarta.xml.bind.annotation.XmlElement(name="rosterEnrolledTo")
    private java.lang.String rosterEnrolledTo;
    @jakarta.xml.bind.annotation.XmlElement(name="residentialAddress")
    private java.lang.String residentialAddress;
    @jakarta.xml.bind.annotation.XmlElement(name="residentialCity")
    private java.lang.String residentialCity;
    @jakarta.xml.bind.annotation.XmlElement(name="residentialProvince")
    private java.lang.String residentialProvince;
    @jakarta.xml.bind.annotation.XmlElement(name="residentialPostal")
    private java.lang.String residentialPostal;
    @jakarta.xml.bind.annotation.XmlElement(name="gender")
    private java.lang.String gender;
    @jakarta.xml.bind.annotation.XmlElement(name="pronoun")
    private java.lang.String pronoun;
    @jakarta.xml.bind.annotation.XmlElement(name="genderId")
    private java.lang.Integer genderId;
    @jakarta.xml.bind.annotation.XmlElement(name="pronounId")
    private java.lang.Integer pronounId;
    @jakarta.xml.bind.annotation.XmlElement(name="patientType")
    private java.lang.String patientType;
    @jakarta.xml.bind.annotation.XmlElement(name="prefName")
    private java.lang.String prefName;
    @jakarta.xml.bind.annotation.XmlElement(name="mrp")
    private EmbeddedProviderTo1 mrp;
    @jakarta.xml.bind.annotation.XmlElement(name="nextAppointment")
    private java.lang.String nextAppointment;
    private java.lang.Integer id;
    private boolean active;
    private java.lang.String label;
    private java.lang.String fullName;
    private java.lang.String age;
    private java.lang.String familyDoctorLastName;
    private java.lang.String familyDoctorFirstName;
    @jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(io.github.carlos_emr.carlos.utility.xml.BirthdayXmlAdapter.class)
    @jakarta.xml.bind.annotation.XmlSchemaType(name="dateTime")
    @jakarta.xml.bind.annotation.XmlElement(name="birthDay")
    private java.util.Calendar birthDay;
    private int numLinks;
    private java.lang.String standardIdentificationHtml;
    private java.lang.String formattedName;
    private java.lang.String birthDayAsString;
    private java.lang.String rosterStatusDisplay;
    @jakarta.xml.bind.annotation.XmlElement(name="formattedDob")
    private java.lang.String formattedDob;
    private java.lang.String formattedDateJoined;
    private java.lang.String familyDoctorName;
    private java.lang.String familyDoctorNumber;
    private java.lang.String familyPhysicianLastName;
    private java.lang.String familyPhysicianFirstName;
    private java.lang.String familyPhysicianFullName;
    private java.lang.String familyPhysicianNumber;
    private java.lang.String formattedEndDate;
    @jakarta.xml.bind.annotation.XmlElement(name="formattedEffDate")
    private java.lang.String formattedEffDate;
    @jakarta.xml.bind.annotation.XmlElement(name="formattedRenewDate")
    private java.lang.String formattedRenewDate;
    private java.lang.String subjectPronoun;
    private java.lang.String possessivePronoun;
    private int ageInYears;
    private java.lang.String formattedLinks;
    private java.lang.Integer currentRecord;
    private boolean persistent;
    public java.lang.Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(java.lang.Integer value) { demographicNo = value; }
    public java.lang.String getPhone() { return phone; }
    public void setPhone(java.lang.String value) { phone = value; }
    public java.lang.String getPatientStatus() { return patientStatus; }
    public void setPatientStatus(java.lang.String value) { patientStatus = value; }
    public java.util.Date getPatientStatusDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(patientStatusDate); }
    public void setPatientStatusDate(java.util.Date value) { patientStatusDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getRosterStatus() { return rosterStatus; }
    public void setRosterStatus(java.lang.String value) { rosterStatus = value; }
    public java.lang.String getProviderNo() { return providerNo; }
    public void setProviderNo(java.lang.String value) { providerNo = value; }
    public java.lang.String getHin() { return hin; }
    public void setHin(java.lang.String value) { hin = value; }
    public java.lang.String getAddress() { return address; }
    public void setAddress(java.lang.String value) { address = value; }
    public java.lang.String getProvince() { return province; }
    public void setProvince(java.lang.String value) { province = value; }
    public java.lang.String getMonthOfBirth() { return monthOfBirth; }
    public void setMonthOfBirth(java.lang.String value) { monthOfBirth = value; }
    public java.lang.String getVer() { return ver; }
    public void setVer(java.lang.String value) { ver = value; }
    public java.lang.String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(java.lang.String value) { dateOfBirth = value; }
    public java.lang.String getSex() { return sex; }
    public void setSex(java.lang.String value) { sex = value; }
    public java.lang.String getSexDesc() { return sexDesc; }
    public void setSexDesc(java.lang.String value) { sexDesc = value; }
    public java.util.Date getDateJoined() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(dateJoined); }
    public void setDateJoined(java.util.Date value) { dateJoined = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getFamilyDoctor() { return familyDoctor; }
    public void setFamilyDoctor(java.lang.String value) { familyDoctor = value; }
    public java.lang.String getFamilyPhysician() { return familyPhysician; }
    public void setFamilyPhysician(java.lang.String value) { familyPhysician = value; }
    public java.lang.String getCity() { return city; }
    public void setCity(java.lang.String value) { city = value; }
    public java.lang.String getFirstName() { return firstName; }
    public void setFirstName(java.lang.String value) { firstName = value; }
    public java.lang.String getPostal() { return postal; }
    public void setPostal(java.lang.String value) { postal = value; }
    public java.util.Date getHcRenewDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(hcRenewDate); }
    public void setHcRenewDate(java.util.Date value) { hcRenewDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getPhone2() { return phone2; }
    public void setPhone2(java.lang.String value) { phone2 = value; }
    public java.lang.String getPcnIndicator() { return pcnIndicator; }
    public void setPcnIndicator(java.lang.String value) { pcnIndicator = value; }
    public java.util.Date getEndDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(endDate); }
    public void setEndDate(java.util.Date value) { endDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getLastName() { return lastName; }
    public void setLastName(java.lang.String value) { lastName = value; }
    public java.lang.String getHcType() { return hcType; }
    public void setHcType(java.lang.String value) { hcType = value; }
    public java.lang.String getChartNo() { return chartNo; }
    public void setChartNo(java.lang.String value) { chartNo = value; }
    public java.lang.String getEmail() { return email; }
    public void setEmail(java.lang.String value) { email = value; }
    public java.lang.Boolean getConsentToUseEmailForCare() { return consentToUseEmailForCare; }
    public void setConsentToUseEmailForCare(java.lang.Boolean value) { consentToUseEmailForCare = value; }
    public java.lang.String getYearOfBirth() { return yearOfBirth; }
    public void setYearOfBirth(java.lang.String value) { yearOfBirth = value; }
    public java.util.Date getEffDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(effDate); }
    public void setEffDate(java.util.Date value) { effDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.util.Date getRosterDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(rosterDate); }
    public void setRosterDate(java.util.Date value) { rosterDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.util.Date getRosterTerminationDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(rosterTerminationDate); }
    public void setRosterTerminationDate(java.util.Date value) { rosterTerminationDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getRosterTerminationReason() { return rosterTerminationReason; }
    public void setRosterTerminationReason(java.lang.String value) { rosterTerminationReason = value; }
    public java.lang.String getLinks() { return links; }
    public void setLinks(java.lang.String value) { links = value; }
    public EmbeddedDemographicExtTo1[] getExtras() { return extras; }
    public void setExtras(EmbeddedDemographicExtTo1[] value) { extras = value; }
    public java.lang.String getAlias() { return alias; }
    public void setAlias(java.lang.String value) { alias = value; }
    public java.lang.String getPreviousAddress() { return previousAddress; }
    public void setPreviousAddress(java.lang.String value) { previousAddress = value; }
    public java.lang.String getChildren() { return children; }
    public void setChildren(java.lang.String value) { children = value; }
    public java.lang.String getSourceOfIncome() { return sourceOfIncome; }
    public void setSourceOfIncome(java.lang.String value) { sourceOfIncome = value; }
    public java.lang.String getCitizenship() { return citizenship; }
    public void setCitizenship(java.lang.String value) { citizenship = value; }
    public java.lang.String getSin() { return sin; }
    public void setSin(java.lang.String value) { sin = value; }
    public java.lang.Integer getHeadRecord() { return headRecord; }
    public void setHeadRecord(java.lang.Integer value) { headRecord = value; }
    public java.util.Set<Integer> getSubRecord() { return subRecord; }
    public void setSubRecord(java.util.Set<Integer> value) { subRecord = value; }
    public java.lang.String getAnonymous() { return anonymous; }
    public void setAnonymous(java.lang.String value) { anonymous = value; }
    public java.lang.String getSpokenLanguage() { return spokenLanguage; }
    public void setSpokenLanguage(java.lang.String value) { spokenLanguage = value; }
    public int getActiveCount() { return activeCount; }
    public void setActiveCount(int value) { activeCount = value; }
    public int getHsAlertCount() { return hsAlertCount; }
    public void setHsAlertCount(int value) { hsAlertCount = value; }
    public java.lang.String getDisplayName() { return displayName; }
    public void setDisplayName(java.lang.String value) { displayName = value; }
    public EmbeddedProviderTo1 getProvider() { return provider; }
    public void setProvider(EmbeddedProviderTo1 value) { provider = value; }
    public java.lang.String getLastUpdateUser() { return lastUpdateUser; }
    public void setLastUpdateUser(java.lang.String value) { lastUpdateUser = value; }
    public java.util.Date getLastUpdateDate() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(lastUpdateDate); }
    public void setLastUpdateDate(java.util.Date value) { lastUpdateDate = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.date(value); }
    public java.lang.String getTitle() { return title; }
    public void setTitle(java.lang.String value) { title = value; }
    public java.lang.String getOfficialLanguage() { return officialLanguage; }
    public void setOfficialLanguage(java.lang.String value) { officialLanguage = value; }
    public java.lang.String getCountryOfOrigin() { return countryOfOrigin; }
    public void setCountryOfOrigin(java.lang.String value) { countryOfOrigin = value; }
    public java.lang.String getNewsletter() { return newsletter; }
    public void setNewsletter(java.lang.String value) { newsletter = value; }
    public java.lang.String getCellPhone() { return cellPhone; }
    public void setCellPhone(java.lang.String value) { cellPhone = value; }
    public java.lang.String getPhoneComment() { return phoneComment; }
    public void setPhoneComment(java.lang.String value) { phoneComment = value; }
    public java.lang.String getMiddleNames() { return middleNames; }
    public void setMiddleNames(java.lang.String value) { middleNames = value; }
    public java.lang.String getRosterEnrolledTo() { return rosterEnrolledTo; }
    public void setRosterEnrolledTo(java.lang.String value) { rosterEnrolledTo = value; }
    public java.lang.String getResidentialAddress() { return residentialAddress; }
    public void setResidentialAddress(java.lang.String value) { residentialAddress = value; }
    public java.lang.String getResidentialCity() { return residentialCity; }
    public void setResidentialCity(java.lang.String value) { residentialCity = value; }
    public java.lang.String getResidentialProvince() { return residentialProvince; }
    public void setResidentialProvince(java.lang.String value) { residentialProvince = value; }
    public java.lang.String getResidentialPostal() { return residentialPostal; }
    public void setResidentialPostal(java.lang.String value) { residentialPostal = value; }
    public java.lang.String getGender() { return gender; }
    public void setGender(java.lang.String value) { gender = value; }
    public java.lang.String getPronoun() { return pronoun; }
    public void setPronoun(java.lang.String value) { pronoun = value; }
    public java.lang.Integer getGenderId() { return genderId; }
    public void setGenderId(java.lang.Integer value) { genderId = value; }
    public java.lang.Integer getPronounId() { return pronounId; }
    public void setPronounId(java.lang.Integer value) { pronounId = value; }
    public java.lang.String getPatientType() { return patientType; }
    public void setPatientType(java.lang.String value) { patientType = value; }
    public java.lang.String getPrefName() { return prefName; }
    public void setPrefName(java.lang.String value) { prefName = value; }
    public EmbeddedProviderTo1 getMrp() { return mrp; }
    public void setMrp(EmbeddedProviderTo1 value) { mrp = value; }
    public java.lang.String getNextAppointment() { return nextAppointment; }
    public void setNextAppointment(java.lang.String value) { nextAppointment = value; }
    public java.lang.Integer getId() { return id; }
    public void setId(java.lang.Integer value) { id = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public java.lang.String getLabel() { return label; }
    public void setLabel(java.lang.String value) { label = value; }
    public java.lang.String getFullName() { return fullName; }
    public void setFullName(java.lang.String value) { fullName = value; }
    public java.lang.String getAge() { return age; }
    public void setAge(java.lang.String value) { age = value; }
    public java.lang.String getFamilyDoctorLastName() { return familyDoctorLastName; }
    public void setFamilyDoctorLastName(java.lang.String value) { familyDoctorLastName = value; }
    public java.lang.String getFamilyDoctorFirstName() { return familyDoctorFirstName; }
    public void setFamilyDoctorFirstName(java.lang.String value) { familyDoctorFirstName = value; }
    public java.util.Calendar getBirthDay() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.calendar(birthDay); }
    public void setBirthDay(java.util.Calendar value) { birthDay = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.calendar(value); }
    public int getNumLinks() { return numLinks; }
    public void setNumLinks(int value) { numLinks = value; }
    public java.lang.String getStandardIdentificationHtml() { return standardIdentificationHtml; }
    public void setStandardIdentificationHtml(java.lang.String value) { standardIdentificationHtml = value; }
    public java.lang.String getFormattedName() { return formattedName; }
    public void setFormattedName(java.lang.String value) { formattedName = value; }
    public java.lang.String getBirthDayAsString() { return birthDayAsString; }
    public void setBirthDayAsString(java.lang.String value) { birthDayAsString = value; }
    public java.lang.String getRosterStatusDisplay() { return rosterStatusDisplay; }
    public void setRosterStatusDisplay(java.lang.String value) { rosterStatusDisplay = value; }
    public java.lang.String getFormattedDob() { return formattedDob; }
    public void setFormattedDob(java.lang.String value) { formattedDob = value; }
    public java.lang.String getFormattedDateJoined() { return formattedDateJoined; }
    public void setFormattedDateJoined(java.lang.String value) { formattedDateJoined = value; }
    public java.lang.String getFamilyDoctorName() { return familyDoctorName; }
    public void setFamilyDoctorName(java.lang.String value) { familyDoctorName = value; }
    public java.lang.String getFamilyDoctorNumber() { return familyDoctorNumber; }
    public void setFamilyDoctorNumber(java.lang.String value) { familyDoctorNumber = value; }
    public java.lang.String getFamilyPhysicianLastName() { return familyPhysicianLastName; }
    public void setFamilyPhysicianLastName(java.lang.String value) { familyPhysicianLastName = value; }
    public java.lang.String getFamilyPhysicianFirstName() { return familyPhysicianFirstName; }
    public void setFamilyPhysicianFirstName(java.lang.String value) { familyPhysicianFirstName = value; }
    public java.lang.String getFamilyPhysicianFullName() { return familyPhysicianFullName; }
    public void setFamilyPhysicianFullName(java.lang.String value) { familyPhysicianFullName = value; }
    public java.lang.String getFamilyPhysicianNumber() { return familyPhysicianNumber; }
    public void setFamilyPhysicianNumber(java.lang.String value) { familyPhysicianNumber = value; }
    public java.lang.String getFormattedEndDate() { return formattedEndDate; }
    public void setFormattedEndDate(java.lang.String value) { formattedEndDate = value; }
    public java.lang.String getFormattedEffDate() { return formattedEffDate; }
    public void setFormattedEffDate(java.lang.String value) { formattedEffDate = value; }
    public java.lang.String getFormattedRenewDate() { return formattedRenewDate; }
    public void setFormattedRenewDate(java.lang.String value) { formattedRenewDate = value; }
    public java.lang.String getSubjectPronoun() { return subjectPronoun; }
    public void setSubjectPronoun(java.lang.String value) { subjectPronoun = value; }
    public java.lang.String getPossessivePronoun() { return possessivePronoun; }
    public void setPossessivePronoun(java.lang.String value) { possessivePronoun = value; }
    public int getAgeInYears() { return ageInYears; }
    public void setAgeInYears(int value) { ageInYears = value; }
    public java.lang.String getFormattedLinks() { return formattedLinks; }
    public void setFormattedLinks(java.lang.String value) { formattedLinks = value; }
    public java.lang.Integer getCurrentRecord() { return currentRecord; }
    public void setCurrentRecord(java.lang.Integer value) { currentRecord = value; }
    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean value) { persistent = value; }
    @Override public String toString() { return "EmbeddedDemographicTo1[protected]"; }
}
