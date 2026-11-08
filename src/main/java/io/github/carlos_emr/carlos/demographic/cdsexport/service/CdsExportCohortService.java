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

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dto.CdsExportSelectionRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCohortManifestDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportSelectionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fully verifies selection bytes before issuing a durable cohort proof. Native capture can receive
 * a patient scope only from a current cursor over that sealed cohort, including physician rosters.
 * Preparing files and publishing their durable pointer are separate boundaries: after prepare(),
 * the caller passes Sealed to ledger.sealSelection(). A lost DB acknowledgement retains private
 * files and retries that exact proof; it never deletes a possibly committed selection or silently
 * reruns discovery. No clinical completeness, output validation or READY is implied by a seal.
 */
@Service
public class CdsExportCohortService {
    private static final int MAX_MANIFEST = 524_288;
    private static final JsonMapper JSON = JsonMapper.builder(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(24).maxStringLength(262_144)
                    .maxNumberLength(20).build()).build())
            .addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final CdsExportStagingFactory stores;
    private final CdsExportLedgerService ledger;
    public CdsExportCohortService(CdsExportStagingFactory stores, CdsExportLedgerService ledger) {
        this.stores = stores; this.ledger = ledger;
    }
    /** The caller already owns this selection file and must retain it across an uncertain subsequent DB seal. */
    public Sealed prepare(CdsExportLeaseDto lease, CdsExportSelectionDto selection, CdsExportRunControl control) throws IOException {
        outside(); Objects.requireNonNull(control); Objects.requireNonNull(selection); control.checkpoint();
        requireBound(lease); if (selection.patients() < 1) throw new IOException("CDS_EXPORT_NO_PATIENTS_SELECTED");
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), control::checkpoint);
        verifySelection(store, lease.document().command(), selection, control);
        var manifest = new CdsExportCohortManifestDto(1, lease.runId(), lease.epoch(), lease.document(), selection);
        CdsExportStagedFileDto member = null; boolean complete = false; Throwable primary = null;
        try {
            byte[] bytes = JSON.writeValueAsBytes(manifest);
            if (bytes.length > MAX_MANIFEST) throw new IOException("CDS_EXPORT_COHORT_MANIFEST_LIMIT");
            member = store.stage("COHORT_MANIFEST", output -> output.write(bytes));
            var readback = manifest(store, member);
            if (!readback.equals(manifest)) invalid(); control.checkpoint();
            var result = new Sealed(lease, manifest, member); complete = true; return result;
        } catch (IOException | RuntimeException | Error failed) { primary = failed; throw failed; }
        finally {
            if (!complete && member != null) try { store.discardUnpublished(member); }
            catch (IOException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
        }
    }
    /** Opens a prepared seal only after the ledger has durably accepted that exact pointer under the live owner. */
    public Cursor open(Sealed sealed, CdsExportRunControl control) throws IOException {
        outside(); Objects.requireNonNull(sealed); Objects.requireNonNull(control); control.checkpoint();
        if (!ledger.cohort(sealed.lease).equals(sealed.member)) invalid();
        var store = stores.openExisting(sealed.lease.runId(), sealed.lease.epoch(), sealed.lease.document().storage(), control::checkpoint);
        return new Cursor(sealed, store.open(sealed.manifest.selection().inventory()), control);
    }
    /** Recovery within a live attempt loads its exact committed manifest; it never rediscovers a roster. */
    public Cursor open(CdsExportLeaseDto lease, CdsExportRunControl control) throws IOException {
        outside(); requireBound(lease); control.checkpoint(); var member = ledger.cohort(lease);
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), control::checkpoint);
        var manifest = manifest(store, member);
        if (!manifest.runId().equals(lease.runId()) || manifest.epoch() != lease.epoch() || !manifest.intent().equals(lease.document())) invalid();
        verifySelection(store, lease.document().command(), manifest.selection(), control);
        return new Cursor(new Sealed(lease, manifest, member), store.open(manifest.selection().inventory()), control);
    }
    @FunctionalInterface public interface RetainedPatients { void accept(CdsExportSelectionRowDto patient) throws IOException; }
    /**
     * Reads protected retained selection metadata after the caller authorizes its published
     * artifact context. Exact intent/epoch/count and authenticated EOF remain mandatory.
     * The callback receives identifiers, never a Patient capture capability or execution lease.
     */
    public long readRetained(CdsExportStagingService store, String runId, long epoch, CdsExportJobDocumentDto intent,
            CdsExportStagedFileDto file, long expectedPatients, Runnable checkpoint, RetainedPatients patients) throws IOException {
        outside(); Objects.requireNonNull(store); Objects.requireNonNull(intent); Objects.requireNonNull(checkpoint); Objects.requireNonNull(patients);
        if (expectedPatients < 1 || expectedPatients > 2_000_000) invalid(); checkpoint.run();
        var manifest = manifest(store, file);
        if (!manifest.runId().equals(runId) || manifest.epoch() != epoch || !manifest.intent().equals(intent)
                || manifest.selection().patients() != expectedPatients) invalid();
        long count = 0;
        try (var input = store.open(manifest.selection().inventory())) {
            var rows = new Rows(input, intent.command(), expectedPatients, checkpoint); CdsExportSelectionRowDto row;
            while ((row = rows.next()) != null) { checkpoint.run(); patients.accept(row); count++; }
            if (!rows.eof || count != expectedPatients) invalid();
        }
        checkpoint.run(); return count;
    }
    private static CdsExportCohortManifestDto manifest(CdsExportStagingService store, CdsExportStagedFileDto file) throws IOException {
        if (!file.purpose().equals("COHORT_MANIFEST") || file.plaintextBytes() < 1 || file.plaintextBytes() > MAX_MANIFEST) invalid();
        try (var input = store.open(file)) {
            byte[] bytes = input.readNBytes(MAX_MANIFEST + 1);
            if (bytes.length > MAX_MANIFEST || input.read() != -1) invalid();
            return JSON.readValue(bytes, CdsExportCohortManifestDto.class);
        }
    }
    private static void verifySelection(CdsExportStagingService store, CdsExportCommand command,
            CdsExportSelectionDto selection, CdsExportRunControl control) throws IOException {
        try (var input = store.open(selection.inventory())) {
            var rows = new Rows(input, command, selection.patients(), control);
            while (rows.next() != null) { /* Full authenticated EOF before any patient scope is issued. */ }
        }
    }
    public static final class Sealed {
        private final CdsExportLeaseDto lease;
        private final CdsExportCohortManifestDto manifest;
        private final CdsExportStagedFileDto member;
        private Sealed(CdsExportLeaseDto lease, CdsExportCohortManifestDto manifest, CdsExportStagedFileDto member) {
            this.lease = lease; this.manifest = manifest; this.member = member;
        }
        void require(CdsExportLeaseDto supplied) { if (!lease.equals(supplied)) throw new IllegalStateException("CDS_EXPORT_COHORT_SCOPE_MISMATCH"); }
        public CdsExportStagedFileDto member() { return member; }
        public long patients() { return manifest.selection().patients(); }
        public Instant readCompletedAt() { return manifest.selection().readCompletedAt(); }
        @Override public String toString() { return "CdsExportSealedCohort[protected]"; }
    }
    public static final class Patient {
        private final Cursor cursor; private final long generation; private final CdsExportSelectionRowDto row;
        private Patient(Cursor cursor, long generation, CdsExportSelectionRowDto row) { this.cursor = cursor; this.generation = generation; this.row = row; }
        void require(CdsExportLeaseDto lease) {
            cursor.sealed.require(lease);
            if (cursor.closed || cursor.failed || cursor.generation != generation || Thread.currentThread() != cursor.owner) throw new IllegalStateException("CDS_EXPORT_PATIENT_SCOPE_EXPIRED");
        }
        public int patient() { return row.demographicNo(); }
        public CdsExportSelectionRowDto selection() { return row; }
        public String cohortSha256() { return cursor.sealed.member.plaintextSha256(); }
        @Override public String toString() { return "CdsExportCohortPatient[protected]"; }
    }
    public static final class Cursor implements AutoCloseable {
        private final Sealed sealed; private final InputStream input; private final Rows rows;
        private volatile boolean closed, failed; private volatile long generation;
        private final Thread owner = Thread.currentThread();
        private Cursor(Sealed sealed, InputStream input, CdsExportRunControl control) {
            this.sealed = sealed; this.input = input; rows = new Rows(input, sealed.lease.document().command(), sealed.patients(), control);
        }
        public Patient next() throws IOException {
            try {
                if (closed || failed || Thread.currentThread() != owner) throw new IOException("CDS_EXPORT_COHORT_CURSOR_CLOSED");
                generation++; var row = rows.next(); return row == null ? null : new Patient(this, generation, row);
            } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
        }
        public boolean complete() { return !failed && rows.eof; }
        @Override public void close() throws IOException { closed = true; generation++; input.close(); }
        @Override public String toString() { return "CdsExportCohortCursor[protected]"; }
    }
    private static final class Rows {
        private final CdsExportJsonLines lines; private final CdsExportCommand command; private final long expected;
        private long count; private int previous; private boolean eof;
        Rows(InputStream input, CdsExportCommand command, long expected, CdsExportRunControl control) {
            this(input, command, expected, control::checkpoint);
        }
        Rows(InputStream input, CdsExportCommand command, long expected, Runnable checkpoint) {
            lines = new CdsExportJsonLines(input, 1024, checkpoint); this.command = command; this.expected = expected;
        }
        CdsExportSelectionRowDto next() throws IOException {
            if (eof) return null; var node = lines.next();
            if (node == null) {
                if (count != expected || command.selection() == CdsExportCommand.Selection.SELECTED_PATIENTS
                        && count != command.patientNos().size()) invalid();
                eof = true; return null;
            }
            if (node.size() != 2 || !node.has("demographicNo") || !node.has("physicianNo") || count >= expected) invalid();
            var id = node.get("demographicNo"); var physician = node.get("physicianNo");
            if (!id.isIntegralNumber() || !id.canConvertToInt() || id.intValue() < 1 || id.intValue() <= previous
                    || !(physician.isNull() || physician.isTextual()) || physician.isTextual()
                    && (physician.textValue().length() > 16 || physician.textValue().codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp >= 0xd800 && cp <= 0xdfff))) invalid();
            int patient = id.intValue(); String provider = physician.isNull() ? null : physician.textValue();
            if (command.selection() == CdsExportCommand.Selection.SELECTED_PATIENTS) {
                if (count >= command.patientNos().size() || command.patientNos().get((int) count) != patient) invalid();
            } else if (provider == null || !command.physicianNos().contains(provider)) invalid();
            count++; previous = patient; return new CdsExportSelectionRowDto(patient, provider);
        }
    }
    private static void requireBound(CdsExportLeaseDto lease) throws IOException {
        if (lease == null || lease.document().protocol() != 2 || lease.document().storage() == null) throw new IOException("CDS_EXPORT_STORAGE_BINDING_REQUIRED");
    }
    private static void outside() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_COHORT_INVENTORY_INVALID"); }
}
