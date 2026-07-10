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
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

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
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("EctConsultationFormRequestPrintAction22Action")
@Tag("unit")
class EctConsultationFormRequestPrintAction22ActionUnitTest extends CarlosUnitTestBase {

    @TempDir
    private Path tempDir;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
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
        ReflectionTestUtils.setField(EctConsultationFormRequestPrintAction22Action.class, "faxManager", faxManager);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should append the validated PDF file path when the attachment is readable")
    void shouldAppendValidatedPdfPath_whenDocumentReadable() throws Exception {
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
    @DisplayName("should skip malformed image documents instead of failing the print package")
    void shouldSkipMalformedImageDocument_whenAppendingAttachments() throws Exception {
        Path imagePath = Files.write(tempDir.resolve("bad-image.png"), new byte[]{1, 2, 3});
        EDoc doc = printableDocument("44", imagePath.getFileName().toString(), "image/png");
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();

        try (MockedConstruction<ImagePDFCreator> mockedImages = mockConstruction(ImagePDFCreator.class,
                (mock, context) -> doThrow(new DocumentException("bad image")).when(mock).printPdf())) {
            appendDocumentAttachments(attachments, streams, List.of(doc));

            assertThat(attachments).isEmpty();
            assertThat(streams).isEmpty();
            assertThat(request.getAttribute("imagePath")).isEqualTo(imagePath.toFile().getPath());
            assertThat(mockedImages.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("should log missing rendered PDF when fax attachment path is null")
    void shouldLogMissingRenderedPdf_whenFaxAttachmentPathNull() {
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();

        try (LogCapture capture = LogCapture.forLogger(EctConsultationFormRequestPrintAction22Action.class)) {
            addRenderedFaxAttachment(attachments, streams, null);

            assertThat(attachments).isEmpty();
            assertThat(streams).isEmpty();
            assertThat(String.join("\n", capture.messages()))
                    .contains("missing rendered PDF")
                    .doesNotContain("unreadable temporary PDF");
        }
    }

    @Test
    @DisplayName("should log unreadable temporary PDF when fax attachment path is missing")
    void shouldLogUnreadableTemporaryPdf_whenFaxAttachmentPathMissing() {
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();

        try (LogCapture capture = LogCapture.forLogger(EctConsultationFormRequestPrintAction22Action.class)) {
            addRenderedFaxAttachment(attachments, streams, tempDir.resolve("missing.pdf"));

            assertThat(attachments).isEmpty();
            assertThat(streams).isEmpty();
            assertThat(String.join("\n", capture.messages()))
                    .contains("unreadable temporary PDF")
                    .doesNotContain("missing rendered PDF");
        }
    }

    @Test
    @DisplayName("should URL encode form attachment forward parameters")
    void shouldUrlEncodeFormAttachmentForwardParameters_whenCreatingTransportContainer() throws Exception {
        AtomicReference<String> forwardedPath = new AtomicReference<>();
        MockHttpServletRequest encodedRequest = requestRecordingForwardPath(forwardedPath);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        servletActionContextMock.when(ServletActionContext::getServletContext).thenReturn(new MockServletContext());
        action.request = encodedRequest;
        action.response = response;
        EctFormData.PatientForm formItem = new EctFormData.PatientForm("form_table",
                "Annual Review & Care+", 123, 456);

        FormTransportContainer formTransportContainer = ReflectionTestUtils.invokeMethod(action,
                "createFormTransportContainer", loggedInInfo, formItem, "456");

        assertThat(forwardedPath).hasValue("/form/forwardshortcutname?method=fetch"
                + "&formname=Annual+Review+%26+Care%2B&demographic_no=456&formId=123");
        assertThat(formTransportContainer.getProviderNo()).isEqualTo("999998");
    }

    @Test
    @DisplayName("should render patient-independent eForms with the consultation demographic")
    void shouldUseConsultationDemographic_whenEFormHasNoDemographic() throws Exception {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Path eFormPath = Files.write(tempDir.resolve("patient-independent-eform.pdf"),
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        EFormData eFormData = new EFormData();
        eFormData.setId(915);
        eFormData.setDemographicId(null);
        ArrayList<Object> attachments = new ArrayList<>();
        ArrayList<InputStream> streams = new ArrayList<>();
        when(faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.EFORM, 915, 456))
                .thenReturn(eFormPath);

        try {
            ReflectionTestUtils.invokeMethod(action, "appendEFormAttachments",
                    loggedInInfo, attachments, streams, List.of(eFormData), "456");

            assertThat(attachments).hasSize(1);
            assertThat(streams).hasSize(1);
        } finally {
            for (InputStream stream : streams) {
                stream.close();
            }
        }
    }

    private void appendDocumentAttachments(ArrayList<Object> attachments, ArrayList<InputStream> streams, List<EDoc> docs) {
        ReflectionTestUtils.invokeMethod(action, "appendDocumentAttachments", attachments, streams, docs, tempDir.toString() + File.separator);
    }

    private void addRenderedFaxAttachment(ArrayList<Object> attachments, ArrayList<InputStream> streams, Path attachmentPath) {
        ReflectionTestUtils.invokeMethod(action, "addRenderedFaxAttachment", attachments, streams, attachmentPath, "EFORM", 45);
    }

    private EDoc printableDocument(String docId, String fileName, String contentType) {
        EDoc doc = new EDoc();
        doc.setDocId(docId);
        doc.setFileName(fileName);
        doc.setContentType(contentType);
        doc.setDescription("Consult attachment " + docId);
        return doc;
    }

    private MockHttpServletRequest requestRecordingForwardPath(AtomicReference<String> forwardedPath) {
        MockHttpServletRequest forwardingRequest = new MockHttpServletRequest() {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                forwardedPath.set(path);
                return new RequestDispatcher() {
                    @Override
                    public void forward(ServletRequest servletRequest, ServletResponse servletResponse) {
                        // No rendered body is required; this test only asserts the internal forward URL.
                    }

                    @Override
                    public void include(ServletRequest servletRequest, ServletResponse servletResponse) {
                        throw new UnsupportedOperationException("include is not used by this test");
                    }
                };
            }
        };
        forwardingRequest.setContextPath("/carlos");
        return forwardingRequest;
    }
}
