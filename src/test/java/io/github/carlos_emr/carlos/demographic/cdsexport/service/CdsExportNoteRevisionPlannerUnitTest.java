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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNoteRevisionPlannerUnitTest {
    private static final String UUID_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            UUID_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            HASH = "a".repeat(64),
            ERROR = "CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID";

    private CdsExportNoteRevisionDto row(long id, String uuid, String time) {
        return new CdsExportNoteRevisionDto(id, 27, uuid, "2020-01-01 01:02:03", time, "0", "0");
    }

    @Test
    void shouldChooseTheOwningChartsMaximumId_withoutUsingTimestampOrder() throws Exception {
        var old = row(91, UUID_A, "2026-11-07 12:13:14");
        var current = row(92, UUID_A, "2020-01-01 01:02:03");
        var result = plan(List.of(current, old));
        assertThat(result.qualifiedLineages()).isTrue();
        assertThat(result.chains()).hasSize(1);
        assertThat(result.chains().getFirst().revisions()).containsExactly(91L, 92L);
        assertThat(result.chains().getFirst().currentNoteId()).isEqualTo(92);
    }

    @Test
    void shouldGroupOnlyCanonicalUuidCaseAliases_withoutChangingTheirEvidence() throws Exception {
        var result =
                plan(
                        List.of(
                                row(91, UUID_A.toUpperCase(Locale.ROOT), "2020-01-01 01:02:03"),
                                row(92, UUID_A, "2020-01-01 01:02:03")));
        assertThat(result.chains()).hasSize(1);
        assertThat(result.chains().getFirst().canonicalUuid()).isEqualTo(UUID_A);
    }

    @Test
    void shouldPreserveIndependentChains_andEveryRevisionIdentity() throws Exception {
        var result =
                plan(
                        List.of(
                                row(91, UUID_A, "2020-01-01 01:02:03"),
                                row(92, UUID_B, "2020-01-01 01:02:03")));
        assertThat(result.chains()).hasSize(2);
        assertThat(result.rows()).isEqualTo(2);
        assertThat(result.reviews()).isEmpty();
        assertThat(result.toString()).isEqualTo("CdsExportNoteRevisionPlanner.Result[protected]");
    }

    @Test
    void shouldRequireReviewForLegacyUuidValues_withoutInventingAChain() throws Exception {
        for (String uuid :
                new String[] {null, "", "1-1-1-1-1", "not-a-uuid", UUID_A.substring(1)}) {
            var result = plan(List.of(row(91, uuid, "2020-01-01 01:02:03")));
            assertThat(result.qualifiedLineages()).isFalse();
            assertThat(result.chains()).isEmpty();
            assertThat(result.reviews()).containsKey(91L);
        }
    }

    @Test
    void shouldQuarantineTheWholeChain_whenAnyRevisionHasUnknownFlags() throws Exception {
        var bad = new CdsExportNoteRevisionDto(92, 27, UUID_A, null, null, "2", "0");
        var result = plan(List.of(row(91, UUID_A, "2020-01-01 01:02:03"), bad));
        assertThat(result.chains()).isEmpty();
        assertThat(result.reviews()).containsOnlyKeys(91L, 92L);
    }

    @Test
    void shouldRefuseMetadataSubstitution_evenWhenTheRecordIdMatches() {
        var original = row(91, UUID_A, "2020-01-01 01:02:03");
        var changed = row(91, UUID_B, "2020-01-01 01:02:03");
        assertThatThrownBy(
                        () ->
                                new CdsExportNoteRevisionPlanner()
                                        .plan(
                                                27,
                                                HASH,
                                                scopes(List.of(original)),
                                                List.of(changed),
                                                witnesses(List.of(original)),
                                                () -> {}))
                .hasMessage(ERROR);
    }

    @Test
    void shouldRequireExactlyTheCapturedNoteUniverse_withoutDuplicatesOrMissingRows() {
        var row = row(91, UUID_A, "2020-01-01 01:02:03");
        assertThatThrownBy(
                        () ->
                                new CdsExportNoteRevisionPlanner()
                                        .plan(
                                                27,
                                                HASH,
                                                scopes(List.of(row)),
                                                List.of(),
                                                witnesses(List.of(row)),
                                                () -> {}))
                .hasMessage(ERROR);
        assertThatThrownBy(
                        () ->
                                new CdsExportNoteRevisionPlanner()
                                        .plan(
                                                27,
                                                HASH,
                                                scopes(List.of(row)),
                                                List.of(row, row),
                                                witnesses(List.of(row)),
                                                () -> {}))
                .hasMessage(ERROR);
        assertThatThrownBy(
                        () ->
                                new CdsExportNoteRevisionPlanner()
                                        .plan(
                                                27,
                                                HASH,
                                                scopes(List.of(row)),
                                                List.of(row),
                                                Map.of(),
                                                () -> {}))
                .hasMessage(ERROR);
    }

    @Test
    void shouldRefuseAnotherPatient_withoutReusingItsOtherwiseMatchingWitness() {
        var row = new CdsExportNoteRevisionDto(91, 28, UUID_A, null, null, "0", "0");
        assertThatThrownBy(
                        () ->
                                new CdsExportNoteRevisionPlanner()
                                        .plan(
                                                27,
                                                HASH,
                                                List.of(
                                                        new CdsExportNoteAccessDto(
                                                                91, 27, "", "1")),
                                                List.of(row),
                                                witnesses(List.of(row)),
                                                () -> {}))
                .hasMessage(ERROR);
    }

    @Test
    void shouldPreserveAnEmptyQualifiedInventory_withoutCreatingANote() throws Exception {
        var result = plan(List.of());
        assertThat(result.rows()).isZero();
        assertThat(result.chains()).isEmpty();
        assertThat(result.reviews()).isEmpty();
    }

    @Test
    void shouldPropagateCancellation_beforeCompletingTheInventory() {
        assertThatThrownBy(
                        () ->
                                new CdsExportNoteRevisionPlanner()
                                        .plan(
                                                27,
                                                HASH,
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                () -> {
                                                    throw new java.util.concurrent
                                                            .CancellationException(
                                                            "Owned cancellation");
                                                }))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
    }

    private CdsExportNoteRevisionPlanner.Result plan(List<CdsExportNoteRevisionDto> rows)
            throws Exception {
        return new CdsExportNoteRevisionPlanner()
                .plan(27, HASH, scopes(rows), rows, witnesses(rows), () -> {});
    }

    private List<CdsExportNoteAccessDto> scopes(List<CdsExportNoteRevisionDto> rows) {
        return rows.stream()
                .map(row -> new CdsExportNoteAccessDto(row.noteId(), row.patient(), "", "1"))
                .toList();
    }

    private Map<Long, CdsExportNativeRowReceiptDto> witnesses(List<CdsExportNoteRevisionDto> rows) {
        var result = new HashMap<Long, CdsExportNativeRowReceiptDto>();
        for (var row : rows) {
            var values = new HashMap<String, String>();
            for (String column :
                    io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                            .CASE_NOTES
                            .columns()) values.put(column, null);
            values.put("note_id", Long.toString(row.noteId()));
            values.put("demographic_no", Integer.toString(row.patient()));
            values.put("uuid", row.uuid());
            values.put("observation_date", row.observationDate());
            values.put("update_date", row.updateDate());
            values.put("archived", row.archived());
            values.put("signed", row.signed());
            values.put("note", "Owned clinical text whose full-row witness remains present");
            var columns = new ArrayList<CdsNativeRowDigest.Column>();
            var cells = new ArrayList<CdsNativeRowState.Cell>();
            for (String column :
                    io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                            .CASE_NOTES
                            .columns()) {
                columns.add(new CdsNativeRowDigest.Column(column, "VARCHAR", 255, 0));
                var value = values.get(column);
                byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
                cells.add(
                        new CdsNativeRowState.Cell(
                                value == null,
                                value == null ? 0 : bytes.length,
                                value == null
                                        ? null
                                        : HexFormat.of()
                                                .formatHex(
                                                        io.github.carlos_emr.carlos.utility
                                                                .CdsImportKeys.digest()
                                                                .digest(bytes))));
            }
            var state =
                    new CdsNativeRowState(
                            "casemgmt_note", Long.toString(row.noteId()), columns, cells);
            result.put(
                    row.noteId(),
                    new CdsExportNativeRowReceiptDto(
                            "casemgmt_note",
                            Long.toString(row.noteId()),
                            CdsExportPatientTableSourceReader.key(row.noteId()),
                            state.digest(),
                            state));
        }
        return result;
    }
}
