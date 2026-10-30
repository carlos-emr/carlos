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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestFonts;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet.Face;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link BundledFontFileService}: the font files it writes, verifies and restores.
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("pdf")
@DisplayName("BundledFontFileService Unit Tests")
class BundledFontFileServiceUnitTest {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    private static final List<String> FONT_RESOURCES =
            List.of(TestFonts.SANS_REGULAR, TestFonts.SANS_BOLD, TestFonts.CJK, TestFonts.SYMBOL);

    @TempDir
    Path root;

    @TempDir
    Path otherRoot;

    private final ClassLoader classes = getClass().getClassLoader();

    // ---- directory and files ----------------------------------------------------------------

    @Test
    @DisplayName("should name the directory after the hash of the font bundle, directly under the root")
    void shouldNameDirectory_fromBundleHash() throws Exception {
        BundledFontSet first = new BundledFontFileService(root, classes).fontSet();
        BundledFontSet second = new BundledFontFileService(otherRoot, classes).fontSet();

        Path directory = directoryOf(first);
        assertThat(directory.getParent()).isEqualTo(root);
        assertThat(directory.getFileName().toString()).matches("carlos-pdf-fonts-[0-9a-f]{12}");
        assertThat(directory.getFileName().toString()).isEqualTo("carlos-pdf-fonts-" + expectedHash());
        assertThat(directoryOf(second).getFileName()).isEqualTo(directory.getFileName());
        assertThat(root.resolve("carlos-temp")).doesNotExist();
    }

    @Test
    @DisplayName("should write the four fonts, byte for byte, and nothing else")
    void shouldWriteFourFonts_andNothingElse() throws Exception {
        Path directory = directoryOf(new BundledFontFileService(root, classes).fontSet());

        assertThat(names(root)).containsExactly(directory.getFileName().toString());
        assertThat(names(directory)).containsExactlyInAnyOrder(
                "DejaVuSans.ttf", "DejaVuSans-Bold.ttf", "wqy-microhei.ttc", "Symbola_hint.ttf");
        for (String resource : FONT_RESOURCES) {
            Path file = directory.resolve(resource.substring(resource.lastIndexOf('/') + 1));
            assertThat(sha256(file)).as(resource).isEqualTo(sha256(resource));
        }
    }

    @Test
    @DisplayName("should hand out a set whose faces are all available and drawn from those files")
    void shouldLoadAllFaces_fromTheWrittenFiles() throws Exception {
        BundledFontSet set = new BundledFontFileService(root, classes).fontSet();

        for (Face face : Face.values()) {
            assertThat(set.isAvailable(face)).as(face.name()).isTrue();
            assertThat(Path.of(set.fontPath(face).replace(",0", ""))).startsWith(root);
        }
        assertThat(set.fontPath(Face.CJK)).endsWith(",0");
        assertThat(set.canDraw(Face.SANS_REGULAR, 'é')).isTrue();
        assertThat(set.canDraw(Face.CJK, '中')).isTrue();
    }

    @Test
    @DisplayName("should touch no file when constructed")
    void shouldTouchNoFile_whenConstructed() throws IOException {
        new BundledFontFileService(root, classes);

        assertThat(names(root)).isEmpty();
    }

    @Test
    @DisplayName("should hand out the same set while nothing changes")
    void shouldReturnSameSet_whenNothingChanged() throws Exception {
        BundledFontFileService service = new BundledFontFileService(root, classes);

        assertThat(service.fontSet()).isSameAs(service.fontSet());
    }

    @Test
    @DisplayName("should reuse the directory after a restart without rewriting a file")
    void shouldReuseDirectory_acrossRestart() throws Exception {
        Path directory = directoryOf(new BundledFontFileService(root, classes).fontSet());
        Path regular = directory.resolve("DejaVuSans.ttf");
        Files.setLastModifiedTime(regular, java.nio.file.attribute.FileTime.fromMillis(1_000_000_000_000L));

        Path again = directoryOf(new BundledFontFileService(root, classes).fontSet());

        assertThat(again).isEqualTo(directory);
        assertThat(names(root)).hasSize(1);
        assertThat(Files.getLastModifiedTime(regular).toMillis()).isEqualTo(1_000_000_000_000L);
    }

