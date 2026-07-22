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
        when(faxManager.resolveAndValidateFilePath(pdf.toString())).thenReturn(pdf);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", pdf.toString());
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
        when(faxManager.resolveAndValidateFilePath(corrupt.toString())).thenReturn(corrupt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", corrupt.toString());
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
