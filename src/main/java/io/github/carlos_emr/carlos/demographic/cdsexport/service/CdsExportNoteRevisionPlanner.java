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
    public enum Review {
        MISSING_UUID,
        UNSUPPORTED_UUID,
        UNSUPPORTED_FLAGS
    }

    public record Chain(String canonicalUuid, List<Long> revisions, long currentNoteId) {
        public Chain {
            revisions = List.copyOf(revisions);
            if (canonicalUuid == null
                    || !canonicalUuid.matches(
                            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    || revisions.isEmpty()
                    || currentNoteId != revisions.getLast())
                throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_CHAIN_INVALID");
            long previous = 0;
            for (long id : revisions) {
                if (id <= previous)
                    throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_CHAIN_INVALID");
                previous = id;
            }
        }

        @Override
        public String toString() {
            return "CdsExportNoteRevisionPlanner.Chain[protected]";
        }
    }

    public record Result(
            int patient,
            String sourceManifestSha256,
            List<Chain> chains,
            Map<Long, Review> reviews,
            long rows) {
        public Result {
            chains = List.copyOf(chains);
            reviews = Map.copyOf(reviews);
            if (patient < 1
                    || sourceManifestSha256 == null
                    || !sourceManifestSha256.matches("[0-9a-f]{64}")
                    || rows < 0)
                throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
            var all = new HashSet<Long>(reviews.keySet());
            if (all.stream().anyMatch(id -> id == null || id < 1))
                throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
            var uuids = new HashSet<String>();
            for (var chain : chains) {
                if (!uuids.add(chain.canonicalUuid()))
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
                for (long id : chain.revisions())
                    if (!all.add(id))
                        throw new IllegalArgumentException(
                                "CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
            }
            if (all.size() != rows)
                throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
        }

        public boolean qualifiedLineages() {
            return reviews.isEmpty();
        }

        @Override
        public String toString() {
            return "CdsExportNoteRevisionPlanner.Result[protected]";
        }
    }

    /** The note fields a revision's metadata is compared with its witness on, in a fixed order. */
    private static final List<String> COMPARED =
            List.of(
                    "note_id",
                    "demographic_no",
                    "uuid",
                    "observation_date",
                    "update_date",
                    "archived",
                    "signed");

    /**
     * The whole-row witnesses of a chart's notes, reduced as each is read to what the inventory
     * compares: the note id and one SHA-256 over the null flag, byte length and digest of the
     * seven compared cells. That is {@value #BYTES_PER_NOTE} bytes a note in two arrays, whatever
     * the receipt holds (a receipt carries every column of the note row and was several kilobytes
     * of heap for each note). Everything about a witness that does not depend on the metadata
     * row is checked here, when it is added.
     */
    public static final class Witnesses {
        /** What one note costs here: its id and one digest. */
        public static final int BYTES_PER_NOTE = Long.BYTES + 32;

        private long[] ids = new long[256];
        private byte[] digests = new byte[256 * 32];
        private int size;
        private boolean ascending = true;

        /** Adds one note's witness; a malformed one, or one of another table, is refused. */
        public void add(CdsExportNativeRowReceiptDto witness) throws IOException {
            if (witness == null
                    || witness.partial()
                    || witness.state() == null
                    || witness.digest() == null
                    || !"casemgmt_note".equals(witness.table())
                    || witness.id() == null) invalid();
            long id;
            try {
                id = Long.parseLong(witness.id());
            } catch (NumberFormatException malformed) {
                invalid();
                return;
            }
            var columns = witness.state().columns();
            if (id < 1
                    || !Long.toString(id).equals(witness.id())
                    || !CdsExportPatientTableSourceReader.key(id).equals(witness.recordKey())
                    || !Set.copyOf(columns.stream().map(CdsNativeRowDigest.Column::name).toList())
                            .equals(
                                    Set.copyOf(
                                            io.github.carlos_emr.carlos.commn.dao
                                                    .CdsExportPatientTableReadSupport.Plan
                                                    .CASE_NOTES
                                                    .columns()))) invalid();
            var digest = io.github.carlos_emr.carlos.utility.CdsImportKeys.digest();
            for (String field : COMPARED) {
                int index = -1;
                for (int i = 0; i < columns.size(); i++)
                    if (columns.get(i).name().equals(field)) index = i;
                if (index < 0) invalid();
                var cell = witness.state().cells().get(index);
                if (cell.exactNumeric() != null
                        || CdsExactNumeric.codec(columns.get(index).type()) != null) invalid();
                compared(digest, cell.sqlNull(), cell.bytes(), cell.sha256());
            }
            if (size == ids.length) {
                int grown = Math.addExact(size, Math.max(256, size >> 1));
                ids = Arrays.copyOf(ids, grown);
                digests = Arrays.copyOf(digests, Math.multiplyExact(grown, 32));
            }
            if (size > 0 && id <= ids[size - 1]) ascending = false;
            ids[size] = id;
            System.arraycopy(digest.digest(), 0, digests, size * 32, 32);
            size++;
        }

        /** How many notes have a witness. */
        public int size() {
            return size;
        }

        /** The heap the witnesses hold: the two arrays, including the room they grew ahead. */
        public long heldBytes() {
            return (long) ids.length * Long.BYTES + digests.length;
        }

        /** Orders the witnesses by note id; a note with two witnesses is refused. */
        private void seal() throws IOException {
            if (!ascending) {
                var order = new Integer[size];
                for (int i = 0; i < size; i++) order[i] = i;
                long[] unordered = ids;
                Arrays.sort(order, (left, right) -> Long.compare(unordered[left], unordered[right]));
                long[] sortedIds = new long[size];
                byte[] sortedDigests = new byte[size * 32];
                for (int i = 0; i < size; i++) {
                    sortedIds[i] = ids[order[i]];
                    System.arraycopy(digests, order[i] * 32, sortedDigests, i * 32, 32);
                }
                ids = sortedIds;
                digests = sortedDigests;
                ascending = true;
            }
            for (int i = 1; i < size; i++) if (ids[i] <= ids[i - 1]) invalid();
        }

        /** The position of a note's witness, or -1. */
        private int find(long id) {
            int found = Arrays.binarySearch(ids, 0, size, id);
            return found < 0 ? -1 : found;
        }

        private boolean matches(int position, byte[] digest) {
            return Arrays.equals(
                    digests, position * 32, position * 32 + 32, digest, 0, digest.length);
        }

        @Override
        public String toString() {
            return "CdsExportNoteRevisionPlanner.Witnesses[protected]";
        }
    }

    /** One compared cell, as both the witness and the metadata row state it. */
    private static void compared(
            java.security.MessageDigest digest, boolean missing, long bytes, String sha256)
            throws IOException {
        digest.update((byte) (missing ? 1 : 0));
        if (missing) return;
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) invalid();
        digest.update(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(bytes).array());
        digest.update(sha256.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Every metadata field must match its exact whole-row frozen witness. This proves revision
     * grouping only; earlier revisions remain explicit audit evidence and no clinical body is
     * dropped, serialized, or approved by this inventory. A null/unsupported legacy UUID needs
     * review rather than an invented chain identity.
     */
    public Result plan(
            int patient,
            String manifest,
            Collection<CdsExportNoteAccessDto> expected,
            Iterable<CdsExportNoteRevisionDto> metadata,
            Map<Long, CdsExportNativeRowReceiptDto> witnesses,
            Runnable checkpoint)
            throws IOException {
        if (witnesses == null || metadata == null) invalid();
        var reduced = new Witnesses();
        for (var entry : witnesses.entrySet()) {
            var witness = entry.getValue();
            // The witness is the one of the note it is filed under.
            if (entry.getKey() == null
                    || witness == null
                    || !Long.toString(entry.getKey()).equals(witness.id())) invalid();
            reduced.add(witness);
        }
        var session = open(patient, manifest, expected, reduced, checkpoint);
        for (var row : metadata) session.accept(row);
        return session.finish();
    }

    /**
     * Starts an inventory that takes the revision metadata a row at a time, so neither the
     * receipts nor the metadata rows of a long chart are held: what a note costs while its chart
     * is inventoried is its reduced witness, two bits and its place in the result.
     *
     * @param expected the note scopes the capture authorized; exactly the witnessed notes
     */
    public Session open(
            int patient,
            String manifest,
            Collection<CdsExportNoteAccessDto> expected,
            Witnesses witnesses,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (patient < 1
                || manifest == null
                || !manifest.matches("[0-9a-f]{64}")
                || expected == null
                || witnesses == null) invalid();
        witnesses.seal();
        var scoped = new BitSet(witnesses.size());
        for (var scope : expected) {
            int position = scope == null ? -1 : witnesses.find(scope.noteId());
            if (position < 0 || scope.patient() != patient || scoped.get(position)) invalid();
            scoped.set(position);
        }
        if (scoped.cardinality() != witnesses.size()) invalid();
        return new Session(patient, manifest, witnesses, checkpoint);
    }

    /** One chart's inventory in progress: every metadata row once, then the result. */
    public static final class Session {
        private final int patient;
        private final String manifest;
        private final Witnesses witnesses;
        private final Runnable checkpoint;
        private final BitSet seen;
        private final TreeMap<Long, Review> reviews = new TreeMap<>();
        private final TreeMap<String, List<Long>> groups = new TreeMap<>();

        private Session(int patient, String manifest, Witnesses witnesses, Runnable checkpoint) {
            this.patient = patient;
            this.manifest = manifest;
            this.witnesses = witnesses;
            this.checkpoint = checkpoint;
            seen = new BitSet(witnesses.size());
        }

        /** One note's revision metadata, compared with its witness and placed. */
        public void accept(CdsExportNoteRevisionDto row) throws IOException {
            checkpoint.run();
            int position = row == null ? -1 : witnesses.find(row.noteId());
            if (position < 0 || row.patient() != patient || seen.get(position)) invalid();
            seen.set(position);
            verify(row, witnesses, position);
            if (row.uuid() == null || row.uuid().isEmpty()) {
                reviews.put(row.noteId(), Review.MISSING_UUID);
                return;
            }
            if (!row.uuid()
                    .matches(
                            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                reviews.put(row.noteId(), Review.UNSUPPORTED_UUID);
                return;
            }
            if (row.archived() != null && !Set.of("0", "1").contains(row.archived())
                    || !Set.of("0", "1").contains(row.signed() == null ? "" : row.signed()))
                reviews.put(row.noteId(), Review.UNSUPPORTED_FLAGS);
            // Canonical UUIDs contain only ASCII hexadecimal and hyphens: folding case matches
            // the chart's SQL collation without guessing how arbitrary legacy strings compare.
            String uuid = UUID.fromString(row.uuid()).toString();
            groups.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(row.noteId());
        }

        /** The inventory, once every witnessed note has had its metadata row. */
        public Result finish() throws IOException {
            if (seen.cardinality() != witnesses.size()) invalid();
            var chains = new ArrayList<Chain>();
            for (var group : groups.entrySet()) {
                checkpoint.run();
                var revisions = group.getValue();
                revisions.sort(Long::compare);
                if (revisions.stream().anyMatch(reviews::containsKey)) {
                    for (long id : revisions) reviews.put(id, Review.UNSUPPORTED_FLAGS);
                } else chains.add(new Chain(group.getKey(), revisions, revisions.getLast()));
            }
            checkpoint.run();
            return new Result(patient, manifest, chains, reviews, witnesses.size());
        }

        @Override
        public String toString() {
            return "CdsExportNoteRevisionPlanner.Session[protected]";
        }
    }

    /** The row's seven compared fields are exactly the cells its witness froze. */
    private static void verify(CdsExportNoteRevisionDto row, Witnesses witnesses, int position)
            throws IOException {
        var values = new HashMap<String, String>();
        values.put("note_id", Long.toString(row.noteId()));
        values.put("demographic_no", Integer.toString(row.patient()));
        values.put("uuid", row.uuid());
        values.put("observation_date", row.observationDate());
        values.put("update_date", row.updateDate());
        values.put("archived", row.archived());
        values.put("signed", row.signed());
        var hash = io.github.carlos_emr.carlos.utility.CdsImportKeys.digest();
        var digest = io.github.carlos_emr.carlos.utility.CdsImportKeys.digest();
        for (String field : COMPARED) {
            String value = values.get(field);
            if (value == null) {
                compared(digest, true, 0, null);
                continue;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            compared(digest, false, bytes.length, HexFormat.of().formatHex(hash.digest(bytes)));
        }
        if (!witnesses.matches(position, digest.digest())) invalid();
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
    }
}
