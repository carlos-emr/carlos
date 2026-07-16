package io.github.carlos_emr.carlos.managers;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfRenderer;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EformDataManagerImpl createEformPDF")
@Tag("unit")
@Tag("fast")
class EformDataManagerImplCreatePdfUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EFormDataDao eFormDataDao;
    @Mock private DocumentManager documentManager;
    @Mock private DocumentAttachmentManager documentAttachmentManager;
    @Mock private FormsManager formsManager;
    @Mock private LoggedInInfo loggedInInfo;
    @Mock private EFormBrowserPdfRenderer eFormBrowserPdfRenderer;

    private AutoCloseable mocks;
    private EformDataManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(NioFileManager.class, org.mockito.Mockito.mock(NioFileManager.class));
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FormsManager.class, formsManager);

        manager = new EformDataManagerImpl(securityInfoManager, eFormBrowserPdfRenderer);
        injectDependency(manager, "eFormDataDao", eFormDataDao);
        injectDependency(manager, "documentManager", documentManager);
        injectDependency(manager, "documentAttachmentManager", documentAttachmentManager);
        injectDependency(manager, "formsManager", formsManager);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_eform"), eq(SecurityInfoManager.UPDATE), isNull())).thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        EFormData eformData = new EFormData();
        eformData.setId(77);
        eformData.setDemographicId(123);
        eformData.setFormName("Consult Form");
        eformData.setFormData("<html></html>");
        eformData.setDemographicId(1);
        when(eFormDataDao.find(77)).thenReturn(eformData);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should throw PDFGenerationException when browser renderer returns null path")
    void shouldThrowPdfGenerationException_whenBrowserRendererReturnsNullPath() throws Exception {
        when(eFormBrowserPdfRenderer.renderSavedEformPdf(77, "999998")).thenReturn(null);

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("browser rendering");
    }

    @Test
    @DisplayName("should return the readable PDF path from the browser renderer")
    void shouldReturnReadablePdfPath_whenBrowserRendererSucceeds() throws Exception {
        Path pdfPath = Files.createTempFile("eform-rendered-", ".pdf");
        try {
            Files.write(pdfPath, new byte[] {1, 2, 3, 4});
            when(eFormBrowserPdfRenderer.renderSavedEformPdf(77, "999998")).thenReturn(pdfPath);

            Path actualPath = manager.createEformPDF(loggedInInfo, 77);

            assertThat(actualPath).isEqualTo(pdfPath);
            verify(eFormBrowserPdfRenderer).renderSavedEformPdf(77, "999998");
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    @DisplayName("should throw PDFGenerationException when browser renderer returns an unreadable path")
    void shouldThrowPdfGenerationException_whenBrowserRendererReturnsUnreadablePath() throws Exception {
        // Create a unique temp path and delete it so the renderer result is guaranteed unreadable,
        // regardless of any files other processes may have left in the shared temp directory.
        Path pdfPath = Files.createTempFile("eform-rendered-missing-", ".pdf");
        Files.deleteIfExists(pdfPath);
        when(eFormBrowserPdfRenderer.renderSavedEformPdf(77, "999998")).thenReturn(pdfPath);

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("unreadable temporary file");

        verify(eFormBrowserPdfRenderer).renderSavedEformPdf(77, "999998");
    }

    @Test
    @DisplayName("should require demographic-scoped eForm read privilege for temporary PDF rendering")
    void shouldRequireDemographicScopedEformReadPrivilege_forTemporaryPdfRendering() {
        convertToEdocMock.when(() -> ConvertToEdoc.saveAsTempPDF(any(EFormData.class))).thenReturn(null);

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class);

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null);
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.UPDATE, "123");
    }

    @Test
    @DisplayName("should throw SecurityException before rendering when demographic-scoped eForm read is denied")
    void shouldThrowSecurityException_whenDemographicScopedEformReadDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123")).thenReturn(false);

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        convertToEdocMock.verifyNoInteractions();
    }
}
