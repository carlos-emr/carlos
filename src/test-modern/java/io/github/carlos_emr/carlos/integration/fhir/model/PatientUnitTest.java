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
package io.github.carlos_emr.carlos.integration.fhir.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;

import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Unit tests for {@link Patient} FHIR resource wrapper.
 *
 * <p>Tests the bidirectional mapping between CARLOS {@link Demographic}
 * and FHIR DSTU3 {@link org.hl7.fhir.dstu3.model.Patient} resources.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("FHIR Patient")
class PatientUnitTest {

    private static Demographic demographic;
    private static Patient patient;

    @BeforeAll
    static void setUp() {
        demographic = new Demographic();
        demographic.setDemographicNo(1);
        demographic.setFirstName("John");
        demographic.setLastName("Doe");
        demographic.setSex("M");
        demographic.setHin("1234567890");
        demographic.setVer("AB");
        demographic.setHcType("ON");
        demographic.setPhone("416-555-0100");
        demographic.setAddress("123 Main St");
        demographic.setCity("Toronto");
        demographic.setProvince("ON");
        demographic.setPostal("M5V 2T6");

        Calendar cal = Calendar.getInstance();
        cal.set(1990, Calendar.JANUARY, 15);
        demographic.setYearOfBirth("1990");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("15");

        patient = new Patient(demographic);
    }

    @Nested
    @DisplayName("getOscarResource")
    class GetOscarResource {

        @Test
        @DisplayName("should return the original Demographic object")
        void shouldReturnOriginalDemographic() {
            assertThat(patient.getOscarResource()).isEqualTo(demographic);
        }

        @Test
        @DisplayName("should preserve demographic number")
        void shouldPreserveDemographicNumber() {
            assertThat(patient.getOscarResource().getDemographicNo()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getFhirResource - gender mapping")
    class GenderMapping {

        @Test
        @DisplayName("should map male sex to MALE gender")
        void shouldMapMaleSex_toMaleGender() {
            assertThat(patient.getFhirResource().getGender()).isEqualTo(AdministrativeGender.MALE);
        }

        @Test
        @DisplayName("should map female sex to FEMALE gender")
        void shouldMapFemaleSex_toFemaleGender() {
            Demographic female = new Demographic();
            female.setDemographicNo(2);
            female.setFirstName("Jane");
            female.setLastName("Doe");
            female.setSex("F");
            female.setYearOfBirth("1990");
            female.setMonthOfBirth("01");
            female.setDateOfBirth("15");

            Patient femalePatient = new Patient(female);
            assertThat(femalePatient.getFhirResource().getGender()).isEqualTo(AdministrativeGender.FEMALE);
        }
    }

    @Nested
    @DisplayName("getFhirResource - name mapping")
    class NameMapping {

        @Test
        @DisplayName("should map last name to family name")
        void shouldMapLastName_toFamilyName() {
            assertThat(patient.getFhirResource().getNameFirstRep().getFamily()).isEqualTo("Doe");
        }

        @Test
        @DisplayName("should map first name to given name")
        void shouldMapFirstName_toGivenName() {
            assertThat(patient.getFhirResource().getNameFirstRep().getGiven().get(0).getValue()).isEqualTo("John");
        }
    }

    @Nested
    @DisplayName("getFhirJSON / getFhirXML")
    class Serialization {

        @Test
        @DisplayName("should produce non-empty JSON")
        void shouldProduceNonEmptyJson() {
            String json = patient.getFhirJSON();
            assertThat(json).isNotEmpty();
            assertThat(json).contains("Patient");
            assertThat(json).contains("Doe");
        }

        @Test
        @DisplayName("should produce non-empty XML")
        void shouldProduceNonEmptyXml() {
            String xml = patient.getFhirXML();
            assertThat(xml).isNotEmpty();
            assertThat(xml).contains("Patient");
        }
    }

    @Nested
    @DisplayName("addGeneralPractitioner")
    class GeneralPractitioner {

        @Test
        @DisplayName("should add provider as general practitioner")
        void shouldAddProvider_asGeneralPractitioner() {
            Demographic demo = new Demographic();
            demo.setDemographicNo(3);
            demo.setFirstName("Test");
            demo.setLastName("Patient");
            demo.setSex("M");
            demo.setYearOfBirth("1985");
            demo.setMonthOfBirth("06");
            demo.setDateOfBirth("20");

            Patient p = new Patient(demo);

            Provider provider = new Provider();
            provider.setProviderNo("999990");
            provider.setFirstName("Dr");
            provider.setLastName("Smith");
            provider.setSex("M");
            provider.setSpecialty("Family Medicine");

            p.addGeneralPractitioner(provider);

            assertThat(p.getFhirResource().getGeneralPractitioner()).isNotEmpty();
        }
    }
}
