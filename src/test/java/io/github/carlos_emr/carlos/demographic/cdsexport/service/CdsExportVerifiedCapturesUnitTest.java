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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A patient's later reads of capture members already read back and checked replay exactly what
 * that checked read delivered, and nothing is kept from a read that failed, from another
 * thread, past the patient's session or beyond the bounds.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportVerifiedCapturesUnitTest {
    private static final long LIMIT = 4L << 20;
    private static final Map<String, CdsExportNativeCaptureReader.Table> PLAN =
            Map.copyOf(
                    Map.of(
                            "demographic",
                            new CdsExportNativeCaptureReader.Table(
                                    Set.of("id", "missing", "empty", "text", "binary"), 2),
                            "empty_table",
                            new CdsExportNativeCaptureReader.Table(Set.of("id"), 0)));

    private record Capture(byte[] values, byte[] index) {}

    /** One delivered field: its metadata, the bytes written for it, and whether it was verified. */
    private record Delivered(CdsExportCapturedFieldDto field, byte[] bytes, boolean verified) {}

    /** Records every callback exactly as a consumer of a read sees it. */
    private static final class Sink implements CdsExportNativeCaptureReader.Fields {
        final List<Delivered> delivered = new ArrayList<>();
        private ByteArrayOutputStream current;

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) {
            current = new ByteArrayOutputStream();
            return current;
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) {
            delivered.add(new Delivered(field, current.toByteArray(), true));
        }
    }

    /** Two rows: a 70 KiB multi-byte text crossing the reader's chunk and a 131 KiB binary. */
    private static Capture capture() throws IOException {
        String text = "é".repeat(35_000) + "😀";
        byte[] binary = new byte[131_073];
        new Random(27).nextBytes(binary);
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of("demographic", "empty_table"),
                        100,
                        LIMIT,
                        () -> {},
                        writer -> {
                            writer.beginTable(
                                    "demographic",
                                    Set.of("id", "missing", "empty", "text", "binary"),
                                    2);
                            for (String key : List.of("0000000027", "0000000028"))
                                writer.row(
                                        key,
                                        row -> {
                                            row.text("id", key.substring(8));
                                            row.text("missing", (String) null);
                                            row.text("empty", "");
                                            row.text("text", new java.io.StringReader(text));
                                            row.binary("binary", new ByteArrayInputStream(binary));
                                        });
                            writer.endTable();
                            writer.beginTable("empty_table", Set.of("id"), 0);
                            writer.endTable();
                        });
        return new Capture(values.toByteArray(), index.toByteArray());
    }

    private static CdsExportStagedFileDto member(String purpose, byte[] bytes, char id)
            throws Exception {
        return new CdsExportStagedFileDto(
                String.valueOf(id).repeat(64),
                purpose,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                bytes.length);
    }

    /** Reads the members as the capture service does: replay, else a full read that records. */
    private static CdsExportNativeCaptureReader.Result read(
            CdsExportVerifiedCaptures verified,
            Capture capture,
            CdsExportStagedFileDto values,
            CdsExportStagedFileDto index,
            Runnable checkpoint,
            CdsExportNativeCaptureReader.Fields sink)
            throws IOException {
        var replayed = verified.replay(values, index, PLAN, 100, LIMIT, checkpoint, sink);
        if (replayed != null) return replayed;
        var recording = verified.record(values, index, PLAN);
        try {
            var result =
                    new CdsExportNativeCaptureReader()
                            .scan(
                                    new ByteArrayInputStream(capture.values()),
                                    new ByteArrayInputStream(capture.index()),
                                    PLAN,
                                    100,
                                    LIMIT,
                                    checkpoint,
                                    recording == null ? sink : recording.tee(sink));
            if (recording != null) {
                verified.keep(recording, result);
                recording = null;
            }
            return result;
        } finally {
            if (recording != null) recording.discard();
        }
    }

    @Test
    void shouldReplayEveryCheckedField_withTheSameBytesOrderAndResult() throws Exception {
        var capture = capture();
        var values = member("SOURCE_VALUES", capture.values(), 'a');
        var index = member("SOURCE_INDEX", capture.index(), 'b');
        try (var verified = new CdsExportVerifiedCaptures(27)) {
            var first = new Sink();
            var read = read(verified, capture, values, index, () -> {}, first);
            var checkpoints = new AtomicInteger();
            var second = new Sink();
            var replayed = verified.replay(values, index, PLAN, 100, LIMIT, checkpoints::incrementAndGet, second);

            assertThat(replayed).isEqualTo(read);
            assertThat(second.delivered).hasSize(first.delivered.size()).hasSize(10);
            for (int i = 0; i < first.delivered.size(); i++) {
                assertThat(second.delivered.get(i).field()).isEqualTo(first.delivered.get(i).field());
                assertThat(second.delivered.get(i).bytes()).isEqualTo(first.delivered.get(i).bytes());
            }
            // Every replayed value is the value the full read checked against its SHA-256.
            var stream = new ByteArrayOutputStream();
            for (var delivered : second.delivered) stream.write(delivered.bytes());
            assertThat(stream.toByteArray()).isEqualTo(capture.values());
            // The replay still reaches the run control: cancellation and lease checks keep running.
            assertThat(checkpoints.get()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void shouldKeepNothing_whenTheRecordedReadFailsItsChecks() throws Exception {
        var capture = capture();
        byte[] tampered = capture.values().clone();
        tampered[tampered.length - 1] ^= 1;
        var values = member("SOURCE_VALUES", capture.values(), 'a');
        var index = member("SOURCE_INDEX", capture.index(), 'b');
        try (var verified = new CdsExportVerifiedCaptures(27)) {
            assertThatThrownBy(
                            () ->
                                    read(
                                            verified,
                                            new Capture(tampered, capture.index()),
                                            values,
                                            index,
                                            () -> {},
                                            new Sink()))
                    .hasMessage("CDS_EXPORT_CAPTURE_READBACK_INVALID");

            assertThat(verified.replay(values, index, PLAN, 100, LIMIT, () -> {}, new Sink())).isNull();
        }
    }

    @Test
    void shouldReadInFull_whenThePlanOrMembersDiffer() throws Exception {
        var capture = capture();
        var values = member("SOURCE_VALUES", capture.values(), 'a');
        var index = member("SOURCE_INDEX", capture.index(), 'b');
        try (var verified = new CdsExportVerifiedCaptures(27)) {
            read(verified, capture, values, index, () -> {}, new Sink());
            var other = Map.of("demographic", PLAN.get("demographic"));

            assertThat(verified.replay(values, index, other, 100, LIMIT, () -> {}, new Sink())).isNull();
            assertThat(
                            verified.replay(
                                    member("SOURCE_VALUES", capture.values(), 'c'),
                                    index,
                                    PLAN,
                                    100,
                                    LIMIT,
                                    () -> {},
                                    new Sink()))
                    .isNull();
        }
    }

    @Test
    void shouldRefuseTheReplay_whenItExceedsTheCallersBounds() throws Exception {
        var capture = capture();
        var values = member("SOURCE_VALUES", capture.values(), 'a');
        var index = member("SOURCE_INDEX", capture.index(), 'b');
        try (var verified = new CdsExportVerifiedCaptures(27)) {
            read(verified, capture, values, index, () -> {}, new Sink());
            var sink = new Sink();

            assertThatThrownBy(() -> verified.replay(values, index, PLAN, 9, LIMIT, () -> {}, sink))
                    .hasMessage("CDS_EXPORT_CAPTURE_READBACK_INVALID");
            assertThatThrownBy(() -> verified.replay(values, index, PLAN, 100, 1024, () -> {}, sink))
                    .hasMessage("CDS_EXPORT_CAPTURE_READBACK_INVALID");
            assertThat(sink.delivered).isEmpty();
        }
    }

    @Test
    void shouldStopTheReplay_whenTheRunControlRefusesToContinue() throws Exception {
        var capture = capture();
        var values = member("SOURCE_VALUES", capture.values(), 'a');
        var index = member("SOURCE_INDEX", capture.index(), 'b');
        try (var verified = new CdsExportVerifiedCaptures(27)) {
            read(verified, capture, values, index, () -> {}, new Sink());

            assertThatThrownBy(
                            () ->
                                    verified.replay(
                                            values,
                                            index,
                                            PLAN,
                                            100,
                                            LIMIT,
                                            () -> {
                                                throw new java.util.concurrent.CancellationException(
                                                        "CDS_EXPORT_WAIT_INTERRUPTED");
                                            },
                                            new Sink()))
                    .isInstanceOf(java.util.concurrent.CancellationException.class);
        }
    }

    @Test
    void shouldReplayNothing_afterTheSessionEndsOrOnAnotherThread() throws Exception {
        var capture = capture();
        var values = member("SOURCE_VALUES", capture.values(), 'a');
        var index = member("SOURCE_INDEX", capture.index(), 'b');
        var manifest = member("SOURCE_MANIFEST", new byte[] {1}, 'd');
        var verified = new CdsExportVerifiedCaptures(27);
        read(verified, capture, values, index, () -> {}, new Sink());
        verified.manifest(manifest, "parsed");
        verified.plan(manifest, Set.of("demographic", "empty_table"), PLAN);

        var elsewhere = new AtomicReference<Object>("unset");
        var thread =
                new Thread(
                        () -> {
                            try {
                                elsewhere.set(
                                        verified.replay(
                                                values, index, PLAN, 100, LIMIT, () -> {}, new Sink()));
                            } catch (IOException failed) {
                                elsewhere.set(failed);
                            }
                        });
        thread.start();
        thread.join();
        assertThat(elsewhere.get()).isNull();
        assertThat(verified.manifest(manifest, String.class)).isEqualTo("parsed");
        assertThat(verified.plan(manifest, Set.of("demographic", "empty_table"))).isEqualTo(PLAN);

        verified.close();

        assertThat(verified.replay(values, index, PLAN, 100, LIMIT, () -> {}, new Sink())).isNull();
        assertThat(verified.record(values, index, PLAN)).isNull();
        assertThat(verified.manifest(manifest, String.class)).isNull();
        assertThat(verified.plan(manifest, Set.of("demographic", "empty_table"))).isNull();
    }

    /** Rows of two tables that share a record key, one field carrying exact numeric evidence. */
    private static final Map<String, CdsExportNativeCaptureReader.Table> MIXED_PLAN =
            Map.of(
                    "drugs",
                    new CdsExportNativeCaptureReader.Table(Set.of("id", "takemin"), 2),
                    "prescription",
                    new CdsExportNativeCaptureReader.Table(Set.of("id"), 1));

    private static Capture mixedCapture() throws IOException {
        var number =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric(
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric.Codec
                                .IEEE754_BINARY32_V1,
                        "3dcccccd");
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of("drugs", "prescription"),
                        100,
                        LIMIT,
                        () -> {},
                        writer -> {
                            writer.beginTable("drugs", Set.of("id", "takemin"), 2);
                            for (String key : List.of("0000000001", "0000000002"))
                                writer.row(
                                        key,
                                        row -> {
                                            row.text("id", key);
                                            row.numeric("takemin", "0.1", number);
                                        });
                            writer.endTable();
                            writer.beginTable("prescription", Set.of("id"), 1);
                            // The same key as the last row of the table before it.
                            writer.row("0000000002", Map.of("id", "2"));
                            writer.endTable();
                        });
        return new Capture(values.toByteArray(), index.toByteArray());
    }

    private static List<Delivered> scan(
            CdsExportVerifiedCaptures verified,
            Capture capture,
            CdsExportStagedFileDto values,
            CdsExportStagedFileDto index)
            throws IOException {
        var sink = new Sink();
        var recording = verified.record(values, index, MIXED_PLAN);
        var result =
                new CdsExportNativeCaptureReader()
                        .scan(
                                new ByteArrayInputStream(capture.values()),
                                new ByteArrayInputStream(capture.index()),
                                MIXED_PLAN,
                                100,
                                LIMIT,
                                () -> {},
                                recording == null ? sink : recording.tee(sink));
        if (recording != null) verified.keep(recording, result);
        return sink.delivered;
    }

    @Test
    void shouldRebuildEveryFieldComponent_forSharedKeysAndExactNumerics() throws Exception {
        var capture = mixedCapture();
        var values = member("SOURCE_VALUES", capture.values(), 'c');
        var index = member("SOURCE_INDEX", capture.index(), 'd');
        try (var verified = new CdsExportVerifiedCaptures(27)) {
            var read = scan(verified, capture, values, index);
            var replayed = new Sink();
            assertThat(verified.replay(values, index, MIXED_PLAN, 100, LIMIT, () -> {}, replayed))
                    .isNotNull();

            assertThat(read).hasSize(5);
            // Table, key, column, kind, offset, length, digest and numeric evidence all equal.
            assertThat(replayed.delivered).usingRecursiveComparison().isEqualTo(read);
            assertThat(replayed.delivered)
                    .extracting(delivered -> delivered.field().table())
                    .containsExactly("drugs", "drugs", "drugs", "drugs", "prescription");
            assertThat(replayed.delivered)
                    .filteredOn(delivered -> delivered.field().exactNumeric() != null)
                    .hasSize(2)
                    .allSatisfy(
                            delivered ->
                                    assertThat(delivered.field().exactNumeric().bits())
                                            .isEqualTo("3dcccccd"));
        }
    }

    @Test
    void shouldNotHoldARecord_whenItWouldPassItsHeapBudget() throws Exception {
        var capture = mixedCapture();
        var values = member("SOURCE_VALUES", capture.values(), 'c');
        var index = member("SOURCE_INDEX", capture.index(), 'd');
        long fixed = CdsExportVerifiedCaptures.cost(capture.values().length, 5, 3);
        // One byte short of the fixed cost: nothing is recorded at all.
        try (var verified = new CdsExportVerifiedCaptures(27, fixed - 1)) {
            assertThat(verified.record(values, index, MIXED_PLAN)).isNull();
        }
        // The fixed cost fits but the row keys do not: the recording is abandoned, the read
        // still delivers every field, and later reads are full reads again.
        try (var verified = new CdsExportVerifiedCaptures(27, fixed + 10)) {
            assertThat(scan(verified, capture, values, index)).hasSize(5);
            assertThat(verified.replay(values, index, MIXED_PLAN, 100, LIMIT, () -> {}, new Sink()))
                    .isNull();
            // It is not tried in memory again: the next complete read may store its replay
            // member, so the capture is not read in full for the rest of the session.
            assertThat(verified.record(values, index, MIXED_PLAN)).isNull();
            assertThat(verified.storable(values, index)).isTrue();
        }
        // With room for the three ten-character keys and the two exact numeric witnesses (eight
        // characters of bits each) the same read is kept and replayed.
        try (var verified =
                new CdsExportVerifiedCaptures(27, fixed + 3 * 2 * 10 + 2 * (64 + 2 * 8))) {
            var read = scan(verified, capture, values, index);
            var replayed = new Sink();
            assertThat(verified.replay(values, index, MIXED_PLAN, 100, LIMIT, () -> {}, replayed))
                    .isNotNull();
            assertThat(replayed.delivered).usingRecursiveComparison().isEqualTo(read);
            // The session budget is spent: a second capture of the same size no longer fits.
            assertThat(
                            verified.record(
                                    member("SOURCE_VALUES", capture.values(), 'e'),
                                    member("SOURCE_INDEX", capture.index(), 'f'),
                                    MIXED_PLAN))
                    .isNull();
        }
    }

    @Test
    void shouldHoldAShortImportedChart_underTheDefaultCommandHeap() {
        // Measured on the installed export lane. An imported chart of 128 notes and 128
        // laboratory results captures 68,541 fields in about 5,200 rows and 4.5 MiB of values,
        // with 64-character row keys: it replays from memory under the default 512 MiB heap.
        // The 512-record chart (273,213 fields, 18.1 MiB) and longer ones do not, and replay
        // from their stored replay member instead.
        long budget = CdsExportVerifiedCaptures.budget(512L << 20);
        long small = CdsExportVerifiedCaptures.cost(4_750_000L, 68_541, 5_200) + 5_200L * 2 * 64;
        long mid = CdsExportVerifiedCaptures.cost(18_980_000L, 273_213, 20_500) + 20_500L * 2 * 64;
        assertThat(budget).isEqualTo(32L << 20);
        assertThat(small).isLessThan(budget);
        assertThat(mid).isGreaterThan(budget);
        // The budget follows the heap between a floor and a ceiling.
        assertThat(CdsExportVerifiedCaptures.budget(64L << 20)).isEqualTo(32L << 20);
        assertThat(CdsExportVerifiedCaptures.budget(1024L << 20)).isEqualTo(64L << 20);
        assertThat(CdsExportVerifiedCaptures.budget(4L << 30)).isEqualTo(256L << 20);
        assertThat(CdsExportVerifiedCaptures.budget(Long.MAX_VALUE)).isEqualTo(256L << 20);
        assertThat(CdsExportVerifiedCaptures.MAXIMUM_BYTES)
                .isEqualTo(CdsExportVerifiedCaptures.budget(Runtime.getRuntime().maxMemory()));
    }

    @Test
    void shouldKeepAStoredReplayMember_forTheSameMembersAndPlanOnly() throws Exception {
        var capture = mixedCapture();
        var values = member("SOURCE_VALUES", capture.values(), 'c');
        var index = member("SOURCE_INDEX", capture.index(), 'd');
        var replay = member("SOURCE_REPLAY", new byte[] {1}, 'e');
        var result = new CdsExportNativeCaptureReader.Result(Map.of(), 5, capture.values().length);
        var erased = new AtomicInteger();
        var verified = new CdsExportVerifiedCaptures(27);

        assertThat(verified.storable(values, index)).isTrue();
        assertThat(verified.stored(values, index, MIXED_PLAN)).isNull();
        assertThat(verified.store(values, index, MIXED_PLAN, replay, result, erased::incrementAndGet))
                .isTrue();

        var stored = verified.stored(values, index, Map.copyOf(MIXED_PLAN));
        assertThat(stored.member()).isEqualTo(replay);
        assertThat(stored.result()).isEqualTo(result);
        // Another plan, or other members, have no stored replay; the same members take no second.
        assertThat(verified.stored(values, index, PLAN)).isNull();
        assertThat(verified.stored(index, values, MIXED_PLAN)).isNull();
        assertThat(verified.storable(values, index)).isFalse();
        assertThat(verified.store(values, index, MIXED_PLAN, replay, result, erased::incrementAndGet))
                .isFalse();
        assertThat(erased).hasValue(0);

        verified.close();

        // The session erased its member exactly once and answers nothing afterwards.
        assertThat(erased).hasValue(1);
        assertThat(verified.stored(values, index, MIXED_PLAN)).isNull();
        assertThat(verified.storable(values, index)).isFalse();
        verified.close();
        assertThat(erased).hasValue(1);
    }

    @Test
    void shouldNotStoreAReplayMember_whenTheReadIsAlreadyHeldInMemory() throws Exception {
        var capture = mixedCapture();
        var values = member("SOURCE_VALUES", capture.values(), 'c');
        var index = member("SOURCE_INDEX", capture.index(), 'd');
        try (var verified = new CdsExportVerifiedCaptures(27)) {
            scan(verified, capture, values, index);

            assertThat(verified.storable(values, index)).isFalse();
        }
    }

    @Test
    void shouldEraseEveryStoredMember_whenOneErasureFails() throws Exception {
        var capture = mixedCapture();
        var result = new CdsExportNativeCaptureReader.Result(Map.of(), 5, capture.values().length);
        var erased = new AtomicInteger();
        var verified = new CdsExportVerifiedCaptures(27);
        for (char id : new char[] {'1', '2', '3'})
            assertThat(
                            verified.store(
                                    member("SOURCE_VALUES", capture.values(), id),
                                    member("SOURCE_INDEX", capture.index(), id),
                                    MIXED_PLAN,
                                    member("SOURCE_REPLAY", new byte[] {1}, id),
                                    result,
                                    () -> {
                                        erased.incrementAndGet();
                                        if (id == '2') throw new IOException("CDS_EXPORT_STAGED_TRUNCATED");
                                    }))
                    .isTrue();

        // Every member is attempted and everything else is dropped before the failure is reported.
        assertThatThrownBy(verified::close)
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasRootCauseMessage("CDS_EXPORT_STAGED_TRUNCATED");
        assertThat(erased).hasValue(3);
        assertThat(verified.usable()).isFalse();
        verified.close();
        assertThat(erased).hasValue(3);
    }

    @Test
    void shouldNotRecord_whenTheFieldMetadataAlonePassesTheBudget() throws Exception {
        try (var verified = new CdsExportVerifiedCaptures(27, 32L << 20)) {
            var values = new CdsExportStagedFileDto("a".repeat(64), "SOURCE_VALUES", "0".repeat(64), 1);
            var index = new CdsExportStagedFileDto("b".repeat(64), "SOURCE_INDEX", "0".repeat(64), 1);
            long rows = (32L << 20) / CdsExportVerifiedCaptures.FIELD_BYTES + 1;
            assertThat(
                            verified.record(
                                    values,
                                    index,
                                    Map.of(
                                            "source",
                                            new CdsExportNativeCaptureReader.Table(
                                                    Set.of("value"), rows))))
                    .isNull();
            assertThat(
                            verified.record(
                                    values,
                                    index,
                                    Map.of(
                                            "source",
                                            new CdsExportNativeCaptureReader.Table(
                                                    Set.of("value"), Long.MAX_VALUE))))
                    .isNull();
        }
        assertThatThrownBy(() -> new CdsExportVerifiedCaptures(27, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotRecord_whenTheCaptureExceedsThePatientBudget() throws Exception {
        try (var verified = new CdsExportVerifiedCaptures(27)) {
            var large =
                    new CdsExportStagedFileDto(
                            "a".repeat(64),
                            "SOURCE_VALUES",
                            "0".repeat(64),
                            CdsExportVerifiedCaptures.MAXIMUM_BYTES + 1);
            var index = new CdsExportStagedFileDto("b".repeat(64), "SOURCE_INDEX", "0".repeat(64), 1);

            assertThat(verified.record(large, index, PLAN)).isNull();
            assertThatThrownBy(() -> new CdsExportVerifiedCaptures(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
