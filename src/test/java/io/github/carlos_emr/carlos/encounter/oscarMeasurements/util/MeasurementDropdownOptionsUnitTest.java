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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import io.github.carlos_emr.carlos.commn.model.Validations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MeasurementDropdownOptions}, which builds the Add Measurement dropdown
 * and flags values recorded under an earlier validation rule (issue #3893, AACP).
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
@DisplayName("MeasurementDropdownOptions")
class MeasurementDropdownOptionsUnitTest {

    private static final List<String> AACP_OPTIONS = List.of("Provided", "Revised", "Reviewed");

    private static Validations validation(String name, String regularExp) {
        Validations v = new Validations();
        v.setName(name);
        v.setRegularExp(regularExp);
        return v;
    }

    /** Option derivation from the validation rule. */
    @Nested
    @DisplayName("forValidation")
    class ForValidation {

        @Test
        @DisplayName("should split a slash-separated rule name into options")
        void shouldReturnNameSegments_whenNameIsSlashSeparated() {
            Validations aacp = validation("Provided/Revised/Reviewed", "Provided|Revised|Reviewed");

            assertThat(MeasurementDropdownOptions.forValidation(aacp)).isEqualTo(AACP_OPTIONS);
        }

        @Test
        @DisplayName("should prefer the short name over the many spellings in the pattern")
        void shouldReturnNameSegments_forYesNoNaRule() {
            Validations yesNoNa = validation("Yes/No/NA", "YES|yes|Yes|Y|NO|no|No|N|NotApplicable|NA");

            assertThat(MeasurementDropdownOptions.forValidation(yesNoNa)).containsExactly("Yes", "No", "NA");
        }

        @Test
        @DisplayName("should split the pattern when the name has no slash")
        void shouldReturnPatternAlternatives_whenNameHasNoSlash() {
            Validations review = validation("Review", "REVIEWED|reviewed|Reviewed");

            assertThat(MeasurementDropdownOptions.forValidation(review))
                    .containsExactly("REVIEWED", "reviewed", "Reviewed");
        }

        @Test
        @DisplayName("should return no options when the rule is missing or has no pattern")
        void shouldReturnEmpty_whenRuleHasNoOptions() {
            assertThat(MeasurementDropdownOptions.forValidation(null)).isEmpty();
            assertThat(MeasurementDropdownOptions.forValidation(validation("Date", null))).isEmpty();
            assertThat(MeasurementDropdownOptions.forValidation(validation(null, ""))).isEmpty();
        }
    }

    /** Option derivation from the rule name alone, as the Health Tracker has it. */
    @Nested
    @DisplayName("forValidationName")
    class ForValidationName {

        @Test
        @DisplayName("should offer the three AACP choices from the Provided/Revised/Reviewed name")
        void shouldReturnNameSegments_forProvidedRevisedReviewedName() {
            assertThat(MeasurementDropdownOptions.forValidationName("Provided/Revised/Reviewed"))
                    .containsExactlyElementsOf(AACP_OPTIONS);
        }

        @Test
        @DisplayName("should return no options when the name is null or has no slash")
        void shouldReturnEmpty_whenNameDoesNotEnumerateOptions() {
            assertThat(MeasurementDropdownOptions.forValidationName(null)).isEmpty();
            assertThat(MeasurementDropdownOptions.forValidationName("Review")).isEmpty();
            assertThat(MeasurementDropdownOptions.forValidationName("Numeric Value: 0 to 10")).isEmpty();
        }

        @Test
        @DisplayName("should agree with forValidation for a slash-named rule")
        void shouldMatchForValidation_forSlashNamedRule() {
            Validations aacp = validation("Provided/Revised/Reviewed", "Provided|Revised|Reviewed");

            assertThat(MeasurementDropdownOptions.forValidationName(aacp.getName()))
                    .isEqualTo(MeasurementDropdownOptions.forValidation(aacp));
        }
    }

    /** Detection of values recorded under an earlier rule. */
    @Nested
    @DisplayName("isLegacyValue")
    class IsLegacyValue {

        @Test
        @DisplayName("should flag a Yes/No value stored before AACP moved to Provided/Revised/Reviewed")
        void shouldReturnTrue_forLegacyYesNoAacpValue() {
            assertThat(MeasurementDropdownOptions.isLegacyValue(AACP_OPTIONS, "Yes")).isTrue();
            assertThat(MeasurementDropdownOptions.isLegacyValue(AACP_OPTIONS, "No")).isTrue();
        }

        @Test
        @DisplayName("should not flag a value that is one of the current options")
        void shouldReturnFalse_whenValueIsCurrentOption() {
            assertThat(MeasurementDropdownOptions.isLegacyValue(AACP_OPTIONS, "Revised")).isFalse();
        }

        @Test
        @DisplayName("should compare case-sensitively, like the page's pre-selection")
        void shouldReturnTrue_whenCaseDiffers() {
            assertThat(MeasurementDropdownOptions.isLegacyValue(AACP_OPTIONS, "provided")).isTrue();
        }

        @Test
        @DisplayName("should not flag a missing or empty value, so new readings get no extra option")
        void shouldReturnFalse_whenValueIsNullOrEmpty() {
            assertThat(MeasurementDropdownOptions.isLegacyValue(AACP_OPTIONS, null)).isFalse();
            assertThat(MeasurementDropdownOptions.isLegacyValue(AACP_OPTIONS, "")).isFalse();
        }

        @Test
        @DisplayName("should flag any stored value when there are no options")
        void shouldReturnTrue_whenOptionsAreNull() {
            assertThat(MeasurementDropdownOptions.isLegacyValue(null, "Yes")).isTrue();
        }
    }
}
