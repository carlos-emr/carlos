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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.openpdf.text.DocumentException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("EctConsultationFormRequestPrintAction22Action")
@Tag("unit")
class EctConsultationFormRequestPrintAction22ActionResilienceUnitTest extends CarlosUnitTestBase {

    @TempDir
    private Path tempDir;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FaxManager originalFaxManager;
    private FaxManager faxManager;
    private EctConsultationFormRequestPrintAction22Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(ConsultationManager.class, mock(ConsultationManager.class));
        registerMock(ConsultationRequestDao.class, mock(ConsultationRequestDao.class));
        faxManager = mock(FaxManager.class);
        registerMock(FaxManager.class, faxManager);

        action = new EctConsultationFormRequestPrintAction22Action();
        originalFaxManager = (FaxManager) ReflectionTestUtils.getField(
                EctConsultationFormRequestPrintAction22Action.class, "faxManager");
        ReflectionTestUtils.setField(EctConsultationFormRequestPrintAction22Action.class, "faxManager", faxManager);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(EctConsultationFormRequestPrintAction22Action.class, "faxManager", originalFaxManager);
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should append the validated PDF file path when the attachment is readable")
    void shouldAppendValidatedPdfPath_whenDocumentReadable() throws Throwable {
        Path pdfPath = Files.write(tempDir.resolve("consult-attachment.pdf"), "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        EDoc doc = printableDocument("42", pdfPath.getFileName().toString(), "application/pdf");
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();

        appendDocumentAttachments(attachments, streams, List.of(doc));

        assertThat(attachments).containsExactly(pdfPath.toFile().getPath());
        assertThat(streams).isEmpty();
    }

    @Test
    @DisplayName("should reject document paths outside the configured attachment directory")
    void shouldRejectTraversalDocumentPath_whenAppendingAttachments() {
        EDoc doc = printableDocument("43", "../outside.pdf", "application/pdf");
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();
        List<EDoc> docs = List.of(doc);

        assertThatThrownBy(() -> appendDocumentAttachments(attachments, streams, docs))
                .isInstanceOf(SecurityException.class);
        assertThat(attachments).isEmpty();
        assertThat(streams).isEmpty();
    }

    @Test
    @DisplayName("should fail the print when an attached image cannot be converted, naming it by id only")
    void shouldFailPrint_whenAttachedImageCannotBeConverted() throws Exception {
        Path imagePath = Files.write(tempDir.resolve("bad-image.png"), new byte[]{1, 2, 3});
        EDoc doc = printableDocument("44", imagePath.getFileName().toString(), "image/png");
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();
        List<EDoc> docs = List.of(doc);

        try (MockedConstruction<ImagePDFCreator> mockedImages = mockConstruction(ImagePDFCreator.class,
                (mock, context) -> doThrow(new DocumentException("clinical text in a renderer message")).when(mock).printPdf())) {
            assertThatThrownBy(() -> appendDocumentAttachments(attachments, streams, docs))
                    .isInstanceOf(PDFGenerationException.class)
                    .hasMessageContaining("DOC 44")
                    .hasMessageContaining("DocumentException")
                    .hasMessageNotContaining("clinical text")
                    .hasMessageNotContaining("bad-image")
                    .hasNoCause();

            assertThat(attachments).isEmpty();
            assertThat(mockedImages.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("should fail the print when an attached document's file is missing")
    void shouldFailPrint_whenAttachedDocumentFileIsMissing() {
        EDoc doc = printableDocument("45", "gone.pdf", "application/pdf");
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();
        List<EDoc> docs = List.of(doc);

        assertThatThrownBy(() -> appendDocumentAttachments(attachments, streams, docs))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("DOC 45")
                .hasMessageNotContaining("gone.pdf");
        assertThat(attachments).isEmpty();
    }

    @Test
    @DisplayName("should fail the print when an attached HRM report has no usable id")
    void shouldFailPrint_whenAttachedHrmReportHasNoId() {
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();
        List<HashMap<String, Object>> reports = List.of(new HashMap<>());

        assertThatThrownBy(() -> invokeUnwrapped("appendHRMAttachments",
                mock(LoggedInInfo.class), attachments, streams, reports))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("HRM");
        assertThat(attachments).isEmpty();
    }

    private void appendDocumentAttachments(ArrayList<Object> attachments, ArrayList<InputStream> streams, List<EDoc> docs) throws Throwable {
        invokeUnwrapped("appendDocumentAttachments", attachments, streams, docs, tempDir.toString() + File.separator);
    }

    /** Reflection wraps a checked exception; hand the test the one the action threw. */
    private void invokeUnwrapped(String method, Object... args) throws Throwable {
        try {
            ReflectionTestUtils.invokeMethod(action, method, args);
        } catch (UndeclaredThrowableException e) {
            throw e.getCause();
        }
    }

    private EDoc printableDocument(String docId, String fileName, String contentType) {
        EDoc doc = new EDoc();
        doc.setDocId(docId);
        doc.setFileName(fileName);
        doc.setContentType(contentType);
        doc.setDescription("Consult attachment " + docId);
        return doc;
    }
}
