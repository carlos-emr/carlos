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
import static org.assertj.core.api.Assertions.within;

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

        /** Product strengths must not multiply the administered unit count. */
        @ParameterizedTest(name = "{0} gives 21 units")
        @ValueSource(strings = {
            "1000 mg po tid x 7 days", "Take 1000 mg po tid x 7 days",
            "1000mg po tid x 7 days", "Take 1000MG po tid x 7 days",
            "5 mg po tid x 7 days", "0.5 mg po tid x 7 days", ".5mg po tid x 7 days",
            "250 mcg po tid x 7 days", "250 µg po tid x 7 days", "250 μg po tid x 7 days",
            "1 g po tid x 7 days", "2g po tid x 7 days", "Take 2g po tid x 7 days",
            "2g TID x 7 days", "Take 1000 mg TID x 7 days", "1000mg TID x 7 days",
            "1000 IU po tid x 7 days", "20 mEq po tid x 7 days",
            "20 IU po tid x 7 days", "20IU po tid x 7 days", "20 IU PO TID x 7 days",
            "20IU sl tid x 7 days",
            "1/2 g po tid x 7 days", "1/2g po tid x 7 days",
            "1000 mgs po tid x 7 days", "Take 1000 milligrams po tid x 7 days",
            "250 mcgs po tid x 7 days", "250 ug po tid x 7 days", "250 micrograms po tid x 7 days",
            "20 mmol po tid x 7 days", "500 mg per dose PO tid x 7 days", "500mg/dose PO tid x 7 days",
            "20 mEq per dose PO tid x 7 days", "250 mcg/dose INH tid x 7 days"
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

        /**
         * A concentration names the product, not an administered amount: no dose is
         * invented and a zero quantity tells the Rx page to keep the card's quantity.
         */
        @ParameterizedTest(name = "{0} leaves the dose and quantity unset")
        @ValueSource(strings = {
            "100units/mL SC BID x 7 days", "250mg/5mL PO TID x 7 days",
            "Take 250 mg/5 mL PO TID x 7 days", "250 mg per 5 mL PO TID x 7 days",
            "Take 250 mg per 5 mL TID x 7 days", "Take 250 mg / 5 mL TID x 7 days",
            "250 mg/5 mL TID x 7 days", "100 IU per 1 mL BID x 7 days", "100IU/5mL tid x 7 days",
            "100IU/mL tid x 7 days", "100IU/5mL IV tid x 7 days",
            "100 IU/mL SC tid x 7 days", "100IU/mL SC tid x 7 days",
            "100 IU/5mL IM tid x 7 days", "100 IU / 5 mL SC tid x 7 days",
            "Apply 2g/g tid x 7 days", "2g/g TOP. tid x 7 days",
            "100IU per mL SC tid x 7 days", "Apply 2g per g tid x 7 days",
            "100units/mL SC tid x 7 days", "100 units/mL SC tid x 7 days",
            "100unit/mL SC tid x 7 days", "100UNITS per 5mL IM tid x 7 days",
            "50-100units/mL SC tid x 7 days", "1/2units/mL SC tid x 7 days"
        })
        void shouldLeaveDoseUnset_whenInstructionsGiveOnlyAConcentration(String instructions) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            rx.setQuantity("200");
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(0f);
            assertThat(rx.getTakeMax()).isEqualTo(0f);
            assertThat(rx.getFrequencyCode()).isNotEmpty();
            assertThat(rx.getDuration()).isEqualTo("7");
            assertThat(rx.getQuantity()).isEqualTo("0");
        }

        /** Administered amounts retain their dose bounds and round fractional dispense totals upward. */
        @ParameterizedTest(name = "{0} keeps dose {1}-{2}")
        @CsvSource(delimiter = '|', value = {
            "2 tabs po tid x 7 days | 2 | 2 | 42",
            "Take 2 tablets (1000 mg) po tid x 7 days | 2 | 2 | 42",
            "1000 mg po 2 tid x 7 days | 2 | 2 | 42",
            "1000mg po 2 tid x 7 days | 2 | 2 | 42",
            "1000 mg po 1-2 tid x 7 days | 1 | 2 | 42",
            "1000 mg po 2-10 tid x 7 days | 2 | 10 | 210",
            "1000 mg po 0 tid x 7 days | 0 | 0 | 0",
            "1000 mg po 1/2 tid x 8 days | 0.5 | 0.5 | 12",
            "Take 20 tabs (20 mg) po tid x 7 days | 20 | 20 | 420",
            "1/2 tab po tid x 8 days | 0.5 | 0.5 | 12",
            "0.5 tab po tid x 8 days | 0.5 | 0.5 | 12",
            "1-2 tabs po tid x 7 days | 1 | 2 | 42",
            "two tablets po tid x 7 days | 2 | 2 | 42",
            "Take 5 mL po tid x 7 days | 5 | 5 | 105",
            "Take 5mL po tid x 7 days | 5 | 5 | 105",
            "Take 5 mL tid x 7 days | 5 | 5 | 105",
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
            "20 IU SC BID x 7 days | 20 | 20 | 280",
            "20IU sc BID x 7 days | 20 | 20 | 280",
            "20IU IM BID x 7 days | 20 | 20 | 280",
            "20 IU qhs x 30 days | 20 | 20 | 600",
            "20IU qhs x 30 days | 20 | 20 | 600",
            "20IU IV qhs x 30 days | 20 | 20 | 600",
            "1/2IU qhs x 8 days | 0.5 | 0.5 | 4",
            "10-20IU IV qhs x 30 days | 10 | 20 | 600",
            "20IU INH qhs x 30 days | 20 | 20 | 600",
            "20 units SC BID x 7 days | 20 | 20 | 280",
            "20units SC BID x 7 days | 20 | 20 | 280",
            "1/2units SC OD x 7 days | 0.5 | 0.5 | 4",
            "0.5IU SC BID x 8 days | 0.5 | 0.5 | 8",
            "1/2IU SC BID x 8 days | 0.5 | 0.5 | 8",
            "10-20IU SC BID x 7 days | 10 | 20 | 280",
            "20-10IU IM BID x 7 days | 10 | 20 | 280",
            "0IU SC BID x 7 days | 0 | 0 | 0",
            "100IU/mL SC 2 tid x 7 days | 2 | 2 | 42",
            "20IU/dose SC BID x 7 days | 20 | 20 | 280",
            "20IU per dose SC BID x 7 days | 20 | 20 | 280",
            "20 IU per dose PO BID x 7 days | 20 | 20 | 280",
            "20IU/dose PO BID x 7 days | 20 | 20 | 280",
            "20IU PER DOSE sl BID x 7 days | 20 | 20 | 280",
            "10-20IU/dose PO BID x 7 days | 10 | 20 | 280",
            "1/2IU per dose PO BID x 8 days | 0.5 | 0.5 | 8",
            "Take 17 g per dose PO daily x 7 days | 17 | 17 | 119",
            "Take 17 g/dose PO daily x 7 days | 17 | 17 | 119",
            "Take 17g/dose PO daily x 7 days | 17 | 17 | 119",
            "Take 17 grams per dose PO daily x 7 days | 17 | 17 | 119",
            "17 g per dose PO daily x 7 days | 17 | 17 | 119",
            "500 mg per dose PO BID x 7 days | 1 | 1 | 14",
            "500mg/dose PO BID x 7 days | 1 | 1 | 14",
            "250-500 mg/dose PO BID x 7 days | 1 | 2 | 28",
            "1/2 g per dose PO BID x 8 days | 0.5 | 0.5 | 8",
            "20 mEq per dose PO BID x 7 days | 1 | 1 | 14",
            "250 mcg/dose INH BID x 7 days | 1 | 1 | 14",
            "500-1000 mg po tid x 7 days | 1 | 2 | 42",
            "500-1000mg po tid x 7 days | 1 | 2 | 42",
            "1000-500 mg po tid x 7 days | 1 | 2 | 42",
            "Take 1-2 mg po q4h prn x 3 days | 1 | 2 | 36",
            "Take 0.2 mL po tid x 7 days | 0.2 | 0.2 | 5",
            "100 mg/5mL PO 10mL TID x 7 days | 10 | 10 | 210",
            "100 mg/5mL PO 10 mL TID x 7 days | 10 | 10 | 210",
            "250mg/5mL PO 5-10mL TID x 7 days | 5 | 10 | 210",
            "250mg/5mL PO 5-10 mL TID x 7 days | 5 | 10 | 210",
            "100 mg/5mL PO 1/2mL TID x 8 days | 0.5 | 0.5 | 12",
            "100units/mL SC 0.5mL BID x 7 days | 0.5 | 0.5 | 7",
            "100units/mL SC 10 units BID x 7 days | 10 | 10 | 140",
            "1000 mg po 10mL tid x 7 days | 10 | 10 | 210",
            "1000 mg PO 2 tablets TID x 7 days | 2 | 2 | 42",
            "1000 mg po 2 tabs tid x 7 days | 2 | 2 | 42",
            "500 mg PO 1-2 capsules BID x 7 days | 1 | 2 | 28",
            "100 mcg INH 2 puffs BID x 7 days | 2 | 2 | 28",
            "50 mcg 1 spray OD x 7 days | 1 | 1 | 7",
            "500 mg PO 1 cap BID x 7 days | 1 | 1 | 14",
            "5 mg TD 2 patches daily x 7 days | 2 | 2 | 14",
            "5 mg/mL OU 2 drops BID x 7 days | 2 | 2 | 28",
            "250 mg/5 mL PO 1/2 tablet BID x 8 days | 0.5 | 0.5 | 8",
            "Take 1 sachet in 125 mL BID x 7 days | 1 | 1 | 14",
            "Take 1 tab PO with 250 mL BID x 7 days | 1 | 1 | 14",
            "two tablets po with 250 mL tid x 7 days | 2 | 2 | 42",
            "Mix medication in 100 mL BID x 7 days | 0 | 0 | 0",
            "Dissolve in 100 mL BID x 7 days | 0 | 0 | 0",
            "Take 0.5mL po OD x 7 days | 0.5 | 0.5 | 4",
            "Take 1/2mL po OD x 7 days | 0.5 | 0.5 | 4",
            "Take 1/2 tab po OD x 7 days | 0.5 | 0.5 | 4",
            "Apply 0.5g OD x 7 days | 0.5 | 0.5 | 4",
            "0.5IU SC OD x 7 days | 0.5 | 0.5 | 4",
            "1-1.5mL po OD x 7 days | 1 | 1.5 | 11",
            "0.1mL po tid x 10 days | 0.1 | 0.1 | 3",
            "1 tab po Q1Week x 7 days | 1 | 1 | 1",
            "1 tab po Q2Week x 14 days | 1 | 1 | 1",
            "1 tab po Q1Month x 30 days | 1 | 1 | 1",
            "1 tab po Q3Month x 90 days | 1 | 1 | 1",
            "1 tab po Q3Month x 3 months | 1 | 1 | 1",
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

        /**
         * A fraction keeps its numerator and denominator until the final division, so an
         * exact course total is not pushed over the next whole unit by a decimal approximation.
         */
        @ParameterizedTest(name = "{0} dispenses {3}")
        @CsvSource(delimiter = '|', value = {
            "5/6 tab PO daily x 6 days | 5 | 6 | 5",
            "5/3 tab PO TID x 1 days | 5 | 3 | 5",
            "2/3 tab PO TID x 1 days | 2 | 3 | 2",
            "1/3 tab PO TID x 2 days | 1 | 3 | 2",
            "1/6 tab PO BID x 3 days | 1 | 6 | 1",
            "Take 5/6 tab PO daily x 7 days | 5 | 6 | 6",
            "1/2 tab PO daily x 7 days | 1 | 2 | 4",
            "1/3 tab PO Q1Week x 3 weeks | 1 | 3 | 1",
            "1/3 tab PO daily x 7 days | 1 | 3 | 3",
            "1/4 tab PO daily x 9 days | 1 | 4 | 3",
            "2/3 tab PO Q1Week x 6 weeks | 2 | 3 | 4"
        })
        void shouldRoundFractionTotals_atTheFinalDivision(String instructions, int numerator,
                int denominator, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            float dose = (float) numerator / denominator;
            assertThat(rx.getTakeMin()).isCloseTo(dose, within(1e-6f));
            assertThat(rx.getTakeMax()).isCloseTo(dose, within(1e-6f));
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * A whole number written before a fraction is one mixed amount: "1 1/2" is one and a
         * half units, so the fraction found just before the frequency must not drop the whole
         * part. A whole number that ends a range ("1-1 1/2") is not read as a mixed number; the
         * leading range is kept instead of the trailing half. A fraction written as a schedule or
         * a ratio ("x 3/7", "140/90") never replaces an explicit leading count.
         */
        @ParameterizedTest(name = "{0} keeps dose {1}-{2}")
        @CsvSource(delimiter = '|', value = {
            "Take 1 1/2 TID x 10 days | 1.5 | 1.5 | 45",
            "1 1/2 TID x 10 days | 1.5 | 1.5 | 45",
            "Take 2 1/2 BID x 4 days | 2.5 | 2.5 | 20",
            "1000 mg po 1 1/2 tid x 10 days | 1.5 | 1.5 | 45",
            "Take 1/2 TID x 10 days | 0.5 | 0.5 | 15",
            "1 1/4 BID x 30 days | 1.25 | 1.25 | 75",
            "Take 1-1 1/2 TID x 10 days | 1 | 1 | 30",
            "Take 1 tab x 3/7 BID x 3 days | 1 | 1 | 6",
            "1 tab po for 3/7 bid x 3 days | 1 | 1 | 6",
            "Take 6 tabs po 1/7 weekly x 12 weeks | 6 | 6 | 72",
            "Take 1 tab PO if SBP > 140/90 BID x 30 days | 1 | 1 | 60",
            "1 1/2 tabs po bid x 30 days | 1.5 | 1.5 | 90",
            "Take 1 1/2 tab po daily x 30 days | 1.5 | 1.5 | 45",
            "2 1/2 tabs po daily x 30 days | 2.5 | 2.5 | 75",
            "1 3/4 tabs po daily x 30 days | 1.75 | 1.75 | 53",
            "Take 1 1/2 po TID x 10 days | 1.5 | 1.5 | 45",
            "1 1/2 tab weekly x 12 weeks | 1.5 | 1.5 | 18",
            "Take 1 1/2 tablets po q4-6h prn x 5 days | 1.5 | 1.5 | 45"
        })
        void shouldKeepWholePart_whenMixedNumberPrecedesFrequency(String instructions,
                float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * Issue #3876 defaults a strength to one unit only when no explicit count is given. A
         * count written with its dose form between the strength and the frequency, such as
         * "1000 mg (2 tabs)", is that explicit count. Dispensing counts ("#30 tabs",
         * "dispense 30 tabs") and counts written after the frequency are not doses.
         */
        @ParameterizedTest(name = "{0} keeps dose {1}-{2}")
        @CsvSource(delimiter = '|', value = {
            "1000 mg (2 tabs) po tid x 7 days | 2 | 2 | 42",
            "Take 1000 mg (2 tabs) po tid x 7 days | 2 | 2 | 42",
            "Take 1000mg (2 tablets) PO TID x 7 days | 2 | 2 | 42",
            "1000 mg po 2 tabs tid x 7 days | 2 | 2 | 42",
            "500 mg 2 tabs po bid x 10 days | 2 | 2 | 40",
            "500 mg 2 caps po bid x 10 days | 2 | 2 | 40",
            "Take 500 mg (1 capsule) po tid x 7 days | 1 | 1 | 21",
            "Take 500-1000 mg (1-2 tabs) po q6h x 5 days | 1 | 2 | 40",
            "Take 1000 mg (1/2 tab) po bid x 10 days | 0.5 | 0.5 | 10",
            "100 mcg 2 puffs BID x 30 days | 2 | 2 | 120",
            "Take 2 g (4 tabs) po once daily x 1 day | 4 | 4 | 4",
            "Take 12.5 mg (1/2 tab) po daily x 30 days | 0.5 | 0.5 | 15",
            "1000 mg po 2 tablets with food tid x 7 days | 2 | 2 | 42",
            "Take 250 mg (5 mL) po tid x 10 days | 5 | 5 | 150",
            "500mg 2 po tid x 7 days | 2 | 2 | 42",
            "15 mg (6 tabs) po weekly x 12 weeks | 6 | 6 | 72",
            "2000 IU (2 caps) po daily x 30 days | 2 | 2 | 60",
            "1000 mg (2 x 500 mg tabs) po q6h for 7 days | 2 | 2 | 56",
            "Take 20 mg (2 x 10 mg tabs) po daily x 30 days | 2 | 2 | 60",
            "2.5 mg (1/2 x 5 mg tabs) po daily x 30 days | 0.5 | 0.5 | 15",
            "1000 mg #30 tabs po tid x 7 days | 1 | 1 | 21",
            "1000 mg dispense 30 tabs po tid x 7 days | 1 | 1 | 21",
            "1000 mg po tid x 7 days (2 tabs) | 1 | 1 | 21",
            "1000 mg 1 tab or 2 tabs po tid x 7 days | 1 | 1 | 21"
        })
        void shouldUseExplicitCount_whenStrengthIsFollowedByCountedDoseForm(String instructions,
                float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * A volume or count written as a vehicle, a ceiling or an amount to dispense is not the
         * dose, even when it sits right before the frequency: "1 g IV in 100 mL Q8H" is one unit of
         * the product per dose, not 100 mL, and "max 8 tabs daily" is a limit.
         */
        @ParameterizedTest(name = "{0} keeps dose {1}-{2}")
        @CsvSource(delimiter = '|', value = {
            "1 g IV in 100 mL Q8H x 7 days | 1 | 1 | 21",
            "2 g IV in 50 mL TID x 7 days | 1 | 1 | 21",
            "Take 17 g PO in 250 mL OD x 30 days | 1 | 1 | 30",
            "Take 4 g PO dissolved in 100 mL TID x 7 days | 1 | 1 | 21",
            "17 g mixed with 250 mL BID x 14 days | 1 | 1 | 28",
            "Take 17 g po in 125-250 mL daily x 30 days | 1 | 1 | 30",
            "Take 17 g po in 125 - 250 mL daily x 30 days | 1 | 1 | 30",
            "1 g IM reconstituted in 3.5 mL daily x 3 days | 1 | 1 | 3",
            "Take 500 mg with 250 mL BID x 7 days | 1 | 1 | 14",
            "Take 1000 mg PO, max 8 tabs daily x 7 days | 1 | 1 | 7",
            "Take 2 g PO, max 4 sachets daily x 7 days | 1 | 1 | 7",
            "100 mg/5mL PO 10mL TID x 7 days | 10 | 10 | 210"
        })
        void shouldNotReadVehicleOrCeilingAsDose_whenStrengthLeads(String instructions,
                float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * Grams put on the skin or a mucosa are the amount applied, however the site is written,
         * not only after Apply, Rub well in or the TOP. route.
         */
        @ParameterizedTest(name = "{0} keeps dose {1}-{2}")
        @CsvSource(delimiter = '|', value = {
            "2 g TOP BID x 14 days | 2 | 2 | 56",
            "2 g topically BID x 14 days | 2 | 2 | 56",
            "2g topical bid x 14 days | 2 | 2 | 56",
            "2 g to affected area BID x 14 days | 2 | 2 | 56",
            "2 g bid to affected area x 14 days | 2 | 2 | 56",
            "4 g qid to knees x 30 days | 4 | 4 | 480",
            "5 g PV qhs x 7 days | 5 | 5 | 35",
            "5 g intravaginally qhs x 7 days | 5 | 5 | 35",
            "2 g po bid x 14 days | 1 | 1 | 28"
        })
        void shouldKeepGramAmount_whenAppliedToSkinOrMucosa(String instructions,
                float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * A rectal gram amount is the strength of one suppository or enema (mesalamine 1 g,
         * 4 g/60 mL), not grams of cream: one unit without a product strength, units of the
         * selected suppository's strength, and nothing for an enema measured by volume, so a
         * 4 g enema is never dispensed as 120.
         */
        @ParameterizedTest(name = "{0} with {1} {2} gives {3}-{4}, quantity {5}")
        @CsvSource(delimiter = '|', value = {
            "4 g PR qhs x 30 days | | | 1 | 1 | 30",
            "4 g rectally qhs x 30 days | | | 1 | 1 | 30",
            "4 g PR qhs x 30 days | 4g/60ml | ENEMA | 0 | 0 | 0",
            "1 g PR qhs x 30 days | 500mg | SUPPOSITORY | 2 | 2 | 60"
        })
        void shouldReadGramsAsStrength_whenGivenRectally(String instructions, String dosage, String drugForm,
                float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            rx.setDosage(dosage);
            rx.setDrugForm(drugForm);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * With a cream, ointment or gel selected, a gram amount is what is applied even when the
         * directions name no site or route: "2-4 g qid" of diclofenac gel is grams of gel, as it was
         * before #3876. A gram tablet stays a strength.
         */
        @ParameterizedTest(name = "{0} with {1} {2} gives {3}-{4}, quantity {5}")
        @CsvSource(delimiter = '|', value = {
            "2-4 g qid x 30 days | 1.16% | GEL | 2 | 4 | 480",
            "4g QID x 30 days | 1.16% | GEL | 4 | 4 | 480",
            "1 g bid x 14 days | 1.0% | CREAM | 1 | 1 | 28",
            "2 g PR bid x 14 days | 1.0% | OINTMENT | 2 | 2 | 56",
            "1 g po qid x 7 days | 1.0g | TABLET | 1 | 1 | 28"
        })
        void shouldKeepGramAmount_whenSelectedProductIsTopical(String instructions, String dosage, String drugForm,
                float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            rx.setDosage(dosage);
            rx.setDrugForm(drugForm);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * A dose per body size and a zero strength state no fixed administered amount, so nothing
         * is inferred and the Rx page keeps the card's quantity.
         */
        @ParameterizedTest(name = "{0} leaves the dose and quantity unset")
        @ValueSource(strings = {
            "Take 10 mg/kg po tid x 7 days", "15 mg/kg po tid x 7 days", "5 mcg/kg IV tid x 7 days",
            "100 mg/m2 po tid x 7 days", "0.0 mg po bid x 7 days"
        })
        void shouldLeaveDoseUnset_whenStrengthHasNoFixedAmount(String instructions) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(0f);
            assertThat(rx.getTakeMax()).isEqualTo(0f);
            assertThat(rx.getFrequencyCode()).isNotEmpty();
            assertThat(rx.getDuration()).isEqualTo("7");
            assertThat(rx.getQuantity()).isEqualTo("0");
        }

        /**
         * When the selected product has one known strength (the drug picker stores it as
         * {@code dosage}, e.g. "500.0mg"), the typed strength is divided by it: 1000 mg of a 500 mg
         * tablet is two tablets, 0.5 mg of a 1 mg tablet is half a tablet. A result that is not a
         * whole number of units (halves and quarters for tablets only), a liquid product, or more
         * than 20 units leaves the dose unset so the prescriber's quantity is kept, and a
         * modified-release or enteric-coated tablet is never split. A combination product or a
         * strength in another measure keeps the one-unit default. With a tablet or capsule in IU
         * selected (vitamin D), an IU amount is its strength even without a route, while insulin
         * solutions and injected IU keep the typed amount.
         */
        @ParameterizedTest(name = "{0} with {1} {2} gives {3}-{4}, quantity {5}")
        @CsvSource(delimiter = '|', value = {
            "1000 mg po tid x 7 days | 500.0mg | TABLET | 2 | 2 | 42",
            "1000 mg po tid x 7 days | 1000.0mg | TABLET | 1 | 1 | 21",
            "1000mg po tid x 7 days | 500.0mg | TABLET | 2 | 2 | 42",
            "Take 1 g po tid x 7 days | 500.0mg | CAPSULE | 2 | 2 | 42",
            "Take 0.5 mg po qhs x 30 days | 1.0mg | TABLET | 0.5 | 0.5 | 15",
            "Take 3 mg po daily x 30 days | 1.0mg | TABLET | 3 | 3 | 90",
            "Take 2 mg po q4h x 5 days | 1.0mg | TABLET | 2 | 2 | 60",
            "Take 40 mg po daily x 5 days | 5.0mg | TABLET | 8 | 8 | 40",
            "Take 100 mcg po daily x 90 days | 50.0mcg | TABLET | 2 | 2 | 180",
            "Take 2000 IU po daily x 90 days | 1000.0unit | TABLET | 2 | 2 | 180",
            "Take 250 mg po daily x 30 days | 500.0mg | TABLET | 0.5 | 0.5 | 15",
            "Take 750 mg po daily x 30 days | 1500.0mg | TABLET (EXTENDED-RELEASE) | 0 | 0 | 0",
            "Take 40 mg po daily x 30 days | 80.0mg | TABLET (ENTERIC-COATED) | 0 | 0 | 0",
            "Take 1000 mg po daily x 30 days | 500.0mg | TABLET (EXTENDED-RELEASE) | 2 | 2 | 60",
            "Take 500-1000 mg po q6h x 5 days | 500.0mg | TABLET | 1 | 2 | 40",
            "Take 20 mg po daily x 30 days | 20.0mg tablet | TABLET | 1 | 1 | 30",
            "Take 250 mg po daily x 30 days | 500.0mg | CAPSULE | 0 | 0 | 0",
            "Take 300 mg po daily x 30 days | 500.0mg | TABLET | 0 | 0 | 0",
            "Take 1000 mg po daily x 30 days | 5.0mg | TABLET | 0 | 0 | 0",
            "Take 250 mg po tid x 10 days | 250.0mg/5.0ml | SUSPENSION | 0 | 0 | 0",
            "Take 10 mg po daily x 30 days | 10.0mg | SOLUTION | 0 | 0 | 0",
            "Take 325 mg po q4h x 5 days | 325.0mg 30.0mg | TABLET | 1 | 1 | 30",
            "Take 1000 IU po daily x 30 days | 25.0mcg | TABLET | 1 | 1 | 30",
            "1000 IU daily x 90 days | 1000.0unit | TABLET | 1 | 1 | 90",
            "2000 IU daily x 90 days | 1000.0unit | CAPSULE | 2 | 2 | 180",
            "20 IU qhs x 30 days | 100.0unit/ml | SOLUTION | 20 | 20 | 600",
            "20 IU qhs x 30 days | 100.0unit | SOLUTION | 20 | 20 | 600",
            "Take 10 IU SC qhs x 30 days | 1000.0unit | TABLET | 10 | 10 | 300",
            "1000 mg (2 tabs) po tid x 7 days | 1000.0mg | TABLET | 2 | 2 | 42",
            "Take 1 1/2 mg po daily x 30 days | 0.5mg | TABLET | 3 | 3 | 90",
            "Take 5 mL po tid x 7 days | 250.0mg/5.0ml | SUSPENSION | 5 | 5 | 105"
        })
        void shouldDivideByProductStrength_whenSelectedProductStrengthIsKnown(String instructions,
                String dosage, String drugForm, float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            rx.setDosage(dosage);
            rx.setDrugForm(drugForm);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * IU taken by mouth is a strength even when no PO/SL token is parsed ("by mouth", "orally",
         * or the method Take with no other route), the vitamin D form of issue #3876. IU with no
         * route at all, or given by injection, stays the administered amount (insulin).
         */
        @ParameterizedTest(name = "{0} keeps dose {1}-{2}")
        @CsvSource(delimiter = '|', value = {
            "Take 1000 IU daily x 30 days | 1 | 1 | 30",
            "1000 IU by mouth daily x 30 days | 1 | 1 | 30",
            "1000 IU orally daily x 30 days | 1 | 1 | 30",
            "Take 50000 IU weekly x 8 weeks | 1 | 1 | 8",
            "20 IU qhs x 30 days | 20 | 20 | 600",
            "Take 20 IU IV qhs x 30 days | 20 | 20 | 600",
            "Take 10 IU SC qhs x 30 days | 10 | 10 | 300",
            "10 IU subcut qhs x 30 days | 10 | 10 | 300"
        })
        void shouldReadOralInternationalUnits_whenDirectionsSayByMouth(String instructions,
                float minimum, float maximum, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(minimum);
            assertThat(rx.getTakeMax()).isEqualTo(maximum);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * A prescription that already carries an administered unit keeps the amount the
         * prescriber typed with explicit per-dose syntax, so the structured dose, the
         * original directions and the clinical export (takeMin with unit) still agree.
         */
        @Test
        void shouldPreserveExplicitGramDoseAndUnit_whenPerDoseSyntaxIsUsed() {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial("Take 17 g per dose PO daily x 7 days");
            rx.setUnit("gm");
            rx.setDosage("");
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(17f);
            assertThat(rx.getTakeMax()).isEqualTo(17f);
            assertThat(rx.getUnit()).isEqualTo("gm");
            assertThat(rx.getDosage()).isEmpty();
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("OD");
            assertThat(rx.getQuantity()).isEqualTo("119");
            assertThat(rx.getSpecial()).isEqualTo("Take 17 g per dose PO daily x 7 days");
        }

        /**
         * Detailed review item 2: a prescription with no product strength that carries an explicit
         * gram dose unit (as the legacy dose editor stores it) keeps the typed gram amount, with or
         * without "per dose", so the structured dose and the exported unit agree (17 gm, not 1 gm).
         * Without that unit, or with a drug-reference strength, the strength rules apply.
         */
        @ParameterizedTest(name = "{0} with unit ''{1}'' and dosage ''{2}'' keeps {3}, quantity {4}")
        @CsvSource(delimiter = '|', value = {
            "Take 17 g PO daily x 7 days | gm | '' | 17 | 119",
            "Take 17 g PO daily x 7 days | g | '' | 17 | 119",
            "Take 17 g per dose PO daily x 7 days | gm | '' | 17 | 119",
            "Take 17 g PO daily x 7 days | '' | '' | 1 | 7",
            "Take 17 g PO daily x 7 days | gm | 17.0g | 1 | 7",
            "Take 500 mg PO BID x 7 days | mg | '' | 1 | 14"
        })
        void shouldKeepTypedGramDose_whenPrescriptionCarriesGramDoseUnit(String instructions, String unit,
                String dosage, float dose, String quantity) {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial(instructions);
            rx.setUnit(unit);
            rx.setDosage(dosage);
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(dose);
            assertThat(rx.getTakeMax()).isEqualTo(dose);
            assertThat(rx.getUnit()).isEqualTo(unit);
            assertThat(rx.getQuantity()).isEqualTo(quantity);
        }

        /**
         * No dispensing quantity is calculated when the entered quantity carries a unit suffix, so the
         * Rx page keeps what the prescriber typed ("119 g").
         */
        @Test
        void shouldNotCalculateQuantity_whenQuantityUnitIsPresent() {
            RxPrescriptionData.Prescription rx = prescriptionWithSpecial("Take 17 g per dose PO daily x 7 days");
            rx.setUnit("gm");
            rx.setQuantity("119");
            rx.setUnitName("g");
            RxUtil.instrucParser(rx);

            assertThat(rx.getTakeMin()).isEqualTo(17f);
            assertThat(rx.getUnit()).isEqualTo("gm");
            assertThat(rx.getUnitName()).isEqualTo("g");
            // The Rx page keeps the typed "119 g" when no quantity is calculated.
            assertThat(rx.getQuantity()).isEqualTo("0");
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
