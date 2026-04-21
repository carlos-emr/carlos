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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for demographic field length validation.
 *
 * @since 2026-04-21
 */
@DisplayName("Demographic field length validation")
@Tag("unit")
@Tag("demographic")
class DemographicFieldLengthUnitTest {

    @Test
    @DisplayName("should return validation error when last name exceeds thirty characters")
    void shouldReturnValidationError_whenLastNameExceedsThirtyCharacters() {
        Demographic demographic = new Demographic();
        demographic.setLastName("X".repeat(Demographic.LAST_NAME_MAX_LENGTH + 1));

        assertThat(demographic.validateFieldLengths())
                .contains("Last name exceeds maximum length of 30 characters.");
    }

    @Test
    @DisplayName("should return validation error when health card number exceeds twenty characters")
    void shouldReturnValidationError_whenHinExceedsTwentyCharacters() {
        Demographic demographic = new Demographic();
        demographic.setHin("1".repeat(Demographic.HIN_MAX_LENGTH + 1));

        assertThat(demographic.validateFieldLengths())
                .contains("Health card number exceeds maximum length of 20 characters.");
    }

    @Test
    @DisplayName("should return validation errors when mapped write-path fields exceed configured lengths")
    void shouldReturnValidationErrors_whenMappedWritePathFieldsExceedConfiguredLengths() {
        Demographic demographic = new Demographic();
        demographic.setProvince("X".repeat(Demographic.PROVINCE_MAX_LENGTH + 1));
        demographic.setResidentialProvince("X".repeat(Demographic.RESIDENTIAL_PROVINCE_MAX_LENGTH + 1));
        demographic.setSex("MF");
        demographic.setMonthOfBirth("001");
        demographic.setDateOfBirth("012");
        demographic.setYearOfBirth("20255");
        demographic.setPreviousAddress("X".repeat(Demographic.PREVIOUS_ADDRESS_MAX_LENGTH + 1));
        demographic.setChildren("X".repeat(Demographic.CHILDREN_MAX_LENGTH + 1));
        demographic.setSourceOfIncome("X".repeat(Demographic.SOURCE_OF_INCOME_MAX_LENGTH + 1));
        demographic.setCitizenship("X".repeat(Demographic.CITIZENSHIP_MAX_LENGTH + 1));

        assertThat(demographic.validateFieldLengths())
                .contains("Province exceeds maximum length of 20 characters.")
                .contains("Residential province exceeds maximum length of 20 characters.")
                .contains("Sex exceeds maximum length of 1 characters.")
                .contains("Month of birth exceeds maximum length of 2 characters.")
                .contains("Date of birth exceeds maximum length of 2 characters.")
                .contains("Year of birth exceeds maximum length of 4 characters.")
                .contains("Previous address exceeds maximum length of 255 characters.")
                .contains("Children exceeds maximum length of 255 characters.")
                .contains("Source of income exceeds maximum length of 255 characters.")
                .contains("Citizenship exceeds maximum length of 40 characters.");
    }

    @Test
    @DisplayName("should not return validation errors when fields are within maximum lengths")
    void shouldNotReturnValidationErrors_whenFieldsAreWithinMaximumLengths() {
        Demographic demographic = new Demographic();
        demographic.setLastName("X".repeat(Demographic.LAST_NAME_MAX_LENGTH));
        demographic.setProvince("ON");
        demographic.setResidentialProvince("BC");
        demographic.setSex("F");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("31");
        demographic.setYearOfBirth("2025");
        demographic.setPreviousAddress("123 Example Street");
        demographic.setChildren("0");
        demographic.setSourceOfIncome("Employment");
        demographic.setCitizenship("Canadian");
        demographic.setHin("1".repeat(Demographic.HIN_MAX_LENGTH));
        demographic.setRosterTerminationReason("RO");

        assertThat(demographic.validateFieldLengths()).isEmpty();
    }
}
