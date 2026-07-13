/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.test.builders;

import io.github.carlos_emr.carlos.commn.model.Demographic;

import java.util.Date;

/**
 * Test data builder for {@link Demographic} entities.
 *
 * <p>Provides deterministic, clinically realistic defaults for patient demographic test data.
 * All default values satisfy NOT NULL constraints and VARCHAR length limits from the
 * database schema.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Demographic patient = DemographicTestBuilder.aDemographic().build();
 * Demographic jane = DemographicTestBuilder.aDemographic()
 *     .withFirstName("Jane")
 *     .withLastName("Smith")
 *     .withSex("F")
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class DemographicTestBuilder {

    private String firstName = "TestFirst";
    private String lastName = "TestLast";
    private String middleNames = "";
    private String sex = "M";
    private String hin = "1234567890";
    private String ver = "AB";
    private String hcType = "ON";
    private String patientStatus = "AC";
    private Date patientStatusDate = new Date(1704067200000L); // 2024-01-01
    private String yearOfBirth = "1990";
    private String monthOfBirth = "01";
    private String dateOfBirth = "15";
    private Date dateJoined = new Date(1704067200000L); // 2024-01-01
    private Date endDate = new Date(4102444800000L); // 2100-01-01
    private String providerNo = "999990";
    private String phone = "416-555-0100";
    private String phone2 = "";
    private String email = "test@example.com";
    private String address = "123 Test Street";
    private String city = "Toronto";
    private String province = "ON";
    private String postal = "M5V 2T6";
    private String rosterStatus = "";
    private String chartNo = "";
    private String familyDoctor = "";

    private DemographicTestBuilder() {
    }

    /**
     * Creates a new builder with clinically realistic defaults.
     *
     * @return a new builder instance
     */
    public static DemographicTestBuilder aDemographic() {
        return new DemographicTestBuilder();
    }

    public DemographicTestBuilder withFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public DemographicTestBuilder withLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public DemographicTestBuilder withMiddleNames(String middleNames) {
        this.middleNames = middleNames;
        return this;
    }

    public DemographicTestBuilder withSex(String sex) {
        this.sex = sex;
        return this;
    }

    public DemographicTestBuilder withHin(String hin) {
        this.hin = hin;
        return this;
    }

    public DemographicTestBuilder withVer(String ver) {
        this.ver = ver;
        return this;
    }

    public DemographicTestBuilder withHcType(String hcType) {
        this.hcType = hcType;
        return this;
    }

    public DemographicTestBuilder withPatientStatus(String patientStatus) {
        this.patientStatus = patientStatus;
        return this;
    }

    public DemographicTestBuilder withYearOfBirth(String yearOfBirth) {
        this.yearOfBirth = yearOfBirth;
        return this;
    }

    public DemographicTestBuilder withMonthOfBirth(String monthOfBirth) {
        this.monthOfBirth = monthOfBirth;
        return this;
    }

    public DemographicTestBuilder withDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        return this;
    }

    public DemographicTestBuilder withDateJoined(Date dateJoined) {
        this.dateJoined = dateJoined;
        return this;
    }

    public DemographicTestBuilder withEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    public DemographicTestBuilder withProviderNo(String providerNo) {
        this.providerNo = providerNo;
        return this;
    }

    public DemographicTestBuilder withPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public DemographicTestBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public DemographicTestBuilder withAddress(String address) {
        this.address = address;
        return this;
    }

    public DemographicTestBuilder withCity(String city) {
        this.city = city;
        return this;
    }

    public DemographicTestBuilder withProvince(String province) {
        this.province = province;
        return this;
    }

    public DemographicTestBuilder withPostal(String postal) {
        this.postal = postal;
        return this;
    }

    public DemographicTestBuilder withRosterStatus(String rosterStatus) {
        this.rosterStatus = rosterStatus;
        return this;
    }

    public DemographicTestBuilder withChartNo(String chartNo) {
        this.chartNo = chartNo;
        return this;
    }

    public DemographicTestBuilder withFamilyDoctor(String familyDoctor) {
        this.familyDoctor = familyDoctor;
        return this;
    }

    /**
     * Creates an inactive patient with deceased status.
     *
     * @return this builder
     */
    public DemographicTestBuilder deceased() {
        this.patientStatus = "DE";
        this.patientStatusDate = new Date(1704067200000L);
        return this;
    }

    /**
     * Creates a female patient with appropriate defaults.
     *
     * @return this builder
     */
    public DemographicTestBuilder female() {
        this.sex = "F";
        this.firstName = "TestFirstF";
        return this;
    }

    public Demographic build() {
        Demographic d = new Demographic();
        d.setFirstName(firstName);
        d.setLastName(lastName);
        d.setMiddleNames(middleNames);
        d.setSex(sex);
        d.setHin(hin);
        d.setVer(ver);
        d.setHcType(hcType);
        d.setPatientStatus(patientStatus);
        d.setPatientStatusDate(patientStatusDate);
        d.setYearOfBirth(yearOfBirth);
        d.setMonthOfBirth(monthOfBirth);
        d.setDateOfBirth(dateOfBirth);
        d.setDateJoined(dateJoined);
        d.setEndDate(endDate);
        d.setProviderNo(providerNo);
        d.setPhone(phone);
        d.setPhone2(phone2);
        d.setEmail(email);
        d.setAddress(address);
        d.setCity(city);
        d.setProvince(province);
        d.setPostal(postal);
        d.setRosterStatus(rosterStatus);
        d.setChartNo(chartNo);
        d.setFamilyDoctor(familyDoctor);
        return d;
    }
}
