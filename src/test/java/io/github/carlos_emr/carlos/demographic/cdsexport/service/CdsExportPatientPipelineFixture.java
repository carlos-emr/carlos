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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;

/** Real encrypted output and XML validation; source metadata/writers are independently controlled by each orchestration test. */
final class CdsExportPatientPipelineFixture {
    static final String HASH = "a".repeat(64), VERSION = "2026.11.0-test";
    final String run = UUID.randomUUID().toString();
    final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    final CdsExportRetainedIndexService indices = mock(CdsExportRetainedIndexService.class);
    final CdsExportNativeRowReceiptReader witnesses = mock(CdsExportNativeRowReceiptReader.class);
    final CdsExportHistoryInventoryService historyInventory = mock(CdsExportHistoryInventoryService.class);
    final CdsExportCareOccurrenceService careInventory = mock(CdsExportCareOccurrenceService.class);
    final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    final CdsExportRunControl control = mock(CdsExportRunControl.class);
    final CdsExportDemographicOccurrenceService demographics =
            mock(CdsExportDemographicOccurrenceService.class);
    final CdsExportClinicalOccurrenceService clinical =
            mock(CdsExportClinicalOccurrenceService.class);
    final CdsExportClinicalOccurrenceService.Bounds bounds =
            new CdsExportClinicalOccurrenceService.Bounds(
                    10000, 8L << 20, 1000, 8L << 20, 100, 100);
    final CdsExportStagingService store;
    final CdsExportLeaseDto lease;
    final CdsExportPatientCaptureDto source;
    final CdsExportStagedFileDto cohort;
    final CdsExportPatientInventoryService inventories;
    final Set<String> tables = Set.of("demographic", "cds_import_unit", "cds_import_field");
    final Map<String, Map<String, CdsExportCapturedTextRowDto>> metadata = new HashMap<>();
    final Set<String> applied = new HashSet<>();
    final Map<String, List<CdsExportRetainedIndexDto.Reference>> references = new HashMap<>();
    final List<CdsExportNativeRowReceiptDto> nativeRows = new ArrayList<>();
    final Path root;
    boolean emitDemographic = true,
            receiptDemographic = true,
            duplicateReceipt,
            swallowDuplicate,
            foreignReceipt;
    CdsExportClinicalOccurrenceService.Receipts lastSink;
    CdsExportClinicalOccurrenceService.Receipt lastReceipt;
    String senderId = "C27";

