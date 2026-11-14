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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A capture too large to replay from memory replays from the member its one complete read wrote:
 * the same fields, order and bytes, every value still hashed against the digest that read
 * verified, and nothing accepted from a member or a value stream that is not exactly that read.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportCaptureReplayUnitTest {
    private static final long LIMIT = 4L << 20;
    private static final Map<String, CdsExportNativeCaptureReader.Table> PLAN =
            Map.of(
                    "drugs",
                    new CdsExportNativeCaptureReader.Table(Set.of("id", "note", "takemin"), 2),
                    "empty_table",
                    new CdsExportNativeCaptureReader.Table(Set.of("id"), 0),
                    "prescription",
                    new CdsExportNativeCaptureReader.Table(Set.of("id", "scan"), 1));

    private record Delivered(CdsExportCapturedFieldDto field, byte[] bytes) {}

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
            delivered.add(new Delivered(field, current.toByteArray()));
        }
    }

    /** One complete read with its members, the replay member it wrote and what it delivered. */
    private record Read(
            byte[] values,
            byte[] replay,
            boolean complete,
            CdsExportNativeCaptureReader.Result result,
            List<Delivered> delivered) {}

    /**
     * Two tables sharing a record key, SQL null and empty text, exact numeric evidence, a text
     * crossing the 64 KiB chunk and a binary of two chunks and a byte.
     */
    private static Read read() throws IOException {
        var number = new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY32_V1, "3dcccccd");
        byte[] binary = new byte[131_073];
        new Random(27).nextBytes(binary);
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        PLAN.keySet(),
                        100,
                        LIMIT,
                        () -> {},
                        writer -> {
                            writer.beginTable("drugs", Set.of("id", "note", "takemin"), 2);
                            for (String key : List.of("0000000001", "0000000002"))
                                writer.row(
                                        key,
                                        row -> {
                                            row.text("id", key);
                                            row.text(
                                                    "note",
                                                    key.endsWith("1")
                                                            ? null
                                                            : "é".repeat(35_000) + "😀");
                                            row.numeric("takemin", "0.1", number);
                                        });
                            writer.endTable();
                            writer.beginTable("empty_table", Set.of("id"), 0);
                            writer.endTable();
                            writer.beginTable("prescription", Set.of("id", "scan"), 1);
                            writer.row(
                                    "0000000002",
                                    row -> {
                                        row.text("id", "");
                                        row.binary("scan", new ByteArrayInputStream(binary));
                                    });
                            writer.endTable();
                        });
        var replay = new ByteArrayOutputStream();
        var writer = new CdsExportCaptureReplay.Writer(replay);
        var sink = new Sink();
        var result =
                new CdsExportNativeCaptureReader()
                        .scan(
                                new ByteArrayInputStream(values.toByteArray()),
                                new ByteArrayInputStream(index.toByteArray()),
                                PLAN,
                                100,
                                LIMIT,
                                () -> {},
                                writer.tee(sink));
        boolean complete = writer.finish(result);
        return new Read(values.toByteArray(), replay.toByteArray(), complete, result, sink.delivered);
    }

    private static CdsExportNativeCaptureReader.Result replay(
            Read read, byte[] replay, byte[] values, CdsExportNativeCaptureReader.Fields sink)
            throws IOException {
        return CdsExportCaptureReplay.read(
                new ByteArrayInputStream(replay),
                new ByteArrayInputStream(values),
                read.result(),
                100,
                LIMIT,
                () -> {},
                sink);
    }

    @Test
    void shouldDeliverEveryField_asTheCompleteReadDeliveredIt() throws Exception {
        var read = read();
        var sink = new Sink();
        var checkpoints = new AtomicInteger();

        var result =
                CdsExportCaptureReplay.read(
                        new ByteArrayInputStream(read.replay()),
                        new ByteArrayInputStream(read.values()),
                        read.result(),
                        100,
                        LIMIT,
                        checkpoints::incrementAndGet,
                        sink);

        assertThat(read.complete()).isTrue();
        assertThat(read.delivered()).hasSize(8);
        assertThat(result).isEqualTo(read.result());
        // Table, key, column, kind, offset, length, digest, numeric evidence and bytes all equal.
        assertThat(sink.delivered).usingRecursiveComparison().isEqualTo(read.delivered());
        assertThat(sink.delivered)
                .extracting(delivered -> delivered.field().table())
                .containsExactly(
                        "drugs", "drugs", "drugs", "drugs", "drugs", "drugs", "prescription",
                        "prescription");
        // The run control is consulted at the start, for every whole chunk and at the end.
        assertThat(checkpoints.get()).isGreaterThanOrEqualTo(5);
        // The member carries no value bytes: it is far smaller than the values it replays.
        assertThat(read.replay().length).isLessThan(1024);
    }

    @Test
    void shouldRefuseTheReplay_whenAValueIsNotTheVerifiedBytes() throws Exception {
        var read = read();
        for (int position : new int[] {0, 70_000, read.values().length - 1}) {
            byte[] changed = read.values().clone();
            changed[position] ^= 1;
            var sink = new Sink();

            assertThatThrownBy(() -> replay(read, read.replay(), changed, sink))
                    .isInstanceOf(IOException.class)
                    .hasMessage("CDS_EXPORT_CAPTURE_READBACK_INVALID");
            // The changed field, and every field after it, is never reported as verified.
            assertThat(sink.delivered.size()).isLessThan(read.delivered().size());
            assertThat(sink.delivered)
                    .usingRecursiveComparison()
                    .isEqualTo(read.delivered().subList(0, sink.delivered.size()));
        }
    }

    @Test
    void shouldRefuseTheReplay_whenEitherMemberIsNotTheWholeRead() throws Exception {
        var read = read();
        byte[] replay = read.replay();
        byte[] values = read.values();
        var cases = new ArrayList<byte[][]>();
        // A truncated, an extended and a corrupted replay member; truncated and extended values.
        cases.add(new byte[][] {java.util.Arrays.copyOf(replay, replay.length - 1), values});
        cases.add(new byte[][] {java.util.Arrays.copyOf(replay, replay.length + 1), values});
        cases.add(new byte[][] {java.util.Arrays.copyOf(replay, 3), values});
        cases.add(new byte[][] {new byte[0], values});
        cases.add(new byte[][] {replay, java.util.Arrays.copyOf(values, values.length - 1)});
        cases.add(new byte[][] {replay, java.util.Arrays.copyOf(values, values.length + 1)});
        for (int position = 0; position < replay.length; position += 7) {
            byte[] corrupted = replay.clone();
            corrupted[position] ^= 0x55;
            cases.add(new byte[][] {corrupted, values});
        }

        for (byte[][] members : cases) {
            var sink = new Sink();
            try {
                replay(read, members[0], members[1], sink);
                // Staging authenticates the member, so a changed byte cannot reach a replay. Even
                // so, the only change that still replays is to a name or key character, and no
                // change delivers a field whose bytes are not the bytes of its recorded digest.
                assertThat(sink.delivered).hasSameSizeAs(read.delivered());
                for (int i = 0; i < sink.delivered.size(); i++) {
                    assertThat(sink.delivered.get(i).bytes()).isEqualTo(read.delivered().get(i).bytes());
                    assertThat(sink.delivered.get(i).field().sha256())
                            .isEqualTo(read.delivered().get(i).field().sha256());
                }
            } catch (IOException refused) {
                assertThat(refused).hasMessage("CDS_EXPORT_CAPTURE_READBACK_INVALID");
            }
        }
    }

    @Test
    void shouldRefuseTheReplay_whenTheStoredReadExceedsTheCallersBounds() throws Exception {
        var read = read();

        for (long[] bounds :
                new long[][] {
                    {7, LIMIT}, {100, read.result().valueBytes() - 1}, {0, LIMIT}, {100, 0}
                })
            assertThatThrownBy(
                            () ->
                                    CdsExportCaptureReplay.read(
                                            new ByteArrayInputStream(read.replay()),
                                            new ByteArrayInputStream(read.values()),
                                            read.result(),
                                            bounds[0],
                                            bounds[1],
                                            () -> {},
                                            new Sink()))
                    .isInstanceOf(IOException.class)
                    .hasMessage("CDS_EXPORT_CAPTURE_READBACK_INVALID");
    }

    @Test
    void shouldStopTheReplay_whenTheRunControlRefusesToContinue() throws Exception {
        var read = read();
        var calls = new AtomicInteger();
        var sink = new Sink();

        assertThatThrownBy(
                        () ->
                                CdsExportCaptureReplay.read(
                                        new ByteArrayInputStream(read.replay()),
                                        new ByteArrayInputStream(read.values()),
                                        read.result(),
                                        100,
                                        LIMIT,
                                        () -> {
                                            if (calls.incrementAndGet() == 3)
                                                throw new java.util.concurrent.CancellationException(
                                                        "CDS_EXPORT_CANCELLED");
                                        },
                                        sink))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(sink.delivered.size()).isLessThan(read.delivered().size());
    }

    @Test
    void shouldNotKeepTheMember_whenTheReadItRecordsIsNotTheWholeGaplessRead() throws Exception {
        var read = read();
        var first = read.delivered().get(0).field();
        // The fifth field: the two before it carry bytes, so it does not follow the first.
        var later = read.delivered().get(4).field();

        // A field delivered out of its place in the value member.
        var gap = new CdsExportCaptureReplay.Writer(new ByteArrayOutputStream());
        var teed = gap.tee(new Sink());
        teed.output(first);
        teed.verified(first);
        teed.output(later);
        teed.verified(later);
        assertThat(gap.finish(new CdsExportNativeCaptureReader.Result(Map.of(), 2, later.offset() + later.bytes())))
                .isFalse();

        // Fewer fields, or fewer bytes, than the result the read returned.
        var partial = new CdsExportCaptureReplay.Writer(new ByteArrayOutputStream());
        var one = partial.tee(new Sink());
        one.output(first);
        one.verified(first);
        assertThat(partial.finish(read.result())).isFalse();
        var exact = new CdsExportCaptureReplay.Writer(new ByteArrayOutputStream());
        var same = exact.tee(new Sink());
        same.output(first);
        same.verified(first);
        assertThat(exact.finish(new CdsExportNativeCaptureReader.Result(Map.of(), 1, first.bytes())))
                .isTrue();
    }
}
