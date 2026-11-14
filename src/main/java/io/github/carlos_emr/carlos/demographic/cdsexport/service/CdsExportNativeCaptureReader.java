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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Independently checks the finished native transport, including exact bytes, UTF-8, sequence,
 * declared source scope, counts, offsets, hashes and both EOFs. Does not reuse composer logic.
 * A caller's table/count plan must itself come from the authorized source inventory; checking
 * this transport alone cannot prove that an adapter discovered every clinical source table.
 * Streams are caller-owned and must already be authenticated/decrypted by staging.
 */
public final class CdsExportNativeCaptureReader {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** The field members every captured field carries as JSON text, checked before it is built. */
    private static final java.util.List<String> TEXT_MEMBERS =
            java.util.List.of("table", "recordKey", "column", "kind", "sha256");

    private static final java.util.regex.Pattern TABLE =
            java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,64}");

    public record Table(Set<String> columns, long rows) {
        public Table {
            columns = Set.copyOf(columns);
            if (columns.isEmpty()
                    || columns.size() > 4096
                    || rows < 0
                    || columns.stream().anyMatch(column -> !identifier(column, 128))) {
                throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_PLAN_INVALID");
            }
        }
    }

    public record Result(Map<String, Long> rows, long fields, long valueBytes) {
        public Result {
            rows = Map.copyOf(rows);
        }
    }

    /** Every field is delivered in bounded byte chunks; the sink remains caller-owned. */
    @FunctionalInterface
    public interface Fields {
        OutputStream output(CdsExportCapturedFieldDto field) throws IOException;

        /** Invoked only after the actual complete value passes strict UTF-8 and SHA-256 verification. */
        default void verified(CdsExportCapturedFieldDto field) throws IOException {}
    }

    public Result verify(
            InputStream values,
            InputStream index,
            Map<String, Table> expected,
            long maximumFields,
            long maximumBytes,
            Runnable checkpoint)
            throws IOException {
        return scan(
                values,
                index,
                expected,
                maximumFields,
                maximumBytes,
                checkpoint,
                field -> OutputStream.nullOutputStream());
    }

    /**
     * Streaming mapper input over the independently checked captured transport. All outputs
     * must stay unpublished until this complete read and independent clinical/output checks
     * pass. Per-field callbacks are not an assertion of whole-patient clinical completeness.
     */
    public Result scan(
            InputStream values,
            InputStream index,
            Map<String, Table> expected,
            long maximumFields,
            long maximumBytes,
            Runnable checkpoint,
            Fields output)
            throws IOException {
        Objects.requireNonNull(output);
        Objects.requireNonNull(values);
        Objects.requireNonNull(index);
        Objects.requireNonNull(checkpoint);
        var plan = Map.copyOf(expected);
        if (plan.isEmpty()
                || plan.size() > 256
                || maximumFields < 1
                || maximumFields > 100_000_000
                || maximumBytes < 1
                || plan.keySet().stream().anyMatch(table -> !TABLE.matcher(table).matches()))
            invalid();
        var lines = new CdsExportJsonLines(index, 1_048_576, checkpoint);
        // One set of working buffers per read, zeroed after every value and released at the end.
        var work = new Work();
        // The value digest stays local to this read (it is not thread-safe, so it is never held in
        // a field) and is passed down with the working buffers.
        MessageDigest digest = sha256();
        try {
            return scan(
                    values, lines, plan, maximumFields, maximumBytes, checkpoint, output, work, digest);
        } finally {
            work.clear();
            digest.reset();
        }
    }

    private Result scan(
            InputStream values,
            CdsExportJsonLines lines,
            Map<String, Table> plan,
            long maximumFields,
            long maximumBytes,
            Runnable checkpoint,
            Fields output,
            Work work,
            MessageDigest digest)
            throws IOException {
        JsonNode header = lines.required();
        exact(header, "kind", "protocol");
        kind(header, "header");
        long protocol = number(header, "protocol");
        if (protocol != 1 && protocol != 2) invalid();
        var counts = new TreeMap<String, Long>();
        long fields = 0, offset = 0;
        JsonNode node;
        while (true) {
            node = lines.required();
            String event = text(node, "kind");
            if ("end".equals(event)) break;
            exact(node, "kind", "table", "columns", "expectedRows");
            kind(node, "table");
            String table = text(node, "table");
            Table scope = plan.get(table);
            if (scope == null
                    || counts.containsKey(table)
                    || number(node, "expectedRows") != scope.rows()) invalid();
            var columns = node.get("columns");
            if (!columns.isArray()
                    || !columns.equals(
                            JSON.valueToTree(scope.columns().stream().sorted().toList())))
                invalid();
            String previous = null;
            for (long row = 0; row < scope.rows(); row++) {
                node = lines.required();
                exact(node, "kind", "recordKey");
                kind(node, "row");
                String key = text(node, "recordKey");
                if (key.isEmpty()
                        || key.length() > 2048
                        || !safeKey(key)
                        || previous != null && previous.compareTo(key) >= 0) invalid();
                previous = key;
                var seen = new HashSet<String>();
                for (int column = 0; column < scope.columns().size(); column++) {
                    node = lines.required();
                    exact(node, "kind", "field");
                    kind(node, "field");
                    JsonNode fieldNode = node.get("field");
                    if (protocol == 2 && fieldNode.has("exactNumeric")) {
                        exact(
                                fieldNode,
                                "table",
                                "recordKey",
                                "column",
                                "kind",
                                "offset",
                                "bytes",
                                "sha256",
                                "exactNumeric");
                        var numeric = fieldNode.get("exactNumeric");
                        exact(numeric, "codec", "bits");
                        text(numeric, "codec");
                        text(numeric, "bits");
                    } else
                        exact(
                                fieldNode,
                                "table",
                                "recordKey",
                                "column",
                                "kind",
                                "offset",
                                "bytes",
                                "sha256");
                    for (String name : TEXT_MEMBERS) text(fieldNode, name);
                    number(fieldNode, "offset");
                    number(fieldNode, "bytes");
                    CdsExportCapturedFieldDto field = field(fieldNode);
                    if (!field.table().equals(table)
                            || !field.recordKey().equals(key)
                            || !scope.columns().contains(field.column())
                            || !seen.add(field.column())
                            || field.offset() != offset
                            || fields == maximumFields
                            || field.bytes() > maximumBytes - offset) invalid();
                    verifyValue(
                            values,
                            field,
                            checkpoint,
                            Objects.requireNonNull(
                                    output.output(field), "CDS_EXPORT_FIELD_SINK_REQUIRED"),
                            work,
                            digest);
                    output.verified(field);
                    offset += field.bytes();
                    fields++;
                }
                node = lines.required();
                exact(node, "kind");
                kind(node, "rowEnd");
            }
            node = lines.required();
            exact(node, "kind", "table", "rows");
            kind(node, "tableEnd");
            if (!text(node, "table").equals(table) || number(node, "rows") != scope.rows())
                invalid();
            counts.put(table, scope.rows());
        }
        exact(node, "kind", "rows", "fields", "valueBytes");
        JsonNode rowCounts = node.get("rows");
        if (!counts.keySet().equals(plan.keySet())
                || !rowCounts.isObject()
                || rowCounts.size() != counts.size()
                || number(node, "fields") != fields
                || number(node, "valueBytes") != offset
                || lines.next() != null) invalid();
        for (var count : counts.entrySet())
            if (number(rowCounts, count.getKey()) != count.getValue()) invalid();
        checkpoint.run();
        if (values.read() != -1) invalid();
        checkpoint.run();
        return new Result(counts, fields, offset);
    }

    /**
     * The working buffers of one read: the value chunk, the UTF-8 check's characters and the
     * decoder. Reused for every value of the read instead of being allocated per value;
     * the part a value used is zeroed as soon as that value is checked, and everything at the end.
     */
    private static final class Work {
        private final byte[] bytes = new byte[65_540];
        private final CharBuffer chars = CharBuffer.allocate(8_192);
        private final java.nio.charset.CharsetDecoder decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);

        void clear() {
            Arrays.fill(bytes, (byte) 0);
            Arrays.fill(chars.array(), '\u0000');
        }
    }

    private static void verifyValue(
            InputStream input,
            CdsExportCapturedFieldDto field,
            Runnable checkpoint,
            OutputStream target,
            Work work,
            MessageDigest digest)
            throws IOException {
        digest.reset();
        long remaining = field.bytes();
        byte[] bytes = work.bytes;
        var decoder = work.decoder;
        decoder.reset();
        CharBuffer chars = work.chars;
        int carried = 0;
        // The prefix of the working buffers this value wrote, zeroed in the finally block.
        int usedBytes = 0;
        boolean decoded = false;
        try {
            while (remaining > 0) {
                checkpoint.run();
                int count = input.read(bytes, carried, (int) Math.min(65_536, remaining));
                if (count <= 0) invalid();
                usedBytes = Math.max(usedBytes, carried + count);
                digest.update(bytes, carried, count);
                remaining -= count;
                if (field.kind() == CdsExportCapturedFieldDto.Kind.UTF8) {
                    var buffer = ByteBuffer.wrap(bytes, 0, carried + count);
                    decoded = true;
                    decode(decoder, buffer, chars, remaining == 0);
                    target.write(bytes, carried, count);
                    carried = buffer.remaining();
                    if (carried > 3) invalid();
                    System.arraycopy(bytes, buffer.position(), bytes, 0, carried);
                } else target.write(bytes, 0, count);
            }
            if (field.kind() == CdsExportCapturedFieldDto.Kind.UTF8) {
                decoded = true;
                if (field.bytes() == 0) decode(decoder, ByteBuffer.wrap(bytes, 0, 0), chars, true);
                while (true) {
                    chars.clear();
                    var result = decoder.flush(chars);
                    if (result.isError()) result.throwException();
                    if (result.isUnderflow()) break;
                }
            }
            if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(field.sha256())))
                invalid();
        } finally {
            digest.reset();
            Arrays.fill(bytes, 0, usedBytes, (byte) 0);
            // A UTF-8 value never decodes to more characters than it has bytes.
            if (decoded) Arrays.fill(chars.array(), 0, Math.min(chars.capacity(), usedBytes), '\u0000');
        }
    }

    /**
     * Builds the field from its already checked members. Only a field with an exact numeric value
     * goes through JSON binding; every other field is built directly with the same validation.
     */
    private static CdsExportCapturedFieldDto field(JsonNode node) throws IOException {
        try {
            if (node.has("exactNumeric"))
                return JSON.treeToValue(node, CdsExportCapturedFieldDto.class);
            return new CdsExportCapturedFieldDto(
                    node.get("table").textValue(),
                    node.get("recordKey").textValue(),
                    node.get("column").textValue(),
                    CdsExportCapturedFieldDto.Kind.valueOf(node.get("kind").textValue()),
                    node.get("offset").longValue(),
                    node.get("bytes").longValue(),
                    node.get("sha256").textValue());
        } catch (RuntimeException
                | com.fasterxml.jackson.core.JsonProcessingException malformed) {
            throw failure();
        }
    }

    /**
     * {@code [A-Za-z0-9_]{1,maximum}} without a regular expression: every read of a capture checks
     * every column name of every table it plans.
     */
    static boolean identifier(String name, int maximum) {
        int length = name.length();
        if (length < 1 || length > maximum) return false;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_'))
                return false;
        }
        return true;
    }

    /** No ISO control character and no surrogate code point, as {@link CdsExportCapturedFieldDto}. */
    private static boolean safeKey(String key) {
        for (int i = 0; i < key.length(); ) {
            int cp = key.codePointAt(i);
            if (Character.isISOControl(cp) || cp >= 0xd800 && cp <= 0xdfff) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    private static void decode(
            java.nio.charset.CharsetDecoder decoder,
            ByteBuffer bytes,
            CharBuffer chars,
            boolean end)
            throws IOException {
        while (true) {
            chars.clear();
            var result = decoder.decode(bytes, chars, end);
            if (result.isError()) result.throwException();
            if (result.isUnderflow()) return;
        }
    }

    private static void exact(JsonNode node, String... names) throws IOException {
        if (node == null || !node.isObject() || node.size() != names.length) invalid();
        for (String name : names) if (!node.has(name)) invalid();
    }

    private static String text(JsonNode node, String name) throws IOException {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) invalid();
        return value.textValue();
    }

    private static long number(JsonNode node, String name) throws IOException {
        JsonNode value = node.get(name);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.longValue() < 0) invalid();
        return value.longValue();
    }

    private static void kind(JsonNode node, String expected) throws IOException {
        if (!text(node, "kind").equals(expected)) invalid();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static IOException failure() {
        return new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
    }

    private static void invalid() throws IOException {
        throw failure();
    }
}
