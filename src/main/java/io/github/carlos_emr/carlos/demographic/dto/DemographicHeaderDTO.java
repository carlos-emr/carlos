/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.demographic.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.Period;

/**
 * Lightweight data transfer object for encounter and chart page headers.
 * Optimized for JPQL constructor expression projection with a LEFT JOIN
 * to Provider for the MRP name.
 *
 * <p>PHI minimization: this DTO omits SIN, address, phone, and email
 * which are not displayed in encounter headers.</p>
 *
 * @since 2026-04-11
 */
public class DemographicHeaderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer demographicNo;
    private String lastName;
    private String firstName;
    private String sex;
    private String sexDesc;
    private String yearOfBirth;
    private String monthOfBirth;
    private String dateOfBirth;
    private String hin;
    private String ver;
    private String hcType;
    private String chartNo;
    private String patientStatus;
    private String rosterStatus;
    private String providerNo;
    private String providerLastName;
    private String providerFirstName;

    /**
     * Default constructor required by frameworks.
     */
    public DemographicHeaderDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions. Parameter order
     * must match the SELECT NEW clause exactly.
     *
     * @param demographicNo Integer the patient demographic number
     * @param lastName String the patient's last name
     * @param firstName String the patient's first name
     * @param sex String the patient's sex code
     * @param sexDesc String the sex description (from formula join)
     * @param yearOfBirth String the birth year (4 digits)
     * @param monthOfBirth String the birth month (2 digits)
     * @param dateOfBirth String the birth day (2 digits)
     * @param hin String the Health Insurance Number
     * @param ver String the HIN version code
     * @param hcType String the health card type (province)
     * @param chartNo String the chart number
     * @param patientStatus String the patient status code
     * @param rosterStatus String the roster status
     * @param providerNo String the most responsible provider number
     * @param providerLastName String the MRP's last name (from LEFT JOIN)
     * @param providerFirstName String the MRP's first name (from LEFT JOIN)
     */
    public DemographicHeaderDTO(Integer demographicNo, String lastName, String firstName,
                                String sex, String sexDesc,
                                String yearOfBirth, String monthOfBirth, String dateOfBirth,
                                String hin, String ver, String hcType,
                                String chartNo, String patientStatus, String rosterStatus,
                                String providerNo, String providerLastName, String providerFirstName) {
        this.demographicNo = demographicNo;
        this.lastName = lastName;
        this.firstName = firstName;
        this.sex = sex;
        this.sexDesc = sexDesc;
        this.yearOfBirth = yearOfBirth;
        this.monthOfBirth = monthOfBirth;
        this.dateOfBirth = dateOfBirth;
        this.hin = hin;
        this.ver = ver;
        this.hcType = hcType;
        this.chartNo = chartNo;
        this.patientStatus = patientStatus;
        this.rosterStatus = rosterStatus;
        this.providerNo = providerNo;
        this.providerLastName = providerLastName;
        this.providerFirstName = providerFirstName;
    }

    /**
     * Returns the patient's formatted name as "LastName, FirstName".
     *
     * @return String the formatted name
     */
    public String getFormattedName() {
        StringBuilder sb = new StringBuilder();
        if (lastName != null) sb.append(lastName);
        sb.append(", ");
        if (firstName != null) sb.append(firstName);
        return sb.toString();
    }

    /**
     * Returns the date of birth formatted as "YYYY-MM-DD", or empty string
     * if any component is missing.
     *
     * @return String the formatted date of birth
     */
    public String getFormattedDob() {
        if (yearOfBirth == null || monthOfBirth == null || dateOfBirth == null) {
            return "";
        }
        return yearOfBirth + "-" + monthOfBirth + "-" + dateOfBirth;
    }

    /**
     * Computes the patient's age in years based on the DOB components.
     *
     * @return String the age in years, or empty string if DOB is incomplete
     */
    public String getAge() {
        if (yearOfBirth == null || monthOfBirth == null || dateOfBirth == null) {
            return "";
        }
        try {
            LocalDate dob = LocalDate.of(
                    Integer.parseInt(yearOfBirth),
                    Integer.parseInt(monthOfBirth),
                    Integer.parseInt(dateOfBirth));
            return String.valueOf(Period.between(dob, LocalDate.now()).getYears());
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /**
     * Returns the MRP (Most Responsible Provider) formatted name as
     * "LastName, FirstName", or empty string if no provider is assigned.
     *
     * @return String the formatted provider name
     */
    public String getProviderName() {
        if (providerLastName == null && providerFirstName == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (providerLastName != null) sb.append(providerLastName.trim());
        if (providerFirstName != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(providerFirstName.trim());
        }
        return sb.toString();
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getSexDesc() {
        return sexDesc;
    }

    public void setSexDesc(String sexDesc) {
        this.sexDesc = sexDesc;
    }

    public String getYearOfBirth() {
        return yearOfBirth;
    }

    public void setYearOfBirth(String yearOfBirth) {
        this.yearOfBirth = yearOfBirth;
    }

    public String getMonthOfBirth() {
        return monthOfBirth;
    }

    public void setMonthOfBirth(String monthOfBirth) {
        this.monthOfBirth = monthOfBirth;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getHin() {
        return hin;
    }

    public void setHin(String hin) {
        this.hin = hin;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getHcType() {
        return hcType;
    }

    public void setHcType(String hcType) {
        this.hcType = hcType;
    }

    public String getChartNo() {
        return chartNo;
    }

    public void setChartNo(String chartNo) {
        this.chartNo = chartNo;
    }

    public String getPatientStatus() {
        return patientStatus;
    }

    public void setPatientStatus(String patientStatus) {
        this.patientStatus = patientStatus;
    }

    public String getRosterStatus() {
        return rosterStatus;
    }

    public void setRosterStatus(String rosterStatus) {
        this.rosterStatus = rosterStatus;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getProviderLastName() {
        return providerLastName;
    }

    public void setProviderLastName(String providerLastName) {
        this.providerLastName = providerLastName;
    }

    public String getProviderFirstName() {
        return providerFirstName;
    }

    public void setProviderFirstName(String providerFirstName) {
        this.providerFirstName = providerFirstName;
    }
}
