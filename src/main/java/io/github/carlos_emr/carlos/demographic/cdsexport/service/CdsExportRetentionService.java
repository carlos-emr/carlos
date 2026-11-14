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

/**
 * Bounded metadata discovery and exclusive owned-attempt retirement, independent of clinical
 * engine availability. A stopped attempt whose finished charts are kept for a resumed attempt
 * ({@code CdsExportLedgerService} {@code KEEPING}) is first trimmed: every member its
 * finished-chart checkpoint does not pin is erased, so only the encrypted charts and their
 * checkpoint stay until the job is resumed, cancelled or seven days pass; then it is erased whole
 * like any other attempt.
 */
@Service
public class CdsExportRetentionService {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(CdsExportRetentionService.class);
    private final CdsExportLedgerService ledger;
    private final CdsExportStagingFactory stores;
    private final CdsExportConfigurationService configuration;
    /** The monotonic clock each cleanup's window is measured with; a test supplies its own. */
    private final java.util.function.LongSupplier clock;
    private ScheduledExecutorService worker;
    private boolean closed;
    private volatile String after;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportRetentionService(
            CdsExportLedgerService ledger,
            CdsExportStagingFactory stores,
            CdsExportConfigurationService configuration) {
        this(ledger, stores, configuration, System::nanoTime);
    }

    CdsExportRetentionService(
            CdsExportLedgerService ledger,
            CdsExportStagingFactory stores,
            CdsExportConfigurationService configuration,
            java.util.function.LongSupplier clock) {
        this.ledger = Objects.requireNonNull(ledger);
        this.stores = Objects.requireNonNull(stores);
        this.configuration = Objects.requireNonNull(configuration);
        this.clock = Objects.requireNonNull(clock);
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

    /**
     * At most 16 kept attempts trimmed, and 16 runs and 64 attempts per run erased, per sweep.
     * Older epochs precede a current retry.
     */
    public void sweep() {
        trimKept();
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

    /** Trims up to 16 kept attempts; one that cannot be trimmed now is tried again next sweep. */
    private void trimKept() {
        java.util.List<CdsExportLedgerService.Untrimmed> kept;
        try {
            kept = ledger.untrimmed(16);
        } catch (RuntimeException failed) {
            LOG.error(
                    "CDS_EXPORT_CHECKPOINT_TRIM_DISCOVERY_FAILED {}",
                    io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(failed));
            return;
        }
        for (var attempt : kept) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                trim(attempt.runId(), attempt.epoch());
            } catch (RuntimeException failed) {
                LOG.error(
                        "CDS_EXPORT_CHECKPOINT_TRIM_FAILED run={} {}",
                        attempt.runId(),
                        io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(failed));
            }
        }
    }

    /**
     * Erases every member of one kept attempt its checkpoint does not pin. The checkpoint is read
     * only from the pointer the ledger recorded; a checkpoint that cannot be trusted is rejected
     * and the attempt erased whole by a later sweep, since nothing of it could be adopted.
     */
    // Preserve a fatal primary error, but surface unknown metadata outcomes.
    @SuppressWarnings("java:S1143")
    private void trim(String run, long epoch) {
        var claim = ledger.beginTrim(run, epoch);
        if (claim == null) return;
        var outcome = CdsExportLedgerService.TrimOutcome.RETRY;
        Throwable primary = null;
        try {
            Runnable checkpoint = leaseCheckpoint(() -> ledger.trimCheckpoint(claim));
            var store =
                    stores.openExisting(run, epoch, claim.document().storage(), checkpoint);
            var chain =
                    CdsExportChartCheckpointReader.open(
                            store, run, epoch, claim.pointer(), checkpoint);
            var pinned = new PinnedMembers();
            for (var member : chain.members()) pinned.add(member.memberId());
            try (var cursor = chain.cursor()) {
                CdsExportChartCheckpointReader.Entry entry;
                while ((entry = cursor.next()) != null) {
                    if (!entry.chartEntry()) continue;
                    var chart =
                            CdsExportClinicalPackageService.checkpointChart(
                                    entry.chart(), entry.patient());
                    for (var member : CdsExportClinicalPackageService.chartMembers(chart))
                        pinned.add(member.memberId());
                }
            }
            pinned.seal();
            long erased =
                    stores.trimAttempt(
                            run, epoch, claim.document().storage(), pinned::contains, checkpoint);
            outcome = CdsExportLedgerService.TrimOutcome.TRIMMED;
            // Counts only.
            LOG.info(
                    "CDS_EXPORT_CHECKPOINT_TRIMMED run={} epoch={} charts={} erased={}",
                    run,
                    epoch,
                    chain.charts(),
                    erased);
        } catch (IOException failed) {
            primary = failed;
            if (CdsExportChartCheckpointReader.untrusted(failed))
                outcome = CdsExportLedgerService.TrimOutcome.REJECTED;
            LOG.error(
                    "CDS_EXPORT_CHECKPOINT_TRIM_INCOMPLETE run={} epoch={} outcome={} {}",
                    run,
                    epoch,
                    outcome,
                    io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(failed));
        } catch (RuntimeException failed) {
            primary = failed;
            LOG.error(
                    "CDS_EXPORT_CHECKPOINT_TRIM_INCOMPLETE run={} epoch={} {}",
                    run,
                    epoch,
                    io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(failed));
        } catch (Error fatal) {
            primary = fatal;
            throw fatal;
        } finally {
            try {
                ledger.finishTrim(claim, outcome);
            } catch (RuntimeException failed) {
                if (primary instanceof Error) primary.addSuppressed(failed);
                else {
                    if (primary != null) failed.addSuppressed(primary);
                    throw failed;
                }
            }
        }
    }

