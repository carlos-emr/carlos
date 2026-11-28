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
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BpmhDrug}.
 *
 * <p>Tests the computed property composition logic for the BPMH
 * (Best Possible Medication History) drug bean, including "what",
 * "how", and "why" field generation from underlying drug properties.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("BpmhDrug")
class BpmhDrugUnitTest {

    private BpmhDrug drug;

    @BeforeEach
    void setUp() {
        drug = new BpmhDrug();
    }

    @Nested
    @DisplayName("getWhy")
    class GetWhy {

        @Test
        @DisplayName("should return set value")
        void shouldReturnSetValue() {
            drug.setWhy("Infection");
            assertThat(drug.getWhy()).isEqualTo("Infection");
        }

        @Test
        @DisplayName("should return empty string when null")
        void shouldReturnEmpty_whenNull() {
            assertThat(drug.getWhy()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getWhat - drug description composition")
    class GetWhat {

        @Test
        @DisplayName("should compose description from dosage, unit, generic name, and form")
        void shouldCompose_fromDosageUnitNameForm() {
            drug.setDosage("500");
            drug.setUnit("mg");
            drug.setGenericName("Amoxicillin");
            drug.setDrugForm("CAP");

            String what = drug.getWhat();
            assertThat(what).contains("500");
            assertThat(what).contains("mg");
            assertThat(what).contains("Amoxicillin");
            assertThat(what).contains("CAP");
        }

        @Test
        @DisplayName("should handle duplicate dosage in generic name")
        void shouldHandleDuplicateDosage_inGenericName() {
            drug.setDosage("500");
            drug.setUnit("mg");
            drug.setGenericName("Amoxicillin 500mg");
            drug.setDrugForm("CAP");

            String what = drug.getWhat();
            assertThat(what).contains("Amoxicillin");
        }

        @Test
        @DisplayName("should handle dosage with unit already included")
        void shouldHandleDosage_withUnitIncluded() {
            drug.setDosage("500mg");
            drug.setUnit("mg");
            drug.setGenericName("Amoxicillin");
            drug.setDrugForm("TAB");

            String what = drug.getWhat();
            assertThat(what).isNotEmpty();
        }

        @Test
        @DisplayName("should use custom name when generic name is empty")
        void shouldUseCustomName_whenGenericNameEmpty() {
            drug.setGenericName("");
            drug.setCustomName("My Custom Drug");
            drug.setDosage("100");
            drug.setUnit("mg");

            String what = drug.getWhat();
            assertThat(what).contains("My Custom Drug");
        }

        @Test
        @DisplayName("should combine dosage with name without dose")
        void shouldCombineDosage_withNameWithoutDose() {
            drug.setDosage("250");
            drug.setUnit("mg");
            drug.setGenericName("Cephalexin");
            drug.setDrugForm("CAP");

            String what = drug.getWhat();
            assertThat(what).contains("250");
            assertThat(what).contains("Cephalexin");
        }
    }

    @Nested
    @DisplayName("getHow - instruction composition")
    class GetHow {

        @Test
        @DisplayName("should extract instruction from multi-line special field")
        void shouldExtractInstruction_fromMultiLineSpecial() {
            drug.setSpecial("Take 1 tablet by mouth twice daily\nwith food");

            String how = drug.getHow();
            assertThat(how).isEqualTo("Take 1 tablet by mouth twice daily\nwith food");
        }

        @Test
        @DisplayName("should compose instruction from method, take, and frequency")
        void shouldComposeInstruction_fromMethodTakeFreq() {
            drug.setMethod("Take");
            drug.setCustomName("Synthetic");
            drug.setUnit("mL");
            drug.setTakeMin("1");
            drug.setTakeMax("2");
            drug.setFreqCode("BID");
            drug.setRoute("PO");
            drug.setDuration("28");
            drug.setDurUnit("D");

            String how = drug.getHow();
            assertThat(how).isEqualTo("Take 1 to 2 mL BID PO");
        }
    }

    @Test
    void shouldRequireARecordedUnit_whenOnlyAProductStrengthIsAvailable() {
        drug.setCustomName("Recorded product");
        drug.setDosage("10 mg");
        drug.setTakeMin("1");
        drug.setUnit("mg");
        drug.setDrugForm("tablet");
        assertThat(drug.getHow()).isEmpty();

        drug.setHow(null);
        drug.setUnit("tablet");
        drug.setTakeMax("0");
        assertThat(drug.getHow()).isEqualTo("1 tablet");
    }

    @Test
    void shouldKeepAuthoredDirections_whenTextResemblesAPrescriptionEnvelope() {
        drug.setGenericName("Synthetic");
        drug.setSpecial("Synthetic substitute on Monday\nQty: verify with patient");
        assertThat(drug.getHow()).isEqualTo("Synthetic substitute on Monday\nQty: verify with patient");
    }

    @Test
    void shouldExcludeMitteAndPharmacyText_whenReadingAdministrationDirections() {
        drug.setGenericName("Synthetic");
        drug.setSpecial("Synthetic\nSupply blister pack\nMitte:7 days Repeats:0");
        drug.usePrescriptionDirections(null, "Supply blister pack", null);
        assertThat(drug.getHow()).isEmpty();

        drug.setSpecial("Synthetic\nTake one daily\nThen one every other day\nMitte:2 weeks Repeats:1");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Take one daily\nThen one every other day");

        drug.setSpecial("Mitte: confirm with the pharmacy");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Mitte: confirm with the pharmacy");

        drug.setSpecial("Mitte: confirm with the pharmacy Repeats: confirm with prescriber");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Mitte: confirm with the pharmacy Repeats: confirm with prescriber");

        drug.setSpecial("Mitte: confirm with the pharmacy Repeats:0");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Mitte: confirm with the pharmacy Repeats:0");

        drug.setSpecial("Mitte:7 days Repeats: confirm with prescriber");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Mitte:7 days Repeats: confirm with prescriber");

        drug.setSpecial("Mitte:7 days only if confirmed Repeats:0");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Mitte:7 days only if confirmed Repeats:0");

        drug.setSpecial("Use only as directed Repeats:0");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Use only as directed Repeats:0");
    }

    @Test
    void shouldLeaveDirectionsMissing_whenOnlyDispensingInstructionsWereRecorded() {
        drug.setSpecial("Qty:30 Repeats:1");
        assertThat(drug.getHow()).isEmpty();

        drug.setGenericName("Synthetic");
        drug.setDosage("10 units");
        drug.setSpecial("Synthetic 10 units\r\nSupply blister pack\r\n  ");
        drug.usePrescriptionDirections(null, "Supply blister pack", null);
        assertThat(drug.getHow()).isEmpty();
        drug.setDosage("10");
        drug.setUnit("units");
        drug.usePrescriptionDirections(null, "Supply blister pack", null);
        assertThat(drug.getHow()).isEmpty();
        drug.setSpecial("Synthetic 10 units\nTake with food  \nSupply blister pack\n");
        drug.usePrescriptionDirections(null, "Supply blister pack", null);
        assertThat(drug.getHow()).isEqualTo("Take with food  ");
        drug.setSpecial("Synthetic 10 units\nTake with food  \n");
        drug.usePrescriptionDirections(null, "Supply blister pack", null);
        assertThat(drug.getHow()).isEqualTo("Take with food  \n");
        assertThat(drug.toString()).isEqualTo("BpmhDrug[protected]");
    }

    @Test
    void shouldPreserveUnrecognizedText_whenFooterLineContainsPatientDirections() {
        drug.setSpecial("Take one tablet daily\nQty:7 tablets. Take with food.\nRepeats:0");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Take one tablet daily\nQty:7 tablets. Take with food.\nRepeats:0");

        drug.setSpecial("Take one tablet daily\nQty:7 tablets. Take with food. Repeats:0");
        drug.usePrescriptionDirections(null, null, null);
        assertThat(drug.getHow()).isEqualTo("Take one tablet daily\nQty:7 tablets. Take with food. Repeats:0");

        for (String malformedFooter : java.util.List.of(
                "Take daily\nQty:30. tabs Repeats:0",
                "Take daily\nQty:1 bottle (120 mL Repeats:0",
                "Take daily\nQty:1 bottle (large) Repeats:0",
                "Take daily\nQty:1 bottle (120) Repeats:0",
                "Take daily\nQty:1 bottle (120 mL.) Repeats:0")) {
            drug.setSpecial(malformedFooter);
            drug.usePrescriptionDirections(null, null, null);
            assertThat(drug.getHow()).isEqualTo(malformedFooter);
        }
    }

    @Test
    void shouldStripContainerUnitsAndPharmacyNotes_whenHeadingsOrFootersArePresent() {
        drug.setSpecial("AMOXICILLIN\nTake 5 mL TID\nDispense oral syringe\nQty:1 bottle (120 mL) Repeats:0");
        drug.usePrescriptionDirections("AMOXICILLIN", "Dispense oral syringe", null);
        assertThat(drug.getHow()).isEqualTo("Take 5 mL TID");

        drug.setSpecial("AMOXICILLIN\nTake 5 mL TID\nDispense oral syringe\nMitte:1 bottle (120 mL) Repeats:0");
        drug.usePrescriptionDirections("AMOXICILLIN", "Dispense oral syringe", null);
        assertThat(drug.getHow()).isEqualTo("Take 5 mL TID");

        drug.setDosage("250mg");
        drug.setUnit("mg");
        drug.setSpecial("AMOXICILLIN 250 MG\nTake 1 capsule PO TID\nDispense in blister pack\nQty:21 Repeats:0");
        drug.usePrescriptionDirections("AMOXICILLIN", "Dispense in blister pack", null);
        assertThat(drug.getHow()).isEqualTo("Take 1 capsule PO TID");

        drug.setDosage("1000iu");
        drug.setUnit("IU");
        drug.setSpecial("VITAMIN D 1000 IU\nTake 1 tablet daily\nFax renewal to clinic\nQty:90 Repeats:3");
        drug.usePrescriptionDirections("VITAMIN D", "Fax renewal to clinic", null);
        assertThat(drug.getHow()).isEqualTo("Take 1 tablet daily");

        drug.setDosage("");
        drug.setUnit("");
        drug.setSpecial("Synthetic 5 drops daily");
        drug.usePrescriptionDirections("Synthetic", null, null);
        assertThat(drug.getHow()).isEqualTo("Synthetic 5 drops daily");

        drug.setSpecial("Take 1 tablet daily\nDispense in blister pack\nQty:30 Repeats:0");
        drug.usePrescriptionDirections(null, "Dispense in blister pack", null);
        assertThat(drug.getHow()).isEqualTo("Take 1 tablet daily");

        drug.setSpecial("Take with food");
        drug.usePrescriptionDirections(null, "Take with food", null);
        assertThat(drug.getHow()).isEqualTo("Take with food");

        BpmhDrug direct = new BpmhDrug();
        direct.setGenericName("atorvastatin 20mg tablet");
        direct.setSpecial("LIPITOR\nTake 1 tablet daily\nDo not crush\nQty:30 Repeats:0");
        direct.setSpecial_instruction("Do not crush");
        assertThat(direct.getHow()).isEqualTo("LIPITOR\nTake 1 tablet daily");
    }

    @Nested
    @DisplayName("instruction property")
    class InstructionProperty {

        @Test
        @DisplayName("should get and set instruction")
        void shouldGetAndSetInstruction() {
            drug.setInstruction("Take with food");
            assertThat(drug.getInstruction()).isEqualTo("Take with food");
        }
    }

    @Nested
    @DisplayName("basic properties")
    class BasicProperties {

        @Test
        @DisplayName("should get and set all basic properties")
        void shouldGetAndSetBasicProperties() {
            drug.setGenericName("Amoxicillin");
            drug.setDosage("500");
            drug.setUnit("mg");
            drug.setFreqCode("BID");
            drug.setRoute("PO");
            drug.setDrugForm("CAP");
            drug.setDuration("14");
            drug.setDurUnit("D");
            drug.setQuantity("28");
            drug.setRepeat("3");
            drug.setAtc("J01CA04");

            assertThat(drug.getGenericName()).isEqualTo("Amoxicillin");
            assertThat(drug.getDosage()).isEqualTo("500");
            assertThat(drug.getUnit()).isEqualTo("mg");
            assertThat(drug.getFreqCode()).isEqualTo("BID");
            assertThat(drug.getRoute()).isEqualTo("PO");
            assertThat(drug.getDrugForm()).isEqualTo("CAP");
            assertThat(drug.getDuration()).isEqualTo("14");
            assertThat(drug.getDurUnit()).isEqualTo("D");
            assertThat(drug.getQuantity()).isEqualTo("28");
            assertThat(drug.getRepeat()).isEqualTo("3");
            assertThat(drug.getAtc()).isEqualTo("J01CA04");
        }
    }
}
