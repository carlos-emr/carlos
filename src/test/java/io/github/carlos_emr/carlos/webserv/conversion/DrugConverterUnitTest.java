/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * This software was written for the
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 DrugConverterTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.webserv.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.managers.DrugLookUpManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverterImpl;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;

/**
 * Unit tests for {@link DrugConverterImpl}.
 *
 * <p>Tests domain-to-transfer and transfer-to-domain conversion of Drug objects,
 * including drug strength population logic.
 * Migrated from legacy JUnit 4 DrugConverterTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("DrugConverter unit tests")
class DrugConverterUnitTest {

    private TestableDrugConverter converter;
    private LoggedInInfo info;

    @BeforeEach
    void setUp() {
        converter = new TestableDrugConverter();
        converter.setDrugLookUpManager(new MockDrugLookUpManager());
        info = new LoggedInInfo();
    }

    @Nested
    @DisplayName("Domain to Transfer conversion")
    class DomainToTransfer {

        @Test
        @DisplayName("should convert all Drug fields to DrugTo1 transfer object")
        void shouldConvertAllDrugFields_toDrugTo1() {
            Date startDate = new Date();
            Date endDate = new Date();
            Date archivedDate = new Date();

            Drug d = new Drug();
            d.setId(1);
            d.setRowVersion(19L);
            d.setDemographicId(1);
            d.setProviderNo("1");
            d.setBrandName("Foobar");
            d.setGenericName("Barbang");
            d.setRegionalIdentifier("12345");
            d.setAtc("abcde");
            d.setTakeMax(2);
            d.setTakeMin(1);
            d.setRxDate((Date) startDate.clone());
            d.setEndDate((Date) endDate.clone());
            d.setFreqCode("BID");
            d.setDuration("28");
            d.setDurUnit("D");
            d.setRoute("PO");
            d.setDrugForm("TAB");
            d.setPrn(true);
            d.setMethod("Take");
            d.setRepeat(5);
            d.setSpecial("some string");
            d.setArchived(false);
            d.setArchivedDate((Date) archivedDate.clone());
            d.setArchivedReason("reason");

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getDrugId()).isEqualTo(1);
            assertThat(t.getRowVersion()).isNull(); // The REST endpoint owns the response opt-in.
            assertThat(t.getDemographicNo()).isEqualTo(1);
            assertThat(t.getProviderNo()).isEqualTo("1");
            assertThat(t.getBrandName()).isEqualTo("Foobar");
            assertThat(t.getGenericName()).isEqualTo("Barbang");
            assertThat(t.getRegionalIdentifier()).isEqualTo("12345");
            assertThat(t.getAtc()).isEqualTo("abcde");
            assertThat(t.getTakeMin()).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(t.getTakeMax()).isCloseTo(2.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(t.getRxDate().toString()).isEqualTo(startDate.toString());
            assertThat(t.getEndDate().toString()).isEqualTo(endDate.toString());
            assertThat(t.getFrequency()).isEqualTo("BID");
            assertThat(t.getDuration()).isEqualTo(28);
            assertThat(t.getDurationUnit()).isEqualTo("D");
            assertThat(t.getRoute()).isEqualTo("PO");
            assertThat(t.getForm()).isEqualTo("TAB");
            assertThat(t.isPrn()).isTrue();
            assertThat(t.getMethod()).isEqualTo("Take");
            assertThat(t.getRepeats()).isEqualTo(5);
            assertThat(t.getInstructions()).isEqualTo("some string");
            assertThat(t.getArchivedDate().toString()).isEqualTo(archivedDate.toString());
            assertThat(t.getArchivedReason()).isEqualTo("reason");
            assertThat(t.isArchived()).isFalse();
        }
    }

    @Nested
    @DisplayName("Transfer to Domain conversion")
    class TransferToDomain {

        @Test
        @DisplayName("should convert all DrugTo1 fields to Drug domain object")
        void shouldConvertAllDrugTo1Fields_toDrugDomain() {
            Date startDate = new Date();
            Date endDate = new Date();
            Date archivedDate = new Date();

            DrugTo1 t = new DrugTo1();
            t.setRowVersion(7L);
            t.setDemographicNo(1);
            t.setProviderNo("1");
            t.setGenericName("bangbar");
            t.setBrandName("foobar");
            t.setRegionalIdentifier("12345");
            t.setAtc("abcde");
            t.setTakeMax(2.0f);
            t.setTakeMin(1.0f);
            t.setRxDate((Date) startDate.clone());
            t.setEndDate((Date) endDate.clone());
            t.setFrequency("BID");
            t.setDuration(28);
            t.setDurationUnit("D");
            t.setRoute("PO");
            t.setForm("TAB");
            t.setPrn(false);
            t.setMethod("take");
            t.setRepeats(5);
            t.setInstructions("some string");
            t.setArchived(false);
            t.setArchivedReason("reason");
            t.setArchivedDate((Date) archivedDate.clone());
            t.setStrength(10.0f);
            t.setStrengthUnit("MG");
            t.setExternalProvider("foo");
            t.setLongTerm(false);
            t.setNoSubstitutions(false);

            Drug d = converter.getAsDomainObject(info, t);

            assertThat(d.getRowVersion()).isEqualTo(7L);
            assertThat(d.getDemographicId()).isEqualTo(1);
            assertThat(d.getProviderNo()).isEqualTo("1");
            assertThat(d.getGenericName()).isEqualTo("bangbar");
            assertThat(d.getBrandName()).isEqualTo("foobar");
            assertThat(d.getRegionalIdentifier()).isEqualTo("12345");
            assertThat(d.getAtc()).isEqualTo("abcde");
            assertThat(d.getTakeMin()).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(d.getTakeMax()).isCloseTo(2.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(d.getRxDate().toString()).isEqualTo(startDate.toString());
            assertThat(d.getEndDate().toString()).isEqualTo(endDate.toString());
            assertThat(d.getFreqCode()).isEqualTo("BID");
            assertThat(d.getDuration()).isEqualTo("28");
            assertThat(d.getDurUnit()).isEqualTo("D");
            assertThat(d.getRoute()).isEqualTo("PO");
            assertThat(d.getDrugForm()).isEqualTo("TAB");
            assertThat(d.getMethod()).isEqualTo("take");
            assertThat(d.getSpecial()).isEqualTo("some string");
            assertThat(d.isPrn()).isFalse();
            assertThat(d.getArchivedReason()).isEqualTo("reason");
            assertThat(d.isArchived()).isFalse();
            assertThat(d.getArchivedDate().toString()).isEqualTo(archivedDate.toString());
            assertThat(d.isLongTerm()).isFalse();
            assertThat(d.isNoSubs()).isFalse();
            assertThat(d.getOutsideProviderName()).isEqualTo("foo");
        }
    }

    @Nested
    @DisplayName("Stored quantity to transfer object")
    class StoredQuantity {

        @ParameterizedTest(name = "stored quantity \"{0}\" -> {1}")
        @CsvSource({"30, 30", "0, 0", "30.0, 30", "1000, 1000"})
        @DisplayName("should report a whole stored quantity")
        void shouldReportQuantity_whenStoredQuantityIsWhole(String stored, int expected) {
            Drug d = new Drug();
            d.setQuantity(stored);

            assertThat(converter.getAsTransferObject(info, d).getQuantity()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "stored quantity \"{0}\"")
        @ValueSource(strings = {"2.5", "0.5", "100 mL"})
        @DisplayName("should omit a quantity the whole-number field cannot carry rather than fail the read")
        void shouldOmitQuantity_whenStoredQuantityIsNotWhole(String stored) {
            // DrugTo1.quantity is an Integer. A decimal quantity ("2.5", of 2.5 mL) cannot be carried
            // in it; rounding it would report a different amount, and Integer.parseInt threw, which
            // failed the read of the patient's whole drug list. The instruction text still has it.
            Drug d = new Drug();
            d.setQuantity(stored);

            assertThat(converter.getAsTransferObject(info, d).getQuantity()).isNull();
        }
    }

    @Nested
    @DisplayName("Stored strength text to transfer object")
    class StoredStrength {

        private void assertStrengthRead(String stored, float strength, String unit) {
            Drug d = new Drug();
            d.setDosage(stored);

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getStrength()).isEqualTo(strength);
            assertThat(t.getStrengthUnit()).isEqualTo(unit);
        }

        private void assertNoStrengthRead(String stored) {
            Drug d = new Drug();
            d.setDosage(stored);

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getStrength()).isNull();
            assertThat(t.getStrengthUnit()).isNull();
        }

        /**
         * The strength was read back with a pattern that needed two digits. That went unnoticed
         * while every strength was stored with a trailing zero ("5.0 mg"); a strength stored as
         * "5 mg" had none, and "5 mg / 20 mg" reported the second ingredient's 20 (#6185).
         */
        @ParameterizedTest(name = "stored \"{0}\" -> {1} {2}")
        @CsvSource({
            "5 mg, 5, mg",
            "5mg, 5, mg",
            "1 mg, 1, mg",
            "0.00 mg, 0, mg",
            ".00e-70 mg, 0, mg",
            "500 mg, 500, mg",
            "0.5 mg, 0.5, mg",
            "12.5 mg, 12.5, mg",
            "5.0 mg, 5, mg",
            "250.0mg, 250, mg"
        })
        @DisplayName("should read the strength and its unit from stored text, with or without a trailing zero")
        void shouldReadStrength_fromStoredStrengthText(String stored, float strength, String unit) {
            assertStrengthRead(stored, strength, unit);
        }

        /**
         * The unit is the unit of the first ingredient. Everything up to the next number was taken
         * as the unit, so "5 mg / 20 mg" gave the unit "mg / " and a client that sent the drug back
         * stored "5 mg /". A number that comes after the first one's unit does not make the first
         * something else: it belongs to the unit ("mg/5mL"), to another ingredient or to a note.
         */
        @ParameterizedTest(name = "stored \"{0}\" -> {1} {2}")
        @CsvSource({
            "5 mg / 20 mg, 5, mg",
            "875 mg / 125 mg, 875, mg",
            "875mg/125mg, 875, mg",
            "500mg/125mg, 500, mg",
            "50mcg/250mcg, 50, mcg",
            "4g/0.5g, 4, g",
            "4 g / 0.5 g, 4, g",
            "2kg/1kg, 2, kg",
            "5g/5mL, 5, g/5mL",
            "5kg/5mL, 5, kg/5mL",
            "5mg+20mg, 5, mg",
            "5mg+ .5mg, 5, mg",
            "5 mg + 20 mg, 5, mg",
            "5mg/ 20 mg, 5, mg",
            "5mg;20mg, 5, mg",
            "'5mg,20mg', 5, mg",
            "5.0 mg / 325.0 mg, 5, mg",
            "325mg 30mg, 325, mg",
            "'5 mg, 20 mg', 5, mg",
            "20mg tablet, 20, mg",
            "2.5 mg/mL, 2.5, mg/mL",
            "250mg/5ml, 250, mg/5ml",
            "250mg/5mL+20mg, 250, mg/5mL",
            "250mg/5g+20mg, 250, mg/5g",
            "250 mg / 5 g, 250, mg / 5 g",
            "20mg/g, 20, mg/g",
            "20 mg / g, 20, mg / g",
            "25mcg/hr, 25, mcg/hr",
            "25 mcg / hr, 25, mcg / hr",
            "50 mcg / 24 hr, 50, mcg / 24 hr",
            "100 mcg/actuation, 100, mcg/actuation",
            "100 mcg / actuation, 100, mcg / actuation",
            "50 mcg / spray, 50, mcg / spray",
            "25 mcg / patch, 25, mcg / patch",
            "5 mg/kg/day, 5, mg/kg/day",
            "5 mg / 20, 5, mg",
            "250mg/1E+2mL+20mg, 250, mg/1E+2mL",
            "250 mg/5mL, 250, mg/5mL",
            "250 mg/5 mL, 250, mg/5 mL",
            "250 mg / 5 mL, 250, mg / 5 mL",
            "2.5 mg / .5 mL, 2.5, mg / .5 mL",
            "2.5 mg / mL/hour, 2.5, mg / mL/hour",
            "250 mg per 5 mL, 250, mg per 5 mL",
            "100 international units, 100, international units",
            "100 International Units / mL, 100, International Units / mL",
            "0.0E7 IU, 0, IU",
            "'  5 mg', 5, mg",
            "10 %, 10, %",
            ".5 mg, 0.5, mg",
            "5 mg (10 mg/mL), 5, mg",
            "10% (100 mg/mL), 10, %",
            "5 mg 1:1000, 5, mg",
            "10 mg/m\u00B2, 10, mg/m\u00B2",
            "10 mcg/mcL, 10, mcg/mcL",
            "10 mcg / mcL, 10, mcg / mcL",
            "100 mg/dL, 100, mg/dL",
            "0.05% w/w, 0.05, % w/w",
            "0.1% w/v, 0.1, % w/v",
            "5% v/v, 5, % v/v",
            "500 mg/capsule, 500, mg/capsule",
            "500mg / tab, 500, mg / tab",
            "10 mg/2 tablets, 10, mg/2 tablets",
            "500 mg per capsule, 500, mg per capsule",
            "20 mg / 2 caplets, 20, mg / 2 caplets",
            "10 mg / suppository, 10, mg / suppository",
            "5 mg \u00BD tablet, 5, mg"
        })
        @DisplayName("should read the first ingredient's strength and unit, and keep a unit of concentration whole")
        void shouldReadFirstStrengthAndUnit_forCombinationAndConcentration(String stored, float strength, String unit) {
            assertStrengthRead(stored, strength, unit);
        }

        /**
         * A strength stored without a unit is still a strength. The pattern required a unit, so
         * "2.5" gave up its fraction to find one and was reported as 2 with the unit ".".
         */
        @ParameterizedTest(name = "stored \"{0}\" -> {1}")
        @CsvSource({"5, 5", "2.5, 2.5", "500, 500", "0.5, 0.5"})
        @DisplayName("should read a strength stored without a unit as that number and no unit")
        void shouldReadStrengthWithoutUnit_whenNoUnitIsStored(String stored, float strength) {
            Drug d = new Drug();
            d.setDosage(stored);

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getStrength()).isEqualTo(strength);
            assertThat(t.getStrengthUnit()).isNull();
        }

        /**
         * Stored text may hold more than one blank, or tabs, between the number and its unit. One
         * blank was allowed, so "5  mg" was read as 5 with no unit and sent back as "5".
         */
        @ParameterizedTest(name = "stored \"{0}\"")
        @ValueSource(strings = {"5  mg", "5\t\tmg", "5 \t mg", "5   mg / 20 mg", "5\u00a0mg", "5\u202fmg"})
        @DisplayName("should read the unit after any run of blanks")
        void shouldReadUnit_afterAnyRunOfBlanks(String stored) {
            Drug d = new Drug();
            d.setDosage(stored);

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getStrength()).isEqualTo(5f);
            assertThat(t.getStrengthUnit()).isEqualTo("mg");
        }

        /**
         * When another number follows the leading one before any unit, the leading digits are not
         * the strength (1 is not 1,000): grouped digits ("1,000 mg", "1 000 mg", also with a
         * no-break space), a decimal comma ("2,5 mg"), a fraction ("1/2 mg"), a combination
         * written together ("5/20 mg", "5+20 mg"), a range ("5-10 mg"), a ratio ("1:1000") or a
         * power ("10^9 CFU"). The single strength field cannot carry the rest, so none is
         * reported; the stored text is unchanged.
         *
         * <p>Blanks around the mark between the two numbers change nothing. Only the character
         * directly after the number was looked at, so "1 / 2 mg" was reported as 1 and a client
         * that sent the drug back stored "1".
         *
         * <p>The number that follows need not be written in ASCII digits: one and a half written
         * with the fraction character U+00BD, a superscript two (U+00B2), a Roman numeral
         * character (U+2163) and a digit of another script (U+0665) are numbers too. The fraction
         * and the superscript were passed over as punctuation, so one and a half read as 1. So
         * was a number outside the Basic Multilingual Plane (the mathematical digit U+1D7DA),
         * which is two {@code char} values of which neither is a number: the text is read by
         * code point.
         */
        @ParameterizedTest(name = "stored \"{0}\"")
        @ValueSource(strings = {
            "1,000 mg", "2,5 mg", "1/2 mg", "5/20 mg", "5-10 mg", "0.5-1 mg", "1,000",
            "1 / 2 mg", "1/ 2 mg", "1 /2 mg", "5 - 10 mg", "1 ,000 mg", "1 000 mg", "1\u00A0000 mg",
            "2 1/2 mg", "1:1000", "1 : 10 000", "5+20 mg", "10^9 CFU", "1.234.567 mg", "5 (10 mg/mL)",
            "1\u00BD mg", "1 \u00BD mg", "10\u00B2 mg", "5 \u2163 mg", "1 \u0665 mg", "1\u00BD",
            "1\uD835\uDFDA mg", "1 \uD835\uDFDA mg", "1 / \uD835\uDFDA"
        })
        @DisplayName("should report no strength when another number follows the first before any unit")
        void shouldReportNoStrength_whenAnotherNumberFollowsBeforeAnyUnit(String stored) {
            assertNoStrengthRead(stored);
        }

        @ParameterizedTest
        @ValueSource(strings = {"1.0e-60 mg", ".0003E-70 mg"})
        void shouldLeaveStrengthUnknown_whenANonzeroAmountUnderflowsTheWireFloat(String stored) {
            assertNoStrengthRead(stored);
        }

        /**
         * Only the first number in the text is the first ingredient's. When it is not one plain
         * decimal there is no strength to report; the next number belongs to another ingredient
         * and was reported in its place ("1,000 mg / 20 mg" read 20 and "mg").
         */
        @ParameterizedTest(name = "stored \"{0}\"")
        @ValueSource(strings = {
            "1,000 mg / 20 mg", "1/2 mg / 20 mg", "5-10 mg / 20 mg", "2,5 mg, 20 mg", "5/20 mg and 10 mg"
        })
        @DisplayName("should not report a later ingredient when the first number is not one plain decimal")
        void shouldReportNoStrength_ratherThanALaterIngredients(String stored) {
            assertNoStrengthRead(stored);
        }

        /**
         * The REST service stored a strength of ten million as the float's own text, "1.0E7 unit".
         * Such rows are not rewritten, so the exponent is read with the number: read without it,
         * the strength was 1 with the unit "E7".
         */
        @ParameterizedTest(name = "stored \"{0}\" -> {1} {2}")
        @CsvSource({
            "1.0E7 unit, 1.0E7, unit",
            "1.0E7unit, 1.0E7, unit",
            "1.0E+7 unit, 1.0E7, unit",
            "1.0E-4 mg, 1.0E-4, mg",
            "2.5e3 mg, 2500, mg",
            "5each, 5, each",
            "5 each, 5, each",
            "5e, 5, e",
            "5e+, 5, e+"
        })
        @DisplayName("should read a stored exponent with its number, and a unit that starts with e as a unit")
        void shouldReadExponentWithNumber_forStrengthStoredAsFloatText(String stored, float strength, String unit) {
            assertStrengthRead(stored, strength, unit);
        }

        @ParameterizedTest(name = "stored \"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
            "5/mL | 5", "10, as directed | 10", "2.5- | 2.5", "5 (base) | 5", "2.5 - | 2.5", "10 , as directed | 10",
            "5 \uD83D\uDC8A mg | 5", "5 \uD83D\uDC8A | 5"
        })
        @DisplayName("should read the number when the separator after it is not followed by a digit")
        void shouldReadStrength_whenSeparatorAfterItStartsNoNumber(String stored, float strength) {
            // Only a separator that leads into more digits makes the number something else
            // ("1,000", "1/2", "5-10"), with or without blanks around it.
            assertStrengthRead(stored, strength, null);
        }

        @Test
        @DisplayName("should read a number at the very end of the text, with or without an exponent")
        void shouldReadStrengthWithoutUnit_whenTextEndsWithTheNumber() {
            Drug plain = new Drug();
            plain.setDosage("5");
            Drug exponent = new Drug();
            exponent.setDosage("1.0E7");

            assertThat(converter.getAsTransferObject(info, plain).getStrength()).isEqualTo(5f);
            assertThat(converter.getAsTransferObject(info, exponent).getStrength()).isEqualTo(1.0E7f);
            assertThat(converter.getAsTransferObject(info, exponent).getStrengthUnit()).isNull();
        }

        @Test
        @DisplayName("should report no strength for an exponent too large to be one")
        void shouldReportNoStrength_whenStoredExponentIsNotFinite() {
            assertNoStrengthRead("1E400 mg");
        }

        @Test
        @DisplayName("should report no strength for digits too long to be one")
        void shouldReportNoStrength_whenStoredNumberIsNotFinite() {
            Drug d = new Drug();
            d.setDosage("9".repeat(60) + " mg");

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getStrength()).isNull();
            assertThat(t.getStrengthUnit()).isNull();
        }

        /**
         * Text that does not start with a number states no strength. A client that sends a unit and
         * no strength has the unit stored alone ("mg/5mL"); the 5 inside it is not a strength, and
         * was read as one with the unit "mL". Such text is read back as the unit, whole, so that
         * sending it again stores the same text.
         */
        @ParameterizedTest(name = "stored \"{0}\" -> unit \"{1}\"")
        @CsvSource(delimiter = '|', value = {
            "mg/5mL | mg/5mL",
            "mg | mg",
            "% | %",
            "as directed | as directed",
            "D3 1000 IU | D3 1000 IU",
            "'  mg/mL ' | mg/mL"
        })
        @DisplayName("should read text that starts with no number as a unit alone, never as a strength")
        void shouldReadUnitAlone_whenStoredTextStartsWithNoNumber(String stored, String unit) {
            Drug d = new Drug();
            d.setDosage(stored);

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getStrength()).isNull();
            assertThat(t.getStrengthUnit()).isEqualTo(unit);
        }

        @ParameterizedTest(name = "stored \"{0}\"")
        @ValueSource(strings = {"/", "(5 mg)", "~5 mg", "- 5 mg", "/5 mL"})
        @DisplayName("should report nothing for text that starts with neither a number nor a unit")
        void shouldReportNoStrength_whenStoredTextStartsWithNeitherNumberNorUnit(String stored) {
            assertNoStrengthRead(stored);
        }

        @Test
        @DisplayName("should give a client back a unit it sent without a strength, and store it again unchanged")
        void shouldReadBackUnitAlone_whenClientSentNoStrength() {
            DrugTo1 sent = restDrug("not in the drug reference");
            sent.setStrengthUnit("mg/5mL");

            Drug stored = converter.getAsDomainObject(info, sent);
            DrugTo1 read = converter.getAsTransferObject(info, stored);
            DrugTo1 sentAgain = restDrug("not in the drug reference");
            sentAgain.setStrength(read.getStrength());
            sentAgain.setStrengthUnit(read.getStrengthUnit());

            assertThat(stored.getDosage()).isEqualTo("mg/5mL");
            assertThat(read.getStrength()).isNull();
            assertThat(read.getStrengthUnit()).isEqualTo("mg/5mL");
            assertThat(converter.getAsDomainObject(info, sentAgain).getDosage()).isEqualTo("mg/5mL");
        }

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource(nullValues = "NULL", value = {"5, mg, 5 mg", "2.5, NULL, 2.5", "500, NULL, 500", "0.5, mg/mL, 0.5 mg/mL"})
        @DisplayName("should give a client back the strength it sent, with or without a unit")
        void shouldReadBackWhatWasWritten_forStrengthSentOverRest(float strength, String unit, String stored) {
            DrugTo1 sent = restDrug("not in the drug reference");
            sent.setStrength(strength);
            sent.setStrengthUnit(unit);

            Drug d = converter.getAsDomainObject(info, sent);
            DrugTo1 read = converter.getAsTransferObject(info, d);

            assertThat(d.getDosage()).isEqualTo(stored);
            assertThat(read.getStrength()).isEqualTo(strength);
            assertThat(read.getStrengthUnit()).isEqualTo(unit);
        }

        @Test
        @DisplayName("should report no strength for a drug stored without one, without logging an error")
        void shouldReportNoStrength_whenNoStrengthTextIsStored() {
            Drug d = new Drug();
            d.setDosage(null);

            try (LogCapture capture = LogCapture.forLogger(DrugConverterImpl.class)) {
                DrugTo1 t = converter.getAsTransferObject(info, d);

                assertThat(t.getStrength()).isNull();
                assertThat(t.getStrengthUnit()).isNull();
                assertThat(capture.events())
                        .filteredOn(event -> event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.WARN))
                        .isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Drug strength population")
    class DrugStrength {

        @Test
        @DisplayName("should populate drug strength with normal input")
        void shouldPopulateDrugStrength_withNormalInput() {
            Drug d = new Drug();
            DrugTo1 t = new DrugTo1();
            t.setStrengthUnit("mg");
            t.setStrength(100f);

            Boolean result = converter.populateDrugStrength(d, t);

            // "100.0 mg" was stored before #6185: a trailing zero is read as ten times the strength.
            assertThat(d.getDosage()).isEqualTo("100 mg");
            assertThat(d.getUnit()).isEqualTo("mg");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should default drug strength when no strength info provided")
        void shouldDefaultDrugStrength_whenNoStrengthInfo() {
            Drug d = new Drug();
            d.setBrandName("aspirin");
            DrugTo1 t = new DrugTo1();

            Boolean result = converter.populateDrugStrength(d, t);

            assertThat(d.getDosage()).isEqualTo("1 mg");
            assertThat(d.getUnit()).isEqualTo("mg");
            assertThat(result).isTrue();
        }

        @ParameterizedTest(name = "{0} {1} -> {2}")
        @CsvSource({
            "1.0, mg, 1 mg",
            "0.5, mg, 0.5 mg",
            "0.0625, mg, 0.0625 mg",
            "500.0, mg, 500 mg",
            "1.0E7, unit, 10000000 unit"
        })
        @DisplayName("should store the strength as a plain decimal with no trailing zero or exponent")
        void shouldStorePlainDecimalStrength_forStrengthSentOverRest(float strength, String unit, String expected) {
            Drug d = new Drug();
            DrugTo1 t = new DrugTo1();
            t.setStrength(strength);
            t.setStrengthUnit(unit);

            converter.populateDrugStrength(d, t);

            assertThat(d.getDosage()).isEqualTo(expected);
        }

        @Test
        @DisplayName("should store the strength of a whole drug sent over REST without a trailing zero")
        void shouldStorePlainDecimalStrength_whenConvertingTransferObject() {
            DrugTo1 t = restDrug("foobar");
            t.setStrength(10.0f);
            t.setStrengthUnit("MG");

            Drug d = converter.getAsDomainObject(info, t);

            assertThat(d.getDosage()).isEqualTo("10 MG");
        }

        @Test
        @DisplayName("should not store the word null as a strength when none was sent or found")
        void shouldStoreNoStrengthText_whenStrengthIsUnknown() {
            DrugTo1 t = restDrug("not in the drug reference");

            Drug d = converter.getAsDomainObject(info, t);

            // "nullnull" was stored. Drug keeps "no text" as null.
            assertThat(d.getDosage()).isNull();
        }

        @Test
        @DisplayName("should store only the unit when a unit was sent without a strength")
        void shouldStoreUnitAlone_whenOnlyUnitIsKnown() {
            DrugTo1 t = restDrug("not in the drug reference");
            t.setStrengthUnit("mg");

            Drug d = converter.getAsDomainObject(info, t);

            assertThat(d.getDosage()).isEqualTo("mg");
        }

        @Test
        void shouldLeaveStrengthUnspecified_whenUpdatingARecognizedDrugWithoutStrengthFields() {
            DrugTo1 t = restDrug("aspirin");
            t.setDrugId(4);

            Drug d = converter.getAsDomainObject(info, t);

            assertThat(d.getDosage()).isNull();
            assertThat(d.getUnit()).isNull();
        }

        @ParameterizedTest(name = "drugId={0}")
        @ValueSource(ints = {0, -1})
        void shouldPopulateStrengthFromDrugLookup_whenDrugIdIsNotPositive(int drugId) {
            DrugTo1 t = restDrug("aspirin");
            t.setDrugId(drugId);

            Drug d = converter.getAsDomainObject(info, t);

            assertThat(d.getDosage()).isEqualTo("1 mg");
            assertThat(d.getUnit()).isEqualTo("mg");
        }

        @ParameterizedTest(name = "invalid strength={0}")
        @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -10f})
        void shouldRejectNonFiniteOrNegativeStrength_whenConvertingFromTransferObject(float invalidStrength) {
            DrugTo1 t = restDrug("aspirin");
            t.setStrength(invalidStrength);
            t.setStrengthUnit("mg");

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> converter.getAsDomainObject(info, t))
                    .isInstanceOf(io.github.carlos_emr.carlos.webserv.rest.conversion.ConversionException.class)
                    .hasMessage("Invalid drug strength");
        }

        @Test
        @DisplayName("should return false when strength is null")
        void shouldReturnFalse_whenStrengthIsNull() {
            Drug d = new Drug();
            DrugTo1 t = new DrugTo1();
            t.setStrengthUnit("mg");
            t.setStrength(null);

            assertThat(converter.populateDrugStrength(d, t)).isFalse();
        }

        @Test
        @DisplayName("should return false when strength unit is null")
        void shouldReturnFalse_whenStrengthUnitIsNull() {
            Drug d = new Drug();
            DrugTo1 t = new DrugTo1();
            t.setStrengthUnit(null);
            t.setStrength(100f);

            assertThat(converter.populateDrugStrength(d, t)).isFalse();
        }
    }

    @Nested
    @DisplayName("To1 strength population from drug lookup")
    class To1Strength {

        @Test
        @DisplayName("should populate To1 strength from drug lookup for recognized drug")
        void shouldPopulateTo1Strength_forRecognizedDrug() {
            DrugTo1 t = new DrugTo1();
            Drug d = new Drug();
            d.setBrandName("aspirin");

            Boolean resp = converter.populateTo1Strength(t, d);

            assertThat(resp).isTrue();
            assertThat(t.getStrength()).isEqualTo(1.0f);
            assertThat(t.getStrengthUnit()).isEqualTo("mg");
        }

        @Test
        @DisplayName("should not overwrite existing strength")
        void shouldNotOverwriteExistingStrength() {
            DrugTo1 t = new DrugTo1();
            t.setStrength(100.0f);
            Drug d = new Drug();
            d.setBrandName("aspirin");

            assertThat(converter.populateTo1Strength(t, d)).isFalse();
            assertThat(t.getStrength()).isEqualTo(100.0f);
        }

        @Test
        @DisplayName("should not overwrite existing strength unit")
        void shouldNotOverwriteExistingStrengthUnit() {
            DrugTo1 t = new DrugTo1();
            t.setStrengthUnit("mg");
            Drug d = new Drug();
            d.setBrandName("aspirin");

            assertThat(converter.populateTo1Strength(t, d)).isFalse();
            assertThat(t.getStrengthUnit()).isEqualTo("mg");
        }

        @Test
        @DisplayName("should return false for null brand name")
        void shouldReturnFalse_forNullBrandName() {
            DrugTo1 t = new DrugTo1();
            Drug d = new Drug();

            assertThat(converter.populateTo1Strength(t, d)).isFalse();
        }

        @Test
        @DisplayName("should return false for unrecognized drug")
        void shouldReturnFalse_forUnrecognizedDrug() {
            DrugTo1 t = new DrugTo1();
            Drug d = new Drug();
            d.setBrandName("not aspirin");

            assertThat(converter.populateTo1Strength(t, d)).isFalse();
        }
    }

    /** A drug as a REST client sends it, with every field the converter reads without a null check. */
    private static DrugTo1 restDrug(String brandName) {
        DrugTo1 t = new DrugTo1();
        t.setDemographicNo(1);
        t.setProviderNo("1");
        t.setBrandName(brandName);
        t.setTakeMin(1.0f);
        t.setTakeMax(1.0f);
        t.setRepeats(0);
        t.setPrn(false);
        t.setArchived(false);
        t.setLongTerm(false);
        t.setNoSubstitutions(false);
        return t;
    }

    /**
     * Testable subclass exposing protected methods.
     */
    static class TestableDrugConverter extends DrugConverterImpl {
        void setDrugLookUpManager(DrugLookUpManager manager) {
            this.drugLookUpManager = manager;
        }

        @Override
        public DrugTo1 getAsTransferObject(LoggedInInfo info, Drug d) {
            return super.getAsTransferObject(info, d);
        }

        @Override
        public Drug getAsDomainObject(LoggedInInfo info, DrugTo1 t) {
            return super.getAsDomainObject(info, t);
        }

        @Override
        public boolean populateDrugStrength(Drug d, DrugTo1 t) {
            return super.populateDrugStrength(d, t);
        }

        @Override
        public Boolean populateTo1Strength(DrugTo1 t, Drug d) {
            return super.populateTo1Strength(t, d);
        }
    }

    /**
     * Mock DrugLookUpManager that recognizes "aspirin" as a known drug.
     */
    static class MockDrugLookUpManager extends DrugLookUpManager {

        public MockDrugLookUpManager() {
        }

        @Override
        public List<DrugSearchTo1> search(String name) {
            List<DrugSearchTo1> toReturn = new ArrayList<>();
            if ("aspirin".equals(name)) {
                DrugSearchTo1 d = new DrugSearchTo1();
                d.setName("aspirin");
                d.setId(1);
                toReturn.add(d);
            }
            return toReturn;
        }

        @Override
        public DrugSearchTo1 details(String id) {
            if ("1".equals(id)) {
                DrugSearchTo1 s = new DrugSearchTo1();
                List<DrugSearchTo1.DrugComponentTo1> components = new ArrayList<>();
                DrugSearchTo1.DrugComponentTo1 c = new DrugSearchTo1.DrugComponentTo1();
                c.setStrength(1.0);
                c.setUnit("mg");
                components.add(c);
                s.setComponents(components);
                return s;
            }
            return null;
        }
    }
}
