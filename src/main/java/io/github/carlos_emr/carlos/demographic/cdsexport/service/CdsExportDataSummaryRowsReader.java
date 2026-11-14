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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads the authenticated captured rows of the data-summary tables of one patient generation, and
 * the patient's demographic row for the summary header. Nothing is queried from the database:
 * every value is read back from the sealed capture with its length, SHA-256 and strict UTF-8
 * verified.
 *
 * <p>No number or size of rows is refused. One row is held at a time: each verified row is written
 * whole to a protected member of the attempt's staging store and dropped, and what stays in memory
 * is its index entry: its capture key, its planning columns ({@link
 * CdsExportDataSummaryPlanner#planning}), the place, length and SHA-256 of its stored copy and
 * what it counts toward a record part. That is about half a kibibyte per row whatever the row
 * holds. A record's rows are loaded from the member when the record is written ({@link
 * Result#load}).
 *
 * @since 2026-11-13
 */
final class CdsExportDataSummaryRowsReader {
    /** The staging purpose of the member that holds one chart's rows while its plan is open. */
    static final String ROWS_PURPOSE = "DATA_SUMMARY_ROWS";

    /**
     * A planning value longer than this is indexed as its SHA-256. Planning compares link values
     * for equality only, and no identity, date, revision group or listing value is this long, so
     * the plan is the same; it keeps an index entry small whatever a link column holds.
     */
    static final int PLANNING_CHARACTERS = 512;

    private static final String PLANNING_DIGEST = '\0' + "sha256:";

    /**
     * One value must fit a Java array to be held while its row is written or rendered. MariaDB
     * cannot store or return a value over 1 GiB, so no chart reaches this.
     */
    static final long ARRAY_BYTES = Integer.MAX_VALUE - 8L;

    static final String SIZE_LIMIT = "CDS_EXPORT_DATA_SUMMARY_SIZE_LIMIT";

    /** Where a row lies in the rows member, and what it counts toward a record part. */
    private record Stored(long offset, long bytes, byte[] sha256, int fields, long text, long binary) {
        /** Two entries are equal when their places, counts and the bytes of their digests are. */
        @Override
        public boolean equals(Object other) {
            return other instanceof Stored entry
                    && offset == entry.offset
                    && bytes == entry.bytes
                    && fields == entry.fields
                    && text == entry.text
                    && binary == entry.binary
                    && java.util.Arrays.equals(sha256, entry.sha256);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(offset, bytes, fields, text, binary)
                    + java.util.Arrays.hashCode(sha256);
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryRowsReader.Stored[protected]";
        }
    }

    /**
     * The index of the captured rows of every data-summary table in the capture (each reduced to
     * its planning columns), the demographic row, and the member the whole rows are loaded from.
     * Closing it erases the member.
     */
    static final class Result implements AutoCloseable {
        private final Map<String, List<CdsExportDataSummaryPlanner.Row>> rows;
        private final Map<String, String> demographic;
        private final Map<String, Map<String, Stored>> stored;
        private final CdsExportStagingService store;
        private CdsExportStagedFileDto member;
        private CdsExportStagingService.VerifiedRanges ranges;
        private boolean closed;

        private Result(
                Map<String, List<CdsExportDataSummaryPlanner.Row>> rows,
                Map<String, String> demographic,
                Map<String, Map<String, Stored>> stored,
                CdsExportStagingService store,
                CdsExportStagedFileDto member) {
            var copy = new LinkedHashMap<String, List<CdsExportDataSummaryPlanner.Row>>();
            rows.forEach((table, values) -> copy.put(table, List.copyOf(values)));
            this.rows = java.util.Collections.unmodifiableMap(copy);
            this.demographic = java.util.Collections.unmodifiableMap(new HashMap<>(demographic));
            this.stored = stored;
            this.store = store;
            this.member = member;
        }

        /** The index entries of each table: every captured row, reduced to its planning columns. */
        Map<String, List<CdsExportDataSummaryPlanner.Row>> rows() {
            return rows;
        }

        Map<String, String> demographic() {
            return demographic;
        }

        /** What an indexed row counts toward a record part. */
        CdsExportDataSummaryPlanner.Weight weight(CdsExportDataSummaryPlanner.Row row) {
            var found = found(row);
            return new CdsExportDataSummaryPlanner.Weight(found.fields(), found.text(), found.binary());
        }

        /**
         * The whole captured row of an index entry, read from the rows member against its length
         * and SHA-256.
         */
        CdsExportDataSummaryPlanner.Row load(CdsExportDataSummaryPlanner.Row row) throws IOException {
            var found = found(row);
            if (closed || member == null) throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
            if (ranges == null) ranges = store.verifiedRanges(member);
            try (var input =
                    new DataInputStream(
                            ranges.open(
                                    found.offset(),
                                    found.bytes(),
                                    HexFormat.of().formatHex(found.sha256())))) {
                var read = decode(input);
                if (!read.table().equals(row.table())
                        || !read.recordKey().equals(row.recordKey())
                        || read.values().size() != found.fields()) invalid();
                return read;
            }
        }

        private Stored found(CdsExportDataSummaryPlanner.Row row) {
            var found = stored.getOrDefault(row.table(), Map.of()).get(row.recordKey());
            if (found == null) throw new IllegalStateException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
            return found;
        }

        /** Authenticates the member whole once more when rows were read from it, then erases it. */
        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            var open = ranges;
            ranges = null;
            var file = member;
            member = null;
            try {
                if (open != null) open.close();
            } finally {
                if (file != null) store.discardUnpublished(file);
            }
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryRowsReader.Result[protected]";
        }
    }

    private final CdsExportNativeCaptureService captures;

    CdsExportDataSummaryRowsReader(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
    }

    /** The registered data-summary tables of a capture scope. */
    static Set<String> tables(Set<String> captured) {
        var result = new TreeSet<String>();
        for (String table : captured)
            if (CdsExportDataSummaryRegistry.tables().contains(table)) result.add(table);
        return Set.copyOf(result);
    }

    /**
     * Reads the capture once.
     *
     * @param store where the whole rows are kept until the result is closed, or null when the
     *     caller needs the index alone (the ReadMe counts): the result then loads nothing
     */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    Result read(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportStagedFileDto manifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportStagingService store)
            throws IOException {
        var selected = new TreeSet<>(tables(requiredTables));
        if (!requiredTables.contains("demographic")) throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        selected.add("demographic");
        var fields = new Collector(Set.copyOf(selected));
        CdsExportStagedFileDto member = null;
        boolean complete = false;
        Throwable primary = null;
        try {
            if (store == null) scan(lease, patient, requiredTables, manifest, bounds, control, fields, selected);
            else
                member =
                        store.stage(
                                ROWS_PURPOSE,
                                output -> {
                                    fields.store(output);
                                    scan(
                                            lease,
                                            patient,
                                            requiredTables,
                                            manifest,
                                            bounds,
                                            control,
                                            fields,
                                            selected);
                                    fields.stored();
                                });
            captures.authorizeSources(lease, patient, requiredTables, control);
            var rows = new LinkedHashMap<String, List<CdsExportDataSummaryPlanner.Row>>();
            for (String table : tables(requiredTables))
                rows.put(table, fields.rows.getOrDefault(table, List.of()));
            var result = new Result(rows, fields.demographic, fields.stored, store, member);
            complete = true;
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!complete && member != null)
                try {
                    store.discardUnpublished(member);
                } catch (IOException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
        }
    }

    private void scan(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportStagedFileDto manifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Collector fields,
            Set<String> selected)
            throws IOException {
        var capture =
                captures.scan(
                        lease,
                        patient,
                        requiredTables,
                        manifest,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        fields);
        fields.finish();
        for (String table : selected) {
            long rows =
                    table.equals("demographic")
                            ? fields.demographicRows
                            : fields.rows.getOrDefault(table, List.of()).size();
            if (capture.tables().get(table) == null || capture.tables().get(table).rows() != rows)
                throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        }
        if (fields.demographicRows != 1) throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
    }

    /**
     * Collects the verified captured values of the selected tables one row at a time: the whole
     * row goes to the rows member (when one was given with {@link #store}) and its index entry
     * stays. The caller calls {@link #finish()} after the scan returns.
     */
    static final class Collector implements CdsExportNativeCaptureReader.Fields {
        private final Set<String> tables;
        private final Map<String, List<CdsExportDataSummaryPlanner.Row>> rows = new HashMap<>();
        private final Map<String, Map<String, Stored>> stored = new HashMap<>();
        private final Map<String, SortedSet<String>> planning = new HashMap<>();
        private final Map<String, String> demographic = new HashMap<>();
        private long demographicRows;
        private String table, key;
        private Map<String, CdsExportDataSummaryPlanner.Value> values = new TreeMap<>();
        private CdsExportCapturedFieldDto pending;
        private ByteArrayOutputStream buffer;
        private long text, binary;
        private OutputStream member;
        private long offset;

        Collector(Set<String> tables) {
            this.tables = Set.copyOf(tables);
        }

        /** The member every whole row is written to from now on. */
        void store(OutputStream output) {
            if (member != null || offset != 0)
                throw new IllegalStateException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
            member = Objects.requireNonNull(output);
        }

        /** The member is complete; nothing more is written to it. */
        void stored() throws IOException {
            if (table != null || pending != null) invalid();
            member = null;
        }

        /** The index entries of each table, in capture order. */
        Map<String, List<CdsExportDataSummaryPlanner.Row>> rows() {
            return rows;
        }

        Map<String, String> demographic() {
            return demographic;
        }

        /** A result over this collector's index and the member it wrote (null for the index alone). */
        Result result(CdsExportStagingService store, CdsExportStagedFileDto written) {
            return new Result(rows, demographic, stored, store, written);
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (!tables.contains(field.table())) return OutputStream.nullOutputStream();
            if (pending != null) invalid();
            if (!field.table().equals(table) || !field.recordKey().equals(key)) {
                finish();
                table = field.table();
                key = field.recordKey();
            }
            pending = field;
            buffer = null;
            switch (field.kind()) {
                case SQL_NULL -> {
                    return OutputStream.nullOutputStream();
                }
                case UTF8 -> {
                    // One value is held whole; no count or sum of values is refused.
                    if (field.bytes() > ARRAY_BYTES)
                        throw new CdsExportMappingReviewException(SIZE_LIMIT);
                    text += field.bytes();
                    buffer = new ByteArrayOutputStream((int) field.bytes());
                    return buffer;
                }
                case BINARY -> {
                    if (!retain(field)) return OutputStream.nullOutputStream();
                    binary += field.bytes();
                    buffer = new ByteArrayOutputStream((int) field.bytes());
                    return buffer;
                }
                default -> throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
            }
        }

        /** A binary value is kept only when its column's presentation needs its bytes. */
        private static boolean retain(CdsExportCapturedFieldDto field) {
            if (field.table().equals("demographic")) return false;
            var rule = CdsExportDataSummaryRegistry.rule(field.table());
            return rule.binaries().containsKey(field.column()) && field.bytes() <= ARRAY_BYTES;
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!tables.contains(field.table())) return;
            if (pending != field) invalid();
            CdsExportDataSummaryPlanner.Value value =
                    switch (field.kind()) {
                        case SQL_NULL -> CdsExportDataSummaryPlanner.Value.sqlNull();
                        case UTF8 ->
                                CdsExportDataSummaryPlanner.Value.text(
                                        StandardCharsets.UTF_8
                                                .newDecoder()
                                                .onMalformedInput(CodingErrorAction.REPORT)
                                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                                .decode(ByteBuffer.wrap(buffer.toByteArray()))
                                                .toString());
                        case BINARY ->
                                new CdsExportDataSummaryPlanner.Value(
                                        CdsExportDataSummaryPlanner.Storage.BINARY,
                                        null,
                                        field.bytes(),
                                        field.sha256(),
                                        buffer == null ? null : buffer.toByteArray());
                    };
            if (values.putIfAbsent(field.column(), value) != null) invalid();
            pending = null;
            buffer = null;
        }

        void finish() throws IOException {
            if (pending != null) invalid();
            if (table == null) return;
            if (table.equals("demographic")) {
                demographicRows++;
                values.forEach((column, value) -> demographic.put(column, value.text()));
            } else {
                var whole = new CdsExportDataSummaryPlanner.Row(table, key, values);
                long length = 0;
                byte[] hash = null;
                if (member != null) {
                    byte[] encoded = encode(whole);
                    member.write(encoded);
                    length = encoded.length;
                    hash = sha256().digest(encoded);
                    java.util.Arrays.fill(encoded, (byte) 0);
                }
                if (stored.computeIfAbsent(table, ignored -> new HashMap<>())
                                .putIfAbsent(
                                        key,
                                        new Stored(offset, length, hash, values.size(), text, binary))
                        != null) invalid();
                offset += length;
                rows.computeIfAbsent(table, ignored -> new ArrayList<>()).add(index(whole));
            }
            values = new TreeMap<>();
            text = 0;
            binary = 0;
            table = null;
            key = null;
        }

        /** The index entry of a whole row: its planning columns only. */
        private CdsExportDataSummaryPlanner.Row index(CdsExportDataSummaryPlanner.Row whole) {
            var columns =
                    planning.computeIfAbsent(whole.table(), CdsExportDataSummaryPlanner::planning);
            var kept = new TreeMap<String, CdsExportDataSummaryPlanner.Value>();
            // One digest for this row's planning values; it holds no state between rows.
            MessageDigest digest = sha256();
            for (String column : columns) {
                var value = whole.values().get(column);
                if (value == null) continue;
                kept.put(column, planningValue(value, digest));
            }
            return new CdsExportDataSummaryPlanner.Row(whole.table(), whole.recordKey(), kept);
        }

        private static void invalid() throws IOException {
            throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        }
    }

    /**
     * A value as planning needs it: text of at most {@link #PLANNING_CHARACTERS} characters as it
     * is, longer text as a NUL followed by its SHA-256 (no stored text begins so and equals a
     * digest by chance), and a binary value without its bytes.
     */
    static CdsExportDataSummaryPlanner.Value planningValue(
            CdsExportDataSummaryPlanner.Value value, MessageDigest digest) {
        return switch (value.storage()) {
            case SQL_NULL -> value;
            case TEXT ->
                    value.text().length() <= PLANNING_CHARACTERS
                            ? value
                            : CdsExportDataSummaryPlanner.Value.text(
                                    PLANNING_DIGEST
                                            + HexFormat.of()
                                                    .formatHex(
                                                            digest.digest(
                                                                    value.text()
                                                                            .getBytes(
                                                                                    StandardCharsets
                                                                                            .UTF_8))));
            case BINARY ->
                    value.content() == null
                            ? value
                            : new CdsExportDataSummaryPlanner.Value(
                                    CdsExportDataSummaryPlanner.Storage.BINARY,
                                    null,
                                    value.bytes(),
                                    value.sha256(),
                                    null);
        };
    }

    /**
     * One whole row as the rows member holds it: table, capture key, then each column in name
     * order with its storage and exact value.
     */
    static byte[] encode(CdsExportDataSummaryPlanner.Row row) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            block(out, row.table().getBytes(StandardCharsets.UTF_8));
            block(out, row.recordKey().getBytes(StandardCharsets.UTF_8));
            out.writeInt(row.values().size());
            for (var entry : row.values().entrySet()) {
                block(out, entry.getKey().getBytes(StandardCharsets.UTF_8));
                var value = entry.getValue();
                switch (value.storage()) {
                    case SQL_NULL -> out.writeByte(0);
                    case TEXT -> {
                        out.writeByte(1);
                        block(out, value.text().getBytes(StandardCharsets.UTF_8));
                    }
                    case BINARY -> {
                        out.writeByte(value.content() == null ? 2 : 3);
                        out.writeLong(value.bytes());
                        out.write(HexFormat.of().parseHex(value.sha256()));
                        if (value.content() != null) block(out, value.content());
                    }
                    default -> throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
                }
            }
        }
        return bytes.toByteArray();
    }

    static CdsExportDataSummaryPlanner.Row decode(DataInputStream input) throws IOException {
        String table = text(input);
        String key = text(input);
        int count = input.readInt();
        if (count < 0) invalid();
        var values = new TreeMap<String, CdsExportDataSummaryPlanner.Value>();
        for (int index = 0; index < count; index++) {
            String column = text(input);
            CdsExportDataSummaryPlanner.Value value;
            int storage = input.readByte();
            switch (storage) {
                case 0 -> value = CdsExportDataSummaryPlanner.Value.sqlNull();
                case 1 -> value = CdsExportDataSummaryPlanner.Value.text(text(input));
                case 2, 3 -> {
                    long bytes = input.readLong();
                    String sha256 = HexFormat.of().formatHex(input.readNBytes(32));
                    value =
                            new CdsExportDataSummaryPlanner.Value(
                                    CdsExportDataSummaryPlanner.Storage.BINARY,
                                    null,
                                    bytes,
                                    sha256,
                                    storage == 3 ? block(input) : null);
                }
                default -> {
                    invalid();
                    return null;
                }
            }
            if (values.putIfAbsent(column, value) != null) invalid();
        }
        if (input.read() != -1) invalid();
        return new CdsExportDataSummaryPlanner.Row(table, key, values);
    }

    private static void block(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static byte[] block(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) invalid();
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) invalid();
        return bytes;
    }

    private static String text(DataInputStream input) throws IOException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(block(input)))
                .toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("CDS_EXPORT_SHA256_UNAVAILABLE", impossible);
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
    }
}
