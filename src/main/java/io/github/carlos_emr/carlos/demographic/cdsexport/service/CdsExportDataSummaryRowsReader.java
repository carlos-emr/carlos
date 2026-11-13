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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads the authenticated captured rows of the data-summary tables of one patient generation, and
 * the patient's demographic row for the summary header. Nothing is queried from the database:
 * every value is read back from the sealed capture with its length, SHA-256 and strict UTF-8
 * verified. Memory is bounded per patient; a chart over the bound is held with {@code
 * CDS_EXPORT_DATA_SUMMARY_SIZE_LIMIT} instead of stopping the job.
 *
 * @since 2026-11-13
 */
final class CdsExportDataSummaryRowsReader {
    /** All text of one patient's data summaries held at once. */
    static final long MAXIMUM_TEXT_BYTES = 64L << 20;

    /** One retained binary value (a photo or signature to embed, an e-mail body to decode). */
    static final long MAXIMUM_RETAINED_VALUE = 16L << 20;

    static final long MAXIMUM_RETAINED_BYTES = 32L << 20;

    static final String SIZE_LIMIT = "CDS_EXPORT_DATA_SUMMARY_SIZE_LIMIT";

    /** The captured rows of every data-summary table in the capture, and the demographic row. */
    record Result(
            Map<String, List<CdsExportDataSummaryPlanner.Row>> rows,
            Map<String, String> demographic) {
        Result {
            var copy = new LinkedHashMap<String, List<CdsExportDataSummaryPlanner.Row>>();
            rows.forEach((table, values) -> copy.put(table, List.copyOf(values)));
            rows = java.util.Collections.unmodifiableMap(copy);
            demographic = java.util.Collections.unmodifiableMap(new HashMap<>(demographic));
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryRowsReader.Result[protected]";
        }
    }

    private final CdsExportNativeCaptureService captures;

    CdsExportDataSummaryRowsReader(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
    }

    /** The registered data-summary tables of a capture scope. */
    static Set<String> tables(Set<String> captured) {
        var result = new TreeSet<String>();
        for (String table : captured)
            if (CdsExportDataSummaryRegistry.tables().contains(table)) result.add(table);
        return Set.copyOf(result);
    }

    Result read(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportStagedFileDto manifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var selected = new TreeSet<>(tables(requiredTables));
        if (!requiredTables.contains("demographic")) throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        selected.add("demographic");
        var fields = new Collector(Set.copyOf(selected));
        var capture =
                captures.scan(
                        lease,
                        patient,
                        requiredTables,
                        manifest,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        fields);
        fields.finish();
        for (String table : selected) {
            long rows =
                    table.equals("demographic")
                            ? fields.demographicRows
                            : fields.rows.getOrDefault(table, List.of()).size();
            if (capture.tables().get(table) == null || capture.tables().get(table).rows() != rows)
                throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        }
        if (fields.demographicRows != 1) throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        captures.authorizeSources(lease, patient, requiredTables, control);
        var rows = new LinkedHashMap<String, List<CdsExportDataSummaryPlanner.Row>>();
        for (String table : tables(requiredTables))
            rows.put(table, fields.rows.getOrDefault(table, List.of()));
        return new Result(rows, fields.demographic);
    }

    /**
     * Collects the verified captured values of the selected tables into rows, within the bounds
     * above. The caller calls {@link #finish()} after the scan returns.
     */
    static final class Collector implements CdsExportNativeCaptureReader.Fields {
        private final Set<String> tables;
        private final Map<String, List<CdsExportDataSummaryPlanner.Row>> rows = new HashMap<>();
        private final Map<String, String> demographic = new HashMap<>();
        private long demographicRows;
        private String table, key;
        private Map<String, CdsExportDataSummaryPlanner.Value> values = new HashMap<>();
        private CdsExportCapturedFieldDto pending;
        private ByteArrayOutputStream buffer;
        private long text, retained;

        Collector(Set<String> tables) {
            this.tables = Set.copyOf(tables);
        }

        Map<String, List<CdsExportDataSummaryPlanner.Row>> rows() {
            return rows;
        }

        Map<String, String> demographic() {
            return demographic;
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (!tables.contains(field.table())) return OutputStream.nullOutputStream();
            if (pending != null) invalid();
            if (!field.table().equals(table) || !field.recordKey().equals(key)) {
                finish();
                table = field.table();
                key = field.recordKey();
            }
            pending = field;
            buffer = null;
            switch (field.kind()) {
                case SQL_NULL -> {
                    return OutputStream.nullOutputStream();
                }
                case UTF8 -> {
                    text += field.bytes();
                    if (field.bytes() > Integer.MAX_VALUE - 8 || text > MAXIMUM_TEXT_BYTES)
                        throw new CdsExportMappingReviewException(SIZE_LIMIT);
                    buffer = new ByteArrayOutputStream((int) field.bytes());
                    return buffer;
                }
                case BINARY -> {
                    if (!retain(field)) return OutputStream.nullOutputStream();
                    retained += field.bytes();
                    if (retained > MAXIMUM_RETAINED_BYTES)
                        throw new CdsExportMappingReviewException(SIZE_LIMIT);
                    buffer = new ByteArrayOutputStream((int) field.bytes());
                    return buffer;
                }
                default -> throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
            }
        }

        /** A binary value is kept only when its column's presentation needs its bytes. */
        private static boolean retain(CdsExportCapturedFieldDto field) {
            if (field.table().equals("demographic")) return false;
            var rule = CdsExportDataSummaryRegistry.rule(field.table());
            return rule.binaries().containsKey(field.column())
                    && field.bytes() <= MAXIMUM_RETAINED_VALUE;
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!tables.contains(field.table())) return;
            if (pending != field) invalid();
            CdsExportDataSummaryPlanner.Value value =
                    switch (field.kind()) {
                        case SQL_NULL -> CdsExportDataSummaryPlanner.Value.sqlNull();
                        case UTF8 ->
                                CdsExportDataSummaryPlanner.Value.text(
                                        StandardCharsets.UTF_8
                                                .newDecoder()
                                                .onMalformedInput(CodingErrorAction.REPORT)
                                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                                .decode(ByteBuffer.wrap(buffer.toByteArray()))
                                                .toString());
                        case BINARY ->
                                new CdsExportDataSummaryPlanner.Value(
                                        CdsExportDataSummaryPlanner.Storage.BINARY,
                                        null,
                                        field.bytes(),
                                        field.sha256(),
                                        buffer == null ? null : buffer.toByteArray());
                    };
            if (values.putIfAbsent(field.column(), value) != null) invalid();
            pending = null;
            buffer = null;
        }

        void finish() throws IOException {
            if (pending != null) invalid();
            if (table == null) return;
            if (table.equals("demographic")) {
                demographicRows++;
                values.forEach((column, value) -> demographic.put(column, value.text()));
            } else
                rows.computeIfAbsent(table, ignored -> new ArrayList<>())
                        .add(new CdsExportDataSummaryPlanner.Row(table, key, values));
            values = new HashMap<>();
            table = null;
            key = null;
        }

        private static void invalid() throws IOException {
            throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        }
    }
}
