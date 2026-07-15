package io.github.carlos_emr.carlos.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FaxManagerImpl")
@Tag("unit")
@Tag("fast")
class FaxManagerImplUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private NioFileManager nioFileManager;
    @Mock private FaxConfigDao faxConfigDao;
    @Mock private ClinicDAO clinicDAO;
    @Mock private LoggedInInfo loggedInInfo;

    private AutoCloseable mocks;
    private MockedStatic<io.github.carlos_emr.carlos.documentManager.EDocUtil> eDocUtilMock;
    private FaxManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        manager = Mockito.spy(new FaxManagerImpl());
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "nioFileManager", nioFileManager);
        injectDependency(manager, "faxConfigDao", faxConfigDao);
        injectDependency(manager, "clinicDAO", clinicDAO);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);

        FaxConfig faxConfig = new FaxConfig();
        faxConfig.setFaxNumber("1234567890");
        faxConfig.setFaxUser("fax-user");
        when(faxConfigDao.getActiveConfigByNumber("1234567890")).thenReturn(faxConfig);

        Clinic clinic = new Clinic();
        clinic.setClinicName("Test Clinic");
        clinic.setClinicAddress("123 Main St");
        when(clinicDAO.getClinic()).thenReturn(clinic);

        eDocUtilMock = Mockito.mockStatic(io.github.carlos_emr.carlos.documentManager.EDocUtil.class);
        eDocUtilMock.when(() -> io.github.carlos_emr.carlos.documentManager.EDocUtil.getPDFPageCount(any(String.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (eDocUtilMock != null) eDocUtilMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should copy allowed temp renderer PDFs into Oscar documents before queuing")
    void shouldCopyAllowedTempRendererPdfIntoOscarDocuments_beforeQueuingFaxJob() throws Exception {
        Path tempRoot = Files.createTempDirectory("fax-renderer-temp-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        resetAllowedTempDirectoriesCache();
        try {
            Path tempPdf = Files.createTempFile(tempRoot, "eform-browser-render-", ".pdf");
            Path copiedPdf = Path.of("/var/lib/OscarDocument/oscar/document", tempPdf.getFileName().toString());
            when(nioFileManager.copyFileToOscarDocuments(tempPdf.toString())).thenReturn(copiedPdf.toString());
            doReturn(copiedPdf).when(manager).resolveAndValidateFilePath(copiedPdf.toString());

            FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                    "faxFilePath", tempPdf.toString(),
                    "recipient", "Test Recipient",
                    "recipientFaxNumber", "123-456-7890",
                    "senderFaxNumber", "1234567890",
                    "demographicNo", 17));

            verify(nioFileManager).copyFileToOscarDocuments(tempPdf.toString());
            verify(manager).resolveAndValidateFilePath(copiedPdf.toString());
            assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.WAITING);
            assertThat(faxJob.getFile_name()).isEqualTo(tempPdf.getFileName().toString());
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            Files.walk(tempRoot)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
            Files.deleteIfExists(tempRoot);
        }
    }


    @Test
    @DisplayName("should resolve allowed temp renderer PDFs without requiring document directory containment")
    void shouldResolveAllowedTempRendererPdf_withoutDocumentDirectoryContainment() throws Exception {
        Path tempRoot = Files.createTempDirectory("fax-renderer-temp-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        resetAllowedTempDirectoriesCache();
        try {
            Path tempPdf = Files.createTempFile(tempRoot, "eform-browser-render-", ".pdf");

            Path resolved = manager.resolveAndValidateFilePath(tempPdf.toString());

            assertThat(resolved).isEqualTo(tempPdf.toRealPath());
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            Files.walk(tempRoot)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should reject file paths outside the document directory and approved temp roots")
    void shouldRejectFilePaths_outsideDocumentDirectoryAndApprovedTempRoots() throws Exception {
        Path outsideFile = Files.createTempFile("fax-invalid-root-", ".pdf");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", "/tmp/another-temp-root-for-validation");
        resetAllowedTempDirectoriesCache();
        try {
            assertThatThrownBy(() -> manager.resolveAndValidateFilePath(outsideFile.toString()))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            Files.deleteIfExists(outsideFile);
        }
    }

    private static void resetAllowedTempDirectoriesCache() throws Exception {
        Field allowedTempDirectories = io.github.carlos_emr.carlos.utility.PathValidationUtils.class
                .getDeclaredField("allowedTempDirectories");
        allowedTempDirectories.setAccessible(true);
        allowedTempDirectories.set(null, null);
    }
}
