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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeHistoryMapperUnitTest {
    private static final String HASH = "a".repeat(64),
            UUID_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private final CdsExportNativeHistoryMapper mapper = new CdsExportNativeHistoryMapper();

    static CdsExportHistoryFieldReconciler.Row row(
            Plan plan, long id, Map<String, String> supplied) {
        var fields = new LinkedHashMap<String, String>();
        plan.columns().forEach(c -> fields.put(c, null));
        fields.put(
                plan == Plan.CASE_NOTES
                        ? "note_id"
                        : plan == Plan.ISSUE_CATALOGUE ? "issue_id" : "id",
                Long.toString(id));
        fields.putAll(supplied);
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var f : fields.entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(f.getKey(), "VARCHAR", 255, 0));
            byte[] bytes =
                    f.getValue() == null ? null : f.getValue().getBytes(StandardCharsets.UTF_8);
            cells.add(
                    new CdsNativeRowState.Cell(
                            bytes == null,
                            bytes == null ? 0 : bytes.length,
                            bytes == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(CdsImportKeys.digest().digest(bytes))));
        }
        var state = new CdsNativeRowState(plan.table(), Long.toString(id), columns, cells);
        String key = CdsExportPatientTableSourceReader.key(id);
        return new CdsExportHistoryFieldReconciler.Row(
                new CdsExportCapturedTextRowDto(plan.table(), key, fields),
                new CdsExportNativeRowReceiptDto(
                        plan.table(), Long.toString(id), key, state.digest(), state));
    }

    static CdsExportHistoryFieldReconciler.Current note(
            long id, String body, String tag, List<CdsExportHistoryFieldReconciler.Row> ext) {
        var fields =
                new LinkedHashMap<>(
                        Map.of(
                                "demographic_no",
                                "27",
                                "uuid",
                                UUID_A,
                                "note",
                                body,
                                "provider_no",
                                "17",
                                "signed",
                                "1",
                                "archived",
                                "0",
                                "signing_provider_no",
                                "17",
                                "update_date",
                                "2026-07-10 12:30:00",
                                "observation_date",
                                "2026-07-09 00:00:00"));
        fields.put("password", "NEVER EXPORTED CREDENTIAL");
        var current = row(Plan.CASE_NOTES, id, fields);
        if (tag == null)
            return new CdsExportHistoryFieldReconciler.Current(
                    current, ext, List.of(), Map.of(), Map.of());
        var issue =
                row(
                        Plan.CASE_ISSUES,
                        41,
                        Map.of("demographic_no", "27", "issue_id", "51", "resolved", "0"));
        var definition =
                row(Plan.ISSUE_CATALOGUE, 51, Map.of("code", tag, "description", "Chart category"));
        var link =
                new CdsExportCapturedTextRowDto(
                        "casemgmt_issue_notes",
                        CdsExportPatientTableSourceReader.key(id)
                                + CdsExportPatientTableSourceReader.key(41),
                        Map.of("id", "41", "note_id", Long.toString(id)));
        return new CdsExportHistoryFieldReconciler.Current(
                current, ext, List.of(link), Map.of("41", issue), Map.of("51", definition));
    }

    static Map<String, CdsExportCapturedTextRowDto> providers() {
        var fields = new LinkedHashMap<String, String>();
        CdsExportProviderRowDto.COLUMNS.forEach(c -> fields.put(c, null));
        fields.putAll(
                Map.of(
                        "provider_no",
                        "17",
                        "first_name",
                        "Zoë😀",
                        "last_name",
                        "Fixture",
                        "ohip_no",
                        "001234"));
        return Map.of(
                "17",
                new CdsExportCapturedTextRowDto(
                        "provider", CdsExportPublicProviderSourceReader.key("17"), fields));
    }

    private CdsExportNativeHistoryMapper.Result map(
            CdsCategory category, List<CdsExportHistoryFieldReconciler.Current> notes)
            throws Exception {
        var result = mapper.map(27, category, notes, providers(), ZONE);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(result.rows(), result.fields(), 10000, () -> {});
        return result;
    }

    static byte[] render(CdsExportNativeHistoryDto result) throws Exception {
        return render(
                result.category(), xml -> new CdsExportNativeHistoryXmlWriter().write(result, xml));
    }

    static byte[] render(CdsCategory category, CdsExportXmlComposer.Content content)
            throws Exception {
        var output = new ByteArrayOutputStream();
        var categories = Set.of(CdsCategory.DEMOGRAPHICS, category);
        var demo =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(HASH, CdsCategory.DEMOGRAPHICS, HASH),
                        xml -> {
                            xml.start("cds", "Names");
                            xml.start("cds_dt", "LegalName");
                            xml.attribute("", "namePurpose", "L");
                            for (var part :
                                    List.of(
                                            Map.entry("FirstName", "GIV"),
                                            Map.entry("LastName", "FAMC"))) {
                                xml.start("cds_dt", part.getKey());
                                xml.element("cds_dt", "Part", "Fixture");
                                xml.element("cds_dt", "PartType", part.getValue());
                                xml.end();
                            }
                            xml.end();
                            xml.end();
                            xml.element("cds", "DateOfBirth", "1980-01-02");
                            xml.element("cds", "Gender", "U");
                            xml.element("cds", "UniqueVendorIdSequence", "27");
                            xml.start("cds", "PersonStatusCode");
                            xml.element("cds", "PersonStatusAsEnum", "A");
                            xml.end();
                        });
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(HASH, category, HASH), content);
        new CdsExportXmlComposer()
                .compose(
                        output,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        categories,
                        List.of(demo, section).iterator(),
                        ignored -> {},
                        8L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        categories,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        return output.toByteArray();
    }

    @Test
    void shouldExportNativeOriginalAndAllEditsAsOneClinicalNote_withPublicAuthorsAndExactText()
            throws Exception {
        var result =
                map(
                        CdsCategory.CLINICAL_NOTES,
                        List.of(
                                note(91, "Original <text> & É😀", null, List.of()),
                                note(92, "Revised\r\nclinical text", null, List.of())));
        var bytes = render(result.value());
        var xml = new String(bytes, StandardCharsets.UTF_8);
        assertThat(xml)
                .contains(
                        "Original &lt;text&gt; &amp; É😀",
                        "Revised&#13;\nclinical text",
                        "001234",
                        "2026-07-10T12:30:00-04:00");
        assertThat(xml).doesNotContain("NEVER EXPORTED CREDENTIAL");
        // Each revision's body appears once, in its own text section, not again in the details.
        assertThat(xml.split("Original &lt;text&gt; &amp; É😀", -1)).hasSize(2);
        assertThat(result.value().metadata()).doesNotContain("\"narrative\"");
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        assertThat(document.getElementsByTagNameNS("cds", "ClinicalNotes").getLength())
                .isEqualTo(1);
        // Both revisions are by provider 17: one participant (this used to repeat it per
        // revision); each revision's author and time remain in the note details.
        assertThat(document.getElementsByTagNameNS("cds", "ParticipatingProviders").getLength())
                .isEqualTo(1);
        assertThat(result.value().metadata()).contains("2026-07-10 12:30:00");
        assertThat(result.rows().stream().filter(r -> r.table().equals("casemgmt_note")))
                .hasSize(2);
    }

    @Test
    void shouldExportAllSixNativeCppCategories_throughTheReceivingSchema() throws Exception {
        var tags =
                Map.of(
                        CdsCategory.PERSONAL_HISTORY,
                        "SocHistory",
                        CdsCategory.FAMILY_HISTORY,
                        "FamHistory",
                        CdsCategory.PAST_HEALTH,
                        "MedHistory",
                        CdsCategory.PROBLEM_LIST,
                        "Concerns",
                        CdsCategory.RISK_FACTORS,
                        "RiskFactors",
                        CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                        "Reminders");
        for (var entry : tags.entrySet()) {
            var result =
                    map(
                            entry.getKey(),
                            List.of(
                                    note(
                                            91,
                                            "Exact native narrative",
                                            entry.getValue(),
                                            List.of())));
            // The narrative is in an element or, escaped, in the token-typed residual details.
            assertThat(new String(render(result.value()), StandardCharsets.UTF_8))
                    .contains("CARLOS.NativeHistory.v1")
                    .containsAnyOf(
                            "Exact native narrative", "Exact\\u0020native\\u0020narrative");
        }
    }

    @Test
    void shouldKeepLongNarrativesInNotes_andAccountForTheActualDestination() throws Exception {
        var result =
                map(
                        CdsCategory.FAMILY_HISTORY,
                        List.of(note(91, "N".repeat(251), "FamHistory", List.of())));
        assertThat(result.value().text()).containsEntry("Notes", "N".repeat(251));
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "casemgmt_note",
                                                CdsExportPatientTableSourceReader.key(91),
                                                "note"))
                                .destination())
                .isEqualTo("Notes");
        // A CARLOS importer reads the item's text from the versioned carrier, not from Notes.
        assertThat(result.value().text()).containsEntry("NativeNarrative", "N".repeat(251));
        assertThat(new String(render(result.value()), StandardCharsets.UTF_8))
                .contains(CdsNativeExtensionContract.HISTORY_NARRATIVE)
                .doesNotContain("ProblemDiagnosisProcedureDescription");
    }

    @Test
    void shouldPreserveNativePartialDatePrecision_andRejectAnInconsistentAnchor() throws Exception {
        for (var precision : List.of("YYYY", "YYYY-MM", "")) {
            var ext =
                    row(
                            Plan.CASE_NOTE_EXT,
                            101,
                            Map.of(
                                    "note_id",
                                    "91",
                                    "key_val",
                                    "Start Date",
                                    "value",
                                    precision,
                                    "date_value",
                                    "2020-01-01 00:00:00"));
            var result =
                    map(
                            CdsCategory.FAMILY_HISTORY,
                            List.of(note(91, "Narrative", "FamHistory", List.of(ext))));
            assertThat(result.value().dates().get("StartDate").lexical())
                    .isEqualTo(
                            precision.equals("YYYY")
                                    ? "2020"
                                    : precision.equals("YYYY-MM") ? "2020-01" : "2020-01-01");
            render(result.value());
        }
        assertThatThrownBy(() -> CdsExportNativeHistoryMapper.partialDate("2020-02-01", "YYYY"))
                .hasMessage("CDS_EXPORT_NATIVE_HISTORY_DATE_PRECISION_CONFLICT");
    }

    @Test
    void shouldRefuseConflictingDuplicateExtensions_insteadOfSelectingAnArbitraryValue() {
        var one =
                row(
                        Plan.CASE_NOTE_EXT,
                        101,
                        Map.of("note_id", "91", "key_val", "Relationship", "value", "Mother"));
        var two =
                row(
                        Plan.CASE_NOTE_EXT,
                        102,
                        Map.of("note_id", "91", "key_val", "Relationship", "value", "Father"));
        assertThatThrownBy(
                        () ->
                                map(
                                        CdsCategory.FAMILY_HISTORY,
                                        List.of(
                                                note(
                                                        91,
                                                        "History",
                                                        "FamHistory",
                                                        List.of(one, two)))))
                .hasMessage("CDS_EXPORT_NATIVE_HISTORY_EXTENSION_CONFLICT");
    }

    @Test
    void shouldRefuseMissingAuthorLookup_ambiguousClockAndOversizedText() {
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        CdsCategory.CLINICAL_NOTES,
                                        List.of(note(91, "Native", null, List.of())),
                                        Map.of(),
                                        ZONE))
                .hasMessage("CDS_EXPORT_NATIVE_NOTE_PROVIDER_REQUIRED");
        assertThatThrownBy(() -> CdsExportNativeHistoryMapper.clock("2026-11-01 01:30:00", ZONE))
                .hasMessage("CDS_EXPORT_NATIVE_HISTORY_CLOCK_AMBIGUOUS");
        assertThatThrownBy(
                        () ->
                                map(
                                        CdsCategory.FAMILY_HISTORY,
                                        List.of(
                                                note(
                                                        91,
                                                        "x".repeat(32001),
                                                        "FamHistory",
                                                        List.of()))))
                .hasMessage("CDS_EXPORT_NATIVE_HISTORY_NARRATIVE_CAPACITY");
    }

    @Test
    void shouldExportAnUnlinkedNativeDiagnosis_withItsCodeDescriptionAndResolvedStatus()
            throws Exception {
        var issue =
                row(
                        Plan.CASE_ISSUES,
                        41,
                        Map.of(
                                "demographic_no",
                                "27",
                                "issue_id",
                                "51",
                                "resolved",
                                "1",
                                "acute",
                                "0",
                                "certain",
                                "1",
                                "major",
                                "0"));
        var definition =
                row(
                        Plan.ISSUE_CATALOGUE,
                        51,
                        Map.of(
                                "code",
                                "250.00",
                                "type",
                                "ICD-9",
                                "description",
                                "Diabetes mellitus"));
        var result = mapper.mapIssue(27, issue, definition);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(result.rows(), result.fields(), 10000, () -> {});
        String xml = new String(render(result.value()), StandardCharsets.UTF_8);
        assertThat(xml).contains("Diabetes mellitus", "250.00", "ICD-9", "Resolved");
    }

    @Test
    void shouldEmitExplicitArchivedState_withoutRemovingTheNativeNarrative() throws Exception {
        var mapped =
                map(
                        CdsCategory.FAMILY_HISTORY,
                        List.of(note(91, "Archived family history", "FamHistory", List.of())));
        var value = mapped.value();
        var archived =
                new CdsExportNativeHistoryDto(
                        value.category(),
                        value.text(),
                        value.dates(),
                        value.code(),
                        value.participants(),
                        value.metadata(),
                        true);
        String xml = new String(render(archived), StandardCharsets.UTF_8);
        assertThat(xml)
                .contains(
                        "CARLOS.History.Archived.v1",
                        "<cdsd:Content>true</cdsd:Content>",
                        "Archived family history");
        // Any receiver, not only CARLOS, is told the item is not current.
        assertThat(xml).contains("<Notes>" + CdsExportNativeHistoryMapper.ARCHIVED_ITEM_STATEMENT + "</Notes>");
        assertThat(new String(render(value), StandardCharsets.UTF_8))
                .doesNotContain("this item was archived");
    }

    @Test
    void shouldKeepAnActualMidnightClock_andRejectDifferentRevisionUuids() throws Exception {
        assertThat(CdsExportNativeHistoryMapper.clock("2026-07-09 00:00:00", ZONE).lexical())
                .isEqualTo("2026-07-09T00:00:00-04:00");
        var second = note(92, "Other", null, List.of());
        var fields = new LinkedHashMap<>(second.note().value().fields());
        fields.put("uuid", UUID.randomUUID().toString());
        var other =
                new CdsExportHistoryFieldReconciler.Current(
                        row(Plan.CASE_NOTES, 92, fields), List.of(), List.of(), Map.of(), Map.of());
        assertThatThrownBy(
                        () ->
                                map(
                                        CdsCategory.CLINICAL_NOTES,
                                        List.of(note(91, "First", null, List.of()), other)))
                .hasMessage("CDS_EXPORT_NATIVE_HISTORY_SCOPE_INVALID");
    }

    /** A patient issue as the encounter editor writes it (prepareCaseIssue): the chart's program. */
    static CdsExportHistoryFieldReconciler.Row uiIssue(long id, long issueId, String program) {
        return row(
                Plan.CASE_ISSUES,
                id,
                Map.of(
                        "demographic_no", "27",
                        "issue_id", Long.toString(issueId),
                        "acute", "0",
                        "certain", "0",
                        "major", "0",
                        "resolved", "0",
                        "program_id", program,
                        "type", "doctor",
                        "update_date", "2026-07-10 12:30:00"));
    }

    static CdsExportHistoryFieldReconciler.Row catalogue(long id, String code, String type) {
        return row(
                Plan.ISSUE_CATALOGUE,
                id,
                Map.of(
                        "code", code,
                        "description", code.equals("250") ? "DIABETES MELLITUS" : code + " entry",
                        "role", "doctor",
                        "type", type,
                        "sortOrderId", "0",
                        "update_date", "2026-01-01 00:00:00"));
    }

    /** A saved, signed note stamped with the provider's program and role, linked to issues. */
    static CdsExportHistoryFieldReconciler.Current uiNote(
            long id,
            String body,
            String program,
            List<CdsExportHistoryFieldReconciler.Row> issues,
            List<CdsExportHistoryFieldReconciler.Row> definitions) {
        var fields = new LinkedHashMap<>(note(id, body, null, List.of()).note().value().fields());
        fields.put("program_no", program);
        fields.put("reporter_caisi_role", "2");
        fields.put("reporter_program_team", "0");
        fields.put("position", "0");
        fields.put("locked", "0");
        fields.put("include_issue_innote", "0");
        var links = new ArrayList<CdsExportCapturedTextRowDto>();
        var patientIssues = new LinkedHashMap<String, CdsExportHistoryFieldReconciler.Row>();
        for (var issue : issues) {
            String issueId = issue.value().fields().get("id");
            patientIssues.put(issueId, issue);
            links.add(
                    new CdsExportCapturedTextRowDto(
                            "casemgmt_issue_notes",
                            CdsExportPatientTableSourceReader.key(id)
                                    + CdsExportPatientTableSourceReader.key(
                                            Long.parseLong(issueId)),
                            Map.of("id", issueId, "note_id", Long.toString(id))));
        }
        var catalogue = new LinkedHashMap<String, CdsExportHistoryFieldReconciler.Row>();
        for (var definition : definitions)
            catalogue.put(definition.value().fields().get("issue_id"), definition);
        return new CdsExportHistoryFieldReconciler.Current(
                row(Plan.CASE_NOTES, id, fields), List.of(), links, patientIssues, catalogue);
    }

    @Test
    void shouldExportAProgramStampedDiagnosis_whenItIsTheProgramOfEveryLinkedNote()
            throws Exception {
        var issue = uiIssue(41, 51, "10034");
        var definition = catalogue(51, "250", "icd9");
        var encounter =
                uiNote(91, "Follow-up for diabetes", "10034", List.of(issue), List.of(definition));
        var result =
                mapper.mapIssue(
                        27,
                        issue,
                        definition,
                        List.of(encounter),
                        program -> {
                            throw new AssertionError("linked note program already qualifies");
                        });
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(result.rows(), result.fields(), 10000, () -> {});
        assertThat(new String(render(result.value()), StandardCharsets.UTF_8))
                .contains("DIABETES MELLITUS", "250", "Active");
        // The same issue, linked to a note of another program: a program the exporter works in
        // is accepted as before; otherwise the issue follows the exported note's access and its
        // program is carried exactly instead of holding the patient.
        var foreign =
                uiNote(91, "Follow-up for diabetes", "10035", List.of(issue), List.of(definition));
        assertThat(mapper.mapIssue(27, issue, definition, List.of(foreign), p -> true).value())
                .isNotNull();
        var followed = mapper.mapIssue(27, issue, definition, List.of(foreign), p -> false);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(followed.rows(), followed.fields(), 10000, () -> {});
        assertThat(followed.value().code().value()).isEqualTo("250");
        assertThat(programDecision(followed, issue))
                .isEqualTo(
                        new CdsExportFieldAssessmentDto(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                CdsExportFieldAssessmentDto.Outcome.MAPPED,
                                "ResidualInfo",
                                CdsExportNativeHistoryMapper.FOREIGN_PROGRAM_ISSUE_RULE));
        assertThat(followed.value().metadata()).contains("\"program_id\":\"10034\"");
    }

    private static CdsExportFieldAssessmentDto programDecision(
            CdsExportNativeHistoryMapper.Result result, CdsExportHistoryFieldReconciler.Row issue) {
        return result.fields()
                .get(
                        new CdsExportNativeFieldKeyDto(
                                issue.value().table(), issue.value().recordKey(), "program_id"));
    }

    @Test
    void shouldAcceptAnIssueOfAProgramTheExporterWorksIn_andHoldOnlyAForeignProgram()
            throws Exception {
        // An issue picked in the encounter editor and never saved with a note keeps the program.
        var unlinked = uiIssue(41, 51, "10034");
        var definition = catalogue(51, "250", "icd9");
        var asked = new ArrayList<String>();
        var result =
                mapper.mapIssue(
                        27,
                        unlinked,
                        definition,
                        List.of(),
                        program -> asked.add(program) && program.equals("10034"));
        assertThat(asked).containsExactly("10034");
        assertThat(result.value().code().value()).isEqualTo("250");
        assertThatThrownBy(
                        () ->
                                mapper.mapIssue(
                                        27,
                                        uiIssue(42, 51, "10016"),
                                        definition,
                                        List.of(),
                                        program -> program.equals("10034")))
                .hasMessage("CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
        // A CPP issue created in one program and written to by a provider of another program:
        // inside the note mapper it follows the note's access when the exporter does not work in
        // the issue's program, with the program carried in the item's native context.
        var concerns = uiIssue(43, 68, "10034");
        var concernsDefinition = catalogue(68, "Concerns", "system");
        var item =
                uiNote(
                        92,
                        "Hypertension",
                        "10035",
                        List.of(concerns),
                        List.of(concernsDefinition));
        var followed =
                mapper.map(
                        27,
                        CdsCategory.PROBLEM_LIST,
                        List.of(item),
                        providers(),
                        ZONE,
                        program -> false);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(followed.rows(), followed.fields(), 10000, () -> {});
        assertThat(programDecision(followed, concerns).outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
        assertThat(programDecision(followed, concerns).rule())
                .isEqualTo(CdsExportNativeHistoryMapper.FOREIGN_PROGRAM_ISSUE_RULE);
        assertThat(followed.value().metadata()).contains("\"program_id\":\"10034\"");
        var mapped =
                mapper.map(
                        27,
                        CdsCategory.PROBLEM_LIST,
                        List.of(item),
                        providers(),
                        ZONE,
                        program -> program.equals("10034"));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(mapped.rows(), mapped.fields(), 10000, () -> {});
        assertThat(new String(render(mapped.value()), StandardCharsets.UTF_8))
                .contains("Hypertension");
    }

    @Test
    void shouldCodeACppItemOnlyWithItsDiagnosis_neverWithASystemMarker() throws Exception {
        var concerns = uiIssue(41, 68, "10034");
        var otherMeds = uiIssue(42, 65, "10034");
        var diabetes = uiIssue(43, 51, "10034");
        var item =
                uiNote(
                        91,
                        "Type 2 diabetes, diet controlled",
                        "10034",
                        List.of(concerns, otherMeds, diabetes),
                        List.of(
                                catalogue(68, "Concerns", "system"),
                                catalogue(65, "OMeds", "system"),
                                catalogue(51, "250", "icd9")));
        var result = map(CdsCategory.PROBLEM_LIST, List.of(item));
        assertThat(result.value().code().system()).isEqualTo("icd9");
        assertThat(result.value().code().value()).isEqualTo("250");
        String xml = new String(render(result.value()), StandardCharsets.UTF_8);
        assertThat(xml).doesNotContain("<cdsd:StandardCodingSystem>system<");
        // The marker is not lost: its patient issue and catalogue facts stay in the context.
        assertThat(result.value().metadata()).contains("OMeds");
        var marker = catalogue(65, "OMeds", "system");
        assertThatThrownBy(() -> mapper.mapIssue(27, otherMeds, marker))
                .hasMessage("CDS_EXPORT_NATIVE_HISTORY_SCOPE_INVALID");
    }

    @Test
    void shouldListEveryAssignedDiagnosis_inNotesInsteadOfHoldingTheItem() throws Exception {
        // A24: "Assign issues" on a CPP item can link two diagnoses; the single DiagnosisCode
        // element holds neither rather than one chosen arbitrarily, and no patient is held.
        var item =
                uiNote(
                        91,
                        "Diabetes and hypertension",
                        "10034",
                        List.of(
                                uiIssue(41, 68, "10034"),
                                uiIssue(43, 51, "10034"),
                                uiIssue(44, 52, "10034")),
                        List.of(
                                catalogue(68, "Concerns", "system"),
                                catalogue(51, "250", "icd9"),
                                catalogue(52, "401", "icd9")));
        var result = map(CdsCategory.PROBLEM_LIST, List.of(item));
        assertThat(result.value().code()).isNull();
        assertThat(result.value().text())
                .containsEntry(
                        "Notes",
                        "Coded diagnoses:\nicd9 250 DIABETES MELLITUS\nicd9 401 401 entry");
        assertThat(result.value().metadata()).contains("\"definition.code\":\"401\"");
        String xml = new String(render(result.value()), StandardCharsets.UTF_8);
        assertThat(xml).doesNotContain("DiagnosisCode").contains("Coded diagnoses:");
    }

    @Test
    void shouldWriteFreeTextAndOverlongCppExtensions_inNotesInsteadOfHoldingTheItem()
            throws Exception {
        // A25: the CPP editor's boxes are free text without a maximum length.
        var family =
                List.of(
                        extension(101, "Age at Onset", "childhood"),
                        extension(102, "Treatment", "T".repeat(251)),
                        extension(103, "Relationship", "R".repeat(51)));
        var result =
                map(
                        CdsCategory.FAMILY_HISTORY,
                        List.of(note(91, "Asthma", "FamHistory", family)));
        assertThat(result.value().text())
                .doesNotContainKeys("AgeAtOnset", "Treatment", "Relationship")
                .containsEntry(
                        "Notes",
                        "Age at Onset:\nchildhood\n\nTreatment:\n"
                                + "T".repeat(251)
                                + "\n\nRelationship:\n"
                                + "R".repeat(51));
        // The exact extension rows still travel in the revision context.
        assertThat(result.value().metadata()).contains("childhood");
        render(result.value());
        var risk =
                List.of(
                        extension(101, "Age at Onset", "0"),
                        extension(102, "Exposure Details", "E".repeat(121)));
        var riskResult =
                map(
                        CdsCategory.RISK_FACTORS,
                        List.of(note(91, "Smoking", "RiskFactors", risk)));
        assertThat(riskResult.value().text())
                .doesNotContainKeys("AgeOfOnset", "ExposureDetails")
                .containsEntry(
                        "Notes",
                        "Age at Onset:\n0\n\nExposure Details:\n" + "E".repeat(121));
        render(riskResult.value());
        var problem =
                List.of(
                        extension(101, "Problem Status", "S".repeat(51)),
                        extension(102, "Problem Description", "D".repeat(251)));
        var problemResult =
                map(
                        CdsCategory.PROBLEM_LIST,
                        List.of(note(91, "Back pain", "Concerns", problem)));
        assertThat(problemResult.value().text())
                .doesNotContainKeys("ProblemStatus", "ProblemDescription")
                .containsKey("Notes");
        render(problemResult.value());
        // A numeric age that fits is still the standard element.
        var fitting =
                map(
                        CdsCategory.FAMILY_HISTORY,
                        List.of(
                                note(
                                        91,
                                        "Asthma",
                                        "FamHistory",
                                        List.of(extension(101, "Age at Onset", "40")))));
        assertThat(fitting.value().text()).containsEntry("AgeAtOnset", "40").doesNotContainKey("Notes");
    }

    @Test
    void shouldWriteATokenNormalizedCodeDescription_andKeepTheExactOneInTheContext()
            throws Exception {
        // A26: the seeded ICSW420 description ends with a space; StandardCodeDescription is a token.
        var definition =
                row(
                        Plan.ISSUE_CATALOGUE,
                        51,
                        Map.of(
                                "code",
                                "ICSW420",
                                "type",
                                "icd9",
                                "description",
                                "Social  worker\tissue "));
        var issue =
                row(
                        Plan.CASE_ISSUES,
                        41,
                        Map.of("demographic_no", "27", "issue_id", "51", "resolved", "0"));
        var standalone = mapper.mapIssue(27, issue, definition);
        assertThat(standalone.value().code().description()).isEqualTo("Social worker issue");
        assertThat(standalone.value().metadata()).contains("Social  worker\\tissue ");
        render(standalone.value());
        var item =
                uiNote(
                        91,
                        "Worker follow-up",
                        "10034",
                        List.of(uiIssue(41, 68, "10034"), uiIssue(43, 51, "10034")),
                        List.of(catalogue(68, "MedHistory", "system"), definition));
        var linked = map(CdsCategory.PAST_HEALTH, List.of(item));
        assertThat(linked.value().code().description()).isEqualTo("Social worker issue");
        render(linked.value());
    }

    private static CdsExportHistoryFieldReconciler.Row extension(long id, String key, String value) {
        return row(
                Plan.CASE_NOTE_EXT, id, Map.of("note_id", "91", "key_val", key, "value", value));
    }

    @Test
    void shouldRefuseUnqualifiedIssueProgramOwnership_andSqlNullNoteBodies() throws Exception {
        var issue =
                row(
                        Plan.CASE_ISSUES,
                        41,
                        Map.of("demographic_no", "27", "issue_id", "51", "program_id", "13"));
        var definition =
                row(
                        Plan.ISSUE_CATALOGUE,
                        51,
                        Map.of(
                                "code",
                                "250.00",
                                "type",
                                "ICD-9",
                                "description",
                                "Diabetes mellitus"));
        assertThatThrownBy(() -> mapper.mapIssue(27, issue, definition))
                .hasMessage("CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
        var original = note(91, "Original", null, List.of());
        var values = new LinkedHashMap<>(original.note().value().fields());
        values.put("note", null);
        var current =
                new CdsExportHistoryFieldReconciler.Current(
                        row(Plan.CASE_NOTES, 91, values), List.of(), List.of(), Map.of(), Map.of());
        assertThatThrownBy(() -> map(CdsCategory.CLINICAL_NOTES, List.of(current)))
                .hasMessage("CDS_EXPORT_NATIVE_NOTE_BODY_REQUIRED");
    }

    @org.junit.jupiter.api.Test
    void shouldPlaceAStoredInstant_inTheClinicZoneWithoutDaylightSavingAmbiguity() throws Exception {
        var zone = java.time.ZoneId.of("America/Toronto");
        // 2025-11-02 01:30 happens twice in Toronto; each instant has one answer.
        assertThat(CdsExportNativeHistoryMapper.instant("1762061400", zone).lexical())
                .isEqualTo("2025-11-02T01:30:00-04:00");
        assertThat(CdsExportNativeHistoryMapper.instant("1762065000", zone).lexical())
                .isEqualTo("2025-11-02T01:30:00-05:00");
        assertThat(CdsExportNativeHistoryMapper.instant("1783701900.5", zone).lexical())
                .isEqualTo("2026-07-10T12:45:00.5-04:00");
        for (String invalid : new String[] {"0", "", "-1", "2026-07-10 16:45:00", "1e9"})
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> CdsExportNativeHistoryMapper.instant(invalid, zone))
                    .hasMessage("CDS_EXPORT_NATIVE_HISTORY_DATE_REQUIRES_REVIEW");
    }

    /** A casemgmt_note_link row of note {@code note}, as CARLOS writes it. */
    private static CdsExportHistoryFieldReconciler.Row noteLink(
            long id, long note, String type, String record, String part) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("note_id", Long.toString(note));
        fields.put("table_name", type);
        fields.put("table_id", record);
        fields.put("other_id", part);
        return row(Plan.CASE_NOTE_LINK, id, fields);
    }

    private static CdsExportHistoryFieldReconciler.Current linked(
            CdsExportHistoryFieldReconciler.Current note,
            CdsExportHistoryFieldReconciler.Row... links) {
        return new CdsExportHistoryFieldReconciler.Current(
                note.note(),
                note.extensions(),
                note.links(),
                note.patientIssues(),
                note.definitions(),
                List.of(links));
    }

    @Test
    void shouldNameTheAttachedDocument_atTheHeadOfTheClinicalNote_andCarryTheLinkExactly()
            throws Exception {
        // ManageDocument2Action.saveDocNote: the note recording a filed document and its link.
        var result =
                map(
                        CdsCategory.CLINICAL_NOTES,
                        List.of(
                                linked(
                                        note(91, "Document Lab letter created.", null, List.of()),
                                        noteLink(301, 91, "5", "12", null))));
        String text = result.value().text().get("MyClinicalNotesContent");
        assertThat(text)
                .startsWith(
                        "[CARLOS: this note is attached to another record in the sending system:"
                                + " document 12.]\n\nDocument Lab letter created.");
        assertThat(result.value().metadata())
                .contains(
                        "\"attachedTo\":[{\"record\":\"document\",\"table_name\":\"5\","
                                + "\"table_id\":\"12\",\"other_id\":null}]");
        assertThat(text).endsWith(result.value().metadata());
        // One fixed key order, so an unchanged note writes the same bytes on every run.
        assertThat(result.value().metadata()).startsWith("{\"version\":1,\"revisions\":[");
        String key = CdsExportPatientTableSourceReader.key(301);
        assertThat(result.rows())
                .filteredOn(r -> r.table().equals("casemgmt_note_link"))
                .singleElement()
                .satisfies(r -> assertThat(r.recordKey()).isEqualTo(key));
        for (String column : List.of("table_name", "table_id", "other_id"))
            assertThat(
                            result.fields()
                                    .get(
                                            new CdsExportNativeFieldKeyDto(
                                                    "casemgmt_note_link", key, column)))
                    .satisfies(
                            decision -> {
                                assertThat(decision.outcome())
                                        .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
                                assertThat(decision.destination())
                                        .isEqualTo("MyClinicalNotesContent");
                                assertThat(decision.rule()).isEqualTo("EXACT_NATIVE_NOTE_LINK");
                            });
        for (String column : List.of("id", "note_id"))
            assertThat(
                            result.fields()
                                    .get(
                                            new CdsExportNativeFieldKeyDto(
                                                    "casemgmt_note_link", key, column))
                                    .outcome())
                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
        assertThat(new String(render(result.value()), StandardCharsets.UTF_8))
                .contains("this note is attached to another record in the sending system");
    }

    @Test
    void shouldCarryTheCopiedLinkOfEveryRevision_andNameTheRecordOnce() throws Exception {
        // copyRevisionMetadata copies a discontinuation note's drug link to each revision.
        var result =
                map(
                        CdsCategory.CLINICAL_NOTES,
                        List.of(
                                linked(
                                        note(91, "Stopped: rash", null, List.of()),
                                        noteLink(301, 91, "2", "46", null)),
                                linked(
                                        note(92, "Stopped: rash and hives", null, List.of()),
                                        noteLink(302, 92, "2", "46", null))));
        String text = result.value().text().get("MyClinicalNotesContent");
        assertThat(text.split("attached to another record", -1)).hasSize(2);
        assertThat(text)
                .startsWith(
                        "[CARLOS: this note is attached to another record in the sending system:"
                                + " prescription 46.]\n\n[Original note]\nStopped: rash");
        assertThat(result.value().metadata().split("\"attachedTo\"", -1)).hasSize(3);
        assertThat(result.rows().stream().filter(r -> r.table().equals("casemgmt_note_link")))
                .hasSize(2);
    }

    @Test
    void shouldCarryTheLinkOfACppItem_inItsResidualContextOnly() throws Exception {
        // EFormUtil.saveCMNote: an eForm that writes to a CPP section links its note to the form.
        var result =
                map(
                        CdsCategory.FAMILY_HISTORY,
                        List.of(
                                linked(
                                        note(91, "Mother: breast cancer", "FamHistory", List.of()),
                                        noteLink(301, 91, "6", "77", null))));
        assertThat(result.value().text())
                .containsEntry("ProblemDiagnosisProcedureDescription", "Mother: breast cancer")
                .doesNotContainKey("Notes");
        assertThat(result.value().metadata()).contains("\"record\":\"eForm\"");
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "casemgmt_note_link",
                                                CdsExportPatientTableSourceReader.key(301),
                                                "table_id"))
                                .destination())
                .isEqualTo("ResidualInfo");
        assertThat(new String(render(result.value()), StandardCharsets.UTF_8))
                .contains("CARLOS.NativeHistory.v1", "eForm")
                .doesNotContain("[CARLOS: this note is attached");
    }

    @Test
    void shouldNameSeveralRecordsAndAnUnknownType_keepingTheSecondaryIdExact() throws Exception {
        var result =
                map(
                        CdsCategory.CLINICAL_NOTES,
                        List.of(
                                linked(
                                        note(91, "Reviewed", null, List.of()),
                                        noteLink(301, 91, "4", "7001", "0-1"),
                                        noteLink(302, 91, "99", "5", ""))));
        assertThat(result.value().text().get("MyClinicalNotesContent"))
                .startsWith(
                        "[CARLOS: this note is attached to other records in the sending system:"
                                + " lab result 7001 (part 0-1); record type 99 5.]");
        assertThat(result.value().metadata())
                .contains("\"other_id\":\"0-1\"", "\"other_id\":\"\"");
    }

    @Test
    void shouldRefuseALinkOfAnotherNote_andHoldASecondaryIdItCannotWrite() {
        assertThatThrownBy(
                        () ->
                                map(
                                        CdsCategory.CLINICAL_NOTES,
                                        List.of(
                                                linked(
                                                        note(91, "Body", null, List.of()),
                                                        noteLink(301, 92, "5", "12", null)))))
                .hasMessage("CDS_EXPORT_NATIVE_HISTORY_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                map(
                                        CdsCategory.CLINICAL_NOTES,
                                        List.of(
                                                linked(
                                                        note(91, "Body", null, List.of()),
                                                        noteLink(301, 91, "5", "12", "a\u0001")))))
                .hasMessage("CDS_EXPORT_NATIVE_NOTE_LINK_TEXT_INVALID");
    }
}
