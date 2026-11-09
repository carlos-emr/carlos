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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded native capture transport: concatenated exact values plus a separate JSON-lines index.
 * Both caller-owned outputs must be encrypted staging streams. No clinical mapping occurs here.
 * Table/column declarations and independent row counts prevent an adapter from silently omitting
 * a declared table, row or column. Source-schema qualification remains the reader's responsibility.
 */
public final class CdsExportNativeCaptureComposer {
    private static final ObjectMapper JSON = new ObjectMapper();

    @FunctionalInterface
    public interface Content {
        void capture(Writer writer) throws IOException;
    }

    @FunctionalInterface
    public interface RowContent {
        void capture(RowWriter writer) throws IOException;
    }

    public record Result(Map<String, Long> rows, long fields, long valueBytes) {
        public Result {
            rows = Map.copyOf(rows);
        }
    }

    public Result compose(
            OutputStream values,
            OutputStream index,
            Set<String> expectedTables,
            long maximumFields,
            long maximumValueBytes,
            Runnable checkpoint,
            Content content)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(index);
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(content);
        var tables = Set.copyOf(expectedTables);
        if (tables.isEmpty()
                || tables.size() > 256
                || tables.stream().anyMatch(table -> !table.matches("[A-Za-z0-9_]{1,64}"))
                || maximumFields < 1
                || maximumFields > 100_000_000
                || maximumValueBytes < 1) invalid();
        var writer =
                new Writer(values, index, tables, maximumFields, maximumValueBytes, checkpoint);
        try {
            writer.event(Map.of("kind", "header", "protocol", 2));
            content.capture(writer);
            writer.check();
            if (writer.table != null || !writer.rows.keySet().equals(tables)) invalid();
            writer.event(
                    Map.of(
                            "kind",
                            "end",
                            "rows",
                            writer.rows,
                            "fields",
                            writer.fields,
                            "valueBytes",
                            writer.bytes));
            index.flush();
            values.flush();
            checkpoint.run();
            return new Result(writer.rows, writer.fields, writer.bytes);
        } finally {
            writer.active = false;
        }
    }

    public static final class Writer {
        private final OutputStream values, index;
        private final Set<String> expected;
        private final long maximumFields, maximumBytes;
        private final Runnable checkpoint;
        private final Thread owner = Thread.currentThread();
        private final Map<String, Long> rows = new TreeMap<>();
        private boolean active = true;
        private volatile boolean failed;
        private String table, previousKey;
        private Set<String> columns;
        private long expectedRows, actualRows, fields, bytes;
        private boolean rowOpen;

        Writer(
                OutputStream values,
                OutputStream index,
                Set<String> expected,
                long maximumFields,
                long maximumBytes,
                Runnable checkpoint) {
            this.values = values;
            this.index =
                    new FilterOutputStream(index) {
                        @Override
                        public void write(byte[] data, int offset, int count) throws IOException {
                            out.write(data, offset, count);
                        }

                        @Override
                        public void close() throws IOException {
                            flush();
                        }
                    };
            this.expected = expected;
            this.maximumFields = maximumFields;
            this.maximumBytes = maximumBytes;
            this.checkpoint = checkpoint;
        }

        public void beginTable(String name, Set<String> selectedColumns, long rowCount)
                throws IOException {
            perform(
                    () -> {
                        var declared = Set.copyOf(selectedColumns);
                        if (table != null
                                || !expected.contains(name)
                                || rows.containsKey(name)
                                || rowCount < 0
                                || declared.isEmpty()
                                || declared.size() > 4096
                                || declared.stream()
                                        .anyMatch(column -> !column.matches("[A-Za-z0-9_]{1,128}")))
                            invalid();
                        table = name;
                        columns = declared;
                        expectedRows = rowCount;
                        actualRows = 0;
                        previousKey = null;
                        event(
                                Map.of(
                                        "kind",
                                        "table",
                                        "table",
                                        name,
                                        "columns",
                                        declared.stream().sorted().toList(),
                                        "expectedRows",
                                        rowCount));
                    });
        }

        public void row(String recordKey, Map<String, String> data) throws IOException {
            perform(
                    () -> {
                        if (columns == null || !data.keySet().equals(columns)) invalid();
                        row(
                                recordKey,
                                writer -> {
                                    for (var field : new TreeMap<>(data).entrySet())
                                        writer.text(field.getKey(), field.getValue());
                                });
                    });
        }

        /**
         * Keys are unique, strictly increasing canonical capture keys. Numeric adapters use a
         * fixed-width unsigned decimal key; original primary-key values remain captured columns.
         * This makes duplicate/replaced rows detectable without an unbounded in-memory key set.
         */
        public void row(String recordKey, RowContent content) throws IOException {
            perform(
                    () -> {
                        Objects.requireNonNull(content);
                        if (table == null
                                || rowOpen
                                || actualRows >= expectedRows
                                || recordKey == null
                                || recordKey.isEmpty()
                                || recordKey.length() > 2048
                                || recordKey
                                        .codePoints()
                                        .anyMatch(
                                                cp ->
                                                        Character.isISOControl(cp)
                                                                || cp >= 0xd800 && cp <= 0xdfff)
                                || previousKey != null && previousKey.compareTo(recordKey) >= 0)
                            invalid();
                        rowOpen = true;
                        var row = new RowWriter(this, recordKey);
                        event(Map.of("kind", "row", "recordKey", recordKey));
                        try {
                            content.capture(row);
                            check();
                            if (!row.seen.equals(columns)) invalid();
                        } finally {
                            row.active = false;
                            rowOpen = false;
                        }
                        previousKey = recordKey;
                        actualRows = Math.addExact(actualRows, 1);
                        event(Map.of("kind", "rowEnd"));
                    });
        }

        public void endTable() throws IOException {
            perform(
                    () -> {
                        if (table == null || rowOpen || actualRows != expectedRows) invalid();
                        event(Map.of("kind", "tableEnd", "table", table, "rows", actualRows));
                        rows.put(table, actualRows);
                        table = null;
                        columns = null;
                    });
        }

        private void check() throws IOException {
            if (!active || failed || Thread.currentThread() != owner) {
                failed = true;
                invalid();
            }
            checkpoint.run();
        }

        @FunctionalInterface
        private interface Operation {
            void run() throws IOException;
        }

        private void perform(Operation operation) throws IOException {
            try {
                check();
                operation.run();
            } catch (IOException | RuntimeException | Error failure) {
                failed = true;
                throw failure;
            }
        }

        private void event(Object value) throws IOException {
            check();
            JSON.writeValue(index, value);
            index.write('\n');
        }

        private void append(byte[] data, int offset, int count, MessageDigest digest)
                throws IOException {
            check();
            if (count > maximumBytes - bytes)
                throw new IOException("CDS_EXPORT_CAPTURE_VALUE_LIMIT");
            values.write(data, offset, count);
            digest.update(data, offset, count);
            bytes += count;
        }
    }

    public static final class RowWriter {
        private final Writer writer;
        private final String recordKey;
        private final Set<String> seen = new HashSet<>();
        private boolean active = true;

        RowWriter(Writer writer, String recordKey) {
            this.writer = writer;
            this.recordKey = recordKey;
        }

        public void text(String column, String value) throws IOException {
            if (value == null) {
                field(column, CdsExportCapturedFieldDto.Kind.SQL_NULL, null, null);
                return;
            }
            field(column, CdsExportCapturedFieldDto.Kind.UTF8, new StringReader(value), null);
        }

        /** Streams a large text value without holding the value or an encoded copy in memory. Caller owns the reader. */
        public void text(String column, Reader value) throws IOException {
            writer.perform(
                    () ->
                            field(
                                    column,
                                    CdsExportCapturedFieldDto.Kind.UTF8,
                                    Objects.requireNonNull(value),
                                    null));
        }

        /** Caller supplies a verified source stream; capture does not close it or infer a file extension. */
        public void binary(String column, InputStream value) throws IOException {
            writer.perform(
                    () ->
                            field(
                                    column,
                                    CdsExportCapturedFieldDto.Kind.BINARY,
                                    null,
                                    Objects.requireNonNull(value)));
        }

        /** Exact native numeric evidence lives only inside the authenticated encrypted index. */
        public void numeric(
                String column,
                String sqlDisplay,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric exact)
                throws IOException {
            writer.perform(
                    () -> {
                        if (sqlDisplay == null || exact == null) invalid();
                        captureField(
                                column,
                                CdsExportCapturedFieldDto.Kind.UTF8,
                                new StringReader(sqlDisplay),
                                null,
                                exact);
                    });
        }

        private void field(
                String column, CdsExportCapturedFieldDto.Kind kind, Reader text, InputStream binary)
                throws IOException {
            writer.perform(() -> captureField(column, kind, text, binary, null));
        }

        private void captureField(
                String column,
                CdsExportCapturedFieldDto.Kind kind,
                Reader text,
                InputStream binary,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric exact)
                throws IOException {
            if (!active || !writer.rowOpen || !writer.columns.contains(column) || !seen.add(column))
                invalid();
            if (writer.fields == writer.maximumFields)
                throw new IOException("CDS_EXPORT_CAPTURE_FIELD_LIMIT");
            long offset = writer.bytes;
            var digest = sha256();
            if (text != null) encode(text, writer, digest);
            if (binary != null) {
                byte[] buffer = new byte[65_536];
                try {
                    int count;
                    while ((count = binary.read(buffer)) != -1) {
                        if (count == 0) throw new IOException("CDS_EXPORT_CAPTURE_SOURCE_STALLED");
                        writer.append(buffer, 0, count, digest);
                    }
                } finally {
                    java.util.Arrays.fill(buffer, (byte) 0);
                }
            }
            var field =
                    new CdsExportCapturedFieldDto(
                            writer.table,
                            recordKey,
                            column,
                            kind,
                            offset,
                            writer.bytes - offset,
                            HexFormat.of().formatHex(digest.digest()),
                            exact);
            writer.event(Map.of("kind", "field", "field", field));
            writer.fields++;
        }
    }

    private static void encode(Reader source, Writer writer, MessageDigest digest)
            throws IOException {
        var encoder =
                StandardCharsets.UTF_8
                        .newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer chars = CharBuffer.allocate(8192);
        chars.limit(0);
        ByteBuffer bytes = ByteBuffer.allocate(32768);
        boolean end = false;
        try {
            while (!end) {
                writer.check();
                chars.compact();
                int count = source.read(chars.array(), chars.position(), chars.remaining());
                if (count == 0) throw new IOException("CDS_EXPORT_CAPTURE_SOURCE_STALLED");
                if (count < 0) end = true;
                else chars.position(chars.position() + count);
                chars.flip();
                while (true) {
                    bytes.clear();
                    var result = encoder.encode(chars, bytes, end);
                    writer.append(bytes.array(), 0, bytes.position(), digest);
                    if (result.isError()) result.throwException();
                    if (result.isUnderflow()) break;
                }
            }
            while (true) {
                bytes.clear();
                var result = encoder.flush(bytes);
                writer.append(bytes.array(), 0, bytes.position(), digest);
                if (result.isError()) result.throwException();
                if (result.isUnderflow()) break;
            }
        } finally {
            java.util.Arrays.fill(chars.array(), '\0');
            java.util.Arrays.fill(bytes.array(), (byte) 0);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_CAPTURE_INVALID");
    }
}
