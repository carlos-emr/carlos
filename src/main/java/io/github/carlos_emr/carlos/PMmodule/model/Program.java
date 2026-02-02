/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package io.github.carlos_emr.carlos.PMmodule.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import java.util.Date;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;

/**
 * This is the object class that relates to the program table. Any customizations belong here.
 * 
 * Entity implementation class for Entity: Program
 * Represents a program in the system.
 */
@Entity
@Table(name = "program")
public class Program extends AbstractModel<Integer> {
    public static final Integer DEFAULT_COMMUNITY_PROGRAM_ID = Integer.valueOf(10010);

    public static final String EXTERNAL_TYPE = "external";
    public static final String COMMUNITY_TYPE = "community";
    public static final String SERVICE_TYPE = "Service";

    public static final String PROGRAM_STATUS_ACTIVE = "active";
    public static final String PROGRAM_STATUS_INACTIVE = "inactive";

    private final Integer DEFAULT_SERVICE_RESTRICTION_DAYS = 30;
    private final Integer MIN_AGE = 1;
    private final Integer MAX_AGE = 200;

    private int hashCode = Integer.MIN_VALUE;// primary key

    @Id
    @Column(name = "id")
    private Integer id;

    private boolean userDefined = true;
    private Integer numOfMembers;
    private Integer numOfIntakes;
    private Integer queueSize;
    private Integer maxAllowed;
    private String type;
    private String description;
    private String functionalCentreId;
    private String address;
    private String phone;
    private String fax;
    private String url;
    private String email;
    private String emergencyNumber;
    private String location;
    private String name;
    private boolean holdingTank;
    private boolean allowBatchAdmission;
    private boolean allowBatchDischarge;
    private boolean hic;
    private String programStatus = "active";
    private Integer intakeProgram;
    private String manOrWoman;
    private String genderDesc;
    private boolean transgender;
    private boolean firstNation;
    private boolean alcohol;
    private String abstinenceSupport;
    private boolean physicalHealth;
    private boolean mentalHealth;
    private boolean housing;
    private String exclusiveView = "no";
    private Integer ageMin;
    private Integer ageMax;
    private Integer maximumServiceRestrictionDays;
    private Integer defaultServiceRestrictionDays;
    private Integer shelterId;
    private int facilityId;

    private String facilityDesc;
    private String orgCd;
    private Integer capacity_funding = Integer.valueOf(0);
    private Integer capacity_space = Integer.valueOf(0);
    private Integer capacity_actual = Integer.valueOf(0);
    private String lastUpdateUser;
    private Date lastUpdateDate = new Date();
    private LookupCodeValue shelter;
    private String siteSpecificField;
    private Boolean enableEncounterTime = false;
    private Boolean enableEncounterTransportationTime = false;
    private String emailNotificationAddressesCsv = null;
    private Date lastReferralNotification = null;
    private boolean enableOCAN;

    //these are all transient - these need to be removed, we shouldn't be having fields like this in JPA model objects.
    private Integer noOfVacancy = 0;
    private String vacancyName;
    private String dateCreated;
    private double matches;
    private Integer vacancyId;
    private String vacancyTemplateName;

    /**
     * Constructor for required fields.
     * 
     * @param id the program ID
     * @param isUserDefined flag indicating if program is user defined
     * @param maxAllowed maximum number of clients allowed in the program
     * @param address the program address
     * @param phone the program phone number
     * @param fax the program fax number
     * @param url the program URL
     * @param email the program email
     * @param emergencyNumber the program emergency contact number
     * @param name the program name
     * @param holdingTank flag indicating if this is a holding tank program
     * @param programStatus the status of the program (active/inactive)
     */
    public Program(Integer id, boolean isUserDefined, Integer maxAllowed, String address, String phone, String fax, String url, String email, String emergencyNumber, String name, boolean holdingTank, String programStatus) {

        setId(id);
        setUserDefined(isUserDefined);
        setMaxAllowed(maxAllowed);
        setAddress(address);
        setPhone(phone);
        setFax(fax);
        setUrl(url);
        setEmail(email);
        setEmergencyNumber(emergencyNumber);
        setName(name);
        setHoldingTank(holdingTank);
        setProgramStatus(programStatus);

    }

