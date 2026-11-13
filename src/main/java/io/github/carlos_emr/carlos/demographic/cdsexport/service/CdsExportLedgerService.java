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

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportEventDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportHeldPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportAttemptDao;
import io.github.carlos_emr.carlos.commn.model.CdsExportAttempt;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportArtifactContextDto;
import io.github.carlos_emr.carlos.commn.model.CdsExportEvent;
import io.github.carlos_emr.carlos.commn.model.CdsExportRun;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Short, fenced, audited export metadata transactions. No clinical write, file I/O, rendering or
 * serialization occurs inside them. Authorization belongs to the application boundary; saved
 * identity and lease ownership do not grant disclosure rights. Operator arguments use
 * CdsExportActorDto.operatorKey(), never an ambiguous display login. No API here can mark an
 * unvalidated package READY. The package publication service owns that separate proof.
 */
@Service
public class CdsExportLedgerService {
    private static final int MAX_DOCUMENT = 262_144;
    private static final long LEASE_SECONDS = 120;
    /** The diagnostic report names at most this many held charts; the encrypted ReadMe names all. */
    private static final int MAX_REPORTED_HELD = 4096;
    private static final JsonMapper JSON =
            JsonMapper.builder(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(12)
                                                    .maxStringLength(MAX_DOCUMENT)
                                                    .maxNumberLength(20)
                                                    .build())
                                    .build())
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();
    private final CdsExportRunDao runs;
    private final CdsExportEventDao events;
    private final CdsExportAttemptDao attempts;
    private final CdsExportHeldPatientDao heldPatients;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final TransactionTemplate captureControl;
    private final TransactionTemplate reportRead;

    public CdsExportLedgerService(
            CdsExportRunDao runs,
            CdsExportEventDao events,
            CdsExportAttemptDao attempts,
            CdsExportHeldPatientDao heldPatients,
            PlatformTransactionManager manager) {
        this.runs = runs;
        this.events = events;
        this.attempts = Objects.requireNonNull(attempts);
        this.heldPatients = Objects.requireNonNull(heldPatients);
        write = transaction(manager, false);
        read = transaction(manager, true);
        captureControl = transaction(manager, false);
        captureControl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reportRead = transaction(manager, true);
        reportRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    private static TransactionTemplate transaction(
            PlatformTransactionManager manager, boolean readOnly) {
        TransactionTemplate value = new TransactionTemplate(manager);
        value.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        value.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        value.setTimeout(15);
        value.setReadOnly(readOnly);
        return value;
    }

    private <T> T transaction(TransactionTemplate selected, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        }
        return selected.execute(status -> work.get());
    }

    /**
     * Same operator/submission and exact intent recover one durable run after a lost
     * acknowledgement.
     */
    public CdsExportJobDto accept(CdsExportJobDocumentDto document) {
        verifyDocument(document);
        String encoded = encode(document), digest = digest(encoded);
        try {
            return transaction(
                    write,
                    () -> {
                        CdsExportRun prior =
                                runs.submission(
                                        document.actor().operatorKey(),
                                        document.command().submissionKey());
                        if (prior != null) return sameSubmission(prior, digest);
                        Instant now = runs.now();
                        CdsExportRun run = new CdsExportRun();
                        run.setId(UUID.randomUUID().toString());
                        run.setOperator(document.actor().operatorKey());
                        run.setProviderNo(document.command().providerNo());
                        run.setSubmissionKey(document.command().submissionKey());
                        run.setRequestDocument(encoded);
                        run.setRequestDigest(digest);
                        run.setCompatibilityDigest(document.compatibilityDigest());
                        run.setState(CdsExportState.ACCEPTED.name());
                        run.setCreatedAt(now);
                        run.setUpdatedAt(now);
                        runs.persist(run);
                        event(run, "ACCEPTED", document.actor().operatorKey(), null, now);
                        runs.flush();
                        return dto(run, now);
                    });
        } catch (DataIntegrityViolationException duplicate) {
            // The unique key arbitrates concurrent first submissions, never a process-local lock.
            return transaction(
                    read,
                    () -> {
                        CdsExportRun prior =
                                runs.submission(
                                        document.actor().operatorKey(),
                                        document.command().submissionKey());
                        if (prior == null) throw duplicate;
                        return sameSubmission(prior, digest);
                    });
        }
    }

    private CdsExportJobDto sameSubmission(CdsExportRun prior, String digest) {
        if (!digest.equals(prior.getRequestDigest()))
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportSubmissionConflictException();
        return dto(prior, runs.now());
    }

    public CdsExportJobDto status(String id) {
        runId(id);
        return transaction(read, () -> dto(required(runs.get(id)), runs.now()));
    }

    /**
     * One consistent, bounded metadata snapshot, available without a clinical engine, policy files
     * or decryption keys.
     */
    public io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDiagnosticDto diagnostic(
            String id) {
        runId(id);
        return transaction(
                reportRead,
                () -> {
                    var row = dto(required(runs.get(id)), runs.now());
                    var recent = events.recent(id, 129);
                    var audit =
                            new java.util.ArrayList<
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportDiagnosticDto.Audit>();
                    for (var value : recent.stream().limit(128).toList()) {
                        if (value.type() == null
                                || !value.type().matches("[A-Z][A-Z0-9_]{0,63}")
                                || value.epoch() < 0
                                || value.at() == null)
                            throw new IllegalStateException("CDS_EXPORT_DIAGNOSTIC_INVALID");
                        if (value.safeCode() != null) safeCode(value.safeCode());
                        audit.add(
                                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportDiagnosticDto.Audit(
                                        value.type(),
                                        value.epoch(),
                                        value.safeCode(),
                                        value.at().toString()));
                    }
                    var heldRows = heldPatients.held(id, row.epoch(), MAX_REPORTED_HELD + 1);
                    var held =
                            new java.util.ArrayList<
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportDiagnosticDto.Held>();
                    for (var value : heldRows.stream().limit(MAX_REPORTED_HELD).toList()) {
                        if (value.patient() <= 0) throw new IllegalStateException("CDS_EXPORT_DIAGNOSTIC_INVALID");
                        safeCode(value.safeCode());
                        held.add(
                                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportDiagnosticDto.Held(
                                        value.patient(), value.safeCode()));
                    }
                    return new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportDiagnosticDto(
                            1,
                            "DIAGNOSTIC_REPORT",
                            id,
                            row.state().name(),
                            row.epoch(),
                            row.totalPatients(),
                            row.processedPatients(),
                            row.blockedPatients(),
                            row.cancelRequested(),
                            row.complete(),
                            row.artifactAvailable(),
                            row.safeCode(),
                            row.createdAt().toString(),
                            row.updatedAt().toString(),
                            recent.size() > 128,
                            audit,
                            heldRows.size() > MAX_REPORTED_HELD,
                            held);
                });
    }

    /**
     * Nullable internal lookup for replay recovery; the application still verifies current
     * authority and exact original intent.
     */
    public CdsExportJobDto findSubmission(String operator, String submissionKey) {
        operator(operator);
        token(submissionKey, 128);
        return transaction(
                read,
                () -> {
                    var found = runs.submission(operator, submissionKey);
                    return found == null ? null : dto(found, runs.now());
                });
    }

    public CdsExportJobDto submission(String operator, String submissionKey) {
        operator(operator);
        token(submissionKey, 128);
        return transaction(
                read, () -> dto(required(runs.submission(operator, submissionKey)), runs.now()));
    }

    /**
     * Does not deserialize clinical intent; an incompatible/corrupt request cannot erase authorized
     * metadata recovery/control.
     */
    public io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobOwnerDto owner(
            String id) {
        runId(id);
        return transaction(
                read,
                () -> {
                    var run = runs.owner(id);
                    if (run == null) throw new IllegalStateException("CDS_EXPORT_RUN_NOT_FOUND");
                    Integer facility = null;
                    String text = run.requestDocument();
                    if (text != null
                            && text.length() <= MAX_DOCUMENT
                            && digest(text).equals(run.requestDigest())) {
                        try {
                            var tree = JSON.readTree(text);
                            var value =
                                    tree == null
                                            ? com.fasterxml.jackson.databind.node.MissingNode
                                                    .getInstance()
                                            : tree.path("command").path("facilityId");
                            if (value.isIntegralNumber()
                                    && value.canConvertToInt()
                                    && value.intValue() > 0) facility = value.intValue();
                        } catch (IOException unavailable) {
                            /* Metadata ownership still comes from the indexed durable columns. */
                        }
                    }
                    return new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportJobOwnerDto(run.operator(), run.providerNo(), facility);
                });
    }

    public CdsExportJobDocumentDto document(String id) {
        runId(id);
        return transaction(read, () -> document(required(runs.get(id))));
    }

    public List<CdsExportJobDto> history(String operator, String after, int limit) {
        operator(operator);
        if (after != null && !after.isEmpty()) runId(after);
        page(limit);
        return transaction(
                read,
                () -> {
                    Instant now = runs.now();
                    return runs.history(operator, after == null ? "" : after, limit).stream()
                            .map(run -> dto(run, now))
                            .toList();
                });
    }

    public List<String> pending(int limit) {
        page(limit);
        return transaction(read, () -> List.copyOf(runs.pending(runs.now(), limit)));
    }

    /**
     * A new attempt has independent staging and an incremented fence; expired owners cannot revive
     * it.
     */
    public CdsExportLeaseDto claim(String id, String compatibilityDigest) {
        runId(id);
        hash(compatibilityDigest);
        return transaction(
                write,
                () -> {
                    CdsExportRun run = required(runs.lock(id));
                    Instant now = runs.now();
                    CdsExportState state = state(run);
                    if (state.terminal() || liveOwner(run, now)) return null;
                    if (run.isCancelRequested()) {
                        finish(run, CdsExportState.CANCELLED, "CDS_EXPORT_CANCELLED", now);
                        return null;
                    }
                    if (!run.getCompatibilityDigest().equals(compatibilityDigest)) {
                        finish(
                                run,
                                CdsExportState.REVIEW_REQUIRED,
                                "CDS_EXPORT_RUNTIME_CHANGED",
                                now);
                        return null;
                    }
                    CdsExportJobDocumentDto document = document(run);
                    run.setOwnerEpoch(Math.addExact(run.getOwnerEpoch(), 1));
                    run.setOwnerToken(UUID.randomUUID().toString());
                    run.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
                    run.setState(CdsExportState.DISCOVERING.name());
                    resetAttemptEvidence(run);
                    var attempt = new CdsExportAttempt();
                    attempt.setId(UUID.randomUUID().toString());
                    attempt.setRunId(id);
                    attempt.setEpoch(run.getOwnerEpoch());
                    attempt.setRequestDigest(run.getRequestDigest());
                    attempt.setState("CAPTURING");
                    attempt.setCreatedAt(now);
                    attempt.setUpdatedAt(now);
                    attempt.setExpiresAt(now.plusSeconds(7 * 86_400L));
                    attempts.persist(attempt);
                    run.setUpdatedAt(now);
                    event(run, "ATTEMPT_CLAIMED", document.actor().operatorKey(), null, now);
                    runs.flush();
                    return new CdsExportLeaseDto(
                            id, run.getOwnerEpoch(), run.getOwnerToken(), document);
                });
    }

    /**
     * Poll durable cancellation and renew current ownership; this is not a saved authorization
     * grant.
     */
    public void checkpoint(CdsExportLeaseDto lease) {
        transaction(
                write,
                () -> {
                    renew(lease);
                    return null;
                });
    }

    /**
     * A source read may hold an ordinary clinical admission barrier and a repeatable-read snapshot.
     * Its cancellation/lease probe must use another short metadata-only transaction; joining that
     * snapshot would miss a concurrent cancellation. This explicit entry point cannot be used from
     * a clinical write transaction. It performs no authorization/clinical query that could wait
     * behind a maintenance writer while the read barrier is held.
     */
    public void captureCheckpoint(CdsExportLeaseDto lease) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
        }
        captureControl.execute(
                status -> {
                    renew(lease);
                    return null;
                });
    }

    private void renew(CdsExportLeaseDto lease) {
        CdsExportRun run = owned(lease);
        Instant now = runs.now();
        if (run.isCancelRequested()) throw new CancellationException("CDS_EXPORT_CANCELLED");
        runs.renewLease(run, now, now.plusSeconds(LEASE_SECONDS));
    }

    /**
     * Files are fully verified/fsynced before this short, idempotent, fenced metadata publication.
     */
    public void sealSelection(CdsExportLeaseDto lease, CdsExportCohortService.Sealed cohort) {
        Objects.requireNonNull(cohort);
        cohort.require(lease);
        transaction(
                write,
                () -> {
                    var run = owned(lease);
                    var now = runs.now();
                    if (run.isCancelRequested())
                        throw new CancellationException("CDS_EXPORT_CANCELLED");
                    var file = cohort.member();
                    if (state(run) == CdsExportState.CAPTURING
                            && Objects.equals(run.getCohortMember(), file.memberId())
                            && Objects.equals(run.getCohortDigest(), file.plaintextSha256())
                            && Objects.equals(run.getCohortBytes(), file.plaintextBytes())
                            && Objects.equals(run.getExpectedPatients(), cohort.patients())
                            && Objects.equals(run.getSelectedAt(), cohort.readCompletedAt()))
                        return null;
                    if (state(run) != CdsExportState.DISCOVERING || run.getCohortMember() != null) {
                        throw new IllegalStateException("CDS_EXPORT_SELECTION_ALREADY_SEALED");
                    }
                    run.setCohortMember(file.memberId());
                    run.setCohortDigest(file.plaintextSha256());
                    run.setCohortBytes(file.plaintextBytes());
                    run.setExpectedPatients(cohort.patients());
                    run.setSelectedAt(cohort.readCompletedAt());
                    run.setState(CdsExportState.CAPTURING.name());
                    run.setUpdatedAt(now);
                    run.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
                    event(
                            run,
                            "SELECTION_SEALED",
                            lease.document().actor().operatorKey(),
                            null,
                            now);
                    return null;
                });
    }

    /** Only a live, exact attempt can open a committed selection for native capture. */
    public io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto cohort(
            CdsExportLeaseDto lease) {
        return transaction(
                write,
                () -> {
                    var run = owned(lease);
                    if (run.isCancelRequested())
                        throw new CancellationException("CDS_EXPORT_CANCELLED");
                    if (run.getCohortMember() == null
                            || run.getCohortBytes() == null
                            || run.getCohortDigest() == null
                            || run.getExpectedPatients() == null
                            || run.getExpectedPatients() < 1
                            || run.getSelectedAt() == null) {
                        throw new IllegalStateException("CDS_EXPORT_SELECTION_NOT_SEALED");
                    }
                    return new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportStagedFileDto(
                            run.getCohortMember(),
                            "COHORT_MANIFEST",
                            run.getCohortDigest(),
                            run.getCohortBytes());
                });
    }

    /**
     * Records only an independently finished package, retaining each epoch's ownership after
     * restart.
     */
    public CdsExportArtifactContextDto retainPrepared(
            CdsExportLeaseDto lease, CdsExportClinicalPackageService.Prepared prepared) {
        Objects.requireNonNull(prepared);
        if (!lease.runId().equals(prepared.runId()) || lease.epoch() != prepared.epoch())
            artifactInvalid();
        var context =
                new CdsExportArtifactContextDto(
                        1,
                        lease.runId(),
                        lease.epoch(),
                        prepared.patients(),
                        lease.document().compatibilityDigest(),
                        lease.document().recipientFingerprint(),
                        lease.document().auditPlanDigest(),
                        cohort(lease),
                        prepared.authorization(),
                        prepared.ownership(),
                        prepared.readMe(),
                        prepared.eventLog(),
                        prepared.archive(),
                        null,
                        null);
        if (!context.cohort().plaintextSha256().equals(prepared.cohortSha256())) artifactInvalid();
        retain(lease, context, false, prepared.metadata().heldPatients());
        return context;
    }

    /** No caller-supplied ciphertext descriptor can stand in for completed encryption evidence. */
    public CdsExportArtifactContextDto retainEncrypted(
            CdsExportLeaseDto lease, CdsExportDeliveryService.Encrypted encrypted) {
        Objects.requireNonNull(encrypted);
        var prepared = Objects.requireNonNull(encrypted.prepared());
        if (!lease.runId().equals(prepared.runId()) || lease.epoch() != prepared.epoch())
            artifactInvalid();
        var context =
                new CdsExportArtifactContextDto(
                        1,
                        lease.runId(),
                        lease.epoch(),
                        prepared.patients(),
                        lease.document().compatibilityDigest(),
                        lease.document().recipientFingerprint(),
                        lease.document().auditPlanDigest(),
                        cohort(lease),
                        prepared.authorization(),
                        prepared.ownership(),
                        prepared.readMe(),
                        prepared.eventLog(),
                        prepared.archive(),
                        encrypted.ciphertext(),
                        encrypted.proof());
        if (!context.cohort().plaintextSha256().equals(prepared.cohortSha256())) artifactInvalid();
        retain(lease, context, true, prepared.metadata().heldPatients());
        return context;
    }

    private void retain(
            CdsExportLeaseDto lease,
            CdsExportArtifactContextDto context,
            boolean encrypted,
            List<io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportHeldPatientDto>
                    held) {
        var heldRows = heldRows(held, context.patients());
        long heldPatients = heldRows.size();
        var text = encodeContext(context);
        var sha = digest(text);
        transaction(
                write,
                () -> {
                    var run = owned(lease);
                    var now = runs.now();
                    if (run.isCancelRequested())
                        throw new CancellationException("CDS_EXPORT_CANCELLED");
                    if (state(run)
                                    != (encrypted
                                            ? CdsExportState.ENCRYPTING
                                            : CdsExportState.PACKAGING)
                            || !Objects.equals(run.getExpectedPatients(), context.patients())
                            || !Objects.equals(run.getCohortMember(), context.cohort().memberId())
                            || !Objects.equals(
                                    run.getCohortDigest(), context.cohort().plaintextSha256())
                            || !Objects.equals(
                                    run.getCohortBytes(), context.cohort().plaintextBytes()))
                        artifactInvalid();
                    var attempt = attempt(run, lease.epoch());
                    if (sha.equals(attempt.getContextDigest())) {
                        context(attempt);
                        recordHeld(run, lease.epoch(), heldRows, now);
                        return null;
                    }
                    if (attempt.getContextDocument() != null) {
                        var prior = context(attempt);
                        if (!encrypted || prior.encrypted() || !preparedOnly(context).equals(prior))
                            artifactInvalid();
                    } else if (encrypted) artifactInvalid();
                    attempt.setContextDocument(text);
                    attempt.setContextDigest(sha);
                    attempt.setUpdatedAt(now);
                    attempt.setState(encrypted ? "ENCRYPTED" : "PREPARED");
                    // The unversioned heartbeat refreshes the row, so it runs before the held
                    // count is set; the count change is then versioned like any other mutation.
                    runs.renewLease(run, now, now.plusSeconds(LEASE_SECONDS));
                    // Held patients are counted and named durably, in the transaction that binds
                    // the ReadMe listing them, so a partial package can never read as a complete
                    // migration in status, history, the diagnostic report or the CLI exit status.
                    recordHeld(run, lease.epoch(), heldRows, now);
                    run.setBlockedPatients(heldPatients);
                    event(
                            run,
                            encrypted ? "CIPHERTEXT_RETAINED" : "PACKAGE_RETAINED",
                            lease.document().actor().operatorKey(),
                            null,
                            now);
                    return null;
                });
    }

    private static java.util.SortedMap<Integer, String> heldRows(
            List<io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportHeldPatientDto>
                    held,
            long patients) {
        if (held == null) artifactInvalid();
        var rows = new java.util.TreeMap<Integer, String>();
        for (var value : held) {
            if (value == null || rows.put(value.patient(), safeHeldCode(value.code())) != null)
                artifactInvalid();
        }
        // A package always carries at least one patient: all-held selections never package.
        if (rows.size() >= patients) artifactInvalid();
        return rows;
    }

    private static String safeHeldCode(String code) {
        if (code == null || !code.matches("CDS_EXPORT_[A-Z0-9_]{1,85}")) artifactInvalid();
        return code;
    }

    /**
     * Writes the epoch's held list once. A replay (lost acknowledgement, prepared then encrypted
     * retention) must name exactly the same patients and codes; anything else is a different
     * package and is refused.
     */
    private void recordHeld(
            CdsExportRun run, long epoch, java.util.SortedMap<Integer, String> rows, Instant now) {
        long existing = heldPatients.count(run.getId(), epoch);
        if (existing == 0) {
            for (var row : rows.entrySet()) {
                var value = new io.github.carlos_emr.carlos.commn.model.CdsExportHeldPatient();
                value.setId(UUID.randomUUID().toString());
                value.setRunId(run.getId());
                value.setEpoch(epoch);
                value.setDemographicNo(row.getKey());
                value.setSafeCode(row.getValue());
                value.setCreatedAt(now);
                heldPatients.persist(value);
            }
            return;
        }
        if (existing != rows.size()) artifactInvalid();
        var recorded = new java.util.TreeMap<Integer, String>();
        for (var row : heldPatients.held(run.getId(), epoch, rows.size() + 1))
            recorded.put(row.patient(), row.safeCode());
        if (!recorded.equals(rows)) artifactInvalid();
    }

    private static CdsExportArtifactContextDto preparedOnly(CdsExportArtifactContextDto value) {
        return new CdsExportArtifactContextDto(
                value.protocol(),
                value.runId(),
                value.epoch(),
                value.patients(),
                value.compatibilityDigest(),
                value.recipientFingerprint(),
                value.auditPlanDigest(),
                value.cohort(),
                value.authorization(),
                value.ownership(),
                value.readMe(),
                value.eventLog(),
                value.archive(),
                null,
                null);
    }

    private CdsExportAttempt attempt(CdsExportRun run, long epoch) {
        var attempt = attempts.lock(run.getId(), epoch);
        if (attempt == null || !Objects.equals(attempt.getRequestDigest(), run.getRequestDigest()))
            artifactInvalid();
        return attempt;
    }

    private static String encodeContext(CdsExportArtifactContextDto context) {
        try {
            var text = JSON.writeValueAsString(context);
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT) artifactInvalid();
            return text;
        } catch (IOException invalid) {
            throw new IllegalStateException("CDS_EXPORT_ARTIFACT_CONTEXT_INVALID");
        }
    }

    private static CdsExportArtifactContextDto context(CdsExportAttempt attempt) {
        var text = attempt.getContextDocument();
        if (text == null
                || text.length() > MAX_DOCUMENT
                || !digest(text).equals(attempt.getContextDigest())) artifactInvalid();
        try {
            var value = JSON.readValue(text, CdsExportArtifactContextDto.class);
            if (value == null
                    || !value.runId().equals(attempt.getRunId())
                    || value.epoch() != attempt.getEpoch()) artifactInvalid();
            return value;
        } catch (IOException invalid) {
            throw new IllegalStateException("CDS_EXPORT_ARTIFACT_CONTEXT_INVALID");
        }
    }

    private static void artifactInvalid() {
        throw new IllegalStateException("CDS_EXPORT_ARTIFACT_CONTEXT_INVALID");
    }

    /**
     * The sole READY transition: private publication evidence, an exact retained context and the
     * live fence.
     */
    public CdsExportJobDto publish(CdsExportPublicationService.Approved approval) {
        Objects.requireNonNull(approval);
        var lease = approval.lease();
        var expected = approval.context();
        try {
            return publishOnce(approval);
        } catch (org.springframework.transaction.TransactionException uncertain) {
            // Locking waits for the previous commit/rollback. Never infer rollback from a lost
            // response.
            var recovered =
                    transaction(
                            write,
                            () -> {
                                var run = required(runs.lock(lease.runId()));
                                var attempt = attempt(run, lease.epoch());
                                return published(run, attempt, expected, runs.now())
                                        ? dto(run, runs.now())
                                        : null;
                            });
            if (recovered != null) return recovered;
            throw uncertain;
        }
    }

    private CdsExportJobDto publishOnce(CdsExportPublicationService.Approved approval) {
        var lease = approval.lease();
        var expected = approval.context();
        return transaction(
                write,
                () -> {
                    var run = required(runs.lock(lease.runId()));
                    var now = runs.now();
                    var attempt = attempt(run, lease.epoch());
                    if (published(run, attempt, expected, now)) return dto(run, now);
                    owned(lease);
                    if (run.isCancelRequested())
                        throw new CancellationException("CDS_EXPORT_CANCELLED");
                    if (state(run) != CdsExportState.ENCRYPTING
                            || !"ENCRYPTED".equals(attempt.getState())
                            || !expected.encrypted()
                            || !expected.equals(context(attempt))
                            || !Objects.equals(run.getExpectedPatients(), expected.patients())
                            || !Objects.equals(run.getCohortMember(), expected.cohort().memberId())
                            || !Objects.equals(
                                    run.getCohortDigest(), expected.cohort().plaintextSha256()))
                        artifactInvalid();
                    run.setProcessedPatients(expected.patients());
                    run.setArtifactMember(expected.ciphertext().memberId());
                    run.setArtifactDigest(expected.ciphertext().plaintextSha256());
                    run.setArtifactBytes(expected.ciphertext().plaintextBytes());
                    run.setExpiresAt(now.plusSeconds(approval.retentionSeconds()));
                    attempt.setState("PUBLISHED");
                    attempt.setUpdatedAt(now);
                    attempt.setExpiresAt(run.getExpiresAt());
                    finish(run, CdsExportState.READY, null, now);
                    runs.flush();
                    return dto(run, now);
                });
    }

    private static boolean published(
            CdsExportRun run,
            CdsExportAttempt attempt,
            CdsExportArtifactContextDto expected,
            Instant now) {
        return !run.isCancelRequested()
                && state(run) == CdsExportState.READY
                && run.getOwnerEpoch() == expected.epoch()
                && "PUBLISHED".equals(attempt.getState())
                && expected.encrypted()
                && expected.equals(context(attempt))
                && Objects.equals(run.getArtifactMember(), expected.ciphertext().memberId())
                && Objects.equals(run.getArtifactDigest(), expected.ciphertext().plaintextSha256())
                && Objects.equals(run.getArtifactBytes(), expected.ciphertext().plaintextBytes())
                && run.getExpiresAt() != null
                && run.getExpiresAt().isAfter(now);
    }

    /**
     * A retained artifact lease is independent of the execution lease, and mutually exclusive with
     * cleanup.
     */
    public Download beginDownload(String id, String operator) {
        runId(id);
        operator(operator);
        return transaction(
                write,
                () -> {
                    var run = required(runs.lock(id));
                    var now = runs.now();
                    if (state(run) != CdsExportState.READY || run.isCancelRequested())
                        throw new IllegalStateException("CDS_EXPORT_ARTIFACT_UNAVAILABLE");
                    var attempt = attempt(run, run.getOwnerEpoch());
                    if (state(run) != CdsExportState.READY
                            || run.isCancelRequested()
                            || attempt.getContextDocument() == null)
                        throw new IllegalStateException("CDS_EXPORT_ARTIFACT_UNAVAILABLE");
                    var context = context(attempt);
                    available(run, attempt, context, now);
                    if (attempt.getCleanupToken() != null
                            || attempt.getDownloadUntil() != null
                                    && attempt.getDownloadUntil().isAfter(now)) {
                        throw new IllegalStateException("CDS_EXPORT_ARTIFACT_BUSY");
                    }
                    var token = UUID.randomUUID().toString();
                    attempt.setDownloadToken(token);
                    attempt.setDownloadOperator(operator);
                    attempt.setDownloadUntil(now.plusSeconds(LEASE_SECONDS));
                    attempt.setUpdatedAt(now);
                    event(run, "DOWNLOAD_STARTED", operator, null, now);
                    return new Download(
                            id, run.getOwnerEpoch(), token, operator, document(run), context);
                });
    }

    public void downloadCheckpoint(Download download) {
        transaction(
                write,
                () -> {
                    var run = required(runs.lock(download.runId()));
                    var now = runs.now();
                    var attempt = downloadAttempt(run, download, now);
                    available(run, attempt, download.context(), now);
                    attempt.setDownloadUntil(now.plusSeconds(LEASE_SECONDS));
                    attempt.setUpdatedAt(now);
                    return null;
                });
    }

    public void finishDownload(Download download, boolean complete) {
        finishDownload(download, complete, complete);
    }

    public void finishDownload(Download download, boolean complete, boolean outputWritten) {
        transaction(
                write,
                () -> {
                    var run = required(runs.lock(download.runId()));
                    var now = runs.now();
                    var attempt = downloadAttempt(run, download, now);
                    if (complete) available(run, attempt, download.context(), now);
                    attempt.setDownloadToken(null);
                    attempt.setDownloadUntil(null);
                    attempt.setDownloadOperator(null);
                    attempt.setUpdatedAt(now);
                    event(
                            run,
                            complete
                                    ? "DOWNLOAD_COMPLETE"
                                    : outputWritten
                                            ? "DOWNLOAD_COMPLETION_UNCONFIRMED"
                                            : "DOWNLOAD_STOPPED",
                            download.operator(),
                            complete
                                    ? null
                                    : outputWritten
                                            ? "CDS_EXPORT_DOWNLOAD_COMPLETION_UNCONFIRMED"
                                            : "CDS_EXPORT_DOWNLOAD_INCOMPLETE",
                            now,
                            download.epoch());
                    return null;
                });
    }

    private CdsExportAttempt downloadAttempt(CdsExportRun run, Download download, Instant now) {
        var attempt = attempt(run, download.epoch());
        if (!Objects.equals(attempt.getDownloadToken(), download.token())
                || !Objects.equals(attempt.getDownloadOperator(), download.operator())
                || attempt.getDownloadUntil() == null
                || !attempt.getDownloadUntil().isAfter(now)
                || !download.context().equals(context(attempt))
                || attempt.getCleanupToken() != null) {
            throw new IllegalStateException("CDS_EXPORT_DOWNLOAD_FENCED");
        }
        return attempt;
    }

    private static void available(
            CdsExportRun run,
            CdsExportAttempt attempt,
            CdsExportArtifactContextDto expected,
            Instant now) {
        if (!published(run, attempt, expected, now))
            throw new IllegalStateException("CDS_EXPORT_ARTIFACT_UNAVAILABLE");
    }

    /**
     * Opaque lease capability; never send its token/context to a browser or print it in CLI output.
     */
    public record Download(
            String runId,
            long epoch,
            String token,
            String operator,
            CdsExportJobDocumentDto document,
            CdsExportArtifactContextDto context) {
        @Override
        public String toString() {
            return "CdsExportDownload[protected]";
        }
    }

    /**
     * Bounded discovery is metadata-only; all eligibility is checked again under run-before-attempt
     * locks.
     */
    public List<String> expiredArtifacts(int limit) {
        page(limit);
        return transaction(read, () -> List.copyOf(attempts.expiredRunIds(runs.now(), limit)));
    }

    public List<String> expiredArtifacts(String after, int limit) {
        runId(after);
        page(limit);
        return transaction(
                read, () -> List.copyOf(attempts.expiredRunIds(runs.now(), after, limit)));
    }

    /** Stops unreadable waiting intent without parsing it into a clinical grant or touching a live owner. */
    public boolean rejectUnreadableIntent(String id) {
        runId(id);
        return transaction(
                write,
                () -> {
                    var run = required(runs.lock(id));
                    var now = runs.now();
                    if (state(run).terminal() || liveOwner(run, now)) return false;
                    try {
                        document(run);
                        return false;
                    } catch (IllegalStateException malformed) {
                        if (!java.util.Set.of(
                                        "CDS_EXPORT_JOB_DOCUMENT_INVALID",
                                        "CDS_EXPORT_JOB_BINDING_INVALID")
                                .contains(
                                        malformed.getMessage() == null
                                                ? ""
                                                : malformed.getMessage())) throw malformed;
                        finish(
                                run,
                                "CDS_EXPORT_JOB_BINDING_INVALID".equals(malformed.getMessage())
                                        ? CdsExportState.FAILED
                                        : CdsExportState.REVIEW_REQUIRED,
                                malformed.getMessage(),
                                now);
                        return true;
                    }
                });
    }

    public List<Long> artifactEpochs(String id, long after, int limit) {
        runId(id);
        page(limit);
        if (after < 0) throw new IllegalArgumentException("CDS_EXPORT_EPOCH_INVALID");
        return transaction(read, () -> List.copyOf(attempts.epochs(id, after, runs.now(), limit)));
    }

    public Cleanup beginCleanup(String id, long epoch) {
        runId(id);
        if (epoch < 1) throw new IllegalArgumentException("CDS_EXPORT_EPOCH_INVALID");
        return transaction(
                write,
                () -> {
                    var run = required(runs.lock(id));
                    var attempt = attempt(run, epoch);
                    var now = runs.now();
                    if ("PURGED".equals(attempt.getState())
                            || attempt.getExpiresAt().isAfter(now)
                            || run.getOwnerEpoch() == epoch && liveOwner(run, now)
                            || attempt.getDownloadUntil() != null
                                    && attempt.getDownloadUntil().isAfter(now)
                            || attempt.getCleanupUntil() != null
                                    && attempt.getCleanupUntil().isAfter(now)) return null;
                    var token = UUID.randomUUID().toString();
                    attempt.setCleanupToken(token);
                    attempt.setCleanupUntil(now.plusSeconds(LEASE_SECONDS));
                    attempt.setUpdatedAt(now);
                    attempt.setState("PURGING");
                    if (run.getOwnerEpoch() == epoch && state(run) == CdsExportState.READY)
                        finish(run, CdsExportState.EXPIRED, "CDS_EXPORT_RETENTION_EXPIRED", now);
                    event(run, "CLEANUP_STARTED", run.getOperator(), null, now, epoch);
                    return new Cleanup(id, epoch, token, document(run));
                });
    }

    public void cleanupCheckpoint(Cleanup cleanup) {
        transaction(
                write,
                () -> {
                    var run = required(runs.lock(cleanup.runId()));
                    var attempt = cleanupAttempt(run, cleanup, runs.now());
                    var now = runs.now();
                    attempt.setCleanupUntil(now.plusSeconds(LEASE_SECONDS));
                    attempt.setUpdatedAt(now);
                    return null;
                });
    }

    public void finishCleanup(Cleanup cleanup, boolean complete) {
        transaction(
                write,
                () -> {
                    var run = required(runs.lock(cleanup.runId()));
                    var now = runs.now();
                    var attempt = cleanupAttempt(run, cleanup, now);
                    attempt.setCleanupToken(null);
                    attempt.setCleanupUntil(null);
                    attempt.setUpdatedAt(now);
                    attempt.setState(complete ? "PURGED" : "CLEANUP_FAILED");
                    if (!complete) attempt.setExpiresAt(now.plusSeconds(300));
                    attempt.setSafeCode(complete ? null : "CDS_EXPORT_CLEANUP_INCOMPLETE");
                    if (complete) {
                        attempt.setContextDocument(null);
                        attempt.setContextDigest(null);
                    }
                    event(
                            run,
                            complete ? "CLEANUP_COMPLETE" : "CLEANUP_STOPPED",
                            run.getOperator(),
                            complete ? null : "CDS_EXPORT_CLEANUP_INCOMPLETE",
                            now,
                            cleanup.epoch());
                    return null;
                });
    }

    private CdsExportAttempt cleanupAttempt(CdsExportRun run, Cleanup cleanup, Instant now) {
        var attempt = attempt(run, cleanup.epoch());
        if (!Objects.equals(attempt.getCleanupToken(), cleanup.token())
                || attempt.getCleanupUntil() == null
                || !attempt.getCleanupUntil().isAfter(now)
                || run.getOwnerEpoch() == cleanup.epoch() && liveOwner(run, now)
                || attempt.getDownloadUntil() != null && attempt.getDownloadUntil().isAfter(now)) {
            throw new IllegalStateException("CDS_EXPORT_CLEANUP_FENCED");
        }
        return attempt;
    }

    public record Cleanup(
            String runId, long epoch, String token, CdsExportJobDocumentDto document) {
        @Override
        public String toString() {
            return "CdsExportCleanup[protected]";
        }
    }

    public void phase(CdsExportLeaseDto lease, CdsExportState next) {
        Objects.requireNonNull(next);
        transaction(
                write,
                () -> {
                    CdsExportRun run = owned(lease);
                    Instant now = runs.now();
                    if (run.isCancelRequested())
                        throw new CancellationException("CDS_EXPORT_CANCELLED");
                    CdsExportState current = state(run);
                    if (next == CdsExportState.CAPTURING)
                        throw new IllegalStateException("CDS_EXPORT_SELECTION_NOT_SEALED");
                    if (!next.executing() || next.ordinal() != current.ordinal() + 1) {
                        throw new IllegalStateException("CDS_EXPORT_PHASE_INVALID");
                    }
                    run.setState(next.name());
                    run.setUpdatedAt(now);
                    run.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
                    event(run, next.name(), lease.document().actor().operatorKey(), null, now);
                    return null;
                });
    }

    public void cancel(String id, String operator) {
        cancel(id, operator, null);
    }

    /** Browser controls fence the observed revision while the row is locked. */
    public void cancel(String id, String operator, Long expectedRevision) {
        runId(id);
        operator(operator);
        transaction(
                write,
                () -> {
                    CdsExportRun run = required(runs.lock(id));
                    Instant now = runs.now();
                    revision(run, expectedRevision);
                    if (run.isCancelRequested() && state(run) == CdsExportState.CANCELLED)
                        return null;
                    if (state(run).terminal() && state(run) != CdsExportState.READY) {
                        throw new IllegalStateException("CDS_EXPORT_CANCEL_INVALID");
                    }
                    run.setCancelRequested(true);
                    attempts.expireForRun(id, now);
                    run.setUpdatedAt(now);
                    event(run, "CANCEL_REQUESTED", operator, null, now);
                    if (!liveOwner(run, now) || state(run) == CdsExportState.READY) {
                        finish(run, CdsExportState.CANCELLED, "CDS_EXPORT_CANCELLED", now);
                    }
                    return null;
                });
    }

    public void resume(String id, String operator, String compatibilityDigest) {
        resume(id, operator, compatibilityDigest, null);
    }

    public void resume(
            String id, String operator, String compatibilityDigest, Long expectedRevision) {
        runId(id);
        operator(operator);
        hash(compatibilityDigest);
        transaction(
                write,
                () -> {
                    CdsExportRun run = required(runs.lock(id));
                    Instant now = runs.now();
                    revision(run, expectedRevision);
                    if (liveOwner(run, now))
                        throw new IllegalStateException("CDS_EXPORT_WORKER_BUSY");
                    if (!run.getCompatibilityDigest().equals(compatibilityDigest)) {
                        throw new IllegalStateException("CDS_EXPORT_RUNTIME_CHANGED");
                    }
                    if (state(run) == CdsExportState.READY
                            || state(run) == CdsExportState.EXPIRED) {
                        throw new IllegalStateException("CDS_EXPORT_RESUME_INVALID");
                    }
                    run.setState(CdsExportState.ACCEPTED.name());
                    run.setCancelRequested(false);
                    run.setOwnerToken(null);
                    run.setLeaseUntil(null);
                    resetAttemptEvidence(run);
                    run.setUpdatedAt(now);
                    event(run, "RESUME_REQUESTED", operator, null, now);
                    return null;
                });
    }

    private static void revision(CdsExportRun run, Long expected) {
        if (expected != null && (expected < 0 || expected != run.getRevision())) {
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportStaleControlException();
        }
    }

    /**
     * Stops this attempt without cancelling disclosure intent. A stopped foreground wait or orderly
     * process restart returns the same durable job to waiting, with a new generation required on
     * claim. An independently requested durable cancellation still wins.
     */
    public void release(CdsExportLeaseDto lease) {
        transaction(
                write,
                () -> {
                    CdsExportRun run = owned(lease);
                    Instant now = runs.now();
                    if (run.isCancelRequested())
                        finish(run, CdsExportState.CANCELLED, "CDS_EXPORT_CANCELLED", now);
                    else {
                        // The next claim starts a new generation that captures again; this
                        // generation's partial staging is no longer needed.
                        attempts.expireForRun(run.getId(), now);
                        run.setState(CdsExportState.ACCEPTED.name());
                        run.setOwnerToken(null);
                        run.setLeaseUntil(null);
                        resetAttemptEvidence(run);
                        run.setUpdatedAt(now);
                        event(
                                run,
                                "ATTEMPT_RELEASED",
                                lease.document().actor().operatorKey(),
                                null,
                                now);
                    }
                    return null;
                });
    }

    private static void resetAttemptEvidence(CdsExportRun run) {
        run.setExpectedPatients(null);
        run.setProcessedPatients(0);
        run.setBlockedPatients(0);
        run.setCohortMember(null);
        run.setCohortBytes(null);
        run.setCohortDigest(null);
        run.setSelectedAt(null);
        run.setArtifactMember(null);
        run.setArtifactDigest(null);
        run.setArtifactBytes(null);
        run.setExpiresAt(null);
        run.setSafeCode(null);
    }

    /**
     * Cancellation is finalized under the same fence even though a normal checkpoint refuses it.
     */
    public void stop(CdsExportLeaseDto lease, CdsExportState outcome, String code) {
        if (outcome != CdsExportState.FAILED
                && outcome != CdsExportState.REVIEW_REQUIRED
                && outcome != CdsExportState.CANCELLED) {
            throw new IllegalArgumentException("CDS_EXPORT_STOP_INVALID");
        }
        safeCode(code);
        transaction(
                write,
                () -> {
                    CdsExportRun run = owned(lease);
                    Instant now = runs.now();
                    if (outcome == CdsExportState.CANCELLED && !run.isCancelRequested()) {
                        throw new IllegalStateException("CDS_EXPORT_DURABLE_CANCEL_REQUIRED");
                    }
                    finish(
                            run,
                            run.isCancelRequested() ? CdsExportState.CANCELLED : outcome,
                            run.isCancelRequested() ? "CDS_EXPORT_CANCELLED" : code,
                            now);
                    return null;
                });
    }

    private void finish(CdsExportRun run, CdsExportState outcome, String code, Instant now) {
        // A failed, refused or cancelled job has no package to keep: its staged selection,
        // captures and partial outputs become eligible for the next retention sweep at once,
        // instead of holding private data and disk reserve for the attempt's 7-day ceiling.
        if (outcome == CdsExportState.FAILED
                || outcome == CdsExportState.REVIEW_REQUIRED
                || outcome == CdsExportState.CANCELLED) attempts.expireForRun(run.getId(), now);
        run.setState(outcome.name());
        run.setSafeCode(code);
        run.setUpdatedAt(now);
        run.setOwnerToken(null);
        run.setLeaseUntil(null);
        event(run, outcome.name(), run.getOperator(), code, now);
    }

    private CdsExportRun owned(CdsExportLeaseDto lease) {
        Objects.requireNonNull(lease);
        runId(lease.runId());
        CdsExportRun run = required(runs.lock(lease.runId()));
        // Evaluate expiry after acquiring the row lock, including time spent waiting for it.
        Instant now = runs.now();
        if (!Objects.equals(run.getOwnerToken(), lease.token())
                || run.getOwnerEpoch() != lease.epoch()
                || !liveOwner(run, now)
                || !state(run).executing()
                || !digest(encode(lease.document())).equals(run.getRequestDigest())) {
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportOwnerFencedException();
        }
        return run;
    }

    /** An expired worker can record its failure, but cannot renew or replace a later generation. */
    public boolean retireExpiredLease(CdsExportLeaseDto lease) {
        Objects.requireNonNull(lease);
        runId(lease.runId());
        return transaction(
                write,
                () -> {
                    CdsExportRun run = required(runs.lock(lease.runId()));
                    Instant now = runs.now();
                    if (!Objects.equals(run.getOwnerToken(), lease.token())
                            || run.getOwnerEpoch() != lease.epoch()
                            || liveOwner(run, now)
                            || !state(run).executing()
                            || !digest(encode(lease.document())).equals(run.getRequestDigest()))
                        return false;
                    finish(
                            run,
                            run.isCancelRequested()
                                    ? CdsExportState.CANCELLED
                                    : CdsExportState.REVIEW_REQUIRED,
                            run.isCancelRequested()
                                    ? "CDS_EXPORT_CANCELLED"
                                    : "CDS_EXPORT_EXECUTION_LEASE_EXPIRED",
                            now);
                    return true;
                });
    }

    private static boolean liveOwner(CdsExportRun run, Instant now) {
        return run.getOwnerToken() != null
                && run.getLeaseUntil() != null
                && run.getLeaseUntil().isAfter(now);
    }

    private void event(CdsExportRun run, String type, String operator, String code, Instant now) {
        event(run, type, operator, code, now, run.getOwnerEpoch());
    }

    private void event(
            CdsExportRun run, String type, String operator, String code, Instant now, long epoch) {
        if (epoch < 0 || epoch > run.getOwnerEpoch())
            throw new IllegalArgumentException("CDS_EXPORT_EVENT_EPOCH_INVALID");
        CdsExportEvent event = new CdsExportEvent();
        event.setId(UUID.randomUUID().toString());
        event.setRunId(run.getId());
        event.setEpoch(epoch);
        event.setEventType(type);
        event.setOperator(operator);
        event.setSafeCode(code);
        event.setCreatedAt(now);
        events.persist(event);
    }

    private CdsExportJobDto dto(CdsExportRun run, Instant now) {
        boolean available =
                state(run) == CdsExportState.READY
                        && !run.isCancelRequested()
                        && run.getArtifactMember() != null
                        && run.getArtifactDigest() != null
                        && run.getExpiresAt() != null
                        && run.getExpiresAt().isAfter(now);
        if (available) {
            var retained = attempts.find(run.getId(), run.getOwnerEpoch());
            if (retained == null || !"PUBLISHED".equals(retained.getState())) available = false;
            else
                try {
                    available = published(run, retained, context(retained), now);
                } catch (IllegalStateException | IllegalArgumentException invalid) {
                    available = false;
                }
        }
        return new CdsExportJobDto(
                run.getId(),
                state(run),
                run.getRevision(),
                run.getExpectedPatients(),
                run.getProcessedPatients(),
                run.getBlockedPatients(),
                run.isCancelRequested(),
                liveOwner(run, now),
                run.getOwnerEpoch(),
                run.getSafeCode(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                available);
    }

    private static CdsExportState state(CdsExportRun run) {
        return CdsExportState.valueOf(run.getState());
    }

    private static CdsExportRun required(CdsExportRun run) {
        if (run == null) throw new IllegalStateException("CDS_EXPORT_RUN_NOT_FOUND");
        return run;
    }

    private static CdsExportJobDocumentDto document(CdsExportRun run) {
        String text = run.getRequestDocument();
        if (text == null
                || text.length() > MAX_DOCUMENT
                || !digest(text).equals(run.getRequestDigest())) {
            throw new IllegalStateException("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        }
        try {
            CdsExportJobDocumentDto result = JSON.readValue(text, CdsExportJobDocumentDto.class);
            verifyDocument(result);
            if (!result.actor().operatorKey().equals(run.getOperator())
                    || !result.command().providerNo().equals(run.getProviderNo())
                    || !result.command().submissionKey().equals(run.getSubmissionKey())
                    || !result.compatibilityDigest().equals(run.getCompatibilityDigest())) {
                throw new IllegalStateException("CDS_EXPORT_JOB_BINDING_INVALID");
            }
            return result;
        } catch (IOException | IllegalArgumentException | NullPointerException failure) {
            throw new IllegalStateException("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        }
    }

    private static void verifyDocument(CdsExportJobDocumentDto document) {
        Objects.requireNonNull(document);
        Objects.requireNonNull(document.actor());
        Objects.requireNonNull(document.command());
        if (!(document.protocol() == 1 && document.storage() == null
                        || document.protocol() == 2 && document.storage() != null)
                || !Objects.equals(document.actor().providerNo(), document.command().providerNo())
                || !Objects.equals(
                        document.actor().facilityId(), document.command().facilityId())) {
            throw new IllegalArgumentException("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        }
        hash(document.compatibilityDigest());
        hash(document.recipientFingerprint());
        hash(document.auditPlanDigest());
        token(document.senderNamespace(), 128);
    }

    private static String encode(CdsExportJobDocumentDto document) {
        try {
            String text = JSON.writeValueAsString(document);
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT)
                throw new IllegalArgumentException("CDS_EXPORT_REQUEST_TOO_LARGE");
            return text;
        } catch (IOException failure) {
            throw new IllegalArgumentException("CDS_EXPORT_JOB_ENCODING_FAILED");
        }
    }

    private static String digest(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void runId(String id) {
        try {
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("CDS_EXPORT_RUN_ID_INVALID");
        }
    }

    private static void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("CDS_EXPORT_DIGEST_INVALID");
    }

    private static void operator(String value) {
        if (value == null
                || value.length() > 128
                || !(value.matches("web:[1-9][0-9]{0,9}")
                        || value.startsWith("unix:")
                                && io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportActorDto.isOperatorIdentifier(
                                        value.substring(5)))) {
            throw new IllegalArgumentException("CDS_EXPORT_TOKEN_INVALID");
        }
    }

    private static void token(String value, int max) {
        if (value == null
                || value.length() > max
                || !value.matches("[A-Za-z0-9_][A-Za-z0-9_.:-]*")) {
            throw new IllegalArgumentException("CDS_EXPORT_TOKEN_INVALID");
        }
    }

    private static void safeCode(String code) {
        if (code == null || !code.matches("CDS_EXPORT_[A-Z0-9_]{1,85}"))
            throw new IllegalArgumentException("CDS_EXPORT_ERROR_CODE_INVALID");
    }

    private static void page(int limit) {
        if (limit < 1 || limit > 100)
            throw new IllegalArgumentException("CDS_EXPORT_PAGE_LIMIT_INVALID");
    }
}
