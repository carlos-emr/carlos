package io.github.carlos_emr.carlos.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

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
        Path tempRoot = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "fax-renderer-temp-root-");
        try {
            Path tempPdf = Files.createTempFile(tempRoot, "eform-browser-render-", ".pdf");
            Path copiedPdf = Path.of("/var/lib/OscarDocument/oscar/document", tempPdf.getFileName().toString());
            doReturn(tempPdf).when(manager).resolveAndValidateFilePath(tempPdf.toString());
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
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should mark fax job error when temp promotion returns no destination")
    void shouldMarkFaxJobError_whenTempPromotionReturnsNoDestination() throws Exception {
        Path tempRoot = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "fax-renderer-temp-root-");
        try {
            Path tempPdf = Files.createTempFile(tempRoot, "eform-browser-render-", ".pdf");
            doReturn(tempPdf).when(manager).resolveAndValidateFilePath(tempPdf.toString());
            when(nioFileManager.copyFileToOscarDocuments(tempPdf.toString())).thenReturn(null);

            FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                    "faxFilePath", tempPdf.toString(),
                    "recipient", "Test Recipient",
                    "recipientFaxNumber", "123-456-7890",
                    "senderFaxNumber", "1234567890",
                    "demographicNo", 17));

            verify(nioFileManager).copyFileToOscarDocuments(tempPdf.toString());
            assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
            assertThat(faxJob.getStatusString()).isEqualTo("File missing on local storage or invalid file path.");
        } finally {
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should use fax preview cache generation for preview images")
    void shouldUseFaxPreviewCacheVersion_whenRenderingPreviewImage() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        Path sourcePdf = Files.createTempFile("fax-preview-source-", ".pdf");
        Path previewImage = Path.of("/tmp/fax-preview-source_2.png");
        try {
            when(nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, sourcePdf.getParent().toString(),
                    sourcePdf.getFileName().toString(), 2)).thenReturn(previewImage);

            Path result = manager.getFaxPreviewImage(loggedInInfo, sourcePdf, 2);

            assertThat(result).isEqualTo(previewImage);
            verify(nioFileManager).createFaxPreviewCacheVersion(loggedInInfo, sourcePdf.getParent().toString(),
                    sourcePdf.getFileName().toString(), 2);
        } finally {
            Files.deleteIfExists(sourcePdf);
        }
    }

    @Test
    @DisplayName("should return null when preview image path is blank")
    void shouldReturnNull_whenPreviewImagePathIsBlank() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

        Path result = manager.getFaxPreviewImage(loggedInInfo, Path.of(" "), 1);

        assertThat(result).isNull();
    }

}
