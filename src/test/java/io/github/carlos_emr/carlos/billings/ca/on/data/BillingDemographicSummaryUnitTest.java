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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.Demographic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * Unit tests for {@link BillingDemographicSummary} — locks the canonical
 * projection behavior the 5 ON billing assemblers depend on, especially
 * the HC-type defaulting and DOB-padding rules.
 */
@Tag("unit")
@Tag("billing")
class BillingDemographicSummaryUnitTest {

    @Nested
    @DisplayName("fromDemographic")
    class FromDemographic {

        @Test
        @DisplayName("should return EMPTY when demographic is null")
        void shouldReturnEmpty_whenDemographicNull() {
            assertThat(BillingDemographicSummary.fromDemographic(null))
                    .isSameAs(BillingDemographicSummary.EMPTY);
        }

        @Test
        @DisplayName("should pass through name/HIN/sex unchanged")
        void shouldPassThroughIdentityFields_whenAllPopulated() {
            Demographic d = new Demographic();
            d.setFirstName("Jane");
            d.setLastName("Doe");
            d.setHin("1234567890");
            d.setVer("AB");
            d.setSex("F");
            d.setHcType("ON");
            d.setYearOfBirth("1980");
            d.setMonthOfBirth("01");
            d.setDateOfBirth("15");

            BillingDemographicSummary s = BillingDemographicSummary.fromDemographic(d);
            assertThat(s.firstName()).isEqualTo("Jane");
            assertThat(s.lastName()).isEqualTo("Doe");
            assertThat(s.hin()).isEqualTo("1234567890");
            assertThat(s.ver()).isEqualTo("AB");
            assertThat(s.sex()).isEqualTo("F");
        }

        @Test
        @DisplayName("should default HC type to ON when null")
        void shouldDefaultHcType_toOn_whenNull() {
            Demographic d = new Demographic();
            d.setHcType(null);
            assertThat(BillingDemographicSummary.fromDemographic(d).hcType()).isEqualTo("ON");
        }

        @Test
        @DisplayName("should default HC type to ON when shorter than 2 characters")
        void shouldDefaultHcType_toOn_whenSingleChar() {
            Demographic d = new Demographic();
            d.setHcType("A");
            assertThat(BillingDemographicSummary.fromDemographic(d).hcType()).isEqualTo("ON");
        }

        @Test
        @DisplayName("should truncate and uppercase HC type when longer than 2 characters")
        void shouldNormalizeHcType_toFirstTwoUppercase() {
            Demographic d = new Demographic();
            d.setHcType("ontario");
            assertThat(BillingDemographicSummary.fromDemographic(d).hcType()).isEqualTo("ON");
        }

        @Test
        @DisplayName("should uppercase HC type independently of default JVM locale")
        void shouldNormalizeHcType_withLocaleRoot() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                Demographic d = new Demographic();
                d.setHcType("in");

                assertThat(BillingDemographicSummary.fromDemographic(d).hcType()).isEqualTo("IN");
            } finally {
                Locale.setDefault(original);
            }
        }

        @Test
        @DisplayName("should zero-pad single-digit month and day to two characters")
        void shouldZeroPadDob_whenSingleDigitMonthDay() {
            Demographic d = new Demographic();
            d.setYearOfBirth("1980");
            d.setMonthOfBirth("4");
            d.setDateOfBirth("7");

            BillingDemographicSummary s = BillingDemographicSummary.fromDemographic(d);
            assertThat(s.dobYy()).isEqualTo("1980");
            assertThat(s.dobMm()).isEqualTo("04");
            assertThat(s.dobDd()).isEqualTo("07");
            assertThat(s.dob()).isEqualTo("19800407");
            assertThat(s.dob()).hasSize(8);
        }

        @Test
        @DisplayName("should keep two-digit month and day unchanged")
        void shouldKeepDob_whenAlreadyTwoDigit() {
            Demographic d = new Demographic();
            d.setYearOfBirth("1995");
            d.setMonthOfBirth("11");
            d.setDateOfBirth("23");

            assertThat(BillingDemographicSummary.fromDemographic(d).dob()).isEqualTo("19951123");
        }

        @Test
        @DisplayName("should null-coalesce missing DOB components to empty string")
        void shouldHandleMissingDobComponents_byEmptyString() {
            Demographic d = new Demographic();
            d.setYearOfBirth(null);
            d.setMonthOfBirth(null);
            d.setDateOfBirth(null);

            BillingDemographicSummary s = BillingDemographicSummary.fromDemographic(d);
            assertThat(s.dob()).isEmpty();
            assertThat(s.dobYy()).isEmpty();
            assertThat(s.dobMm()).isEmpty();
            assertThat(s.dobDd()).isEmpty();
        }

        @Test
        @DisplayName("should pass through sex unchanged (not normalize to 1/2)")
        void shouldPassThroughSex_withoutNormalization() {
            Demographic d = new Demographic();
            d.setSex("M");
            assertThat(BillingDemographicSummary.fromDemographic(d).sex()).isEqualTo("M");

            d.setSex("F");
            assertThat(BillingDemographicSummary.fromDemographic(d).sex()).isEqualTo("F");

            d.setSex(null);
            assertThat(BillingDemographicSummary.fromDemographic(d).sex()).isEmpty();
        }
    }
}
