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

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Complete source-bound revision inventory; a current note is the owning chart's maximum native ID, not its latest timestamp. */
public final class CdsExportNoteRevisionPlanner {
    public enum Review { MISSING_UUID, UNSUPPORTED_UUID, UNSUPPORTED_FLAGS }
    public record Chain(String canonicalUuid, List<Long> revisions, long currentNoteId) {
        public Chain {
            revisions = List.copyOf(revisions);
            if (canonicalUuid == null || !canonicalUuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") || revisions.isEmpty() || revisions.size() > 8192 || currentNoteId != revisions.getLast()) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_CHAIN_INVALID");
            long previous = 0; for (long id : revisions) { if (id <= previous) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_CHAIN_INVALID"); previous = id; }
        }
        @Override public String toString() { return "CdsExportNoteRevisionPlanner.Chain[protected]"; }
    }
    public record Result(int patient, String sourceManifestSha256, List<Chain> chains, Map<Long, Review> reviews, long rows) {
        public Result {
            chains = List.copyOf(chains); reviews = Map.copyOf(reviews);
            if (patient < 1 || sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}") || rows < 0 || rows > 8192) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
            if (chains.size() > 8192 || reviews.size() > 8192) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
            var all = new HashSet<Long>(reviews.keySet());
            if (all.stream().anyMatch(id -> id == null || id < 1)) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
            var uuids = new HashSet<String>();
            for (var chain : chains) {
                if (!uuids.add(chain.canonicalUuid())) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
                for (long id : chain.revisions()) if (!all.add(id)) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
            }
            if (all.size() != rows) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
        }
        public boolean qualifiedLineages() { return reviews.isEmpty(); }
        @Override public String toString() { return "CdsExportNoteRevisionPlanner.Result[protected]"; }
    }

    /**
     * Every metadata field must match its exact whole-row frozen witness. This proves revision
     * grouping only; earlier revisions remain explicit audit evidence and no clinical body is
     * dropped, serialized, or approved by this inventory. A null/unsupported legacy UUID needs
     * review rather than an invented chain identity.
     */
    public Result plan(int patient, String manifest, Collection<CdsExportNoteAccessDto> expected,
            Iterable<CdsExportNoteRevisionDto> metadata, Map<Long, CdsExportNativeRowReceiptDto> witnesses, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint); checkpoint.run();
        if (patient < 1 || manifest == null || !manifest.matches("[0-9a-f]{64}") || expected == null || expected.size() > 8192 || witnesses == null || metadata == null) invalid();
        var ids = new HashSet<Long>(); for (var scope : expected) if (scope.patient() != patient || !ids.add(scope.noteId())) invalid();
        if (!ids.equals(witnesses.keySet())) invalid();
        var seen = new HashSet<Long>(); var reviews = new TreeMap<Long, Review>(); var groups = new TreeMap<String, List<Long>>();
        for (var row : metadata) {
            checkpoint.run(); if (row == null || row.patient() != patient || !ids.contains(row.noteId()) || !seen.add(row.noteId()) || seen.size() > 8192) invalid();
            verify(row, witnesses.get(row.noteId()));
            if (row.uuid() == null || row.uuid().isEmpty()) { reviews.put(row.noteId(), Review.MISSING_UUID); continue; }
            if (!row.uuid().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) { reviews.put(row.noteId(), Review.UNSUPPORTED_UUID); continue; }
            if (row.archived() != null && !Set.of("0", "1").contains(row.archived()) || !Set.of("0", "1").contains(row.signed() == null ? "" : row.signed())) reviews.put(row.noteId(), Review.UNSUPPORTED_FLAGS);
            // Canonical UUIDs contain only ASCII hexadecimal and hyphens: folding case matches
            // the chart's SQL collation without guessing how arbitrary legacy strings compare.
            String uuid = UUID.fromString(row.uuid()).toString(); groups.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(row.noteId());
        }
        if (!seen.equals(ids)) invalid();
        var chains = new ArrayList<Chain>();
        for (var group : groups.entrySet()) {
            checkpoint.run(); var revisions = group.getValue(); revisions.sort(Long::compare);
            if (revisions.stream().anyMatch(reviews::containsKey)) { for (long id : revisions) reviews.put(id, Review.UNSUPPORTED_FLAGS); }
            else chains.add(new Chain(group.getKey(), revisions, revisions.getLast()));
        }
        checkpoint.run(); return new Result(patient, manifest, chains, reviews, seen.size());
    }
    private static void verify(CdsExportNoteRevisionDto row, CdsExportNativeRowReceiptDto witness) throws IOException {
        String id = Long.toString(row.noteId());
        if (witness == null || witness.partial() || witness.state() == null || witness.digest() == null || !witness.table().equals("casemgmt_note")
                || !id.equals(witness.id()) || !CdsExportPatientTableSourceReader.key(row.noteId()).equals(witness.recordKey())
                || !Set.copyOf(witness.state().columns().stream().map(CdsNativeRowDigest.Column::name).toList()).equals(Set.copyOf(io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan.CASE_NOTES.columns()))) invalid();
        var values = new HashMap<String, String>(); values.put("note_id", id); values.put("demographic_no", Integer.toString(row.patient())); values.put("uuid", row.uuid());
        values.put("observation_date", row.observationDate()); values.put("update_date", row.updateDate()); values.put("archived", row.archived()); values.put("signed", row.signed());
        for (var value : values.entrySet()) {
            int index = -1; for (int i = 0; i < witness.state().columns().size(); i++) if (witness.state().columns().get(i).name().equals(value.getKey())) index = i;
            if (index < 0) invalid(); var cell = witness.state().cells().get(index);
            if (cell.exactNumeric() != null || CdsExactNumeric.codec(witness.state().columns().get(index).type()) != null) invalid();
            if (value.getValue() == null) { if (!cell.sqlNull()) invalid(); }
            else {
                byte[] bytes = value.getValue().getBytes(StandardCharsets.UTF_8);
                if (cell.sqlNull() || cell.bytes() != bytes.length || !cell.sha256().equals(HexFormat.of().formatHex(io.github.carlos_emr.carlos.utility.CdsImportKeys.digest().digest(bytes)))) invalid();
            }
        }
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID"); }
}
