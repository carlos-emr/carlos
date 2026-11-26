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
         * stored "5 mg /".
         */
        @ParameterizedTest(name = "stored \"{0}\" -> {1} {2}")
        @CsvSource({
            "5 mg / 20 mg, 5, mg",
            "5.0 mg / 325.0 mg, 5, mg",
            "325mg 30mg, 325, mg",
            "'5 mg, 20 mg', 5, mg",
            "20mg tablet, 20, mg",
            "2.5 mg/mL, 2.5, mg/mL",
            "250mg/5ml, 250, mg/5ml",
            "10 %, 10, %",
            ".5 mg, 0.5, mg"
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
        @ValueSource(strings = {"5  mg", "5\t\tmg", "5 \t mg", "5   mg / 20 mg"})
        @DisplayName("should read the unit after any run of blanks")
        void shouldReadUnit_afterAnyRunOfBlanks(String stored) {
            Drug d = new Drug();
            d.setDosage(stored);

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getStrength()).isEqualTo(5f);
            assertThat(t.getStrengthUnit()).isEqualTo("mg");
        }

        /**
         * A number that goes on after a comma, a slash or a hyphen is not one plain decimal:
         * grouped digits ("1,000 mg"), a decimal comma ("2,5 mg"), a fraction ("1/2 mg"), a
         * combination written together ("5/20 mg") or a range ("5-10 mg"). Its first digits are
         * not the strength (1 is not 1,000), and the single strength field cannot carry the rest,
         * so none is reported; the stored text is unchanged.
         */
        @ParameterizedTest(name = "stored \"{0}\"")
        @ValueSource(strings = {"1,000 mg", "2,5 mg", "1/2 mg", "5/20 mg", "5-10 mg", "0.5-1 mg", "1,000"})
        @DisplayName("should report no strength when the first number is not one plain decimal")
        void shouldReportNoStrength_whenFirstNumberIsNotOnePlainDecimal(String stored) {
            assertNoStrengthRead(stored);
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

        @ParameterizedTest(name = "stored \"{0}\"")
        @ValueSource(strings = {"as directed", "mg", "/"})
        @DisplayName("should report no strength for text that states no number")
        void shouldReportNoStrength_whenStoredTextHasNoNumber(String stored) {
            assertNoStrengthRead(stored);
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
