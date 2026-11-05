/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ProfessionalSpecialistAddressUnitTest {
    @Test
    void shouldPreserveSeparators_whenUpdatingAnIncompleteAddress() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("Synthetic street,Synthetic city");
        specialist.setCity("Replacement city");
        specialist.setPostal("A1A 1A1");
        specialist.setProvince("ON");
        assertThat(specialist.getStreetAddress()).isEqualTo("Synthetic street,Replacement city,A1A 1A1,ON,,");
        assertThat(specialist.getAddressArray()).containsExactly("Synthetic street", "Replacement city", "A1A 1A1", "ON", "");
        assertThat(specialist.getAddress()).isEqualTo("Synthetic street");
        assertThat(specialist.getCity()).isEqualTo("Replacement city");
        assertThat(specialist.getPostal()).isEqualTo("A1A 1A1");
        assertThat(specialist.getProvince()).isEqualTo("ON");
    }

    @Test
    void shouldReturnEmptyComponents_forSingleLineAddress() {
        // Before #5476 a comma-less address left the component cache untouched, so getAddress()
        // returned "," and the city, postal and province getters returned null. Contact consumers
        // now receive the street and empty strings for the missing components.
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("123 Synthetic Street");
        assertThat(specialist.getAddress()).isEqualTo("123 Synthetic Street");
        assertThat(specialist.getCity()).isEmpty();
        assertThat(specialist.getPostal()).isEmpty();
        assertThat(specialist.getProvince()).isEmpty();
        assertThat(specialist.getStreetAddress()).isEqualTo("123 Synthetic Street");
    }

    @Test
    void shouldRetainSingleComponentStreet_whenSettingCity() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("New street");
        specialist.setCity("New city");
        assertThat(specialist.getStreetAddress()).isEqualTo("New street,New city,,,,");
        assertThat(specialist.getAddressArray()).containsExactly("New street", "New city", "", "", "");
    }

    @Test
    void shouldDiscardStaleComponents_whenReplacingWithSingleComponentStreet() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("Old street,Old city,A1A 1A1,ON,Canada");
        specialist.setStreetAddress("New street");
        specialist.setCity("New city");
        assertThat(specialist.getStreetAddress()).isEqualTo("New street,New city,,,,");
        assertThat(specialist.getAddressArray()).containsExactly("New street", "New city", "", "", "");
    }

    @Test
    void shouldInitializeMissingComponents_whenSettingPostalOnFreshObject() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setPostal("A1A 1A1");
        assertThat(specialist.getStreetAddress()).isEqualTo(",,A1A 1A1,,,");
        assertThat(specialist.getPostal()).isEqualTo("A1A 1A1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ",", ",,,,"})
    void shouldClearOldComponents_whenReplacingWithEmptyAddress(String address) {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("Old street,Old city,A1A 1A1,ON,Canada");
        specialist.setStreetAddress(address);
        specialist.setCity("New city");
        assertThat(specialist.getStreetAddress()).isEqualTo(",New city,,,,");
        assertThat(specialist.getAddressArray()).containsExactly("", "New city", "", "", "");
    }

    @Test
    void shouldPreserveCountryAndComponentValues_whenEditingCompleteAddressRepeatedly() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("Old street,Old city,A1A 1A1,ON,Canada,");
        specialist.setCity("New city");
        specialist.setPostal("B2B 2B2");
        specialist.setProvince("BC");
        specialist.setAddress("New street");
        specialist.setCity("Final city");
        assertThat(specialist.getStreetAddress()).isEqualTo("New street,Final city,B2B 2B2,BC,Canada,");
        assertThat(specialist.getAddressArray()).containsExactly("New street", "Final city", "B2B 2B2", "BC", "Canada");
    }

    @Test
    void shouldUseFormReplacement_whenUpdatingComponents() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("Old street,Old city");
        specialist.setStreetAddressFromForm("New street");
        specialist.setCity("New city");
        assertThat(specialist.getStreetAddress()).isEqualTo("New street,New city,,,,");
    }

    @Test
    void shouldKeepArrayComponentPositions_whenValuesContainCommasOrNulls() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        String[] components = {"Suite 2, Synthetic street", "Synthetic, city", null, "O,N", "Can,ada"};
        specialist.setStreetAddress(components);

        assertThat(specialist.getStreetAddress()).isEqualTo("Suite 2 Synthetic street,Synthetic city,,ON,Canada,");
        assertThat(specialist.getAddressArray()).containsExactly("Suite 2 Synthetic street", "Synthetic city", "", "ON", "Canada");
        assertThat(components).containsExactly("Suite 2, Synthetic street", "Synthetic, city", null, "O,N", "Can,ada");

        specialist.setPostal("A1A 1A1");
        specialist.setCity("Replacement city");
        assertThat(specialist.getStreetAddress()).isEqualTo("Suite 2 Synthetic street,Replacement city,A1A 1A1,ON,Canada,");
        assertThat(specialist.getAddressArray()).containsExactly("Suite 2 Synthetic street", "Replacement city", "A1A 1A1", "ON", "Canada");
    }

    @Test
    void shouldRemoveEmbeddedDelimiters_whenEditingAddressComponents() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("Old street,Old city,A1A 1A1,ON,Canada,");
        specialist.setCity("New, city");
        specialist.setPostal("B2B, 2B2");
        specialist.setProvince("B,C");
        specialist.setAddress("New, street");
        assertThat(specialist.getStreetAddress()).isEqualTo("New street,New city,B2B 2B2,BC,Canada,");
        assertThat(specialist.getAddressArray()).containsExactly("New street", "New city", "B2B 2B2", "BC", "Canada");
        assertThat(specialist.getAddress()).isEqualTo("New street");
        assertThat(specialist.getCity()).isEqualTo("New city");
        assertThat(specialist.getPostal()).isEqualTo("B2B 2B2");
        assertThat(specialist.getProvince()).isEqualTo("BC");
    }
}
