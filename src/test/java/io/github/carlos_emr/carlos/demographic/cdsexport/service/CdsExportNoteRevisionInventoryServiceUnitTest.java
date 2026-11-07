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

@Tag("unit") @Tag("cds-export")
class CdsExportNoteRevisionInventoryServiceUnitTest {
    private static final String HASH = "a".repeat(64), UUID_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    private final CdsExportNativeRowReceiptReader receipts = mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final Set<String> tables = Set.of("casemgmt_note");
    private CdsExportPatientCaptureDto source;
    private CdsExportNativeCaptureDto capture;
    private CdsExportNativeRowReceiptDto witness;
    private CdsExportNoteRevisionInventoryService service;
    @BeforeEach void setup() throws Exception {
        service = new CdsExportNoteRevisionInventoryService(captures, receipts); when(patient.patient()).thenReturn(27);
        var fields = new LinkedHashMap<String, String>(); for (String name : io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan.CASE_NOTES.columns()) fields.put(name, null);
        fields.put("note_id", "91"); fields.put("demographic_no", "27"); fields.put("program_no", ""); fields.put("reporter_caisi_role", "1"); fields.put("uuid", UUID_A); fields.put("signed", "0"); fields.put("archived", "0"); fields.put("update_date", "2020-01-01 01:02:03"); fields.put("note", "Owned clinical body"); fields.put("history", "");
        var columns = new ArrayList<CdsNativeRowDigest.Column>(); var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var field : fields.entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(field.getKey(), "VARCHAR", 255, 0)); var text = field.getValue(); byte[] bytes = text == null ? null : text.getBytes(StandardCharsets.UTF_8);
            cells.add(new CdsNativeRowState.Cell(text == null, text == null ? 0 : bytes.length, text == null ? null : HexFormat.of().formatHex(io.github.carlos_emr.carlos.utility.CdsImportKeys.digest().digest(bytes))));
        }
        var state = new CdsNativeRowState("casemgmt_note", "91", columns, cells); witness = new CdsExportNativeRowReceiptDto("casemgmt_note", "91", CdsExportPatientTableSourceReader.key(91), state.digest(), state);
        capture = mock(CdsExportNativeCaptureDto.class); when(capture.tables()).thenReturn(Map.of("casemgmt_note", new CdsExportCapturedTableDto(fields.keySet(), 1, HASH, columns, List.of(new CdsExportNoteAccessDto(91, 27, "", "1")))));
        when(capture.values()).thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", HASH, 1)); when(capture.index()).thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", HASH, 1));
        source = new CdsExportPatientCaptureDto(capture, new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", HASH, 1));
        var values = new ByteArrayOutputStream(); var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer().compose(values, index, tables, 1000, 1L << 20, () -> {}, writer -> {
            writer.beginTable("casemgmt_note", fields.keySet(), 1); writer.row(CdsExportPatientTableSourceReader.key(91), row -> { for (var field : fields.entrySet()) row.text(field.getKey(), field.getValue()); }); writer.endTable();
        });
        when(captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any())).thenAnswer(call -> {
            new CdsExportNativeCaptureReader().scan(new ByteArrayInputStream(values.toByteArray()), new ByteArrayInputStream(index.toByteArray()),
                    Map.of("casemgmt_note", new CdsExportNativeCaptureReader.Table(fields.keySet(), 1)), 1000, 1L << 20, () -> {}, call.getArgument(7)); return capture;
        });
        when(receipts.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any())).thenAnswer(call -> {
            call.<CdsExportNativeRowReceiptReader.Receipts>getArgument(7).accept(witness);
            return new CdsExportNativeRowReceiptsDto(HASH, Map.of("casemgmt_note", 1L), 1, 0, Set.of());
        });
    }
    private CdsExportNoteRevisionPlanner.Result run(long memory) throws Exception { return service.inventory(lease, patient, tables, source, 1000, 1L << 20, memory, control); }
    @Test void shouldOwnBothCompleteScans_andReauthorizeBeforeReturningTheInventory() throws Exception {
        var result = run(1L << 20); assertThat(result.rows()).isEqualTo(1); assertThat(result.chains().getFirst().currentNoteId()).isEqualTo(91);
        verify(captures, times(2)).authorizeCapturedSources(lease, patient, tables, capture, control);
    }
    @Test void shouldRefuseAMissingNoteWitness_beforeOpeningRevisionMetadata() throws Exception {
        doReturn(new CdsExportNativeRowReceiptsDto(HASH, Map.of("casemgmt_note", 0L), 0, 0, Set.of())).when(receipts).scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        assertThatThrownBy(() -> run(1L << 20)).hasMessage("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID"); verifyNoInteractions(captures);
    }
    @Test void shouldRefuseAReceiptFromAnotherSourceGeneration_beforeMetadataReadback() throws Exception {
        doAnswer(call -> { call.<CdsExportNativeRowReceiptReader.Receipts>getArgument(7).accept(witness); return new CdsExportNativeRowReceiptsDto("b".repeat(64), Map.of("casemgmt_note", 1L), 1, 0, Set.of()); }).when(receipts).scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        assertThatThrownBy(() -> run(1L << 20)).hasMessage("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID"); verifyNoInteractions(captures);
    }
    @Test void shouldEnforceTheWitnessMemoryBudget_beforeAllocatingTheMetadataList() {
        assertThatThrownBy(() -> run(1)).hasMessage("CDS_EXPORT_RESOURCE_LIMIT"); verifyNoInteractions(captures);
    }
    @Test void shouldPropagateFinalAuthorizationRevocation_withoutReturningAnInventory() {
        doThrow(new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED")).when(captures).authorizeCapturedSources(lease, patient, tables, capture, control);
        assertThatThrownBy(() -> run(1L << 20)).hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
    }
    @Test void shouldRefuseAChangedVerifiedCapture_withoutUsingItsMetadata() throws Exception {
        doReturn(mock(CdsExportNativeCaptureDto.class)).when(captures).scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        assertThatThrownBy(() -> run(1L << 20)).hasMessage("CDS_EXPORT_NOTE_REVISION_METADATA_INVALID");
    }
}
