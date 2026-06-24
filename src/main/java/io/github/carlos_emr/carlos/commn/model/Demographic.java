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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.commn.model;

import org.springframework.beans.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import io.github.carlos_emr.carlos.PMmodule.utility.DateTimeFormatUtils;
import io.github.carlos_emr.carlos.PMmodule.utility.Utility;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.i18n.LocaleContextHolder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This is the object class that relates to the demographic table. Any customizations belong here.
 */
@Entity
@Table(name = "demographic")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class Demographic extends AbstractModel<Integer> implements Serializable {

    private static final String DEFAULT_MONTH = "01";
    private static final String DEFAULT_DATE = "01";
    private static final String DEFAULT_YEAR = "1900";
    private static final String DEFAULT_SEX = "M";
    private static final String DEFAULT_PATIENT_STATUS = PatientStatus.AC.name();
    private static final String DEFAULT_HEATH_CARD_TYPE = "ON";
    private static final String DEFAULT_FUTURE_DATE = "2100-01-01";
    public static final String ANONYMOUS = "ANONYMOUS";
    public static final String UNIQUE_ANONYMOUS = "UNIQUE_ANONYMOUS";
    public static final int LAST_NAME_MAX_LENGTH = 30;
    public static final int FIRST_NAME_MAX_LENGTH = 30;
    public static final int MIDDLE_NAMES_MAX_LENGTH = 100;
    public static final int ALIAS_MAX_LENGTH = 70;
    public static final int ADDRESS_MAX_LENGTH = 60;
    public static final int CITY_MAX_LENGTH = 50;
    public static final int PROVINCE_MAX_LENGTH = 20;
    public static final int POSTAL_MAX_LENGTH = 9;
    public static final int RESIDENTIAL_ADDRESS_MAX_LENGTH = 60;
    public static final int RESIDENTIAL_CITY_MAX_LENGTH = 50;
    public static final int RESIDENTIAL_PROVINCE_MAX_LENGTH = 20;
    public static final int RESIDENTIAL_POSTAL_MAX_LENGTH = 9;
    public static final int EMAIL_MAX_LENGTH = 100;
    public static final int PHONE_MAX_LENGTH = 20;
    public static final int HIN_MAX_LENGTH = 20;
    public static final int VER_MAX_LENGTH = 3;
    public static final int SEX_MAX_LENGTH = 1;
    public static final int MONTH_OF_BIRTH_MAX_LENGTH = 2;
    public static final int DATE_OF_BIRTH_MAX_LENGTH = 2;
    public static final int YEAR_OF_BIRTH_MAX_LENGTH = 4;
    public static final int ROSTER_STATUS_MAX_LENGTH = 20;
    public static final int ROSTER_ENROLLED_TO_MAX_LENGTH = 20;
    public static final int PATIENT_STATUS_MAX_LENGTH = 20;
    public static final int CHART_NO_MAX_LENGTH = 10;
    public static final int PROVIDER_NO_MAX_LENGTH = 250;
    public static final int PCN_INDICATOR_MAX_LENGTH = 20;
    public static final int HC_TYPE_MAX_LENGTH = 20;
    public static final int FAMILY_DOCTOR_MAX_LENGTH = 80;
    public static final int PREVIOUS_ADDRESS_MAX_LENGTH = 255;
    public static final int CHILDREN_MAX_LENGTH = 255;
    public static final int SOURCE_OF_INCOME_MAX_LENGTH = 255;
    public static final int CITIZENSHIP_MAX_LENGTH = 40;
    public static final int SIN_MAX_LENGTH = 15;
    public static final int ROSTER_TERMINATION_REASON_MAX_LENGTH = 2;
    public static final int REFERRING_DOCTOR_INPUT_MAX_LENGTH = 40;
    public static final int REFERRING_DOCTOR_OHIP_INPUT_MAX_LENGTH = 6;

    private final static Pattern FD_LAST_NAME = Pattern.compile(".*<([fr])d>([^, ]*), .*</([fr])d>.*");
    private final static Pattern FD_FIRST_NAME = Pattern.compile(".*<([fr])d>[^, ]*, (.*)</([fr])d>.*");
    private final static Pattern FD_FULL_NAME = Pattern.compile(".*<([fr])d>(.*)</([fr])d>.*");
    private final static Pattern FD_OHIP = Pattern.compile("<([fr])dohip>(.*)</[fr]dohip>.*");


    private int hashCode = Integer.MIN_VALUE; // primary key
    private Integer demographicNo; // fields
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
    private String familyPhysician;

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
    private DemographicExt[] extras;
    private String alias;
    private String previousAddress;
    private String children;
    private String sourceOfIncome;
    private String citizenship;
    private String sin;
    private Integer headRecord = null;
    private Set<Integer> subRecord = null;
    private String anonymous = null;
    private String spokenLanguage;

    private int activeCount = 0;
    private int hsAlertCount = 0;
    private String displayName = null;

    private Provider provider;
    private String lastUpdateUser = null;
    private Date lastUpdateDate = new Date();

    private String title;
    private String officialLanguage;

    private String countryOfOrigin;
    private String newsletter;

    private String cellPhone;
    private String phoneComment;

    private String middleNames;
    private String rosterEnrolledTo;

    private String residentialAddress;
    private String residentialCity;
    private String residentialProvince;
    private String residentialPostal;
    private String gender;
    private String pronoun;
    private Integer genderId;
    private Integer pronounId;
    private String patientType;

    private String prefName;

    private Provider mrp;

    private String nextAppointment;

    public enum PatientStatus {
        AC, IN, DE, IC, ID, MO, FI
    }

    /**
     * @deprecated default for birth day should be null
     */
    @Deprecated
    public static Demographic create(String firstName, String lastName, String gender, String monthOfBirth, String dateOfBirth, String yearOfBirth, String hin, String ver, boolean applyDefaultBirthDate) {
        return (create(firstName, lastName, gender, monthOfBirth, dateOfBirth, yearOfBirth, hin, ver));
    }

    /**
     * @param firstName
     * @param lastName
     * @param gender
     * @param monthOfBirth
     * @param dateOfBirth
     * @param yearOfBirth
     * @param hin
     * @param ver
     * @return Demographic
     */
    public static Demographic create(String firstName, String lastName, String gender, String monthOfBirth, String dateOfBirth, String yearOfBirth, String hin, String ver) {
        Demographic demographic = new Demographic();

        demographic.setFirstName(firstName);
        demographic.setLastName(lastName);
        demographic.setMonthOfBirth(monthOfBirth);
        demographic.setDateOfBirth(dateOfBirth);
        demographic.setYearOfBirth(yearOfBirth);
        demographic.setHin(hin);
        demographic.setVer(ver);
        demographic.setHcType(DEFAULT_HEATH_CARD_TYPE);
        demographic.setPatientStatus(DEFAULT_PATIENT_STATUS);
        demographic.setPatientStatusDate(new Date());
        demographic.setSex(gender == null || gender.length() == 0 ? DEFAULT_SEX : gender.substring(0, 1).toUpperCase());
        demographic.setDateJoined(new Date());
        demographic.setEndDate(DateTimeFormatUtils.getDateFromString(DEFAULT_FUTURE_DATE));

        return demographic;
    }

    // constructors
    public Demographic() {
        initialize();
    }

    // FindSecBugs BEAN_PROPERTY_INJECTION: Spring BeanUtils.copyProperties copies fixed JavaBean
    // descriptors between known CARLOS types; no user-controlled property name reaches the sink.
    @SuppressFBWarnings(value = "BEAN_PROPERTY_INJECTION",
            justification = "Spring BeanUtils.copyProperties copies fixed JavaBean descriptors between " +
                    "known CARLOS types; no user-controlled property name reaches the sink")
    public Demographic(Demographic d) {
        try {
            BeanUtils.copyProperties(d, this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructor for primary key
     */
    public Demographic(Integer demographicNo) {
        this.setDemographicNo(demographicNo);
        initialize();
    }

    /**
     * Constructor for required fields
     */
    public Demographic(Integer demographicNo, String firstName, String lastName) {

        this.setDemographicNo(demographicNo);
        this.setFirstName(firstName);
        this.setLastName(lastName);
        initialize();
    }

    /**
     * Validates string field lengths against the configured demographic database
     * column sizes.
     *
     * @return List<String> validation errors for fields that exceed the maximum
     *         allowed length
     */
    public List<String> validateFieldLengths() {
        List<String> errors = new ArrayList<>();

        addFieldLengthError(errors, "Last name", lastName, LAST_NAME_MAX_LENGTH);
        addFieldLengthError(errors, "First name", firstName, FIRST_NAME_MAX_LENGTH);
        addFieldLengthError(errors, "Middle names", middleNames, MIDDLE_NAMES_MAX_LENGTH);
        addFieldLengthError(errors, "Preferred name", alias, ALIAS_MAX_LENGTH);
        addFieldLengthError(errors, "Address", address, ADDRESS_MAX_LENGTH);
        addFieldLengthError(errors, "City", city, CITY_MAX_LENGTH);
        addFieldLengthError(errors, "Province", province, PROVINCE_MAX_LENGTH);
        addFieldLengthError(errors, "Postal code", postal, POSTAL_MAX_LENGTH);
        addFieldLengthError(errors, "Residential address", residentialAddress, RESIDENTIAL_ADDRESS_MAX_LENGTH);
        addFieldLengthError(errors, "Residential city", residentialCity, RESIDENTIAL_CITY_MAX_LENGTH);
        addFieldLengthError(errors, "Residential province", residentialProvince, RESIDENTIAL_PROVINCE_MAX_LENGTH);
        addFieldLengthError(errors, "Residential postal code", residentialPostal, RESIDENTIAL_POSTAL_MAX_LENGTH);
        addFieldLengthError(errors, "Phone", phone, PHONE_MAX_LENGTH);
        addFieldLengthError(errors, "Alternate phone", phone2, PHONE_MAX_LENGTH);
        addFieldLengthError(errors, "Email", email, EMAIL_MAX_LENGTH);
        addFieldLengthError(errors, "Sex", sex, SEX_MAX_LENGTH);
        addFieldLengthError(errors, "Month of birth", monthOfBirth, MONTH_OF_BIRTH_MAX_LENGTH);
        addFieldLengthError(errors, "Date of birth", dateOfBirth, DATE_OF_BIRTH_MAX_LENGTH);
        addFieldLengthError(errors, "Year of birth", yearOfBirth, YEAR_OF_BIRTH_MAX_LENGTH);
        addFieldLengthError(errors, "Health card number", hin, HIN_MAX_LENGTH);
        addFieldLengthError(errors, "Version code", ver, VER_MAX_LENGTH);
        addFieldLengthError(errors, "Roster status", rosterStatus, ROSTER_STATUS_MAX_LENGTH);
        addFieldLengthError(errors, "Roster enrolled to", rosterEnrolledTo, ROSTER_ENROLLED_TO_MAX_LENGTH);
        addFieldLengthError(errors, "Patient status", patientStatus, PATIENT_STATUS_MAX_LENGTH);
        addFieldLengthError(errors, "Chart number", chartNo, CHART_NO_MAX_LENGTH);
        addFieldLengthError(errors, "Provider number", providerNo, PROVIDER_NO_MAX_LENGTH);
        addFieldLengthError(errors, "PCN indicator", pcnIndicator, PCN_INDICATOR_MAX_LENGTH);
        addFieldLengthError(errors, "Health card type", hcType, HC_TYPE_MAX_LENGTH);
        addFieldLengthError(errors, "Referring doctor", familyDoctor, FAMILY_DOCTOR_MAX_LENGTH);
        addFieldLengthError(errors, "Previous address", previousAddress, PREVIOUS_ADDRESS_MAX_LENGTH);
        addFieldLengthError(errors, "Children", children, CHILDREN_MAX_LENGTH);
        addFieldLengthError(errors, "Source of income", sourceOfIncome, SOURCE_OF_INCOME_MAX_LENGTH);
        addFieldLengthError(errors, "Citizenship", citizenship, CITIZENSHIP_MAX_LENGTH);
        addFieldLengthError(errors, "SIN", sin, SIN_MAX_LENGTH);
        addFieldLengthError(errors, "Roster termination reason", rosterTerminationReason, ROSTER_TERMINATION_REASON_MAX_LENGTH);

        return errors;
    }

    private static void addFieldLengthError(List<String> errors, String fieldName, String value, int maxLength) {
        if (StringUtils.length(value) > maxLength) {
            String unit = maxLength == 1 ? "character" : "characters";
            errors.add(fieldName + " exceeds maximum length of " + maxLength + " " + unit + ".");
        }
    }
    @jakarta.persistence.Transient

    public String getDisplayName() {
        if (displayName == null) {
            displayName = getLastName() + ", " + getFirstName();
            return displayName;
        } else return displayName;
    }

    /**
     * Return the unique identifier of this class
     */
    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @jakarta.persistence.Column(name = "demographic_no")
    public Integer getDemographicNo() {
        return demographicNo;
    }

    /**
     * Set the unique identifier of this class
     *
     * @param demographicNo the new ID
     */
    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
        this.hashCode = Integer.MIN_VALUE;
    }
    @jakarta.persistence.Column(name = "title")

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    @jakarta.persistence.Column(name = "official_lang")

    public String getOfficialLanguage() {
        return officialLanguage;
    }

    public void setOfficialLanguage(String officialLanguage) {
        this.officialLanguage = officialLanguage;
    }
    @jakarta.persistence.Column(name = "lastUpdateUser")

    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.TIMESTAMP)

    @jakarta.persistence.Column(name = "lastUpdateDate")

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    /**
     * @return the rosterDate
     */
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.DATE)
    @jakarta.persistence.Column(name = "roster_date")
    public Date getRosterDate() {
        return rosterDate;
    }

    /**
     * @param rosterDate the rosterDate to set
     */
    public void setRosterDate(Date rosterDate) {
        this.rosterDate = rosterDate;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.DATE)

    @jakarta.persistence.Column(name = "roster_termination_date")

    public Date getRosterTerminationDate() {
        return rosterTerminationDate;
    }

    public void setRosterTerminationDate(Date rosterTermDate) {
        this.rosterTerminationDate = rosterTermDate;
    }
    @jakarta.persistence.Column(name = "roster_termination_reason", length = 2)

    public String getRosterTerminationReason() {
        return rosterTerminationReason;
    }

    public void setRosterTerminationReason(String rosterTermReason) {
        this.rosterTerminationReason = rosterTermReason;
    }


    /**
     * Return the value associated with the column: phone
     */
    @jakarta.persistence.Column(name = "phone", length = 20)
    public String getPhone() {
        if (phone == null) {
            return "";
        }
        return phone;
    }

    /**
     * Set the value related to the column: phone
     *
     * @param phone the phone value
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Return the value associated with the column: patient_status
     */
    @jakarta.persistence.Column(name = "patient_status", length = 20)
    public String getPatientStatus() {
        if (patientStatus == null) {
            return "";
        }
        return patientStatus;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.DATE)

    @jakarta.persistence.Column(name = "patient_status_date")

    public Date getPatientStatusDate() {
        return patientStatusDate;
    }

    /**
     * Set the value related to the column: patient_status
     *
     * @param patientStatus the patient_status value
     */
    public void setPatientStatus(String patientStatus) {
        this.patientStatus = patientStatus;
    }

    public void setPatientStatusDate(Date patientStatusDate) {
        this.patientStatusDate = patientStatusDate;
    }

    /**
     * Return the value associated with the column: roster_status
     */
    @jakarta.persistence.Column(name = "roster_status", length = 20)
    public String getRosterStatus() {
        if (rosterStatus == null) {
            return "";
        }
        return rosterStatus;
    }

    /**
     * Set the value related to the column: roster_status
     *
     * @param rosterStatus the roster_status value
     */
    public void setRosterStatus(String rosterStatus) {
        this.rosterStatus = rosterStatus;
    }

    /**
     * Return the value associated with the column: provider_no
     */
    @jakarta.persistence.Column(name = "provider_no", length = 250)
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Set the value related to the column: provider_no
     *
     * @param providerNo the provider_no value
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }




    /**
     * Return the value associated with the column: hin
     */
    @jakarta.persistence.Column(name = "hin", length = 20)
    public String getHin() {
        return hin;
    }

    /**
     * Set the value related to the column: hin
     *
     * @param hin the hin value
     */
    public void setHin(String hin) {
        this.hin = hin;
    }

    /**
     * Return the value associated with the column: address
     */
    @jakarta.persistence.Column(name = "address", length = 60)
    public String getAddress() {
        return address;
    }

    /**
     * Set the value related to the column: address
     *
     * @param address the address value
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Return the value associated with the column: province
     */
    @jakarta.persistence.Column(name = "province", length = 20)
    public String getProvince() {
        return province;
    }

    /**
     * Set the value related to the column: province
     *
     * @param province the province value
     */
    public void setProvince(String province) {
        province = StringUtils.trimToNull(province);

        if (province != null) province = province.toUpperCase();

        this.province = province;
    }

    /**
     * Return the value associated with the column: month_of_birth
     */
    @jakarta.persistence.Column(name = "month_of_birth", length = 2)
    public String getMonthOfBirth() {
        return monthOfBirth;
    }

    /**
     * Set the value related to the column: month_of_birth
     *
     * @param monthOfBirth the month_of_birth value
     */
    public void setMonthOfBirth(String monthOfBirth) {
        this.monthOfBirth = StringUtils.trimToNull(monthOfBirth);
    }

    /**
     * Return the value associated with the column: ver
     */
    @jakarta.persistence.Column(name = "ver", length = 3)
    public String getVer() {
        return ver;
    }

    /**
     * Set the value related to the column: ver
     *
     * @param ver the ver value
     */
    public void setVer(String ver) {
        this.ver = ver;
    }

    /**
     * Return the value associated with the column: date_of_birth
     */
    @jakarta.persistence.Column(name = "date_of_birth", length = 2)
    public String getDateOfBirth() {
        return dateOfBirth;
    }

    /**
     * Set the value related to the column: date_of_birth
     *
     * @param dateOfBirth the date_of_birth value
     */
    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = StringUtils.trimToNull(dateOfBirth);
    }

    /**
     * Return the value associated with the column: sex
     */
    @jakarta.persistence.Column(name = "sex", length = 1, nullable = false)
    public String getSex() {
        return sex;
    }

    /**
     * Set the value related to the column: sex
     *
     * @param sex the sex value
     */
    public void setSex(String sex) {
        this.sex = sex;
    }

    /**
     * Return the value associated with the column: date_joined
     */
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.DATE)
    @jakarta.persistence.Column(name = "date_joined")
    public Date getDateJoined() {
        return dateJoined;
    }
    @jakarta.persistence.Transient

    public String getFormattedDateJoined() {
        Date d = getDateJoined();
        if (d != null) return (DateFormatUtils.ISO_DATE_FORMAT.format(d));
        else return ("");
    }

    /**
     * Set the value related to the column: date_joined
     *
     * @param dateJoined the date_joined value
     */
    public void setDateJoined(Date dateJoined) {
        this.dateJoined = dateJoined;
    }

    /**
     * Return the value associated with the column: family_doctor
     */
    @jakarta.persistence.Column(name = "family_doctor", length = 80)
    public String getFamilyDoctor() {
        if (StringUtils.isBlank(familyDoctor)) {
            this.familyDoctor = "";
        }
        return familyDoctor;
    }

    /**
     * Set the value related to the column: family_doctor
     *
     * @param familyDoctor the family_doctor value
     */
    public void setFamilyDoctor(String familyDoctor) {
        this.familyDoctor = familyDoctor;
    }

    /**
     * Set the value related to the column: family_physician
     *
     * @param familyPhysician the family_doctor value
     */
    public void setFamilyPhysician(String familyPhysician) {
        this.familyPhysician = familyPhysician;
    }

    /**
     * Return the value associated with the column: family_physician
     */
    @jakarta.persistence.Transient
    public String getFamilyPhysician() {
        return familyPhysician;
    }

    /**
     * Return the last name as parsed from column: family_doctor
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @jakarta.persistence.Transient
    public String getFamilyDoctorLastName() {
        String doctorName = "";
        Matcher m = FD_LAST_NAME.matcher(getFamilyDoctor());

        if (m.find()) {
            if (!"null".equalsIgnoreCase(m.group(2))) {
                doctorName = m.group(2);
            }
        }
        return doctorName;
    }

    /**
     * Return the first name as parsed from column: family_doctor
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @jakarta.persistence.Transient
    public String getFamilyDoctorFirstName() {
        String doctorName = "";
        Matcher m = FD_FIRST_NAME.matcher(getFamilyDoctor());

        if (m.find()) {
            if (!"null".equalsIgnoreCase(m.group(2))) {
                doctorName = m.group(2);
            }
        }
        return doctorName;
    }
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @jakarta.persistence.Transient

    public String getFamilyDoctorName() {
        String doctorName = "";

        if (!getFamilyDoctorLastName().isEmpty() && !getFamilyDoctorFirstName().isEmpty()) {
            doctorName = getFamilyDoctorLastName() + ", " + getFamilyDoctorFirstName();
        } else {
            Matcher m = FD_FULL_NAME.matcher(getFamilyDoctor());
            if (m.find() && !"null".equalsIgnoreCase(m.group(2))) {
                doctorName = m.group(2);
            }
        }
        return doctorName;
    }

    /**
     * Return the doctor number as parsed from column: family_doctor
     */
    @jakarta.persistence.Transient
    public String getFamilyDoctorNumber() {

        Matcher m = FD_OHIP.matcher(getFamilyDoctor());

        if (m.find()) {
            return m.group(2);
        }
        return "";
    }
    @jakarta.persistence.Transient

    // The family_physician column is frequently null. Coalesce to "" before matching
    // (mirroring getFamilyDoctor()'s null handling) so these transient parse getters
    // never NPE — e.g. when Jackson serializes a Demographic over the REST API.
    public String getFamilyPhysicianLastName() {
        Matcher m = FD_LAST_NAME.matcher(StringUtils.trimToEmpty(getFamilyPhysician()));
        if (m.find()) {
            return m.group(2);
        }
        return "";
    }
    @jakarta.persistence.Transient

    public String getFamilyPhysicianFirstName() {
        Matcher m = FD_FIRST_NAME.matcher(StringUtils.trimToEmpty(getFamilyPhysician()));
        if (m.find()) {
            return m.group(2);
        }
        return "";
    }
    @jakarta.persistence.Transient

    public String getFamilyPhysicianFullName() {
        Matcher m = FD_FULL_NAME.matcher(StringUtils.trimToEmpty(getFamilyPhysician()));
        if (m.find()) {
            return m.group(2);
        }
        return "";
    }
    @jakarta.persistence.Transient

    public String getFamilyPhysicianNumber() {
        Matcher m = FD_OHIP.matcher(StringUtils.trimToEmpty(getFamilyPhysician()));
        if (m.find()) {
            return m.group(2);
        }

        return "";
    }

    /**
     * Return the value associated with the column: city
     */
    @jakarta.persistence.Column(name = "city", length = 50)
    public String getCity() {
        return city;
    }

    /**
     * Set the value related to the column: city
     *
     * @param city the city value
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * Return the value associated with the column: first_name
     */
    @jakarta.persistence.Column(name = "first_name", length = 30, nullable = false)
    public String getFirstName() {
        return firstName;
    }

    /**
     * Gets demographic's full name.
     *
     * @return Returns the last name, first name pair.
     */
    @jakarta.persistence.Transient
    public String getFullName() {
        return getLastName() + ", " + getFirstName();
    }

    /**
     * Set the value related to the column: first_name
     *
     * @param firstName the first_name value
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Gets demographic's preferred name.
     *
     * @return Returns the preferred name.
     */
    @jakarta.persistence.Column(name = "pref_name")
    public String getPrefName() {
        if (getAlias() != null && !getAlias().isEmpty()) {
            return getAlias();
        }

        if (prefName == null) {
            return getAlias();
        }

        return prefName;
    }

    /**
     * Set the value related to the column: pref_name
     *
     * @param prefName the pref_name value
     */
    public void setPrefName(String prefName) {
        this.prefName = prefName;
    }

    /**
     * Return the value associated with the column: postal
     */
    @jakarta.persistence.Column(name = "postal", length = 9)
    public String getPostal() {
        return postal;
    }

    /**
     * Set the value related to the column: postal
     *
     * @param postal the postal value
     */
    public void setPostal(String postal) {
        this.postal = postal;
    }

    /**
     * Return the value associated with the column: hc_renew_date
     */
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.DATE)
    @jakarta.persistence.Column(name = "hc_renew_date")
    public Date getHcRenewDate() {
        return hcRenewDate;
    }

    /**
     * Set the value related to the column: hc_renew_date
     *
     * @param hcRenewDate the hc_renew_date value
     */
    public void setHcRenewDate(Date hcRenewDate) {
        this.hcRenewDate = hcRenewDate;
    }

    /**
     * Return the value associated with the column: phone2
     */
    @jakarta.persistence.Column(name = "phone2", length = 20)
    public String getPhone2() {
        if (phone2 == null) {
            return "";
        }
        return phone2;
    }

    /**
     * Set the value related to the column: phone2
     *
     * @param phone2 the phone2 value
     */
    public void setPhone2(String phone2) {
        this.phone2 = phone2;
    }

    /**
     * Return the value associated with the column: pcn_indicator
     */
    @jakarta.persistence.Column(name = "pcn_indicator", length = 20)
    public String getPcnIndicator() {
        return pcnIndicator;
    }

    /**
     * Set the value related to the column: pcn_indicator
     *
     * @param pcnIndicator the pcn_indicator value
     */
    public void setPcnIndicator(String pcnIndicator) {
        this.pcnIndicator = pcnIndicator;
    }

    /**
     * Return the value associated with the column: end_date
     */
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.DATE)
    @jakarta.persistence.Column(name = "end_date")
    public Date getEndDate() {
        return endDate;
    }
    @jakarta.persistence.Transient

    public String getFormattedEndDate() {
        Date d = getEndDate();
        if (d != null) return (DateFormatUtils.ISO_DATE_FORMAT.format(d));
        else return ("");
    }

    /**
     * Set the value related to the column: end_date
     *
     * @param endDate the end_date value
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Return the value associated with the column: last_name
     */
    @jakarta.persistence.Column(name = "last_name", length = 30, nullable = false)
    public String getLastName() {
        return lastName;
    }

    /**
     * Set the value related to the column: last_name
     *
     * @param lastName the last_name value
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Return the value associated with the column: hc_type
     */
    @jakarta.persistence.Column(name = "hc_type", length = 20)
    public String getHcType() {
        return hcType;
    }

    /**
     * Set the value related to the column: hc_type
     *
     * @param hcType the hc_type value
     */
    public void setHcType(String hcType) {
        this.hcType = hcType;
    }

    /**
     * Return the value associated with the column: chart_no
     */
    @jakarta.persistence.Column(name = "chart_no", length = 10)
    public String getChartNo() {
        if (chartNo == null) {
            return "";
        }
        return chartNo;
    }

    /**
     * Set the value related to the column: chart_no
     *
     * @param chartNo the chart_no value
     */
    public void setChartNo(String chartNo) {
        this.chartNo = chartNo;
    }

    /**
     * Return the value associated with the column: email
     */
    @jakarta.persistence.Column(name = "email", length = 100)
    public String getEmail() {
        return email;
    }

    /**
     * Set the value related to the column: email
     *
     * @param email the email value
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Return the value associated with the column: year_of_birth
     */
    @jakarta.persistence.Column(name = "year_of_birth", length = 4)
    public String getYearOfBirth() {
        return yearOfBirth;
    }

    /**
     * Set the value related to the column: year_of_birth
     *
     * @param yearOfBirth the year_of_birth value
     */
    public void setYearOfBirth(String yearOfBirth) {
        this.yearOfBirth = StringUtils.trimToNull(yearOfBirth);
    }

    /**
     * Return the value associated with the column: eff_date
     */
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.DATE)
    @jakarta.persistence.Column(name = "eff_date")
    public Date getEffDate() {
        return effDate;
    }
    @jakarta.persistence.Transient

    public String getFormattedEffDate() {
        Date d = getEffDate();
        if (d != null) return (DateFormatUtils.ISO_DATE_FORMAT.format(d));
        else return ("");
    }
    @jakarta.persistence.Column(name = "anonymous")

    public String getAnonymous() {
        return anonymous;
    }

    /**
     * @param anonymous can be any string indicating it's anonymisity (if that's a word), null means it's not anonymous.
     */
    public void setAnonymous(String anonymous) {
        this.anonymous = anonymous;
    }

    public void setFormattedEffDate(String formattedDate) {
        if (StringUtils.isBlank(formattedDate))
            return;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date d = sdf.parse(formattedDate);
            this.setEffDate(d);
        } catch (ParseException e) {
            MiscUtils.getLogger().error("Error", e);
        }

    }
    @jakarta.persistence.Transient

    public String getFormattedRenewDate() {
        Date d = getHcRenewDate();
        if (d != null) return (DateFormatUtils.ISO_DATE_FORMAT.format(d));
        else return ("");
    }

    public void setFormattedRenewDate(String formattedDate) {
        if (StringUtils.isBlank(formattedDate))
            return;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date d = sdf.parse(formattedDate);
            this.setHcRenewDate(d);
        } catch (ParseException e) {
            MiscUtils.getLogger().error("Error", e);
        }

    }

    /**
     * Set the value related to the column: eff_date
     *
     * @param effDate the eff_date value
     */
    public void setEffDate(Date effDate) {
        this.effDate = effDate;
    }
    @jakarta.persistence.Column(name = "alias", length = 70)

    public String getAlias() {
        if (alias == null) {
            return "";
        }
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
    @jakarta.persistence.Column(name = "children", length = 255)

    public String getChildren() {
        return children;
    }

    public void setChildren(String children) {
        this.children = children;
    }
    @jakarta.persistence.Column(name = "citizenship", length = 40)

    public String getCitizenship() {
        return citizenship;
    }

    public void setCitizenship(String citizenship) {
        this.citizenship = citizenship;
    }
    @jakarta.persistence.Column(name = "previousAddress", length = 255)

    public String getPreviousAddress() {
        return previousAddress;
    }

    public void setPreviousAddress(String previousAddress) {
        this.previousAddress = previousAddress;
    }
    @jakarta.persistence.Column(name = "sin", length = 15)

    public String getSin() {
        return sin;
    }

    public void setSin(String sin) {
        this.sin = sin;
    }
    @jakarta.persistence.Column(name = "sourceOfIncome", length = 255)

    public String getSourceOfIncome() {
        return sourceOfIncome;
    }

    public void setSourceOfIncome(String sourceOfIncome) {
        this.sourceOfIncome = sourceOfIncome;
    }
    @jakarta.persistence.Transient

    public String getCellPhone() {
        if (this.cellPhone == null) {
            this.cellPhone = getExtraValue(DemographicExt.DemographicProperty.demo_cell);
        }
        return this.cellPhone;
    }

    public void setCellPhone(String cellPhone) {
        this.cellPhone = cellPhone;
    }
    @jakarta.persistence.Transient

    public String getPhoneComment() {
        if (this.phoneComment == null) {
            this.phoneComment = getExtraValue(DemographicExt.DemographicProperty.phoneComment);
        }
        return phoneComment;
    }

    public void setPhoneComment(String phoneComment) {
        this.phoneComment = phoneComment;
    }
    @jakarta.persistence.Column(name = "gender")

    public String getGender() {
        if (gender == null) {
            return "";
        }
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
    @jakarta.persistence.Column(name = "pronoun")

    public String getPronoun() {
        if (pronoun == null) {
            return "";
        }
        return pronoun;
    }

    public void setPronoun(String pronoun) {
        this.pronoun = pronoun;
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj) return false;
        if (!(obj instanceof Demographic)) return false;
        else {
            Demographic demographic = (Demographic) obj;
            if (null == this.getDemographicNo() || null == demographic.getDemographicNo()) return false;
            else return (this.getDemographicNo().equals(demographic.getDemographicNo()));
        }
    }

    @Override
    public int hashCode() {
        if (Integer.MIN_VALUE == this.hashCode) {
            if (null == this.getDemographicNo()) {
                // do nothing, warn everyone.
                MiscUtils.getLogger().warn(OBJECT_NOT_YET_PERISTED, new Exception());
            } else {
                String hashStr = this.getClass().getName() + ":" + this.getDemographicNo().hashCode();
                this.hashCode = hashStr.hashCode();
            }
        }
        return this.hashCode;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    protected void initialize() {
        links = StringUtils.EMPTY;
    }

    public String addZero(String text, int num) {
        text = text.trim();

        for (int i = text.length(); i < num; i++) {
            text = "0" + text;
        }

        return text;
    }
    @jakarta.persistence.Transient

    public String getAge() {
        return (String.valueOf(Utility.calcAge(Utility.convertToReplaceStrIfEmptyStr(getYearOfBirth(), DEFAULT_YEAR), Utility.convertToReplaceStrIfEmptyStr(getMonthOfBirth(), DEFAULT_MONTH), Utility.convertToReplaceStrIfEmptyStr(getDateOfBirth(), DEFAULT_DATE))));
    }

    public String getAgeAsOf(Date asofDate) {
        return getAgeAsOf(asofDate, null);
    }
    
    public String getAgeAsOf(Date asofDate, Locale locale) {
        return Utility.calcAgeAtDate(
            Utility.calcDate(
                Utility.convertToReplaceStrIfEmptyStr(
                    getYearOfBirth(), DEFAULT_YEAR),
                Utility.convertToReplaceStrIfEmptyStr(
                    getMonthOfBirth(), DEFAULT_MONTH),
                Utility.convertToReplaceStrIfEmptyStr(
                    getDateOfBirth(), DEFAULT_DATE)),
            asofDate,
            locale);
    }
    @jakarta.persistence.Transient

    public String getSubjectPronoun() {
        if ("M".equals(sex)) {
            return "he";
        } else if ("F".equals(sex)) {
            return "she";
        } else {
            return "they";
        }
    }
    @jakarta.persistence.Transient

    public String getPossessivePronoun() {
        if ("M".equals(sex)) {
            return "his";
        } else if ("F".equals(sex)) {
            return "her";
        } else {
            return "their";
        }
    }
    @jakarta.persistence.Transient

    public int getAgeInYears() {
        return Utility.getNumYears(Utility.calcDate(Utility.convertToReplaceStrIfEmptyStr(getYearOfBirth(), DEFAULT_YEAR), Utility.convertToReplaceStrIfEmptyStr(getMonthOfBirth(), DEFAULT_MONTH), Utility.convertToReplaceStrIfEmptyStr(getDateOfBirth(), DEFAULT_DATE)), Calendar.getInstance().getTime());
    }

    public int getAgeInYearsAsOf(Date asofDate) {
        return Utility.getNumYears(Utility.calcDate(Utility.convertToReplaceStrIfEmptyStr(getYearOfBirth(), DEFAULT_YEAR), Utility.convertToReplaceStrIfEmptyStr(getMonthOfBirth(), DEFAULT_MONTH), Utility.convertToReplaceStrIfEmptyStr(getDateOfBirth(), DEFAULT_DATE)), asofDate);
    }
    @jakarta.persistence.Transient

    public DemographicExt[] getExtras() {
        return extras;
    }

    /**
     * Fetch a specific demographic extra object matching the given key.
     *
     * @param key
     * @return
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public DemographicExt getExtra(DemographicExt.DemographicProperty key) {
        List<DemographicExt> demographicExtList = new ArrayList<>();
        DemographicExt demographicExtResult = null;
        if (this.extras != null) {
            for (DemographicExt demographicExt : this.extras) {
                if (key.name().equalsIgnoreCase(demographicExt.getKey())) {
                    demographicExtList.add(demographicExt);
                }
            }
        }
        /*
         * Only return the first (hopefully the only) result for now.
         */
        if (!demographicExtList.isEmpty()) {
            demographicExtResult = demographicExtList.get(0);
        }
        return demographicExtResult;
    }

    /**
     * Fetch a specific extra value matching the given key.
     *
     * @param key
     * @return
     */
    public String getExtraValue(DemographicExt.DemographicProperty key) {
        DemographicExt demographicExt = getExtra(key);
        String extraValue = "";
        if (demographicExt != null) {
            extraValue = demographicExt.getValue();
        }
        return extraValue;
    }
    @jakarta.persistence.Transient

    public String getFormattedDob() {
        Calendar cal = getBirthDay();
        if (cal != null) return (DateFormatUtils.ISO_DATE_FORMAT.format(cal));
        else return ("");
    }

    public void setFormattedDob(String formattedDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date d = sdf.parse(formattedDate);
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            this.setBirthDay(cal);
        } catch (ParseException e) {
            MiscUtils.getLogger().error("Error", e);
        }

    }
    @jakarta.persistence.Transient

    public String getFormattedLinks() {
        StringBuilder response = new StringBuilder();

        if (getNumLinks() > 0) {
            String[] links = getLinks().split(",");
            for (int x = 0; x < links.length; x++) {
                if (response.length() > 0) {
                    response.append(",");
                }
            }
        }

        return response.toString();
    }
    @jakarta.persistence.Transient

    public String getFormattedName() {
        StringBuilder stringBuilder = new StringBuilder(getLastName() + ", " + getFirstName());
        if (getAlias() != null && !getAlias().isEmpty()) {
            stringBuilder.append(" (");
            stringBuilder.append(getAlias());
            stringBuilder.append(")");
        }
        return stringBuilder.toString();
    }
    @jakarta.persistence.Transient

    public String getLinks() {
        return links;
    }
    @jakarta.persistence.Transient

    public int getNumLinks() {
        if (getLinks() == null) {
            return 0;
        }

        if (getLinks().equals("")) {
            return 0;
        }

        return getLinks().split(",").length;
    }

    public void setExtras(DemographicExt[] extras) {
        this.extras = extras;
    }

    public void setLinks(String links) {
        this.links = links;
    }
    @org.hibernate.annotations.Formula("(select d.merged_to from demographic_merged d where d.deleted = 0 and d.demographic_no = demographic_no)")

    public Integer getHeadRecord() {
        return headRecord;
    }

    public void setHeadRecord(Integer headRecord) {
        this.headRecord = headRecord;
    }
    @jakarta.persistence.Transient

    public Integer getCurrentRecord() {
        if (headRecord != null) return headRecord;
        return demographicNo;
    }
    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)

    @jakarta.persistence.CollectionTable(name = "demographic_merged", joinColumns = @jakarta.persistence.JoinColumn(name = "merged_to"))

    @jakarta.persistence.Column(name = "demographic_no")

    @org.hibernate.annotations.SQLRestriction("deleted = 0")

    public Set<Integer> getSubRecord() {
        return subRecord;
    }

    public void setSubRecord(Set<Integer> subRecord) {
        this.subRecord = subRecord;
    }
    @org.hibernate.annotations.Formula("(select lst.description from lst_gender lst where lst.code=sex)")

    public String getSexDesc() {
        if (sexDesc == null) {
            return "";
        }
        return sexDesc;
    }

    public void setSexDesc(String sexDesc) {
        this.sexDesc = sexDesc;
    }
    @jakarta.persistence.Transient

    public boolean isActive() {
        return activeCount > 0;
    }

    public boolean hasHsAlert() {
        return hsAlertCount > 0;
    }
    @org.hibernate.annotations.Formula("(select count(*) from admission a where a.client_id=demographic_no and a.admission_status='current'                and a.program_id in (select p.id from program p where p.type='Service' ))")

    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
    }
    @org.hibernate.annotations.Formula("(select count(*) from health_safety h where h.demographic_no=demographic_no)")

    public int getHsAlertCount() {
        return hsAlertCount;
    }

    public void setHsAlertCount(int hsAlertCount) {
        this.hsAlertCount = hsAlertCount;
    }

    public void setBirthDay(Calendar cal) {
        if (cal == null) {
            dateOfBirth = monthOfBirth = yearOfBirth = null;
        } else {
            dateOfBirth = addZero(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), 2);
            monthOfBirth = addZero(String.valueOf(cal.get(Calendar.MONTH) + 1), 2);
            yearOfBirth = addZero(String.valueOf(cal.get(Calendar.YEAR)), 4);
        }
    }
    @jakarta.persistence.Transient

    public GregorianCalendar getBirthDay() {
        GregorianCalendar cal = null;

        if (dateOfBirth != null && monthOfBirth != null && yearOfBirth != null) {
            cal = new GregorianCalendar();
            cal.setTimeInMillis(0);
            cal.set(Integer.parseInt(yearOfBirth), Integer.parseInt(monthOfBirth) - 1, Integer.parseInt(dateOfBirth));

            // force materialisation of data
            cal.getTimeInMillis();
        }

        return (cal);
    }

    // Returns birthday in the format yyyy-mm-dd
    @jakarta.persistence.Transient
    public String getBirthDayAsString() {
        return getYearOfBirth() + "-" + getMonthOfBirth() + "-" + getDateOfBirth();
    }
    @jakarta.persistence.Column(name = "spoken_lang")

    public String getSpokenLanguage() {
        return spokenLanguage;
    }

    public void setSpokenLanguage(String spokenLanguage) {
        this.spokenLanguage = spokenLanguage;
    }

    /**
     * @return the providers
     */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinColumn(name = "provider_no", insertable = false, updatable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    public Provider getProvider() {
        return provider;
    }

    /**
     * @param provider the providers to set
     */
    public void setProvider(Provider provider) {
        this.provider = provider;
    }
    @jakarta.persistence.Column(name = "country_of_origin")

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }
    @jakarta.persistence.Column(name = "newsletter")

    public String getNewsletter() {
        return newsletter;
    }

    public void setNewsletter(String newsletter) {
        this.newsletter = newsletter;
    }
    @jakarta.persistence.Column(name = "middleNames", length = 100)

    public String getMiddleNames() {
        return middleNames;
    }

    public void setMiddleNames(String middleNames) {
        this.middleNames = middleNames;
    }

    @jakarta.persistence.Column(name = "roster_enrolled_to", length = 20)


    public String getRosterEnrolledTo() {
        return rosterEnrolledTo;
    }

    public void setRosterEnrolledTo(String rosterEnrolledTo) {
        this.rosterEnrolledTo = rosterEnrolledTo;
    }
    @jakarta.persistence.Transient

    public String getLabel() {
        String label = getDisplayName() + "\n";
        List<String> addressLineValues = new ArrayList<String>();
        if (!StringUtils.isEmpty(getAddress())) {
            addressLineValues.add(getAddress());
        }
        if (!StringUtils.isEmpty(getCity())) {
            addressLineValues.add(getCity());
        }
        if (!StringUtils.isEmpty(getProvince())) {
            addressLineValues.add(getProvince());
        }
        if (!StringUtils.isEmpty(getPostal())) {
            addressLineValues.add(getPostal());
        }
        if (!addressLineValues.isEmpty()) {
            label += StringUtils.join(addressLineValues, ", ") + "\n";
        }
        label += "Tel: " + (!StringUtils.isEmpty(getPhone()) ? getPhone() + "(H)" : "") + (!StringUtils.isEmpty(getPhone2()) ? " " + getPhone2() + "(W)" : "") + "\n";
        label += StringUtils.trimToEmpty(getDateOfBirth()) + "/" + StringUtils.trimToEmpty(getMonthOfBirth()) + "/" + StringUtils.trimToEmpty(getYearOfBirth());
        label += "(" + getSex() + ")";
        label += " HIN:" + getHin() + getVer();
        return label;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    @jakarta.persistence.Transient

    public String getPatientType() {
        return patientType;
    }

    public void setPatientType(String patientType) {
        this.patientType = patientType;
    }
    @jakarta.persistence.Transient

    public Integer getGenderId() {
        return genderId;
    }

    public void setGenderId(Integer genderId) {
        this.genderId = genderId;
    }
    @jakarta.persistence.Transient

    public Integer getPronounId() {
        return pronounId;
    }

    public void setPronounId(Integer pronounId) {
        this.pronounId = pronounId;
    }

    public static final Comparator<Demographic> FormattedNameComparator = new Comparator<Demographic>() {
        @Override
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getFormattedName().compareToIgnoreCase(dm2.getFormattedName());
        }
    };
    public static final Comparator<Demographic> LastNameComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getLastName().compareTo(dm2.getLastName());
        }
    };
    public static final Comparator<Demographic> FirstNameComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getFirstName().compareTo(dm2.getFirstName());
        }
    };
    public static final Comparator<Demographic> LastAndFirstNameComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            int res = dm1.getLastName().compareToIgnoreCase(dm2.getLastName());
            if (res != 0) {
                return res;
            }
            return dm1.getFirstName().compareTo(dm2.getFirstName());
        }
    };
    public static final Comparator<Demographic> DemographicNoComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getDemographicNo().compareTo(dm2.getDemographicNo());
        }
    };
    public static final Comparator<Demographic> SexComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getSex().compareTo(dm2.getSex());
        }
    };
    public static final Comparator<Demographic> AgeComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getAge().compareTo(dm2.getAge());
        }
    };
    public static final Comparator<Demographic> DateOfBirthComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getBirthDayAsString().compareTo(dm2.getBirthDayAsString());
        }
    };
    public static final Comparator<Demographic> RosterStatusComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getRosterStatus().compareTo(dm2.getRosterStatus());
        }
    };
    public static final Comparator<Demographic> ChartNoComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getChartNo().compareTo(dm2.getChartNo());
        }
    };
    public static final Comparator<Demographic> ProviderNoComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getProviderNo().compareTo(dm2.getProviderNo());
        }
    };
    public static final Comparator<Demographic> PatientStatusComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getPatientStatus().compareTo(dm2.getPatientStatus());
        }
    };
    public static final Comparator<Demographic> PhoneComparator = new Comparator<Demographic>() {
        public int compare(Demographic dm1, Demographic dm2) {
            return dm1.getPhone().compareTo(dm2.getPhone());
        }
    };


    /**
     * Loads the oscarResources bundle for the given locale, falling back to English if the locale
     * has no matching bundle.
     */
    private static ResourceBundle getResourceBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle("oscarResources", locale);
        } catch (MissingResourceException e) {
            return ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);
        }
    }

    /**
     * Returns the localized string for {@code key} from {@code bundle}, or {@code fallback} when
     * the key is absent (guards against incomplete translations).
     */
    private static String getRes(ResourceBundle bundle, String key, String fallback) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }

    public String getStandardIdentificationHTML(String contextPath) {
        //TODO move this into the DemographicManager as a property modifier and wrap each item with setting preferences
        StringBuilder sb = new StringBuilder();
        ResourceBundle carlosRes = getResourceBundle(LocaleContextHolder.getLocale());

        sb.append("<div id='patient-label'>");
        sb.append("<div id='patient-full-name'>");
        String editHref = contextPath + "/demographic/DemographicEdit?demographic_no="
                + SafeEncode.forUriComponent(String.valueOf(getDemographicNo()));
        sb.append("<h1><a href='").append(SafeEncode.forHtmlAttribute(editHref));
        sb.append("' target='_blank'>");

        if (getTitle() != null && getTitle().length() > 0) {
            sb.append(SafeEncode.forHtmlContent(getTitle())).append(" ");
        }

        sb.append(SafeEncode.forHtmlContent(getFormattedName()));
        sb.append("</a></h1>");
        sb.append("</div>");

        //--> pronouns
        if (getPronoun() != null && !getPronoun().isEmpty()) {
            sb.append("<div id='patient-pronouns'>");
            sb.append("<div class='label'>");
            String pronouns = getRes(carlosRes, "demographic.demographicaddrecordhtm.formPronouns", "Pronouns");
            sb.append(pronouns);
            sb.append("</div>");
            sb.append(SafeEncode.forHtml(getPronoun()));
            sb.append("</div>");
        }

        //--> sex
        sb.append("<div id='patient-sex'>");
        sb.append("<div class='label'>");
        String sexLabel = getRes(carlosRes, "demographic.demographicaddrecordhtm.formSex", "Sex");
        sb.append(sexLabel);
        sb.append("</div>");
        sb.append(SafeEncode.forHtmlContent(getSex()));
        sb.append("</div>");

        //--> gender
        if (getGender() != null && !getGender().isEmpty()) {
            sb.append("<div id='patient-gender'>");
            sb.append("<div class='label'>");
            String genderLabel = getRes(carlosRes, "demographic.demographicaddrecordhtm.formGender", "Gender");
            sb.append(genderLabel);
            sb.append("</div>");
            sb.append(SafeEncode.forHtmlContent(getGender()));
            sb.append("</div>");
        }

        //--> Birthdate
        sb.append("<div id='patient-dob'>");
        sb.append("<div class='label'>");
        String dob = getRes(carlosRes, "demographic.demographicaddrecordhtm.formDOB", "DOB");
        sb.append(dob);
        sb.append("</div>");
        sb.append(SafeEncode.forHtmlContent(getBirthDayAsString()));
        sb.append("</div>");

        //--> age
        sb.append("<div id='patient-age'>");
        sb.append("<div class='label'>");
        String age = getRes(carlosRes, "global.age", "Age");
        sb.append(age);
        sb.append("</div>");
        sb.append(SafeEncode.forHtmlContent(getAgeAsOf(new Date())));
        sb.append("</div>");

        //--> Insurance number
        if (getHin() != null && getHin().length() > 0) {
            sb.append("<div id='patient-hin' class='copyable' onclick=\"copyToClip('")
                    .append(SafeEncode.forHtmlAttribute(SafeEncode.forJavaScript(getHin())))
                    .append("',this)\">");
            sb.append("<div class='label'>");
            String hinLabel = getRes(carlosRes, "demographic.patient.context.hin", "HIN");
            sb.append(hinLabel);
			sb.append(" (");
            sb.append(SafeEncode.forHtml(getHcType()));
			sb.append(")</div>");
			sb.append(SafeEncode.forHtml(getHin()));
			sb.append("&nbsp;");
			sb.append(SafeEncode.forHtml(getVer()));
            sb.append("</div>");
        }

        //--> phone
        if (getPhone() != null && !getPhone().isEmpty()) {
            sb.append("<div id='patient-phone' class='copyable' title='")
                    .append(SafeEncode.forHtmlAttribute(getPhoneComment()))
                    .append("' onclick=\"copyToClip('")
                    .append(SafeEncode.forHtmlAttribute(SafeEncode.forJavaScript(getPhone())))
                    .append("',this)\">");
            sb.append("<div class='label'>");
            String phoneLabel = getRes(carlosRes, "demographic.demographicaddrecordhtm.formPhone", "Phone");
            sb.append(phoneLabel);
            sb.append("</div>");
            sb.append(SafeEncode.forHtmlContent(getPhone()));
            sb.append("</div>");
        }

        //--> cell phone
        if (getCellPhone() != null && !getCellPhone().isEmpty()) {
            sb.append("<div id='patient-cell-phone' class='copyable' title='")
                    .append(SafeEncode.forHtmlAttribute(getPhoneComment()))
                    .append("' onclick=\"copyToClip('")
                    .append(SafeEncode.forHtmlAttribute(SafeEncode.forJavaScript(getCellPhone())))
                    .append("',this)\">");
            sb.append("<div class='label'>");
            String cell = getRes(carlosRes, "demographic.demographicaddrecordhtm.formPhoneCell", "Cell Phone");
            sb.append(cell);
            sb.append("</div>");
            sb.append(SafeEncode.forHtmlContent(getCellPhone()));
            sb.append("</div>");
        }

        //--> email
        if (getEmail() != null && !getEmail().isEmpty()) {
            sb.append("<div id='patient-email' class='copyable' onclick=\"copyToClip('")
                    .append(SafeEncode.forHtmlAttribute(SafeEncode.forJavaScript(getEmail())))
                    .append("',this)\">");
            sb.append("<div class='label'>");
            String emailLabel = getRes(carlosRes, "demographic.demographicaddrecordhtm.formEMail", "Email");
            sb.append(emailLabel);
            sb.append("</div>");
            sb.append(SafeEncode.forHtmlContent(getEmail()));
            sb.append("</div>");
        }

        //--> next appointment date
        sb.append("<div id='patient-next-appointment'>");
        sb.append("<div class='label'>");
        String apptHref = contextPath + "/demographic/DemographicApptHistory?demographic_no="
                + SafeEncode.forUriComponent(String.valueOf(getDemographicNo()))
                + "&orderby=appointment_date&dboperation=appt_history&limit1=0&limit2=25";
        sb.append("<a href=\"").append(SafeEncode.forHtmlAttribute(apptHref))
                .append("\" title='View Appointment History' target='_blank'>");
        String nAppt = getRes(carlosRes, "global.nextAppointment", "Next Appt.");
        sb.append(nAppt);
        sb.append("</a>");
        sb.append("</div>");
        String unknown = getRes(carlosRes, "demographic.demographicaddrecordhtm.formNewsLetter.optUnknown", "Unknown");
        if (getNextAppointment() != null && !getNextAppointment().isEmpty()) {
            sb.append(SafeEncode.forHtmlContent(getNextAppointment()));
        } else {
            sb.append(SafeEncode.forHtmlContent(unknown));
        }
        sb.append("</div>");

        //--> most responsible practitioner (last item, pushed to right via CSS)
        sb.append("<div id='patient-mrp'>");
        sb.append("<div class='label'>");
        String mrpLabel = getRes(carlosRes, "demographic.demographiceditdemographic.formMRP", "MRP");
        sb.append(mrpLabel);
        sb.append("</div>");
        Provider mrp = getMrp();
        if (mrp != null) {
            sb.append(SafeEncode.forHtmlContent(mrp.getFormattedName()));
        } else {
            sb.append(SafeEncode.forHtmlContent(unknown));
        }
        sb.append("</div>");

        sb.append("</div>");

        return sb.toString();
    }

    @Override
    @jakarta.persistence.Transient
    public Integer getId() {
        return this.getDemographicNo();
    }
    @jakarta.persistence.Transient

    public String getRosterStatusDisplay() {
        String rs = StringUtils.trimToNull(this.getRosterStatus());
        ResourceBundle carlosRes = getResourceBundle(LocaleContextHolder.getLocale());
        if (rs != null) {
            if ("RO".equals(rs)) {
                return getRes(carlosRes, "demographic.enrollementhistory.Rostered", "ROSTERED");
            }
            if ("TE".equals(rs)) {
                return getRes(carlosRes, "demographic.enrollementhistory.terminated", "TERMINATED");
            }
            if ("FS".equals(rs)) {
                return getRes(carlosRes, "demographic.enrollementhistory.feeforservice", "FEE FOR SERVICE");
            }
            return rs;
        } else {
            return "";
        }
    }
    @jakarta.persistence.Column(name = "residentialAddress", length = 60)

    public String getResidentialAddress() {
        return residentialAddress;
    }

    public void setResidentialAddress(String residentialAddress) {
        this.residentialAddress = residentialAddress;
    }
    @jakarta.persistence.Column(name = "residentialCity", length = 50)

    public String getResidentialCity() {
        return residentialCity;
    }

    public void setResidentialCity(String residentialCity) {
        this.residentialCity = residentialCity;
    }
    @jakarta.persistence.Column(name = "residentialProvince", length = 20)

    public String getResidentialProvince() {
        return residentialProvince;
    }

    public void setResidentialProvince(String residentialProvince) {
        this.residentialProvince = residentialProvince;
    }
    @jakarta.persistence.Column(name = "residentialPostal", length = 9)

    public String getResidentialPostal() {
        return residentialPostal;
    }

    public void setResidentialPostal(String residentialPostal) {
        this.residentialPostal = residentialPostal;
    }
    @jakarta.persistence.Column(name = "consentToUseEmailForCare")

    public Boolean getConsentToUseEmailForCare() {
        return consentToUseEmailForCare;
    }

    public void setConsentToUseEmailForCare(Boolean consentToUseEmailForCare) {
        this.consentToUseEmailForCare = consentToUseEmailForCare;
    }
    @jakarta.persistence.Transient

    public String getStandardIdentificationHtml() {
        StringBuilder sb = new StringBuilder();
        //name: <b>LAST, FIRST</b><br/>
        sb.append("<b>").append(SafeEncode.forHtml(getLastName().toUpperCase())).append("</b>").append(",");
        sb.append(SafeEncode.forHtml(getFirstName()));
        if (getTitle() != null && getTitle().length() > 0) {
            sb.append(" ").append("(").append(SafeEncode.forHtml(getTitle())).append(")");
        }
        sb.append("<br/>");
        // birthday: Born <b>DATE_OF_BIRTH</b>
        sb.append("Born ").append("<b>").append(SafeEncode.forHtml(getFormattedDob())).append("</b>");

        // hin: <br/>HC <b>HIN VER (TYPE)</b>
        if (getHin() != null && getHin().length() > 0) {
            sb.append("<br/>");
            sb.append("HC ")
                    .append("<b>")
                    .append(SafeEncode.forHtml(getHin())).append(" ").append(SafeEncode.forHtml(getVer()))
                    .append("(").append(SafeEncode.forHtml(getHcType())).append(")")
                    .append("</b>");
        }

        // chart number: <br/> Chart No <b>CHART_NO</b>
        if (getChartNo() != null && getChartNo().length() > 0) {
            sb.append("<br/>");
            sb.append("Chart No ").append("<b>").append(SafeEncode.forHtml(getChartNo())).append("</b>");
        }
        return sb.toString();
    }
    @jakarta.persistence.Transient

    public Provider getMrp() {
        return mrp;
    }

    public void setMrp(Provider mrp) {
        this.mrp = mrp;
    }
    @jakarta.persistence.Transient

    public String getNextAppointment() {
        return nextAppointment;
    }

    public void setNextAppointment(String nextAppointment) {
        this.nextAppointment = nextAppointment;
    }
}
