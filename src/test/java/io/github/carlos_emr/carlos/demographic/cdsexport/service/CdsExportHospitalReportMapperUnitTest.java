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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * One HRM report of the chart as one CDS 5.2 Reports entry: the standard elements from the stored
 * message, the sign-offs as ReportReviewed, comments, subclasses and the report row in Notes, and
 * every captured field accounted. Rows are shaped as CARLOS stores them (the demo HRM reports in
 * development.sql and the fixture message the HRM viewer tests read).
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportHospitalReportMapperUnitTest {
    static final Path FIXTURE = Path.of(".devcontainer/db/db_data/hrm/demo-hrm-diagnostic-imaging.xml");
    private static final int PATIENT = 27;
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");

    static String fixture() throws Exception {
        return Files.readString(FIXTURE);
    }

    /** The HRMDocument row of a received report, as the demo data stores it. */
    static Map<String, String> report(int id) {
        var row = new TreeMap<String, String>();
        Plan.HRM_REPORTS.columns().forEach(column -> row.put(column, null));
        row.put("id", Integer.toString(id));
        row.put("timeReceived", "2024-01-18 12:05:00");
        row.put("reportType", "Diagnostic Imaging Report");
        row.put("reportHash", "9bc35e1acf0ffb30f3a49defa8a9cfa2");
        row.put("reportLessTransactionInfoHash", "7099332f414f8b3137e91e3dbdb641ea");
        row.put("reportStatus", "S");
        row.put("reportFile", "LabUpload.demo-" + id + ".xml.1690313901104");
        row.put("unmatchedProviders", "|FAKE-Raven, Melanie (D104412)");
        row.put("numDuplicatesReceived", "1");
        row.put("reportDate", "2024-01-18 10:15:00");
        row.put("reportLessDemographicInfoHash", "73e8f20802b5e7f4dfcb4c524dce9d53");
        row.put("sourceFacility", "DEMO");
        row.put("description", "");
        row.put("formattedName", "FAKE-Jones, FAKE-Jacky");
        row.put("dob", "1985-06-15");
        row.put("gender", "M");
        row.put("hcn", "0000000000");
        row.put("recipientId", "999998");
        row.put("recipientName", "Provider, Demo");
        row.put("className", "Diagnostic Imaging Report");
        row.put("sourceFacilityReportNo", "DEMO-DI-0001");
        return row;
    }

    static CdsExportCapturedTextRowDto reportRow(int id, Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto(
                "HRMDocument", CdsExportPatientTableSourceReader.key(id), fields);
    }

    static CdsExportCapturedTextRowDto link(int id, int report) {
        var row = new TreeMap<String, String>();
        row.put("id", Integer.toString(id));
        row.put("demographicNo", Integer.toString(PATIENT));
        row.put("hrmDocumentId", Integer.toString(report));
        row.put("timeAssigned", "2024-01-18 12:06:00");
        row.put("cds_patient_key", Integer.toString(PATIENT));
        return new CdsExportCapturedTextRowDto(
                "HRMDocumentToDemographic", CdsExportPatientTableSourceReader.key(id), row);
    }

    static CdsExportCapturedTextRowDto comment(int id, int report, String deleted, String text) {
        var row = new TreeMap<String, String>();
        row.put("id", Integer.toString(id));
        row.put("providerNo", "101");
        row.put("hrmDocumentId", Integer.toString(report));
        row.put("comment", text);
        row.put("commentTime", "2024-01-19 08:30:00");
        row.put("deleted", deleted);
        return new CdsExportCapturedTextRowDto(
                "HRMDocumentComment", CdsExportPatientTableSourceReader.key(id), row);
    }

    static CdsExportCapturedTextRowDto subClass(int id, int report, String active) {
        var row = new TreeMap<String, String>();
        row.put("id", Integer.toString(id));
        row.put("hrmDocumentId", Integer.toString(report));
        row.put("subClass", "CR");
        row.put("subClassMnemonic", "ANK3LX");
        row.put("subClassDescription", "Ankle -Recheck 2 - 3 views: LT");
        row.put("subClassDateTime", "2024-01-18");
        row.put("isActive", active);
        row.put("sendingFacilityId", "DEMO");
        return new CdsExportCapturedTextRowDto(
                "HRMDocumentSubClass", CdsExportPatientTableSourceReader.key(id), row);
    }

    static CdsExportCapturedTextRowDto route(
            int id, int report, String provider, String signed, String time) {
        var row = new TreeMap<String, String>();
        row.put("id", Integer.toString(id));
        row.put("providerNo", provider);
        row.put("hrmDocumentId", Integer.toString(report));
        row.put("signedOff", signed);
        row.put("signedOffTimestamp", time);
        row.put("viewed", "1");
        row.put("filed", null);
        row.put("mrpDemographicNo", null);
        return new CdsExportCapturedTextRowDto(
                "HRMDocumentToProvider", CdsExportPatientTableSourceReader.key(id), row);
    }

    static CdsExportCapturedTextRowDto provider(String number, String first, String last, String ohip) {
        var row = new TreeMap<String, String>();
        CdsExportProviderRowDto.COLUMNS.forEach(column -> row.put(column, null));
        row.put("provider_no", number);
        row.put("first_name", first);
        row.put("last_name", last);
        row.put("ohip_no", ohip);
        return new CdsExportCapturedTextRowDto(
                "provider", CdsExportPublicProviderSourceReader.key(number), row);
    }

    static CdsExportCapturedHospitalReportFiles.File file(
            int report, CdsExportHospitalReportFileSourceReader.State state, byte[] content) {
        CdsExportCapturedFieldDto field = null;
        if (content != null)
            field =
                    new CdsExportCapturedFieldDto(
                            "hrm_report_files",
                            CdsExportPatientTableSourceReader.key(report),
                            "content",
                            CdsExportCapturedFieldDto.Kind.BINARY,
                            0,
                            content.length,
                            "d".repeat(64));
        return new CdsExportCapturedHospitalReportFiles.File(
                report,
                state == CdsExportHospitalReportFileSourceReader.State.NOT_RECORDED
                        ? null
                        : "LabUpload.demo-" + report + ".xml.1690313901104",
                state,
                field,
                CdsExportPatientTableSourceReader.key(report));
    }

    private static Map<String, CdsExportCapturedTextRowDto> providers() {
        return Map.of(
                "101", provider("101", "Ada", "Reviewer", "123456"),
                "102", provider("102", "Bea", "Inbox", "234567"));
    }

    private static CdsExportHospitalReportMapper.Result mapped(
            CdsExportHospitalReportMapper.Source source,
            CdsExportHospitalReportMessage.Parsed parsed,
            CdsExportHospitalReportMapper.Content content)
            throws Exception {
        var result =
                new CdsExportHospitalReportMapper().map(source, parsed, content, providers(), ZONE);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(result.rows(), result.fields(), 1_000_000, () -> {});
        return result;
    }

    @Test
    void shouldMapTheStoredMessageToStandardReportElements_forATextReport() throws Exception {
        byte[] message = fixture().getBytes(StandardCharsets.UTF_8);
        var parsed = CdsExportHospitalReportMessage.parse(message);
        var source =
                new CdsExportHospitalReportMapper.Source(
                        PATIENT,
                        reportRow(5, report(5)),
                        List.of(link(9, 5)),
                        List.of(comment(11, 5, "0", "Booked a follow-up ultrasound")),
                        List.of(subClass(12, 5, "1")),
                        List.of(),
                        file(5, CdsExportHospitalReportFileSourceReader.State.READ, message));
        var result = mapped(source, parsed, CdsExportHospitalReportMapper.Content.text());
        assertThat(result.text())
                .containsEntry("Media", "Download")
                .containsEntry("Format", "Text")
                .containsEntry("FileExtensionAndVersion", ".txt")
                .containsEntry("Class", "Diagnostic Imaging Report")
                .containsEntry("SubClass", "X-Ray")
                .containsEntry("SourceFacility", "DEMO")
                .containsEntry("SendingFacilityId", "DEMO")
                .containsEntry("SendingFacilityReport", "DEMO-DI-0001")
                .containsEntry("HRMResultStatus", "S")
                .containsEntry("MessageUniqueID", "demo-hrm-diagnostic-imaging-0001");
        assertThat(result.text().get("TextContent")).startsWith("SEED-HRM-ATTACHMENT-MARKER");
        // A message time with no offset is a clinic wall-clock time.
        assertThat(result.dates().get("EventDateTime").lexical())
                .isEqualTo("2024-01-18T10:15:00-05:00");
        assertThat(result.dates().get("ReceivedDateTime").lexical())
                .isEqualTo("2024-01-18T12:00:00-05:00");
        assertThat(result.author()).containsExactly("Demo", "Radiologist");
        assertThat(result.recipient()).containsExactly("Demo", "Provider");
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().getFirst().mnemonic()).isEqualTo("CXR");
        String notes = result.text().get("Notes");
        assertThat(notes)
                .startsWith(CdsExportHospitalReportMapper.NOTES_HEADER)
                .contains(
                        "Booked a follow-up ultrasound",
                        "ANK3LX",
                        "DeliverToUserID",
                        "\"messageFile\":\"READ\"",
                        "\"numDuplicatesReceived\":\"1\"")
                // Local storage and duplicate-detection digests are not source context.
                .doesNotContain("LabUpload.demo-5", "9bc35e1acf0ffb30f3a49defa8a9cfa2");
        // The comment author's public name travels with the comment.
        assertThat(notes).contains("\"lastName\":\"Reviewer\"");
        var content =
                result.fields().entrySet().stream()
                        .filter(entry -> entry.getKey().table().equals("hrm_report_files"))
                        .filter(entry -> entry.getKey().column().equals("content"))
                        .findFirst()
                        .orElseThrow()
                        .getValue();
        assertThat(content.destination()).isEqualTo("Reports/Content");
        assertThat(content.outcome()).isEqualTo(Outcome.MAPPED);
    }

    @Test
    void shouldExportASignOffAsReportReviewed_andKeepItsExactTimeInNotes() throws Exception {
        byte[] message = fixture().getBytes(StandardCharsets.UTF_8);
        var source =
                new CdsExportHospitalReportMapper.Source(
                        PATIENT,
                        reportRow(5, report(5)),
                        List.of(link(9, 5)),
                        List.of(),
                        List.of(),
                        List.of(
                                route(21, 5, "101", "1", "2024-01-19 16:45:00"),
                                route(22, 5, "102", "0", null),
                                route(23, 5, "-1", null, null),
                                // An undated sign-off stays whole in Notes.
                                route(24, 5, "102", "1", null)),
                        file(5, CdsExportHospitalReportFileSourceReader.State.READ, message));
        var result =
                mapped(
                        source,
                        CdsExportHospitalReportMessage.parse(message),
                        CdsExportHospitalReportMapper.Content.text());
        assertThat(result.reviewers()).hasSize(1);
        var reviewed = result.reviewers().getFirst();
        assertThat(reviewed.provider().lastName()).isEqualTo("Reviewer");
        assertThat(reviewed.provider().ohipNumber()).isEqualTo("123456");
        assertThat(reviewed.date().lexical()).isEqualTo("2024-01-19");
        assertThat(result.text().get("Notes"))
                .contains("2024-01-19 16:45:00", "\"signOffs\"", "\"lastName\":\"Inbox\"");
        var unsigned =
                result.fields().entrySet().stream()
                        .filter(entry -> entry.getKey().table().equals("HRMDocumentToProvider"))
                        .filter(
                                entry ->
                                        entry.getKey()
                                                .recordKey()
                                                .equals(CdsExportPatientTableSourceReader.key(22)))
                        .map(entry -> entry.getValue().outcome())
                        .distinct()
                        .toList();
        assertThat(unsigned).containsExactly(Outcome.OPERATIONAL);
    }

    @Test
    void shouldExportTheRecordedDetails_whenTheStoredMessageIsMissing() throws Exception {
        var row = report(6);
        row.put("className", "Medical Records Report");
        row.put("reportType", "Medical Records Report");
        row.put("subClassName", "96874-3-COVID19 Immunization note");
        var source =
                new CdsExportHospitalReportMapper.Source(
                        PATIENT,
                        reportRow(6, row),
                        List.of(link(9, 6)),
                        List.of(),
                        List.of(subClass(12, 6, "1")),
                        List.of(),
                        file(6, CdsExportHospitalReportFileSourceReader.State.MISSING, null));
        var result = mapped(source, null, CdsExportHospitalReportMapper.Content.none());
        assertThat(result.text())
                .containsEntry("Format", "Text")
                .containsEntry("Class", "Medical Records Report")
                .containsEntry("SubClass", "96874-3-COVID19 Immunization note")
                .containsEntry("SendingFacilityReport", "DEMO-DI-0001")
                .containsEntry("HRMResultStatus", "S")
                .doesNotContainKeys("TextContent", "FilePath", "MessageUniqueID");
        assertThat(result.dates().get("EventDateTime").lexical())
                .isEqualTo("2024-01-18T10:15:00-05:00");
        assertThat(result.dates().get("ReceivedDateTime").lexical())
                .isEqualTo("2024-01-18T12:05:00-05:00");
        // The accompanying subclasses CARLOS kept are its OBR content.
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().getFirst().observed().lexical()).isEqualTo("2024-01-18");
        assertThat(result.text().get("Notes"))
                .startsWith("The stored HRM message file of this report could not be read (MISSING)")
                .contains("\"messageFile\":\"MISSING\"");
    }

    @Test
    void shouldLeaveADeletedCommentOut_withEveryFieldAccounted() throws Exception {
        byte[] message = fixture().getBytes(StandardCharsets.UTF_8);
        var source =
                new CdsExportHospitalReportMapper.Source(
                        PATIENT,
                        reportRow(5, report(5)),
                        List.of(link(9, 5), link(10, 5)),
                        List.of(
                                comment(11, 5, "1", "Wrong patient, deleted"),
                                comment(12, 5, null, "Legacy comment with no deleted flag")),
                        List.of(),
                        List.of(),
                        file(5, CdsExportHospitalReportFileSourceReader.State.READ, message));
        var result =
                mapped(
                        source,
                        CdsExportHospitalReportMessage.parse(message),
                        CdsExportHospitalReportMapper.Content.text());
        assertThat(result.text().get("Notes"))
                .doesNotContain("Wrong patient, deleted")
                .contains("Legacy comment with no deleted flag");
        assertThat(
                        result.fields().entrySet().stream()
                                .filter(entry -> entry.getKey().table().equals("HRMDocumentComment"))
                                .filter(
                                        entry ->
                                                entry.getKey()
                                                        .recordKey()
                                                        .equals(
                                                                CdsExportPatientTableSourceReader.key(
                                                                        11)))
                                .map(entry -> entry.getValue().rule())
                                .distinct()
                                .toList())
                .containsExactly("DELETED_HRM_COMMENT_NOT_SHOWN_IN_CHART");
    }

    @Test
    void shouldAttachAStoredFileThatIsNoHrmMessage_asStored() throws Exception {
        byte[] stored = "not an HRM message".getBytes(StandardCharsets.UTF_8);
        var source =
                new CdsExportHospitalReportMapper.Source(
                        PATIENT,
                        reportRow(5, report(5)),
                        List.of(link(9, 5)),
                        List.of(),
                        List.of(),
                        List.of(),
                        file(5, CdsExportHospitalReportFileSourceReader.State.READ, stored));
        assertThatThrownBy(() -> CdsExportHospitalReportMessage.parse(stored))
                .isInstanceOf(CdsExportHospitalReportMessage.Unreadable.class);
        var result =
                mapped(
                        source,
                        null,
                        new CdsExportHospitalReportMapper.Content(
                                CdsExportHospitalReportMapper.ContentKind.MESSAGE_FILE,
                                "Reports/" + "e".repeat(64) + ".xml",
                                "xml"));
        assertThat(result.text())
                .containsEntry("Format", "Binary")
                .containsEntry("FileExtensionAndVersion", "xml")
                .containsEntry("FilePath", "Reports/" + "e".repeat(64) + ".xml")
                .containsEntry("Class", "Diagnostic Imaging Report");
        assertThat(result.text().get("Notes")).startsWith("The stored HRM message of this report");
    }

    @Test
    void shouldHoldThePatient_forAMessageHoldingSeveralReports() throws Exception {
        String twice =
                fixture()
                        .replaceFirst(
                                "(?s)(<ReportsReceived>.*?</ReportsReceived>)", "$1\n    $1");
        byte[] message = twice.getBytes(StandardCharsets.UTF_8);
        var parsed = CdsExportHospitalReportMessage.parse(message);
        assertThat(parsed.reports()).hasSize(2);
        var source =
                new CdsExportHospitalReportMapper.Source(
                        PATIENT,
                        reportRow(5, report(5)),
                        List.of(link(9, 5)),
                        List.of(),
                        List.of(),
                        List.of(),
                        file(5, CdsExportHospitalReportFileSourceReader.State.READ, message));
        assertThatThrownBy(
                        () ->
                                new CdsExportHospitalReportMapper()
                                        .map(
                                                source,
                                                parsed,
                                                CdsExportHospitalReportMapper.Content.text(),
                                                providers(),
                                                ZONE))
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_HRM_REPORT_MULTIPLE_REPORTS_REQUIRES_REVIEW");
    }

    @Test
    void shouldHoldThePatient_forASignOffStateCarlosNeverWrites() throws Exception {
        byte[] message = fixture().getBytes(StandardCharsets.UTF_8);
        var source =
                new CdsExportHospitalReportMapper.Source(
                        PATIENT,
                        reportRow(5, report(5)),
                        List.of(link(9, 5)),
                        List.of(),
                        List.of(),
                        List.of(route(21, 5, "101", "2", "2024-01-19 16:45:00")),
                        file(5, CdsExportHospitalReportFileSourceReader.State.READ, message));
        assertThatThrownBy(
                        () ->
                                new CdsExportHospitalReportMapper()
                                        .map(
                                                source,
                                                CdsExportHospitalReportMessage.parse(message),
                                                CdsExportHospitalReportMapper.Content.text(),
                                                providers(),
                                                ZONE))
                .hasMessage("CDS_EXPORT_HRM_SIGN_OFF_STATUS_REQUIRES_REVIEW");
    }

    @Test
    void shouldKeepAMessageDateOffset_andOmitADateCdsCannotCarry() throws Exception {
        var offset =
                new CdsExportHospitalReportMessage.When(
                        "DateTime",
                        "2024-01-18T10:15:00Z",
                        javax.xml.datatype.DatatypeFactory.newInstance()
                                .newXMLGregorianCalendar("2024-01-18T10:15:00Z"));
        assertThat(CdsExportHospitalReportMessage.date(offset, ZONE).lexical())
                .isEqualTo("2024-01-18T10:15:00Z");
        var month =
                new CdsExportHospitalReportMessage.When(
                        "YearMonth",
                        "2024-01",
                        javax.xml.datatype.DatatypeFactory.newInstance()
                                .newXMLGregorianCalendar("2024-01"));
        assertThat(CdsExportHospitalReportMessage.date(month, ZONE).lexical()).isEqualTo("2024-01");
        var ancient =
                new CdsExportHospitalReportMessage.When(
                        "FullDate",
                        "10000-01-01",
                        javax.xml.datatype.DatatypeFactory.newInstance()
                                .newXMLGregorianCalendar("10000-01-01"));
        assertThat(CdsExportHospitalReportMessage.date(ancient, ZONE)).isNull();
    }
}
