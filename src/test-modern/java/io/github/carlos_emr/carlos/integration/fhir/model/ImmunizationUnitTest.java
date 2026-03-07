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

import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Prevention;

/**
 * Unit tests for {@link Immunization} FHIR resource wrapper.
 *
 * <p>Tests the mapping between CARLOS {@link Prevention} and FHIR DSTU3
 * {@link org.hl7.fhir.dstu3.model.Immunization} resources.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("FHIR Immunization")
class ImmunizationUnitTest {

    private static final Date IMMUNIZATION_DATE = new Date(1704067200000L); // 2024-01-01
    private static Prevention prevention;
    private static Immunization<Prevention> immunization;

    @BeforeAll
    static void setUp() {
        prevention = new Prevention();
        prevention.setDemographicNo(1);
        prevention.setProviderNo("999990");
        prevention.setPreventionDate(IMMUNIZATION_DATE);
        prevention.setPreventionType("Td");
        prevention.setRoute("IM");
        prevention.setSite("Left Deltoid");
        prevention.setDose("0.5 mL");
        prevention.setLotNo("LOT12345");
        prevention.setName("Tetanus-Diphtheria");

        immunization = new Immunization<>(prevention);
    }

    @Nested
    @DisplayName("getOscarResource")
    class GetOscarResource {

        @Test
        @DisplayName("should return the original Prevention object")
        void shouldReturnOriginalPrevention() {
            assertThat(immunization.getOscarResource()).isEqualTo(prevention);
        }

        @Test
        @DisplayName("should preserve prevention date")
        void shouldPreservePreventionDate() {
            assertThat(immunization.getOscarResource().getPreventionDate()).isEqualTo(IMMUNIZATION_DATE);
        }
    }

    @Nested
    @DisplayName("getFhirResource")
    class GetFhirResource {

        @Test
        @DisplayName("should map prevention date to FHIR immunization date")
        void shouldMapPreventionDate_toFhirDate() {
            assertThat(immunization.getFhirResource().getDate()).isEqualTo(IMMUNIZATION_DATE);
        }

        @Test
        @DisplayName("should produce non-null FHIR resource")
        void shouldProduceNonNullFhirResource() {
            assertThat(immunization.getFhirResource()).isNotNull();
        }
    }

    @Nested
    @DisplayName("serialization")
    class Serialization {

        @Test
        @DisplayName("should produce non-empty FHIR JSON")
        void shouldProduceNonEmptyFhirJson() {
            String json = immunization.getFhirJSON();
            assertThat(json).isNotEmpty();
            assertThat(json).contains("Immunization");
        }

        @Test
        @DisplayName("should produce non-empty FHIR XML")
        void shouldProduceNonEmptyFhirXml() {
            String xml = immunization.getFhirXML();
            assertThat(xml).isNotEmpty();
            assertThat(xml).contains("Immunization");
        }
    }

    @Nested
    @DisplayName("vaccine details mapping")
    class VaccineDetails {

        @Test
        @DisplayName("should map route to FHIR immunization route")
        void shouldMapRoute_toFhirRoute() {
            org.hl7.fhir.dstu3.model.Immunization fhir = immunization.getFhirResource();
            assertThat(fhir.getRoute()).isNotNull();
        }

        @Test
        @DisplayName("should map lot number to FHIR lot number")
        void shouldMapLotNumber_toFhirLotNumber() {
            org.hl7.fhir.dstu3.model.Immunization fhir = immunization.getFhirResource();
            assertThat(fhir.getLotNumber()).isEqualTo("LOT12345");
        }

        @Test
        @DisplayName("should map site to FHIR immunization site")
        void shouldMapSite_toFhirSite() {
            org.hl7.fhir.dstu3.model.Immunization fhir = immunization.getFhirResource();
            assertThat(fhir.getSite()).isNotNull();
        }
    }
}