    public String getSiteSpecificField() {
        return siteSpecificField;
    }

    public void setSiteSpecificField(String siteSpecificField) {
        this.siteSpecificField = siteSpecificField;
    }

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public String getFunctionalCentreId() {
        return functionalCentreId;
    }

    public void setFunctionalCentreId(String functionalCentreId) {
        this.functionalCentreId = functionalCentreId;
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }

    public Integer getCapacity_actual() {
        return capacity_actual;
    }

    public void setCapacity_actual(Integer capacity_actual) {
        this.capacity_actual = capacity_actual;
    }

    public Integer getCapacity_funding() {
        return capacity_funding;
    }

    public void setCapacity_funding(Integer capacity_funding) {
        this.capacity_funding = capacity_funding;
    }

    public Integer getCapacity_space() {
        return capacity_space;
    }

    public void setCapacity_space(Integer capacity_space) {
        this.capacity_space = capacity_space;
    }

    /**
     * Default no-arg constructor.
     * Required by JPA.
     */
    public Program() {
        // no arg constructor for JPA
    }

    public Integer getShelterId() {
        return shelterId;
    }

    public void setShelterId(Integer shelterId) {
        this.shelterId = shelterId;
    }

    public int getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(int facilityId) {
        this.facilityId = facilityId;
    }

    public String getOrgCd() {
        return orgCd;
    }

    public void setOrgCd(String orgCd) {
        this.orgCd = orgCd;
    }

    /**
     * Constructor for primary key.
     * 
     * @param id the program ID
     */
    public Program(Integer id) {
        this.setId(id);
    }

    /**
     * Checks if this program is user defined.
     * 
     * @return true if user defined, false otherwise
     */
    public boolean isUserDefined() {
        return userDefined;
    }

    /**
     * Sets whether this program is user defined.
     * 
     * @param userDefined true if user defined, false otherwise
     */
    public void setUserDefined(boolean userDefined) {
        this.userDefined = userDefined;
    }

    /**
     * Checks if this program is active.
     * 
     * @return true if the program status is active, false otherwise
     */
    public boolean isActive() {
        return PROGRAM_STATUS_ACTIVE.equals(programStatus);
    }

    /**
     * Checks if this program is full (number of members equals or exceeds the maximum allowed).
     * 
     * @return true if the program is full, false otherwise
     */
    public boolean isFull() {
        return getNumOfMembers().intValue() >= getMaxAllowed().intValue();
    }

    /**
     * Checks if this is an external program.
     * 
     * @return true if the program type is "external", false otherwise
     */
    public boolean isExternal() {
        return EXTERNAL_TYPE.equalsIgnoreCase(getType());
    }


    /**
     * Checks if this is a community program.
     * 
     * @return true if the program type is "community", false otherwise
     */
    public boolean isCommunity() {
        return COMMUNITY_TYPE.equalsIgnoreCase(getType());
    }

    /**
     * Checks if this is a service program.
     * 
     * @return true if the program type is "Service", false otherwise
     */
    public boolean isService() {
        return SERVICE_TYPE.equalsIgnoreCase(getType());
    }

    /**
     * Checks if this is a holding tank program.
     * 
     * @return true if this is a holding tank program, false otherwise
     */
    public boolean getHoldingTank() {
        return isHoldingTank();
    }

    /**
     * Gets the ID of this program.
     * 
     * @return the program ID
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the ID of this program.
     * 
     * @param id the program ID to set
     */
    public void setId(Integer id) {
        this.id = id;
        this.hashCode = Integer.MIN_VALUE;
    }

