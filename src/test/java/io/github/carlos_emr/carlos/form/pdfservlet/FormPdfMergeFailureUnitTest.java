/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.pdfservlet;

import io.github.carlos_emr.carlos.eform.util.EFormPDFServlet;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
class FormPdfMergeFailureUnitTest extends CarlosUnitTestBase {
    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0} export reports a failed merge")
    @ValueSource(strings = {"form", "eform"})
    void shouldFailTheResponse_whenTheSecondPageCannotBeStaged(String module) throws Exception {
        Path firstStage = Files.createTempFile(tempDir, "page-", ".pdf");
        AtomicInteger stagingAttempts = new AtomicInteger();
        try (var paths = mockStatic(PathValidationUtils.class, call -> {
            if (call.getMethod().getName().equals("createSecureTempFile")
                    && "pdf-merge-source-".equals(call.getArgument(0))) {
                if (stagingAttempts.incrementAndGet() == 1) {
                    return firstStage.toFile();
                }
                throw new IOException("Temporary PDF staging unavailable");
            }
            return call.callRealMethod();
        })) {
            var response = exportTwoPages(module, false);

            assertThat(stagingAttempts).hasValue(2);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(response.getContentType()).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(firstStage).doesNotExist();
            logActionMock.verifyNoInteractions();
        }
    }

    @ParameterizedTest(name = "{0} rejects an unreadable required page")
    @ValueSource(strings = {"form", "eform"})
    void shouldFailTheResponse_whenTheSecondPageIsUnreadable(String module) throws Exception {
        var response = exportTwoPages(module, true);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(response.getContentType()).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0} includes both valid pages")
    @ValueSource(strings = {"form", "eform"})
    void shouldIncludeAllRequestedPages_whenBothPagesAreReadable(String module) throws Exception {
        var response = exportTwoPages(module, false);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        try (PDDocument document = Loader.loadPDF(response.getContentAsByteArray())) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
        }
    }

    private MockHttpServletResponse exportTwoPages(String module, boolean unreadableSecondPage) throws Exception {
        HttpServlet servlet = servletWithPages(module, unreadableSecondPage);
        servlet.init(new MockServletConfig());
        var request = new MockHttpServletRequest("POST", "/" + module + "/createpdf");
        var response = new MockHttpServletResponse();
        var actor = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), actor);
        request.setParameter("demographic_no", "123");
        request.setParameter("multiple", "2");
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(actor, "_" + module, "r", "123")).thenReturn(true);

        servlet.service(request, response);
        return response;
    }

    private static HttpServlet servletWithPages(String module, boolean unreadableSecondPage) {
        if (module.equals("form")) {
            return new FrmPDFServlet() {
                @Override
                protected ByteArrayOutputStream generatePDFDocumentBytes(HttpServletRequest request,
                        ServletContext context, ByteArrayOutputStream output, int page) throws IOException {
                    return pageBytes(page, unreadableSecondPage);
                }
            };
        }
        return new EFormPDFServlet() {
            @Override
            protected ByteArrayOutputStream generatePDFDocumentBytes(HttpServletRequest request,
                    ServletContext context, int page) throws IOException {
                return pageBytes(page, unreadableSecondPage);
            }
        };
    }

    private static ByteArrayOutputStream pageBytes(int page, boolean unreadableSecondPage) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (unreadableSecondPage && page == 1) return output;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(output);
        }
        return output;
    }
}
