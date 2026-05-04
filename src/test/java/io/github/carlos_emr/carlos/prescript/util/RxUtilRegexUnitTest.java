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
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests verifying correctness and ReDoS-safety of the regex patterns in
 * {@link RxUtil} and {@link RxInstructionPolicy}.
 *
 * <p>The prescription parsing utilities previously contained polynomial-complexity
 * regex patterns of the form {@code \d*\.*\d+} (two adjacent overlapping quantifiers),
 * which create O(n²) backtracking when embedded in compound patterns and the overall
 * match fails on attacker-controlled input (e.g., {@code \s*\d*\.*\d+\s+FREQUENCY}
 * where the trailing {@code \s+FREQUENCY} cannot match). These tests confirm that:
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
    // isStringToNumber — correctness
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
            // Verifies the replacement pattern maintains correct behavior on long
            // digit strings. The old \d*\.*\d+ overlapping quantifiers cause O(n²)
            // backtracking when embedded in compound patterns elsewhere in RxUtil;
            // this test confirms the standalone isStringToNumber replacement is safe.
            String adversarial = "1".repeat(100);
            boolean result = assertTimeoutPreemptively(Duration.ofMillis(500), () -> RxUtil.isStringToNumber(adversarial));
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should complete within time limit for adversarial digit-then-letter input (ReDoS guard)")
        void shouldCompleteInTime_forAdversarialDigitThenLetterInput() {
            // Digits followed by a letter — verifies the method correctly returns
            // false when non-numeric trailing characters are present, and that the
            // new pattern completes in linear time.
            String adversarial = "1".repeat(100) + "X";
            boolean result = assertTimeoutPreemptively(Duration.ofMillis(500), () -> RxUtil.isStringToNumber(adversarial));
            assertThat(result).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // instrucParser — regex pattern correctness for dosage parsing
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("instrucParser dosage parsing")
    class InstrucParserDosageParsing {

        private RxPrescriptionData.Prescription parse(String instructions) {
            RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "999", 1);
            rx.setSpecial(instructions);
            RxUtil.instrucParser(rx);
            return rx;
        }

        @Test
        @DisplayName("should parse integer dosage with frequency")
        void shouldParseIntegerDosage_withFrequency() {
            RxPrescriptionData.Prescription rx = parse("Take 1 BID ");
            assertThat(rx.getTakeMax()).isEqualTo(1.0f);
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("BID");
        }

        @Test
        @DisplayName("should parse decimal dosage with frequency")
        void shouldParseDecimalDosage_withFrequency() {
            RxPrescriptionData.Prescription rx = parse("Take 1.5 BID ");
            assertThat(rx.getTakeMax()).isEqualTo(1.5f);
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("BID");
        }

        @Test
        @DisplayName("should parse leading-dot decimal dosage with frequency")
        void shouldParseLeadingDotDecimalDosage_withFrequency() {
            RxPrescriptionData.Prescription rx = parse("Take .5 BID ");
            assertThat(rx.getTakeMax()).isEqualTo(0.5f);
        }

        @Test
        @DisplayName("should parse dosage range with frequency")
        void shouldParseDosageRange_withFrequency() {
            RxPrescriptionData.Prescription rx = parse("Take 1-2 BID ");
            assertThat(rx.getTakeMin()).isEqualTo(1.0f);
            assertThat(rx.getTakeMax()).isEqualTo(2.0f);
        }

        @Test
        @DisplayName("should parse decimal dosage range with frequency")
        void shouldParseDecimalDosageRange_withFrequency() {
            RxPrescriptionData.Prescription rx = parse("Take 0.5-1.5 BID ");
            assertThat(rx.getTakeMin()).isEqualTo(0.5f);
            assertThat(rx.getTakeMax()).isEqualTo(1.5f);
        }

        @Test
        @DisplayName("should parse fraction 1/2 dosage")
        void shouldParseFractionHalf_withFrequency() {
            RxPrescriptionData.Prescription rx = parse("Take 1/2 BID ");
            assertThat(rx.getTakeMax()).isEqualTo(0.5f);
        }

        @Test
        @DisplayName("should parse OD frequency")
        void shouldParseODFrequency() {
            RxPrescriptionData.Prescription rx = parse("Take 1 OD ");
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("OD");
        }

        @Test
        @DisplayName("should complete instrucParser within time limit for adversarial input")
        void shouldCompleteInTime_forAdversarialInput() {
            // This exercises the compound patterns (\s*DECIMAL\s+FREQUENCY) that
            // were the actual O(n²) ReDoS vectors in the old code. Long digit
            // strings with no matching frequency cause maximum backtracking.
            String adversarial = "Take " + "1".repeat(100) + " notafrequency ";
            RxPrescriptionData.Prescription rx = assertTimeoutPreemptively(Duration.ofMillis(500), () -> parse(adversarial));
            assertThat(rx).isNotNull();
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
            List<String> errors = check("Take 5 U daily");
            assertThat(errors).anyMatch(e -> e.contains("'U'") && e.contains("Unit"));
        }

        @Test
        @DisplayName("should flag 'I.U.' unit abbreviation in instruction")
        void shouldFlagIUAbbreviation_whenPresent() {
            List<String> errors = check("Take 100 I.U. daily");
            assertThat(errors).anyMatch(e -> e.contains("I.U.") && e.contains("Unit"));
        }

        @Test
        @DisplayName("should flag 'S.C.' subcutaneous abbreviation in instruction")
        void shouldFlagSCAbbreviation_whenPresent() {
            List<String> errors = check("Inject 5mg S.C. daily");
            assertThat(errors).anyMatch(e -> e.contains("S.C.") && e.contains("ubcutaneous"));
        }

        @Test
        @DisplayName("should flag 'C.C.' abbreviation in instruction")
        void shouldFlagCCAbbreviation_whenPresent() {
            List<String> errors = check("Give 5 C.C. daily");
            assertThat(errors).anyMatch(e -> e.contains("C.C.") && e.contains("ml"));
        }

        @Test
        @DisplayName("should flag 'A.U.' both-ears abbreviation in instruction")
        void shouldFlagAUAbbreviation_whenPresent() {
            List<String> errors = check("instill 2 drops A.U. twice daily");
            assertThat(errors).anyMatch(e -> e.contains("A.U.") && e.contains("both ears"));
        }

        @Test
        @DisplayName("should flag 'AU' both-ears abbreviation without dots")
        void shouldFlagAUAbbreviationWithoutDots_whenPresent() {
            List<String> errors = check("instill 2 drops AU daily");
            assertThat(errors).anyMatch(e -> e.contains("A.U.") && e.contains("both ears"));
        }

        @Test
        @DisplayName("should flag 'A.S.' left-ear abbreviation in instruction")
        void shouldFlagASAbbreviation_whenPresent() {
            List<String> errors = check("instill 2 drops A.S. daily");
            assertThat(errors).anyMatch(e -> e.contains("A.S.") && e.contains("left ear"));
        }

        @Test
        @DisplayName("should flag 'A.D.' right-ear abbreviation in instruction")
        void shouldFlagADAbbreviation_whenPresent() {
            List<String> errors = check("instill 2 drops A.D. daily");
            assertThat(errors).anyMatch(e -> e.contains("A.D.") && e.contains("right ear"));
        }

        @Test
        @DisplayName("should flag 'O.D.' right-eye abbreviation in instruction")
        void shouldFlagODAbbreviation_whenPresent() {
            List<String> errors = check("instill 1 drop O.D. daily");
            assertThat(errors).anyMatch(e -> e.contains("O.D.") && e.contains("right eye"));
        }

        @Test
        @DisplayName("should flag 'O.S.' left-eye abbreviation in instruction")
        void shouldFlagOSAbbreviation_whenPresent() {
            List<String> errors = check("instill 1 drop O.S. daily");
            assertThat(errors).anyMatch(e -> e.contains("O.S.") && e.contains("left eye"));
        }

        @Test
        @DisplayName("should flag 'O.U.' both-eyes abbreviation in instruction")
        void shouldFlagOUAbbreviation_whenPresent() {
            List<String> errors = check("instill 1 drop O.U. daily");
            assertThat(errors).anyMatch(e -> e.contains("O.U.") && e.contains("both eyes"));
        }

        @Test
        @DisplayName("should flag 'q.o.d' every-other-day abbreviation")
        void shouldFlagQODAbbreviation_whenPresent() {
            List<String> errors = check("Take 1 tablet q.o.d. daily");
            assertThat(errors).anyMatch(e -> e.contains("q.o.d") && e.contains("every other day"));
        }

        @Test
        @DisplayName("should flag 'qod' every-other-day abbreviation without dots")
        void shouldFlagQODAbbreviationWithoutDots_whenPresent() {
            List<String> errors = check("Take 1 tablet qod daily");
            assertThat(errors).anyMatch(e -> e.contains("q.o.d") && e.contains("every other day"));
        }

        @Test
        @DisplayName("should flag 'q.d' daily abbreviation")
        void shouldFlagQDAbbreviation_whenPresent() {
            List<String> errors = check("Take 1 tablet q.d. daily");
            assertThat(errors).anyMatch(e -> e.contains("q.d") && e.contains("daily"));
        }

        @Test
        @DisplayName("should flag 'o.d.' daily abbreviation")
        void shouldFlagODDailyAbbreviation_whenPresent() {
            List<String> errors = check("Take 1 tablet o.d. daily");
            assertThat(errors).anyMatch(e -> e.contains("o.d.") && e.contains("daily"));
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
    // applyStJoesPolicy — behavioral bug fixes
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("applyStJoesPolicy bug fix regressions")
    class ApplyStJoesPolicyBugFixes {

        private List<String> check(String instr) {
            List<String> errors = new ArrayList<>();
            RxInstructionPolicy.applyStJoesPolicy(instr, errors);
            return errors;
        }

        @Test
        @DisplayName("should detect I.U. followed by space (\\b→lookahead fix)")
        void shouldDetectIU_whenFollowedBySpace() {
            // The old \b after a dot-terminated abbreviation required the next
            // character to be a word character. This fix uses (?=\s|$) instead.
            List<String> errors = check("100I.U. daily");
            assertThat(errors).anyMatch(e -> e.contains("I.U.") && e.contains("Unit"));
        }

        @Test
        @DisplayName("should detect I.U. at end of string (\\b→lookahead fix)")
        void shouldDetectIU_atEndOfString() {
            List<String> errors = check("100I.U.");
            assertThat(errors).anyMatch(e -> e.contains("I.U.") && e.contains("Unit"));
        }

        @Test
        @DisplayName("should detect S.C. followed by space (\\b→lookahead fix)")
        void shouldDetectSC_whenFollowedBySpace() {
            List<String> errors = check("Inject S.C. daily");
            assertThat(errors).anyMatch(e -> e.contains("S.C.") && e.contains("ubcutaneous"));
        }

        @Test
        @DisplayName("should detect C.C. followed by space (\\b→lookahead fix)")
        void shouldDetectCC_whenFollowedBySpace() {
            List<String> errors = check("Give 5C.C. daily");
            assertThat(errors).anyMatch(e -> e.contains("C.C.") && e.contains("ml"));
        }

        @Test
        @DisplayName("should detect AD with leading whitespace (^s+→^\\s+ fix)")
        void shouldDetectAD_withLeadingWhitespace() {
            // The old pattern had ^s+ (literal 's') instead of ^\s+ (whitespace).
            // This verifies that whitespace-prefixed instructions are now caught.
            List<String> errors = check(" AD daily");
            assertThat(errors).anyMatch(e -> e.contains("A.D.") && e.contains("right ear"));
        }

        @Test
        @DisplayName("should detect qd with leading whitespace (^s+→^\\s+ fix)")
        void shouldDetectQD_withLeadingWhitespace() {
            List<String> errors = check(" qd daily");
            assertThat(errors).anyMatch(e -> e.contains("q.d") && e.contains("daily"));
        }

        @Test
        @DisplayName("should not flag non-standard separators after dot escaping (.?→\\.? fix)")
        void shouldNotFlag_forNonStandardSeparators() {
            // The old .? matched any character between abbreviation letters.
            // After escaping to \.?, only literal dots should match.
            // "O S" with a space between is NOT a valid abbreviation.
            assertThat(check("instill 1 drop O S daily")).noneMatch(e -> e.contains("O.S."));
        }
    }

    // -----------------------------------------------------------------------
    // applyStJoesPolicy — ReDoS safety
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("applyStJoesPolicy ReDoS safety")
    class ApplyStJoesPolicyReDoSSafety {

        private List<String> checkWithTimeout(String instr) {
            return assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
                List<String> errors = new ArrayList<>();
                RxInstructionPolicy.applyStJoesPolicy(instr, errors);
                return errors;
            });
        }

        @Test
        @DisplayName("should complete within time limit for many-space adversarial input")
        void shouldCompleteInTime_forManySpacesInput() {
            // The old unescaped .? (matching any character) overlaps with \s+ when
            // .? matches whitespace, creating multiple match paths at each position.
            // With escaped \.? (literal dot only), this overlap is eliminated.
            String adversarial = " ".repeat(200) + "AU";
            List<String> errors = checkWithTimeout(adversarial);
            assertThat(errors).as("AU at end of whitespace-heavy input should still be flagged")
                    .anyMatch(e -> e.contains("A.U."));
        }

        @Test
        @DisplayName("should complete within time limit for input with spaces and partial abbreviation")
        void shouldCompleteInTime_forPartialAbbreviationInput() {
            // 'A' with trailing spaces but no 'U' — no A.U. match expected.
            String adversarial = " ".repeat(100) + "A" + " ".repeat(100);
            List<String> errors = checkWithTimeout(adversarial);
            assertThat(errors).noneMatch(e -> e.contains("A.U."));
        }

        @Test
        @DisplayName("should complete within time limit for long instruction with embedded abbreviation letters")
        void shouldCompleteInTime_forLongInstructionWithAbbreviationLetters() {
            // Pattern: lots of text, then 'q', lots of text, no terminating space
            // — tests q.o.d detection with max backtracking for old patterns.
            String adversarial = "Take ".repeat(40) + "q" + " ".repeat(100);
            List<String> errors = checkWithTimeout(adversarial);
            assertThat(errors).noneMatch(e -> e.contains("q.o.d"));
        }

        @Test
        @DisplayName("should complete within time limit for input with many digits and spaces")
        void shouldCompleteInTime_forDigitsAndSpacesInput() {
            // Tests the \b\d+U\b type patterns under adversarial conditions.
            String adversarial = "1".repeat(100) + " " + "U".repeat(50);
            List<String> errors = checkWithTimeout(adversarial);
            assertThat(errors).isNotNull();
        }
    }
}
