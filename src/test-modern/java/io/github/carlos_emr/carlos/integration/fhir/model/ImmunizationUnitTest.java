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
 * Migrated from legacy JUnit 4 ImmunizationTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.integration.fhir.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Prevention;

/**
 * Unit tests for {@link Immunization} FHIR resource wrapper.
 *
 * <p>Tests conversion from CARLOS Prevention model to FHIR Immunization resource.
 * Migrated from legacy JUnit 4 ImmunizationTest.
 *
 * @since 2017-01-01 (original)
 */
@Tag("unit")
@Tag("fhir")
@DisplayName("FHIR Immunization unit tests")
class ImmunizationUnitTest {

    private static AbstractOscarFhirResource<org.hl7.fhir.dstu3.model.Immunization, Prevention> oscarFhirResource;
    private static Date date;

    @BeforeAll
    static void setUp() {
        date = new Date(System.currentTimeMillis());

        Prevention immunization = new Prevention();
        immunization.setImmunizationDate(date);
        immunization.setImmunizationRefused(Boolean.FALSE);
        immunization.setImmunizationRefusedReason("Didnt want it.");
        immunization.setComment("This is a comment");
        immunization.setDose("20cc");
        immunization.setImmunizationType("HPV");
        immunization.setSite("LD");
        immunization.setRoute("IM");
        immunization.setLotNo("123456");
        immunization.setManufacture("Pfizer");
        immunization.setName("HPV Vaccine");

        oscarFhirResource = new Immunization<>(immunization);
    }

    @Test
    @DisplayName("should return prevention date from OSCAR resource")
    void shouldReturnPreventionDate_fromOscarResource() {
        assertThat(oscarFhirResource.getOscarResource().getPreventionDate()).isEqualTo(date);
    }

    @Test
    @DisplayName("should map prevention date to FHIR immunization date")
    void shouldMapPreventionDate_toFhirImmunizationDate() {
        assertThat(oscarFhirResource.getFhirResource().getDate()).isEqualTo(date);
    }
}
