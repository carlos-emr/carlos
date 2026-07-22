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
package io.github.carlos_emr.carlos.fax.action;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Fax2Action authorization unit tests")
@Tag("unit")
@Tag("fast")
class Fax2ActionAuthorizationUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should reject getPageCount when fax read privilege is missing")
    void shouldRejectGetPageCount_whenFaxReadPrivilegeMissing() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/tmp/example.pdf");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPageCount();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }


    @Test
    @DisplayName("should reject prepareFax when fax read privilege is missing")
    void shouldRejectPrepareFax_whenFaxReadPrivilegeMissing() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "eform");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            assertThat(action.prepareFax()).isEqualTo(Fax2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verifyNoInteractions(documentAttachmentManager);
        }
    }

    @Test
    @DisplayName("should reject getPreview when jobId is not numeric")
    void shouldRejectGetPreview_whenJobIdIsNotNumeric() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("jobId", "abc");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("should reject getPreview when pageNumber is not numeric")
    void shouldRejectGetPreview_whenPageNumberIsNotNumeric() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pageNumber", "abc");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("should serve the image preview with an inline content disposition")
    void shouldServeImagePreview_withInlineContentDisposition(@TempDir Path tempDir) throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        Path previewPng = tempDir.resolve("fax-preview-page.png");
        Files.write(previewPng, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        when(faxManager.getFaxPreviewImage(any(LoggedInInfo.class), eq("/tmp/carlos-temp/fax.pdf"), eq(1)))
                .thenReturn(previewPng);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/tmp/carlos-temp/fax.pdf");
        request.setParameter("showAs", "image");
        request.setParameter("pageNumber", "1");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentType()).isEqualTo("image/png");
            assertThat(response.getHeader("Content-Disposition")).startsWith("inline;");
        }
    }

    @Test
    @DisplayName("should return 403 (not an uncaught throw) when image preview generation is denied by _edoc")
    void shouldReturnForbidden_whenImagePreviewThrowsSecurityException() {
        // getPreview() gates _fax READ, but preview image generation runs through
        // NioFileManager.createCacheVersion2, which enforces an _edoc READ gate. A _fax-only principal
        // therefore triggers a SecurityException deep in the call chain; getPreview must own that error
        // (clean 403) rather than let it escape and leave Struts to write an HTML error page into the
        // image/png stream (direct-response contract).
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);
        when(faxManager.getFaxPreviewImage(any(LoggedInInfo.class), eq("/tmp/carlos-temp/fax.pdf"), eq(1)))
                .thenThrow(new SecurityException("missing required sec object (_edoc)"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/tmp/carlos-temp/fax.pdf");
        request.setParameter("showAs", "image");
        request.setParameter("pageNumber", "1");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }

    @Test
    @DisplayName("should terminate execute with NONE for the direct-response preview methods")
    void shouldReturnNone_whenExecuteDispatchesDirectResponseMethods() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("method", "getPreview");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            // Direct-response contract: streaming methods must resolve to NONE, never a named
            // result or bare null that would let Struts write HTML into the binary download.
            assertThat(new Fax2Action().execute()).isEqualTo(org.apache.struts2.action.Action.NONE);

            // Fresh response per dispatch: the first sendError commits the mock response.
            MockHttpServletResponse secondResponse = new MockHttpServletResponse();
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(secondResponse);
            request.setParameter("method", "getPageCount");
            assertThat(new Fax2Action().execute()).isEqualTo(org.apache.struts2.action.Action.NONE);
        }
    }

    @Test
    @DisplayName("should resolve the page count from a validated fax file path")
    void shouldResolvePageCount_fromValidatedFaxFilePath(@TempDir Path tempDir) throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        Path pdf = tempDir.resolve("fax.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(pdf.toFile());
        }
        // The request parameter itself must satisfy the application-temp-workspace guard (no jobId
        // means the path came directly from the caller); the real PDF bytes live under the JUnit
        // @TempDir and are supplied via the mocked resolution below, mirroring how the direct-path
        // CoverPage.jsp flow only ever names a carlos-temp artifact.
        String requestFaxFilePath = "/tmp/carlos-temp/fax-page-count.pdf";
        when(faxManager.resolveAndValidateFilePath(requestFaxFilePath)).thenReturn(pdf);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", requestFaxFilePath);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new Fax2Action().getPageCount();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("\"pageCount\":1");
        }
    }

    @Test
    @DisplayName("should answer 403 when page-count path validation raises a security exception")
    void shouldReturnForbidden_whenPageCountPathValidationFails() throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(faxManager.resolveAndValidateFilePath("/etc/passwd"))
                .thenThrow(new SecurityException("Path traversal attempt"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/etc/passwd");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new Fax2Action().getPageCount();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Test
    @DisplayName("should answer 404 when the page-count target is not a readable PDF")
    void shouldReturnNotFound_whenPageCountTargetNotReadablePdf(@TempDir Path tempDir) throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        // A corrupt (non-PDF) file is a realistic input in a fax pipeline; PDFBox throws IOException.
        Path corrupt = tempDir.resolve("corrupt.pdf");
        Files.writeString(corrupt, "this is not a pdf");
        // Request parameter must satisfy the application-temp-workspace guard (no jobId); the real
        // (corrupt) file lives under the JUnit @TempDir and is supplied via the mocked resolution.
        String requestFaxFilePath = "/tmp/carlos-temp/fax-corrupt.pdf";
        when(faxManager.resolveAndValidateFilePath(requestFaxFilePath)).thenReturn(corrupt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", requestFaxFilePath);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new Fax2Action().getPageCount();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("should reject a document-store path supplied directly as faxFilePath for page count")
    void shouldSend403_whenPageCountPathParamTargetsDocumentStore() throws Exception {
        // No jobId is supplied, so this path is taken at face value as a direct request-parameter
        // path. Manage Faxes only ever sends jobId for stored documents; a DOCUMENT_DIR path
        // supplied directly must be rejected before FaxManager ever sees it.
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/var/lib/OscarDocument/oscar/document/123.pdf");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new Fax2Action().getPageCount();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(faxManager, never()).resolveAndValidateFilePath(any());
        }
    }

    @Test
    @DisplayName("should return 404 instead of an empty 200 when no preview image is available")
    void shouldReturnNotFound_whenPreviewImageUnavailable() throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);
        // The source PDF is gone: getFaxPreviewImage warns server-side and returns null. An empty
        // 200 left the CoverPage user staring at a broken image with no signal.
        when(faxManager.getFaxPreviewImage(any(LoggedInInfo.class), eq("/tmp/carlos-temp/fax.pdf"), eq(1)))
                .thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/tmp/carlos-temp/fax.pdf");
        request.setParameter("showAs", "image");
        request.setParameter("pageNumber", "1");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }

    @Test
    @DisplayName("should return 500 when the resolved preview file cannot be streamed")
    void shouldReturnServerError_whenPreviewStreamingFails() throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);
        // The file vanished between path resolution and streaming (e.g. concurrent flush): the
        // response must say so while uncommitted, not end as an empty 200.
        when(faxManager.resolveAndValidateFilePath("/tmp/carlos-temp/fax.pdf"))
                .thenReturn(Path.of("/tmp/carlos-temp/vanished-fax.pdf"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/tmp/carlos-temp/fax.pdf");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @DisplayName("should reject a document-store path supplied directly as faxFilePath for preview")
    void shouldSend403_whenPreviewPathParamTargetsDocumentStore() throws Exception {
        // No jobId is supplied, so getPreview treats faxFilePath as a direct request-parameter
        // path. CoverPage.jsp (the only direct-path caller) only ever names a freshly minted
        // carlos-temp artifact; a DOCUMENT_DIR path supplied directly must be rejected before
        // FaxManager ever sees it, closing the pre-existing arbitrary-document-read exposure.
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/var/lib/OscarDocument/oscar/document/123.pdf");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(faxManager, never()).resolveAndValidateFilePath(any());
            verify(faxManager, never()).getFaxPreviewImage(any(LoggedInInfo.class), any(String.class), anyInt());
        }
    }

    @Test
    @DisplayName("should serve preview for a carlos-temp path supplied as faxFilePath")
    void shouldServePreview_whenPathParamIsApplicationTemp(@TempDir Path tempDir) throws Exception {
        // No jobId: faxFilePath is a direct request-parameter path, but it names a CARLOS-owned
        // temp artifact (the CoverPage.jsp pre-send flow), so it must still stream successfully.
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        Path tempPdf = tempDir.resolve("fax-temp-preview.pdf");
        byte[] pdfBytes = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        Files.write(tempPdf, pdfBytes);
        String requestFaxFilePath = "/tmp/carlos-temp/fax-temp-preview.pdf";
        when(faxManager.resolveAndValidateFilePath(requestFaxFilePath)).thenReturn(tempPdf);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", requestFaxFilePath);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray()).isEqualTo(pdfBytes);
        }
    }

    @Test
    @DisplayName("should serve preview through a job binding for stored documents")
    void shouldServePreview_whenJobIdResolvesStoredDocument(@TempDir Path tempDir) throws Exception {
        // jobId resolves a FaxJob whose file_name lives in the document store (DOCUMENT_DIR); the
        // job binds that stored document to a queued fax this user is permitted to see, so it must
        // stream successfully even though the resolved path is outside the temp workspace.
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(42)))
                .thenReturn(true);

        Path storedDoc = tempDir.resolve("stored-fax.pdf");
        byte[] pdfBytes = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        Files.write(storedDoc, pdfBytes);

        FaxJob faxJob = new FaxJob();
        faxJob.setFile_name("/var/lib/OscarDocument/oscar/document/stored-fax.pdf");
        faxJob.setDemographicNo(42);
        when(faxManager.getFaxJob(any(LoggedInInfo.class), eq(77))).thenReturn(faxJob);
        when(faxManager.resolveAndValidateFilePath(faxJob.getFile_name())).thenReturn(storedDoc);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("jobId", "77");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray()).isEqualTo(pdfBytes);
        }
    }

    @Test
    @DisplayName("should reject a stored document served through a job bound to another patient's record")
    void shouldSend403_whenJobDemographicDeniesPatientAccess() throws Exception {
        // The job binds the file to a queued fax, but that fax carries a demographic outside this
        // provider's circle of care; job binding alone must not bypass the patient-record check
        // validateFaxInputs enforces on the send path.
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(99)))
                .thenReturn(false);

        FaxJob faxJob = new FaxJob();
        faxJob.setFile_name("/var/lib/OscarDocument/oscar/document/other-patient-fax.pdf");
        faxJob.setDemographicNo(99);
        when(faxManager.getFaxJob(any(LoggedInInfo.class), eq(88))).thenReturn(faxJob);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("jobId", "88");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(faxManager, never()).resolveAndValidateFilePath(any());
        }
    }

    @Test
    @DisplayName("should render the preview with failure status without logging un-persisted ERROR jobs")
    void shouldSkipFaxClientLog_forUnsavedErrorJobs() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("w"), isNull()))
                .thenReturn(true);

        // createAndSaveFaxJob returns validation-failure jobs un-persisted (no id); queue() must
        // surface them on the preview but write no FaxClientLog row (there is no fax id to log).
        FaxJob errorJob = new FaxJob();
        errorJob.setStatus(FaxJob.STATUS.ERROR);
        errorJob.setStatusString("File missing on local storage or invalid file path.");
        when(faxManager.createAndSaveFaxJob(any(LoggedInInfo.class), anyMap()))
                .thenReturn(List.of(errorJob));

        MockHttpServletRequest request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setRecipientFaxNumber("1234567890");
            action.setFaxFilePath("/tmp/carlos-temp/fax.pdf");

            String result = action.queue();

            assertThat(result).isEqualTo("preview");
            assertThat(request.getAttribute("faxSuccessful")).isEqualTo(false);
            verify(faxManager, never()).logFaxJob(any(), any(), any(), anyInt());
        }
    }
}
