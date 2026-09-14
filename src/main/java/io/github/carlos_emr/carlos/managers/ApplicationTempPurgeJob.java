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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Spring-managed background sweeper that removes orphaned PHI-bearing temporary artifacts left
 * behind by generation/preview flows that crash, get cancelled, or otherwise skip their own cleanup.
 *
 * <p>Each cycle sweeps two locations:</p>
 * <ol>
 *   <li><b>Application temp root</b> ({@code <java.io.tmpdir>/carlos-temp}, see
 *       {@link PathValidationUtils#APPLICATION_TEMP_ROOT_NAME}) &mdash; the root
 *       {@code NioFileManagerImpl.saveTempFile} (which writes {@code tempPDF*} subdirectories) and
 *       {@code createTempFile} (which writes {@code tempDirectory*} subdirectories, used by
 *       {@code ImportDemographicDataAction42Action} to stage multi-patient demographic import files)
 *       write generated output under. This root is exclusively CARLOS-owned, so every direct child
 *       (file or directory, regardless of name) older than the configured max age is removed.</li>
 *   <li><b>Document preview cache</b> ({@code document_cache}, see
 *       {@link NioFileManagerImpl#resolveDocumentCacheDirectory()}) &mdash; the backstop for the
 *       flush-vs-writer race: a cancelled preview whose render lands after a successful
 *       {@code removeCacheVersions} flush can leave one PHI-bearing page PNG behind. Stale
 *       {@code *.png} page images and {@code *.png.tmp} atomic-move partials (orphaned when a
 *       crash lands between {@code createCacheVersion2}'s temp write and its move into place)
 *       older than the configured max age are removed.</li>
 * </ol>
 *
 * <p><strong>Scheduling:</strong> modeled on {@code FaxSchedulerJob} &mdash; a daemon {@link Timer}
 * started in {@link #initialize()} and cancelled in {@link #shutdown()}. The interval is configurable
 * via {@code carlos_temp_purge_interval_ms} (default one hour); a configured value of {@code 0}
 * disables the sweep entirely. The per-entry age threshold is configurable via
 * {@code carlos_temp_purge_max_age_hours} (default 24).</p>
 *
 * <p><strong>Startup safety:</strong> this class is component-scanned into the production Spring
 * context, so {@link #initialize()} must never throw. Property parsing failures fall back to
 * defaults (logged at WARN); a missing temp root or cache directory is treated as "nothing to sweep
 * yet" rather than an error. {@link #runCycle()} catches every {@link Throwable} category so one bad
 * cycle (a transient I/O failure, a JVM error) never kills the timer for subsequent cycles.</p>
 *
 * @see PathValidationUtils#APPLICATION_TEMP_ROOT_NAME
 * @see NioFileManagerImpl#resolveDocumentCacheDirectory()
 * @since 2026-07-22
 */
@Component
public class ApplicationTempPurgeJob {
    private static final Logger logger = MiscUtils.getLogger();

    static final String INTERVAL_MS_PROPERTY_KEY = "carlos_temp_purge_interval_ms";
    static final String MAX_AGE_HOURS_PROPERTY_KEY = "carlos_temp_purge_max_age_hours";

    private static final long DEFAULT_INTERVAL_MS = 3_600_000L; // hourly
    private static final long DEFAULT_MAX_AGE_HOURS = 24L;
    // Ten years in hours: generous for any real retention need, small enough that the
    // Instant.minus(hours) cutoff computation can never overflow.
    private static final long MAX_AGE_HOURS_CEILING = 24L * 365 * 10;
    // First run happens shortly after Spring finishes wiring, matching FaxSchedulerJob's pattern of
    // not sweeping synchronously from @PostConstruct.
    private static final long INITIAL_DELAY_MS = 3000L;

    private static final String CACHE_IMAGE_SUFFIX = ".png";
    // createCacheVersion2's atomic-move staging suffix: a crash between createTempFile and the
    // move orphans a PHI-bearing partial that removeCacheVersions deliberately never matches.
    private static final String CACHE_PARTIAL_SUFFIX = CACHE_IMAGE_SUFFIX + ".tmp";

    private final NioFileManagerImpl nioFileManagerImpl;

    private Timer timer;
    private TimerTask timerTask;

    /**
     * Creates the purge job with the {@link NioFileManagerImpl} bean used to resolve the preview
     * cache directory. Depends on the concrete implementation type (rather than the
     * {@link NioFileManager} interface) because {@link NioFileManagerImpl#resolveDocumentCacheDirectory()}
     * is intentionally package-private: it is the un-gated cache-directory resolver behind the
     * privilege-checked {@link NioFileManagerImpl#getDocumentCacheDirectory}, and this background sweep
     * has no per-request {@link io.github.carlos_emr.carlos.utility.LoggedInInfo} to authorize against.
     * Widening that method to the public interface would expose it to every caller in the codebase;
     * same-package access keeps the un-gated resolver reachable only from trusted, same-package code.
     *
     * @param nioFileManagerImpl NioFileManagerImpl used to resolve the preview cache directory
     */
    @Autowired
    public ApplicationTempPurgeJob(NioFileManagerImpl nioFileManagerImpl) {
        this.nioFileManagerImpl = nioFileManagerImpl;
    }

    /**
     * Starts the daemon timer after bean initialization, unless purging is disabled via
     * {@code carlos_temp_purge_interval_ms=0}. Never throws: a malformed interval property falls back
     * to the default rather than failing application startup.
     */
    @PostConstruct
    public void initialize() {
        long intervalMs;
        try {
            intervalMs = readIntervalMs();
        } catch (RuntimeException e) {
            logger.warn("Could not read {}; ApplicationTempPurgeJob will use the default interval", INTERVAL_MS_PROPERTY_KEY, e);
            intervalMs = DEFAULT_INTERVAL_MS;
        }

        if (intervalMs == 0) {
            logger.info("ApplicationTempPurgeJob disabled ({}=0)", INTERVAL_MS_PROPERTY_KEY);
            return;
        }

        startTask(intervalMs);
    }

    private synchronized void startTask(long intervalMs) {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                runCycle();
            }
        };
        timer = new Timer("ApplicationTempPurgeJob Timer", true);
        timer.schedule(timerTask, INITIAL_DELAY_MS, intervalMs);
        logger.info("ApplicationTempPurgeJob scheduled: interval={} ms", intervalMs);
    }

    /** Package-private test hook: whether the daemon timer is currently scheduled. */
    synchronized boolean isTimerScheduled() {
        return timer != null;
    }

    /**
     * Cancels the daemon timer during bean shutdown.
     */
    @PreDestroy
    synchronized void shutdown() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * Runs one sweep cycle over both purge targets. Catches every {@link Throwable} category
     * (checked exceptions, runtime exceptions, and JVM {@link Error}s) so a single bad cycle cannot
     * cancel the underlying {@link Timer} and silently stop all future purges &mdash; unlike a fatal
     * scheduler (e.g. fax polling), a missed purge cycle degrades gracefully (the next cycle catches up)
     * and must not escalate to disabling the job.
     */
    private void runCycle() {
        try {
            long maxAgeHours;
            try {
                maxAgeHours = readMaxAgeHours();
            } catch (RuntimeException e) {
                logger.warn("Could not read {}; using default max age", MAX_AGE_HOURS_PROPERTY_KEY, e);
                maxAgeHours = DEFAULT_MAX_AGE_HOURS;
            }
            Instant cutoff = Instant.now().minus(maxAgeHours, ChronoUnit.HOURS);

            PurgeOutcome tempRootOutcome = sweepApplicationTempRoot(cutoff);
            PurgeOutcome cacheOutcome = sweepPreviewCache(cutoff);

            int removed = tempRootOutcome.removed() + cacheOutcome.removed();
            int skipped = tempRootOutcome.skipped() + cacheOutcome.skipped();
            int failed = tempRootOutcome.failed() + cacheOutcome.failed();

            logger.info("ApplicationTempPurgeJob cycle complete: removed={} skipped={} failed={}", removed, skipped, failed);
            if (failed > 0) {
                logger.warn("ApplicationTempPurgeJob: {} expired temp entr{} could not be removed this cycle",
                        failed, failed == 1 ? "y" : "ies");
            }
        } catch (OutOfMemoryError e) {
            logger.error("ApplicationTempPurgeJob cycle aborted due to out of memory", e);
        } catch (Error e) {
            logger.error("ApplicationTempPurgeJob cycle aborted due to a JVM error", e);
        } catch (RuntimeException e) {
            logger.error("ApplicationTempPurgeJob cycle failed with a runtime exception", e);
        } catch (Exception e) {
            logger.error("ApplicationTempPurgeJob cycle failed with an unexpected checked exception", e);
        }
    }

    private PurgeOutcome sweepApplicationTempRoot(Instant cutoff) {
        Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir"))
                .resolve(PathValidationUtils.APPLICATION_TEMP_ROOT_NAME);
        if (!Files.isDirectory(tempRoot)) {
            // Nothing has ever been written yet (fresh install) or the temp dir was already
            // cleaned up externally; this is a normal no-op, not an error.
            logger.debug("Application temp root does not exist yet, nothing to purge");
            return PurgeOutcome.EMPTY;
        }
        // Refuse a symlinked ROOT. purgeExpiredEntries already skips symlinked children, but this
        // job resolves the path itself rather than going through NioFileManagerImpl, so nothing had
        // ever checked the root — and java.io.tmpdir is world-writable, so any local account can
        // pre-create carlos-temp as a link. newDirectoryStream follows it, and every expired child of
        // the TARGET would then be handed to deleteEntry. Deleting is not recoverable; refusing to
        // sweep merely leaves temp files for an operator to notice.
        if (Files.isSymbolicLink(tempRoot)) {
            logger.error("Application temp root is a symbolic link; refusing to purge through it: {}",
                    LogSafe.sanitize(String.valueOf(tempRoot)));
            return PurgeOutcome.EMPTY;
        }
        return purgeExpiredEntries(tempRoot, cutoff);
    }

    private PurgeOutcome sweepPreviewCache(Instant cutoff) {
        Path cacheDir;
        try {
            cacheDir = nioFileManagerImpl.resolveDocumentCacheDirectory();
        } catch (RuntimeException e) {
            // Defensive: resolveDocumentCacheDirectory() derives its path from the deployment's
            // ServletContext and configured document root; if either is unavailable (e.g. very early
            // in a non-web bootstrap) this must degrade to "nothing to sweep" rather than derail the
            // temp-root sweep that already ran above.
            logger.warn("Could not resolve preview cache directory for purge", e);
            // Count as a failed target (not EMPTY): the entire preview-cache sweep — the flush-race PHI
            // backstop — was skipped because of an error, so the cycle summary must not read failed=0
            // (healthy) when a whole target could not be swept.
            return new PurgeOutcome(0, 0, 1);
        }
        if (cacheDir == null || !Files.isDirectory(cacheDir)) {
            logger.debug("Preview cache directory does not exist yet, nothing to purge");
            return PurgeOutcome.EMPTY;
        }
        return purgeExpiredCacheImages(cacheDir, cutoff);
    }

    // ========================================================================
    // Package-private, Timer-free sweep logic (unit-testable directly)
    // ========================================================================

    /**
     * Sweeps the direct children of {@code root} &mdash; the CARLOS-owned application temp root
     * &mdash; deleting every expired file or directory whose last-modified time is strictly before
     * {@code cutoff}, regardless of name. This covers both {@code NioFileManagerImpl.saveTempFile}'s
     * {@code tempPDF*} output and {@code createTempFile}'s {@code tempDirectory*} output, plus any
     * stray file, without gating on a name prefix: since the root is exclusively application-owned,
     * any direct child old enough to be expired is a purgeable orphan.
     *
     * <p>Symlinked children are never followed or deleted &mdash; they are counted as
     * {@link PurgeOutcome#skipped()} and logged at WARN, regardless of age, since a symlink under an
     * application-owned temp root is itself suspicious and must not be treated as an ordinary
     * expired file. Every deletion target is re-validated with
     * {@link PathValidationUtils#validateExistingPath(File, File)} against {@code root} immediately
     * before deletion (closing the check-then-use gap between listing the directory and removing the
     * entry). Never logs file contents; filenames are sanitized with {@link LogSafe}.</p>
     *
     * @param root application temp root to sweep (only its direct children are considered as
     *             deletion candidates; expired subdirectories of any name are removed recursively as
     *             a unit)
     * @param cutoff entries last modified strictly before this instant are removed
     * @return counts of entries removed, skipped (symlinks), and failed (validation/deletion errors)
     */
    static PurgeOutcome purgeExpiredEntries(Path root, Instant cutoff) {
        return sweep(root, cutoff, ApplicationTempPurgeJob::isTempRootCandidate);
    }

    /**
     * Sweeps the direct children of {@code cacheDir} for expired {@code *.png} preview-cache page
     * images and {@code *.png.tmp} atomic-move partials.
     * Same symlink/validation/logging contract as {@link #purgeExpiredEntries(Path, Instant)}.
     *
     * @param cacheDir document preview cache directory to sweep
     * @param cutoff entries last modified strictly before this instant are removed
     * @return counts of entries removed, skipped (symlinks), and failed (validation/deletion errors)
     */
    static PurgeOutcome purgeExpiredCacheImages(Path cacheDir, Instant cutoff) {
        return sweep(cacheDir, cutoff, ApplicationTempPurgeJob::isCacheImageCandidate);
    }

    private static boolean isTempRootCandidate(Path entry) {
        // carlos-temp is exclusively application-owned (see PathValidationUtils.APPLICATION_TEMP_ROOT_NAME):
        // NioFileManagerImpl.saveTempFile writes tempPDF* subdirectories and createTempFile writes
        // tempDirectory* subdirectories, both directly under this root, with no other legitimate writer.
        // Gating directories on a name prefix therefore only creates purge blind spots for other
        // CARLOS-owned output shapes; every direct child (file or directory) older than the cutoff is a
        // purgeable orphan regardless of name.
        return Files.isRegularFile(entry) || Files.isDirectory(entry);
    }

    private static boolean isCacheImageCandidate(Path entry) {
        if (!Files.isRegularFile(entry)) {
            return false;
        }
        String name = entry.getFileName().toString();
        return name.endsWith(CACHE_IMAGE_SUFFIX) || name.endsWith(CACHE_PARTIAL_SUFFIX);
    }

    private static PurgeOutcome sweep(Path root, Instant cutoff, Predicate<Path> candidateFilter) {
        if (root == null || !Files.isDirectory(root)) {
            return PurgeOutcome.EMPTY;
        }

        int removed = 0;
        int skipped = 0;
        int failed = 0;

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                String safeName = LogSafe.sanitize(entry.getFileName().toString());
                try {
                    if (Files.isSymbolicLink(entry)) {
                        logger.warn("Skipping symlink found in purge-swept directory: {}", safeName);
                        skipped++;
                        continue;
                    }
                    if (!candidateFilter.test(entry)) {
                        continue;
                    }
                    Instant lastModified = Files.getLastModifiedTime(entry).toInstant();
                    if (!lastModified.isBefore(cutoff)) {
                        continue; // not expired yet
                    }

                    File validatedTarget;
                    try {
                        validatedTarget = PathValidationUtils.validateExistingPath(entry.toFile(), root.toFile());
                    } catch (SecurityException e) {
                        logger.warn("Refusing to purge entry that failed containment validation: {}", safeName);
                        failed++;
                        continue;
                    }

                    if (deleteEntry(validatedTarget.toPath())) {
                        removed++;
                    } else {
                        // deleteEntry logged the failure with its cause at the failure site.
                        failed++;
                    }
                } catch (IOException e) {
                    failed++;
                    logger.warn("Error evaluating temp entry for purge: {}", safeName, e);
                }
            }
        } catch (IOException e) {
            // A scan that could not even list the directory must count as failed work: a cycle
            // summary of failed=0 would otherwise read as healthy for a sweep that swept nothing.
            failed++;
            logger.warn("Error scanning directory for purge: {}", LogSafe.sanitize(root.toString(), 1024), e);
        } catch (DirectoryIteratorException e) {
            // The DirectoryStream iterator wraps mid-iteration I/O errors in this
            // RuntimeException; without this arm it escaped to runCycle, skipped the remaining
            // sweeps and the cycle summary, and repeated every cycle at the same entry.
            failed++;
            logger.warn("Error iterating directory for purge: {}", LogSafe.sanitize(root.toString(), 1024), e.getCause());
        }

        return new PurgeOutcome(removed, skipped, failed);
    }

    /**
     * Deletes a single already-validated file, or an expired directory recursively (regardless of
     * name &mdash; covers both {@code tempPDF*} and {@code tempDirectory*} output shapes).
     * {@link Files#walk(Path, java.nio.file.FileVisitOption...)} does not follow symlinks by default,
     * so a symlink nested inside a purged subdirectory has its link removed but its target left
     * untouched &mdash; consistent with the top-level symlink handling in {@link #sweep}.
     */
    private static boolean deleteEntry(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(ApplicationTempPurgeJob::deleteQuietly);
                }
                return !Files.exists(path);
            }
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            // Log the cause here, at the failure site: why a PHI-bearing temp entry cannot be
            // deleted (permissions, immutable attribute, read-only mount) is the actionable part.
            logger.warn("Failed to purge expired temp entry: {}",
                    LogSafe.sanitize(path.getFileName() == null ? "" : path.getFileName().toString()), e);
            return false;
        } catch (UncheckedIOException e) {
            // Files.walk throws this (not IOException) when a nested directory becomes unopenable
            // mid-traversal; letting it escape aborted the whole sweep and turned the failed
            // subtree into a permanent purge blind spot for every entry sorted after it.
            logger.warn("Failed to purge expired temp entry: {}",
                    LogSafe.sanitize(path.getFileName() == null ? "" : path.getFileName().toString()), e.getCause());
            return false;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Failed to delete nested entry while purging temp directory: {}",
                    LogSafe.sanitize(path.getFileName() == null ? "" : path.getFileName().toString()), e);
        }
    }

    // Package-private for direct configuration-parsing tests.
    long readIntervalMs() {
        String configured = (String) CarlosProperties.getInstance().get(INTERVAL_MS_PROPERTY_KEY);
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_INTERVAL_MS;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            if (parsed < 0) {
                logger.warn("{} must not be negative, got {}. Using default: {} ms",
                        INTERVAL_MS_PROPERTY_KEY, parsed, DEFAULT_INTERVAL_MS);
                return DEFAULT_INTERVAL_MS;
            }
            return parsed; // 0 is a valid, meaningful "disabled" value
        } catch (NumberFormatException e) {
            logger.warn("{} is invalid: {}. Using default: {} ms",
                    INTERVAL_MS_PROPERTY_KEY, LogSafe.sanitize(configured), DEFAULT_INTERVAL_MS);
            return DEFAULT_INTERVAL_MS;
        }
    }

    // Package-private for direct configuration-parsing tests.
    long readMaxAgeHours() {
        String configured = (String) CarlosProperties.getInstance().get(MAX_AGE_HOURS_PROPERTY_KEY);
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_MAX_AGE_HOURS;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            if (parsed <= 0 || parsed > MAX_AGE_HOURS_CEILING) {
                // The ceiling keeps Instant.minus(hours) overflow-free: an absurd value used to
                // throw ArithmeticException in every cycle before either sweep ran, silently
                // disabling all cleanup until the property was fixed.
                logger.warn("{} must be between 1 and {}, got {}. Using default: {} hours",
                        MAX_AGE_HOURS_PROPERTY_KEY, MAX_AGE_HOURS_CEILING, parsed, DEFAULT_MAX_AGE_HOURS);
                return DEFAULT_MAX_AGE_HOURS;
            }
            return parsed;
        } catch (NumberFormatException e) {
            logger.warn("{} is invalid: {}. Using default: {} hours",
                    MAX_AGE_HOURS_PROPERTY_KEY, LogSafe.sanitize(configured), DEFAULT_MAX_AGE_HOURS);
            return DEFAULT_MAX_AGE_HOURS;
        }
    }

    /**
     * Per-cycle sweep counts for one directory. {@code removed} counts files and directories
     * actually deleted; {@code skipped} counts symlinked children left untouched; {@code failed}
     * counts entries that were expired but could not be validated or deleted.
     */
    record PurgeOutcome(int removed, int skipped, int failed) {
        static final PurgeOutcome EMPTY = new PurgeOutcome(0, 0, 0);
    }
}
