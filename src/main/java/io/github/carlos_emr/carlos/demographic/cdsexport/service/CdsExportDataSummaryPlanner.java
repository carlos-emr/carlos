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

import io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Groups the authenticated captured rows of the data-summary tables into exported records,
 * following {@link CdsExportDataSummaryRegistry} only. The plan is deterministic: the same
 * capture always yields the same records, in the same order, with the same identities, so the
 * inventory, the emitted Reports and the package ReadMe count the same records.
 *
 * @since 2026-11-13
 */
final class CdsExportDataSummaryPlanner {
    /** How a captured value was stored. */
    enum Storage {
        SQL_NULL,
        TEXT,
        BINARY
    }

    /**
     * One captured value. Text keeps its exact characters. A binary value keeps its stored size
     * and SHA-256, and its bytes only when its column's presentation needs them.
     */
    record Value(Storage storage, String text, long bytes, String sha256, byte[] content) {
        Value {
            Objects.requireNonNull(storage);
            if ((storage == Storage.TEXT) != (text != null)
                    || storage == Storage.BINARY
                            && (sha256 == null || !sha256.matches("[0-9a-f]{64}") || bytes < 0)
                    || content != null && (storage != Storage.BINARY || content.length != bytes))
                throw new IllegalArgumentException("CDS_EXPORT_DATA_SUMMARY_VALUE_INVALID");
        }

        static Value sqlNull() {
            return new Value(Storage.SQL_NULL, null, 0, null, null);
        }

        static Value text(String text) {
            return new Value(Storage.TEXT, Objects.requireNonNull(text), 0, null, null);
        }

