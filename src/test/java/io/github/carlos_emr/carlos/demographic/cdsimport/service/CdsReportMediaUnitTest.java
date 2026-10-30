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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportUnit;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Tag("unit")
@Isolated("Report date projections exercise the actual default clinical timezone")
class CdsReportMediaUnitTest {
    @TempDir Path temp;
    private final CdsReportMediaService media = new CdsReportMediaService();

    @Test
    void shouldReadOnlyFixedHeaderBudget_whenBinaryReportIsHuge() throws Exception {
        byte[] prefix = "%PDF-1.7\n0000000".getBytes(StandardCharsets.US_ASCII);
        AtomicLong read = new AtomicLong();
        var result =
                media.inspect(
                        "pdf",
                        64L << 30,
                        "FilePath",
                        () ->
                                new InputStream() {
                                    @Override
                                    public int read(byte[] buffer, int offset, int length) {
                                        if (length == 0) return 0;
                                        long position = read.get();
                                        if (position >= 16)
                                            throw new AssertionError(
                                                    "Binary body was read by media inspection");
                                        int count = (int) Math.min(length, 16 - position);
                                        System.arraycopy(
                                                prefix, (int) position, buffer, offset, count);
                                        read.addAndGet(count);
                                        return count;
                                    }

                                    @Override
                                    public int read() {
                                        throw new AssertionError("Unexpected scalar read");
                                    }
                                });
        assertThat(read.get()).isEqualTo(16);
        assertThat(result.mime()).isEqualTo("application/pdf");
        assertThat(result.reviewRequired()).isFalse();
    }