    @Test
    @DisplayName("should write the directory and files owner-only where POSIX permissions exist")
    void shouldCreateOwnerOnlyDirectory_wherePosixSupported() throws Exception {
        assumeTrue(posix());

        Path directory = directoryOf(new BundledFontFileService(root, classes).fontSet());

        assertThat(Files.getPosixFilePermissions(directory)).isEqualTo(OWNER_ONLY);
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                assertThat(Files.getPosixFilePermissions(file)).as(file.getFileName().toString())
                        .noneMatch(permission -> permission.name().startsWith("GROUP")
                                || permission.name().startsWith("OTHERS"));
            }
        }
    }

    // ---- verification and restoring ---------------------------------------------------------

    @Test
    @DisplayName("should restore a font file deleted after first use")
    void shouldRestoreFile_deletedAfterFirstUse() throws Exception {
        BundledFontFileService service = new BundledFontFileService(root, classes);
        BundledFontSet first = service.fontSet();
        Path file = directoryOf(first).resolve("DejaVuSans.ttf");
        Files.delete(file);

        BundledFontSet second = service.fontSet();

        assertThat(second).isNotSameAs(first);
        assertThat(sha256(file)).isEqualTo(sha256(TestFonts.SANS_REGULAR));
        assertThat(service.fontSet()).isSameAs(second);
    }

    @Test
    @DisplayName("should restore a font file truncated after first use")
    void shouldRestoreFile_truncatedAfterFirstUse() throws Exception {
        BundledFontFileService service = new BundledFontFileService(root, classes);
        BundledFontSet first = service.fontSet();
        Path file = directoryOf(first).resolve("DejaVuSans-Bold.ttf");
        Files.write(file, new byte[10]);

        BundledFontSet second = service.fontSet();

        assertThat(second).isNotSameAs(first);
        assertThat(sha256(file)).isEqualTo(sha256(TestFonts.SANS_BOLD));
    }

    @Test
    @DisplayName("should restore the whole directory when it is deleted after first use")
    void shouldRecreateDirectory_whenDeletedAfterFirstUse() throws Exception {
        BundledFontFileService service = new BundledFontFileService(root, classes);
        Path directory = directoryOf(service.fontSet());
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        Files.delete(directory);

        BundledFontSet again = service.fontSet();

        assertThat(directoryOf(again)).isEqualTo(directory);
        assertThat(names(directory)).hasSize(4);
    }

    @Test
    @DisplayName("should replace a font file that was swapped for a link")
    void shouldReplaceFile_whenFontPathBecomesSymlink() throws Exception {
        BundledFontFileService service = new BundledFontFileService(root, classes);
        Path file = directoryOf(service.fontSet()).resolve("DejaVuSans.ttf");
        Path copy = TestFonts.copy(TestFonts.SANS_REGULAR, otherRoot);
        Files.delete(file);
        createSymbolicLink(file, copy);

        service.fontSet();

        assertThat(Files.isSymbolicLink(file)).isFalse();
        assertThat(Files.isRegularFile(file)).isTrue();
        assertThat(sha256(file)).isEqualTo(sha256(TestFonts.SANS_REGULAR));
    }

    @Test
    @DisplayName("should rewrite a font file whose content changed without changing its size")
    void shouldRewriteFile_whenSameSizeContentAltered() throws Exception {
        Path file = directoryOf(new BundledFontFileService(root, classes).fontSet()).resolve("DejaVuSans.ttf");
        byte[] altered = Files.readAllBytes(file);
        altered[altered.length / 2] ^= 0x55;
        Files.write(file, altered);
        assertThat(sha256(file)).isNotEqualTo(sha256(TestFonts.SANS_REGULAR));

        try (LogCapture capture = LogCapture.forLogger(BundledFontFileService.class)) {
            new BundledFontFileService(root, classes).fontSet();

            assertThat(capture.messages()).anyMatch(message -> message.contains("differed"));
            assertThat(capture.messages()).noneMatch(message -> message.contains(root.toString()));
        }
        assertThat(sha256(file)).isEqualTo(sha256(TestFonts.SANS_REGULAR));
    }

    // ---- an untrusted directory -------------------------------------------------------------

    @Test
    @DisplayName("should use a private directory and warn when the shared name is a symbolic link")
    void shouldUsePrivateDirectory_whenDirectoryIsSymlink() throws Exception {
        String name = directoryOf(new BundledFontFileService(otherRoot, classes).fontSet()).getFileName().toString();
        Path target = Files.createDirectory(root.resolve("elsewhere"));
        Path link = root.resolve(name);
        createSymbolicLink(link, target);

        try (LogCapture capture = LogCapture.forLogger(BundledFontFileService.class)) {
            BundledFontSet set = new BundledFontFileService(root, classes).fontSet();

            Path directory = directoryOf(set);
            assertThat(directory).isNotEqualTo(link);
            assertThat(directory.startsWith(target)).isFalse();
            assertThat(directory.getParent()).isEqualTo(root);
            assertThat(directory.getFileName().toString()).startsWith("carlos-pdf-fonts-");
            assertThat(Files.isSymbolicLink(link)).isTrue();
            assertThat(names(target)).isEmpty();
            assertThat(capture.messages()).anyMatch(message -> message.contains("private directory"));
            assertThat(capture.messages()).noneMatch(message -> message.contains(root.toString()));
        }
    }

    @Test
    @DisplayName("should use a private directory when the shared name is taken by a regular file")
    void shouldUsePrivateDirectory_whenNameIsARegularFile() throws Exception {
        String name = directoryOf(new BundledFontFileService(otherRoot, classes).fontSet()).getFileName().toString();
        Path squatter = Files.writeString(root.resolve(name), "not a directory");

        BundledFontSet set = new BundledFontFileService(root, classes).fontSet();

        assertThat(directoryOf(set)).isNotEqualTo(squatter);
        assertThat(Files.readString(squatter)).isEqualTo("not a directory");
        assertThat(set.isAvailable(Face.SANS_REGULAR)).isTrue();
    }

    @Test
    @DisplayName("should tighten a shared directory that it owns but others can reach, and use it")
    void shouldTightenDirectory_whenOwnedButOpenToOthers() throws Exception {
        assumeTrue(posix());
        String name = directoryOf(new BundledFontFileService(otherRoot, classes).fontSet()).getFileName().toString();
        Path wide = Files.createDirectory(root.resolve(name));
        Files.setPosixFilePermissions(wide, PosixFilePermissions.fromString("rwxrwxrwx"));

        try (LogCapture capture = LogCapture.forLogger(BundledFontFileService.class)) {
            BundledFontSet set = new BundledFontFileService(root, classes).fontSet();

            assertThat(directoryOf(set)).isEqualTo(wide);
            assertThat(Files.getPosixFilePermissions(wide)).isEqualTo(OWNER_ONLY);
            assertThat(capture.messages()).noneMatch(message -> message.contains("private directory"));
        }
    }

    // ---- concurrency ------------------------------------------------------------------------

    @Test
    @DisplayName("should write the files once when eight threads use the service first at the same moment")
    void shouldExtractOnce_underConcurrentFirstUse() throws Exception {
        BundledFontFileService service = new BundledFontFileService(root, classes);

        List<BundledFontSet> sets = concurrently(8, service::fontSet);

        assertThat(sets).hasSize(8).containsOnly(sets.get(0));
        Path directory = directoryOf(sets.get(0));
        assertThat(names(root)).containsExactly(directory.getFileName().toString());
        assertThat(names(directory)).hasSize(4).noneMatch(name -> name.endsWith(".tmp"));
        assertThat(sha256(directory.resolve("DejaVuSans.ttf"))).isEqualTo(sha256(TestFonts.SANS_REGULAR));
    }

    @Test
    @DisplayName("should leave one intact directory when several services start on the same root at once")
    void shouldLeaveOneDirectory_whenSeveralServicesStartTogether() throws Exception {
        List<BundledFontFileService> services = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            services.add(new BundledFontFileService(root, classes));
        }
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(services.size());
        try {
            List<Future<BundledFontSet>> futures = new ArrayList<>();
            for (BundledFontFileService service : services) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return service.fontSet();
                }));
            }
            go.countDown();
            List<Path> directories = new ArrayList<>();
            for (Future<BundledFontSet> future : futures) {
                directories.add(directoryOf(future.get(60, TimeUnit.SECONDS)));
            }
            assertThat(directories).containsOnly(directories.get(0));
        } finally {
            pool.shutdownNow();
        }
        assertThat(names(root)).hasSize(1);
        try (Stream<Path> files = Files.list(root.resolve(names(root).get(0)))) {
            assertThat(files.map(f -> f.getFileName().toString())).hasSize(4);
        }
    }

    // ---- failures ---------------------------------------------------------------------------

    @Test
    @DisplayName("should fail clearly, and touch no disk, when the DejaVu Sans fonts are not in the application")
    void shouldFailClearly_whenDejaVuMissing() throws Exception {
        BundledFontFileService service =
                new BundledFontFileService(root, new URLClassLoader(new URL[0], null));

        assertThatExceptionOfType(PDFGenerationException.class).isThrownBy(service::fontSet)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(root.toString()));
        assertThatExceptionOfType(PDFGenerationException.class).isThrownBy(service::fontSet);
        assertThat(names(root)).isEmpty();
    }

    @Test
    @DisplayName("should leave a fallback face out, and say so, when its font is not in the application")
    void shouldLeaveFallbackOut_whenOptionalFontMissing() throws Exception {
        ClassLoader withoutCjk = new ClassLoader(classes) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return TestFonts.CJK.equals(name) ? null : super.getResourceAsStream(name);
            }
        };

        try (LogCapture capture = LogCapture.forLogger(BundledFontFileService.class)) {
            BundledFontSet set = new BundledFontFileService(root, withoutCjk).fontSet();

            assertThat(set.isAvailable(Face.CJK)).isFalse();
            assertThat(set.isAvailable(Face.SYMBOL)).isTrue();
            assertThat(set.isAvailable(Face.SANS_BOLD)).isTrue();
            assertThat(capture.messages()).anyMatch(message -> message.contains("CJK"));
            assertThat(names(directoryOf(set))).hasSize(3);
        }
    }

    @Test
    @DisplayName("should try again on the next call after the directory could not be written")
    void shouldRetry_afterWriteFailure() throws Exception {
        Path missingParent = root.resolve("not-yet");
        BundledFontFileService service = new BundledFontFileService(missingParent, classes);

        assertThatExceptionOfType(PDFGenerationException.class).isThrownBy(service::fontSet)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(root.toString()));

        Files.createDirectory(missingParent);
        assertThat(service.fontSet().isAvailable(Face.SANS_REGULAR)).isTrue();
    }

    @Test
    @DisplayName("should reject null constructor arguments")
    void shouldRejectNull_whenConstructorArgumentMissing() {
        assertThatNullPointerException().isThrownBy(() -> new BundledFontFileService(null, classes));
        assertThatNullPointerException().isThrownBy(() -> new BundledFontFileService(root, null));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static Path directoryOf(BundledFontSet set) {
        return Path.of(set.fontPath(Face.SANS_REGULAR)).getParent();
    }

    private boolean posix() {
        return root.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static List<String> names(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static void createSymbolicLink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException e) {
            org.junit.jupiter.api.Assumptions.abort("symbolic links are not available here");
        }
    }

    private byte[] sha256(String resource) throws Exception {
        try (InputStream in = classes.getResourceAsStream(resource)) {
            return MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
        }
    }

    private static byte[] sha256(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }

    /** The hash the directory name is built from: SHA-256 over the four fonts' bytes, first 12 hex digits. */
    private String expectedHash() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String resource : FONT_RESOURCES) {
            try (InputStream in = classes.getResourceAsStream(resource)) {
                digest.update(in.readAllBytes());
            }
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, 12);
    }

    private static <T> List<T> concurrently(int threads, java.util.concurrent.Callable<T> call) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
