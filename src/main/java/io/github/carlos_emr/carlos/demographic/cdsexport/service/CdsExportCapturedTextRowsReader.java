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
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Streams one selected captured table into bounded immutable rows. Every source table/value
 * in the independent recipe is still verified, including both EOFs and current authorization.
 * Consumers must keep derived outputs private until the entire call succeeds. A row callback
 * is not whole-capture or clinical-completeness proof. No mutable clinical rows are queried.
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
            int maximumRowBytes,
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
                maximumRowBytes,
                control,
                rows);
    }

    /** One authenticated pass for related metadata tables; equal keys in different tables remain separate rows. */
    public CdsExportNativeCaptureDto forEachTable(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            Set<String> tables,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            int maximumRowBytes,
            CdsExportRunControl control,
            Rows rows)
            throws IOException {
        if (tables == null
                || tables.isEmpty()
                || !requiredTables.containsAll(tables)
                || maximumRowBytes < 1
                || maximumRowBytes > 67_108_864) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_SCOPE_INVALID");
        }
        var selected = Set.copyOf(tables);
        try (var fields = new Fields(selected, maximumRowBytes, rows)) {
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
        private final int maximum;
        private final Rows consumer;
        private final Map<String, String> values = new HashMap<>();
        private final Map<
                        String,
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric>
                numeric = new HashMap<>();
        private String table, key;
        private long total;
        private final Map<String, Long> rows = new HashMap<>();
        private CdsExportCapturedFieldDto field;
        private byte[] pending;
        private int position;

        private Fields(Set<String> tables, int maximum, Rows consumer) {
            this.tables = tables;
            this.maximum = maximum;
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
                    || next.bytes() > maximum - total
                    || values.containsKey(next.column())) invalid();
            field = next;
            total += next.bytes();
            pending = new byte[(int) next.bytes()];
            position = 0;
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    if (field != next || pending == null || position == pending.length) invalid();
                    pending[position++] = (byte) value;
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    Objects.checkFromIndexSize(offset, length, bytes.length);
                    if (field != next || pending == null || length > pending.length - position)
                        invalid();
                    System.arraycopy(bytes, offset, pending, position, length);
                    position += length;
                }
            };
        }

        @Override
        public void verified(CdsExportCapturedFieldDto next) throws IOException {
            if (!tables.contains(next.table())) return;
            if (field != next || pending == null || position != pending.length) invalid();
            try {
                String text =
                        next.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL
                                ? null
                                : StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                                        .decode(ByteBuffer.wrap(pending))
                                        .toString();
                values.put(next.column(), text);
                if (next.exactNumeric() != null) numeric.put(next.column(), next.exactNumeric());
            } finally {
                Arrays.fill(pending, (byte) 0);
                pending = null;
                field = null;
                position = 0;
            }
        }

        private void finish() throws IOException {
            if (field != null) invalid();
            if (key == null) return;
            consumer.accept(new CdsExportCapturedTextRowDto(table, key, values, numeric));
            rows.merge(table, 1L, Math::addExact);
            key = null;
            table = null;
            total = 0;
            values.clear();
            numeric.clear();
        }

        @Override
        public void close() {
            if (pending != null) Arrays.fill(pending, (byte) 0);
            pending = null;
            values.clear();
            numeric.clear();
        }

        private static void invalid() throws IOException {
            throw new IOException("CDS_EXPORT_NATIVE_ROW_INVALID");
        }
    }
}
