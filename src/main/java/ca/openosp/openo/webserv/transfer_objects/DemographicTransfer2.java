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


package ca.openosp.openo.webserv.transfer_objects;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import ca.openosp.openo.commn.model.Demographic;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class DemographicTransfer2 {

    private Integer demographicNo;
    private String phone;
    private String patientStatus;
    private Date patientStatusDate;
    private String rosterStatus;
    private String providerNo;
    private String hin;
    private String address;
    private String province;
    private String monthOfBirth;
    private String ver;
    private String dateOfBirth;
    private String sex;
    private String sexDesc;
    private Date dateJoined;
    private String familyDoctor;
    private String city;
    private String firstName;
    private String postal;
    private Date hcRenewDate;
    private String phone2;
    private String pcnIndicator;
    private Date endDate;
    private String lastName;
    private String hcType;
    private String chartNo;
    private String email;
    private Boolean consentToUseEmailForCare;
    private String yearOfBirth;
    private Date effDate;
    private Date rosterDate;
    private Date rosterTerminationDate;
    private String rosterTerminationReason;
    private String links;
    private String alias;
    private String previousAddress;
    private String children;
    private String sourceOfIncome;
    private String citizenship;
    private String sin;
    private Integer headRecord;
    private String anonymous;
    private String spokenLanguage;
    private Integer activeCount;
    private Integer hsAlertCount;
    private String displayName;
    private String lastUpdateUser;
    private Date lastUpdateDate;
    private String title;
    private String officialLanguage;

    private String countryOfOrigin;
    private String newsletter;

    private String middleNames;
    private String rosterEnrolledTo;

    private String residentialAddress;
    private String residentialCity;
    private String residentialProvince;
    private String residentialPostal;

    private String cellPhone;
    private String phoneComment;
    private String gender;
    private String pronoun;
    private Integer genderId;
    private Integer pronounId;


    public Integer getDemographicNo() {
        return (demographicNo);
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getPhone() {
        return (phone);
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPatientStatus() {
        return (patientStatus);
    }

    public void setPatientStatus(String patientStatus) {
        this.patientStatus = patientStatus;
    }

    public Date getPatientStatusDate() {
        return (patientStatusDate);
    }

    public void setPatientStatusDate(Date patientStatusDate) {
        this.patientStatusDate = patientStatusDate;
    }

    public String getRosterStatus() {
        return (rosterStatus);
    }

    public void setRosterStatus(String rosterStatus) {
        this.rosterStatus = rosterStatus;
    }

    public String getProviderNo() {
        return (providerNo);
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }



    public String getHin() {
        return (hin);
    }

    public void setHin(String hin) {
        this.hin = hin;
    }

    public String getAddress() {
        return (address);
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getProvince() {
        return (province);
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getMonthOfBirth() {
        return (monthOfBirth);
    }

    public void setMonthOfBirth(String monthOfBirth) {
        this.monthOfBirth = monthOfBirth;
    }

    public String getVer() {
        return (ver);
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getDateOfBirth() {
        return (dateOfBirth);
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getSex() {
        return (sex);
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getSexDesc() {
        return (sexDesc);
    }

    public void setSexDesc(String sexDesc) {
        this.sexDesc = sexDesc;
    }

    public Date getDateJoined() {
        return (dateJoined);
    }

    public void setDateJoined(Date dateJoined) {
        this.dateJoined = dateJoined;
    }

    public String getFamilyDoctor() {
        return (familyDoctor);
    }

    public void setFamilyDoctor(String familyDoctor) {
        this.familyDoctor = familyDoctor;
    }

    public String getCity() {
        return (city);
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getFirstName() {
        return (firstName);
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getPostal() {
        return (postal);
    }

    public void setPostal(String postal) {
        this.postal = postal;
    }

    public Date getHcRenewDate() {
        return (hcRenewDate);
    }

    public void setHcRenewDate(Date hcRenewDate) {
        this.hcRenewDate = hcRenewDate;
    }

    public String getPhone2() {
        return (phone2);
    }

    public void setPhone2(String phone2) {
        this.phone2 = phone2;
    }

    public String getPcnIndicator() {
        return (pcnIndicator);
    }

    public void setPcnIndicator(String pcnIndicator) {
        this.pcnIndicator = pcnIndicator;
    }

    public Date getEndDate() {
        return (endDate);
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getLastName() {
        return (lastName);
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getHcType() {
        return (hcType);
    }

    public void setHcType(String hcType) {
        this.hcType = hcType;
    }

    public String getChartNo() {
        return (chartNo);
    }

    public void setChartNo(String chartNo) {
        this.chartNo = chartNo;
    }

    public String getEmail() {
        return (email);
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getYearOfBirth() {
        return (yearOfBirth);
    }

    public void setYearOfBirth(String yearOfBirth) {
        this.yearOfBirth = yearOfBirth;
    }

    public Date getEffDate() {
        return (effDate);
    }

    public void setEffDate(Date effDate) {
        this.effDate = effDate;
    }

    public Date getRosterDate() {
        return (rosterDate);
    }

    public void setRosterDate(Date rosterDate) {
        this.rosterDate = rosterDate;
    }

    public Date getRosterTerminationDate() {
        return (rosterTerminationDate);
    }

    public void setRosterTerminationDate(Date rosterTerminationDate) {
        this.rosterTerminationDate = rosterTerminationDate;
    }

    public String getRosterTerminationReason() {
        return (rosterTerminationReason);
    }

    public void setRosterTerminationReason(String rosterTerminationReason) {
        this.rosterTerminationReason = rosterTerminationReason;
    }

    public String getLinks() {
        return (links);
    }

    public void setLinks(String links) {
        this.links = links;
    }

    public String getAlias() {
        return (alias);
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getPreviousAddress() {
        return (previousAddress);
    }

    public void setPreviousAddress(String previousAddress) {
        this.previousAddress = previousAddress;
    }

    public String getChildren() {
        return (children);
    }

    public void setChildren(String children) {
        this.children = children;
    }

    public String getSourceOfIncome() {
        return (sourceOfIncome);
    }

    public void setSourceOfIncome(String sourceOfIncome) {
        this.sourceOfIncome = sourceOfIncome;
    }

    public String getCitizenship() {
        return (citizenship);
    }

    public void setCitizenship(String citizenship) {
        this.citizenship = citizenship;
    }

    public String getSin() {
        return (sin);
    }

    public void setSin(String sin) {
        this.sin = sin;
    }

    public Integer getHeadRecord() {
        return (headRecord);
    }

    public void setHeadRecord(Integer headRecord) {
        this.headRecord = headRecord;
    }

    public String getAnonymous() {
        return (anonymous);
    }

    public void setAnonymous(String anonymous) {
        this.anonymous = anonymous;
    }

    public String getSpokenLanguage() {
        return (spokenLanguage);
    }

    public void setSpokenLanguage(String spokenLanguage) {
        this.spokenLanguage = spokenLanguage;
    }

    public Integer getActiveCount() {
        return (activeCount);
    }

    public void setActiveCount(Integer activeCount) {
        this.activeCount = activeCount;
    }

    public Integer getHsAlertCount() {
        return (hsAlertCount);
    }

    public void setHsAlertCount(Integer hsAlertCount) {
        this.hsAlertCount = hsAlertCount;
    }

    public String getDisplayName() {
        return (displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLastUpdateUser() {
        return (lastUpdateUser);
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }

    public Date getLastUpdateDate() {
        return (lastUpdateDate);
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getTitle() {
        return (title);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOfficialLanguage() {
        return (officialLanguage);
    }

    public void setOfficialLanguage(String officialLanguage) {
        this.officialLanguage = officialLanguage;
    }

    public Boolean getConsentToUseEmailForCare() {
        return consentToUseEmailForCare;
    }

    public void setConsentToUseEmailForCare(Boolean consentToUseEmailForCare) {
        this.consentToUseEmailForCare = consentToUseEmailForCare;
    }

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public String getNewsletter() {
        return newsletter;
    }

    public void setNewsletter(String newsletter) {
        this.newsletter = newsletter;
    }

    public String getMiddleNames() {
        return middleNames;
    }

    public void setMiddleNames(String middleNames) {
        this.middleNames = middleNames;
    }

    public String getRosterEnrolledTo() {
        return rosterEnrolledTo;
    }

    public void setRosterEnrolledTo(String rosterEnrolledTo) {
        this.rosterEnrolledTo = rosterEnrolledTo;
    }

    public String getResidentialAddress() {
        return residentialAddress;
    }

    public void setResidentialAddress(String residentialAddress) {
        this.residentialAddress = residentialAddress;
    }

    public String getResidentialCity() {
        return residentialCity;
    }

    public void setResidentialCity(String residentialCity) {
        this.residentialCity = residentialCity;
    }

    public String getResidentialProvince() {
        return residentialProvince;
    }

    public void setResidentialProvince(String residentialProvince) {
        this.residentialProvince = residentialProvince;
    }

    public String getResidentialPostal() {
        return residentialPostal;
    }

    public void setResidentialPostal(String residentialPostal) {
        this.residentialPostal = residentialPostal;
    }

    public String getCellPhone() {
        return cellPhone;
    }

    public void setCellPhone(String cellPhone) {
        this.cellPhone = cellPhone;
    }

    public String getPhoneComment() {
        return phoneComment;
    }

    public void setPhoneComment(String phoneComment) {
        this.phoneComment = phoneComment;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPronoun() {
        return pronoun;
    }

    public void setPronoun(String pronoun) {
        this.pronoun = pronoun;
    }

    public Integer getGenderId() {
        return genderId;
    }

    public void setGenderId(Integer genderId) {
        this.genderId = genderId;
    }

    public Integer getPronounId() {
        return pronounId;
    }

    public void setPronounId(Integer pronounId) {
        this.pronounId = pronounId;
    }

    public static DemographicTransfer2 toTransfer(Demographic demographic) {
        if (demographic == null) return (null);

        DemographicTransfer2 demographicTransfer = new DemographicTransfer2();

        BeanUtils.copyProperties(demographic, demographicTransfer);

        return (demographicTransfer);
    }


    public static DemographicTransfer2[] toTransfers(List<Demographic> demographics) {
        ArrayList<DemographicTransfer2> results = new ArrayList<DemographicTransfer2>();

        for (Demographic demographic : demographics) {
            results.add(toTransfer(demographic));
        }

        return (results.toArray(new DemographicTransfer2[0]));
    }

    @Override
    public String toString() {
        return (ReflectionToStringBuilder.toString(this));
    }

    public DemographicTransfer2 filter(String[] fields) {
        DemographicTransfer2 dto = new DemographicTransfer2();
        for (String field : fields) {
            if ("demographicNo".equals(field)) dto.setDemographicNo(this.getDemographicNo());
            else if ("address".equals(field)) dto.setAddress(this.getAddress());
            else if ("city".equals(field)) dto.setCity(this.getCity());
            else if ("province".equals(field)) dto.setProvince(this.getProvince());
            else if ("postal".equals(field)) dto.setPostal(this.getPostal());
            else if ("activeCount".equals(field)) dto.setActiveCount(this.getActiveCount());
            else if ("alias".equals(field)) dto.setAlias(this.getAlias());
            else if ("anonymous".equals(field)) dto.setAnonymous(this.getAnonymous());
            else if ("chartNo".equals(field)) dto.setChartNo(this.getChartNo());
            else if ("children".equals(field)) dto.setChildren(this.getChildren());
            else if ("citizenship".equals(field)) dto.setCitizenship(this.getCitizenship());
            else if ("dateJoined".equals(field)) dto.setDateJoined(this.getDateJoined());
            else if ("dateOfBirth".equals(field)) dto.setDateOfBirth(this.getDateOfBirth());
            else if ("monthOfBirth".equals(field)) dto.setMonthOfBirth(this.getMonthOfBirth());
            else if ("yearOfBirth".equals(field)) dto.setYearOfBirth(this.getYearOfBirth());
            else if ("displayName".equals(field)) dto.setDisplayName(this.getDisplayName());
            else if ("effDate".equals(field)) dto.setEffDate(this.getEffDate());
            else if ("endDate".equals(field)) dto.setEndDate(this.getEndDate());
            else if ("email".equals(field)) dto.setEmail(this.getEmail());
            else if ("familyDoctor".equals(field)) dto.setFamilyDoctor(this.getFamilyDoctor());
            else if ("firstName".equals(field)) dto.setFirstName(this.getFirstName());
            else if ("lastName".equals(field)) dto.setLastName(this.getLastName());
            else if ("hcRenewDate".equals(field)) dto.setHcRenewDate(this.getHcRenewDate());
            else if ("hcType".equals(field)) dto.setHcType(this.getHcType());
            else if ("headRecord".equals(field)) dto.setHeadRecord(this.getHeadRecord());
            else if ("hin".equals(field)) dto.setHin(this.getHin());
            else if ("hsAlertCount".equals(field)) dto.setHsAlertCount(this.getHsAlertCount());
            else if ("lastUpdateDate".equals(field)) dto.setLastUpdateDate(this.getLastUpdateDate());
            else if ("lastUpdateUser".equals(field)) dto.setLastUpdateUser(this.getLastUpdateUser());
            else if ("links".equals(field)) dto.setLinks(this.getLinks());
            else if ("officialLanguage".equals(field)) dto.setOfficialLanguage(this.getOfficialLanguage());
            else if ("patientStatus".equals(field)) dto.setPatientStatus(this.getPatientStatus());
            else if ("patientStatusDate".equals(field)) dto.setPatientStatusDate(this.getPatientStatusDate());
            else if ("pcnIndicator".equals(field)) dto.setPcnIndicator(this.getPcnIndicator());
            else if ("phone".equals(field)) dto.setPhone(this.getPhone());
            else if ("phone2".equals(field)) dto.setPhone(this.getPhone2());
            else if ("previousAddress".equals(field)) dto.setPreviousAddress(this.getPreviousAddress());
            else if ("providerNo".equals(field)) dto.setProviderNo(this.getProviderNo());
            else if ("rosterDate".equals(field)) dto.setRosterDate(this.getRosterDate());
            else if ("rosterStatus".equals(field)) dto.setRosterStatus(this.getRosterStatus());
            else if ("rosterTerminationDate".equals(field))
                dto.setRosterTerminationDate(this.getRosterTerminationDate());
            else if ("rosterTerminationReason".equals(field))
                dto.setRosterTerminationReason(this.getRosterTerminationReason());
            else if ("sex".equals(field)) dto.setSex(this.getSex());
            else if ("sexDesc".equals(field)) dto.setSexDesc(this.getSexDesc());
            else if ("sin".equals(field)) dto.setSin(this.getSin());
            else if ("sourceOfIncome".equals(field)) dto.setSourceOfIncome(this.getSourceOfIncome());
            else if ("spokenLanguage".equals(field)) dto.setSpokenLanguage(this.getSpokenLanguage());
            else if ("title".equals(field)) dto.setTitle(this.getTitle());
            else if ("ver".equals(field)) dto.setVer(this.getVer());
        }
        return dto;
    }


}
