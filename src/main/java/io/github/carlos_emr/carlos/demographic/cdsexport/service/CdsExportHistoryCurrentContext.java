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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Closed complete patient case-management context. Only authenticated complete scans can create this evidence. */
public final class CdsExportHistoryCurrentContext {
    private static final Set<String> TABLES = Set.of("casemgmt_note", "casemgmt_note_ext", "casemgmt_issue_notes", "casemgmt_issue", "issue");
    private final String manifest;
    private final int patient;
    private final Map<String, Map<String, CdsExportHistoryFieldReconciler.Row>> records;
    private final List<CdsExportCapturedTextRowDto> links;
    private final Map<Long, CdsExportNoteAccessDto> scopes;
    private CdsExportHistoryCurrentContext(String manifest, int patient, Map<String, Map<String, CdsExportHistoryFieldReconciler.Row>> records,
            List<CdsExportCapturedTextRowDto> links, Map<Long, CdsExportNoteAccessDto> scopes) {
        this.manifest = manifest; this.patient = patient;
        var copied = new HashMap<String, Map<String, CdsExportHistoryFieldReconciler.Row>>(); records.forEach((table, rows) -> copied.put(table, Map.copyOf(rows)));
        this.records = Map.copyOf(copied); this.links = List.copyOf(links); this.scopes = Map.copyOf(scopes);
    }
    /** Reads all five domains and their independent full-row witnesses under the same verified captured manifest. */
    public static CdsExportHistoryCurrentContext read(CdsExportNativeCaptureService captures, CdsExportNativeRowReceiptReader receipts,
            CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportPatientCaptureDto source, CdsExportClinicalOccurrenceService.Bounds bounds, CdsExportRunControl control) throws IOException {
        Objects.requireNonNull(captures); Objects.requireNonNull(receipts); Objects.requireNonNull(bounds); Objects.requireNonNull(control); patient.require(lease);
        if (source == null || !requiredTables.containsAll(TABLES) || !source.capture().tables().keySet().equals(requiredTables)) invalid();
        var states = new HashMap<String, CdsExportNativeRowReceiptDto>(); var records = new HashMap<String, Map<String, CdsExportHistoryFieldReconciler.Row>>();
        var links = new ArrayList<CdsExportCapturedTextRowDto>(); long[] memory = {0};
        for (String table : TABLES) {
            long rows = source.capture().tables().get(table).rows();
            if (rows > (table.equals("issue") ? bounds.lookupRows() : bounds.nativeRows())) limit();
            records.put(table, new HashMap<>());
        }
        var closed = receipts.scan(lease, patient, requiredTables, source, bounds.fields(), bounds.bytes(), control, row -> {
            if (!TABLES.contains(row.table())) return;
            if (row.state() == null || row.partial() || row.digest() == null || states.putIfAbsent(key(row.table(), row.id()), row) != null) invalid();
            memory[0] = Math.addExact(memory[0], 2048L + row.state().columns().size() * 384L); if (memory[0] > bounds.indexBytes()) limit();
        });
        if (!closed.sourceManifestSha256().equals(source.manifest().plaintextSha256())) invalid();
        for (String table : TABLES) if ((!Objects.equals(closed.rows().get(table), source.capture().tables().get(table).rows()) || closed.unavailableTables().contains(table))) invalid();
        var count = new HashMap<String, Long>();
        var actual = new CdsExportCapturedTextRowsReader(captures).forEachTable(lease, patient, requiredTables, TABLES, source.manifest(), bounds.fields(), bounds.bytes(),
                (int) Math.min(67_108_864L, bounds.indexBytes()), control, row -> {
            control.checkpoint(); memory[0] = Math.addExact(memory[0], 1024L + row.fields().size() * 256L);
            for (String value : row.fields().values()) if (value != null) memory[0] = Math.addExact(memory[0], value.getBytes(StandardCharsets.UTF_8).length * 3L + 64L);
            if (memory[0] > bounds.indexBytes()) limit(); count.merge(row.table(), 1L, Math::addExact);
            if (row.table().equals("casemgmt_issue_notes")) links.add(row);
            String primary = switch (row.table()) { case "casemgmt_note" -> "note_id"; case "issue" -> "issue_id"; default -> "id"; };
            String id = row.table().equals("casemgmt_issue_notes") ? row.fields().get("note_id") + "_" + row.fields().get("id") : row.fields().get(primary); var witness = states.remove(key(row.table(), id)); if (witness == null) invalid();
            CdsExportCaseManagementProjectionEvidence.verify(row, witness);
            if (records.get(row.table()).putIfAbsent(id, new CdsExportHistoryFieldReconciler.Row(row, witness)) != null) invalid();
        });
        if (!actual.equals(source.capture()) || !states.isEmpty()) invalid();
        for (String table : TABLES) if (count.getOrDefault(table, 0L) != actual.tables().get(table).rows()) invalid();
        var scopes = new HashMap<Long, CdsExportNoteAccessDto>();
        for (var scope : actual.tables().get("casemgmt_note").noteAccess()) if (scope.patient() != patient.patient() || scopes.putIfAbsent(scope.noteId(), scope) != null) invalid();
        if (scopes.size() != records.get("casemgmt_note").size()) invalid();
        for (var note : records.get("casemgmt_note").values()) {
            var scope = scopes.get(Long.valueOf(note.witness().id()));
            if (!Integer.toString(patient.patient()).equals(note.value().fields().get("demographic_no")) || scope == null
                    || !Objects.equals(scope.program(), note.value().fields().get("program_no"))
                    || !Objects.equals(scope.reporterRole(), note.value().fields().get("reporter_caisi_role"))) invalid();
        }
        for (var extension : records.get("casemgmt_note_ext").values()) if (!records.get("casemgmt_note").containsKey(extension.value().fields().get("note_id"))) invalid();

        var pairs = new HashSet<String>();
        for (var link : links) {
            String note = link.fields().get("note_id"), issue = link.fields().get("id");
            try { if (!link.recordKey().equals(CdsExportPatientTableSourceReader.key(Long.parseLong(note)) + CdsExportPatientTableSourceReader.key(Long.parseLong(issue)))) invalid(); }
            catch (NumberFormatException malformed) { invalid(); }
            if (!link.fields().keySet().equals(Set.of("id", "note_id")) || !link.exactNumbers().isEmpty()
                    || !records.get("casemgmt_note").containsKey(note) || !records.get("casemgmt_issue").containsKey(issue) || !pairs.add(note + "/" + issue)) invalid();
        }
        for (var issue : records.get("casemgmt_issue").values()) if (!Integer.toString(patient.patient()).equals(issue.value().fields().get("demographic_no"))
                || !records.get("issue").containsKey(issue.value().fields().get("issue_id"))) invalid();
        captures.authorizeCapturedSources(lease, patient, requiredTables, actual, control);
        return new CdsExportHistoryCurrentContext(source.manifest().plaintextSha256(), patient.patient(), records, links, scopes);
    }
    Set<Long> noteIds() { return scopes.keySet(); }
    Set<String> patientIssueIds() { return records.get("casemgmt_issue").keySet(); }
    Set<String> definitionIds() { return records.get("issue").keySet(); }
    String manifest() { return manifest; }
    int patient() { return patient; }
    CdsExportNoteAccessDto scope(long id) { return scopes.get(id); }
    CdsExportHistoryFieldReconciler.Current note(long id) throws IOException {
        var note = records.get("casemgmt_note").get(Long.toString(id)); if (note == null) invalid();
        var extensions = records.get("casemgmt_note_ext").values().stream().filter(row -> Long.toString(id).equals(row.value().fields().get("note_id"))).toList();
        var selectedLinks = links.stream().filter(row -> Long.toString(id).equals(row.fields().get("note_id"))).toList();
        return new CdsExportHistoryFieldReconciler.Current(note, extensions, selectedLinks, records.get("casemgmt_issue"), records.get("issue"));
    }
    CdsExportHistoryFieldReconciler.Row original(String table, String id) { return records.getOrDefault(table, Map.of()).get(id); }
    @Override public String toString() { return "CdsExportHistoryCurrentContext[protected]"; }
    private static String key(String table, String id) { return table + "/" + id; }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID"); }
    private static void limit() { throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException(io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException.Reason.RESOURCE_LIMIT); }
}
