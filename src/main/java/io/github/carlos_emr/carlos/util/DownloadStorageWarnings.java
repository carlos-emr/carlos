/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.util;

import java.io.File;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.Logger;

/** Bounded operational diagnostics for an unavailable configured download directory. */
public final class DownloadStorageWarnings {
    private static final long INTERVAL_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
    private static final DownloadStorageWarnings DEFAULT = new DownloadStorageWarnings(
            org.apache.logging.log4j.LogManager.getLogger(DownloadStorageWarnings.class), System::nanoTime);
    private final Logger logger;
    private final LongSupplier clock;
    private boolean warned;
    private long lastWarning;

    DownloadStorageWarnings(Logger logger, LongSupplier clock) {
        this.logger = logger;
        this.clock = clock;
    }

    /** Emits at most one warning a minute across both endpoints, without paths or requested names. */
    public static void reportIfUnavailable(File directory) {
        DEFAULT.report(directory);
    }

    synchronized void report(File directory) {
        if (directory.isDirectory() && directory.canRead() && directory.canExecute()) return;
        long now = clock.getAsLong();
        if (warned && now - lastWarning < INTERVAL_NANOS) return;
        warned = true;
        lastWarning = now;
        logger.warn("The configured download directory is unavailable to the service. Check storage permissions and service filesystem restrictions.");
    }
}
