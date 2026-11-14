/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The retained fields a section receives are decoded when they are read and never kept: a
 * section holds the fields of the imported record it is working on, whatever the chart's size,
 * and finds them without a pass over every field of the chart.
 */
@Tag("unit")
@Tag("cds-export")
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
class CdsExportRetainedFieldListUnitTest {
    private static final String HASH = "a".repeat(64);
    private static final String RUN = "11111111-1111-4111-8111-111111111111";
    private static final Set<CdsCategory> ALLERGIES =
            Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
    private static final int PATIENT = 27, PER_RECORD = 20;

    /** The address of imported record {@code number}: its native unit. */
    private static String record(int number) {
        return RUN + ":" + String.format("%064x", number + 1);
    }

    /** The unit key of the page the fields of record {@code number} were imported on. */
    private static String page(int number) {
        return String.format("%064x", 0x1000_0000L + number);
    }

    /**
     * An index of {@code records} imported records of twenty fields each. Each record has a
     * native unit, which names its native row, and a page unit, which names its fields.
     */
    private static CdsExportRetainedIndexDto index(int records) {
        var fields = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var parents = new HashMap<String, String>();
        var references = new HashMap<String, List<CdsExportRetainedIndexDto.Reference>>();
        var applied = new HashSet<String>();
        for (int number = 0; number < records; number++) {
            var named = new ArrayList<CdsExportRetainedIndexDto.Reference>();
            for (int field = 1; field <= PER_RECORD; field++) {
                int ordinal = number * PER_RECORD + field;
                var values =
                        new TreeMap<>(
                                CdsExportRetainedFieldMapperUnitTest.row(
                                                ordinal, "Value " + ordinal, null, null)
                                        .fields());
                values.put("unit_key", page(number));
                values.put(
                        "id",
                        io.github.carlos_emr.carlos.utility.CdsImportKeys.hash(
                                values.get("unit_key"),
                                values.get("source_namespace"),
                                values.get("source_path"),
                                values.get("occurrence")));
                var row =
                        new CdsExportCapturedTextRowDto(
                                "cds_import_field", values.get("id"), values);
                fields.put(row.recordKey(), row);
                parents.put(row.recordKey(), record(number));
                named.add(
                        new CdsExportRetainedIndexDto.Reference(
                                "cds_import_field", row.recordKey(), HASH));
            }
            references.put(RUN + ":" + page(number), List.copyOf(named));
            references.put(
                    record(number),
                    List.of(
                            new CdsExportRetainedIndexDto.Reference(
                                    "allergies", Integer.toString(number + 1), HASH)));
            applied.add(RUN + ":" + page(number));
            applied.add(record(number));
        }
        return new CdsExportRetainedIndexDto(
                HASH,
                0,
                0,
                Map.of("cds_import_unit", Map.of(), "cds_import_field", fields),
                applied,
                references,
                parents);
    }

    private static CdsExportRetainedReceiptVerificationDto checked(CdsExportRetainedIndexDto index) {
        var units =
                new HashMap<String, List<CdsExportRetainedReceiptVerificationDto.CheckedReference>>();
        index.references()
                .forEach(
                        (unit, references) ->
                                units.put(
                                        unit,
                                        references.stream()
                                                .map(
                                                        reference ->
                                                                new CdsExportRetainedReceiptVerificationDto
                                                                        .CheckedReference(
                                                                        reference,
                                                                        reference.digest(),
                                                                        CdsExportRetainedReceiptVerificationDto
                                                                                .Reason
                                                                                .UNCHANGED_ROW))
                                                .toList()));
        return new CdsExportRetainedReceiptVerificationDto(
                HASH, units, index.appliedUnits(), Set.of());
    }

