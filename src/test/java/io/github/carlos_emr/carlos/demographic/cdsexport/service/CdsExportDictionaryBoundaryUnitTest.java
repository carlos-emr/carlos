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
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Data-dictionary capacities are independent of the broader historical XSD limits. */
class CdsExportDictionaryBoundaryUnitTest {
    private static final CdsExportDateDto UNKNOWN =
            new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);

    @Test
    void medicationOneKAndThirtyTwoKBoundariesUseDecimalThousandsWithoutTruncation()
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
    void structuredStrengthHasIndependentTenAndTwentyCharacterBounds() throws Exception {
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
    void allergyDirectAndRetainedValuesUseTheSameThirtyTwoKBoundary() throws Exception {
        var exact = allergy("😀".repeat(32000));
        CdsExportAllergyXmlWriter.validate(exact);
        assertThatThrownBy(() -> CdsExportAllergyXmlWriter.validate(allergy("x".repeat(32001))))
                .hasMessage("CDS_EXPORT_ALLERGY_VALUES_REQUIRES_REVIEW");
        var decision =
                new CdsExportAllergyFieldReconciler.Result(
                        "a".repeat(64),
                        "protected-unit",
                        new CdsExportAllergyMappingDto(
                                allergy("x".repeat(32001)),
                                Map.of(
                                        new CdsExportNativeFieldKeyDto(
                                                "allergies", "1", "DESCRIPTION"),
                                        new CdsExportFieldAssessmentDto(
                                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                                CdsExportFieldAssessmentDto.Outcome.MAPPED,
                                                "OffendingAgentDescription",
                                                "TEST"))),
                        Set.of(),
                        Set.of());
        var values = mock(CdsExportRetainedSectionWriter.Values.class);
        var index = mock(CdsExportRetainedIndexDto.class);
        when(index.sourceManifestSha256()).thenReturn("a".repeat(64));
        when(index.appliedUnits()).thenReturn(Set.of("protected-unit"));
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        "a".repeat(64), Map.of(), Set.of(), Set.of());
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyRetainedAssembler()
                                        .prepare(
                                                27, decision, index, checked, List.of(), values,
                                                () -> {}))
                .hasMessage("CDS_EXPORT_ALLERGY_VALUES_REQUIRES_REVIEW");
    }

    @Test
    void retainedMedicationCannotBypassTheDirectWriterCapacityGate() {
        var mapping =
                new CdsExportMedicationDto(
                        UNKNOWN,
                        UNKNOWN,
                        null,
                        null,
                        null,
                        Map.of(Field.DrugName, "Synthetic", Field.Notes, "x".repeat(32001)));
        var fields =
                Map.of(
                        new CdsExportNativeFieldKeyDto("drugs", "1", "customName"),
                        new CdsExportFieldAssessmentDto(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                CdsExportFieldAssessmentDto.Outcome.MAPPED,
                                "DrugName",
                                "TEST"));
        var decision =
                new CdsExportMedicationFieldReconciler.Result(
                        "a".repeat(64),
                        "protected-unit",
                        new CdsExportMedicationMappingDto(mapping, fields),
                        Set.of(),
                        Set.of());
        var values = mock(CdsExportRetainedSectionWriter.Values.class);
        var index = mock(CdsExportRetainedIndexDto.class);
        when(index.sourceManifestSha256()).thenReturn("a".repeat(64));
        when(index.appliedUnits()).thenReturn(Set.of("protected-unit"));
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        "a".repeat(64), Map.of(), Set.of(), Set.of());
        assertThatThrownBy(
                        () ->
                                new CdsExportMedicationRetainedAssembler()
                                        .prepare(
                                                27, decision, index, checked, List.of(), values,
                                                () -> {}))
                .hasMessage("CDS_EXPORT_MEDICATION_VALUES_REQUIRES_REVIEW");
    }

    @Test
    void caughtDictionaryFailureCannotProduceACompletedXmlDocument() {
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
