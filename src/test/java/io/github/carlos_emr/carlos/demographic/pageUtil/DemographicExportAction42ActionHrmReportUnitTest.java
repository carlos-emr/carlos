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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import cds.OmdCdsDocument;
import cds.ReportsDocument.Reports;
import io.github.carlos_emr.carlos.utility.LegacyXmlBeans;

/**
 * How the legacy export handles a chart's HRM reports. Their media used to be read whole into
 * memory and embedded with no bound, outside the document budget. It is now decoded straight to
 * disk while the HRM file is parsed, and embedded or carried as a file by the same limits, and
 * against the same per-patient total, as a document. Every report is in the archive either way,
 * and one that cannot be exported is recorded with its patient and the reason.
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action HRM reports: embedded, carried, or recorded")
class DemographicExportAction42ActionHrmReportUnitTest extends DemographicExportExecuteUnitTestBase {

    private static final String DOCUMENT_LIMIT = "cds.legacyExport.maxEmbeddedDocumentBytes";
    private static final String PATIENT_BUDGET = "cds.legacyExport.maxEmbeddedPatientBytes";

    /** One exported report: embedded media or text (media as Latin-1 text), or the carrying file's name. */
    private record Exported(String media, String text, String filePath, String format, String sourceFacility) {
    }

    @Test
    @DisplayName("should count HRM content against the patient budget and carry what does not fit as named files")
    void shouldEmbedUpToThePatientBudgetAndCarryTheRest_whenHrmReportsExceedIt() throws Exception {
        property(DOCUMENT_LIMIT, "100");
        property(PATIENT_BUDGET, "250");
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        byte[] document = bytes(1, 100);
        byte[] first = bytes(2, 100);
        byte[] second = bytes(3, 100);
        byte[] oversized = bytes(4, 300);
        String note = "FAKE discharge summary text";
        document(101, "11", "scan.pdf", document);
        hrmDocument(101, 31, "hrm31.xml", first, second);
        hrmDocument(101, 32, "hrm32.xml", note, oversized);
        byte[] other = bytes(5, 100);
        hrmDocument(102, 41, "hrm41.xml", other);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(response).setHeader("X-Export-Status", "success");
        Map<String, byte[]> archive = streamedArchive();
        List<Exported> ann = reports(archive, patientXml(101, "FAKE-Ann"));
        // One document and four HRM reports in, five reports out.
        assertThat(ann).hasSize(5);
        assertThat(ann.get(0).media()).isEqualTo(latin1(document));
        // The document took 100 of the 250; the first HRM report makes 200 and is embedded.
        assertThat(ann.get(1)).isEqualTo(new Exported(latin1(first), null, null, "Binary", "FAKE Hospital"));
        // The second would make 300: it and every later report of this patient is carried.
        assertThat(ann.get(2)).isEqualTo(new Exported(null, null, "hrm_101_31_2.pdf", "Binary", "FAKE Hospital"));
        assertThat(ann.get(3)).isEqualTo(new Exported(null, null, "hrm_101_32_1.txt", "Text", "FAKE Hospital"));
        assertThat(ann.get(4)).isEqualTo(new Exported(null, null, "hrm_101_32_2.pdf", "Binary", "FAKE Hospital"));
        assertThat(archive.get(PROVIDER_FOLDER + "/hrm_101_31_2.pdf")).isEqualTo(second);
        assertThat(archive.get(PROVIDER_FOLDER + "/hrm_101_32_1.txt")).isEqualTo(note.getBytes(StandardCharsets.UTF_8));
        assertThat(archive.get(PROVIDER_FOLDER + "/hrm_101_32_2.pdf")).isEqualTo(oversized);

        // The total starts again for the next patient.
        assertThat(reports(archive, patientXml(102, "FAKE-Bea")))
                .containsExactly(new Exported(latin1(other), null, null, "Binary", "FAKE Hospital"));
        // Nothing but the charts, their carried files, the ReadMe and the log: no spooled media.
        assertThat(archive.keySet()).noneMatch(name -> name.contains("hrm-media-"));
        // A carried report (FilePath in place of Content) is still valid against the OntarioMD schema.
        assertThat(new String(archive.get("ExportEvent.log"), StandardCharsets.UTF_8))
                .doesNotContain("NOT exported", "XSD validation", "could not be re-read");
        assertThat(exportDirectoriesLeft()).isEmpty();
    }

    @Test
    @DisplayName("should embed HRM media and text unchanged when the chart stays within the limits")
    void shouldEmbedMediaAndTextUnchanged_whenTheChartStaysWithinTheLimits() throws Exception {
        chart(101, "FAKE-Ann");
        // Larger than one decoding chunk, with a length that leaves Base64 padding.
        byte[] media = bytes(6, 50_001);
        hrmDocument(101, 31, "hrm31.xml", media, "FAKE consult note été");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        Map<String, byte[]> archive = streamedArchive();
        assertThat(reports(archive, patientXml(101, "FAKE-Ann"))).containsExactly(
                new Exported(latin1(media), null, null, "Binary", "FAKE Hospital"),
                new Exported(null, "FAKE consult note été", null, "Text", "FAKE Hospital"));
        assertThat(archive.keySet()).noneMatch(name -> name.contains("hrm_") || name.contains("hrm-media-"));
    }

    @Test
    @DisplayName("should record an HRM file that cannot be read, report the archive incomplete and export the rest")
    void shouldRecordTheReportAndExportTheRest_whenAnHrmFileCannotBeRead() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        byte[] good = bytes(7, 64);
        Files.writeString(documentDir.resolve("broken.xml"), "<OmdCds xmlns=\"cds\"><PatientRecord><ReportsRec",
                StandardCharsets.UTF_8);
        linkHrmDocument(101, 31, "broken.xml");
        hrmDocument(101, 32, "hrm32.xml", good);
        hrmDocument(102, 41, "hrm41.xml", good);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        Map<String, byte[]> archive = streamedArchive();
        // It used to count as a file with no reports: left out without a word.
        assertThat(new String(archive.get("ExportEvent.log"), StandardCharsets.UTF_8)).contains(
                "Error! HRM report (HRM document id 31) for Patient 101 was NOT exported: its file could not be read");
        verify(response).setHeader("X-Export-Status", "incomplete");
        verify(response).setHeader("X-Export-Incomplete-Charts", "101");
        assertThat(reports(archive, patientXml(101, "FAKE-Ann")))
                .containsExactly(new Exported(latin1(good), null, null, "Binary", "FAKE Hospital"));
        assertThat(reports(archive, patientXml(102, "FAKE-Bea")))
                .containsExactly(new Exported(latin1(good), null, null, "Binary", "FAKE Hospital"));
        assertThat(exportDirectoriesLeft()).isEmpty();
    }

    @Test
    @DisplayName("should record an HRM report whose content cannot be carried and export the other reports")
    void shouldRecordTheReportAndExportTheRest_whenItsContentCannotBeCarried() throws Exception {
        property(DOCUMENT_LIMIT, "100");
        chart(101, "FAKE-Ann");
        byte[] kept = bytes(8, 300);
        hrmDocument(101, 31, "hrm31.xml", bytes(9, 300), kept);

        String result;
        // The disk fills while the first report's media is being put in place.
        try (MockedStatic<DemographicExportAction42Action> carries = mockStatic(DemographicExportAction42Action.class,
                withSettings().defaultAnswer(call -> {
                    if ("carryHrmContent".equals(call.getMethod().getName()) && Integer.valueOf(1).equals(call.getArgument(4))) {
                        throw new FileSystemException("/synthetic/FAKE-path", null, "No space left on device");
                    }
                    return call.callRealMethod();
                }))) {
            result = action.execute();
        }

        assertThat(result).isEqualTo(ActionSupport.NONE);
        Map<String, byte[]> archive = streamedArchive();
        // No empty report stands in for the one that was not exported.
        assertThat(reports(archive, patientXml(101, "FAKE-Ann")))
                .containsExactly(new Exported(null, null, "hrm_101_31_2.pdf", "Binary", "FAKE Hospital"));
        assertThat(archive.get(PROVIDER_FOLDER + "/hrm_101_31_2.pdf")).isEqualTo(kept);
        assertThat(new String(archive.get("ExportEvent.log"), StandardCharsets.UTF_8)).contains(
                "Error! HRM report 1 of HRM document id 31 for Patient 101 was NOT exported: "
                        + "the file could not be read or copied (FileSystemException: No space left on device)")
                .doesNotContain("/synthetic/FAKE-path");
        verify(response).setHeader("X-Export-Status", "incomplete");
        verify(response, never()).setHeader("X-Export-Status", "success");
        // The media that could not be carried is not left in the archive under its spool name.
        assertThat(archive.keySet()).noneMatch(name -> name.contains("hrm-media-"));
    }

    @ParameterizedTest(name = "{0} bytes")
    @ValueSource(ints = {0, 1, 2, 3, 4, 6143, 6144, 6145, 12_288, 50_001})
    @DisplayName("should decode HRM media to a file byte for byte, whatever its length")
    void shouldSpoolMediaByteForByte_forEveryBase64Ending(int length) throws Exception {
        Path spool = Files.createDirectory(root.resolve("spool"));
        byte[] media = bytes(length + 10, length);
        Path file = documentDir.resolve("sized.xml");
        writeHrmFile(file, media, "FAKE text report", bytes(11, 9));

        ReadHRMFile read = new ReadHRMFile(file.toString(), spool.toFile());

        assertThat(read.isUnreadable()).isFalse();
        assertThat(read.getReportsReceivedTotal()).isEqualTo(3);
        assertThat(read.getReportMediaFile(0)).exists().hasBinaryContent(media);
        // The media is on disk, not in the object tree.
        assertThat((byte[]) read.getReportContent(0).get("media")).isEmpty();
        assertThat(read.getReportMediaFile(1)).isNull();
        assertThat(read.getReportContent(1)).containsEntry("textcontent", "FAKE text report");
        assertThat(read.getReportMediaFile(2)).hasBinaryContent(bytes(11, 9));
        // A report's own Media element (how it arrived) is not mistaken for its content.
        assertThat(read.getReportStrings(0)).containsEntry("format", "BINARY");

        read.discardMediaFiles();
        assertThat(spool).isEmptyDirectory();
    }

    @Test
    @DisplayName("should keep media in memory, as before, when no spool directory is given")
    void shouldKeepMediaInMemory_whenNoSpoolDirectoryIsGiven() throws Exception {
        byte[] media = bytes(12, 5000);
        Path file = documentDir.resolve("inline.xml");
        writeHrmFile(file, media);

        ReadHRMFile read = new ReadHRMFile(file.toString());

        assertThat(read.isUnreadable()).isFalse();
        assertThat((byte[]) read.getReportContent(0).get("media")).isEqualTo(media);
        assertThat(read.getReportMediaFile(0)).isNull();
    }

    @Test
    @DisplayName("should say a file is unreadable, and leave no spooled media, when it cannot be parsed")
    void shouldReportTheFileUnreadable_whenItCannotBeParsed() throws Exception {
        Path spool = Files.createDirectory(root.resolve("spool"));
        Path file = documentDir.resolve("cut.xml");
        writeHrmFile(file, bytes(13, 20_000), bytes(14, 20_000));
        String whole = Files.readString(file, StandardCharsets.UTF_8);
        // Cut off inside the second report's media: the first was already spooled.
        Files.writeString(file, whole.substring(0, whole.lastIndexOf("</") - 15_000), StandardCharsets.UTF_8);

        ReadHRMFile read = new ReadHRMFile(file.toString(), spool.toFile());

        assertThat(read.isUnreadable()).isTrue();
        assertThat(read.getReportsReceivedTotal()).isZero();
        assertThat(read.getReportMediaFile(0)).isNull();
        assertThat(spool).isEmptyDirectory();
        // A file that is not there is not "unreadable": the exporter reports that case itself.
        assertThat(new ReadHRMFile(documentDir.resolve("absent.xml").toString(), spool.toFile()).isUnreadable()).isFalse();
    }

    @Test
    @DisplayName("should name a carried HRM file from numeric ids and a plain extension only")
    void shouldNameTheCarriedFileFromNumericIdsOnly_forAReportFilePath() throws Exception {
        Path export = Files.createDirectory(root.resolve("AbCdEfGh"));
        Path spooled = Files.write(export.resolve("hrm-media-1.spool"), bytes(15, 10));

        File media = DemographicExportAction42Action.carryHrmContent(
                spooled.toFile(), null, "101", "31", 2, " .PDF 1.7", export.toFile());
        File text = DemographicExportAction42Action.carryHrmContent(
                null, "FAKE note", "101", "31", 3, "../../etc", export.toFile());
        File bare = DemographicExportAction42Action.carryHrmContent(
                Files.write(export.resolve("hrm-media-2.spool"), bytes(16, 4)).toFile(), null, "101", "32", 1, null, export.toFile());

        assertThat(media.toPath()).isEqualTo(export.resolve("hrm_101_31_2.PDF")).hasBinaryContent(bytes(15, 10));
        assertThat(spooled).doesNotExist();
        assertThat(text.toPath()).isEqualTo(export.resolve("hrm_101_31_3.txt")).hasContent("FAKE note");
        assertThat(bare.toPath()).isEqualTo(export.resolve("hrm_101_32_1"));
        assertThatThrownBy(() -> DemographicExportAction42Action.carryHrmContent(
                null, "x", "../101", "31", 1, "txt", export.toFile())).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> DemographicExportAction42Action.carryHrmContent(
                null, "x", "101", "31x", 1, "txt", export.toFile())).isInstanceOf(IOException.class);
    }

    private static List<Exported> reports(Map<String, byte[]> archive, String patientXml) throws Exception {
        assertThat(archive).containsKey(patientXml);
        OmdCdsDocument document = LegacyXmlBeans.parse(OmdCdsDocument.class, OmdCdsDocument.type,
                new String(archive.get(patientXml), StandardCharsets.UTF_8));
        List<Exported> exported = new ArrayList<>();
        for (Reports report : document.getOmdCds().getPatientRecord().getReportsArray()) {
            byte[] media = report.getContent() == null ? null : report.getContent().getMedia();
            exported.add(new Exported(media == null ? null : latin1(media),
                    report.getContent() == null ? null : report.getContent().getTextContent(), report.getFilePath(),
                    report.getFormat() == null ? null : report.getFormat().toString(), report.getSourceFacility()));
        }
        return exported;
    }

    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    /** Reproducible bytes of every value, so a decoding slip cannot go unnoticed. */
    private static byte[] bytes(int seed, int length) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }
}
