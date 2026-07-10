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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import jakarta.servlet.ServletContext;
import io.github.carlos_emr.CarlosProperties;
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
        ServletContext servletContext = mock(ServletContext.class);

        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), anyString(), eq(""))).thenReturn(true);
        when(servletContext.getContextPath()).thenReturn("carlos");

        ReflectionTestUtils.setField(nioFileManager, "securityInfoManager", securityInfoManager);
        ReflectionTestUtils.setField(nioFileManager, "context", servletContext);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (allowedTempDir != null) {
            Files.deleteIfExists(allowedTempDir.resolve("fax-preview.pdf"));
            Files.deleteIfExists(allowedTempDir.resolve("fax-preview-unique.pdf"));
            Files.deleteIfExists(getDocumentCacheDirectory().resolve("fax-preview.pdf_1.png"));
            Files.deleteIfExists(getDocumentCacheDirectory().resolve("fax-preview-unique.pdf_1.png"));
            Files.deleteIfExists(allowedTempDir);
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

    private Path createOutsideAllowedTempDirectory() throws IOException {
        return Files.createTempDirectory(Path.of(System.getProperty("user.dir")), "nio-delete-outside-" + UUID.randomUUID());
    }

    private static Path getDocumentCacheDirectory() {
        return Path.of(CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR"), "carlos", "document_cache");
    }

    private static void createSinglePagePdf(Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(path.toFile());
        }
    }
}
