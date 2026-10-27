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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDiagnosticDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportReview;
import io.github.carlos_emr.carlos.commn.model.CdsImportRun;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportReviewCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable bounded review acceptance and supervised validation; original source and every prior plan remain immutable evidence. */
@Service
public class CdsImportReviewService {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> PENDING=Set.of("REQUESTED", "VALIDATING", "OVERLAY_PUBLISHED", "PATIENT_DECLINED", "REVISION_PUBLISHED");
    private final CdsImportLedgerService ledger;
    private final CdsImportJobService jobs;
    private final CdsImportConfigurationService configuration;
    private final CdsImportAccessService access;
    private final CdsImportReviewDao reviews;
    private final CdsImportRunDao runs;
    private final CdsImportDiagnosticDao diagnostics;
    private final CdsSnapshotService snapshots;
    private final CdsXmlParser parser;
    private final CdsFieldParser fields;
    private final CdsCorrectionLookupService corrections;
    private final CdsImportCoordinatorService coordinator;
    private final CdsUnwrittenRevisionReviewService revisions;
    private final TransactionTemplate transaction;
    @PersistenceContext(unitName="entityManagerFactory") private EntityManager entityManager;

    public CdsImportReviewService(CdsImportLedgerService ledger, CdsImportJobService jobs,
            CdsImportConfigurationService configuration, CdsImportAccessService access, CdsImportReviewDao reviews,
            CdsImportRunDao runs, CdsImportDiagnosticDao diagnostics, CdsSnapshotService snapshots, CdsXmlParser parser,
            CdsFieldParser fields, CdsCorrectionLookupService corrections, CdsImportCoordinatorService coordinator, CdsUnwrittenRevisionReviewService revisions, PlatformTransactionManager manager) {
        this.ledger=ledger; this.jobs=jobs; this.configuration=configuration; this.access=access; this.reviews=reviews;
        this.runs=runs; this.diagnostics=diagnostics; this.snapshots=snapshots; this.parser=parser; this.fields=fields;
        this.corrections=corrections; this.coordinator=coordinator; this.revisions=revisions; transaction=CdsImportLedgerService.template(manager);
    }

