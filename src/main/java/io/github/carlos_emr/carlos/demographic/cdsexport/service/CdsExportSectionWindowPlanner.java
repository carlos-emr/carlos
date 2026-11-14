/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Plans the windows in which one section of a chart reads its captured rows, so that a section
 * holds the witnesses, lookups and rows of one window at a time and never those of the whole
 * chart, and no row count or size refuses the chart.
 *
 * <p>A section's rows have owners: the rows of its main table, in the order the capture holds
 * them, which is the order the section emits their occurrences. Every other row the section
 * reads (a precision row, an extension, a reason, a native-row witness) belongs to exactly one
 * owner. A window is a run of consecutive owners. The section loads what the owners of one window
 * need, emits their occurrences, drops what it loaded and goes on to the next window, so the
 * occurrences leave in the same order whatever the window size.
 *
 * <p>The first attempt is always the whole chart as one window, read exactly as a chart was read
 * before windows existed: no extra pass and the same output bytes. Only when the chart has more
 * owners than {@link Limits#rows} or the load reports that it passed {@link Limits#bytes} is the
 * chart read in several windows. Then one pass over the main table keeps each owner's id (its
 * characters and four bytes, see {@link Owners}), the only state of the section that grows with
 * the chart. A window whose load still passes the byte limit is abandoned before anything of it
 * is emitted and read again with fewer owners; a window of one owner is never abandoned, because
 * one owner's rows are needed together. A threshold here changes how a chart is read, never
 * whether.
 *
 * <p>The planner reads and writes nothing itself and keeps no state between calls. The section
 * keeps its own completeness checks: every row of a child table must be taken by the window of
 * its owner, which the section proves by counting what its windows took against the capture's
 * row count, since a row whose owner is in no window is taken by none.
 *
 * @since 2026-11-14
 */
final class CdsExportSectionWindowPlanner {
    /** Owners of the main table one window may hold, and the estimated bytes loaded for them. */
    record Limits(int rows, long bytes) {
        Limits {
            if (rows < 1 || bytes < 1)
                throw new IllegalArgumentException("CDS_EXPORT_SECTION_WINDOW_INVALID");
        }
    }

    /**
     * What one window's load has taken so far. Passing a limit never throws and never refuses the
     * chart: it marks the window as too large, the section stops keeping rows for it, and the
     * planner reads the same owners again in smaller windows. A window of one owner has no limit.
     */
    static final class Budget {
        /** Kinds of loaded state a section counts apart, each against {@link Limits#bytes}. */
        private static final int ACCOUNTS = 4;

        private final Limits limits;
        private final boolean enforced;
        private final long[] bytes = new long[ACCOUNTS];
        private long rows, fitted;
        private boolean exceeded;

        private Budget(Limits limits, boolean enforced) {
            this.limits = limits;
            this.enforced = enforced;
        }

        /** True once the load passed a limit; the section then keeps nothing more for the window. */
        boolean exceeded() {
            return exceeded;
        }

        /**
         * Counts one owner (a row or a witness of the main table) of {@code size} bytes.
         *
         * @return false when the window is too large and the owner must not be kept
         */
        boolean owner(long size) {
            if (exceeded) return false;
            rows = Math.addExact(rows, 1);
            bytes[0] = Math.addExact(bytes[0], Math.max(0, size));
            if (enforced && (rows > limits.rows() || bytes[0] > limits.bytes())) exceeded = true;
            else fitted = rows;
            return !exceeded;
        }

        /**
         * Counts {@code size} more bytes of one kind of state held for the window's owners
         * ({@code account} 0 is the owners' own, 1 to 3 are the section's to name).
         *
         * @return false when the window is too large and the state must not be kept
         */
        boolean add(int account, long size) {
            if (exceeded) return false;
            bytes[account] = Math.addExact(bytes[account], Math.max(0, size));
            if (enforced && bytes[account] > limits.bytes()) exceeded = true;
            return !exceeded;
        }

        /** Whether passing a limit abandons this window (false for a window of one owner). */
        boolean enforced() {
            return enforced;
        }
    }

    /** The owners one window holds. */
    static final class Window {
        private final Set<String> ids;
        private final long size;

        private Window(Set<String> ids, long size) {
            this.ids = ids;
            this.size = size;
        }

        /** True for the single window that holds every owner of the chart. */
        boolean whole() {
            return ids == null;
        }

        /** Whether the owner with this id belongs to this window. */
        boolean contains(String id) {
            return ids == null ? id != null : id != null && ids.contains(id);
        }

        /** How many owners the window holds. */
        long size() {
            return size;
        }