    /**
     * Gets the number of members in this program.
     * 
     * @return the number of members
     */
    public Integer getNumOfMembers() {
        return numOfMembers;
    }

    /**
     * Sets the number of members in this program.
     * 
     * @param numOfMembers the number of members to set
     */
    public void setNumOfMembers(Integer numOfMembers) {
        this.numOfMembers = numOfMembers;
    }

    /**
     * Gets the queue size for this program.
     * 
     * @return the queue size
     */
    public Integer getQueueSize() {
        return queueSize;
    }

    /**
     * Sets the queue size for this program.
     * 
     * @param queueSize the queue size to set
     */
    public void setQueueSize(Integer queueSize) {
        this.queueSize = queueSize;
    }

    /**
     * Gets the maximum number of clients allowed in this program.
     * 
     * @return the maximum allowed
     */
    public Integer getMaxAllowed() {
        return maxAllowed;
    }

    /**
     * Sets the maximum number of clients allowed in this program.
     * 
     * @param maxAllowed the maximum allowed to set
     */
    public void setMaxAllowed(Integer maxAllowed) {
        this.maxAllowed = maxAllowed;
    }

    /**
     * Gets the type of this program.
     * 
     * @return the program type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of this program.
     * 
     * @param type the program type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the description of this program.
     * 
     * @return the program description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this program.
     * 
     * @param description the program description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the address of this program.
     * 
     * @return the program address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the address of this program.
     * 
     * @param address the program address to set
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Gets the phone number of this program.
     * 
     * @return the program phone number
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the phone number of this program.
     * 
     * @param phone the program phone number to set
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Gets the fax number of this program.
     * 
     * @return the program fax number
     */
    public String getFax() {
        return fax;
    }

    /**
     * Sets the fax number of this program.
     * 
     * @param fax the program fax number to set
     */
    public void setFax(String fax) {
        this.fax = fax;
    }

    /**
     * Gets the URL of this program.
     * 
     * @return the program URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the URL of this program.
     * 
     * @param url the program URL to set
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the email address of this program.
     * 
     * @return the program email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email address of this program.
     * 
     * @param email the program email address to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Gets the emergency contact number of this program.
     * 
     * @return the program emergency contact number
     */
    public String getEmergencyNumber() {
        return emergencyNumber;
    }

    /**
     * Sets the emergency contact number of this program.
     * 
     * @param emergencyNumber the program emergency contact number to set
     */
    public void setEmergencyNumber(String emergencyNumber) {
        this.emergencyNumber = emergencyNumber;
    }

    /**
     * Gets the location of this program.
     * 
     * @return the program location
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the location of this program.
     * 
     * @param location the program location to set
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Gets the name of this program.
     * 
     * @return the program name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the JavaScript-escaped name of this program.
     * 
     * @return the JavaScript-escaped program name
     */
    public String getNameJs() {
        return Misc.getStringJs(name);
    }

    /**
     * Sets the name of this program.
     * 
     * @param name the program name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Checks if this is a holding tank program.
     * 
     * @return true if this is a holding tank program, false otherwise
     */
    public boolean isHoldingTank() {
        return holdingTank;
    }

    /**
     * Sets whether this is a holding tank program.
     * 
     * @param holdingTank true if this is a holding tank program, false otherwise
     */
    public void setHoldingTank(boolean holdingTank) {
        this.holdingTank = holdingTank;
    }

    /**
     * Checks if batch admission is allowed for this program.
     * 
     * @return true if batch admission is allowed, false otherwise
     */
    public boolean isAllowBatchAdmission() {
        return allowBatchAdmission;
    }

    /**
     * Sets whether batch admission is allowed for this program.
     * 
     * @param allowBatchAdmission true if batch admission is allowed, false otherwise
     */
    public void setAllowBatchAdmission(boolean allowBatchAdmission) {
        this.allowBatchAdmission = allowBatchAdmission;
    }

