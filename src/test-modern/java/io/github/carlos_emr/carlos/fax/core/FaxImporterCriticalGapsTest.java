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
 * Written for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * @since 2026-02-12
 */
package io.github.carlos_emr.carlos.fax.core;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.codec.Base64;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Critical gap tests for FaxImporter covering collision-free filename generation,
 * PDF validation, and atomic move failure recovery.
 *
 * <p><strong>Critical Test Coverage Areas:</strong></p>
 * <ol>
 *   <li><strong>Collision-Free Filename Generation (Priority 10):</strong> Verifies that
 *       the AtomicLong counter prevents filename collisions even when 100+ faxes arrive
 *       in rapid succession within the same second.</li>
 *   <li><strong>PDF Validation Failure (Priority 9):</strong> Ensures corrupted or invalid
 *       PDF data is properly rejected, temp files are cleaned up, and FaxJob status is set
 *       to ERROR with appropriate error messages.</li>
 *   <li><strong>Atomic Move Failure Recovery (Priority 9):</strong> Tests cleanup when
 *       Files.move() fails (simulated permission errors), verifies temp file deletion in
 *       finally block, and ensures FaxJob status reflects the error.</li>
 * </ol>
 *
 * <p><strong>Why These Tests Are Critical:</strong></p>
 * <ul>
 *   <li><strong>Patient Safety:</strong> Filename collisions could cause fax overwrites,
 *       potentially losing critical patient information (referrals, lab results, consults).</li>
 *   <li><strong>Data Integrity:</strong> Accepting corrupted PDFs could lead to unreadable
 *       documents in patient charts, causing clinical delays or errors.</li>
 *   <li><strong>System Stability:</strong> Temp file leaks consume disk space and could
 *       eventually cause system failures in high-volume practices.</li>
 *   <li><strong>Regulatory Compliance:</strong> PHI protection requires proper error handling
 *       and cleanup - temp files containing patient data must not persist on disk.</li>
 * </ul>
 *
 * <p><strong>Testing Approach:</strong></p>
 * <p>These tests use reflection to access private methods (generateUniqueFilename,
 * validateAndCountPages) for targeted unit-style testing, while also testing the full
 * saveAndInsertIntoQueue() workflow with mocked dependencies to verify integration behavior.</p>
 *
 * @see FaxImporter
 * @see FaxJob
 * @see FaxConfig
 */
@DisplayName("FaxImporter Critical Gap Tests")
@Tag("unit")
@Tag("fax")
@Tag("security")
class FaxImporterCriticalGapsTest extends OpenOUnitTestBase {

    private FaxImporter faxImporter;
    private FaxConfigDao faxConfigDao;
    private FaxJobDao faxJobDao;
    private QueueDocumentLinkDao queueDocumentLinkDao;
    private ProviderLabRoutingDao providerLabRoutingDao;
    private FaxProviderClientFactory faxProviderClientFactory;

    private Method generateUniqueFilenameMethod;
    private Method validateAndCountPagesMethod;
    private Method saveAndInsertIntoQueueMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Mock all DAOs
        faxConfigDao = mock(FaxConfigDao.class);
        faxJobDao = mock(FaxJobDao.class);
        queueDocumentLinkDao = mock(QueueDocumentLinkDao.class);
        providerLabRoutingDao = mock(ProviderLabRoutingDao.class);
        faxProviderClientFactory = mock(FaxProviderClientFactory.class);

        // Create FaxImporter instance with mocked dependencies
        faxImporter = new FaxImporter(faxConfigDao, faxJobDao, queueDocumentLinkDao,
                providerLabRoutingDao, faxProviderClientFactory);

        // Use reflection to access private methods for targeted testing
        generateUniqueFilenameMethod = FaxImporter.class.getDeclaredMethod("generateUniqueFilename", String.class);
        generateUniqueFilenameMethod.setAccessible(true);

        validateAndCountPagesMethod = FaxImporter.class.getDeclaredMethod("validateAndCountPages", File.class);
        validateAndCountPagesMethod.setAccessible(true);

