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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.ApplicationTempPurgeJob.PurgeOutcome;

/**
 * Unit tests for {@link ApplicationTempPurgeJob}'s Timer-free sweep logic
 * ({@link ApplicationTempPurgeJob#purgeExpiredEntries(Path, Instant)} and
 * {@link ApplicationTempPurgeJob#purgeExpiredCacheImages(Path, Instant)}). Exercises the static
 * sweep methods directly against real filesystem fixtures so no Spring context, {@code Timer}, or
 * {@code NioFileManagerImpl} bean is required.
 *
 * @since 2026-07-22
 */
@Tag("unit")
@Tag("fast")
@Tag("manager")
class ApplicationTempPurgeJobUnitTest {

    /** Directory under test, standing in for {@code <java.io.tmpdir>/carlos-temp}. */
    @TempDir
    Path tempRoot;

    /** Separate directory so a symlink's target lives outside the swept root. */
    @TempDir
    Path externalDir;

    private static void setLastModified(Path path, Instant instant) throws IOException {
        Files.setLastModifiedTime(path, FileTime.from(instant));
    }

    @Test
    @DisplayName("Removes expired file and expired tempPDF* directory; retains fresh file and symlink; returns accurate counts")
    void shouldRemoveExpiredEntries_whenOlderThanCutoff() throws IOException {
        Instant now = Instant.now();
        Instant old = now.minus(48, ChronoUnit.HOURS);
        Instant fresh = now.minus(1, ChronoUnit.HOURS);
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);

        Path oldFile = Files.createFile(tempRoot.resolve("old-render.pdf"));
        setLastModified(oldFile, old);

        Path freshFile = Files.createFile(tempRoot.resolve("fresh-render.pdf"));
        setLastModified(freshFile, fresh);

        Path oldTempPdfDir = Files.createDirectory(tempRoot.resolve("tempPDF1700000000000"));
        Path nestedFile = Files.createFile(oldTempPdfDir.resolve("attached.pdf"));
        setLastModified(nestedFile, old);
        setLastModified(oldTempPdfDir, old);

        Path symlinkTarget = Files.createFile(externalDir.resolve("target.pdf"));
        Path symlink = tempRoot.resolve("linked.pdf");
        try {
            Files.createSymbolicLink(symlink, symlinkTarget);
        } catch (IOException | UnsupportedOperationException symlinksUnsupported) {
            assumeTrue(false, "filesystem does not support symbolic links: " + symlinksUnsupported.getMessage());
        }

        PurgeOutcome outcome = ApplicationTempPurgeJob.purgeExpiredEntries(tempRoot, cutoff);

        assertThat(Files.exists(oldFile)).as("expired file should be removed").isFalse();
        assertThat(Files.exists(oldTempPdfDir)).as("expired tempPDF* directory should be removed recursively").isFalse();
        assertThat(Files.exists(nestedFile)).as("contents of the removed tempPDF* directory should be gone too").isFalse();

        assertThat(Files.exists(freshFile)).as("fresh file should be retained").isTrue();
        assertThat(Files.exists(symlink, LinkOption.NOFOLLOW_LINKS)).as("symlink itself should be retained, never deleted").isTrue();
        assertThat(Files.exists(symlinkTarget)).as("symlink target outside the swept root must be untouched").isTrue();