    @Test
    void shouldDecodeEveryFieldInTheOrderOfItsId_asTheKeptFieldsWere() throws Exception {
        var index = index(5);
        var kept = new TreeMap<String, CdsExportRetainedFieldDto>();
        var mapper = new CdsExportRetainedFieldMapper();
        for (var row : index.tables().get("cds_import_field").values()) {
            var field = mapper.map(PATIENT, ALLERGIES, row);
            kept.put(field.id(), field);
        }
        var list = CdsExportRetainedFieldList.of(index, PATIENT, ALLERGIES, () -> {});
        assertThat(list).hasSize(100).containsExactlyElementsOf(kept.values());
        assertThat(list.find(kept.firstKey())).isEqualTo(kept.firstEntry().getValue());
        assertThat(list.find("f".repeat(64))).isNull();
        assertThat(list.idsOf(record(2))).hasSize(PER_RECORD).isSorted();
        assertThat(list.idsOf(record(99))).isEmpty();
        assertThat(list.toString()).doesNotContain("Value", "27");
        assertThatThrownBy(() -> list.add(kept.firstEntry().getValue()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRefuseTheChart_forAFieldRowThatCannotBeDecoded() {
        var index = index(2);
        // Another patient's field, and a category the job did not select.
        assertThatThrownBy(() -> CdsExportRetainedFieldList.of(index, 28, ALLERGIES, () -> {}))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(
                        () ->
                                CdsExportRetainedFieldList.of(
                                        index, PATIENT, Set.of(CdsCategory.REPORTS), () -> {}))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldReturnTheFieldsOfARecord_asAPassOverEveryFieldOfTheChartDoes() throws Exception {
        var index = index(40);
        var checked = checked(index);
        var list = CdsExportRetainedFieldList.of(index, PATIENT, ALLERGIES, () -> {});
        var kept = List.copyOf(list);
        var fields = new CdsExportRetainedOccurrenceFields();
        for (int number : List.of(0, 17, 39)) {
            var scanned =
                    fields.fieldsFor(
                            PATIENT,
                            CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                            record(number),
                            index,
                            checked,
                            kept,
                            () -> {});
            assertThat(scanned).hasSize(PER_RECORD);
            assertThat(
                            fields.fieldsFor(
                                    PATIENT,
                                    CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                    record(number),
                                    index,
                                    checked,
                                    list,
                                    () -> {}))
                    .containsExactlyElementsOf(scanned);
        }
        // The same refusals: a record of another category, and a record with no field.
        for (List<CdsExportRetainedFieldDto> supplied : List.of(kept, list)) {
            assertThatThrownBy(
                            () ->
                                    fields.fieldsFor(
                                            PATIENT,
                                            CdsCategory.REPORTS,
                                            record(3),
                                            index,
                                            checked,
                                            supplied,
                                            () -> {}))
                    .hasMessage("CDS_EXPORT_REPORT_RETAINED_ASSEMBLY_SCOPE_INVALID");
            assertThatThrownBy(
                            () ->
                                    fields.fieldsFor(
                                            PATIENT,
                                            CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                            RUN + ":" + page(3),
                                            index,
                                            checked,
                                            supplied,
                                            () -> {}))
                    .hasMessage("CDS_EXPORT_ALLERGY_SOURCE_FIELDS_REQUIRED");
        }
    }

    @Test
    void shouldHoldNoDecodedField_whileASectionWorksThroughAnImportedChart(@TempDir Path root)
            throws Exception {
        // Before: withValues decoded every field of the chart and kept them all for the section
        // (81,938 in each section of a chart of 2,048 notes and 2,048 results).
        int records = 200;
        var index = index(records);
        var checked = checked(index);
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 47);
        var store =
                new CdsExportStagingService(
                        root, RUN, 1, new SecretKeySpec(key, "AES"), 64L << 20, 0, () -> {});
        var values = store.stage("RETAINED_VALUES", output -> {});
        var members = store.stage("RETAINED_INDEX", output -> output.write(1));
        var indices = mock(CdsExportRetainedIndexService.class);
        var payloads = mock(CdsExportRetainedPayloadService.class);
        var captures = mock(CdsExportNativeCaptureService.class);
        var stores = mock(CdsExportStagingFactory.class);
        var patient = mock(CdsExportCohortService.Patient.class);
        var control = mock(CdsExportRunControl.class);
        when(patient.patient()).thenReturn(PATIENT);
        when(stores.openExisting(any(), anyLong(), any(), any())).thenReturn(store);
        when(indices.index(
                        any(), any(), anySet(), any(), anyLong(), anyLong(), anyInt(), anyLong(),
                        any()))
                .thenReturn(index);
        long count = index.tables().get("cds_import_field").size();
        doAnswer(
                        call -> {
                            CdsExportNativeCaptureReader.Fields output = call.getArgument(8);
                            for (String id : index.tables().get("cds_import_field").keySet())
                                output.output(
                                        new CdsExportCapturedFieldDto(
                                                "cds_import_field",
                                                id,
                                                "payload",
                                                CdsExportCapturedFieldDto.Kind.SQL_NULL,
                                                0,
                                                0,
                                                HASH));
                            return new CdsExportRetainedPayloadCaptureDto(
                                    1, RUN, 1, PATIENT, HASH, HASH, HASH, count, 0, 0, values, members);
                        })
                .when(payloads)
                .scan(any(), any(), anySet(), any(), any(), anyLong(), anyLong(), any(), any());
        var lease =
                new CdsExportLeaseDto(
                        RUN,
                        1,
                        HASH,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                new CdsExportCommand(
                                        "retained-fields",
                                        "999998",
                                        null,
                                        CdsLogicalProfile.ONTARIO_MD_5_2,
                                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                                        List.of(),
                                        List.of(PATIENT),
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                                        "recipient",
                                        "audit"),
                                HASH,
                                "sender",
                                HASH,
                                HASH,
                                new CdsExportStorageBindingDto("primary", "primary", HASH)));
        var handed = new ArrayList<WeakReference<CdsExportRetainedFieldDto>>();
        int[] reachable = {-1};
        long[] emitted = {0};
        new CdsExportRetainedValuesService(indices, payloads, captures, stores)
                .withValues(
                        lease,
                        patient,
                        Set.of("cds_import_field"),
                        new CdsExportStagedFileDto("1".repeat(64), "SOURCE_MANIFEST", HASH, 1),
                        new CdsExportStagedFileDto("4".repeat(64), "RETAINED_MANIFEST", HASH, 1),
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        1,
                        1,
                        control,
                        (same, retained, streams) -> {
                            assertThat(same).isSameAs(index);
                            assertThat(retained).hasSize((int) count);
                            // A section: the ids of its category, then record by record.
                            var expected = new HashSet<String>();
                            for (var field : retained) expected.add(field.id());
                            var occurrences = new CdsExportRetainedOccurrenceFields();
                            for (int number = 0; number < records; number++) {
                                var fields =
                                        occurrences.fieldsFor(
                                                PATIENT,
                                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                                record(number),
                                                index,
                                                checked,
                                                retained,
                                                () -> {});
                                for (var field : fields) {
                                    handed.add(new WeakReference<>(field));
                                    try (var text = streams.text(field)) {
                                        emitted[0] += text.transferTo(java.io.Writer.nullWriter());
                                    }
                                    assertThat(expected.remove(field.id())).isTrue();
                                }
                            }
                            assertThat(expected).isEmpty();
                            // A return to keeping the chart's fields would leave every one
                            // reachable here, inside the section.
                            for (int attempt = 0; attempt < 20; attempt++) {
                                System.gc();
                                reachable[0] = 0;
                                for (var field : handed) if (field.get() != null) reachable[0]++;
                                if (reachable[0] <= PER_RECORD) break;
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException("INTERRUPTED");
                                }
                            }
                        });
        assertThat(handed).hasSize((int) count);
        assertThat(emitted[0]).isGreaterThan(count);
        assertThat(reachable[0]).isBetween(0, PER_RECORD);
        handed.clear();
    }
}
