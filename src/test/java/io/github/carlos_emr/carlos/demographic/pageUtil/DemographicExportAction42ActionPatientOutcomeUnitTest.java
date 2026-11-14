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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogAction;

/**
 * What the legacy export does with each chart of the requested set: every chart is exported, or
 * the export's own log and the response say which was not and why. No chart goes missing quietly,
 * one chart's failure does not cost the charts after it their export, and a download that did not
 * reach the client is not reported or audited as a success.
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action charts: exported, or recorded and reported")
class DemographicExportAction42ActionPatientOutcomeUnitTest extends DemographicExportExecuteUnitTestBase {

    private static final Path EXPORT_JSP = Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicExport.jsp");

    @Test
    @DisplayName("should export a chart that has no most responsible provider and list it in the ReadMe")
    void shouldExportTheChartAndListItInTheReadMe_whenItHasNoMostResponsibleProvider() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea").setProviderNo(null);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(response).setHeader("X-Export-Status", "success");
        Map<String, byte[]> archive = streamedArchive();
        // A chart without an MRP has no provider folder: its file is at the top of the archive.
        assertThat(archive).containsKeys(patientXml(101, "FAKE-Ann"), "FAKE-Bea_FAKE-Patient_102_15031980.xml");
        String readMe = text(archive, "ReadMe.txt");
        assertThat(readMe).contains("\tFAKE Doctor: 1", "\tNo most responsible physician: 1");
        assertThat(readMe).contains("101-102").doesNotContain("Charts exported incompletely");
    }

    @Test
    @DisplayName("should record a chart whose file cannot be written, export the later charts and report the archive incomplete")
    void shouldRecordTheChartAndExportTheLaterOnes_whenItsFileCannotBeWritten() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        chart(103, "FAKE-Cyd");
        // Something already sits where chart 102's file belongs, so that one file cannot be written.
        duringExportOf(102, directory ->
                Files.createDirectory(directory.resolve("FAKE-Bea_FAKE-Patient_102_15031980.xml")));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        Map<String, byte[]> archive = streamedArchive();
        // The loop used to stop at the failure: 103 was dropped with 102 and the archive called a success.
        assertThat(archive).containsKeys(patientXml(101, "FAKE-Ann"), patientXml(103, "FAKE-Cyd"));
        assertThat(archive).doesNotContainKey(patientXml(102, "FAKE-Bea"));

        // Never a plain success: the status and headers tell the operator which chart is missing.
        verify(response).setHeader("X-Export-Status", "incomplete");
        verify(response, never()).setHeader("X-Export-Status", "success");
        verify(response).setHeader("X-Export-Incomplete-Count", "1");
        verify(response).setHeader("X-Export-Incomplete-Charts", "102");

        String log = text(archive, "ExportEvent.log");
        assertThat(log).containsPattern(Pattern.compile(
                "Error! Patient 102 was NOT exported: its export file could not be written \\(\\w+(: [^)]+)?\\)"));
        // The log's table has a row for each chart in the archive, and none for the one that is not.
        assertThat(log).containsPattern("(?m)^101\\s+\\|").containsPattern("(?m)^103\\s+\\|")
                .doesNotContainPattern("(?m)^102\\s+\\|");
        // By chart number and reason only: the file name, which holds the patient's name, is not repeated.
        assertThat(log).doesNotContain("FAKE-Bea");
        String readMe = text(archive, "ReadMe.txt");
        assertThat(readMe).contains("(See ExportEvent.log for detail)");
        assertThat(readMe).containsPattern("Charts exported incompletely\\s*: 102");
        assertThat(auditData()).contains("outcome=incomplete").doesNotContain("outcome=success");
        assertThat(exportDirectoriesLeft()).isEmpty();
    }

    @Test
    @DisplayName("should fail the whole export when a chart's file cannot be written and nothing else can be either")
    void shouldFailTheExportLoudly_whenNothingMoreCanBeWritten() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        duringExportOf(101, directory ->
                Files.createDirectory(directory.resolve("FAKE-Ann_FAKE-Patient_101_15031980.xml")));

        // The write probe fails too: the disk refuses every write, not just this chart's file.
        try (MockedStatic<DemographicExportAction42Action> probe = mockStatic(DemographicExportAction42Action.class,
                withSettings().defaultAnswer(call -> "exportDirectoryWritable".equals(call.getMethod().getName())
                        ? Boolean.FALSE : call.callRealMethod()))) {
            assertThatThrownBy(() -> action.execute()).isInstanceOf(IOException.class);
        }

        verify(response, never()).getOutputStream();
        assertThat(nothingStreamed()).isTrue();
        assertThat(auditData()).contains("outcome=error");
        assertThat(exportDirectoriesLeft()).isEmpty();
    }

    @Test
    @DisplayName("should tell a directory that can be written to from one that cannot")
    void shouldProbeTheExportDirectoryWithARealWrite_forItsVerdict() throws Exception {
        Path writable = Files.createDirectory(root.resolve("writable"));
        Path file = Files.writeString(root.resolve("a-file"), "x", StandardCharsets.UTF_8);

        assertThat(DemographicExportAction42Action.exportDirectoryWritable(writable.toFile())).isTrue();
        assertThat(writable).isEmptyDirectory();
        assertThat(DemographicExportAction42Action.exportDirectoryWritable(root.resolve("gone").toFile())).isFalse();
        assertThat(DemographicExportAction42Action.exportDirectoryWritable(file.toFile())).isFalse();
    }

    @Test
    @DisplayName("should record a chart whose file cannot be named, export the later charts and report the archive incomplete")
    void shouldRecordTheChartAndExportTheLaterOnes_whenItsFileCannotBeNamed() throws Exception {
        chart(101, "FAKE-Ann");
        // The chart's file is named and filed under its MRP, whose lookup fails for this chart.
        chart(102, "FAKE-Bea").setProviderNo("777");
        chart(103, "FAKE-Cyd");
        when(providerDao.getProvider("777")).thenThrow(new IllegalStateException("synthetic FAKE-Bea lookup failure"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        Map<String, byte[]> archive = streamedArchive();
        assertThat(archive).containsKeys(patientXml(101, "FAKE-Ann"), patientXml(103, "FAKE-Cyd"));
        assertThat(archive.keySet()).noneMatch(name -> name.contains("_102_"));
        verify(response).setHeader("X-Export-Status", "incomplete");
        verify(response).setHeader("X-Export-Incomplete-Count", "1");
        verify(response).setHeader("X-Export-Incomplete-Charts", "102");
        // It used to be skipped with a server log line only.
        assertThat(text(archive, "ExportEvent.log")).contains(
                "Error! Patient 102 was NOT exported: its export file could not be named (IllegalStateException)")
                .doesNotContain("synthetic FAKE-Bea")
                .containsPattern("(?m)^101\\s+\\|").containsPattern("(?m)^103\\s+\\|")
                .doesNotContainPattern("(?m)^102\\s+\\|");
        assertThat(text(archive, "ReadMe.txt")).containsPattern("Charts exported incompletely\\s*: 102");
        assertThat(auditData()).contains("outcome=incomplete");
    }

    @Test
    @DisplayName("should fail the whole export when the export directory is gone before a chart's file is named")
    void shouldFailTheExportLoudly_whenTheExportDirectoryIsGoneBeforeAChartIsWritten() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        duringExportOf(101, DemographicExportAction42ActionPatientOutcomeUnitTest::deleteRecursively);

        // Not chart 101's fault, and chart 102 could not be written either: no chart-by-chart record.
        assertThatThrownBy(() -> action.execute()).isInstanceOf(IOException.class)
                .hasMessage("Legacy export directory is no longer available");

        verify(response, never()).getOutputStream();
        assertThat(nothingStreamed()).isTrue();
        assertThat(auditData()).contains("outcome=error", "error=IOException");
    }

    @Test
    @DisplayName("should report a document that was not exported as an incomplete archive, with its chart")
    void shouldReportTheArchiveIncomplete_whenADocumentWasNotExported() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        document(101, "11", "kept.pdf", "%PDF-kept".getBytes(StandardCharsets.ISO_8859_1));
        document(102, "21", "lost.pdf", "%PDF-lost".getBytes(StandardCharsets.ISO_8859_1));
        Files.delete(documentDir.resolve("lost.pdf"));

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        verify(response).setHeader("X-Export-Status", "incomplete");
        verify(response).setHeader("X-Export-Incomplete-Count", "1");
        verify(response).setHeader("X-Export-Incomplete-Charts", "102");
        Map<String, byte[]> archive = streamedArchive();
        // Chart 102 itself is exported; one of its documents is not, and the log says which.
        assertThat(archive).containsKeys(patientXml(101, "FAKE-Ann"), patientXml(102, "FAKE-Bea"));
        assertThat(text(archive, "ExportEvent.log")).contains("Error! Document \"lost.pdf\" does not exist!");
        assertThat(auditData()).contains("outcome=incomplete");
    }

    @Test
    @DisplayName("should report the archive incomplete when a patient directive refuses a chart")
    void shouldReportTheArchiveIncomplete_whenAPatientDirectiveRefusesAChart() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        chart(103, "FAKE-Cyd");
        refusedByDirective.add("102");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        Map<String, byte[]> archive = streamedArchive();
        assertThat(archive).containsKeys(patientXml(101, "FAKE-Ann"), patientXml(103, "FAKE-Cyd"));
        assertThat(archive.keySet()).noneMatch(name -> name.contains("_102_"));
        assertThat(text(archive, "ExportEvent.log")).contains("Unable to export patient 102 due to Patient Directive");
        // The log always said so; the response used to call the archive a success all the same.
        verify(response).setHeader("X-Export-Status", "incomplete");
        verify(response).setHeader("X-Export-Incomplete-Charts", "102");
    }

    @Test
    @DisplayName("should say that nothing was exported, and for which charts, when no chart could be")
    void shouldSayThatNothingWasExported_whenNoChartCouldBe() throws Exception {
        chart(101, "FAKE-Ann").setProviderNo("777");
        chart(102, "FAKE-Bea").setProviderNo("777");
        when(providerDao.getProvider("777")).thenThrow(new IllegalStateException("synthetic lookup failure"));

        // Nothing was written to the response, so the export page is shown with its error.
        assertThat(action.execute()).isEqualTo("fail");

        verify(response).setHeader("X-Export-Status", "error");
        verify(response).setHeader("X-Export-Error", DemographicExportAction42Action.NOTHING_EXPORTED_CODE);
        verify(response).setHeader("X-Export-Incomplete-Count", "2");
        verify(response).setHeader("X-Export-Incomplete-Charts", "101,102");
        verify(response, never()).getOutputStream();
        assertThat(nothingStreamed()).isTrue();
        assertThat(auditData()).contains("outcome=fail");
        String jsp = Files.readString(EXPORT_JSP, StandardCharsets.UTF_8);
        assertThat(jsp).contains("data-export-error-code=\"" + DemographicExportAction42Action.NOTHING_EXPORTED_CODE + "\"");
        assertThat(DemographicExportAction42Action.NOTHING_EXPORTED_CODE).matches("[A-Za-z]+");
    }

    @Test
    @DisplayName("should note a contact-only record in the export log instead of passing over it without a word")
    void shouldNoteTheRecordInTheExportLog_whenItIsContactOnly() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea").setPatientStatus("Contact-only");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        Map<String, byte[]> archive = streamedArchive();
        assertThat(archive).containsKey(patientXml(101, "FAKE-Ann"));
        assertThat(archive.keySet()).noneMatch(name -> name.contains("_102_"));
        assertThat(text(archive, "ExportEvent.log"))
                .contains("Patient 102 is a Contact-only record and was not exported");
        // A contact is not a patient: every chart of the set is in the archive.
        verify(response).setHeader("X-Export-Status", "success");
    }

    @Test
    @DisplayName("should audit a download that breaks off as a failure and still end Struts processing")
    void shouldAuditAStreamFailure_whenTheDownloadBreaksOff() throws Exception {
        chart(101, "FAKE-Ann");
        failStreamAfterBytes = 64;

        String result = action.execute();

        // Part of the archive is on the wire: no page may follow it.
        assertThat(result).isEqualTo(ActionSupport.NONE);
        // It used to be audited as outcome=success, as if the operator had the archive.
        assertThat(auditData()).contains("outcome=stream_failed").doesNotContain("outcome=success");
        assertThat(exportDirectoriesLeft()).isEmpty();
    }

    @Test
    @DisplayName("should answer with an error, not an empty success, when the download cannot start")
    void shouldAnswerWithAnError_whenTheDownloadCannotStart() throws Exception {
        chart(101, "FAKE-Ann");

        String result;
        try (MockedStatic<Util> downloads = mockStatic(Util.class, withSettings().defaultAnswer(call ->
                "downloadFile".equals(call.getMethod().getName()) ? Util.Download.NOT_STARTED : call.callRealMethod()))) {
            result = action.execute();
        }

        // Nothing was written, so the export page can be shown with its error.
        assertThat(result).isEqualTo("fail");
        InOrder status = inOrder(response);
        status.verify(response).setHeader("X-Export-Status", "success");
        status.verify(response).setHeader("X-Export-Status", "error");
        verify(response, never()).getOutputStream();
        assertThat(auditData()).contains("outcome=stream_not_started").doesNotContain("outcome=success");
    }

    @Test
    @DisplayName("should say how a download ended and leave the response alone when it cannot start")
    void shouldSayHowTheDownloadEnded_forEachWayItCanEnd() throws Exception {
        Files.writeString(tmpDir.resolve("archive.zip"), "PK synthetic archive", StandardCharsets.ISO_8859_1);

        assertThat(Util.downloadFile("archive.zip", tmpDir.toString(), response)).isEqualTo(Util.Download.COMPLETE);
        verify(response).setContentLengthLong(20);

        org.mockito.Mockito.clearInvocations(response);
        assertThat(Util.downloadFile("missing.zip", tmpDir.toString(), response)).isEqualTo(Util.Download.NOT_STARTED);
        assertThat(Util.downloadFile("", tmpDir.toString(), response)).isEqualTo(Util.Download.NOT_STARTED);
        // No status, no header, no 404 sent behind the caller's back.
        org.mockito.Mockito.verifyNoInteractions(response);
        assertThat(Util.downloadFile("archive.zip", tmpDir.toString(), null)).isEqualTo(Util.Download.NOT_STARTED);

        failStreamAfterBytes = 5;
        assertThat(Util.downloadFile("archive.zip", tmpDir.toString(), response)).isEqualTo(Util.Download.INTERRUPTED);
    }

    @Test
    @DisplayName("should put only whole chart numbers in the incomplete-charts header, within its length")
    void shouldListWholeChartNumbersOnly_withinTheHeaderLength() {
        assertThat(DemographicExportAction42Action.incompleteChartsHeader(List.of("101", "?", "102", "10 3", "104")))
                .isEqualTo("101,102,104");
        List<String> many = new ArrayList<>();
        for (int chart = 100000; chart < 100500; chart++) {
            many.add(String.valueOf(chart));
        }

        String header = DemographicExportAction42Action.incompleteChartsHeader(many);

        assertThat(header.length()).isLessThanOrEqualTo(DemographicExportAction42Action.MAX_INCOMPLETE_CHARTS_HEADER_LENGTH);
        assertThat(header).matches("[0-9]{6}(,[0-9]{6})*").startsWith("100000,100001,");
        // The count header still carries the full number; the log and the ReadMe list every chart.
        assertThat(header.split(",").length).isLessThan(many.size());
    }

    @Test
    @DisplayName("export page should download an incomplete archive and warn instead of announcing success")
    void shouldWireTheIncompleteStatusIntoTheExportPage_forTheOperator() throws Exception {
        String jsp = Files.readString(EXPORT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("var incomplete = exportStatus === '"
                + DemographicExportAction42Action.EXPORT_STATUS_INCOMPLETE + "';");
        assertThat(jsp).contains("if ((exportStatus === 'success' || incomplete) && response.ok) {");
        assertThat(jsp).contains(
                "response.headers.get('" + DemographicExportAction42Action.EXPORT_INCOMPLETE_COUNT_HEADER + "')",
                "response.headers.get('" + DemographicExportAction42Action.EXPORT_INCOMPLETE_CHARTS_HEADER + "')");
        // The success banner is shown only for a complete archive.
        int warn = jsp.indexOf("showExportIncomplete(response.headers.get(");
        int success = jsp.indexOf("document.getElementById('exportSuccessMessage').style.display = 'block';");
        assertThat(warn).isPositive().isLessThan(success);
        assertThat(jsp.substring(warn, success)).contains("} else {");
        // Header values reach the page as text, after being checked to be plain numbers.
        assertThat(jsp).contains("document.getElementById('exportIncompleteDetail').textContent = text;");
        String english = Files.readString(Path.of("src/main/resources/oscarResources_en.properties"), StandardCharsets.UTF_8);
        for (String key : List.of("exportIncomplete", "exportIncompleteCharts", "exportIncompleteMore")) {
            assertThat(jsp).contains("<fmt:message key=\"demographic.demographicexport." + key + "\"/>");
            assertThat(english).contains("\ndemographic.demographicexport." + key + "=");
        }
    }

    private String auditData() {
        ArgumentCaptor<OscarLog> audit = ArgumentCaptor.forClass(OscarLog.class);
        logActionMock.verify(() -> LogAction.addLogSynchronous(audit.capture()));
        return audit.getValue().getData();
    }

    private static String text(Map<String, byte[]> archive, String entry) {
        assertThat(archive).containsKey(entry);
        return new String(archive.get(entry), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
