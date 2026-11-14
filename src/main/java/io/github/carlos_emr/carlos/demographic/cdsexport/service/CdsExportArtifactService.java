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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportArtifactUnavailableException;
import java.io.*;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * Authorized recipient-ciphertext retrieval shared by HTTP and CLI. No request selects a physical
 * staging member.
 */
@Service
public class CdsExportArtifactService {
    private final CdsExportLedgerService ledger;
    private final CdsExportStagingFactory stores;
    private final CdsExportDisclosureScopeService scopes;
    private final CdsExportDisclosurePolicyService disclosure;
    private final CdsExportAccessService access;
    private final java.util.Properties settings = new java.util.Properties();
    /** The monotonic clock a download measures its silence with; a test supplies its own. */
    private final java.util.function.LongSupplier clock;

    /**
     * Once the first byte is sent, a cohort re-verification pass writes the next block of the
     * package whenever this long has passed without one, so the transfer is never silent for a
     * whole pass: a reverse proxy (nginx's {@code proxy_read_timeout}, 300 s as packaged) or a
     * browser would otherwise cut a download whose pass outlasts its timeout. Bytes are sent only
     * after the pass that runs before the first byte has authorized the whole cohort.
     */
    static final long KEEP_ALIVE_NANOS = TimeUnit.SECONDS.toNanos(10);

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportArtifactService(
            CdsExportLedgerService ledger,
            CdsExportStagingFactory stores,
            CdsExportDisclosureScopeService scopes,
            CdsExportDisclosurePolicyService disclosure,
            CdsExportAccessService access,
            @org.springframework.beans.factory.annotation.Qualifier("cdsExportProperties")
                    java.util.Properties settings) {
        this(ledger, stores, scopes, disclosure, access, settings, System::nanoTime);
    }

    public CdsExportArtifactService(
            CdsExportLedgerService ledger,
            CdsExportStagingFactory stores,
            CdsExportDisclosureScopeService scopes,
            CdsExportDisclosurePolicyService disclosure,
            CdsExportAccessService access) {
        this(ledger, stores, scopes, disclosure, access, new java.util.Properties(), System::nanoTime);
    }

    CdsExportArtifactService(
            CdsExportLedgerService ledger,
            CdsExportStagingFactory stores,
            CdsExportDisclosureScopeService scopes,
            CdsExportDisclosurePolicyService disclosure,
            CdsExportAccessService access,
            java.util.Properties settings,
            java.util.function.LongSupplier clock) {
        this.ledger = Objects.requireNonNull(ledger);
        this.stores = Objects.requireNonNull(stores);
        this.scopes = Objects.requireNonNull(scopes);
        this.disclosure = Objects.requireNonNull(disclosure);
        this.access = Objects.requireNonNull(access);
        this.settings.putAll(Objects.requireNonNull(settings));
        this.clock = Objects.requireNonNull(clock);
    }

    /** Caller has reconstructed the current observer in the original provider/facility scope. */
    public Download open(CdsExportActorDto actor, String runId, String artifact)
            throws IOException {
        if (!"package".equals(artifact))
            throw new IllegalArgumentException("CDS_EXPORT_ARTIFACT_ID_INVALID");
        var intent = ledger.document(runId);
        authority(actor, intent);
        long timeoutNanos =
                TimeUnit.SECONDS.toNanos(
                        CdsExportActivePlanService.number(
                                settings, "downloadTimeoutSeconds", 900, 1, 86400));
        CdsExportLedgerService.Download lease;
        try {
            lease = ledger.beginDownload(runId, actor.operatorKey());
        } catch (IllegalStateException unavailable) {
            if ("CDS_EXPORT_ARTIFACT_UNAVAILABLE".equals(unavailable.getMessage()))
                throw new CdsExportArtifactUnavailableException(
                        CdsExportArtifactUnavailableException.Kind.PACKAGE);
            throw unavailable;
        }
        var download = new Download(actor, lease, timeoutNanos);
        try {
            download.initialize();
            return download;
        } catch (IOException | RuntimeException | Error failed) {
            try {
                download.close();
            } catch (IOException | RuntimeException cleanup) {
                failed.addSuppressed(cleanup);
            }
            throw failed;
        }
    }

