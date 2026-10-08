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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BillingGroupNumber}: the stored provider group number is repaired
 * into the four-character MOH form where that is unambiguous, and nothing
 * else is guessed (issue #4277).
 */
@DisplayName("BillingGroupNumber")
@Tag("unit")
@Tag("billing")
class BillingGroupNumberUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest
    @CsvSource({
            "123, 0123",
            "1, 0001",
            "12, 0012",
            "1234, 1234",
            "' 1234 ', 1234",
            "12a4, 12A4",
            "abcd, ABCD",
            "'  123', 0123",
    })
    void shouldNormalizeToFourCharacters_whenValueIsRepairable(String raw, String expected) {
        assertThat(BillingGroupNumber.normalize(raw)).isEqualTo(expected);
        assertThat(BillingGroupNumber.isWellFormed(BillingGroupNumber.normalize(raw))).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "null", "0", "00", "000", "0000"})
    void shouldTreatBlankAndZeroValues_asSoloBilling(String raw) {
        assertThat(BillingGroupNumber.normalize(raw)).isEqualTo(BillingGroupNumber.SOLO);
        assertThat(BillingGroupNumber.isSolo(raw)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "12A", "A1", "12-4", "１２３４", "12 34", "0123x"})
    void shouldLeaveValueIllFormed_whenItCannotBeNormalized(String raw) {
        String normalized = BillingGroupNumber.normalize(raw);

        assertThat(BillingGroupNumber.isWellFormed(normalized)).isFalse();
        assertThat(BillingGroupNumber.isSolo(raw)).isFalse();
    }

    @Test
    void shouldNotPadValuesContainingLetters_whenShorterThanFour() {
        // Only an all-digit value can have lost a leading zero; "A12" is not "0A12".
        assertThat(BillingGroupNumber.normalize("a12")).isEqualTo("A12");
        assertThat(BillingGroupNumber.isWellFormed("A12")).isFalse();
    }

    @Test
    void shouldRejectNullAndLowerCase_forWellFormedCheck() {
        assertThat(BillingGroupNumber.isWellFormed(null)).isFalse();
        assertThat(BillingGroupNumber.isWellFormed("12a4")).isFalse();
        assertThat(BillingGroupNumber.isWellFormed("12A4")).isTrue();
        assertThat(BillingGroupNumber.isWellFormed("0000")).isTrue();
    }
}
