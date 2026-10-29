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
package io.github.carlos_emr.carlos.eform.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("EFormPDFServlet unit tests")
@Tag("unit")
@Tag("fast")
class EFormPDFServletUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;
    private String previousEformImagesDir;

    @TempDir
    Path root;

    @BeforeEach
    void registerSecurity() {
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        previousEformImagesDir = CarlosProperties.getInstance().getProperty("EFORM_IMAGES_DIR");
    }

    @AfterEach
    void restoreEformImagesDir() {
        if (previousEformImagesDir == null) {
            CarlosProperties.getInstance().remove("EFORM_IMAGES_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", previousEformImagesDir);
        }
    }

    /** Points the eForm image directory at a fresh folder and puts one PDF template in it. */
    private Path imageDirectoryWithTemplate(String templateName) throws IOException {
        Path images = Files.createDirectories(root.resolve("eform-images"));
        writePdf(images.resolve(templateName + ".pdf"), 1);
        CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", images.toString());
        return images;
    }

    private static void writePdf(Path target, int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(target.toFile());
        }
    }

    /**
     * Runs the real servlet as a logged-in provider, so a request that reaches PDF generation
     * renders a PDF (the page footer needs the provider's name). Without this a traversal request
     * would fail on the missing session rather than on the template check.
     */
    private static void postAsProvider(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Provider provider = new Provider();
        provider.setLastName("FAKE");
        provider.setFirstName("Printer");
        when(loggedInInfo.getLoggedInProvider()).thenReturn(provider);
        try (MockedStatic<LoggedInInfo> session = mockStatic(LoggedInInfo.class)) {
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            new ContextEFormPDFServlet().doPost(request, response);
        }
    }

    private static MockHttpServletRequest templateRequest(String template) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/createpdf");
        request.setParameter("demographic_no", "123");
        request.setParameter("__title", "FAKE-Template");
        request.setParameter("__template", template);
        return request;
    }

    @Test
    @DisplayName("should render the named template from the eForm image directory")
    void shouldRenderTemplate_whenNameIsSingleComponent() throws Exception {
        imageDirectoryWithTemplate("consult");
        allowRead("123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        postAsProvider(templateRequest("consult"), response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        try (PDDocument pdf = Loader.loadPDF(response.getContentAsByteArray())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "template \"{0}\"")
    @ValueSource(strings = {"../other-patient/scan", "../../other-patient/scan", "sub/../../other-patient/scan",
            "..\\other-patient\\scan", ".hidden"})
    @DisplayName("should refuse a template outside the eForm image directory without reading it")
    void shouldRefuseTemplate_whenNameLeavesImageDirectory(String template) throws Exception {
        // Issue #5316: __template went straight into new PdfReader(imageDir + "/" + template), so an
        // eForm reader could overlay any PDF the server can read, such as another patient's scan.
        imageDirectoryWithTemplate("consult");
        Path otherPatient = Files.createDirectories(root.resolve("other-patient"));
        writePdf(otherPatient.resolve("scan.pdf"), 3);
        allowRead("123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        postAsProvider(templateRequest(template), response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).isEqualTo("Invalid PDF template.").doesNotContain(template);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should refuse an absolute template path")
    void shouldRefuseTemplate_whenNameIsAbsolutePath() throws Exception {
        imageDirectoryWithTemplate("consult");
        Path otherPatient = Files.createDirectories(root.resolve("other-patient"));
        writePdf(otherPatient.resolve("scan.pdf"), 3);
        allowRead("123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        postAsProvider(templateRequest(otherPatient.resolve("scan").toString()), response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest(name = "template \"{0}\"")
    @ValueSource(strings = {"", "  "})
    @DisplayName("should refuse a request that names no template as a bad request")
    void shouldRefuseRequest_whenTemplateIsBlank(String template) throws Exception {
        // Without a template the servlet cannot render; this used to surface as a 500 after the
        // PDF writer failed on an empty document.
        imageDirectoryWithTemplate("consult");
        allowRead("123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        postAsProvider(templateRequest(template), response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should refuse a request with no template parameter as a bad request")
    void shouldRefuseRequest_whenTemplateParameterIsMissing() throws Exception {
        imageDirectoryWithTemplate("consult");
        allowRead("123");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/createpdf");
        request.setParameter("demographic_no", "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        postAsProvider(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should load a print layout file from the eForm image directory")
    void shouldLoadPrintLayout_whenFileIsInImageDirectory() throws Exception {
        Path images = imageDirectoryWithTemplate("consult");
        Files.writeString(images.resolve("consultPrintCfg.txt"), "patientName=left, 50, 100, 0, BaseFont.HELVETICA, 12\n");

        Properties layout = new EFormPDFServlet().getCfgProp("consultPrintCfg.txt");

        assertThat(layout.getProperty("patientName")).isEqualTo("left, 50, 100, 0, BaseFont.HELVETICA, 12");
        assertThat(new EFormPDFServlet().getCfgProp(".consultPrintCfg.txt")).isEmpty();
        assertThat(new EFormPDFServlet().getCfgProp("../consultPrintCfg.txt")).isEmpty();
    }

    private void allowRead(String demographicNo) {
        when(securityInfoManager.hasPrivilege(
                nullable(LoggedInInfo.class), eq("_eform"), eq("r"), eq(demographicNo))).thenReturn(true);
    }

    @Test
    @DisplayName("should set inline PDF content disposition with the static filename")
    void shouldSetInlineContentDisposition_whenStreamingPdf() throws Exception {
        EFormPDFServlet servlet = new HeaderOnlyEFormPDFServlet();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/EFormPDFServlet");
        request.setParameter("demographic_no", "123");
        allowRead("123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.doPost(request, response);

        assertThat(response.getHeader("Content-disposition")).isEqualTo("inline; filename=\"filename_.pdf\"");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("should refuse to generate a PDF for a demographic the caller may not read")
    void shouldRefuseGeneration_whenDemographicNotReadable() {
        // This servlet is mapped at /eform/createpdf independently of any action, so it is directly
        // reachable. It previously performed no authorization at all: authentication alone let any
        // user generate a PDF for any patient.
        EFormPDFServlet servlet = new HeaderOnlyEFormPDFServlet();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/createpdf");
        request.setParameter("demographic_no", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> servlet.doPost(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eform");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    /** The real servlet, with a servlet context so it can run outside a container. */
    private static final class ContextEFormPDFServlet extends EFormPDFServlet {
        @Override
        public ServletContext getServletContext() {
            return mock(ServletContext.class);
        }
    }

    private static final class HeaderOnlyEFormPDFServlet extends EFormPDFServlet {
        @Override
        protected ByteArrayOutputStream generatePDFDocumentBytes(HttpServletRequest req, ServletContext ctx, int multiple) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.writeBytes(new byte[] {1, 2, 3, 4});
            return output;
        }

        @Override
        public ServletContext getServletContext() {
            return mock(ServletContext.class);
        }
    }
}