    /**
     * A ready package is already validated and encrypted to its pinned recipient, so retrieving
     * it does not depend on the application build that made it (maintainer decision 8): an
     * upgrade must not strand a finished transfer. Retrieval still requires current observation
     * and disclosure scope, the accepted recipient/audit policy, the original store binding and
     * the published artifact digest.
     */
    private void authority(CdsExportActorDto actor, CdsExportJobDocumentDto intent) {
        access.requireObserve(actor, intent);
        access.requireScope(actor, intent.command());
        disclosure.resolveAccepted(intent, Instant.now());
    }

    /**
     * A single-use delivery of a package of any size; the durable lease also protects a slow
     * output from retention, and a transfer that stops moving is ended by its heartbeat.
     */
    public final class Download implements AutoCloseable {
        private final CdsExportActorDto actor;
        private final CdsExportLedgerService.Download lease;
        private final long started = clock.getAsLong();
        private final long timeoutNanos;

        /**
         * When the download thread last made progress (a block written, a verification step).
         * {@code cds.export.downloadTimeoutSeconds} bounds the time since then and nothing else:
         * it detects a transfer that has stopped moving, and never ends one for how long a
         * package of any size takes to send. Only the lease heartbeat can observe the silence.
         */
        private final java.util.concurrent.atomic.AtomicLong progress =
                new java.util.concurrent.atomic.AtomicLong(started);
        private long lastProbe = started - 1_000_000_000L, lastScope;
        /** Duration of the latest complete cohort re-verification, and the allowance it earns. */
        private long lastScopeNanos, verificationAllowanceNanos;
        private final AtomicReference<RuntimeException> heartbeatFailure = new AtomicReference<>();
        private final ScheduledExecutorService heartbeat;
        private CdsExportStagingService store;
        /** The transfer once its first block may be sent, until its last; null otherwise. */
        private Stream stream;
        private boolean used, written, outputWritten, closed, completed;
        /**
         * The patient whose re-authorization pass is open, or null. A download reads no patient
         * data; each cohort verification opens one pass per patient and discards it.
         */
        private CdsExportPatientAuthorization pass;

        private CdsExportPatientAuthorization pass(int patient) {
            var current = pass;
            return current != null && current.patient() == patient ? current : null;
        }

        private final CdsExportRunControl control =
                new CdsExportRunControl() {
                    @Override
                    public void checkpoint() {
                        probe();
                    }

                    @Override
                    public void captureCheckpoint() {
                        throw new IllegalStateException("CDS_EXPORT_DOWNLOAD_CAPTURE_REFUSED");
                    }

                    @Override
                    public void beginAuthorizationPass(int patient) {
                        probe();
                        pass = null;
                        pass =
                                CdsExportPatientAuthorization.pass(
                                        patient,
                                        new CdsExportPatientAuthorization.Live(
                                                access, actor, lease.document().command()));
                    }

                    @Override
                    public void endAuthorizationPass() {
                        pass = null;
                    }

                    @Override
                    public void authorizePatient(int patient) {
                        probe();
                        var current = pass(patient);
                        if (current != null) current.patient(patient);
                        else access.requirePatient(actor, lease.document().command(), patient);
                    }

                    @Override
                    public void authorizeCategory(
                            int patient, CdsCategory category, boolean sensitive) {
                        probe();
                        var current = pass(patient);
                        if (current != null) current.category(patient, category, sensitive);
                        else
                            access.requireCategory(
                                    actor,
                                    lease.document().command(),
                                    patient,
                                    category,
                                    sensitive);
                    }

                    @Override
                    public void authorizePharmacy(int patient) {
                        probe();
                        var current = pass(patient);
                        if (current != null) current.pharmacy(patient);
                        else
                            access.requirePharmacyRead(
                                    actor, lease.document().command(), patient);
                    }

                    @Override
                    public void authorizeCaseManagement(int patient) {
                        probe();
                        var current = pass(patient);
                        if (current != null) current.caseManagement(patient);
                        else
                            access.requireCaseManagementRead(
                                    actor, lease.document().command(), patient);
                    }

                    @Override
                    public void authorizeNote(CdsCategory category, CdsExportNoteAccessDto note) {
                        probe();
                        access.requireNoteRead(actor, lease.document().command(), category, note);
                    }

                    @Override
                    public boolean issueProgramReadable(
                            int patient, CdsCategory category, String program) {
                        probe();
                        var current = pass(patient);
                        if (current != null)
                            return current.issueProgram(patient, category, program);
                        return access.canReadIssueProgram(
                                actor, lease.document().command(), category, patient, program);
                    }

                    @Override
                    public boolean restrictedProgramReadable(
                            int patient, CdsCategory category, String program) {
                        probe();
                        var current = pass(patient);
                        if (current != null)
                            return current.restrictedProgram(patient, category, program);
                        return access.canReadRestrictedProgram(
                                actor, lease.document().command(), category, patient, program);
                    }

                    @Override
                    public boolean recordReadable(int patient, String object) {
                        probe();
                        var current = pass(patient);
                        if (current != null) return current.record(patient, object);
                        return access.canReadRecordObject(
                                actor, lease.document().command(), patient, object);
                    }

                    @Override
                    public void authorizeNotes(
                            CdsCategory category, java.util.List<CdsExportNoteAccessDto> notes) {
                        probe();
                        access.requireNotesRead(actor, lease.document().command(), category, notes);
                        probe();
                    }
                };

