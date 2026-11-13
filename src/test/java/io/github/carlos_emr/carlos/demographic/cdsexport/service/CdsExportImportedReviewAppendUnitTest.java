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

import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A review or inbox comment recorded in CARLOS on a report or lab result that came from an
 * earlier CDS import no longer holds the patient. The review is appended to the retained
 * occurrence after the imported reviewers, every imported element keeps its exact bytes and is
 * still accounted as retained, and the review rows travel whole: in Notes appended to a report
 * without its own Notes, in a supplementary record otherwise, and for a lab in its NewCategory
 * record.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportImportedReviewAppendUnitTest {
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final String RUN = "11111111-1111-4111-8111-111111111111";

    static CdsExportRetainedFieldDto field(String relative, String value) {
        String[] parts = relative.split("/");
        var category = CdsCategory.fromWireName(parts[0].substring(0, parts[0].indexOf('[')));
        var type = CdsWireSchema.schemaModel().element("cds", category.wireName());
        String namespace = "cds";
        for (int i = 1; i < parts.length; i++) {
            String name = parts[i].substring(0, parts[i].indexOf('['));
            var child =
                    type.childNames().stream()
                            .filter(candidate -> candidate.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            namespace = child.namespace();
            type = type.child(child.namespace(), child.local());
        }
        String id;
        try {
            id =
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(relative.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        return new CdsExportRetainedFieldDto(
                id,
                RUN,
                "a".repeat(64),
                "c".repeat(64),
                category,
                "/OmdCds[1]/PatientRecord[1]/" + relative,
                1,
                namespace,
                "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                null,
                value,
                value,
                null);
    }

    static CdsExportRetainedSectionWriter.Values inline() {
        return new CdsExportRetainedSectionWriter.Values() {
            @Override
            public Reader text(CdsExportRetainedFieldDto field) {
                return new StringReader(field.logicalInline());
            }

            @Override
            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError();
            }
        };
    }

    /** A Text report as another EMR sent it, reviewed there by one physician. */
    private static List<CdsExportRetainedFieldDto> importedReport(boolean notes) {
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(field("Reports[1]/Format[1]", "Text"));
        fields.add(field("Reports[1]/Content[1]/TextContent[1]", "Imported letter  text"));
        fields.add(field("Reports[1]/Class[1]", "Consultant Report"));
        fields.add(field("Reports[1]/ReportReviewed[1]/Name[1]/FirstName[1]", "Sending"));
        fields.add(field("Reports[1]/ReportReviewed[1]/Name[1]/LastName[1]", "Reviewer"));
        fields.add(
                field("Reports[1]/ReportReviewed[1]/DateTimeReportReviewed[1]/FullDate[1]", "2025-01-02"));
        if (notes) fields.add(field("Reports[1]/Notes[1]", "Sender's own note"));
        return fields;
    }

    private static CdsExportCapturedTextRowDto documentRoute(
            long id, String provider, String status, String comment) {
        var row =
                CdsExportLaboratoryReviewerMappingUnitTest.route(
                        id, provider, status, "2026-03-08 11:45:00", comment);
        var values = new LinkedHashMap<>(row.fields());
        values.put("lab_no", "91");
        values.put("lab_type", "DOC");
        return new CdsExportCapturedTextRowDto(
                CdsExportDocumentRouteSourceReader.TABLE, row.recordKey(), values);
    }

    private static Map<String, CdsExportCapturedTextRowDto> providers() {
        return Map.of(
                "101",
                CdsExportLaboratoryReviewerMappingUnitTest.provider("101", "Ada", "Carlos", "123456"));
    }

    private static String xml(CdsCategory category, CdsExportXmlComposer.Content content)
            throws Exception {
        return new String(render(category, content), StandardCharsets.UTF_8);
    }

    @Test
    void shouldAppendACarlosAcknowledgementAndCommentToAnImportedReport_withoutHoldingThePatient()
            throws Exception {
        var fields = importedReport(false);
        var acknowledged = documentRoute(5, "101", "A", "Booked a follow-up");
        var waiting = documentRoute(6, "0", "N", null);
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        var assessments = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var supplementary =
                CdsExportReportOccurrenceService.importedReviews(
                        List.of(acknowledged, waiting), providers(), ZONE, fields, changes, assessments, rows);
        assertThat(supplementary).isNull();
        assertThat(changes)
                .extracting(CdsExportRetainedSectionWriter.Change::relativePath)
                .containsExactly("ReportReviewed[2]", "Notes[1]");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(rows, assessments, 100000, () -> {});
        var prepared =
                new CdsExportRetainedSectionWriter().prepareChanges(fields, inline(), changes, () -> {});
        // Every imported element is still retained exactly; nothing imported is replaced.
        assertThat(prepared.replacedFields()).isEmpty();
        assertThat(prepared.retainedFields())
                .containsExactlyInAnyOrderElementsOf(
                        fields.stream().map(CdsExportRetainedFieldDto::id).toList());
        String report = xml(CdsCategory.REPORTS, prepared.content());
        assertThat(report)
                .contains("Imported letter  text", "<cdsd:FirstName>Sending</cdsd:FirstName>");
        // The sending EMR's reviewer first, then CARLOS's acknowledgement on its date.
        assertThat(report.indexOf("Sending")).isLessThan(report.indexOf("<cdsd:FirstName>Ada"));
        assertThat(report)
                .contains(
                        "<ReviewingOHIPPhysicianId>123456</ReviewingOHIPPhysicianId>",
                        "<cdsd:FullDate>2026-03-08</cdsd:FullDate>",
                        "[CARLOS reviews recorded after the import]",
                        "Booked a follow-up");
    }

    @Test
    void shouldCarryTheReviewsOfAnImportedReportWithItsOwnNotes_inASupplementaryRecord()
            throws Exception {
        var fields = importedReport(true);
        var acknowledged = documentRoute(5, "101", "A", "Booked a follow-up");
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        var assessments = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var supplementary =
                CdsExportReportOccurrenceService.importedReviews(
                        List.of(acknowledged), providers(), ZONE, fields, changes, assessments, rows);
        assertThat(CdsExportReportOccurrenceService.importedNotes(fields)).isTrue();
        // The imported Notes is never rewritten: only the reviewer is appended.
        assertThat(changes)
                .extracting(CdsExportRetainedSectionWriter.Change::relativePath)
                .containsExactly("ReportReviewed[2]");
        assertThat(supplementary).isNotNull();
        assertThat(supplementary.text()).contains("Booked a follow-up", "CARLOS data summary");
        assertThat(supplementary.fields().values())
                .extracting(CdsExportFieldAssessmentDto::destination)
                .doesNotContain("Reports/Notes");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(rows, supplementary.fields(), 100000, () -> {});
        var prepared =
                new CdsExportRetainedSectionWriter().prepareChanges(fields, inline(), changes, () -> {});
        assertThat(prepared.replacedFields()).isEmpty();
        assertThat(xml(CdsCategory.REPORTS, prepared.content()))
                .contains("Sender's own note")
                .doesNotContain("Booked a follow-up");
        assertThat(
                        CdsExportReportOccurrenceService.importedReviewsId(RUN + ":" + "b".repeat(64)))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void shouldAppendACarlosAcknowledgementToAnImportedLabResult_andCarryItsCommentInNewCategory()
            throws Exception {
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(field("LaboratoryResults[1]/LaboratoryName[1]", "Sending Lab"));
        fields.add(field("LaboratoryResults[1]/TestNameReportedByLab[1]", "Glucose"));
        fields.add(field("LaboratoryResults[1]/CollectionDateTime[1]/FullDate[1]", "2025-01-02"));
        fields.add(field("LaboratoryResults[1]/ResultReviewer[1]/Name[1]/FirstName[1]", "Sending"));
        fields.add(field("LaboratoryResults[1]/ResultReviewer[1]/Name[1]/LastName[1]", "Reviewer"));
        fields.add(
                field(
                        "LaboratoryResults[1]/ResultReviewer[1]/DateTimeResultReviewed[1]/FullDate[1]",
                        "2025-01-03"));
        fields.add(
                field(
                        "LaboratoryResults[1]/ResultNormalAbnormalFlag[1]/resultNormalAbnormalFlagAsEnum[1]",
                        "N"));
        var commented =
                CdsExportLaboratoryReviewerMappingUnitTest.route(
                        14, "101", "A", "2026-07-10 16:45:00", "Discussed with patient");
        var reviews =
                CdsExportInboxReviewMapper.laboratory()
                        .map(List.of(commented), List.of(), providers(), ZONE);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(reviews.rows(), reviews.fields(), 100000, () -> {});
        var changes = CdsExportLaboratoryOccurrenceService.appendedReviewers(fields, reviews);
        assertThat(changes)
                .extracting(CdsExportRetainedSectionWriter.Change::relativePath)
                .containsExactly("ResultReviewer[2]");
        var prepared =
                new CdsExportRetainedSectionWriter().prepareChanges(fields, inline(), changes, () -> {});
        assertThat(prepared.replacedFields()).isEmpty();
        assertThat(prepared.retainedFields()).hasSize(fields.size());
        String result = xml(CdsCategory.LABORATORY_RESULTS, prepared.content());
        assertThat(result)
                .contains("Sending", "<cdsd:FirstName>Ada</cdsd:FirstName>", "2026-07-10T16:45:00")
                .doesNotContain("Discussed with patient");
        // The comment travels whole in the result's NewCategory record.
        var comments =
                new CdsExportLaboratoryOccurrenceService.ImportedComments(
                        "d".repeat(64), "91", reviews.carried());
        String record =
                xml(
                        CdsCategory.NEW_CATEGORY,
                        body -> CdsExportNativeLaboratoryMapper.writeImportedComments(comments, body));
        assertThat(record)
                .contains("CARLOS.ImportedLaboratoryReview.v1", "Discussed\\u0020with\\u0020patient");
        var carried =
                CdsExportInboxReviewMapper.carriedFields(comments.routes(), "NewCategory/ResidualInfo");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(comments.routes(), carried, 100000, () -> {});
        assertThat(Set.copyOf(reviews.carried())).containsExactly(commented);
    }
}
