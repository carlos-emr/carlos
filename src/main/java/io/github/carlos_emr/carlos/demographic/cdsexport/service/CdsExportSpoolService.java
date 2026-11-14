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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * One attempt's protected record spool: what the packaging step used to keep in memory for every
 * chart of the roster is appended here instead, in encrypted staging members of the attempt's own
 * store, and read back in order or through an external merge sort.
 *
 * <p>Memory is bounded whatever the roster: one batch of at most {@link #BATCH_BYTES} of records
 * waiting to be written, one record per open cursor, {@link #FAN_IN} heads during a merge, and a
 * list of segment descriptors that never exceeds {@code FAN_IN} per level (a full level is
 * combined into one segment of the next, so the list grows with the logarithm of the record
 * count). No count or size refuses an append: the only failure is a genuine storage failure of the
 * staging store, which carries that store's own code.
 *
 * <p>A record is a key and an opaque value. Keys order a {@link #sorted} spool by {@link
 * String#compareTo}, the same order the in-memory maps this replaces used; equal keys keep their
 * append order. Segments are intermediates of the packaging step: {@link #close} erases every one
 * of them. One thread owns a spool. This is storage, not clinical or disclosure evidence.
 */
public final class CdsExportSpoolService implements AutoCloseable {
    /** The largest batch of records held in memory before it is written to protected storage. */
    static final int BATCH_BYTES = 4 << 20;

    /** Segments combined at once, and the most segment descriptors kept per level. */
    static final int FAN_IN = 32;

    private static final byte[] MAGIC = "CDSSPL01".getBytes(StandardCharsets.US_ASCII);

    /** One spooled record. The value is the caller's own encoding; the spool never interprets it. */
    public record Entry(String key, byte[] value) {
        @Override
        public String toString() {
            return "CdsExportSpoolService.Entry[protected]";
        }
    }

    /** A forward read; {@code null} is the authenticated end of the spool. */
    public interface Cursor extends AutoCloseable {
        Entry next() throws IOException;

        @Override
        void close() throws IOException;
    }

    private record Segment(CdsExportStagedFileDto file, long entries) {}

    private record Head(Reader reader, Entry entry, int order) {}

    private final CdsExportStagingService store;
    private final String purpose;
    private final Runnable checkpoint;
    private final int batchBytes, fanIn;
    private final boolean ordered;
    private final Thread owner = Thread.currentThread();

    /** Oldest level last: level 0 holds the newest segments, each list oldest first. */
    private final List<List<Segment>> levels = new ArrayList<>();

    private final List<Entry> batch = new ArrayList<>();
    private long batchUsed, records, segmentsWritten;
    private boolean closed, failed;

    /** An append-ordered spool whose segments carry {@code purpose} in the attempt's store. */
    public CdsExportSpoolService(CdsExportStagingService store, String purpose, Runnable checkpoint) {
        this(store, purpose, checkpoint, BATCH_BYTES, FAN_IN, false);
    }

    /** Small batches and fan-in let a test drive many levels with little data. */
    CdsExportSpoolService(
            CdsExportStagingService store,
            String purpose,
            Runnable checkpoint,
            int batchBytes,
            int fanIn) {
        this(store, purpose, checkpoint, batchBytes, fanIn, false);
    }

    private CdsExportSpoolService(
            CdsExportStagingService store,
            String purpose,
            Runnable checkpoint,
            int batchBytes,
            int fanIn,
            boolean ordered) {
        this.store = Objects.requireNonNull(store);
        this.checkpoint = Objects.requireNonNull(checkpoint);
        if (purpose == null
                || !purpose.matches("[A-Z][A-Z0-9_]{0,63}")
                || batchBytes < 1
                || fanIn < 2) throw new IllegalArgumentException("CDS_EXPORT_SPOOL_SCOPE_INVALID");
        this.purpose = purpose;
        this.batchBytes = batchBytes;
        this.fanIn = fanIn;
        this.ordered = ordered;
    }

    /** Appends one record; nothing is refused for how many or how large the records are. */
    public void append(String key, byte[] value) throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        usable();
        try {
            checkpoint.run();
            long size = 64L + 2L * key.length() + value.length;
            if (!batch.isEmpty() && size > batchBytes - batchUsed) flush();
            batch.add(new Entry(key, value));
            batchUsed += size;
            records++;
        } catch (IOException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
    }

    /** Records appended so far. */
    public long records() {
        return records;
    }

    /** Protected segments written so far, including those since combined; proof of spilling. */
    long segmentsWritten() {
        return segmentsWritten;
    }

    /** Segment descriptors currently held in memory. */
    int segmentsHeld() {
        int held = 0;
        for (var level : levels) held += level.size();
        return held;
    }

    /**
     * Reads every record back: in append order, or in key order for a {@link #sorted} spool. The
     * pending batch is written first, so a cursor reads protected storage only.
     */
    public Cursor open() throws IOException {
        usable();
        try {
            flush();
            if (ordered) {
                var runs = runs();
                if (runs.size() > 1) throw new IllegalStateException("CDS_EXPORT_SPOOL_NOT_SORTED");
                return new Sequence(runs);
            }
            return new Sequence(runs());
        } catch (IOException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
    }

    /**
     * A new spool holding the same records in key order ({@link String#compareTo}; equal keys keep
     * their order here). The caller owns and closes both spools.
     */
    public CdsExportSpoolService sorted(String sortedPurpose) throws IOException {
        usable();
        var result =
                new CdsExportSpoolService(store, sortedPurpose, checkpoint, batchBytes, fanIn, true);
        boolean complete = false;
        try {
            try (var cursor = open()) {
                Entry entry;
                while ((entry = cursor.next()) != null) result.append(entry.key(), entry.value());
            }
            result.flush();
            // Whatever is left on the levels is combined until one run holds every record.
            var runs = result.runs();
            while (runs.size() > 1) {
                // Both lists stay on the levels so a failure still erases every segment.
                var remaining = new ArrayList<>(runs);
                var next = new ArrayList<Segment>();
                result.levels.clear();
                result.levels.add(next);
                result.levels.add(remaining);
                while (!remaining.isEmpty()) {
                    var head = remaining.subList(0, Math.min(remaining.size(), fanIn));
                    var group = List.copyOf(head);
                    next.add(group.size() == 1 ? group.getFirst() : result.combine(group));
                    head.clear();
                }
                runs = next;
            }
            result.levels.clear();
            result.levels.add(new ArrayList<>(runs));
            if (result.records != records) invalid();
            complete = true;
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        } finally {
            if (!complete) result.closeQuietly();
        }
    }

    /** Erases every segment of this spool. Cursors must already be closed. */
    // Cleanup only throws without a primary failure; every segment is still attempted.
    @SuppressWarnings("java:S1143")
    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        batch.clear();
        Throwable primary = null;
        for (var level : levels)
            for (var segment : level)
                try {
                    store.discardUnpublished(segment.file());
                } catch (IOException | RuntimeException failure) {
                    if (primary == null) primary = failure;
                    else primary.addSuppressed(failure);
                }
        levels.clear();
        if (primary instanceof IOException io) throw io;
        if (primary instanceof RuntimeException unchecked) throw unchecked;
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException | RuntimeException cleanup) {
            // The primary failure is what the caller reports; the attempt's purge removes the rest.
        }
    }

    private void usable() {
        if (closed || failed || Thread.currentThread() != owner)
            throw new IllegalStateException("CDS_EXPORT_SPOOL_SCOPE_INVALID");
    }

    /** Oldest record first: the highest level is the oldest, and each level is oldest first. */
    private List<Segment> runs() {
        var result = new ArrayList<Segment>();
        for (var level : levels.reversed()) result.addAll(level);
        return result;
    }

    private void flush() throws IOException {
        if (batch.isEmpty()) return;
        if (ordered) batch.sort((left, right) -> left.key().compareTo(right.key()));
        var entries = List.copyOf(batch);
        var file =
                store.stage(
                        purpose,
                        output -> {
                            var data = new DataOutputStream(output);
                            data.write(MAGIC);
                            for (var entry : entries) {
                                checkpoint.run();
                                write(data, entry);
                            }
                            data.writeInt(-1);
                            data.writeLong(entries.size());
                            data.flush();
                        });
        segmentsWritten++;
        batch.clear();
        batchUsed = 0;
        add(0, new Segment(file, entries.size()));
    }

    /** A full level becomes one segment of the next, so no level ever holds more than the fan-in. */
    private void add(int level, Segment segment) throws IOException {
        while (levels.size() <= level) levels.add(new ArrayList<>());
        var held = levels.get(level);
        held.add(segment);
        if (held.size() < fanIn) return;
        var group = List.copyOf(held);
        held.clear();
        add(level + 1, combine(group));
    }

    /**
     * Writes one segment holding the group's records and erases the group: a concatenation for an
     * append-ordered spool, a stable merge by key for a sorted one.
     */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    private Segment combine(List<Segment> group) throws IOException {
        long expected = 0;
        for (var segment : group) expected = Math.addExact(expected, segment.entries());
        long total = expected;
        var file =
                store.stage(
                        purpose,
                        output -> {
                            var data = new DataOutputStream(output);
                            data.write(MAGIC);
                            long count = ordered ? merge(group, data) : concatenate(group, data);
                            if (count != total) invalid();
                            data.writeInt(-1);
                            data.writeLong(total);
                            data.flush();
                        });
        segmentsWritten++;
        Throwable primary = null;
        for (var segment : group)
            try {
                store.discardUnpublished(segment.file());
            } catch (IOException | RuntimeException failure) {
                if (primary == null) primary = failure;
                else primary.addSuppressed(failure);
            }
        if (primary != null) {
            try {
                store.discardUnpublished(file);
            } catch (IOException | RuntimeException cleanup) {
                primary.addSuppressed(cleanup);
            }
            if (primary instanceof IOException io) throw io;
            throw (RuntimeException) primary;
        }
        return new Segment(file, total);
    }

    private long concatenate(List<Segment> group, DataOutputStream data) throws IOException {
        long count = 0;
        for (var segment : group)
            try (var reader = new Reader(segment)) {
                Entry entry;
                while ((entry = reader.next()) != null) {
                    write(data, entry);
                    count++;
                }
            }
        return count;
    }

    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    private long merge(List<Segment> group, DataOutputStream data) throws IOException {
        var readers = new ArrayList<Reader>();
        Throwable primary = null;
        try {
            var queue =
                    new PriorityQueue<Head>(
                            (left, right) -> {
                                int order = left.entry().key().compareTo(right.entry().key());
                                return order != 0
                                        ? order
                                        : Integer.compare(left.order(), right.order());
                            });
            for (int index = 0; index < group.size(); index++) {
                var reader = new Reader(group.get(index));
                readers.add(reader);
                var entry = reader.next();
                if (entry != null) queue.add(new Head(reader, entry, index));
            }
            long count = 0;
            while (!queue.isEmpty()) {
                var head = queue.remove();
                write(data, head.entry());
                count++;
                var next = head.reader().next();
                if (next != null) {
                    if (next.key().compareTo(head.entry().key()) < 0) invalid();
                    queue.add(new Head(head.reader(), next, head.order()));
                }
            }
            return count;
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            for (var reader : readers.reversed())
                try {
                    reader.close();
                } catch (IOException | RuntimeException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
        }
    }

    private static void write(DataOutputStream data, Entry entry) throws IOException {
        byte[] key = entry.key().getBytes(StandardCharsets.UTF_8);
        data.writeInt(key.length);
        data.write(key);
        data.writeInt(entry.value().length);
        data.write(entry.value());
    }

    /** One segment, read to its authenticated end even when closed early. */
    private final class Reader implements AutoCloseable {
        private final Segment segment;
        private final DataInputStream input;
        private long count;
        private boolean eof, broken;

        Reader(Segment segment) throws IOException {
            this.segment = segment;
            checkpoint.run();
            input = new DataInputStream(store.open(segment.file()));
            try {
                if (!MessageDigest.isEqual(input.readNBytes(MAGIC.length), MAGIC)) invalid();
            } catch (IOException | RuntimeException | Error failure) {
                try {
                    input.close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        Entry next() throws IOException {
            if (broken) invalid();
            if (eof) return null;
            try {
                checkpoint.run();
                int keyBytes = input.readInt();
                if (keyBytes == -1) {
                    if (input.readLong() != segment.entries()
                            || count != segment.entries()
                            || input.read() != -1) invalid();
                    eof = true;
                    return null;
                }
                if (keyBytes < 0 || ++count > segment.entries()) invalid();
                byte[] key = input.readNBytes(keyBytes);
                if (key.length != keyBytes) invalid();
                int valueBytes = input.readInt();
                if (valueBytes < 0) invalid();
                byte[] value = input.readNBytes(valueBytes);
                if (value.length != valueBytes) invalid();
                return new Entry(
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(key))
                                .toString(),
                        value);
            } catch (IOException | RuntimeException | Error failure) {
                broken = true;
                throw failure;
            }
        }

        // Cleanup only throws without a primary failure; otherwise its error is suppressed on
        // that failure.
        @SuppressWarnings("java:S1143")
        @Override
        public void close() throws IOException {
            Throwable primary = null;
            try {
                // A partial read attests nothing: finish to the authenticated end.
                if (!broken) while (next() != null) checkpoint.run();
            } catch (IOException | RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                try {
                    input.close();
                } catch (IOException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }

    private final class Sequence implements Cursor {
        private final List<Segment> segments;
        private int index;
        private Reader reader;
        private long count;
        private boolean done;

        Sequence(List<Segment> segments) {
            this.segments = List.copyOf(segments);
        }

        @Override
        public Entry next() throws IOException {
            usable();
            while (!done) {
                if (reader == null) {
                    if (index == segments.size()) {
                        if (count != records) invalid();
                        done = true;
                        return null;
                    }
                    reader = new Reader(segments.get(index++));
                }
                var entry = reader.next();
                if (entry != null) {
                    count++;
                    return entry;
                }
                var finished = reader;
                reader = null;
                finished.close();
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            var current = reader;
            reader = null;
            done = true;
            if (current != null) current.close();
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_SPOOL_INVALID");
    }

    @Override
    public String toString() {
        return "CdsExportSpoolService[protected]";
    }
}