        private Download(
                CdsExportActorDto actor, CdsExportLedgerService.Download lease, long timeoutNanos) {
            this.actor = actor;
            this.lease = lease;
            this.timeoutNanos = timeoutNanos;
            heartbeat =
                    Executors.newSingleThreadScheduledExecutor(
                            work -> {
                                var thread = new Thread(work, "cds-export-download-lease");
                                thread.setDaemon(true);
                                return thread;
                            });
            heartbeat.scheduleWithFixedDelay(
                    () -> {
                        try {
                            local();
                            authority();
                            ledger.downloadCheckpoint(lease);
                        } catch (RuntimeException failed) {
                            heartbeatFailure.compareAndSet(null, failed);
                        }
                    },
                    20,
                    20,
                    TimeUnit.SECONDS);
        }

        private void initialize() throws IOException {
            authority();
            store =
                    stores.openExisting(
                            lease.runId(),
                            lease.epoch(),
                            lease.document().storage(),
                            control::checkpoint);
            fullScope();
            // The cohort is re-verified at least three more times (during streaming, after the
            // last byte and at completion), and a large roster's pass can take minutes. The
            // configured deadline is for the transfer itself, so each download also earns three
            // times its measured first pass, never beyond the 24 h configuration ceiling
            // (review EC-3). Liveness is still proven every 20 s by the lease heartbeat.
            verificationAllowanceNanos =
                    Math.min(
                            lastScopeNanos > Long.MAX_VALUE / 3
                                    ? Long.MAX_VALUE
                                    : 3 * lastScopeNanos,
                            Math.max(0, TimeUnit.SECONDS.toNanos(86_400) - timeoutNanos));
        }

        public long bytes() {
            return lease.context().ciphertext().plaintextBytes();
        }

        public String sha256() {
            return lease.context().ciphertext().plaintextSha256();
        }

        /**
         * True when the delivery is a tar archive of CMS parts (a package larger than one AES-GCM
         * message), false when it is one CMS object.
         */
        public boolean parts() {
            return CdsExportDeliveryComposer.PARTS_PROFILE.equals(
                    lease.context().delivery().encryptionProfile());
        }

        private void local() {
            if (Thread.currentThread().isInterrupted()
                    || clock.getAsLong() - progress.get()
                            > timeoutNanos + verificationAllowanceNanos)
                throw new CancellationException("CDS_EXPORT_DOWNLOAD_STOPPED");
            var failed = heartbeatFailure.get();
            if (failed != null) throw failed;
        }

        private void authority() {
            CdsExportArtifactService.this.authority(actor, lease.document());
        }

        private void probe() {
            // Only the download thread probes: reaching here is its proof of progress.
            progress.set(clock.getAsLong());
            local();
            long now = clock.getAsLong();
            if (now - lastProbe >= 1_000_000_000L) {
                authority();
                ledger.downloadCheckpoint(lease);
                lastProbe = now;
            }
            // A pass during the transfer keeps bytes flowing: every authorization step that comes
            // KEEP_ALIVE_NANOS after the last block sends the next one. Its output failure is the
            // transfer's (see fullScope).
            var transfer = stream;
            if (transfer != null && transfer.verifying && now - transfer.lastWrite >= KEEP_ALIVE_NANOS) {
                try {
                    transfer.next();
                } catch (IOException failed) {
                    throw new KeepAliveFailure(failed);
                }
            }
        }

