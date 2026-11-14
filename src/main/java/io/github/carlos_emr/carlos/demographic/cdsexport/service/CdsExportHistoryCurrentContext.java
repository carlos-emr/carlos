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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Closed complete patient case-management context. Only authenticated complete scans can create
 * this evidence.
 *
 * <p>A chart whose case-management tables fit one section window
 * ({@link CdsExportSectionWindowPlanner}) is held whole: every row with its witness, as it always
 * was. A larger chart is never refused. It is proved complete in two passes that keep 48 bytes
 * for a row (its key and one digest of its witness,
 * {@link CdsExportCaseManagementProjectionEvidence#reduce}) and, after them, each row's key (16
 * bytes) and what the section plans from: each note's scope, a bit for each note whose own
 * columns or attached records need the native carrier, each patient issue's catalogue entry as a
 * number and each catalogue entry's CPP tag as a byte. Its rows are then read again a
 * {@linkplain #window window} at a time, for the notes and issues the window's occurrences own,
 * each row checked against its whole witness before use.
 *
 * <p>Grouping that spans notes is decided from the chart-wide facts, never from a window: a note
 * chain's section comes from the links of its current revision, an issue's standalone ProblemList
 * entry from every revision that links it. A window holds whole owners only: every revision of a
 * chain with its extensions, links and attached records, and the patient issues and catalogue
 * entries those links name.
 */
public final class CdsExportHistoryCurrentContext {
    private static final Set<String> TABLES =
            Set.of(
                    "casemgmt_note",
                    "casemgmt_note_ext",
                    "casemgmt_issue_notes",
                    "casemgmt_issue",
                    "issue");

    /**
     * The records each note is attached to. The source catalogue requires it in every history
     * capture; a capture made without the catalogue (a fixture, or a reader set predating the
     * links) has none to read.
     */
    static final String NOTE_LINKS = "casemgmt_note_link";

    /** The tables whose rows an import unit's references name. */
    private static final Set<String> REFERENCED =
            Set.of("casemgmt_note", "casemgmt_note_ext", "casemgmt_issue", "casemgmt_issue_notes");

    /** Note columns whose value sends a single imported revision through the native carrier. */
    private static final List<String> NATIVE_CONTEXT_COLUMNS =
            List.of(
                    "archived",
                    "signed",
                    "signing_provider_no",
                    "history",
                    "encounter_type",
                    "billing_code",
                    "appointmentNo",
                    "hourOfEncounterTime",
                    "minuteOfEncounterTime",
                    "hourOfEncTransportationTime",
                    "minuteOfEncTransportationTime");

    private final String manifest;
    private final int patient;
    private final Map<Long, CdsExportNoteAccessDto> scopes;
    /** The rows held: the whole chart's, one window's, or null for a chart read in windows. */
    private final Map<String, Map<String, CdsExportHistoryFieldReconciler.Row>> records;
    /** Each held note's extensions, by record key, so the note's details never change order. */
    private final Map<String, List<CdsExportHistoryFieldReconciler.Row>> extensions;
    /** Each held note's issue links, in the order the capture holds them. */
    private final Map<String, List<CdsExportCapturedTextRowDto>> issueLinks;
    /** Each note's links, in link-row order, so an unchanged chart writes the same context. */
    private final Map<String, List<CdsExportHistoryFieldReconciler.Row>> noteLinks;
    /** The chart-wide facts of a chart read in windows; null for a chart held whole. */
    private final Chart chart;
    /** The notes whose rows and child rows a window holds; null when the whole chart is held. */
    private final Set<String> owned;
    /** Child rows of the owned notes this window took: extensions, issue links, note links. */
    private final long[] taken;

    private CdsExportHistoryCurrentContext(
            String manifest,
            int patient,
            Map<String, Map<String, CdsExportHistoryFieldReconciler.Row>> records,
            List<CdsExportCapturedTextRowDto> links,
            Map<Long, CdsExportNoteAccessDto> scopes,
            Chart chart,
            Set<String> owned) {
        this.manifest = manifest;
        this.patient = patient;
        this.scopes = scopes;
        this.chart = chart;
        this.owned = owned;
        this.taken = new long[3];
        if (records == null) {
            this.records = null;
            this.extensions = Map.of();
            this.issueLinks = Map.of();
            this.noteLinks = Map.of();
            return;
        }
        var copied = new HashMap<String, Map<String, CdsExportHistoryFieldReconciler.Row>>();
        records.forEach((table, rows) -> copied.put(table, Map.copyOf(rows)));
        this.records = Map.copyOf(copied);
        // Map.copyOf iterates in a per-JVM salted order; the extensions and the note links feed
        // the note's details, which must not change between runs.
        this.extensions = grouped(this.records.getOrDefault("casemgmt_note_ext", Map.of()));
        this.noteLinks = grouped(this.records.getOrDefault(NOTE_LINKS, Map.of()));
        var byNote = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        for (var link : links)
            byNote.computeIfAbsent(link.fields().get("note_id"), ignored -> new ArrayList<>())
                    .add(link);
        var frozen = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        byNote.forEach((note, rows) -> frozen.put(note, List.copyOf(rows)));
        this.issueLinks = Map.copyOf(frozen);
        if (owned != null) {
            for (var rows : this.extensions.entrySet())
                if (owned.contains(rows.getKey())) taken[0] += rows.getValue().size();
            for (var rows : this.issueLinks.entrySet())
                if (owned.contains(rows.getKey())) taken[1] += rows.getValue().size();
            for (var rows : this.noteLinks.entrySet())
                if (owned.contains(rows.getKey())) taken[2] += rows.getValue().size();
        }
    }

    /** The rows of one child table by the note they name, each note's in record-key order. */
    private static Map<String, List<CdsExportHistoryFieldReconciler.Row>> grouped(
            Map<String, CdsExportHistoryFieldReconciler.Row> rows) {
        var grouped = new HashMap<String, List<CdsExportHistoryFieldReconciler.Row>>();
        rows.values().stream()
                .sorted(Comparator.comparing(row -> row.value().recordKey()))
                .forEach(
                        row ->
                                grouped.computeIfAbsent(
                                                row.value().fields().get("note_id"),
                                                ignored -> new ArrayList<>())
                                        .add(row));
        var frozen = new HashMap<String, List<CdsExportHistoryFieldReconciler.Row>>();
        grouped.forEach((note, list) -> frozen.put(note, List.copyOf(list)));
        return Map.copyOf(frozen);
    }

    /**
     * Reads all five domains, and the note links when they were captured, with their independent
     * full-row witnesses under the same verified captured manifest: whole when they fit the
     * section window of {@code bounds}, otherwise as chart-wide facts whose rows are read by
     * {@link #window}. No size refuses a chart.
     */
    public static CdsExportHistoryCurrentContext read(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader receipts,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        Objects.requireNonNull(captures);
        Objects.requireNonNull(receipts);
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(control);
        patient.require(lease);
        if (source == null
                || !requiredTables.containsAll(TABLES)
                || !source.capture().tables().keySet().equals(requiredTables)) invalid();
        // The catalogue requires the note links with every history category; a catalogued
        // capture without them could export an attached note without naming what it is
        // attached to.
        if (requiredTables.contains("clinical_source_catalogue")
                && !requiredTables.contains(NOTE_LINKS)) invalid();
        var tables = new HashSet<>(TABLES);
        if (requiredTables.contains(NOTE_LINKS)) tables.add(NOTE_LINKS);
        var loader =
                new Loader(
                        captures,
                        receipts,
                        lease,
                        patient,
                        Set.copyOf(requiredTables),
                        Set.copyOf(tables),
                        source,
                        bounds,
                        control);
        // The first attempt is the whole chart, read exactly as it always was. A chart with more
        // rows than one window holds, or whose rows and witnesses pass the window's bytes, is
        // read in windows instead: the two sizes decide how, never whether.
        var limits = bounds.window();
        boolean fits = true;
        long total = 0;
        for (String table : tables) {
            long rows = source.capture().tables().get(table).rows();
            total = Math.addExact(total, rows);
            if (rows > limits.rows()) fits = false;
        }
        if (fits) {
            var whole = loader.whole(CdsExportSectionWindowPlanner.wholeBudget(total, limits));
            if (whole != null) return whole;
        }
        return loader.chart();
    }

    /** Everything one read of a chart's capture is bound to. */
    private record Loader(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader receipts,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control) {

        @Override
        public String toString() {
            return "CdsExportHistoryCurrentContext.Loader[protected]";
        }

        /** The whole chart with its witnesses, or null when it passed the window's bytes. */
        private CdsExportHistoryCurrentContext whole(CdsExportSectionWindowPlanner.Budget budget)
                throws IOException {
            var states = new HashMap<String, CdsExportNativeRowReceiptDto>();
            var records = new HashMap<String, Map<String, CdsExportHistoryFieldReconciler.Row>>();
            var links = new ArrayList<CdsExportCapturedTextRowDto>();
            // The rows are held with their witnesses while the section reconciles them. Nothing
            // refuses them for their size: a chart too large to hold is read in windows.
            for (String table : tables) records.put(table, new HashMap<>());
            var closed =
                    receipts.scan(
                            lease,
                            patient,
                            requiredTables,
                            source,
                            bounds.fields(),
                            bounds.bytes(),
                            control,
                            row -> {
                                if (!tables.contains(row.table())) return;
                                if (row.state() == null
                                        || row.partial()
                                        || row.digest() == null) invalid();
                                if (!budget.add(2, size(row))) return;
                                if (states.putIfAbsent(key(row.table(), row.id()), row) != null)
                                    invalid();
                            });
            if (budget.exceeded()) return null;
            if (!closed.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
                invalid();
            for (String table : tables)
                if ((!Objects.equals(
                                closed.rows().get(table),
                                source.capture().tables().get(table).rows())
                        || closed.unavailableTables().contains(table))) invalid();
            var count = new HashMap<String, Long>();
            var actual =
                    new CdsExportCapturedTextRowsReader(captures)
                            .forEachTable(
                                    lease,
                                    patient,
                                    requiredTables,
                                    tables,
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    // One row's text, not the chart's.
                                    67_108_864,
                                    control,
                                    row -> {
                                        control.checkpoint();
                                        if (budget.exceeded()) return;
                                        if (row.table().equals("casemgmt_note")
                                                ? !budget.owner(size(row))
                                                : !budget.add(1, size(row))) return;
                                        count.merge(row.table(), 1L, Math::addExact);
                                        if (row.table().equals("casemgmt_issue_notes"))
                                            links.add(row);
                                        String id = id(row);
                                        var witness = states.remove(key(row.table(), id));
                                        if (witness == null) invalid();
                                        CdsExportCaseManagementProjectionEvidence.verify(
                                                row, witness);
                                        if (records.get(row.table())
                                                        .putIfAbsent(
                                                                id,
                                                                new CdsExportHistoryFieldReconciler
                                                                        .Row(row, witness))
                                                != null) invalid();
                                    });
            if (budget.exceeded()) return null;
            if (!actual.equals(source.capture()) || !states.isEmpty()) invalid();
            for (String table : tables)
                if (count.getOrDefault(table, 0L) != actual.tables().get(table).rows()) invalid();
            var scopes = scopes(actual);
            if (scopes.size() != records.get("casemgmt_note").size()) invalid();
            for (var note : records.get("casemgmt_note").values()) requireScope(note.value(), scopes);
            for (var extension : records.get("casemgmt_note_ext").values())
                if (!records.get("casemgmt_note")
                        .containsKey(extension.value().fields().get("note_id"))) invalid();
            // A link travels with the note it attaches; capture reads exactly the links of the
            // patient's own notes, so a link naming no captured note is not this chart's.
            for (var link : records.getOrDefault(NOTE_LINKS, Map.of()).values())
                if (!records.get("casemgmt_note").containsKey(link.value().fields().get("note_id")))
                    invalid();

            var pairs = new HashSet<String>();
            for (var link : links) {
                String note = link.fields().get("note_id"), issue = link.fields().get("id");
                requireLink(link);
                if (!records.get("casemgmt_note").containsKey(note)
                        || !records.get("casemgmt_issue").containsKey(issue)
                        || !pairs.add(note + "/" + issue)) invalid();
            }
            for (var issue : records.get("casemgmt_issue").values())
                if (!Integer.toString(patient.patient())
                                .equals(issue.value().fields().get("demographic_no"))
                        || !records.get("issue")
                                .containsKey(issue.value().fields().get("issue_id"))) invalid();
            captures.authorizeCapturedSources(lease, patient, requiredTables, actual, control);
            return new CdsExportHistoryCurrentContext(
                    source.manifest().plaintextSha256(),
                    patient.patient(),
                    records,
                    links,
                    scopes,
                    null,
                    null);
        }

        /** The note scopes the capture authorized, one for each note of this patient. */
        private Map<Long, CdsExportNoteAccessDto> scopes(CdsExportNativeCaptureDto actual)
                throws IOException {
            var scopes = new HashMap<Long, CdsExportNoteAccessDto>();
            for (var scope : actual.tables().get("casemgmt_note").noteAccess())
                if (scope.patient() != patient.patient()
                        || scopes.putIfAbsent(scope.noteId(), scope) != null) invalid();
            return Map.copyOf(scopes);
        }

        /** A note row is this patient's and carries the program and role of its scope. */
        private void requireScope(
                CdsExportCapturedTextRowDto note, Map<Long, CdsExportNoteAccessDto> scopes)
                throws IOException {
            CdsExportNoteAccessDto scope;
            try {
                scope = scopes.get(Long.valueOf(note.fields().get("note_id")));
            } catch (NumberFormatException malformed) {
                throw new IOException("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
            }
            if (!Integer.toString(patient.patient()).equals(note.fields().get("demographic_no"))
                    || scope == null
                    || !Objects.equals(scope.program(), note.fields().get("program_no"))
                    || !Objects.equals(
                            scope.reporterRole(), note.fields().get("reporter_caisi_role")))
                invalid();
        }

        /**
         * The chart-wide facts of a chart read in windows. One witness pass reduces every
         * witness to its key and a digest; one row pass proves every row is the row its witness
         * froze, that every witness has its row, and every rule the whole read applies to a
         * row, keeping of each row only what the section plans from.
         */
        private CdsExportHistoryCurrentContext chart() throws IOException {
            var stores = new HashMap<String, Store>();
            for (String table : tables) stores.put(table, new Store());
            var closed =
                    receipts.scan(
                            lease,
                            patient,
                            requiredTables,
                            source,
                            bounds.fields(),
                            bounds.bytes(),
                            control,
                            row -> {
                                if (!tables.contains(row.table())) return;
                                if (row.state() == null
                                        || row.partial()
                                        || row.digest() == null) invalid();
                                control.checkpoint();
                                stores.get(row.table())
                                        .add(CdsExportCaseManagementProjectionEvidence.reduce(row));
                            });
            if (!closed.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
                invalid();
            for (String table : tables)
                if ((!Objects.equals(
                                closed.rows().get(table),
                                source.capture().tables().get(table).rows())
                        || closed.unavailableTables().contains(table))) invalid();
            // A row with two witnesses is refused here, as the whole read refuses it.
            for (var store : stores.values()) store.seal();
            // The scopes are part of the captured table; the row pass returns the same capture.
            var scopes = scopes(source.capture());
            var notes = stores.get("casemgmt_note");
            var issues = stores.get("casemgmt_issue");
            var definitions = stores.get("issue");
            var facts = new Chart(notes, stores.get("casemgmt_issue_notes"), issues, definitions);
            var actual =
                    new CdsExportCapturedTextRowsReader(captures)
                            .forEachTable(
                                    lease,
                                    patient,
                                    requiredTables,
                                    tables,
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    // One row's text, not the chart's.
                                    67_108_864,
                                    control,
                                    row -> {
                                        control.checkpoint();
                                        int position =
                                                stores.get(row.table())
                                                        .claim(
                                                                CdsExportCaseManagementProjectionEvidence
                                                                        .reduce(row));
                                        switch (row.table()) {
                                            case "casemgmt_note" -> {
                                                requireScope(row, scopes);
                                                if (nativeContextRow(row.fields()))
                                                    facts.nativeContext.set(position);
                                            }
                                            case "casemgmt_note_ext" ->
                                                    notes.position(
                                                            notes.require(
                                                                    row.fields().get("note_id")));
                                            case NOTE_LINKS ->
                                                    facts.nativeContext.set(
                                                            notes.position(
                                                                    notes.require(
                                                                            row.fields()
                                                                                    .get(
                                                                                            "note_id"))));
                                            case "casemgmt_issue_notes" -> {
                                                requireLink(row);
                                                notes.position(
                                                        notes.require(row.fields().get("note_id")));
                                            }
                                            case "casemgmt_issue" -> {
                                                if (!Integer.toString(patient.patient())
                                                        .equals(row.fields().get("demographic_no")))
                                                    invalid();
                                                facts.issueDefinition[position] =
                                                        definitions.require(
                                                                row.fields().get("issue_id"));
                                            }
                                            case "issue" -> {
                                                var tag =
                                                        CdsExportNativeHistoryMapper.tag(row);
                                                facts.definitionFacts[position] =
                                                        (byte)
                                                                ((tag == null
                                                                                ? 0
                                                                                : tag.ordinal()
                                                                                        + 1)
                                                                        | (CdsExportNativeHistoryMapper
                                                                                        .systemDefinition(
                                                                                                row)
                                                                                ? Chart.SYSTEM
                                                                                : 0));
                                            }
                                            default -> invalid();
                                        }
                                    });
            if (!actual.equals(source.capture())) invalid();
            // Every witness had its row, so the tables hold as many rows as the capture states.
            for (String table : tables)
                if (!stores.get(table).complete()
                        || stores.get(table).size() != actual.tables().get(table).rows())
                    invalid();
            if (scopes.size() != notes.size()) invalid();
            for (long note : scopes.keySet()) if (notes.find(note, 0) < 0) invalid();
            // Each link joins a patient issue of this chart; each patient issue names a
            // captured catalogue entry.
            facts.seal();
            // The digests proved the rows; only the keys are kept.
            for (var store : stores.values()) store.release();
            captures.authorizeCapturedSources(lease, patient, requiredTables, actual, control);
            return new CdsExportHistoryCurrentContext(
                    source.manifest().plaintextSha256(),
                    patient.patient(),
                    null,
                    List.of(),
                    scopes,
                    facts.bind(this),
                    null);
        }

        /**
         * One window of a chart read in windows: the rows of {@code notes} with every extension,
         * issue link and note link that names one of them, the patient issues those links and
         * {@code issues} name with their catalogue entries, and every row {@code references}
         * names, each checked against its whole witness.
         *
         * @return null when the load passed the window's bytes; nothing is kept then
         */
        private CdsExportHistoryCurrentContext window(
                Chart chart,
                Map<Long, CdsExportNoteAccessDto> scopes,
                Set<Long> notes,
                Set<String> issues,
                Collection<CdsExportRetainedIndexDto.Reference> references,
                CdsExportSectionWindowPlanner.Budget budget)
                throws IOException {
            patient.require(lease);
            control.checkpoint();
            var owned = new HashSet<String>();
            var needed = new HashMap<String, Set<String>>();
            for (String table : tables) needed.put(table, new HashSet<>());
            for (long note : notes) {
                if (!scopes.containsKey(note)) invalid();
                owned.add(Long.toString(note));
                needed.get("casemgmt_issue").addAll(chart.linkedIssues(note));
            }
            needed.get("casemgmt_issue").addAll(issues);
            // What an import unit names is compared with the row the chart holds now, whichever
            // note it belongs to today; a row that no longer exists is simply not found.
            for (var reference : references)
                if (REFERENCED.contains(reference.table()))
                    needed.get(reference.table()).add(reference.id());
            for (String issue : needed.get("casemgmt_issue")) {
                String definition = chart.definitionOf(issue);
                if (definition != null) needed.get("issue").add(definition);
            }
            var kept = new HashMap<String, CdsExportCapturedTextRowDto>();
            var links = new ArrayList<CdsExportCapturedTextRowDto>();
            var actual =
                    new CdsExportCapturedTextRowsReader(captures)
                            .forEachTable(
                                    lease,
                                    patient,
                                    requiredTables,
                                    tables,
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    // One row's text, not the chart's.
                                    67_108_864,
                                    control,
                                    row -> {
                                        control.checkpoint();
                                        // A window found too large keeps nothing more; it is
                                        // read again with fewer owners.
                                        if (budget.exceeded()) return;
                                        String id = id(row);
                                        boolean owner =
                                                switch (row.table()) {
                                                    case "casemgmt_note" -> owned.contains(id);
                                                    case "casemgmt_note_ext",
                                                                    "casemgmt_issue_notes",
                                                                    NOTE_LINKS ->
                                                            owned.contains(
                                                                    row.fields().get("note_id"));
                                                    default -> false;
                                                };
                                        if (!owner && !needed.get(row.table()).contains(id))
                                            return;
                                        if (owner && row.table().equals("casemgmt_note")
                                                ? !budget.owner(size(row))
                                                : !budget.add(1, size(row))) return;
                                        if (owner && row.table().equals("casemgmt_issue_notes"))
                                            links.add(row);
                                        if (kept.putIfAbsent(key(row.table(), id), row) != null)
                                            invalid();
                                    });
            if (budget.exceeded()) return null;
            if (!actual.equals(source.capture())) invalid();
            var states = new HashMap<String, CdsExportNativeRowReceiptDto>();
            var closed =
                    receipts.scan(
                            lease,
                            patient,
                            requiredTables,
                            source,
                            bounds.fields(),
                            bounds.bytes(),
                            control,
                            row -> {
                                if (!tables.contains(row.table())
                                        || !kept.containsKey(key(row.table(), row.id()))) return;
                                if (row.state() == null
                                        || row.partial()
                                        || row.digest() == null) invalid();
                                if (!budget.add(2, size(row))) return;
                                if (states.putIfAbsent(key(row.table(), row.id()), row) != null)
                                    invalid();
                            });
            if (budget.exceeded()) return null;
            if (!closed.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                    || states.size() != kept.size()) invalid();
            var records = new HashMap<String, Map<String, CdsExportHistoryFieldReconciler.Row>>();
            for (String table : tables) records.put(table, new HashMap<>());
            for (var entry : kept.entrySet()) {
                var row = entry.getValue();
                var witness = states.remove(entry.getKey());
                if (witness == null) invalid();
                CdsExportCaseManagementProjectionEvidence.verify(row, witness);
                records.get(row.table())
                        .put(id(row), new CdsExportHistoryFieldReconciler.Row(row, witness));
            }
            // Every note the window owns was found, and each is the patient's under its scope.
            for (String note : owned) {
                var row = records.get("casemgmt_note").get(note);
                if (row == null) invalid();
                requireScope(row.value(), scopes);
            }
            // The window took exactly the links the chart-wide pass counted for its notes, and
            // each joins a patient issue the window holds.
            long linked = 0;
            for (long note : notes) linked = Math.addExact(linked, chart.linkedIssues(note).size());
            if (linked != links.size()) invalid();
            for (var link : links) {
                requireLink(link);
                if (!records.get("casemgmt_issue").containsKey(link.fields().get("id"))) invalid();
            }
            for (String issue : issues)
                if (!records.get("casemgmt_issue").containsKey(issue)) invalid();
            for (var issue : records.get("casemgmt_issue").values())
                if (!Integer.toString(patient.patient())
                                .equals(issue.value().fields().get("demographic_no"))
                        || !records.get("issue")
                                .containsKey(issue.value().fields().get("issue_id"))) invalid();
            captures.authorizeCapturedSources(lease, patient, requiredTables, actual, control);
            return new CdsExportHistoryCurrentContext(
                    source.manifest().plaintextSha256(),
                    patient.patient(),
                    records,
                    links,
                    scopes,
                    chart,
                    Set.copyOf(owned));
        }
    }

    /**
     * One table's rows of a chart read in windows, reduced: each row's key as two numbers and,
     * until the row pass has proved it, the digest of its witness.
     */
    private static final class Store {
        private long[] first = new long[256], second = new long[256];
        private byte[] digests = new byte[256 * 32];
        private int size;
        private boolean ascending = true;
        private BitSet seen;

        private void add(CdsExportCaseManagementProjectionEvidence.Reduced witness) {
            if (size == first.length) {
                int grown = Math.addExact(size, Math.max(256, size >> 1));
                first = Arrays.copyOf(first, grown);
                second = Arrays.copyOf(second, grown);
                digests = Arrays.copyOf(digests, Math.multiplyExact(grown, 32));
            }
            long a = witness.key().first(), b = witness.key().second();
            if (size > 0 && compare(first[size - 1], second[size - 1], a, b) >= 0)
                ascending = false;
            first[size] = a;
            second[size] = b;
            System.arraycopy(witness.digest(), 0, digests, size * 32, 32);
            size++;
        }

        private static int compare(long a, long b, long otherA, long otherB) {
            int order = Long.compare(a, otherA);
            return order != 0 ? order : Long.compare(b, otherB);
        }

        /** Orders the witnesses by key; a row with two witnesses is refused. */
        private void seal() throws IOException {
            if (!ascending) {
                var order = new Integer[size];
                for (int i = 0; i < size; i++) order[i] = i;
                long[] a = first, b = second;
                Arrays.sort(
                        order, (left, right) -> compare(a[left], b[left], a[right], b[right]));
                long[] sortedFirst = new long[size], sortedSecond = new long[size];
                byte[] sortedDigests = new byte[size * 32];
                for (int i = 0; i < size; i++) {
                    sortedFirst[i] = first[order[i]];
                    sortedSecond[i] = second[order[i]];
                    System.arraycopy(digests, order[i] * 32, sortedDigests, i * 32, 32);
                }
                first = sortedFirst;
                second = sortedSecond;
                digests = sortedDigests;
                ascending = true;
            }
            for (int i = 1; i < size; i++)
                if (compare(first[i - 1], second[i - 1], first[i], second[i]) >= 0) invalid();
            seen = new BitSet(size);
        }

        private int size() {
            return size;
        }

        /** The position of the row with this key, or -1. */
        private int find(long a, long b) {
            int low = 0, high = size - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int order = compare(first[middle], second[middle], a, b);
                if (order < 0) low = middle + 1;
                else if (order > 0) high = middle - 1;
                else return middle;
            }
            return -1;
        }

        /** The first position whose key is not below ({@code a}, 0). */
        private int from(long a) {
            int low = 0, high = size;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (first[middle] < a) low = middle + 1;
                else high = middle;
            }
            return low;
        }

        /** The number a row names another row by: canonical and positive, or refused. */
        private long require(String id) throws IOException {
            long number = canonical(id);
            if (number < 1) invalid();
            return number;
        }

        /** The position of the row with this id; a row that names none is refused. */
        private int position(long id) throws IOException {
            int position = find(id, 0);
            if (position < 0) invalid();
            return position;
        }

        /**
         * Binds a row to its witness: the witness exists, froze exactly this row and has had no
         * other row.
         */
        private int claim(CdsExportCaseManagementProjectionEvidence.Reduced row)
                throws IOException {
            int position = find(row.key().first(), row.key().second());
            if (position < 0 || seen.get(position)) invalid();
            if (!Arrays.equals(
                    digests, position * 32, position * 32 + 32, row.digest(), 0, row.digest().length))
                throw new IOException("CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID");
            seen.set(position);
            return position;
        }

        private boolean complete() {
            return seen.cardinality() == size;
        }

        private void release() {
            digests = null;
            seen = null;
            first = Arrays.copyOf(first, size);
            second = Arrays.copyOf(second, size);
        }
    }

    /**
     * What the section plans from, for a chart read in windows: the notes, the note-to-issue
     * links, the patient issues with their catalogue entry and the catalogue entries with their
     * CPP tag. Nothing here is a row.
     */
    private static final class Chart {
        private static final int SYSTEM = 0x80;
        private static final CdsCategory[] CATEGORIES = CdsCategory.values();

        private final Store notes, links, issues, definitions;
        /** A note whose own columns or attached records need the native carrier. */
        private final BitSet nativeContext;
        /** The catalogue entry of each patient issue, by the issue's position. */
        private final long[] issueDefinition;
        /** Each catalogue entry's CPP tag (its ordinal plus one) and whether it is a system entry. */
        private final byte[] definitionFacts;
        private Loader loader;

        private Chart(Store notes, Store links, Store issues, Store definitions) {
            this.notes = notes;
            this.links = links;
            this.issues = issues;
            this.definitions = definitions;
            nativeContext = new BitSet(notes.size());
            issueDefinition = new long[issues.size()];
            definitionFacts = new byte[definitions.size()];
        }

        private Chart bind(Loader loader) {
            this.loader = loader;
            return this;
        }

        /** Every link joins a captured patient issue; every issue names a captured entry. */
        private void seal() throws IOException {
            for (int i = 0; i < links.size(); i++)
                if (issues.find(links.second[i], 0) < 0) invalid();
            for (long definition : issueDefinition)
                if (definitions.find(definition, 0) < 0) invalid();
        }

        private List<String> linkedIssues(long note) {
            var result = new ArrayList<String>();
            for (int i = links.from(note); i < links.size() && links.first[i] == note; i++)
                result.add(Long.toString(links.second[i]));
            return result;
        }

        private int issue(String id) {
            long number = canonical(id);
            return number < 1 ? -1 : issues.find(number, 0);
        }

        private int definition(String id) {
            long number = canonical(id);
            return number < 1 ? -1 : definitions.find(number, 0);
        }

        private String definitionOf(String issue) {
            int position = issue(issue);
            return position < 0 ? null : Long.toString(issueDefinition[position]);
        }

        private CdsCategory tag(int definition) {
            int tag = definitionFacts[definition] & 0x7f;
            return tag == 0 ? null : CATEGORIES[tag - 1];
        }
    }

    /** True for a chart read in windows: this context holds its facts and none of its rows. */
    boolean windowed() {
        return records == null;
    }

    /**
     * The rows of some owners of a chart read in windows, read from the capture and checked
     * against their whole witnesses: the given notes with every row that belongs to them, the
     * given patient issues, and whatever rows the given import-unit references name.
     *
     * @return null when the load passed the window's bytes; nothing is kept then
     */
    CdsExportHistoryCurrentContext window(
            Set<Long> notes,
            Set<String> issues,
            Collection<CdsExportRetainedIndexDto.Reference> references,
            CdsExportSectionWindowPlanner.Budget budget)
            throws IOException {
        if (records != null || chart == null) invalid();
        return chart.loader.window(chart, scopes, notes, issues, references, budget);
    }

    /** The child rows this window took for its notes: extensions, issue links, note links. */
    long[] taken() {
        return taken.clone();
    }

    Set<Long> noteIds() {
        return scopes.keySet();
    }

    /** The CPP section the links of one note file it under. */
    CdsCategory category(long note) throws IOException {
        if (records != null) return CdsExportNativeHistoryMapper.category(note(note));
        var categories = EnumSet.noneOf(CdsCategory.class);
        for (int i = chart.links.from(note);
                i < chart.links.size() && chart.links.first[i] == note;
                i++) {
            int issue = chart.issues.find(chart.links.second[i], 0);
            if (issue < 0) invalid();
            int definition = chart.definitions.find(chart.issueDefinition[issue], 0);
            if (definition < 0) invalid();
            var category = chart.tag(definition);
            if (category != null) categories.add(category);
        }
        return CdsExportNativeHistoryMapper.category(categories);
    }

    /** The patient issues one note links. */
    List<String> linkedIssues(long note) throws IOException {
        if (records == null) return chart.linkedIssues(note);
        var result = new ArrayList<String>();
        for (var link : note(note).links()) result.add(link.fields().get("id"));
        return result;
    }

    /** Every patient issue of the chart. */
    Collection<String> patientIssueIds() {
        if (records != null) return records.get("casemgmt_issue").keySet();
        return new AbstractList<>() {
            @Override
            public String get(int index) {
                return Long.toString(chart.issues.first[index]);
            }

            @Override
            public int size() {
                return chart.issues.size();
            }
        };
    }

    /** The catalogue entry a patient issue names, or null when the chart has no such issue. */
    String definitionOf(String issue) {
        if (records == null) return chart.definitionOf(issue);
        var row = records.get("casemgmt_issue").get(issue);
        return row == null ? null : row.value().fields().get("issue_id");
    }

    /** Whether the capture holds this catalogue entry. */
    boolean hasDefinition(String definition) {
        return records == null
                ? chart.definition(definition) >= 0
                : records.get("issue").containsKey(definition);
    }

    /** {@link CdsExportNativeHistoryMapper#systemDefinition} of a captured catalogue entry. */
    boolean systemDefinition(String definition) throws IOException {
        if (records != null) {
            var row = records.get("issue").get(definition);
            if (row == null) invalid();
            return CdsExportNativeHistoryMapper.systemDefinition(row);
        }
        int position = chart.definition(definition);
        if (position < 0) invalid();
        return (chart.definitionFacts[position] & Chart.SYSTEM) != 0;
    }

    /** {@link CdsExportNativeHistoryMapper#issueCategory} of a captured catalogue entry. */
    CdsCategory issueCategory(String definition) throws IOException {
        if (records != null)
            return CdsExportNativeHistoryMapper.issueCategory(records.get("issue").get(definition));
        int position = chart.definition(definition);
        if (position < 0) invalid();
        var tag = chart.tag(position);
        return tag == null ? CdsCategory.PROBLEM_LIST : tag;
    }

    /** Whether exactly these catalogue entries were captured. */
    boolean definitionsAre(Set<String> definitions) {
        if (records != null) return definitions.equals(records.get("issue").keySet());
        if (definitions.size() != chart.definitions.size()) return false;
        for (String definition : definitions) if (chart.definition(definition) < 0) return false;
        return true;
    }

    /**
     * Whether a note's own row sends a single imported revision through the native carrier: it
     * is attached to another record, or one of its archive, signature or encounter columns
     * holds a value.
     */
    boolean nativeContextNote(long note) throws IOException {
        if (records == null) {
            int position = chart.notes.find(note, 0);
            if (position < 0) invalid();
            return chart.nativeContext.get(position);
        }
        var current = note(note);
        // The retained import source has no place for what a note is attached to; only the
        // native revision context carries it. (The CARLOS importer writes no note links, so an
        // imported note has one only if CARLOS attached it to a record later.)
        if (!current.noteLinks().isEmpty()) return true;
        // A single imported revision can acquire archive, signature or encounter context too.
        // Route it through the same qualified native carrier used for complete revision chains.
        return nativeContextRow(current.note().value().fields());
    }

    private static boolean nativeContextRow(Map<String, String> fields) {
        for (String column : NATIVE_CONTEXT_COLUMNS) {
            String value = fields.get(column);
            if (value != null && !value.isEmpty() && !value.equals("0")) return true;
        }
        return false;
    }

    String manifest() {
        return manifest;
    }

    int patient() {
        return patient;
    }

    CdsExportNoteAccessDto scope(long id) {
        return scopes.get(id);
    }

    CdsExportHistoryFieldReconciler.Current note(long id) throws IOException {
        if (records == null) invalid();
        String key = Long.toString(id);
        var note = records.get("casemgmt_note").get(key);
        // A window holds the child rows of the notes it owns and of no other note.
        if (note == null || owned != null && !owned.contains(key)) invalid();
        return new CdsExportHistoryFieldReconciler.Current(
                note,
                extensions.getOrDefault(key, List.of()),
                issueLinks.getOrDefault(key, List.of()),
                records.get("casemgmt_issue"),
                records.get("issue"),
                noteLinks.getOrDefault(key, List.of()));
    }

    CdsExportHistoryFieldReconciler.Row original(String table, String id) {
        if (records == null)
            throw new IllegalStateException("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
        return records.getOrDefault(table, Map.of()).get(id);
    }

    @Override
    public String toString() {
        return "CdsExportHistoryCurrentContext[protected]";
    }

    private static String id(CdsExportCapturedTextRowDto row) {
        String primary =
                switch (row.table()) {
                    case "casemgmt_note" -> "note_id";
                    case "issue" -> "issue_id";
                    default -> "id";
                };
        return row.table().equals("casemgmt_issue_notes")
                ? row.fields().get("note_id") + "_" + row.fields().get("id")
                : row.fields().get(primary);
    }

    /** A note-to-issue link row is the pair its key states and nothing else. */
    private static void requireLink(CdsExportCapturedTextRowDto link) throws IOException {
        String note = link.fields().get("note_id"), issue = link.fields().get("id");
        try {
            if (!link.recordKey()
                    .equals(
                            CdsExportPatientTableSourceReader.key(Long.parseLong(note))
                                    + CdsExportPatientTableSourceReader.key(
                                            Long.parseLong(issue)))) invalid();
        } catch (NumberFormatException malformed) {
            invalid();
        }
        if (!link.fields().keySet().equals(Set.of("id", "note_id"))
                || !link.exactNumbers().isEmpty()) invalid();
    }

    /** What a held row counts against its window: its text and a fixed share for each field. */
    private static long size(CdsExportCapturedTextRowDto row) {
        long size = 0;
        for (var value : row.fields().values())
            size += (value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length) + 64L;
        return size;
    }

    /** What a held witness counts against its window. */
    private static long size(CdsExportNativeRowReceiptDto witness) {
        return 1024L + (witness.state() == null ? 0 : witness.state().columns().size() * 256L);
    }

    private static String key(String table, String id) {
        return table + "/" + id;
    }

    /** A positive number in its canonical decimal form, or -1. */
    private static long canonical(String id) {
        if (id == null) return -1;
        try {
            long number = Long.parseLong(id);
            return number > 0 && Long.toString(number).equals(id) ? number : -1;
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }
}
