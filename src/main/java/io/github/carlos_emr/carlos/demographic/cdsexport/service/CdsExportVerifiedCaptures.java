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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
 * most {@link #MAXIMUM_BYTES} bytes and {@link #MAXIMUM_FIELDS} fields; a capture that does not fit
 * is simply read from its members every time, as before.
 *
 * @since 2026-11-13
 */
public final class CdsExportVerifiedCaptures implements AutoCloseable {
    /**
     * The most value bytes kept for one patient (the members stay encrypted on disk). A larger
     * chart is read from its members every time, as before, so the worker's memory stays bounded.
     */
    static final long MAXIMUM_BYTES = 32L << 20;

    /** The most fields kept for one patient (each costs a few hundred bytes of metadata). */
    static final long MAXIMUM_FIELDS = 200_000;

    /** How many replayed fields may pass between two run-control checkpoints. */
    private static final int CHECKPOINT_FIELDS = 256;

    /** One write to a consumer's sink, as the reader writes a value. */
    private static final int CHUNK = 65_536;

    private final int patient;
    private final Thread owner = Thread.currentThread();
    private final Map<Key, Recording> recordings = new HashMap<>();
    private final Map<CdsExportStagedFileDto, Object> manifests = new HashMap<>();
    private final Map<PlanKey, Map<String, CdsExportNativeCaptureReader.Table>> plans =
            new HashMap<>();
    private final Map<Object, Object> derived = new HashMap<>();
    private long bytes, fields;
    private boolean closed;

    public CdsExportVerifiedCaptures(int patient) {
        if (patient < 1) throw new IllegalArgumentException("CDS_EXPORT_SESSION_PATIENT_INVALID");
        this.patient = patient;
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
                || maximumFields > 100_000_000
                || maximumBytes < 1
                || result.fields() > maximumFields
                || result.valueBytes() > maximumBytes)
            throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
        checkpoint.run();
        int sinceCheckpoint = 0;
        for (var field : recording.fields) {
            if (closed) throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
            if (++sinceCheckpoint == CHECKPOINT_FIELDS) {
                checkpoint.run();
                sinceCheckpoint = 0;
            }
            var target =
                    Objects.requireNonNull(output.output(field), "CDS_EXPORT_FIELD_SINK_REQUIRED");
            int offset = Math.toIntExact(field.offset());
            int remaining = Math.toIntExact(field.bytes());
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
        if (recordings.containsKey(key)) return null;
        long planned = 0;
        for (var table : plan.values())
            planned = Math.addExact(planned, Math.multiplyExact(table.rows(), table.columns().size()));
        if (values.plaintextBytes() > MAXIMUM_BYTES - bytes || planned > MAXIMUM_FIELDS - fields)
            return null;
        return new Recording(key, Map.copyOf(plan), (int) values.plaintextBytes(), (int) planned);
    }

    /** Keeps a recording whose read finished every check and returned {@code result}. */
    void keep(Recording recording, CdsExportNativeCaptureReader.Result result) {
        Objects.requireNonNull(recording);
        Objects.requireNonNull(result);
        if (!usable()
                || recording.abandoned
                || recordings.containsKey(recording.key)
                || result.valueBytes() != recording.values.length
                || result.fields() != recording.fields.size()) {
            recording.clear();
            return;
        }
        recording.result = result;
        recordings.put(recording.key, recording);
        bytes += recording.values.length;
        fields += recording.fields.size();
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

    /** Zeroes and drops everything held; later calls record and replay nothing. */
    @Override
    public void close() {
        closed = true;
        for (var recording : recordings.values()) recording.clear();
        recordings.clear();
        manifests.clear();
        plans.clear();
        derived.clear();
        bytes = 0;
        fields = 0;
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
     * passes them on and each field once the reader has verified it.
     */
    final class Recording {
        private final Key key;
        private Map<String, CdsExportNativeCaptureReader.Table> plan;
        private final byte[] values;
        private final List<CdsExportCapturedFieldDto> fields;
        private CdsExportNativeCaptureReader.Result result;
        private boolean abandoned;

        private Recording(
                Key key,
                Map<String, CdsExportNativeCaptureReader.Table> plan,
                int valueBytes,
                int plannedFields) {
            this.key = key;
            this.plan = plan;
            this.values = new byte[valueBytes];
            this.fields = new ArrayList<>(Math.min(plannedFields, 65_536));
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
                    if (!abandoned) {
                        var last = fields.isEmpty() ? null : fields.getLast();
                        long next = last == null ? 0 : last.offset() + last.bytes();
                        if (field.offset() != next) abandoned = true;
                        else fields.add(field);
                    }
                    output.verified(field);
                }
            };
        }

        /** Zeroes what a read that did not finish had copied; it is never kept. */
        void discard() {
            clear();
        }

        private void clear() {
            abandoned = true;
            Arrays.fill(values, (byte) 0);
            fields.clear();
        }

        @Override
        public String toString() {
            return "CdsExportVerifiedCaptures.Recording[protected]";
        }
    }
}