        // Records compare arrays by identity; a value is equal by its bytes.
        @Override
        public boolean equals(Object other) {
            return other instanceof Value that
                    && storage == that.storage
                    && bytes == that.bytes
                    && Objects.equals(text, that.text)
                    && Objects.equals(sha256, that.sha256)
                    && java.util.Arrays.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storage, text, bytes, sha256, java.util.Arrays.hashCode(content));
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryPlanner.Value[protected]";
        }
    }

    /** One captured source row, its columns in name order. */
    record Row(String table, String recordKey, Map<String, Value> values) {
        Row {
            Objects.requireNonNull(table);
            Objects.requireNonNull(recordKey);
            values = Collections.unmodifiableMap(new TreeMap<>(values));
        }

        String text(String column) {
            var value = values.get(column);
            return value == null || value.storage() != Storage.TEXT ? null : value.text();
        }

        /**
         * The row as the field-assessment validator sees it: SQL NULL stays null, an empty value
         * (text or zero bytes) stays empty, and a binary value is represented by its digest.
         */
        CdsExportCapturedTextRowDto assessed() {
            var fields = new TreeMap<String, String>();
            values.forEach(
                    (column, value) ->
                            fields.put(
                                    column,
                                    switch (value.storage()) {
                                        case SQL_NULL -> null;
                                        case TEXT -> value.text();
                                        case BINARY ->
                                                value.bytes() == 0 ? "" : "sha256:" + value.sha256();
                                    }));
            return new CdsExportCapturedTextRowDto(table, recordKey, fields);
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryPlanner.Row[protected]";
        }
    }

    /**
     * One exported record: its kind, the table and identity of its root row (or of a child row
     * standing alone), every source row in it, its revision number among the form's revisions and
     * its clinical date when the source holds a valid one.
     */
    record Group(
            CdsExportDataSummaryRegistry.Kind kind,
            String table,
            String id,
            List<Row> rows,
            int revision,
            int revisions,
            CdsExportDateDto event,
            boolean detached,
            String notice) {
        Group {
            rows = List.copyOf(rows);
        }

        Group(
                CdsExportDataSummaryRegistry.Kind kind,
                String table,
                String id,
                List<Row> rows,
                int revision,
                int revisions,
                CdsExportDateDto event,
                boolean detached) {
            this(kind, table, id, rows, revision, revisions, event, detached, null);
        }

        /**
         * The same record with a fixed, PHI-free notice printed under the summary's statement
         * (an eForm whose rendered form could not be produced).
         */
        Group withNotice(String text) {
            return new Group(kind, table, id, rows, revision, revisions, event, detached, text);
        }

        /** The root row of the record (the row itself for a detached child). */
        Row root() {
            return rows.getFirst();
        }

        /** The CDS SubClass: the record kind, the form and its revision (at most 60 characters). */
        String subClass() {
            if (detached) return kind.label() + ": " + table + " record";
            if (revisions > 0) return table + " revision " + revision + " of " + revisions;
            return kind.label();
        }

        /** The document title. */
        String title() {
            if (detached)
                return "CARLOS data summary: " + kind.label() + " (" + table + " record)";
            if (revisions > 0)
                return "CARLOS data summary: "
                        + kind.label()
                        + " "
                        + table
                        + " (revision "
                        + revision
                        + " of "
                        + revisions
                        + ")";
            return "CARLOS data summary: " + kind.label();
        }

        boolean hasImage() {
            for (var row : rows) {
                var rule = CdsExportDataSummaryRegistry.rule(row.table());
                for (var entry : rule.binaries().entrySet())
                    if (entry.getValue() == CdsExportDataSummaryRegistry.BinaryPresentation.IMAGE
                            && row.values().get(entry.getKey()) != null
                            && row.values().get(entry.getKey()).content() != null) return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryPlanner.Group[protected]";
        }
    }

    private CdsExportDataSummaryPlanner() {}

    /**
     * Groups every row of the supplied data-summary tables.
     *
     * @param rows the captured rows of each registered table (absent tables count as empty)
     * @param zone the clinic's zone, for date-times stored as local wall-clock values
     * @return the records in a stable order: registration order of their table, then identity
     */
    static List<Group> plan(Map<String, List<Row>> rows, ZoneId zone) {
        for (String table : rows.keySet()) CdsExportDataSummaryRegistry.rule(table);
        var roots = new LinkedHashMap<String, Map<String, Builder>>();
        var detached = new ArrayList<Builder>();
        for (String table : CdsExportDataSummaryRegistry.tables()) {
            var rule = CdsExportDataSummaryRegistry.rule(table);
            if (!rule.root()) continue;
            var byId = new LinkedHashMap<String, Builder>();
            var ordered = new ArrayList<>(rows.getOrDefault(table, List.of()));
            ordered.sort(identityOrder(table));
            for (var row : ordered) {
                String id = identity(row);
                if (byId.putIfAbsent(id, new Builder(rule, row, id, false)) != null)
                    throw new IllegalStateException("CDS_EXPORT_DATA_SUMMARY_IDENTITY_DUPLICATE");
            }
            if (rule.revisioned()) {
                int n = 0;
                for (var builder : byId.values()) {
                    builder.revision = ++n;
                    builder.revisions = byId.size();
                }
            }
            roots.put(table, byId);
        }
        // Every placed row of each table with the record it joined: a root row is its own record,
        // a child row the record it joined or its own detached record. A child registered later
        // may name any of them as its parent.
        var placed = new HashMap<String, List<Map.Entry<Row, Builder>>>();
        for (var entry : roots.entrySet()) {
            var list = new ArrayList<Map.Entry<Row, Builder>>();
            for (var builder : entry.getValue().values()) list.add(Map.entry(builder.root, builder));
            placed.put(entry.getKey(), list);
        }
        var indices = new HashMap<CdsExportDataSummaryRegistry.Link, Map<List<String>, List<Builder>>>();
        for (String table : CdsExportDataSummaryRegistry.tables()) {
            var rule = CdsExportDataSummaryRegistry.rule(table);
            if (rule.root()) continue;
            var ordered = new ArrayList<>(rows.getOrDefault(table, List.of()));
            ordered.sort(identityOrder(table));
            var here = new ArrayList<Map.Entry<Row, Builder>>();
            for (var row : ordered) {
                // Distinct records, in the order found: two parent rows of the same record are
                // one candidate, two records are an ambiguous link.
                var matches = new java.util.LinkedHashSet<Builder>();
                for (var link : rule.links()) {
                    if (link.constantColumn() != null
                            && !link.parent().equals(row.text(link.constantColumn()))) continue;
                    var key = values(row, link.childColumns());
                    if (key == null) continue;
                    var index =
                            indices.computeIfAbsent(
                                    link,
                                    ignored -> {
                                        var byKey = new HashMap<List<String>, List<Builder>>();
                                        for (var parent :
                                                placed.getOrDefault(link.parent(), List.of())) {
                                            var parentKey =
                                                    values(parent.getKey(), link.parentColumns());
                                            if (parentKey != null)
                                                byKey.computeIfAbsent(
                                                                parentKey,
                                                                none -> new ArrayList<>())
                                                        .add(parent.getValue());
                                        }
                                        return byKey;
                                    });
                    matches.addAll(index.getOrDefault(key, List.of()));
                }
                if (matches.size() == 1) {
                    var record = matches.iterator().next();
                    record.children.add(row);
                    here.add(Map.entry(row, record));
                } else {
                    // An orphan, another chart's parent or an ambiguous link is never guessed
                    // into a record: the row is exported as its own record.
                    var alone = new Builder(rule, row, identity(row), true);
                    detached.add(alone);
                    here.add(Map.entry(row, alone));
                }
            }
            placed.put(table, here);
        }
        var result = new ArrayList<Group>();
        for (var byId : roots.values()) for (var builder : byId.values()) result.add(builder.build(zone));
        for (var builder : detached) result.add(builder.build(zone));
        return List.copyOf(result);
    }

    /** The record identity: the integer row identity, or the capture key of a composite one. */
    static String identity(Row row) {
        var plan = CdsExportDataSummaryReadDao.plan(row.table());
        if (plan.numericIdentity()) {
            String id = row.text(plan.keys().getFirst());
            if (id == null || !id.matches("-?(0|[1-9][0-9]{0,18})"))
                throw new IllegalStateException("CDS_EXPORT_DATA_SUMMARY_IDENTITY_INVALID");
            return id;
        }
        if (!row.recordKey().matches("[0-9]{20}"))
            throw new IllegalStateException("CDS_EXPORT_DATA_SUMMARY_IDENTITY_INVALID");
        return row.recordKey();
    }

    private static Comparator<Row> identityOrder(String table) {
        var plan = CdsExportDataSummaryReadDao.plan(table);
        if (plan.numericIdentity())
            return Comparator.comparingLong(row -> Long.parseLong(identity(row)));
        return Comparator.comparing(Row::recordKey);
    }

    /** The exact captured text of the link columns, or null when one is not text. */
    private static List<String> values(Row row, List<String> columns) {
        var result = new ArrayList<String>();
        for (String column : columns) {
            String value = row.text(column);
            if (value == null) return null;
            result.add(value);
        }
        return List.copyOf(result);
    }

    /**
     * The record's clinical date at its true precision: a valid date stays a date; a local
     * date-time becomes an offset date-time in the clinic's zone, or stays a date when that local
     * time is ambiguous or skipped. A missing or zero date gives no date (never an invented one).
     */
    static CdsExportDateDto event(Row row, CdsExportDataSummaryRegistry.Event event, ZoneId zone) {
        if (event == null) return null;
        String date = row.text(event.dateColumn());
        if (date == null) return null;
        try {
            // A legacy lab stored its service date as text in the basic form YYYYMMDD (CML).
            if (date.matches("[0-9]{8}"))
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.DATE,
                        LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE).toString());
            if (date.length() == 10) {
                var day = LocalDate.parse(date);
                String time = event.timeColumn() == null ? null : row.text(event.timeColumn());
                if (time != null && time.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}"))
                    return clock(day.atTime(LocalTime.parse(time)), zone);
                return new CdsExportDateDto(CdsExportDateDto.Precision.DATE, day.toString());
            }
            if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?"))
                return null;
            return clock(LocalDateTime.parse(date.substring(0, 19).replace(' ', 'T')), zone);
        } catch (DateTimeException invalid) {
            return null;
        }
    }

    private static CdsExportDateDto clock(LocalDateTime local, ZoneId zone) {
        if (local.getYear() < 1) return null;
        var offsets = zone == null ? List.<java.time.ZoneOffset>of() : zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1 || offsets.getFirst().getTotalSeconds() % 60 != 0)
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.DATE, local.toLocalDate().toString());
        return new CdsExportDateDto(
                CdsExportDateDto.Precision.DATE_TIME,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local.atOffset(offsets.getFirst())));
    }

    private static final class Builder {
        private final CdsExportDataSummaryRegistry.Rule rule;
        private final Row root;
        private final String id;
        private final boolean detached;
        private final List<Row> children = new ArrayList<>();
        private int revision, revisions;

        private Builder(CdsExportDataSummaryRegistry.Rule rule, Row root, String id, boolean detached) {
            this.rule = rule;
            this.root = root;
            this.id = id;
            this.detached = detached;
        }

        private Group build(ZoneId zone) {
            var all = new ArrayList<Row>();
            all.add(root);
            // Children in registration order of their table, then capture order.
            var order = new HashMap<String, Integer>();
            int index = 0;
            for (String table : CdsExportDataSummaryRegistry.tables()) order.put(table, index++);
            var sorted = new ArrayList<>(children);
            sorted.sort(
                    Comparator.<Row>comparingInt(row -> order.get(row.table()))
                            .thenComparing(Row::recordKey));
            all.addAll(sorted);
            return new Group(
                    rule.kind(),
                    rule.table(),
                    id,
                    all,
                    revision,
                    revisions,
                    event(root, rule.event(), zone),
                    detached);
        }
    }
}
