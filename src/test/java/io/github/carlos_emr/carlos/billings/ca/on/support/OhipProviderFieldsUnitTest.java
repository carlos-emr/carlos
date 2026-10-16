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
package io.github.carlos_emr.carlos.billings.ca.on.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OhipProviderFields}: a provider's stored values must fill their
 * numeric fields in the OHIP batch header exactly.
 */
@DisplayName("OhipProviderFields")
@Tag("unit")
@Tag("billing")
class OhipProviderFieldsUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"012345", "999998"})
    void shouldAcceptBillingNumber_whenItIsSixDigits(String ohipNo) {
        assertThat(OhipProviderFields.isWellFormedBillingNumber(ohipNo)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "123 456", "0123456 "})
    void shouldRejectBillingNumber_whenItDoesNotFillSixCharacters(String ohipNo) {
        assertThat(OhipProviderFields.isWellFormedBillingNumber(ohipNo)).isFalse();
    }

    // Six characters fit the record, but the MOH field is numeric (format N), so the
    // batch would be rejected. Full-width digits are not ASCII and would not fit either.
    @ParameterizedTest
    @ValueSource(strings = {"12345A", "ABCDEF", " 12345", "12 456", "１２３４５６"})
    void shouldRejectBillingNumber_whenItHoldsAnythingButDigits(String ohipNo) {
        assertThat(OhipProviderFields.isWellFormedBillingNumber(ohipNo)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "08"})
    void shouldAcceptSpecialtyCode_whenItIsTwoDigits(String specialtyCode) {
        assertThat(OhipProviderFields.isWellFormedSpecialtyCode(specialtyCode)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"8", "008", " 8 "})
    void shouldRejectSpecialtyCode_whenItDoesNotFillTwoCharacters(String specialtyCode) {
        assertThat(OhipProviderFields.isWellFormedSpecialtyCode(specialtyCode)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {" 8", "8 ", "0A", "GP", "０８"})
    void shouldRejectSpecialtyCode_whenItHoldsAnythingButDigits(String specialtyCode) {
        assertThat(OhipProviderFields.isWellFormedSpecialtyCode(specialtyCode)).isFalse();
    }
}
