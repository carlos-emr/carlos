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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit tests for {@link BillingDateOfBirths#calculateAge}.
 *
 * <p>Three failure modes (length≠8, NumberFormatException,
 * DateTimeException) must all set {@code invalid=true}; the empty-input
 * case must return {@code invalid=false} (no patient yet ≠ parse
 * failure).</p>
 *
 * <p>Regression armor for the silent-age=0 bug: visit-type defaults and
 * age-keyed premium codes branch off {@code AgeResult.age()}, so any future
 * refactor that drops the {@code invalid} flag and returns 0 silently must
 * fail one of these tests loudly.</p>
 *
 * @since 2026-04-25
 */
@DisplayName("BillingDateOfBirths.calculateAge")
@Tag("unit")
@Tag("billing")
class BillingDateOfBirthsCalculateAgeUnitTest {

    @Test
    void shouldReturnZeroAndNotInvalid_whenDobIsNull() {
        BillingDateOfBirths.AgeResult result = BillingDateOfBirths.calculateAge(null);

        assertThat(result.age()).isEqualTo(0);
        assertThat(result.invalid()).isFalse();
    }

    @Test
    void shouldReturnZeroAndNotInvalid_whenDobIsEmpty() {
        BillingDateOfBirths.AgeResult result = BillingDateOfBirths.calculateAge("");

        assertThat(result.age()).isEqualTo(0);
        assertThat(result.invalid()).isFalse();
    }

    @Test
    void shouldFlagInvalid_whenDobIsTooShort() {
        BillingDateOfBirths.AgeResult result = BillingDateOfBirths.calculateAge("1985");

        assertThat(result.age()).isEqualTo(0);
        assertThat(result.invalid()).isTrue();
    }

    @Test
    void shouldFlagInvalid_whenDobIsTooLong() {
        BillingDateOfBirths.AgeResult result = BillingDateOfBirths.calculateAge("198506150000");

        assertThat(result.age()).isEqualTo(0);
        assertThat(result.invalid()).isTrue();
    }

    /**
     * The classic length==8 trap: parses as integers but fails at
     * LocalDate.of with DateTimeException. The original silent-age=0 bug.
     */
    @Test
    void shouldFlagInvalid_whenDobIsAllNinesAndDateTimeRejects() {
        BillingDateOfBirths.AgeResult result = BillingDateOfBirths.calculateAge("99999999");

        assertThat(result.age()).isEqualTo(0);
        assertThat(result.invalid()).isTrue();
    }

    @Test
    void shouldFlagInvalid_whenDobContainsNonDigits() {
        BillingDateOfBirths.AgeResult result = BillingDateOfBirths.calculateAge("19AB0615");

        assertThat(result.age()).isEqualTo(0);
        assertThat(result.invalid()).isTrue();
    }

    @Test
    void shouldComputeAgeAndNotInvalid_forValidDob() {
        // Use a fixed historical year so the test is stable across runs.
        // 1900-01-01 is far enough back that age > 100 always.
        BillingDateOfBirths.AgeResult result = BillingDateOfBirths.calculateAge("19000101");

        assertThat(result.age()).isGreaterThan(100);
        assertThat(result.invalid()).isFalse();
    }

    @Test
    void shouldFlagInvalid_whenDobIsInTheFuture() {
        String futureDob = java.time.LocalDate.now()
                .plusYears(1)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        BillingDateOfBirths.AgeResult result = BillingDateOfBirths.calculateAge(futureDob);

        assertThat(result.age()).isEqualTo(0);
        assertThat(result.invalid()).isTrue();
    }
}
