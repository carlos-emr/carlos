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
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Table(Set<String> columns, long rows) {
        public Table {
            columns = Set.copyOf(columns);
            if (columns.isEmpty() || columns.size() > 4096 || rows < 0
                    || columns.stream().anyMatch(column -> !column.matches("[A-Za-z0-9_]{1,128}"))) {
                throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_PLAN_INVALID");
            }
        }
    }
    public record Result(Map<String, Long> rows, long fields, long valueBytes) {
        public Result { rows = Map.copyOf(rows); }
    }
    /** Every field is delivered in bounded byte chunks; the sink remains caller-owned. */
    @FunctionalInterface public interface Fields {
        OutputStream output(CdsExportCapturedFieldDto field) throws IOException;
        /** Invoked only after the actual complete value passes strict UTF-8 and SHA-256 verification. */
        default void verified(CdsExportCapturedFieldDto field) throws IOException { }
    }
    public Result verify(InputStream values, InputStream index, Map<String, Table> expected,
            long maximumFields, long maximumBytes, Runnable checkpoint) throws IOException {
        return scan(values, index, expected, maximumFields, maximumBytes, checkpoint, field -> OutputStream.nullOutputStream());
    }
    /**
     * Streaming mapper input over the independently checked captured transport. All outputs
     * must stay unpublished until this complete read and independent clinical/output checks
     * pass. Per-field callbacks are not an assertion of whole-patient clinical completeness.
     */
    public Result scan(InputStream values, InputStream index, Map<String, Table> expected,
            long maximumFields, long maximumBytes, Runnable checkpoint, Fields output) throws IOException {
        Objects.requireNonNull(output);
        Objects.requireNonNull(values); Objects.requireNonNull(index); Objects.requireNonNull(checkpoint);
        var plan = Map.copyOf(expected);
        if (plan.isEmpty() || plan.size() > 256 || maximumFields < 1 || maximumFields > 100_000_000 || maximumBytes < 1
                || plan.keySet().stream().anyMatch(table -> !table.matches("[A-Za-z0-9_]{1,64}"))) invalid();
        var lines = new CdsExportJsonLines(index, 1_048_576, checkpoint); JsonNode header = lines.required();
        exact(header, "kind", "protocol"); kind(header, "header"); long protocol = number(header, "protocol"); if (protocol != 1 && protocol != 2) invalid();
        var counts = new TreeMap<String, Long>(); long fields = 0, offset = 0;
        JsonNode node;
        while (true) {
            node = lines.required(); String event = text(node, "kind");
            if ("end".equals(event)) break;
            exact(node, "kind", "table", "columns", "expectedRows"); kind(node, "table");
            String table = text(node, "table"); Table scope = plan.get(table);
            if (scope == null || counts.containsKey(table) || number(node, "expectedRows") != scope.rows()) invalid();
            var columns = node.get("columns");
            if (!columns.isArray() || !columns.equals(JSON.valueToTree(scope.columns().stream().sorted().toList()))) invalid();
            String previous = null;
            for (long row = 0; row < scope.rows(); row++) {
                node = lines.required(); exact(node, "kind", "recordKey"); kind(node, "row");
                String key = text(node, "recordKey");
                if (key.isEmpty() || key.length() > 2048 || key.codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp >= 0xd800 && cp <= 0xdfff)
                        || previous != null && previous.compareTo(key) >= 0) invalid();
                previous = key; var seen = new HashSet<String>();
                for (int column = 0; column < scope.columns().size(); column++) {
                    node = lines.required(); exact(node, "kind", "field"); kind(node, "field");
                    JsonNode fieldNode = node.get("field");
                    if (protocol == 2 && fieldNode.has("exactNumeric")) {
                        exact(fieldNode, "table", "recordKey", "column", "kind", "offset", "bytes", "sha256", "exactNumeric");
                        var numeric = fieldNode.get("exactNumeric"); exact(numeric, "codec", "bits"); text(numeric, "codec"); text(numeric, "bits");
                    } else exact(fieldNode, "table", "recordKey", "column", "kind", "offset", "bytes", "sha256");
                    for (String name : Set.of("table", "recordKey", "column", "kind", "sha256")) text(fieldNode, name);
                    number(fieldNode, "offset"); number(fieldNode, "bytes");
                    CdsExportCapturedFieldDto field;
                    try { field = JSON.treeToValue(fieldNode, CdsExportCapturedFieldDto.class); }
                    catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException malformed) { throw failure(); }
                    if (!field.table().equals(table) || !field.recordKey().equals(key) || !scope.columns().contains(field.column())
                            || !seen.add(field.column()) || field.offset() != offset || fields == maximumFields
                            || field.bytes() > maximumBytes - offset) invalid();
                    verifyValue(values, field, checkpoint, Objects.requireNonNull(output.output(field), "CDS_EXPORT_FIELD_SINK_REQUIRED"));
                    output.verified(field); offset += field.bytes(); fields++;
                }
                node = lines.required(); exact(node, "kind"); kind(node, "rowEnd");
            }
            node = lines.required(); exact(node, "kind", "table", "rows"); kind(node, "tableEnd");
            if (!text(node, "table").equals(table) || number(node, "rows") != scope.rows()) invalid();
            counts.put(table, scope.rows());
        }
        exact(node, "kind", "rows", "fields", "valueBytes");
        JsonNode rowCounts = node.get("rows");
        if (!counts.keySet().equals(plan.keySet()) || !rowCounts.isObject() || rowCounts.size() != counts.size()
                || number(node, "fields") != fields || number(node, "valueBytes") != offset || lines.next() != null) invalid();
        for (var count : counts.entrySet()) if (number(rowCounts, count.getKey()) != count.getValue()) invalid();
        checkpoint.run(); if (values.read() != -1) invalid(); checkpoint.run();
        return new Result(counts, fields, offset);
    }
    private static void verifyValue(InputStream input, CdsExportCapturedFieldDto field, Runnable checkpoint, OutputStream target) throws IOException {
        MessageDigest digest = sha256(); long remaining = field.bytes(); byte[] bytes = new byte[65_540];
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer chars = CharBuffer.allocate(65_536); int carried = 0;
        try {
            while (remaining > 0) {
                checkpoint.run(); int count = input.read(bytes, carried, (int) Math.min(65_536, remaining));
                if (count <= 0) invalid(); digest.update(bytes, carried, count); remaining -= count;
                if (field.kind() == CdsExportCapturedFieldDto.Kind.UTF8) {
                    var buffer = ByteBuffer.wrap(bytes, 0, carried + count);
                    decode(decoder, buffer, chars, remaining == 0);
                    target.write(bytes, carried, count);
                    carried = buffer.remaining(); if (carried > 3) invalid();
                    System.arraycopy(bytes, buffer.position(), bytes, 0, carried);
                } else target.write(bytes, 0, count);
            }
            if (field.kind() == CdsExportCapturedFieldDto.Kind.UTF8) {
                if (field.bytes() == 0) decode(decoder, ByteBuffer.wrap(bytes, 0, 0), chars, true);
                while (true) {
                    chars.clear(); var result = decoder.flush(chars);
                    if (result.isError()) result.throwException(); if (result.isUnderflow()) break;
                }
            }
            if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(field.sha256()))) invalid();
        } finally { Arrays.fill(bytes, (byte) 0); Arrays.fill(chars.array(), '\u0000'); }
    }
    private static void decode(java.nio.charset.CharsetDecoder decoder, ByteBuffer bytes, CharBuffer chars, boolean end) throws IOException {
        while (true) {
            chars.clear(); var result = decoder.decode(bytes, chars, end);
            if (result.isError()) result.throwException(); if (result.isUnderflow()) return;
        }
    }
    private static void exact(JsonNode node, String... names) throws IOException {
        if (node == null || !node.isObject() || node.size() != names.length) invalid();
        for (String name : names) if (!node.has(name)) invalid();
    }
    private static String text(JsonNode node, String name) throws IOException {
        JsonNode value = node.get(name); if (value == null || !value.isTextual()) invalid(); return value.textValue();
    }
    private static long number(JsonNode node, String name) throws IOException {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) invalid();
        return value.longValue();
    }
    private static void kind(JsonNode node, String expected) throws IOException { if (!text(node, "kind").equals(expected)) invalid(); }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static IOException failure() { return new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID"); }
    private static void invalid() throws IOException { throw failure(); }
}
