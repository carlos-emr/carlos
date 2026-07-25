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

    private String originalHeadless;
    private String originalBaseDocumentDir;

    @BeforeEach
    void setUp() throws IOException {
        // Force headless so ImageIO/AWT cache rendering works in CI; capture the prior value so
        // tearDown can restore it and this global property does not leak into other tests' JVM.
        originalHeadless = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "true");
        // Point the document root at this test's @TempDir so the fax-preview cache writes land in
        // an isolated, auto-cleaned location rather than the real configured document store. The
        // production resolver (NioFileManagerImpl#baseDocumentDir) reads this property live, so the
        // override takes effect without a redeploy; captured here for restoration in tearDown.
        originalBaseDocumentDir = CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("BASE_DOCUMENT_DIR", tempDir.toString());
        // getDocumentCacheDirectory() creates a single-level "document_cache" dir under <base>/carlos,
        // so the parent context directory must already exist.
        Files.createDirectories(tempDir.resolve("carlos"));
        nioFileManager = new NioFileManagerImpl();
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        ServletContext servletContext = mock(ServletContext.class);

        // Stub the READ level exactly: the WRITE->READ downgrade on the preview-cache gate is the
        // enabling change for _fax-only preview, and an anyString() level stub would let a
        // regression back to WRITE pass every test here.
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.READ), eq(""))).thenReturn(true);
        // The Servlet API returns a non-root context path WITH a leading slash; mirror that contract
        // so the test exercises the same input the production resolver strips .
        when(servletContext.getContextPath()).thenReturn("/carlos");

        ReflectionTestUtils.setField(nioFileManager, "securityInfoManager", securityInfoManager);
        ReflectionTestUtils.setField(nioFileManager, "context", servletContext);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (allowedTempDir != null) {
            Files.deleteIfExists(allowedTempDir.resolve("fax-preview.pdf"));
            Files.deleteIfExists(allowedTempDir.resolve("fax-preview-unique.pdf"));
            // The generated cache files carry the source-scoped key (<name>_<key>_<page>.png), so a
            // fixed-name delete never matches; the cache lives under the @TempDir document root and is
            // auto-removed. Each cache test deletes its own returned path.
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
        if (originalHeadless == null) {
            System.clearProperty("java.awt.headless");
        } else {
            System.setProperty("java.awt.headless", originalHeadless);
        }
        // Restore the document root. Properties.setProperty rejects null, so a previously-unset
        // value is cleared from the map rather than re-set.
        if (originalBaseDocumentDir == null) {
            CarlosProperties.getInstance().remove("BASE_DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("BASE_DOCUMENT_DIR", originalBaseDocumentDir);
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
    void shouldCreateCacheVersion_whenSourcePdfIsInApplicationTempDirectory() throws IOException {
        allowedTempDir = createApplicationTempDirectory("nio-cache-preview-");
        assumeTrue(PathValidationUtils.isInApplicationTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside a CARLOS-owned temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path sourcePdf = allowedTempDir.resolve("fax-preview-unique.pdf");
        createSinglePagePdf(sourcePdf);

        Path cacheVersion = null;
        try {
            cacheVersion = nioFileManager.createCacheVersion2(loggedInInfo, allowedTempDir.toString(), sourcePdf.getFileName().toString(), 1);

            assertThat(cacheVersion).isNotNull().exists();
            assertThat(cacheVersion.getFileName().toString()).endsWith("_1.png");
            assertThat(Files.size(cacheVersion)).isPositive();
        } finally {
            if (cacheVersion != null) {
                Files.deleteIfExists(cacheVersion);
            }
        }
    }

    @Test
    @DisplayName("Bounds the cache filename for overlong PDF names so preview rendering still succeeds")
    void shouldBoundCacheFilename_whenSourcePdfNameIsOverlong() throws IOException {
        allowedTempDir = createApplicationTempDirectory("nio-cache-longname-");
        assumeTrue(PathValidationUtils.isInApplicationTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside a CARLOS-owned temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        // A 200-char base name is a valid file component but would blow past the 255-char limit once
        // the "_<sourceKey>_<page>.png" suffix is appended, unless the cache name is bounded.
        String longName = "a".repeat(200) + ".pdf";
        Path sourcePdf = allowedTempDir.resolve(longName);
        createSinglePagePdf(sourcePdf);

        Path cacheVersion = null;
        try {
            cacheVersion = nioFileManager.createCacheVersion2(loggedInInfo, allowedTempDir.toString(), longName, 1);

            assertThat(cacheVersion).isNotNull().exists();
            String cacheName = cacheVersion.getFileName().toString();
            assertThat(cacheName).endsWith("_1.png");
            assertThat(cacheName.length()).isLessThanOrEqualTo(255);
        } finally {
            if (cacheVersion != null) {
                Files.deleteIfExists(cacheVersion);
            }
            // tearDown only knows the fixed fixture names, so remove this test's long-named source
            // before it tries to delete the (now-empty) allowedTempDir.
            Files.deleteIfExists(sourcePdf);
        }
    }

    @Test
    @DisplayName("Scopes the preview cache to the source directory so a reused filename cannot leak another document")
    void shouldScopeCacheToSourceDirectory_whenTwoTempSourcesReuseAFilename() throws IOException {
        Path firstSource = createApplicationTempDirectory("nio-cache-collide-a-");
        Path secondSource = createApplicationTempDirectory("nio-cache-collide-b-");
        assumeTrue(PathValidationUtils.isInApplicationTempDirectory(firstSource.toFile())
                        && PathValidationUtils.isInApplicationTempDirectory(secondSource.toFile()),
                "test temp directories must resolve inside a CARLOS-owned temp directory");
        Files.createDirectories(getDocumentCacheDirectory());

        // Same filename, distinct source directories: the collision case the source-scoped key closes.
        String sharedFilename = "collision.pdf";
        createSinglePagePdf(firstSource.resolve(sharedFilename));
        createSinglePagePdf(secondSource.resolve(sharedFilename));

        try {
            Path firstCache = nioFileManager.createCacheVersion2(loggedInInfo, firstSource.toString(), sharedFilename, 1);
            Path secondCache = nioFileManager.createCacheVersion2(loggedInInfo, secondSource.toString(), sharedFilename, 1);

            assertThat(firstCache).isNotNull().exists();
            assertThat(secondCache).isNotNull().exists();
            // Keyed on filename+page alone, both would resolve to the same "collision.pdf_1.png" and
            // the second call would hand back the first source's cached page. The source-scoped key
            // gives each source a distinct cache file.
            assertThat(secondCache.getFileName().toString())
                    .isNotEqualTo(firstCache.getFileName().toString());
            assertThat(firstCache.getFileName().toString()).endsWith("_1.png");
            assertThat(secondCache.getFileName().toString()).endsWith("_1.png");
        } finally {
            // Source PDFs/dirs live under the system temp root (not the @TempDir), so clean them up.
            deleteQuietly(firstSource.resolve(sharedFilename));
            deleteQuietly(secondSource.resolve(sharedFilename));
            deleteQuietly(firstSource);
            deleteQuietly(secondSource);
        }
    }

    @Test
    @DisplayName("Removes every source-scoped preview page image when clearing by source PDF")
    void shouldRemoveAllPreviewPages_whenClearingBySource() throws IOException {
        allowedTempDir = createApplicationTempDirectory("nio-cache-flush-");
        assumeTrue(PathValidationUtils.isInApplicationTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside a CARLOS-owned temp directory");
        Path cacheDir = getDocumentCacheDirectory();
        Files.createDirectories(cacheDir);
        Path sourcePdf = allowedTempDir.resolve("flush-me.pdf");
        createSinglePagePdf(sourcePdf);

        Path pageOne = nioFileManager.createCacheVersion2(loggedInInfo, allowedTempDir.toString(), sourcePdf.getFileName().toString(), 1);
        assertThat(pageOne).isNotNull().exists();
        // A second page image shares the same source-scoped prefix (simulate a multi-page preview),
        // and a different source's cache page must be left untouched.
        String scopedBase = pageOne.getFileName().toString().replaceFirst("_1\\.png$", "");
        Path pageTwo = Files.createFile(cacheDir.resolve(scopedBase + "_2.png"));
        Path otherSourcePage = Files.createFile(cacheDir.resolve("other-source_0123456789abcdef_1.png"));

        try {
            int removed = nioFileManager.removeCacheVersions(loggedInInfo, 
                    allowedTempDir.toString(), sourcePdf.getFileName().toString());

            assertThat(removed).as("both pages of this source removed").isEqualTo(2);
            assertThat(Files.exists(pageOne)).as("page 1 removed").isFalse();
            assertThat(Files.exists(pageTwo)).as("page 2 removed").isFalse();
            assertThat(Files.exists(otherSourcePage)).as("a different source's cache page is untouched").isTrue();
        } finally {
            Files.deleteIfExists(pageOne);
            Files.deleteIfExists(pageTwo);
            Files.deleteIfExists(otherSourcePage);
            Files.deleteIfExists(sourcePdf);
        }
    }

    @Test
    @DisplayName("Clears preview pages without _edoc so the fax-only cancel/flush flow is not broken")
    void shouldRemovePreviewPages_withoutEdocPrivilege() throws IOException {
        allowedTempDir = createApplicationTempDirectory("nio-cache-noedoc-");
        assumeTrue(PathValidationUtils.isInApplicationTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside a CARLOS-owned temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path sourcePdf = allowedTempDir.resolve("cancel-me.pdf");
        createSinglePagePdf(sourcePdf);
        // Seed the cache while _edoc is granted (createCacheVersion2 requires it).
        Path pageOne = nioFileManager.createCacheVersion2(loggedInInfo, allowedTempDir.toString(), sourcePdf.getFileName().toString(), 1);
        assertThat(pageOne).isNotNull().exists();

        // Now simulate a user with _fax READ but not _edoc READ (the fax-cancel/flush caller): removal
        // must still succeed rather than throwing before the temp artifact can be cleaned up.
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), anyString(), eq(""))).thenReturn(false);
        try {
            int removed = nioFileManager.removeCacheVersions(loggedInInfo, allowedTempDir.toString(), sourcePdf.getFileName().toString());

            assertThat(removed).as("preview page removed without _edoc").isEqualTo(1);
            assertThat(Files.exists(pageOne)).isFalse();
        } finally {
            Files.deleteIfExists(pageOne);
            Files.deleteIfExists(sourcePdf);
        }
    }

    @Test
    @DisplayName("Clears preview pages when flush reaches the source dir through a symlink (canonical cache key)")
    void shouldRemovePreviewPages_whenSourceDirReachedViaSymlink() throws IOException {
        allowedTempDir = createApplicationTempDirectory("nio-cache-symlink-");
        assumeTrue(PathValidationUtils.isInApplicationTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside a CARLOS-owned temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path sourcePdf = allowedTempDir.resolve("symlinked.pdf");
        createSinglePagePdf(sourcePdf);

        // A symlink whose target is the real (canonical) source dir models a symlinked java.io.tmpdir
        // (e.g. macOS /tmp -> /private/tmp). The write side keys the page-image cache off the canonical
        // dir (EDocUtil.resolvePath), so the remove side must canonicalize too or removeCacheVersions
        // computes a non-matching prefix and silently clears nothing.
        Path symlinkDir;
        try {
            symlinkDir = Files.createSymbolicLink(
                    allowedTempDir.resolveSibling(allowedTempDir.getFileName() + "-link"), allowedTempDir);
        } catch (IOException | UnsupportedOperationException symlinkUnsupported) {
            assumeTrue(false, "filesystem does not support symbolic links");
            return;
        }

        // Seed the cache via the real (canonical) dir path — the write side.
        Path pageOne = nioFileManager.createCacheVersion2(loggedInInfo, allowedTempDir.toString(), sourcePdf.getFileName().toString(), 1);
        assertThat(pageOne).isNotNull().exists();
        try {
            // Flush via the symlinked dir path — the remove side must resolve to the same canonical
            // source and clear the page image.
            int removed = nioFileManager.removeCacheVersions(loggedInInfo, symlinkDir.toString(), sourcePdf.getFileName().toString());

            assertThat(removed).as("preview page removed even when the source dir is reached via a symlink").isEqualTo(1);
            assertThat(Files.exists(pageOne)).isFalse();
        } finally {
            Files.deleteIfExists(pageOne);
            Files.deleteIfExists(sourcePdf);
            Files.deleteIfExists(symlinkDir);
        }
    }

    @Test
    @DisplayName("Refuses to clear preview cache for a source outside the allowed preview locations")
    void shouldRejectFlush_forDisallowedSource() throws Exception {
        // A directory under the shared temp root but NOT CARLOS-owned (and not the document root) is not
        // a valid preview source. It cannot be keyed, so cleanup keyed on it must FAIL LOUDLY rather
        // than silently return 0 — a PHI flush that cannot even derive its source-scoped prefix must
        // not be reported to the caller as "nothing to remove".
        Path foreignDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "nio-foreign-flush-");
        assumeTrue(!PathValidationUtils.isInApplicationTempDirectory(foreignDir.toFile()),
                "foreign dir must not resolve inside a CARLOS-owned temp directory");
        Path cacheDir = getDocumentCacheDirectory();
        Files.createDirectories(cacheDir);

        // Seed a cache file that would match the prefix removeCacheVersions computes IF it processed
        // this foreign source. If the guard were bypassed, this file would be deleted; asserting it
        // survives proves the guard rejected the source, not merely that no files matched. The prefix
        // mirrors createCacheVersion2's naming.
        String fileName = "whatever.pdf";
        String wouldBePrefix = fileName + "_" + sourceKeyOf(foreignDir) + "_";
        Path wouldBeCache = Files.createFile(cacheDir.resolve(wouldBePrefix + "1.png"));
        try {
            assertThatThrownBy(() -> nioFileManager.removeCacheVersions(loggedInInfo, foreignDir.toString(), fileName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("source directory is not an allowed preview source");
            assertThat(Files.exists(wouldBeCache)).as("a disallowed source's would-be cache is left intact").isTrue();
        } finally {
            Files.deleteIfExists(wouldBeCache);
            deleteQuietly(foreignDir);
        }
    }

    /**
     * Reproduces NioFileManagerImpl's source-directory cache key: the first 8 bytes of the SHA-256 of
     * the normalized absolute directory path, as 16 lowercase hex characters.
     */
    private static String sourceKeyOf(Path directory) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(directory.normalize().toAbsolutePath().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder(16);
        for (int i = 0; i < 8; i++) {
            key.append(String.format("%02x", hash[i] & 0xff));
        }
        return key.toString();
    }

    @Test
    @DisplayName("Rejects preview sources in the shared temp root that are not CARLOS-owned")
    void shouldReturnNull_whenSourceIsInSharedTempButNotApplicationOwned() throws IOException {
        // A directory directly under java.io.tmpdir (not under carlos-temp) is inside the broad
        // allowed temp root but is NOT a CARLOS-owned preview area — an unrelated file another
        // process could leave there must not be renderable via the fax-preview path.
        Path foreignDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "nio-foreign-temp-");
        assumeTrue(PathValidationUtils.isInAllowedTempDirectory(foreignDir.toFile())
                        && !PathValidationUtils.isInApplicationTempDirectory(foreignDir.toFile()),
                "foreign dir must be in the shared temp root but not CARLOS-owned");
        Files.createDirectories(getDocumentCacheDirectory());
        Path foreignPdf = foreignDir.resolve("foreign.pdf");
        createSinglePagePdf(foreignPdf);

        try {
            Path cacheVersion = nioFileManager.createCacheVersion2(loggedInInfo, foreignDir.toString(), "foreign.pdf", 1);
            assertThat(cacheVersion).isNull();
        } finally {
            deleteQuietly(foreignPdf);
            deleteQuietly(foreignDir);
        }
    }

    @Test
    @DisplayName("Denies the preview cache to callers without _edoc READ using the paren-form message")
    void shouldThrowSecurityException_whenEdocReadPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.READ), eq(""))).thenReturn(false);

        assertThatThrownBy(() -> nioFileManager.hasCacheVersion2(loggedInInfo, "fax-preview.pdf", 1))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_edoc)");
    }

    @Test
    @DisplayName("Leaves no partial temp files behind after rendering a preview page")
    void shouldLeaveNoPartialFiles_afterCacheRender() throws IOException {
        allowedTempDir = createApplicationTempDirectory("nio-cache-atomic-");
        assumeTrue(PathValidationUtils.isInApplicationTempDirectory(allowedTempDir.toFile()),
                "test temp directory must resolve inside a CARLOS-owned temp directory");
        Files.createDirectories(getDocumentCacheDirectory());
        Path sourcePdf = allowedTempDir.resolve("fax-preview-unique.pdf");
        createSinglePagePdf(sourcePdf);

        Path cacheVersion = null;
        try {
            cacheVersion = nioFileManager.createCacheVersion2(loggedInInfo, allowedTempDir.toString(), sourcePdf.getFileName().toString(), 1);

            assertThat(cacheVersion).isNotNull().exists();
            // The atomic write path renders to "<name>_....png.tmp" and moves into place; a leftover
            // .tmp would mean the move or its cleanup regressed.
            try (var cacheEntries = Files.list(getDocumentCacheDirectory())) {
                assertThat(cacheEntries.filter(entry -> entry.getFileName().toString().endsWith(".tmp")))
                        .isEmpty();
            }
        } finally {
            if (cacheVersion != null) {
                Files.deleteIfExists(cacheVersion);
            }
        }
    }

    @Test
    @DisplayName("Treats a lost cache-promotion race as the cache hit it is")
    void shouldTreatLostPromotionRace_asExistingCacheHit(@TempDir Path raceDir) throws IOException {
        // Two concurrent misses can render the same page; the loser's atomic move finds the
        // winner's finished file already in place. That is success — the entry exists — never a
        // failure that surfaces as a broken preview. (POSIX/NTFS renames replace silently; the
        // FileAlreadyExistsException arm covers filesystems whose atomic move refuses.)
        Path partial = raceDir.resolve("page_1.png.tmp");
        Files.write(partial, new byte[] {9, 9, 9, 9});
        Path winner = raceDir.resolve("page_1.png");
        Files.write(winner, new byte[] {1, 2, 3});

        // Must not throw: in production both writers rendered the SAME page, so either file is a
        // valid cache entry. POSIX/NTFS renames replace (destination becomes the partial's 4
        // bytes); a refusing filesystem keeps the winner's 3. Deleted-or-throwing is the bug.
        NioFileManagerImpl.promotePartialIntoPlace(partial, winner);

        assertThat(winner).exists();
        assertThat(Files.size(winner)).isIn(3L, 4L);
    }

    @Test
    @DisplayName("Uniquifies the promoted document name instead of overwriting an existing document")
    void shouldUniquifyPromotedDocument_whenBasenameCollides() throws Exception {
        Path firstSource = createApplicationTempDirectory("nio-promote-collide-a-");
        Path secondSource = createApplicationTempDirectory("nio-promote-collide-b-");
        assumeTrue(PathValidationUtils.isInApplicationTempDirectory(firstSource.toFile())
                        && PathValidationUtils.isInApplicationTempDirectory(secondSource.toFile()),
                "test temp directories must resolve inside a CARLOS-owned temp directory");
        // Point DOCUMENT_DIR at an isolated destination: getDocumentDirectory() prefers it over the
        // <BASE_DOCUMENT_DIR>/document fallback, and this test must never write to a real store.
        String originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        Path documentDir = tempDir.resolve("document");
        Files.createDirectories(documentDir);
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        String sharedFilename = "promoted-collision.pdf";
        try {
            Files.writeString(firstSource.resolve(sharedFilename), "first document body");
            Files.writeString(secondSource.resolve(sharedFilename), "second document body");

            Path firstPromoted = nioFileManager.promoteApplicationTempFile(firstSource.resolve(sharedFilename));
            Path secondPromoted = nioFileManager.promoteApplicationTempFile(secondSource.resolve(sharedFilename));

            assertThat(firstPromoted).isNotNull();
            assertThat(secondPromoted).isNotNull();
            // DOCUMENT_DIR filenames are referenced by persisted records: the second promotion must
            // land under a fresh name, leaving the first document's content untouched.
            assertThat(secondPromoted).isNotEqualTo(firstPromoted);
            assertThat(Files.readString(firstPromoted)).isEqualTo("first document body");
            assertThat(Files.readString(secondPromoted)).isEqualTo("second document body");
        } finally {
            deleteQuietly(firstSource.resolve(sharedFilename));
            deleteQuietly(secondSource.resolve(sharedFilename));
            deleteQuietly(firstSource);
            deleteQuietly(secondSource);
            if (originalDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
            }
        }
    }

    private static Path createApplicationTempDirectory(String prefix) throws IOException {
        Path applicationParent = Files.createDirectories(
                Path.of(System.getProperty("java.io.tmpdir"), PathValidationUtils.APPLICATION_TEMP_ROOT_NAME));
        return Files.createTempDirectory(applicationParent, prefix);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort test cleanup
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
