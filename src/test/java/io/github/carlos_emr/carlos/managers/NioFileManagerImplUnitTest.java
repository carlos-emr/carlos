/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.servlet.ServletContext;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@Tag("manager")
class NioFileManagerImplUnitTest extends CarlosUnitTestBase {

    private NioFileManagerImpl nioFileManager;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private ServletContext servletContext;
    private Path allowedTempDir;
    private Path outsideDir;
    private Path outsideFile;
    private Path symlink;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        System.setProperty("java.awt.headless", "true");
        nioFileManager = new NioFileManagerImpl();
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        servletContext = mock(ServletContext.class);

        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), anyString(), eq(""))).thenReturn(true);
        when(servletContext.getContextPath()).thenReturn("/carlos");

        ReflectionTestUtils.setField(nioFileManager, "securityInfoManager", securityInfoManager);
        ReflectionTestUtils.setField(nioFileManager, "context", servletContext);
        ReflectionTestUtils.setField(nioFileManager, "baseDocumentDirectory", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (allowedTempDir != null) {
            deleteDirectoryQuietly(allowedTempDir);
        }
        if (symlink != null) {
            Files.deleteIfExists(symlink);
        }
        if (outsideFile != null) {
            Files.deleteIfExists(outsideFile);
        }
        if (outsideDir != null) {
            Files.deleteIfExists(outsideDir);
        }
    }

    @Test
    @DisplayName("Creates document cache directories for nested servlet context paths")
    void shouldCreateDocumentCacheDirectory_whenContextPathIsNested() {
        when(servletContext.getContextPath()).thenReturn("/clinic/carlos");

        Path cacheDirectory = nioFileManager.getDocumentCacheDirectory(loggedInInfo);

        assertThat(cacheDirectory)
                .isEqualTo(tempDir.resolve(Path.of("clinic", "carlos", "document_cache"))
                        .normalize()
                        .toAbsolutePath());
        assertThat(cacheDirectory).isDirectory();
    }

    @Test
    @DisplayName("Deletes a valid temp file")
    void shouldDeleteTempFile_whenValidTempFileProvided() throws IOException {
        Path tempFile = Files.createFile(tempDir.resolve("valid-upload.tmp"));

        boolean deleted = nioFileManager.deleteTempFile(tempFile.toString());

        assertThat(deleted).isTrue();
        assertThat(tempFile).doesNotExist();
    }

    @Test
    @DisplayName("Rejects delete targets outside approved temp directories")
    void shouldThrowSecurityException_whenOutsidePathProvided() throws IOException {
        outsideDir = createOutsideAllowedTempDirectory();
        outsideFile = Files.createFile(outsideDir.resolve("outside.tmp"));
        assumeTrue(!PathValidationUtils.isInAllowedTempDirectory(outsideFile.toFile()),
                "outside test directory unexpectedly resolves inside an allowed temp directory");
        String outsidePath = outsideFile.toString();

        assertThatThrownBy(() -> nioFileManager.deleteTempFile(outsidePath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid temp deletion target");
        assertThat(outsideFile).exists();
    }

    @Test
    @DisplayName("Rejects directory temp deletion targets")
    void shouldThrowSecurityException_whenDirectoryTargetProvided() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("not-a-file"));
        String directoryPath = directory.toString();

        assertThatThrownBy(() -> nioFileManager.deleteTempFile(directoryPath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("regular file");
        assertThat(directory).isDirectory();
    }

    @Test
    @DisplayName("Rejects temp symlinks that escape approved temp directories")
    void shouldThrowSecurityException_whenTempSymlinkEscapesAllowedTempDirectory() throws IOException {
        outsideDir = createOutsideAllowedTempDirectory();
        outsideFile = Files.createFile(outsideDir.resolve("victim.tmp"));
        assumeTrue(!PathValidationUtils.isInAllowedTempDirectory(outsideFile.toFile()),
                "outside test file unexpectedly resolves inside an allowed temp directory");
        symlink = tempDir.resolve("link.tmp");
        try {
            Files.createSymbolicLink(symlink, outsideFile);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available in this test environment: " + e.getMessage());
        }
        String symlinkPath = symlink.toString();

        assertThatThrownBy(() -> nioFileManager.deleteTempFile(symlinkPath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid temp deletion target");
        assertThat(outsideFile).exists();
    }

    @Test
    @DisplayName("Returns false for missing approved temp files")
    void shouldReturnFalse_whenTempFileMissing() {
        Path missingFile = tempDir.resolve("missing-upload.tmp");

        boolean deleted = nioFileManager.deleteTempFile(missingFile.toString());

        assertThat(deleted).isFalse();
        assertThat(missingFile).doesNotExist();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Returns false for null or blank temp deletion targets")
    void shouldReturnFalse_whenTempFileNameBlankOrNull(String fileName) {
        boolean deleted = nioFileManager.deleteTempFile(fileName);

        assertThat(deleted).isFalse();
    }


    @Test
    @DisplayName("Creates preview images for approved temp PDFs used by fax rendering")
    void shouldCreateCacheVersion_whenSourcePdfIsInAllowedTempDirectory() throws IOException {
        allowedTempDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "nio-cache-preview-");
        assumeTrue(PathValidationUtils.isInAllowedTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside an allowed temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path sourcePdf = allowedTempDir.resolve("fax-preview-unique.pdf");
        Path expectedCache = getDocumentCacheDirectory().resolve("fax-preview-unique.pdf_1.png");
        Files.deleteIfExists(expectedCache);
        createSinglePagePdf(sourcePdf);

        try {
            Path cacheVersion = nioFileManager.createCacheVersion2(loggedInInfo, allowedTempDir.toString(), sourcePdf.getFileName().toString(), 1);

            assertThat(cacheVersion).isNotNull().exists();
            assertThat(cacheVersion.getFileName().toString()).endsWith("_1.png");
            assertThat(Files.size(cacheVersion)).isPositive();
        } finally {
            Files.deleteIfExists(expectedCache);
        }
    }

    @Test
    @DisplayName("Creates fax preview images with fax read privilege without requiring edoc write")
    void shouldCreateFaxPreviewCacheVersion_whenFaxReadGrantedAndEdocWriteDenied() throws IOException {
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.WRITE), eq(""))).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.READ), eq(""))).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        allowedTempDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "nio-fax-preview-");
        assumeTrue(PathValidationUtils.isInAllowedTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside an allowed temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        String sourceFilename = "fax-preview-fax-only-" + UUID.randomUUID() + ".pdf";
        Path sourcePdf = allowedTempDir.resolve(sourceFilename);
        Path cacheVersion = null;
        createSinglePagePdf(sourcePdf);

        try {
            cacheVersion = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, allowedTempDir.toString(), sourceFilename, 1);

            assertThat(cacheVersion).isNotNull().exists();
            assertThat(cacheVersion.getFileName().toString())
                    .startsWith(sourceFilename + "_")
                    .endsWith("_1.png")
                    .isNotEqualTo(sourceFilename + "_1.png");
            assertThat(Files.size(cacheVersion)).isPositive();
        } finally {
            deleteFileQuietly(cacheVersion);
            Files.deleteIfExists(sourcePdf);
        }
    }

    @Test
    @DisplayName("Isolates fax preview cache files for temp PDFs with the same basename")
    void shouldCreateDistinctFaxPreviewCacheFiles_whenTempSourcesShareBasename() throws IOException {
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        Path firstTempDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "nio-fax-preview-one-");
        Path secondTempDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "nio-fax-preview-two-");
        assumeTrue(PathValidationUtils.isInAllowedTempDirectory(firstTempDir.toFile()),
                "first test temp directory must resolve inside an allowed temp directory");
        assumeTrue(PathValidationUtils.isInAllowedTempDirectory(secondTempDir.toFile()),
                "second test temp directory must resolve inside an allowed temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path firstSourcePdf = firstTempDir.resolve("fax-preview.pdf");
        Path secondSourcePdf = secondTempDir.resolve("fax-preview.pdf");
        Path firstCache = null;
        Path secondCache = null;
        createSinglePagePdf(firstSourcePdf);
        createSinglePagePdf(secondSourcePdf);

        try {
            firstCache = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, firstTempDir.toString(), "fax-preview.pdf", 1);
            secondCache = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, secondTempDir.toString(), "fax-preview.pdf", 1);

            assertThat(firstCache).isNotNull().exists();
            assertThat(secondCache).isNotNull().exists();
            assertThat(firstCache).isNotEqualTo(secondCache);
        } finally {
            deleteFileQuietly(firstCache);
            deleteFileQuietly(secondCache);
            deleteDirectoryQuietly(firstTempDir);
            deleteDirectoryQuietly(secondTempDir);
        }
    }

    @Test
    @DisplayName("Creates new fax preview cache files when same-path source contents change")
    void shouldCreateNewFaxPreviewCacheFile_whenSourceAtSamePathChanges() throws IOException {
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        allowedTempDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "nio-fax-preview-replace-");
        assumeTrue(PathValidationUtils.isInAllowedTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside an allowed temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path sourcePdf = allowedTempDir.resolve("fax-preview-replaced.pdf");
        Path firstCache = null;
        Path secondCache = null;

        try {
            createSinglePagePdf(sourcePdf);
            firstCache = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, allowedTempDir.toString(),
                    sourcePdf.getFileName().toString(), 1);

            createPdf(sourcePdf, 2);
            Files.setLastModifiedTime(sourcePdf, FileTime.fromMillis(System.currentTimeMillis() + 10000L));
            secondCache = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, allowedTempDir.toString(),
                    sourcePdf.getFileName().toString(), 1);

            assertThat(firstCache).isNotNull().exists();
            assertThat(secondCache).isNotNull().exists();
            assertThat(secondCache).isNotEqualTo(firstCache);
        } finally {
            deleteFileQuietly(firstCache);
            deleteFileQuietly(secondCache);
        }
    }

    @Test
    @DisplayName("Creates fax preview cache generation for document-store sources")
    void shouldCreateFaxPreviewCacheVersion_whenSourcePdfIsInDocumentStore() throws IOException {
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        Path sourceDir = tempDir.resolve(Path.of("carlos", "fax-preview-doc-source-" + UUID.randomUUID()));
        Files.createDirectories(sourceDir);
        Path sourcePdf = sourceDir.resolve("fax-preview-document.pdf");
        Path cacheVersion = null;
        createSinglePagePdf(sourcePdf);

        try {
            cacheVersion = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo,
                    sourceDir.toString(), sourcePdf.getFileName().toString(), 1);

            assertThat(cacheVersion).isNotNull().exists();
            assertThat(cacheVersion.getFileName().toString())
                    .startsWith(sourcePdf.getFileName().toString() + "_")
                    .endsWith("_1.png")
                    .isNotEqualTo(sourcePdf.getFileName().toString() + "_1.png");
        } finally {
            deleteFileQuietly(cacheVersion);
            Files.deleteIfExists(sourcePdf);
            Files.deleteIfExists(sourceDir);
        }
    }

    @Test
    @DisplayName("Returns null for null fax preview page numbers")
    void shouldReturnNull_whenFaxPreviewPageNumberIsNull() {
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

        Path cacheVersion = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, "/tmp", "fax-preview.pdf", null);

        assertThat(cacheVersion).isNull();
    }

    @Test
    @DisplayName("Removes discriminator-named fax preview cache pages")
    void shouldRemoveFaxPreviewCacheVersions_whenSourceUsesDiscriminator() throws IOException {
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        allowedTempDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "nio-fax-preview-remove-");
        assumeTrue(PathValidationUtils.isInAllowedTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside an allowed temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path sourcePdf = allowedTempDir.resolve("fax-preview-removal.pdf");
        createPdf(sourcePdf, 2);
        Path firstCache = null;
        Path secondCache = null;
        Path legacyCache = getDocumentCacheDirectory().resolve("fax-preview-removal.pdf_1.png");

        try {
            firstCache = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, allowedTempDir.toString(),
                    sourcePdf.getFileName().toString(), 1);
            secondCache = nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, allowedTempDir.toString(),
                    sourcePdf.getFileName().toString(), 2);
            Files.writeString(legacyCache, "legacy");

            boolean removed = nioFileManager.removeFaxPreviewCacheVersions(loggedInInfo, sourcePdf.toString());

            assertThat(removed).isTrue();
            assertThat(firstCache).doesNotExist();
            assertThat(secondCache).doesNotExist();
            assertThat(legacyCache).exists();
        } finally {
            deleteFileQuietly(firstCache);
            deleteFileQuietly(secondCache);
            deleteFileQuietly(legacyCache);
        }
    }

    @Test
    @DisplayName("Returns true when an approved fax preview source has no cache pages")
    void shouldReturnTrue_whenApprovedFaxPreviewSourceHasNoCachePages() throws IOException {
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        Path sourceDir = tempDir.resolve(Path.of("carlos", "fax-preview-doc-source-" + UUID.randomUUID()));
        Files.createDirectories(sourceDir);
        Path sourcePdf = sourceDir.resolve("fax-preview-document.pdf");
        createSinglePagePdf(sourcePdf);

        try {
            boolean removed = nioFileManager.removeFaxPreviewCacheVersions(loggedInInfo, sourcePdf.toString());

            assertThat(removed).isTrue();
        } finally {
            Files.deleteIfExists(sourcePdf);
            Files.deleteIfExists(sourceDir);
        }
    }

    @Test
    @DisplayName("Ignores discriminator cache matches for sources outside approved roots")
    void shouldNotRemoveFaxPreviewCacheVersions_whenSourcePathIsOutsideApprovedRoots() throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        outsideDir = createOutsideAllowedTempDirectory();
        outsideFile = outsideDir.resolve("fax-preview-removal.pdf");
        createSinglePagePdf(outsideFile);
        assumeTrue(!PathValidationUtils.isInAllowedTempDirectory(outsideFile.toFile()),
                "outside test PDF unexpectedly resolves inside an allowed temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path cacheFile = getDocumentCacheDirectory().resolve("fax-preview-removal.pdf_"
                + sourcePathDiscriminator(outsideFile) + "_1.png");
        Files.writeString(cacheFile, "cache");

        try {
            boolean removed = nioFileManager.removeFaxPreviewCacheVersions(loggedInInfo, outsideFile.toString());

            assertThat(removed).isFalse();
            assertThat(cacheFile).exists();
        } finally {
            Files.deleteIfExists(cacheFile);
        }
    }

    @Test
    @DisplayName("Keeps edoc cache generation gated by edoc write privilege")
    void shouldRequireEdocWrite_whenCreatingDocumentCacheVersion() {
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.WRITE), eq(""))).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

        assertThatThrownBy(() -> nioFileManager.createCacheVersion2(loggedInInfo, "/tmp", "fax-preview.pdf", 1))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_edoc)");
    }

    @Test
    @DisplayName("Keeps edoc cache generation gated by edoc read privilege")
    void shouldRequireEdocRead_whenCreatingDocumentCacheVersion() {
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.WRITE), eq(""))).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.READ), eq(""))).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

        assertThatThrownBy(() -> nioFileManager.createCacheVersion2(loggedInInfo, "/tmp", "fax-preview.pdf", 1))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_edoc)");
    }

    @Test
    @DisplayName("Rejects fax preview cache generation without fax read privilege")
    void shouldRequireFaxRead_whenCreatingFaxPreviewCacheVersion() {
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);

        assertThatThrownBy(() -> nioFileManager.createFaxPreviewCacheVersion(loggedInInfo, "/tmp", "fax-preview.pdf", 1))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_fax)");
    }

    private Path createOutsideAllowedTempDirectory() throws IOException {
        return Files.createTempDirectory(Path.of(System.getProperty("user.dir")), "nio-delete-outside-" + UUID.randomUUID());
    }

    private Path getDocumentCacheDirectory() {
        return tempDir.resolve(Path.of("carlos", "document_cache"));
    }

    private static String sourcePathDiscriminator(Path sourceFile) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(sourceFile.toRealPath().toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(16);
        for (byte value : digest) {
            if (builder.length() >= 16) {
                break;
            }
            builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
            if (builder.length() < 16) {
                builder.append(Character.forDigit(value & 0xF, 16));
            }
        }
        return builder.toString();
    }

    private static void createSinglePagePdf(Path path) throws IOException {
        createPdf(path, 1);
    }

    private static void createPdf(Path path, int pageCount) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            document.save(path.toFile());
        }
    }

    private static void deleteDirectoryQuietly(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        }
    }

    private static void deleteFileQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Best-effort cleanup in tests must not mask the assertion failure being reported.
            }
        }
    }
}
