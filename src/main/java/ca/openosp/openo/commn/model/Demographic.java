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


package ca.openosp.openo.commn.model;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import ca.openosp.openo.PMmodule.utility.DateTimeFormatUtils;
import ca.openosp.openo.PMmodule.utility.Utility;
import ca.openosp.openo.utility.MiscUtils;
import org.owasp.encoder.Encode;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is the object class that relates to the demographic table. Any customizations belong here.
 */
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

    private final static Pattern FD_LAST_NAME = Pattern.compile(".*<([fr])d>([^,]*),.*</([fr])d>.*");
    private final static Pattern FD_FIRST_NAME = Pattern.compile(".*<([fr])d>[^,]*,(.*)</([fr])d>.*");
    private final static Pattern FD_FULL_NAME = Pattern.compile(".*<([fr])d>(.*)</([fr])d>.*");
    private final static Pattern FD_OHIP = Pattern.compile("<([fr])dohip>(.*)</[fr]dohip>.*");


    private int hashCode = Integer.MIN_VALUE;// primary key
    private Integer demographicNo;// fields
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

    public Demographic(Demographic d) {
        try {
            BeanUtils.copyProperties(this, d);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
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

    public String getDisplayName() {
        if (displayName == null) {
            displayName = getLastName() + ", " + getFirstName();
            return displayName;
        } else return displayName;
    }

    /**
     * Return the unique identifier of this class
     */
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOfficialLanguage() {
        return officialLanguage;
    }

    public void setOfficialLanguage(String officialLanguage) {
        this.officialLanguage = officialLanguage;
    }

    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    /**
     * @return the rosterDate
     */
    public Date getRosterDate() {
        return rosterDate;
    }

    /**
     * @param rosterDate the rosterDate to set
     */
    public void setRosterDate(Date rosterDate) {
        this.rosterDate = rosterDate;
    }

    public Date getRosterTerminationDate() {
        return rosterTerminationDate;
    }

    public void setRosterTerminationDate(Date rosterTermDate) {
        this.rosterTerminationDate = rosterTermDate;
    }

    public String getRosterTerminationReason() {
        return rosterTerminationReason;
    }

    public void setRosterTerminationReason(String rosterTermReason) {
        this.rosterTerminationReason = rosterTermReason;
    }


    /**
     * Return the value associated with the column: phone
     */
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
    public String getPatientStatus() {
        if (patientStatus == null) {
            return "";
        }
        return patientStatus;
    }

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
    public Date getDateJoined() {
        return dateJoined;
    }

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
    public String getFamilyPhysician() {
        return familyPhysician;
    }

    /**
     * Return the last name as parsed from column: family_doctor
     */
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
    public String getFamilyDoctorNumber() {

        Matcher m = FD_OHIP.matcher(getFamilyDoctor());

        if (m.find()) {
            return m.group(2);
        }
        return "";
    }

    public String getFamilyPhysicianLastName() {
        Matcher m = FD_LAST_NAME.matcher(getFamilyPhysician());
        if (m.find()) {
            return m.group(2);
        }
        return "";
    }

    public String getFamilyPhysicianFirstName() {
        Matcher m = FD_FIRST_NAME.matcher(getFamilyPhysician());
        if (m.find()) {
            return m.group(2);
        }
        return "";
    }

    public String getFamilyPhysicianFullName() {
        Matcher m = FD_FULL_NAME.matcher(getFamilyPhysician());
        if (m.find()) {
            return m.group(2);
        }
        return "";
    }

    public String getFamilyPhysicianNumber() {
        Matcher m = FD_OHIP.matcher(getFamilyPhysician());
        if (m.find()) {
            return m.group(2);
        }

        return "";
    }

    /**
     * Return the value associated with the column: city
     */
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
    public String getFirstName() {
        return firstName;
    }

    /**
     * Gets demographic's full name.
     *
     * @return Returns the last name, first name pair.
     */
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
    public Date getEndDate() {
        return endDate;
    }

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
    public Date getEffDate() {
        return effDate;
    }

    public String getFormattedEffDate() {
        Date d = getEffDate();
        if (d != null) return (DateFormatUtils.ISO_DATE_FORMAT.format(d));
        else return ("");
    }

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

    public String getAlias() {
        if (alias == null) {
            return "";
        }
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getChildren() {
        return children;
    }

    public void setChildren(String children) {
        this.children = children;
    }

    public String getCitizenship() {
        return citizenship;
    }

    public void setCitizenship(String citizenship) {
        this.citizenship = citizenship;
    }

    public String getPreviousAddress() {
        return previousAddress;
    }

    public void setPreviousAddress(String previousAddress) {
        this.previousAddress = previousAddress;
    }

    public String getSin() {
        return sin;
    }

    public void setSin(String sin) {
        this.sin = sin;
    }

    public String getSourceOfIncome() {
        return sourceOfIncome;
    }

    public void setSourceOfIncome(String sourceOfIncome) {
        this.sourceOfIncome = sourceOfIncome;
    }

    public String getCellPhone() {
        if (this.cellPhone == null) {
            this.cellPhone = getExtraValue(DemographicExt.DemographicProperty.demo_cell);
        }
        return this.cellPhone;
    }

    public void setCellPhone(String cellPhone) {
        this.cellPhone = cellPhone;
    }

    public String getPhoneComment() {
        if (this.phoneComment == null) {
            this.phoneComment = getExtraValue(DemographicExt.DemographicProperty.phoneComment);
        }
        return phoneComment;
    }

    public void setPhoneComment(String phoneComment) {
        this.phoneComment = phoneComment;
    }

    public String getGender() {
        if (gender == null) {
            return "";
        }
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

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

    public String getAge() {
        return (String.valueOf(Utility.calcAge(Utility.convertToReplaceStrIfEmptyStr(getYearOfBirth(), DEFAULT_YEAR), Utility.convertToReplaceStrIfEmptyStr(getMonthOfBirth(), DEFAULT_MONTH), Utility.convertToReplaceStrIfEmptyStr(getDateOfBirth(), DEFAULT_DATE))));
    }

    public String getAgeAsOf(Date asofDate) {
        return Utility.calcAgeAtDate(Utility.calcDate(Utility.convertToReplaceStrIfEmptyStr(getYearOfBirth(), DEFAULT_YEAR), Utility.convertToReplaceStrIfEmptyStr(getMonthOfBirth(), DEFAULT_MONTH), Utility.convertToReplaceStrIfEmptyStr(getDateOfBirth(), DEFAULT_DATE)), asofDate);
    }

    public String getSubjectPronoun() {
        if ("M".equals(sex)) {
            return "he";
        } else if ("F".equals(sex)) {
            return "she";
        } else {
            return "they";
        }
    }

    public String getPossessivePronoun() {
        if ("M".equals(sex)) {
            return "his";
        } else if ("F".equals(sex)) {
            return "her";
        } else {
            return "their";
        }
    }

    public int getAgeInYears() {
        return Utility.getNumYears(Utility.calcDate(Utility.convertToReplaceStrIfEmptyStr(getYearOfBirth(), DEFAULT_YEAR), Utility.convertToReplaceStrIfEmptyStr(getMonthOfBirth(), DEFAULT_MONTH), Utility.convertToReplaceStrIfEmptyStr(getDateOfBirth(), DEFAULT_DATE)), Calendar.getInstance().getTime());
    }

    public int getAgeInYearsAsOf(Date asofDate) {
        return Utility.getNumYears(Utility.calcDate(Utility.convertToReplaceStrIfEmptyStr(getYearOfBirth(), DEFAULT_YEAR), Utility.convertToReplaceStrIfEmptyStr(getMonthOfBirth(), DEFAULT_MONTH), Utility.convertToReplaceStrIfEmptyStr(getDateOfBirth(), DEFAULT_DATE)), asofDate);
    }

    public DemographicExt[] getExtras() {
        return extras;
    }

    /**
     * Fetch a specific demographic extra object matching the given key.
     *
     * @param key
     * @return
     */
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

    public String getFormattedName() {
        StringBuilder stringBuilder = new StringBuilder(getLastName() + ", " + getFirstName());
        if (getAlias() != null && !getAlias().isEmpty()) {
            stringBuilder.append(" (");
            stringBuilder.append(getAlias());
            stringBuilder.append(")");
        }
        return stringBuilder.toString();
    }

    public String getLinks() {
        return links;
    }

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

    public Integer getHeadRecord() {
        return headRecord;
    }

    public void setHeadRecord(Integer headRecord) {
        this.headRecord = headRecord;
    }

    public Integer getCurrentRecord() {
        if (headRecord != null) return headRecord;
        return demographicNo;
    }

    public Set<Integer> getSubRecord() {
        return subRecord;
    }

    public void setSubRecord(Set<Integer> subRecord) {
        this.subRecord = subRecord;
    }

    public String getSexDesc() {
        if (sexDesc == null) {
            return "";
        }
        return sexDesc;
    }

    public void setSexDesc(String sexDesc) {
        this.sexDesc = sexDesc;
    }

    public boolean isActive() {
        return activeCount > 0;
    }

    public boolean hasHsAlert() {
        return hsAlertCount > 0;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
    }

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
    public String getBirthDayAsString() {
        return getYearOfBirth() + "-" + getMonthOfBirth() + "-" + getDateOfBirth();
    }

    public String getSpokenLanguage() {
        return spokenLanguage;
    }

    public void setSpokenLanguage(String spokenLanguage) {
        this.spokenLanguage = spokenLanguage;
    }

    /**
     * @return the providers
     */
    public Provider getProvider() {
        return provider;
    }

    /**
     * @param provider the providers to set
     */
    public void setProvider(Provider provider) {
        this.provider = provider;
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

    public String getPatientType() {
        return patientType;
    }

    public void setPatientType(String patientType) {
        this.patientType = patientType;
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


    public String getStandardIdentificationHTML(String contextPath) {
        //TODO move this into the DemographicManager as a property modifier and wrap each item with setting preferences
        StringBuilder sb = new StringBuilder();

        sb.append("<div id='patient-label'>");
        sb.append("<div id='patient-full-name'>");
        sb.append("<h1><a href='"+ contextPath + "/demographic/demographiccontrol.jsp?demographic_no=");
        sb.append(Encode.forHtml(getDemographicNo() + ""));
        sb.append("&displaymode=edit&dboperation=search_detail' target='_blank'>");

        if (getTitle() != null && getTitle().length() > 0) {
            sb.append(getTitle() + " ");
        }

        sb.append(Encode.forHtmlContent(getFormattedName()));
        sb.append("</a></h1>");
        sb.append("</div>");

        //--> pronouns
        if (getPronoun() != null && !getPronoun().isEmpty()) {
            sb.append("<div id='patient-pronouns'>");
            sb.append("<div class='label'>");
            sb.append("pronouns");
            sb.append("</div>");
            sb.append(Encode.forHtml(getPronoun()));
            sb.append("</div>");
        }

        //--> sex
        sb.append("<div id='patient-sex'>");
        sb.append("<div class='label'>");
        sb.append("sex");
        sb.append("</div>");
        sb.append(getSex());
        sb.append("</div>");

        //--> gender
        if (getGender() != null && !getGender().isEmpty()) {
            sb.append("<div id='patient-gender'>");
            sb.append("<div class='label'>");
            sb.append("gender");
            sb.append("</div>");
            sb.append(getGender());
            sb.append("</div>");
        }

        //--> Birthdate
        sb.append("<div id='patient-dob'>");
        sb.append("<div class='label'>");
        sb.append("dob");
        sb.append("</div>");
        sb.append(getBirthDayAsString());
        sb.append("</div>");

        //--> age
        sb.append("<div id='patient-age'>");
        sb.append("<div class='label'>");
        sb.append("age");
        sb.append("</div>");
        sb.append(getAgeAsOf(new Date()));
        sb.append("</div>");

        //--> Insurance number
        if (getHin() != null && getHin().length() > 0) {
            sb.append("<div id='patient-hin'>");
            sb.append("<div class='label'>");
			sb.append("hin (");
            sb.append(Encode.forHtml(getHcType()));
			sb.append(")</div>");
			sb.append(Encode.forHtml(getHin()));
			sb.append("&nbsp;");
			sb.append(Encode.forHtml(getVer()));
            sb.append("</div>");
        }

        //--> most responsible practitioner
        sb.append("<div id='patient-mrp'>");
        sb.append("<div class='label'>");
        sb.append("MRP");
        sb.append("</div>");
        Provider mrp = getMrp();
        if (mrp != null) {
            sb.append(Encode.forHtmlContent(mrp.getFormattedName()));
        } else {
            sb.append("Unknown");
        }
        sb.append("</div>");

        //--> phone
        if (getPhone() != null && !getPhone().isEmpty()) {
            sb.append("<div id='patient-phone' title='")
                    .append(getPhoneComment())
                    .append("' >");
            sb.append("<div class='label'>");
            sb.append("phone");
            sb.append("</div>");
            sb.append(Encode.forHtmlContent(getPhone()));
            sb.append("</div>");
        }

        //--> cell phone
        if (getCellPhone() != null && !getCellPhone().isEmpty()) {
            sb.append("<div id='patient-cell-phone' title='")
                    .append(getPhoneComment())
                    .append("' >");
            sb.append("<div class='label'>");
            sb.append("cell");
            sb.append("</div>");
            sb.append(Encode.forHtmlContent(getCellPhone()));
            sb.append("</div>");
        }

        //--> email
        if (getEmail() != null && !getEmail().isEmpty()) {
            sb.append("<div id='patient-email'>");
            sb.append("<div class='label'>");
            sb.append("email");
            sb.append("</div>");
            sb.append(Encode.forHtmlContent(getEmail()));
            sb.append("</div>");
        }

        //--> next appointment date
        sb.append("<div id='patient-next-appointment'>");
        sb.append("<div class='label'>");
        sb.append("<a href=\"" + contextPath + "/demographic/demographiccontrol.jsp?demographic_no=")
        .append(Encode.forHtml(getDemographicNo() + ""))
                .append("&amp;last_name=").append(Encode.forUriComponent(getLastName())).append("&amp;first_name=")
                .append(Encode.forUriComponent(getFirstName()))
                .append("&amp;orderby=appointment_date&amp;displaymode=appt_history&amp;dboperation=appt_history&amp;limit1=0&amp;limit2=25\" title='View Appointment History' target='_blank'>");
        sb.append("Next Appt.");
        sb.append("</a>");
        sb.append("</div>");
        if (getNextAppointment() != null && !getNextAppointment().isEmpty()) {
            sb.append(getNextAppointment());
        } else {
            sb.append("Unknown");
        }
        sb.append("</div>");

        sb.append("</div>");

        return sb.toString();
    }

    @Override
    public Integer getId() {
        return this.getDemographicNo();
    }

    public String getRosterStatusDisplay() {
        String rs = StringUtils.trimToNull(this.getRosterStatus());
        if (rs != null) {
            if ("RO".equals(rs)) {
                return "ROSTERED";
            }
            if ("TE".equals(rs)) {
                return "TERMINATED";
            }
            if ("FS".equals(rs)) {
                return "FEE FOR SERVICE";
            }
            return rs;
        } else {
            return "";
        }
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

    public Boolean getConsentToUseEmailForCare() {
        return consentToUseEmailForCare;
    }

    public void setConsentToUseEmailForCare(Boolean consentToUseEmailForCare) {
        this.consentToUseEmailForCare = consentToUseEmailForCare;
    }

    public String getStandardIdentificationHtml() {
        StringBuilder sb = new StringBuilder();
        //name: <b>LAST, FIRST</b><br/>
        sb.append("<b>").append(Encode.forHtml(getLastName().toUpperCase())).append("</b>").append(",");
        sb.append(getFirstName());
        if (getTitle() != null && getTitle().length() > 0) {
            sb.append(" ").append("(").append(getTitle()).append(")");
        }
        sb.append("<br/>");
        // birthday: Born <b>DATE_OF_BIRTH</b>
        sb.append("Born ").append("<b>").append(getFormattedDob()).append("</b>");

        // hin: <br/>HC <b>HIN VER (TYPE)</b>
        if (getHin() != null && getHin().length() > 0) {
            sb.append("<br/>");
            sb.append("HC ")
                    .append("<b>")
                    .append(getHin()).append(" ").append(getVer())
                    .append("(").append(getHcType()).append(")")
                    .append("</b>");
        }

        // chart number: <br/> Chart No <b>CHART_NO</b>
        if (getChartNo() != null && getChartNo().length() > 0) {
            sb.append("<br/>");
            sb.append("Chart No ").append("<b>").append(getChartNo()).append("</b>");
        }
        return sb.toString();
    }

    public Provider getMrp() {
        return mrp;
    }

    public void setMrp(Provider mrp) {
        this.mrp = mrp;
    }

    public String getNextAppointment() {
        return nextAppointment;
    }

    public void setNextAppointment(String nextAppointment) {
        this.nextAppointment = nextAppointment;
    }
}
