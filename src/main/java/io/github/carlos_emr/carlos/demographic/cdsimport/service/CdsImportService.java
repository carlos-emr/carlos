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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportReviewCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** Authorized application boundary shared by HTTP adapters and the verified foreground CLI. */
@Service
public class CdsImportService {
    private static final org.apache.logging.log4j.Logger LOG=org.apache.logging.log4j.LogManager.getLogger(CdsImportService.class);
    private final CdsImportLedgerService ledger;
    private final CdsImportJobService jobs;
    private final CdsImportExecutionService execution;
    private final CdsImportResultService results;
    private final CdsImportAccessService access;
    private final CdsImportConfigurationService configuration;
    private final Set<String> scheduled=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t=new Thread(r, "cds-import-worker"); t.setDaemon(true); t.setUncaughtExceptionHandler((thread, failure) -> LOG.error("CDS worker terminated: WORKER_FATAL")); return t; });
    private ScheduledExecutorService dispatcher;
    private final CdsImportCleanupService cleanup;
    private volatile Map<String, Object> administrativeCleanup=Map.of();
    private final CdsImportReviewService reviews;
    private final CdsImportFailureEvidenceService failureEvidence;
    private volatile String housekeepingAlert;
    private final java.util.concurrent.atomic.AtomicReference<String> fatalRun=new java.util.concurrent.atomic.AtomicReference<>();
    private long nextHousekeepingNanos=System.nanoTime();

    public CdsImportService(CdsImportLedgerService ledger, CdsImportJobService jobs, CdsImportExecutionService execution,
            CdsImportResultService results, CdsImportAccessService access, CdsImportConfigurationService configuration, CdsImportCleanupService cleanup, CdsImportReviewService reviews,CdsImportFailureEvidenceService failureEvidence) {
        this.ledger=ledger; this.jobs=jobs; this.execution=execution; this.results=results; this.access=access; this.configuration=configuration; this.cleanup=cleanup; this.reviews=reviews; this.failureEvidence=failureEvidence;
    }

    @PostConstruct public void start() {
        if (!Boolean.parseBoolean(configuration.value("cds.import.worker.enabled", "true"))) return;
        dispatcher=Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r, "cds-import-dispatcher"); t.setDaemon(true); return t; });
        dispatcher.scheduleWithFixedDelay(this::dispatch, 1, 5, TimeUnit.SECONDS);
    }
    @PreDestroy public void close() { if (dispatcher!=null) dispatcher.shutdownNow(); worker.shutdownNow(); }

    /** Acceptance is durable before foreground callers print the run ID or perform lengthy execution. */
    public CdsImportJobDto accept(CdsImportActorDto actor, CdsImportCommand command) throws IOException {
        access.context(actor, command, null); return jobs.accept(actor, command);
    }
    public CdsImportJobDto submit(LoggedInInfo viewer, CdsImportCommand command) throws IOException {
        CdsImportActorDto actor=access.webActor(viewer, command); CdsImportJobDto job=accept(actor, command); schedule(job.id(), false); return job;
    }
    public CdsImportJobDto submit(CdsImportActorDto actor, CdsImportCommand command) throws IOException {
        CdsImportJobDto job=accept(actor, command); schedule(job.id(), false); return job;
    }
    public CdsImportJobDto run(CdsImportActorDto actor, CdsImportCommand command) throws IOException { return runAccepted(actor, accept(actor, command).id()); }
    public CdsImportJobDto runAccepted(CdsImportActorDto actor, String run) throws IOException { requireActor(actor, run); requireNoPendingReview(run); return execution.execute(run); }
    public CdsImportJobDto resumeAndWait(CdsImportActorDto actor, String run) throws IOException { requireActor(actor, run); requireNoPendingReview(run); ledger.requestResume(run); return execution.execute(run, true); }

    public CdsImportJobDto status(LoggedInInfo viewer, String run) { CdsImportJobDto job=ledger.status(run); access.requireView(viewer, job); return job; }
    public CdsImportJobDto status(CdsImportActorDto actor, String run) throws IOException { requireActor(actor, run); return ledger.status(run); }
    public Map<String, Object> summary(LoggedInInfo viewer, String run) { status(viewer, run); return operatorSummary(run); }
    public Map<String, Object> summary(CdsImportActorDto actor, String run) throws IOException { requireActor(actor, run); return operatorSummary(run); }
    public List<CdsImportPatientDto> patients(LoggedInInfo viewer, String run, String after, int limit) { status(viewer, run); return ledger.patients(run, after, limit); }
    public List<CdsImportPatientDto> patients(CdsImportActorDto actor, String run, String after, int limit) throws IOException { requireActor(actor, run); return ledger.patients(run, after, limit); }
    public List<CdsImportDiagnosticDto> diagnostics(LoggedInInfo viewer, String run, long after, int limit) { status(viewer, run); return ledger.diagnostics(run, after, limit); }
    public List<CdsImportDiagnosticDto> diagnostics(CdsImportActorDto actor, String run, long after, int limit) throws IOException { requireActor(actor, run); return ledger.diagnostics(run, after, limit); }
    public void cancel(LoggedInInfo viewer, String run) {
        CdsImportJobDto job=status(viewer, run); CdsImportActorDto initiator=controlActor(viewer);
        ledger.control(run, initiator, "CANCEL_REQUESTED", () -> {access.requireView(viewer, job); access.requireOperatorSession(viewer); reviews.cancel(run); ledger.cancelParticipating(run); });
    }
    public void cancel(CdsImportActorDto actor, String run) throws IOException { requireActor(actor, run); reviews.cancel(run); ledger.cancel(run); }
    public void resume(LoggedInInfo viewer, String run) {
        CdsImportJobDto job=status(viewer, run); configuration.requireEnabled(); CdsImportActorDto initiator=controlActor(viewer);
        ledger.control(run, initiator, "RESUME_REQUESTED", () -> {access.requireView(viewer, job); access.requireOperatorSession(viewer); requireNoPendingReview(run); ledger.requestResumeParticipating(run); }); schedule(run, true);
    }
    public void resume(CdsImportActorDto actor, String run) throws IOException { requireActor(actor, run); configuration.requireEnabled(); requireNoPendingReview(run); ledger.requestResume(run); schedule(run, true); }
    public Path report(LoggedInInfo viewer, String run, String type) throws IOException { status(viewer, run); return results.report(run, type); }
    public Path report(CdsImportActorDto actor, String run, String type) throws IOException { requireActor(actor, run); return results.report(run, type); }
    public Map<String,Object> diagnosticEvidence(LoggedInInfo viewer,String run,long diagnosticId) throws IOException {
        status(viewer,run);var diagnostic=ledger.diagnosticStatus(run,diagnosticId);
        if(diagnostic.protectedDetailLocation()==null) throw new CdsImportException("IMPORT_FAILURE_EVIDENCE_UNAVAILABLE");
        return failureEvidence.read(run,diagnostic.protectedDetailLocation());
    }

    public void observe(CdsImportActorDto actor, String run, String operation, long osUid, String verifiedNonce) throws IOException {
        requireActor(actor, run); ledger.observe(run, actor.operator(), actor.providerNo(), operation, osUid, verifiedNonce);
    }
    public void resolveReview(LoggedInInfo viewer, String run, CdsImportReviewCommand command) throws IOException {
        status(viewer, run); String review=reviews.accept(viewer, run, command); scheduleReview(review);
    }

    private CdsImportActorDto controlActor(LoggedInInfo viewer) {
        access.requireOperatorSession(viewer); Integer security=viewer.getLoggedInSecurity().getSecurityNo(); String provider=viewer.getLoggedInProviderNo();
        return new CdsImportActorDto("web-"+security, provider, provider, security, viewer.getCurrentFacility()==null?null:viewer.getCurrentFacility().getId(), null);
    }

    private void requireNoPendingReview(String run) { if (reviews.hasPending(run)) throw new CdsImportException("IMPORT_REVIEW_PENDING"); }

    private void requireActor(CdsImportActorDto actor, String run) throws IOException {
        CdsImportJobDto job=ledger.status(run);
        if (actor==null || !Objects.equals(job.operator(), actor.operator()) || !Objects.equals(job.providerNo(), actor.providerNo())) throw new SecurityException("IMPORT_ACCESS_DENIED");
        var document=jobs.load(run); if (!document.actor().equals(actor)) throw new SecurityException("IMPORT_ACCESS_DENIED");
        try { access.context(actor, document.command(), null); }
        catch (SecurityException revoked) { ledger.authorizationStopped(run, ledger.status(run).ownerEpoch()); throw revoked; }
    }
    private Map<String, Object> operatorSummary(String run) {
        Map<String, Object> value=new java.util.LinkedHashMap<>(ledger.summary(run));
        value.putAll(reviews.summary(run));
        value.put("administrativeCleanup", administrativeCleanup); value.put("administrativeAlert", housekeepingAlert); value.put("workerAlert", run.equals(fatalRun.get())?"WORKER_FATAL":null);
        return java.util.Collections.unmodifiableMap(value);
    }

    private void schedule(String run, boolean resume) {
        if (!scheduled.add(run)) return;
        try { worker.execute(() -> { try { if (fatalRun.get()!=null || reviews.hasPending(run)) return; execution.execute(run, "RESUME_REQUESTED".equals(ledger.status(run).state())); } catch (RuntimeException failure) { LOG.error("CDS worker stopped for run {} with code {}", run, failure instanceof CdsImportException known?known.getCode():"IMPORT_SUPERVISION_FAILURE"); } catch (Error fatal) { fatalRun.compareAndSet(null, run); LOG.error("CDS worker terminated for run {}: WORKER_FATAL", run); throw fatal; } finally { scheduled.remove(run); } }); }
        catch (RejectedExecutionException full) { scheduled.remove(run); /* Durable acceptance remains discoverable by the bounded dispatcher. */ }
    }
    private void scheduleReview(String reviewId) {
        String key="review:"+reviewId;
        if (!scheduled.add(key)) return;
        try { worker.execute(() -> {
            try {
                if (fatalRun.get()!=null) return;
                String run=reviews.process(reviewId);
                if (run!=null && !reviews.hasPending(run) && ledger.requestResumeAfterReview(run)) schedule(run, true);
            } catch (Exception failure) { LOG.error("CDS review worker stopped for review {} with code {}", reviewId, failure instanceof CdsImportException known?known.getCode():"IMPORT_REVIEW_FAILED"); }
            finally { scheduled.remove(key); }
        }); } catch (RejectedExecutionException full) { scheduled.remove(key); }
    }

    private void dispatch() {
        try {
            if (!configuration.enabled()) return;
            String failed=fatalRun.get();
            if (failed!=null) { execution.superviseFatal(failed); fatalRun.compareAndSet(failed, null); }
            long now=System.nanoTime();
            if (now-nextHousekeepingNanos>=0) {
                nextHousekeepingNanos=now+TimeUnit.MINUTES.toNanos(1);
                try {
                    administrativeCleanup=cleanup.housekeeping();
                    if (!"COMPLETE".equals(administrativeCleanup.get("state"))) housekeepingAlert="IMPORT_ARTIFACT_CLEANUP_REQUIRES_REVIEW";
                    else if (Boolean.TRUE.equals(administrativeCleanup.get("fullPassComplete"))) housekeepingAlert=null;
                } catch (RuntimeException cleanupFailure) { housekeepingAlert="IMPORT_ARTIFACT_CLEANUP_FAILED"; LOG.error("CDS housekeeping failed: IMPORT_ARTIFACT_CLEANUP_FAILED"); }
            }
            for (String review:reviews.pending(16)) scheduleReview(review);
            for (CdsImportJobDto job:ledger.pending("", 16)) schedule(job.id(), "RESUME_REQUESTED".equals(job.state()));
        } catch (RuntimeException unavailable) { LOG.error("CDS dispatcher could not query durable pending work: IMPORT_DISPATCH_UNAVAILABLE"); }
    }
}
