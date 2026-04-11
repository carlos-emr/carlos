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
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.DtoFormatUtils;

/**
 * Lightweight data transfer object for patient search results and autocomplete,
 * optimized for JPQL constructor expression projection. Carries only the fields
 * needed for patient identification in search results.
 *
 * <p>PHI minimization: this DTO deliberately omits SIN (Social Insurance Number)
 * which is never displayed in search results.</p>
 *
 * <p>The {@code cellPhone} field is not part of the JPQL projection constructor
 * because it comes from the {@code demographic_ext} table. It can be populated
 * post-query via {@link #setCellPhone(String)} if needed.</p>
 *
 * @since 2026-04-11
 */
public class DemographicListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer demographicNo;
    private String lastName;
    private String firstName;
    private String alias;
    private String sex;
    private String yearOfBirth;
    private String monthOfBirth;
    private String dateOfBirth;
    private String patientStatus;
    private String rosterStatus;
    private String providerNo;
    private String chartNo;
    private String phone;
    private String email;
    private String hin;
    private String address;

    // Not part of JPQL projection — populated post-query from DemographicExt
    private String cellPhone;

    /**
     * Default constructor required by frameworks.
     */
    public DemographicListItemDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions. Parameter order
     * must match the SELECT NEW clause exactly. Does not include cellPhone
     * (loaded from demographic_ext, not the demographic table).
     *
     * @param demographicNo Integer the patient demographic number
     * @param lastName String the patient's last name
     * @param firstName String the patient's first name
     * @param alias String the patient's alias/preferred name
     * @param sex String the patient's sex code
     * @param yearOfBirth String the birth year (4 digits)
     * @param monthOfBirth String the birth month (2 digits)
     * @param dateOfBirth String the birth day (2 digits)
     * @param patientStatus String the patient status code (AC, IN, DE, etc.)
     * @param rosterStatus String the roster status
     * @param providerNo String the most responsible provider number
     * @param chartNo String the chart number
     * @param phone String the primary phone number
     * @param email String the email address
     * @param hin String the Health Insurance Number
     * @param address String the primary address
     */
    public DemographicListItemDTO(Integer demographicNo, String lastName, String firstName,
                                  String alias, String sex,
                                  String yearOfBirth, String monthOfBirth, String dateOfBirth,
                                  String patientStatus, String rosterStatus,
                                  String providerNo, String chartNo,
                                  String phone, String email, String hin, String address) {
        this.demographicNo = demographicNo;
        this.lastName = lastName;
        this.firstName = firstName;
        this.alias = alias;
        this.sex = sex;
        this.yearOfBirth = yearOfBirth;
        this.monthOfBirth = monthOfBirth;
        this.dateOfBirth = dateOfBirth;
        this.patientStatus = patientStatus;
        this.rosterStatus = rosterStatus;
        this.providerNo = providerNo;
        this.chartNo = chartNo;
        this.phone = phone;
        this.email = email;
        this.hin = hin;
        this.address = address;
    }

    /**
     * Creates a DemographicListItemDTO from a full Demographic entity.
     *
     * @param d Demographic the entity to convert; must not be null
     * @return DemographicListItemDTO a lightweight projection
     */
    public static DemographicListItemDTO fromEntity(Demographic d) {
        Objects.requireNonNull(d, "Demographic entity must not be null for DTO conversion");
        DemographicListItemDTO dto = new DemographicListItemDTO(
                d.getDemographicNo(),
                d.getLastName(),
                d.getFirstName(),
                d.getAlias(),
                d.getSex(),
                d.getYearOfBirth(),
                d.getMonthOfBirth(),
                d.getDateOfBirth(),
                d.getPatientStatus(),
                d.getRosterStatus(),
                d.getProviderNo(),
                d.getChartNo(),
                d.getPhone(),
                d.getEmail(),
                d.getHin(),
                d.getAddress()
        );
        dto.setCellPhone(d.getCellPhone());
        return dto;
    }

    /**
     * Returns the patient's formatted name as "LastName, FirstName" with alias
     * appended in parentheses if present.
     *
     * @return String the formatted name
     */
    public String getFormattedName() {
        boolean hasAlias = alias != null && !alias.trim().isEmpty();
        if (lastName == null && firstName == null && !hasAlias) {
            return "N/A";
        }
        String base = DtoFormatUtils.formatName(lastName, firstName, "");
        if (hasAlias) {
            return base + " (" + alias.trim() + ")";
        }
        return base;
    }

    /**
     * Returns the date of birth formatted as "YYYY-MM-DD", or empty string
     * if any component is missing.
     *
     * @return String the formatted date of birth
     */
    public String getFormattedDob() {
        return DtoFormatUtils.formatDob(yearOfBirth, monthOfBirth, dateOfBirth);
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

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
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

    public String getChartNo() {
        return chartNo;
    }

    public void setChartNo(String chartNo) {
        this.chartNo = chartNo;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHin() {
        return hin;
    }

    public void setHin(String hin) {
        this.hin = hin;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCellPhone() {
        return cellPhone;
    }

    public void setCellPhone(String cellPhone) {
        this.cellPhone = cellPhone;
    }
}
