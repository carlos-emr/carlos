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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportControlOutcomeUnknownException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSubmissionConflictException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Authorized application facade shared by browser and verified CLI adapters. Acceptance commits
 * immutable intent before scheduling source work. Database rediscovery, not an HTTP session or
 * an in-memory queue, owns recovery. Recognized but unqualified profiles cannot create an export.
 */
@Service
public class CdsExportService {
    private static final Logger LOG = LogManager.getLogger(CdsExportService.class);
    private final CdsExportLedgerService ledger;
    private final CdsExportAccessService access;
    private final CdsExportConfigurationService configuration;
    private final CdsExportEngineRegistry engines;
    private final CdsExportExecutionService execution;
    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();
    private final Set<Thread> foreground = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean supervisorStopped = new AtomicBoolean();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), work -> {
                Thread thread = new Thread(work, "cds-export-worker"); thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((failed, error) -> LOG.error("CDS export worker stopped: CDS_EXPORT_WORKER_FATAL"));
                return thread;
            });
    private volatile boolean closed;
    private ScheduledExecutorService dispatcher;

    public CdsExportService(CdsExportLedgerService ledger, CdsExportAccessService access,
            CdsExportConfigurationService configuration, CdsExportEngineRegistry engines, CdsExportExecutionService execution) {
        this.ledger = ledger; this.access = access; this.configuration = configuration; this.engines = engines; this.execution = execution;
    }
    /** Web startup calls this only after publishing the normal Spring bridge. Dedicated CLI contexts disable both dispatchers. */
    public synchronized void start() {
        if (closed || dispatcher != null || !configuration.workerEnabled() || !configuration.enabled()
                || engines.qualifiedProfiles().isEmpty()) return;
        dispatcher = Executors.newSingleThreadScheduledExecutor(work -> {
            Thread thread = new Thread(work, "cds-export-dispatcher"); thread.setDaemon(true); return thread;
        });
        dispatcher.scheduleWithFixedDelay(this::dispatch, 1, 5, TimeUnit.SECONDS);
    }
    @PreDestroy public synchronized void close() {
        if (closed) return; closed = true;
        if (dispatcher != null) dispatcher.shutdownNow(); worker.shutdownNow();
        for (var waiting : foreground) waiting.interrupt();
    }
    public Set<CdsLogicalProfile> qualifiedProfiles() { return engines.qualifiedProfiles(); }
    public boolean supervisionStopped() { return supervisorStopped.get() || execution.supervisionStopped(); }
    public synchronized boolean dispatcherRunning() { return dispatcher != null && !dispatcher.isShutdown() && !supervisionStopped(); }

    /** Returns a durable handle before source planning/capture/rendering begins. */
    public CdsExportJobDto submit(LoggedInInfo viewer, CdsExportCommand command) throws IOException {
        var actor = access.webActor(viewer, command);
        CdsExportJobDto accepted = accept(actor, command); schedule(accepted.id()); return accepted;
    }
    /** Verified CLI acceptance never starts an implicit background worker in its one-shot process. */
    public CdsExportJobDto acceptDelegated(String verifiedOperator, CdsExportCommand command) throws IOException {
        return accept(access.delegatedActor(verifiedOperator, command), command);
    }
    private CdsExportJobDto accept(CdsExportActorDto actor, CdsExportCommand command) throws IOException {
        var prior = ledger.findSubmission(actor.operatorKey(), command.submissionKey());
        if (prior != null) return originalIntent(prior, actor, command);
        configuration.requireEnabled();
        var engine = engines.require(command.profile()); var document = engine.bind(actor, command);
        if (document == null || !Objects.equals(document.actor(), actor) || !Objects.equals(document.command(), command)
                || !Objects.equals(document.compatibilityDigest(), engine.compatibilityDigest())) {
            throw new IllegalStateException("CDS_EXPORT_JOB_BINDING_INVALID");
        }
        try { return ledger.accept(document); }
        catch (CdsExportSubmissionConflictException concurrent) {
            // Concurrent first requests can observe a recipient/audit policy rotation. Recover
            // unchanged original intent; never replace its accepted server-owned bindings.
            return originalIntent(ledger.submission(actor.operatorKey(), command.submissionKey()), actor, command);
        }
    }
    private CdsExportJobDto originalIntent(CdsExportJobDto prior, CdsExportActorDto actor, CdsExportCommand command) {
        var original = ledger.document(prior.id());
        if (!Objects.equals(original.actor(), actor) || !Objects.equals(original.command(), command)) {
            throw new CdsExportSubmissionConflictException();
        }
        return prior;
    }
    public CdsExportJobDto submission(LoggedInInfo viewer, String key) {
        var actor = access.operatorSession(viewer); var selected = ledger.submission(actor.operatorKey(), key);
        return observe(viewer, selected.id());
    }
    public CdsExportJobDto observe(LoggedInInfo viewer, String runId) {
        authorize(viewer, runId); return ledger.status(runId);
    }
    public CdsExportJobDto observeDelegated(String verifiedOperator, String provider, String runId) {
        authorizeDelegated(verifiedOperator, provider, runId); return ledger.status(runId);
    }
    public List<CdsExportJobDto> history(LoggedInInfo viewer, String after, int limit) {
        var actor = access.operatorSession(viewer); return ledger.history(actor.operatorKey(), after, limit);
    }
    public CdsExportJobDto submissionDelegated(String verifiedOperator, String provider, String key) {
        // Identifier validation does not supply authority; authorize the recovered immutable scope before returning its handle.
        var selected = ledger.submission("unix:" + verifiedOperator, key);
        return observeDelegated(verifiedOperator, provider, selected.id());
    }
    public CdsExportJobDto cancel(LoggedInInfo viewer, String runId) {
        var actor = authorize(viewer, runId); ledger.cancel(runId, actor.operatorKey()); return ledger.status(runId);
    }
    public CdsExportJobDto cancel(LoggedInInfo viewer, String runId, long expectedRevision) {
        if (expectedRevision < 0) throw new IllegalArgumentException("CDS_EXPORT_REVISION_REQUIRED");
        var actor = authorize(viewer, runId); ledger.cancel(runId, actor.operatorKey(), expectedRevision); return ledger.status(runId);
    }
    public CdsExportJobDto cancelDelegated(String verifiedOperator, String provider, String runId) {
        var actor = authorizeDelegated(verifiedOperator, provider, runId); ledger.cancel(runId, actor.operatorKey()); return ledger.status(runId);
    }
    public CdsExportJobDto resume(LoggedInInfo viewer, String runId) {
        var actor = authorize(viewer, runId); resume(actor, runId); schedule(runId); return ledger.status(runId);
    }
    public CdsExportJobDto resume(LoggedInInfo viewer, String runId, long expectedRevision) {
        if (expectedRevision < 0) throw new IllegalArgumentException("CDS_EXPORT_REVISION_REQUIRED");
        var actor = authorize(viewer, runId); resume(actor, runId, expectedRevision); schedule(runId); return ledger.status(runId);
    }
    public CdsExportJobDto resumeDelegated(String verifiedOperator, String provider, String runId) {
        var actor = authorizeDelegated(verifiedOperator, provider, runId); resume(actor, runId); return ledger.status(runId);
    }
    private void resume(CdsExportActorDto actor, String runId) {
        resume(actor, runId, null);
    }
    private void resume(CdsExportActorDto actor, String runId, Long expectedRevision) {
        configuration.requireEnabled(); var document = ledger.document(runId); var engine = engines.require(document.command().profile());
        if (expectedRevision == null) ledger.resume(runId, actor.operatorKey(), engine.compatibilityDigest());
        else ledger.resume(runId, actor.operatorKey(), engine.compatibilityDigest(), expectedRevision);
    }
    /** Explicit foreground work. Interrupting this caller does not call cancel(); stopped attempts return to durable waiting. */
    public CdsExportJobDto executeDelegated(String verifiedOperator, String provider, String runId) {
        authorizeDelegated(verifiedOperator, provider, runId); configuration.requireEnabled();
        var document = ledger.document(runId); engines.require(document.command().profile());
        Thread current = Thread.currentThread(); foreground.add(current);
        try {
            if (closed) throw new IllegalStateException("CDS_EXPORT_RUNTIME_CLOSED");
            execution.execute(runId); return ledger.status(runId);
        } finally { foreground.remove(current); }
    }
    private CdsExportActorDto authorize(LoggedInInfo viewer, String runId) {
        var actor = access.operatorSession(viewer); access.requireObserve(actor, ledger.owner(runId)); return actor;
    }
    private CdsExportActorDto authorizeDelegated(String verifiedOperator, String provider, String runId) {
        var owner = ledger.owner(runId);
        Integer hint = Objects.equals(provider, owner.providerNo()) ? owner.facilityHint() : null;
        var actor = access.delegatedSession(verifiedOperator, provider, hint); access.requireObserve(actor, owner); return actor;
    }
    public io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDiagnosticDto report(LoggedInInfo viewer, String runId) {
        authorize(viewer, runId); return ledger.diagnostic(runId);
    }
    public io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDiagnosticDto reportDelegated(String verifiedOperator, String provider, String runId) {
        authorizeDelegated(verifiedOperator, provider, runId); return ledger.diagnostic(runId);
    }
    /** Retrieval reconstructs the observer in the exact accepted clinical provider/facility scope. */
    public io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto downloadActor(LoggedInInfo viewer, String runId) {
        authorize(viewer, runId); return access.webActor(viewer, ledger.document(runId).command());
    }
    public io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto downloadActorDelegated(String verifiedOperator, String provider, String runId) {
        authorizeDelegated(verifiedOperator, provider, runId); var document = ledger.document(runId);
        if (!Objects.equals(provider, document.command().providerNo())) throw new SecurityException("CDS_EXPORT_ACCESS_DENIED");
        return access.delegatedActor(verifiedOperator, document.command());
    }
    private synchronized void schedule(String runId) {
        if (closed || dispatcher == null || supervisionStopped() || !scheduled.add(runId)) return;
        try {
            worker.execute(() -> {
                try { if (!closed && !supervisionStopped()) execution.execute(runId); }
                catch (RuntimeException failed) {
                    if (failed instanceof CdsExportControlOutcomeUnknownException) supervisorStopped.set(true);
                    LOG.error("CDS export attempt stopped for {}: CDS_EXPORT_EXECUTION_FAILED", runId);
                } catch (Error failed) { supervisorStopped.set(true); throw failed; }
                finally { scheduled.remove(runId); }
            });
        } catch (RejectedExecutionException full) { scheduled.remove(runId); }
        // Full/paused queues leave the accepted database job discoverable; acceptance is never undone.
    }
    private void dispatch() {
        if (closed || supervisionStopped()) return;
        try {
            if (!configuration.enabled()) return;
            for (String runId : ledger.pending(16)) schedule(runId);
        } catch (RuntimeException failed) {
            LOG.error("CDS export discovery unavailable: CDS_EXPORT_DISCOVERY_FAILED");
        } catch (Error failed) { supervisorStopped.set(true); throw failed; }
    }
}
