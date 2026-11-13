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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportUnit;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsDocumentPublicationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportConfigurationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportLedgerService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportUnitService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsNativePreparationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsReportImportService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsReportMediaService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSnapshotService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

/**
 * A document CARLOS stores travels through its own CDS export, its own import and its export
 * again without holding the patient (issue #6196). Each case starts from a stored file as the
 * document manager keeps it, takes the declaration the exporter writes for it
 * ({@link CdsExportReportFileFormats}), imports a report carrying those bytes through the real
 * {@link CdsReportImportService} and media check, and binds the document row the import wrote the
 * way the re-export does ({@link CdsExportReportOccurrenceService#bind}).
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportReportMediaRoundTripUnitTest {
    private static final String UNIT = "c".repeat(64);
    private static final int PATIENT = 27;

    @TempDir Path temp;

    /** The bytes a stored document begins with, by the type the document manager recorded. */
    private static byte[] stored(String kind) {
        return switch (kind) {
            case "pdf" -> ascii("%PDF-1.7\n%synthetic referral letter\n");
            case "png" -> new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};
            case "jpg" -> new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16};
            case "tif" -> new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0};
            case "gif" -> new byte[] {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, (byte) 0x80, 0, 0};
            case "bmp" -> new byte[] {'B', 'M', 70, 0, 0, 0, 0, 0, 0, 0, 54, 0, 0, 0, 40, 0};
            case "doc" ->
                    new byte[] {
                        (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1,
                        0x1a, (byte) 0xe1, 0, 0
                    };
            case "docx" -> new byte[] {'P', 'K', 3, 4, 20, 0, 6, 0, 8, 0, 0, 0};
            case "rtf" -> ascii("{\\rtf1\\ansi Synthetic referral letter}");
            case "txt" -> "Synthetic referral letter\r\n".getBytes(StandardCharsets.UTF_8);
            case "html" ->
                    "<html><body><script>alert(1)</script>Synthetic letter</body></html>"
                            .getBytes(StandardCharsets.UTF_8);
            case "zip" -> new byte[] {'P', 'K', 3, 4, 10, 0, 0, 0, 0, 0};
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    @ParameterizedTest(name = "{0} ({1})")
    @CsvSource({
        // stored name,                   stored content type,     bytes, exported, imported mime,                review
        "20260305101400letter.pdf,        application/pdf,         pdf,   pdf,  application/pdf,                false",
        "20260305101400photo.png,         image/png,               png,   png,  image/png,                      false",
        "20260305101400Photo.JPEG,        image/jpeg,              jpg,   jpeg, image/jpeg,                     false",
        "20260305101600scan0001,          image/tiff,              tif,   tif,  image/tiff,                     false",
        "20260305101400chart.gif,         image/gif,               gif,   gif,  image/gif,                      false",
        "20260305101400wound.bmp,         image/bmp,               bmp,   bmp,  image/bmp,                      false",
        "20260305101400letter.doc,        application/msword,      doc,   doc,  application/msword,             false",
        "20260305101400letter.docx,       ,                        docx,  docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document, false",
        "20260305101400letter.rtf,        text/rtf,                rtf,   rtf,  application/rtf,                false",
        // Plain text of unstated encoding, an HTML file and an unknown type stay reviewed.
        "20260305101400note.txt,          text/plain,              txt,   txt,  text/plain;charset=UTF-8,       true",
        "20260305101400page.html,         text/html,               html,  html, application/octet-stream,       true",
        "20260305101400page.htm,          text/html,               html,  htm,  application/octet-stream,       true",
        "20260305101400archive.zip,       application/zip,         zip,   bin,  application/octet-stream,       true"
    })
    void shouldReexportAStoredDocument_afterCarlosImportsItsOwnExport(
            String name, String contentType, String kind, String exported, String mime, boolean review)
            throws Exception {
        byte[] bytes = stored(kind);
        String declaration =
                CdsExportReportFileFormats.extension(
                        name,
                        contentType,
                        Arrays.copyOf(bytes, Math.min(bytes.length, CdsExportReportFileFormats.PREFIX)));
        assertThat(declaration).isEqualTo(exported);

        var imported = importFile(declaration, bytes);

        assertThat(imported.variant().mime()).isEqualTo(mime);
        assertThat(imported.variant().mediaReviewRequired()).isEqualTo(review);
        assertThat(imported.document().getContenttype()).isEqualTo(mime);
        assertThat(imported.document().getDocdesc())
                .isEqualTo(review ? "Imported report (source media review required)" : "Imported report");
        assertThatCode(imported::bind).doesNotThrowAnyException();
    }

    @Test
    void shouldReexportAnAddHtmlDocument_asThePlainTextItWasImportedAs() throws Exception {
        // An Add HTML or Add Link document is exported as Format Text with its HTML as TextContent.
        String declaration = CdsExportNativeReportMapper.Content.html().extension();
        var values = new LinkedHashMap<String, String>();
        values.put("Format", "Text");
        values.put("FileExtensionAndVersion", declaration);
        values.put("Content/TextContent", "<p>Synthetic <b>letter</b></p><script>alert(1)</script>");
        values.put("Class", "Other Letter");

        var imported = importRecord(CdsClinicalFixture.record(temp, "Reports", values), null);

        assertThat(imported.variant().mime()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(imported.variant().mediaReviewRequired()).isTrue();
        assertThat(imported.variant().diagnostics())
                .extracting("code")
                .containsExactly("CDS_REPORT_HTML_RETAINED_AS_TEXT");
        assertThatCode(imported::bind).doesNotThrowAnyException();
    }

    /** One imported representation: the row the import wrote and what the re-export binds. */
    private record Imported(Document document, CdsReportNativeDto prepared) {
        CdsReportNativeDto.Variant variant() {
            return prepared.variants().getFirst();
        }

        /** The document row as the re-export captures it, bound as CdsExportReportOccurrenceService does. */
        void bind() throws Exception {
            var row = new HashMap<String, String>();
            row.put("document_no", document.getDocumentNo().toString());
            row.put("doctype", document.getDoctype());
            row.put("status", String.valueOf(document.getStatus()));
            row.put("docfilename", document.getDocfilename());
            row.put("docClass", document.getDocClass());
            row.put("contenttype", document.getContenttype());
            row.put("cds_content_precision", document.getCdsContentPrecision());
            row.put("docdesc", document.getDocdesc());
            row.put("abnormal", document.isAbnormal() ? "1" : "0");
            row.put("public1", Integer.toString(document.getPublic1()));
            row.put("restrictToProgram", Boolean.TRUE.equals(document.isRestrictToProgram()) ? "1" : "0");
            row.put("responsible", document.getResponsible());
            row.put("reviewer", document.getReviewer());
            row.values().removeIf(java.util.Objects::isNull);
            var link =
                    Map.of(
                            "document_no", row.get("document_no"),
                            "module", "demographic",
                            "module_id", Integer.toString(PATIENT),
                            "status", "A");
            var key = CdsExportPatientTableSourceReader.key(document.getDocumentNo());
            CdsExportReportOccurrenceService.bind(
                    new CdsExportCapturedTextRowDto("document", key, row),
                    new CdsExportCapturedTextRowDto("ctl_document", key, link),
                    prepared,
                    variant(),
                    document.getDocfilename(),
                    PATIENT);
        }
    }

    private Imported importFile(String declaration, byte[] bytes) throws Exception {
        Path member = Files.write(temp.resolve("member"), bytes);
        var values = new LinkedHashMap<String, String>();
        values.put("Format", "Binary");
        values.put("FileExtensionAndVersion", declaration);
        values.put("FilePath", "Reports/member");
        values.put("Class", "Other Letter");
        var file =
                new CdsSnapshotFileDto(
                        "Reports/member",
                        member,
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                        bytes.length,
                        false);
        return importRecord(CdsClinicalFixture.record(temp, "Reports", values), file);
    }

    /** Prepares and writes one report record as the importer does, returning the row it wrote. */
    private Imported importRecord(
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto record,
            CdsSnapshotFileDto file)
            throws Exception {
        var fields = new CdsFieldParser();
        var snapshots = mock(CdsSnapshotService.class);
        var publication = mock(CdsDocumentPublicationService.class);
        var preparation = mock(CdsNativePreparationService.class);
        var unit = new CdsImportUnit();
        unit.setPatientKey("patient");
        // The ledger's unit lookup is package-private: every lookup answers this run's unit.
        var ledger =
                mock(
                        CdsImportLedgerService.class,
                        call ->
                                call.getMethod().getReturnType() == CdsImportUnit.class
                                        ? unit
                                        : RETURNS_DEFAULTS.answer(call));
        var documents = mock(DocumentDao.class);
        var service =
                new CdsReportImportService(
                        fields,
                        snapshots,
                        publication,
                        new CdsClinicalNormalizationService(fields),
                        new CdsImportConfigurationService(new Properties()),
                        preparation,
                        ledger,
                        new CdsReportMediaService(),
                        documents,
                        mock(CtlDocumentDao.class));
        var snapshot =
                new CdsSnapshotDto(
                        temp, temp.resolve("manifest"), "snapshot", 2, 1, file == null ? 0 : file.length());
        if (file != null)
            when(snapshots.resolveReference(snapshot, "patient.xml", file.relativeName()))
                    .thenReturn(file);
        var command =
                new CdsImportCommand(
                        temp, "reports", "999998", null, null, "test", "cds-5.2", false, false,
                        false, 0, null, false);
        service.validate(record, command, snapshot, "patient.xml");
        service.prepare("run", 1, UNIT, record, command, snapshot, "patient.xml");
        var captured = ArgumentCaptor.forClass(CdsReportNativeDto.class);
        verify(preparation)
                .store(
                        eq("run"),
                        eq(1L),
                        eq(UNIT),
                        eq(record.effectiveSha256()),
                        eq(command),
                        captured.capture());
        var prepared = captured.getValue();
        when(preparation.load("run", 1, UNIT, record.effectiveSha256(), command, CdsReportNativeDto.class))
                .thenReturn(prepared);
        // The published name the import gives a representation: cds-<unit>-<variant>-<digest>.<ext>.
        when(publication.reference(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        call ->
                                "cds-" + call.getArgument(2) + "-" + call.getArgument(3) + "-"
                                        + call.getArgument(5) + "." + call.getArgument(4));
        List<Document> written = new ArrayList<>();
        doAnswer(
                        call -> {
                            if (call.getArgument(0) instanceof Document document) {
                                document.setDocumentNo(9);
                                written.add(document);
                            }
                            return null;
                        })
                .when(documents)
                .persist(any());
        try (var fence = mockStatic(CdsImportUnitService.class)) {
            service.write("run", 1, UNIT, PATIENT, record, command);
        }
        assertThat(written).hasSize(1);
        return new Imported(written.getFirst(), prepared);
    }
}
