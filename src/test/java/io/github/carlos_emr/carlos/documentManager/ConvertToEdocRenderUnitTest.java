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
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How {@link ConvertToEdoc} hands a document to {@link HtmlPdfRenderService}: the request it builds (the webapp
 * root and the directories a document may read), which source each entry point reports, and what happens when
 * wkhtmltopdf, the render or the save fails. The service and the file manager are mocks; wkhtmltopdf is made to fail,
 * which is when the service is used.
 */
@Tag("unit")
@Tag("fast")
@Isolated
@DisplayName("ConvertToEdoc rendering unit tests")
class ConvertToEdocRenderUnitTest extends CarlosUnitTestBase {

    @TempDir
    Path directory;

    private HtmlPdfRenderService service;
    private NioFileManager files;
    private ServletContext servletContext;
    private Object originalRealPath;
    private String originalCatalinaBase;

    @BeforeEach
    void setUp() throws Exception {
        originalRealPath = ReflectionTestUtils.getField(ConvertToEdoc.class, "realPath");
        originalCatalinaBase = System.getProperty("catalina.base");
        service = mock(HtmlPdfRenderService.class);
        files = mock(NioFileManager.class);
        servletContext = mock(ServletContext.class);
        registerMock(HtmlPdfRenderService.class, service);
        registerMock(NioFileManager.class, files);
        registerMock(ServletContext.class, servletContext);
        when(service.render(any())).thenReturn(new HtmlPdfRenderResult("%PDF-1.4 FAKE".getBytes(), Set.of()));
        when(files.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenReturn(directory.resolve("saved.pdf"));
    }

    @AfterEach
    void restore() {
        synchronized (ConvertToEdoc.class) {
            ReflectionTestUtils.setField(ConvertToEdoc.class, "realPath", originalRealPath);
        }
        if (originalCatalinaBase == null) {
            System.clearProperty("catalina.base");
        } else {
            System.setProperty("catalina.base", originalCatalinaBase);
        }
    }

    private static void setRealPath(String value) {
        synchronized (ConvertToEdoc.class) {
            ReflectionTestUtils.setField(ConvertToEdoc.class, "realPath", value);
        }
    }

    /** wkhtmltopdf fails, so the render goes to the service. */
    private MockedConstruction<InternalEDocConverter> primaryConverterFails() {
        return Mockito.mockConstruction(InternalEDocConverter.class, (converter, context) ->
                doThrow(new IOException("FAKE-primary quoted the document")).when(converter).convert(any(), any()));
    }

    private HtmlPdfRenderRequest requestSentToService() throws Exception {
        ArgumentCaptor<HtmlPdfRenderRequest> request = ArgumentCaptor.forClass(HtmlPdfRenderRequest.class);
        verify(service, times(1)).render(request.capture());
        return request.getValue();
    }

    // ---- the request ------------------------------------------------------------------------

    @Test
    @DisplayName("should build a request with the webapp root as base and the root, the eForm images, the temp and the work directories allowed")
    void shouldBuildRequest_withWebappRootAndAllowedDirectories() throws Exception {
        Path root = Files.createDirectory(directory.resolve("webapp"));
        Path images = Files.createDirectory(directory.resolve("eform-images"));
        Path catalina = Files.createDirectory(directory.resolve("catalina"));
        System.setProperty("catalina.base", catalina.toString());
        String originalImages = CarlosProperties.getInstance().getProperty("EFORM_IMAGES_DIR");
        CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", images.toString());
        try {
            setRealPath(root.toString());

            HtmlPdfRenderRequest request = ConvertToEdoc.buildRenderRequest("<p>FAKE-x</p>", HtmlPdfRenderSource.FORM);

            assertThat(request.baseDirectory()).isEqualTo(root.toAbsolutePath().normalize());
            assertThat(request.allowedDirectories()).containsExactly(root.toAbsolutePath().normalize(), images.toAbsolutePath().normalize(),
                    Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize(),
                    catalina.resolve("work").toAbsolutePath().normalize());
            assertThat(request.html()).isEqualTo("<p>FAKE-x</p>");
            assertThat(request.source()).isEqualTo(HtmlPdfRenderSource.FORM);
        } finally {
            if (originalImages == null) {
                CarlosProperties.getInstance().remove("EFORM_IMAGES_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", originalImages);
            }
        }
    }

    @Test
    @DisplayName("should leave the base directory out, and still allow the temp directory, when there is no webapp root")
    void shouldPassNullBase_whenNoWebappRoot() {
        setRealPath(null);
        when(servletContext.getRealPath("/")).thenReturn(null);
        System.clearProperty("catalina.base");

        HtmlPdfRenderRequest request = ConvertToEdoc.buildRenderRequest("<p>x</p>", HtmlPdfRenderSource.FORM);

        assertThat(request.baseDirectory()).isNull();
        assertThat(request.allowedDirectories()).contains(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("should treat a filesystem root as no webapp root")
    void shouldPassNullBase_whenRootIsFilesystemRoot() {
        setRealPath("/");

        assertThat(ConvertToEdoc.buildRenderRequest("<p>x</p>", HtmlPdfRenderSource.FORM).baseDirectory()).isNull();
    }

    @Test
    @DisplayName("should take the webapp root from the servlet context when none was set, and set it for the translation too")
    void shouldUseSameRoot_forTranslationAndRendering() throws Exception {
        Path root = Files.createDirectory(directory.resolve("context-root"));
        Files.createFile(root.resolve("stamp.png"));
        setRealPath(null);
        when(servletContext.getRealPath("/")).thenReturn(root + "/");

        try (MockedConstruction<InternalEDocConverter> converter = primaryConverterFails()) {
            assertThat(ConvertToEdoc.saveAsTempPDF("<html><body><img src=\"stamp.png\"></body></html>", "FAKE-name")).isNotNull();
        }

        HtmlPdfRenderRequest request = requestSentToService();
        assertThat(request.baseDirectory()).isEqualTo(root.toAbsolutePath().normalize());
        // The path translation used the same root: the image became a file: URI under it.
        assertThat(request.html()).contains(root.resolve("stamp.png").toUri().toString());
        assertThat(ReflectionTestUtils.getField(ConvertToEdoc.class, "realPath")).isEqualTo(root + "/");
    }

    @Test
    @DisplayName("should label each entry point's request with its source")
    void shouldLabelSources_perEntryPoint() throws Exception {
        setRealPath(directory.toString());
        FormTransportContainer form = mock(FormTransportContainer.class);
        when(form.getHTML()).thenReturn("<p>FAKE-form</p>");
        when(form.getRealPath()).thenReturn(directory.toString());
        when(form.getFormName()).thenReturn("FAKE-form");
        when(form.getDemographicNo()).thenReturn("1");
        EmailData email = mock(EmailData.class);
        when(email.getEncryptedMessage()).thenReturn("<p>FAKE-email</p>");
        EFormData eform = mock(EFormData.class);
        when(eform.getFormData()).thenReturn("<p>FAKE-hrm</p>");
        when(eform.getFormName()).thenReturn("FAKE-hrm");
        when(eform.getDemographicId()).thenReturn(1);

        try (MockedConstruction<InternalEDocConverter> converter = primaryConverterFails()) {
            ConvertToEdoc.saveAsTempPDF(form);
            ConvertToEdoc.saveAsTempPDF(email);
            ConvertToEdoc.saveAsTempPDF(eform);
            ConvertToEdoc.saveAsTempPDF("<p>FAKE-string</p>", "FAKE-string");
        }

        ArgumentCaptor<HtmlPdfRenderRequest> requests = ArgumentCaptor.forClass(HtmlPdfRenderRequest.class);
        verify(service, times(4)).render(requests.capture());
        assertThat(requests.getAllValues()).extracting(HtmlPdfRenderRequest::source).containsExactly(HtmlPdfRenderSource.FORM,
                HtmlPdfRenderSource.EMAIL, HtmlPdfRenderSource.HRM, HtmlPdfRenderSource.FORM);
    }

    // ---- the ladder of converters -----------------------------------------------------------

    @Test
    @DisplayName("should use the service only when wkhtmltopdf fails, and log the class of that failure, not its message")
    void shouldUseServiceAsFallback_whenWkhtmltopdfFails() throws Exception {
        setRealPath(directory.toString());

        try (LogCapture capture = LogCapture.forLogger(ConvertToEdoc.class);
                MockedConstruction<InternalEDocConverter> converter = primaryConverterFails()) {
            Path saved = ConvertToEdoc.saveAsTempPDF("<html><body><p>FAKE-body</p></body></html>", "FAKE-name");

            assertThat(saved).isNotNull();
            assertThat(requestSentToService().source()).isEqualTo(HtmlPdfRenderSource.FORM);
            assertThat(capture.messages()).anyMatch(message -> message.contains("Primary PDF conversion failed (IOException)"));
            assertThat(capture.messages()).noneMatch(message -> message.contains("FAKE-primary"));
        }
    }

    @Test
    @DisplayName("should not call the service when wkhtmltopdf produces the document")
    void shouldNotUseService_whenWkhtmltopdfSucceeds() throws Exception {
        setRealPath(directory.toString());

        try (MockedConstruction<InternalEDocConverter> converter = Mockito.mockConstruction(InternalEDocConverter.class)) {
            assertThat(ConvertToEdoc.saveAsTempPDF("<html><body><p>FAKE-body</p></body></html>", "FAKE-name")).isNotNull();
        }

        verify(service, never()).render(any());
    }

    // ---- failures ---------------------------------------------------------------------------

    @Test
    @DisplayName("should return null and log a fixed message, the sanitised file name and the class of the cause, never the HTML")
    void shouldReturnNullAndLogNoHtml_whenRenderFails() throws Exception {
        setRealPath(directory.toString());
        when(service.render(any())).thenThrow(new PDFGenerationException("FAKE-secret body text from the engine"));

        try (LogCapture capture = LogCapture.forLogger(ConvertToEdoc.class);
                MockedConstruction<InternalEDocConverter> converter = primaryConverterFails()) {
            Path saved = ConvertToEdoc.saveAsTempPDF("<html><body><p>FAKE-secret body text</p></body></html>", "FAKE-name\n[forged]");

            assertThat(saved).isNull();
            assertThat(capture.messages()).anyMatch(message -> message.startsWith("HTML could not be converted to PDF for ")
                    && message.endsWith(": PDFGenerationException"));
            assertThat(capture.messages()).noneMatch(message -> message.contains("FAKE-secret") || message.contains("\n"));
        }
        verify(files, never()).saveTempFile(anyString(), any(ByteArrayOutputStream.class));
    }

    @Test
    @DisplayName("should return null for blank HTML without letting an exception out")
    void shouldReturnNull_forBlankHtml() throws Exception {
        setRealPath(directory.toString());

        try (MockedConstruction<InternalEDocConverter> converter = primaryConverterFails()) {
            assertThat(ConvertToEdoc.saveAsTempPDF("   ", "FAKE-name")).isNull();
            assertThat(ConvertToEdoc.saveAsTempPDF("", "FAKE-name")).isNull();
            assertThat(ConvertToEdoc.saveAsTempPDF((String) null, "FAKE-name")).isNull();
        }

        verify(service, never()).render(any());
    }

    @Test
    @DisplayName("should return null, and not try again, when the temporary file cannot be saved")
    void shouldReturnNull_whenTempSaveFails() throws Exception {
        setRealPath(directory.toString());
        when(files.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenThrow(new IOException("disk full at " + directory));

        try (LogCapture capture = LogCapture.forLogger(ConvertToEdoc.class);
                MockedConstruction<InternalEDocConverter> converter = primaryConverterFails()) {
            assertThat(ConvertToEdoc.saveAsTempPDF("<html><body><p>FAKE-body</p></body></html>", "FAKE-name")).isNull();

            assertThat(capture.messages()).anyMatch(message -> message.endsWith(": IOException"));
            assertThat(capture.messages()).noneMatch(message -> message.contains(directory.toString()));
        }

        verify(files, times(1)).saveTempFile(anyString(), any(ByteArrayOutputStream.class));
        verify(service, times(1)).render(any());
    }

    @Test
    @DisplayName("should discard what wkhtmltopdf wrote before it failed, so only the service's PDF is saved")
    void shouldDiscardPartialOutput_whenWkhtmltopdfFailsMidWay() throws Exception {
        setRealPath(directory.toString());
        List<byte[]> saved = new java.util.ArrayList<>();
        when(files.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenAnswer(invocation -> {
            saved.add(invocation.<ByteArrayOutputStream>getArgument(1).toByteArray());
            return directory.resolve("saved.pdf");
        });

        try (MockedConstruction<InternalEDocConverter> converter = Mockito.mockConstruction(InternalEDocConverter.class, (mock, context) ->
                Mockito.doAnswer(invocation -> {
                    invocation.<java.io.OutputStream>getArgument(1).write("PARTIAL-OUTPUT".getBytes());
                    throw new IOException("failed after writing");
                }).when(mock).convert(any(), any()))) {
            ConvertToEdoc.saveAsTempPDF("<html><body><p>FAKE-body</p></body></html>", "FAKE-name");
        }

        assertThat(saved).singleElement().isEqualTo("%PDF-1.4 FAKE".getBytes());
    }

    @Test
    @DisplayName("should hand the service the document with its resources translated, and the bytes it renders to the file manager")
    void shouldSaveWhatTheServiceRenders() throws Exception {
        setRealPath(directory.toString());
        List<byte[]> saved = new java.util.ArrayList<>();
        when(files.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenAnswer(invocation -> {
            saved.add(invocation.<ByteArrayOutputStream>getArgument(1).toByteArray());
            return directory.resolve("saved.pdf");
        });

        try (MockedConstruction<InternalEDocConverter> converter = primaryConverterFails()) {
            ConvertToEdoc.saveAsTempPDF("<html><body><p>FAKE-body</p></body></html>", "FAKE-name");
        }

        assertThat(saved).singleElement().isEqualTo("%PDF-1.4 FAKE".getBytes());
    }
}
