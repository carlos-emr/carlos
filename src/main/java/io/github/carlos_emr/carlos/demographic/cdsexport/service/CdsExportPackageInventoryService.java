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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/**
 * Encrypted external sort and sealed, reopenable package membership. Native/cohort capture
 * order need not equal archive component order. Only bounded batches and merge heads are
 * retained in memory. Clinical payload members remain caller-owned throughout; this service
 * deletes only its own intermediate inventory members. This is not clinical publication proof.
 */
@Service
public class CdsExportPackageInventoryService {
    private static final byte[] MAGIC = "CDSPMI01".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> PURPOSES = Set.of("PATIENT_XML", "REPORT_ATTACHMENT", "SUPPORTING_README", "SUPPORTING_EVENT_LOG");
    private static final int BATCH_ENTRIES = 4096, MERGE_FAN_IN = 32;
    private static final long BATCH_BYTES = 8L << 20;
    @FunctionalInterface public interface Producer { void write(Members output) throws IOException; }
    @FunctionalInterface public interface Members { void accept(CdsExportPackageMemberDto member) throws IOException; }
    private record Entry(CdsExportPackageMemberDto member, List<String> key, long bytes) { }
    private record Run(CdsExportStagedFileDto file, long entries) { }
    private record Head(Reader reader, Entry entry) { }

