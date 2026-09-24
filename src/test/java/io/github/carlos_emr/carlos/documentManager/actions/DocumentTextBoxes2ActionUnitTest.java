// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Exercises the word-box endpoint with real PDF extraction and HTTP method checks.
 * @since 2026-09-20
 */
class DocumentTextBoxes2ActionUnitTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo info;
    private SecurityInfoManager security;

    @BeforeEach
    void setUpEndpoint() {
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("docId", "42");
        request.setParameter("page", "1");
        response = new MockHttpServletResponse();
        info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(info), eq("_edoc"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
    }

    @Test
    void shouldRejectPost_whenMethodIsNotReadOnly() throws Exception {
        request.setMethod("POST");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            assertThat(execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
            verifyNoInteractions(security);
            documents.verifyNoInteractions();
        }
    }

    @Test
    void shouldRefuseDocumentRead_whenPrivilegeMissing() {
        when(security.hasPrivilege(eq(info), eq("_edoc"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);
        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_edoc");
    }

    @Test
    void shouldReturnBadRequest_whenPageCannotParse() throws Exception {
        request.setParameter("page", "abc");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            assertThat(execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            documents.verifyNoInteractions();
        }
    }

    @Test
    void shouldReturnNotFound_whenDocumentHasNoFilename() throws Exception {
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(new EDoc());
            assertThat(execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Test
    void shouldCheckChartAccess_whenDocumentIsPatientLinked() {
        EDoc document = document();
        document.setModule("demographic");
        document.setModuleId("770001");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("patient record");
            verify(security).isAllowedAccessToPatientRecord(info, 770001);
        }
    }

    @Test
    void shouldCheckChartAccessBeforeMissingFilename_whenDocumentIsPatientLinked() {
        EDoc document = new EDoc();
        document.setModule("demographic");
        document.setModuleId("770001");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);

            assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("patient record");

            verify(security).isAllowedAccessToPatientRecord(info, 770001);
        }
    }

    @Test
    void shouldReturnEmptyWordsWithSuccessfulRead_whenPdfHasNoTextLayer(@TempDir Path documentDir)
            throws Exception {
        saveBlankPdf(documentDir.resolve("source.pdf"));
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<CarlosProperties> configuration = configureDocumentDir(documentDir)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document());

            assertThat(execute()).isEqualTo(ActionSupport.NONE);

            JsonNode payload = mapper.readTree(response.getContentAsString());
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).contains("application/json");
            assertThat(payload.get("page").asInt()).isEqualTo(1);
            assertThat(payload.get("textLayerRead").asBoolean()).isTrue();
            assertThat(payload.get("hasTextLayer").asBoolean()).isFalse();
            assertThat(payload.get("words").size()).isZero();
        }
    }

    @Test
    void shouldReturnGeometryWithoutPatientText_whenPdfHasTextLayer(@TempDir Path documentDir)
            throws Exception {
        saveTextPdf(documentDir.resolve("source.pdf"), "SensitiveFixture Patient");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<CarlosProperties> configuration = configureDocumentDir(documentDir)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document());

            assertThat(execute()).isEqualTo(ActionSupport.NONE);

            JsonNode payload = mapper.readTree(response.getContentAsString());
            assertThat(payload.get("textLayerRead").asBoolean()).isTrue();
            assertThat(payload.get("hasTextLayer").asBoolean()).isTrue();
            assertThat(payload.get("words").size()).isGreaterThan(0);
            JsonNode first = payload.get("words").get(0);
            assertThat(first.get("x").asDouble()).isBetween(0d, 1d);
            assertThat(first.get("y").asDouble()).isBetween(0d, 1d);
            assertThat(response.getContentAsString()).doesNotContain("SensitiveFixture", "Patient");
        }
    }

    @Test
    void shouldReportFailedRead_whenPdfCannotBeParsed(@TempDir Path documentDir) throws Exception {
        Files.writeString(documentDir.resolve("source.pdf"), "not a PDF");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<CarlosProperties> configuration = configureDocumentDir(documentDir)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document());

            assertThat(execute()).isEqualTo(ActionSupport.NONE);

            JsonNode payload = mapper.readTree(response.getContentAsString());
            assertThat(payload.get("textLayerRead").asBoolean()).isFalse();
            assertThat(payload.get("hasTextLayer").asBoolean()).isFalse();
            assertThat(payload.get("words").size()).isZero();
        }
    }

    @Test
    void shouldReturnHeadersWithoutBody_whenRequestUsesHead(@TempDir Path documentDir) throws Exception {
        saveBlankPdf(documentDir.resolve("source.pdf"));
        request.setMethod("HEAD");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document());

            assertThat(execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).contains("application/json");
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }

    private String execute() throws Exception {
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            return new DocumentTextBoxes2Action(security).execute();
        }
    }

    private static EDoc document() {
        EDoc document = new EDoc();
        document.setFileName("source.pdf");
        return document;
    }

    private static MockedStatic<CarlosProperties> configureDocumentDir(Path documentDir) {
        MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
        CarlosProperties properties = mock(CarlosProperties.class);
        configuration.when(CarlosProperties::getInstance).thenReturn(properties);
        when(properties.getDocumentDirectory()).thenReturn(documentDir.toString());
        return configuration;
    }

    private static void saveBlankPdf(Path target) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            pdf.save(target.toFile());
        }
    }

    private static void saveTextPdf(Path target, String text) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(72, 600);
                content.showText(text);
                content.endText();
            }
            pdf.save(target.toFile());
        }
    }
}
