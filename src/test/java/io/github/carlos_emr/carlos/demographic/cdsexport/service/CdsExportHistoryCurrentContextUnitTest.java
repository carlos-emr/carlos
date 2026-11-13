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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportHistoryCurrentContextUnitTest {
    private static final String HASH = "a".repeat(64);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportNativeRowReceiptReader receipts =
            mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final Map<String, List<CdsExportCapturedTextRowDto>> rows = new TreeMap<>();
    private final List<CdsExportNativeRowReceiptDto> witnesses = new ArrayList<>();
    private final Map<String, CdsExportCapturedTableDto> tables = new TreeMap<>();
    private CdsExportPatientCaptureDto source;
    private CdsExportNativeCaptureDto capture;
    private List<CdsExportNoteAccessDto> scopes;

    @BeforeEach
    void setup() {
        when(patient.patient()).thenReturn(27);
        scopes = List.of(new CdsExportNoteAccessDto(91, 27, "", "1"));
        for (var plan :
                List.of(
                        Plan.CASE_NOTES,
                        Plan.CASE_NOTE_EXT,
                        Plan.CASE_ISSUES,
                        Plan.ISSUE_CATALOGUE)) rows.put(table(plan), new ArrayList<>());
        add(
                Plan.CASE_NOTES,
                91,
                Map.of(
                        "demographic_no",
                        "27",
                        "uuid",
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        "program_no",
                        "",
                        "reporter_caisi_role",
                        "1",
                        "note",
                        "Clinical text"));
        add(
                Plan.CASE_NOTE_EXT,
                101,
                Map.of("note_id", "91", "key_val", "clinicalMetadata", "value", "Exact value"));
        add(Plan.CASE_ISSUES, 41, Map.of("demographic_no", "27", "issue_id", "51"));
        add(Plan.ISSUE_CATALOGUE, 51, Map.of("code", "FamHistory"));
        rows.put("casemgmt_issue_notes", new ArrayList<>(List.of(link("91", "41"))));
    }

    private static String table(Plan plan) {
        return switch (plan) {
            case CASE_NOTES -> "casemgmt_note";
            case CASE_NOTE_EXT -> "casemgmt_note_ext";
            case CASE_NOTE_LINK -> "casemgmt_note_link";
            case CASE_ISSUES -> "casemgmt_issue";
            case ISSUE_CATALOGUE -> "issue";
            default -> throw new IllegalArgumentException();
        };
    }

    private void add(Plan plan, long id, Map<String, String> supplied) {
        var fields = new LinkedHashMap<String, String>();
        for (String name : plan.columns()) fields.put(name, null);
        fields.put(
                plan == Plan.CASE_NOTES
                        ? "note_id"
                        : plan == Plan.ISSUE_CATALOGUE ? "issue_id" : "id",
                Long.toString(id));
        fields.putAll(supplied);
        rows.get(table(plan))
                .add(
                        new CdsExportCapturedTextRowDto(
                                table(plan), CdsExportPatientTableSourceReader.key(id), fields));
    }

    private static CdsExportCapturedTextRowDto link(String note, String issue) {
        return new CdsExportCapturedTextRowDto(
                "casemgmt_issue_notes",
                CdsExportPatientTableSourceReader.key(Long.parseLong(note))
                        + CdsExportPatientTableSourceReader.key(Long.parseLong(issue)),
                Map.of("note_id", note, "id", issue));
    }

    private void edit(String table, String field, String value) {
        var row = rows.get(table).getFirst();
        var fields = new LinkedHashMap<>(row.fields());
        fields.put(field, value);
        rows.get(table).set(0, new CdsExportCapturedTextRowDto(table, row.recordKey(), fields));
    }

    private void prepare() throws Exception {
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        witnesses.clear();
        tables.clear();
        for (var entry : rows.entrySet()) {
            var fields = entry.getValue().getFirst().fields().keySet();
            var columns =
                    fields.stream()
                            .map(name -> new CdsNativeRowDigest.Column(name, "VARCHAR", 255, 0))
                            .toList();
            tables.put(
                    entry.getKey(),
                    new CdsExportCapturedTableDto(
                            fields,
                            entry.getValue().size(),
                            HASH,
                            columns,
                            entry.getKey().equals("casemgmt_note") ? scopes : List.of()));
            for (var row : entry.getValue()) {
                var cells = new ArrayList<CdsNativeRowState.Cell>();
                for (var column : columns) {
                    String value = row.fields().get(column.name());
                    byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
                    cells.add(
                            new CdsNativeRowState.Cell(
                                    value == null,
                                    value == null ? 0 : bytes.length,
                                    value == null
                                            ? null
                                            : HexFormat.of()
                                                    .formatHex(
                                                            CdsImportKeys.digest().digest(bytes))));
                }
                String id =
                        row.fields()
                                .get(
                                        entry.getKey().equals("casemgmt_note")
                                                ? "note_id"
                                                : entry.getKey().equals("issue")
                                                        ? "issue_id"
                                                        : "id");
                if (entry.getKey().equals("casemgmt_issue_notes"))
                    id = row.fields().get("note_id") + "_" + id;
                var state = new CdsNativeRowState(entry.getKey(), id, columns, cells);
                witnesses.add(
                        new CdsExportNativeRowReceiptDto(
                                entry.getKey(), id, row.recordKey(), state.digest(), state));
            }
        }
        capture = mock(CdsExportNativeCaptureDto.class);
        when(capture.tables()).thenReturn(tables);
        when(capture.values())
                .thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", HASH, 1));
        when(capture.index())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", HASH, 1));
        source =
                new CdsExportPatientCaptureDto(
                        capture,
                        new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", HASH, 1));
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        rows.keySet(),
                        1000,
                        1L << 20,
                        () -> {},
                        writer -> {
                            for (var entry : rows.entrySet()) {
                                writer.beginTable(
                                        entry.getKey(),
                                        tables.get(entry.getKey()).columns(),
                                        entry.getValue().size());
                                for (var row : entry.getValue())
                                    writer.row(
                                            row.recordKey(),
                                            out -> {
                                                for (var field : row.fields().entrySet())
                                                    out.text(field.getKey(), field.getValue());
                                            });
                                writer.endTable();
                            }
                        });
        when(captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            var expected =
                                    new TreeMap<String, CdsExportNativeCaptureReader.Table>();
                            tables.forEach(
                                    (name, table) ->
                                            expected.put(
                                                    name,
                                                    new CdsExportNativeCaptureReader.Table(
                                                            table.columns(), table.rows())));
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            new ByteArrayInputStream(index.toByteArray()),
                                            expected,
                                            1000,
                                            1L << 20,
                                            () -> {},
                                            call.getArgument(7));
                            return capture;
                        });
        when(receipts.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            for (var witness : witnesses)
                                call.<CdsExportNativeRowReceiptReader.Receipts>getArgument(7)
                                        .accept(witness);
                            var counts = new TreeMap<String, Long>();
                            tables.forEach((name, table) -> counts.put(name, table.rows()));
                            return new CdsExportNativeRowReceiptsDto(
                                    HASH,
                                    counts,
                                    counts.values().stream().mapToLong(Long::longValue).sum(),
                                    0,
                                    Set.of());
                        });
    }

    private CdsExportHistoryCurrentContext read(long memory) throws Exception {
        return CdsExportHistoryCurrentContext.read(
                captures,
                receipts,
                lease,
                patient,
                rows.keySet(),
                source,
                new CdsExportClinicalOccurrenceService.Bounds(
                        1000, 1L << 20, 100, memory, 100, 100),
                control);
    }

    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            UUID_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    private CdsExportRetainedIndexDto index(String manifest, String note, boolean duplicate) {
        var units = new HashMap<String, CdsExportCapturedTextRowDto>();
        var refs = new HashMap<String, List<CdsExportRetainedIndexDto.Reference>>();
        for (String unit : duplicate ? List.of(HASH, "c".repeat(64)) : List.of(HASH)) {
            String id = CdsImportKeys.hash(RUN, unit);
            units.put(
                    id,
                    new CdsExportCapturedTextRowDto(
                            "cds_import_unit",
                            id,
                            Map.of("run_id", RUN, "unit_key", unit, "category", "FamilyHistory")));
            refs.put(
                    RUN + ":" + unit,
                    List.of(new CdsExportRetainedIndexDto.Reference("casemgmt_note", note, HASH)));
        }
        return new CdsExportRetainedIndexDto(
                manifest,
                units.size(),
                1,
                Map.of("cds_import_unit", units),
                refs.keySet(),
                refs,
                Map.of());
    }

    private CdsExportNoteRevisionPlanner.Result inventory(long... ids) {
        return new CdsExportNoteRevisionPlanner.Result(
                27,
                HASH,
                ids.length == 0
                        ? List.of()
                        : List.of(
                                new CdsExportNoteRevisionPlanner.Chain(
                                        UUID_A,
                                        java.util.Arrays.stream(ids).boxed().toList(),
                                        ids[ids.length - 1])),
                Map.of(),
                ids.length);
    }

    @Test
    void shouldPlanOneCurrentHistoryOccurrence_afterClosingItsPatientIssueAssociations()
            throws Exception {
        prepare();
        var result =
                CdsExportHistoryOccurrenceService.plan(
                        index(HASH, "91", false), inventory(91), read(1L << 20), () -> {});
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().category())
                .isEqualTo(
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                .FAMILY_HISTORY);
        assertThat(result.getFirst().noteId()).isEqualTo(91);
    }

    @Test
    void shouldPlanNativeHistory_withoutRequiringAnImportedParent()
            throws Exception {
        prepare();
        var index =
                new CdsExportRetainedIndexDto(HASH, 0, 0, Map.of(), Set.of(), Map.of(), Map.of());
        var current = read(1L << 20);
        var planned = CdsExportHistoryOccurrenceService.plan(index, inventory(91), current, () -> {});
        assertThat(planned).hasSize(1);
        assertThat(planned.getFirst().category()).isEqualTo(CdsCategory.FAMILY_HISTORY);
        assertThat(planned.getFirst().address()).isNull();
    }

    @Test
    void shouldGiveEachNoteItsOwnLinks_inLinkRowOrder() throws Exception {
        // A note may carry many links (a dump note gains one per revision of the note it
        // annotates). The context indexes rows by id, so their order is restored explicitly.
        rows.put("casemgmt_note_link", new ArrayList<>());
        var expected = new ArrayList<String>();
        for (int id = 301; id <= 340; id++) {
            add(
                    Plan.CASE_NOTE_LINK,
                    id,
                    Map.of("note_id", "91", "table_name", "1", "table_id", Integer.toString(id)));
            expected.add(Integer.toString(id));
        }
        prepare();
        var current = read(1L << 20).note(91);
        assertThat(current.noteLinks())
                .extracting(link -> link.value().fields().get("id"))
                .containsExactlyElementsOf(expected);
        assertThat(current.noteLinks().getFirst().witness().id()).isEqualTo("301");
        // The planner classifies the note by its issues, never by what it is attached to.
        var planned =
                CdsExportHistoryOccurrenceService.plan(
                        index(HASH, "91", false), inventory(91), read(1L << 20), () -> {});
        assertThat(planned).singleElement().satisfies(o -> assertThat(o.noteId()).isEqualTo(91));
    }

    @Test
    void shouldRefuseANoteLink_ofANoteOutsideTheCapture() throws Exception {
        rows.put("casemgmt_note_link", new ArrayList<>());
        add(
                Plan.CASE_NOTE_LINK,
                301,
                Map.of("note_id", "92", "table_name", "5", "table_id", "12"));
        prepare();
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRejectAnImportedNoteMissingFromTheCurrentInventory_insteadOfDroppingIt()
            throws Exception {
        prepare();
        var current = read(1L << 20);
        assertThatThrownBy(
                        () ->
                                CdsExportHistoryOccurrenceService.plan(
                                        index(HASH, "92", false), inventory(91), current, () -> {}))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRejectTwoSourceParentsForOneNativeOccurrence_withoutChoosingOneArbitrarily()
            throws Exception {
        prepare();
        var current = read(1L << 20);
        assertThatThrownBy(
                        () ->
                                CdsExportHistoryOccurrenceService.plan(
                                        index(HASH, "91", true), inventory(91), current, () -> {}))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldAccountForAnUnlinkedDiagnosis_asItsOwnProblemListOccurrence() throws Exception {
        add(Plan.CASE_ISSUES, 42, Map.of("demographic_no", "27", "issue_id", "52"));
        add(Plan.ISSUE_CATALOGUE, 52, Map.of("code", "250", "type", "icd9"));
        prepare();
        var current = read(1L << 20);
        var result = CdsExportHistoryOccurrenceService.plan(
                index(HASH, "91", false), inventory(91), current, () -> {});
        assertThat(result).hasSize(2);
        var issue = result.stream().filter(o -> "42".equals(o.issueId())).toList();
        assertThat(issue).hasSize(1);
        assertThat(issue.getFirst().category()).isEqualTo(CdsCategory.PROBLEM_LIST);
        assertThat(issue.getFirst().revisions()).isEmpty();
    }

    @Test
    void shouldHoldAnUnlinkedCppTag_insteadOfInventingAProblem() throws Exception {
        // This patient issue used to become a "Family History as part of cpp" ProblemList entry
        // coded system:FamHistory. A filing marker with no note has nothing clinical to carry.
        add(Plan.CASE_ISSUES, 42, Map.of("demographic_no", "27", "issue_id", "51"));
        prepare();
        var current = read(1L << 20);
        assertThatThrownBy(
                        () ->
                                CdsExportHistoryOccurrenceService.plan(
                                        index(HASH, "91", false), inventory(91), current, () -> {}))
                .hasMessage("CDS_EXPORT_NATIVE_SYSTEM_ISSUE_WITHOUT_NOTE_REQUIRES_REVIEW");
    }

    @Test
    void shouldLeaveLinkedSystemMarkersAndCppDiagnoses_toTheirNotes() throws Exception {
        // Note 91 is a Family History item that also carries an ICD-9 diagnosis (the relative's)
        // and an "Other Meds" system marker: none of them is a standalone patient problem.
        add(Plan.CASE_ISSUES, 42, Map.of("demographic_no", "27", "issue_id", "52"));
        add(Plan.ISSUE_CATALOGUE, 52, Map.of("code", "250", "type", "icd9"));
        add(Plan.CASE_ISSUES, 43, Map.of("demographic_no", "27", "issue_id", "65"));
        add(Plan.ISSUE_CATALOGUE, 65, Map.of("code", "OMeds", "type", "system"));
        rows.get("casemgmt_issue_notes").add(link("91", "42"));
        rows.get("casemgmt_issue_notes").add(link("91", "43"));
        prepare();
        var current = read(1L << 20);
        var planned =
                CdsExportHistoryOccurrenceService.plan(
                        index(HASH, "91", false), inventory(91), current, () -> {});
        assertThat(planned).hasSize(1);
        assertThat(planned.getFirst().category()).isEqualTo(CdsCategory.FAMILY_HISTORY);
        assertThat(planned.getFirst().issueId()).isNull();
    }

    @Test
    void shouldNotTurnATicklerMarker_intoAProblem() throws Exception {
        // A tickler note (DbTicklerAdd2Action) links the TicklerNote system issue; the note is a
        // clinical note and the marker travels in its native context only.
        edit("issue", "code", "TicklerNote");
        edit("issue", "type", "system");
        prepare();
        var index =
                new CdsExportRetainedIndexDto(HASH, 0, 0, Map.of(), Set.of(), Map.of(), Map.of());
        var planned =
                CdsExportHistoryOccurrenceService.plan(
                        index, inventory(91), read(1L << 20), () -> {});
        assertThat(planned).hasSize(1);
        assertThat(planned.getFirst().category()).isEqualTo(CdsCategory.CLINICAL_NOTES);
    }

    @Test
    void shouldCarryTheLinkedNoteRevisions_withAnEncounterDiagnosisOccurrence() throws Exception {
        edit("issue", "code", "250");
        edit("issue", "type", "icd9");
        prepare();
        var index =
                new CdsExportRetainedIndexDto(HASH, 0, 0, Map.of(), Set.of(), Map.of(), Map.of());
        var planned =
                CdsExportHistoryOccurrenceService.plan(
                        index, inventory(91), read(1L << 20), () -> {});
        assertThat(planned)
                .extracting(CdsExportHistoryOccurrenceService.Occurrence::category)
                .containsExactly(CdsCategory.PROBLEM_LIST, CdsCategory.CLINICAL_NOTES);
        // The issue's program is judged against these notes, whose access is checked with it.
        assertThat(planned.getFirst().issueId()).isEqualTo("41");
        assertThat(planned.getFirst().revisions()).containsExactly(91L);
    }

    @Test
    void shouldPlanOneCurrentOccurrence_withEveryOriginalAndEditedRevision()
            throws Exception {
        add(
                Plan.CASE_NOTES,
                92,
                Map.of(
                        "demographic_no",
                        "27",
                        "uuid",
                        UUID_A,
                        "program_no",
                        "",
                        "reporter_caisi_role",
                        "1",
                        "note",
                        "New current note"));
        scopes =
                List.of(
                        new CdsExportNoteAccessDto(91, 27, "", "1"),
                        new CdsExportNoteAccessDto(92, 27, "", "1"));
        prepare();
        var current = read(1L << 20);
        var result = CdsExportHistoryOccurrenceService.plan(
                index(HASH, "91", false), inventory(91, 92), current, () -> {});
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().noteId()).isEqualTo(92);
        assertThat(result.getFirst().revisions()).containsExactly(91L, 92L);
    }

    @Test
    void shouldRefuseAMissingInventoryNote_beforeClaimingCompleteClinicalCoverage()
            throws Exception {
        prepare();
        var current = read(1L << 20);
        assertThatThrownBy(
                        () ->
                                CdsExportHistoryOccurrenceService.plan(
                                        index(HASH, "91", false), inventory(), current, () -> {}))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAnotherSourceGeneration_beforeSelectingAnyHistoryUnit() throws Exception {
        prepare();
        var current = read(1L << 20);
        assertThatThrownBy(
                        () ->
                                CdsExportHistoryOccurrenceService.plan(
                                        index("b".repeat(64), "91", false),
                                        inventory(91),
                                        current,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldCloseAllFiveDomains_andAuthorizeAfterTheFinalCallback() throws Exception {
        prepare();
        var context = read(1L << 20);
        assertThat(context.manifest()).isEqualTo(HASH);
        assertThat(context.patient()).isEqualTo(27);
        assertThat(context.note(91).extensions()).hasSize(1);
        assertThat(context.note(91).links()).hasSize(1);
        assertThat(context.note(91).patientIssues()).hasSize(1);
        verify(captures, times(2))
                .authorizeCapturedSources(lease, patient, rows.keySet(), capture, control);
    }

    @Test
    void shouldRejectAMissingWitness_insteadOfAcceptingAnOmittedClinicalRow() throws Exception {
        prepare();
        witnesses.removeFirst();
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRejectAnExtraWitness_insteadOfAcceptingIncompleteTextReadback() throws Exception {
        prepare();
        var state = witnesses.getFirst();
        witnesses.add(
                new CdsExportNativeRowReceiptDto(
                        state.table(),
                        "999",
                        CdsExportPatientTableSourceReader.key(999),
                        new CdsNativeRowState(
                                        state.table(),
                                        "999",
                                        state.state().columns(),
                                        state.state().cells())
                                .digest(),
                        new CdsNativeRowState(
                                state.table(),
                                "999",
                                state.state().columns(),
                                state.state().cells())));
        assertThatThrownBy(() -> read(1L << 20)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectAnotherReceiptGeneration_beforeReadingClinicalText() throws Exception {
        prepare();
        doReturn(new CdsExportNativeRowReceiptsDto("b".repeat(64), Map.of(), 0, 0, Set.of()))
                .when(receipts)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
        verifyNoInteractions(captures);
    }

    @Test
    void shouldRejectAChangedReadbackGeneration_afterAuthenticatedEof() throws Exception {
        prepare();
        doReturn(mock(CdsExportNativeCaptureDto.class))
                .when(captures)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        assertThatThrownBy(() -> read(1L << 20)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseWitnessAllocation_whenTheMemoryBudgetIsExhausted() throws Exception {
        prepare();
        assertThatThrownBy(() -> read(1)).hasMessage("CDS_EXPORT_RESOURCE_LIMIT");
        verifyNoInteractions(captures);
    }

    @Test
    void shouldRejectCrossPatientNotes_evenWhenTheirFullRowWitnessMatches() throws Exception {
        edit("casemgmt_note", "demographic_no", "28");
        prepare();
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRejectCrossPatientIssues_evenWhenTheirFullRowWitnessMatches() throws Exception {
        edit("casemgmt_issue", "demographic_no", "28");
        prepare();
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRejectAnExtensionWithAnUncapturedParent_beforeReturningContext() throws Exception {
        edit("casemgmt_note_ext", "note_id", "92");
        prepare();
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRejectAnIssueWithAMissingCatalogueDefinition_beforeReturningContext()
            throws Exception {
        edit("casemgmt_issue", "issue_id", "52");
        prepare();
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRejectDuplicatePhysicalLinks_insteadOfDeduplicatingThem() throws Exception {
        rows.get("casemgmt_issue_notes").add(link("91", "41"));
        assertThatThrownBy(
                        () -> {
                            prepare();
                            read(1L << 20);
                        })
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectAnUnownedLinkParent_beforeReturningContext() throws Exception {
        rows.get("casemgmt_issue_notes").set(0, link("92", "41"));
        prepare();
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRejectAChangedProgramScope_withoutAuthorizingTheWrongProgram() throws Exception {
        scopes = List.of(new CdsExportNoteAccessDto(91, 27, "42", "1"));
        prepare();
        assertThatThrownBy(() -> read(1L << 20))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldPropagateRevocationAfterTheFinalRow_withoutReturningCompleteEvidence()
            throws Exception {
        prepare();
        doThrow(new SecurityException("CDS_EXPORT_ACCESS_CHANGED"))
                .when(captures)
                .authorizeCapturedSources(lease, patient, rows.keySet(), capture, control);
        assertThatThrownBy(() -> read(1L << 20)).hasMessage("CDS_EXPORT_ACCESS_CHANGED");
    }
}
