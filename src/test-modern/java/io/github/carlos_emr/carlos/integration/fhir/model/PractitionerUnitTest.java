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

import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Unit tests for {@link Practitioner} FHIR resource wrapper.
 *
 * <p>Tests the mapping between CARLOS {@link Provider} and FHIR DSTU3
 * {@link org.hl7.fhir.dstu3.model.Practitioner} resources.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("FHIR Practitioner")
class PractitionerUnitTest {

    private static Provider provider;
    private static Practitioner practitioner;

    @BeforeAll
    static void setUp() {
        provider = new Provider();
        provider.setProviderNo("999990");
        provider.setFirstName("John");
        provider.setLastName("Smith");
        provider.setSex("M");
        provider.setSpecialty("Family Medicine");
        provider.setOhipNo("12345");

        practitioner = new Practitioner(provider);
    }

    @Test
    @DisplayName("should return original Provider via getOscarResource")
    void shouldReturnOriginalProvider() {
        assertThat(practitioner.getOscarResource()).isEqualTo(provider);
    }

    @Test
    @DisplayName("should produce non-empty FHIR JSON")
    void shouldProduceNonEmptyFhirJson() {
        String json = practitioner.getFhirJSON();
        assertThat(json).isNotEmpty();
        assertThat(json).contains("Practitioner");
        assertThat(json).contains("Smith");
    }

    @Test
    @DisplayName("should produce non-empty FHIR XML")
    void shouldProduceNonEmptyFhirXml() {
        String xml = practitioner.getFhirXML();
        assertThat(xml).isNotEmpty();
        assertThat(xml).contains("Practitioner");
    }

    @Test
    @DisplayName("should map provider name to FHIR practitioner name")
    void shouldMapProviderName_toFhirName() {
        org.hl7.fhir.dstu3.model.Practitioner fhir = practitioner.getFhirResource();
        assertThat(fhir.getNameFirstRep().getFamily()).isEqualTo("Smith");
        assertThat(fhir.getNameFirstRep().getGiven().get(0).getValue()).isEqualTo("John");
    }

    @Test
    @DisplayName("should generate reference link")
    void shouldGenerateReferenceLink() {
        String ref = practitioner.getReferenceLink();
        assertThat(ref).isNotEmpty();
        assertThat(ref).contains("Practitioner");
    }
}
