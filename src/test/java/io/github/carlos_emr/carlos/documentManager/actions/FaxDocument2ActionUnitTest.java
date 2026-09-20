// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

/** Exercises fax handoff authorization and refusal paths using actual file containment checks.
 * @since 2026-09-20
 */
class FaxDocument2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo info;
    private SecurityInfoManager security;
    private FaxManager faxManager;

    @BeforeEach
    void setUpGate() {
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("docId", "42");
        response = new MockHttpServletResponse();
        info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = mock(SecurityInfoManager.class);
        faxManager = mock(FaxManager.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(FaxManager.class, faxManager);
        when(security.hasPrivilege(eq(info), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(info), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        when(faxManager.getFaxGatewayAccounts(info)).thenReturn(List.of(mock(FaxConfig.class)));
    }

    @Test
    void shouldRejectPost_whenMethodIsNotGet() {
        request.setMethod("POST");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET");
        verifyNoInteractions(security, faxManager);
    }

    @Test
    void shouldRefuseDocumentRead_whenPrivilegeMissing() {
        when(security.hasPrivilege(eq(info), eq("_edoc"), eq("r"), isNull())).thenReturn(false);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_edoc");

        verifyNoInteractions(faxManager);
    }

    @Test
    void shouldRefuseFaxRead_whenPrivilegeMissing() {
        when(security.hasPrivilege(eq(info), eq("_fax"), eq("r"), isNull())).thenReturn(false);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_fax");

        verifyNoInteractions(faxManager);
    }

    @Test
    void shouldExplainInvalidId_whenIdCannotParse() {
        request.setParameter("docId", "not-an-id");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            assertThat(execute()).isEqualTo("noFax");
            assertThat(request.getAttribute("message")).isEqualTo("A valid document ID is required.");
            verifyNoInteractions(faxManager);
            documents.verifyNoInteractions();
        }
    }

    @Test
    void shouldExplainMissingAccount_whenNoActiveAccountExists() {
        when(faxManager.getFaxGatewayAccounts(info)).thenReturn(List.of());
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            assertThat(execute()).isEqualTo("noFax");
            assertThat(request.getAttribute("message")).asString().contains("No active fax accounts");
            documents.verifyNoInteractions();
        }
    }

    @Test
    void shouldExplainUnknownDocument_whenLookupHasNoFilename() {
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(new EDoc());

            assertThat(execute()).isEqualTo("noFax");

            assertThat(request.getAttribute("message")).isEqualTo("Document not found.");
        }
    }

    @Test
    void shouldRejectNonPdf_whenContentTypeIsNotPdf() {
        EDoc document = document("application/pdfx", "/unused/file.pdf");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);

            assertThat(execute()).isEqualTo("noFax");

            assertThat(request.getAttribute("message")).asString().contains("Only PDF documents");
        }
    }

    @Test
    void shouldRejectStoredPath_whenOutsideDocumentDirectory(@TempDir Path root) throws Exception {
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        Path outside = Files.write(root.resolve("other-patient.pdf"), new byte[]{1, 2, 3});
        EDoc document = document("application/pdf", outside.toString());
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            CarlosProperties configuration = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(configuration);
            when(configuration.getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"))
                    .thenReturn(documentDir.toString());

            assertThat(execute()).isEqualTo("noFax");

            assertThat(request.getAttribute("message")).isEqualTo("Invalid document path.");
            assertThat(request.getAttribute("preparedFaxTarget")).isNull();
        }
    }

    @Test
    void shouldExplainMissingFile_whenInsideDocumentDirectory(@TempDir Path documentDir) {
        EDoc document = document("application/pdf", documentDir.resolve("missing.pdf").toString());
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            CarlosProperties configuration = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(configuration);
            when(configuration.getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"))
                    .thenReturn(documentDir.toString());

            assertThat(execute()).isEqualTo("noFax");

            assertThat(request.getAttribute("message")).isEqualTo("Document file is not available on the server.");
            assertThat(request.getAttribute("preparedFaxTarget")).isNull();
        }
    }

    @Test
    void shouldOfferPreparedFaxHandoff_whenUnlinkedPdfIsValid(@TempDir Path documentDir) throws Exception {
        Path pdf = Files.write(documentDir.resolve("source.pdf"), new byte[]{1, 2, 3});
        EDoc document = document("application/pdf", pdf.toString());
        request.setContextPath("/carlos");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            CarlosProperties configuration = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(configuration);
            when(configuration.getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"))
                    .thenReturn(documentDir.toString());

            assertThat(execute()).isEqualTo("prepareFax");

            assertThat(request.getAttribute("preparedFaxTarget")).isEqualTo(
                    "/carlos/fax/faxAction?method=prepareFax&transactionType=DOCUMENT&transactionId=42&demographicNo=0");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verify(security, never()).isAllowedAccessToPatientRecord(info, 0);
        }
    }

    @Test
    void shouldRejectLinkedPatient_whenChartAccessMissing(@TempDir Path documentDir) throws Exception {
        Path pdf = Files.write(documentDir.resolve("source.pdf"), new byte[]{1, 2, 3});
        EDoc document = document("application/pdf", pdf.toString());
        document.setModule("demographic");
        document.setModuleId("770001");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            CarlosProperties configuration = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(configuration);
            when(configuration.getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"))
                    .thenReturn(documentDir.toString());

            assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("patient record");

            verify(security).isAllowedAccessToPatientRecord(info, 770001);
            assertThat(request.getAttribute("preparedFaxTarget")).isNull();
        }
    }

    @Test
    void shouldCheckPatientAccessBeforeRevealingFileType_whenLinkedPatientIsDenied() {
        EDoc document = document("text/plain", "/private/patient-document.txt");
        document.setModule("demographic");
        document.setModuleId("770001");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);

            assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("patient record");

            assertThat(request.getAttribute("message")).isNull();
            verify(security).isAllowedAccessToPatientRecord(info, 770001);
        }
    }

    private String execute() {
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            return new FaxDocument2Action().execute();
        }
    }

    private static EDoc document(String contentType, String filePath) {
        EDoc document = new EDoc();
        document.setFileName("source.pdf");
        document.setContentType(contentType);
        document.setFilePath(filePath);
        return document;
    }
}
