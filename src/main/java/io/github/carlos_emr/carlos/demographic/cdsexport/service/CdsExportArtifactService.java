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

/** Authorized recipient-ciphertext retrieval shared by HTTP and CLI. No request selects a physical staging member. */
@Service
public class CdsExportArtifactService {
    private final CdsExportLedgerService ledger;
    private final CdsExportStagingFactory stores;
    private final CdsExportDisclosureScopeService scopes;
    private final CdsExportDisclosurePolicyService disclosure;
    private final CdsExportAccessService access;
    private final CdsExportEngineRegistry engines;

    public CdsExportArtifactService(
            CdsExportLedgerService ledger,
            CdsExportStagingFactory stores,
            CdsExportDisclosureScopeService scopes,
            CdsExportDisclosurePolicyService disclosure,
            CdsExportAccessService access,
            CdsExportEngineRegistry engines) {
        this.ledger = Objects.requireNonNull(ledger);
        this.stores = Objects.requireNonNull(stores);
        this.scopes = Objects.requireNonNull(scopes);
        this.disclosure = Objects.requireNonNull(disclosure);
        this.access = Objects.requireNonNull(access);
        this.engines = Objects.requireNonNull(engines);
    }

    /** Caller has reconstructed the current observer in the original provider/facility scope. */
    public Download open(CdsExportActorDto actor, String runId, String artifact)
            throws IOException {
        if (!"package".equals(artifact))
            throw new IllegalArgumentException("CDS_EXPORT_ARTIFACT_ID_INVALID");
        var intent = ledger.document(runId);
        access.requireObserve(actor, intent);
        access.requireScope(actor, intent.command());
        CdsExportLedgerService.Download lease;
        try {
            lease = ledger.beginDownload(runId, actor.operatorKey());
        } catch (IllegalStateException unavailable) {
            if ("CDS_EXPORT_ARTIFACT_UNAVAILABLE".equals(unavailable.getMessage()))
                throw new CdsExportArtifactUnavailableException(
                        CdsExportArtifactUnavailableException.Kind.PACKAGE);
            throw unavailable;
        }
        var download = new Download(actor, lease);
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

    /** A single-use, finite delivery; the durable lease also protects a slow/blocked output from retention. */
    public final class Download implements AutoCloseable {
        private final CdsExportActorDto actor;
        private final CdsExportLedgerService.Download lease;
        private final long started = System.nanoTime();
        private long lastProbe = started - 1_000_000_000L, lastScope;
        private final AtomicReference<RuntimeException> heartbeatFailure = new AtomicReference<>();
        private final ScheduledExecutorService heartbeat;
        private CdsExportStagingService store;
        private boolean used, written, closed, completed;
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
                    public void authorizePatient(int patient) {
                        probe();
                        access.requirePatient(actor, lease.document().command(), patient);
                    }

                    @Override
                    public void authorizeCategory(
                            int patient, CdsCategory category, boolean sensitive) {
                        probe();
                        access.requireCategory(
                                actor, lease.document().command(), patient, category, sensitive);
                    }

                    @Override
                    public void authorizePharmacy(int patient) {
                        probe();
                        access.requirePharmacyRead(actor, lease.document().command(), patient);
                    }

                    @Override
                    public void authorizeCaseManagement(int patient) {
                        probe();
                        access.requireCaseManagementRead(
                                actor, lease.document().command(), patient);
                    }

                    @Override
                    public void authorizeNote(CdsCategory category, CdsExportNoteAccessDto note) {
                        probe();
                        access.requireNoteRead(actor, lease.document().command(), category, note);
                    }
                };

        private Download(CdsExportActorDto actor, CdsExportLedgerService.Download lease) {
            this.actor = actor;
            this.lease = lease;
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
        }

        public long bytes() {
            return lease.context().ciphertext().plaintextBytes();
        }

        public String sha256() {
            return lease.context().ciphertext().plaintextSha256();
        }

        private void local() {
            if (Thread.currentThread().isInterrupted()
                    || System.nanoTime() - started > TimeUnit.MINUTES.toNanos(15))
                throw new CancellationException("CDS_EXPORT_DOWNLOAD_STOPPED");
            var failed = heartbeatFailure.get();
            if (failed != null) throw failed;
        }

        private void authority() {
            var intent = lease.document();
            access.requireObserve(actor, intent);
            access.requireScope(actor, intent.command());
            if (!intent.compatibilityDigest()
                    .equals(engines.require(intent.command().profile()).compatibilityDigest()))
                throw new IllegalStateException("CDS_EXPORT_RUNTIME_CHANGED");
            disclosure.resolveAccepted(intent, Instant.now());
        }

        private void probe() {
            local();
            long now = System.nanoTime();
            if (now - lastProbe >= 1_000_000_000L) {
                authority();
                ledger.downloadCheckpoint(lease);
                lastProbe = now;
            }
        }

        private void fullScope() throws IOException {
            probe();
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
            lastScope = System.nanoTime();
        }

        public void writeTo(OutputStream output) throws IOException {
            Objects.requireNonNull(output);
            if (closed || used) throw new IllegalStateException("CDS_EXPORT_DOWNLOAD_ALREADY_USED");
            used = true;
            try (var input = store.open(lease.context().ciphertext())) {
                byte[] buffer = new byte[64 * 1024];
                long count = 0;
                try {
                    for (int n; (n = input.read(buffer)) != -1; ) {
                        probe();
                        if (System.nanoTime() - lastScope >= TimeUnit.SECONDS.toNanos(30))
                            fullScope();
                        count = Math.addExact(count, n);
                        if (count > bytes())
                            throw new IOException("CDS_EXPORT_DOWNLOAD_LENGTH_CHANGED");
                        output.write(buffer, 0, n);
                    }
                    if (count != bytes())
                        throw new IOException("CDS_EXPORT_DOWNLOAD_LENGTH_CHANGED");
                    output.flush();
                    fullScope();
                    authority();
                    ledger.downloadCheckpoint(lease);
                } finally {
                    java.util.Arrays.fill(buffer, (byte) 0);
                }
            }
            written = true;
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
            if (!completed) ledger.finishDownload(lease, false);
        }

        @Override
        public String toString() {
            return "CdsExportArtifactDownload[protected]";
        }
    }
}
