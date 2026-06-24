package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
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

    private AutoCloseable mocks;
    private MockedStatic<ConvertToEdoc> convertToEdocMock;
    private EformDataManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(NioFileManager.class, org.mockito.Mockito.mock(NioFileManager.class));
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FormsManager.class, formsManager);

        manager = new EformDataManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "eFormDataDao", eFormDataDao);
        injectDependency(manager, "documentManager", documentManager);
        injectDependency(manager, "documentAttachmentManager", documentAttachmentManager);
        injectDependency(manager, "formsManager", formsManager);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_eform"), eq(SecurityInfoManager.UPDATE), isNull())).thenReturn(true);

        EFormData eformData = new EFormData();
        eformData.setId(77);
        eformData.setFormName("Consult Form");
        eformData.setFormData("<html></html>");
        when(eFormDataDao.find(77)).thenReturn(eformData);

        convertToEdocMock = mockStatic(ConvertToEdoc.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (convertToEdocMock != null) convertToEdocMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should throw PDFGenerationException when conversion returns null path")
    void shouldThrowPdfGenerationExceptionWhenConversionReturnsNullPath() {
        convertToEdocMock.when(() -> ConvertToEdoc.saveAsTempPDF(any(EFormData.class))).thenReturn(null);

        assertThatThrownBy(() -> manager.createEformPDF(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("HTML-to-PDF conversion");
    }
}
