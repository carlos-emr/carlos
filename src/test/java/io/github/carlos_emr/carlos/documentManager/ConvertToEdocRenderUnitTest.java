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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How {@link ConvertToEdoc} hands a document to {@link HtmlPdfRenderService}: the request it builds (the webapp
 * root and the directories a document may read), which source each entry point reports, and what happens when
 * the render or the save fails. The service and the file manager are mocks.
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

    /** A form that holds {@code html} and names {@code realPath} as the webapp root (which may be {@code null}). */
    private static FormTransportContainer form(String html, String realPath, String formName) {
        FormTransportContainer form = mock(FormTransportContainer.class);
        when(form.getHTML()).thenReturn(html);
        when(form.getRealPath()).thenReturn(realPath);
        when(form.getFormName()).thenReturn(formName);
        when(form.getDemographicNo()).thenReturn("1");
        return form;
    }

    private static String page(String body) {
        return "<html><body>" + body + "</body></html>";
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

        assertThat(ConvertToEdoc.saveAsTempPDF(form(page("<img src=\"stamp.png\">"), null, "FAKE-name"))).isNotNull();

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
        FormTransportContainer form = form("<p>FAKE-form</p>", directory.toString(), "FAKE-form");
        EmailData email = mock(EmailData.class);
        when(email.getEncryptedMessage()).thenReturn("<p>FAKE-email</p>");
        EFormData eform = mock(EFormData.class);
        when(eform.getFormData()).thenReturn("<p>FAKE-hrm</p>");
        when(eform.getFormName()).thenReturn("FAKE-hrm");
        when(eform.getDemographicId()).thenReturn(1);

        ConvertToEdoc.saveAsTempPDF(form);
        ConvertToEdoc.saveAsTempPDF(email);
        ConvertToEdoc.saveAsTempPDF(eform);

        ArgumentCaptor<HtmlPdfRenderRequest> requests = ArgumentCaptor.forClass(HtmlPdfRenderRequest.class);
        verify(service, times(3)).render(requests.capture());
        assertThat(requests.getAllValues()).extracting(HtmlPdfRenderRequest::source).containsExactly(HtmlPdfRenderSource.FORM,
                HtmlPdfRenderSource.EMAIL, HtmlPdfRenderSource.HRM);
    }

    // ---- the one engine ---------------------------------------------------------------------

    @Test
    @DisplayName("should render a form through the service, once, and save what it renders")
    void shouldRenderThroughService_once() throws Exception {
        setRealPath(directory.toString());

        Path saved = ConvertToEdoc.saveAsTempPDF(form(page("<p>FAKE-body</p>"), directory.toString(), "FAKE-name"));

        assertThat(saved).isEqualTo(directory.resolve("saved.pdf"));
        assertThat(requestSentToService().source()).isEqualTo(HtmlPdfRenderSource.FORM);
        verify(files, times(1)).saveTempFile(anyString(), any(ByteArrayOutputStream.class));
    }

    // ---- failures ---------------------------------------------------------------------------

    @Test
    @DisplayName("should return null and log a fixed message, the sanitised file name and the class of the cause, never the HTML")
    void shouldReturnNullAndLogNoHtml_whenRenderFails() throws Exception {
        setRealPath(directory.toString());
        when(service.render(any())).thenThrow(new PDFGenerationException("FAKE-secret body text from the engine"));

        try (LogCapture capture = LogCapture.forLogger(ConvertToEdoc.class)) {
            Path saved = ConvertToEdoc.saveAsTempPDF(form(page("<p>FAKE-secret body text</p>"), directory.toString(), "FAKE-name\n[forged]"));

            assertThat(saved).isNull();
            assertThat(capture.messages()).anyMatch(message -> message.startsWith("HTML could not be converted to PDF for ")
                    && message.endsWith(": PDFGenerationException"));
            assertThat(capture.messages()).noneMatch(message -> message.contains("FAKE-secret") || message.contains("\n"));
        }
        verify(files, never()).saveTempFile(anyString(), any(ByteArrayOutputStream.class));
        verify(service, times(1)).render(any());
    }

    @Test
    @DisplayName("should return null and log only the class of the cause when the service fails unexpectedly")
    void shouldReturnNullAndLogNoMessage_whenServiceFailsUnexpectedly() throws Exception {
        setRealPath(directory.toString());
        when(service.render(any())).thenThrow(new IllegalStateException("FAKE-secret body text from the engine"));

        try (LogCapture capture = LogCapture.forLogger(ConvertToEdoc.class)) {
            assertThat(ConvertToEdoc.saveAsTempPDF(form(page("<p>FAKE-secret body text</p>"), directory.toString(), "FAKE-name"))).isNull();

            assertThat(capture.messages()).anyMatch(message -> message.endsWith(": IllegalStateException"));
            assertThat(capture.messages()).noneMatch(message -> message.contains("FAKE-secret"));
        }
        verify(files, never()).saveTempFile(anyString(), any(ByteArrayOutputStream.class));
        verify(service, times(1)).render(any());
    }

    @Test
    @DisplayName("should return null for blank HTML without letting an exception out")
    void shouldReturnNull_forBlankHtml() throws Exception {
        setRealPath(directory.toString());

        assertThat(ConvertToEdoc.saveAsTempPDF(form("   ", directory.toString(), "FAKE-name"))).isNull();
        assertThat(ConvertToEdoc.saveAsTempPDF(form("", directory.toString(), "FAKE-name"))).isNull();
        assertThat(ConvertToEdoc.saveAsTempPDF(form(null, directory.toString(), "FAKE-name"))).isNull();

        verify(service, never()).render(any());
    }

    @Test
    @DisplayName("should return null, and not try again, when the temporary file cannot be saved")
    void shouldReturnNull_whenTempSaveFails() throws Exception {
        setRealPath(directory.toString());
        when(files.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenThrow(new IOException("disk full at " + directory));

        try (LogCapture capture = LogCapture.forLogger(ConvertToEdoc.class)) {
            assertThat(ConvertToEdoc.saveAsTempPDF(form(page("<p>FAKE-body</p>"), directory.toString(), "FAKE-name"))).isNull();

            assertThat(capture.messages()).anyMatch(message -> message.endsWith(": IOException"));
            assertThat(capture.messages()).noneMatch(message -> message.contains(directory.toString()));
        }

        verify(files, times(1)).saveTempFile(anyString(), any(ByteArrayOutputStream.class));
        verify(service, times(1)).render(any());
    }

    @Test
    @DisplayName("should hand the service the document with its resources translated, and the bytes it renders to the file manager")
    void shouldSaveWhatTheServiceRenders_asTheDocument() throws Exception {
        setRealPath(directory.toString());
        List<byte[]> saved = new java.util.ArrayList<>();
        when(files.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenAnswer(invocation -> {
            saved.add(invocation.<ByteArrayOutputStream>getArgument(1).toByteArray());
            return directory.resolve("saved.pdf");
        });

        ConvertToEdoc.saveAsTempPDF(form(page("<p>FAKE-body</p>"), directory.toString(), "FAKE-name"));

        assertThat(saved).singleElement().isEqualTo("%PDF-1.4 FAKE".getBytes());
    }
}