    @Test
    void shouldRetainOpaqueBytesAndWarn_whenMediaDeclarationMismatches() throws Exception {
        byte[] original = "This is not a PDF report".getBytes(StandardCharsets.UTF_8);
        byte[] before = original.clone();
        var result = inspect("pdf", original);
        assertThat(result.mime()).isEqualTo("application/octet-stream");
        assertThat(result.extension()).isEqualTo("bin");
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.diagnostics())
                .extracting("code")
                .contains("CDS_REPORT_MEDIA_DECLARATION_MISMATCH");
        assertThat(original).isEqualTo(before);
    }

    @Test
    void shouldUseRecognizedHeaderWithWarning_whenMediaDeclarationIsVersioned() throws Exception {
        var result =
                inspect(
                        "PDF version1.7",
                        "%PDF-1.7\nsynthetic".getBytes(StandardCharsets.US_ASCII));
        assertThat(result.mime()).isEqualTo("application/pdf");
        assertThat(result.reviewRequired()).isFalse();
        assertThat(result.diagnostics())
                .extracting("code")
                .containsExactly("CDS_REPORT_MEDIA_FROM_BOUNDED_HEADER");
    }

    @Test
    void shouldRetainNativeOpaqueRepresentation_whenMediaIsUnsupported() throws Exception {
        var result = inspect("docx version2026", new byte[] {'P', 'K', 3, 4, 0, 0, 0, 0});
        assertThat(result.mime()).isEqualTo("application/octet-stream");
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.diagnostics())
                .extracting("code")
                .containsExactly("CDS_REPORT_MEDIA_UNRECOGNIZED_SOURCE_RETAINED");
    }

    @Test
    void shouldPreserveMultibyteTextAndWarn_whenUtf8EncodingIsInferred() throws Exception {
        byte[] text = ("A".repeat(8191) + "😀 clinical source").getBytes(StandardCharsets.UTF_8);
        var result = inspect("txt", text);
        assertThat(result.mime()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.diagnostics())
                .extracting("code")
                .contains("CDS_REPORT_TEXT_ENCODING_INFERRED_UTF8");
    }

    @Test
    void shouldAcceptEncodingEvidenceOnlyAfterValidation_whenUtf8IsDeclared() throws Exception {
        byte[] bytes = "\uFEFFclinical source 😀".getBytes(StandardCharsets.UTF_8);
        assertThat(inspect("txt", bytes).reviewRequired()).isFalse();
        assertThat(
                        inspect(
                                        "text/plain; charset=UTF-8",
                                        "clinical source 😀".getBytes(StandardCharsets.UTF_8))
                                .reviewRequired())
                .isFalse();
        assertThat(inspect("text/plain; charset=UTF-8", new byte[] {(byte) 0xE9}).reviewRequired())
                .isTrue();
    }

    @Test
    void shouldKeepOriginalOpaqueBytes_whenTextEncodingIsUnknown() throws Exception {
        for (byte[] value :
                List.of(new byte[] {(byte) 0xE9}, new byte[] {(byte) 0xFF, (byte) 0xFE, 'A', 0})) {
            var result = inspect("txt", value);
            assertThat(result.mime()).isEqualTo("application/octet-stream");
            assertThat(result.diagnostics())
                    .extracting("code")
                    .contains("CDS_REPORT_TEXT_ENCODING_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldRequireMediaReview_whenBinaryContentIsDeclaredText() throws Exception {
        byte[] png = {(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(inspect("txt", png).mime()).isEqualTo("application/octet-stream");
        assertThat(inspect("png", png).mime()).isEqualTo("image/png");
    }

    @Test
    void shouldStreamValidation_whenTextReportIsLarge() throws Exception {
        long length = 20L << 20;
        AtomicLong consumed = new AtomicLong();
        var result =
                media.inspect(
                        "txt",
                        length,
                        "FilePath",
                        () ->
                                new InputStream() {
                                    @Override
                                    public int read(byte[] bytes, int offset, int capacity) {
                                        if (capacity == 0) return 0;
                                        int count =
                                                (int) Math.min(capacity, length - consumed.get());
                                        if (count == 0) return -1;
                                        Arrays.fill(bytes, offset, offset + count, (byte) 'x');
                                        consumed.addAndGet(count);
                                        return count;
                                    }

                                    @Override
                                    public int read() {
                                        if (consumed.get() >= length) return -1;
                                        consumed.incrementAndGet();
                                        return 'x';
                                    }
                                });
        assertThat(consumed.get()).isEqualTo(length);
        assertThat(result.mime()).isEqualTo("text/plain;charset=UTF-8");
    }

    @Test
    void shouldPropagateFailure_whenSourceIoOrLengthChanges() {
        assertThatThrownBy(
                        () ->
                                media.inspect(
                                        "txt",
                                        100,
                                        "FilePath",
                                        () -> new ByteArrayInputStream(new byte[] {'a'})))
                .hasMessageContaining("REPORT_REPRESENTATION_LENGTH_CHANGED");
        assertThatThrownBy(
                        () ->
                                media.inspect(
                                        "txt",
                                        100,
                                        "FilePath",
                                        () ->
                                                new InputStream() {
                                                    @Override
                                                    public int read() throws IOException {
                                                        throw new IOException(
                                                                "synthetic source I/O failure");
                                                    }
                                                }))
                .isInstanceOf(IOException.class)
                .hasMessage("synthetic source I/O failure");
    }

    @Test
    void shouldPropagateCloseFailure_whenTextEncodingAlsoFails() {
        assertThatThrownBy(
                        () ->
                                media.inspect(
                                        "txt",
                                        1,
                                        "FilePath",
                                        () ->
                                                new ByteArrayInputStream(new byte[] {(byte) 0xE9}) {
                                                    @Override
                                                    public void close() throws IOException {
                                                        throw new IOException(
                                                                "synthetic close failure");
                                                    }
                                                }))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("synthetic close failure");
    }

    @Test
    void shouldStopInspection_whenThreadIsInterrupted() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(
                            () ->
                                    inspect(
                                            "pdf",
                                            "%PDF-1.7\nsynthetic".getBytes(StandardCharsets.UTF_8)))
                    .hasMessageContaining("IMPORT_CANCELLED");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldUseParserEncodingAndWarn_whenXmlTextIsEmpty() {
        assertThat(media.xmlText(10).diagnostics()).isEmpty();
        assertThat(media.xmlText(10).reviewRequired()).isFalse();
        assertThat(media.xmlText(0).reviewRequired()).isTrue();
        assertThat(media.xmlText(0).diagnostics())
                .extracting("code")
                .containsExactly("CDS_REPORT_EMPTY_REPRESENTATION");
    }

    @Test
    void shouldRetainDeclarationAndWarn_whenXmlTextDeclaresDifferentMedia() {
        var result = media.xmlText(12, "pdf");
        assertThat(result.mime()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.diagnostics())
                .extracting("code")
                .containsExactly("CDS_REPORT_TEXT_MEDIA_DECLARATION_RETAINED");
        assertThat(media.xmlText(12, "txt").reviewRequired()).isFalse();
    }

    @Test
    void shouldRetainBinaryMediaAndWarn_whenFormatDeclaresText() throws Exception {
        var result =
                media.binaryUnderTextDeclaration(
                        inspect("pdf", "%PDF-1.7\nsynthetic".getBytes(StandardCharsets.UTF_8)));
        assertThat(result.mime()).isEqualTo("application/pdf");
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.diagnostics())
                .extracting("code")
                .containsExactly("CDS_REPORT_BINARY_FORMAT_DECLARATION_RETAINED");
    }

    @Test
    void shouldMapPreparedAuthorAndDatePrecision_whenNativeReportIsWritten() throws Exception {
        var fields = spy(new CdsFieldParser());
        var publication = mock(CdsDocumentPublicationService.class);
        var configuration = mock(CdsImportConfigurationService.class);
        var preparation = mock(CdsNativePreparationService.class);
        var ledger = mock(CdsImportLedgerService.class);
        var documentDao = mock(DocumentDao.class);
        var links = mock(CtlDocumentDao.class);
        var service =
                new CdsReportImportService(
                        fields,
                        mock(CdsSnapshotService.class),
                        publication,
                        new CdsClinicalNormalizationService(fields),
                        configuration,
                        preparation,
                        ledger,
                        media,
                        documentDao,
                        links);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Format", "Text");
        values.put("Class", "DiagnosticImaging");
        values.put("Content/TextContent", "Synthetic clinical report");
        values.put("SourceAuthorPhysician/AuthorName/FirstName", "Synthetic");
        values.put("SourceAuthorPhysician/AuthorName/LastName", "Source Author");
        values.put("EventDateTime/FullDateTime", "2020-01-01T23:59:59.999999999-05:00");
        values.put("ReceivedDateTime/FullDateTime", "2020-01-02T00:00:00-05:00");
        values.put("SentDateTime/FullDate", "2020-01-02");
        var record = CdsClinicalFixture.record(temp, "Reports", values);
        var command =
                new CdsImportCommand(
                        temp, "reports", "999998", null, null, "test", "cds-5.2", false, false,
                        false, 0, null, false);
        service.prepare("run", 1, "unit", record, command, null, "source.xml");
        ArgumentCaptor<CdsReportNativeDto> captured =
                ArgumentCaptor.forClass(CdsReportNativeDto.class);
        verify(preparation)
                .store(
                        eq("run"),
                        eq(1L),
                        eq("unit"),
                        eq(record.effectiveSha256()),
                        eq(command),
                        captured.capture());
        var dto = captured.getValue();
        assertThat(dto.values().get("NativeSourceAuthor")).isEqualTo("Synthetic Source Author");
        when(preparation.load(
                        "run",
                        1,
                        "unit",
                        record.effectiveSha256(),
                        command,
                        CdsReportNativeDto.class))
                .thenReturn(dto);
        when(publication.reference(
                        anyString(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("owned.txt");
        CdsImportUnit unit = new CdsImportUnit();
        unit.setPatientKey("patient");
        when(ledger.unit("run", "unit")).thenReturn(unit);
        List<Document> documents = new ArrayList<>();
        doAnswer(
                        call -> {
                            if (call.getArgument(0) instanceof Document document) {
                                document.setDocumentNo(9);
                                documents.add(document);
                            }
                            return null;
                        })
                .when(documentDao)
                .persist(any());
        clearInvocations(fields);
        try (var fence = mockStatic(CdsImportUnitService.class)) {
            assertThat(service.write("run", 1, "unit", 42, record, command))
                    .containsEntry("document#0", "9")
                    .containsEntry("ctl_document#0", "42_9");
        }
        var link = ArgumentCaptor.forClass(CtlDocument.class);
        verify(links).persist(link.capture());
        assertThat(link.getValue().getId().getModule()).isEqualTo("demographic");
        assertThat(link.getValue().getId().getModuleId()).isEqualTo(42);
        assertThat(link.getValue().getId().getDocumentNo()).isEqualTo(9);
        assertThat(link.getValue().getStatus()).isEqualTo("A");
        verifyNoInteractions(fields);
        assertThat(documents)
                .singleElement()
                .satisfies(
                        document -> {
                            assertThat(document.getSource()).isEqualTo("Synthetic Source Author");
                            assertThat(document.getContentdatetime().toInstant())
                                    .isEqualTo(Instant.parse("2020-01-02T04:59:59Z"));
                            assertThat(document.getSentDateTime()).isNull();
                            assertThat(document.getReviewer()).isEmpty();
                            assertThat(document.getCdsContentPrecision())
                                    .isEqualTo(dto.event().precision().name());
                            assertThat(document.getNumberofpages()).isZero();
                        });
        verify(ledger)
                .diagnostic(
                        eq("run"),
                        eq(1L),
                        eq("patient"),
                        eq("unit"),
                        eq("WARNING"),
                        eq("CDS_REPORT_SUBSECOND_PRECISION_RETAINED_IN_SOURCE"),
                        eq("NORMALIZATION"),
                        eq("EventDateTime"),
                        anyString(),
                        isNull(),
                        eq(false));
        verify(ledger)
                .diagnostic(
                        eq("run"),
                        eq(1L),
                        eq("patient"),
                        eq("unit"),
                        eq("WARNING"),
                        eq("CDS_REPORT_RECEIVED_TIME_RETAINED_IN_SOURCE"),
                        eq("NORMALIZATION"),
                        eq("ReceivedDateTime"),
                        anyString(),
                        isNull(),
                        eq(false));
        verify(ledger)
                .diagnostic(
                        eq("run"),
                        eq(1L),
                        eq("patient"),
                        eq("unit"),
                        eq("WARNING"),
                        eq("CDS_REPORT_SENT_DATE_ONLY_RETAINED_IN_SOURCE"),
                        eq("NORMALIZATION"),
                        eq("SentDateTime"),
                        anyString(),
                        isNull(),
                        eq(false));
    }

    @Test void shouldRetainFullSourceForUnsupportedNativeReportTiming_andReportItsDisposition() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            for (String[] choice : List.of(
                    new String[] {"FullDateTime", "1500-01-01T00:00:00Z", "CDS_REPORT_NATIVE_TIMESTAMP_SOURCE_ONLY"},
                    new String[] {"FullDateTime", "2020-11-01T01:30:00-04:00", "CDS_REPORT_NATIVE_TIMESTAMP_SOURCE_ONLY"},
                    new String[] {"FullDate", "1582-10-10", "CDS_REPORT_NATIVE_DATE_SOURCE_ONLY"})) {
                var fields = new CdsFieldParser();
                var publication = mock(CdsDocumentPublicationService.class);
                var configuration = new CdsImportConfigurationService(new Properties());
                var preparation = mock(CdsNativePreparationService.class);
                var ledger = mock(CdsImportLedgerService.class);
                var documentDao = mock(DocumentDao.class);
                var service = new CdsReportImportService(fields, mock(CdsSnapshotService.class), publication,
                        new CdsClinicalNormalizationService(fields), configuration, preparation, ledger, media,
                        documentDao, mock(CtlDocumentDao.class));
                var record = CdsClinicalFixture.record(temp, "Reports", Map.of("Format", "Text",
                        "Content/TextContent", "Complete source report", "EventDateTime/" + choice[0], choice[1]));
                var command = new CdsImportCommand(temp, "calendar-report", "999998", null, null,
                        "test", "pcon-2024-02", false, false, false, 0, null, false);
                service.validate(record, command, null, "source.xml");
                service.prepare("run", 1, "unit", record, command, null, "source.xml");
                var captured = ArgumentCaptor.forClass(CdsReportNativeDto.class);
                verify(preparation).store(eq("run"), eq(1L), eq("unit"), eq(record.effectiveSha256()), eq(command), captured.capture());
                var dto = captured.getValue();
                when(preparation.load("run", 1, "unit", record.effectiveSha256(), command, CdsReportNativeDto.class)).thenReturn(dto);
                when(publication.reference(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn("owned.txt");
                CdsImportUnit unit = new CdsImportUnit();unit.setPatientKey("patient");when(ledger.unit("run", "unit")).thenReturn(unit);
                List<Document> documents = new ArrayList<>();
                doAnswer(call -> { if (call.getArgument(0) instanceof Document document) { document.setDocumentNo(9);documents.add(document); } return null; }).when(documentDao).persist(any());
                try (var fence = mockStatic(CdsImportUnitService.class)) {
                    assertThat(service.write("run", 1, "unit", 42, record, command)).containsEntry("document#0", "9");
                }
                assertThat(documents).singleElement().satisfies(document -> {
                    assertThat(document.getContentdatetime()).isNull();
                    assertThat(document.getCdsContentPrecision()).isEqualTo(dto.event().precision().name());
                    if (choice[0].equals("FullDate")) assertThat(document.getObservationdate()).isNull();
                });
                assertThat(dto.event().original()).isEqualTo(choice[1]);
                assertThat(fields.firstToken(record, "EventDateTime/" + choice[0], 128)).isEqualTo(choice[1]);
                verify(ledger, times(1)).diagnostic(eq("run"), eq(1L), eq("patient"), eq("unit"), eq("WARNING"),
                        eq(choice[2]), eq("NORMALIZATION"), eq("EventDateTime"), anyString(), isNull(), eq(false));
            }
        } finally { TimeZone.setDefault(previous); }
    }

    @Test
    void shouldStopBeforePublication_whenExternalTextReadLosesOwnership() throws Exception {
        var fields = spy(new CdsFieldParser());
        var publication = mock(CdsDocumentPublicationService.class);
        var snapshots = mock(CdsSnapshotService.class);
        var preparation = mock(CdsNativePreparationService.class);
        var service =
                new CdsReportImportService(
                        fields,
                        snapshots,
                        publication,
                        new CdsClinicalNormalizationService(fields),
                        mock(CdsImportConfigurationService.class),
                        preparation,
                        mock(CdsImportLedgerService.class),
                        media,
                        mock(DocumentDao.class),
                        mock(CtlDocumentDao.class));
        Path external = Files.writeString(temp.resolve("report.txt"), "A".repeat(40000));
        var snapshot = new CdsSnapshotDto(temp, temp.resolve("manifest"), "snapshot", 2, 1, 40000);
        var file = new CdsSnapshotFileDto("report.txt", external, "digest", 40000, false);
        var armed = new AtomicBoolean();
        var reads = new AtomicInteger();
        when(snapshots.resolveReference(eq(snapshot), eq("source.xml"), eq("report.txt")))
                .thenAnswer(
                        call -> {
                            armed.set(true);
                            return file;
                        });
        doAnswer(
                        call -> {
                            if (armed.get() && reads.incrementAndGet() == 3)
                                throw new CdsImportException("OWNER_FENCED");
                            return call.callRealMethod();
                        })
                .when(fields)
                .checkpoint();
        var record =
                CdsClinicalFixture.record(
                        temp,
                        "Reports",
                        Map.of(
                                "Format",
                                "Text",
                                "Class",
                                "Other",
                                "FileExtensionAndVersion",
                                "txt",
                                "FilePath",
                                "report.txt"));
        var command =
                new CdsImportCommand(
                        temp, "reports", "999998", null, null, "test", "cds-5.2", false, false,
                        false, 0, null, false);
        assertThatThrownBy(
                        () ->
                                service.prepare(
                                        "run", 1, "unit", record, command, snapshot, "source.xml"))
                .hasMessage("OWNER_FENCED");
        assertThat(reads).hasValue(3);
        verifyNoInteractions(publication, preparation);
    }

    private CdsReportMediaService.Result inspect(String type, byte[] bytes) throws IOException {
        return media.inspect(type, bytes.length, "FilePath", () -> new ByteArrayInputStream(bytes));
    }
}