    CdsExportPatientPipelineFixture(Path root, Set<CdsCategory> categories) throws Exception {
        this.root = root;
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        var storage = new CdsExportStorageBindingDto("primary", "primary", HASH);
        var command =
                new CdsExportCommand(
                        "pipeline-proof",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        categories,
                        "receiver",
                        "audit");
        lease =
                new CdsExportLeaseDto(
                        run,
                        1,
                        UUID.randomUUID().toString(),
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                HASH,
                                "sender",
                                HASH,
                                HASH,
                                storage));
        store =
                new CdsExportStagingService(
                        root,
                        run,
                        1,
                        new SecretKeySpec(new byte[32], "AES"),
                        8L << 20,
                        0,
                        () -> {});
        when(stores.openExisting(eq(run), eq(1L), eq(storage), any())).thenReturn(store);
        when(patient.patient()).thenReturn(27);
        cohort = store.stage("COHORT_MANIFEST", output -> output.write(1));
        when(patient.cohortSha256()).thenReturn(cohort.plaintextSha256());
        var values = store.stage("SOURCE_VALUES", output -> {});
        var index = store.stage("SOURCE_INDEX", output -> {});
        var manifest = store.stage("SOURCE_MANIFEST", output -> output.write(1));
        var scopes = new HashMap<String, CdsExportCapturedTableDto>();
        tables.forEach(
                table ->
                        scopes.put(
                                table,
                                new CdsExportCapturedTableDto(
                                        Set.of("id"), table.equals("demographic") ? 1 : 0, HASH)));
        var now = Instant.now();
        source =
                new CdsExportPatientCaptureDto(
                        new CdsExportNativeCaptureDto(
                                2,
                                run,
                                1,
                                27,
                                cohort.plaintextSha256(),
                                now,
                                now,
                                scopes,
                                1,
                                0,
                                values,
                                index),
                        manifest);
        nativeRows.add(new CdsExportNativeRowReceiptDto("demographic", "27", "0000000027", HASH));
        when(indices.index(
                        any(), any(), anySet(), any(), anyLong(), anyLong(), anyInt(), anyLong(),
                        any()))
                .thenAnswer(call -> index());
        when(witnesses.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            var sink =
                                    (CdsExportNativeRowReceiptReader.Receipts) call.getArgument(7);
                            var counts = new HashMap<String, Long>();
                            for (var row : nativeRows) {
                                sink.accept(row);
                                counts.merge(row.table(), 1L, Long::sum);
                            }
                            return new CdsExportNativeRowReceiptsDto(
                                    source.manifest().plaintextSha256(),
                                    counts,
                                    nativeRows.size(),
                                    0,
                                    Set.of());
                        });
        inventories = new CdsExportPatientInventoryService(indices, witnesses, historyInventory, careInventory);
        when(demographics.append(
                        any(),
                        any(),
                        anyString(),
                        anySet(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenAnswer(
                        call -> {
                            String key = call.getArgument(2);
                            var emitter = (CdsExportXmlComposer.SectionEmitter) call.getArgument(8);
                            var sink =
                                    (CdsExportClinicalOccurrenceService.Receipts)
                                            call.getArgument(9);
                            var identity =
                                    new CdsExportIdentityDto(
                                            key,
                                            CdsCategory.DEMOGRAPHICS,
                                            CdsImportKeys.hash(
                                                    "carlos-native-occurrence-v1",
                                                    "demographic",
                                                    "27"));
                            if (emitDemographic)
                                emitter.emit(
                                        new CdsExportXmlComposer.Section(
                                                identity, this::demographicContent));
                            var receipt =
                                    new CdsExportClinicalOccurrenceService.Receipt(
                                            identity,
                                            source.manifest().plaintextSha256(),
                                            "demographic",
                                            "27",
                                            null,
                                            Map.of(),
                                            Set.of(),
                                            Set.of());
                            lastSink = sink;
                            lastReceipt = receipt;
                            if (receiptDemographic) sink.accept(receipt);
                            if (foreignReceipt) {
                                var fault =
                                        new java.util.concurrent.atomic.AtomicReference<
                                                Throwable>();
                                Thread foreign =
                                        new Thread(
                                                () -> {
                                                    try {
                                                        sink.accept(receipt);
                                                    } catch (Throwable failure) {
                                                        fault.set(failure);
                                                    }
                                                });
                                foreign.start();
                                foreign.join();
                                if (!(fault.get() instanceof IOException))
                                    throw new IOException("FOREIGN_CALLBACK_ACCEPTED");
                            }
                            if (duplicateReceipt) {
                                try {
                                    sink.accept(receipt);
                                } catch (IOException refused) {
                                    if (!swallowDuplicate) throw refused;
                                }
                            }
                            return new CdsExportClinicalOccurrenceService.Result(
                                    source.manifest().plaintextSha256(),
                                    CdsCategory.DEMOGRAPHICS,
                                    1,
                                    0,
                                    1,
                                    0);
                        });
    }

    void report(boolean attach, boolean reference) throws IOException {
        String unit = "d".repeat(64);
        unit(CdsCategory.REPORTS, unit, List.of());
        when(clinical.append(
                        eq(CdsCategory.REPORTS),
                        any(),
                        any(),
                        anyString(),
                        anySet(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenAnswer(
                        call -> {
                            String patientKey = call.getArgument(3);
                            var identity =
                                    new CdsExportIdentityDto(
                                            patientKey,
                                            CdsCategory.REPORTS,
                                            CdsImportKeys.hash(
                                                    "carlos-retained-occurrence-v1",
                                                    run + ":" + unit));
                            var emitter = (CdsExportXmlComposer.SectionEmitter) call.getArgument(9);
                            var sink =
                                    (CdsExportClinicalOccurrenceService.Receipts)
                                            call.getArgument(10);
                            emitter.emit(
                                    new CdsExportXmlComposer.Section(
                                            identity,
                                            writer -> {
                                                writer.element("cds", "Format", "Binary");
                                                writer.element(
                                                        "cds", "FileExtensionAndVersion", "PDF");
                                                if (reference)
                                                    writer.element(
                                                            "cds",
                                                            "FilePath",
                                                            "Reports/example.pdf");
                                                else {
                                                    writer.start("cds", "Content");
                                                    writer.element("cds_dt", "Media", "cGRm");
                                                    writer.end();
                                                }
                                                writer.element(
                                                        "cds",
                                                        "Class",
                                                        "Diagnostic Imaging Report");
                                            }));
                            sink.accept(
                                    new CdsExportClinicalOccurrenceService.Receipt(
                                            identity,
                                            source.manifest().plaintextSha256(),
                                            "cds_import_unit",
                                            unit,
                                            run + ":" + unit,
                                            Map.of(),
                                            Set.of(),
                                            Set.of()));
                            if (attach) {
                                var attachment =
                                        store.stage(
                                                "REPORT_ATTACHMENT",
                                                output ->
                                                        output.write(
                                                                "pdf"
                                                                        .getBytes(
                                                                                java.nio.charset
                                                                                        .StandardCharsets
                                                                                        .UTF_8)));
                                sink.attachment(
                                        identity,
                                        new CdsExportPackageMemberDto(
                                                "Reports/example.pdf", attachment));
                            }
                            return new CdsExportClinicalOccurrenceService.Result(
                                    source.manifest().plaintextSha256(),
                                    CdsCategory.REPORTS,
                                    1,
                                    1,
                                    0,
                                    0);
                        });
    }

    CdsExportRetainedIndexDto index() {
        return new CdsExportRetainedIndexDto(
                source.manifest().plaintextSha256(), 0, 0, metadata, applied, references, Map.of());
    }

    void unit(CdsCategory category, String key, List<CdsExportRetainedIndexDto.Reference> refs) {
        String address = run + ":" + key, id = CdsImportKeys.hash(run, key);
        var row =
                new CdsExportCapturedTextRowDto(
                        "cds_import_unit",
                        id,
                        Map.of("run_id", run, "unit_key", key, "category", category.wireName()));
        metadata.computeIfAbsent("cds_import_unit", ignored -> new HashMap<>()).put(id, row);
        applied.add(address);
        references.put(address, refs);
    }

    CdsExportPatientDocumentService.Document compose() throws IOException {
        return new CdsExportPatientDocumentService(
                        stores, captures, inventories, demographics, clinical)
                .compose(
                        lease,
                        patient,
                        tables,
                        source,
                        source.manifest(),
                        bounds,
                        VERSION,
                        CdsExportXmlLimitsDto.defaults(),
                        control);
    }

    Set<String> members() throws IOException {
        try (var files = Files.list(root)) {
            return files.map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private void demographicContent(CdsExportXmlComposer.ContentWriter writer) throws IOException {
        writer.start("cds", "Names");
        writer.start("cds_dt", "LegalName");
        writer.attribute("", "namePurpose", "L");
        writer.start("cds_dt", "FirstName");
        writer.element("cds_dt", "Part", "Export");
        writer.element("cds_dt", "PartType", "GIV");
        writer.end();
        writer.start("cds_dt", "LastName");
        writer.element("cds_dt", "Part", "Fixture");
        writer.element("cds_dt", "PartType", "FAMC");
        writer.end();
        writer.end();
        writer.end();
        writer.element("cds", "DateOfBirth", "1971-03-04");
        writer.element("cds", "Gender", "F");
        writer.element("cds", "UniqueVendorIdSequence", senderId);
        writer.start("cds", "PersonStatusCode");
        writer.element("cds", "PersonStatusAsEnum", "A");
        writer.end();
    }
}
