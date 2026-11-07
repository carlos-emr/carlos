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
 * completion. Engines checkpoint all bounded I/O/render phases; a stalled/expired attempt can
 * never publish after another owner claims it. No placeholder clinical engine is registered.
 */
@Service
public class CdsExportExecutionService {
    private static final Logger LOG = LogManager.getLogger(CdsExportExecutionService.class);
    private final CdsExportLedgerService ledger;
    private final CdsExportAccessService access;
    private final CdsExportConfigurationService configuration;
    private final CdsExportEngineRegistry engines;
    private final AtomicBoolean fatal = new AtomicBoolean();
    private final java.util.concurrent.Semaphore attempt = new java.util.concurrent.Semaphore(1);
    public CdsExportExecutionService(CdsExportLedgerService ledger, CdsExportAccessService access,
            CdsExportConfigurationService configuration, CdsExportEngineRegistry engines) {
        this.ledger = ledger; this.access = access; this.configuration = configuration; this.engines = engines;
    }
    public boolean supervisionStopped() { return fatal.get(); }

    /** False means waiting/unclaimed, not failed or complete. Unknown control outcomes propagate to the supervisor. */
    public boolean execute(String runId) {
        if (fatal.get()) throw new CdsExportControlOutcomeUnknownException();
        if (!attempt.tryAcquire()) return false;
        try { return ownedExecution(runId); } finally { attempt.release(); }
    }
    private boolean ownedExecution(String runId) {
        if (fatal.get()) throw new CdsExportControlOutcomeUnknownException();
        var document = ledger.document(runId); var engine = engines.find(document.command().profile());
        if (engine == null || !configuration.enabled()) return false;
        long maximumMillis = configuration.maxDurationMillis();
        var lease = ledger.claim(runId, engine.compatibilityDigest()); if (lease == null) return false;
        try {
            access.requireScope(lease.document().actor(), lease.document().command());
            engine.execute(lease, new Control(lease, maximumMillis));
            if (!ledger.status(runId).state().terminal()) stop(lease, CdsExportState.FAILED, "CDS_EXPORT_ENGINE_DID_NOT_PUBLISH");
        } catch (CdsExportOwnerFencedException retired) {
            // Another attempt or a completed publication is authoritative. Never overwrite it.
        } catch (CdsExportControlOutcomeUnknownException unknown) { fatal.set(true); throw unknown; }
        catch (CancellationException interrupted) {
            // release() distinguishes a durable cancel request from stopping a foreground wait.
            finishUninterrupted(() -> ledger.release(lease));
        } catch (CdsExportReviewException review) { stop(lease, CdsExportState.REVIEW_REQUIRED, review.reason().code()); }
        catch (SecurityException denied) { stop(lease, CdsExportState.REVIEW_REQUIRED, "CDS_EXPORT_ACCESS_CHANGED"); }
        catch (IOException failed) {
            if (Thread.currentThread().isInterrupted()) finishUninterrupted(() -> ledger.release(lease));
            else stop(lease, CdsExportState.FAILED, "CDS_EXPORT_IO_FAILED");
        } catch (RuntimeException failed) {
            if (Thread.currentThread().isInterrupted()) finishUninterrupted(() -> ledger.release(lease));
            else stop(lease, CdsExportState.FAILED, "CDS_EXPORT_EXECUTION_FAILED");
        }
        catch (Error failed) { fatal.set(true); throw failed; }
        return true;
    }
    private void stop(CdsExportLeaseDto lease, CdsExportState state, String code) {
        finishUninterrupted(() -> ledger.stop(lease, state, code));
    }
    /** Interrupt state is restored; only the short metadata retirement may proceed with it temporarily clear. */
    private void finishUninterrupted(Runnable completion) {
        boolean interrupted = Thread.interrupted();
        try { completion.run(); }
        catch (CdsExportOwnerFencedException retired) { /* A newer durable outcome is authoritative. */ }
        catch (RuntimeException unknown) {
            fatal.set(true); LOG.error("CDS export control outcome is unknown: CDS_EXPORT_CONTROL_OUTCOME_UNKNOWN");
            throw new CdsExportControlOutcomeUnknownException();
        } catch (Error failed) { fatal.set(true); throw failed; }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
    private final class Control implements CdsExportRunControl {
        private final CdsExportLeaseDto lease;
        private final long started = System.nanoTime(), maximumNanos;
        private final AtomicLong lastProbe = new AtomicLong(started - 1_000_000_000L);
        Control(CdsExportLeaseDto lease, long maximumMillis) { this.lease = lease; maximumNanos = maximumMillis * 1_000_000L; }
        @Override public void checkpoint() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
            if (probe()) ledger.checkpoint(lease);
        }
        @Override public void captureCheckpoint() {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw new IllegalStateException("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
            }
            if (probe()) ledger.captureCheckpoint(lease);
        }
        private boolean probe() {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("CDS_EXPORT_WAIT_INTERRUPTED");
            if (fatal.get()) throw new CdsExportControlOutcomeUnknownException();
            configuration.requireEnabled(); long now = System.nanoTime();
            if (now - started > maximumNanos) throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
            long previous = lastProbe.get();
            return now - previous >= 1_000_000_000L && lastProbe.compareAndSet(previous, now);
        }
        private void outsideCapture() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("CDS_EXPORT_AUTHORIZATION_INSIDE_CAPTURE_REFUSED");
            }
            checkpoint();
        }
        @Override public void authorizePatient(int patient) {
            outsideCapture(); access.requirePatient(lease.document().actor(), lease.document().command(), patient);
        }
        @Override public void authorizePharmacy(int patient) {
            outsideCapture(); access.requirePharmacyRead(lease.document().actor(), lease.document().command(), patient);
        }
        @Override public void authorizeNote(CdsCategory category, io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto scope) {
            outsideCapture(); access.requireNoteRead(lease.document().actor(), lease.document().command(), category, scope);
        }
        @Override public void authorizeCategory(int patient, CdsCategory category, boolean sensitiveLaboratory) {
            outsideCapture(); access.requireCategory(lease.document().actor(), lease.document().command(), patient, category, sensitiveLaboratory);
        }
    }
}
