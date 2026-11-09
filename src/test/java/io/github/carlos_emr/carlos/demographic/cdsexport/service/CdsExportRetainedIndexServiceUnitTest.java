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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedIndexServiceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            NATIVE = "c".repeat(64),
            FIELDS = "b".repeat(64);
    private static final Set<String> TABLES =
            Set.of(
                    "cds_import_run",
                    "cds_import_patient",
                    "cds_import_unit",
                    "cds_import_field",
                    "cds_import_dependency",
                    "cds_import_preparation");
    private final CdsExportCapturedTextRowsReader rows =
            mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportStagedFileDto manifest =
            new CdsExportStagedFileDto("e".repeat(64), "SOURCE_MANIFEST", "f".repeat(64), 1);
    private final Map<String, CdsExportCapturedTextRowDto> data = new LinkedHashMap<>();
    private CdsExportLeaseDto lease;
    private CdsExportRetainedIndexService service;
    private String fieldId;

    @BeforeEach
    void setup() throws Exception {
        var command =
                new CdsExportCommand(
                        "index",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                        "receiver",
                        "audit");
        lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        "a".repeat(64),
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                "b".repeat(64),
                                "sender",
                                "c".repeat(64),
                                "d".repeat(64),
                                new CdsExportStorageBindingDto(
                                        "primary", "primary", "a".repeat(64))));
        when(patient.patient()).thenReturn(27);
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(TABLES),
                        eq(TABLES),
                        eq(manifest),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows out = call.getArgument(9);
                            for (var row : data.values()) out.accept(row);
                            return mock(CdsExportNativeCaptureDto.class);
                        });
        service = new CdsExportRetainedIndexService(rows, captures);
        baseline();
    }

    private Set<String> reportScope() throws Exception {
        var old = lease.document();
        var command = old.command();
        var categories = new HashSet<>(command.categories());
        categories.add(CdsCategory.REPORTS);
        var selected =
                new CdsExportCommand(
                        command.submissionKey(),
                        command.providerNo(),
                        command.facilityId(),
                        command.profile(),
                        command.selection(),
                        command.physicianNos(),
                        command.patientNos(),
                        categories,
                        command.recipientId(),
                        command.auditPlanId());
        lease =
                new CdsExportLeaseDto(
                        lease.runId(),
                        lease.epoch(),
                        lease.token(),
                        new CdsExportJobDocumentDto(
                                2,
                                old.actor(),
                                selected,
                                old.compatibilityDigest(),
                                old.senderNamespace(),
                                old.recipientFingerprint(),
                                old.auditPlanDigest(),
                                old.storage()));
        var scope = new HashSet<>(TABLES);
        scope.addAll(Set.of("cds_import_file", "cds_import_file_reference"));
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(scope),
                        eq(scope),
                        eq(manifest),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows out = call.getArgument(9);
                            for (var row : data.values()) out.accept(row);
                            return mock(CdsExportNativeCaptureDto.class);
                        });
        return Set.copyOf(scope);
    }

    @Test
    void shouldRetainOriginalPublicationMetadata_whenReportsNeedItsReceiptClosure()
            throws Exception {
        var scope = reportScope();
        String file = "22222222-2222-4222-8222-222222222222",
                link = CdsImportKeys.hash(RUN, NATIVE, file);
        put(
                "publication",
                row(
                        Plan.IMPORT_FILES,
                        file,
                        Map.of(
                                "run_id",
                                RUN,
                                "location",
                                "/private/native/report.pdf",
                                "location_key",
                                CdsImportKeys.hash("/private/native/report.pdf"),
                                "digest",
                                "a".repeat(64),
                                "byte_length",
                                "17",
                                "generation",
                                "2",
                                "state",
                                "PUBLISHED",
                                "verified_epoch",
                                "9")));
        put(
                "publication-link",
                row(
                        Plan.IMPORT_FILE_REFERENCES,
                        link,
                        Map.of("run_id", RUN, "unit_key", NATIVE, "file_id", file)));
        var result =
                service.index(
                        lease, patient, scope, manifest, 10000, 8L << 20, 1000, 4L << 20, control);
        assertThat(result.tables().get("cds_import_file")).containsKey(file);
        assertThat(result.tables().get("cds_import_file_reference")).containsKey(link);
        assertThat(result.metadataRows()).isEqualTo(data.size());
        assertThatThrownBy(() -> result.tables().get("cds_import_file").clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRefuseMissingPublicationInputs_beforeReportEvidenceCanBeOmitted() throws Exception {
        reportScope();
        assertThatThrownBy(this::index).hasMessage("CDS_EXPORT_RETAINED_INDEX_SCOPE_INVALID");
    }

    private CdsExportRetainedIndexDto index() throws Exception {
        return service.index(
                lease, patient, TABLES, manifest, 10000, 8L << 20, 1000, 4L << 20, control);
    }

    private void baseline() throws Exception {
        data.clear();
        put(
                "run",
                row(
                        Plan.IMPORT_RUNS,
                        RUN,
                        Map.of(
                                "options_digest",
                                "1".repeat(64),
                                "ruleset_digest",
                                "2".repeat(64),
                                "profile",
                                "omd-5.2")));
        put(
                "patient",
                row(
                        Plan.IMPORT_PATIENTS,
                        "3".repeat(64),
                        Map.of(
                                "run_id",
                                RUN,
                                "patient_key",
                                "a".repeat(64),
                                "target_demographic_no",
                                "27",
                                "held",
                                "0",
                                "state",
                                "COMPLETE")));
        var field =
                CdsExportRetainedFieldMapperUnitTest.row(
                        1, "original source", "accepted correction", null);
        fieldId = field.recordKey();
        put("field", field);
        String nativeRefs = refs("allergies", "51");
        put("native", unit("7".repeat(64), NATIVE, "AllergiesAndAdverseReactions", nativeRefs));
        put(
                "fields",
                unit(
                        "8".repeat(64),
                        FIELDS,
                        "AllergiesAndAdverseReactions_FIELDS",
                        refs("cds_import_field", fieldId)));
        put(
                "edge",
                row(
                        Plan.IMPORT_DEPENDENCIES,
                        "d".repeat(64),
                        Map.of("run_id", RUN, "unit_key", FIELDS, "depends_on_unit_key", NATIVE)));
        String payload = "{\"description\":\"original source\"}";
        put(
                "prepared",
                row(
                        Plan.IMPORT_PREPARATIONS,
                        "9".repeat(64),
                        Map.of(
                                "run_id",
                                RUN,
                                "unit_key",
                                NATIVE,
                                "source_digest",
                                "4".repeat(64),
                                "options_digest",
                                "1".repeat(64),
                                "ruleset_digest",
                                "2".repeat(64),
                                "payload_type",
                                "synthetic.NativeAllergy",
                                "payload",
                                payload,
                                "digest",
                                CdsPreparationSeal.digest(
                                        "synthetic.NativeAllergy",
                                        "4".repeat(64),
                                        "1".repeat(64),
                                        "2".repeat(64),
                                        payload))));
    }

    @Test
    void shouldJoinFieldUnitsToTheirSeparateNativeParents_andKeepAllEvidenceImmutable()
            throws Exception {
        var result = index();
        assertThat(result.appliedUnits()).hasSize(2);
        assertThat(result.nativeParents())
                .containsEntry(fieldId, RUN + ":" + NATIVE)
                .doesNotContainValue(RUN + ":" + FIELDS);
        assertThat(result.tables().get("cds_import_preparation")).hasSize(1);
        assertThatThrownBy(() -> result.tables().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.toString()).doesNotContain("original source", "27", "51");
    }

    @Test
    void
            shouldResolveOccurrenceFieldsUsingTheActualIndexContract_includingTheirOwnSeparateUnitReceipts()
                    throws Exception {
        var result = index();
        var field =
                new CdsExportRetainedFieldMapper()
                        .map(
                                27,
                                Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                                result.tables().get("cds_import_field").get(fieldId));
        var checked = checked(result);
        assertThat(
                        new CdsExportRetainedOccurrenceFields()
                                .fieldsFor(
                                        27,
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                        RUN + ":" + NATIVE,
                                        result,
                                        checked,
                                        List.of(field),
                                        control::checkpoint))
                .containsExactly(field);
        var wrong =
                new CdsExportRetainedIndexDto(
                        result.sourceManifestSha256(),
                        result.metadataRows(),
                        result.metadataBytes(),
                        result.tables(),
                        result.appliedUnits(),
                        result.references(),
                        Map.of(RUN + ":" + FIELDS, RUN + ":" + NATIVE));
        assertThatThrownBy(
                        () ->
                                new CdsExportRetainedOccurrenceFields()
                                        .fieldsFor(
                                                27,
                                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                                RUN + ":" + NATIVE,
                                                wrong,
                                                checked,
                                                List.of(field),
                                                control::checkpoint))
                .hasMessage("CDS_EXPORT_ALLERGY_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    private static CdsExportRetainedReceiptVerificationDto checked(
            CdsExportRetainedIndexDto result) {
        var units =
                new LinkedHashMap<
                        String, List<CdsExportRetainedReceiptVerificationDto.CheckedReference>>();
        result.references()
                .forEach(
                        (unit, refs) ->
                                units.put(
                                        unit,
                                        refs.stream()
                                                .map(
                                                        ref ->
                                                                new CdsExportRetainedReceiptVerificationDto
                                                                        .CheckedReference(
                                                                        ref,
                                                                        ref.digest(),
                                                                        CdsExportRetainedReceiptVerificationDto
                                                                                .Reason
                                                                                .UNCHANGED_ROW))
                                                .toList()));
        return new CdsExportRetainedReceiptVerificationDto(
                result.sourceManifestSha256(), units, result.appliedUnits(), Set.of());
    }

    @Test
    void shouldExcludeUnwrittenProposals_fromAppliedClinicalEvidence() throws Exception {
        change("patient", "state", "REFUSED");
        change("native", "state", "EXPECTED");
        change("fields", "state", "EXPECTED");
        data.remove("field");
        change("prepared", "payload", "unapplied proposal, not a clinical fact");
        var result = index();
        assertThat(result.appliedUnits()).isEmpty();
        assertThat(result.references()).isEmpty();
        assertThat(result.nativeParents()).isEmpty();
        assertThat(result.tables().get("cds_import_preparation")).hasSize(1);
    }

    @Test
    void shouldKeepReplayAliasesWithoutDuplicatingRetainedFieldIdentity() throws Exception {
        String replay = "22222222-2222-4222-8222-222222222222";
        put("replay-run", copy(data.get("run"), replay, Map.of()));
        put(
                "replay-patient",
                copy(
                        data.get("patient"),
                        "e".repeat(64),
                        Map.of("run_id", replay, "state", "ALREADY_COMPLETE")));
        put(
                "replay-native",
                copy(
                        data.get("native"),
                        "f".repeat(64),
                        Map.of("run_id", replay, "state", "ALREADY_COMPLETE")));
        put(
                "replay-fields",
                copy(
                        data.get("fields"),
                        "0".repeat(64),
                        Map.of("run_id", replay, "state", "ALREADY_COMPLETE")));
        put("replay-edge", copy(data.get("edge"), "1".repeat(64), Map.of("run_id", replay)));
        var result = index();
        assertThat(result.appliedUnits()).hasSize(4);
        assertThat(result.nativeParents()).hasSize(1);
        assertThat(result.tables().get("cds_import_field")).hasSize(1);
    }

    @Test
    void shouldRejectMissingEdgesCyclesHoldsAndUncommittedPreservation() throws Exception {
        for (String defect : List.of("missing-edge", "cycle", "hold", "uncommitted")) {
            baseline();
            switch (defect) {
                case "missing-edge" -> data.remove("edge");
                case "cycle" ->
                        put(
                                "cycle",
                                row(
                                        Plan.IMPORT_DEPENDENCIES,
                                        "0".repeat(64),
                                        Map.of(
                                                "run_id",
                                                RUN,
                                                "unit_key",
                                                NATIVE,
                                                "depends_on_unit_key",
                                                FIELDS)));
                case "hold" -> change("patient", "held", "1");
                case "uncommitted" -> {
                    change("patient", "state", "REFUSED");
                    change("native", "state", "EXPECTED");
                    change("fields", "state", "EXPECTED");
                }
            }
            assertThatThrownBy(this::index).isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldRejectChangedPreparedSealsWrongOptionsAndMalformedReceiptProtocols()
            throws Exception {
        for (String defect :
                List.of("payload", "options", "protocol", "wrong-patient", "missing-field")) {
            baseline();
            if (defect.equals("payload"))
                change("prepared", "payload", "changed clinical baseline");
            if (defect.equals("options")) change("prepared", "options_digest", "5".repeat(64));
            if (defect.equals("protocol"))
                receipt(
                        "native",
                        refs("allergies", "51")
                                .replace("\"protocol\":2", "\"protocol\":4294967298"));
            if (defect.equals("wrong-patient")) receipt("native", refs("demographic", "28"));
            if (defect.equals("missing-field")) data.remove("field");
            assertThatThrownBy(this::index).hasMessage("CDS_EXPORT_RETAINED_INDEX_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldEnforceExplicitResourceBounds_andReturnNoIndexAfterFinalAuthorityIsRevoked()
            throws Exception {
        assertThatThrownBy(
                        () ->
                                service.index(
                                        lease, patient, TABLES, manifest, 10000, 8L << 20, 1,
                                        4L << 20, control))
                .hasMessage("CDS_EXPORT_RETAINED_INDEX_RESOURCE_LIMIT");
        doThrow(new SecurityException("ACCESS_REVOKED"))
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::index).hasMessage("ACCESS_REVOKED");
    }

    private CdsExportCapturedTextRowDto unit(
            String id, String key, String category, String references) {
        return row(
                Plan.IMPORT_UNITS,
                id,
                Map.of(
                        "run_id",
                        RUN,
                        "unit_key",
                        key,
                        "patient_key",
                        "a".repeat(64),
                        "category",
                        category,
                        "ordinal",
                        "1",
                        "source_digest",
                        "4".repeat(64),
                        "state",
                        "COMMITTED",
                        "target_references",
                        references,
                        "target_digest",
                        hex(references)));
    }

    private static final String COPY = "course-7-student-123456-program-11";

    private CdsExportRetainedImportOptionsReader.Options syntheticCopy() {
        change("run", "dry_run", "0");
        change("run", "snapshot_location", "/owned/job-" + "a".repeat(64) + ".json");
        change("patient", "copy_key", COPY);
        change("prepared", "options_digest", "5".repeat(64));
        var prepared = data.get("prepared").fields();
        change(
                "prepared",
                "digest",
                CdsPreparationSeal.digest(
                        prepared.get("payload_type"),
                        prepared.get("source_digest"),
                        prepared.get("options_digest"),
                        prepared.get("ruleset_digest"),
                        prepared.get("payload")));
        return new CdsExportRetainedImportOptionsReader.Options(
                RUN,
                "a".repeat(64),
                "1".repeat(64),
                "2".repeat(64),
                "omd-5.2",
                true,
                365,
                Map.of(COPY, "5".repeat(64)),
                "b".repeat(64));
    }

    private CdsExportRetainedIndexDto capturedIndex(
            CdsExportRetainedImportOptionsReader.Options options, boolean include, boolean sqlNull)
            throws Exception {
        var selected = new HashSet<>(TABLES);
        selected.add("retained_import_options");
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(selected),
                        eq(TABLES),
                        eq(manifest),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows output = call.getArgument(9);
                            for (var row : data.values()) output.accept(row);
                            return mock(CdsExportNativeCaptureDto.class);
                        });
        when(rows.forEach(
                        eq(lease),
                        eq(patient),
                        eq(selected),
                        eq("retained_import_options"),
                        eq(manifest),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            if (include) {
                                var map = new HashMap<String, String>();
                                map.put(
                                        "options",
                                        sqlNull
                                                ? null
                                                : CdsExportRetainedImportOptionsReader
                                                        .encodeCaptured(options));
                                CdsExportCapturedTextRowsReader.Rows output = call.getArgument(9);
                                output.accept(
                                        new CdsExportCapturedTextRowDto(
                                                "retained_import_options", RUN, map));
                            }
                            return mock(CdsExportNativeCaptureDto.class);
                        });
        service = new CdsExportRetainedIndexService(rows, captures);
        return service.index(
                lease, patient, selected, manifest, 10000, 8L << 20, 1000, 4L << 20, control);
    }

    @Test
    void shouldRestoreAppliedCopies_fromCapturedOptionsWithoutOriginalConfiguration()
            throws Exception {
        var options = syntheticCopy();
        var result = capturedIndex(options, true, false);
        assertThat(result.effectiveOptionsByUnit())
                .containsEntry(RUN + ":" + NATIVE, "5".repeat(64));
        assertThat(new CdsExportRetainedImportOptionsReader().fromCaptured(result, RUN))
                .isEqualTo(options);
    }

    @Test
    void shouldRejectMissingCapturedRunOptionRows_withoutFallingBackToOriginalFiles() {
        var options = syntheticCopy();
        assertThatThrownBy(() -> capturedIndex(options, false, false))
                .hasMessage("CDS_EXPORT_RETAINED_INDEX_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseNullCapturedOptions_forAnAppliedRun() {
        var options = syntheticCopy();
        assertThatThrownBy(() -> capturedIndex(options, true, true))
                .hasMessage("CDS_EXPORT_CAPTURED_IMPORT_OPTIONS_REQUIRED");
    }

    @Test
    void shouldRejectAChangedCapturedOptionsBinding_beforeInterpretingThePreparation() {
        var original = syntheticCopy();
        var changed =
                new CdsExportRetainedImportOptionsReader.Options(
                        RUN,
                        original.documentSha256(),
                        original.optionsSha256(),
                        "3".repeat(64),
                        original.sourceProfile(),
                        true,
                        365,
                        original.copyOptionsSha256(),
                        original.copiesSha256());
        assertThatThrownBy(() -> capturedIndex(changed, true, false))
                .hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_BINDING_MISMATCH");
    }

    @Test
    void shouldRejectUnfrozenCopyOptions_whenAnOlderCaptureLacksOriginalOptions() {
        syntheticCopy();
        assertThatThrownBy(this::index).hasMessage("CDS_EXPORT_RETAINED_COPY_OPTIONS_REQUIRED");
    }

    @Test
    void shouldRejectAResealedPreparation_whenItsCapturedCopyDigestWasChanged() {
        var options = syntheticCopy();
        change("prepared", "options_digest", "6".repeat(64));
        var prepared = data.get("prepared").fields();
        change(
                "prepared",
                "digest",
                CdsPreparationSeal.digest(
                        prepared.get("payload_type"),
                        prepared.get("source_digest"),
                        prepared.get("options_digest"),
                        prepared.get("ruleset_digest"),
                        prepared.get("payload")));
        assertThatThrownBy(() -> capturedIndex(options, true, false))
                .hasMessage("CDS_EXPORT_RETAINED_COPY_OPTIONS_MISMATCH");
    }

    private static String refs(String table, String id) throws Exception {
        return new ObjectMapper()
                .writeValueAsString(
                        new LinkedHashMap<>(
                                Map.of(
                                        "protocol",
                                        2,
                                        "refs",
                                        List.of(
                                                Map.of(
                                                        "table",
                                                        table,
                                                        "id",
                                                        id,
                                                        "digest",
                                                        "6".repeat(64))))));
    }

    private void receipt(String key, String refs) {
        change(key, "target_references", refs);
        change(key, "target_digest", hex(refs));
    }

    private void change(String key, String column, String value) {
        put(key, copy(data.get(key), data.get(key).recordKey(), Map.of(column, value)));
    }

    private void put(String key, CdsExportCapturedTextRowDto row) {
        data.put(key, row);
    }

    private static CdsExportCapturedTextRowDto row(
            Plan plan, String id, Map<String, String> values) {
        var fields = new TreeMap<String, String>();
        plan.columns().forEach(column -> fields.put(column, null));
        fields.putAll(values);
        fields.put("id", id);
        return new CdsExportCapturedTextRowDto(plan.table(), id, fields);
    }

    private static CdsExportCapturedTextRowDto copy(
            CdsExportCapturedTextRowDto original, String id, Map<String, String> overrides) {
        var values = new TreeMap<>(original.fields());
        values.putAll(overrides);
        values.put("id", id);
        return new CdsExportCapturedTextRowDto(original.table(), id, values);
    }

    private static String hex(String value) {
        return HexFormat.of()
                .formatHex(
                        io.github.carlos_emr.carlos.utility.CdsImportKeys.digest()
                                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
