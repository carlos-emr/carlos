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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Bounded metadata discovery and exclusive owned-attempt retirement, independent of clinical engine availability. */
@Service
public class CdsExportRetentionService {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(CdsExportRetentionService.class);
    private final CdsExportLedgerService ledger;
    private final CdsExportStagingFactory stores;
    private final CdsExportConfigurationService configuration;
    private ScheduledExecutorService worker;
    private boolean closed;
    private volatile String after;

    public CdsExportRetentionService(
            CdsExportLedgerService ledger,
            CdsExportStagingFactory stores,
            CdsExportConfigurationService configuration) {
        this.ledger = Objects.requireNonNull(ledger);
        this.stores = Objects.requireNonNull(stores);
        this.configuration = Objects.requireNonNull(configuration);
    }

    @EventListener
    public synchronized void start(ContextRefreshedEvent event) {
        try {
            startWorker();
        } catch (IllegalStateException malformed) {
            if (!"CDS_EXPORT_CONFIGURATION_INVALID".equals(malformed.getMessage())) throw malformed;
            LOG.error(
                    "CDS_EXPORT_RETENTION_STARTUP_DISABLED code=CDS_EXPORT_CONFIGURATION_INVALID"
                            + " {}",
                    io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(malformed));
        }
    }

    private void startWorker() {
        if (closed || worker != null || !configuration.workerEnabled()) return;
        worker =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            var thread = new Thread(task, "cds-export-retention");
                            thread.setDaemon(true);
                            return thread;
                        });
        worker.scheduleWithFixedDelay(this::scheduled, 60, 60, TimeUnit.SECONDS);
    }

    private void scheduled() {
        try {
            sweep();
        } catch (RuntimeException failed) {
            LOG.error(
                    "CDS export retention stopped: CDS_EXPORT_CLEANUP_DISCOVERY_FAILED {}",
                    io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(failed));
        } catch (Error fatal) {
            LOG.error("CDS export retention stopped: CDS_EXPORT_CLEANUP_FATAL");
            throw fatal;
        }
    }

    /** At most 16 runs and 64 attempts per run per sweep. Older epochs precede a current retry. */
    public void sweep() {
        var runs = after == null ? ledger.expiredArtifacts(16) : ledger.expiredArtifacts(after, 16);
        // Rotate bounded pages even when a whole page contains unreadable jobs. A
        // corrupt request cannot indefinitely starve later expired runs.
        after = runs.size() == 16 ? runs.getLast() : null;
        RuntimeException failure = null;
        for (var run : runs) {
            java.util.List<Long> epochs;
            try {
                epochs = ledger.artifactEpochs(run, 0, 64);
            } catch (RuntimeException failed) {
                failure = remember(failure, failed, run);
                continue;
            }
            for (long epoch : epochs) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    retire(run, epoch);
                } catch (RuntimeException failed) {
                    failure = remember(failure, failed, run);
                }
            }
        }
        if (failure != null) throw failure;
    }

    private RuntimeException remember(RuntimeException prior, RuntimeException failed, String run) {
        LOG.error(
                "CDS_EXPORT_CLEANUP_RUN_FAILED run={} {}",
                run,
                io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(failed));
        if (prior == null) return failed;
        if (prior != failed) prior.addSuppressed(failed);
        return prior;
    }

    // Preserve a fatal primary error, but surface unknown metadata retirement outcomes.
    @SuppressWarnings("java:S1143")
    private void retire(String run, long epoch) {
        var claim = ledger.beginCleanup(run, epoch);
        if (claim == null) return;
        boolean complete = false;
        Throwable primary = null;
        try {
            long started = System.nanoTime();
            var last = new java.util.concurrent.atomic.AtomicLong(started - 1_000_000_000L);
            Runnable checkpoint =
                    () -> {
                        if (Thread.currentThread().isInterrupted()
                                || System.nanoTime() - started > TimeUnit.MINUTES.toNanos(15))
                            throw new CancellationException("CDS_EXPORT_CLEANUP_STOPPED");
                        long now = System.nanoTime(), prior = last.get();
                        if (now - prior >= 1_000_000_000L && last.compareAndSet(prior, now))
                            ledger.cleanupCheckpoint(claim);
                    };
            // Version one could not create a bound artifact store; there is no directory to
            // erase.
            if (claim.document().protocol() == 2)
                stores.purgeAttempt(run, epoch, claim.document().storage(), checkpoint);
            ledger.cleanupCheckpoint(claim);
            complete = true;
        } catch (IOException | RuntimeException failed) {
            primary = failed;
            LOG.error(
                    "CDS export retention did not finish for {}: CDS_EXPORT_CLEANUP_INCOMPLETE {}",
                    run,
                    io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(failed));
        } catch (Error fatal) {
            primary = fatal;
            throw fatal;
        } finally {
            try {
                ledger.finishCleanup(claim, complete);
            } catch (RuntimeException failed) {
                if (primary instanceof Error) primary.addSuppressed(failed);
                else {
                    if (primary != null) failed.addSuppressed(primary);
                    throw failed;
                }
            }
        }
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        if (worker != null) worker.shutdownNow();
    }
}
