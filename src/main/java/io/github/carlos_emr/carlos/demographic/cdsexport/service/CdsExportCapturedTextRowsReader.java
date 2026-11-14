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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Streams one selected captured table into immutable rows. Every source table/value in the
 * independent recipe is still verified, including both EOFs and current authorization.
 * Consumers must keep derived outputs private until the entire call succeeds. A row callback
 * is not whole-capture or clinical-completeness proof. No mutable clinical rows are queried.
 *
 * <p>No row is refused for its size (#58). One row is held at a time, as the consumer receives
 * it; a value longer than the caller's buffering threshold is decoded as its bytes arrive rather
 * than collected whole first, so its bytes and its text are never both held whole. A row is
 * bounded by the capacity of its source columns.
 */
public final class CdsExportCapturedTextRowsReader {
    @FunctionalInterface
    public interface Rows {
        void accept(CdsExportCapturedTextRowDto row) throws IOException;
    }

    private final CdsExportNativeCaptureService captures;

    public CdsExportCapturedTextRowsReader(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
    }

    public CdsExportNativeCaptureDto forEach(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            String table,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            int bufferedValueBytes,
            CdsExportRunControl control,
            Rows rows)
            throws IOException {
        if (table == null)
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_SCOPE_INVALID");
        return forEachTable(
                lease,
                patient,
                requiredTables,
                Set.of(table),
                manifest,
                maximumFields,
                maximumBytes,
                bufferedValueBytes,
                control,
                rows);
    }

    /**
     * One authenticated pass for related metadata tables; equal keys in different tables remain
     * separate rows.
     *
     * @param bufferedValueBytes a text value of up to this many bytes is collected whole before it
     *     is decoded, a longer one is decoded as it arrives; it decides how a value is read, never
     *     whether a row is read
     */
    public CdsExportNativeCaptureDto forEachTable(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            Set<String> tables,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            int bufferedValueBytes,
            CdsExportRunControl control,
            Rows rows)
            throws IOException {
        if (tables == null
                || tables.isEmpty()
                || !requiredTables.containsAll(tables)
                || bufferedValueBytes < 1) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_SCOPE_INVALID");
        }
        var selected = Set.copyOf(tables);
        try (var fields = new Fields(selected, bufferedValueBytes, rows)) {
            var capture =
                    captures.scan(
                            lease,
                            patient,
                            requiredTables,
                            manifest,
                            maximumFields,
                            maximumBytes,
                            control,
                            fields);
            fields.finish();
            for (String table : selected) {
                var expected = capture.tables().get(table);
                if (expected == null || fields.rows.getOrDefault(table, 0L) != expected.rows())
                    throw new IOException("CDS_EXPORT_NATIVE_ROW_SCOPE_INVALID");
            }
            // The last row callback runs after the underlying scan returns. Recheck live
            // authority after that callback as well, before returning any completed result.
            captures.authorizeSources(lease, patient, requiredTables, control);
            if (capture.tables().containsKey("casemgmt_note"))
                captures.authorizeCapturedSources(lease, patient, requiredTables, capture, control);
            return capture;
        }
    }

    private static final class Fields
            implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private final Set<String> tables;
        private final int buffered;
        private final Rows consumer;
        private final Map<String, String> values = new HashMap<>();
        private final Map<
                        String,
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric>
                numeric = new HashMap<>();
        private String table, key;
        private final Map<String, Long> rows = new HashMap<>();
        private CdsExportCapturedFieldDto field;
        private Text pending;

        private Fields(Set<String> tables, int buffered, Rows consumer) {
            this.tables = tables;
            this.buffered = buffered;
            this.consumer = Objects.requireNonNull(consumer);
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto next) throws IOException {
            if (!tables.contains(next.table())) return OutputStream.nullOutputStream();
            if (field != null) invalid();
            if (!next.table().equals(table) || !next.recordKey().equals(key)) {
                finish();
                table = next.table();
                key = next.recordKey();
            }
            if (next.kind() == CdsExportCapturedFieldDto.Kind.BINARY
                    || values.containsKey(next.column())) invalid();
            field = next;
            pending = new Text(next.bytes(), buffered);
            return pending;
        }

        @Override
        public void verified(CdsExportCapturedFieldDto next) throws IOException {
            if (!tables.contains(next.table())) return;
            if (field != next || pending == null || !pending.complete()) invalid();
            try {
                String text =
                        next.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL
                                ? null
                                : pending.text();
                values.put(next.column(), text);
                if (next.exactNumeric() != null) numeric.put(next.column(), next.exactNumeric());
            } finally {
                pending.clear();
                pending = null;
                field = null;
            }
        }

        private void finish() throws IOException {
            if (field != null) invalid();
            if (key == null) return;
            consumer.accept(new CdsExportCapturedTextRowDto(table, key, values, numeric));
            rows.merge(table, 1L, Math::addExact);
            key = null;
            table = null;
            values.clear();
            numeric.clear();
        }

        @Override
        public void close() {
            if (pending != null) pending.clear();
            pending = null;
            values.clear();
            numeric.clear();
        }
    }

    /**
     * One captured text value as its verified bytes arrive. A value of up to the buffering
     * threshold is collected whole and decoded at once; a longer one is decoded chunk by chunk as
     * it arrives, so its raw bytes are never held whole beside its text. Either way malformed
     * UTF-8 is refused, and a value is never cut or refused for its length.
     */
    private static final class Text extends OutputStream {
        private static final int CHUNK = 8192;
        private final long expected;
        private long received;
        private boolean cleared;
        private byte[] whole;
        private CharsetDecoder decoder;
        private ByteBuffer input;
        private CharBuffer output;
        private StringBuilder decoded;

        Text(long expected, int buffered) throws IOException {
            if (expected < 0) invalid();
            this.expected = expected;
            if (expected <= buffered) whole = new byte[(int) expected];
            else {
                decoder = utf8();
                input = ByteBuffer.allocate(CHUNK);
                output = CharBuffer.allocate(CHUNK);
                decoded = new StringBuilder();
            }
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (cleared || length > expected - received) invalid();
            if (whole != null) System.arraycopy(bytes, offset, whole, (int) received, length);
            else
                for (int at = offset, end = offset + length; at < end; ) {
                    int count = Math.min(end - at, input.remaining());
                    input.put(bytes, at, count);
                    at += count;
                    decode(false);
                }
            received += length;
        }

        boolean complete() {
            return !cleared && received == expected;
        }

        String text() throws IOException {
            if (!complete()) invalid();
            if (whole != null) return utf8().decode(ByteBuffer.wrap(whole)).toString();
            decode(true);
            var flushed = decoder.flush(output);
            if (flushed.isError()) flushed.throwException();
            drain();
            return decoded.toString();
        }

        /** Decodes what has arrived; an incomplete character waits for the next bytes. */
        private void decode(boolean end) throws IOException {
            input.flip();
            while (true) {
                var result = decoder.decode(input, output, end);
                if (result.isError()) result.throwException();
                drain();
                if (result.isUnderflow()) break;
            }
            input.compact();
        }

        private void drain() {
            output.flip();
            decoded.append(output);
            output.clear();
        }

        void clear() {
            cleared = true;
            if (whole != null) Arrays.fill(whole, (byte) 0);
            if (input != null) Arrays.fill(input.array(), (byte) 0);
            if (output != null) Arrays.fill(output.array(), '\0');
            whole = null;
            decoded = null;
        }

        private static CharsetDecoder utf8() {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_ROW_INVALID");
    }
}