        @Override
        public String toString() {
            return "CdsExportSectionWindowPlanner.Window[protected]";
        }
    }

    /**
     * The ids of a main table's rows in capture order, kept only for a chart read in several
     * windows. Each id costs its UTF-8 bytes in one shared array plus a four-byte offset: 12
     * bytes for an eight-digit id, whatever the row itself holds.
     */
    static final class Owners {
        private byte[] text = new byte[1024];
        private int[] ends = new int[64];
        private int count, used;

        /** Appends the next owner in the order the capture holds it. */
        void add(String id) throws IOException {
            if (id == null || id.isEmpty()) throw new IOException("CDS_EXPORT_SECTION_WINDOW_INVALID");
            byte[] encoded = id.getBytes(StandardCharsets.UTF_8);
            if (encoded.length > text.length - used)
                text =
                        Arrays.copyOf(
                                text,
                                Math.toIntExact(
                                        Math.max(
                                                (long) text.length + (text.length >> 1),
                                                (long) used + encoded.length)));
            System.arraycopy(encoded, 0, text, used, encoded.length);
            used += encoded.length;
            if (count == ends.length) ends = Arrays.copyOf(ends, count + (count >> 1) + 1);
            ends[count++] = used;
        }

        long size() {
            return count;
        }

        private Window window(int from, int length) {
            var ids = new HashSet<String>(Math.max(16, length * 2));
            for (int index = from; index < from + length; index++) {
                int start = index == 0 ? 0 : ends[index - 1];
                ids.add(new String(text, start, ends[index] - start, StandardCharsets.UTF_8));
            }
            // A repeated id would let two rows share one window slot; the section's own
            // uniqueness check refuses the row, this keeps the window's size honest.
            return new Window(ids, ids.size());
        }

        @Override
        public String toString() {
            return "CdsExportSectionWindowPlanner.Owners[protected]";
        }
    }

    /** One authenticated pass over the main table that reports every owner's id in capture order. */
    @FunctionalInterface
    interface OwnerScan {
        void scan(Owners owners) throws IOException;
    }

    /**
     * Loads one window and, when the load fits, emits its occurrences. It returns {@code false}
     * only when the {@link Budget} was exceeded, and then it has emitted nothing and changed no
     * state it shares with other windows: the planner reads the same owners again.
     */
    @FunctionalInterface
    interface WindowWork {
        boolean run(Window window, Budget budget) throws IOException;
    }

    private CdsExportSectionWindowPlanner() {}

    /**
     * Runs {@code work} over every owner of a main table of {@code rows} rows: once for the whole
     * chart when that fits {@code limits}, otherwise window by window in capture order.
     *
     * @return the number of windows that completed (1 for a chart read whole)
     */
    static long forEachWindow(long rows, Limits limits, OwnerScan scan, WindowWork work)
            throws IOException {
        Objects.requireNonNull(limits);
        Objects.requireNonNull(scan);
        Objects.requireNonNull(work);
        if (rows < 0) throw new IOException("CDS_EXPORT_SECTION_WINDOW_INVALID");
        int size = limits.rows();
        if (rows <= limits.rows()) {
            var budget = new Budget(limits, rows > 1);
            if (work.run(new Window(null, rows), budget)) return 1;
            if (!budget.exceeded()) throw new IOException("CDS_EXPORT_SECTION_WINDOW_INVALID");
            size = smaller((int) rows, budget);
        }
        // The pass reports the owners the capture delivers. The section proves afterwards that
        // its windows emitted as many rows as the capture states, under its own code.
        var owners = new Owners();
        scan.scan(owners);
        long windows = 0;
        int from = 0;
        while (from < owners.count) {
            int length = Math.min(size, owners.count - from);
            var window = owners.window(from, length);
            // Two rows of one id in a window: the section refuses the chart when it reads them.
            var budget = new Budget(limits, length > 1);
            if (work.run(window, budget)) {
                from += length;
                windows++;
                continue;
            }
            if (!budget.exceeded() || length == 1)
                throw new IOException("CDS_EXPORT_SECTION_WINDOW_INVALID");
            size = smaller(length, budget);
        }
        return windows;
    }

    /**
     * The next window size after one of {@code length} owners was too large: as many owners as
     * were counted before the limit, when the load counted them, otherwise half. Always fewer
     * than before, so the loop ends at one owner.
     */
    private static int smaller(int length, Budget budget) {
        long hint = budget.fitted > 0 ? budget.fitted : length / 2;
        return (int) Math.max(1, Math.min(length - 1L, hint));
    }
}
