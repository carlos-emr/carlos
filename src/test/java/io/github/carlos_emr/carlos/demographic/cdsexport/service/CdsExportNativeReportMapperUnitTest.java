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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeReportMapperUnitTest {
    private static final String PATH = "Reports/" + "a".repeat(64) + ".pdf";
    private final CdsExportNativeReportMapper mapper = new CdsExportNativeReportMapper();

    static CdsExportCapturedTextRowDto document() {
        var fields = new LinkedHashMap<String, String>();
        Plan.DOCUMENTS.columns().forEach(c -> fields.put(c, null));
        fields.putAll(
                Map.of(
                        "document_no",
                        "91",
                        "status",
                        "A",
                        "restrictToProgram",
                        "0",
                        "docfilename",
                        "original-report.pdf",
                        "doctype",
                        "consult",
                        "docdesc",
                        "Exact café 😀 report description",
                        "source",
                        "Dr. Source Author",
                        "sourceFacility",
                        "Native clinic",
                        "observationdate",
                        "2026-07-09",
                        "doccreator",
                        "999998"));
        fields.put("receivedDate", "2026-07-10 12:30:00");
        fields.put("reviewer", "17");
        fields.put("reviewdatetime", "2026-07-11 12:30:00");
        return new CdsExportCapturedTextRowDto(
                "document", CdsExportPatientTableSourceReader.key(91), fields);
    }

    static CdsExportCapturedTextRowDto link() {
        return new CdsExportCapturedTextRowDto(
                "ctl_document",
                CdsExportPatientTableSourceReader.key(27)
                        + CdsExportPatientTableSourceReader.key(91),
                Map.of(
                        "document_no",
                        "91",
                        "module",
                        "demographic",
                        "module_id",
                        "27",
                        "status",
                        "A"));
    }

    private static CdsExportCapturedTextRowDto edit(
            CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new LinkedHashMap<>(row.fields());
        fields.put(column, value);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }

    private CdsExportNativeReportMapper.Result map(CdsExportCapturedTextRowDto document)
            throws Exception {
        var mapped =
                mapper.map(
                        27,
                        document,
                        link(),
                        CdsExportNativeReportMapper.Content.file(PATH, "pdf"),
                        providers(),
                        ZoneId.of("America/Toronto"));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(mapped.rows(), mapped.fields(), 10000, () -> {});
        return mapped;
    }

    @Test
    void shouldPreserveAnUndatedReviewerInNotes_withoutInventingAStandardReviewDate()
            throws Exception {
        var mapped = map(edit(document(), "reviewdatetime", null));
        String xml =
                new String(
                        render(CdsCategory.REPORTS, body -> mapper.write(mapped, body)),
                        StandardCharsets.UTF_8);
        assertThat(xml).doesNotContain("<cds:ReportReviewed>").contains("publicReviewer", "Zoë😀");
        assertThat(
                        mapped.fields().entrySet().stream()
                                .filter(e -> e.getKey().table().equals("provider"))
                                .map(e -> e.getValue().destination()))
                .containsOnly("Reports/Notes");
    }

    @Test
    void shouldMapAnOrdinaryNativeReport_withItsSourceAuthorDatesPublicReviewerAndExactMetadata()
            throws Exception {
        var mapped = map(document());
        String xml =
                new String(
                        render(CdsCategory.REPORTS, body -> mapper.write(mapped, body)),
                        StandardCharsets.UTF_8);
        assertThat(xml)
                .contains(
                        "Consultant Report",
                        PATH,
                        "Dr. Source Author",
                        "Native clinic",
                        "Zoë😀",
                        "001234",
                        "2026-07-09",
                        "2026-07-10T12:30:00-04:00",
                        "2026-07-11",
                        "Exact café 😀 report description",
                        "original-report.pdf");
        assertThat(mapped.text().get("SourceAuthorPhysician")).isEqualTo("Dr. Source Author");
        assertThat(mapped.fields())
                .hasSize(
                        Plan.DOCUMENTS.columns().size() + Plan.DOCUMENT_LINKS.columns().size() + 6);
    }

    @Test
    void
            shouldKeepExplicitNativeReportClasses_andUseTheGeneralMedicalRecordClassForOtherNativeTypes()
                    throws Exception {
        assertThat(
                        map(edit(document(), "docClass", "Diagnostic Imaging Report"))
                                .text()
                                .get("Class"))
                .isEqualTo("Diagnostic Imaging Report");
        assertThat(
                        map(edit(document(), "doctype", "Custom native document category"))
                                .text()
                                .get("Class"))
                .isEqualTo("Medical Records Report");
    }

    @Test
    void shouldRefuseMissingReviewerLookups_archivedReportsAndUnqualifiedProgramRestrictions() {
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        document(),
                                        link(),
                                        CdsExportNativeReportMapper.Content.file(PATH, "pdf"),
                                        Map.of(),
                                        ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRED");
        // A soft-deleted document is no longer a review case: the occurrence service accounts
        // for it as not exported and never maps it (review R6), so reaching the mapper is a
        // scope error. A status the chart does not know still holds the patient.
        assertThatThrownBy(() -> map(edit(document(), "status", "D")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_SCOPE_INVALID");
        assertThatThrownBy(() -> map(edit(document(), "status", "I")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_STATUS_REQUIRES_REVIEW");
        assertThatThrownBy(() -> map(edit(document(), "restrictToProgram", "1")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_PROGRAM_REQUIRES_REVIEW");
    }

    @Test
    void shouldRejectAnotherPatientOrUnsafeDeliveryPath_beforeAnyAttachmentCanBeStaged() {
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        28,
                                        document(),
                                        link(),
                                        CdsExportNativeReportMapper.Content.file(PATH, "pdf"),
                                        providers(),
                                        ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        document(),
                                        link(),
                                        CdsExportNativeReportMapper.Content.file(
                                                "../../report.pdf", "pdf"),
                                        providers(),
                                        ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_SCOPE_INVALID");
    }

    @Test
    void shouldUseTheRecordedObservationDate_andTreatTheUploadInstantAsReceipt() throws Exception {
        // A letter dated 2026-03-01, faxed in and filed on 2026-03-05: review R1.
        var faxed =
                map(
                        edit(
                                edit(
                                        edit(document(), "observationdate", "2026-03-01"),
                                        "contentdatetime",
                                        "2026-03-05 10:14:00"),
                                "receivedDate",
                                null));
        assertThat(faxed.dates().get("EventDateTime").lexical()).startsWith("2026-03-01");
        assertThat(faxed.dates().get("ReceivedDateTime").lexical()).startsWith("2026-03-05T10:14");
        // A recorded received date wins over the upload instant.
        var received =
                map(
                        edit(
                                edit(document(), "observationdate", "2026-03-01"),
                                "contentdatetime",
                                "2026-03-05 10:14:00"));
        assertThat(received.dates().get("ReceivedDateTime").lexical()).startsWith("2026-07-10");
    }

    /** An Add HTML document (AddEditHtml2Action): status H, its content in docxml, no file. */
    private static CdsExportCapturedTextRowDto html(String body) {
        var document = edit(edit(edit(document(), "status", "H"), "docxml", body), "contenttype", "text/html");
        return edit(document, "docfilename", "20260709123000html");
    }

    private static CdsExportCapturedTextRowDto htmlLink() {
        var fields = new LinkedHashMap<>(link().fields());
        fields.put("status", "H");
        return new CdsExportCapturedTextRowDto("ctl_document", link().recordKey(), fields);
    }

    private CdsExportNativeReportMapper.Result mapHtml(CdsExportCapturedTextRowDto document)
            throws Exception {
        var mapped =
                mapper.map(
                        27,
                        document,
                        htmlLink(),
                        CdsExportNativeReportMapper.Content.html(),
                        providers(),
                        ZoneId.of("America/Toronto"));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(mapped.rows(), mapped.fields(), 10000, () -> {});
        return mapped;
    }

    @Test
    void shouldExportStoredHtml_asTheReportTextInSchemaOrder() throws Exception {
        String body = "<p>Discharge summary 😀</p>\r\n<p>Plan</p>";
        var mapped = mapHtml(html(body));
        String xml =
                new String(
                        render(CdsCategory.REPORTS, body2 -> mapper.write(mapped, body2)),
                        StandardCharsets.UTF_8);
        int format = xml.indexOf("<Format>Text</Format>");
        int extension = xml.indexOf("<FileExtensionAndVersion>html</FileExtensionAndVersion>");
        int content = xml.indexOf("<Content><cdsd:TextContent>");
        int classification = xml.indexOf("<Class>");
        assertThat(format).isPositive();
        assertThat(extension).isGreaterThan(format);
        assertThat(content).isGreaterThan(extension);
        assertThat(classification).isGreaterThan(content);
        assertThat(xml).doesNotContain("<FilePath>").contains("Discharge summary 😀");
        assertThat(mapped.text().get("TextContent")).isEqualTo(body);
        // The HTML is the Content and is not repeated in the Notes source context.
        assertThat(mapped.text().get("Notes")).doesNotContain("Discharge summary").contains("document.docxml");
        assertThat(
                        mapped.fields().entrySet().stream()
                                .filter(e -> e.getKey().column().equals("docxml"))
                                .map(e -> e.getValue().destination()))
                .containsExactly("Reports/Content");
    }

    @Test
    void shouldHoldBlankOrUnrepresentableStoredHtml_insteadOfExportingAnEmptyReport() {
        assertThatThrownBy(() -> mapHtml(html(" \r\n\t")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_STORED_HTML_REQUIRES_REVIEW");
        assertThatThrownBy(() -> mapHtml(html("<p>bell \u0007</p>")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_STORED_HTML_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseAContentKindTheChartDoesNotShow_forTheDocument() {
        // An uploaded file document cannot be exported as stored HTML, nor the reverse.
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        document(),
                                        link(),
                                        CdsExportNativeReportMapper.Content.html(),
                                        providers(),
                                        ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        html("<p>x</p>"),
                                        htmlLink(),
                                        CdsExportNativeReportMapper.Content.file(PATH, "pdf"),
                                        providers(),
                                        ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_SCOPE_INVALID");
    }

    @Test
    void shouldHoldAnUnknownLinkStatus_whileAcceptingActiveHtmlAndLegacyNullLinks() throws Exception {
        var unknown = new LinkedHashMap<>(link().fields());
        unknown.put("status", "I");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        document(),
                                        new CdsExportCapturedTextRowDto(
                                                "ctl_document", link().recordKey(), unknown),
                                        CdsExportNativeReportMapper.Content.file(PATH, "pdf"),
                                        providers(),
                                        ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_STATUS_REQUIRES_REVIEW");
        var legacy = new LinkedHashMap<>(link().fields());
        legacy.put("status", null);
        assertThat(
                        mapper.map(
                                        27,
                                        document(),
                                        new CdsExportCapturedTextRowDto(
                                                "ctl_document", link().recordKey(), legacy),
                                        CdsExportNativeReportMapper.Content.file(PATH, "pdf"),
                                        providers(),
                                        ZoneId.of("America/Toronto"))
                                .text())
                .containsKey("FilePath");
    }

    @Test
    void shouldRejectImportedPrecisionAnchors_withoutInventingAFullDate() {
        assertThatThrownBy(() -> map(edit(document(), "cds_content_precision", "YEAR")))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_IMPORTED_PRECISION_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseOversizedMetadata_insteadOfClippingTheReportDescriptionOrSourceContext() {
        assertThatThrownBy(() -> map(edit(document(), "docdesc", "x".repeat(32769))))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_METADATA_CAPACITY");
    }
}
