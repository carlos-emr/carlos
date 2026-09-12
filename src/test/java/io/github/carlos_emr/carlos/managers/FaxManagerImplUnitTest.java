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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("FaxManagerImpl")
@Tag("unit")
@Tag("fast")
@Tag("manager")
class FaxManagerImplUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private NioFileManager nioFileManager;
    @Mock private FaxConfigDao faxConfigDao;
    @Mock private io.github.carlos_emr.carlos.commn.dao.FaxJobDao faxJobDao;
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
        injectDependency(manager, "faxJobDao", faxJobDao);
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,false", "1,false", "2,false", "1,true", "2,true"})
    @DisplayName("should delete prepared fax files only after a confirmed rollback")
    void shouldRetainPreparedFiles_untilRollbackIsKnown(int completion, boolean persistenceThrows,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path pdf = Files.writeString(directory.resolve("fax.pdf"), "fixture fax");
        FaxJob job = new FaxJob();
        job.setStatus(FaxJob.STATUS.WAITING);
        job.setFile_name("fax.pdf");
        doReturn(job).when(manager).createFaxJob(eq(loggedInInfo), anyMap());
        when(nioFileManager.getOscarDocument(Path.of("fax.pdf"))).thenReturn(pdf);
        if (persistenceThrows) doThrow(new IllegalStateException("injected persistence failure"))
                .when(manager).saveFaxJob(eq(loggedInInfo), anyList());
        else doReturn(List.of(job)).when(manager).saveFaxJob(eq(loggedInInfo), anyList());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try (MockedStatic<io.github.carlos_emr.carlos.utility.PathValidationUtils> paths =
                Mockito.mockStatic(io.github.carlos_emr.carlos.utility.PathValidationUtils.class)) {
            paths.when(() -> io.github.carlos_emr.carlos.utility.PathValidationUtils.isInApplicationTempDirectory(any(java.io.File.class)))
                    .thenReturn(true);
            Map<String, Object> params = Map.of("faxFilePath", pdf.toString(), "coverpage", "false");
            if (persistenceThrows) assertThatThrownBy(() -> manager.createAndSaveFaxJob(loggedInInfo, params))
                    .isInstanceOf(IllegalStateException.class);
            else assertThat(manager.createAndSaveFaxJob(loggedInInfo, params)).singleElement().isSameAs(job);
            assertThat(pdf).exists();
            var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertThat(callbacks).hasSize(1);
            callbacks.get(0).afterCompletion(completion);
            assertThat(Files.exists(pdf)).isEqualTo(completion != org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {SecurityInfoManager.READ, SecurityInfoManager.WRITE})
    @DisplayName("should reject resend before locking or saving when either fax permission is missing")
    void shouldRejectResend_whenPermissionIsMissing(String deniedPermission) {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", deniedPermission, null)).thenReturn(false);
        assertThatThrownBy(() -> manager.resendFax(loggedInInfo, "123", "+442079460100"))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(faxJobDao);
        verify(manager, never()).saveFaxJob(any(), any(FaxJob.class));
    }

    private FaxJob stubResendSource() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)).thenReturn(true);
        faxConfigDao.getActiveConfigByNumber("1234567890").setProviderType(FaxConfig.ProviderType.SRFAX);
        FaxJob source = new FaxJob();
        source.setId(123);
        source.setDirection(FaxJob.Direction.OUT);
        source.setStatus(FaxJob.STATUS.ERROR);
        source.setFax_line("1234567890");
        source.setDestination("+442079460100");
        source.setJobId(99L);
        when(faxJobDao.findForUpdate(123)).thenReturn(source);
        return source;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"+44 20 7946 0100", "011442079460100"})
    @DisplayName("should preserve international intent for unchanged and replacement resend destinations")
    void shouldPreserveInternationalDestination_whenResending(String replacement) {
        FaxJob source = stubResendSource();
        java.util.List<FaxJob> saves = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(call -> {
            FaxJob job = call.getArgument(1);
            if (job.getId() == null) job.setId(124);
            saves.add(job);
            return job;
        }).when(manager).saveFaxJob(eq(loggedInInfo), any(FaxJob.class));
        assertThat(manager.resendFax(loggedInInfo, "123", replacement)).isTrue();
        assertThat(saves).hasSize(2);
        assertThat(saves.get(0).getDestination()).isEqualTo("+442079460100");
        assertThat(saves.get(0).getJobId()).isNull();
        assertThat(source.getStatus()).isEqualTo(FaxJob.STATUS.RESENT);
        assertThat(manager.resendFax(loggedInInfo, "123", replacement)).isFalse();
        assertThat(saves).hasSize(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"short", "inactive", "inbound", "waiting", "resent"})
    @DisplayName("should not clone invalid, inactive, inbound, pending, or already resent fax jobs")
    void shouldRejectResend_beforeSaving(String scenario) {
        FaxJob source = stubResendSource();
        if ("inactive".equals(scenario)) when(faxConfigDao.getActiveConfigByNumber("1234567890")).thenReturn(null);
        if ("inbound".equals(scenario)) source.setDirection(FaxJob.Direction.IN);
        if ("waiting".equals(scenario)) source.setStatus(FaxJob.STATUS.WAITING);
        if ("resent".equals(scenario)) source.setStatus(FaxJob.STATUS.RESENT);
        assertThat(manager.resendFax(loggedInInfo, "123", "short".equals(scenario) ? "12345678" : null)).isFalse();
        verify(manager, never()).saveFaxJob(any(), any(FaxJob.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("should validate SRFax primary and copy destinations before reading or publishing the PDF")
    void shouldRejectUndialableSrfaxRecipient_beforeFileAccess(boolean copy) throws Exception {
        faxConfigDao.getActiveConfigByNumber("1234567890").setProviderType(FaxConfig.ProviderType.SRFAX);
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("faxFilePath", "/tmp/not-read.pdf");
        input.put("recipient", "Test Recipient");
        input.put("recipientFaxNumber", copy ? "4165550100" : "12345678");
        input.put("senderFaxNumber", "1234567890");
        input.put("demographicNo", 17);
        if (copy) input.put("copyToRecipients", new String[]{"\"name\":\"Copy\",\"fax\":\"12345678\""});
        FaxJob result = manager.createFaxJob(loggedInInfo, input);
        assertThat(result.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        verifyNoInteractions(nioFileManager);
        verify(manager, never()).resolveAndValidateFilePath(any(String.class));
    }

    @Test
    @DisplayName("should deduplicate equivalent domestic and international SRFax recipients")
    void shouldDeduplicateEquivalentNumbers_whenAddingSrfaxRecipients() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.SRFAX);
        FaxJob primary = new FaxJob();
        primary.setDestination("14165550100");
        primary.setFaxAccount(new io.github.carlos_emr.carlos.fax.core.FaxAccount(config));
        List<FaxJob> copies = manager.addRecipients(loggedInInfo, primary, List.of(
                new io.github.carlos_emr.carlos.fax.core.FaxRecipient("Same", "416-555-0100"),
                new io.github.carlos_emr.carlos.fax.core.FaxRecipient("Same", "+1 4165550100"),
                new io.github.carlos_emr.carlos.fax.core.FaxRecipient("International", "+44 20 7946 0100"),
                new io.github.carlos_emr.carlos.fax.core.FaxRecipient("Same international", "011442079460100")));
        assertThat(copies).hasSize(1);
        assertThat(copies.get(0).getDestination()).isEqualTo("+442079460100");
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
            Path copiedPdf = Path.of("/var/lib/CarlosDocument/carlos/document", tempPdf.getFileName().toString());
            when(nioFileManager.promoteApplicationTempFile(canonicalTempPdf)).thenReturn(copiedPdf);

            FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                    "faxFilePath", tempPdf.toString(),
                    "recipient", "Test Recipient",
                    "recipientFaxNumber", "123-456-7890",
                    "senderFaxNumber", "1234567890",
                    "demographicNo", 17));

            // Validation runs on the original temp path before the canonical source is promoted.
            verify(manager).resolveAndValidateFilePath(tempPdf.toString());
            verify(nioFileManager).promoteApplicationTempFile(canonicalTempPdf);
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
            // rejected even though it is inside an allowed temp root.
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
            when(nioFileManager.promoteApplicationTempFile(any(Path.class)))
                    .thenThrow(new FilePromotionException("test failure"));

            FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                    "faxFilePath", tempPdf.toString(),
                    "recipient", "Test Recipient",
                    "recipientFaxNumber", "123-456-7890",
                    "senderFaxNumber", "1234567890",
                    "demographicNo", 17));

            // The ERROR job must be display-ready: CoverPage.jsp renders recipient/destination/
            // statusString per job, and the pre-fix bare shell rendered as an empty row. The
            // message must describe a STORAGE failure — the source file provably existed.
            assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
            assertThat(faxJob.getStatusString()).contains("could not be stored");
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
    @DisplayName("should fail before file promotion when the fax account is missing")
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

            // Promotion must not run when the account is invalid.
            assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
            assertThat(faxJob.getRecipient()).isEqualTo("Test Recipient");
            verify(nioFileManager, never()).promoteApplicationTempFile(any(Path.class));
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
    @DisplayName("should fail before file promotion when the file path is invalid")
    void shouldLeaveTempSourceIntact_whenFilePathInvalid() throws Exception {
        FaxJob faxJob = manager.createFaxJob(loggedInInfo, Map.of(
                "faxFilePath", "/nonexistent/nowhere/missing.pdf",
                "recipient", "Test Recipient",
                "recipientFaxNumber", "123-456-7890",
                "senderFaxNumber", "1234567890",
                "demographicNo", 17));

        assertThat(faxJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(faxJob.getStatusString()).contains("File missing");
        assertThat(faxJob.getRecipient()).isEqualTo("Test Recipient");
        verify(nioFileManager, never()).promoteApplicationTempFile(any(Path.class));
    }

    @Test
    @DisplayName("should fail fast before any file promotion when a copy-to recipient entry is unparseable")
    void shouldFailFast_beforePromotionWhenCopyToRecipientUnparseable() {
        try (LogCapture capture = LogCapture.forLogger(FaxManagerImpl.class)) {
            assertThatThrownBy(() -> manager.createAndSaveFaxJob(loggedInInfo, Map.of(
                    "coverpage", "false",
                    "copyToRecipients", new String[] {"\"name\":\"SensitiveFixturePatient\",\"fax\":\"5550000000\", NOT-JSON"})))
                    .isInstanceOf(FaxPreparationException.class)
                    .hasMessageContaining("Failed to parse 1 recipient(s)")
                    .hasMessageNotContaining("SensitiveFixturePatient")
                    .hasMessageNotContaining("5550000000")
                    .hasMessageNotContaining("NOT-JSON");
            assertThat(capture.messages()).anyMatch(message -> message.contains("recipient parsing failed"));
            assertThat(capture.messages().toString()).doesNotContain("SensitiveFixturePatient", "5550000000", "NOT-JSON");
            assertThat(capture.events()).allMatch(event -> event.getThrown() == null);
        }

        // Recipient parsing must precede createFaxJob: createFaxJob's temp->document promotion
        // deletes the preview source, so a recipient-shape failure after it destroys the user's
        // only copy and strands an orphan PDF in the document store.
        verify(manager, never()).createFaxJob(any(LoggedInInfo.class), anyMap());
        verifyNoInteractions(nioFileManager);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "NOT-JSON"})
    @DisplayName("should return a display-ready error when copy-recipient validation fails")
    void shouldReturnError_withoutFilesWhenCopyRecipientIsInvalid(String entry) {
        FaxJob job = manager.createFaxJob(loggedInInfo, Map.of("recipient", "Fixture",
                "recipientFaxNumber", "4165551234", "senderFaxNumber", "1234567890",
                "demographicNo", 17, "copyToRecipients", new String[] {entry}));
        assertThat(job.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(job.getStatusString()).contains("invalid").doesNotContain("NOT-JSON");
        assertThat(job.getId()).isNull();
        verifyNoInteractions(nioFileManager, faxJobDao);
    }

    @Test
    @DisplayName("should fail fast when a parseable copy-to recipient lacks a usable fax number")
    void shouldFailFast_whenCopyToRecipientLacksFaxNumber() {
        // The null-safe FaxRecipient constructor must not quietly void the fail-fast contract:
        // an entry that parses but carries no fax number would otherwise become a WAITING job
        // with a null destination (or a post-promotion persist failure).
        assertThatThrownBy(() -> manager.createAndSaveFaxJob(loggedInInfo, Map.of(
                "coverpage", "false",
                "copyToRecipients", new String[] {"\"name\":\"No Fax\""})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse");

        verify(manager, never()).createFaxJob(any(LoggedInInfo.class), anyMap());
        verifyNoInteractions(nioFileManager);
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
        // Entry is brace-less: production wraps each entry in braces before parsing, and the
        // hoisted parse now runs even when the primary job errors, so a double-braced fixture
        // would fail parsing before the behavior under test.
        List<FaxJob> result = manager.createAndSaveFaxJob(loggedInInfo, Map.of(
                "coverpage", "true",
                "copyToRecipients", new String[]{"\"name\":\"Copy To\",\"fax\":\"1112223333\""}));

        // Identity assertions: FaxJob.equals delegates to AbstractModel.getId(), which NPEs for
        // un-persisted (id-less) jobs, so collection equality cannot be used here.
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(errorJob);
        assertThat(errorJob.getId()).isNull();
        verify(manager, never()).addRecipients(any(), any(), anyList());
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
    @DisplayName("should reject the whole batch when any recipient job is invalid")
    void shouldRejectWholeBatch_whenBatchHasMixedWaitingAndErrorJobs() throws Exception {
        // The primary job is a normal WAITING job with a real file to cover.
        FaxJob waitingJob = new FaxJob();
        waitingJob.setStatus(FaxJob.STATUS.WAITING);
        waitingJob.setFile_name("queued-fax.pdf");
        waitingJob.setRecipient("Primary Recipient");
        waitingJob.setNumPages(1);
        doReturn(waitingJob).when(manager).createFaxJob(eq(loggedInInfo), any());

        // A copy-to duplicate that failed recipient validation: ERROR status, file_name left null.
        // The cover-page loop's `if (STATUS.ERROR...) continue;` guard must skip this job rather
        // than NPE on Paths.get(null), and it must never reach saveFaxJob.
        FaxJob copyErrorJob = new FaxJob();
        copyErrorJob.setStatus(FaxJob.STATUS.ERROR);
        copyErrorJob.setStatusString("Invalid fax number for copy-to recipient.");
        copyErrorJob.setRecipient("Copy Recipient");
        // Brace-less entry (production adds the braces); the stub targets the List overload
        // because createAndSaveFaxJob now parses entries itself and passes FaxRecipient objects.
        String[] copyToRecipients = new String[]{"\"name\":\"Copy Recipient\",\"fax\":\"9998887777\""};
        doReturn(List.of(copyErrorJob)).when(manager)
                .addRecipients(eq(loggedInInfo), eq(waitingJob), anyList());

        Path coveredDocument = Paths.get("Cover_test-uuid_queued-fax.pdf");
        doReturn(coveredDocument).when(manager)
                .addCoverPage(eq(loggedInInfo), any(), any(), any(), eq(Paths.get("queued-fax.pdf")));
        List<FaxJob> result = manager.createAndSaveFaxJob(loggedInInfo, Map.of(
                "coverpage", "true",
                "comments", "See attached",
                "copyToRecipients", copyToRecipients));

        assertThat(result).hasSize(2);
        assertThat(waitingJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(waitingJob.getStatusString()).contains("batch");
        assertThat(waitingJob.getFile_name()).isEqualTo(coveredDocument.getFileName().toString());
        assertThat(waitingJob.getNumPages()).isEqualTo(2);
        assertThat(copyErrorJob.getFile_name()).isNull();
        assertThat(copyErrorJob.getId()).isNull();
        verify(manager).addCoverPage(eq(loggedInInfo), any(), any(), any(), any(Path.class));
        verify(manager, never()).saveFaxJob(eq(loggedInInfo), anyList());
    }

    @Test
    @DisplayName("should be annotated @Transactional so a mid-batch persist failure rolls back prior recipients")
    void shouldBeTransactional_soMidBatchPersistFailureRollsBackPriorRecipients() throws Exception {
        // A pure unit test cannot exercise real Spring-proxy rollback without a container/DB —
        // FaxManagerImplTransactionIntegrationTest covers the end-to-end mid-batch rollback
        // against H2. This pins the annotation itself so the atomicity contract cannot silently
        // regress: without @Transactional on this exact method, the Spring AOP proxy created by
        // <tx:annotation-driven proxy-target-class="true"/> would not wrap the per-recipient
        // faxJobDao.persist calls in a single transaction at all.
        Method createAndSaveFaxJob = FaxManagerImpl.class.getMethod(
                "createAndSaveFaxJob", LoggedInInfo.class, Map.class);

        assertThat(createAndSaveFaxJob.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("should persist and audit one pre-built fax job inside a transaction")
    void shouldPersistAndAuditPrebuiltFaxJob_insideTransaction() throws Exception {
        Method method = FaxManagerImpl.class.getMethod("persistAndLogFaxJob",
                LoggedInInfo.class, FaxJob.class, FaxManager.TransactionType.class, int.class);
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();

        FaxJobDao faxJobDao = mock(FaxJobDao.class);
        injectDependency(manager, "faxJobDao", faxJobDao);
        FaxJob faxJob = new FaxJob();
        Mockito.doNothing().when(manager)
                .logFaxJob(loggedInInfo, faxJob, FaxManager.TransactionType.RX, 123);

        manager.persistAndLogFaxJob(loggedInInfo, faxJob, FaxManager.TransactionType.RX, 123);

        org.mockito.InOrder ordered = Mockito.inOrder(faxJobDao, manager);
        ordered.verify(faxJobDao).persist(faxJob);
        ordered.verify(manager).logFaxJob(loggedInInfo, faxJob, FaxManager.TransactionType.RX, 123);
    }

    @Test
    @DisplayName("should reject pre-built consultation fax batches without fax write privilege")
    void shouldRejectPrebuiltConsultationFaxBatch_withoutFaxWritePrivilege() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(false);
        FaxJobDao faxJobDao = mock(FaxJobDao.class);
        injectDependency(manager, "faxJobDao", faxJobDao);

        assertThatThrownBy(() -> manager.persistAndLogConsultationFaxJobs(loggedInInfo, List.of(new FaxJob()), 123))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_fax)");

        verifyNoInteractions(faxJobDao);
    }

    @Test
    @DisplayName("should reject a pre-built fax job before persistence or audit when fax write is denied")
    void shouldRejectPrebuiltFaxJob_whenFaxWriteIsDenied() throws Exception {
        FaxJobDao faxJobDao = mock(FaxJobDao.class);
        injectDependency(manager, "faxJobDao", faxJobDao);
        FaxJob faxJob = new FaxJob();
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, (String) null))
                .thenReturn(false);
        // Model a read-only role: if the implementation ever substitutes READ
        // for WRITE, it must not accidentally satisfy this denial regression.
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, (String) null))
                .thenReturn(true);

        assertThatThrownBy(() -> manager.persistAndLogFaxJob(
                loggedInInfo, faxJob, FaxManager.TransactionType.RX, 123))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_fax)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, (String) null);
        verifyNoInteractions(faxJobDao, loggedInInfo);
        verify(manager, never()).logFaxJob(loggedInInfo, faxJob, FaxManager.TransactionType.RX, 123);
    }

    @Test
    @DisplayName("should not persist or audit a pre-built fax job when its authorization check fails")
    void shouldNotPersistPrebuiltFaxJob_whenAuthorizationCheckFails() throws Exception {
        FaxJobDao faxJobDao = mock(FaxJobDao.class);
        injectDependency(manager, "faxJobDao", faxJobDao);
        FaxJob faxJob = new FaxJob();
        IllegalStateException authorizationFailure = new IllegalStateException("role lookup unavailable");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, (String) null))
                .thenThrow(authorizationFailure);

        assertThatThrownBy(() -> manager.persistAndLogFaxJob(
                loggedInInfo, faxJob, FaxManager.TransactionType.RX, 123))
                .isSameAs(authorizationFailure);

        verifyNoInteractions(faxJobDao, loggedInInfo);
        verify(manager, never()).logFaxJob(loggedInInfo, faxJob, FaxManager.TransactionType.RX, 123);
    }

    @Test
    @DisplayName("should delete the orphaned Cover_* file when PDF concatenation fails")
    void shouldDeleteOrphanCoverFile_whenConcatFails() throws Exception {
        FaxDocumentManager faxDocumentManager = mock(FaxDocumentManager.class);
        injectDependency(manager, "faxDocumentManager", faxDocumentManager);
        when(faxDocumentManager.createCoverPage(eq(loggedInInfo), any(), eq(1))).thenReturn("cover-page-bytes".getBytes());

        // A real temp directory stands in for the permanent document store: the private
        // addCoverPage(byte[], Path) creates its Cover_* file as a sibling of currentDocument, so
        // nioFileManager.getOscarDocument is stubbed to resolve into this fixture directory rather
        // than the real DOCUMENT_DIR.
        Path docStoreDir = Files.createTempDirectory("fax-doc-store-");
        try {
            Path currentDocument = docStoreDir.resolve("queued-fax.pdf");
            Files.write(currentDocument, "not a real pdf, but readable bytes".getBytes());
            when(nioFileManager.getOscarDocument(any(Path.class))).thenReturn(currentDocument);

            try (MockedStatic<ConcatPDF> concatPdfMock = Mockito.mockStatic(ConcatPDF.class)) {
                // Force the merge step itself to fail. ConcatPDF.concat(List, OutputStream) does not
                // declare a checked exception - it catches PDFMergerUtility's IOException internally
                // and wraps it as a RuntimeException (see ConcatPDF source) - so a RuntimeException is
                // the faithful stand-in here. This exercises the catch block under test, not PDFBox's
                // own corrupt-input skip-and-continue behavior (merely-corrupt bytes are silently
                // skipped by ConcatPDF and never throw at all).
                concatPdfMock.when(() -> ConcatPDF.concat(anyList(), any(OutputStream.class)))
                        .thenThrow(new RuntimeException("simulated PDF merge failure"));

                assertThatThrownBy(() -> manager.addCoverPage(loggedInInfo, "See attached", currentDocument))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessage("simulated PDF merge failure");
            }

            try (Stream<Path> entries = Files.list(docStoreDir)) {
                assertThat(entries.filter(p -> p.getFileName().toString().startsWith("Cover_")))
                        .isEmpty();
            }
        } finally {
            try (Stream<Path> paths = Files.walk(docStoreDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
        }
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
    void shouldFlushCacheOnly_forDocumentDirectorySource() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        // A DOCUMENT_DIR path (the fax cancel flow passes these) is not a CARLOS temp artifact:
        // pre-fix, deleteTempFile raised a SecurityException out of flush and broke fax-cancel.
        String documentPath = "/var/lib/CarlosDocument/carlos/document/some-fax.pdf";
        when(nioFileManager.removeCacheVersions(loggedInInfo, "/var/lib/CarlosDocument/carlos/document", "some-fax.pdf")).thenReturn(1);

        boolean flushed = manager.flush(loggedInInfo, documentPath);

        assertThat(flushed).isTrue();
        verify(nioFileManager).removeCacheVersions(loggedInInfo, "/var/lib/CarlosDocument/carlos/document", "some-fax.pdf");
        verify(nioFileManager, never()).deleteTempFile(any(String.class));
    }

    @Test
    @DisplayName("should report flush failure when the preview source cannot be keyed to an allowed preview source")
    void shouldReturnFalse_whenPreviewSourceCannotBeKeyed() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        String documentPath = "/var/lib/CarlosDocument/carlos/document/some-fax.pdf";
        // removeCacheVersions now rejects an unkeyable/disallowed preview source with an
        // IllegalArgumentException instead of a misleading 0. A PHI flush that cannot even key its
        // source must be reported as an uncleared cache, never success.
        when(nioFileManager.removeCacheVersions(loggedInInfo, "/var/lib/CarlosDocument/carlos/document", "some-fax.pdf"))
                .thenThrow(new IllegalArgumentException("source directory is not an allowed preview source"));

        boolean flushed = manager.flush(loggedInInfo, documentPath);

        assertThat(flushed).as("an unkeyable preview source must not report a successful flush").isFalse();
        verify(nioFileManager).removeCacheVersions(loggedInInfo, "/var/lib/CarlosDocument/carlos/document", "some-fax.pdf");
    }

    @Test
    @DisplayName("should log at DEBUG (not WARN) when skipping a non-temp path outside the boundary")
    void shouldLogDebug_whenSkippingNonTempPathOutsideBoundary() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        String documentPath = "/var/lib/CarlosDocument/carlos/document/some-fax.pdf";
        when(nioFileManager.removeCacheVersions(loggedInInfo, "/var/lib/CarlosDocument/carlos/document", "some-fax.pdf")).thenReturn(1);

        try (LogCapture logCapture = LogCapture.forLogger(FaxManagerImpl.class)) {
            boolean flushed = manager.flush(loggedInInfo, documentPath);

            assertThat(flushed).isTrue();
            assertThat(logCapture.events())
                    .noneMatch(event -> event.getLevel() == Level.WARN)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                        assertThat(event.getMessage().getFormattedMessage())
                                .contains("Fax flush skipped non-temp path");
                    });
        }
    }

    @Test
    @DisplayName("should still report success for a real, existing file that is legitimately outside the temp boundary")
    void shouldReturnTrue_whenExistingFileIsLegitimatelyOutsideTempBoundary() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        // A DOCUMENT_DIR-style fax source is a real, persisted patient document — it routinely
        // EXISTS on disk. This pins that the fix's distinguishing check does not regress that common
        // case: `File.exists()` alone cannot tell "legitimately outside the boundary" apart from
        // "could not canonicalize", because an existing, boundary-rejected document and a still-
        // existing, unverifiable temp artifact both satisfy `exists() == true`. The fix instead
        // re-resolves the canonical path independently to see whether canonicalization itself
        // succeeded (see FaxManagerImpl.flush) — for a plain existing file with no symlink cycle,
        // it does, so this must still report success.
        Path outsideRoot = Files.createTempDirectory("fax-flush-outside-boundary-");
        try {
            Path existingDocument = outsideRoot.resolve("some-fax.pdf");
            Files.write(existingDocument, "not a real pdf, but readable bytes".getBytes());
            when(nioFileManager.removeCacheVersions(loggedInInfo, outsideRoot.toString(), "some-fax.pdf")).thenReturn(1);

            boolean flushed = manager.flush(loggedInInfo, existingDocument.toString());

            assertThat(flushed).isTrue();
            verify(nioFileManager, never()).deleteTempFile(any(String.class));
        } finally {
            try (Stream<Path> paths = Files.walk(outsideRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    @DisplayName("should report flush failure and warn when a path cannot be canonicalized to verify it as a temp artifact")
    void shouldReturnFalseAndWarn_whenPathCannotBeCanonicalized() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        Path tempRoot = Files.createTempDirectory("fax-flush-canon-fail-root-");
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempRoot.toString());
        try {
            resetAllowedTempDirectoriesCache();
            Path rendererDir = Files.createDirectories(tempRoot.resolve("carlos-eform-browser-pdf-temp"));
            // A two-node symlink cycle makes File.getCanonicalPath() throw
            // "IOException: Too many levels of symbolic links" (ELOOP) — a real, deterministic,
            // portable-on-Linux way to force the exact canonicalization failure
            // PathValidationUtils.validateApplicationTempPath() wraps as
            // SecurityException("Cannot resolve temp path"), without mocking the static utility.
            // Note this is ALSO why File.exists() cannot be the distinguishing signal: a symlink
            // loop makes File.exists() return false too (both operations resolve the same broken
            // chain), so an existence check alone would silently miscategorize this exact case as
            // "outside boundary" instead of "unverifiable temp artifact".
            Path loopA = rendererDir.resolve("loopA");
            Path loopB = rendererDir.resolve("loopB");
            Files.createSymbolicLink(loopA, loopB);
            Files.createSymbolicLink(loopB, loopA);
            String unresolvablePath = loopA.resolve("child.pdf").toString();

            try (LogCapture logCapture = LogCapture.forLogger(FaxManagerImpl.class)) {
                boolean flushed = manager.flush(loggedInInfo, unresolvablePath);

                // A path we could not verify as (non-)temp must NOT report success: reporting
                // success here would leave an unverified, possibly PHI-bearing artifact on disk
                // while telling the caller the flush succeeded.
                assertThat(flushed).isFalse();
                assertThat(logCapture.events())
                        .anySatisfy(event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.WARN);
                            assertThat(event.getMessage().getFormattedMessage())
                                    .contains("could not canonicalize a path to verify it as a temp artifact");
                        });
            }
            verify(nioFileManager, never()).deleteTempFile(any(String.class));
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
            resetAllowedTempDirectoriesCache();
            // Files.walk does not follow symlinks by default, so the loop is visited as two leaf
            // entries (not traversed into) and deleting each path object removes the link itself.
            try (Stream<Path> paths = Files.walk(tempRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(tempRoot);
        }
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
    @DisplayName("should report flush failure when cached preview pages cannot be removed")
    void shouldReturnFalse_whenCachedPreviewPagesCannotBeRemoved() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        // Cached preview pages are rendered images of the fax document (PHI): a removal failure
        // must fail the flush, not report success with the images still on disk.
        when(nioFileManager.removeCacheVersions(eq(loggedInInfo), any(String.class), any(String.class)))
                .thenThrow(new IOException("2 preview cache page image(s) could not be removed"));

        boolean flushed = manager.flush(loggedInInfo, "/var/lib/CarlosDocument/carlos/document/some-fax.pdf");

        assertThat(flushed).isFalse();
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
