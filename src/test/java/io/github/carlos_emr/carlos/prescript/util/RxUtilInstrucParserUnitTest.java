/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Unit tests for {@link RxUtil#instrucParser(RxPrescriptionData.Prescription)}.
 *
 * <p>Covers regex injection safety (CodeQL CWE-730) for the pattern-building
 * code in the word-amount branch that concatenates user-derived {@code frequency}
 * into {@code Pattern.compile()} calls. The fix applies {@code Pattern.quote(frequency)}
 * so that any regex metacharacters in the frequency string are treated as literals
 * and cannot alter the compiled pattern or cause
 * {@link java.util.regex.PatternSyntaxException}.</p>
 *
 * @since 2026-04-04
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("RxUtil.instrucParser unit tests")
class RxUtilInstrucParserUnitTest extends CarlosUnitTestBase {

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static RxPrescriptionData.Prescription prescriptionWithSpecial(String special) {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "000000", 0);
        rx.setSpecial(special);
        return rx;
    }

    @Nested
    @DisplayName("strengths and dispense quantities (issue #3876)")
    class StrengthQuantities {

        @ParameterizedTest(name = "{0} gives 21 units")
        @ValueSource(strings = {
            "1000 mg po tid x 7 days", "Take 1000 mg po tid x 7 days",
            "1000mg po tid x 7 days", "Take 1000MG po tid x 7 days",
            "5 mg po tid x 7 days", "0.5 mg po tid x 7 days", ".5mg po tid x 7 days",
            "250 mcg po tid x 7 days", "250 µg po tid x 7 days", "250 μg po tid x 7 days",
            "1 g po tid x 7 days", "2g po tid x 7 days", "Take 2g po tid x 7 days",
            "1000 IU po tid x 7 days", "20 mEq po tid x 7 days",
            "500-1000 mg po tid x 7 days", "500-1000mg po tid x 7 days",
            "1/2 g po tid x 7 days", "1/2g po tid x 7 days"
        })
        void shouldDefaultToOneUnit_whenLeadingNumberHasStrengthUnit(String instructions) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(1f);
            assertThat(rx.getTakeMax()).isEqualTo(1f);
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("TID");
            assertThat(rx.getDuration()).isEqualTo("7");
            assertThat(rx.getDurationUnit()).isEqualTo("D");
            assertThat(rx.getQuantity()).isEqualTo("21");
        }

        @ParameterizedTest(name = "{0} keeps dose {1}-{2}")
        @CsvSource(delimiter = '|', value = {
            "2 tabs po tid x 7 days | 2 | 2 | 42",
            "Take 2 tablets (1000 mg) po tid x 7 days | 2 | 2 | 42",
            "1000 mg po 2 tid x 7 days | 2 | 2 | 42",
            "1000mg po 2 tid x 7 days | 2 | 2 | 42",
            "1000 mg po 1-2 tid x 7 days | 1 | 2 | 42",
            "1000 mg po 2-10 tid x 7 days | 2 | 10 | 210",
            "1000 mg po 0 tid x 7 days | 0 | 0 | 0",
            "Take 20 tabs (20 mg) po tid x 7 days | 20 | 20 | 420",
            "1/2 tab po tid x 8 days | 0.5 | 0.5 | 12",
            "0.5 tab po tid x 8 days | 0.5 | 0.5 | 12",
            "1-2 tabs po tid x 7 days | 1 | 2 | 42",
            "two tablets po tid x 7 days | 2 | 2 | 42",
            "Take 5 mL po tid x 7 days | 5 | 5 | 105",
            "Take 5mL po tid x 7 days | 5 | 5 | 105",
            "1/2mL po tid x 8 days | 0.5 | 0.5 | 12",
            "5-10mL po tid x 7 days | 5 | 10 | 210",
            "10-5mL po tid x 7 days | 5 | 10 | 210",
            "20 units po tid x 7 days | 20 | 20 | 420",
            "20units po tid x 7 days | 20 | 20 | 420",
            "Apply 2 g BID x 7 days | 2 | 2 | 28",
            "Apply 2g BID x 7 days | 2 | 2 | 28",
            "Rub well in 2 g BID x 7 days | 2 | 2 | 28",
            "rub WELL IN 2G BID x 7 days | 2 | 2 | 28",
            "Apply 0.5g BID x 7 days | 0.5 | 0.5 | 7",
            "Apply 1/2g BID x 7 days | 0.5 | 0.5 | 7",
            "Apply 2-5g BID x 7 days | 2 | 5 | 70",
            "Apply 5-2g BID x 7 days | 2 | 5 | 70",
            "Apply 5mg BID x 7 days | 1 | 1 | 14",
            "2 g TOP. BID x 7 days | 2 | 2 | 28",
            "2g top. BID x 7 days | 2 | 2 | 28",
            "1/2g TOP. BID x 7 days | 0.5 | 0.5 | 7",
            "2-5g TOP. BID x 7 days | 2 | 5 | 70",
            "5mg TOP. BID x 7 days | 1 | 1 | 14",
            "2 mgtablets po tid x 7 days | 2 | 2 | 42"
        })
        void shouldPreserveDoseCounts_whenInstructionsSpecifyAdministeredAmounts(String instructions,
                float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }
    }

    // -------------------------------------------------------------------------
    // Null / empty guard
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("null and empty instructions")
    class NullAndEmpty {

        @Test
        @DisplayName("should not throw when prescription is null")
        void shouldNotThrow_whenPrescriptionIsNull() {
            assertThatCode(() -> RxUtil.instrucParser(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not throw when special is null")
        void shouldNotThrow_whenSpecialIsNull() {
            RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "000000", 0);
            assertThatCode(() -> RxUtil.instrucParser(rx)).doesNotThrowAnyException();
        }
    }

    // -------------------------------------------------------------------------
    // Normal frequency parsing
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normal frequency parsing")
    class NormalFrequency {

        @Test
        @DisplayName("should parse OD frequency from instructions ending with OD")
        void shouldParseOdFrequency_whenInstructionsEndWithOd() {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial("Take one tablet OD");
            RxUtil.instrucParser(rx);
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("OD");
        }

        @Test
        @DisplayName("should parse BID frequency from instructions ending with BID")
        void shouldParseBidFrequency_whenInstructionsEndWithBid() {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial("Take one tablet BID");
            RxUtil.instrucParser(rx);
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("BID");
        }

        @Test
        @DisplayName("should parse OD frequency from 'once daily' synonym")
        void shouldParseOdFrequency_whenOnceDailySynonymUsed() {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial("Take one tablet once daily");
            RxUtil.instrucParser(rx);
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("OD");
        }
    }

    // -------------------------------------------------------------------------
    // Word-amount code path — the fixed injection site
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("word-amount frequency parsing (regex injection fix)")
    class WordAmountFrequency {

        @Test
        @DisplayName("should parse word amount 'one OD' without PatternSyntaxException")
        void shouldParseWordAmount_withOdFrequency() {
            // Exercises the word-amount else branch which builds Pattern.compile(r1/r2)
            // using the frequency value with Pattern.quote() applied as defense-in-depth.
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial("one OD");
            assertThatCode(() -> RxUtil.instrucParser(rx)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should parse word amount 'two BID' without PatternSyntaxException")
        void shouldParseWordAmount_withBidFrequency() {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial("two BID");
            assertThatCode(() -> RxUtil.instrucParser(rx)).doesNotThrowAnyException();
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("BID");
        }

        @ParameterizedTest(name = "word amount ''{0}'' with OD")
        @ValueSource(strings = {
            "one OD",
            "two OD",
            "three OD",
            "four OD"
        })
        @DisplayName("should not throw for various word amounts with OD frequency")
        void shouldNotThrow_forVariousWordAmountsWithOdFrequency(String special) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(special);
            assertThatCode(() -> RxUtil.instrucParser(rx)).doesNotThrowAnyException();
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("OD");
        }
    }

    // -------------------------------------------------------------------------
    // Regex injection safety (CWE-730 / CodeQL alerts)
    // -------------------------------------------------------------------------

    /**
     * The {@code frequency} variable at the injection sites is always a standard
     * medical code extracted from a fixed {@code frequences} array (e.g. "OD",
     * "BID", "Q3-4H"). Those codes do not contain regex metacharacters in practice,
     * so direct regex injection via frequency is not currently exploitable. However,
     * {@code Pattern.quote(frequency)} is applied as defense-in-depth (consistent
     * with the same safeguard already used at the numeric-amount regex sites) to
     * guard against future additions to the frequency table and to satisfy CodeQL.
     *
     * <p>These tests verify two complementary properties:</p>
     * <ol>
     *   <li>The full instruction string can contain regex metacharacters without
     *       causing a {@link java.util.regex.PatternSyntaxException}.</li>
     *   <li>Frequencies with hyphen-range syntax (Q3-4H, Q4-6H, Q1-2H) — the
     *       closest real-world values to metacharacter-containing strings — are
     *       handled correctly through the word-amount code path.</li>
     * </ol>
     */
    @Nested
    @DisplayName("regex injection safety")
    class RegexInjectionSafety {

        @Test
        @DisplayName("should not throw PatternSyntaxException for instructions with regex metacharacters in the special string")
        void shouldNotThrowPatternSyntaxException_forMetacharactersInSpecialString() {
            // The full special string is user-derived. Verify that metacharacters
            // present elsewhere in the instruction string do not cause PatternSyntaxException
            // when instrucParser processes the recognized frequency.
            String[] adversarialPrefixes = {
                "(a+)+",      // catastrophic backtracking
                ".*.*.*",     // nested wildcards
                "[invalid",   // unclosed character class
                "(?invalid)", // invalid group
                "one\\Q OD",  // \\Q...\\E is Pattern.quote()'s own escape syntax; ensures
                              // user input containing \\Q does not break the quoting wrapper
            };
            for (String prefix : adversarialPrefixes) {
                // Embed the adversarial text before the recognized frequency so that
                // frequency detection still finds "OD" while the rest of the string
                // contains potential injection content.
                String special = prefix + " OD";
                RxPrescriptionData.Prescription rx = prescriptionWithSpecial(special);
                final String label = special;
                assertThatCode(() -> RxUtil.instrucParser(rx))
                    .as("instrucParser should not throw for special=%s", label)
                    .doesNotThrowAnyException();
            }
        }

        @ParameterizedTest(name = "word-amount path with hyphenated frequency ''{0}''")
        @ValueSource(strings = {"Q1-2H", "Q3-4H", "Q4-6H"})
        @DisplayName("should handle hyphenated frequency codes through the word-amount path without PatternSyntaxException")
        void shouldHandleHyphenatedFrequency_throughWordAmountPath(String frequencyCode) {
            // Hyphenated codes (Q3-4H, Q4-6H, Q1-2H) are the real values that come
            // closest to containing regex metacharacters — the hyphen is literal
            // outside a character class but signals a range inside one. Pattern.quote()
            // ensures the frequency is always treated as a literal regardless.
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial("one " + frequencyCode);
            assertThatCode(() -> RxUtil.instrucParser(rx)).doesNotThrowAnyException();
        }
    }
}