    /**
     * The members a checkpoint pins, as the first 64 bits of each member's random identifier,
     * sorted: eight bytes a member. Two identifiers that share those bits only keep an unpinned
     * member until the attempt is erased; they never erase a pinned one.
     */
    private static final class PinnedMembers {
        private long[] values = new long[64];
        private int size;
        private boolean sealed;

        void add(String memberId) {
            if (sealed) throw new IllegalStateException("CDS_EXPORT_CHECKPOINT_TRIM_INVALID");
            if (size == values.length)
                values =
                        java.util.Arrays.copyOf(
                                values, Math.toIntExact(Math.min(Integer.MAX_VALUE - 8L, 2L * size)));
            values[size++] = prefix(memberId);
        }

        void seal() {
            java.util.Arrays.sort(values, 0, size);
            sealed = true;
        }

        boolean contains(String memberId) {
            if (!sealed) throw new IllegalStateException("CDS_EXPORT_CHECKPOINT_TRIM_INVALID");
            return java.util.Arrays.binarySearch(values, 0, size, prefix(memberId)) >= 0;
        }

        private static long prefix(String memberId) {
            return Long.parseUnsignedLong(memberId.substring(0, 16), 16);
        }
    }

    /**
     * A cleanup checkpoint: the lease renewed at most once a second, and a 15-minute window after
     * which this sweep stops the cleanup ({@code CDS_EXPORT_CLEANUP_STOPPED}) and leaves the rest
     * to the next sweep. The window decides how a cleanup is spread over sweeps, never whether it
     * finishes: what one sweep erased stays erased ({@link CdsExportStagingFactory#purgeAttempt}).
     */
    private Runnable leaseCheckpoint(Runnable renew) {
        long started = clock.getAsLong();
        var last = new java.util.concurrent.atomic.AtomicLong(started - 1_000_000_000L);
        return () -> {
            long now = clock.getAsLong();
            if (Thread.currentThread().isInterrupted()
                    || now - started > TimeUnit.MINUTES.toNanos(15))
                throw new CancellationException("CDS_EXPORT_CLEANUP_STOPPED");
            long prior = last.get();
            if (now - prior >= 1_000_000_000L && last.compareAndSet(prior, now)) renew.run();
        };
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
            Runnable checkpoint = leaseCheckpoint(() -> ledger.cleanupCheckpoint(claim));
            // Version one could not create a bound artifact store; there is no directory to
            // erase.
            if (claim.document().protocol() == 2)
                stores.purgeAttempt(run, epoch, claim.document().storage(), checkpoint);
            ledger.cleanupCheckpoint(claim);
            complete = true;
        } catch (CancellationException stopped) {
            primary = stopped;
            // The window ended this sweep's share of a large cleanup: what it erased stays erased
            // and the next sweep continues. Not a failure, so no trace.
            if (!Thread.currentThread().isInterrupted()
                    && "CDS_EXPORT_CLEANUP_STOPPED".equals(stopped.getMessage()))
                LOG.info("CDS_EXPORT_CLEANUP_CONTINUES run={} epoch={}", run, epoch);
            else
                LOG.error(
                        "CDS export retention did not finish for {}: CDS_EXPORT_CLEANUP_INCOMPLETE {}",
                        run,
                        io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(stopped));
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
