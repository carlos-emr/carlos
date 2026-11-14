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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Data-dictionary capacities are independent of the broader XSD limits: text CARLOS writes keeps
 * the dictionary's 1,000 and 32,000 characters at the writer, and an imported element retained
 * unchanged keeps the schema's 1,024 and 32,768 (CdsExportDictionaryLength).
 */
class CdsExportDictionaryBoundaryUnitTest {
    private static final CdsExportDateDto UNKNOWN =
            new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);

    @Test
    void shouldVerifyMedicationOneKAndThirtyTwoKBoundariesUseDecimalThousands_withoutTruncation()
            throws Exception {
        for (var field :
                List.of(
                        Field.Duration,
                        Field.RefillDuration,
                        Field.Quantity,
                        Field.RefillQuantity,
                        Field.Notes,
                        Field.PrescriptionInstructions)) {
            int maximum =
                    field == Field.Notes || field == Field.PrescriptionInstructions ? 32000 : 1000;
            assertThat(field.maximum()).isEqualTo(maximum);
            String text = (maximum == 1000 ? "1" : "😀").repeat(maximum);
            var values = new EnumMap<Field, String>(Field.class);
            values.put(Field.DrugName, "Synthetic medication");
            values.put(field, text);
            var exact = new CdsExportMedicationDto(UNKNOWN, UNKNOWN, null, null, null, values);
            CdsExportMedicationXmlWriter.validate(exact);
            assertThat(exact.fields().get(field)).isEqualTo(text);
            values.put(field, text + "x");
            var overflow = new CdsExportMedicationDto(UNKNOWN, UNKNOWN, null, null, null, values);
            assertThatThrownBy(() -> CdsExportMedicationXmlWriter.validate(overflow))
                    .hasMessage("CDS_EXPORT_MEDICATION_VALUES_REQUIRES_REVIEW");
        }
    }

    @Test
    void
            shouldVerifyStructuredStrengthHasIndependentTenAndTwentyCharacterBounds_forDictionaryBoundaryContract()
                    throws Exception {
        var values = Map.of(Field.DrugName, "Synthetic medication");
        CdsExportMedicationXmlWriter.validate(
                new CdsExportMedicationDto(
                        UNKNOWN, UNKNOWN, "1".repeat(10), "u".repeat(20), null, values));
        for (var pair : List.of(List.of("1".repeat(11), "u"), List.of("1", "u".repeat(21)))) {
            var value =
                    new CdsExportMedicationDto(
                            UNKNOWN, UNKNOWN, pair.getFirst(), pair.getLast(), null, values);
            assertThatThrownBy(() -> CdsExportMedicationXmlWriter.validate(value))
                    .hasMessage("CDS_EXPORT_MEDICATION_VALUES_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldHoldAllergyNotesCarlosWrites_toTheDictionaryLengthAndImportedOnesToTheSchema()
            throws Exception {
        var exact = allergy("😀".repeat(32000));
        CdsExportAllergyXmlWriter.validate(exact);
        assertThatThrownBy(() -> CdsExportAllergyXmlWriter.validate(allergy("x".repeat(32001))))
                .hasMessage("CDS_EXPORT_ALLERGY_VALUES_REQUIRES_REVIEW");
        // The retained assembler passes true only for the imported element's own text, unchanged
        // (CdsExportAllergyRetainedAssemblerUnitTest): it is exported as imported.
        for (int length : List.of(32001, 32768))
            CdsExportAllergyXmlWriter.validate(allergy("x".repeat(length)), true);
        assertThatThrownBy(
                        () -> CdsExportAllergyXmlWriter.validate(allergy("x".repeat(32769)), true))
                .hasMessage("CDS_EXPORT_ALLERGY_VALUES_REQUIRES_REVIEW");
    }

    @Test
    void shouldHoldMedicationTextCarlosWrites_toTheDictionaryLengthAndImportedElementsToTheSchema()
            throws Exception {
        for (var field :
                List.of(
                        Field.Duration,
                        Field.RefillDuration,
                        Field.Quantity,
                        Field.RefillQuantity,
                        Field.Notes,
                        Field.PrescriptionInstructions)) {
            boolean thousand = field.maximum() == 1000;
            assertThat(field.schemaMaximum()).isEqualTo(thousand ? 1024 : 32768);
            var values = new EnumMap<Field, String>(Field.class);
            values.put(Field.DrugName, "Synthetic medication");
            values.put(field, "x".repeat(field.maximum() + 1));
            var gap = new CdsExportMedicationDto(UNKNOWN, UNKNOWN, null, null, null, values);
            // Written from the reconciled value it is CARLOS text and keeps the dictionary length.
            assertThatThrownBy(() -> CdsExportMedicationXmlWriter.validate(gap))
                    .hasMessage("CDS_EXPORT_MEDICATION_VALUES_REQUIRES_REVIEW");
            // Kept as its imported element it keeps the schema's.
            CdsExportMedicationXmlWriter.validate(gap, Set.of(field));
            values.put(field, "x".repeat(field.schemaMaximum() + 1));
            var past = new CdsExportMedicationDto(UNKNOWN, UNKNOWN, null, null, null, values);
            assertThatThrownBy(() -> CdsExportMedicationXmlWriter.validate(past, Set.of(field)))
                    .hasMessage("CDS_EXPORT_MEDICATION_VALUES_REQUIRES_REVIEW");
        }
    }

    @Test
    void
            shouldVerifyCaughtDictionaryFailureCannotProduceACompletedXmlDocument_forDictionaryBoundaryContract() {
        var output = new ByteArrayOutputStream();
        var composer = new CdsExportXmlComposer();
        var categories =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        assertThatThrownBy(
                        () ->
                                composer.composeFromSource(
                                        output,
                                        "a".repeat(64),
                                        "b".repeat(64),
                                        "2026.08.0-test",
                                        CdsLogicalProfile.ONTARIO_MD_5_2,
                                        categories,
                                        emitter -> {
                                            emitter.emit(
                                                    new CdsExportXmlComposer.Section(
                                                            new CdsExportIdentityDto(
                                                                    "b".repeat(64),
                                                                    CdsCategory.DEMOGRAPHICS,
                                                                    "c".repeat(64)),
                                                            xml -> {}));
                                            try {
                                                emitter.emit(
                                                        new CdsExportXmlComposer.Section(
                                                                new CdsExportIdentityDto(
                                                                        "b".repeat(64),
                                                                        CdsCategory
                                                                                .ALLERGIES_AND_ADVERSE_REACTIONS,
                                                                        "d".repeat(64)),
                                                                xml ->
                                                                        new CdsExportAllergyXmlWriter()
                                                                                .write(
                                                                                        allergy(
                                                                                                "x"
                                                                                                        .repeat(
                                                                                                                32001)),
                                                                                        xml)));
                                            } catch (IOException expected) {
                                            }
                                        },
                                        ignored -> {},
                                        1L << 20,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
    }

    private static CdsExportAllergyDto allergy(String notes) {
        return new CdsExportAllergyDto(
                "Synthetic allergen",
                null,
                null,
                null,
                null,
                UNKNOWN,
                null,
                null,
                null,
                UNKNOWN,
                notes,
                false);
    }
}
