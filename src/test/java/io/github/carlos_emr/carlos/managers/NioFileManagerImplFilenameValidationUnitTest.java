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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.ServletContext;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the filename-validation contracts introduced when the local {@code sanitizeFileName}
 * helper was retired in favour of {@link PathValidationUtils} (issue #2213).
 *
 * <p>The retired helper silently returned {@code ""} or the sentinel {@code "invalid_filename"} on
 * bad input, so an invalid name could become a real on-disk file or make a PHI-cache flush report
 * success without clearing anything. These tests pin the replacement behaviour:</p>
 * <ul>
 *   <li>Read/preview lookups ({@code hasCacheVersion2}, {@code createCacheVersion2}) return
 *       {@code null} for an unusable filename rather than a sentinel.</li>
 *   <li>Mutating/opening paths ({@code getOscarDocument}, {@code removeCacheVersion},
 *       {@code removeCacheVersions}) fail loudly with a {@link SecurityException}.</li>
 *   <li>{@code saveTempFile} intentionally normalizes a newly supplied name and fails loudly when it
 *       cannot produce a usable one.</li>
 * </ul>
 */
@Tag("unit")
@Tag("manager")
@Tag("security")
@DisplayName("NioFileManagerImpl filename validation (issue #2213)")
class NioFileManagerImplFilenameValidationUnitTest extends CarlosUnitTestBase {

    private NioFileManagerImpl nioFileManager;
    private LoggedInInfo loggedInInfo;

    @TempDir
    Path tempDir;

    private String originalBaseDocumentDir;

    @BeforeEach
    void setUp() {
        originalBaseDocumentDir = CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("BASE_DOCUMENT_DIR", tempDir.toString());

        nioFileManager = new NioFileManagerImpl();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getContextPath()).thenReturn("/carlos");
        // Grant _edoc READ so the tests exercise the filename branch, not the privilege gate.
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.READ), eq("")))
                .thenReturn(true);

        ReflectionTestUtils.setField(nioFileManager, "securityInfoManager", securityInfoManager);
        ReflectionTestUtils.setField(nioFileManager, "context", servletContext);
    }

    @AfterEach
    void tearDown() {
        if (originalBaseDocumentDir == null) {
            CarlosProperties.getInstance().remove("BASE_DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("BASE_DOCUMENT_DIR", originalBaseDocumentDir);
        }
    }

    @Nested
    @DisplayName("read/preview lookups return null (not a sentinel) for invalid names")
    class ReadPathsReturnNull {

        @ParameterizedTest
        @ValueSource(strings = {"../evil.png", "..\\evil.png", ".hidden.png", "sub/evil.png"})
        @DisplayName("hasCacheVersion2 returns null for a path-like or hidden filename")
        void shouldReturnNull_forInvalidCacheFilename(String filename) {
            assertThat(nioFileManager.hasCacheVersion2(loggedInInfo, filename, 1)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"../evil.pdf", "..\\evil.pdf", ".hidden.pdf", "sub/evil.pdf"})
        @DisplayName("createCacheVersion2 returns null for a path-like or hidden source filename")
        void shouldReturnNull_forInvalidSourceFilename(String filename) {
            assertThat(nioFileManager.createCacheVersion2(loggedInInfo, tempDir.toString(), filename, 1)).isNull();
        }
    }

    @Nested
    @DisplayName("open/remove paths fail loudly for invalid names")
    class MutatingPathsFailLoudly {

        @ParameterizedTest
        @ValueSource(strings = {"../secret.pdf", "..\\secret.pdf", ".hidden.pdf", "nested/secret.pdf"})
        @DisplayName("getOscarDocument throws SecurityException rather than resolving a sentinel path")
        void shouldThrowSecurityException_forInvalidDocumentFilename(String filename) {
            assertThatThrownBy(() -> nioFileManager.getOscarDocument(filename))
                    .isInstanceOf(SecurityException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"../secret.png", "..\\secret.png", ".hidden.png", "nested/secret.png"})
        @DisplayName("removeCacheVersion (single) throws SecurityException for an invalid filename")
        void shouldThrowSecurityException_forInvalidSingleRemovalFilename(String filename) {
            assertThatThrownBy(() -> nioFileManager.removeCacheVersion(loggedInInfo, filename))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("removeCacheVersions fails loudly for an invalid filename instead of reporting 0 cleared")
        void shouldThrowSecurityException_forInvalidFlushFilename() throws IOException {
            Path allowedSource = createApplicationTempDirectory();
            try {
                assertThatThrownBy(() ->
                        nioFileManager.removeCacheVersions(loggedInInfo, allowedSource.toString(), "../secret.pdf"))
                        .isInstanceOf(SecurityException.class);
            } finally {
                Files.deleteIfExists(allowedSource);
            }
        }
    }

    @Nested
    @DisplayName("saveTempFile normalizes a new name and fails loudly when none remains")
    class SaveTempFileContract {

        @Test
        @DisplayName("normalizes a newly supplied temp filename (whitespace to underscore)")
        void shouldNormalizeNewlySuppliedName_whenSavingTempFile() throws IOException {
            Path saved = null;
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                os.write("hi".getBytes(StandardCharsets.UTF_8));
                saved = nioFileManager.saveTempFile("my report", os, "pdf");
                assertThat(saved.getFileName().toString()).isEqualTo("my_report.pdf");
            } finally {
                if (saved != null) {
                    Files.deleteIfExists(saved);
                    Files.deleteIfExists(saved.getParent());
                }
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"@@@", "///"})
        @DisplayName("throws rather than creating a sentinel temp file when the name cleans to empty")
        void shouldThrow_whenTempFilenameCleansToEmpty(String filename) throws IOException {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                os.write("hi".getBytes(StandardCharsets.UTF_8));
                assertThatThrownBy(() -> nioFileManager.saveTempFile(filename, os, "pdf"))
                        .isInstanceOf(SecurityException.class);
            }
        }
    }

    private Path createApplicationTempDirectory() throws IOException {
        Path applicationParent = Files.createDirectories(
                Path.of(System.getProperty("java.io.tmpdir"), PathValidationUtils.APPLICATION_TEMP_ROOT_NAME));
        return Files.createTempDirectory(applicationParent, "nio-2213-");
    }
}
