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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeRowReceiptDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeRowReceiptsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads one patient's captured care rows (measurements, their extensions, deleted versions, the
 * vocabulary and the demographic row) in bounded windows instead of all at once, so a chart with
 * any number of measurements is exported rather than held (issue #6200, CARE-5).
 *
 * <p>Every row of a measurement belongs to one owner: a current measurement's own id, a deleted
 * version's {@code originalId}, an extension's {@code measurement_id}. A first pass over the
 * authenticated capture validates every row and keeps only a compact index (a few longs per
 * row): row counts, unique ids, the extension ownership rules and each owner's weight. Owners
 * are then grouped, in the decimal (string) order of their ids that the exported occurrences
 * already follow, into windows of at most {@code observations} measurement rows and about
 * {@code bytes} of row memory; one owner's rows are never split. Each window is read again from
 * the capture and every row of it is checked against its native-row witness before use, so the
 * rows held at once never exceed one window plus the vocabulary and the demographic row.
 *
 * <p>When the whole chart fits in one window (every chart up to the old per-patient bound) the
 * first pass keeps the rows and one witness pass verifies them: the same two scans as before and
 * the same rows, so the output is byte-identical. Nothing here writes or holds output; callers
 * keep their derived output private until the whole patient succeeds.
 */
final class CdsExportCapturedCareRowsReader {
    static final Set<String> TABLES =
            Set.of(
                    "measurements",
                    "measurementsExt",
                    "measurementsDeleted",
                    "measurementType",
                    "demographic");

    /** Rows of the vocabulary and the patient, held in every window. */
    private static final Set<String> FIXED = Set.of("measurementType", "demographic");

    /** One authenticated pass over the captured care tables. */
    @FunctionalInterface
    interface RowScan {
        CdsExportNativeCaptureDto scan(CdsExportCapturedTextRowsReader.Rows rows) throws IOException;
    }

    /** One authenticated pass over the capture's native-row witnesses. */
    @FunctionalInterface
    interface WitnessScan {
        CdsExportNativeRowReceiptsDto scan(CdsExportNativeRowReceiptReader.Receipts receipts)
                throws IOException;
    }

    /**
     * The rows one window may hold: measurement rows (current and deleted versions) and the
     * estimated memory of all its rows, the vocabulary and the demographic row included.
     */
    record Limits(int observations, long bytes) {
        Limits {
            if (observations < 1 || bytes < 2)
                throw new IllegalArgumentException("CDS_EXPORT_CARE_WINDOW_LIMITS_INVALID");
        }
    }

    /** Verified rows keyed {@code table:id}, each with the witness it was checked against. */
    record Slice(
            SortedMap<String, CdsExportCapturedTextRowDto> current,
            Map<String, CdsExportNativeRowReceiptDto> exact) {
        Slice {
            current = Collections.unmodifiableSortedMap(current);
            exact = Collections.unmodifiableMap(exact);
        }

        @Override
        public String toString() {
            return "CdsExportCapturedCareRowsReader.Slice[protected]";
        }
    }

    /** One window: the owners from {@code first} to {@code last} in decimal order. */
    private record Window(long first, long last, long rows) {}

    private final int patient;
    private final RowScan rows;
    private final WitnessScan witnesses;
    private final CdsExportNativeCaptureDto expected;
    private final String manifest;
    private final Runnable checkpoint;

    CdsExportCapturedCareRowsReader(
            int patient,
            RowScan rows,
            WitnessScan witnesses,
            CdsExportNativeCaptureDto expected,
            String manifest,
            Runnable checkpoint) {
        if (patient < 1) throw new IllegalArgumentException("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
        this.patient = patient;
        this.rows = Objects.requireNonNull(rows);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.expected = Objects.requireNonNull(expected);
        this.manifest = Objects.requireNonNull(manifest);
        this.checkpoint = Objects.requireNonNull(checkpoint);
    }

    /**
     * The first pass: validates every captured care row and plans the windows. Owner rules that
     * concern the whole chart (an extension owned by no observation, or by a current and a
     * deleted one) are decided here, before any window is read or any occurrence emitted.
     */
    Capture capture(Limits limits) throws IOException {
        Objects.requireNonNull(limits);
        var fixed = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var retained = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var owners = new Longs();
        var sizes = new Longs();
        var observed = new BitSet();
        var live = new Longs();
        var deletedOwners = new Longs();
        var deletedIds = new Longs();
        var extensionOwners = new Longs();
        var extensionIds = new Longs();
        var counts = new HashMap<String, Long>();
        long[] used = {0, 0, 0}; // fixed bytes, retained observations, retained bytes
        boolean[] retaining = {true};
        var scanned =
                rows.scan(
                        row -> {
                            if ((owners.size & 4095) == 0) checkpoint.run();
                            if (!TABLES.contains(row.table())) invalid();
                            String id = id(row);
                            requireShape(row);
                            counts.merge(row.table(), 1L, Math::addExact);
                            long size = size(row);
                            String key = key(row.table(), id);
                            if (FIXED.contains(row.table())) {
                                if (fixed.putIfAbsent(key, row) != null) invalid();
                                used[0] = Math.addExact(used[0], size);
                                // The vocabulary is the clinic's, captured for the types this
                                // chart uses; it is held in every window.
                                if (used[0] > limits.bytes() / 2) limit();
                                return;
                            }
                            long owner = owner(row);
                            boolean observation = !row.table().equals("measurementsExt");
                            switch (row.table()) {
                                case "measurements" -> live.add(owner);
                                case "measurementsDeleted" -> {
                                    deletedOwners.add(owner);
                                    deletedIds.add(Long.parseLong(id));
                                }
                                default -> {
                                    extensionOwners.add(owner);
                                    extensionIds.add(Long.parseLong(id));
                                }
                            }
                            if (observation) observed.set(owners.size);
                            owners.add(owner);
                            sizes.add(size);
                            if (!retaining[0]) return;
                            if (observation) used[1]++;
                            used[2] = Math.addExact(used[2], size);
                            if (used[1] > limits.observations() || used[2] > limits.bytes()) {
                                retaining[0] = false;
                                retained.clear();
                            } else if (retained.putIfAbsent(key, row) != null) invalid();
                        });
        if (!scanned.equals(expected)) invalid();
        for (String table : TABLES) {
            var captured = expected.tables().get(table);
            if (captured == null || counts.getOrDefault(table, 0L) != captured.rows()) invalid();
        }
        if (!fixed.containsKey(key("demographic", Integer.toString(patient)))
                || expected.tables().get("demographic").rows() != 1) invalid();
        long[] current = live.sorted();
        unique(current);
        unique(deletedIds.sorted());
        unique(extensionIds.sorted());
        requireOwners(current, deletedOwners.sorted(), extensionOwners);
        var windows = plan(owners, sizes, observed, limits.bytes() - used[0], limits.observations());
        if (retaining[0] && used[2] + used[0] <= limits.bytes()) {
            retained.putAll(fixed);
            return new Capture(fixed, windows, slice(retained));
        }
        return new Capture(fixed, windows, null);
    }

    /** The planned windows and, when the chart fits in one, its verified rows. */
    final class Capture {
        private final SortedMap<String, CdsExportCapturedTextRowDto> fixed;
        private final List<Window> windows;
        private final Slice whole;

        private Capture(
                SortedMap<String, CdsExportCapturedTextRowDto> fixed,
                List<Window> windows,
                Slice whole) {
            this.fixed = Collections.unmodifiableSortedMap(fixed);
            this.windows = List.copyOf(windows);
            this.whole = whole;
        }

        /** The number of windows; a chart held in one window has exactly one. */
        int windows() {
            return whole != null ? 1 : windows.size();
        }

        /** True when the chart is read again window by window. */
        boolean windowed() {
            return whole == null;
        }

        /** The vocabulary and demographic rows every window holds, as the first pass read them. */
        SortedMap<String, CdsExportCapturedTextRowDto> vocabulary() {
            return fixed;
        }

        /**
         * The verified rows of one window, in the order the windows are planned. Each owner's
         * observation, deleted versions and extensions are in the same window, with the
         * vocabulary and the demographic row.
         */
        Slice window(int index) throws IOException {
            if (whole != null) {
                if (index != 0) invalid();
                return whole;
            }
            var window = windows.get(index);
            checkpoint.run();
            return load(
                    fixed,
                    row -> {
                        long owner = owner(row);
                        return lexical(owner, window.first()) >= 0
                                && lexical(owner, window.last()) <= 0;
                    },
                    window.rows());
        }

        /**
         * The verified rows an imported care value can name: the observations with these ids,
         * the deleted versions of them, their extensions and the extensions named by id, with
         * the vocabulary and the demographic row. It is as large as the patient's retained
         * import index, which bounds it.
         */
        Slice owners(Set<String> observations, Set<String> extensions) throws IOException {
            if (whole != null) return whole;
            var ids = Set.copyOf(observations);
            var named = Set.copyOf(extensions);
            checkpoint.run();
            return load(
                    fixed,
                    row ->
                            switch (row.table()) {
                                case "measurements" -> ids.contains(row.fields().get("id"));
                                case "measurementsDeleted" ->
                                        ids.contains(row.fields().get("originalId"));
                                default ->
                                        ids.contains(row.fields().get("measurement_id"))
                                                || named.contains(row.fields().get("id"));
                            },
                    -1);
        }

        @Override
        public String toString() {
            return "CdsExportCapturedCareRowsReader.Capture[protected]";
        }
    }

    @FunctionalInterface
    private interface Member {
        boolean test(CdsExportCapturedTextRowDto row) throws IOException;
    }

    private Slice load(
            SortedMap<String, CdsExportCapturedTextRowDto> fixed, Member member, long count)
            throws IOException {
        var current = new TreeMap<String, CdsExportCapturedTextRowDto>(fixed);
        long[] loaded = {0};
        var scanned =
                rows.scan(
                        row -> {
                            if (!TABLES.contains(row.table())) invalid();
                            // The vocabulary and the patient row were kept by the first pass;
                            // they are verified again with every window below.
                            if (FIXED.contains(row.table()) || !member.test(row)) return;
                            String id = id(row);
                            requireShape(row);
                            if (current.putIfAbsent(key(row.table(), id), row) != null) invalid();
                            loaded[0]++;
                        });
        if (!scanned.equals(expected) || count >= 0 && loaded[0] != count) invalid();
        return slice(current);
    }

    /** Every row of the slice against its witness; the witnesses of other rows are skipped. */
    private Slice slice(SortedMap<String, CdsExportCapturedTextRowDto> current)
            throws IOException {
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
        var proof =
                witnesses.scan(
                        row -> {
                            if (!TABLES.contains(row.table())) return;
                            String key = key(row.table(), row.id());
                            if (!current.containsKey(key)) return;
                            if (exact.putIfAbsent(key, row) != null) invalid();
                        });
        if (!proof.sourceManifestSha256().equals(manifest)
                || !current.keySet().equals(exact.keySet())) invalid();
        for (var entry : current.entrySet()) {
            checkpoint.run();
            verify(entry.getValue(), exact.get(entry.getKey()));
        }
        return new Slice(current, exact);
    }

    /**
     * Groups owners, in decimal order, into windows of at most {@code observations} measurement
     * rows and {@code bytes} of rows. One owner is never split: an observation with very large
     * extensions takes a window of its own.
     */
    private static List<Window> plan(
            Longs owners, Longs sizes, BitSet observed, long bytes, int observations) {
        long[] distinct = owners.sorted();
        int unique = 0;
        for (int i = 0; i < distinct.length; i++)
            if (i == 0 || distinct[i] != distinct[i - 1]) distinct[unique++] = distinct[i];
        distinct = Arrays.copyOf(distinct, unique);
        int[] counted = new int[unique];
        long[] rowsOf = new long[unique], bytesOf = new long[unique];
        for (int i = 0; i < owners.size; i++) {
            int at = Arrays.binarySearch(distinct, owners.values[i]);
            if (observed.get(i)) counted[at]++;
            rowsOf[at]++;
            bytesOf[at] = Math.addExact(bytesOf[at], sizes.values[i]);
        }
        var order = new Long[unique];
        for (int i = 0; i < unique; i++) order[i] = distinct[i];
        Arrays.sort(order, CdsExportCapturedCareRowsReader::lexical);
        var windows = new ArrayList<Window>();
        long first = 0, last = 0, rowsIn = 0, bytesIn = 0;
        int counts = 0;
        for (Long owner : order) {
            int at = Arrays.binarySearch(distinct, owner);
            if (rowsIn > 0
                    && (counts + counted[at] > observations || bytesIn + bytesOf[at] > bytes)) {
                windows.add(new Window(first, last, rowsIn));
                rowsIn = 0;
                bytesIn = 0;
                counts = 0;
            }
            if (rowsIn == 0) first = owner;
            last = owner;
            rowsIn += rowsOf[at];
            bytesIn = Math.addExact(bytesIn, bytesOf[at]);
            counts += counted[at];
        }
        if (rowsIn > 0) windows.add(new Window(first, last, rowsIn));
        return windows;
    }

    /**
     * Every extension belongs to exactly one of this patient's observations: a current
     * measurement, or the one measurementsDeleted row whose originalId it still carries.
     */
    private static void requireOwners(long[] live, long[] deleted, Longs extensions)
            throws IOException {
        for (int i = 0; i < extensions.size; i++) {
            long owner = extensions.values[i];
            boolean current = Arrays.binarySearch(live, owner) >= 0;
            int at = Arrays.binarySearch(deleted, owner), versions = 0;
            if (at >= 0) {
                int low = at, high = at;
                while (low > 0 && deleted[low - 1] == owner) low--;
                while (high + 1 < deleted.length && deleted[high + 1] == owner) high++;
                versions = high - low + 1;
            }
            if (!current && versions == 0) invalid();
            if (current && versions > 0 || versions > 1)
                throw new CdsExportMappingReviewException(
                        "CDS_EXPORT_NATIVE_CARE_EXTENSION_OWNER_AMBIGUOUS");
        }
    }

    private static void unique(long[] sorted) throws IOException {
        for (int i = 1; i < sorted.length; i++) if (sorted[i] == sorted[i - 1]) invalid();
    }

    /** The owner of one measurement, deleted version or extension row. */
    private long owner(CdsExportCapturedTextRowDto row) throws IOException {
        String value =
                switch (row.table()) {
                    case "measurements" -> row.fields().get("id");
                    case "measurementsDeleted" -> row.fields().get("originalId");
                    case "measurementsExt" -> row.fields().get("measurement_id");
                    default -> null;
                };
        if (!row.table().equals("measurementsExt")
                && !Integer.toString(patient).equals(row.fields().get("demographicNo"))) invalid();
        // Only an exact decimal id can name an observation; anything else names none.
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) invalid();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            invalid();
            return 0;
        }
    }

    /**
     * The order of decimal ids as strings (the order of the exported occurrences), without
     * building strings: compare the leading digits the two share, then the shorter first.
     */
    static int lexical(long left, long right) {
        if (left == right) return 0;
        int a = digits(left), b = digits(right);
        long x = left, y = right;
        if (a > b) x = left / POWERS[a - b];
        else if (b > a) y = right / POWERS[b - a];
        if (x != y) return Long.compare(x, y);
        return Integer.compare(a, b);
    }

    private static final long[] POWERS = new long[19];

    static {
        POWERS[0] = 1;
        for (int i = 1; i < POWERS.length; i++) POWERS[i] = POWERS[i - 1] * 10;
    }

    private static int digits(long value) {
        if (value < 1) throw new IllegalArgumentException("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
        int digits = 1;
        while (digits < 19 && value >= POWERS[digits]) digits++;
        return digits;
    }

    /** The memory one captured row takes while it is held, as the old per-chart budget counted. */
    static long size(CdsExportCapturedTextRowDto row) {
        long used = 1024L + row.fields().size() * 256L;
        for (String value : row.fields().values())
            if (value != null)
                used = Math.addExact(used, value.getBytes(StandardCharsets.UTF_8).length + 64L);
        return used;
    }

    private static void requireShape(CdsExportCapturedTextRowDto row) throws IOException {
        if (!row.fields().keySet().equals(columns(row.table())) || !row.exactNumbers().isEmpty())
            invalid();
    }

    private static Set<String> columns(String table) throws IOException {
        if (table.equals("demographic")) {
            var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
            columns.add("demographic_no");
            return Set.copyOf(columns);
        }
        return Set.copyOf(
                switch (table) {
                    case "measurements" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_CURRENT.columns();
                    case "measurementsExt" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_EXT.columns();
                    case "measurementType" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENT_TYPES.columns();
                    case "measurementsDeleted" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED.columns();
                    default -> throw new IOException("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
                });
    }

    /** The row's id, which must be canonical and agree with its record key. */
    static String id(CdsExportCapturedTextRowDto row) throws IOException {
        String value =
                row.fields().get(row.table().equals("demographic") ? "demographic_no" : "id");
        try {
            long id = Long.parseLong(value);
            if (id < 1
                    || !Long.toString(id).equals(value)
                    || !(row.table().equals("demographic")
                                    ? String.format(Locale.ROOT, "%010d", id)
                                    : CdsExportPatientTableSourceReader.key(id))
                            .equals(row.recordKey())) invalid();
        } catch (NumberFormatException malformed) {
            invalid();
        }
        return value;
    }

    /** The captured row is exactly the projection its native-row witness digested. */
    static void verify(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness)
            throws IOException {
        if (witness == null
                || witness.state() == null
                || witness.partial()
                || !Objects.equals(witness.digest(), witness.state().digest())
                || !row.table().equals(witness.table())
                || !id(row).equals(witness.id())
                || !row.recordKey().equals(witness.recordKey())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) invalid();
        for (int i = 0; i < witness.state().columns().size(); i++) {
            var column = witness.state().columns().get(i);
            var cell = witness.state().cells().get(i);
            String value = row.fields().get(column.name());
            if (cell.exactNumeric() != null || CdsExactNumeric.codec(column.type()) != null)
                invalid();
            if (value == null) {
                if (!cell.sqlNull()) invalid();
            } else {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                if (cell.sqlNull()
                        || bytes.length != cell.bytes()
                        || !HexFormat.of()
                                .formatHex(CdsImportKeys.digest().digest(bytes))
                                .equals(cell.sha256())) invalid();
            }
        }
    }

    static String key(String table, String id) {
        return table + ":" + id;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }

    /** A growable array of longs: eight bytes a row instead of a row object. */
    private static final class Longs {
        private long[] values = new long[64];
        private int size;

        void add(long value) {
            if (size == values.length) values = Arrays.copyOf(values, Math.multiplyExact(size, 2));
            values[size++] = value;
        }

        long[] sorted() {
            long[] copy = Arrays.copyOf(values, size);
            Arrays.sort(copy);
            return copy;
        }
    }
}