    /**
     * Checks if batch discharge is allowed for this program.
     * 
     * @return true if batch discharge is allowed, false otherwise
     */
    public boolean isAllowBatchDischarge() {
        return allowBatchDischarge;
    }

    /**
     * Sets whether batch discharge is allowed for this program.
     * 
     * @param allowBatchDischarge true if batch discharge is allowed, false otherwise
     */
    public void setAllowBatchDischarge(boolean allowBatchDischarge) {
        this.allowBatchDischarge = allowBatchDischarge;
    }

    /**
     * Checks if this program requires a health insurance card (HIC).
     * 
     * @return true if a HIC is required, false otherwise
     */
    public boolean isHic() {
        return hic;
    }

    /**
     * Sets whether this program requires a health insurance card (HIC).
     * 
     * @param hic true if a HIC is required, false otherwise
     */
    public void setHic(boolean hic) {
        this.hic = hic;
    }

    /**
     * Gets the status of this program.
     * 
     * @return the program status
     */
    public String getProgramStatus() {
        return programStatus;
    }

    /**
     * Sets the status of this program.
     * 
     * @param programStatus the program status to set
     */
    public void setProgramStatus(String programStatus) {
        this.programStatus = programStatus;
    }

    /**
     * Gets the intake program associated with this program.
     * 
     * @return the intake program ID
     */
    public Integer getIntakeProgram() {
        return intakeProgram;
    }

    /**
     * Sets the intake program associated with this program.
     * 
     * @param intakeProgram the intake program ID to set
     */
    public void setIntakeProgram(Integer intakeProgram) {
        this.intakeProgram = intakeProgram;
    }


    /**
     * Gets the abstinence support offered by this program.
     * 
     * @return the abstinence support description
     */
    public String getAbstinenceSupport() {
        return abstinenceSupport;
    }

    /**
     * Sets the abstinence support offered by this program.
     * 
     * @param abstinenceSupport the abstinence support description to set
     */
    public void setAbstinenceSupport(String abstinenceSupport) {
        this.abstinenceSupport = abstinenceSupport;
    }

    /**
     * Checks if this program addresses alcohol addiction.
     * 
     * @return true if the program addresses alcohol addiction, false otherwise
     */
    public boolean isAlcohol() {
        return alcohol;
    }

    /**
     * Sets whether this program addresses alcohol addiction.
     * 
     * @param alcohol true if the program addresses alcohol addiction, false otherwise
     */
    public void setAlcohol(boolean alcohol) {
        this.alcohol = alcohol;
    }


    /**
     * Checks if this program serves First Nations communities.
     * 
     * @return true if the program serves First Nations, false otherwise
     */
    public boolean isFirstNation() {
        return firstNation;
    }

    /**
     * Sets whether this program serves First Nations communities.
     * 
     * @param firstNation true if the program serves First Nations, false otherwise
     */
    public void setFirstNation(boolean firstNation) {
        this.firstNation = firstNation;
    }

    public int getHashCode() {
        return hashCode;
    }

    public void setHashCode(int hashCode) {
        this.hashCode = hashCode;
    }

    /**
     * Checks if this program provides housing support.
     * 
     * @return true if the program provides housing support, false otherwise
     */
    public boolean isHousing() {
        return housing;
    }

    /**
     * Sets whether this program provides housing support.
     * 
     * @param housing true if the program provides housing support, false otherwise
     */
    public void setHousing(boolean housing) {
        this.housing = housing;
    }

    /**
     * Gets the gender served by this program (man, woman, or both).
     * 
     * @return the gender served by the program
     */
    public String getManOrWoman() {
        return manOrWoman;
    }

    /**
     * Sets the gender served by this program (man, woman, or both).
     * 
     * @param manOrWoman the gender served by the program
     */
    public void setManOrWoman(String manOrWoman) {
        this.manOrWoman = manOrWoman;
    }

