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
 * Migrated from legacy JUnit 4 OrganizationTest to JUnit 5 for the CARLOS EMR project (2026).
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
 * <p>Tests conversion from CARLOS Clinic model to FHIR Organization resource.
 * Migrated from legacy JUnit 4 OrganizationTest.
 *
 * @since 2017-01-01 (original)
 */
@Tag("unit")
@Tag("fhir")
@DisplayName("FHIR Organization unit tests")
class OrganizationUnitTest {

    private static Organization<Clinic> organization;
    private static Clinic clinic;

    @BeforeAll
    static void setUp() {
        clinic = new Clinic();
        clinic.setId(2);
        clinic.setClinicAddress("123 Clinic Street");
        clinic.setClinicCity("Vancouver");
        clinic.setClinicProvince("BC");
        clinic.setClinicPhone("778-567-3445");
        clinic.setClinicFax("778-343-3453");
        clinic.setClinicName("Test Medical Clinic");

        organization = new Organization<>(clinic);
    }

    @Test
    @DisplayName("should produce non-null FHIR JSON representation")
    void shouldProduceNonNullFhirJson() {
        assertThat(organization.getFhirJSON()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("should return original clinic as OSCAR resource")
    void shouldReturnOriginalClinic_asOscarResource() {
        assertThat(organization.getOscarResource()).isEqualTo(clinic);
    }
}