        private void fullScope() throws IOException {
            var transfer = stream;
            if (transfer != null) transfer.verifying = true;
            try {
                verifyScope();
            } catch (KeepAliveFailure failed) {
                throw failed.getCause();
            } finally {
                if (transfer != null) transfer.verifying = false;
            }
        }

        private void verifyScope() throws IOException {
            probe();
            long began = clock.getAsLong();
            var c = lease.context();
            var verified =
                    scopes.verifyRetained(
                            lease.runId(),
                            lease.epoch(),
                            lease.document(),
                            c.cohort(),
                            c.authorization(),
                            c.patients(),
                            control);
            if (!lease.runId().equals(verified.runId())
                    || lease.epoch() != verified.epoch()
                    || c.patients() != verified.patients()
                    || !c.cohort().plaintextSha256().equals(verified.cohortSha256())
                    || !c.authorization().plaintextSha256().equals(verified.authorizationSha256()))
                throw new IOException("CDS_EXPORT_DOWNLOAD_SCOPE_INVALID");
            lastScope = clock.getAsLong();
            lastScopeNanos = Math.max(0, lastScope - began);
        }

        /**
         * While streaming, the cohort is re-verified every 30 s, but never sooner after the last
         * pass than that pass took, so re-verification uses at most half of the streaming time.
         */
        boolean scopeDue(long now) {
            return now - lastScope >= Math.max(TimeUnit.SECONDS.toNanos(30), lastScopeNanos);
        }

        public void writeTo(OutputStream output) throws IOException {
            Objects.requireNonNull(output);
            if (closed || used) throw new IllegalStateException("CDS_EXPORT_DOWNLOAD_ALREADY_USED");
            used = true;
            try (var input = store.open(lease.context().ciphertext())) {
                var transfer = new Stream(input, output);
                stream = transfer;
                try {
                    do {
                        probe();
                        if (scopeDue(clock.getAsLong())) fullScope();
                    } while (transfer.next());
                    if (transfer.count != bytes())
                        throw new IOException("CDS_EXPORT_DOWNLOAD_LENGTH_CHANGED");
                    stream = null;
                    output.flush();
                    // The recipient may have all ciphertext even if final authority or
                    // audit confirmation subsequently fails. Do not call that no delivery.
                    outputWritten = true;
                    fullScope();
                    authority();
                    ledger.downloadCheckpoint(lease);
                } finally {
                    stream = null;
                    transfer.erase();
                }
            }
            written = true;
        }

        /**
         * The ciphertext being sent: the main loop and a re-verification pass inside it write its
         * blocks in order, one thread only, each checked against the length the ledger pinned.
         */
        private final class Stream {
            private final InputStream input;
            private final OutputStream output;
            private final byte[] buffer = new byte[64 * 1024];
            private long count;
            private long lastWrite = clock.getAsLong();
            private boolean ended, verifying;

            Stream(InputStream input, OutputStream output) {
                this.input = input;
                this.output = output;
            }

            /** Sends the next block; false once the ciphertext has ended. */
            boolean next() throws IOException {
                if (ended) return false;
                int n = input.read(buffer);
                if (n == -1) {
                    ended = true;
                    return false;
                }
                count = Math.addExact(count, n);
                if (count > bytes()) throw new IOException("CDS_EXPORT_DOWNLOAD_LENGTH_CHANGED");
                output.write(buffer, 0, n);
                lastWrite = clock.getAsLong();
                return true;
            }

            void erase() {
                java.util.Arrays.fill(buffer, (byte) 0);
            }
        }

        /** An output failure of a block sent during a pass, carried out of the pass unchecked. */
        private static final class KeepAliveFailure extends UncheckedIOException {
            KeepAliveFailure(IOException cause) {
                super(cause);
            }
        }

        /** Called after the adapter has successfully flushed/committed its own output. */
        public void complete() throws IOException {
            if (closed || !written || completed)
                throw new IllegalStateException("CDS_EXPORT_DOWNLOAD_NOT_DELIVERED");
            fullScope();
            authority();
            ledger.downloadCheckpoint(lease);
            ledger.finishDownload(lease, true);
            completed = true;
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            heartbeat.shutdownNow();
            if (!completed) ledger.finishDownload(lease, false, outputWritten);
        }

        @Override
        public String toString() {
            return "CdsExportArtifactDownload[protected]";
        }
    }
}
