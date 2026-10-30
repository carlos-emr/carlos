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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/**
 * The HTML branch of {@link HRMPDFCreator#printPdf()}: a conversion that fails must be reported to the
 * caller as a {@link PDFGenerationException}, not as the {@code NullPointerException} that
 * {@code Files.readAllBytes(null)} used to throw, and the temporary PDF that the conversion leaves must
 * not outlive the copy. Everything here is synthetic.
 */
@Tag("unit")
@Tag("hrm")
@DisplayName("HRMPDFCreator HTML report conversion failures")
class HRMPDFCreatorHtmlFailureUnitTest extends CarlosUnitTestBase {

    private static final int HRM_ID = 8120;
    private static final String REPORT_FILE = "synthetic-html-report.xml";
    private static final String FAILURE_MESSAGE = "HTML HRM report could not be converted to PDF";
    private static final String REPORT_HTML = "<html><body><p>FAKE-Nguyễn ≥ 5</p></body></html>";

    @TempDir
    Path tempDir;

    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private HRMPDFCreator creatorForHtmlReport(MockedStatic<HRMReportParser> parser, OutputStream target) {
        var dao = mock(HRMDocumentDao.class);
        var stored = new HRMDocument();
        stored.setReportFile(REPORT_FILE);
        when(dao.findById(HRM_ID)).thenReturn(List.of(stored));
        registerMock(HRMDocumentDao.class, dao);

        var report = mock(HRMReport.class);
        when(report.isBinary()).thenReturn(true);
        when(report.getFileExtension()).thenReturn(".html");
        when(report.getBinaryContent()).thenReturn(REPORT_HTML.getBytes(StandardCharsets.UTF_8));
        parser.when(() -> HRMReportParser.parseReport(user, REPORT_FILE)).thenReturn(report);
        return new HRMPDFCreator(target, HRM_ID, user);
    }

    @Test
    @DisplayName("should throw PDFGenerationException, not NullPointerException, when the conversion returns null")
    void shouldThrowPdfGenerationException_whenHtmlConversionReturnsNull() {
        try (var parser = mockStatic(HRMReportParser.class);
                var edoc = mockStatic(ConvertToEdoc.class)) {
            edoc.when(() -> ConvertToEdoc.saveAsTempPDF(any(EFormData.class))).thenReturn(null);
            HRMPDFCreator creator = creatorForHtmlReport(parser, output);

            assertThatThrownBy(creator::printPdf)
                    .isExactlyInstanceOf(PDFGenerationException.class)
                    .hasMessage(FAILURE_MESSAGE)
                    .hasNoCause();
        }
        assertThat(output.size()).as("nothing is written for a failed conversion").isZero();
    }

    @Test
    @DisplayName("should pass the HTML to the conversion as UTF-8 form data")
    void shouldConvertReportHtml_asUtf8FormData() throws Exception {
        Path converted = Files.write(tempDir.resolve("temporaryPDF1.pdf"), new byte[] {'%', 'P', 'D', 'F'});
        try (var parser = mockStatic(HRMReportParser.class);
                var edoc = mockStatic(ConvertToEdoc.class)) {
            edoc.when(() -> ConvertToEdoc.saveAsTempPDF(any(EFormData.class))).thenReturn(converted);
            creatorForHtmlReport(parser, output).printPdf();

            edoc.verify(() -> ConvertToEdoc.saveAsTempPDF(
                    org.mockito.ArgumentMatchers.argThat((EFormData data) -> REPORT_HTML.equals(data.getFormData()))));
        }
    }

    @Test
    @DisplayName("should copy the converted PDF to the output and delete the temporary file")
    void shouldDeleteTempPdf_afterCopyingHtmlReport() throws Exception {
        byte[] pdf = "%PDF-1.4 synthetic converted report".getBytes(StandardCharsets.US_ASCII);
        Path converted = Files.write(tempDir.resolve("temporaryPDF2.pdf"), pdf);

        try (var parser = mockStatic(HRMReportParser.class);
                var edoc = mockStatic(ConvertToEdoc.class)) {
            edoc.when(() -> ConvertToEdoc.saveAsTempPDF(any(EFormData.class))).thenReturn(converted);
            creatorForHtmlReport(parser, output).printPdf();
        }

        assertThat(output.toByteArray()).isEqualTo(pdf);
        assertThat(converted).doesNotExist();
    }

    @Test
    @DisplayName("should delete the temporary file even when the output cannot be written")
    void shouldDeleteTempPdf_whenOutputCannotBeWritten() throws Exception {
        Path converted = Files.write(tempDir.resolve("temporaryPDF3.pdf"), "%PDF-1.4 synthetic".getBytes(StandardCharsets.US_ASCII));
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("synthetic: client went away");
            }
        };

        try (var parser = mockStatic(HRMReportParser.class);
                var edoc = mockStatic(ConvertToEdoc.class)) {
            edoc.when(() -> ConvertToEdoc.saveAsTempPDF(any(EFormData.class))).thenReturn(converted);
            HRMPDFCreator creator = creatorForHtmlReport(parser, failing);

            // The write failure keeps the handling every branch of printPdf has always had (logged).
            assertThatCode(creator::printPdf).doesNotThrowAnyException();
        }

        assertThat(converted).doesNotExist();
    }

    @Test
    @DisplayName("should log a fixed warning, with no path and no stack trace, when the temporary file cannot be deleted")
    void shouldWarnWithoutPath_whenTempPdfCannotBeDeleted() throws Exception {
        // A directory with a file in it can be neither read as a PDF nor deleted: the read failure keeps the
        // handling printPdf has always had, and the delete failure in the finally block must neither replace
        // it nor carry the temporary file's name into the log.
        Path undeletable = Files.createDirectory(tempDir.resolve("temporaryPDF-undeletable"));
        Files.writeString(undeletable.resolve("keep"), "x");

        try (var parser = mockStatic(HRMReportParser.class);
                var edoc = mockStatic(ConvertToEdoc.class);
                LogCapture capture = LogCapture.forLogger(HRMPDFCreator.class)) {
            edoc.when(() -> ConvertToEdoc.saveAsTempPDF(any(EFormData.class))).thenReturn(undeletable);
            HRMPDFCreator creator = creatorForHtmlReport(parser, output);

            assertThatCode(creator::printPdf).doesNotThrowAnyException();

            assertThat(capture.events())
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getMessage().getFormattedMessage())
                                .startsWith("Could not delete the temporary PDF of an HTML HRM report")
                                .doesNotContain("temporaryPDF")
                                .doesNotContain(tempDir.toString());
                        assertThat(event.getThrown()).as("the exception message names the file").isNull();
                    });
        }
    }
}
