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
 *
 * <p>
 * Migrated from legacy JUnit 4 PatientTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.integration.fhir.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;

import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Unit tests for {@link Patient} FHIR resource wrapper.
 *
 * <p>Tests conversion from CARLOS Demographic model to FHIR Patient resource.
 * Migrated from legacy JUnit 4 PatientTest.
 *
 * @since 2017-01-01 (original)
 */
@Tag("unit")
@Tag("fhir")
@DisplayName("FHIR Patient unit tests")
class PatientUnitTest {

    private static Patient patient;
    private static Demographic demographic;

    @BeforeAll
    static void setUp() {
        demographic = new Demographic();
        demographic.setDemographicNo(122343);
        demographic.setTitle("Mr");
        demographic.setSex("M");
        demographic.setFirstName("Dennis");
        demographic.setLastName("Warren");
        Calendar birthdate = Calendar.getInstance();
        birthdate.set(1969, 6, 18);
        demographic.setBirthDay(birthdate);

        Provider provider = new Provider();
        provider.setProviderNo("8879");
        provider.setFirstName("Doug");
        provider.setLastName("Ross");
        provider.setHsoNo("12342");
        provider.setOhipNo("12342");

        patient = new Patient(demographic);
        patient.addGeneralPractitioner(provider);
    }

    @Test
    @DisplayName("should return original demographic from FHIR patient")
    void shouldReturnOriginalDemographic_fromFhirPatient() {
        assertThat(patient.getOscarResource()).isEqualTo(demographic);
    }

    @Test
    @DisplayName("should map male sex to FHIR AdministrativeGender.MALE")
    void shouldMapMaleSex_toFhirMaleGender() {
        assertThat(patient.getFhirResource().getGender()).isEqualTo(AdministrativeGender.MALE);
    }
}
