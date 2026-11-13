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
        try (var control = new Control(lease, maximumMillis)) {
            control.startHeartbeat();
            access.requireScope(lease.document().actor(), lease.document().command());
            engine.execute(lease, control);
            if (!ledger.status(runId).state().terminal()) {
                control.checkpoint();
                stop(lease, CdsExportState.FAILED, "CDS_EXPORT_ENGINE_DID_NOT_PUBLISH");
            }
        } catch (CdsExportOwnerFencedException retired) {
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
                stop(lease, CdsExportState.FAILED, "CDS_EXPORT_IO_FAILED");
                technicalFailure(failed);
                LOG.error(
                        "CDS_EXPORT_IO_FAILED run={} epoch={} {}",
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
            // captured authorization grant. The same deadline bounds blocked work.
            heartbeat.scheduleWithFixedDelay(
                    () -> {
                        try {
                            local();
                            ledger.checkpoint(lease);
                        } catch (RuntimeException failed) {
                            heartbeatFailure.compareAndSet(null, failed);
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
            local();
            long now = System.nanoTime();
            long previous = lastProbe.get();
            return now - previous >= 1_000_000_000L && lastProbe.compareAndSet(previous, now);
        }

        private void local() {
            if (Thread.currentThread().isInterrupted())
                throw new CancellationException("CDS_EXPORT_WAIT_INTERRUPTED");
            if (fatal.get()) throw new CdsExportControlOutcomeUnknownException();
            var failed = heartbeatFailure.get();
            if (failed != null) throw failed;
            configuration.requireEnabled();
            long now = System.nanoTime();
            if (now - started > maximumNanos)
                throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
        }

        /** The current patient's session, or null (checks are then answered fresh). */
        private CdsExportPatientAuthorization session;

        private CdsExportPatientAuthorization session(int patient) {
            var current = session;
            return current != null && current.patient() == patient ? current : null;
        }

        @Override
        public void beginPatient(int patient) {
            outsideCapture();
            session = new CdsExportPatientAuthorization(patient, new FreshChecks());
        }

        @Override
        public void endPatient(int patient) {
            var current = session(patient);
            session = null;
            if (current == null) throw new IllegalStateException("CDS_EXPORT_SESSION_NOT_STARTED");
            outsideCapture();
            current.finish();
        }

        @Override
        public void discardPatient() {
            session = null;
        }

        /** The uncached checks the session falls back to and re-runs when it closes. */
        private final class FreshChecks implements CdsExportPatientAuthorization.Fresh {
            @Override
            public void patient(int patient) {
                access.requirePatient(lease.document().actor(), lease.document().command(), patient);
            }

            @Override
            public void pharmacy(int patient) {
                access.requirePharmacyRead(
                        lease.document().actor(), lease.document().command(), patient);
            }

            @Override
            public void caseManagement(int patient) {
                access.requireCaseManagementRead(
                        lease.document().actor(), lease.document().command(), patient);
            }

            @Override
            public void category(int patient, CdsCategory category, boolean sensitiveLaboratory) {
                access.requireCategory(
                        lease.document().actor(),
                        lease.document().command(),
                        patient,
                        category,
                        sensitiveLaboratory);
            }

            @Override
            public void note(
                    CdsCategory category,
                    io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto
                            scope,
                    CdsExportAccessService.PatientGrants grants) {
                access.requireNoteRead(
                        lease.document().actor(),
                        lease.document().command(),
                        category,
                        scope,
                        grants);
            }

            @Override
            public void notes(
                    CdsCategory category,
                    java.util.List<
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportNoteAccessDto>
                            scopes) {
                access.requireNotesRead(
                        lease.document().actor(), lease.document().command(), category, scopes);
            }

            @Override
            public boolean issueProgram(int patient, CdsCategory category, String program) {
                return access.canReadIssueProgram(
                        lease.document().actor(),
                        lease.document().command(),
                        category,
                        patient,
                        program);
            }

            @Override
            public boolean restrictedProgram(int patient, CdsCategory category, String program) {
                return access.canReadRestrictedProgram(
                        lease.document().actor(),
                        lease.document().command(),
                        category,
                        patient,
                        program);
            }

            @Override
            public boolean record(int patient, String object) {
                return access.canReadRecordObject(
                        lease.document().actor(), lease.document().command(), patient, object);
            }
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
                for (var scope : batch) current.note(category, scope);
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