    /**
     * Checks if this program addresses mental health.
     * 
     * @return true if the program addresses mental health, false otherwise
     */
    public boolean isMentalHealth() {
        return mentalHealth;
    }

    /**
     * Sets whether this program addresses mental health.
     * 
     * @param mentalHealth true if the program addresses mental health, false otherwise
     */
    public void setMentalHealth(boolean mentalHealth) {
        this.mentalHealth = mentalHealth;
    }

    /**
     * Checks if this program addresses physical health.
     * 
     * @return true if the program addresses physical health, false otherwise
     */
    public boolean isPhysicalHealth() {
        return physicalHealth;
    }

    /**
     * Sets whether this program addresses physical health.
     * 
     * @param physicalHealth true if the program addresses physical health, false otherwise
     */
    public void setPhysicalHealth(boolean physicalHealth) {
        this.physicalHealth = physicalHealth;
    }

    /**
     * Checks if this program serves transgender individuals.
     * 
     * @return true if the program serves transgender individuals, false otherwise
     */
    public boolean isTransgender() {
        return transgender;
    }

    /**
     * Sets whether this program serves transgender individuals.
     * 
     * @param transgender true if the program serves transgender individuals, false otherwise
     */
    public void setTransgender(boolean transgender) {
        this.transgender = transgender;
    }

    /**
     * Gets the exclusive view setting for this program.
     * 
     * @return the exclusive view setting
     */
    public String getExclusiveView() {
        return exclusiveView;
    }

    /**
     * Sets the exclusive view setting for this program.
     * 
     * @param exclusiveView the exclusive view setting to set
     */
    public void setExclusiveView(String exclusiveView) {
        this.exclusiveView = exclusiveView;
    }

    /**
     * Gets the minimum age for this program.
     * 
     * @return the minimum age, or the default minimum age if not set
     */
    public Integer getAgeMin() {
        if (this.ageMin != null) {
            return ageMin;
        }

        return this.MIN_AGE;
    }

    /**
     * Sets the minimum age for this program.
     * 
     * @param ageMin the minimum age to set
     */
    public void setAgeMin(Integer ageMin) {
        this.ageMin = ageMin;
    }

    /**
     * Gets the maximum age for this program.
     * 
     * @return the maximum age, or the default maximum age if not set
     */
    public Integer getAgeMax() {
        if (this.ageMax != null) {
            return ageMax;
        }
        return this.MAX_AGE;
    }

    /**
     * Sets the maximum age for this program.
     * 
     * @param ageMax the maximum age to set
     */
    public void setAgeMax(Integer ageMax) {
        this.ageMax = ageMax;
    }

    /**
     * Gets the maximum number of service restriction days for this program.
     * 
     * @return the maximum service restriction days
     */
    public Integer getMaximumServiceRestrictionDays() {
        return maximumServiceRestrictionDays;
    }

    /**
     * Sets the maximum number of service restriction days for this program.
     * 
     * @param maximumServiceRestrictionDays the maximum service restriction days to set
     */
    public void setMaximumServiceRestrictionDays(Integer maximumServiceRestrictionDays) {
        this.maximumServiceRestrictionDays = maximumServiceRestrictionDays;
    }

    /**
     * Gets the default number of service restriction days for this program.
     * 
     * @return the default service restriction days, or the program default if not set or invalid
     */
    public Integer getDefaultServiceRestrictionDays() {
        if ((this.defaultServiceRestrictionDays != null) && (this.defaultServiceRestrictionDays > 0)) {
            return defaultServiceRestrictionDays;
        }

        return this.DEFAULT_SERVICE_RESTRICTION_DAYS;
    }

