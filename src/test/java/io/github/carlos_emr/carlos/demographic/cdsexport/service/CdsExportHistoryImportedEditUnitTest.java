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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.providers;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Import, edit and export of a CDS-imported history item (B5, B10). The imported revision has the
 * rows {@code CdsHistoryPersister.write} stores (the narrative, no author, the category tag issue
 * and its link, and the cdsDatePrecision/cdsDateOriginal/cdsDateField rows of a dated item) beside
 * the item's retained source elements. The CARLOS edit is a new revision as {@code
 * CaseManagementManagerImpl} saves it: same uuid, the editing provider, the previous revision's
 * extension and issue rows copied, and whatever the clinician changed. The export runs the strict
 * import guard and the native reconciler as {@code CdsExportHistoryFieldReconciler.reconcile}
 * does, and the result is rendered through the receiving schema.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportHistoryImportedEditUnitTest {
    private static final String HASH = "a".repeat(64),
            RUN = "11111111-1111-4111-8111-111111111111",
            UUID_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");

    private CdsCategory category;
    private CdsHistoryNativeDto prepared;
    private CdsExportHistoryFieldReconciler.Current imported;
    private final Map<String, CdsNativeRowState> baseline = new HashMap<>();
    private final List<CdsExportRetainedIndexDto.Reference> references = new ArrayList<>();
    private final List<CdsExportRetainedFieldDto> retained = new ArrayList<>();
    private final Map<String, String> retainedValues = new HashMap<>();
    private final CdsExportRetainedSectionWriter.Values values =
            new CdsExportRetainedSectionWriter.Values() {
                public Reader text(CdsExportRetainedFieldDto field) {
                    return new StringReader(retainedValues.get(field.id()));
                }

                public InputStream binary(CdsExportRetainedFieldDto field) throws IOException {
                    throw new IOException("UNEXPECTED_BINARY");
                }
            };

    /** An imported item, with the native rows the CARLOS importer writes for it. */
    private void imported(CdsCategory selected, String tag, boolean dated) {
        category = selected;
        var temporal = dated ? CdsTemporal.parse(null, null, "2020-01-01", null, "Date") : null;
        String field =
                !dated
                        ? null
                        : selected == CdsCategory.CLINICAL_NOTES ? "EventDateTime" : "StartDate";
        prepared =
                new CdsHistoryNativeDto(
                        "Imported narrative",
                        tag,
                        dated ? 1577854800000L : null,
                        null,
                        null,
                        temporal,
                        field,
                        List.of());
        var note = new LinkedHashMap<String, String>();
        Plan.CASE_NOTES.columns().forEach(column -> note.put(column, null));
        note.putAll(
                Map.of(
                        "note_id", "91",
                        "demographic_no", "27",
                        "note", prepared.content(),
                        "provider_no", "",
                        "signing_provider_no", "",
                        "signed", "0",
                        "archived", "0",
                        "update_date", "2026-11-01 09:00:00",
                        "uuid", UUID_A,
                        "history", ""));
        note.put("program_no", "");
        note.put("reporter_caisi_role", "1");
        note.put("reporter_program_team", "0");
        note.put("observation_date", dated ? "2020-01-01 00:00:00" : null);
        var noteRow = keep(row(Plan.CASE_NOTES, "91", note));
        var extensions = new ArrayList<CdsExportHistoryFieldReconciler.Row>();
        if (dated) {
            int id = 101;
            for (var entry :
                    List.of(
                            Map.entry("cdsDatePrecision", "FULL_DATE"),
                            Map.entry("cdsDateOriginal", "2020-01-01"),
                            Map.entry("cdsDateField", field)))
                extensions.add(keep(extension(id++, 91, entry.getKey(), entry.getValue(), null)));
        }
        var links = new ArrayList<CdsExportCapturedTextRowDto>();
        var issues = new HashMap<String, CdsExportHistoryFieldReconciler.Row>();
        var definitions = new HashMap<String, CdsExportHistoryFieldReconciler.Row>();
        if (tag != null) {
            issues.put("41", keep(issue(41, 51)));
            definitions.put("51", definition(51, tag, "system"));
            links.add(keep(link(91, 41)).value());
        }
        imported =
                new CdsExportHistoryFieldReconciler.Current(
                        noteRow, extensions, links, issues, definitions);
    }

    /** The CARLOS revision of the imported item: copied rows, the editor, and the edit applied. */
    private CdsExportHistoryFieldReconciler.Current edited(
            long id,
            Map<String, String> changes,
            List<CdsExportHistoryFieldReconciler.Row> addedExtensions,
            Map<CdsExportHistoryFieldReconciler.Row, CdsExportHistoryFieldReconciler.Row>
                    addedIssues) {
        var note = new LinkedHashMap<>(imported.note().value().fields());
        note.put("note_id", Long.toString(id));
        note.put("provider_no", "17");
        note.put("update_date", "2026-11-10 10:00:00");
        note.putAll(changes);
        var extensions = new ArrayList<CdsExportHistoryFieldReconciler.Row>();
        int next = (int) id * 10;
        for (var copied : imported.extensions()) {
            var fields = copied.value().fields();
            extensions.add(
                    extension(
                            next++,
                            id,
                            fields.get("key_val"),
                            fields.get("value"),
                            fields.get("date_value")));
        }
        extensions.addAll(addedExtensions);
        var links = new ArrayList<CdsExportCapturedTextRowDto>();
        var issues = new HashMap<>(imported.patientIssues());
        var definitions = new HashMap<>(imported.definitions());
        for (var link : imported.links())
            links.add(link(id, Long.parseLong(link.fields().get("id"))).value());
        for (var added : addedIssues.entrySet()) {
            String issueId = added.getKey().value().fields().get("id");
            issues.put(issueId, added.getKey());
            definitions.put(added.getValue().value().fields().get("issue_id"), added.getValue());
            links.add(link(id, Long.parseLong(issueId)).value());
        }
        return new CdsExportHistoryFieldReconciler.Current(
                row(Plan.CASE_NOTES, Long.toString(id), note),
                extensions,
                links,
                issues,
                definitions);
    }

    /** Exports the edited item and returns its rendered occurrence. */
    private String export(CdsExportHistoryFieldReconciler.Current current) throws Exception {
        var nativeHistory =
                new CdsExportNativeHistoryMapper()
                        .map(27, category, List.of(imported, current), providers(), ZONE);
        var ids = new HashSet<String>();
        retained.forEach(field -> ids.add(field.id()));
        var result =
                new CdsExportHistoryFieldReconciler()
                        .reconcileNative(
                                27,
                                category,
                                RUN + ":" + HASH,
                                HASH,
                                prepared,
                                imported.note().witness().state(),
                                current,
                                references,
                                baseline,
                                retained,
                                new CdsExportHistoryRetainedSource.Result(ids, Map.of(), List.of()),
                                nativeHistory,
                                values,
                                () -> {});
        assertThat(result.representable()).isTrue();
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(nativeHistory.rows(), result.fields(), 10000, () -> {});
        var output =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(retained, values, result.changes(), () -> {});
        var accounted = new HashSet<>(output.retainedFields());
        accounted.addAll(output.replacedFields());
        assertThat(accounted).isEqualTo(ids);
        return new String(render(category, output.content()), StandardCharsets.UTF_8);
    }

    @Test
    void shouldExportAnImportedCppItemArchivedInCarlos_asAStatedArchivedItem() throws Exception {
        // B10: the CPP editor's Archive button saves a revision with archived = 1, stamped with
        // the editing provider's program team when the patient's admission has one.
        imported(CdsCategory.FAMILY_HISTORY, "FamHistory", true);
        source("ProblemDiagnosisProcedureDescription[1]", "Imported narrative");
        source("StartDate[1]/FullDate[1]", "2020-01-01");
        String xml =
                export(
                        edited(
                                92,
                                Map.of("archived", "1", "reporter_program_team", "13"),
                                List.of(),
                                Map.of()));
        assertThat(xml)
                .contains(
                        CdsExportNativeHistoryMapper.ARCHIVED_ITEM_STATEMENT,
                        CdsNativeExtensionContract.HISTORY_ARCHIVED,
                        "Imported narrative",
                        "2020-01-01");
    }

    @Test
    void shouldExportAnEditedImportedPersonalHistory_withTheCurrentNarrative() throws Exception {
        // B10: PersonalHistory has no standard narrative element; the edit travels encoded.
        imported(CdsCategory.PERSONAL_HISTORY, "SocHistory", false);
        source("ResidualInfo[1]/DataElement[1]/Name[1]", CdsNativeExtensionContract.HISTORY_NARRATIVE);
        source("ResidualInfo[1]/DataElement[1]/DataType[1]", "string");
        source(
                "ResidualInfo[1]/DataElement[1]/Content[1]",
                CdsNativeExtensionContract.encodeNarrative("Imported narrative"));
        String narrative = "Lives alone.\n  Walks   daily";
        String xml = export(edited(92, Map.of("note", narrative), List.of(), Map.of()));
        assertThat(xml).contains(CdsNativeExtensionContract.encodeNarrative(narrative));
        // The carrier is replaced, not added beside the imported one.
        assertThat(xml.split(CdsNativeExtensionContract.HISTORY_NARRATIVE, -1)).hasSize(2);
    }

    @Test
    void shouldRewriteThePlainTextCopy_whenAnImportedPersonalHistoryIsEdited() throws Exception {
        // An item imported from a CARLOS export carries the carrier and its plain-text copy. The
        // edit (a new narrative and a Social History start date) rewrites both; a stale copy
        // beside the current carrier would show other EMRs the imported text.
        imported(CdsCategory.PERSONAL_HISTORY, "SocHistory", false);
        source("ResidualInfo[1]/DataElement[1]/Name[1]", CdsNativeExtensionContract.HISTORY_NARRATIVE);
        source("ResidualInfo[1]/DataElement[1]/DataType[1]", "string");
        source(
                "ResidualInfo[1]/DataElement[1]/Content[1]",
                CdsNativeExtensionContract.encodeNarrative("Imported narrative"));
        source(
                "ResidualInfo[1]/DataElement[2]/Name[1]",
                CdsNativeExtensionContract.HISTORY_NARRATIVE_TEXT);
        source("ResidualInfo[1]/DataElement[2]/DataType[1]", "string");
        source("ResidualInfo[1]/DataElement[2]/Content[1]", "Imported narrative");
        String narrative = "Lives alone.\n  Walks   daily";
        String xml =
                export(
                        edited(
                                92,
                                Map.of("note", narrative),
                                List.of(
                                        extension(
                                                301,
                                                92,
                                                "Start Date",
                                                "YYYY",
                                                "2010-01-01 00:00:00")),
                                Map.of()));
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        assertThat(
                        CdsExportNativeHistoryMapperUnitTest.residual(
                                bytes, CdsNativeExtensionContract.HISTORY_NARRATIVE))
                .isEqualTo(CdsNativeExtensionContract.encodeNarrative(narrative));
        assertThat(
                        CdsExportNativeHistoryMapperUnitTest.residual(
                                bytes, CdsNativeExtensionContract.HISTORY_NARRATIVE_TEXT))
                .isEqualTo("Lives alone. Walks daily Start Date: 2010");
        // PersonalHistory has no Notes; the date is not written as one.
        assertThat(xml).doesNotContain("<Notes>");
    }

    @Test
    void shouldExportADiagnosisAssignedToAnImportedCppItem_asItsCode() throws Exception {
        // B5: "Assign issues" links an ICD-9 diagnosis to the imported family history.
        imported(CdsCategory.FAMILY_HISTORY, "FamHistory", true);
        source("ProblemDiagnosisProcedureDescription[1]", "Imported narrative");
        String xml =
                export(
                        edited(
                                92,
                                Map.of(),
                                List.of(),
                                Map.of(issue(43, 52), definition(52, "250", "icd9"))));
        assertThat(xml).contains("DiagnosisProcedureCode", ">250<", ">icd9<", ">250 entry<");
    }

    @Test
    void shouldKeepTheImportedCode_andNameTheCarlosDiagnosisInNotes() throws Exception {
        imported(CdsCategory.FAMILY_HISTORY, "FamHistory", true);
        source("ProblemDiagnosisProcedureDescription[1]", "Imported narrative");
        source("DiagnosisProcedureCode[1]/StandardCodingSystem[1]", "ICD9");
        source("DiagnosisProcedureCode[1]/StandardCode[1]", "4939");
        source("DiagnosisProcedureCode[1]/StandardCodeDescription[1]", "Asthma");
        source("Notes[1]", "Imported notes");
        String xml =
                export(
                        edited(
                                92,
                                Map.of(),
                                List.of(),
                                Map.of(issue(43, 52), definition(52, "250", "icd9"))));
        assertThat(xml)
                .contains("4939", "Imported notes\n\nCoded diagnoses:\nicd9 250 250 entry")
                .doesNotContain(">250<");
    }

    @Test
    void shouldExportAnEncounterNoteWithAnAssignedIssue_andItsChangedObservationDate()
            throws Exception {
        // B5: an issue assigned to an imported encounter note and its observation date edited.
        imported(CdsCategory.CLINICAL_NOTES, null, true);
        source("MyClinicalNotesContent[1]", "Imported narrative");
        source("EventDateTime[1]/FullDate[1]", "2020-01-01");
        String xml =
                export(
                        edited(
                                92,
                                Map.of("observation_date", "2021-03-04 00:00:00"),
                                List.of(),
                                Map.of(issue(43, 52), definition(52, "250", "icd9"))));
        assertThat(xml)
                .contains("Imported narrative", "definition.code", "250 entry")
                .contains("2021-03-04")
                .doesNotContain("2020-01-01</");
    }

    @Test
    void shouldExportCppExtensionsAddedToAnImportedItem_asElementsOrNotes() throws Exception {
        // B5: the CPP editor adds a start date and a free-text age at onset.
        imported(CdsCategory.FAMILY_HISTORY, "FamHistory", true);
        source("ProblemDiagnosisProcedureDescription[1]", "Imported narrative");
        source("StartDate[1]/FullDate[1]", "2020-01-01");
        String xml =
                export(
                        edited(
                                92,
                                Map.of(),
                                List.of(
                                        extension(301, 92, "Start Date", "", "2021-05-01 00:00:00"),
                                        extension(302, 92, "Age at Onset", "childhood", null)),
                                Map.of()));
        assertThat(xml).contains("2021-05-01", "Age at Onset:\nchildhood");
    }

    @Test
    void shouldExportAnImportedItemRefiledIntoAnotherCppSection_carryingItsImportedElements()
            throws Exception {
        // B5 re-filing: the clinician moves an imported family history item to Medical History
        // (the CPP editor removes the FamHistory tag, adds MedHistory and notes the removal).
        // The item is exported under its current section with its current values, and every
        // element of its imported FamilyHistory record travels exactly in its native context
        // (REFILED_IMPORTED_ITEM_SOURCE_SECTION_CARRIED); the imported section no longer claims it.
        imported(CdsCategory.FAMILY_HISTORY, "FamHistory", true);
        source("ProblemDiagnosisProcedureDescription[1]", "Imported narrative");
        source("StartDate[1]/YearOnly[1]", "2019");
        source("Relationship[1]", "Mother");
        source("Notes[1]", "Imported\nnotes");
        var refiled =
                edited(92, Map.of("note", "Imported narrative\nRemoved: Family History"), List.of(), Map.of());
        var moved =
                new CdsExportHistoryFieldReconciler.Current(
                        refiled.note(),
                        refiled.extensions(),
                        List.of(link(92, 44).value()),
                        Map.of("44", issue(44, 53)),
                        Map.of("53", definition(53, "MedHistory", "system")));
        assertThat(CdsExportNativeHistoryMapper.category(moved))
                .isEqualTo(CdsCategory.PAST_HEALTH);
        // The import shifted its dates; the carried element takes the shift it would have had.
        var carried =
                CdsExportHistoryFieldReconciler.refiledSource(
                        CdsCategory.FAMILY_HISTORY,
                        retained,
                        Map.of(
                                "StartDate[1]",
                                new CdsExportDateDto(CdsExportDateDto.Precision.YEAR, "2020")),
                        values,
                        () -> {});
        var mapped =
                new CdsExportNativeHistoryMapper()
                        .map(
                                27,
                                CdsCategory.PAST_HEALTH,
                                List.of(imported, moved),
                                providers(),
                                ZONE,
                                program -> false,
                                carried);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(mapped.rows(), mapped.fields(), 10000, () -> {});
        assertThat(mapped.value().text())
                .containsEntry(
                        "PastHealthProblemDescriptionOrProcedures",
                        "Imported narrative\nRemoved: Family History");
        var context =
                com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .readTree(mapped.value().metadata())
                        .get("refiledImportedSource");
        assertThat(context.get("rule").asText())
                .isEqualTo("REFILED_IMPORTED_ITEM_SOURCE_SECTION_CARRIED");
        assertThat(context.get("section").asText()).isEqualTo("FamilyHistory");
        var elements = new LinkedHashMap<String, String>();
        context.get("elements")
                .forEach(e -> elements.put(e.get("path").asText(), e.get("value").asText()));
        assertThat(elements)
                .containsExactly(
                        Map.entry("ProblemDiagnosisProcedureDescription[1]", "Imported narrative"),
                        Map.entry("StartDate[1]/YearOnly[1]", "2020"),
                        Map.entry("Relationship[1]", "Mother"),
                        Map.entry("Notes[1]", "Imported\nnotes"));
        String xml =
                new String(render(mapped.value()), StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("PastHealthProblemDescriptionOrProcedures", "refiledImportedSource")
                .doesNotContain("<FamilyHistory");
    }

    private void source(String path, String value) throws Exception {
        var type =
                CdsWireSchema.schemaModel()
                        .element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", category.wireName());
        String namespace = "cds";
        for (String part : path.split("/")) {
            String name = part.substring(0, part.indexOf('['));
            var expanded =
                    type.childNames().stream()
                            .filter(n -> n.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            namespace = expanded.namespace();
            type = type.child(namespace, name);
        }
        String id = CdsImportKeys.hash(path);
        retainedValues.put(id, value);
        retained.add(
                new CdsExportRetainedFieldDto(
                        id,
                        RUN,
                        HASH,
                        HASH,
                        category,
                        "/OmdCds[1]/PatientRecord[1]/" + category.wireName() + "[1]/" + path,
                        1,
                        namespace,
                        "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                        null,
                        value,
                        value,
                        null));
    }

    private CdsExportHistoryFieldReconciler.Row keep(CdsExportHistoryFieldReconciler.Row row) {
        var state = row.witness().state();
        baseline.put(state.table() + "/" + state.id(), state);
        references.add(
                new CdsExportRetainedIndexDto.Reference(state.table(), state.id(), state.digest()));
        return row;
    }

    private static CdsExportHistoryFieldReconciler.Row extension(
            long id, long note, String key, String value, String date) {
        var fields = new LinkedHashMap<String, String>();
        Plan.CASE_NOTE_EXT.columns().forEach(column -> fields.put(column, null));
        fields.put("id", Long.toString(id));
        fields.put("note_id", Long.toString(note));
        fields.put("key_val", key);
        fields.put("value", value);
        fields.put("date_value", date);
        return row(Plan.CASE_NOTE_EXT, Long.toString(id), fields);
    }

    /** A patient issue as the CARLOS importer (41) or the issue editor (43) saves it. */
    private static CdsExportHistoryFieldReconciler.Row issue(long id, long issueId) {
        var fields = new LinkedHashMap<String, String>();
        Plan.CASE_ISSUES.columns().forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "id", Long.toString(id),
                        "demographic_no", "27",
                        "issue_id", Long.toString(issueId),
                        "type", "general",
                        "acute", "0",
                        "certain", "0",
                        "major", "0",
                        "resolved", "0",
                        "update_date", "2026-11-01 09:00:00"));
        return row(Plan.CASE_ISSUES, Long.toString(id), fields);
    }

    private static CdsExportHistoryFieldReconciler.Row definition(
            long issueId, String code, String type) {
        var fields = new LinkedHashMap<String, String>();
        Plan.ISSUE_CATALOGUE.columns().forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "issue_id", Long.toString(issueId),
                        "code", code,
                        "description", code + " entry",
                        "role", "doctor",
                        "type", type,
                        "sortOrderId", "0",
                        "update_date", "2026-01-01 00:00:00"));
        return row(Plan.ISSUE_CATALOGUE, Long.toString(issueId), fields);
    }

    private static CdsExportHistoryFieldReconciler.Row link(long note, long issue) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("id", Long.toString(issue));
        fields.put("note_id", Long.toString(note));
        return row(Plan.CASE_NOTE_ISSUE_LINK, note + "_" + issue, fields);
    }

    private static CdsExportHistoryFieldReconciler.Row row(
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
                new CdsExportCapturedTextRowDto(plan.table(), recordKey, new LinkedHashMap<>(fields)),
                new CdsExportNativeRowReceiptDto(
                        plan.table(), id, recordKey, state.digest(), state));
    }
}
