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
 * Migrated from legacy JUnit 4 PractitionerTest to JUnit 5 for the CARLOS EMR project (2026).
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
 * <p>Tests conversion from CARLOS Provider model to FHIR Practitioner resource.
 * Migrated from legacy JUnit 4 PractitionerTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("fhir")
@DisplayName("FHIR Practitioner unit tests")
class PractitionerUnitTest {

    private static Practitioner practitioner;

    @BeforeAll
    static void setUp() {
        Provider provider = new Provider();
        provider.setProviderNo("8879");
        provider.setFirstName("Doug");
        provider.setLastName("Ross");
        provider.setHsoNo("12342");
        provider.setOhipNo("12342");

        practitioner = new Practitioner(provider);
    }

    @Test
    @DisplayName("should produce non-null FHIR JSON representation")
    void shouldProduceNonNullFhirJson() {
        assertThat(practitioner.getFhirJSON()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("should retain original provider as OSCAR resource")
    void shouldRetainOriginalProvider_asOscarResource() {
        assertThat(practitioner.getOscarResource()).isNotNull();
        assertThat(practitioner.getOscarResource().getFirstName()).isEqualTo("Doug");
        assertThat(practitioner.getOscarResource().getLastName()).isEqualTo("Ross");
    }
}
