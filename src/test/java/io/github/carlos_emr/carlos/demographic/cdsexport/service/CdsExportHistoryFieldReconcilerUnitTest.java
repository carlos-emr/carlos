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
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportHistoryFieldReconcilerUnitTest {
    private static final String HASH = "a".repeat(64),
            RUN = "11111111-1111-4111-8111-111111111111",
            UUID_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private CdsCategory category;
    private CdsHistoryNativeDto prepared;
    private CdsExportHistoryFieldReconciler.Row original, current;
    private final List<CdsExportHistoryFieldReconciler.Row> extensions = new ArrayList<>();
    private final List<CdsExportCapturedTextRowDto> links = new ArrayList<>();
    private final Map<String, CdsExportHistoryFieldReconciler.Row> patientIssues = new HashMap<>(),
            definitions = new HashMap<>();
    private final Map<String, CdsNativeRowState> baseline = new HashMap<>();
    private final List<CdsExportRetainedIndexDto.Reference> references = new ArrayList<>();
    private final List<CdsExportRetainedFieldDto> source = new ArrayList<>();

    @BeforeEach
    void setup() {
        fixture(CdsCategory.CLINICAL_NOTES);
    }

    private void fixture(CdsCategory selected) {
        category = selected;
        extensions.clear();
        links.clear();
        patientIssues.clear();
        definitions.clear();
        baseline.clear();
        references.clear();
        source.clear();
        String code =
                switch (selected) {
                    case FAMILY_HISTORY -> "FamHistory";
                    case PERSONAL_HISTORY -> "SocHistory";
                    default -> null;
                };
        var temporal = CdsTemporal.parse(null, null, "2020-01-01", null, "Date");
        prepared =
                new CdsHistoryNativeDto(
                        "Original <note> & exact text",
                        code,
                        1577836800000L,
                        null,
                        null,
                        temporal,
                        selected == CdsCategory.FAMILY_HISTORY ? "StartDate" : "EventDateTime",
                        List.of());
        var fields = blanks(Plan.CASE_NOTES);
        fields.put("note_id", "91");
        fields.put("demographic_no", "27");
        fields.put("note", prepared.content());
        fields.put("provider_no", "");
        fields.put("history", "");
        fields.put("signing_provider_no", "");
        fields.put("signed", "0");
        fields.put("archived", "0");
        fields.put("program_no", "");
        fields.put("reporter_caisi_role", "1");
        fields.put("reporter_program_team", "0");
        fields.put("position", "0");
        fields.put("include_issue_innote", "0");
        fields.put("appointmentNo", "0");
        fields.put("update_date", "2026-11-07 01:02:03");
        fields.put("observation_date", "2020-01-01 00:00:00");
        fields.put("uuid", UUID_A);
        fields.put("password", "Protected credential never emitted");
        original = current = row(Plan.CASE_NOTES, "91", fields);
        keep(original);
        int id = 101;
        for (var entry :
                new LinkedHashMap<>(
                                Map.of(
                                        "cdsDatePrecision",
                                        "FULL_DATE",
                                        "cdsDateOriginal",
                                        "2020-01-01",
                                        "cdsDateField",
                                        prepared.observationField()))
                        .entrySet()) {
            var extension = blanks(Plan.CASE_NOTE_EXT);
            extension.put("id", Integer.toString(id));
            extension.put("note_id", "91");
            extension.put("key_val", entry.getKey());
            extension.put("value", entry.getValue());
            var row = row(Plan.CASE_NOTE_EXT, Integer.toString(id++), extension);
            extensions.add(row);
            keep(row);
        }
        if (code != null) {
            var issue = blanks(Plan.CASE_ISSUES);
            issue.put("id", "41");
            issue.put("demographic_no", "27");
            issue.put("issue_id", "51");
            issue.put("type", "general");
            issue.put("acute", "0");
            issue.put("certain", "0");
            issue.put("major", "0");
            issue.put("resolved", "0");
            issue.put("update_date", "2026-11-07 01:02:03");
            var issueRow = row(Plan.CASE_ISSUES, "41", issue);
            patientIssues.put("41", issueRow);
            keep(issueRow);
            var definition = blanks(Plan.ISSUE_CATALOGUE);
            definition.put("issue_id", "51");
            definition.put("code", code);
            definition.put("description", "Local category label");
            definition.put("role", "doctor");
            definition.put("sortOrderId", "0");
            definition.put("update_date", "2026-11-07 01:02:03");
            definitions.put("51", row(Plan.ISSUE_CATALOGUE, "51", definition));
            var association =
                    row(Plan.CASE_NOTE_ISSUE_LINK, "91_41", Map.of("id", "41", "note_id", "91"));
            keep(association);
            links.add(association.value());
        }
        String body =
                selected == CdsCategory.FAMILY_HISTORY
                        ? "ProblemDiagnosisProcedureDescription"
                        : "MyClinicalNotesContent";
        var type =
                CdsWireSchema.schemaModel()
                        .element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", selected.wireName())
                        .child("cds", body);
        source.add(
                new CdsExportRetainedFieldDto(
                        HASH,
                        RUN,
                        HASH,
                        HASH,
                        selected,
                        "/OmdCds[1]/PatientRecord[1]/"
                                + selected.wireName()
                                + "[1]/"
                                + body
                                + "[1]",
                        1,
                        "cds",
                        "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                        null,
                        prepared.content(),
                        prepared.content(),
                        null));
    }

    @Test
    void shouldPreserveAnUnchangedSource_andAccountForEveryCurrentField() throws Exception {
        var result = choose();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).isEmpty();
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(rows(), result.fields(), 1000, () -> {});
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "casemgmt_note",
                                                current.value().recordKey(),
                                                "password"))
                                .rule())
                .isEqualTo("SECRET_NEVER_EXPORTED");
    }

    @Test
    void shouldUseOnlyTheEditedCurrentNarrative_preservingOtherOriginalSourceMeaning()
            throws Exception {
        editNote("note", "Edited & complete current text");
        var result = choose();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).hasSize(1);
        var change = result.changes().getFirst();
        assertThat(change.relativePath()).isEqualTo("MyClinicalNotesContent[1]");
        assertThat(change.expectedPriorFields()).containsExactly(HASH);
        var xml = mock(CdsExportXmlComposer.ContentWriter.class);
        change.content().write(xml);
        verify(xml).text("Edited & complete current text");
    }

    @Test
    void shouldAcceptExactClonedDateHints_onANewCurrentRevision() throws Exception {
        var fields = new LinkedHashMap<>(current.value().fields());
        fields.put("note_id", "92");
        fields.put("note", "A genuine new revision");
        current = row(Plan.CASE_NOTES, "92", fields);
        for (int i = 0; i < extensions.size(); i++) {
            var value = new LinkedHashMap<>(extensions.get(i).value().fields());
            value.put("id", Integer.toString(201 + i));
            value.put("note_id", "92");
            extensions.set(i, row(Plan.CASE_NOTE_EXT, Integer.toString(201 + i), value));
        }
        var result = choose();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).hasSize(1);
    }

    @Test
    void shouldRetainSourceDates_whenOnlyTheNativeUpdateTimeChanges() throws Exception {
        editNote("update_date", "2026-11-08 01:02:03");
        assertThat(choose().representable()).isTrue();
        assertThat(choose().changes()).isEmpty();
    }

    @Test
    void shouldRequireReviewForChangedObservation_withoutReplayingTheOldDate() throws Exception {
        editNote("observation_date", "2021-02-03 00:00:00");
        var result = choose();
        assertThat(result.representable()).isFalse();
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "casemgmt_note",
                                                current.value().recordKey(),
                                                "observation_date"))
                                .outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldRejectChangedDateHintValues_independentlyOfTheNoteBody() throws Exception {
        int index = index("cdsDateOriginal");
        var changed = new LinkedHashMap<>(extensions.get(index).value().fields());
        changed.put("value", "2021-01-01");
        extensions.set(index, row(Plan.CASE_NOTE_EXT, changed.get("id"), changed));
        assertThat(choose().representable()).isFalse();
    }

    @Test
    void shouldRejectNewClinicalExtensions_withoutSilentlyDroppingThem() throws Exception {
        var fields = blanks(Plan.CASE_NOTE_EXT);
        fields.put("id", "104");
        fields.put("note_id", "91");
        fields.put("key_val", "NewClinicalValue");
        fields.put("value", "Clinical content");
        extensions.add(row(Plan.CASE_NOTE_EXT, "104", fields));
        assertThat(choose().representable()).isFalse();
    }

    @Test
    void shouldRequireReviewForDeletedDateHints_withoutGuessingPrecision() {
        extensions.removeFirst();
        assertThatThrownBy(this::choose)
                .hasMessage("CDS_EXPORT_HISTORY_DATE_EXTENSION_DELETION_REQUIRES_REVIEW");
    }

    @Test
    void shouldRequireReviewForDuplicateDateKeys_withoutChoosingAnArbitraryRow() throws Exception {
        var fields = new LinkedHashMap<>(extensions.getFirst().value().fields());
        fields.put("id", "104");
        extensions.add(row(Plan.CASE_NOTE_EXT, "104", fields));
        assertThat(choose().representable()).isFalse();
    }

    @Test
    void shouldRequireAuthorAndSignatureReconciliation_afterEitherChanges() throws Exception {
        editNote("provider_no", "123456");
        assertThat(choose().representable()).isFalse();
        current = original;
        editNote("signed", "1");
        assertThat(choose().representable()).isFalse();
    }

    @Test
    void shouldKeepZeroEncounterDefaults_andBlockPopulatedClinicalTiming() throws Exception {
        editNote("hourOfEncounterTime", "0");
        assertThat(choose().representable()).isTrue();
        editNote("hourOfEncounterTime", "1");
        assertThat(choose().representable()).isFalse();
    }

    @Test
    void shouldRequireAnAuditCarrier_forPopulatedHistory() throws Exception {
        editNote("history", "Prior revision clinical text");
        assertThat(choose().representable()).isFalse();
    }

    @Test
    void shouldRejectClinicalTeamsAndArchivedContext_withoutInventingWireMeaning()
            throws Exception {
        editNote("reporter_program_team", "13");
        assertThat(choose().representable()).isFalse();
        current = original;
        editNote("archived", "1");
        assertThat(choose().representable()).isFalse();
    }

    @Test
    void shouldKeepUnchangedHistoryCategoryAssociations_andAccountForTheirFields()
            throws Exception {
        fixture(CdsCategory.FAMILY_HISTORY);
        var result = choose();
        assertThat(result.representable()).isTrue();
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(rows(), result.fields(), 1000, () -> {});
    }

    @Test
    void shouldIgnoreOnlyAnIssueUpdateTimestamp_whileRetainingItsClinicalComparison()
            throws Exception {
        fixture(CdsCategory.FAMILY_HISTORY);
        var fields = new LinkedHashMap<>(patientIssues.get("41").value().fields());
        fields.put("update_date", "2026-11-08 01:02:03");
        patientIssues.put("41", row(Plan.CASE_ISSUES, "41", fields));
        assertThat(choose().representable()).isTrue();
        fields.put("resolved", "1");
        patientIssues.put("41", row(Plan.CASE_ISSUES, "41", fields));
        assertThatThrownBy(this::choose)
                .hasMessage("CDS_EXPORT_HISTORY_ISSUE_EDIT_REQUIRES_RECONCILIATION");
    }

    @Test
    void shouldRejectAChangedCatalogueCode_evenWhenThePatientIssueRowIsUnchanged() {
        fixture(CdsCategory.FAMILY_HISTORY);
        var fields = new LinkedHashMap<>(definitions.get("51").value().fields());
        fields.put("code", "Concerns");
        definitions.put("51", row(Plan.ISSUE_CATALOGUE, "51", fields));
        assertThatThrownBy(this::choose)
                .hasMessage("CDS_EXPORT_HISTORY_ISSUE_EDIT_REQUIRES_RECONCILIATION");
    }

    @Test
    void shouldRejectUnexpectedIssueLinks_insteadOfDroppingTheirMeaning() {
        links.add(
                new CdsExportCapturedTextRowDto(
                        "casemgmt_issue_notes", "unexpected", Map.of("id", "41", "note_id", "91")));
        assertThatThrownBy(this::choose)
                .hasMessage("CDS_EXPORT_HISTORY_ISSUE_ASSOCIATION_REQUIRES_REVIEW");
    }

    @Test
    void shouldRejectNarrativeTruncation_forBoundedHistoryWireFields() throws Exception {
        fixture(CdsCategory.FAMILY_HISTORY);
        editNote("note", "x".repeat(251));
        assertThat(choose().representable()).isFalse();
    }

    @Test
    void shouldRefuseASubstitutedProjection_whenItsFullRowWitnessDoesNotMatch() {
        var fields = new LinkedHashMap<>(current.value().fields());
        fields.put("note", "Substituted");
        current =
                new CdsExportHistoryFieldReconciler.Row(
                        new CdsExportCapturedTextRowDto(
                                "casemgmt_note", current.value().recordKey(), fields),
                        current.witness());
        assertThatThrownBy(this::choose)
                .hasMessage("CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID");
    }

    @Test
    void shouldRefuseAnOriginalProjection_whenItsSealedPreparationDoesNotMatch() {
        prepared =
                new CdsHistoryNativeDto(
                        "Not the original projection",
                        null,
                        prepared.observationMillis(),
                        null,
                        null,
                        prepared.observationDate(),
                        prepared.observationField(),
                        List.of());
        assertThatThrownBy(this::choose)
                .hasMessage("CDS_EXPORT_HISTORY_RECONCILIATION_SCOPE_INVALID");
    }

    private CdsExportHistoryFieldReconciler.Result choose() throws Exception {
        return new CdsExportHistoryFieldReconciler()
                .choose(
                        27,
                        category,
                        prepared,
                        original.witness().state(),
                        new CdsExportHistoryFieldReconciler.Current(
                                current, extensions, links, patientIssues, definitions),
                        references,
                        baseline,
                        source,
                        List.of(),
                        Set.of(HASH),
                        HASH,
                        RUN + ":" + HASH,
                        () -> {});
    }

    private List<CdsExportCapturedTextRowDto> rows() {
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(current.value());
        extensions.forEach(row -> rows.add(row.value()));
        links.forEach(rows::add);
        patientIssues.values().forEach(row -> rows.add(row.value()));
        definitions.values().forEach(row -> rows.add(row.value()));
        return rows;
    }

    private int index(String key) {
        for (int i = 0; i < extensions.size(); i++)
            if (key.equals(extensions.get(i).value().fields().get("key_val"))) return i;
        throw new AssertionError("Missing owned extension");
    }

    private void editNote(String key, String value) {
        var fields = new LinkedHashMap<>(current.value().fields());
        fields.put(key, value);
        current = row(Plan.CASE_NOTES, fields.get("note_id"), fields);
    }

    private void keep(CdsExportHistoryFieldReconciler.Row row) {
        var state = row.witness().state();
        baseline.put(state.table() + "/" + state.id(), state);
        references.add(
                new CdsExportRetainedIndexDto.Reference(state.table(), state.id(), state.digest()));
    }

    private Map<String, String> blanks(Plan plan) {
        var fields = new LinkedHashMap<String, String>();
        for (String name : plan.columns()) fields.put(name, null);
        return fields;
    }

    private CdsExportHistoryFieldReconciler.Row row(
            Plan plan, String id, Map<String, String> fields) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var field : fields.entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(field.getKey(), "VARCHAR", 255, 0));
            var value = field.getValue();
            byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
            cells.add(
                    new CdsNativeRowState.Cell(
                            value == null,
                            value == null ? 0 : bytes.length,
                            value == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(CdsImportKeys.digest().digest(bytes))));
        }
        var state = new CdsNativeRowState(plan.table(), id, columns, cells);
        String recordKey =
                plan == Plan.CASE_NOTE_ISSUE_LINK
                        ? CdsExportPatientTableSourceReader.key(
                                        Long.parseLong(fields.get("note_id")))
                                + CdsExportPatientTableSourceReader.key(
                                        Long.parseLong(fields.get("id")))
                        : CdsExportPatientTableSourceReader.key(Long.parseLong(id));
        return new CdsExportHistoryFieldReconciler.Row(
                new CdsExportCapturedTextRowDto(plan.table(), recordKey, fields),
                new CdsExportNativeRowReceiptDto(
                        plan.table(), id, recordKey, state.digest(), state));
    }
}
