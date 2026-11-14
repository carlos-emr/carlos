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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
class CdsExportNoteRevisionInventoryServiceUnitTest {
    private static final String HASH = "a".repeat(64),
            UUID_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportNativeRowReceiptReader receipts =
            mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final Set<String> tables = Set.of("casemgmt_note");
    private CdsExportPatientCaptureDto source;
    private CdsExportNativeCaptureDto capture;
    private CdsExportNativeRowReceiptDto witness;
    private CdsExportNoteRevisionInventoryService service;

    @BeforeEach
    void setup() throws Exception {
        service = new CdsExportNoteRevisionInventoryService(captures, receipts);
        when(patient.patient()).thenReturn(27);
        chart(1);
    }

    /** Runs when the second scan (the revision metadata) starts. */
    private Runnable beforeMetadataScan = () -> {};

    private static LinkedHashMap<String, String> fields(long note) {
        var fields = new LinkedHashMap<String, String>();
        for (String name :
                io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                        .CASE_NOTES
                        .columns()) fields.put(name, null);
        fields.put("note_id", Long.toString(note));
        fields.put("demographic_no", "27");
        fields.put("program_no", "");
        fields.put("reporter_caisi_role", "1");
        // Note 91 keeps the fixture's own UUID; every other note is its own revision chain.
        fields.put(
                "uuid",
                note == 91
                        ? UUID_A
                        : String.format(Locale.ROOT, "%08x-aaaa-4aaa-8aaa-aaaaaaaaaaaa", note));
        fields.put("signed", "0");
        fields.put("archived", "0");
        fields.put("update_date", "2020-01-01 01:02:03");
        fields.put("note", "Owned clinical body");
        fields.put("history", "");
        return fields;
    }

    /** The whole-row receipt of one synthetic note, as the receipt scan delivers it. */
    private static CdsExportNativeRowReceiptDto receipt(
            long note, List<CdsNativeRowDigest.Column> columns) {
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (String text : fields(note).values()) {
            byte[] bytes = text == null ? null : text.getBytes(StandardCharsets.UTF_8);
            cells.add(
                    new CdsNativeRowState.Cell(
                            text == null,
                            text == null ? 0 : bytes.length,
                            text == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(
                                                    io.github.carlos_emr.carlos.utility
                                                            .CdsImportKeys.digest()
                                                            .digest(bytes))));
        }
        var state = new CdsNativeRowState("casemgmt_note", Long.toString(note), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                "casemgmt_note",
                Long.toString(note),
                CdsExportPatientTableSourceReader.key(note),
                state.digest(),
                state);
    }