    /**
     * Sets the default number of service restriction days for this program.
     * 
     * @param defaultServiceRestrictionDays the default service restriction days to set
     */
    public void setDefaultServiceRestrictionDays(Integer defaultServiceRestrictionDays) {
        this.defaultServiceRestrictionDays = defaultServiceRestrictionDays;
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj) return false;
        if (!(obj instanceof Program)) return false;
        else {
            Program program = (Program) obj;
            if (null == this.getId() || null == program.getId()) return false;
            else return (this.getId().equals(program.getId()));
        }
    }

    @Override
    public int hashCode() {
        if (Integer.MIN_VALUE == this.hashCode) {
            if (null == this.getId()) return super.hashCode();
            else {
                String hashStr = this.getClass().getName() + ":" + this.getId().hashCode();
                this.hashCode = hashStr.hashCode();
            }
        }
        return this.hashCode;
    }

    public String toString() {
        return super.toString();
    }

    /**
     * Gets the description of the facility associated with this program.
     * 
     * @return the facility description
     */
    public String getFacilityDesc() {
        return facilityDesc;
    }

    /**
     * Sets the description of the facility associated with this program.
     * 
     * @param facilityDesc the facility description to set
     */
    public void setFacilityDesc(String facilityDesc) {
        this.facilityDesc = facilityDesc;
    }


    /**
     * Gets the number of intake appointments for this program.
     * 
     * @return the number of intakes
     */
    public Integer getNumOfIntakes() {
        return numOfIntakes;
    }

    /**
     * Sets the number of intake appointments for this program.
     * 
     * @param numOfIntakes the number of intakes to set
     */
    public void setNumOfIntakes(Integer numOfIntakes) {
        this.numOfIntakes = numOfIntakes;
    }

    /**
     * Gets the description of the genders served by this program.
     * 
     * @return the gender description
     */
    public String getGenderDesc() {
        return genderDesc;
    }

    /**
     * Sets the description of the genders served by this program.
     * 
     * @param genderDesc the gender description to set
     */
    public void setGenderDesc(String genderDesc) {
        this.genderDesc = genderDesc;
    }

    /**
     * Gets the shelter associated with this program.
     * 
     * @return the shelter
     */
    public LookupCodeValue getShelter() {
        return shelter;
    }

    /**
     * Sets the shelter associated with this program.
     * 
     * @param shelter the shelter to set
     */
    public void setShelter(LookupCodeValue shelter) {
        this.shelter = shelter;
    }

    /**
     * Checks if encounter time tracking is enabled for this program.
     * 
     * @return true if encounter time tracking is enabled, false otherwise
     */
    public Boolean isEnableEncounterTime() {
        return enableEncounterTime;
    }

    /**
     * Gets the flag indicating if encounter time tracking is enabled for this program.
     * 
     * @return the enableEncounterTime flag
     */
    public Boolean getEnableEncounterTime() {
        return enableEncounterTime;
    }

    /**
     * Sets the flag indicating if encounter time tracking is enabled for this program.
     * 
     * @param enableEncounterTime the enableEncounterTime flag to set
     */
    public void setEnableEncounterTime(Boolean enableEncounterTime) {
        this.enableEncounterTime = enableEncounterTime;
    }
    
    /**
     * Checks if encounter transportation time tracking is enabled for this program.
     * 
     * @return true if encounter transportation time tracking is enabled, false otherwise
     */
    public Boolean isEnableEncounterTransportationTime() {
        return enableEncounterTransportationTime;
    }

    /**
     * Gets the flag indicating if encounter transportation time tracking is enabled for this program.
     * 
     * @return the enableEncounterTransportationTime flag
     */
    public Boolean getEnableEncounterTransportationTime() {
        return enableEncounterTransportationTime;
    }

    /**
     * Sets the flag indicating if encounter transportation time tracking is enabled for this program.
     * 
     * @param enableEncounterTransportationTime the enableEncounterTransportationTime flag to set
     */
    public void setEnableEncounterTransportationTime(Boolean enableEncounterTransportationTime) {
        this.enableEncounterTransportationTime = enableEncounterTransportationTime;
    }

    /**
     * Gets the comma-separated list of email addresses for notifications related to this program.
     * 
     * @return the email notification addresses CSV
     */
    public String getEmailNotificationAddressesCsv() {
        return emailNotificationAddressesCsv;
    }

    /**
     * Sets the comma-separated list of email addresses for notifications related to this program.
     * 
     * @param emailNotificationAddressesCsv the email notification addresses CSV to set
     */
    public void setEmailNotificationAddressesCsv(String emailNotificationAddressesCsv) {
        this.emailNotificationAddressesCsv = emailNotificationAddressesCsv;
    }

    /**
     * Gets the date of the last referral notification for this program.
     * 
     * @return the last referral notification date
     */
    public Date getLastReferralNotification() {
        return lastReferralNotification;
    }

    /**
     * Sets the date of the last referral notification for this program.
     * 
     * @param lastReferralNotification the last referral notification date to set
     */
    public void setLastReferralNotification(Date lastReferralNotification) {
        this.lastReferralNotification = lastReferralNotification;
    }

    /**
     * Gets the number of vacancies in this program.
     * 
     * @return the number of vacancies
     */
    public Integer getNoOfVacancy() {
        return noOfVacancy;
    }

    /**
     * Sets the number of vacancies in this program.
     * 
     * @param noOfVacancy the number of vacancies to set
     */
    public void setNoOfVacancy(Integer noOfVacancy) {
        this.noOfVacancy = noOfVacancy;
    }

    /**
     * Gets the name of the vacancy in this program.
     * 
     * @return the vacancy name
     */
    public String getVacancyName() {
        return vacancyName;
    }

    /**
     * Sets the name of the vacancy in this program.
     * 
     * @param vacancyName the vacancy name to set
     */
    public void setVacancyName(String vacancyName) {
        this.vacancyName = vacancyName;
    }

    /**
     * Gets the date this program was created.
     * 
     * @return the date created
     */
    public String getDateCreated() {
        return dateCreated;
    }

    /**
     * Sets the date this program was created.
     * 
     * @param dateCreated the date created to set
     */
    public void setDateCreated(String dateCreated) {
        this.dateCreated = dateCreated;
    }

    /**
     * Gets the number of matches for this program.
     * 
     * @return the number of matches
     */
    public double getMatches() {
        return matches;
    }

    /**
     * Sets the number of matches for this program.
     * 
     * @param matches the number of matches to set
     */
    public void setMatches(double matches) {
        this.matches = matches;
    }

    /**
     * Gets the ID of the vacancy in this program.
     * 
     * @return the vacancy ID
     */
    public Integer getVacancyId() {
        return vacancyId;
    }

    /**
     * Sets the ID of the vacancy in this program.
     * 
     * @param vacancyId the vacancy ID to set
     */
    public void setVacancyId(Integer vacancyId) {
        this.vacancyId = vacancyId;
    }

    /**
     * Gets the name of the vacancy template used by this program.
     * 
     * @return the vacancy template name
     */
    public String getVacancyTemplateName() {
        return vacancyTemplateName;
    }

    /**
     * Sets the name of the vacancy template used by this program.
     * 
     * @param vacancyTemplateName the vacancy template name to set
     */
    public void setVacancyTemplateName(String vacancyTemplateName) {
        this.vacancyTemplateName = vacancyTemplateName;
    }

    /**
     * Checks if OCAN (Ontario Common Assessment of Need) is enabled for this program.
     * 
     * @return true if OCAN is enabled, false otherwise
     */
    public boolean isEnableOCAN() {
        return enableOCAN;
    }

    /**
     * Sets whether OCAN (Ontario Common Assessment of Need) is enabled for this program.
     * 
     * @param enableOCAN true if OCAN is enabled, false otherwise
     */
    public void setEnableOCAN(boolean enableOCAN) {
        this.enableOCAN = enableOCAN;
    }

}
