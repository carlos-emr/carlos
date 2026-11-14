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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The capture transports of one patient that have already been read back completely and checked,
 * kept for the rest of that patient's export so that each later read of the same members replays
 * the checked fields instead of decrypting, parsing and hashing them again.
 *
 * <p>Every reader of a chart (the inventory, each section, each witness and receipt check) reads
 * the patient's whole sealed capture: dozens of complete reads per chart, each one decrypting the
 * members, parsing every index line and hashing every value. The members are written once, by
 * this worker, under the job's key, and never change. A read that has finished every check of
 * {@link CdsExportNativeCaptureReader#scan} (authenticated chunks, both end-of-stream checks,
 * strict UTF-8, each value's SHA-256, sequence, offsets and counts) is recorded here; a later read
 * of the identical members and plan gets the same fields, in the same order, with the same bytes.
 * Only the repeated transport work is skipped. Everything a read does around the transport still
 * runs on every read: the manifest and scope checks, the schema contract, the authorization checks
 * before and after, the note-scope binding and every consumer's own receipt and witness checks.
 *
 * <p>The record belongs to one patient and one worker thread and lives only inside that patient's
 * authorization session ({@link io.github.carlos_emr.carlos.demographic.cdsexport.engine
 * .CdsExportRunControl#verifiedCaptures}). It is never shared with another patient or job, and
 * {@link #close()} zeroes every byte it holds when the patient ends, however it ends. It holds at
 * most {@link #MAXIMUM_BYTES} bytes of heap, counting the value bytes and the metadata of every
 * field and row. A capture that does not fit is not held: its first complete read also writes the
 * verified fields to a staged replay member ({@link CdsExportCaptureReplay}), and later reads
 * stream that member beside the value member, so a chart's size is bounded by staging disk and
 * not by this heap budget. The session remembers the member and erases it when the patient ends.
 *
 * <p>A recording keeps each verified field with its digest ({@link #FIELD_BYTES} bytes) and
 * shares one table, key and column string among the fields that name the same one, so a replay
 * hands each consumer the same field value again and validates, formats and parses nothing. An
 * imported chart carries about 270 captured fields for each imported record (its retained source
 * fields and unit receipts): a patient with 128 imported notes and 128 imported laboratory
 * results is about 68,500 fields and 4.5 MiB of values, about 16 MiB kept this way.
 *
 * @since 2026-11-13
 */
public final class CdsExportVerifiedCaptures implements AutoCloseable {
    /**
     * Heap bytes a recording holds for each field, beside the field's value bytes: its slot, the
     * field value and the 64 characters of its SHA-256.
     */
    static final int FIELD_BYTES = 4 + 56 + 104;

    /** Heap bytes charged for each row beside its key: the key's object and list slot. */
    static final int ROW_BYTES = 64;

    /**
     * The most heap kept for one patient: value bytes plus {@link #FIELD_BYTES} for each field and
     * {@link #ROW_BYTES} and the key's characters for each row (the members stay encrypted on
     * disk). A sixteenth of the worker's heap, at least 32 MiB and at most 256 MiB: 32 MiB under
     * the default 512 MiB command-line heap, which holds a chart of about 250 imported records
     * or 1,000 native ones. One worker handles one patient at a time, so this is the whole of
     * it. It is deliberately small: the sections that reconcile a long chart need the heap more
     * than its replay does, and a larger capture replays from its stored replay member at a
     * cost of staging disk instead.
     */
    static final long MAXIMUM_BYTES = budget(Runtime.getRuntime().maxMemory());

    /** The session budget for a worker whose largest heap is {@code heap} bytes. */
    static long budget(long heap) {
        return Math.max(32L << 20, Math.min(256L << 20, heap / 16));
    }

    /**
     * The heap a recording of {@code valueBytes} value bytes, {@code fields} fields and {@code
     * rows} rows holds before its row keys, which are charged two bytes a character as they arrive.
     */
    static long cost(long valueBytes, long fields, long rows) {
        return valueBytes + fields * FIELD_BYTES + rows * ROW_BYTES;
    }

    /** How many replayed fields may pass between two run-control checkpoints. */
    private static final int CHECKPOINT_FIELDS = 256;

    /** One write to a consumer's sink, as the reader writes a value. */
    private static final int CHUNK = 65_536;

    private final int patient;
    private final long maximumBytes;
    private final Thread owner = Thread.currentThread();
    private final Map<Key, Recording> recordings = new HashMap<>();
    private final Map<CdsExportStagedFileDto, Object> manifests = new HashMap<>();
    private final Map<PlanKey, Map<String, CdsExportNativeCaptureReader.Table>> plans =
            new HashMap<>();
    private final Map<Object, Object> derived = new HashMap<>();
    private final Map<Key, Stored> stored = new HashMap<>();
    /** Members whose recording outgrew the budget while it was read: never recorded again. */
    private final java.util.Set<Key> unrecordable = new java.util.HashSet<>();
    private long bytes;
    private boolean closed;

    public CdsExportVerifiedCaptures(int patient) {
        this(patient, MAXIMUM_BYTES);
    }

    /** A session with an explicit heap budget (the worker's is {@link #MAXIMUM_BYTES}). */
    CdsExportVerifiedCaptures(int patient, long maximumBytes) {
        if (patient < 1 || maximumBytes < 1 || maximumBytes > 256L << 20)
            throw new IllegalArgumentException("CDS_EXPORT_SESSION_PATIENT_INVALID");
        this.patient = patient;
        this.maximumBytes = maximumBytes;
    }

    /** The patient whose captures these are. */
    public int patient() {
        return patient;
    }

    /** True when this record may be used from the calling thread (its owner, still open). */
    boolean usable() {
        return !closed && Thread.currentThread() == owner;
    }

    /**
     * Replays a recorded read of exactly these members and plan into {@code output}, or returns
     * null when none is recorded (the caller then reads the members). A recorded read that exceeds
     * the caller's bounds is refused as the full read would refuse it.
     */
    CdsExportNativeCaptureReader.Result replay(
            CdsExportStagedFileDto values,
            CdsExportStagedFileDto index,
            Map<String, CdsExportNativeCaptureReader.Table> plan,
            long maximumFields,
            long maximumBytes,
            Runnable checkpoint,
            CdsExportNativeCaptureReader.Fields output)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(output);
        if (!usable()) return null;
        var recording = recordings.get(new Key(values, index));
        // The same members read under another plan are read in full, as before.
        if (recording == null) return null;
        if (recording.plan != plan) {
            if (!recording.plan.equals(plan)) return null;
            recording.plan = Map.copyOf(plan); // later reads with this plan compare by identity
        }
        var result = recording.result;
        if (maximumFields < 1
                || maximumBytes < 1
                || result.fields() > maximumFields
                || result.valueBytes() > maximumBytes)
            throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
        checkpoint.run();
        int sinceCheckpoint = 0;
        for (int ordinal = 0; ordinal < recording.count; ordinal++) {
            if (closed) throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
            if (++sinceCheckpoint == CHECKPOINT_FIELDS) {
                checkpoint.run();
                sinceCheckpoint = 0;
            }
            // The very field the full read delivered and verified.
            var field = recording.fields[ordinal];
            var target =
                    Objects.requireNonNull(output.output(field), "CDS_EXPORT_FIELD_SINK_REQUIRED");
            int offset = (int) field.offset();
            int remaining = (int) field.bytes();
            while (remaining > 0) {
                int count = Math.min(CHUNK, remaining);
                if (count == CHUNK) checkpoint.run();
                target.write(recording.values, offset, count);
                offset += count;
                remaining -= count;
            }
            output.verified(field);
        }
        checkpoint.run();
        return result;
    }

    /**
     * Starts recording a read of these members, or returns null when they are already recorded
     * or would not fit. The recording keeps nothing until {@link #keep} is given the read's
     * result after the read has finished every check.
     */
    Recording record(
            CdsExportStagedFileDto values,
            CdsExportStagedFileDto index,
            Map<String, CdsExportNativeCaptureReader.Table> plan) {
        if (!usable()) return null;
        var key = new Key(values, index);
        if (recordings.containsKey(key) || unrecordable.contains(key)) return null;
        long plannedFields = 0;
        long plannedRows = 0;
        for (var table : plan.values()) {
            // Past these counts nothing fits the budget anyway; refusing here also keeps the sums
            // below from overflowing for an absurd plan.
            if (table.rows() > maximumBytes / FIELD_BYTES) return null;
            plannedRows += table.rows();
            plannedFields += table.rows() * table.columns().size();
            if (plannedFields > maximumBytes / FIELD_BYTES) return null;
        }
        // Everything but the row keys is known before the read; the keys are charged as they
        // arrive, and a recording that would pass the budget then is abandoned and zeroed.
        if (values.plaintextBytes() < 0 || values.plaintextBytes() > maximumBytes) return null;
        long fixed = cost(values.plaintextBytes(), plannedFields, plannedRows);
        if (fixed > maximumBytes - bytes) return null;
        try {
            return new Recording(
                    key,
                    Map.copyOf(plan),
                    (int) values.plaintextBytes(),
                    (int) plannedFields,
                    (int) plannedRows,
                    maximumBytes - bytes - fixed);
        } catch (OutOfMemoryError tight) {
            // The heap cannot spare the record just now: nothing was kept, and the read goes on
            // from its members exactly as it does for a capture past the budget.
            return null;
        }
    }

    /** Keeps a recording whose read finished every check and returned {@code result}. */
    void keep(Recording recording, CdsExportNativeCaptureReader.Result result) {
        Objects.requireNonNull(recording);
        Objects.requireNonNull(result);
        if (!usable()
                || recording.abandoned
                || recordings.containsKey(recording.key)
                || result.valueBytes() != recording.values.length
                || result.fields() != recording.count
                || recording.count != recording.fields.length) {
            // A read that outgrew the budget on its row keys is not tried in memory again: the
            // next complete read writes the stored replay member instead.
            if (usable() && recording.abandoned && unrecordable.size() < 64)
                unrecordable.add(recording.key);
            recording.clear();
            return;
        }
        recording.result = result;
        recordings.put(recording.key, recording);
        bytes += recording.held();
    }

    /**
     * A parsed manifest already read from this staged member in this session, or null. The member
     * names its authenticated plaintext digest, so the same member is the same bytes.
     */
    <T> T manifest(CdsExportStagedFileDto file, Class<T> type) {
        if (!usable()) return null;
        var known = manifests.get(Objects.requireNonNull(file));
        return type.isInstance(known) ? type.cast(known) : null;
    }

    /** Remembers the manifest parsed from this staged member after its read was checked. */
    void manifest(CdsExportStagedFileDto file, Object parsed) {
        if (!usable() || manifests.size() >= 64) return;
        manifests.putIfAbsent(Objects.requireNonNull(file), Objects.requireNonNull(parsed));
    }

    /**
     * The plan already derived in this session from this manifest member for this table scope:
     * its tables checked against the installed readers' column and schema contracts. Null when
     * not yet derived. The check depends only on the manifest's authenticated bytes and the job's
     * readers, so the same member and scope give the same plan.
     */
    Map<String, CdsExportNativeCaptureReader.Table> plan(
            CdsExportStagedFileDto manifest, Set<String> tables) {
        if (!usable()) return null;
        return plans.get(new PlanKey(Objects.requireNonNull(manifest), Set.copyOf(tables)));
    }

    /** Remembers a plan that passed every check, for later reads of the same member and scope. */
    void plan(
            CdsExportStagedFileDto manifest,
            Set<String> tables,
            Map<String, CdsExportNativeCaptureReader.Table> plan) {
        if (!usable() || plans.size() >= 64) return;
        plans.putIfAbsent(
                new PlanKey(Objects.requireNonNull(manifest), Set.copyOf(tables)),
                Map.copyOf(plan));
    }

    /**
     * An immutable result a reader already derived in this session from this patient's checked
     * capture members alone, under {@code key} (a value record naming the members and every input
     * the result depends on), or null. The reader still runs its own authorization and scope checks
     * when it reuses the result.
     */
    <T> T derived(Object key, Class<T> type) {
        if (!usable()) return null;
        var known = derived.get(Objects.requireNonNull(key));
        return type.isInstance(known) ? type.cast(known) : null;
    }

    /** Remembers an immutable result derived from this patient's checked capture members. */
    void derived(Object key, Object result) {
        if (!usable() || derived.size() >= 64) return;
        derived.putIfAbsent(Objects.requireNonNull(key), Objects.requireNonNull(result));
    }

    /** The most stored replay members one session keeps (a patient has two captures). */
    private static final int MAXIMUM_STORED = 8;

    /** Erases one stored replay member from staging. */
    interface Erasure {
        void erase() throws IOException;
    }

    /** One checked read kept as a staged replay member, with the result that read returned. */
    record Stored(
            Map<String, CdsExportNativeCaptureReader.Table> plan,
            CdsExportStagedFileDto member,
            CdsExportNativeCaptureReader.Result result,
            Erasure erasure) {
        @Override
        public String toString() {
            return "CdsExportVerifiedCaptures.Stored[protected]";
        }
    }

    /**
     * The stored replay of a checked read of exactly these members and plan, or null when there
     * is none (the caller then reads the members in full).
     */
    Stored stored(
            CdsExportStagedFileDto values,
            CdsExportStagedFileDto index,
            Map<String, CdsExportNativeCaptureReader.Table> plan) {
        if (!usable()) return null;
        var known = stored.get(new Key(values, index));
        return known != null && known.plan.equals(plan) ? known : null;
    }

    /** True when a complete read of these members may be kept as a stored replay member. */
    boolean storable(CdsExportStagedFileDto values, CdsExportStagedFileDto index) {
        var key = new Key(values, index);
        return usable()
                && !recordings.containsKey(key)
                && !stored.containsKey(key)
                && stored.size() < MAXIMUM_STORED;
    }

    /**
     * Remembers the replay member a complete read wrote after it passed every check and returned
     * {@code result}. False when the session does not take it; the caller then erases the member.
     * A member the session takes is erased by {@link #close()}.
     */
    boolean store(
            CdsExportStagedFileDto values,
            CdsExportStagedFileDto index,
            Map<String, CdsExportNativeCaptureReader.Table> plan,
            CdsExportStagedFileDto member,
            CdsExportNativeCaptureReader.Result result,
            Erasure erasure) {
        Objects.requireNonNull(member);
        Objects.requireNonNull(result);
        Objects.requireNonNull(erasure);
        if (!storable(values, index)) return false;
        stored.put(new Key(values, index), new Stored(Map.copyOf(plan), member, result, erasure));
        return true;
    }

    /**
     * Zeroes and drops everything held and erases every stored replay member; later calls record
     * and replay nothing. Everything is dropped and every erasure attempted before a failed
     * erasure is reported, as an {@link java.io.UncheckedIOException}: the member it could not
     * erase is still encrypted under the job's key in the job's staging, which is purged with it.
     */
    @Override
    public void close() {
        closed = true;
        for (var recording : recordings.values()) recording.clear();
        recordings.clear();
        manifests.clear();
        plans.clear();
        derived.clear();
        unrecordable.clear();
        bytes = 0;
        IOException failed = null;
        for (var replay : stored.values())
            try {
                replay.erasure.erase();
            } catch (IOException failure) {
                if (failed == null) failed = failure;
                else failed.addSuppressed(failure);
            }
        stored.clear();
        if (failed != null) throw new java.io.UncheckedIOException(failed);
    }

    @Override
    public String toString() {
        return "CdsExportVerifiedCaptures[protected]";
    }

    private record Key(CdsExportStagedFileDto values, CdsExportStagedFileDto index) {
        @Override
        public String toString() {
            return "CdsExportVerifiedCaptures.Key[protected]";
        }
    }

    private record PlanKey(CdsExportStagedFileDto manifest, Set<String> tables) {
        @Override
        public String toString() {
            return "CdsExportVerifiedCaptures.PlanKey[protected]";
        }
    }


    /**
     * One read being recorded: wraps the reader's sink, copying each value's bytes as the reader
     * passes them on and keeping each field once the reader has verified it. Only a gapless read
     * is kept: every field's value follows the one before it in the value member.
     */
    final class Recording {
        private final Key key;
        private Map<String, CdsExportNativeCaptureReader.Table> plan;
        private final byte[] values;
        private final CdsExportCapturedFieldDto[] fields;
        private final int plannedRows;
        private final Map<String, String> names = new HashMap<>();
        private String table;
        private String recordKey;
        private int rows;
        private int count;
        private long next;
        private final long keyBudget;
        private long keyBytes;
        private CdsExportNativeCaptureReader.Result result;
        private boolean abandoned;

        private Recording(
                Key key,
                Map<String, CdsExportNativeCaptureReader.Table> plan,
                int valueBytes,
                int plannedFields,
                int plannedRows,
                long keyBudget) {
            this.key = key;
            this.plan = plan;
            this.values = new byte[valueBytes];
            this.fields = new CdsExportCapturedFieldDto[plannedFields];
            this.plannedRows = plannedRows;
            this.keyBudget = keyBudget;
        }

        /** The heap this recording holds, as charged against the session budget. */
        private long held() {
            return values.length
                    + (long) fields.length * FIELD_BYTES
                    + (long) plannedRows * ROW_BYTES
                    + keyBytes;
        }

        /** The sink the reader writes to: every call reaches {@code output} unchanged. */
        CdsExportNativeCaptureReader.Fields tee(CdsExportNativeCaptureReader.Fields output) {
            Objects.requireNonNull(output);
            return new CdsExportNativeCaptureReader.Fields() {
                @Override
                public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
                    var target = output.output(field);
                    if (target == null || abandoned) return target;
                    if (field.offset() + field.bytes() > values.length) {
                        abandoned = true;
                        return target;
                    }
                    return new OutputStream() {
                        private int position = Math.toIntExact(field.offset());
                        private final int end = Math.toIntExact(field.offset() + field.bytes());

                        @Override
                        public void write(int value) throws IOException {
                            copy(new byte[] {(byte) value}, 0, 1);
                            target.write(value);
                        }

                        @Override
                        public void write(byte[] bytes, int offset, int length)
                                throws IOException {
                            Objects.checkFromIndexSize(offset, length, bytes.length);
                            copy(bytes, offset, length);
                            target.write(bytes, offset, length);
                        }

                        private void copy(byte[] bytes, int offset, int length) {
                            if (abandoned) return;
                            if (length > end - position) {
                                abandoned = true;
                                return;
                            }
                            System.arraycopy(bytes, offset, values, position, length);
                            position += length;
                        }
                    };
                }

                @Override
                public void verified(CdsExportCapturedFieldDto field) throws IOException {
                    if (!abandoned) add(field);
                    output.verified(field);
                }
            };
        }

        /** Keeps one verified field, or abandons the recording when it no longer fits or follows. */
        private void add(CdsExportCapturedFieldDto field) {
            if (field.offset() != next || count == fields.length) {
                abandoned = true;
                return;
            }
            if (!field.table().equals(table) || !field.recordKey().equals(recordKey)) {
                // Two bytes a character covers a key stored as UTF-16; an exact numeric witness,
                // which few fields carry, is charged with the row below.
                long cost = 2L * field.recordKey().length();
                if (rows == plannedRows || cost > keyBudget - keyBytes) {
                    abandoned = true;
                    return;
                }
                rows++;
                keyBytes += cost;
                table = names.computeIfAbsent(field.table(), name -> name);
                recordKey = field.recordKey();
            }
            if (field.exactNumeric() != null) {
                long cost = 64L + 2L * field.exactNumeric().bits().length();
                if (cost > keyBudget - keyBytes) {
                    abandoned = true;
                    return;
                }
                keyBytes += cost;
            }
            // The same field, sharing the row's table and key and the table's column name with
            // the other fields that name them, so each string is held once.
            fields[count++] =
                    new CdsExportCapturedFieldDto(
                            table,
                            recordKey,
                            names.computeIfAbsent(field.column(), name -> name),
                            field.kind(),
                            field.offset(),
                            field.bytes(),
                            field.sha256(),
                            field.exactNumeric());
            next += field.bytes();
        }

        /** Zeroes what a read that did not finish had copied; it is never kept. */
        void discard() {
            clear();
        }

        private void clear() {
            abandoned = true;
            Arrays.fill(values, (byte) 0);
            Arrays.fill(fields, null);
            names.clear();
            table = null;
            recordKey = null;
            count = 0;
        }

        @Override
        public String toString() {
            return "CdsExportVerifiedCaptures.Recording[protected]";
        }
    }
}
