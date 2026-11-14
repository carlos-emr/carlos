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
 * unvalidated package READY. The package publication service owns that separate proof. No
 * transaction here has a time limit of its own: each is a write unit ({@link
 * io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport#writing}), so a slow
 * database is waited for, statement by statement, and a transaction is run again only when it
 * provably did not commit.
 */
@Service
public class CdsExportLedgerService {
    /**
     * The size of a job document's own line, of an artifact context and of the prefix the owner
     * query reads. It does not bound a request: an explicit selection that would carry the job
     * document past it is written on a second line of the same column (see {@link
     * #encode(CdsExportJobDocumentDto)}), whatever its length.
     */
    private static final int MAX_DOCUMENT = 262_144;
    /** How long a claim or a renewal keeps the attempt's lease. */
    static final long LEASE_SECONDS = 120;
    /**
     * The seven-day ceiling of an attempt's protected staging: an attempt whose worker was lost
     * keeps its directory until seven days after it was claimed, and a stopped attempt keeps its
     * finished charts for a resumed attempt until seven days after it stopped.
     */
    private static final long ATTEMPT_SECONDS = 7 * 86_400L;
    /** An attempt stopped with finished charts kept; the sweep has not trimmed it yet. */
    static final String KEEPING = "KEEPING";
    /** A kept attempt holding only its checkpoint and the members the checkpoint pins. */
    static final String KEPT = "KEPT";
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
    private final java.util.concurrent.atomic.AtomicReferenceArray<LeaseDigest> leaseDigests =
            new java.util.concurrent.atomic.AtomicReferenceArray<>(8);
    private final java.util.concurrent.atomic.AtomicInteger nextLeaseDigest =
            new java.util.concurrent.atomic.AtomicInteger();
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

    /**
     * A metadata transaction with no timeout of its own: a slow database is waited for, never
     * failed. Each runs as a write unit ({@link #transaction(TransactionTemplate, Supplier)}).
     */
    private static TransactionTemplate transaction(
            PlatformTransactionManager manager, boolean readOnly) {
        TransactionTemplate value = new TransactionTemplate(manager);
        value.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        value.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        value.setReadOnly(readOnly);
        return value;
    }

    /**
     * One metadata transaction as a write unit ({@link
     * io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport#writing}): every
     * statement of it, its writes included, waits for the database with a server-side deadline
     * that is sent again, twice as long, until it is answered, and the commit is sent only when
     * no statement of it was ended. A sending the server ended (its deadline, a lock wait, a
     * deadlock) never reached its commit, so it is rolled back and the work runs again from its
     * first read: the fence ({@link #owned}) is decided again on the row it locks, against the
     * database clock read after that lock, so a sending that comes late finds a lease lost
     * meanwhile and is fenced, and nothing a rolled-back sending wrote survives to be written
     * twice. A fence, a cancellation and every refusal of the work's own end it at once. The waits
     * end with the caller's attempt (its observer: the lease a renewal has left, a cancel, the
     * stall detector) or with an interrupt; nothing else ends them.
     */
    private <T> T transaction(TransactionTemplate selected, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        }
        return io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport.writing(
                selected, status -> work.get());
    }

    /**
     * Same operator/submission and exact intent recover one durable run after a lost
     * acknowledgement.
     */
    public CdsExportJobDto accept(CdsExportJobDocumentDto document) {
        verifyDocument(document);
        String encoded = encode(document), digest = digest(fixed(encoded));
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

    // FindSecBugs UNSAFE_HASH_EQUALS: SHA-256 of the job document an operator submitted, compared with the digest of the document the same operator submitted before under the same submission key; decides replay or conflict, not a secret.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "UNSAFE_HASH_EQUALS", justification = "SHA-256 of the job document an operator submitted, compared with the digest of the document the same operator submitted before under the same submission key; decides replay or conflict, not a secret")
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
                    var unlinked =
                            new java.util.ArrayList<
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportDiagnosticDto.Unlinked>();
                    for (String code :
                            events.safeCodes(
                                    id,
                                    row.epoch(),
                                    io.github.carlos_emr.carlos.commn.dto
                                            .CdsExportUnlinkedSourceDto.EVENT,
                                    65)) {
                        var value =
                                io.github.carlos_emr.carlos.commn.dto.CdsExportUnlinkedSourceDto
                                        .fromEventCode(code)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "CDS_EXPORT_DIAGNOSTIC_INVALID"));
                        unlinked.add(
                                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportDiagnosticDto.Unlinked(
                                        value.table(), value.rows()));
                    }
                    if (unlinked.size() > 64)
                        throw new IllegalStateException("CDS_EXPORT_DIAGNOSTIC_INVALID");
                    unlinked.sort(
                            java.util.Comparator.comparing(
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                    .CdsExportDiagnosticDto.Unlinked
                                            ::table));
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
                            held,
                            unlinked);
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
                    // The query reads a prefix; the document's own line lies wholly inside it and
                    // is what the digest names, so a long selection is never read here.
                    String text = run.requestDocument() == null ? null : fixed(run.requestDocument());
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
                    if (!io.github.carlos_emr.carlos.utility.CdsImportKeys.sameDigest(
                            run.getCompatibilityDigest(), compatibilityDigest)) {
                        finish(
                                run,
                                CdsExportState.REVIEW_REQUIRED,
                                "CDS_EXPORT_RUNTIME_CHANGED",
                                now);
                        return null;
                    }
                    CdsExportJobDocumentDto document = document(run);
                    // An earlier attempt whose worker was lost before it recorded any outcome
                    // and that sealed a finished-chart checkpoint keeps its charts, until its own
                    // seven-day ceiling, for the attempt claimed now to adopt.
                    for (var orphan : attempts.orphanedCheckpoints(id, now))
                        keep(run, attempts.lock(id, orphan.getEpoch()), orphan.getExpiresAt(), now);
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
                    attempt.setExpiresAt(now.plusSeconds(ATTEMPT_SECONDS));
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
        // A write unit of its own, like every other metadata transaction of the ledger.
        io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport.writing(
                captureControl,
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
     * Records the newest sealed member of this attempt's finished-chart checkpoint ({@link
     * CdsExportChartCheckpointPersister}) on the attempt row, under the live fence, with how many of
     * the charts it names this attempt adopted from an earlier attempt. The chain only grows: a
     * pointer that counts fewer charts, or fewer adopted ones, than the pointer already recorded is
     * refused. Identifiers and counts only; never a patient value.
     */
    public void recordCheckpoint(
            CdsExportLeaseDto lease,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto pointer,
            long adopted) {
        recordCheckpoint(lease, pointer, adopted, java.util.Set.of(), false);
    }

    /**
     * Records the newest sealed member of this attempt's checkpoint, and releases the earlier
     * attempts whose kept charts this attempt has decided again ({@code superseded}): each is
     * made due for the retention sweep, which erases it, and audited as {@code
     * CHECKPOINT_SUPERSEDED}. {@code complete} at the end of the roster: when this attempt adopted
     * any chart, the count is audited as {@code CHARTS_ADOPTED}.
     */
    public void recordCheckpoint(
            CdsExportLeaseDto lease,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto pointer,
            long adopted,
            java.util.Set<Long> superseded,
            boolean complete) {
        Objects.requireNonNull(pointer);
        var released = new java.util.TreeSet<>(superseded);
        if (adopted < 0
                || adopted > pointer.charts()
                || released.stream().anyMatch(epoch -> epoch < 1 || epoch >= lease.epoch()))
            throw new IllegalArgumentException("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
        transaction(
                write,
                () -> {
                    var run = owned(lease);
                    var now = runs.now();
                    if (run.isCancelRequested())
                        throw new CancellationException("CDS_EXPORT_CANCELLED");
                    var attempt = attempt(run, lease.epoch());
                    if (attempt.getCheckpointCharts() != null
                            && (pointer.charts() < attempt.getCheckpointCharts()
                                    || adopted < attempt.getAdoptedCharts()))
                        throw new IllegalStateException("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
                    attempt.setCheckpointMember(pointer.memberId());
                    attempt.setCheckpointDigest(pointer.sha256());
                    attempt.setCheckpointBytes(pointer.bytes());
                    attempt.setCheckpointCharts(pointer.charts());
                    attempt.setAdoptedCharts(adopted);
                    attempt.setUpdatedAt(now);
                    for (long epoch : released) {
                        var prior = attempts.lock(run.getId(), epoch);
                        if (prior == null
                                || !(KEEPING.equals(prior.getState())
                                        || KEPT.equals(prior.getState()))
                                || !prior.getExpiresAt().isAfter(now)) continue;
                        prior.setExpiresAt(now);
                        prior.setUpdatedAt(now);
                        event(
                                run,
                                "CHECKPOINT_SUPERSEDED",
                                lease.document().actor().operatorKey(),
                                null,
                                now,
                                epoch);
                    }
                    if (complete && adopted > 0)
                        event(
                                run,
                                "CHARTS_ADOPTED",
                                lease.document().actor().operatorKey(),
                                "CDS_EXPORT_CHARTS_ADOPTED_" + adopted,
                                now);
                    // The heartbeat refreshes the run row; it changes nothing recorded above.
                    runs.renewLease(run, now, now.plusSeconds(LEASE_SECONDS));
                    return null;
                });
    }

    /** One earlier attempt's kept finished charts: its epoch and its recorded checkpoint. */
    public record KeptCheckpoint(
            long epoch,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto
                    pointer) {
        @Override
        public String toString() {
            return "CdsExportKeptCheckpoint[protected]";
        }
    }

    /**
     * The kept checkpoints of the job's earlier attempts this live attempt may adopt from, newest
     * first: stopped attempts kept for a resume and not yet due for erasure.
     */
    public List<KeptCheckpoint> keptCheckpoints(CdsExportLeaseDto lease) {
        return transaction(
                write,
                () -> {
                    var run = owned(lease);
                    if (run.isCancelRequested())
                        throw new CancellationException("CDS_EXPORT_CANCELLED");
                    var result = new java.util.ArrayList<KeptCheckpoint>();
                    for (var kept : attempts.kept(run.getId(), runs.now())) {
                        if (kept.getEpoch() >= lease.epoch()
                                || kept.getCheckpointDigest() == null
                                || kept.getCheckpointBytes() == null
                                || kept.getCheckpointCharts() == null) continue;
                        result.add(
                                new KeptCheckpoint(
                                        kept.getEpoch(),
                                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                .CdsExportChartCheckpointDto(
                                                kept.getCheckpointMember(),
                                                kept.getCheckpointDigest(),
                                                kept.getCheckpointBytes(),
                                                kept.getCheckpointCharts())));
                    }
                    return List.copyOf(result);
                });
    }

    /**
     * Releases an earlier attempt whose kept checkpoint this live attempt found untrustworthy (its
     * chain does not match its recorded pointer, a member it pins is missing or altered, the key
     * is gone): it is made due for the retention sweep and audited as {@code CHECKPOINT_REJECTED}.
     */
    public void rejectCheckpoint(CdsExportLeaseDto lease, long epoch) {
        if (epoch < 1 || epoch >= lease.epoch())
            throw new IllegalArgumentException("CDS_EXPORT_EPOCH_INVALID");
        transaction(
                write,
                () -> {
                    var run = owned(lease);
                    var now = runs.now();
                    var prior = attempts.lock(run.getId(), epoch);
                    if (prior == null
                            || !(KEEPING.equals(prior.getState()) || KEPT.equals(prior.getState()))
                            || !prior.getExpiresAt().isAfter(now)) return null;
                    prior.setExpiresAt(now);
                    prior.setUpdatedAt(now);
                    event(
                            run,
                            "CHECKPOINT_REJECTED",
                            lease.document().actor().operatorKey(),
                            "CDS_EXPORT_CHART_CHECKPOINT_INVALID",
                            now,
                            epoch);
                    return null;
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
                    if (io.github.carlos_emr.carlos.utility.CdsImportKeys.sameDigest(
                            sha, attempt.getContextDigest())) {
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
                    // Charts kept by earlier attempts of the job have served their purpose.
                    attempts.flush();
                    attempts.expireKeptForRun(run.getId(), now);
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
                        if (attempt.getCheckpointMember() != null)
                            // Kept charts erased: with the job's cancellation, after a resumed
                            // attempt adopted past them, or after their seven days.
                            event(
                                    run,
                                    "CHARTS_ERASED",
                                    run.getOperator(),
                                    "CDS_EXPORT_CHARTS_ERASED_"
                                            + (attempt.getCheckpointCharts() == null
                                                    ? 0
                                                    : attempt.getCheckpointCharts()),
                                    now,
                                    cleanup.epoch());
                        attempt.setCheckpointMember(null);
                        attempt.setCheckpointDigest(null);
                        attempt.setCheckpointBytes(null);
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

    /** One kept attempt whose unpinned members the retention sweep has not erased yet. */
    public record Untrimmed(String runId, long epoch) {}

    /** Kept attempts the retention sweep still has to trim, oldest change first. */
    public List<Untrimmed> untrimmed(int limit) {
        page(limit);
        return transaction(
                read,
                () ->
                        attempts.untrimmed(runs.now(), limit).stream()
                                .map(value -> new Untrimmed(value.getRunId(), value.getEpoch()))
                                .toList());
    }

    /**
     * Exclusive lease to erase the members of a kept attempt that its checkpoint does not pin.
     * Mutually exclusive with downloads and with the attempt's own erasure, like cleanup.
     */
    public record Trim(
            String runId,
            long epoch,
            String token,
            CdsExportJobDocumentDto document,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto
                    pointer) {
        @Override
        public String toString() {
            return "CdsExportTrim[protected]";
        }
    }

    /** How a trim ended. */
    public enum TrimOutcome {
        /** Only the checkpoint and the members it pins remain: the attempt is {@value #KEPT}. */
        TRIMMED,
        /** Not finished; the next sweep tries again. */
        RETRY,
        /**
         * The checkpoint cannot be trusted or read (its chain does not match the recorded
         * pointer, or its key is gone): nothing of the attempt can be adopted, so it is erased.
         */
        REJECTED
    }

    public Trim beginTrim(String id, long epoch) {
        runId(id);
        if (epoch < 1) throw new IllegalArgumentException("CDS_EXPORT_EPOCH_INVALID");
        return transaction(
                write,
                () -> {
                    var run = required(runs.lock(id));
                    var attempt = attempt(run, epoch);
                    var now = runs.now();
                    if (!KEEPING.equals(attempt.getState())
                            || attempt.getCheckpointMember() == null
                            || !attempt.getExpiresAt().isAfter(now)
                            || run.getOwnerEpoch() == epoch && liveOwner(run, now)
                            || attempt.getDownloadUntil() != null
                                    && attempt.getDownloadUntil().isAfter(now)
                            || attempt.getCleanupUntil() != null
                                    && attempt.getCleanupUntil().isAfter(now)) return null;
                    var pointer =
                            new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                    .CdsExportChartCheckpointDto(
                                    attempt.getCheckpointMember(),
                                    attempt.getCheckpointDigest(),
                                    attempt.getCheckpointBytes(),
                                    attempt.getCheckpointCharts());
                    var token = UUID.randomUUID().toString();
                    attempt.setCleanupToken(token);
                    attempt.setCleanupUntil(now.plusSeconds(LEASE_SECONDS));
                    attempt.setUpdatedAt(now);
                    return new Trim(id, epoch, token, document(run), pointer);
                });
    }

    public void trimCheckpoint(Trim trim) {
        transaction(
                write,
                () -> {
                    var run = required(runs.lock(trim.runId()));
                    var now = runs.now();
                    var attempt = trimAttempt(run, trim, now);
                    attempt.setCleanupUntil(now.plusSeconds(LEASE_SECONDS));
                    attempt.setUpdatedAt(now);
                    return null;
                });
    }

    public void finishTrim(Trim trim, TrimOutcome outcome) {
        Objects.requireNonNull(outcome);
        transaction(
                write,
                () -> {
                    var run = required(runs.lock(trim.runId()));
                    var now = runs.now();
                    var attempt = trimAttempt(run, trim, now);
                    attempt.setCleanupToken(null);
                    attempt.setCleanupUntil(null);
                    attempt.setUpdatedAt(now);
                    switch (outcome) {
                        case TRIMMED -> {
                            attempt.setState(KEPT);
                            // The prepared package it pointed to, if any, is gone.
                            attempt.setContextDocument(null);
                            attempt.setContextDigest(null);
                        }
                        case REJECTED -> {
                            attempt.setExpiresAt(now);
                            event(
                                    run,
                                    "CHECKPOINT_REJECTED",
                                    run.getOperator(),
                                    "CDS_EXPORT_CHART_CHECKPOINT_INVALID",
                                    now,
                                    trim.epoch());
                        }
                        case RETRY -> {
                            /* Still KEEPING; the next sweep tries again. */
                        }
                        default -> throw new IllegalArgumentException("CDS_EXPORT_TRIM_INVALID");
                    }
                    return null;
                });
    }

    private CdsExportAttempt trimAttempt(CdsExportRun run, Trim trim, Instant now) {
        var attempt = attempt(run, trim.epoch());
        if (!Objects.equals(attempt.getCleanupToken(), trim.token())
                || attempt.getCleanupUntil() == null
                || !attempt.getCleanupUntil().isAfter(now)
                || !KEEPING.equals(attempt.getState())
                || !Objects.equals(attempt.getCheckpointMember(), trim.pointer().memberId())
                || run.getOwnerEpoch() == trim.epoch() && liveOwner(run, now)
                || attempt.getDownloadUntil() != null && attempt.getDownloadUntil().isAfter(now))
            throw new IllegalStateException("CDS_EXPORT_CLEANUP_FENCED");
        return attempt;
    }

    /**
     * Records, once for this attempt generation, how many rows of each reviewed inbox table named
     * no patient when its capture began. Those rows hold no chart and are not exported, so the
     * counts go into the run's event ledger, the audit trail of the export, from which the
     * diagnostic report lists them. One {@code UNLINKED_SOURCE_ROWS} event per table with rows;
     * its safe code carries only the table and the count. A replay of the same generation must
     * record exactly the same counts.
     */
    public void recordUnlinkedSources(
            CdsExportLeaseDto lease,
            List<io.github.carlos_emr.carlos.commn.dto.CdsExportUnlinkedSourceDto> rows) {
        var codes = new java.util.TreeSet<String>();
        for (var row : List.copyOf(rows)) {
            String code = Objects.requireNonNull(row).eventCode();
            safeCode(code);
            if (!codes.add(code)
                    || rows.stream().filter(other -> other.table().equals(row.table())).count()
                            != 1) throw new IllegalArgumentException("CDS_EXPORT_UNLINKED_SOURCE_INVALID");
        }
        transaction(
                write,
                () -> {
                    var run = owned(lease);
                    var now = runs.now();
                    if (run.isCancelRequested())
                        throw new CancellationException("CDS_EXPORT_CANCELLED");
                    if (state(run) != CdsExportState.CAPTURING)
                        throw new IllegalStateException("CDS_EXPORT_PHASE_INVALID");
                    String event =
                            io.github.carlos_emr.carlos.commn.dto.CdsExportUnlinkedSourceDto.EVENT;
                    var recorded =
                            events.safeCodes(run.getId(), lease.epoch(), event, codes.size() + 2);
                    if (!recorded.isEmpty()) {
                        if (!new java.util.TreeSet<>(recorded).equals(codes)
                                || recorded.size() != codes.size())
                            throw new IllegalStateException("CDS_EXPORT_UNLINKED_SOURCE_CONFLICT");
                        return null;
                    }
                    for (String code : codes)
                        event(run, event, lease.document().actor().operatorKey(), code, now);
                    return null;
                });
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
                        // A stopped job that keeps finished charts for a resume: cancelling it
                        // erases them (the next retention sweep removes the kept attempts whole)
                        // and leaves its recorded outcome as it was. Nothing else of a stopped
                        // job can be cancelled.
                        if (run.isCancelRequested()) return null;
                        if (attempts.kept(id, now).isEmpty())
                            throw new IllegalStateException("CDS_EXPORT_CANCEL_INVALID");
                        run.setCancelRequested(true);
                        attempts.expireForRun(id, now);
                        run.setUpdatedAt(now);
                        event(run, "CANCEL_REQUESTED", operator, null, now);
                        return null;
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
                    if (!io.github.carlos_emr.carlos.utility.CdsImportKeys.sameDigest(
                            run.getCompatibilityDigest(), compatibilityDigest)) {
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
                        // The next claim starts a new generation that captures again: this
                        // generation's finished charts are kept for it to adopt, and the rest of
                        // its partial staging is no longer needed.
                        keepCheckpoint(run, now);
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
                            now,
                            true);
                    return null;
                });
    }

    private void finish(CdsExportRun run, CdsExportState outcome, String code, Instant now) {
        finish(run, outcome, code, now, false);
    }

    /**
     * Records the job's outcome.
     *
     * @param resumable true when the attempt itself stopped (a failure, a review, an expired or
     *     stalled worker) and the operator can resume the job: its finished charts are then kept
     *     for the resumed attempt ({@link #keepCheckpoint}). A cancellation, or a job that can
     *     never be resumed as it is (its runtime changed, its intent is unreadable), keeps nothing.
     */
    private void finish(
            CdsExportRun run, CdsExportState outcome, String code, Instant now, boolean resumable) {
        // A failed, refused or cancelled job has no package to keep: its staged selection,
        // captures and partial outputs become eligible for the next retention sweep at once,
        // instead of holding private data and disk reserve for the attempt's 7-day ceiling. Only
        // a resumable job keeps its finished charts, encrypted, for a resumed attempt to adopt.
        if (outcome == CdsExportState.FAILED || outcome == CdsExportState.REVIEW_REQUIRED) {
            if (resumable) keepCheckpoint(run, now);
            else attempts.expireForRun(run.getId(), now);
        } else if (outcome == CdsExportState.CANCELLED) attempts.expireForRun(run.getId(), now);
        run.setState(outcome.name());
        run.setSafeCode(code);
        run.setUpdatedAt(now);
        run.setOwnerToken(null);
        run.setLeaseUntil(null);
        event(run, outcome.name(), run.getOperator(), code, now);
    }

    /**
     * A job that stopped and can be resumed keeps its current attempt's finished charts: when the
     * attempt recorded a checkpoint, the attempt is kept ({@value #KEEPING}) until seven days from
     * now, and the retention sweep then erases every member of it the checkpoint does not pin.
     * An earlier attempt already kept stays as it is (until its own expiry, until a later attempt
     * has adopted past it, or until the job is cancelled). Every other attempt of the job is
     * released to the retention sweep at once, as before.
     */
    private void keepCheckpoint(CdsExportRun run, Instant now) {
        var current = attempts.lock(run.getId(), run.getOwnerEpoch());
        if (current != null
                && current.getCheckpointMember() != null
                && java.util.Set.of("CAPTURING", "PREPARED", "ENCRYPTED").contains(current.getState()))
            keep(run, current, now.plusSeconds(ATTEMPT_SECONDS), now);
        attempts.flush();
        attempts.expireUnkeptForRun(run.getId(), now);
    }

    /** Keeps one attempt's finished charts until {@code until}; audited with their count. */
    private void keep(CdsExportRun run, CdsExportAttempt attempt, Instant until, Instant now) {
        attempt.setState(KEEPING);
        attempt.setExpiresAt(until);
        attempt.setUpdatedAt(now);
        long charts = attempt.getCheckpointCharts() == null ? 0 : attempt.getCheckpointCharts();
        event(
                run,
                "CHARTS_KEPT",
                run.getOperator(),
                "CDS_EXPORT_CHARTS_KEPT_" + charts,
                now,
                attempt.getEpoch());
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
                || !requestDigest(lease.document()).equals(run.getRequestDigest())) {
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportOwnerFencedException();
        }
        return run;
    }

    /** An expired worker can record its failure, but cannot renew or replace a later generation. */
    public boolean retireExpiredLease(CdsExportLeaseDto lease) {
        return retireExpiredLease(
                lease, CdsExportState.REVIEW_REQUIRED, "CDS_EXPORT_EXECUTION_LEASE_EXPIRED");
    }

    /**
     * Retires this attempt's own expired generation under the given outcome and code: a worker
     * that was declared stalled ends as FAILED under its own code whether its lease had already
     * expired or not. A successor generation, a publication or any other recorded outcome stays
     * authoritative.
     *
     * @return true when this call recorded the outcome
     */
    public boolean retireExpiredLease(CdsExportLeaseDto lease, CdsExportState outcome, String code) {
        Objects.requireNonNull(lease);
        runId(lease.runId());
        if (outcome != CdsExportState.FAILED && outcome != CdsExportState.REVIEW_REQUIRED)
            throw new IllegalArgumentException("CDS_EXPORT_STOP_INVALID");
        safeCode(code);
        return transaction(
                write,
                () -> {
                    CdsExportRun run = required(runs.lock(lease.runId()));
                    Instant now = runs.now();
                    if (!Objects.equals(run.getOwnerToken(), lease.token())
                            || run.getOwnerEpoch() != lease.epoch()
                            || liveOwner(run, now)
                            || !state(run).executing()
                            || !requestDigest(lease.document()).equals(run.getRequestDigest()))
                        return false;
                    finish(
                            run,
                            run.isCancelRequested() ? CdsExportState.CANCELLED : outcome,
                            run.isCancelRequested() ? "CDS_EXPORT_CANCELLED" : code,
                            now,
                            true);
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
        // The latest attempt: what it adopted and built, as its recorded checkpoint says.
        var latest =
                run.getOwnerEpoch() < 1 ? null : attempts.find(run.getId(), run.getOwnerEpoch());
        if (available) {
            if (latest == null || !"PUBLISHED".equals(latest.getState())) available = false;
            else
                try {
                    available = published(run, latest, context(latest), now);
                } catch (IllegalStateException | IllegalArgumentException invalid) {
                    available = false;
                }
        }
        long adopted = 0, built = 0;
        if (latest != null && latest.getCheckpointCharts() != null) {
            adopted = latest.getAdoptedCharts() == null ? 0 : latest.getAdoptedCharts();
            built = Math.max(0, latest.getCheckpointCharts() - adopted);
        }
        // What stopped attempts keep for a resume. A published or expired job keeps nothing
        // (publication released it), so only other jobs are looked at.
        long kept = 0;
        Instant keptUntil = null;
        if (state(run) != CdsExportState.READY && state(run) != CdsExportState.EXPIRED)
            for (var attempt : attempts.kept(run.getId(), now)) {
                if (attempt.getCheckpointCharts() == null || attempt.getCheckpointCharts() < 1)
                    continue;
                kept = Math.max(kept, attempt.getCheckpointCharts());
                if (keptUntil == null || attempt.getExpiresAt().isBefore(keptUntil))
                    keptUntil = attempt.getExpiresAt();
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
                available,
                kept,
                keptUntil,
                adopted,
                built);
    }

    private static CdsExportState state(CdsExportRun run) {
        return CdsExportState.valueOf(run.getState());
    }

    private static CdsExportRun required(CdsExportRun run) {
        if (run == null) throw new IllegalStateException("CDS_EXPORT_RUN_NOT_FOUND");
        return run;
    }

    private static CdsExportJobDocumentDto document(CdsExportRun run) {
        String stored = run.getRequestDocument();
        String text = stored == null ? null : fixed(stored);
        if (text == null
                || text.length() > MAX_DOCUMENT
                || !digest(text).equals(run.getRequestDigest())) {
            throw new IllegalStateException("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        }
        try {
            CdsExportJobDocumentDto result =
                    text.length() == stored.length()
                            ? JSON.readValue(text, CdsExportJobDocumentDto.class)
                            : selected(text, stored.substring(text.length() + 1));
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

    /**
     * The stored form of a job document. A document of at most {@link #MAX_DOCUMENT} bytes is its
     * JSON, byte for byte as before. A longer one, which only an explicit selection can make, is
     * two lines: the same JSON with both selection lists empty and a {@code selection} member
     * giving the count of each list and the SHA-256 of the second line; then the second line,
     * {@code {"patientNos":[...],"physicianNos":[...]}} in the command's own sorted order. No
     * length refuses a request. The request digest names the first line ({@link #fixed}), which
     * names the selection by its digest, so a job stays bound to exactly its selection.
     */
    private static String encode(CdsExportJobDocumentDto document) {
        try {
            String text = JSON.writeValueAsString(document);
            if (text.getBytes(StandardCharsets.UTF_8).length <= MAX_DOCUMENT) return text;
            var command = document.command();
            var selection = new StringBuilder("{\"patientNos\":[");
            String separator = "";
            for (Integer patient : command.patientNos()) {
                selection.append(separator).append(patient.intValue());
                separator = ",";
            }
            selection.append("],\"physicianNos\":[");
            separator = "";
            // A provider number is letters, digits, "_" and "-" (CdsExportCommand): no escape.
            for (String physician : command.physicianNos()) {
                selection.append(separator).append('"').append(physician).append('"');
                separator = ",";
            }
            String second = selection.append("]}").toString();
            // The same members the one-line form has, so a member added to the document later
            // is carried here too; only the two lists move to the second line.
            com.fasterxml.jackson.databind.node.ObjectNode head = JSON.valueToTree(document);
            var intent = (com.fasterxml.jackson.databind.node.ObjectNode) head.get("command");
            intent.putArray("patientNos");
            intent.putArray("physicianNos");
            head.putObject("selection")
                    .put("patients", command.patientNos().size())
                    .put("physicians", command.physicianNos().size())
                    .put("sha256", digest(second));
            String first = JSON.writeValueAsString(head);
            // Everything left on the first line has a fixed greatest length; this cannot happen.
            if (first.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT || first.indexOf('\n') >= 0)
                throw new IllegalArgumentException("CDS_EXPORT_JOB_ENCODING_FAILED");
            return first + "\n" + second;
        } catch (IOException failure) {
            throw new IllegalArgumentException("CDS_EXPORT_JOB_ENCODING_FAILED");
        }
    }

    /** The document's own line: all of a one-line document, or the part before the selection. */
    private static String fixed(String stored) {
        int end = stored.indexOf('\n');
        return end < 0 ? stored : stored.substring(0, end);
    }

    /**
     * Reads a two-line document back. The second line must be exactly the selection the first
     * line names (SHA-256 and both counts) before either list is used.
     */
    private static CdsExportJobDocumentDto selected(String first, String second)
            throws IOException {
        var head = JSON.readTree(first);
        if (!(head instanceof com.fasterxml.jackson.databind.node.ObjectNode document)
                || !(document.remove("selection")
                        instanceof com.fasterxml.jackson.databind.node.ObjectNode named)
                || !(document.get("command")
                        instanceof com.fasterxml.jackson.databind.node.ObjectNode command)
                || named.size() != 3
                || !named.path("patients").isInt()
                || !named.path("physicians").isInt()
                || !named.path("sha256").isTextual()
                || !digest(second).equals(named.get("sha256").textValue())
                || !command.path("patientNos").isEmpty()
                || !command.path("physicianNos").isEmpty())
            throw new IllegalStateException("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        var lists = JSON.readTree(second);
        if (lists == null
                || lists.size() != 2
                || !lists.path("patientNos").isArray()
                || !lists.path("physicianNos").isArray()
                || lists.get("patientNos").size() != named.get("patients").intValue()
                || lists.get("physicianNos").size() != named.get("physicians").intValue())
            throw new IllegalStateException("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        command.set("patientNos", lists.get("patientNos"));
        command.set("physicianNos", lists.get("physicianNos"));
        return JSON.treeToValue(document, CdsExportJobDocumentDto.class);
    }

    /**
     * The request digest of a lease's document. A lease keeps one immutable document for its
     * whole attempt and every checkpoint proves it again, so the digests of the documents last
     * proven are remembered by identity: a long explicit selection is encoded once, not at every
     * checkpoint.
     */
    private String requestDigest(CdsExportJobDocumentDto document) {
        for (int index = 0; index < leaseDigests.length(); index++) {
            var known = leaseDigests.get(index);
            if (known != null && known.document() == document) return known.digest();
        }
        String digest = digest(fixed(encode(document)));
        leaseDigests.set(
                Math.floorMod(nextLeaseDigest.getAndIncrement(), leaseDigests.length()),
                new LeaseDigest(document, digest));
        return digest;
    }

    private record LeaseDigest(CdsExportJobDocumentDto document, String digest) {}

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