        assertThat(outcome.removed()).as("removed count: old file + old tempPDF* dir").isEqualTo(2);
        assertThat(outcome.skipped()).as("skipped count: the one symlink").isEqualTo(1);
        assertThat(outcome.failed()).as("no deletion should fail in this fixture").isZero();
    }

    @Test
    @DisplayName("Removes an expired tempDirectory* directory (createTempFile's output shape) and its nested content")
    void shouldRemoveExpiredDirectory_whenNameMatchesTempDirectoryPrefix() throws IOException {
        Instant now = Instant.now();
        Instant old = now.minus(48, ChronoUnit.HOURS);
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);

        // NioFileManagerImpl.createTempFile stages multi-patient demographic import files under
        // tempDirectory<ts> subdirectories (see ImportDemographicDataAction42Action). Pre-fix, the
        // tempPDF*-only filter never matched this prefix, so these directories were retained forever.
        Path oldTempDirectoryDir = Files.createDirectory(tempRoot.resolve("tempDirectory1700000000000"));
        Path nestedImportFile = Files.createFile(oldTempDirectoryDir.resolve("import-batch.csv"));
        setLastModified(nestedImportFile, old);
        setLastModified(oldTempDirectoryDir, old);

        PurgeOutcome outcome = ApplicationTempPurgeJob.purgeExpiredEntries(tempRoot, cutoff);

        assertThat(Files.exists(oldTempDirectoryDir))
                .as("expired tempDirectory* directory should be removed recursively")
                .isFalse();
        assertThat(Files.exists(nestedImportFile))
                .as("contents of the removed tempDirectory* directory should be gone too")
                .isFalse();
        assertThat(outcome.removed()).isEqualTo(1);
        assertThat(outcome.skipped()).isZero();
        assertThat(outcome.failed()).isZero();
    }

    @Test
    @DisplayName("Removes an expired directory of any other name and its nested content; retains a fresh one")
    void shouldRemoveExpiredDirectory_whenNameDoesNotMatchAnyKnownPrefix() throws IOException {
        Instant now = Instant.now();
        Instant old = now.minus(48, ChronoUnit.HOURS);
        Instant fresh = now.minus(1, ChronoUnit.HOURS);
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);

        // carlos-temp is exclusively CARLOS-owned, so any direct child older than the cutoff is a
        // purgeable orphan regardless of name -- not just the two known writer prefixes.
        Path oldArbitraryDir = Files.createDirectory(tempRoot.resolve("some-other-directory"));
        Path nestedStrayFile = Files.createFile(oldArbitraryDir.resolve("stray.tmp"));
        setLastModified(nestedStrayFile, old);
        setLastModified(oldArbitraryDir, old);

        Path freshArbitraryDir = Files.createDirectory(tempRoot.resolve("another-directory"));
        setLastModified(freshArbitraryDir, fresh);

        PurgeOutcome outcome = ApplicationTempPurgeJob.purgeExpiredEntries(tempRoot, cutoff);

        assertThat(Files.exists(oldArbitraryDir))
                .as("expired directory of any name should be removed recursively")
                .isFalse();
        assertThat(Files.exists(nestedStrayFile))
                .as("contents of the removed arbitrarily-named directory should be gone too")
                .isFalse();
        assertThat(Files.exists(freshArbitraryDir))
                .as("fresh directory of any name should be retained")
                .isTrue();

        assertThat(outcome.removed()).isEqualTo(1);
        assertThat(outcome.skipped()).isZero();
        assertThat(outcome.failed()).isZero();
    }

    @Test
    @DisplayName("Returns an empty outcome when the root directory does not exist yet")
    void shouldReturnEmptyOutcome_whenRootDoesNotExist() {
        Path missingRoot = tempRoot.resolve("does-not-exist");

        PurgeOutcome outcome = ApplicationTempPurgeJob.purgeExpiredEntries(missingRoot, Instant.now());

        assertThat(outcome.removed()).isZero();
        assertThat(outcome.skipped()).isZero();
        assertThat(outcome.failed()).isZero();
    }

    @Test
    @DisplayName("Removes only expired .png files from the preview cache directory, ignoring other extensions")
    void shouldRemoveExpiredPngFiles_fromPreviewCacheDirectory() throws IOException {
        Instant now = Instant.now();
        Instant old = now.minus(48, ChronoUnit.HOURS);
        Instant fresh = now.minus(1, ChronoUnit.HOURS);
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);

        Path oldPng = Files.createFile(tempRoot.resolve("stale-page.png"));
        setLastModified(oldPng, old);

        Path freshPng = Files.createFile(tempRoot.resolve("recent-page.png"));
        setLastModified(freshPng, fresh);

        // Non-PNG file, old enough to be expired if the filter were wrong: must be left alone
        // because the cache sweep only targets *.png.
        Path oldNonPng = Files.createFile(tempRoot.resolve("stale-page.pdf.tmp"));
        setLastModified(oldNonPng, old);

        PurgeOutcome outcome = ApplicationTempPurgeJob.purgeExpiredCacheImages(tempRoot, cutoff);

        assertThat(Files.exists(oldPng)).as("expired PNG should be removed").isFalse();
        assertThat(Files.exists(freshPng)).as("fresh PNG should be retained").isTrue();
        assertThat(Files.exists(oldNonPng)).as("non-PNG files are out of scope for the cache sweep").isTrue();

        assertThat(outcome.removed()).isEqualTo(1);
        assertThat(outcome.skipped()).isZero();
        assertThat(outcome.failed()).isZero();
    }

    @Test
    @DisplayName("Removes expired .png.tmp atomic-move partials from the preview cache directory")
    void shouldRemoveExpiredPngTmpPartials_fromPreviewCacheDirectory() throws IOException {
        Instant now = Instant.now();
        Instant old = now.minus(48, ChronoUnit.HOURS);
        Instant fresh = now.minus(1, ChronoUnit.HOURS);
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);

        // A crash between createCacheVersion2's createTempFile and its atomic move orphans a
        // PHI-bearing .png.tmp partial that removeCacheVersions deliberately never matches —
        // this sweep is the only cleanup path such a partial has.
        Path oldPartial = Files.createFile(tempRoot.resolve("scoped_1234.png.tmp"));
        setLastModified(oldPartial, old);

        Path freshPartial = Files.createFile(tempRoot.resolve("scoped_5678.png.tmp"));
        setLastModified(freshPartial, fresh);

        PurgeOutcome outcome = ApplicationTempPurgeJob.purgeExpiredCacheImages(tempRoot, cutoff);

        assertThat(Files.exists(oldPartial)).as("expired .png.tmp partial should be removed").isFalse();
        assertThat(Files.exists(freshPartial)).as("fresh .png.tmp partial should be retained").isTrue();

        assertThat(outcome.removed()).isEqualTo(1);
        assertThat(outcome.skipped()).isZero();
        assertThat(outcome.failed()).isZero();
    }

    @Test
    @DisplayName("Falls back to the default max age when the configured value would overflow the cutoff computation")
    void shouldFallBackToDefaultMaxAge_whenConfiguredValueOverflows() {
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.get(ApplicationTempPurgeJob.MAX_AGE_HOURS_PROPERTY_KEY))
                .thenReturn("9223372036854775807");

        try (MockedStatic<CarlosProperties> carlosPropertiesMock = mockStatic(CarlosProperties.class)) {
            carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);

            ApplicationTempPurgeJob job = new ApplicationTempPurgeJob(mock(NioFileManagerImpl.class));

            // Long.MAX_VALUE hours overflows Instant.minus, which used to kill every cycle
            // before either sweep ran; the ceiling keeps the documented default instead.
            assertThat(job.readMaxAgeHours()).isEqualTo(24L);
        }
    }

    @Test
    @DisplayName("Counts a scan failure when the swept directory cannot be listed")
    void shouldCountScanFailure_whenDirectoryUnreadable() throws IOException {
        // Root ignores POSIX permission bits, so an unreadable directory is only reproducible as
        // a non-root user (the devcontainer runs as root and skips this); the production change —
        // a scan abort counts as at least one failure so the cycle summary can never report
        // failed=0 for a cycle that listed nothing — is a single reviewed line.
        assumeTrue(!"root".equals(System.getProperty("user.name")),
                "POSIX permission bits are ignored when running as root");

        Path unreadable = tempRoot.resolve("unreadable");
        Files.createDirectory(unreadable);
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(unreadable);
        try {
            Files.setPosixFilePermissions(unreadable, EnumSet.noneOf(PosixFilePermission.class));

            PurgeOutcome outcome = ApplicationTempPurgeJob.purgeExpiredEntries(unreadable, Instant.now());

            assertThat(outcome.failed()).isGreaterThanOrEqualTo(1);
        } finally {
            Files.setPosixFilePermissions(unreadable, original);
        }
    }
}
