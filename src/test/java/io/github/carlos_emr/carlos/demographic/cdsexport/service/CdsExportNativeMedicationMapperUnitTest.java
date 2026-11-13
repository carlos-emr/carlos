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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportMedicationMapperUnitTest.medication;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeMedicationMapperUnitTest {
    final CdsExportNativeMedicationMapper mapper = new CdsExportNativeMedicationMapper();

    static Map<String, String> source() {
        var fields = CdsExportMedicationMapperUnitTest.baseline();
        fields.put("cds_currentness", null);
        fields.put("cds_dose_text", null);
        fields.put("rxStatus", null);
        fields.put("regional_identifier", "00123456");
        fields.put("dosage", "5 mg / 10 mL");
        fields.put("freqcode", "OD");
        fields.put("duration", "02");
        fields.put("durunit", "W");
        fields.put("takemin", "0");
        fields.put("takemax", "0");
        fields.put("script_no", "71");
        fields.put("pharmacyId", "9");
        fields.put("end_date", "2026-07-31");
        fields.put("hide_from_drug_profile", "1");
        return fields;
    }

    private static CdsExportCapturedTextRowDto publicReference(Map<String, String> source)
            throws Exception {
        return new CdsExportCapturedTextRowDto(
                "medication_reference",
                CdsExportPatientTableSourceReader.key(51),
                Map.of(
                        "drugid",
                        "51",
                        "GCN_SEQNO",
                        source.get("GCN_SEQNO"),
                        "regional_identifier",
                        source.get("regional_identifier"),
                        "public_reference",
                        JsonMapper.builder()
                                .build()
                                .writeValueAsString(
                                        CdsExportDrugReferenceSourceReaderUnitTest.reference())));
    }

    @Test
    void
            shouldPreserveRepresentativeDinNamesAndCompleteCombinationStrength_withoutCatalogueSubstitution()
                    throws Exception {
        var fields = source();
        fields.put("GCN_SEQNO", "17");
        fields.put("customName", "Authored generic name");
        fields.put("BN", "Historical source brand");
        fields.put("dosage", "5 mg / 125 mg");
        var reference =
                new CdsExportDrugReferenceDto(
                        1,
                        CdsExportDrugReferenceDto.AUTHORITY,
                        "00123456",
                        "Different representative brand",
                        List.of(
                                new CdsExportDrugReferenceDto.Component(
                                        "First ingredient", "5", "mg"),
                                new CdsExportDrugReferenceDto.Component(
                                        "Second ingredient", "125", "mg")));
        var proof =
                new CdsExportCapturedTextRowDto(
                        "medication_reference",
                        CdsExportPatientTableSourceReader.key(51),
                        Map.of(
                                "drugid",
                                "51",
                                "GCN_SEQNO",
                                "17",
                                "regional_identifier",
                                "00123456",
                                "public_reference",
                                JsonMapper.builder().build().writeValueAsString(reference)));
        var result = mapper.map(27, medication(fields), List.of(), Map.of(), proof);
        assertThat(result.value().fields())
                .containsEntry(Field.DrugName, "Authored generic name")
                .containsEntry(Field.DrugDescription, "5 mg / 125 mg")
                .containsEntry(Field.DrugIdentificationNumber, "00123456");
        assertThat(result.value().strengthAmount()).isEqualTo("5");
        assertThat(result.metadata())
                .contains("Historical source brand", "Second ingredient", "125");
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(result, xml));
    }

    @Test
    void shouldEmitTheVerifiedDinAndTypedIngredientStrength_forDatabaseSelectedMedication()
            throws Exception {
        var fields = source();
        fields.put("GCN_SEQNO", "17");
        fields.put("customName", null);
        fields.put("BN", "Native reference tablet");
        fields.put("dosage", "5 mg");
        var result =
                mapper.map(27, medication(fields), List.of(), Map.of(), publicReference(fields));
        assertThat(result.value().fields())
                .containsEntry(Field.DrugIdentificationNumber, "00123456");
        assertThat(result.value().strengthAmount()).isEqualTo("5");
        assertThat(result.value().strengthUnit()).isEqualTo("mg");
        var assessed = List.of(medication(fields), publicReference(fields));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(assessed, result.fields(), 1000, () -> {});
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(result, xml));
    }

    @Test
    void shouldRefuseStaleOrConflictingDrugReferenceEvidence_insteadOfInventingAuthority()
            throws Exception {
        var fields = source();
        fields.put("GCN_SEQNO", "17");
        fields.put("customName", null);
        fields.put("BN", "Native reference tablet");
        fields.put("dosage", "5 mg");
        assertThatThrownBy(() -> mapper.map(27, medication(fields), List.of(), Map.of()))
                .isInstanceOf(java.io.IOException.class);
        var reference = publicReference(fields);
        for (var change :
                List.of(Map.of("regional_identifier", "99999999"), Map.of("GCN_SEQNO", "18"))) {
            var edited = new LinkedHashMap<>(fields);
            edited.putAll(change);
            assertThatThrownBy(
                            () ->
                                    mapper.map(
                                            27, medication(edited), List.of(), Map.of(), reference))
                    .isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldPreserveThePrintedNativeSigAndPharmacyInstructions_inTheirOwnLabeledValues()
            throws Exception {
        var fields = source();
        fields.put("special", "Authored native SIG café 😀");
        fields.put("special_instruction", "Exact pharmacy instruction");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().fields())
                .containsEntry(
                        Field.PrescriptionInstructions,
                        "Authored native SIG café 😀\n"
                                + "[Native pharmacy instructions]\n"
                                + "Exact pharmacy instruction");
        assertThat(result.metadata())
                .contains("Authored native SIG café 😀", "Exact pharmacy instruction");
        fields.put("archived", null);
        assertThatThrownBy(() -> mapper.map(27, medication(fields), List.of(), Map.of()))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void
            shouldPreserveTheEntireOrdinaryNativePrescription_withoutInventingCodeAuthorityOrCurrentness()
                    throws Exception {
        var fields = source();
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().fields())
                .containsEntry(Field.DrugDescription, "5 mg / 10 mL")
                .containsEntry(Field.Duration, "14")
                .containsEntry(Field.Frequency, "QD")
                .doesNotContainKeys(
                        Field.DrugIdentificationNumber,
                        Field.PastMedications,
                        Field.PrescriptionStatus,
                        Field.Dosage);
        var json = JsonMapper.builder().build().readTree(result.metadata());
        assertThat(json.path("fields").size()).isEqualTo(fields.size());
        for (var entry : fields.entrySet()) {
            var actual = json.path("fields").get(entry.getKey());
            if (entry.getValue() == null) assertThat(actual.isNull()).isTrue();
            else assertThat(actual.textValue()).isEqualTo(entry.getValue());
        }
        assertThat(json.path("regionalIdentifierAuthority").textValue()).isEqualTo("UNQUALIFIED");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(medication(fields)), result.fields(), 1000, () -> {});
        String xml =
                new String(
                        render(
                                CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                x -> mapper.write(result, x)),
                        StandardCharsets.UTF_8);
        assertThat(xml)
                .contains(
                        "CARLOS.Medication.Source.v1",
                        "CARLOS.Medication.Archived.v1",
                        "5 mg / 10 mL");
        assertThat(xml.indexOf("ResidualInfo>")).isLessThan(xml.indexOf("DrugName>"));
        assertThat(xml)
                .doesNotContain(
                        "<cds:DrugIdentificationNumber>",
                        "<cds:Strength>",
                        "<cds:Dosage>",
                        "<cds:PastMedications>");
    }

    @Test
    void shouldKeepNativeMonthsAtThirtyDaysWithoutMultiplyingRefills() throws Exception {
        var fields = source();
        fields.put("duration", "2");
        fields.put("durunit", "M");
        fields.put("repeat", "5");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Duration, "60")
                .containsEntry(Field.NumberOfRefills, "5");
    }

    @Test
    void
            shouldKeepAnArchivedActivePrescriptionArchived_withoutConvertingArchiveIntoAClinicalStatus()
                    throws Exception {
        var fields = source();
        fields.put("archived", "1");
        fields.put("archived_reason", "Stopped display");
        fields.put("rxStatus", "Active");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.archived()).isTrue();
        assertThat(result.value().fields()).containsEntry(Field.PrescriptionStatus, "Active");
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, x -> mapper.write(result, x));
    }

    private static CdsExportCapturedTextRowDto prescribed(Map<String, String> fields) {
        // takemin = takemax = 1.0f, as the Rx page stores "Take 1 tab".
        var one =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric(
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric.Codec
                                .IEEE754_BINARY32_V1,
                        "3f800000");
        fields.put("takemin", "1.0");
        fields.put("takemax", "1.0");
        return new CdsExportCapturedTextRowDto(
                "drugs",
                CdsExportPatientTableSourceReader.key(51),
                fields,
                Map.of("takemin", one, "takemax", one));
    }

    @Test
    void shouldNeverExportTheProductStrengthUnit_asTheUnitOfACountedDose() throws Exception {
        // RxWriteScript2Action.createNewRx: Amoxicillin 500 mg, "Take 1 tab TID".
        var fields = source();
        fields.put("dosage", "500.0mg");
        fields.put("unit", "mg");
        fields.put("unitName", null);
        fields.put("drug_form", "CAPSULE");
        var capsule = mapper.map(27, prescribed(fields), List.of(), Map.of());
        assertThat(capsule.value().fields())
                .containsEntry(Field.Dosage, "1")
                .containsEntry(Field.DosageUnitOfMeasure, "capsule")
                .containsEntry(Field.DrugDescription, "500.0mg");
        // A form that is not counted (a suspension) has no recorded dose unit: none is invented.
        fields.put("dosage", "250mg/5mL");
        fields.put("unit", "mL");
        fields.put("drug_form", "SUSPENSION");
        var suspension = mapper.map(27, prescribed(fields), List.of(), Map.of());
        assertThat(suspension.value().fields())
                .containsEntry(Field.Dosage, "1")
                .doesNotContainKey(Field.DosageUnitOfMeasure);
        assertThat(suspension.metadata()).contains("\"unit\":\"mL\"", "250mg/5mL");
        // A unit the strength does not name is the dose unit as recorded.
        fields.put("dosage", "100mcg/dose");
        fields.put("unit", "puff");
        fields.put("drug_form", "AEROSOL, METERED DOSE");
        assertThat(mapper.map(27, prescribed(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.DosageUnitOfMeasure, "puff");
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(capsule, xml));
    }

    @Test
    void shouldExportTheQuantityWithItsUnit_andNeverAsADoseUnit() throws Exception {
        // A custom drug with quantity "100 mL", and a Mitte "1 month" prescription.
        var fields = source();
        fields.put("unit", "");
        fields.put("unitName", "mL");
        fields.put("quantity", "100");
        var liquid = mapper.map(27, prescribed(fields), List.of(), Map.of());
        assertThat(liquid.value().fields())
                .containsEntry(Field.Quantity, "100 mL")
                .doesNotContainKey(Field.DosageUnitOfMeasure);
        fields.put("unitName", "month");
        fields.put("quantity", "1");
        var mitte = mapper.map(27, prescribed(fields), List.of(), Map.of());
        assertThat(mitte.value().fields())
                .containsEntry(Field.Quantity, "1 month")
                .doesNotContainKey(Field.DosageUnitOfMeasure);
        // An importer-shaped (or re-prescribed imported) row repeats the dose unit in unitName.
        fields.put("unit", "tablet");
        fields.put("unitName", "tablet");
        fields.put("quantity", "30");
        assertThat(mapper.map(27, prescribed(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Quantity, "30")
                .containsEntry(Field.DosageUnitOfMeasure, "tablet");
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(mitte, xml));
    }

    @Test
    void shouldUseTheAuthoredCustomName_andRetainTheSeparateBrandAndAmbiguousUnits()
            throws Exception {
        var fields = source();
        fields.put("BN", "Separate brand");
        fields.put("unitName", "different unit");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().fields())
                .containsEntry(Field.DrugName, "Synthetic café 😀")
                .doesNotContainKey(Field.DosageUnitOfMeasure);
        assertThat(result.metadata()).contains("Separate brand", "different unit");
    }

    @Test
    void shouldRefuseUnboundImportedAuthorityAndMalformedNativeRegimens() throws Exception {
        for (var changes :
                List.of(
                        Map.of("cds_currentness", "CURRENT"),
                        Map.of("cds_dose_text", "1"),
                        Map.of("cds_projection_incomplete", "1"),
                        Map.of("durunit", "unknown"),
                        Map.of("duration", "one"),
                        Map.of("rxStatus", "Completed", "past_med", "0"),
                        Map.of("archived", "yes"),
                        Map.of("customName", " "))) {
            var fields = source();
            fields.putAll(changes);
            assertThatThrownBy(() -> mapper.map(27, medication(fields), List.of(), Map.of()))
                    .isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldNotGuessAnOutsidePrescribersNameParts() throws Exception {
        var fields = source();
        fields.put("outside_provider_name", "Dr. Authored Full Name");
        fields.put("outside_provider_ohip", "123456");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().prescriber()).isNull();
        assertThat(result.metadata()).contains("Dr. Authored Full Name", "123456");
    }
}
