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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.prescript.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * Unit tests verifying correctness and ReDoS-safety of the regex patterns in
 * {@link RxUtil} and {@link RxInstructionPolicy}.
 *
 * <p>The prescription parsing utilities previously contained polynomial-complexity
 * regex patterns of the form {@code \d*\.*\d+} (two adjacent overlapping quantifiers),
 * which create O(n²) backtracking when the overall match fails on attacker-controlled
 * input.  These tests confirm that:
 * <ul>
 *   <li>Normal prescription values are still recognised correctly.</li>
 *   <li>Adversarial inputs (long digit strings, many spaces) complete well within
 *       a strict time budget — proving that catastrophic backtracking has been
 *       eliminated.</li>
 * </ul>
 *
 * @since 2026-04-04
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("RxUtil and RxInstructionPolicy regex safety tests")
class RxUtilRegexUnitTest {

    // -----------------------------------------------------------------------
    // isStringToNumber
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("isStringToNumber")
    class IsStringToNumber {

        @Test
        @DisplayName("should return true for a plain integer")
        void shouldReturnTrue_forPlainInteger() {
            assertThat(RxUtil.isStringToNumber("1")).isTrue();
            assertThat(RxUtil.isStringToNumber("10")).isTrue();
            assertThat(RxUtil.isStringToNumber("100")).isTrue();
        }

        @Test
        @DisplayName("should return true for a decimal with leading zero")
        void shouldReturnTrue_forDecimalWithLeadingZero() {
            assertThat(RxUtil.isStringToNumber("0.5")).isTrue();
            assertThat(RxUtil.isStringToNumber("1.5")).isTrue();
            assertThat(RxUtil.isStringToNumber("2.25")).isTrue();
        }

        @Test
        @DisplayName("should return true for a leading-dot decimal")
        void shouldReturnTrue_forLeadingDotDecimal() {
            assertThat(RxUtil.isStringToNumber(".5")).isTrue();
            assertThat(RxUtil.isStringToNumber(".25")).isTrue();
        }

        @Test
        @DisplayName("should return false for a non-numeric word")
        void shouldReturnFalse_forNonNumericWord() {
            assertThat(RxUtil.isStringToNumber("take")).isFalse();
            assertThat(RxUtil.isStringToNumber("abc")).isFalse();
        }

        @Test
        @DisplayName("should return false for an empty string")
        void shouldReturnFalse_forEmptyString() {
            assertThat(RxUtil.isStringToNumber("")).isFalse();
        }

        @Test
        @DisplayName("should return false for a number followed by non-numeric characters")
        void shouldReturnFalse_forNumberFollowedByLetters() {
            assertThat(RxUtil.isStringToNumber("5mg")).isFalse();
            assertThat(RxUtil.isStringToNumber("1.5mg")).isFalse();
        }

