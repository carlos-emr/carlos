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
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit") @Tag("cds-export")
class CdsExportMedicationFieldReconcilerUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    static final String RUN = "11111111-1111-4111-8111-111111111111", UNIT = "a".repeat(64), ADDRESS = RUN + ":" + UNIT, SOURCE = "b".repeat(64);
    @TempDir Path directory;
    @Test void shouldRetainVerifiedDinStrengthOriginalPrescriberIdentifiersAndClock_whileExportingEditedProviderNotes() throws Exception {
        var fields = baseline(); fields.put("comment", "Current provider note café 😀");
        var result = reconcile(fixture(fields, numbers(), true));
        assertThat(result.mapping().nativeRowRepresentable()).isTrue();
        assertThat(result.mapping().value().fields().get(Field.Notes)).isEqualTo("Current provider note café 😀");
        assertThat(result.mapping().value().fields().get(Field.DrugIdentificationNumber)).isEqualTo("00123456");
        assertThat(result.mapping().value().strengthAmount()).isEqualTo("5"); assertThat(result.mapping().value().strengthUnit()).isEqualTo("mg");
        assertThat(result.mapping().value().prescriber().firstName()).isEqualTo("Original");
        assertThat(result.currentPaths()).containsExactly("Notes");
        var received = roundTrip(result.mapping().value());
        assertThat(received.din()).isEqualTo("00123456"); assertThat(received.strengthAmount()).isEqualTo("5");
        assertThat(received.dose()).isEqualTo("0.5-1"); assertThat(received.providerNotes()).isEqualTo("Current provider note café 😀");
        assertThat(received.prescriberFirstName()).isEqualTo("Original"); assertThat(received.prescriberOhip()).isEqualTo("001234");
        assertThat(received.prescriptionIdentifier()).isEqualTo("rx-source"); assertThat(received.description()).isEqualTo("Source description");
        assertThat(received.written().original()).isEqualTo("2024-02-29T12:34:56.1200-05:00");
    }
    @Test void shouldPreserveNativeClearsAndAChangedQuantity_withoutRestoringTheOldValue() throws Exception {
        var fields = baseline(); fields.put("comment", null); fields.put("quantity", "21.5"); var result = reconcile(fixture(fields, numbers(), true));
        assertThat(result.mapping().nativeRowRepresentable()).isTrue();
        assertThat(result.mapping().value().fields()).doesNotContainKey(Field.Notes).containsEntry(Field.Quantity, "21.5");
        assertThat(result.currentPaths()).containsExactlyInAnyOrder("Notes", "Quantity");
    }
    @Test void shouldRequireReviewForAFloatEditHiddenInTheLegacyDisplay_andItsStaleGeneratedInstructions() throws Exception {
        var changed = new HashMap<>(numbers()); changed.put("takemin", numeric("3f000001"));
        var f = fixture(baseline(), changed, true); var result = reconcile(f);
        assertThat(f.witness().digest()).isEqualTo(f.checked().units().get(ADDRESS).getFirst().expected().digest());
        assertThat(result.mapping().nativeRowRepresentable()).isFalse(); assertThat(result.currentPaths()).contains("Dosage");
        assertThat(result.mapping().fields().get(key("special")).rule()).isEqualTo("GENERATED_SIG_DEPENDENCIES_CHANGED_REQUIRES_REVIEW");
    }
    @Test void shouldNotInventExactHistoricalFloatEvidence_fromAnUnchangedLegacyDisplay() throws Exception {
        var f = fixture(baseline(), numbers(), false);
        assertThatThrownBy(() -> reconcile(f)).hasMessage("CDS_EXPORT_MEDICATION_FIELD_BASELINE_REQUIRED");
    }
    @Test void shouldRequireReviewForChangedStructuredStrengthOrAnOriginalClockDate() throws Exception {
        var fields = baseline(); fields.put("dosage", "10 mg");
        assertThat(reconcile(fixture(fields, numbers(), true)).mapping().nativeRowRepresentable()).isFalse();
        fields = baseline(); fields.put("written_date", "2024-03-01");
        var result = reconcile(fixture(fields, numbers(), true)); assertThat(result.mapping().nativeRowRepresentable()).isFalse();
        assertThat(result.mapping().fields().get(key("written_date")).rule()).isEqualTo("UNPROJECTED_CLOCK_OR_OFFSET_REQUIRES_RECONCILIATION");
    }
    @Test void shouldRequireReviewForDeletedOrAliasedOccurrences_withoutResurrectingOrDuplicatingThem() throws Exception {
        var f = fixture(baseline(), numbers(), true); var ref = f.checked().units().get(ADDRESS).getFirst();
        var deleted = new CdsExportRetainedReceiptVerificationDto(SOURCE, Map.of(ADDRESS, List.of(new CheckedReference(ref.expected(), null, Reason.ROW_NOT_IN_CAPTURE))), Set.of(), Set.of(ADDRESS));
        assertThatThrownBy(() -> new CdsExportMedicationFieldReconciler().reconcile(27, f.index(), ADDRESS, deleted, null, null, List.of(), Map.of()))
                .hasMessage("CDS_EXPORT_MEDICATION_DELETION_REQUIRES_REVIEW");
        String alias = RUN + ":" + "f".repeat(64); var references = new LinkedHashMap<>(f.index().references()); references.put(alias, references.get(ADDRESS));
        var index = new CdsExportRetainedIndexDto(SOURCE, 3, 4096, f.index().tables(), Set.of(ADDRESS, alias), references, Map.of());
        var checked = new CdsExportRetainedReceiptVerificationDto(SOURCE, Map.of(ADDRESS, List.of(ref), alias, List.of(ref)), Set.of(ADDRESS, alias), Set.of());
        assertThatThrownBy(() -> new CdsExportMedicationFieldReconciler().reconcile(27, index, ADDRESS, checked, f.row(), f.witness(), List.of(), Map.of()))
                .hasMessage("CDS_EXPORT_MEDICATION_ALIAS_REQUIRES_REVIEW");
    }
    @Test void shouldRejectSubstitutedSqlOrExactNumericRowValues_beforeSelectingAnyClinicalField() throws Exception {
        var f = fixture(baseline(), numbers(), true); var values = new LinkedHashMap<>(f.row().fields()); values.put("comment", "Substituted");
        assertThatThrownBy(() -> new CdsExportMedicationFieldReconciler().reconcile(27, f.index(), ADDRESS, f.checked(),
                new CdsExportCapturedTextRowDto("drugs", f.row().recordKey(), values, numbers()), f.witness(), List.of(), Map.of())).hasMessage("CDS_EXPORT_MEDICATION_RECONCILIATION_SCOPE_INVALID");
        var numeric = new LinkedHashMap<>(numbers()); numeric.put("takemin", numeric("3f000001"));
        assertThatThrownBy(() -> new CdsExportMedicationFieldReconciler().reconcile(27, f.index(), ADDRESS, f.checked(),
                new CdsExportCapturedTextRowDto("drugs", f.row().recordKey(), f.row().fields(), numeric), f.witness(), List.of(), Map.of())).hasMessage("CDS_EXPORT_MEDICATION_RECONCILIATION_SCOPE_INVALID");
    }
    @Test void shouldQualifyNativeDoseBoundsOnlyWithExactValues_andRefuseTheUndocumentedZeroDefault() throws Exception {
        var fields = CdsExportMedicationMapperUnitTest.baseline(); fields.put("takemin", "0.1"); fields.put("takemax", "0.1"); fields.put("cds_dose_text", null);
        var low = numeric("3dcccccd"); var high = numeric("3dccccce");
        var row = new CdsExportCapturedTextRowDto("drugs", CdsExportPatientTableSourceReader.key(51), fields, Map.of("takemin", low, "takemax", high));
        var result = new CdsExportMedicationMapper().map(27, row, List.of());
        assertThat(result.nativeRowRepresentable()).isTrue(); assertThat(result.value().fields().get(Field.Dosage)).isEqualTo("0.1-0.10000001");
        var zeros = Map.of("takemin", numeric("00000000"), "takemax", numeric("00000000"));
        fields.put("takemin", "0"); fields.put("takemax", "0");
        assertThat(new CdsExportMedicationMapper().map(27, new CdsExportCapturedTextRowDto("drugs", row.recordKey(), fields, zeros), List.of()).nativeRowRepresentable()).isFalse();
        fields.put("takemax", "1");
        var signedZero = Map.of("takemin", numeric("80000000"), "takemax", numeric("3f800000"));
        assertThat(new CdsExportMedicationMapper().map(27, new CdsExportCapturedTextRowDto("drugs", row.recordKey(), fields, signedZero), List.of()).nativeRowRepresentable()).isFalse();
    }
    record Fixture(CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness) { }
    static CdsExportMedicationFieldReconciler.Result reconcile(Fixture f) throws Exception {
        return new CdsExportMedicationFieldReconciler().reconcile(27, f.index(), ADDRESS, f.checked(), f.row(), f.witness(), List.of(), Map.of());
    }
    static Fixture fixture(Map<String, String> fields, Map<String, CdsExactNumeric> numeric, boolean includeBaseline) throws Exception {
        var old = state(baseline(), numbers()); var now = state(fields, numeric); String id = CdsImportKeys.hash(RUN, UNIT);
        String sourceDigest = "c".repeat(64), options = "d".repeat(64), ruleset = "e".repeat(64);
        var ref = new CdsExportRetainedIndexDto.Reference("drugs", "51", old.digest());
        String references = JSON.writeValueAsString(Map.of("protocol", 2, "refs", List.of(ref))), target = sha(references);
        var prepared = new CdsMedicationNativeDto(source(), new CdsProviderMatchDto(null, CdsProviderMatchDto.Outcome.UNMATCHED_IDENTIFIER)); String payload = JSON.writeValueAsString(prepared);
        var unit = new LinkedHashMap<String, String>(Map.of("id", id, "run_id", RUN, "unit_key", UNIT, "category", "MedicationsAndTreatments", "state", "COMMITTED",
                "source_digest", sourceDigest, "target_references", references, "target_digest", target));
        unit.put("native_baseline", includeBaseline ? CdsNativeBaselineCodec.encode(references, target, List.of(old)) : null);
        var prep = Map.of("id", id, "run_id", RUN, "unit_key", UNIT, "source_digest", sourceDigest, "options_digest", options, "ruleset_digest", ruleset,
                "payload_type", CdsMedicationNativeDto.class.getName(), "payload", payload, "digest", CdsPreparationSeal.digest(CdsMedicationNativeDto.class.getName(), sourceDigest, options, ruleset, payload));
        var tables = Map.of("cds_import_unit", Map.of(id, new CdsExportCapturedTextRowDto("cds_import_unit", id, unit)),
                "cds_import_preparation", Map.of(id, new CdsExportCapturedTextRowDto("cds_import_preparation", id, prep)),
                "cds_import_run", Map.of(RUN, new CdsExportCapturedTextRowDto("cds_import_run", RUN, Map.of("id", RUN, "options_digest", options, "ruleset_digest", ruleset))));
        var index = new CdsExportRetainedIndexDto(SOURCE, 3, 4096, tables, Set.of(ADDRESS), Map.of(ADDRESS, List.of(ref)), Map.of());
        var difference = CdsNativeRowComparison.compare(old, now); boolean same = old.digest().equals(now.digest()) && difference.unchangedProjection();
        var checked = new CdsExportRetainedReceiptVerificationDto(SOURCE, Map.of(ADDRESS, List.of(new CheckedReference(ref, now.digest(), same ? Reason.UNCHANGED_ROW : Reason.CHANGED_ROW, difference))), same ? Set.of(ADDRESS) : Set.of(), same ? Set.of() : Set.of(ADDRESS));
        var row = new CdsExportCapturedTextRowDto("drugs", CdsExportPatientTableSourceReader.key(51), fields, numeric);
        return new Fixture(index, checked, row, new CdsExportNativeRowReceiptDto("drugs", "51", row.recordKey(), now.digest(), now));
    }
    static CdsMedicationDto source() {
        return new CdsMedicationDto("Original café drug", "00123456", "Source description", "5", "mg", "0.5-1", "tablet", "tablet", "PO", "BID", "030", 30, "12.5", 12, 2,
                true, false, false, true, false, "CHRON", "Active", CdsMedicationDto.Currentness.CURRENT, "Original Physician", "001234", "Original", "Physician",
                "Original provider notes", "Original instructions", "7", "proto", "prior", "rx-source", CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200-05:00", "Date"),
                CdsTemporal.parse(null, null, "2020-02-29", null, "Date"), false, false, List.of());
    }
    static Map<String, String> baseline() {
        var fields = new LinkedHashMap<String, String>(); CdsExportPatientTableReadSupport.Plan.DRUGS.columns().forEach(column -> fields.put(column, null));
        fields.putAll(Map.of("drugid", "51", "demographic_no", "27", "provider_no", "", "GCN_SEQNO", "0", "BN", "Original café drug", "regional_identifier", "00123456", "written_date", "2024-02-29", "rx_date", "2020-02-29", "start_date_unknown", "0"));
        fields.putAll(Map.of("duration", "030", "durunit", "D", "quantity", "12.5", "repeat", "2", "refill_duration", "30", "refill_quantity", "12", "freqcode", "BID", "route", "PO", "drug_form", "tablet"));
        fields.putAll(Map.of("unit", "tablet", "unitName", "tablet", "cds_dose_text", "0.5-1", "takemin", "0.5", "takemax", "1", "dosage", "5 mg", "long_term", "1", "short_term", "0", "past_med", "0", "patient_compliance", "0"));
        fields.putAll(Map.of("nosubs", "1", "non_authoritative", "0", "eTreatmentType", "CHRON", "rxStatus", "Active", "cds_currentness", "CURRENT", "cds_projection_incomplete", "0", "prn", "0", "archived", "0", "dispense_interval", "7", "protocol", "proto"));
        fields.putAll(Map.of("priorRxProtocol", "prior", "comment", "Original provider notes", "special_instruction", "Original instructions", "custom_instructions", "1", "custom_note", "0", "outside_provider_name", "Original Physician", "outside_provider_ohip", "001234", "special", "Original café drug 5 mg\n 0.5-1 tablet PO BID", "position", "0", "dispenseInternal", "0"));
        fields.put("hide_cpp", "0"); fields.put("create_date", "2024-02-29 01:02:03"); fields.put("lastUpdateDate", "2024-02-29 01:02:03"); return fields;
    }
    static Map<String, CdsExactNumeric> numbers() { return Map.of("takemin", numeric("3f000000"), "takemax", numeric("3f800000")); }
    static CdsExactNumeric numeric(String bits) { return new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY32_V1, bits); }
    private static CdsNativeRowState state(Map<String, String> values, Map<String, CdsExactNumeric> numeric) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>(); var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (String column : CdsExportPatientTableReadSupport.Plan.DRUGS.columns()) {
            columns.add(new CdsNativeRowDigest.Column(column, Set.of("takemin", "takemax").contains(column) ? "FLOAT" : "VARCHAR", 128, 0)); String value = values.get(column);
            cells.add(value == null ? new CdsNativeRowState.Cell(true, 0, null) : new CdsNativeRowState.Cell(false, value.getBytes(StandardCharsets.UTF_8).length, sha(value), numeric.containsKey(column) ? numeric.get(column).witness() : null));
        }
        return new CdsNativeRowState("drugs", "51", columns, cells);
    }
    static CdsExportNativeFieldKeyDto key(String field) { return new CdsExportNativeFieldKeyDto("drugs", CdsExportPatientTableSourceReader.key(51), field); }
    private static String sha(String value) { return HexFormat.of().formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8))); }
    private CdsMedicationDto roundTrip(CdsExportMedicationDto value) throws Exception {
        var patient = new LinkedHashMap<String, String>(); CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(Map.of("first_name", "Synthetic", "last_name", "Fixture", "year_of_birth", "1980", "month_of_birth", "01", "date_of_birth", "01", "patient_status", "AC", "sex", "U"));
        var demographics = new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.MEDICATIONS_AND_TREATMENTS);
        var sections = List.of(new CdsExportXmlComposer.Section(new CdsExportIdentityDto("a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)), xml -> new CdsExportDemographicXmlWriter().write(demographics, xml)),
                new CdsExportXmlComposer.Section(new CdsExportIdentityDto("a".repeat(64), CdsCategory.MEDICATIONS_AND_TREATMENTS, "c".repeat(64)), xml -> new CdsExportMedicationXmlWriter().write(value, xml)));
        var output = new ByteArrayOutputStream(); new CdsExportXmlComposer().compose(output, "d".repeat(64), "a".repeat(64), "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, selected, sections.iterator(), ignored -> {}, 1L << 20, () -> {});
        new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "27", selected, "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored -> {}, () -> {});
        var file = Files.write(directory.resolve("medication.xml"), output.toByteArray()); var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser().parse(file, directory.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
        return new CdsClinicalNormalizationService(new CdsFieldParser()).medication(records.get(1));
    }
}
