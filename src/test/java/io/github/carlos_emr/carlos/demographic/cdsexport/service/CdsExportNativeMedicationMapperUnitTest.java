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
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

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

    @Test
    void shouldCarryAnUnknownStartDatesPrecision_inTheResidualInsteadOfAsAStartDate()
            throws Exception {
        // Review M10: the start-date-unknown flag suppresses StartDate, so its year-only
        // precision row must not stay "MAPPED to StartDate"; it travels with the drug row.
        var fields = source();
        fields.put("start_date_unknown", "1");
        fields.put("rx_date", "2019-01-01");
        var result =
                mapper.map(
                        27,
                        CdsExportMedicationMapperUnitTest.medication(fields),
                        List.of(CdsExportMedicationMapperUnitTest.precision(12, 51, 5, "YYYY")),
                        Map.of());
        assertThat(result.value().start().precision())
                .isEqualTo(io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto.Precision.ABSENT);
        var format =
                result.fields()
                        .get(
                                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportNativeFieldKeyDto(
                                        "partial_date",
                                        CdsExportPatientTableSourceReader.key(12),
                                        "format"));
        assertThat(format.destination()).isEqualTo("MedicationsAndTreatments/ResidualInfo");
        assertThat(result.metadata()).contains("\"precision\"", "\"format\":\"YYYY\"");
    }

    @Test
    void shouldWriteOnceDaily_asQdOnBothTheNativeAndReconciledPaths() throws Exception {
        // Review M8: the shared candidate normalizes it, so provenance cannot change the value.
        var fields = source();
        fields.put("freqcode", "OD");
        assertThat(
                        new CdsExportMedicationMapper()
                                .candidate(
                                        27,
                                        CdsExportMedicationMapperUnitTest.medication(fields),
                                        List.of(),
                                        Map.of())
                                .values())
                .containsEntry(Field.Frequency, "QD");
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
        // Without a captured reference the drug is exported without a DIN (A21); a captured
        // reference that does not match the drug row is still refused.
        assertThat(
                        mapper.map(27, medication(fields), List.of(), Map.of())
                                .value()
                                .fields())
                .doesNotContainKey(Field.DrugIdentificationNumber);
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
    void shouldExportTheIndicationAsProblemCode_andEveryShownReasonInItsOwnResidual()
            throws Exception {
        // P3: drugReason used to be ruled reference data, so no ProblemCode was ever written.
        var fields = source();
        var primary = CdsExportMedicationReasonMapperUnitTest.reason(7, "icd9", "250", "1");
        primary.put("comments", "Started  for HbA1c 8.1");
        var secondary = CdsExportMedicationReasonMapperUnitTest.reason(8, "icd10", "E11.9", "0");
        var archived = CdsExportMedicationReasonMapperUnitTest.reason(9, "icd9", "401", "1");
        archived.put("archivedFlag", "1");
        archived.put("archivedReason", "Entered on the wrong drug");
        var rows =
                List.of(
                        CdsExportMedicationReasonMapperUnitTest.row(primary),
                        CdsExportMedicationReasonMapperUnitTest.row(secondary),
                        CdsExportMedicationReasonMapperUnitTest.row(archived));
        var result = mapper.map(27, medication(fields), List.of(), Map.of(), null, rows);
        assertThat(result.value().fields()).containsEntry(Field.ProblemCode, "250");
        assertThat(result.reasons())
                .contains("\"code\":\"250\"", "\"code\":\"E11.9\"", "Started\\u0020\\u0020for")
                .doesNotContain("401", "wrong");
        var all = new ArrayList<CdsExportCapturedTextRowDto>(rows);
        all.add(medication(fields));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(all, result.fields(), 1000, () -> {});
        String xml =
                new String(
                        render(
                                CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                x -> mapper.write(result, x)),
                        StandardCharsets.UTF_8);
        assertThat(xml)
                .contains(
                        "<ProblemCode>250</ProblemCode>",
                        "CARLOS.Medication.Reasons.v1",
                        "CARLOS.Medication.Source.v1");
        // A prescription without reasons is written exactly as before.
        var plain = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(plain.reasons()).isNull();
        assertThat(plain.value().fields()).doesNotContainKey(Field.ProblemCode);
        assertThat(
                        new String(
                                render(
                                        CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                        x -> mapper.write(plain, x)),
                                StandardCharsets.UTF_8))
                .doesNotContain("ProblemCode", "CARLOS.Medication.Reasons.v1");
    }

    @Test
    void shouldWriteNativeMonthsAndYearsWithTheirUnit_neverAsAnAssertedDayCount() throws Exception {
        // Review M9: this test used to expect "2 months" exported as Duration 60, the
        // prescription calculator's 30-day month. A month or year is not an exact number of
        // days, so the authored unit is written instead (the medication Duration is CDS text);
        // the CARLOS importer reads "2 months" back as duration 2, unit M. Refills stay separate.
        var fields = source();
        fields.put("duration", "2");
        fields.put("durunit", "M");
        fields.put("repeat", "5");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Duration, "2 months")
                .containsEntry(Field.NumberOfRefills, "5");
        fields.put("duration", "1");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Duration, "1 month");
        fields.put("durunit", "Y");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Duration, "1 year");
        fields.put("duration", "3");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Duration, "3 years");
    }

    @Test
    void shouldWriteNativeWeeksAsTheirExactDays_onTheNativeAndReconciledPaths() throws Exception {
        var fields = source();
        fields.put("duration", "3");
        fields.put("durunit", "W");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Duration, "21");
        var candidate =
                new CdsExportMedicationMapper()
                        .candidate(
                                27,
                                CdsExportMedicationMapperUnitTest.medication(fields),
                                List.of(),
                                Map.of());
        assertThat(candidate.values()).containsEntry(Field.Duration, "21");
        assertThat(
                        candidate.assessments().values().stream()
                                .filter(field -> "Duration".equals(field.destination()))
                                .map(field -> field.outcome()))
                .doesNotContain(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldRoundTripAuthoredDurationUnits_throughTheCarlosImporter() throws Exception {
        // The exported text must be read back by CdsClinicalNormalizationService with its unit.
        for (var entry : Map.of("D", "30", "M", "3", "Y", "1").entrySet()) {
            String text = CdsExportMedicationMapper.duration(entry.getValue(), entry.getKey());
            var matcher =
                    java.util.regex.Pattern.compile(
                                    "([0-9]+)\\s*(D|DAYS?|W|WEEKS?|M|MONTHS?|Y|YEARS?)?",
                                    java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(text);
            assertThat(matcher.matches()).as(text).isTrue();
            assertThat(matcher.group(1)).isEqualTo(entry.getValue());
            assertThat(matcher.group(2) == null ? "D" : matcher.group(2).substring(0, 1).toUpperCase(java.util.Locale.ROOT))
                    .isEqualTo(entry.getKey());
        }
        assertThat(CdsExportMedicationMapper.duration("2", "W")).isEqualTo("14");
        assertThat(CdsExportMedicationMapper.duration("2", null)).isNull();
        assertThat(CdsExportMedicationMapper.duration("2", "unknown")).isNull();
        assertThat(CdsExportMedicationMapper.duration("1.5", "D")).isNull();
    }

    @Test
    void
            shouldKeepAnArchivedPrescriptionArchived_andNeverExportItsStaleActiveStatus()
                    throws Exception {
        // Every CARLOS archive path (re-prescribe, delete, discontinue, long-term toggle) means
        // the row is no longer the current prescription; exporting its copied "Active" would
        // duplicate or resurrect the medication at the receiver (review M2).
        var fields = source();
        fields.put("archived", "1");
        fields.put("archived_reason", "Stopped display");
        fields.put("rxStatus", "Active");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.archived()).isTrue();
        assertThat(result.value().fields())
                .containsEntry(Field.PrescriptionStatus, "Aborted")
                .containsEntry(Field.PastMedications, "Y");
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
    void shouldRestoreTheQuantityUnitAndAsNeededFlag_whenTheCarlosImporterReadsTheExport()
            throws Exception {
        // #6198: a custom liquid "100 mL" with the as-needed box ticked beside BID. CDS has no
        // element for either; the CARLOS importer restores both from CARLOS.Medication.Source.v1
        // because the standard Quantity and Frequency the exporter wrote agree with it.
        var fields = source();
        fields.put("unit", "");
        fields.put("unitName", "mL");
        fields.put("quantity", "100");
        fields.put("freqcode", "BID");
        fields.put("prn", "1");
        var exported = mapper.map(27, prescribed(fields), List.of(), Map.of());
        assertThat(exported.value().fields())
                .containsEntry(Field.Quantity, "100 mL")
                .containsEntry(Field.Frequency, "BID PRN");
        var received = receive(exported);
        assertThat(received.quantity()).isEqualTo("100");
        assertThat(received.quantityUnit()).isEqualTo("mL");
        assertThat(received.asNeeded()).isTrue();
        assertThat(received.frequency()).isEqualTo("BID PRN");
        // The imported prescription re-exports the same standard Quantity beside the original
        // carrier, and a second CARLOS import restores the same native values.
        var again = new CdsExportPreparedMedicationMapper().map(received);
        assertThat(again.fields())
                .containsEntry(Field.Quantity, "100 mL")
                .containsEntry(Field.Frequency, "BID PRN");
        var second =
                receive(
                        new CdsExportNativeMedicationMapper.Result(
                                again, exported.metadata(), Map.of(), false, null));
        assertThat(second.quantity()).isEqualTo("100");
        assertThat(second.quantityUnit()).isEqualTo("mL");
        assertThat(second.asNeeded()).isTrue();
        // An unticked box beside a PRN frequency is restored as recorded.
        fields.put("freqcode", "PRN");
        fields.put("prn", "0");
        fields.put("unitName", "");
        var unticked = receive(mapper.map(27, prescribed(fields), List.of(), Map.of()));
        assertThat(unticked.asNeeded()).isFalse();
        assertThat(unticked.quantityUnit()).isNull();
        assertThat(unticked.quantity()).isEqualTo("100");
    }

    /** Writes one prescription with its residual, validates it and reads it with the importer. */
    private io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto receive(
            CdsExportNativeMedicationMapper.Result result) throws Exception {
        byte[] xml = render(CdsCategory.MEDICATIONS_AND_TREATMENTS, x -> mapper.write(result, x));
        var folder = java.nio.file.Files.createTempDirectory(directory, "receive-");
        var file = java.nio.file.Files.write(folder.resolve("patient.xml"), xml);
        var records =
                new ArrayList<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto>();
        new io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser()
                .parse(
                        file,
                        folder.resolve("parsed"),
                        io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto
                                .defaults(),
                        () -> false,
                        records::add);
        return new io.github.carlos_emr.carlos.demographic.cdsimport.service
                        .CdsClinicalNormalizationService(
                        new io.github.carlos_emr.carlos.demographic.cdsimport.service
                                .CdsFieldParser())
                .medication(records.get(1));
    }

    @Test
    void shouldUseTheAuthoredCustomName_andRetainTheSeparateBrandAndAmbiguousUnits()
            throws Exception {
        var fields = source();
        fields.put("BN", "Separate brand");
        fields.put("unitName", "different unit");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        // unit and unitName are not ambiguous: on the Rx page unit is the dose unit (when the
        // strength does not name it) and a different unitName is the quantity's unit.
        assertThat(result.value().fields())
                .containsEntry(Field.DrugName, "Synthetic café 😀")
                .containsEntry(Field.DosageUnitOfMeasure, "tablet");
        assertThat(result.value().fields().get(Field.Quantity)).endsWith(" different unit");
        assertThat(result.metadata()).contains("Separate brand", "different unit");
    }

    @Test
    void shouldEscapeEverySpaceOfTheSourceContext_soATokenReaderKeepsRunsOfSpaces()
            throws Exception {
        var fields = source();
        fields.put("BN", "Separate  brand");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        String xml =
                new String(
                        render(
                                CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                x -> mapper.write(result, x)),
                        StandardCharsets.UTF_8);
        // Residual Content is an xs:token, whose normalization collapses runs of spaces.
        assertThat(xml)
                .contains("CARLOS.Medication.Source.v1", "Separate\\u0020\\u0020brand")
                .doesNotContain("Separate  brand");
        // The in-memory details keep the exact value; only the written carrier is escaped.
        assertThat(result.metadata()).contains("Separate  brand");
    }

    @Test
    void shouldExportAnArchivedPrescriptionAsNoLongerCurrent_byItsArchiveReason() throws Exception {
        for (var reason :
                List.of(
                        List.of("represcribed", "Obsolete"),
                        List.of("doseChange", "Obsolete"),
                        List.of("deleted", "Nullified"),
                        List.of("prescribingError", "Nullified"),
                        List.of("adverseReaction", "Aborted"),
                        List.of("noLongerNecessary", "Aborted"))) {
            var fields = source();
            fields.put("archived", "1");
            fields.put("archived_reason", reason.get(0));
            fields.put("rxStatus", "Active"); // re-prescribing copies the old status
            fields.put("past_med", "0");
            var result = mapper.map(27, medication(fields), List.of(), Map.of());
            assertThat(result.value().fields())
                    .as(reason.get(0))
                    .containsEntry(Field.PrescriptionStatus, reason.get(1))
                    .containsEntry(Field.PastMedications, "Y");
            assertThat(result.metadata()).contains(reason.get(0), "\"rxStatus\":\"Active\"");
        }
    }

    @Test
    void shouldMapTheRxPagesAsNeededChoices_insteadOfRefusingThePatient() throws Exception {
        var fields = source();
        fields.put("eTreatmentType", "PRNL");
        fields.put("prn", "1");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.TreatmentType, "PRN Long-term");
        fields.put("eTreatmentType", "PRNS");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.TreatmentType, "PRN Short-term");
        // The as-needed box with an ordinary frequency: "QD as needed".
        fields.put("eTreatmentType", "CHRON");
        var qualified = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(qualified.value().fields())
                .containsEntry(Field.Frequency, "QD PRN")
                .containsEntry(Field.TreatmentType, "CHRON");
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(qualified, xml));
    }

    @Test
    void shouldRefuseUnboundImportedAuthorityAndMalformedNativeRegimens() throws Exception {
        for (var changes :
                List.of(
                        Map.of("cds_currentness", "CURRENT"),
                        Map.of("cds_dose_text", "1"),
                        Map.of("cds_projection_incomplete", "1"),
                        Map.of("archived", "yes"),
                        // Text no XML document can hold is the one value that still holds.
                        Map.of("duration", "7\u0001"),
                        Map.of("special", "take\u0001one"))) {
            var fields = source();
            fields.putAll(changes);
            assertThatThrownBy(() -> mapper.map(27, medication(fields), List.of(), Map.of()))
                    .isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldExportIndependentlySavedStatusAndPastMedication_asStoredInsteadOfHolding()
            throws Exception {
        // A18: prescribe.jsp saves "Past medication" and the status select independently.
        for (var pair :
                List.of(
                        Map.of("rxStatus", "Active", "past_med", "1"),
                        Map.of("rxStatus", "Completed", "past_med", "0"),
                        Map.of("rxStatus", "Aborted", "past_med", "0"))) {
            var fields = source();
            fields.putAll(pair);
            var result = mapper.map(27, medication(fields), List.of(), Map.of());
            assertThat(result.value().fields())
                    .containsEntry(Field.PrescriptionStatus, pair.get("rxStatus"))
                    .containsEntry(
                            Field.PastMedications, "1".equals(pair.get("past_med")) ? "Y" : "N");
            assertThat(result.metadata()).contains("\"past_med\":\"" + pair.get("past_med"));
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            List.of(medication(fields)), result.fields(), 1000, () -> {});
            render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(result, xml));
        }
    }

    @Test
    void shouldExportTheChosenTreatmentTypeAndAsNeededFrequency_insteadOfHolding()
            throws Exception {
        // A19: the treatment select, the short-term box and the as-needed box are independent.
        var fields = source();
        fields.put("eTreatmentType", "ACU");
        fields.put("short_term", "0");
        fields.put("long_term", null);
        var acute = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(acute.value().fields()).containsEntry(Field.TreatmentType, "ACU");
        assertThat(acute.metadata()).contains("\"short_term\":\"0\"");
        // The as-needed box with no frequency is the frequency PRN.
        fields = source();
        fields.put("freqcode", null);
        fields.put("prn", "1");
        var asNeeded = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(asNeeded.value().fields()).containsEntry(Field.Frequency, "PRN");
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(asNeeded, xml));
        // A PRN frequency or treatment with the box unticked is written as chosen.
        fields = source();
        fields.put("freqcode", "PRN");
        fields.put("prn", "0");
        var unticked = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(unticked.value().fields()).containsEntry(Field.Frequency, "PRN");
        assertThat(unticked.metadata()).contains("\"prn\":\"0\"");
        fields = source();
        fields.put("eTreatmentType", "PRNL");
        fields.put("prn", "0");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.TreatmentType, "PRN Long-term");
    }

    /**
     * A prescription as the Rx page saves it when its directions name no duration: RxUtil's
     * directions parser stores duration "0", the frequency it found and the unit it deduced from
     * that frequency, each "" when there is none, and Drug's setters store "" as NULL.
     */
    private static Map<String, String> savedWithoutDuration(
            String directions, String frequency, String unit, boolean asNeeded) {
        var fields = source();
        fields.put("special", directions);
        fields.put("duration", "0");
        fields.put("durunit", unit);
        fields.put("freqcode", frequency);
        fields.put("prn", asNeeded ? "1" : "0");
        return fields;
    }

    @Test
    void shouldExportDirectionsThatNameNoDuration_withoutHoldingOrWritingAZeroDayCourse()
            throws Exception {
        // "use as directed" and "apply thin layer": no frequency, no duration, box unticked.
        for (String directions : List.of("use as directed", "apply thin layer")) {
            var fields = savedWithoutDuration(directions, null, null, false);
            var result = mapper.map(27, medication(fields), List.of(), Map.of());
            assertThat(result.value().fields())
                    .as(directions)
                    .doesNotContainKeys(Field.Duration, Field.Frequency)
                    .containsKey(Field.PrescriptionInstructions);
            // The exact columns travel in CARLOS.Medication.Source.v1.
            assertThat(result.metadata())
                    .contains("\"duration\":\"0\"", "\"durunit\":null", "\"freqcode\":null");
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            List.of(medication(fields)), result.fields(), 1000, () -> {});
            assertThat(
                            new String(
                                    render(
                                            CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                            xml -> mapper.write(result, xml)),
                                    StandardCharsets.UTF_8))
                    .doesNotContain("Duration>");
        }
        // "2 puffs prn": the as-needed box with no frequency is the frequency PRN, as the
        // operator guide promises, and a receiving CARLOS reads no duration back.
        var asNeeded = savedWithoutDuration("2 puffs prn", null, null, true);
        var puffs = mapper.map(27, medication(asNeeded), List.of(), Map.of());
        assertThat(puffs.value().fields())
                .containsEntry(Field.Frequency, "PRN")
                .doesNotContainKey(Field.Duration);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(medication(asNeeded)), puffs.fields(), 1000, () -> {});
        var received = receive(puffs);
        assertThat(received.frequency()).isEqualTo("PRN");
        assertThat(received.asNeeded()).isTrue();
        assertThat(received.duration()).isNull();
        // "1 tab BID": the parser deduces unit D from the frequency, and its "0" still means no
        // duration was stated, never a 0-day course.
        var daily = savedWithoutDuration("1 tab BID", "BID", "D", false);
        var bid = mapper.map(27, medication(daily), List.of(), Map.of());
        assertThat(bid.value().fields())
                .containsEntry(Field.Frequency, "BID")
                .doesNotContainKey(Field.Duration);
        assertThat(receive(bid).duration()).isNull();
        // An amount typed into the card's duration box when the directions named no unit is not
        // written as a bare number (a receiver reads days); it stays exact in the residual.
        var typed = savedWithoutDuration("use as directed", null, null, false);
        typed.put("duration", "7");
        var amount = mapper.map(27, medication(typed), List.of(), Map.of());
        assertThat(amount.value().fields()).doesNotContainKey(Field.Duration);
        assertThat(amount.metadata()).contains("\"duration\":\"7\"", "\"durunit\":null");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(medication(typed)), amount.fields(), 1000, () -> {});
    }

    @Test
    void shouldMapEveryDurationUnitCarlosReads_whateverCaseTheRxCardStored() throws Exception {
        // The Rx card's duration-unit box is free text (prescribe.jsp durationUnit_<id>,
        // RxWriteScript2Action.updateProperty) and drugs.durunit is CHAR(1): "days", "wk" or "mo"
        // typed there are stored as d, w and m where the database truncates, and Prescription
        // .calcEndDate and RxUtil.findNDays read the unit ignoring case.
        for (var unit :
                List.of(
                        List.of("D", "7", "D"),
                        List.of("d", "7", "D"),
                        List.of("W", "49", "D"),
                        List.of("w", "49", "D"),
                        List.of("M", "7 months", "M"),
                        List.of("m", "7 months", "M"),
                        List.of("Y", "7 years", "Y"),
                        List.of("y", "7 years", "Y"))) {
            var fields = source();
            fields.put("duration", "7");
            fields.put("durunit", unit.get(0));
            var result = mapper.map(27, medication(fields), List.of(), Map.of());
            assertThat(result.value().fields())
                    .as(unit.get(0))
                    .containsEntry(Field.Duration, unit.get(1));
            // The stored character itself stays exact in CARLOS.Medication.Source.v1.
            assertThat(result.metadata())
                    .contains("\"duration\":\"7\"", "\"durunit\":\"" + unit.get(0) + "\"");
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            List.of(medication(fields)), result.fields(), 1000, () -> {});
            var received = receive(result);
            assertThat(received.duration()).as(unit.get(0)).isEqualTo(unit.get(0).matches("[Ww]") ? "49" : "7");
            assertThat(received.durationUnit()).as(unit.get(0)).isEqualTo(unit.get(2));
        }
        var single = source();
        single.put("duration", "1");
        single.put("durunit", "m");
        assertThat(mapper.map(27, medication(single), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Duration, "1 month");
    }

    @Test
    void shouldCarryADurationWithNoCdsMeaning_exactlyInTheResidualInsteadOfHolding()
            throws Exception {
        // A unit that is no day, week, month or year (the truncated first letter of "hours" or
        // "x"), a duration typed as text, and an amount with no unit: none is a Duration, none
        // holds the patient, and each column is exact in CARLOS.Medication.Source.v1.
        for (var stored :
                List.of(
                        java.util.Arrays.asList("7", "h", "BID"),
                        java.util.Arrays.asList("7", "x", "BID"),
                        java.util.Arrays.asList("7", "7", "BID"),
                        java.util.Arrays.asList("7", "\u00e9", null),
                        java.util.Arrays.asList("10 days", "D", "TID"),
                        java.util.Arrays.asList("10 days", null, "TID"),
                        java.util.Arrays.asList("2-4", "w", "QID"),
                        java.util.Arrays.asList("one", null, null),
                        java.util.Arrays.asList("7", null, "BID"))) {
            var fields = source();
            fields.put("duration", stored.get(0));
            fields.put("durunit", stored.get(1));
            fields.put("freqcode", stored.get(2));
            var result = mapper.map(27, medication(fields), List.of(), Map.of());
            assertThat(result.value().fields()).as(stored.toString()).doesNotContainKey(Field.Duration);
            var carried =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(result.metadata().replace("\\u0020", " "))
                            .get("fields");
            assertThat(carried.get("duration").asText()).isEqualTo(stored.get(0));
            if (stored.get(1) == null) assertThat(carried.get("durunit").isNull()).isTrue();
            else assertThat(carried.get("durunit").asText()).isEqualTo(stored.get(1));
            if (stored.get(2) == null) assertThat(carried.get("freqcode").isNull()).isTrue();
            else assertThat(carried.get("freqcode").asText()).isEqualTo(stored.get(2));
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            List.of(medication(fields)), result.fields(), 1000, () -> {});
            assertThat(
                            new String(
                                    render(
                                            CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                            xml -> mapper.write(result, xml)),
                                    StandardCharsets.UTF_8))
                    .doesNotContain("Duration>");
            assertThat(receive(result).duration()).as(stored.toString()).isNull();
        }
    }

    @Test
    void shouldExportAPrescriptionNote_withALabelForItsMissingDrugName() throws Exception {
        // The Rx page's Note button (newCustomNote): custom_note 1, no brand, no custom name, no
        // drug code; the typed text is the whole prescription (rx-custom-note Playwright check).
        var fields = source();
        fields.put("BN", null);
        fields.put("customName", null);
        fields.put("regional_identifier", "");
        fields.put("GCN_SEQNO", "0");
        fields.put("custom_note", "1");
        fields.put("special", "Bring the old pill bottles");
        fields.put("special_instruction", null);
        fields.put("dosage", "");
        fields.put("unit", null);
        fields.put("duration", "0");
        fields.put("durunit", null);
        fields.put("freqcode", null);
        fields.put("quantity", "0");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().fields())
                .containsEntry(Field.DrugName, CdsExportNativeMedicationMapper.NOTE_NAME)
                .containsEntry(Field.PrescriptionInstructions, "Bring the old pill bottles")
                .doesNotContainKey(Field.DrugIdentificationNumber);
        assertThat(result.metadata()).contains("\"custom_note\":\"1\"", "\"BN\":null");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(medication(fields)), result.fields(), 1000, () -> {});
        var received = receive(result);
        assertThat(received.name()).isEqualTo(CdsExportNativeMedicationMapper.NOTE_NAME);
        // A migrated row that names no drug and is no note says so too; a blank name is no name.
        fields.put("custom_note", "0");
        fields.put("customName", " ");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.DrugName, CdsExportNativeMedicationMapper.UNNAMED);
    }

    @Test
    void shouldLeaveOutAnElementItsStoredValueCannotFill_andCarryTheValue() throws Exception {
        // Stored by the legacy Rx form, favourites, the REST service or OSCAR 19 data: none of
        // these is rewritten into its element and none holds the patient.
        var cases = new java.util.LinkedHashMap<Map<String, String>, Field>();
        cases.put(Map.of("quantity", "30 tabs"), Field.Quantity);
        cases.put(Map.of("quantity", "1e2"), Field.Quantity);
        cases.put(Map.of("repeat", "-1"), Field.NumberOfRefills);
        cases.put(Map.of("refill_quantity", "-5"), Field.RefillQuantity);
        cases.put(Map.of("long_term", "2"), Field.LongTermMedication);
        cases.put(Map.of("eTreatmentType", "LONG"), Field.TreatmentType);
        cases.put(Map.of("comment", "c".repeat(32001)), Field.Notes);
        cases.put(Map.of("dosage", "s".repeat(2001)), Field.DrugDescription);
        cases.put(Map.of("special", "s".repeat(32001)), Field.PrescriptionInstructions);
        for (var entry : cases.entrySet()) {
            var fields = source();
            fields.putAll(entry.getKey());
            var result = mapper.map(27, medication(fields), List.of(), Map.of());
            assertThat(result.value().fields())
                    .as(entry.getValue().name())
                    .doesNotContainKey(entry.getValue());
            String column = entry.getKey().keySet().iterator().next();
            assertThat(
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(result.metadata().replace("\\u0020", " "))
                                    .get("fields")
                                    .get(column)
                                    .asText())
                    .isEqualTo(entry.getKey().get(column));
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            List.of(medication(fields)), result.fields(), 1000, () -> {});
            render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(result, xml));
        }
        // A SIG that fits alone is written without the pharmacy instructions that overflow it.
        var both = source();
        both.put("special", "Take one");
        both.put("special_instruction", "p".repeat(32000));
        assertThat(mapper.map(27, medication(both), List.of(), Map.of()).value().fields())
                .containsEntry(Field.PrescriptionInstructions, "Take one");
        // A frequency that fills its element keeps it; the as-needed flag stays in the residual.
        var full = source();
        full.put("freqcode", "f".repeat(118));
        full.put("prn", "1");
        var qualified = mapper.map(27, medication(full), List.of(), Map.of());
        assertThat(qualified.value().fields()).containsEntry(Field.Frequency, "f".repeat(118));
        assertThat(qualified.metadata()).contains("\"prn\":\"1\"");
    }

    @Test
    void shouldWriteNoDate_forOscarsNoDateDefaultsAndCarryTheColumn() throws Exception {
        // drugs.rx_date and written_date default to 0001-01-01 (and old data holds the zero
        // date): OSCAR's "no date", never a recorded clinical date.
        for (var stored :
                List.of(
                        Map.of("rx_date", "0001-01-01"),
                        Map.of("written_date", "0001-01-01"),
                        Map.of("rx_date", "0000-00-00"))) {
            var fields = source();
            fields.putAll(stored);
            var result = mapper.map(27, medication(fields), List.of(), Map.of());
            String column = stored.keySet().iterator().next();
            var date = column.equals("rx_date") ? result.value().start() : result.value().written();
            assertThat(date.precision()).isEqualTo(CdsExportDateDto.Precision.ABSENT);
            assertThat(result.metadata())
                    .contains("\"" + column + "\":\"" + stored.get(column) + "\"");
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            List.of(medication(fields)), result.fields(), 1000, () -> {});
        }
    }

    @Test
    void shouldMoveADrugNameLongerThanItsElement_wholeToNotes() throws Exception {
        var fields = source();
        fields.put("customName", null);
        String name = "Long brand ".repeat(12).strip();
        fields.put("BN", name);
        fields.put("comment", "Provider note");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().fields())
                .containsEntry(Field.DrugName, CdsExportNativeMedicationMapper.LONG_NAME)
                .containsEntry(
                        Field.Notes,
                        CdsExportNativeMedicationMapper.LONG_NAME_LABEL + name + "\nProvider note");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(medication(fields)), result.fields(), 1000, () -> {});
    }

    @Test
    void shouldCarryAnOverlongDispenseIntervalAndProtocol_inTheResidualOnly() throws Exception {
        // A20: the Rx page sets no maximum length on these; the elements hold 10 and 20.
        var fields = source();
        fields.put("dispense_interval", "every 2 weeks");
        fields.put("protocol", "Protocol identifier text");
        fields.put("priorRxProtocol", "Prior protocol identifier");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().fields())
                .doesNotContainKeys(
                        Field.DispenseInterval,
                        Field.ProtocolIdentifier,
                        Field.PriorPrescriptionReferenceIdentifier);
        assertThat(result.metadata())
                .contains("every 2 weeks", "Protocol identifier text", "Prior protocol identifier");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(medication(fields)), result.fields(), 1000, () -> {});
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(result, xml));
    }

    @Test
    void shouldExportACatalogueDrugUnknownToDrugRef_withoutADinInsteadOfHolding()
            throws Exception {
        // A21: no public reference was captured (the DIN is blank or DrugRef no longer holds it).
        for (String din : Arrays.asList("00999999", null)) {
            var fields = source();
            fields.put("GCN_SEQNO", "17");
            fields.put("customName", null);
            fields.put("BN", "Discontinued brand tablet");
            fields.put("regional_identifier", din);
            var result = mapper.map(27, medication(fields), List.of(), Map.of(), null);
            assertThat(result.value().fields())
                    .containsEntry(Field.DrugName, "Discontinued brand tablet")
                    .doesNotContainKey(Field.DrugIdentificationNumber);
            assertThat(result.metadata())
                    .contains(
                            "\"regionalIdentifierAuthority\":\"UNQUALIFIED\"",
                            "\"catalogueReference\":\"NOT_IN_DRUG_REFERENCE\"",
                            "\"GCN_SEQNO\":\"17\"");
            if (din != null) assertThat(result.metadata()).contains(din);
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            List.of(medication(fields)), result.fields(), 1000, () -> {});
            render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(result, xml));
        }
    }

    @Test
    void shouldNameThePrescriber_whoseProviderRecordHasNoUsableOhipOrCpsoNumber()
            throws Exception {
        // A17: one provider record no longer holds every patient it prescribed for.
        var provider =
                new LinkedHashMap<>(CdsExportPublicProviderMapperUnitTest.provider(null, "12345").fields());
        provider.put("ohip_no", "1234 56789");
        var row =
                new CdsExportCapturedTextRowDto(
                        "provider", CdsExportPublicProviderSourceReader.key("0é27"), provider);
        var fields = source();
        fields.put("provider_no", "0é27");
        var result = mapper.map(27, medication(fields), List.of(), Map.of("0é27", row));
        assertThat(result.value().prescriber().firstName()).isEqualTo("Zoë😀");
        assertThat(result.value().prescriber().ohipNumber()).isNull();
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        List.of(medication(fields), row), result.fields(), 1000, () -> {});
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(result, xml));
    }

    @Test
    void shouldExportAPrescriptionRecordedForAnOutsidePrescriber_withoutNamingTheRecorder()
            throws Exception {
        // The Rx page's outside-provider box: the CARLOS provider records another physician's
        // prescription (a blank OHIP box is stored as "0"). The recorder is not PrescribedBy.
        var row = CdsExportPublicProviderMapperUnitTest.provider("CPSO", "000123");
        var fields = source();
        fields.put("provider_no", "0é27");
        fields.put("outside_provider_name", "Dr. Outside Writer");
        fields.put("outside_provider_ohip", "0");
        var result = mapper.map(27, medication(fields), List.of(), Map.of("0é27", row));
        assertThat(result.value().prescriber()).isNull();
        assertThat(result.metadata()).contains("Dr. Outside Writer", "\"outside_provider_ohip\":\"0\"");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        List.of(medication(fields), row), result.fields(), 1000, () -> {});
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, xml -> mapper.write(result, xml));
        // The recorder's own name and number in the boxes still name the recorder.
        fields.put("outside_provider_name", "Zoë😀 D’Arcy");
        fields.put("outside_provider_ohip", "001234");
        assertThat(
                        mapper.map(27, medication(fields), List.of(), Map.of("0é27", row))
                                .value()
                                .prescriber())
                .isNotNull();
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
