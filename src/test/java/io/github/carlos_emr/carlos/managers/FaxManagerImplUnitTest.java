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

package io.github.carlos_emr.carlos.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        manager = spy(new FaxManagerImpl());
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
        // Wrap so mocks.close() always runs even if the static-mock close throws; otherwise an
        // un-closed MockedStatic<EDocUtil> would leak its registration into later tests.
        try {
            if (eDocUtilMock != null) eDocUtilMock.close();
        } finally {
            if (mocks != null) mocks.close();
        }
    }

    @Test
    @DisplayName("should copy allowed temp renderer PDFs into Oscar documents before queuing")
    void shouldCopyAllowedTempRendererPdfIntoOscarDocuments_beforeQueuingFaxJob() throws Exception {
        Path tempRoot = Files.createTempDirectory("fax-renderer-temp-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            // The eForm browser renderer writes under a CARLOS-owned temp subtree; place the fixture
            // there so it satisfies the application-owned temp boundary the fax flow now enforces.
            Path rendererDir = Files.createDirectories(tempRoot.resolve("carlos-eform-browser-pdf-temp"));
            Path tempPdf = Files.createTempFile(rendererDir, "eform-browser-render-", ".pdf");
            Path canonicalTempPdf = tempPdf.toRealPath();
            Path copiedPdf = Path.of("/var/lib/OscarDocument/oscar/document", tempPdf.getFileName().toString());
            when(nioFileManager.copyFileToOscarDocuments(canonicalTempPdf.toString())).thenReturn(copiedPdf.toString());

            FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                    "faxFilePath", tempPdf.toString(),
                    "recipient", "Test Recipient",
                    "recipientFaxNumber", "123-456-7890",
                    "senderFaxNumber", "1234567890",
                    "demographicNo", 17));

            // Validation runs on the ORIGINAL temp path first; only the validated canonical path is
            // promoted, and only after every validation has passed (destructive step last).
            verify(manager).resolveAndValidateFilePath(tempPdf.toString());
            verify(nioFileManager).copyFileToOscarDocuments(canonicalTempPdf.toString());
            assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.WAITING);
            assertThat(faxJob.getFile_name()).isEqualTo(tempPdf.getFileName().toString());
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }


    @Test
    @DisplayName("should resolve allowed temp renderer PDFs without requiring document directory containment")
    void shouldResolveAllowedTempRendererPdf_withoutDocumentDirectoryContainment() throws Exception {
        Path tempRoot = Files.createTempDirectory("fax-renderer-temp-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            Path rendererDir = Files.createDirectories(tempRoot.resolve("carlos-eform-browser-pdf-temp"));
            Path tempPdf = Files.createTempFile(rendererDir, "eform-browser-render-", ".pdf");

            Path resolved = manager.resolveAndValidateFilePath(tempPdf.toString());

            assertThat(resolved).isEqualTo(tempPdf.toRealPath());
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should reject file paths outside the document directory and approved temp roots")
    void shouldRejectFilePaths_outsideDocumentDirectoryAndApprovedTempRoots() throws Exception {
        Path outsideFile = Files.createTempFile("fax-invalid-root-", ".pdf");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", "/tmp/another-temp-root-for-validation");
        try {
            resetAllowedTempDirectoriesCache();
            String outsidePath = outsideFile.toString();
            assertThatThrownBy(() -> manager.resolveAndValidateFilePath(outsidePath))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            Files.deleteIfExists(outsideFile);
        }
    }

    @Test
    @DisplayName("should reject a temp file inside the shared temp root that is not CARLOS-owned")
    void shouldRejectFilePath_whenInSharedTempButNotApplicationOwned() throws Exception {
        Path tempRoot = Files.createTempDirectory("fax-shared-temp-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            // A file directly under the shared temp root (not under a carlos-owned subtree) must be
            // rejected even though it is inside an allowed temp root (cubic SCQPk).
            Path foreignPdf = Files.createTempFile(tempRoot, "foreign-", ".pdf");

            assertThatThrownBy(() -> manager.resolveAndValidateFilePath(foreignPdf.toString()))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should return a display-ready ERROR job and keep the temp source when promotion fails")
    void shouldReturnPopulatedErrorJob_whenPromotionFails() throws Exception {
        Path tempRoot = Files.createTempDirectory("fax-renderer-temp-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            Path rendererDir = Files.createDirectories(tempRoot.resolve("carlos-eform-browser-pdf-temp"));
            Path tempPdf = Files.createTempFile(rendererDir, "eform-browser-render-", ".pdf");
            when(nioFileManager.copyFileToOscarDocuments(any(String.class))).thenReturn(null);

            FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                    "faxFilePath", tempPdf.toString(),
                    "recipient", "Test Recipient",
                    "recipientFaxNumber", "123-456-7890",
                    "senderFaxNumber", "1234567890",
                    "demographicNo", 17));

            // The ERROR job must be display-ready: CoverPage.jsp renders recipient/destination/
            // statusString per job, and the pre-fix bare shell rendered as an empty row.
            assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
            assertThat(faxJob.getStatusString()).contains("File missing");
            assertThat(faxJob.getRecipient()).isEqualTo("Test Recipient");
            assertThat(faxJob.getDestination()).isEqualTo("1234567890");
            assertThat(Files.exists(tempPdf)).isTrue();
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should fail before the destructive promotion when the fax account is missing")
    void shouldLeaveTempSourceIntact_whenFaxAccountMissing() throws Exception {
        Path tempRoot = Files.createTempDirectory("fax-renderer-temp-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            Path rendererDir = Files.createDirectories(tempRoot.resolve("carlos-eform-browser-pdf-temp"));
            Path tempPdf = Files.createTempFile(rendererDir, "eform-browser-render-", ".pdf");
            when(faxConfigDao.getActiveConfigByNumber("0000000000")).thenReturn(null);

            FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                    "faxFilePath", tempPdf.toString(),
                    "recipient", "Test Recipient",
                    "recipientFaxNumber", "123-456-7890",
                    "senderFaxNumber", "0000000000",
                    "demographicNo", 17));

            // Promotion deletes the temp source on success, so it must never have run: a retry
            // after the operator fixes the fax account still has its preview document.
            assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
            assertThat(faxJob.getRecipient()).isEqualTo("Test Recipient");
            verify(nioFileManager, never()).copyFileToOscarDocuments(any(String.class));
            assertThat(Files.exists(tempPdf)).isTrue();
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should fail before the destructive promotion when the file path is invalid")
    void shouldLeaveTempSourceIntact_whenFilePathInvalid() {
        FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                "faxFilePath", "/nonexistent/nowhere/missing.pdf",
                "recipient", "Test Recipient",
                "recipientFaxNumber", "123-456-7890",
                "senderFaxNumber", "1234567890",
                "demographicNo", 17));

        assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(faxJob.getStatusString()).contains("File missing");
        assertThat(faxJob.getRecipient()).isEqualTo("Test Recipient");
        verify(nioFileManager, never()).copyFileToOscarDocuments(any(String.class));
    }

    @Test
    @DisplayName("should return the un-persisted ERROR job instead of NPEing when a cover page was requested")
    void shouldReturnUnsavedErrorJob_whenPrimaryJobFailsValidation() {
        FaxJob errorJob = new FaxJob();
        errorJob.setStatus(FaxJob.STATUS.ERROR);
        errorJob.setStatusString("File missing on local storage or invalid file path.");
        errorJob.setRecipient("Test Recipient");
        doReturn(errorJob).when(manager).createFaxJob(eq(loggedInInfo), any());

        // Pre-fix, coverpage=true ran Paths.get(errorJob.getFile_name()) -> NPE, and without a
        // cover page the all-ERROR filter threw an unmapped RuntimeException. Both paths must now
        // surface the job so the preview can render its per-job status.
        List<FaxJob> result = manager.createAndSaveFaxJob(loggedInInfo, Map.of(
                "coverpage", "true",
                "copyToRecipients", new String[]{"{\"name\":\"Copy To\",\"fax\":\"1112223333\"}"}));

        // Identity assertions: FaxJob.equals delegates to AbstractModel.getId(), which NPEs for
        // un-persisted (id-less) jobs, so collection equality cannot be used here.
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(errorJob);
        assertThat(errorJob.getId()).isNull();
        verify(manager, never()).addRecipients(any(), any(), any(String[].class));
        verify(manager, never()).saveFaxJob(eq(loggedInInfo), anyList());
    }

    @Test
    @DisplayName("should return the cover-page-failure job un-persisted instead of dropping or throwing")
    void shouldReturnCoverPageFailureJob_withoutThrowing() throws Exception {
        FaxJob waitingJob = new FaxJob();
        waitingJob.setStatus(FaxJob.STATUS.WAITING);
        waitingJob.setFile_name("queued-fax.pdf");
        waitingJob.setRecipient("Test Recipient");
        doReturn(waitingJob).when(manager).createFaxJob(eq(loggedInInfo), any());
        doThrow(new IOException("disk full")).when(manager)
                .addCoverPage(eq(loggedInInfo), any(), any(), any(), any(Path.class));

        List<FaxJob> result = manager.createAndSaveFaxJob(loggedInInfo, Map.of("coverpage", "true"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(waitingJob);
        assertThat(waitingJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(waitingJob.getStatusString()).contains("Cover page creation failed");
        verify(manager, never()).saveFaxJob(eq(loggedInInfo), anyList());
    }

    @Test
    @DisplayName("should clear the preview cache and delete an existing CARLOS temp artifact on flush")
    void shouldFlushCacheAndDeleteTempArtifact_whenPreviewSourceIsApplicationTemp() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        Path tempRoot = Files.createTempDirectory("fax-flush-temp-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            Path rendererDir = Files.createDirectories(tempRoot.resolve("carlos-eform-browser-pdf-temp"));
            Path tempPdf = Files.createTempFile(rendererDir, "eform-browser-render-", ".pdf");
            when(nioFileManager.removeCacheVersions(loggedInInfo, rendererDir.toString(), tempPdf.getFileName().toString())).thenReturn(2);
            // flush deletes the VALIDATED canonical path (check-vs-use closure), not the raw input.
            when(nioFileManager.deleteTempFile(tempPdf.toRealPath().toString())).thenReturn(true);

            boolean flushed = manager.flush(loggedInInfo, tempPdf.toString());

            assertThat(flushed).isTrue();
            verify(nioFileManager).removeCacheVersions(loggedInInfo, rendererDir.toString(), tempPdf.getFileName().toString());
            verify(nioFileManager).deleteTempFile(tempPdf.toRealPath().toString());
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should clear the cache without attempting temp deletion for a document-directory source")
    void shouldFlushCacheOnly_forDocumentDirectorySource() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        // A DOCUMENT_DIR path (the fax cancel flow passes these) is not a CARLOS temp artifact:
        // pre-fix, deleteTempFile raised a SecurityException out of flush and broke fax-cancel.
        String documentPath = "/var/lib/OscarDocument/carlos/document/some-fax.pdf";
        when(nioFileManager.removeCacheVersions(loggedInInfo, "/var/lib/OscarDocument/carlos/document", "some-fax.pdf")).thenReturn(1);

        boolean flushed = manager.flush(loggedInInfo, documentPath);

        assertThat(flushed).isTrue();
        verify(nioFileManager).removeCacheVersions(loggedInInfo, "/var/lib/OscarDocument/carlos/document", "some-fax.pdf");
        verify(nioFileManager, never()).deleteTempFile(any(String.class));
    }

    @Test
    @DisplayName("should treat an already-clean preview as flush success, not an error")
    void shouldReturnTrue_whenNothingLeftToClear() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        Path tempRoot = Files.createTempDirectory("fax-flush-clean-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            Path rendererDir = Files.createDirectories(tempRoot.resolve("carlos-eform-browser-pdf-temp"));
            // The preview artifact is already gone (never rendered, or flushed once before): the
            // fax-cancel flow must not show "Failed to clear fax preview cache" for that.
            Path missingPdf = rendererDir.resolve("already-flushed.pdf");

            boolean flushed = manager.flush(loggedInInfo, missingPdf.toString());

            assertThat(flushed).isTrue();
            verify(nioFileManager, never()).deleteTempFile(any(String.class));
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should report flush failure when an existing temp artifact cannot be deleted")
    void shouldReturnFalse_whenExistingTempArtifactCannotBeDeleted() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        Path tempRoot = Files.createTempDirectory("fax-flush-fail-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            Path rendererDir = Files.createDirectories(tempRoot.resolve("carlos-eform-browser-pdf-temp"));
            Path tempPdf = Files.createTempFile(rendererDir, "eform-browser-render-", ".pdf");
            when(nioFileManager.deleteTempFile(tempPdf.toString())).thenReturn(false);

            boolean flushed = manager.flush(loggedInInfo, tempPdf.toString());

            // A PHI-bearing preview PDF that exists but could not be removed is a real failure the
            // user (and operator, via the logs) must hear about.
            assertThat(flushed).isFalse();
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
    }

    @Test
    @DisplayName("should require the fax read security object for flush")
    void shouldRejectFlush_whenFaxReadPrivilegeMissing() {
        // setUp only grants _fax WRITE; the unstubbed READ check returns false.
        assertThatThrownBy(() -> manager.flush(loggedInInfo, "/tmp/carlos-temp/fax.pdf"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("missing required sec object (_fax)");
        verifyNoInteractions(nioFileManager);
    }

    private static void resetAllowedTempDirectoriesCache() throws Exception {
        clearStaticField("allowedTempDirectories");
        // The application-temp-root map is keyed on the same system properties (java.io.tmpdir /
        // catalina.*) and cached independently, so it must be cleared alongside the allowed-dirs
        // cache when a test rebinds java.io.tmpdir.
        clearStaticField("applicationTempRoots");
    }

    private static void clearStaticField(String fieldName) throws Exception {
        Field field = io.github.carlos_emr.carlos.utility.PathValidationUtils.class
                .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, null);
    }
}
