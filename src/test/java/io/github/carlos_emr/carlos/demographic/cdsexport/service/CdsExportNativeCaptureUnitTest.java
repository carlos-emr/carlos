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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeCaptureUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long LIMIT = 2L << 20;
    private final CdsExportNativeCaptureComposer composer = new CdsExportNativeCaptureComposer();
    private final CdsExportNativeCaptureReader reader = new CdsExportNativeCaptureReader();

    private record Capture(byte[] values, byte[] index) {}

    @Test
    void shouldPreserveEveryRawValueAndItsActualByteReceipt_withoutClosingCallerStreams()
            throws Exception {
        String text = "a".repeat(8191) + "😀é\r\n\u0000" + "b".repeat(65535) + "😀";
        Reader splitSurrogates =
                new StringReader(text) {
                    @Override
                    public int read(char[] chars, int offset, int count) throws IOException {
                        return super.read(chars, offset, Math.min(count, 1));
                    }
                };
        byte[] binary = new byte[131073];
        new java.util.Random(27).nextBytes(binary);
        var values = new TrackingOutput();
        var index = new TrackingOutput();
        var result =
                composer.compose(
                        values,
                        index,
                        Set.of("demographic", "empty_table"),
                        10,
                        LIMIT,
                        () -> {},
                        writer -> {
                            writer.beginTable(
                                    "demographic",
                                    Set.of("id", "missing", "empty", "text", "binary"),
                                    1);
                            writer.row(
                                    "0000000027",
                                    row -> {
                                        row.text("id", "0");
                                        row.text("missing", (String) null);
                                        row.text("empty", "");
                                        row.text("text", splitSurrogates);
                                        row.binary("binary", new ByteArrayInputStream(binary));
                                    });
                            writer.endTable();
                            writer.beginTable("empty_table", Set.of("id"), 0);
                            writer.endTable();
                        });
        assertThat(values.closed).isFalse();
        assertThat(index.closed).isFalse();
        var expected = new ByteArrayOutputStream();
        expected.write('0');
        expected.write(text.getBytes(StandardCharsets.UTF_8));
        expected.write(binary);
        assertThat(values.toByteArray()).isEqualTo(expected.toByteArray());
        var fields =
                nodes(index.toByteArray()).stream()
                        .filter(n -> "field".equals(n.path("kind").asText()))
                        .map(n -> n.get("field"))
                        .toList();
        assertThat(fields).hasSize(5);
        assertThat(fields.get(1).get("kind").asText()).isEqualTo("SQL_NULL");
        assertThat(fields.get(2).get("kind").asText()).isEqualTo("UTF8");
        long offset = 0;
        for (var field : fields) {
            int count = field.get("bytes").intValue();
            assertThat(field.get("offset").longValue()).isEqualTo(offset);
            byte[] slice =
                    Arrays.copyOfRange(values.toByteArray(), (int) offset, (int) offset + count);
            assertThat(field.get("sha256").asText()).isEqualTo(sha(slice));
            offset += count;
        }
        assertThat(result.fields()).isEqualTo(5);
        assertThat(result.valueBytes()).isEqualTo(values.size());
        var verified =
                reader.verify(
                        new ByteArrayInputStream(values.toByteArray()),
                        new ByteArrayInputStream(index.toByteArray()),
                        Map.of(
                                "demographic",
                                        new CdsExportNativeCaptureReader.Table(
                                                Set.of("id", "missing", "empty", "text", "binary"),
                                                1),
                                "empty_table",
                                        new CdsExportNativeCaptureReader.Table(Set.of("id"), 0)),
                        10,
                        LIMIT,
                        () -> {});
        assertThat(verified.fields()).isEqualTo(result.fields());
        assertThat(verified.rows()).isEqualTo(result.rows());
    }

    @Test
    void shouldPreserveExactNumericEvidence_andReadTheOriginalVersionOneTransport()
            throws Exception {
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        var number =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric(
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric.Codec
                                .IEEE754_BINARY32_V1,
                        "3dccccce");
        composer.compose(
                values,
                index,
                Set.of("drugs"),
                1,
                LIMIT,
                () -> {},
                writer -> {
                    writer.beginTable("drugs", Set.of("takemin"), 1);
                    writer.row("01", row -> row.numeric("takemin", "0.1", number));
                    writer.endTable();
                });
        var found =
                new AtomicReference<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportCapturedFieldDto>();
        var scope = Map.of("drugs", new CdsExportNativeCaptureReader.Table(Set.of("takemin"), 1));
        reader.scan(
                new ByteArrayInputStream(values.toByteArray()),
                new ByteArrayInputStream(index.toByteArray()),
                scope,
                1,
                LIMIT,
                () -> {},
                field -> {
                    found.set(field);
                    return java.io.OutputStream.nullOutputStream();
                });
        assertThat(found.get().exactNumeric()).isEqualTo(number);
        assertThat(values.toString(StandardCharsets.UTF_8)).isEqualTo("0.1");
        String current = index.toString(StandardCharsets.UTF_8);
        assertThatThrownBy(
                        () ->
                                reader.verify(
                                        new ByteArrayInputStream(values.toByteArray()),
                                        new ByteArrayInputStream(
                                                current.replace("\"protocol\":2", "\"protocol\":1")
                                                        .getBytes(StandardCharsets.UTF_8)),
                                        scope,
                                        1,
                                        LIMIT,
                                        () -> {}))
                .isInstanceOf(IOException.class);
        String legacy =
                current.replace("\"protocol\":2", "\"protocol\":1")
                        .replace(
                                ",\"exactNumeric\":{\"codec\":\"IEEE754_BINARY32_V1\",\"bits\":\"3dccccce\"}",
                                "");
        assertThat(
                        reader.verify(
                                        new ByteArrayInputStream(values.toByteArray()),
                                        new ByteArrayInputStream(
                                                legacy.getBytes(StandardCharsets.UTF_8)),
                                        scope,
                                        1,
                                        LIMIT,
                                        () -> {})
                                .fields())
                .isEqualTo(1);
        for (String invalid : List.of("7fc00000", "123"))
            assertThatThrownBy(
                            () ->
                                    reader.verify(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            new ByteArrayInputStream(
                                                    current.replace("3dccccce", invalid)
                                                            .getBytes(StandardCharsets.UTF_8)),
                                            scope,
                                            1,
                                            LIMIT,
                                            () -> {}))
                    .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseMissingTablesRowsColumnsAndDuplicateOrUnorderedKeys() {
        List<CdsExportNativeCaptureComposer.Content> invalid =
                List.of(
                        w -> {},
                        w -> {
                            w.beginTable("source", Set.of("value"), 1);
                            w.endTable();
                        },
                        w -> {
                            w.beginTable("source", Set.of("value", "other"), 1);
                            w.row("01", row -> row.text("value", "x"));
                        },
                        w -> {
                            w.beginTable("source", Set.of("value"), 1);
                            w.row("01", row -> row.text("extra", "x"));
                        },
                        w -> {
                            w.beginTable("source", Set.of("value"), 1);
                            w.row(
                                    "01",
                                    row -> {
                                        row.text("value", "x");
                                        row.text("value", "y");
                                    });
                        },
                        w -> {
                            w.beginTable("source", Set.of("value"), 2);
                            w.row("01", Map.of("value", "x"));
                            w.row("01", Map.of("value", "y"));
                        },
                        w -> {
                            w.beginTable("source", Set.of("value"), 2);
                            w.row("02", Map.of("value", "x"));
                            w.row("01", Map.of("value", "y"));
                        },
                        w -> {
                            w.beginTable("source", Set.of("value"), 0);
                            w.row("01", Map.of("value", "x"));
                        },
                        w -> {
                            w.beginTable("unexpected", Set.of("value"), 0);
                            w.endTable();
                        });
        for (var content : invalid)
            assertThatThrownBy(() -> capture(content)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldStayFailed_whenAnAdapterSwallowsSourceEncodingOrColumnFailure() {
        for (int fault = 0; fault < 4; fault++) {
            int mode = fault;
            assertThatThrownBy(
                            () ->
                                    capture(
                                            w -> {
                                                w.beginTable("source", Set.of("value"), 1);
                                                try {
                                                    w.row(
                                                            "01",
                                                            row -> {
                                                                try {
                                                                    if (mode == 0)
                                                                        row.text(
                                                                                "value",
                                                                                "bad\ud800");
                                                                    if (mode == 1)
                                                                        row.binary(
                                                                                "value",
                                                                                new InputStream() {
                                                                                    @Override
                                                                                    public int
                                                                                            read()
                                                                                                    throws
                                                                                                            IOException {
                                                                                        throw new IOException(
                                                                                                "SOURCE_FAILED");
                                                                                    }
                                                                                });
                                                                    if (mode == 2)
                                                                        row.text("extra", "hidden");
                                                                    if (mode == 3)
                                                                        row.binary(
                                                                                "value",
                                                                                new InputStream() {
                                                                                    @Override
                                                                                    public int
                                                                                            read() {
                                                                                        return 0;
                                                                                    }

                                                                                    @Override
                                                                                    public int read(
                                                                                            byte[]
                                                                                                    bytes,
                                                                                            int
                                                                                                    offset,
                                                                                            int
                                                                                                    count) {
                                                                                        return 0;
                                                                                    }
                                                                                });
                                                                } catch (IOException swallowed) {
                                                                    /* deliberately broken adapter */
                                                                }
                                                            });
                                                } catch (IOException swallowed) {
                                                    /* deliberately broken adapter */
                                                }
                                                try {
                                                    w.endTable();
                                                } catch (IOException swallowed) {
                                                    /* cannot recover */
                                                }
                                            }))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldStayFailed_whenOutputFailureOrCancellationIsSwallowed() {
        var broken =
                new ByteArrayOutputStream() {
                    @Override
                    public synchronized void write(byte[] bytes, int offset, int count) {
                        throw new IllegalStateException("OUTPUT_FAILED");
                    }
                };
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        broken,
                                        new ByteArrayOutputStream(),
                                        Set.of("source"),
                                        2,
                                        LIMIT,
                                        () -> {},
                                        w -> {
                                            w.beginTable("source", Set.of("value"), 1);
                                            try {
                                                w.row("01", Map.of("value", "x"));
                                            } catch (RuntimeException swallowed) {
                                                /* deliberately broken adapter */
                                            }
                                            try {
                                                w.endTable();
                                            } catch (IOException swallowed) {
                                                /* cannot recover */
                                            }
                                        }))
                .isInstanceOf(IOException.class);
        var cancel = new boolean[1];
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        new ByteArrayOutputStream(),
                                        new ByteArrayOutputStream(),
                                        Set.of("source"),
                                        2,
                                        LIMIT,
                                        () -> {
                                            if (cancel[0])
                                                throw new IllegalStateException("CANCELLED");
                                        },
                                        w -> {
                                            w.beginTable("source", Set.of("value"), 0);
                                            cancel[0] = true;
                                            try {
                                                w.endTable();
                                            } catch (RuntimeException swallowed) {
                                                cancel[0] = false;
                                            }
                                        }))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseEscapedAndForeignThreadWriters() throws Exception {
        var escaped = new CdsExportNativeCaptureComposer.RowWriter[1];
        capture(
                w -> {
                    w.beginTable("source", Set.of("value"), 1);
                    w.row(
                            "01",
                            row -> {
                                escaped[0] = row;
                                row.text("value", "x");
                            });
                    w.endTable();
                });
        assertThatThrownBy(() -> escaped[0].text("value", "later")).isInstanceOf(IOException.class);
        assertThatThrownBy(
                        () ->
                                capture(
                                        w -> {
                                            w.beginTable("source", Set.of("value"), 0);
                                            var error = new AtomicReference<Throwable>();
                                            Thread thread =
                                                    new Thread(
                                                            () -> {
                                                                try {
                                                                    w.endTable();
                                                                } catch (Throwable failure) {
                                                                    error.set(failure);
                                                                }
                                                            });
                                            thread.start();
                                            try {
                                                thread.join();
                                            } catch (InterruptedException interrupted) {
                                                throw new IOException(interrupted);
                                            }
                                            assertThat(error.get()).isInstanceOf(IOException.class);
                                        }))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldEnforceActualValueAndFieldLimits() {
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        new ByteArrayOutputStream(),
                                        new ByteArrayOutputStream(),
                                        Set.of("source"),
                                        1,
                                        3,
                                        () -> {},
                                        w -> {
                                            w.beginTable("source", Set.of("value"), 1);
                                            w.row("01", Map.of("value", "😀"));
                                            w.endTable();
                                        }))
                .hasMessage("CDS_EXPORT_CAPTURE_VALUE_LIMIT");
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        new ByteArrayOutputStream(),
                                        new ByteArrayOutputStream(),
                                        Set.of("source"),
                                        1,
                                        LIMIT,
                                        () -> {},
                                        w -> {
                                            w.beginTable("source", Set.of("a", "b"), 1);
                                            w.row("01", Map.of("a", "", "b", ""));
                                            w.endTable();
                                        }))
                .hasMessage("CDS_EXPORT_CAPTURE_FIELD_LIMIT");
    }

    @Test
    void shouldDetectTruncationTamperingTrailingDataAndChangedSourcePlans_inFinishedStreams()
            throws Exception {
        Capture valid = simple();
        verify(valid);
        assertThatThrownBy(() -> verify(new Capture(new byte[0], valid.index())))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> verify(new Capture(new byte[] {'y'}, valid.index())))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> verify(new Capture(new byte[] {'x', 0}, valid.index())))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(
                        () ->
                                verify(
                                        new Capture(
                                                valid.values(),
                                                Arrays.copyOf(
                                                        valid.index(), valid.index().length - 1))))
                .isInstanceOf(IOException.class);
        for (String extra : List.of("{}\n", "\n", "{}")) {
            assertThatThrownBy(
                            () ->
                                    verify(
                                            new Capture(
                                                    valid.values(),
                                                    (new String(
                                                                            valid.index(),
                                                                            StandardCharsets.UTF_8)
                                                                    + extra)
                                                            .getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOf(IOException.class);
        }
        assertThatThrownBy(
                        () ->
                                reader.verify(
                                        new ByteArrayInputStream(valid.values()),
                                        new ByteArrayInputStream(valid.index()),
                                        Map.of(
                                                "source",
                                                new CdsExportNativeCaptureReader.Table(
                                                        Set.of("value"), 2)),
                                        10,
                                        LIMIT,
                                        () -> {}))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectMalformedFieldMetadata_andUnknownOrDuplicateJsonFields() throws Exception {
        Capture valid = simple();
        for (String name :
                List.of(
                        "column",
                        "recordKey",
                        "table",
                        "kind",
                        "offset",
                        "bytes",
                        "sha256",
                        "extra")) {
            var nodes = nodes(valid.index());
            var field =
                    (ObjectNode)
                            nodes.stream()
                                    .filter(n -> n.has("field"))
                                    .findFirst()
                                    .orElseThrow()
                                    .get("field");
            switch (name) {
                case "column", "recordKey", "table", "sha256" -> field.put(name, "changed");
                case "kind" -> field.put(name, "SQL_NULL");
                case "offset", "bytes" -> field.put(name, field.get(name).asText());
                default -> field.put(name, "unrecognized");
            }
            Capture malformed = new Capture(valid.values(), encode(nodes));
            assertThatThrownBy(() -> verify(malformed)).isInstanceOf(IOException.class);
        }
        String duplicate =
                new String(valid.index(), StandardCharsets.UTF_8)
                        .replace("\"protocol\":2", "\"protocol\":2,\"protocol\":2");
        assertThatThrownBy(
                        () ->
                                verify(
                                        new Capture(
                                                valid.values(),
                                                duplicate.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectInvalidUtf8_evenWhenAnAttackerRewritesTheFieldHash() throws Exception {
        Capture valid = simple();
        var nodes = nodes(valid.index());
        byte[] invalid = {(byte) 0x80};
        var field =
                (ObjectNode)
                        nodes.stream()
                                .filter(n -> n.has("field"))
                                .findFirst()
                                .orElseThrow()
                                .get("field");
        field.put("sha256", sha(invalid));
        Capture malformed = new Capture(invalid, encode(nodes));
        assertThatThrownBy(() -> verify(malformed)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldIndependentlyRejectOmittedAndDuplicatedRowsAndFields() throws Exception {
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        composer.compose(
                values,
                index,
                Set.of("source"),
                10,
                LIMIT,
                () -> {},
                w -> {
                    w.beginTable("source", Set.of("value"), 2);
                    w.row("01", Map.of("value", "x"));
                    w.row("02", Map.of("value", "y"));
                    w.endTable();
                });
        List<JsonNode> original = nodes(index.toByteArray());
        for (int fault = 0; fault < 3; fault++) {
            var nodes = nodes(index.toByteArray());
            if (fault == 0) nodes.subList(2, 5).clear();
            if (fault == 1) ((ObjectNode) nodes.get(5)).put("recordKey", "01");
            if (fault == 2) nodes.add(4, nodes.get(3).deepCopy());
            var malformedIndex = new ByteArrayInputStream(encode(nodes));
            assertThatThrownBy(
                            () ->
                                    reader.verify(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            malformedIndex,
                                            Map.of(
                                                    "source",
                                                    new CdsExportNativeCaptureReader.Table(
                                                            Set.of("value"), 2)),
                                            10,
                                            LIMIT,
                                            () -> {}))
                    .isInstanceOf(IOException.class);
        }
        assertThat(original).hasSize(10);
    }

    @Test
    void shouldDeliverExactFinishedFieldBytes_withoutClosingSinks_orVerifyingCorruptFields()
            throws Exception {
        Capture valid = simple();
        var actual = new TrackingOutput();
        var verified = new ArrayList<String>();
        var sink =
                new CdsExportNativeCaptureReader.Fields() {
                    @Override
                    public java.io.OutputStream output(
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportCapturedFieldDto
                                    field) {
                        assertThat(field.table()).isEqualTo("source");
                        assertThat(field.column()).isEqualTo("value");
                        return actual;
                    }

                    @Override
                    public void verified(
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportCapturedFieldDto
                                    field) {
                        verified.add(field.sha256());
                    }
                };
        reader.scan(
                new ByteArrayInputStream(valid.values()),
                new ByteArrayInputStream(valid.index()),
                Map.of("source", new CdsExportNativeCaptureReader.Table(Set.of("value"), 1)),
                10,
                LIMIT,
                () -> {},
                sink);
        assertThat(actual.toString(StandardCharsets.UTF_8)).isEqualTo("x");
        assertThat(actual.closed).isFalse();
        assertThat(verified).containsExactly(sha(valid.values()));
        verified.clear();
        assertThatThrownBy(
                        () ->
                                reader.scan(
                                        new ByteArrayInputStream(new byte[] {'y'}),
                                        new ByteArrayInputStream(valid.index()),
                                        Map.of(
                                                "source",
                                                new CdsExportNativeCaptureReader.Table(
                                                        Set.of("value"), 1)),
                                        10,
                                        LIMIT,
                                        () -> {},
                                        sink))
                .hasMessage("CDS_EXPORT_CAPTURE_READBACK_INVALID");
        assertThat(verified).isEmpty();
    }

    @Test
    void shouldPropagateSinkFailures_withoutReturningACompleteSourceRead() throws Exception {
        Capture valid = simple();
        assertThatThrownBy(
                        () ->
                                reader.scan(
                                        new ByteArrayInputStream(valid.values()),
                                        new ByteArrayInputStream(valid.index()),
                                        Map.of(
                                                "source",
                                                new CdsExportNativeCaptureReader.Table(
                                                        Set.of("value"), 1)),
                                        10,
                                        LIMIT,
                                        () -> {},
                                        field ->
                                                new java.io.OutputStream() {
                                                    @Override
                                                    public void write(int value)
                                                            throws IOException {
                                                        throw new IOException(
                                                                "MAPPER_OUTPUT_FAILED");
                                                    }
                                                }))
                .hasMessage("MAPPER_OUTPUT_FAILED");
    }

    @Test
    void shouldDeliverUnmodifiedUtf8Bytes_whenAFieldCrossesTheDecoderCarryBoundary()
            throws Exception {
        String text = "a".repeat(65535) + "😀é";
        var valid =
                capture(
                        w -> {
                            w.beginTable("source", Set.of("value"), 1);
                            w.row("01", Map.of("value", text));
                            w.endTable();
                        });
        var output = new ByteArrayOutputStream();
        reader.scan(
                new ByteArrayInputStream(valid.values()),
                new ByteArrayInputStream(valid.index()),
                Map.of("source", new CdsExportNativeCaptureReader.Table(Set.of("value"), 1)),
                10,
                LIMIT,
                () -> {},
                field -> output);
        assertThat(output.toByteArray()).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
    }

    private Capture simple() throws IOException {
        return capture(
                w -> {
                    w.beginTable("source", Set.of("value"), 1);
                    w.row("01", Map.of("value", "x"));
                    w.endTable();
                });
    }

    private Capture capture(CdsExportNativeCaptureComposer.Content content) throws IOException {
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        composer.compose(values, index, Set.of("source"), 10, LIMIT, () -> {}, content);
        return new Capture(values.toByteArray(), index.toByteArray());
    }

    private void verify(Capture capture) throws IOException {
        reader.verify(
                new ByteArrayInputStream(capture.values()),
                new ByteArrayInputStream(capture.index()),
                Map.of("source", new CdsExportNativeCaptureReader.Table(Set.of("value"), 1)),
                10,
                LIMIT,
                () -> {});
    }

    private static List<JsonNode> nodes(byte[] index) throws IOException {
        var nodes = new ArrayList<JsonNode>();
        for (String line : new String(index, StandardCharsets.UTF_8).split("\n"))
            nodes.add(JSON.readTree(line));
        return nodes;
    }

    private static byte[] encode(List<JsonNode> nodes) throws IOException {
        var output = new ByteArrayOutputStream();
        for (var node : nodes) {
            output.write(JSON.writeValueAsBytes(node));
            output.write('\n');
        }
        return output.toByteArray();
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class TrackingOutput extends ByteArrayOutputStream {
        boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