    /** One synthetic chart of {@code count} notes, numbered from 91, as both scans see it. */
    private void chart(int count) throws Exception {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        for (String name : fields(91).keySet())
            columns.add(new CdsNativeRowDigest.Column(name, "VARCHAR", 255, 0));
        var witnesses = new ArrayList<CdsExportNativeRowReceiptDto>();
        var scopes = new ArrayList<CdsExportNoteAccessDto>();
        for (long note = 91; note < 91 + count; note++) {
            witnesses.add(receipt(note, columns));
            scopes.add(new CdsExportNoteAccessDto(note, 27, "", "1"));
        }
        witness = witnesses.getFirst();
        var names = fields(91).keySet();
        capture = mock(CdsExportNativeCaptureDto.class);
        when(capture.tables())
                .thenReturn(
                        Map.of(
                                "casemgmt_note",
                                new CdsExportCapturedTableDto(
                                        names, count, HASH, columns, scopes)));
        when(capture.values())
                .thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", HASH, 1));
        when(capture.index())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", HASH, 1));
        source =
                new CdsExportPatientCaptureDto(
                        capture,
                        new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", HASH, 1));
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        long maximumFields = 1000L * count, maximumBytes = (1L << 20) * count;
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        tables,
                        maximumFields,
                        maximumBytes,
                        () -> {},
                        writer -> {
                            writer.beginTable("casemgmt_note", names, count);
                            for (long note = 91; note < 91 + count; note++) {
                                var row = fields(note);
                                writer.row(
                                        CdsExportPatientTableSourceReader.key(note),
                                        cells -> {
                                            for (var field : row.entrySet())
                                                cells.text(field.getKey(), field.getValue());
                                        });
                            }
                            writer.endTable();
                        });
        reset(captures, receipts);
        when(captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            beforeMetadataScan.run();
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            new ByteArrayInputStream(index.toByteArray()),
                                            Map.of(
                                                    "casemgmt_note",
                                                    new CdsExportNativeCaptureReader.Table(
                                                            names, count)),
                                            maximumFields,
                                            maximumBytes,
                                            () -> {},
                                            call.getArgument(7));
                            return capture;
                        });
        when(receipts.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            for (var each : witnesses)
                                call.<CdsExportNativeRowReceiptReader.Receipts>getArgument(7)
                                        .accept(each);
                            return new CdsExportNativeRowReceiptsDto(
                                    HASH, Map.of("casemgmt_note", (long) count), count, 0, Set.of());
                        });
    }

    private CdsExportNoteRevisionPlanner.Result run(long memory) throws Exception {
        return service.inventory(lease, patient, tables, source, 1000, 1L << 20, memory, control);
    }

    @Test
    void shouldOwnBothCompleteScans_andReauthorizeBeforeReturningTheInventory() throws Exception {
        var result = run(1L << 20);
        assertThat(result.rows()).isEqualTo(1);
        assertThat(result.chains().getFirst().currentNoteId()).isEqualTo(91);
        verify(captures, times(2))
                .authorizeCapturedSources(lease, patient, tables, capture, control);
    }

    @Test
    void shouldRefuseAMissingNoteWitness_beforeOpeningRevisionMetadata() throws Exception {
        doReturn(
                        new CdsExportNativeRowReceiptsDto(
                                HASH, Map.of("casemgmt_note", 0L), 0, 0, Set.of()))
                .when(receipts)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        assertThatThrownBy(() -> run(1L << 20))
                .hasMessage("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
        verifyNoInteractions(captures);
    }

    @Test
    void shouldRefuseAReceiptFromAnotherSourceGeneration_beforeMetadataReadback() throws Exception {
        doAnswer(
                        call -> {
                            call.<CdsExportNativeRowReceiptReader.Receipts>getArgument(7)
                                    .accept(witness);
                            return new CdsExportNativeRowReceiptsDto(
                                    "b".repeat(64), Map.of("casemgmt_note", 1L), 1, 0, Set.of());
                        })
                .when(receipts)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        assertThatThrownBy(() -> run(1L << 20))
                .hasMessage("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
        verifyNoInteractions(captures);
    }

    /**
     * A chart of 16,391 notes. The inventory used to hold a chart with more than 8,192 notes,
     * and one whose receipts were estimated above the caller's byte bound (about 3,500 notes at
     * the default), under {@code CDS_EXPORT_RESOURCE_LIMIT}. Neither holds a chart now,
     * whatever that former bound says.
     */
    @Test
    void shouldInventoryEveryNoteOfALongChart_whateverTheFormerMemoryBoundSays() throws Exception {
        int count = 2 * 8192 + 7;
        chart(count);
        for (long formerBound : new long[] {1, 1L << 20, Long.MAX_VALUE}) {
            var result = run(formerBound);
            assertThat(result.rows()).isEqualTo(count);
            assertThat(result.chains()).hasSize(count);
        }
        verify(captures, times(6))
                .authorizeCapturedSources(lease, patient, tables, capture, control);
    }

    /**
     * The memory of the inventory must not grow with what a note's receipt holds. Every receipt
     * is delivered once and never referenced by the test again; when the second scan starts, the
     * inventory must hold none of them. An inventory that keeps one receipt for each note until
     * it plans (as it did, several kilobytes a note) fails here.
     */
    @Test
    void shouldKeepNoReceiptOfAnyNote_onceTheReceiptScanIsDone() throws Exception {
        // Enough notes to tell "none kept" from "some kept"; the check does not depend on how
        // many there are, and the fixture itself holds a second copy of every receipt.
        int count = 4_000;
        chart(count);
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        for (String name : fields(91).keySet())
            columns.add(new CdsNativeRowDigest.Column(name, "VARCHAR", 255, 0));
        var delivered = new ArrayList<java.lang.ref.WeakReference<CdsExportNativeRowReceiptDto>>();
        // Replaces the fixture's scan, which hands out receipts the fixture itself keeps.
        org.mockito.Mockito.doAnswer(
                        call -> {
                            for (long note = 91; note < 91 + count; note++) {
                                var each = receipt(note, columns);
                                delivered.add(new java.lang.ref.WeakReference<>(each));
                                call.<CdsExportNativeRowReceiptReader.Receipts>getArgument(7)
                                        .accept(each);
                            }
                            return new CdsExportNativeRowReceiptsDto(
                                    HASH, Map.of("casemgmt_note", (long) count), count, 0, Set.of());
                        })
                .when(receipts)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        var held = new long[] {-1};
        beforeMetadataScan =
                () -> {
                    // Between the two scans: which of the receipts is still reachable?
                    for (int attempt = 0; attempt < 50; attempt++) {
                        System.gc();
                        held[0] =
                                delivered.stream()
                                        .filter(reference -> reference.get() != null)
                                        .count();
                        if (held[0] == 0) return;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                };

        var result = run(1);

        assertThat(result.rows()).isEqualTo(count);
        assertThat(result.chains()).hasSize(count);
        assertThat(delivered).hasSize(count);
        assertThat(held[0]).isZero();
    }

    @Test
    void shouldPropagateFinalAuthorizationRevocation_withoutReturningAnInventory() {
        doThrow(new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED"))
                .when(captures)
                .authorizeCapturedSources(lease, patient, tables, capture, control);
        assertThatThrownBy(() -> run(1L << 20)).hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
    }

    @Test
    void shouldRefuseAChangedVerifiedCapture_withoutUsingItsMetadata() throws Exception {
        doReturn(mock(CdsExportNativeCaptureDto.class))
                .when(captures)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        assertThatThrownBy(() -> run(1L << 20))
                .hasMessage("CDS_EXPORT_NOTE_REVISION_METADATA_INVALID");
    }
}
