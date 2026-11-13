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

import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportImportedReviewAppendUnitTest.field;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportImportedReviewAppendUnitTest.inline;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.CheckedReference;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Import, then an edit through the document edit page, then export. The page
 * ({@code AddEditDocument2Action.editDocument} with {@code EDocUtil.editDocumentSQL}) rewrites a
 * fixed set of columns; the user's edit supersedes the imported value, a first review becomes
 * one more ReportReviewed, and every superseded imported value travels in the report's edit
 * context. An unedited imported document is not touched.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportImportedDocumentEditUnitTest {
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final String RUN = "11111111-1111-4111-8111-111111111111";
    private static final String KEY = "b".repeat(64);
    private static final String ADDRESS = RUN + ":" + KEY;
    private static final String FILE = "cds-" + KEY + "-referenced-" + "a".repeat(64) + ".pdf";

    private static final CdsReportNativeDto.Variant VARIANT =
            new CdsReportNativeDto.Variant(
                    "referenced", "pdf", "application/pdf", "a".repeat(64), 12, false, List.of());
    private static final CdsReportNativeDto PREPARED =
            new CdsReportNativeDto(
                    Map.of(
                            "Class", "Consultant Report",
                            "SubClass", "Cardiology consult",
                            "SourceFacility", "General Hospital",
                            "NativeSourceAuthor", "Dr Sending"),
                    List.of(VARIANT),
                    CdsTemporalDto.unknown(),
                    CdsTemporalDto.unknown(),
                    CdsTemporalDto.unknown());

    /** The document row the CDS import wrote for the report. */
    private static Map<String, String> imported() {
        var row = new TreeMap<String, String>();
        Plan.DOCUMENTS.columns().forEach(column -> row.put(column, null));
        row.put("document_no", "91");
        row.put("doctype", "CDS");
        row.put("status", "A");
        row.put("docfilename", FILE);
        row.put("docClass", "Consultant Report");
        row.put("docSubClass", "Cardiology consult");
        row.put("docdesc", "Cardiology consult");
        row.put("source", "Dr Sending");
        row.put("sourceFacility", "General Hospital");
        row.put("contenttype", "application/pdf");
        row.put("cds_content_precision", "UNKNOWN");
        row.put("doccreator", "999998");
        row.put("responsible", "");
        row.put("reviewer", "");
        row.put("abnormal", "0");
        row.put("public1", "0");
        row.put("restrictToProgram", "0");
        row.put("observationdate", "2025-01-02");
        row.put("updatedatetime", "2025-03-04 10:00:00");
        return row;
    }

    private static CdsExportCapturedTextRowDto document(Map<String, String> values) {
        return new CdsExportCapturedTextRowDto(
                "document", CdsExportPatientTableSourceReader.key(91), values);
    }

    private static CdsExportCapturedTextRowDto link() {
        var values = new TreeMap<String, String>();
        Plan.DOCUMENT_LINKS.columns().forEach(column -> values.put(column, null));
        values.put("document_no", "91");
        values.put("module", "demographic");
        values.put("module_id", "27");
        values.put("status", "A");
        return new CdsExportCapturedTextRowDto(
                "ctl_document", CdsExportPatientTableSourceReader.key(91), values);
    }

    private static CdsNativeRowState state(Map<String, String> values) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : new TreeMap<>(values).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
            String value = entry.getValue();
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    value.getBytes(StandardCharsets.UTF_8).length,
                                    HexFormat.of()
                                            .formatHex(
                                                    CdsImportKeys.digest()
                                                            .digest(
                                                                    value.getBytes(
                                                                            StandardCharsets.UTF_8)))));
        }
        return new CdsNativeRowState("document", "91", columns, cells);
    }

    /** The retained report the import kept, reviewed by the sending EMR. */
    private static List<CdsExportRetainedFieldDto> retained(boolean notes) {
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(field("Reports[1]/Format[1]", "Binary"));
        fields.add(field("Reports[1]/FileExtensionAndVersion[1]", "pdf"));
        fields.add(field("Reports[1]/FilePath[1]", "Reports/" + "c".repeat(64) + ".pdf"));
        fields.add(field("Reports[1]/Class[1]", "Consultant Report"));
        fields.add(field("Reports[1]/SubClass[1]", "Cardiology consult"));
        fields.add(field("Reports[1]/EventDateTime[1]/FullDate[1]", "2025-01-02"));
        fields.add(field("Reports[1]/SourceAuthorPhysician[1]/AuthorFreeText[1]", "Dr Sending"));
        fields.add(field("Reports[1]/SourceFacility[1]", "General Hospital"));
        fields.add(field("Reports[1]/ReportReviewed[1]/Name[1]/FirstName[1]", "Sending"));
        fields.add(field("Reports[1]/ReportReviewed[1]/Name[1]/LastName[1]", "Reviewer"));
        fields.add(
                field("Reports[1]/ReportReviewed[1]/DateTimeReportReviewed[1]/FullDate[1]", "2025-01-03"));
        if (notes) fields.add(field("Reports[1]/Notes[1]", "Sender's own note"));
        return fields;
    }

    private static Map<String, CdsExportCapturedTextRowDto> providers() {
        return Map.of(
                "101",
                CdsExportLaboratoryReviewerMappingUnitTest.provider("101", "Ada", "Carlos", "123456"));
    }

    private record Exported(
            String xml,
            CdsExportImportedDocumentEditReconciler.Result edit,
            CdsExportRetainedSectionWriter.Prepared prepared,
            CdsExportReportOccurrenceService.ImportedReviews supplementary,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments) {}

    /** Reconciles the edited row as the Reports assembler does and writes the retained report. */
    private static Exported export(Map<String, String> current, boolean notes) throws Exception {
        var fields = retained(notes);
        var row = document(current);
        var baseline = state(imported());
        var now = state(current);
        var edited = CdsNativeRowComparison.compare(baseline, now).changedColumns();
        // The import's own binding still holds for every column the page did not change ...
        CdsExportReportOccurrenceService.bind(row, link(), PREPARED, VARIANT, FILE, 27, edited);
        var edit =
                new CdsExportImportedDocumentEditReconciler()
                        .reconcile(
                                row,
                                baseline,
                                now,
                                CdsExportImportedDocumentEditReconciler.imported(
                                        PREPARED, VARIANT, FILE),
                                fields,
                                inline(),
                                providers(),
                                ZONE);
        var assessments = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>(List.of(row));
        rows.addAll(edit.rows());
        var changes = new ArrayList<>(edit.changes());
        var supplementary =
                CdsExportReportOccurrenceService.importedReviews(
                        List.of(),
                        providers(),
                        ZONE,
                        fields,
                        changes,
                        assessments,
                        rows,
                        new CdsExportReportOccurrenceService.Edits(
                                edit.reviewers(), List.of(edit.context()), edit.fields()));
        // Every changed column and the reviewer's provider row has an explicit decision.
        for (var key : edit.fields().keySet())
            assertThat(assessments.get(key).outcome())
                    .isNotEqualTo(CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
        var prepared =
                new CdsExportRetainedSectionWriter().prepareChanges(fields, inline(), changes, () -> {});
        var all = new HashSet<>(prepared.retainedFields());
        all.addAll(prepared.replacedFields());
        assertThat(all)
                .containsExactlyInAnyOrderElementsOf(
                        fields.stream().map(CdsExportRetainedFieldDto::id).toList());
        String xml =
                new String(
                        render(CdsCategory.REPORTS, prepared.content()), StandardCharsets.UTF_8);
        return new Exported(xml, edit, prepared, supplementary, assessments);
    }

    @Test
    void shouldAppendTheEditPageReview_andCarryEveryOtherChangedColumn() throws Exception {
        // "Reviewed" on the edit page: the page stores the reviewer and time, posts "" for the
        // HTML the row did not have, and a type from its list ("CDS" is not offered).
        var current = new TreeMap<>(imported());
        current.put("reviewer", "101");
        current.put("reviewdatetime", "2026-03-08 11:45:00");
        current.put("docxml", "");
        current.put("doctype", "consult");
        // The import's binding alone refuses the edited row, as before this change.
        assertThatThrownBy(
                        () ->
                                CdsExportReportOccurrenceService.bind(
                                        document(current), link(), PREPARED, VARIANT, FILE, 27))
                .isInstanceOf(java.io.IOException.class);
        var exported = export(current, false);
        assertThat(exported.supplementary()).isNull();
        assertThat(exported.prepared().replacedFields()).isEmpty();
        String xml = exported.xml();
        // The sending EMR's reviewer, then CARLOS's review on its date.
        assertThat(xml.indexOf("Sending")).isLessThan(xml.indexOf("<cdsd:FirstName>Ada"));
        assertThat(xml)
                .contains(
                        "<ReviewingOHIPPhysicianId>123456</ReviewingOHIPPhysicianId>",
                        "<cdsd:FullDate>2026-03-08</cdsd:FullDate>",
                        "<Class>Consultant Report</Class>",
                        "<SubClass>Cardiology consult</SubClass>",
                        "[CARLOS reviews and edits recorded after the import]",
                        "\"doctype\":{\"imported\":\"CDS\",\"current\":\"consult\"",
                        "2026-03-08 11:45:00");
        var decisions = exported.edit().fields();
        assertThat(
                        decisions.get(
                                        new CdsExportNativeFieldKeyDto(
                                                "document",
                                                CdsExportPatientTableSourceReader.key(91),
                                                "docxml"))
                                .rule())
                .isEqualTo(CdsExportImportedDocumentEditReconciler.EMPTY_FORM_VALUE);
        assertThat(
                        decisions.get(
                                        new CdsExportNativeFieldKeyDto(
                                                "document",
                                                CdsExportPatientTableSourceReader.key(91),
                                                "reviewer"))
                                .destination())
                .isEqualTo("Reports/ReportReviewed/Name");
    }

    @Test
    void shouldExportTheEditedDescriptionSubclassAndDate_andCarryTheSupersededImportedValues()
            throws Exception {
        // "Update" on the edit page: the user changed the description, subclass and observation
        // date; the page also clears the (empty) review and stamps the update time.
        var current = new TreeMap<>(imported());
        current.put("docdesc", "Cardiology follow-up letter");
        current.put("docSubClass", "Cardiology letter");
        current.put("observationdate", "2025-02-03");
        current.put("updatedatetime", "2026-03-09 08:00:00");
        current.put("reviewer", null);
        var exported = export(current, false);
        String xml = exported.xml();
        assertThat(xml)
                .contains(
                        "<SubClass>Cardiology letter</SubClass>",
                        "<cdsd:FullDate>2025-02-03</cdsd:FullDate>",
                        // The description has no element: current and imported in the context.
                        "\"docdesc\":{\"imported\":\"Cardiology consult\",\"current\":\"Cardiology follow-up letter\"",
                        // The superseded imported elements, exactly.
                        "\"supersededElements\"",
                        "2025-01-02")
                .doesNotContain("<SubClass>Cardiology consult</SubClass>");
        assertThat(exported.prepared().replacedFields())
                .containsExactlyInAnyOrder(
                        field("Reports[1]/SubClass[1]", "").id(),
                        field("Reports[1]/EventDateTime[1]/FullDate[1]", "").id());
        assertThat(exported.edit().reviewers()).isEmpty();
        // An imported report with its own Notes keeps them: the context is a supplementary record.
        var noted = export(current, true);
        assertThat(noted.supplementary()).isNotNull();
        assertThat(noted.supplementary().text()).contains("Cardiology follow-up letter");
        assertThat(noted.xml()).contains("Sender's own note").doesNotContain("Cardiology follow-up letter");
    }

    @Test
    void shouldLeaveAnUneditedImportedDocumentAlone_andHoldAChangeTheEditPageNeverMakes()
            throws Exception {
        var reference =
                new CdsExportRetainedIndexDto.Reference("document", "91", state(imported()).digest());
        // Unchanged unit: nothing to reconcile, the report exports exactly as before.
        var unchanged =
                new CdsExportRetainedReceiptVerificationDto(
                        "a".repeat(64),
                        Map.of(ADDRESS, List.of(new CheckedReference(reference, reference.digest(), Reason.UNCHANGED_ROW))),
                        Set.of(ADDRESS),
                        Set.of());
        assertThat(
                        CdsExportImportedDocumentEditReconciler.editedDocuments(
                                ADDRESS, index(null), unchanged))
                .isEmpty();
        // A replaced file is not an edit-page metadata edit: the patient is still held.
        var replaced = new TreeMap<>(imported());
        replaced.put("docfilename", "replacement.pdf");
        assertThatThrownBy(
                        () ->
                                CdsExportImportedDocumentEditReconciler.editedDocuments(
                                        ADDRESS, index(null), changed(reference, replaced)))
                .isInstanceOf(CdsExportReviewException.class);
        // An edit-page edit with the import's sealed baseline is reconciled from that baseline.
        var reviewed = new TreeMap<>(imported());
        reviewed.put("reviewer", "101");
        reviewed.put("reviewdatetime", "2026-03-08 11:45:00");
        var baselines =
                CdsExportImportedDocumentEditReconciler.editedDocuments(
                        ADDRESS, index(state(imported())), changed(reference, reviewed));
        assertThat(baselines).containsOnlyKeys("91");
        assertThat(baselines.get("91").digest()).isEqualTo(reference.digest());
        // Without a sealed baseline the changed columns cannot be proved: still held.
        assertThatThrownBy(
                        () ->
                                CdsExportImportedDocumentEditReconciler.editedDocuments(
                                        ADDRESS, index(null), changed(reference, reviewed)))
                .isInstanceOf(CdsExportReviewException.class);
    }

    private static CdsExportRetainedReceiptVerificationDto changed(
            CdsExportRetainedIndexDto.Reference reference, Map<String, String> current) {
        var now = state(current);
        return new CdsExportRetainedReceiptVerificationDto(
                "a".repeat(64),
                Map.of(
                        ADDRESS,
                        List.of(
                                new CheckedReference(
                                        reference,
                                        now.digest(),
                                        Reason.CHANGED_ROW,
                                        CdsNativeRowComparison.compare(state(imported()), now),
                                        null))),
                Set.of(),
                Set.of(ADDRESS));
    }

    private static CdsExportRetainedIndexDto index(CdsNativeRowState baseline) throws Exception {
        String references =
                "{\"protocol\":2,\"refs\":[{\"table\":\"document\",\"id\":\"91\",\"digest\":\""
                        + state(imported()).digest()
                        + "\"}]}";
        String digest =
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(references.getBytes(StandardCharsets.UTF_8)));
        var values = new HashMap<String, String>();
        values.put("run_id", RUN);
        values.put("unit_key", KEY);
        values.put("category", "Reports");
        values.put("target_references", references);
        values.put("target_digest", digest);
        if (baseline != null)
            values.put(
                    "native_baseline",
                    CdsNativeBaselineCodec.encode(references, digest, List.of(baseline)));
        String id = CdsImportKeys.hash(RUN, KEY);
        var unit = new CdsExportCapturedTextRowDto("cds_import_unit", id, values);
        return new CdsExportRetainedIndexDto(
                "a".repeat(64),
                1,
                4096,
                Map.of("cds_import_unit", Map.of(id, unit)),
                Set.of(ADDRESS),
                Map.of(
                        ADDRESS,
                        List.of(
                                new CdsExportRetainedIndexDto.Reference(
                                        "document", "91", state(imported()).digest()))),
                Map.of());
    }
}
