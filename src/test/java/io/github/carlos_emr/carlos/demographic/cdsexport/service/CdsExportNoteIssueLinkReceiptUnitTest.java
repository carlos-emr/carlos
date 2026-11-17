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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNoteIssueLinkReceiptUnitTest {
    private static final String TABLE = "casemgmt_issue_notes", HASH = "a".repeat(64);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final List<CdsExportCapturedTextRowDto> rows = new ArrayList<>();
    private List<CdsNativeRowDigest.Column> columns =
            List.of(
                    new CdsNativeRowDigest.Column("id", "BIGINT", 19, 0),
                    new CdsNativeRowDigest.Column("note_id", "BIGINT", 19, 0));
    private CdsExportPatientCaptureDto source;
    private CdsExportNativeCaptureDto capture;

    @BeforeEach
    void setup() {
        rows.add(row("91", "41"));
    }

    private static CdsExportCapturedTextRowDto row(String note, String issue) {
        return new CdsExportCapturedTextRowDto(
                TABLE,
                CdsExportPatientTableSourceReader.key(Long.parseLong(note))
                        + CdsExportPatientTableSourceReader.key(Long.parseLong(issue)),
                Map.of("note_id", note, "id", issue));
    }

    private void prepare() throws Exception {
        capture = mock(CdsExportNativeCaptureDto.class);
        var table =
                new CdsExportCapturedTableDto(Set.of("id", "note_id"), rows.size(), HASH, columns);
        when(capture.tables()).thenReturn(Map.of(TABLE, table));
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
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of(TABLE),
                        1000,
                        1L << 20,
                        () -> {},
                        writer -> {
                            writer.beginTable(TABLE, table.columns(), rows.size());
                            for (var row : rows)
                                writer.row(
                                        row.recordKey(),
                                        output -> {
                                            for (var field : row.fields().entrySet())
                                                output.text(field.getKey(), field.getValue());
                                        });
                            writer.endTable();
                        });
        when(captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            new ByteArrayInputStream(index.toByteArray()),
                                            Map.of(
                                                    TABLE,
                                                    new CdsExportNativeCaptureReader.Table(
                                                            table.columns(), rows.size())),
                                            1000,
                                            1L << 20,
                                            () -> {},
                                            call.getArgument(7));
                            return capture;
                        });
    }

    private List<CdsExportNativeRowReceiptDto> read() throws Exception {
        var output = new ArrayList<CdsExportNativeRowReceiptDto>();
        var result =
                new CdsExportNativeRowReceiptReader(captures)
                        .scan(
                                lease,
                                patient,
                                Set.of(TABLE),
                                source,
                                1000,
                                1L << 20,
                                control,
                                output::add);
        assertThat(result.rows().get(TABLE)).isEqualTo(rows.size());
        return output;
    }

    @Test
    void shouldReproduceTheImporterCompositeIdentity_withAnExactPhysicalRowDigest()
            throws Exception {
        prepare();
        var receipt = read().getFirst();
        assertThat(receipt.id()).isEqualTo("91_41");
        assertThat(receipt.state().columns()).isEqualTo(columns);
        var expected = CdsNativeRowDigest.hasher(columns);
        for (String value : List.of("41", "91")) {
            byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            expected.append(
                    false,
                    bytes.length,
                    HexFormat.of()
                            .formatHex(
                                    io.github.carlos_emr.carlos.utility.CdsImportKeys.digest()
                                            .digest(bytes)));
        }
        assertThat(receipt.digest()).isEqualTo(expected.finish());
        CdsExportCaseManagementProjectionEvidence.verify(rows.getFirst(), receipt);
    }

    @Test
    void shouldKeepTwoNotesLinkedToTheSameIssue_asDistinctCompositeReceipts() throws Exception {
        rows.add(row("92", "41"));
        prepare();
        assertThat(read())
                .extracting(CdsExportNativeRowReceiptDto::id)
                .containsExactly("91_41", "92_41");
    }

    @Test
    void shouldKeepTwoIssuesLinkedToTheSameNote_asDistinctCompositeReceipts() throws Exception {
        rows.add(row("91", "42"));
        prepare();
        assertThat(read())
                .extracting(CdsExportNativeRowReceiptDto::id)
                .containsExactly("91_41", "91_42");
    }

    @Test
    void shouldRefuseAKeyForDifferentParents_beforeReturningAnyReceipt() throws Exception {
        rows.set(
                0,
                new CdsExportCapturedTextRowDto(
                        TABLE, row("92", "41").recordKey(), rows.getFirst().fields()));
        prepare();
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseANoncanonicalNoteParent_withoutInventingAnAssociationIdentity()
            throws Exception {
        rows.set(
                0,
                new CdsExportCapturedTextRowDto(
                        TABLE, rows.getFirst().recordKey(), Map.of("note_id", "091", "id", "41")));
        prepare();
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseANoncanonicalIssueParent_withoutInventingAnAssociationIdentity()
            throws Exception {
        rows.set(
                0,
                new CdsExportCapturedTextRowDto(
                        TABLE, rows.getFirst().recordKey(), Map.of("note_id", "91", "id", "041")));
        prepare();
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
    }

    @Test
    void shouldKeepMissingDriverMetadata_unavailableRatherThanClaimingAHistoricalMatch()
            throws Exception {
        columns = List.of();
        prepare();
        var receipt = read().getFirst();
        assertThat(receipt.id()).isEqualTo("91_41");
        assertThat(receipt.digest()).isNull();
        assertThat(receipt.state()).isNull();
    }

    @Test
    void shouldPropagateFinalAuthorizationRevocation_afterTheCompositeCallback() throws Exception {
        prepare();
        doThrow(new SecurityException("CDS_EXPORT_ACCESS_CHANGED"))
                .when(captures)
                .authorizeSources(lease, patient, Set.of(TABLE), control);
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_ACCESS_CHANGED");
    }
}
