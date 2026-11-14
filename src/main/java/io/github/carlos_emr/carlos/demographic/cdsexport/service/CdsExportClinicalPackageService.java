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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.commn.dto.CdsExportLocalSourceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportSupportingFilesValidator;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Cohort packaging orchestration over the shared patient pipeline. Selection EOF, same-capture
 * file binding, actual XML counts, supporting-file read-back, sorted membership and complete
 * ZIP validation must all succeed. This private byte/package capability does not qualify a
 * whole-chart source catalogue, prove clinical semantics, encrypt delivery or publish READY.
 */
@Service
public class CdsExportClinicalPackageService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The spooled chart records: the same mapper plus the naming record's date of birth. */
    private static final JsonMapper RECORDS =
            JsonMapper.builder()
                    .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .build();
    private static final String ACCESS_CHANGED = "CDS_EXPORT_ACCESS_CHANGED";
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(CdsExportClinicalPackageService.class);

    /**
     * What a re-authorization pass needs of one packaged chart: its patient and its exported
     * notes. The notes are read from the protected chart spool when the pass reaches the patient,
     * so a pass holds one chart's notes at a time. {@link #authorizeCohort} still indexes one of
     * these handles (two words) per packaged patient for the length of its own pass.
     */
    private static final class PatientFile {
        private final int patient;
        private final ChartFiles source;

        private PatientFile(int patient, ChartFiles source) {
            this.patient = patient;
            this.source = source;
        }

        PatientFile document() {
            return this;
        }

        int patient() {
            return patient;
        }

        List<CdsExportNoteAccessDto> notes() {
            return source.notes(patient);
        }
    }

    /**
     * Everything the later packaging steps need of one exported chart. It is appended to the
     * attempt's protected spool as soon as the chart is finished and never kept in memory for the
     * roster.
     */
    private record Chart(
            int patient,
            String sourceManifestSha256,
            String relative,
            CdsExportPatientFileDto naming,
            CdsExportStagedFileDto xml,
            List<CdsExportPackageMemberDto> attachments,
            CdsExportXmlValidationDto validation,
            Map<String, Long> dataSummaries,
            List<io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto> listed,
            Map<String, Long> notExported,
            List<CdsExportNoteAccessDto> notes) {}

    /** A spooled chart: its patient number, then the chart as JSON. */
    private static byte[] encode(Chart chart) throws IOException {
        byte[] json = RECORDS.writeValueAsBytes(chart);
        return java.nio.ByteBuffer.allocate(4 + json.length).putInt(chart.patient()).put(json).array();
    }

    private static int patientOf(CdsExportSpoolService.Entry entry) throws IOException {
        if (entry.value().length < 4) invalid();
        return java.nio.ByteBuffer.wrap(entry.value()).getInt();
    }

    private static Chart decode(CdsExportSpoolService.Entry entry) throws IOException {
        if (entry.value().length < 4) invalid();
        var chart = RECORDS.readValue(entry.value(), 4, entry.value().length - 4, Chart.class);
        if (chart == null
                || chart.patient() != patientOf(entry)
                || !entry.key().equals(chart.relative())) invalid();
        return chart;
    }

    /**
     * Ascending patient numbers in four bytes each: the charts this attempt has read, and the
     * patients it holds back. The cohort is sealed in ascending order, so membership is a binary
     * search and no boxed set of the roster is kept.
     */
    private static final class Patients extends java.util.AbstractSet<Integer> {
        private int[] values = new int[256];
        private int size;

        void append(int patient) throws IOException {
            if (patient < 1 || size > 0 && values[size - 1] >= patient) invalid();
            if (size == values.length)
                values = Arrays.copyOf(values, Math.toIntExact(Math.min(Integer.MAX_VALUE - 8L, 2L * size)));
            values[size++] = patient;
        }

        int at(int index) {
            return values[Objects.checkIndex(index, size)];
        }

        int indexOf(int patient) {
            return Arrays.binarySearch(values, 0, size, patient);
        }

        @Override
        public boolean contains(Object value) {
            return value instanceof Integer patient && indexOf(patient) >= 0;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                private int next;

                @Override
                public boolean hasNext() {
                    return next < size;
                }

                @Override
                public Integer next() {
                    if (next >= size) throw new NoSuchElementException();
                    return values[next++];
                }
            };
        }
    }

    /** The read charts already in the chart audit: one bit per chart this attempt has read. */
    private static final class Audited extends java.util.AbstractSet<Integer> {
        private final Patients read;
        private final java.util.BitSet marks = new java.util.BitSet();

        Audited(Patients read) {
            this.read = read;
        }

        @Override
        public boolean add(Integer patient) {
            int index = read.indexOf(patient);
            if (index < 0) throw new IllegalStateException("CDS_EXPORT_CLINICAL_PACKAGE_INVALID");
            boolean added = !marks.get(index);
            marks.set(index);
            return added;
        }

        @Override
        public boolean contains(Object value) {
            if (!(value instanceof Integer patient)) return false;
            int index = read.indexOf(patient);
            return index >= 0 && marks.get(index);
        }

        @Override
        public int size() {
            return marks.cardinality();
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                private int next = marks.nextSetBit(0);

                @Override
                public boolean hasNext() {
                    return next >= 0;
                }

                @Override
                public Integer next() {
                    if (next < 0) throw new NoSuchElementException();
                    int patient = read.at(next);
                    next = marks.nextSetBit(next + 1);
                    return patient;
                }
            };
        }

        /** The charts read and not yet audited, in cohort order. */
        Iterable<Integer> missing() {
            return () ->
                    new Iterator<>() {
                        private int next = marks.nextClearBit(0);

                        @Override
                        public boolean hasNext() {
                            return next < read.size();
                        }

                        @Override
                        public Integer next() {
                            if (next >= read.size()) throw new NoSuchElementException();
                            int patient = read.at(next);
                            next = marks.nextClearBit(next + 1);
                            return patient;
                        }
                    };
        }
    }

    /** Held patients in cohort order: four bytes and one shared code reference each. */
    private static final class Held {
        private final Patients patients = new Patients();
        private final ArrayList<String> codes = new ArrayList<>();
        private final Map<String, String> shared = new HashMap<>();

        void add(int patient, String code) throws IOException {
            patients.append(patient);
            // A few distinct codes serve every held patient.
            codes.add(shared.computeIfAbsent(new CdsExportHeldPatientDto(patient, code).code(), key -> key));
        }

        int size() {
            return patients.size();
        }

        CdsExportHeldPatientDto at(int index) {
            return new CdsExportHeldPatientDto(patients.at(index), codes.get(index));
        }

        List<CdsExportHeldPatientDto> list() {
            var result = new ArrayList<CdsExportHeldPatientDto>(size());
            for (int index = 0; index < size(); index++) result.add(at(index));
            return result;
        }
    }

    /**
     * The packaged charts as {@link #authorizeCohort} takes them: a collection read from the
     * protected spool, whose members are two-word handles. A pass asks for each patient's notes
     * once, in cohort order, which is the spool's own order.
     */
    private static final class ChartFiles extends java.util.AbstractCollection<PatientFile>
            implements AutoCloseable {
        private final CdsExportSpoolService charts;
        private final List<CdsExportSpoolService.Cursor> open = new ArrayList<>();
        private CdsExportSpoolService.Cursor notes;
        private Chart current;

        ChartFiles(CdsExportSpoolService charts) {
            this.charts = charts;
        }

        @Override
        public int size() {
            return Math.toIntExact(Math.min(Integer.MAX_VALUE, charts.records()));
        }

        @Override
        public Iterator<PatientFile> iterator() {
            try {
                var cursor = charts.open();
                open.add(cursor);
                return new Iterator<>() {
                    private CdsExportSpoolService.Entry next = cursor.next();

                    @Override
                    public boolean hasNext() {
                        return next != null;
                    }

                    @Override
                    public PatientFile next() {
                        if (next == null) throw new NoSuchElementException();
                        try {
                            var file = new PatientFile(patientOf(next), ChartFiles.this);
                            next = cursor.next();
                            return file;
                        } catch (IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    }
                };
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }

        List<CdsExportNoteAccessDto> notes(int patient) {
            try {
                if (current != null && current.patient() == patient) return current.notes();
                if (current != null && current.patient() > patient) invalid();
                if (notes == null) {
                    notes = charts.open();
                    open.add(notes);
                }
                CdsExportSpoolService.Entry entry;
                while ((entry = notes.next()) != null) {
                    int found = patientOf(entry);
                    if (found < patient) continue;
                    if (found > patient) invalid();
                    current = decode(entry);
                    return current.notes();
                }
                invalid();
                return List.of();
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }

        // Cleanup only throws without a primary failure; every cursor is still closed.
        @SuppressWarnings("java:S1143")
        @Override
        public void close() throws IOException {
            IOException primary = null;
            for (var cursor : open.reversed())
                try {
                    cursor.close();
                } catch (IOException failure) {
                    if (primary == null) primary = failure;
                    else primary.addSuppressed(failure);
                }
            open.clear();
            if (primary != null) throw primary;
        }
    }

    /** One re-authorization of the whole cohort, reading the packaged charts from the spool. */
    private void reauthorize(
            CdsExportLeaseDto lease,
            Set<String> tables,
            CdsExportSpoolService charts,
            Held held,
            CdsExportRunControl control,
            long expected,
            boolean auditPrepared,
            Set<Integer> audited)
            throws IOException {
        try (var files = new ChartFiles(charts)) {
            authorizeCohort(
                    lease, tables, files, held.patients, control, expected, auditPrepared, audited);
        } catch (java.io.UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    /**
     * The cohort's disclosure lines in cohort order: the packaged charts from the spool merged
     * with the held patients, both ascending by patient number. One patient's notes are split
     * into lines of at most {@link #NOTE_BATCH}, so no chart is refused for how many it has.
     */
    private static final class Disclosed implements AutoCloseable {
        private final CdsExportSpoolService.Cursor cursor;
        private final Held held;
        private final Runnable checkpoint;
        private final java.util.ArrayDeque<Object> pending = new java.util.ArrayDeque<>();
        private CdsExportSpoolService.Entry chart;
        private int heldIndex;

        Disclosed(CdsExportSpoolService charts, Held held, Runnable checkpoint) throws IOException {
            this.held = held;
            this.checkpoint = checkpoint;
            cursor = charts.open();
            try {
                chart = cursor.next();
            } catch (IOException | RuntimeException | Error failure) {
                try {
                    cursor.close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        /** The next line, or null after the last patient. */
        Object next() throws IOException {
            checkpoint.run();
            if (!pending.isEmpty()) return pending.remove();
            boolean heldNext =
                    heldIndex < held.size()
                            && (chart == null || held.patients.at(heldIndex) < patientOf(chart));
            if (heldNext) {
                var value = held.at(heldIndex++);
                return new ScopeHeld("HELD", value.patient(), value.code());
            }
            if (chart == null) return null;
            var file = decode(chart);
            chart = cursor.next();
            var notes = file.notes();
            int first = Math.min(notes.size(), NOTE_BATCH);
            for (int start = first; start < notes.size(); start += NOTE_BATCH)
                pending.add(
                        new ScopeNotes(
                                "NOTES",
                                file.patient(),
                                List.copyOf(
                                        notes.subList(
                                                start, Math.min(notes.size(), start + NOTE_BATCH)))));
            return new ScopePatient(
                    "PATIENT",
                    file.patient(),
                    file.sourceManifestSha256(),
                    List.copyOf(notes.subList(0, first)));
        }

        @Override
        public void close() throws IOException {
            cursor.close();
        }
    }

    /** Charts in archive path order, read from the sorted spool; closing ends the read. */
    private static final class SortedCharts implements Iterator<Chart>, AutoCloseable {
        private final CdsExportSpoolService.Cursor cursor;
        private final Runnable checkpoint;
        private Chart next;
        private String previous;

        SortedCharts(CdsExportSpoolService sorted, Runnable checkpoint) throws IOException {
            this.checkpoint = checkpoint;
            cursor = sorted.open();
            try {
                advance();
            } catch (IOException | RuntimeException | Error failure) {
                try {
                    cursor.close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        private void advance() throws IOException {
            checkpoint.run();
            var entry = cursor.next();
            if (entry == null) {
                next = null;
                return;
            }
            // Two charts under one archive path cannot both be packaged.
            if (entry.key().equals(previous)) ambiguous();
            if (previous != null && previous.compareTo(entry.key()) > 0) invalid();
            previous = entry.key();
            next = decode(entry);
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Chart next() {
            if (next == null) throw new NoSuchElementException();
            var result = next;
            try {
                advance();
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
            return result;
        }

        @Override
        public void close() throws IOException {
            cursor.close();
        }
    }

    /**
     * Proof that no two packaged charts share a sender identifier, kept on protected storage: the
     * identifiers are spooled as the supporting files are validated and sorted afterwards, and
     * two equal neighbours fail the package.
     */
    private static final class SpooledIdentities
            implements CdsExportSupportingFilesValidator.Identities, AutoCloseable {
        private final CdsExportSpoolService spool;
        private final Runnable checkpoint;
        private static final byte[] NONE = new byte[0];

        SpooledIdentities(CdsExportStagingService store, Runnable checkpoint, int batchBytes) {
            this.checkpoint = checkpoint;
            spool =
                    new CdsExportSpoolService(
                            store,
                            "PACKAGE_IDENTITY_SPOOL",
                            checkpoint,
                            batchBytes,
                            CdsExportSpoolService.FAN_IN);
        }

        @Override
        public void accept(String senderPatientId) throws IOException {
            spool.append(Objects.requireNonNull(senderPatientId), NONE);
        }

        @Override
        public void requireUnique() throws IOException {
            try (var sorted = spool.sorted("PACKAGE_IDENTITY_SORT");
                    var cursor = sorted.open()) {
                String previous = null;
                CdsExportSpoolService.Entry entry;
                while ((entry = cursor.next()) != null) {
                    checkpoint.run();
                    if (entry.key().equals(previous))
                        throw new IOException("CDS_EXPORT_SUPPORTING_FILES_INVALID");
                    previous = entry.key();
                }
            }
        }

        @Override
        public void close() throws IOException {
            spool.close();
        }
    }

    private final CdsExportStagingFactory stores;
    private final CdsExportCohortService cohorts;
    private final CdsExportLedgerService ledger;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportRetainedPayloadService payloads;
    private final CdsExportPatientDocumentService documents;
    private final CdsExportPatientFileBindingService naming;
    private final CdsExportPackageInventoryService inventories;
    private final CdsExportDisclosurePolicyService disclosure;
    private final CdsExportAuditPersister audit;
    private final CdsExportDataSummaryOccurrenceService dataSummaries;

    /** Without data summaries: a capture that holds data-summary tables is refused. */
    public CdsExportClinicalPackageService(
            CdsExportStagingFactory stores,
            CdsExportCohortService cohorts,
            CdsExportLedgerService ledger,
            CdsExportNativeCaptureService captures,
            CdsExportRetainedPayloadService payloads,
            CdsExportPatientDocumentService documents,
            CdsExportPatientFileBindingService naming,
            CdsExportPackageInventoryService inventories,
            CdsExportDisclosurePolicyService disclosure,
            CdsExportAuditPersister audit) {
        this(
                stores,
                cohorts,
                ledger,
                captures,
                payloads,
                documents,
                naming,
                inventories,
                disclosure,
                audit,
                null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportClinicalPackageService(
            CdsExportStagingFactory stores,
            CdsExportCohortService cohorts,
            CdsExportLedgerService ledger,
            CdsExportNativeCaptureService captures,
            CdsExportRetainedPayloadService payloads,
            CdsExportPatientDocumentService documents,
            CdsExportPatientFileBindingService naming,
            CdsExportPackageInventoryService inventories,
            CdsExportDisclosurePolicyService disclosure,
            CdsExportAuditPersister audit,
            CdsExportDataSummaryOccurrenceService dataSummaries) {
        this.dataSummaries = dataSummaries;
        this.stores = Objects.requireNonNull(stores);
        this.cohorts = Objects.requireNonNull(cohorts);
        this.ledger = Objects.requireNonNull(ledger);
        this.captures = Objects.requireNonNull(captures);
        this.payloads = Objects.requireNonNull(payloads);
        this.documents = Objects.requireNonNull(documents);
        this.naming = Objects.requireNonNull(naming);
        this.inventories = Objects.requireNonNull(inventories);
        this.disclosure = Objects.requireNonNull(disclosure);
        this.audit = Objects.requireNonNull(audit);
    }

    /**
     * Starts with a committed cohort in CAPTURING and returns only a private validated ZIP in
     * PACKAGING.
     *
     * <p>No count or size refuses a package. What this step knows about each chart goes to the
     * attempt's protected spool ({@link CdsExportSpoolService}) as the chart is finished, and the
     * later steps read it back in cohort order or through an external sort by archive path. What
     * stays in memory for the roster is: four bytes per chart read and one bit per chart audited
     * ({@link Patients}, {@link Audited}); four bytes and one reference per held patient ({@link
     * Held}), plus one {@link CdsExportHeldPatientDto} per held patient in the package metadata;
     * one entry per physician folder; and, only for the length of each re-authorization pass, the
     * two-word handle per packaged chart that {@link #authorizeCohort} indexes.
     */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public Prepared compose(
            CdsExportLeaseDto lease,
            String applicationVersion,
            ZoneId clinicalZone,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportXmlLimitsDto xmlLimits,
            CdsExportRunControl control)
            throws IOException {
        Objects.requireNonNull(control);
        Objects.requireNonNull(clinicalZone);
        var initial = ledger.status(lease.runId());
        if (initial.state() != CdsExportState.CAPTURING
                || initial.totalPatients() == null
                || initial.totalPatients() < 1) invalid();
        var cohort = ledger.cohort(lease);
        var policy = disclosure.resolveAccepted(lease.document(), Instant.now());
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        // Every finished chart, in cohort order, and every staged member this attempt owns.
        var charts = spool(store, "PACKAGE_CHART_SPOOL", control);
        var owned = spool(store, "PACKAGE_OWNED_SPOOL", control);
        // The staged members of the chart being captured; spooled once the chart is finished.
        var inFlight = new ArrayList<CdsExportStagedFileDto>();
        // Members staged after the owned spool was sealed into the ownership manifest.
        var tail = new ArrayList<CdsExportStagedFileDto>();
        var held = new Held();
        // Charts whose capture began (the export read them), and those already in the audit.
        var read = new Patients();
        var audited = new Audited(read);
        var physicians = new TreeMap<String, CdsExportPhysicianSummaryDto>();
        var billingNames = new HashMap<String, String>();
        var tables = captures.requiredTables(lease.document().command());
        // Inbox rows linked to no patient hold no chart and are never read. Count them once for
        // this generation, before any chart is captured, so their exclusion is recorded in the
        // run's event ledger, the diagnostic report and the ReadMe instead of being silent.
        var unlinked = captures.unlinkedSourceRows();
        ledger.recordUnlinkedSources(lease, unlinked);
        if (!unlinked.isEmpty())
            // Table names and counts only; no row, identifier or value.
            LOG.info(
                    "CDS_EXPORT_UNLINKED_SOURCE_ROWS run={} counts={}",
                    lease.runId(),
                    unlinked.stream()
                            .map(value -> value.table() + "=" + value.rows())
                            .toList());
        long patients = 0, expectedMembers = 2;
        boolean complete = false;
        Throwable primary = null;
        try {
            try (var cursor = cohorts.open(lease, control)) {
                CdsExportCohortService.Patient patient;
                while ((patient = cursor.next()) != null) {
                    control.checkpoint();
                    patients = Math.addExact(patients, 1);
                    if (!cohort.plaintextSha256().equals(patient.cohortSha256())) invalid();
                    // A patient whose chart this release cannot represent is held back and named,
                    // never silently omitted; infrastructure failures still stop the whole job.
                    Captured captured;
                    read.append(patient.patient());
                    try {
                        // One authorization session per patient: every step's check is answered
                        // once, and endPatient re-runs every grant fresh after the last read and
                        // before this patient's output is accepted (CdsExportPatientAuthorization).
                        control.beginPatient(patient.patient());
                        captured =
                                capturePatient(
                                        lease,
                                        patient,
                                        tables,
                                        bounds,
                                        applicationVersion,
                                        xmlLimits,
                                        control,
                                        inFlight);
                        control.endPatient(patient.patient());
                    } catch (CdsExportReviewException
                            | CdsExportMappingReviewException
                            | SecurityException refused) {
                        String code = heldCode(refused);
                        if (code == null) throw refused;
                        for (int i = inFlight.size() - 1; i >= 0; i--)
                            store.discardUnpublished(inFlight.remove(i));
                        held.add(patient.patient(), code);
                        // Safe code only; the patient number is recorded in the protected
                        // disclosure manifest and the encrypted ReadMe, not in the log.
                        LOG.warn("CDS_EXPORT_PATIENT_HELD run={} code={}", lease.runId(), code);
                        continue;
                    } finally {
                        // Never let a session outlive its patient, whatever ended it.
                        control.discardPatient();
                    }
                    var source = captured.source();
                    var document = captured.document();
                    var name = captured.name();
                    expectedMembers =
                            Math.addExact(expectedMembers, 1L + document.attachments().size());
                    var path = new CdsExportPathComposer().compose(name);
                    String relative = path.physicianFolder() + "/" + path.patientXmlFile();
                    var noteScope = source.capture().tables().get("casemgmt_note");
                    var notes =
                            noteScope == null
                                    ? List.<CdsExportNoteAccessDto>of()
                                    : noteScope.noteAccess();
                    if (document.patient() != patient.patient()) invalid();
                    // The chart leaves memory here: later steps read it from the spool. Two
                    // charts under one archive path are found when the spool is sorted by path.
                    charts.append(
                            relative,
                            encode(
                                    new Chart(
                                            document.patient(),
                                            document.sourceManifestSha256(),
                                            relative,
                                            name,
                                            document.xml(),
                                            document.attachments(),
                                            document.validation(),
                                            captured.dataSummaries(),
                                            captured.listed(),
                                            captured.notExported(),
                                            notes)));
                    for (var member : inFlight) own(owned, member);
                    inFlight.clear();
                    // Two names for one billing number are ambiguous; physicians without a
                    // number (and the Unassigned folder) are told apart by name only.
                    if (name.physicianBillingNumber() != null
                            && !name.physicianBillingNumber().isEmpty()) {
                        String priorName =
                                billingNames.putIfAbsent(
                                        name.physicianBillingNumber(), path.physicianFolder());
                        if (priorName != null && !priorName.equals(path.physicianFolder()))
                            ambiguous();
                    }
                    var prior = physicians.get(path.physicianFolder());
                    physicians.put(
                            path.physicianFolder(),
                            new CdsExportPhysicianSummaryDto(
                                    name.physicianFirstName(),
                                    name.physicianLastName(),
                                    name.physicianBillingNumber(),
                                    prior == null ? 1 : Math.addExact(prior.patientFiles(), 1)));
                }
                if (!cursor.complete()
                        || patients != initial.totalPatients()
                        || patients != charts.records() + held.size()) invalid();
            }
            long packaged = charts.records();
            auditReads(lease, held.patients, CdsExportAuditPersister.ReadOutcome.HELD, audited);
            // Every selected patient held back: there is no package to publish.
            if (packaged == 0) {
                var refusal = allHeld(held.list());
                if (refusal instanceof CdsExportMappingReviewException mapping) throw mapping;
                throw (RuntimeException) refusal;
            }
            var localSources = captures.unreviewedLocalSources();
            if (!localSources.isEmpty())
                // Table names from information_schema only; no values or patient identifiers.
                LOG.warn(
                        "CDS_EXPORT_UNREVIEWED_LOCAL_TABLES run={} tables={}",
                        lease.runId(),
                        localSources.stream().map(CdsExportLocalSourceDto::table).toList());
            ledger.phase(lease, CdsExportState.RECONCILING);
            reauthorize(lease, tables, charts, held, control, patients, false, audited);
            policy = disclosure.resolveAccepted(lease.document(), Instant.now());
            var metadata =
                    new CdsExportPackageMetadataDto(
                            "CDSExport-" + lease.runId(),
                            policy.physicianGroup(),
                            policy.groupContactInformation(),
                            applicationVersion,
                            Instant.now(),
                            clinicalZone,
                            lease.document().command().profile(),
                            lease.document().command().categories(),
                            policy.auditAccessDescription(),
                            held.list(),
                            localSources,
                            captures.listedClinicSources(),
                            unlinked);
            long totalPatients = patients;
            var header =
                    new ScopeHeader(
                            "HEADER",
                            1,
                            lease.runId(),
                            lease.epoch(),
                            cohort.plaintextSha256(),
                            lease.document().compatibilityDigest(),
                            patients,
                            new TreeSet<>(tables));
            var authorization =
                    store.stage(
                            "DISCLOSURE_MANIFEST",
                            output -> {
                                line(output, header);
                                long listed = 0;
                                try (var disclosed =
                                        new Disclosed(charts, held, control::checkpoint)) {
                                    Object entry;
                                    while ((entry = disclosed.next()) != null) {
                                        line(output, entry);
                                        if (!(entry instanceof ScopeNotes)) listed++;
                                    }
                                }
                                if (listed != totalPatients) invalid();
                                line(output, new ScopeEnd("END", totalPatients));
                            });
            own(owned, authorization);
            try (var input = store.open(authorization);
                    var disclosed = new Disclosed(charts, held, control::checkpoint)) {
                var lines = new CdsExportJsonLines(input, 1 << 20, control::checkpoint);
                if (!same(header, lines.next())) invalid();
                Object entry;
                while ((entry = disclosed.next()) != null)
                    if (!same(entry, lines.next())) invalid();
                if (!same(new ScopeEnd("END", patients), lines.next()) || lines.next() != null)
                    invalid();
            }
            ledger.phase(lease, CdsExportState.SERIALIZING);
            var eventLog = new CdsExportStagedFileDto[1];
            var totals = new CdsExportSupportingFilesComposer.Totals[1];
            CdsExportStagedFileDto readMe;
            CdsExportSupportingFilesValidator.Result supporting;
            // The charts once more, in archive path order, for the supporting files.
            try (var sorted = charts.sorted("PACKAGE_CHART_SORT")) {
                try (var ordered = new SortedCharts(sorted, control::checkpoint)) {
                    readMe =
                            store.stage(
                                    "SUPPORTING_README",
                                    readme -> {
                                        eventLog[0] =
                                                store.stage(
                                                        "SUPPORTING_EVENT_LOG",
                                                        log ->
                                                                totals[0] =
                                                                        new CdsExportSupportingFilesComposer()
                                                                                .compose(
                                                                                        readme,
                                                                                        log,
                                                                                        metadata,
                                                                                        physicians
                                                                                                .values()
                                                                                                .iterator(),
                                                                                        summaries(
                                                                                                ordered),
                                                                                        control
                                                                                                ::checkpoint));
                                        own(owned, eventLog[0]);
                                    });
                } catch (java.io.UncheckedIOException failure) {
                    throw failure.getCause();
                }
                own(owned, readMe);
                if (totals[0].patientFiles() != packaged
                        || totals[0].physicians() != physicians.size()) invalid();
                ledger.phase(lease, CdsExportState.VALIDATING);
                try (var readme = store.open(readMe);
                        var log = store.open(eventLog[0]);
                        var files = new SortedCharts(sorted, control::checkpoint);
                        var identities =
                                new SpooledIdentities(
                                        store, control::checkpoint, spoolBatchBytes)) {
                    supporting =
                            new CdsExportSupportingFilesValidator()
                                    .validate(
                                            readme,
                                            log,
                                            metadata,
                                            physicians.values().iterator(),
                                            supportingPatients(files, metadata.exportRoot()),
                                            identities,
                                            control::checkpoint);
                } catch (java.io.UncheckedIOException failure) {
                    throw failure.getCause();
                }
            }
            if (!supporting.readMeSha256().equals(readMe.plaintextSha256())
                    || supporting.readMeBytes() != readMe.plaintextBytes()
                    || !supporting.eventLogSha256().equals(eventLog[0].plaintextSha256())
                    || supporting.eventLogBytes() != eventLog[0].plaintextBytes()
                    || supporting.patientFiles() != packaged
                    || supporting.physicians() != physicians.size()) invalid();
            ledger.phase(lease, CdsExportState.PACKAGING);
            var supportingReadMe = readMe;
            var members =
                    inventories.seal(
                            store,
                            control::checkpoint,
                            output -> {
                                output.accept(
                                        new CdsExportPackageMemberDto("ReadMe.txt", supportingReadMe));
                                output.accept(
                                        new CdsExportPackageMemberDto(
                                                "ExportEventLog.csv", eventLog[0]));
                                try (var cursor = charts.open()) {
                                    CdsExportSpoolService.Entry entry;
                                    while ((entry = cursor.next()) != null) {
                                        control.checkpoint();
                                        var file = decode(entry);
                                        output.accept(
                                                new CdsExportPackageMemberDto(
                                                        file.relative(), file.xml()));
                                        String folder =
                                                file.relative()
                                                        .substring(
                                                                0,
                                                                file.relative().lastIndexOf('/')
                                                                        + 1);
                                        for (var attachment : file.attachments())
                                            output.accept(
                                                    new CdsExportPackageMemberDto(
                                                            folder + attachment.relativePath(),
                                                            attachment.file()));
                                    }
                                }
                            });
            own(owned, members.member());
            var archive =
                    new CdsExportPackageComposer().compose(store, members, control::checkpoint);
            own(owned, archive.archive());
            if (archive.entries() != expectedMembers) invalid();
            reauthorize(lease, tables, charts, held, control, patients, false, audited);
            disclosure.resolveAccepted(lease.document(), Instant.now());
            control.checkpoint();
            // Every member this attempt owns, in member-identifier order, from protected storage.
            long ownedFiles = owned.records();
            var ownershipHeader =
                    new OwnershipHeader(
                            "OWNERSHIP",
                            1,
                            lease.runId(),
                            lease.epoch(),
                            cohort.plaintextSha256(),
                            ownedFiles);
            CdsExportStagedFileDto ownership;
            try (var descriptors = owned.sorted("PACKAGE_OWNED_SORT")) {
                ownership =
                        store.stage(
                                "ATTEMPT_MEMBERS",
                                output -> {
                                    line(output, ownershipHeader);
                                    String previous = null;
                                    try (var cursor = descriptors.open()) {
                                        CdsExportSpoolService.Entry entry;
                                        while ((entry = cursor.next()) != null) {
                                            control.checkpoint();
                                            var member = member(entry);
                                            if (member.memberId().equals(previous)) invalid();
                                            line(output, member);
                                            previous = member.memberId();
                                        }
                                    }
                                    line(output, new OwnershipEnd("END", ownedFiles));
                                });
                tail.add(ownership);
                try (var input = store.open(ownership);
                        var cursor = descriptors.open()) {
                    var lines = new CdsExportJsonLines(input, 1 << 20, control::checkpoint);
                    if (!same(ownershipHeader, lines.next())) invalid();
                    CdsExportSpoolService.Entry entry;
                    while ((entry = cursor.next()) != null)
                        if (!same(member(entry), lines.next())) invalid();
                    if (!same(new OwnershipEnd("END", ownedFiles), lines.next())
                            || lines.next() != null) invalid();
                }
            }
            reauthorize(lease, tables, charts, held, control, patients, true, audited);
            disclosure.resolveAccepted(lease.document(), Instant.now());
            control.checkpoint();
            // Counts only: how much of the roster's packaging state went to protected storage.
            LOG.info(
                    "CDS_EXPORT_PACKAGE_SPOOLED run={} charts={} members={} segments={}",
                    lease.runId(),
                    packaged,
                    ownedFiles,
                    charts.segmentsWritten() + owned.segmentsWritten());
            var result =
                    new Prepared(
                            lease.runId(),
                            lease.epoch(),
                            cohort.plaintextSha256(),
                            patients,
                            metadata,
                            archive,
                            members.member(),
                            readMe,
                            eventLog[0],
                            authorization,
                            ownership);
            complete = true;
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!complete) {
                // Erase what this attempt staged. A job that stopped is released to the retention
                // sweep at once and that sweep removes the whole attempt directory, so a member
                // this cannot reach (the spool itself failed, or the lease is gone) is not left.
                for (var member : tail.reversed()) discard(store, member, primary);
                for (var member : inFlight.reversed()) discard(store, member, primary);
                try (var cursor = owned.open()) {
                    CdsExportSpoolService.Entry entry;
                    while ((entry = cursor.next()) != null) discard(store, member(entry), primary);
                } catch (IOException | RuntimeException cleanup) {
                    if (primary != null && cleanup != primary) primary.addSuppressed(cleanup);
                }
                // Charts read by a job that stopped are still chart reads (census D11).
                try {
                    auditReads(
                            lease,
                            audited.missing(),
                            CdsExportAuditPersister.ReadOutcome.STOPPED,
                            audited);
                } catch (RuntimeException cleanup) {
                    if (primary == null) throw cleanup;
                    // The audit store may fail again with the very same failure.
                    if (cleanup != primary) primary.addSuppressed(cleanup);
                }
            }
            // The spools are intermediates of this step, whether it finished or not.
            for (var spool : List.of(charts, owned))
                try {
                    spool.close();
                } catch (IOException | RuntimeException cleanup) {
                    // A finished package is not failed for an intermediate that could not be
                    // erased: the attempt's own cleanup removes it with everything else.
                    if (primary != null) primary.addSuppressed(cleanup);
                    else LOG.warn("CDS_EXPORT_SPOOL_CLEANUP_INCOMPLETE run={}", lease.runId());
                }
        }
    }

    /**
     * The records one spool keeps in memory before writing them to protected storage. A test
     * lowers it to make a small roster spill; no export changes it.
     */
    int spoolBatchBytes = CdsExportSpoolService.BATCH_BYTES;

    private CdsExportSpoolService spool(
            CdsExportStagingService store, String purpose, CdsExportRunControl control) {
        return new CdsExportSpoolService(
                store, purpose, control::checkpoint, spoolBatchBytes, CdsExportSpoolService.FAN_IN);
    }

    private static void discard(
            CdsExportStagingService store, CdsExportStagedFileDto member, Throwable primary) {
        try {
            store.discardUnpublished(member);
        } catch (IOException | RuntimeException cleanup) {
            if (primary != null && cleanup != primary) primary.addSuppressed(cleanup);
        }
    }

    private static void own(CdsExportSpoolService owned, CdsExportStagedFileDto member)
            throws IOException {
        owned.append(member.memberId(), JSON.writeValueAsBytes(member));
    }

    private static CdsExportStagedFileDto member(CdsExportSpoolService.Entry entry)
            throws IOException {
        var member = JSON.readValue(entry.value(), CdsExportStagedFileDto.class);
        if (member == null || !member.memberId().equals(entry.key())) invalid();
        return member;
    }

    private void auditReads(
            CdsExportLeaseDto lease,
            Iterable<Integer> patients,
            CdsExportAuditPersister.ReadOutcome outcome,
            Set<Integer> audited) {
        var batch = new ArrayList<Integer>(100);
        for (int patient : patients) {
            if (audited.contains(patient)) continue;
            batch.add(patient);
            if (batch.size() == 100) {
                audit.read(lease, batch, outcome);
                audited.addAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            audit.read(lease, batch, outcome);
            audited.addAll(batch);
        }
    }

    private record Captured(
            CdsExportPatientCaptureDto source,
            CdsExportPatientDocumentService.Document document,
            CdsExportPatientFileDto name,
            Map<String, Long> dataSummaries,
            List<io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto> listed,
            Map<String, Long> notExported) {}

    /** One selected patient's capture, retained payload, finished document and file binding. */
    private Captured capturePatient(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            String applicationVersion,
            CdsExportXmlLimitsDto xmlLimits,
            CdsExportRunControl control,
            List<CdsExportStagedFileDto> owned)
            throws IOException {
        var source =
                captures.capture(lease, patient, tables, bounds.fields(), bounds.bytes(), control);
        owned.addAll(
                List.of(source.manifest(), source.capture().values(), source.capture().index()));
        var retained =
                payloads.freeze(
                        lease,
                        patient,
                        tables,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        control);
        owned.addAll(
                List.of(
                        retained.manifest(),
                        retained.capture().values(),
                        retained.capture().index()));
        var document =
                documents.compose(
                        lease,
                        patient,
                        tables,
                        source,
                        retained.manifest(),
                        bounds,
                        applicationVersion,
                        xmlLimits,
                        control);
        var outputs = new ArrayList<CdsExportStagedFileDto>();
        outputs.add(document.xml());
        outputs.add(document.reconciliation());
        for (var attachment : document.attachments()) outputs.add(attachment.file());
        owned.addAll(outputs);
        var name = naming.bind(lease, patient, tables, source, document, bounds, control);
        // Per-patient ReadMe facts from the same sealed capture: the data-summary records per kind
        // (the same plan the Reports emission used) and the listed removed-feature tables.
        if (CdsExportDataSummaryOccurrenceService.captured(tables) && dataSummaries == null)
            invalid();
        var summaries =
                dataSummaries == null
                        ? Map.<String, Long>of()
                        : dataSummaries.kinds(lease, patient, tables, source, bounds, control);
        var withheld =
                dataSummaries == null
                        ? Map.<String, Long>of()
                        : dataSummaries.notExported(lease, patient, tables, source, bounds, control);
        return new Captured(
                source,
                document,
                name,
                summaries,
                listed(lease, patient, tables, source, bounds, control),
                withheld);
    }

    /** The captured listed-table counts of one patient, read back from the sealed capture. */
    private List<io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto> listed(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!tables.contains(CdsExportListedSourceReader.TABLE)) return List.of();
        var result = new ArrayList<io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto>();
        new CdsExportCapturedTextRowsReader(captures)
                .forEach(
                        lease,
                        patient,
                        tables,
                        CdsExportListedSourceReader.TABLE,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        4096,
                        control,
                        row -> {
                            String table = row.fields().get("table_name"),
                                    rows = row.fields().get("row_count");
                            if (table == null
                                    || !table.equals(row.recordKey())
                                    || rows == null
                                    || !rows.matches("[1-9][0-9]{0,18}")
                                    || result.size() >= 1024) invalid();
                            result.add(
                                    new io.github.carlos_emr.carlos.commn.dto
                                            .CdsExportListedSourceDto(table, Long.parseLong(rows)));
                        });
        return List.copyOf(result);
    }

    /**
     * The safe code for a refusal that concerns only this patient's chart or this patient's
     * access, or null when the refusal concerns the whole job (limits, recipient, audit policy,
     * runtime change), which must still stop it.
     */
    static String heldCode(Exception refused) {
        if (refused instanceof CdsExportMappingReviewException mapping) return mapping.getMessage();
        if (refused instanceof SecurityException) return ACCESS_CHANGED;
        if (refused instanceof CdsExportReviewException review)
            return switch (review.reason()) {
                case UNMAPPED_DATA,
                        PREVENTION_PROGRAM_SCOPE,
                        DOCUMENT_PROGRAM_SCOPE,
                        MERGED_PATIENT_SCOPE,
                        SOURCE_CHANGED,
                        AMBIGUOUS_SOURCE,
                        INCOMPLETE_RENDER,
                        MISSING_ATTACHMENT,
                        CLINICAL_VALIDATION_FAILED,
                        CLINICAL_SOURCE_UNREVIEWED -> review.reason().code();
                case AUDIT_ACCESS_UNAVAILABLE,
                        RECIPIENT_UNAVAILABLE,
                        RESOURCE_LIMIT,
                        RUNTIME_CHANGED,
                        NO_PATIENTS_SELECTED,
                        PATIENTS_HELD,
                        CLINIC_SOURCE_UNREVIEWED,
                        DOCUMENT_STORAGE_UNTRUSTED -> null;
            };
        return null;
    }

    /**
     * The refusal for a job whose every selected patient was held, so there is no package. When
     * every patient was held for the same cause the job ends exactly as a one-patient job did
     * before patients could be held: the same review reason, the mapping refusal (reported as
     * unmapped data) or, when access was refused for every patient, the access refusal (decision:
     * a right missing for the whole selection refuses the job). Mixed causes report PATIENTS_HELD;
     * the per-patient reasons stay in the protected held-patient record.
     */
    static Exception allHeld(Collection<CdsExportHeldPatientDto> held) {
        var codes = new TreeSet<String>();
        for (var value : held) codes.add(value.code());
        if (codes.size() == 1) {
            String code = codes.first();
            if (ACCESS_CHANGED.equals(code)) return new SecurityException(ACCESS_CHANGED);
            for (var reason : CdsExportReviewException.Reason.values())
                if (reason.code().equals(code)) return new CdsExportReviewException(reason);
            return new CdsExportMappingReviewException(code);
        }
        return new CdsExportReviewException(CdsExportReviewException.Reason.PATIENTS_HELD);
    }

    private record ScopeHeld(String kind, int patient, String code) {}

    private void authorizeCohort(
            CdsExportLeaseDto lease,
            Set<String> tables,
            Collection<PatientFile> files,
            Set<Integer> held,
            CdsExportRunControl control,
            long expected,
            boolean auditPrepared,
            Set<Integer> audited)
            throws IOException {
        var byPatient = new HashMap<Integer, PatientFile>();
        for (var file : files)
            if (byPatient.putIfAbsent(file.document().patient(), file) != null) invalid();
        long count = 0;
        var auditBatch = new ArrayList<Integer>(100);
        try (var cursor = cohorts.open(lease, control)) {
            CdsExportCohortService.Patient patient;
            while ((patient = cursor.next()) != null) {
                control.checkpoint();
                var file = byPatient.get(patient.patient());
                if (file == null) {
                    // A held patient is not disclosed: no source authorization or chart audit.
                    if (!held.contains(patient.patient())) invalid();
                    count++;
                    continue;
                }
                // One re-authorization pass per patient: its gates and notes are checked from
                // the live database now, without one transaction per check.
                control.beginAuthorizationPass(patient.patient());
                try {
                    captures.authorizeSources(lease, patient, tables, control);
                    authorizeNotes(file.notes(), control);
                } finally {
                    control.endAuthorizationPass();
                }
                if (auditPrepared) {
                    auditBatch.add(patient.patient());
                    if (auditBatch.size() == 100) {
                        audit.prepared(lease, auditBatch);
                        audited.addAll(auditBatch);
                        auditBatch.clear();
                    }
                }
                count++;
            }
            if (!cursor.complete() || count != expected) invalid();
            if (!auditBatch.isEmpty()) {
                audit.prepared(lease, auditBatch);
                audited.addAll(auditBatch);
            }
        }
    }

    /**
     * The patient's exported notes, authorized in ascending note-id batches through one fresh
     * authorization transaction each (review EC-3): the same live program, reporter-role and
     * facility rules as one note at a time, without one database round trip per note.
     */
    static void authorizeNotes(List<CdsExportNoteAccessDto> notes, CdsExportRunControl control) {
        var sorted = new ArrayList<>(notes);
        sorted.sort(java.util.Comparator.comparingLong(CdsExportNoteAccessDto::noteId));
        for (int start = 0; start < sorted.size(); start += NOTE_BATCH) {
            control.checkpoint();
            control.authorizeNotes(
                    CdsCategory.CLINICAL_NOTES,
                    List.copyOf(sorted.subList(start, Math.min(sorted.size(), start + NOTE_BATCH))));
        }
    }

    /** The largest note batch one authorization accepts. */
    static final int NOTE_BATCH = 8192;

    private record ScopeHeader(
            String kind,
            int protocol,
            String runId,
            long epoch,
            String cohortSha256,
            String compatibilityDigest,
            long patients,
            Set<String> tables) {}

    private record ScopePatient(
            String kind,
            int patient,
            String sourceManifestSha256,
            List<CdsExportNoteAccessDto> notes) {}

    /** The rest of one patient's notes, after the first {@link #NOTE_BATCH} on its PATIENT line. */
    private record ScopeNotes(String kind, int patient, List<CdsExportNoteAccessDto> notes) {}

    private record ScopeEnd(String kind, long patients) {}

    private record OwnershipHeader(
            String kind, int protocol, String runId, long epoch, String cohortSha256, long files) {}

    private record OwnershipEnd(String kind, long files) {}

    private static boolean same(Object expected, com.fasterxml.jackson.databind.JsonNode actual)
            throws IOException {
        return actual != null && JSON.readTree(JSON.writeValueAsBytes(expected)).equals(actual);
    }

    private static void line(java.io.OutputStream output, Object value) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(value);
        // No line this step writes can be this long: notes are split into batches.
        if (bytes.length >= (1 << 20)) invalid();
        output.write(bytes);
        output.write('\n');
    }

    private static Iterator<CdsExportPatientSummaryDto> summaries(Iterator<Chart> charts) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return charts.hasNext();
            }

            @Override
            public CdsExportPatientSummaryDto next() {
                var file = charts.next();
                var xml = file.validation();
                boolean reports = xml.occurrences().containsKey(CdsCategory.REPORTS);
                return new CdsExportPatientSummaryDto(
                        file.naming(),
                        xml.occurrences(),
                        reports ? xml.textReports() : null,
                        reports ? xml.binaryReports() : null,
                        xml.xmlSha256(),
                        xml.xmlBytes(),
                        file.dataSummaries(),
                        file.listed(),
                        file.notExported());
            }
        };
    }

    private static Iterator<CdsExportSupportingFilesValidator.Patient> supportingPatients(
            Iterator<Chart> charts, String exportRoot) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return charts.hasNext();
            }

            @Override
            public CdsExportSupportingFilesValidator.Patient next() {
                var file = charts.next();
                return new CdsExportSupportingFilesValidator.Patient(
                        exportRoot + "/" + file.relative(),
                        file.naming(),
                        file.validation(),
                        file.dataSummaries(),
                        file.listed(),
                        file.notExported());
            }
        };
    }

    /** Same-cohort private package evidence. Delivery encryption, live authority and fenced publication remain mandatory. */
    public static final class Prepared {
        private final String runId, cohortSha256;
        private final long epoch, patients;
        private final CdsExportPackageMetadataDto metadata;
        private final CdsExportPackageDto archive;
        private final CdsExportStagedFileDto members, readMe, eventLog, authorization, ownership;

        private Prepared(
                String run,
                long epoch,
                String cohort,
                long patients,
                CdsExportPackageMetadataDto metadata,
                CdsExportPackageDto archive,
                CdsExportStagedFileDto members,
                CdsExportStagedFileDto readMe,
                CdsExportStagedFileDto eventLog,
                CdsExportStagedFileDto authorization,
                CdsExportStagedFileDto ownership) {
            runId = run;
            this.epoch = epoch;
            cohortSha256 = cohort;
            this.patients = patients;
            this.metadata = metadata;
            this.archive = archive;
            this.members = members;
            this.readMe = readMe;
            this.eventLog = eventLog;
            this.authorization = authorization;
            this.ownership = ownership;
        }

        public String runId() {
            return runId;
        }

        public long epoch() {
            return epoch;
        }

        public String cohortSha256() {
            return cohortSha256;
        }

        public long patients() {
            return patients;
        }

        public CdsExportPackageMetadataDto metadata() {
            return metadata;
        }

        public CdsExportPackageDto archive() {
            return archive;
        }

        public CdsExportStagedFileDto members() {
            return members;
        }

        public CdsExportStagedFileDto readMe() {
            return readMe;
        }

        public CdsExportStagedFileDto eventLog() {
            return eventLog;
        }

        public CdsExportStagedFileDto authorization() {
            return authorization;
        }

        public CdsExportStagedFileDto ownership() {
            return ownership;
        }

        @Override
        public String toString() {
            return "CdsExportPreparedClinicalPackage[protected]";
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CLINICAL_PACKAGE_INVALID");
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
