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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Clinic;

/**
 * Unit tests for {@link Organization} FHIR resource wrapper.
 *
 * <p>Tests the mapping between CARLOS {@link Clinic} and FHIR DSTU3
 * {@link org.hl7.fhir.dstu3.model.Organization} resources.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("FHIR Organization")
class OrganizationUnitTest {

    private static Clinic clinic;
    private static Organization<Clinic> organization;

    @BeforeAll
    static void setUp() {
        clinic = new Clinic();
        clinic.setClinicName("Test Family Clinic");
        clinic.setClinicAddress("456 Health Ave");
        clinic.setClinicCity("Toronto");
        clinic.setClinicProvince("ON");
        clinic.setClinicPostal("M5V 3A1");
        clinic.setClinicPhone("416-555-0300");
        clinic.setClinicFax("416-555-0301");

        organization = new Organization<>(clinic);
    }

    @Test
    @DisplayName("should return original Clinic via getOscarResource")
    void shouldReturnOriginalClinic() {
        assertThat(organization.getOscarResource()).isEqualTo(clinic);
    }

    @Test
    @DisplayName("should produce non-empty FHIR JSON")
    void shouldProduceNonEmptyFhirJson() {
        String json = organization.getFhirJSON();
        assertThat(json).isNotEmpty();
        assertThat(json).contains("Organization");
    }

    @Test
    @DisplayName("should produce non-empty FHIR XML")
    void shouldProduceNonEmptyFhirXml() {
        String xml = organization.getFhirXML();
        assertThat(xml).isNotEmpty();
        assertThat(xml).contains("Organization");
    }

    @Test
    @DisplayName("should map clinic name to organization name")
    void shouldMapClinicName_toOrganizationName() {
        org.hl7.fhir.dstu3.model.Organization fhir = organization.getFhirResource();
        assertThat(fhir.getName()).isEqualTo("Test Family Clinic");
    }

    @Test
    @DisplayName("should map clinic contact info to FHIR telecom")
    void shouldMapClinicContact_toFhirTelecom() {
        org.hl7.fhir.dstu3.model.Organization fhir = organization.getFhirResource();
        assertThat(fhir.getTelecom()).isNotEmpty();
    }
}
