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
import java.util.List;

import jakarta.servlet.ServletContext;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
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
 *
 * <p>Methods are kept flat (no {@code @Nested}) so they are reliably selected by a
 * {@code -Dtest=NioFileManagerImplFilenameValidationUnitTest} Surefire run.</p>
 *
 * @since 2026-08-15
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
    void setUp() throws IOException {
        originalBaseDocumentDir = CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("BASE_DOCUMENT_DIR", tempDir.toString());
        // resolveDocumentCacheDirectory creates only the leaf, so the context directory must exist.
        Files.createDirectories(tempDir.resolve("carlos"));

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

    // ---- read/preview lookups return null (not a sentinel) for invalid names ----

    @ParameterizedTest
    @ValueSource(strings = {"../evil.png", "..\\evil.png", ".hidden.png", "sub/evil.png"})
    @DisplayName("hasCacheVersion2 returns null for a path-like or hidden filename even when a rewritten name is cached")
    void shouldReturnNull_forInvalidCacheFilename(String filename) throws IOException {
        // Seed a cache entry under every name a rewriting sanitizer could have produced, so a null
        // result proves the name was refused rather than being an ordinary cache miss.
        Path cacheDir = nioFileManager.resolveDocumentCacheDirectory();
        for (String rewritten : List.of("evil.png", "hidden.png", ".hidden.png", "sub_evil.png", "invalid_filename")) {
            Files.write(cacheDir.resolve(rewritten + "_1.png"), new byte[] {1});
            Files.write(cacheDir.getParent().resolve(rewritten + "_1.png"), new byte[] {1});
        }

        assertThat(nioFileManager.hasCacheVersion2(loggedInInfo, filename, 1)).isNull();
    }

    @Test
    @DisplayName("hasCacheVersion2 control: a valid cached name is found (so the seeding above is meaningful)")
    void shouldReturnCachedPath_forValidCacheFilename() throws IOException {
        Path cacheDir = nioFileManager.resolveDocumentCacheDirectory();
        Path cached = Files.write(cacheDir.resolve("evil.png_1.png"), new byte[] {1});

        assertThat(nioFileManager.hasCacheVersion2(loggedInInfo, "evil.png", 1)).isEqualTo(cached.toRealPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil.pdf", "..\\evil.pdf", ".hidden.pdf", "sub/evil.pdf"})
    @DisplayName("createCacheVersion2 returns null for a path-like or hidden source filename even when a rewritten source exists")
    void shouldReturnNull_forInvalidSourceFilename(String filename) throws IOException {
        // Real PDFs exist under every name a rewriting sanitizer could have produced, so null proves
        // the name was refused rather than the source simply being absent.
        for (String rewritten : List.of("evil.pdf", "hidden.pdf", ".hidden.pdf", "sub_evil.pdf", "invalid_filename")) {
            writeBlankPdf(tempDir.resolve(rewritten));
        }

        assertThat(nioFileManager.createCacheVersion2(loggedInInfo, tempDir.toString(), filename, 1)).isNull();
        try (java.util.stream.Stream<Path> cached = Files.list(nioFileManager.resolveDocumentCacheDirectory())) {
            assertThat(cached.toList()).isEmpty();
        }
    }

    @Test
    @DisplayName("createCacheVersion2 control: a valid source renders (so the seeded PDFs above are reachable)")
    void shouldRenderPreview_forValidSourceFilename() throws IOException {
        writeBlankPdf(tempDir.resolve("evil.pdf"));

        assertThat(nioFileManager.createCacheVersion2(loggedInInfo, tempDir.toString(), "evil.pdf", 1)).isNotNull();
    }

    // ---- open/remove paths fail loudly for invalid names ----

    @ParameterizedTest
    @ValueSource(strings = {"../secret.pdf", "..\\secret.pdf", ".hidden.pdf", "nested/secret.pdf"})
    @DisplayName("getOscarDocument throws SecurityException rather than resolving a sentinel path")
    void shouldThrowSecurityException_forInvalidDocumentFilename(String filename) {
        assertThatThrownBy(() -> nioFileManager.getOscarDocument(filename))
                .isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "../secret.png", "..\\secret.png", ".hidden.png", "nested/secret.png"})
    @DisplayName("removeCacheVersion (single) throws SecurityException for a null, blank, or invalid filename")
    void shouldThrowSecurityException_forInvalidSingleRemovalFilename(String filename) {
        assertThatThrownBy(() -> nioFileManager.removeCacheVersion(loggedInInfo, filename))
                .isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "../secret.pdf", "..\\secret.pdf", ".hidden.pdf", "nested/secret.pdf"})
    @DisplayName("removeCacheVersions fails loudly for a null, blank, or invalid filename instead of reporting 0 cleared")
    void shouldThrowSecurityException_forInvalidFlushFilename(String filename) throws IOException {
        Path allowedSource = createApplicationTempDirectory();
        try {
            assertThatThrownBy(() ->
                    nioFileManager.removeCacheVersions(loggedInInfo, allowedSource.toString(), filename))
                    .isInstanceOf(SecurityException.class);
        } finally {
            Files.deleteIfExists(allowedSource);
        }
    }

    // ---- saveTempFile normalizes a new name and fails loudly when none remains ----

    @Test
    @DisplayName("saveTempFile normalizes a newly supplied temp filename (whitespace to underscore)")
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
    @DisplayName("saveTempFile throws and leaves no temp directory behind when the name cleans to empty")
    void shouldThrow_whenTempFilenameCleansToEmpty(String filename) throws IOException {
        // The rejected-name validation must happen before the temp directory is created, so a
        // failure leaves nothing behind (issue #2213 review). Point java.io.tmpdir at a per-test
        // directory so the assertion cannot race other tests or forks writing tempPDF* directories
        // into the shared system temp root. applicationTempParent() reads the property on every call.
        Path isolatedTmp = Files.createDirectory(tempDir.resolve("isolated-tmp"));
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", isolatedTmp.toString());
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            os.write("hi".getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> nioFileManager.saveTempFile(filename, os, "pdf"))
                    .isInstanceOf(SecurityException.class);

            Path applicationParent = isolatedTmp.resolve(PathValidationUtils.APPLICATION_TEMP_ROOT_NAME);
            if (Files.isDirectory(applicationParent)) {
                try (java.util.stream.Stream<Path> entries = Files.list(applicationParent)) {
                    assertThat(entries.toList()).isEmpty();
                }
            }
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
        }
    }

    private static void writeBlankPdf(Path target) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(target.toFile());
        }
    }

    private Path createApplicationTempDirectory() throws IOException {
        Path applicationParent = Files.createDirectories(
                Path.of(System.getProperty("java.io.tmpdir"), PathValidationUtils.APPLICATION_TEMP_ROOT_NAME));
        return Files.createTempDirectory(applicationParent, "nio-2213-");
    }
}
