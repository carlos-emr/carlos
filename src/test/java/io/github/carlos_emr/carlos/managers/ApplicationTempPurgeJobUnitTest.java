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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    @DisplayName("Retains a stale directory that does not match the tempPDF* prefix")
    void shouldRetainDirectory_whenNameDoesNotMatchTempPdfPrefix() throws IOException {
        Instant now = Instant.now();
        Instant old = now.minus(48, ChronoUnit.HOURS);
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);

        Path unrelatedOldDir = Files.createDirectory(tempRoot.resolve("some-other-directory"));
        setLastModified(unrelatedOldDir, old);

        PurgeOutcome outcome = ApplicationTempPurgeJob.purgeExpiredEntries(tempRoot, cutoff);

        assertThat(Files.exists(unrelatedOldDir))
                .as("only tempPDF*-prefixed directories are purge candidates")
                .isTrue();
        assertThat(outcome.removed()).isZero();
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
}
