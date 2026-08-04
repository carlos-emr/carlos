package io.github.carlos_emr.carlos.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("EformDataManagerImpl PDF generation and lookup")
@Tag("unit")
@Tag("fast")
class EformDataManagerImplCreatePdfUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EFormDataDao eFormDataDao;
    @Mock private DocumentManager documentManager;
    @Mock private DocumentAttachmentManager documentAttachmentManager;
    @Mock private FormsManager formsManager;
    @Mock private LoggedInInfo loggedInInfo;
    @Mock private EFormBrowserPdfService eFormBrowserPdfService;

    private AutoCloseable mocks;
    private EformDataManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(NioFileManager.class, org.mockito.Mockito.mock(NioFileManager.class));
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FormsManager.class, formsManager);

        manager = new EformDataManagerImpl(securityInfoManager, eFormBrowserPdfService);
        injectDependency(manager, "eFormDataDao", eFormDataDao);
        injectDependency(manager, "documentManager", documentManager);
        injectDependency(manager, "documentAttachmentManager", documentAttachmentManager);
        injectDependency(manager, "formsManager", formsManager);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_eform"), eq(SecurityInfoManager.READ), eq("123"))).thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        EFormData eformData = new EFormData();
        eformData.setId(77);
        eformData.setDemographicId(123);
        eformData.setFormName("Consult Form");
        eformData.setFormData("<html></html>");
        when(eFormDataDao.find(77)).thenReturn(eformData);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should throw PDFGenerationException when browser renderer returns null path")
    void shouldThrowPdfGenerationException_whenBrowserRendererReturnsNullPath() throws Exception {
        when(eFormBrowserPdfService.renderSavedEformPdf(
                loggedInInfo, 77, (EFormRenderApproval) null)).thenReturn(null);

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("browser rendering");
    }

    @Test
    @DisplayName("should return the readable PDF path from the browser renderer")
    void shouldReturnReadablePdfPath_whenBrowserRendererSucceeds() throws Exception {
        // Filename must match the RenderedEformPdf guard prefix (the real renderer output name).
        Path pdfPath = Files.createTempFile("eform-browser-render-", ".pdf");
        try {
            Files.write(pdfPath, new byte[] {1, 2, 3, 4});
            when(eFormBrowserPdfService.renderSavedEformPdf(
                    loggedInInfo, 77, (EFormRenderApproval) null))
                .thenReturn(new EFormBrowserPdfService.RenderedEformPdf(pdfPath));

            Path actualPath = manager.createEformPDF(loggedInInfo, 77);

            assertThat(actualPath).isEqualTo(pdfPath);
            verify(eFormBrowserPdfService).renderSavedEformPdf(
                    loggedInInfo, 77, (EFormRenderApproval) null);
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    @DisplayName("should throw PDFGenerationException when browser renderer returns an unreadable path")
    void shouldThrowPdfGenerationException_whenBrowserRendererReturnsUnreadablePath() throws Exception {
        // Create a unique temp path and delete it so the renderer result is guaranteed unreadable,
        // regardless of any files other processes may have left in the shared temp directory.
        // Filename must match the RenderedEformPdf guard prefix (the real renderer output name).
        Path pdfPath = Files.createTempFile("eform-browser-render-missing-", ".pdf");
        Files.deleteIfExists(pdfPath);
        when(eFormBrowserPdfService.renderSavedEformPdf(
                loggedInInfo, 77, (EFormRenderApproval) null))
                .thenReturn(new EFormBrowserPdfService.RenderedEformPdf(pdfPath));

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("unreadable temporary file");

        verify(eFormBrowserPdfService).renderSavedEformPdf(
                loggedInInfo, 77, (EFormRenderApproval) null);
    }

    @Test
    @DisplayName("should require demographic-scoped eForm read privilege before browser rendering")
    void shouldRequireDemographicScopedEformReadPrivilege_beforeBrowserRendering() throws Exception {
        when(eFormBrowserPdfService.renderSavedEformPdf(
                loggedInInfo, 77, (EFormRenderApproval) null)).thenReturn(null);

        // This test's contract is the demographic-scoped privilege check below; the null-path →
        // exception-message contract is owned by shouldThrowPdfGenerationException_whenBrowserRendererReturnsNullPath,
        // so assert only the exception type here to avoid duplicating that message assertion.
        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class);

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null);
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.UPDATE, "123");
        verify(eFormBrowserPdfService).renderSavedEformPdf(
                loggedInInfo, 77, (EFormRenderApproval) null);
    }

    @Test
    @DisplayName("should throw SecurityException before rendering when demographic-scoped eForm read is denied")
    void shouldThrowSecurityException_whenDemographicScopedEformReadDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123")).thenReturn(false);

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        verifyNoInteractions(eFormBrowserPdfService);
    }

    @Test
    @DisplayName("should throw SecurityException for missing eForm without broad read")
    void shouldThrowSecurityException_whenMissingEform() {
        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 404))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null);
        verifyNoInteractions(eFormBrowserPdfService);
    }

    @Test
    @DisplayName("should throw PDFGenerationException for missing eForm when broad read is allowed")
    void shouldThrowPdfGenerationException_whenMissingEformAndBroadReadAllowed() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null)).thenReturn(true);

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 404))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("eForm was not found");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null);
        verifyNoInteractions(eFormBrowserPdfService);
    }

    @Test
    @DisplayName("should throw SecurityException when broad eForm read is denied for lookup")
    void shouldThrowSecurityException_whenFindByFdidBroadReadDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null)).thenReturn(false);

        assertThatThrownBy(() -> manager.findByFdid(loggedInInfo, 77))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");

        verify(eFormDataDao, never()).find(77);
    }

    @Test
    @DisplayName("should match attached eForm HRMs when id metadata is numeric string")
    void shouldMatchAttachedEFormHrmDocuments_whenIdMetadataIsNumericString() {
        HashMap<String, Object> hrmDocument = new HashMap<>();
        hrmDocument.put("id", "43");
        ArrayList<HashMap<String, ? extends Object>> allHrmDocuments = new ArrayList<>();
        allHrmDocuments.add(hrmDocument);
        when(documentAttachmentManager.getEFormAttachments(loggedInInfo, 77, DocumentType.HRM, 123))
                .thenReturn(List.of("43"));

        try (MockedStatic<HRMUtil> hrmUtilMock = mockStatic(HRMUtil.class)) {
            hrmUtilMock.when(() -> HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, "123", false))
                    .thenReturn(allHrmDocuments);

            ArrayList<HashMap<String, ? extends Object>> result =
                    manager.getHRMDocumentsAttachedToEForm(loggedInInfo, "77", "123");

            assertThat(result).containsExactly(hrmDocument);
        }
    }

    @Test
    @DisplayName("should ignore malformed attached eForm HRM ids")
    void shouldIgnoreMalformedAttachedEFormHrmIds() {
        when(documentAttachmentManager.getEFormAttachments(loggedInInfo, 77, DocumentType.HRM, 123))
                .thenReturn(List.of("not-a-number"));

        try (MockedStatic<HRMUtil> hrmUtilMock = mockStatic(HRMUtil.class)) {
            hrmUtilMock.when(() -> HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, "123", false))
                    .thenReturn(new ArrayList<>());

            assertThat(manager.getHRMDocumentsAttachedToEForm(loggedInInfo, "77", "123")).isEmpty();
        }
    }
}
