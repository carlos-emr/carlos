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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportState;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportControlOutcomeUnknownException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportOwnerFencedException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessLogging;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One read-only export attempt, usable by the browser dispatcher or an explicit foreground CLI.
 * Lease-fenced metadata, current authorization and finite work remain separate from clinical
 * completion. Engines checkpoint all bounded I/O/render phases; a stalled/expired attempt can never
 * publish after another owner claims it. No placeholder clinical engine is registered.
 */
@Service
public class CdsExportExecutionService {
    private static final Logger LOG = LogManager.getLogger(CdsExportExecutionService.class);
    private final CdsExportLedgerService ledger;
    private final CdsExportAccessService access;
    private final CdsExportConfigurationService configuration;
    private final CdsExportEngineRegistry engines;
    private final java.util.function.Supplier<java.util.concurrent.ScheduledExecutorService>
            schedulers;
    private final AtomicBoolean fatal = new AtomicBoolean();
    private final java.util.concurrent.Semaphore attempt = new java.util.concurrent.Semaphore(1);

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportExecutionService(
            CdsExportLedgerService ledger,
            CdsExportAccessService access,
            CdsExportConfigurationService configuration,
            CdsExportEngineRegistry engines) {
        this(
                ledger,
                access,
                configuration,
                engines,
                () ->
                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                                work -> {
                                    var thread = new Thread(work, "cds-export-execution-lease");
                                    thread.setDaemon(true);
                                    return thread;
                                }));
    }

    CdsExportExecutionService(
            CdsExportLedgerService ledger,
            CdsExportAccessService access,
            CdsExportConfigurationService configuration,
            CdsExportEngineRegistry engines,
            java.util.function.Supplier<java.util.concurrent.ScheduledExecutorService> schedulers) {
        this.ledger = ledger;
        this.access = access;
        this.configuration = configuration;
        this.engines = engines;
        this.schedulers = schedulers;
    }

    public boolean supervisionStopped() {
        return fatal.get();
    }

    /**
     * False means waiting/unclaimed, not failed or complete. Unknown control outcomes propagate to
     * the supervisor.
     */
    public boolean execute(String runId) {
        if (fatal.get()) throw new CdsExportControlOutcomeUnknownException();
        if (!attempt.tryAcquire()) return false;
        try {
            return ownedExecution(runId);
        } finally {
            attempt.release();
        }
    }

    private boolean ownedExecution(String runId) {
        if (fatal.get()) throw new CdsExportControlOutcomeUnknownException();
        io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto document;
        try {
            document = ledger.document(runId);
        } catch (IllegalStateException malformed) {
            if (!java.util.Set.of(
                            "CDS_EXPORT_JOB_DOCUMENT_INVALID", "CDS_EXPORT_JOB_BINDING_INVALID")
                    .contains(malformed.getMessage() == null ? "" : malformed.getMessage()))
                throw malformed;
            ledger.rejectUnreadableIntent(runId);
            LOG.warn(
                    "CDS_EXPORT_JOB_DOCUMENT_INVALID run={} {}",
                    runId,
                    LogSafe.exceptionTrace(malformed));
            return true;
        }
        var engine = engines.find(document.command().profile());
        if (engine == null || !configuration.enabled()) return false;
        long maximumMillis = configuration.maxDurationMillis();
        var lease = ledger.claim(runId, engine.compatibilityDigest());
        if (lease == null) return false;
        stalledAttempt.set(null);
        stallSettled.set(null);
        try (var control = new Control(lease, maximumMillis)) {
            control.startHeartbeat();
            access.requireScope(lease.document().actor(), lease.document().command());
            engine.execute(lease, control);
            if (!ledger.status(runId).state().terminal()) {
                control.checkpoint();
                stop(lease, CdsExportState.FAILED, "CDS_EXPORT_ENGINE_DID_NOT_PUBLISH");
            }
        } catch (CdsExportOwnerFencedException retired) {
            if (stalledAttempt.get() == lease) {
                // This attempt was declared stalled. Whichever thread gets there first (the
                // heartbeat that saw the silence, or this returning engine thread) records the
                // one outcome a stall has; the other finds it recorded and changes nothing.
                finishUninterrupted(() -> recordStall(lease));
            } else
                // Retire only our own expired generation. A successor or publication remains
                // authoritative; it must never be overwritten by the returning old worker.
                finishUninterrupted(
                        () -> {
                            if (ledger.retireExpiredLease(lease))
                                LOG.warn(
                                        "CDS_EXPORT_EXECUTION_LEASE_EXPIRED run={} epoch={}",
                                        runId,
                                        lease.epoch());
                        });
        } catch (CdsExportControlOutcomeUnknownException unknown) {
            fatal.set(true);
            throw unknown;
        } catch (CancellationException interrupted) {
            // release() distinguishes a durable cancel request from stopping a foreground wait.
            finishUninterrupted(() -> ledger.release(lease));
        } catch (CdsExportReviewException review) {
            stop(lease, CdsExportState.REVIEW_REQUIRED, review.reason().code());
            technicalFailure(review);
            LOG.warn(
                    "CDS_EXPORT_REVIEW_REQUIRED run={} epoch={} code={} {}",
                    runId,
                    lease.epoch(),
                    review.reason().code(),
                    LogSafe.exceptionTrace(review));
        } catch (SecurityException denied) {
            stop(lease, CdsExportState.REVIEW_REQUIRED, "CDS_EXPORT_ACCESS_CHANGED");
            technicalFailure(denied);
        } catch (
                io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportMappingReviewException
                        review) {
            stop(lease, CdsExportState.REVIEW_REQUIRED, "CDS_EXPORT_UNMAPPED_DATA");
            technicalFailure(review);
            LOG.warn(
                    "CDS_EXPORT_REVIEW_REQUIRED run={} epoch={} code=CDS_EXPORT_UNMAPPED_DATA {}",
                    runId,
                    lease.epoch(),
                    LogSafe.exceptionTrace(review));
        } catch (IOException failed) {
            if (Thread.currentThread().isInterrupted())
                finishUninterrupted(() -> ledger.release(lease));
            else {
                // A genuine storage failure is the only thing that ends an export for its size.
                // It is a failure, under its own code, with nothing published or truncated, and
                // the job can be resumed once the storage is fixed.
                String code = storageFailure(failed);
                stop(lease, CdsExportState.FAILED, code);
                technicalFailure(failed);
                LOG.error(
                        "{} run={} epoch={} {}",
                        code,
                        runId,
                        lease.epoch(),
                        LogSafe.exceptionTrace(failed));
            }
        } catch (RuntimeException failed) {
            if (Thread.currentThread().isInterrupted())
                finishUninterrupted(() -> ledger.release(lease));
            else {
                stop(lease, CdsExportState.FAILED, "CDS_EXPORT_EXECUTION_FAILED");
                technicalFailure(failed);
                LOG.error(
                        "CDS_EXPORT_EXECUTION_FAILED run={} epoch={} {}",
                        runId,
                        lease.epoch(),
                        LogSafe.exceptionTrace(failed));
            }
        } catch (Error failed) {
            fatal.set(true);
            throw failed;
        }
        return true;
    }

    /** The staging store's free space fell below {@code cds.export.minimumFreeBytes}. */
    static final String DISK_RESERVE = "CDS_EXPORT_DISK_RESERVE";

    /** The file system refused a write for lack of space or quota. */
    static final String DISK_FULL = "CDS_EXPORT_DISK_FULL";

    /** Any other I/O failure. */
    static final String IO_FAILED = "CDS_EXPORT_IO_FAILED";

    /** The worker reached no checkpoint for the whole stall window. */
    static final String WORKER_STALLED = "CDS_EXPORT_WORKER_STALLED";

    /**
     * The code of a storage failure: the store's own reserve refusal by its exact fixed code, the
     * operating system's out-of-space or quota refusal wherever it sits in the cause chain, and
     * otherwise the general I/O failure. Only these fixed codes are ever recorded; an exception's
     * own text is not.
     */
    static String storageFailure(IOException failed) {
        if (DISK_RESERVE.equals(failed.getMessage())) return DISK_RESERVE;
        var seen = new java.util.HashSet<Throwable>();
        for (Throwable cause = failed; cause != null && seen.add(cause); cause = cause.getCause()) {
            String text =
                    cause instanceof java.nio.file.FileSystemException file
                                    && file.getReason() != null
                            ? file.getReason()
                            : cause.getMessage();
            if (text != null
                    && (text.contains("No space left on device")
                            || text.contains("Disk quota exceeded")
                            || text.contains("There is not enough space on the disk")))
                return DISK_FULL;
        }
        return IO_FAILED;
    }

    /** The attempt whose engine thread was declared stalled; null for a healthy attempt. */
    private final java.util.concurrent.atomic.AtomicReference<CdsExportLeaseDto> stalledAttempt =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** The stalled attempt whose outcome the ledger has answered for; guards one record. */
    private final java.util.concurrent.atomic.AtomicReference<CdsExportLeaseDto> stallSettled =
            new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * Ends a stalled attempt as FAILED under {@link #WORKER_STALLED}, from whichever thread calls
     * first. A lease that is still live is stopped; one that already expired is retired under the
     * same code, so the outcome does not depend on when the engine thread returns. A successor
     * generation or a publication stays authoritative, and a stalled attempt can never publish:
     * the ledger has cleared its ownership.
     *
     * @return true when this call recorded the outcome
     */
    private boolean recordStall(CdsExportLeaseDto lease) {
        synchronized (stallSettled) {
            if (stallSettled.get() == lease) return false;
            boolean recorded;
            try {
                ledger.stop(lease, CdsExportState.FAILED, WORKER_STALLED);
                recorded = true;
            } catch (CdsExportOwnerFencedException expired) {
                recorded =
                        ledger.retireExpiredLease(lease, CdsExportState.FAILED, WORKER_STALLED);
            }
            // The ledger answered: this attempt's outcome is settled, by this call or by a
            // successor generation or publication that is authoritative instead.
            stallSettled.set(lease);
            // Loud, once per attempt: the run and its outcome only.
            if (recorded)
                LOG.error(
                        "CDS_EXPORT_WORKER_STALLED run={} epoch={} state=FAILED",
                        lease.runId(),
                        lease.epoch());
            return recorded;
        }
    }

    /** The CLI disables framework logging; this channel accepts only the bounded metadata projection. */
    private static void technicalFailure(Throwable failure) {
        LogManager.getLogger("carlos.cds.safeTechnical")
                .error(
                        "CDS_TECHNICAL: {}",
                        new String(
                                CdsProcessLogging.failureSummary(failure, false),
                                java.nio.charset.StandardCharsets.UTF_8));
    }

    private void stop(CdsExportLeaseDto lease, CdsExportState state, String code) {
        finishUninterrupted(
                () -> {
                    try {
                        ledger.stop(lease, state, code);
                    } catch (CdsExportOwnerFencedException expired) {
                        if (ledger.retireExpiredLease(lease))
                            LOG.warn(
                                    "CDS_EXPORT_EXECUTION_LEASE_EXPIRED run={} epoch={}",
                                    lease.runId(),
                                    lease.epoch());
                    }
                });
    }

    /**
     * Interrupt state is restored; only the short metadata retirement may proceed with it
     * temporarily clear.
     */
    private void finishUninterrupted(Runnable completion) {
        boolean interrupted = Thread.interrupted();
        try {
            completion.run();
        } catch (CdsExportOwnerFencedException retired) {
            /* A newer durable outcome is authoritative. */
        } catch (RuntimeException unknown) {
            fatal.set(true);
            LOG.error("CDS export control outcome is unknown: CDS_EXPORT_CONTROL_OUTCOME_UNKNOWN");
            throw new CdsExportControlOutcomeUnknownException();
        } catch (Error failed) {
            fatal.set(true);
            throw failed;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private final class Control implements CdsExportRunControl, AutoCloseable {
        private final CdsExportLeaseDto lease;
        private final long started = System.nanoTime(), maximumNanos;
        private final AtomicLong lastProbe = new AtomicLong(started - 1_000_000_000L);

        /**
         * When the engine thread last passed a checkpoint. {@code cds.export.maxDurationMillis}
         * bounds the time since then and nothing else: it is a stall detector, not a budget. A
         * worker that keeps reaching checkpoints is alive and is never stopped for how long its
         * roster takes; a worker that reaches none for that long is treated as dead, and its
         * attempt ends FAILED under {@code CDS_EXPORT_WORKER_STALLED} whether or not the thread
         * ever returns. Only the heartbeat thread can observe that, because the engine thread's
         * own checkpoint is the proof of life.
         */
        private final AtomicLong alive = new AtomicLong(started);

        private final AtomicBoolean stalled = new AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicReference<RuntimeException>
                heartbeatFailure = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.ScheduledExecutorService heartbeat;

        Control(CdsExportLeaseDto lease, long maximumMillis) {
            this.lease = lease;
            maximumNanos = maximumMillis * 1_000_000L;
            heartbeat = schedulers.get();
        }

        void startHeartbeat() {
            // Metadata only: this thread never joins a clinical snapshot or carries a
            // captured authorization grant. It is also the stall detector: when the engine
            // thread has passed no checkpoint for the configured time, it stops renewing.
            heartbeat.scheduleWithFixedDelay(
                    () -> {
                        try {
                            local(false);
                            ledger.checkpoint(lease);
                        } catch (RuntimeException failed) {
                            heartbeatFailure.compareAndSet(null, failed);
                            // The engine thread cannot record its own silence: this thread does,
                            // at once and again on every beat until the ledger has it, so the job
                            // ends in one visible state instead of being claimed and restarted.
                            if (stalled.get())
                                try {
                                    recordStall(lease);
                                } catch (RuntimeException unrecorded) {
                                    LOG.error(
                                            "CDS_EXPORT_WORKER_STALLED run={} epoch={} outcome"
                                                    + " not recorded yet {}",
                                            lease.runId(),
                                            lease.epoch(),
                                            LogSafe.exceptionTrace(unrecorded));
                                }
                        } catch (Error failed) {
                            fatal.set(true);
                            heartbeatFailure.compareAndSet(
                                    null, new CdsExportControlOutcomeUnknownException());
                            throw failed;
                        }
                    },
                    20,
                    20,
                    java.util.concurrent.TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            heartbeat.shutdownNow();
            closeVerified();
        }

        @Override
        public void checkpoint() {
            if (TransactionSynchronizationManager.isActualTransactionActive())
                throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
            if (probe()) ledger.checkpoint(lease);
        }

        @Override
        public void captureCheckpoint() {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw new IllegalStateException("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
            }
            if (probe()) ledger.captureCheckpoint(lease);
        }

        private boolean probe() {
            local(true);
            long now = System.nanoTime();
            long previous = lastProbe.get();
            return now - previous >= 1_000_000_000L && lastProbe.compareAndSet(previous, now);
        }

        /**
         * The cheap local checks of every checkpoint.
         *
         * @param engine true on the engine thread, whose checkpoint is the proof of life; false
         *     on the heartbeat thread, which is the one that can see the engine has stalled
         */
        private void local(boolean engine) {
            if (Thread.currentThread().isInterrupted())
                throw new CancellationException("CDS_EXPORT_WAIT_INTERRUPTED");
            if (fatal.get()) throw new CdsExportControlOutcomeUnknownException();
            var failed = heartbeatFailure.get();
            // The heartbeat keeps trying to record a stall; every other failure is final here.
            if (failed != null && (engine || !stalled.get())) throw failed;
            configuration.requireEnabled();
            long now = System.nanoTime();
            if (engine) {
                alive.set(now);
                return;
            }
            if (stalled.get() || now - alive.get() > maximumNanos) {
                // Once per attempt; the run and times only, never a patient.
                if (stalled.compareAndSet(false, true))
                    LOG.warn(
                            "CDS_EXPORT_WORKER_STALLED run={} epoch={} silentMillis={}"
                                    + " attemptMillis={}",
                            lease.runId(),
                            lease.epoch(),
                            (now - alive.get()) / 1_000_000L,
                            (now - started) / 1_000_000L);
                // A stall is sticky: a worker that was silent for the whole window does not
                // resume, even if it reaches a checkpoint later. The heartbeat stops renewing
                // and records the attempt as FAILED under CDS_EXPORT_WORKER_STALLED; the engine
                // thread, if it ever returns, is fenced at its next checkpoint and can publish
                // nothing.
                stalledAttempt.set(lease);
                throw new CdsExportOwnerFencedException();
            }
        }

        /**
         * The current patient's session or re-authorization pass, or null (checks are then
         * answered fresh, one transaction each).
         */
        private CdsExportPatientAuthorization session;

        private CdsExportPatientAuthorization session(int patient) {
            var current = session;
            return current != null && current.patient() == patient ? current : null;
        }

        /** The live checks of this attempt's actor and command. */
        private CdsExportPatientAuthorization.Fresh fresh() {
            return new CdsExportPatientAuthorization.Live(
                    access, lease.document().actor(), lease.document().command());
        }

        /** The current patient's checked capture reads; zeroed when its session ends. */
        private CdsExportVerifiedCaptures verified;

        @Override
        public void beginPatient(int patient) {
            outsideCapture();
            closeVerified();
            session = null;
            // The opening pass: every fixed gate, from the live database, before any read.
            session = new CdsExportPatientAuthorization(patient, fresh());
            verified = new CdsExportVerifiedCaptures(patient);
        }

        @Override
        public void endPatient(int patient) {
            var current = session(patient);
            session = null;
            // The patient's last read is done: nothing replays past this point.
            closeVerified();
            if (current == null || !current.session())
                throw new IllegalStateException("CDS_EXPORT_SESSION_NOT_STARTED");
            outsideCapture();
            current.finish();
        }

        @Override
        public void discardPatient() {
            session = null;
            closeVerified();
        }

        @Override
        public void beginAuthorizationPass(int patient) {
            outsideCapture();
            // A pass never replaces a session: that would drop the session's closing check.
            if (session != null) throw new IllegalStateException("CDS_EXPORT_SESSION_ACTIVE");
            session = CdsExportPatientAuthorization.pass(patient, fresh());
        }

        @Override
        public void endAuthorizationPass() {
            var current = session;
            if (current != null && current.session())
                throw new IllegalStateException("CDS_EXPORT_SESSION_ACTIVE");
            session = null;
        }

        @Override
        public CdsExportVerifiedCaptures verifiedCaptures(int patient) {
            var current = verified;
            return current != null && current.patient() == patient && current.usable()
                    ? current
                    : null;
        }

        private void closeVerified() {
            var current = verified;
            verified = null;
            if (current != null) current.close();
        }

        /** Table names only; kept for this attempt and dropped with it. */
        private final java.util.Set<String> schemaProbes =
                java.util.concurrent.ConcurrentHashMap.newKeySet();

        @Override
        public java.util.Set<String> schemaProbes() {
            return schemaProbes;
        }

        private void outsideCapture() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("CDS_EXPORT_AUTHORIZATION_INSIDE_CAPTURE_REFUSED");
            }
            checkpoint();
        }

        @Override
        public void authorizePatient(int patient) {
            outsideCapture();
            var current = session(patient);
            if (current != null) current.patient(patient);
            else access.requirePatient(lease.document().actor(), lease.document().command(), patient);
        }

        @Override
        public void authorizePharmacy(int patient) {
            outsideCapture();
            var current = session(patient);
            if (current != null) current.pharmacy(patient);
            else
                access.requirePharmacyRead(
                        lease.document().actor(), lease.document().command(), patient);
        }

        @Override
        public void authorizeCaseManagement(int patient) {
            outsideCapture();
            var current = session(patient);
            if (current != null) current.caseManagement(patient);
            else
                access.requireCaseManagementRead(
                        lease.document().actor(), lease.document().command(), patient);
        }

        @Override
        public void authorizeNote(
                CdsCategory category,
                io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto
                        scope) {
            outsideCapture();
            var current = scope == null ? null : session(scope.patient());
            if (current != null) current.note(category, scope);
            else
                access.requireNoteRead(
                        lease.document().actor(), lease.document().command(), category, scope);
        }

        @Override
        public boolean issueProgramReadable(int patient, CdsCategory category, String program) {
            outsideCapture();
            var current = session(patient);
            if (current != null) return current.issueProgram(patient, category, program);
            return access.canReadIssueProgram(
                    lease.document().actor(), lease.document().command(), category, patient,
                    program);
        }

        @Override
        public boolean restrictedProgramReadable(
                int patient, CdsCategory category, String program) {
            outsideCapture();
            var current = session(patient);
            if (current != null) return current.restrictedProgram(patient, category, program);
            return access.canReadRestrictedProgram(
                    lease.document().actor(), lease.document().command(), category, patient,
                    program);
        }

        @Override
        public boolean recordReadable(int patient, String object) {
            outsideCapture();
            var current = session(patient);
            if (current != null) return current.record(patient, object);
            return access.canReadRecordObject(
                    lease.document().actor(), lease.document().command(), patient, object);
        }

        @Override
        public void authorizeNotes(
                CdsCategory category,
                java.util.List<
                                io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportNoteAccessDto>
                        scopes) {
            outsideCapture();
            var batch = java.util.List.copyOf(scopes);
            var current = batch.isEmpty() ? null : session(batch.getFirst().patient());
            if (current != null) {
                current.notes(category, batch);
                return;
            }
            access.requireNotesRead(
                    lease.document().actor(), lease.document().command(), category, batch);
        }

        @Override
        public io.github.carlos_emr.carlos.utility.LoggedInInfo authorizeRendering(int patient) {
            outsideCapture();
            return access.requireEformRendering(
                    lease.document().actor(), lease.document().command(), patient);
        }

        @Override
        public void authorizeCategory(
                int patient, CdsCategory category, boolean sensitiveLaboratory) {
            outsideCapture();
            var current = session(patient);
            if (current != null) current.category(patient, category, sensitiveLaboratory);
            else
                access.requireCategory(
                        lease.document().actor(),
                        lease.document().command(),
                        patient,
                        category,
                        sensitiveLaboratory);
        }
    }
}