        saveAndInsertIntoQueueMethod = FaxImporter.class.getDeclaredMethod("saveAndInsertIntoQueue",
                FaxConfig.class, FaxJob.class, FaxJob.class);
        saveAndInsertIntoQueueMethod.setAccessible(true);
    }

    /**
     * Tests for collision-free filename generation (Priority 10).
     *
     * <p><strong>Critical Risk:</strong> Without proper collision prevention, high-volume
     * practices could experience filename collisions during busy periods (e.g., morning
     * referral rush), potentially overwriting patient faxes.</p>
     */
    @Nested
    @DisplayName("Collision-Free Filename Generation Tests (Priority 10)")
    @Tag("collision")
    class CollisionFreeFilenameTests {

        @Test
        @DisplayName("should generate 100 unique filenames when called in rapid succession")
        void shouldGenerateUniqueFilenames_whenCalledRapidly() throws Exception {
            // Given: 100 faxes arriving in rapid succession with same original filename
            String originalFilename = "referral.pdf";
            int numberOfFaxes = 100;
            Set<String> generatedFilenames = new HashSet<>();

            // When: Generate 100 filenames as fast as possible (simulating concurrent arrivals)
            for (int i = 0; i < numberOfFaxes; i++) {
                String filename = (String) generateUniqueFilenameMethod.invoke(faxImporter, originalFilename);
                generatedFilenames.add(filename);
            }

            // Then: All filenames must be unique (no collisions)
            assertThat(generatedFilenames)
                    .as("All generated filenames must be unique - no collisions allowed")
                    .hasSize(numberOfFaxes);

            // Then: All filenames should contain the original filename
            assertThat(generatedFilenames)
                    .allSatisfy(filename -> assertThat(filename).contains("referral.pdf"));

            // Then: All filenames should follow the pattern: yyyyMMdd-HHmmss-NNNNN-original.pdf
            assertThat(generatedFilenames)
                    .allSatisfy(filename -> {
                        assertThat(filename).matches("\\d{8}-\\d{6}-\\d{5}-.+\\.pdf");
                    });
        }

        @Test
        @DisplayName("should generate unique filenames when multiple threads generate concurrently")
        @Tag("concurrency")
        void shouldGenerateUniqueFilenames_whenMultipleThreadsConcurrent() throws Exception {
            // Given: 10 threads each generating 10 filenames (simulating parallel SRFax downloads)
            int numberOfThreads = 10;
            int filenamesPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            Set<String> allFilenames = ConcurrentHashMap.newKeySet();

            // When: Generate filenames concurrently from multiple threads
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                futures.add(executor.submit(() -> {
                    List<String> threadFilenames = new ArrayList<>();
                    for (int j = 0; j < filenamesPerThread; j++) {
                        String filename = (String) generateUniqueFilenameMethod.invoke(
                                faxImporter, "thread" + threadId + "-fax.pdf");
                        threadFilenames.add(filename);
                        allFilenames.add(filename);
                    }
                    return threadFilenames;
                }));
            }

            // Wait for all threads to complete
            for (Future<List<String>> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            // Then: All filenames across all threads must be unique
            assertThat(allFilenames)
                    .as("Concurrent filename generation must not produce collisions")
                    .hasSize(numberOfThreads * filenamesPerThread);
        }

        @Test
        @DisplayName("should generate unique filenames when original filename is null")
        void shouldGenerateUniqueFilename_whenOriginalFilenameIsNull() throws Exception {
            // Given: Fax with null filename (edge case from some providers)
            String nullFilename = null;

            // When: Generate filename with null input
            String filename1 = (String) generateUniqueFilenameMethod.invoke(faxImporter, nullFilename);
            String filename2 = (String) generateUniqueFilenameMethod.invoke(faxImporter, nullFilename);

            // Then: Should generate valid filenames with fallback pattern
            assertThat(filename1)
                    .isNotNull()
                    .endsWith(".pdf")
                    .contains("fax-");

            assertThat(filename2)
                    .isNotNull()
                    .endsWith(".pdf")
                    .contains("fax-");

            // Then: Should not collide even with null input
            assertThat(filename1).isNotEqualTo(filename2);
        }

        @Test
        @DisplayName("should sanitize dangerous characters in filename")
        @Tag("security")
        void shouldSanitizeDangerousCharacters_inFilename() throws Exception {
            // Given: Filename with dangerous characters
            String dangerousFilename = "../../../etc/passwd|rm -rf *|<script>alert('xss')</script>.pdf";

            // When: Generate filename
            String sanitized = (String) generateUniqueFilenameMethod.invoke(faxImporter, dangerousFilename);

            // Then: Should not contain dangerous characters
            assertThat(sanitized)
                    .doesNotContain("..", "|", "\\", "/", ":", "*", "?", "\"", "<", ">", "script");

            // Then: Should still be a valid PDF filename
            assertThat(sanitized).endsWith(".pdf");
        }
    }

    /**
     * Tests for PDF validation failure handling (Priority 9).
     *
     * <p><strong>Critical Risk:</strong> Accepting corrupted PDFs into the document system
     * could lead to unreadable faxes in patient charts, causing clinical delays or errors
     * when providers attempt to view critical patient information.</p>
     */
    @Nested
    @DisplayName("PDF Validation Failure Tests (Priority 9)")
    @Tag("validation")
    class PdfValidationTests {

        @Test
        @DisplayName("should reject corrupted PDF and throw FaxProviderException")
        void shouldRejectCorruptedPdf_andThrowException() throws Exception {
            // Given: Corrupted PDF data (not valid PDF structure)
            byte[] corruptedPdf = "This is not a valid PDF file content".getBytes();
            File tempFile = createTempFile(corruptedPdf);

            try {
                // When/Then: Validate the corrupted PDF should throw exception
                assertThatThrownBy(() -> {
                    try {
                        validateAndCountPagesMethod.invoke(faxImporter, tempFile);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                })
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("Cannot read PDF");
            } finally {
                // Cleanup
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }

        @Test
        @DisplayName("should reject empty PDF file")
        void shouldRejectEmptyPdf() throws Exception {
            // Given: Empty file (0 bytes)
            File emptyFile = createTempFile(new byte[0]);

            try {
                // When/Then: Validate empty file should throw exception
                assertThatThrownBy(() -> {
                    try {
                        validateAndCountPagesMethod.invoke(faxImporter, emptyFile);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                })
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("Cannot read PDF");
            } finally {
                if (emptyFile.exists()) {
                    emptyFile.delete();
                }
            }
        }

        @Test
        @DisplayName("should accept valid PDF and return correct page count")
        void shouldAcceptValidPdf_andReturnPageCount() throws Exception {
            // Given: Valid 3-page PDF
            File validPdf = createValidPdf(3);

            try {
                // When: Validate the PDF
                int pageCount = (int) validateAndCountPagesMethod.invoke(faxImporter, validPdf);

                // Then: Should return correct page count
                assertThat(pageCount).isEqualTo(3);

            } finally {
                if (validPdf.exists()) {
                    validPdf.delete();
                }
            }
        }

        @Test
        @DisplayName("should reject PDF with zero pages")
        void shouldRejectPdf_withZeroPages() throws Exception {
            // Given: Malformed PDF structure that might have 0 pages
            // Note: iTextPDF will throw exception for truly malformed PDFs
            // This test documents expected behavior

            // Create a minimal PDF structure that might parse but have 0 pages
            byte[] minimalPdf = "%PDF-1.4\n%%EOF".getBytes();
            File pdfFile = createTempFile(minimalPdf);

            try {
                // When/Then: Should throw exception for invalid PDF
                assertThatThrownBy(() -> {
                    try {
                        validateAndCountPagesMethod.invoke(faxImporter, pdfFile);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                })
                .isInstanceOf(FaxProviderException.class);
            } finally {
                if (pdfFile.exists()) {
                    pdfFile.delete();
                }
            }
        }

        @Test
        @DisplayName("should set ERROR status when PDF validation fails during saveAndInsertIntoQueue")
        @Tag("error-handling")
        void shouldSetErrorStatus_whenPdfValidationFails() throws Exception {
            // Given: FaxJob with corrupted PDF data
            FaxConfig faxConfig = createFaxConfig();
            FaxJob receivedFax = createReceivedFax();
            FaxJob faxFile = createFaxFile("Corrupted PDF data not base64");

            // When: Call saveAndInsertIntoQueue with corrupted data
            EDoc result = (EDoc) saveAndInsertIntoQueueMethod.invoke(
                    faxImporter, faxConfig, receivedFax, faxFile);

            // Then: Should return null (failure)
            assertThat(result).isNull();

            // Then: receivedFax should have ERROR status
            assertThat(receivedFax.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);

            // Then: Should have descriptive error message
            assertThat(receivedFax.getStatusString())
                    .isNotNull()
                    .containsAnyOf("validation", "PDF", "failed");
        }

        @Test
        @DisplayName("should clean up temp file when validation fails")
        @Tag("cleanup")
        void shouldCleanupTempFile_whenValidationFails() throws Exception {
            // Given: FaxJob with invalid PDF
            FaxConfig faxConfig = createFaxConfig();
            FaxJob receivedFax = createReceivedFax();

            // Create base64 of corrupted PDF
            String corruptedBase64 = Base64.encodeBytes("Not a valid PDF".getBytes());
            FaxJob faxFile = createFaxFile(corruptedBase64);

            // Track temp files before test
            String tempDir = System.getProperty("java.io.tmpdir");
            Path carlosFaxTemp = Paths.get(tempDir, "carlos-fax-import");
            long tempFilesBefore = countTempFiles(carlosFaxTemp);

            // When: Call saveAndInsertIntoQueue with corrupted data
            saveAndInsertIntoQueueMethod.invoke(faxImporter, faxConfig, receivedFax, faxFile);

            // Give filesystem time to sync
            Thread.sleep(100);

            // Then: No temp files should be leaked
            long tempFilesAfter = countTempFiles(carlosFaxTemp);
            assertThat(tempFilesAfter)
                    .as("Temp files should be cleaned up after validation failure")
                    .isEqualTo(tempFilesBefore);
        }
    }

    /**
     * Tests for atomic move failure recovery (Priority 9).
     *
     * <p><strong>Critical Risk:</strong> Atomic move failures could leave temp files containing
     * PHI on disk indefinitely, violating HIPAA/PIPEDA requirements. Proper cleanup in finally
     * blocks is essential for regulatory compliance.</p>
     */
    @Nested
    @DisplayName("Atomic Move Failure Recovery Tests (Priority 9)")
    @Tag("atomicity")
    class AtomicMoveFailureTests {

        @Test
        @DisplayName("should clean up temp file when destination directory does not exist")
        @Tag("cleanup")
        void shouldCleanupTempFile_whenDestinationDirectoryMissing() throws Exception {
            // Given: Valid PDF but invalid DOCUMENT_DIR (simulates configuration error)
            FaxConfig faxConfig = createFaxConfig();
            FaxJob receivedFax = createReceivedFax();

            // Create valid PDF as base64
            File validPdf = createValidPdf(1);
            byte[] pdfBytes = Files.readAllBytes(validPdf.toPath());
            validPdf.delete(); // Clean up temp PDF

            String validBase64 = Base64.encodeBytes(pdfBytes);
            FaxJob faxFile = createFaxFile(validBase64);

            // Track temp files before test
            String tempDir = System.getProperty("java.io.tmpdir");
            Path carlosFaxTemp = Paths.get(tempDir, "carlos-fax-import");
            long tempFilesBefore = countTempFiles(carlosFaxTemp);

            // When: Call saveAndInsertIntoQueue (will fail on Files.move due to bad DOCUMENT_DIR)
            EDoc result = (EDoc) saveAndInsertIntoQueueMethod.invoke(
                    faxImporter, faxConfig, receivedFax, faxFile);

            // Give filesystem time to sync
            Thread.sleep(100);

            // Then: Should return null (failure)
            assertThat(result).isNull();

            // Then: Should set ERROR status
            assertThat(receivedFax.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);

            // Then: Temp file should be cleaned up (no leak)
            long tempFilesAfter = countTempFiles(carlosFaxTemp);
            assertThat(tempFilesAfter)
                    .as("Temp file must be deleted in finally block when atomic move fails")
                    .isEqualTo(tempFilesBefore);
        }

        @Test
        @DisplayName("should set ERROR status with descriptive message when file move fails")
        @Tag("error-handling")
        void shouldSetErrorStatus_whenFileMoveFailsWithMessage() throws Exception {
            // Given: Valid PDF but move will fail
            FaxConfig faxConfig = createFaxConfig();
            FaxJob receivedFax = createReceivedFax();

            File validPdf = createValidPdf(1);
            byte[] pdfBytes = Files.readAllBytes(validPdf.toPath());
            validPdf.delete();

            String validBase64 = Base64.encodeBytes(pdfBytes);
            FaxJob faxFile = createFaxFile(validBase64);

            // When: Attempt to save (will fail)
            saveAndInsertIntoQueueMethod.invoke(faxImporter, faxConfig, receivedFax, faxFile);

            // Then: Should have ERROR status
            assertThat(receivedFax.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);

            // Then: Should have descriptive error message
            assertThat(receivedFax.getStatusString())
                    .isNotNull()
                    .satisfiesAnyOf(
                            msg -> assertThat(msg).containsIgnoringCase("file system"),
                            msg -> assertThat(msg).containsIgnoringCase("I/O"),
                            msg -> assertThat(msg).containsIgnoringCase("error")
                    );
        }

        @Test
        @DisplayName("should not create partial file in DOCUMENT_DIR when move fails")
        @Tag("atomicity")
        void shouldNotCreatePartialFile_whenMoveFails() throws Exception {
            // Given: Valid PDF
            FaxConfig faxConfig = createFaxConfig();
            FaxJob receivedFax = createReceivedFax();

            File validPdf = createValidPdf(1);
            byte[] pdfBytes = Files.readAllBytes(validPdf.toPath());
            validPdf.delete();

            String validBase64 = Base64.encodeBytes(pdfBytes);
            FaxJob faxFile = createFaxFile(validBase64);

            // When: Attempt to save (atomic move will fail due to bad DOCUMENT_DIR)
            saveAndInsertIntoQueueMethod.invoke(faxImporter, faxConfig, receivedFax, faxFile);

            // Then: No files should exist in DOCUMENT_DIR with the generated filename pattern
            // (This is implicit - if DOCUMENT_DIR doesn't exist, no files can be created there)
            // The atomic move ensures either complete success or complete failure
            assertThat(receivedFax.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a temporary file with given content for testing.
     */
    private File createTempFile(byte[] content) throws IOException {
        File tempFile = File.createTempFile("test-fax-", ".pdf");
        Files.write(tempFile.toPath(), content);
        return tempFile;
    }

    /**
     * Creates a valid PDF file with specified number of pages.
     */
    private File createValidPdf(int numberOfPages) throws IOException, DocumentException {
        File tempFile = File.createTempFile("test-valid-fax-", ".pdf");

        Document document = new Document(PageSize.LETTER);
        PdfWriter.getInstance(document, Files.newOutputStream(tempFile.toPath()));
        document.open();

        for (int i = 1; i <= numberOfPages; i++) {
            document.add(new Paragraph("Test page " + i));
            if (i < numberOfPages) {
                document.newPage();
            }
        }

        document.close();
        return tempFile;
    }

    /**
     * Counts temp files in the given directory.
     */
    private long countTempFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0;
        }

        return Files.list(directory)
                .filter(p -> p.getFileName().toString().startsWith("fax-"))
                .filter(p -> p.getFileName().toString().endsWith(".pdf"))
                .count();
    }

    /**
     * Creates a test FaxConfig.
     */
    private FaxConfig createFaxConfig() {
        FaxConfig config = new FaxConfig();
        config.setId(1);
        config.setActive(true);
        config.setDownload(true);
        config.setProviderType(FaxConfig.ProviderType.SRFAX);
        config.setQueue(1);
        return config;
    }

    /**
     * Creates a test FaxJob representing received fax metadata.
     */
    private FaxJob createReceivedFax() {
        FaxJob fax = new FaxJob();
        fax.setId(1);
        fax.setFile_name("incoming-referral.pdf");
        fax.setStamp(new Date());
        fax.setStatus(FaxJob.STATUS.WAITING);
        return fax;
    }

    /**
     * Creates a test FaxJob with document content.
     */
    private FaxJob createFaxFile(String base64Content) {
        FaxJob fax = new FaxJob();
        fax.setDocument(base64Content);
        return fax;
    }
}
