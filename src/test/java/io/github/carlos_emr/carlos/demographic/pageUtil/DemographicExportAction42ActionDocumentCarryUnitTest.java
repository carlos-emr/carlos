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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import cds.OmdCdsDocument;
import cds.ReportsDocument.Reports;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LegacyXmlBeans;

/**
 * How the legacy export puts a chart's documents in the archive. Every document is in it, either
 * embedded in its patient's XML or carried as a file the XML names. The embedding limits only
 * choose between the two. A document that cannot be exported is named in the export's own log
 * with its patient and the reason, and costs no other document or patient its export.
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action documents: embedded, carried, or recorded")
class DemographicExportAction42ActionDocumentCarryUnitTest extends DemographicExportExecuteUnitTestBase {

    private static final String DOCUMENT_LIMIT = "cds.legacyExport.maxEmbeddedDocumentBytes";
    private static final String PATIENT_BUDGET = "cds.legacyExport.maxEmbeddedPatientBytes";

    /**
     * One exported report: the embedded bytes (as Latin-1 text, so two reports compare by
     * content), or the name of the file that carries them.
     */
    private record Exported(String embedded, String filePath) {
    }

    @Test
    @DisplayName("should embed a chart's documents up to the patient budget and carry the rest as named files")
    void shouldEmbedUpToThePatientBudgetAndCarryTheRest_whenAChartExceedsIt() throws Exception {
        property(DOCUMENT_LIMIT, "100");
        property(PATIENT_BUDGET, "250");
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        byte[] first = content('a', 100);
        byte[] second = content('b', 100);
        byte[] third = content('c', 100);
        byte[] fourth = content('d', 40);
        byte[] oversized = content('e', 300);
        document(101, "11", "first.pdf", first);
        document(101, "12", "second.pdf", second);
        document(101, "13", "third.pdf", third);
        document(101, "14", "fourth.pdf", fourth);
        document(101, "15", "oversized.pdf", oversized);
        byte[] other1 = content('f', 100);
        byte[] other2 = content('g', 100);
        document(102, "21", "other1.pdf", other1);
        document(102, "22", "other2.pdf", other2);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        Map<String, byte[]> archive = streamedArchive();
        List<Exported> ann = reports(archive, patientXml(101, "FAKE-Ann"));
        // Five documents in, five reports out: none is left out by either limit.
        assertThat(ann).hasSize(5);
        assertThat(ann.get(0).embedded()).isEqualTo(latin1(first));
        assertThat(ann.get(1).embedded()).isEqualTo(latin1(second));
        // 200 bytes are embedded; the third would make 300, past the 250 budget, so it and every
        // later document of this patient is carried, even the fourth, which alone would still fit.
        assertThat(ann.get(2)).isEqualTo(new Exported(null, "document_101_13.pdf"));
        assertThat(ann.get(3)).isEqualTo(new Exported(null, "document_101_14.pdf"));
        assertThat(ann.get(4)).isEqualTo(new Exported(null, "document_101_15.pdf"));
        // Each FilePath names a file that is in the archive, beside the patient's XML, complete.
        assertThat(archive.get(PROVIDER_FOLDER + "/document_101_13.pdf")).isEqualTo(third);
        assertThat(archive.get(PROVIDER_FOLDER + "/document_101_14.pdf")).isEqualTo(fourth);
        assertThat(archive.get(PROVIDER_FOLDER + "/document_101_15.pdf")).isEqualTo(oversized);

        // The running total starts again for the next patient.
        List<Exported> bea = reports(archive, patientXml(102, "FAKE-Bea"));
        assertThat(bea).hasSize(2);
        assertThat(bea.get(0).embedded()).isEqualTo(latin1(other1));
        assertThat(bea.get(1).embedded()).isEqualTo(latin1(other2));
        assertThat(archive.keySet()).noneMatch(name -> name.contains("document_102_"));

        verify(response).setHeader("X-Export-Status", "success");
        verify(response, never()).setHeader("X-Export-Status", "incomplete");
        // Nothing went wrong, so the export log records nothing against any document.
        assertThat(text(archive, "ExportEvent.log"))
                .doesNotContain("NOT exported", ".pdf", "XSD validation", "could not be re-read");
        assertThat(text(archive, "ReadMe.txt")).doesNotContain("See ExportEvent.log");
        assertThat(exportDirectoriesLeft()).isEmpty();
    }

    @Test
    @DisplayName("should embed every document when the chart stays within the patient budget")
    void shouldEmbedEveryDocument_whenTheChartStaysWithinThePatientBudget() throws Exception {
        property(DOCUMENT_LIMIT, "100");
        property(PATIENT_BUDGET, "200");
        chart(101, "FAKE-Ann");
        document(101, "11", "first.pdf", content('a', 100));
        document(101, "12", "second.pdf", content('b', 100));

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        Map<String, byte[]> archive = streamedArchive();
        assertThat(reports(archive, patientXml(101, "FAKE-Ann")))
                .hasSize(2).allMatch(report -> report.embedded() != null && report.filePath() == null);
        assertThat(archive.keySet()).noneMatch(name -> name.contains("document_"));
    }

    @Test
    @DisplayName("should record a document whose id cannot name its file and export everything else")
    void shouldRecordTheDocumentAndExportTheRest_whenItsIdIsNotNumeric() throws Exception {
        property(DOCUMENT_LIMIT, "100");
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        byte[] small = content('a', 50);
        byte[] carried = content('d', 300);
        document(101, "11", "small.pdf", small);
        document(101, "12x", "badid.pdf", content('b', 300));
        document(101, null, "noid.pdf", content('c', 300));
        document(101, "14", "carried.pdf", carried);
        byte[] other = content('f', 300);
        document(102, "21", "other.pdf", other);

        String result = action.execute();

        // The export is not aborted: both patients' files are produced and streamed.
        assertThat(result).isEqualTo(ActionSupport.NONE);
        Map<String, byte[]> archive = streamedArchive();
        List<Exported> ann = reports(archive, patientXml(101, "FAKE-Ann"));
        // No empty report stands in for a document that was not exported.
        assertThat(ann).containsExactly(new Exported(latin1(small), null), new Exported(null, "document_101_14.pdf"));
        assertThat(archive.get(PROVIDER_FOLDER + "/document_101_14.pdf")).isEqualTo(carried);
        assertThat(reports(archive, patientXml(102, "FAKE-Bea")))
                .containsExactly(new Exported(null, "document_102_21.pdf"));
        assertThat(archive.get(PROVIDER_FOLDER + "/document_102_21.pdf")).isEqualTo(other);

        // The export's own log names each document, its patient and the reason.
        String log = text(archive, "ExportEvent.log");
        assertThat(log).contains(
                "Error! Document \"badid.pdf\" (document id 12x) for Patient 101 was NOT exported: "
                        + "its document id is missing or not numeric, so its file could not be named",
                "Error! Document \"noid.pdf\" (no document id) for Patient 101 was NOT exported: "
                        + "its document id is missing or not numeric, so its file could not be named");
        assertThat(log).doesNotContain("small.pdf", "carried.pdf", "other.pdf");
        assertThat(text(archive, "ReadMe.txt")).contains("(See ExportEvent.log for detail)");
        // The archive is missing two documents of chart 101: the response says so.
        verify(response).setHeader("X-Export-Status", "incomplete");
        verify(response, never()).setHeader("X-Export-Status", "success");
        verify(response).setHeader("X-Export-Incomplete-Count", "1");
        verify(response).setHeader("X-Export-Incomplete-Charts", "101");
        assertThat(exportDirectoriesLeft()).isEmpty();
    }

    @Test
    @DisplayName("should still embed a document whose id is not numeric and record that its notes were not read")
    void shouldEmbedTheDocumentAndRecordItsNotes_whenAnEmbeddableDocumentHasANonNumericId() throws Exception {
        property(DOCUMENT_LIMIT, "100");
        property(PATIENT_BUDGET, "60");
        chart(101, "FAKE-Ann");
        byte[] first = content('a', 50);
        byte[] second = content('b', 50);
        document(101, "11", "first.pdf", first);
        // Past the budget, but it cannot be given a carried file name: the budget never leaves a
        // document out, so it is embedded.
        document(101, "7a", "second.pdf", second);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        Map<String, byte[]> archive = streamedArchive();
        assertThat(reports(archive, patientXml(101, "FAKE-Ann")))
                .containsExactly(new Exported(latin1(first), null), new Exported(latin1(second), null));
        assertThat(text(archive, "ExportEvent.log")).contains(
                "Error! Notes for Document \"second.pdf\" for Patient 101 were NOT exported: "
                        + "its document id is not numeric");
    }

    @Test
    @DisplayName("should record a document whose copy fails and export the other documents and patients")
    void shouldRecordTheDocumentAndExportTheRest_whenItsCopyFails() throws Exception {
        property(DOCUMENT_LIMIT, "100");
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        byte[] before = content('a', 300);
        byte[] after = content('c', 300);
        byte[] other = content('f', 300);
        document(101, "11", "before.pdf", before);
        document(101, "12", "full.pdf", content('b', 300));
        document(101, "13", "after.pdf", after);
        document(102, "21", "other.pdf", other);

        String result;
        // The disk fills while "full.pdf" is being copied; every other copy runs for real.
        try (MockedStatic<DemographicExportAction42Action> copies = failingCopy("full.pdf", carry -> {
            throw new FileSystemException("/synthetic/FAKE-path/full.pdf", null, "No space left on device");
        })) {
            result = action.execute();
        }

        assertThat(result).isEqualTo(ActionSupport.NONE);
        Map<String, byte[]> archive = streamedArchive();
        assertThat(reports(archive, patientXml(101, "FAKE-Ann"))).containsExactly(
                new Exported(null, "document_101_11.pdf"), new Exported(null, "document_101_13.pdf"));
        assertThat(archive.get(PROVIDER_FOLDER + "/document_101_11.pdf")).isEqualTo(before);
        assertThat(archive.get(PROVIDER_FOLDER + "/document_101_13.pdf")).isEqualTo(after);
        assertThat(archive).doesNotContainKey(PROVIDER_FOLDER + "/document_101_12.pdf");
        assertThat(reports(archive, patientXml(102, "FAKE-Bea")))
                .containsExactly(new Exported(null, "document_102_21.pdf"));
        assertThat(archive.get(PROVIDER_FOLDER + "/document_102_21.pdf")).isEqualTo(other);

        String log = text(archive, "ExportEvent.log");
        assertThat(log).contains(
                "Error! Document \"full.pdf\" (document id 12) for Patient 101 was NOT exported: "
                        + "the file could not be read or copied (FileSystemException: No space left on device)");
        // The reason comes from the failure's type and the system's text, never its path.
        assertThat(log).doesNotContain("/synthetic/FAKE-path");
        assertThat(text(archive, "ReadMe.txt")).contains("(See ExportEvent.log for detail)");
    }

    @Test
    @DisplayName("should fail the whole export when the export directory itself is gone")
    void shouldFailTheExportLoudly_whenTheExportDirectoryIsGone() throws Exception {
        property(DOCUMENT_LIMIT, "100");
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        document(101, "11", "gone.pdf", content('a', 300));
        document(102, "21", "other.pdf", content('f', 300));
        NoSuchFileException gone = new NoSuchFileException("/synthetic/FAKE-path");

        // Not this document's fault: the export has lost its working directory.
        try (MockedStatic<DemographicExportAction42Action> copies = failingCopy("gone.pdf", carry -> {
            deleteRecursively(((File) carry.getArgument(3)).toPath());
            throw gone;
        })) {
            assertThatThrownBy(() -> action.execute()).isSameAs(gone);
        }

        verify(response, never()).getOutputStream();
        assertThat(nothingStreamed()).isTrue();
        ArgumentCaptor<OscarLog> audit = ArgumentCaptor.forClass(OscarLog.class);
        logActionMock.verify(() -> LogAction.addLogSynchronous(audit.capture()));
        assertThat(audit.getValue().getData()).contains("outcome=error", "error=NoSuchFileException");
    }

    @Test
    @DisplayName("should return the page result, not NONE, when nothing was streamed")
    void shouldReturnThePageResult_whenNoArchiveWasStreamed() throws Exception {
        // TMP_DIR does not exist, so the export cannot make its directory and writes nothing.
        property("TMP_DIR", root.resolve("missing").toString());
        chart(101, "FAKE-Ann");

        assertThat(action.execute()).isEqualTo("fail");

        verify(response, never()).getOutputStream();
        assertThat(nothingStreamed()).isTrue();
    }

    @Test
    @DisplayName("should leave no partial copy behind when a document cannot be copied")
    void shouldLeaveNoPartialCopy_whenTheSourceCannotBeRead() throws Exception {
        Path export = Files.createDirectory(root.resolve("AbCdEfGh"));
        File missing = documentDir.resolve("missing.pdf").toFile();

        assertThatThrownBy(() -> DemographicExportAction42Action.carryOversizedDocument(missing, "101", "12", export.toFile()))
                .isInstanceOf(NoSuchFileException.class);

        assertThat(export).isEmptyDirectory();
    }

    @Test
    @DisplayName("should describe a document failure without the failure's own path text")
    void shouldDescribeAFailureByTypeAndReason_withoutItsPath() {
        assertThat(DemographicExportAction42Action.describeDocumentFailure(
                new FileSystemException("/synthetic/FAKE-path/scan.pdf", null, "No space left on device")))
                .isEqualTo("the file could not be read or copied (FileSystemException: No space left on device)");
        assertThat(DemographicExportAction42Action.describeDocumentFailure(new NoSuchFileException("/synthetic/FAKE-path")))
                .isEqualTo("the file could not be read or copied (NoSuchFileException)");
        assertThat(DemographicExportAction42Action.describeDocumentFailure(new IOException("/synthetic/FAKE-path unreadable")))
                .isEqualTo("the file could not be read or copied (IOException)");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(value = {
        "12,12", "' 12 ',12", "0012,0012", "12x,NULL", "../12,NULL", "'',NULL", "-1,NULL", "1.5,NULL",
        "1234567890123456789,NULL", "NULL,NULL"
    }, nullValues = "NULL")
    @DisplayName("should accept only a plain number as an id that can name a file")
    void shouldAcceptOnlyAPlainNumber_asAnIdThatNamesAFile(String id, String expected) {
        assertThat(DemographicExportAction42Action.numericId(id)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(value = {
        "NULL,268435456", "garbage,268435456", "0,268435456", "-5,268435456", "'',268435456",
        "1,1", "' 4096 ',4096", "1073741824,1073741824", "1073741825,1073741824", "99999999999999,1073741824"
    }, nullValues = "NULL")
    @DisplayName("should validate the patient budget like the document limit")
    void shouldValidateThePatientBudget_likeTheDocumentLimit(String configured, long expected) {
        if (configured != null) {
            property(PATIENT_BUDGET, configured);
        }

        assertThat(action.maxEmbeddedPatientBytes()).isEqualTo(expected);
        assertThat(DemographicExportAction42Action.DEFAULT_MAX_EMBEDDED_PATIENT_BYTES).isEqualTo(256L << 20);
    }

    /**
     * Runs the real exporter with one named document's copy replaced by a failure. Every other
     * static method of the action, and every other document's copy, runs for real.
     */
    private static MockedStatic<DemographicExportAction42Action> failingCopy(String fileName, Answer<?> failure) {
        return mockStatic(DemographicExportAction42Action.class, withSettings().defaultAnswer(call -> {
            if ("carryOversizedDocument".equals(call.getMethod().getName())
                    && fileName.equals(((File) call.getArgument(0)).getName())) {
                return failure.answer(call);
            }
            return call.callRealMethod();
        }));
    }

    private static List<Exported> reports(Map<String, byte[]> archive, String patientXml) throws Exception {
        assertThat(archive).containsKey(patientXml);
        OmdCdsDocument document = LegacyXmlBeans.parse(OmdCdsDocument.class, OmdCdsDocument.type,
                new String(archive.get(patientXml), StandardCharsets.UTF_8));
        List<Exported> exported = new ArrayList<>();
        for (Reports report : document.getOmdCds().getPatientRecord().getReportsArray()) {
            exported.add(new Exported(report.getContent() == null ? null : latin1(report.getContent().getMedia()),
                    report.getFilePath()));
        }
        return exported;
    }

    private static String text(Map<String, byte[]> archive, String entry) {
        assertThat(archive).containsKey(entry);
        return new String(archive.get(entry), StandardCharsets.UTF_8);
    }

    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static byte[] content(char filler, int length) {
        byte[] content = new byte[length];
        Arrays.fill(content, (byte) filler);
        // A PDF signature, so the bytes are plainly a binary document in the fixture store.
        System.arraycopy("%PDF-".getBytes(StandardCharsets.ISO_8859_1), 0, content, 0, Math.min(5, length));
        return content;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