    /** HTTP work is bounded to the request and short metadata transaction; XSD replay/plan archive runs on the worker. */
    public String accept(LoggedInInfo viewer, String runId, CdsImportReviewCommand command) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        var document=jobs.load(runId);
        access.requireView(viewer, ledger.status(runId));
        var actor=access.webActor(viewer, document.command());
        if ("CORRECT_VALUE".equals(command.kind()) && (command.sourceXPath()==null || command.sourceXPath().isBlank() || command.replacementValue()==null)) {
            throw new CdsImportException("REVIEW_SCALAR_TARGET_REQUIRED");
        }
        if ("DECLINE_PATIENT".equals(command.kind()) && (command.patientKey()==null || command.patientKey().isBlank())) {
            throw new CdsImportException("REVIEW_IDENTIFIED_PATIENT_REQUIRED");
        }
        byte[] request=JSON.writeValueAsBytes(new Request(actor, command));
        if (request.length>1048576) throw new CdsImportException("REVIEW_REQUEST_CAPACITY_EXCEEDED");
        String id=UUID.randomUUID().toString(), payload=new String(request, StandardCharsets.UTF_8), digest=hash(request);
        return transaction.execute(status -> {
            var run=runs.lock(runId); requireUnwritten(run);
            if (!Set.of("REVIEW_REQUIRED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE").contains(run.getState())) throw new CdsImportException("REVIEW_JOB_NOT_STOPPED");
            if (run.getReviewVersion()!=command.expectedVersion()) throw new CdsImportException("REVIEW_VERSION_CONFLICT");
            if (!reviews.pendingRun(runId, 1).isEmpty()) throw new CdsImportException("REVIEW_VALIDATION_PENDING");
            var diagnostic=diagnostics.lock(command.diagnosticId());
            if (diagnostic==null || !runId.equals(diagnostic.getRunId()) || !diagnostic.isBlocking() || diagnostic.isResolved()
                    || !"PREFLIGHT".equals(diagnostic.getStage())) throw new CdsImportException("REVIEW_DIAGNOSTIC_NOT_ELIGIBLE");
            if (diagnostic.getPatientKey()!=null && !diagnostic.getPatientKey().equals(command.patientKey())) throw new CdsImportException("REVIEW_PATIENT_BINDING_MISMATCH");
            if ("APPROVE_UNWRITTEN_REVISION".equals(command.kind())
                    && (!"SOURCE_REVISION_REVIEW_REQUIRED".equals(diagnostic.getCode()) || diagnostic.getPatientKey()==null
                        || command.replacementValue()!=null && !command.replacementValue().isEmpty())) throw new CdsImportException("REVIEW_REVISION_CONTEXT_REQUIRED");
            if (run.getReviewVersion()==Long.MAX_VALUE) throw new CdsImportException("REVIEW_VERSION_EXHAUSTED");
            var row=new CdsImportReview(); row.setId(id); row.setRunId(runId); row.setReviewVersion(run.getReviewVersion()+1);
            row.setKind(command.kind()); row.setDiagnosticId(command.diagnosticId());
            // Scalar source corrections affect every authorized copy of this
            // accepted source patient. Declines are explicitly copy-specific.
            row.setPatientKey("CORRECT_VALUE".equals(command.kind())?null:blank(command.patientKey()));
            row.setState("REQUESTED"); row.setOperator(actor.operator()); row.setProviderNo(actor.providerNo()); row.setSecurityNo(actor.securityNo());
            row.setCreatedAt(Instant.now()); row.setPayload(payload); row.setPayloadSha256(digest);
            row.setReviewedSourceXPath(command.sourceXPath()); row.setReviewReason(command.reason());
            // Explicitly absent pending proof. These sentinels are never accepted by reset/lookup as validated evidence.
            row.setPlanLocation(""); row.setPlanSha256(""); row.setPlanBytes(0); reviews.persist(row);
            run.setReviewVersion(row.getReviewVersion()); run.setCancelRequested(false); run.setReportState("PENDING");
            ledger.touch(run); ledger.actorEvent(run, actor, "REVIEW_REQUESTED"); return id;
        });
    }

    public List<String> pending(int limit) {
        if (limit<1 || limit>50) throw new IllegalArgumentException("REVIEW_PAGE_INVALID");
        return transaction.execute(status -> reviews.pending(limit).stream().map(CdsImportReview::getId).toList());
    }
    public boolean hasPending(String runId){return transaction.execute(status -> !reviews.pendingRun(runId, 1).isEmpty() || reviews.inactiveDraftCount(runId)>0); }

    /** Healthy supervisor only: retain every published proof and stop automatic fatal retries. */
    public void superviseFatal(String reviewId) throws IOException {
        CdsImportLedgerService.requireNoTransaction(); Snapshot initial=snapshot(reviewId);
        try (var ownership=new ReviewOwnership(initial.run()); var coordination=coordinator.acquire(); FileChannel channel=FileChannel.open(jobs.root(initial.run()).resolve("planning.lock"),
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); FileLock ignored=lock(channel)) {
            if (!PENDING.contains(snapshot(reviewId).state())) return;
            coordination.check(); long owner=ownership.acquired(ledger.claimReviewPlanning(initial.run()));
            transaction.executeWithoutResult(status -> {
                var run=ledger.lockReviewAdministrativeOwned(initial.run(), owner); var row=reviews.lock(reviewId);
                if (row==null || row.getReviewVersion()!=initial.version() || run.getReviewVersion()!=initial.version() || !PENDING.contains(row.getState())) return;
                row.setSafeErrorCode("REVIEW_WORKER_FATAL"); run.setReportState("PENDING");
                if (!Set.of("OVERLAY_PUBLISHED", "PATIENT_DECLINED", "REVISION_PUBLISHED").contains(row.getState())) run.setState("REVIEW_REQUIRED");
                ledger.touch(run); ledger.event(run, null, "REVIEW_WORKER_FATAL");
            });
        }
    }

    /** Participant: facade has reauthorized the explicit Resume initiator before entering this run-locked transaction. */
    public String retryFatal(String runId, CdsImportActorDto currentActor) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("REVIEW_REQUIRES_TRANSACTION");
        var run=runs.lock(runId); if (run==null) throw new CdsImportException("IMPORT_RUN_NOT_FOUND");
        var pending=reviews.pendingRun(runId, 2); if (pending.size()>1) throw new CdsImportException("REVIEW_MULTIPLE_PENDING");
        if (pending.isEmpty()) return null;
        var row=reviews.lock(pending.getFirst().getId());
        if (row==null || !PENDING.contains(row.getState()) || !"REVIEW_WORKER_FATAL".equals(row.getSafeErrorCode())) return null;
        requireCurrent(row, run, run.getReviewVersion()); row.setSafeErrorCode(null); run.setCancelRequested(false); run.setReportState("PENDING");
        ledger.touch(run); ledger.actorEvent(run, currentActor, "REVIEW_FATAL_RETRY_REQUESTED"); return row.getId();
    }
    public Map<String, Object> summary(String runId) {
        return transaction.execute(status -> {
            var rows=reviews.pendingRun(runId, 2); var latest=reviews.latest(runId);
            long drafts=reviews.inactiveDraftCount(runId);
            Map<String, Object> value=new LinkedHashMap<>(); value.put("pendingReviewCount", rows.size()); value.put("inactiveDraftCount", drafts);
            value.put("reviewState", latest==null?"IDLE":latest.getState()); value.put("reviewId", latest==null?null:latest.getId());
            value.put("reviewErrorCode", latest==null?null:latest.getSafeErrorCode());
            value.put("unwrittenRevisionApprovalAllowed", rows.isEmpty() && drafts==0 && revisionRequestAllowed(runs.get(runId)));
            return value;
        });
    }

    /** Availability to request review; the worker still validates immutable lineage and evidence under ownership. */
    private boolean revisionRequestAllowed(CdsImportRun run) {
        return run!=null && Set.of("REVIEW_REQUIRED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE").contains(run.getState())
                && run.getReviewVersion()!=Long.MAX_VALUE && run.getManifestDigest()==null && run.getExecutionDigest()==null
                && diagnostics.hasUnwrittenRevisionContext(run.getId()) && !hasWrittenOrUncertainWork(run.getId());
    }
    public void cancel(String runId) {
        transaction.executeWithoutResult(status -> {
            if (runs.lock(runId)==null) throw new CdsImportException("IMPORT_RUN_NOT_FOUND");
            for (var pending:reviews.pendingRun(runId, 2)) {
                var row=reviews.lock(pending.getId());
                if (Set.of("REQUESTED", "VALIDATING").contains(row.getState())) {row.setState("CANCELLED"); row.setSafeErrorCode("REVIEW_CANCELLED"); }
            }
        });
    }

    /** Returns the run ready for fresh complete preflight, never a declaration that the import is complete. */
    public String process(String reviewId) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        Snapshot initial=snapshot(reviewId);
        // Ownership encloses failure recording: retain the fence until the stopped outcome is durable.
        try (var ownership=new ReviewOwnership(initial.run())) {
            return processOwned(reviewId, initial, ownership);
        }
    }

    private String processOwned(String reviewId, Snapshot initial, ReviewOwnership ownership) throws IOException {
        long epoch=-1;
        try (var coordination=coordinator.acquire(); FileChannel channel=FileChannel.open(jobs.root(initial.run()).resolve("planning.lock"), Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); FileLock ignored=lock(channel)) {
            var document=jobs.load(initial.run());
            Snapshot current=snapshot(reviewId);
            if (!PENDING.contains(current.state())) throw new CdsImportException("REVIEW_NOT_PENDING");
            Request request=request(current);
            boolean published=Set.of("OVERLAY_PUBLISHED", "PATIENT_DECLINED", "REVISION_PUBLISHED").contains(current.state());
            if (!published) access.context(request.actor(), document.command(), null);
            coordination.check(); epoch=ownership.acquired(ledger.claimReviewPlanning(current.run()));
            long owner=epoch;
            if (published) {
                verifyArchive(current, document.jobRoot());
                coordination.check();
                ledger.resetUnsealedPlan(current.run(), epoch, current.version());
                ready(reviewId, current.run(), epoch, current.version()); completeCancelledReset(current.run(), epoch); return current.run();
            }
            transaction.executeWithoutResult(status -> {
                var run=ledger.lockPlanningOwned(current.run(), owner); requireUnwritten(run);
                var row=reviews.lock(reviewId); requireCurrent(row, run, current.version());
                row.setState("VALIDATING"); ledger.touch(run); ledger.event(run, null, "REVIEW_VALIDATING");
            });
            Cancellation cancelled=new Cancellation(current.run(), coordination);
            check(cancelled);
            var snapshot=jobs.snapshot(current.run(), document);
            if ("APPROVE_UNWRITTEN_REVISION".equals(current.kind())) {
                return publishRevision(current, request, document, snapshot, owner, cancelled, coordination);
            }
            Target target=target(current, request, snapshot, document.jobRoot());
            Path evidenceRoot=directory(document.jobRoot().resolve("reviews"), document.jobRoot());
            Path attempt=CdsPaths.privateDirectory(evidenceRoot.resolve("review-"+reviewId+"-"+UUID.randomUUID()), evidenceRoot);
            write(attempt.resolve("request.json"), attempt, JSON.writeValueAsBytes(request));
            Archive archive=archive(current.run(), owner, current.version(), attempt, cancelled);
            transaction.executeWithoutResult(status -> {
                var run=ledger.lockPlanningOwned(current.run(), owner); var row=reviews.lock(reviewId); requireCurrent(row, run, current.version());
                row.setPlanLocation(archive.path().toString()); row.setPlanSha256(archive.sha256()); row.setPlanBytes(archive.bytes());
            });
            CdsCorrectionSetDto overlay=null;
            if ("CORRECT_VALUE".equals(current.kind())) {
                overlay=corrected(current, request, target, document.jobRoot());
                CdsCorrectionSetDto proposed=overlay;
                transaction.executeWithoutResult(status -> {
                    var run=ledger.lockPlanningOwned(current.run(), owner); var row=reviews.lock(reviewId); requireCurrent(row, run, current.version());
                    row.setManifestLocation(proposed.manifestFile().toString()); row.setManifestSha256(proposed.sha256());
                    row.setSourceKey(CdsCorrectionLookupService.sourceKey(target.xml().relativeName(), target.xml().sha256()));
                    row.setSourceXmlSha256(target.xml().sha256()); row.setSourcePatientId(target.sourcePatientId()); row.setCategory(target.record().section());
                });
                parser.parse(target.xml().path(), attempt.resolve("validated-source"), configuration.limits(), cancelled, record -> { }, overlay);
            } else {
                if (target.sourcePatientId()==null || current.patient()==null || !Files.isRegularFile(target.folder().resolve("parse.seal"), LinkOption.NOFOLLOW_LINKS)) {
                    throw new CdsImportException("REVIEW_SOURCE_INVENTORY_UNVERIFIED");
                }
                var patient=ledger.patientStatus(current.run(), current.patient());
                if (!target.sourcePatientId().equals(patient.sourcePatientId())) throw new CdsImportException("REVIEW_PATIENT_BINDING_MISMATCH");
                // Decline is a visible patient exclusion. It cannot declare malformed/unknown XML parsed.
                parser.parse(target.xml().path(), attempt.resolve("validated-source"), configuration.limits(), cancelled, record -> { },
                        corrections.find(current.run(), target.xml().relativeName(), target.xml().sha256(), target.folder()));
            }
            List<String> approvedOrigins=overlay==null?List.of():requireOriginApproval(current, request, target, overlay, epoch);
            check(cancelled); coordination.check(); access.context(request.actor(), document.command(), null);
            CdsCorrectionSetDto selected=overlay;
            transaction.executeWithoutResult(status -> {
                var run=ledger.lockPlanningOwned(current.run(), owner); requireUnwritten(run);
                var row=reviews.lock(reviewId); requireCurrent(row, run, current.version());
                if (run.isCancelRequested() || !"VALIDATING".equals(row.getState())) throw new CdsImportException("REVIEW_CANCELLED");
                row.setSourceKey(CdsCorrectionLookupService.sourceKey(target.xml().relativeName(), target.xml().sha256()));
                row.setSourceXmlSha256(target.xml().sha256()); row.setSourcePatientId(target.sourcePatientId());
                row.setCategory(target.record()==null?null:target.record().section());
                row.setPlanLocation(archive.path().toString()); row.setPlanSha256(archive.sha256()); row.setPlanBytes(archive.bytes());
                if (selected!=null){row.setManifestLocation(selected.manifestFile().toString()); row.setManifestSha256(selected.sha256()); }
                row.setState(selected==null?"PATIENT_DECLINED":"OVERLAY_PUBLISHED"); row.setAppliedAt(Instant.now());
                if (selected!=null) approveCarriedOrigins(request, approvedOrigins);
                ledger.touch(run); ledger.actorEvent(run, request.actor(), selected==null?"PATIENT_DECLINE_REVIEWED":"CORRECTION_OVERLAY_PUBLISHED");
            });
            ledger.resetUnsealedPlan(current.run(), epoch, current.version());
            ready(reviewId, current.run(), epoch, current.version()); completeCancelledReset(current.run(), epoch); return current.run();
        } catch (Exception failure) {
            String code=failure instanceof CdsImportException known?known.getCode():failure instanceof io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException input?input.code():failure instanceof SecurityException?"REVIEW_AUTHORIZATION_CHANGED":"REVIEW_VALIDATION_FAILED";
            boolean busy=Set.of("IMPORT_BUSY", "IMPORT_COORDINATOR_BUSY", "IMPORT_COORDINATOR_UNAVAILABLE", "IMPORT_COORDINATOR_LOST", "REVIEW_PLANNING_BUSY", "OWNER_FENCED").contains(code);
            if (!busy) reject(initial, epoch, code);
            if ("REVIEW_CANCELLED".equals(code) && epoch>=0) {
                try {ledger.endPreflight(initial.run(), epoch, "CANCELLED_BEFORE_WRITE", code); }
                catch (CdsImportException fenced){ /* A newer explicit owner retains its own outcome. */ }
            }
            if (failure instanceof IOException io) throw io;
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new CdsImportException("REVIEW_VALIDATION_FAILED", false, failure);
        }
    }

    private String publishRevision(Snapshot current, Request request, CdsImportJobDocumentDto document, CdsSnapshotDto snapshot,
            long epoch, Cancellation cancelled, CdsImportCoordinatorService.Owner coordination) throws IOException {
        DiagnosticContext context=transaction.execute(status -> {
            var diagnostic=diagnostics.get(current.diagnostic());
            if (diagnostic==null || !current.run().equals(diagnostic.getRunId()) || !diagnostic.isBlocking() || diagnostic.isResolved()
                    || !"PREFLIGHT".equals(diagnostic.getStage()) || !"SOURCE_REVISION_REVIEW_REQUIRED".equals(diagnostic.getCode())
                    || !java.util.Objects.equals(current.patient(), diagnostic.getPatientKey())) throw new CdsImportException("REVIEW_REVISION_CONTEXT_REQUIRED");
            return new DiagnosticContext(diagnostic.getProtectedDetailLocation(), diagnostic.getSourcePath());
        });
        String location=context.location();
        if (location==null || location.isBlank()) throw new CdsImportException("REVIEW_REVISION_CONTEXT_REQUIRED");
        Path evidenceRoot=directory(document.jobRoot().resolve("reviews"), document.jobRoot());
        Path attempt=CdsPaths.privateDirectory(evidenceRoot.resolve("review-"+current.id()+"-"+UUID.randomUUID()), evidenceRoot);
        write(attempt.resolve("request.json"), attempt, JSON.writeValueAsBytes(request));
        var selected=revisions.prepare(current.run(), epoch, current.patient(), location, document, snapshot, attempt, cancelled);
        Archive archive=archive(current.run(), epoch, current.version(), attempt, cancelled, selected.artifacts());
        check(cancelled); coordination.check(); access.context(request.actor(), document.command(), null);
        transaction.executeWithoutResult(status -> {
            var run=ledger.lockPlanningOwned(current.run(), epoch); requireUnwritten(run);
            var row=reviews.lock(current.id()); requireCurrent(row, run, current.version());
            if (run.isCancelRequested() || !"VALIDATING".equals(row.getState())) throw new CdsImportException("REVIEW_CANCELLED");
            revisions.approve(current.run(), epoch, current.version(), selected);
            row.setSourceKey(CdsCorrectionLookupService.sourceKey(selected.source().relativeName(), selected.source().sha256()));
            row.setSourceXmlSha256(selected.source().sha256()); row.setSourcePatientId(selected.revision().sourcePatientId());
            row.setReviewedSourceXPath(selected.recordXPath()); row.setCategory(null);
            row.setPlanLocation(archive.path().toString()); row.setPlanSha256(archive.sha256()); row.setPlanBytes(archive.bytes());
            row.setManifestLocation(attempt.resolve("lineage-approval.json").toString());
            row.setManifestSha256((String) selected.artifacts().get("lineage-approval.json").get("sha256"));
            row.setState("REVISION_PUBLISHED"); row.setAppliedAt(Instant.now()); row.setSafeErrorCode(null);
            ledger.touch(run); ledger.actorEvent(run, request.actor(), "UNWRITTEN_REVISION_REVIEWED");
        });
        ledger.resetUnsealedPlan(current.run(), epoch, current.version());
        ready(current.id(), current.run(), epoch, current.version()); completeCancelledReset(current.run(), epoch); return current.run();
    }

    private void ready(String id, String run, long epoch, long version) {
        transaction.executeWithoutResult(status -> {
            var managed=ledger.lockPlanningOwned(run, epoch); var row=reviews.lock(id); requireCurrent(row, managed, version);
            row.setState("CORRECT_VALUE".equals(row.getKind())?"OVERLAY_READY":"APPROVE_UNWRITTEN_REVISION".equals(row.getKind())?"REVISION_READY":"DECLINE_READY"); ledger.touch(managed);
            row.setSafeErrorCode(null);
        });
    }
    private void completeCancelledReset(String run, long epoch) {
        if (ledger.status(run).cancelRequested()) ledger.endPreflight(run, epoch, "CANCELLED_BEFORE_WRITE", "REVIEW_CANCELLED");
    }
    private void reject(Snapshot expected, long epoch, String code) {
        transaction.executeWithoutResult(status -> {
            CdsImportRun job;
            if (epoch>=0) {
                try {job=ledger.lockPlanningOwned(expected.run(), epoch); }
                catch (CdsImportException fenced){if ("OWNER_FENCED".equals(fenced.getCode())) return; throw fenced; }
            } else job=runs.lock(expected.run());
            if (job==null || job.getReviewVersion()!=expected.version() || (epoch<0 && job.getOwnerEpoch()!=expected.ownerEpoch())) return;
            var row=reviews.lock(expected.id()); if (row==null || row.getReviewVersion()!=expected.version() || !PENDING.contains(row.getState())) return;
            // A published proof/reset may be resumed; never discard it because later administrative retirement failed.
            if (Set.of("OVERLAY_PUBLISHED", "PATIENT_DECLINED", "REVISION_PUBLISHED").contains(row.getState())) {
                row.setSafeErrorCode(code.matches("[A-Z0-9_]{1,96}")?code:"REVIEW_RETIREMENT_REQUIRES_RECONCILIATION"); return;
            }
            boolean draft=Set.of("CDS_XML_SCHEMA_INVALID", "REVIEW_OTHER_OPERATOR_CONFIRMATION_REQUIRED").contains(code)
                    && row.getManifestLocation()!=null && row.getSourceKey()!=null;
            row.setState("REVIEW_CANCELLED".equals(code)?"CANCELLED":draft?"DRAFT_REQUIRES_CORRECTION":"REJECTED");
            row.setSafeErrorCode(code.matches("[A-Z0-9_]{1,96}")?code:"REVIEW_VALIDATION_FAILED");
            if (job.getManifestDigest()==null){job.setState("REVIEW_REQUIRED"); ledger.touch(job); }
        });
    }

    private Target target(Snapshot review, Request request, CdsSnapshotDto source, Path jobRoot) throws IOException {
        DiagnosticContext diagnostic=transaction.execute(status -> {
            var value=diagnostics.get(review.diagnostic());
            if (value==null || !review.run().equals(value.getRunId())) throw new CdsImportException("REVIEW_DIAGNOSTIC_NOT_ELIGIBLE");
            return new DiagnosticContext(value.getProtectedDetailLocation(), value.getSourcePath());
        });
        String detail=diagnostic.location();
        if (detail==null) throw new CdsImportException("REVIEW_SOURCE_CONTEXT_MISSING");
        if ("CORRECT_VALUE".equals(review.kind()) && diagnostic.xpath()!=null && diagnostic.xpath().startsWith("/OmdCds[")
                && !request.command().sourceXPath().equals(diagnostic.xpath())
                && !request.command().sourceXPath().startsWith(diagnostic.xpath()+"/")) throw new CdsImportException("REVIEW_DIAGNOSTIC_TARGET_MISMATCH");
        Path location=CdsPaths.within(Path.of(detail), jobRoot.resolve("parsed"));
        Path folder=Files.isDirectory(location, LinkOption.NOFOLLOW_LINKS)?location:location.getParent();
        String folderName=folder.getFileName().toString();
        CdsSnapshotFileDto[] selected={null};
        snapshots.forEachXml(source, xml -> {
            String base=CdsImportKeys.hash(xml.relativeName(), xml.sha256());
            if (folderName.equals(base) || folderName.matches(java.util.regex.Pattern.quote(base)+"-attempt-[0-9a-f-]{36}")) {
                if (selected[0]!=null) throw new CdsImportException("REVIEW_SOURCE_CONTEXT_AMBIGUOUS"); selected[0]=xml;
            }
        });
        if (selected[0]==null) throw new CdsImportException("REVIEW_ACCEPTED_SOURCE_MISMATCH");
        CdsRecordDto[] record={null}; CdsFieldDto[] field={null}; String[] sourcePatient={null};
        fields.forEachRecord(folder.resolve("records.jsonl"), candidate -> {
            try {
                if ("Demographics".equals(candidate.section())) sourcePatient[0]=fields.firstToken(candidate, "UniqueVendorIdSequence", 128);
                if (request.command().sourceXPath()==null || !request.command().sourceXPath().startsWith(candidate.sourceXPath()+"/")) return;
                fields.forEachField(candidate, value -> {
                    if ((candidate.sourceXPath()+"/"+value.path()).equals(request.command().sourceXPath())) {
                        if (field[0]!=null) throw new CdsImportException("REVIEW_SCALAR_TARGET_AMBIGUOUS"); record[0]=candidate; field[0]=value;
                    }
                });
            } catch (IOException failure){throw new UncheckedIOException(failure); }
        });
        if ("CORRECT_VALUE".equals(review.kind())) {
            if (record[0]==null || field[0]==null || "BINARY".equals(field[0].kind())) throw new CdsImportException("REVIEW_SCALAR_TARGET_REQUIRED");
            if (CdsFieldParser.unindexed(field[0].path()).endsWith("UniqueVendorIdSequence")
                    || ("ATTRIBUTE".equals(field[0].kind()) && !field[0].namespace().isEmpty())) throw new CdsImportException("REVIEW_STRUCTURAL_IDENTITY_IMMUTABLE");
            fields.verifyRecord(record[0]);
        }
        return new Target(selected[0], folder, record[0], field[0], sourcePatient[0]);
    }

    private CdsCorrectionSetDto corrected(Snapshot review, Request request, Target target, Path jobRoot) throws IOException {
        String key=CdsCorrectionLookupService.sourceKey(target.xml().relativeName(), target.xml().sha256());
        Path root=directory(jobRoot.resolve("corrections"), jobRoot), source=directory(root.resolve(key), root);
        Path revision=CdsPaths.privateDirectory(source.resolve("revision-"+review.id()+"-"+UUID.randomUUID()), source);
        CdsCorrectionSetDto prior=corrections.find(review.run(), target.xml().relativeName(), target.xml().sha256(), target.folder());
        CdsCorrectionSetDto draft=transaction.execute(status -> {
            var row=reviews.latestDraft(review.run(), key);
            var active=reviews.latestCorrection(review.run(), key);
            // A stale inactive proposal cannot replace a newer published set.
            if (row!=null && active!=null && row.getReviewVersion()<=active.getReviewVersion()) return null;
            return row==null?null:new CdsCorrectionSetDto(Path.of(row.getManifestLocation()), row.getManifestSha256(), row.getSourceXmlSha256());
        });
        if (draft!=null) CdsPaths.within(draft.manifestFile(), source);
        // Drafts remain inactive. Publication below additionally requires every
        // carried unvalidated scalar to belong to this reviewer or be explicitly
        // re-entered by them; another operator's proposal never gains approval silently.
        if (draft!=null) prior=draft;
        Path directory=revision.resolve("corrections"); ArrayNode entries=JSON.createArrayNode();
        if (prior!=null) {
            var copied=io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsCorrectionFiles.stage(prior, revision);
            JsonNode document=JSON.readTree(CdsImportJobService.boundedFile(copied.manifestFile(), 1048576));
            for (JsonNode entry:document.path("corrections")) {
                if (!entry.path("sourceXPath").asText().equals(request.command().sourceXPath()) || !entry.path("namespace").asText().equals(target.field().namespace())) entries.add(entry);
            }
        } else CdsPaths.privateDirectory(directory, revision);
        if (entries.size()>=128) throw new CdsImportException("REVIEW_CORRECTION_CAPACITY_EXCEEDED");
        ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(request.command().replacementValue()));
        byte[] value=new byte[encoded.remaining()]; encoded.get(value);
        if (value.length>262144) throw new CdsImportException("REVIEW_REPLACEMENT_CAPACITY_EXCEEDED");
        String digest=hash(value), name="value-"+digest+".utf8";
        if (!Files.exists(directory.resolve(name), LinkOption.NOFOLLOW_LINKS)) write(directory.resolve(name), directory, value);
        ObjectNode entry=JSON.createObjectNode().put("sourceXPath", request.command().sourceXPath()).put("namespace", target.field().namespace())
                .put("reviewId", review.id())
                .put("kind", target.field().kind()).put("schemaType", target.field().schemaType()).put("whitespace", target.field().whitespace())
                .put("originalSha256", target.field().sha256()).put("originalLength", target.field().length())
                .put("replacementFile", name).put("replacementSha256", digest).put("replacementBytes", value.length);
        entries.add(entry);
        byte[] bytes=JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1).put("acceptedXmlSha256", target.xml().sha256()).set("corrections", entries));
        if (bytes.length>1048576) throw new CdsImportException("REVIEW_MANIFEST_CAPACITY_EXCEEDED");
        Path manifest=directory.resolve(hash(bytes)+".json"); write(manifest, directory, bytes); CdsPaths.forceDirectory(revision);
        return new CdsCorrectionSetDto(manifest, hash(bytes), target.xml().sha256());
    }

    private List<JsonNode> manifestEntries(CdsCorrectionSetDto reference) throws IOException {
        byte[] bytes=CdsImportJobService.boundedFile(reference.manifestFile(), 1048576);
        if (!hash(bytes).equals(reference.sha256())) throw new CdsImportException("REVIEW_MANIFEST_CHANGED");
        JsonNode document=JSON.readTree(bytes), entries=document.path("corrections");
        if (!reference.acceptedXmlSha256().equals(document.path("acceptedXmlSha256").asText()) || !entries.isArray() || entries.size()>128) throw new CdsImportException("REVIEW_MANIFEST_CHANGED");
        List<JsonNode> values=new ArrayList<>(); entries.forEach(values::add); return values;
    }
    private List<String> requireOriginApproval(Snapshot review, Request request, Target target, CdsCorrectionSetDto selected, long epoch) throws IOException {
        List<JsonNode> entries=manifestEntries(selected); Map<String, JsonNode> prior=new LinkedHashMap<>();
        for (JsonNode entry:entries) {
            String id=entry.path("reviewId").asText();
            if (!id.matches("[0-9a-f-]{36}")) throw new CdsImportException("REVIEW_PROVENANCE_MISSING");
            if (!review.id().equals(id)) prior.put(id, entry);
        }
        var origins=transaction.execute(status -> reviews.origins(review.run(), prior.keySet()));
        if (origins.size()!=prior.size()) throw new CdsImportException("REVIEW_PROVENANCE_MISSING");
        boolean unapproved=false; List<String> approved=new ArrayList<>();
        for (var origin:origins) {
            JsonNode entry=prior.get(origin.reviewId());
            if (!CdsCorrectionLookupService.sourceKey(target.xml().relativeName(), target.xml().sha256()).equals(origin.sourceKey())
                    || !entry.path("sourceXPath").asText().equals(origin.sourceXPath())) throw new CdsImportException("REVIEW_PROVENANCE_CHANGED");
            boolean active=Set.of("OVERLAY_READY", "REVALIDATED").contains(origin.state());
            if (!active && !"DRAFT_REQUIRES_CORRECTION".equals(origin.state())) throw new CdsImportException("REVIEW_PROVENANCE_NOT_VALIDATED");
            boolean same=java.util.Objects.equals(origin.operator(), request.actor().operator())
                    && java.util.Objects.equals(origin.securityNo(), request.actor().securityNo());
            if (!active && !same) {
                unapproved=true;
                ledger.diagnostic(review.run(), epoch, null, null, "ERROR", "REVIEW_SCALAR_CONFIRMATION_REQUIRED", "PREFLIGHT", origin.sourceXPath(),
                        "Another operator's inactive scalar proposal must be explicitly re-entered or replaced before publication", target.folder().resolve("parse-result.json").toString(), true);
            } else if (!active) approved.add(origin.reviewId());
        }
        if (unapproved) throw new CdsImportException("REVIEW_OTHER_OPERATOR_CONFIRMATION_REQUIRED");
        return List.copyOf(approved);
    }
    /** Participant in the publication transaction, called only after full XSD proof and origin approval. */
    private void approveCarriedOrigins(Request request, List<String> ids) {
            for (String id:ids) {
                var row=reviews.lock(id);
                if (row!=null && "DRAFT_REQUIRES_CORRECTION".equals(row.getState())
                        && java.util.Objects.equals(row.getOperator(), request.actor().operator())
                        && java.util.Objects.equals(row.getSecurityNo(), request.actor().securityNo())) {
                    row.setState("OVERLAY_READY"); row.setAppliedAt(Instant.now()); row.setSafeErrorCode(null);
                }
            }
    }

    private Archive archive(String run, long epoch, long version, Path directory, BooleanSupplier cancelled) throws IOException {
        return archive(run, epoch, version, directory, cancelled, Map.of());
    }
    private Archive archive(String run, long epoch, long version, Path directory, BooleanSupplier cancelled,
            Map<String, Map<String, Object>> proofArtifacts) throws IOException {
        Path file=directory.resolve("prior-plan.jsonl"); long patientCount=0, unitCount=0, dependencyCount=0;
        try (OutputStream output=CdsPaths.newPrivateFile(file, directory)) {
            line(output, Map.of("type", "HEADER", "runId", run, "reviewVersion", version));
            if (!proofArtifacts.isEmpty()) line(output, Map.of("type", "REVIEW_PROOF", "artifacts", proofArtifacts));
            String patientAfter="";
            while (true) {
                check(cancelled); var patients=ledger.patients(run, patientAfter, 100); if (patients.isEmpty()) break;
                for (var patient:patients) {
                    line(output, Map.of("type", "PATIENT", "data", patient)); patientCount++;
                    String after="";
                    while (true) {
                        check(cancelled); var units=ledger.units(run, patient.patientKey(), after, 100); if (units.isEmpty()) break;
                        for (var unit:units){line(output, Map.of("type", "UNIT", "data", unit)); unitCount++; after=unit.unitKey(); }
                    }
                    patientAfter=patient.patientKey();
                }
            }
            String after="";
            while (true) {
                check(cancelled); var dependencies=ledger.dependencyEvidence(run, after, 100); if (dependencies.isEmpty()) break;
                for (var dependency:dependencies){line(output, Map.of("type", "DEPENDENCY", "data", dependency)); dependencyCount++; after=dependency.id(); }
            }
            line(output, Map.of("type", "FOOTER", "patients", patientCount, "units", unitCount, "dependencies", dependencyCount));
        }
        var actual=transaction.execute(status -> {
            var owned=ledger.lockPlanningOwned(run, epoch);
            if (owned.getReviewVersion()!=version) throw new CdsImportException("REVIEW_VERSION_CONFLICT");
            long patients=entityManager.createQuery("select count(p) from CdsImportPatient p where p.runId=:run", Long.class).setParameter("run", run).getSingleResult();
            long units=entityManager.createQuery("select count(u) from CdsImportUnit u where u.runId=:run", Long.class).setParameter("run", run).getSingleResult();
            long dependencies=entityManager.createQuery("select count(d) from CdsImportDependency d where d.runId=:run", Long.class).setParameter("run", run).getSingleResult();
            return new long[]{patients, units, dependencies};
        });
        if (actual[0]!=patientCount || actual[1]!=unitCount || actual[2]!=dependencyCount) throw new CdsImportException("REVIEW_PLAN_ARCHIVE_INCOMPLETE");
        CdsPaths.forceFile(file); CdsPaths.forceDirectory(directory); return new Archive(file, hash(file), Files.size(file));
    }

    private void verifyArchive(Snapshot review, Path root) throws IOException {
        if (review.plan()==null || review.plan().isBlank() || review.planHash()==null || !review.planHash().matches("[0-9a-f]{64}")) throw new CdsImportException("REVIEW_PLAN_PROOF_MISSING");
        Path file=CdsPaths.within(Path.of(review.plan()), root.resolve("reviews"));
        if (Files.size(file)!=review.planBytes() || !hash(file).equals(review.planHash())) throw new CdsImportException("REVIEW_PLAN_PROOF_CHANGED");
        try (var reader=Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            boundedLine(reader); String second=boundedLine(reader);
            if (second==null) throw new CdsImportException("REVIEW_PLAN_PROOF_CHANGED");
            JsonNode row=JSON.readTree(second);
            if ("REVIEW_PROOF".equals(row.path("type").asText())) {
                var artifacts=row.path("artifacts");
                if (!artifacts.isObject() || artifacts.size()<1 || artifacts.size()>16) throw new CdsImportException("REVIEW_PLAN_PROOF_CHANGED");
                var names=artifacts.fieldNames();
                while (names.hasNext()) {
                    String name=names.next(); JsonNode evidence=artifacts.path(name);
                    if (!name.matches("[A-Za-z0-9_-]{1,100}\\.[A-Za-z0-9]{1,12}") || !evidence.path("sha256").asText().matches("[0-9a-f]{64}")
                            || !evidence.path("bytes").isIntegralNumber() || !evidence.path("bytes").canConvertToLong() || evidence.path("bytes").longValue()<0) throw new CdsImportException("REVIEW_PLAN_PROOF_CHANGED");
                    Path artifact=CdsPaths.within(file.getParent().resolve(name), file.getParent());
                    if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS) || Files.size(artifact)!=evidence.path("bytes").longValue()
                            || !hash(artifact).equals(evidence.path("sha256").asText())) throw new CdsImportException("REVIEW_PLAN_PROOF_CHANGED");
                }
            }
        }
    }
    private static String boundedLine(java.io.BufferedReader reader) throws IOException {
        StringBuilder line=new StringBuilder(); int value;
        while ((value=reader.read())!=-1) {
            if (value=='\n') return line.toString();
            if (line.length()==65536) throw new CdsImportException("REVIEW_PLAN_PROOF_CHANGED"); line.append((char) value);
        }
        return line.isEmpty()?null:line.toString();
    }
    private Snapshot snapshot(String id) {
        return transaction.execute(status -> {
            var row=reviews.get(id); if (row==null) throw new CdsImportException("REVIEW_NOT_FOUND");
            var run=runs.get(row.getRunId()); if (run==null) throw new CdsImportException("IMPORT_RUN_NOT_FOUND");
            return new Snapshot(row.getId(), row.getRunId(), row.getReviewVersion(), run.getOwnerEpoch(), row.getKind(), row.getState(), row.getDiagnosticId(), row.getPatientKey(),
                    row.getPayload(), row.getPayloadSha256(), row.getPlanLocation(), row.getPlanSha256(), row.getPlanBytes());
        });
    }
    private Request request(Snapshot review) throws IOException {
        byte[] bytes=review.payload().getBytes(StandardCharsets.UTF_8);
        if (bytes.length>1048576 || !hash(bytes).equals(review.payloadHash())) throw new CdsImportException("REVIEW_REQUEST_CHANGED");
        return JSON.readValue(bytes, Request.class);
    }
    private void requireCurrent(CdsImportReview row, CdsImportRun run, long version) {
        if (row==null || row.getReviewVersion()!=version || run.getReviewVersion()!=version || !row.getRunId().equals(run.getId())) throw new CdsImportException("REVIEW_VERSION_CONFLICT");
    }
    private void requireUnwritten(CdsImportRun run) {
        if (run==null || run.getManifestDigest()!=null || run.getExecutionDigest()!=null) throw new CdsImportException("REVIEW_AFTER_SEAL_REFUSED");
        if (hasWrittenOrUncertainWork(run.getId())) throw new CdsImportException("REVIEW_AFTER_WRITE_REQUIRES_RECONCILIATION");
    }
    private boolean hasWrittenOrUncertainWork(String runId) {
        boolean touched=!entityManager.createQuery("select p.id from CdsImportPatient p where p.runId=:run and (p.held=true or p.targetDemographicNo is not null or p.state='RECONCILIATION_REQUIRED' or exists(select l.id from CdsImportLineage l where l.id=p.lineageKey and l.targetDemographicNo is not null) or exists(select c.id from CdsImportCheckpoint c where c.lineageKey=p.lineageKey))", String.class)
                .setParameter("run", runId).setMaxResults(1).getResultList().isEmpty();
        return touched || !entityManager.createQuery("select u.id from CdsImportUnit u where u.runId=:run and u.state<>'EXPECTED'", String.class)
                .setParameter("run", runId).setMaxResults(1).getResultList().isEmpty();
    }
    private static FileLock lock(FileChannel channel) throws IOException {
        try {var lock=channel.tryLock(); if (lock==null) throw new CdsImportException("REVIEW_PLANNING_BUSY"); return lock; }
        catch (java.nio.channels.OverlappingFileLockException busy){throw new CdsImportException("REVIEW_PLANNING_BUSY"); }
    }
    private static Path directory(Path path, Path root) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {try {return CdsPaths.privateDirectory(path, root); }catch (java.nio.file.FileAlreadyExistsException concurrent){ /* Validate below. */ }}
        Path selected=CdsPaths.within(path, root); if (!Files.isDirectory(selected, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw new CdsImportException("REVIEW_PRIVATE_STORAGE_REQUIRED"); return selected;
    }
    private static void write(Path path, Path root, byte[] bytes) throws IOException {
        try (OutputStream output=CdsPaths.newPrivateFile(path, root)){output.write(bytes); }CdsPaths.forceFile(path); CdsPaths.forceDirectory(root);
    }
    private static void line(OutputStream output, Object row) throws IOException {output.write(JSON.writeValueAsBytes(row)); output.write('\n'); }
    private static String hash(byte[] bytes){return CdsPaths.hex(CdsPaths.sha256().digest(bytes)); }
    private static String hash(Path path) throws IOException {
        var hash=CdsPaths.sha256(); try (InputStream input=Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[65536]; int count; while ((count=input.read(buffer))>=0) hash.update(buffer, 0, count); }return CdsPaths.hex(hash.digest());
    }
    private static String blank(String value){return value==null || value.isBlank()?null:value; }
    private static void check(BooleanSupplier cancelled){if (cancelled.getAsBoolean()) throw new CdsImportException("REVIEW_CANCELLED"); }
    /** Try-with-resources preserves every primary failure and reports a failed normal retirement. */
    private final class ReviewOwnership implements AutoCloseable {
        private final String run;
        private long epoch=-1;
        ReviewOwnership(String run){this.run=run; }
        long acquired(long value){epoch=value; return value; }
        @Override public void close() {
            if (epoch<0) return;
            try {ledger.release(run, epoch); }
            catch (CdsImportException failure){if (!"OWNER_FENCED".equals(failure.getCode())) throw failure; }
        }
    }
    private final class Cancellation implements BooleanSupplier {
        private final String run; private final CdsImportCoordinatorService.Owner owner; private long checked; private boolean cancelled;
        Cancellation(String run, CdsImportCoordinatorService.Owner owner){this.run=run; this.owner=owner; }
        @Override public boolean getAsBoolean(){if (Thread.currentThread().isInterrupted()) return true; long now=System.nanoTime(); if (now-checked>250_000_000L){owner.check(); cancelled=ledger.status(run).cancelRequested(); checked=now; }return cancelled; }
    }
    private record Request(CdsImportActorDto actor, CdsImportReviewCommand command){@Override public String toString(){return "ReviewRequest[protected]"; }}
    private record Snapshot(String id, String run, long version, long ownerEpoch, String kind, String state, long diagnostic, String patient, String payload, String payloadHash, String plan, String planHash, long planBytes){@Override public String toString(){return "ReviewSnapshot[protected]"; }}
    private record Target(CdsSnapshotFileDto xml, Path folder, CdsRecordDto record, CdsFieldDto field, String sourcePatientId){@Override public String toString(){return "ReviewTarget[protected]"; }}
    private record Archive(Path path, String sha256, long bytes){@Override public String toString(){return "ReviewArchive[protected]"; }}
    private record DiagnosticContext(@org.jspecify.annotations.Nullable String location, @org.jspecify.annotations.Nullable String xpath){@Override public String toString(){return "ReviewDiagnosticContext[protected]"; }}
}