    /** Consumes arbitrary source order, validates and read-backs the complete final inventory. */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that failure.
    @SuppressWarnings("java:S1143")
    public Sealed seal(CdsExportStagingService store, long maximumEntries, long maximumIndexBytes,
            Runnable checkpoint, Producer producer) throws IOException {
        Objects.requireNonNull(store); Objects.requireNonNull(checkpoint); Objects.requireNonNull(producer);
        if (maximumEntries < 1 || maximumEntries > 2_000_000 || maximumIndexBytes < 12 || maximumIndexBytes > (64L << 30)) {
            throw new IllegalArgumentException("CDS_EXPORT_PACKAGE_INVENTORY_LIMIT_INVALID");
        }
        var owned = new ArrayList<CdsExportStagedFileDto>(); var runs = new ArrayList<Run>();
        var batch = new ArrayList<Entry>(); long[] count = {0}, bytes = {12}, batchBytes = {0};
        var active = new AtomicBoolean(true); var failed = new AtomicBoolean(); Thread owner = Thread.currentThread();
        Throwable primary = null; CdsExportStagedFileDto result = null; boolean complete = false;
        try {
            try {
                producer.write(member -> {
                    try {
                        if (!active.get() || Thread.currentThread() != owner) invalid(); checkpoint.run();
                        Entry entry = entry(member);
                        if (count[0] == maximumEntries || entry.bytes() > maximumIndexBytes - bytes[0]) limit();
                        if (!batch.isEmpty() && (batch.size() == BATCH_ENTRIES || entry.bytes() > BATCH_BYTES - batchBytes[0])) {
                            runs.add(flush(store, batch, checkpoint, owned)); batchBytes[0] = 0;
                        }
                        batch.add(entry); count[0]++; bytes[0] += entry.bytes(); batchBytes[0] += entry.bytes();
                    } catch (IOException | RuntimeException | Error failure) { failed.set(true); throw failure; }
                });
            } finally { active.set(false); }
            if (failed.get() || count[0] == 0) invalid();
            if (!batch.isEmpty()) runs.add(flush(store, batch, checkpoint, owned));
            while (runs.size() > MERGE_FAN_IN) {
                var next = new ArrayList<Run>();
                for (int offset = 0; offset < runs.size(); offset += MERGE_FAN_IN) {
                    var group = List.copyOf(runs.subList(offset, Math.min(runs.size(), offset + MERGE_FAN_IN)));
                    Run merged = merge(store, group, "PACKAGE_MEMBER_SORT", checkpoint, owned); next.add(merged);
                    discard(store, group, owned);
                }
                runs.clear(); runs.addAll(next);
            }
            Run finalRun = merge(store, runs, "PACKAGE_MEMBERS", checkpoint, owned); result = finalRun.file();
            if (finalRun.entries() != count[0] || result.plaintextBytes() != bytes[0]) invalid();
            var sealed = new Sealed(store, finalRun, maximumIndexBytes, checkpoint);
            try (var cursor = sealed.open()) { while (cursor.next() != null) checkpoint.run(); }
            discard(store, runs, owned); owned.remove(result);
            complete = true; return sealed;
        } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            active.set(false);
            for (var file : owned.reversed()) try { store.discardUnpublished(file); }
            catch (IOException | RuntimeException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
            if (!complete && result != null && !owned.contains(result)) try { store.discardUnpublished(result); }
            catch (IOException | RuntimeException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
        }
    }
    private static Run flush(CdsExportStagingService store, List<Entry> batch, Runnable checkpoint,
            List<CdsExportStagedFileDto> owned) throws IOException {
        batch.sort((left, right) -> compare(left.key(), right.key())); long count = batch.size();
        var file = store.stage("PACKAGE_MEMBER_SORT", output -> {
            var data = new DataOutputStream(output); data.write(MAGIC); var paths = new CdsExportPackagePaths();
            for (var entry : batch) { checkpoint.run(); paths.accept(entry.member().relativePath()); write(data, entry.member()); }
            data.writeInt(-1); data.flush();
        });
        owned.add(file); batch.clear(); return new Run(file, count);
    }
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that failure.
    @SuppressWarnings("java:S1143")
    private static Run merge(CdsExportStagingService store, List<Run> runs, String purpose, Runnable checkpoint,
            List<CdsExportStagedFileDto> owned) throws IOException {
        long expected = 0; for (var run : runs) expected = Math.addExact(expected, run.entries()); long total = expected;
        var file = store.stage(purpose, output -> {
            var inputs = new ArrayList<Reader>(); Throwable primary = null;
            try {
                var queue = new PriorityQueue<Head>((left, right) -> compare(left.entry().key(), right.entry().key()));
                for (var run : runs) {
                    checkpoint.run(); var reader = new Reader(store, run, checkpoint); inputs.add(reader);
                    var entry = reader.nextEntry(); if (entry == null) invalid(); queue.add(new Head(reader, entry));
                }
                var data = new DataOutputStream(output); data.write(MAGIC); var paths = new CdsExportPackagePaths(); long count = 0;
                while (!queue.isEmpty()) {
                    checkpoint.run(); var head = queue.remove(); paths.accept(head.entry().member().relativePath());
                    if (++count > total) invalid(); write(data, head.entry().member());
                    var next = head.reader().nextEntry(); if (next != null) queue.add(new Head(head.reader(), next));
                }
                if (count != total) invalid(); data.writeInt(-1); data.flush();
            } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
            finally {
                Throwable closing = null;
                for (var reader : inputs.reversed()) try { reader.close(); }
                catch (IOException | RuntimeException | Error failure) { if (closing == null) closing = failure; else closing.addSuppressed(failure); }
                if (closing != null) {
                    if (primary != null) primary.addSuppressed(closing);
                    else if (closing instanceof IOException io) throw io;
                    else if (closing instanceof RuntimeException unchecked) throw unchecked;
                    else throw (Error) closing;
                }
            }
        });
        owned.add(file); return new Run(file, total);
    }
    private static void discard(CdsExportStagingService store, List<Run> runs, List<CdsExportStagedFileDto> owned) throws IOException {
        for (var run : runs) { store.discardUnpublished(run.file()); owned.remove(run.file()); }
    }
    private static Entry entry(CdsExportPackageMemberDto member) throws IOException {
        if (member == null || member.file() == null || !PURPOSES.contains(member.file().purpose())) invalid();
        var key = CdsExportPackagePaths.canonicalParts(member.relativePath());
        long bytes = member.relativePath().getBytes(StandardCharsets.UTF_8).length + member.file().purpose().length() + 152L;
        return new Entry(member, key, bytes);
    }
    private static int compare(List<String> left, List<String> right) {
        int common = Math.min(left.size(), right.size());
        for (int i = 0; i < common; i++) { int order = left.get(i).compareTo(right.get(i)); if (order != 0) return order; }
        return Integer.compare(left.size(), right.size());
    }
    private static void write(DataOutputStream output, CdsExportPackageMemberDto member) throws IOException {
        text(output, member.relativePath()); text(output, member.file().memberId()); text(output, member.file().purpose());
        text(output, member.file().plaintextSha256()); output.writeLong(member.file().plaintextBytes());
    }
    private static void text(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); output.writeInt(bytes.length); output.write(bytes);
    }
    private static String text(DataInputStream input, int length, int maximum) throws IOException {
        if (length < 1 || length > maximum) invalid(); byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) invalid();
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
    private static final class Reader implements CdsExportPackageMembers.Cursor {
        private final DataInputStream input; private final Run run; private final Runnable checkpoint;
        private final CdsExportPackagePaths paths = new CdsExportPackagePaths();
        private final Thread owner = Thread.currentThread(); private long count; private boolean eof, closed, failed;
        Reader(CdsExportStagingService store, Run run, Runnable checkpoint) throws IOException {
            this.run = run; this.checkpoint = checkpoint; input = new DataInputStream(store.open(run.file()));
            try { if (!MessageDigest.isEqual(input.readNBytes(MAGIC.length), MAGIC)) invalid(); }
            catch (IOException | RuntimeException | Error failure) { try { input.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); } throw failure; }
        }
        Entry nextEntry() throws IOException {
            if (closed || failed || Thread.currentThread() != owner) invalid(); if (eof) return null;
            try {
                checkpoint.run(); int length = input.readInt();
                if (length == -1) {
                    if (count != run.entries() || input.read() != -1) invalid(); eof = true; return null;
                }
                if (++count > run.entries()) invalid(); String name = text(input, length, 65535);
                String id = text(input, input.readInt(), 64), purpose = text(input, input.readInt(), 64), digest = text(input, input.readInt(), 64);
                var member = new CdsExportPackageMemberDto(name, new CdsExportStagedFileDto(id, purpose, digest, input.readLong()));
                paths.accept(name); return entry(member);
            } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
        }
        @Override public CdsExportPackageMemberDto next() throws IOException { var entry = nextEntry(); return entry == null ? null : entry.member(); }
        // Cleanup only throws without a primary failure; otherwise its error is suppressed on that failure.
        @SuppressWarnings("java:S1143")
        @Override public void close() throws IOException {
            if (closed) return; Throwable primary = null;
            try { if (!failed) while (nextEntry() != null) checkpoint.run(); }
            catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
            finally {
                closed = true;
                try { input.close(); } catch (IOException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
            }
        }
    }
    /** Only minted after a complete read-back; cursors authenticate EOF even when closed early. */
    public static final class Sealed implements CdsExportPackageMembers {
        private final CdsExportStagingService store; private final Run run; private final long maximumBytes; private final Runnable checkpoint;
        private Sealed(CdsExportStagingService store, Run run, long maximumBytes, Runnable checkpoint) {
            this.store = store; this.run = run; this.maximumBytes = maximumBytes; this.checkpoint = checkpoint;
        }
        public CdsExportStagedFileDto member() { return run.file(); }
        public long entries() { return run.entries(); }
        @Override public Cursor open() throws IOException {
            checkpoint.run(); if (run.file().plaintextBytes() > maximumBytes || !"PACKAGE_MEMBERS".equals(run.file().purpose())) invalid();
            return new Reader(store, run, checkpoint);
        }
        @Override public String toString() { return "CdsExportSealedPackageInventory[protected]"; }
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_PACKAGE_INVENTORY_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_PACKAGE_INVENTORY_LIMIT"); }
}