        @Test
        @DisplayName("should complete within time limit for adversarial digit-only input (ReDoS guard)")
        void shouldCompleteInTime_forAdversarialDigitInput() {
            // 100 consecutive digits without a trailing match context.
            // With the old \d*\.*\d+ pattern this forces O(n²) backtracking attempts;
            // with the fixed pattern it runs in O(n).
            String adversarial = "1".repeat(100);
            boolean result = assertTimeout(Duration.ofMillis(500), () -> RxUtil.isStringToNumber(adversarial));
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should complete within time limit for adversarial digit-then-letter input (ReDoS guard)")
        void shouldCompleteInTime_forAdversarialDigitThenLetterInput() {
            // Digits followed by a letter — the trailing letter prevents the outer
            // context from matching, forcing maximum backtracking with the old pattern.
            String adversarial = "1".repeat(100) + "X";
            boolean result = assertTimeout(Duration.ofMillis(500), () -> RxUtil.isStringToNumber(adversarial));
            assertThat(result).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // applyStJoesPolicy — correctness
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("applyStJoesPolicy correctness")
    class ApplyStJoesPolicyCorrectness {

        private List<String> check(String instr) {
            List<String> errors = new ArrayList<>();
            RxInstructionPolicy.applyStJoesPolicy(instr, errors);
            return errors;
        }

        @Test
        @DisplayName("should flag 'U' unit abbreviation in instruction")
        void shouldFlagUAbbreviation_whenPresent() {
            // "Take 5 U daily" — "U" used as an abbreviation for "units" is
            // considered a policy violation (should use "Unit").
            assertThat(check("Take 5 U daily")).isNotEmpty();
        }

        @Test
        @DisplayName("should flag 'I.U.' unit abbreviation in instruction")
        void shouldFlagIUAbbreviation_whenPresent() {
            assertThat(check("Take 100 I.U. daily")).isNotEmpty();
        }

        @Test
        @DisplayName("should flag 'A.U.' both-ears abbreviation in instruction")
        void shouldFlagAUAbbreviation_whenPresent() {
            assertThat(check("instill 2 drops A.U. twice daily")).isNotEmpty();
        }

        @Test
        @DisplayName("should flag 'AU' both-ears abbreviation without dots")
        void shouldFlagAUAbbreviationWithoutDots_whenPresent() {
            assertThat(check("instill 2 drops AU daily")).isNotEmpty();
        }

        @Test
        @DisplayName("should flag 'O.D.' right-eye abbreviation in instruction")
        void shouldFlagODAbbreviation_whenPresent() {
            assertThat(check("instill 1 drop O.D. daily")).isNotEmpty();
        }

        @Test
        @DisplayName("should flag 'O.S.' left-eye abbreviation in instruction")
        void shouldFlagOSAbbreviation_whenPresent() {
            assertThat(check("instill 1 drop O.S. daily")).isNotEmpty();
        }

        @Test
        @DisplayName("should flag 'q.o.d' every-other-day abbreviation")
        void shouldFlagQODAbbreviation_whenPresent() {
            assertThat(check("Take 1 tablet q.o.d.")).isNotEmpty();
        }

        @Test
        @DisplayName("should flag 'qod' every-other-day abbreviation without dots")
        void shouldFlagQODAbbreviationWithoutDots_whenPresent() {
            assertThat(check("Take 1 tablet qod")).isNotEmpty();
        }

        @Test
        @DisplayName("should not flag a clean standard prescription instruction")
        void shouldNotFlag_forCleanInstruction() {
            assertThat(check("Take 1 tablet twice daily with food")).isEmpty();
        }

        @Test
        @DisplayName("should not flag an instruction that contains no prohibited abbreviations")
        void shouldNotFlag_forInstructionWithNoAbbreviations() {
            assertThat(check("Apply to affected area once daily")).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // applyStJoesPolicy — ReDoS safety
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("applyStJoesPolicy ReDoS safety")
    class ApplyStJoesPolicyReDoSSafety {

        private List<String> checkWithTimeout(String instr) {
            return assertTimeout(Duration.ofMillis(500), () -> {
                List<String> errors = new ArrayList<>();
                RxInstructionPolicy.applyStJoesPolicy(instr, errors);
                return errors;
            });
        }

        @Test
        @DisplayName("should complete within time limit for many-space adversarial input")
        void shouldCompleteInTime_forManySpacesInput() {
            // With the old unescaped '.?' patterns, spaces before the abbreviation
            // letters create polynomial backtracking via the \s+ quantifiers.
            String adversarial = " ".repeat(200) + "AU";
            checkWithTimeout(adversarial);
        }

        @Test
        @DisplayName("should complete within time limit for input with spaces and partial abbreviation")
        void shouldCompleteInTime_forPartialAbbreviationInput() {
            // 'A' with trailing spaces but no 'U' — causes many \s+/\.? interaction paths.
            String adversarial = " ".repeat(100) + "A" + " ".repeat(100);
            checkWithTimeout(adversarial);
        }

        @Test
        @DisplayName("should complete within time limit for long instruction with embedded abbreviation letters")
        void shouldCompleteInTime_forLongInstructionWithAbbreviationLetters() {
            // Pattern: lots of text, then 'q', lots of text, no terminating space
            // — tests q.o.d detection with max backtracking for old patterns.
            String adversarial = "Take ".repeat(40) + "q" + " ".repeat(100);
            checkWithTimeout(adversarial);
        }

        @Test
        @DisplayName("should complete within time limit for input with many digits and spaces")
        void shouldCompleteInTime_forDigitsAndSpacesInput() {
            // Tests the \b\d+U\b type patterns under adversarial conditions.
            String adversarial = "1".repeat(100) + " " + "U".repeat(50);
            checkWithTimeout(adversarial);
        }
    }
}

