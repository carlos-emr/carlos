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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Table-driven, patient-scoped reads of the reviewed data-summary (DOCUMENT) and listed (LISTED)
 * tables of the source catalogue. The read plan of each table is derived from the pinned physical
 * contract, never from client input: every physical column, the primary key (or the one reviewed
 * NOT NULL unique key of a table without one, or, for the reviewed keyless legacy lab segments,
 * the position in the order of every column) for ordering and row identity, and the reviewed
 * patient scope (a direct owner column, or a parent chain from {@link
 * CdsExportIndirectPatientScopeReadSupport#owned}). A table with clinical remainders reads only
 * its rows with a stored remainder.
 *
 * <p>Values keep their exact bytes: character, date and number columns are read as {@code CAST(..
 * AS BINARY)} (utf8mb4 by contract), binary columns as their raw bytes; SQL NULL stays distinct
 * from empty. A page returns at most a bounded number of rows and inline bytes; a larger value is
 * read in byte ranges within the same snapshot and verified against the SHA-256 the page computed.
 * Wide legacy form tables are read in column groups whose row keys must agree exactly.
 *
 * <p>A table of the other province's profile that this database does not have reads as absent
 * (zero rows); the source catalogue check in the same capture proves the installed profile. The
 * non-transactional {@code formRourke2009} is read only under the pinned source read lock.
 *
 * @since 2026-11-13
 */
@Repository
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class CdsExportDataSummaryReadDao {
    /** Sentinel engine of a profile table this installation does not have. */
    public static final String ABSENT = "ABSENT";

    private static final int COLUMN_GROUP = 512;

    /**
     * Reviewed row identity of the data-summary tables that have no primary key: the NOT NULL
     * unique key {@code id} of the BC antenatal record 2020 and {@code form_data} of its pages
     * (V1.0.1__bc_schema.sql).
     * Uniqueness is still proven at read time: the capture refuses a page sequence whose row count
     * differs from the independent count.
     */
    private static final Map<String, List<String>> UNIQUE_KEYS =
            Map.of(
                    "formBCAR2020", List.of("id"),
                    "formBCAR2020Data", List.of("form_id", "page_no", "field"),
                    "formBCAR2020Text", List.of("form_id", "page_no", "field"));

    /**
     * Reviewed data-summary tables with no key at all: the segments of a legacy MDS lab report,
     * which the old loader wrote with only a non-unique {@code segmentID} index. Two rows may be
     * byte-for-byte identical. Their rows are read whole, ordered by every column, and paged by
     * position inside the one repeatable-read capture snapshot, so the order is total up to
     * identical rows and each row is captured exactly once; the capture names them by ordinal.
     * Every column must be short enough to be read inline, since a row with no key cannot be
     * addressed again for a byte range.
     */
    private static final Set<String> KEYLESS =
            Set.of(
                    "mdsNTE", "mdsOBR", "mdsOBX", "mdsPID", "mdsPV1", "mdsZMC", "mdsZMN",
                    "mdsZRG");

    /** The longest stored value of a keyless table: 255 four-byte characters. */
    private static final int KEYLESS_VALUE_BYTES = 1_020;

    @PersistenceContext private EntityManager entityManager;

    /** The immutable read recipe of one reviewed table. */
    public record Plan(
            String table,
            List<String> columns,
            List<String> keys,
            Set<String> binary,
            Set<String> integerKeys) {
        public Plan {
            columns = List.copyOf(columns);
            keys = List.copyOf(keys);
            binary = Set.copyOf(binary);
            integerKeys = Set.copyOf(integerKeys);
        }

        /** True when the row identity is one integer column, so it is also the capture key. */
        public boolean numericIdentity() {
            return keys.size() == 1 && integerKeys.contains(keys.getFirst());
        }

        /**
         * True for a reviewed table with no key: its rows are paged by position (a page row's key
         * is the number of rows read so far) and named by ordinal in the capture.
         */
        public boolean keyless() {
            return keys.isEmpty();
        }
    }

    /** One page row: the exact key values in key order and every column's descriptor. */
    public record Row(List<Object> key, Map<String, CdsExportRawCellDto> cells) {
        public Row {
            key = Collections.unmodifiableList(new ArrayList<>(key));
            cells = Collections.unmodifiableMap(new TreeMap<>(cells));
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryReadDao.Row[protected]";
        }
    }

    /**
     * The read plan of a DOCUMENT table, from the first pinned profile that has it.
     *
     * @throws IllegalArgumentException for a table that is not a reviewed data-summary table
     */
    public static Plan plan(String table) {
        var catalogue = CdsExportSourceCatalogue.current();
        if (catalogue.tables().get(table) != CdsExportSourceCatalogue.Disposition.DOCUMENT)
            throw new IllegalArgumentException("CDS_EXPORT_DATA_SUMMARY_TABLE_UNKNOWN");
        var schema = catalogue.physical(table).orElseThrow(CdsExportDataSummaryReadDao::invalid);
        // Every profile that has the table must describe the same columns and key; only the
        // engine of the one legacy non-transactional table may differ.
        for (var profile : catalogue.profiles()) {
            var other = profile.tables().get(table);
            if (other != null
                    && (!other.columns().equals(schema.columns())
                            || !other.primaryKeyColumns().equals(schema.primaryKeyColumns())))
                throw invalid();
        }
        var columns = new ArrayList<String>();
        var binary = new java.util.HashSet<String>();
        var integers = new java.util.HashSet<String>();
        for (var column : schema.columns()) {
            if (column.charset() != null && !column.charset().equals("utf8mb4")) throw invalid();
            columns.add(column.name());
            String type = column.type();
            if (type.matches("(?:tiny|medium|long)?blob|(?:var)?binary\\([0-9]+\\)"))
                binary.add(column.name());
            if (type.matches(
                    "(?:tinyint|smallint|mediumint|int|bigint)\\([0-9]{1,3}\\)(?: unsigned)?"))
                integers.add(column.name());
        }
        if (KEYLESS.contains(table)) {
            if (!schema.primaryKeyColumns().isEmpty()
                    || columns.size() > 64
                    || !binary.isEmpty()
                    || schema.columns().stream().anyMatch(column -> !inline(column.type())))
                throw invalid();
            return new Plan(table, columns, List.of(), binary, integers);
        }
        var keys =
                schema.primaryKeyColumns().isEmpty()
                        ? UNIQUE_KEYS.get(table)
                        : schema.primaryKeyColumns();
        if (keys == null || keys.isEmpty() || keys.size() > 4) throw invalid();
        for (String key : keys) {
            var column =
                    schema.columns().stream()
                            .filter(c -> c.name().equals(key))
                            .findFirst()
                            .orElseThrow(CdsExportDataSummaryReadDao::invalid);
            if (!"NO".equals(column.nullable())
                    || !integers.contains(key)
                            && !column.type().matches("(?:var)?char\\([0-9]{1,3}\\)"))
                throw invalid();
        }
        return new Plan(table, columns, keys, binary, integers);
    }

    /**
     * The actual physical structure of the plan's table, or an {@link #ABSENT} descriptor when this
     * database has no such table. Whole-table column visibility is proven first, without returning
     * values.
     */
    // Identifiers come only from the pinned catalogue plan.
    @SuppressWarnings("java:S2077")
    public CdsExportTableSchemaDto schema(Plan plan) {
        String engine = engine(plan);
        if (engine == null) return new CdsExportTableSchemaDto(plan.table(), ABSENT, List.of(), List.of());
        // Inside a capture's schema pass the same structure comes from the transaction's one
        // complete catalogue read (CdsExportPhysicalSchemaReadSupport says how visibility and
        // stability are then kept).
        var pass = CdsExportPhysicalSchemaReadSupport.current();
        if (pass != null) {
            var found = pass.named(plan.table());
            if (found.size() != 1
                    || found.getFirst().columns().isEmpty()
                    || found.getFirst().columns().size() > 4096
                    || found.getFirst().primaryKeyColumns().size() > 16) throw invalid();
            if (pass.unprobed(plan.table())) {
                visible(plan);
                pass.probed(plan.table());
            }
            return new CdsExportTableSchemaDto(
                    plan.table(),
                    engine,
                    found.getFirst().columns(),
                    found.getFirst().primaryKeyColumns());
        }
        visible(plan);
        var columns =
                rows(
                        CdsExportPatientTableReadSupport.query(
                                        entityManager,
                                        "select COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,CHARACTER_SET_NAME,"
                                                + "COLLATION_NAME,NULLIF(GENERATION_EXPRESSION,'')"
                                                + " from information_schema.COLUMNS where"
                                                + " TABLE_SCHEMA=DATABASE() and TABLE_NAME=:table")
                                .setParameter("table", plan.table())
                                .setMaxResults(4097));
        var keys =
                rows(
                        CdsExportPatientTableReadSupport.query(
                                        entityManager,
                                        "select COLUMN_NAME,ORDINAL_POSITION from"
                                                + " information_schema.KEY_COLUMN_USAGE where"
                                                + " TABLE_SCHEMA=DATABASE() and TABLE_NAME=:table and"
                                                + " CONSTRAINT_NAME='PRIMARY' order by"
                                                + " ORDINAL_POSITION")
                                .setParameter("table", plan.table())
                                .setMaxResults(17));
        if (columns.isEmpty() || columns.size() > 4096 || keys.size() > 16) throw invalid();
        var captured = new ArrayList<CdsExportTableSchemaDto.Column>();
        for (var column : columns) {
            if (column.length != 6) throw invalid();
            captured.add(
                    new CdsExportTableSchemaDto.Column(
                            (String) column[0],
                            (String) column[1],
                            (String) column[2],
                            (String) column[3],
                            (String) column[4],
                            (String) column[5]));
        }
        captured.sort(java.util.Comparator.comparing(CdsExportTableSchemaDto.Column::name));
        var primary = new ArrayList<String>();
        for (var key : keys) primary.add((String) key[0]);
        visible(plan);
        return new CdsExportTableSchemaDto(plan.table(), engine, captured, primary);
    }

    /** The selected patient's rows; zero for a table this database does not have. */
    public long count(Plan plan, int patient) {
        patient(patient);
        if (!readable(plan)) return 0;
        var found =
                rows(
                        CdsExportPatientTableReadSupport.query(
                                        entityManager,
                                        "select count(*),1 from `"
                                                + plan.table()
                                                + "` d where "
                                                + predicate(plan.table()))
                                .setParameter("patient", patient));
        if (found.size() != 1) throw invalid();
        long count = number(found.getFirst()[0]);
        if (count < 0) throw invalid();
        return count;
    }

    /**
     * The next page of the selected patient's rows in row-identity order, after {@code after} (the
     * previous page's last key, or null). Every column group of the page must return exactly the
     * same keys in the same order.
     */
    public List<Row> page(Plan plan, int patient, List<Object> after, int limit) {
        patient(patient);
        if (plan.keyless()) return keylessPage(plan, patient, after, limit);
        if (limit < 1 || limit > 1024 || after != null && after.size() != plan.keys().size())
            throw invalid();
        if (!readable(plan)) return List.of();
        String keyList = keyList(plan);
        var keyQuery =
                new StringBuilder("select ")
                        .append(keyList)
                        .append(",1 from `")
                        .append(plan.table())
                        .append("` d where ")
                        .append(predicate(plan.table()));
        if (after != null) keyQuery.append(" and ").append(compare(plan, ">", "a"));
        keyQuery.append(" order by ").append(keyList);
        var query =
                CdsExportPatientTableReadSupport.query(entityManager, keyQuery.toString())
                        .setParameter("patient", patient)
                        .setMaxResults(limit);
        if (after != null) bind(query, plan, "a", after);
        var keys = new ArrayList<List<Object>>();
        for (var row : rows(query)) {
            if (row.length != plan.keys().size() + 1) throw invalid();
            keys.add(key(plan, row, 0));
        }
        if (keys.isEmpty()) return List.of();
        var cells = new ArrayList<TreeMap<String, CdsExportRawCellDto>>();
        for (int i = 0; i < keys.size(); i++) cells.add(new TreeMap<>());
        for (int start = 0; start < plan.columns().size(); start += COLUMN_GROUP) {
            var group =
                    plan.columns().subList(start, Math.min(plan.columns().size(), start + COLUMN_GROUP));
            int inline = Math.min(16_384, 1_048_576 / group.size());
            var projection = new StringBuilder("select ").append(keyList);
            for (String column : group) {
                String value = "CAST(d.`" + column + "` AS BINARY)";
                projection
                        .append(",d.`")
                        .append(column)
                        .append("` IS NULL,OCTET_LENGTH(")
                        .append(value)
                        .append("),SHA2(")
                        .append(value)
                        .append(",256),CASE WHEN OCTET_LENGTH(")
                        .append(value)
                        .append(")<=:inline THEN ")
                        .append(value)
                        .append(" ELSE NULL END");
            }
            projection
                    .append(" from `")
                    .append(plan.table())
                    .append("` d where ")
                    .append(predicate(plan.table()))
                    .append(" and ")
                    .append(compare(plan, ">=", "f"))
                    .append(" and ")
                    .append(compare(plan, "<=", "l"))
                    .append(" order by ")
                    .append(keyList);
            var values =
                    CdsExportPatientTableReadSupport.query(entityManager, projection.toString())
                            .setParameter("patient", patient)
                            .setParameter("inline", inline)
                            .setMaxResults(keys.size() + 1);
            bind(values, plan, "f", keys.getFirst());
            bind(values, plan, "l", keys.getLast());
            var found = rows(values);
            if (found.size() != keys.size()) throw invalid();
            for (int i = 0; i < found.size(); i++) {
                var row = found.get(i);
                if (row.length != plan.keys().size() + 4 * group.size()
                        || !key(plan, row, 0).equals(keys.get(i))) throw invalid();
                int index = plan.keys().size();
                for (String column : group) {
                    boolean missing = missing(row[index]);
                    long bytes = missing ? 0 : number(row[index + 1]);
                    String hash =
                            missing ? CdsExportRawCellDto.EMPTY_SHA256 : (String) row[index + 2];
                    byte[] data = (byte[]) row[index + 3];
                    if (missing
                                    && (row[index + 1] != null
                                            || row[index + 2] != null
                                            || data != null)
                            || !missing && bytes <= inline && data == null
                            || !missing && bytes > inline && data != null) throw invalid();
                    cells.get(i).put(column, new CdsExportRawCellDto(missing, bytes, hash, data));
                    index += 4;
                }
            }
        }
        var result = new ArrayList<Row>();
        for (int i = 0; i < keys.size(); i++) result.add(new Row(keys.get(i), cells.get(i)));
        return List.copyOf(result);
    }

    /**
     * The next page of a keyless table's rows for the patient, by position in the total order of
     * every column ({@link #KEYLESS}). {@code after} is the previous page's last key: the number of
     * rows read so far. Every value is returned inline.
     */
    // Identifiers come only from the pinned catalogue plan.
    @SuppressWarnings("java:S2077")
    private List<Row> keylessPage(Plan plan, int patient, List<Object> after, int limit) {
        long offset = 0;
        if (after != null) {
            if (after.size() != 1 || !(after.getFirst() instanceof Long position) || position < 0)
                throw invalid();
            offset = position;
        }
        if (limit < 1 || limit > 1024 || offset > Integer.MAX_VALUE - limit) throw invalid();
        if (!readable(plan)) return List.of();
        var projection = new StringBuilder("select 1");
        var order = new ArrayList<String>();
        for (String column : plan.columns()) {
            String value = "CAST(d.`" + column + "` AS BINARY)";
            projection
                    .append(",d.`")
                    .append(column)
                    .append("` IS NULL,OCTET_LENGTH(")
                    .append(value)
                    .append("),SHA2(")
                    .append(value)
                    .append(",256),")
                    .append(value);
            // NULL sorts first; text by its exact bytes, so a collation tie never reorders rows.
            order.add(plan.integerKeys().contains(column) ? "d.`" + column + "`" : value);
        }
        projection
                .append(" from `")
                .append(plan.table())
                .append("` d where ")
                .append(predicate(plan.table()))
                .append(" order by ")
                .append(String.join(",", order));
        var found =
                rows(
                        CdsExportPatientTableReadSupport.query(
                                        entityManager, projection.toString())
                                .setParameter("patient", patient)
                                .setFirstResult((int) offset)
                                .setMaxResults(limit));
        var result = new ArrayList<Row>();
        for (var row : found) {
            if (row.length != 1 + 4 * plan.columns().size()) throw invalid();
            var cells = new TreeMap<String, CdsExportRawCellDto>();
            int index = 1;
            for (String column : plan.columns()) {
                boolean missing = missing(row[index]);
                byte[] data = (byte[]) row[index + 3];
                long bytes = missing ? 0 : number(row[index + 1]);
                if (missing
                                && (row[index + 1] != null
                                        || row[index + 2] != null
                                        || data != null)
                        || !missing
                                && (data == null
                                        || data.length != bytes
                                        || bytes > KEYLESS_VALUE_BYTES)) throw invalid();
                cells.put(
                        column,
                        new CdsExportRawCellDto(
                                missing,
                                bytes,
                                missing ? CdsExportRawCellDto.EMPTY_SHA256 : (String) row[index + 2],
                                missing ? null : data));
                index += 4;
            }
            result.add(new Row(List.of(offset + result.size() + 1), cells));
        }
        return List.copyOf(result);
    }

    /** A column type whose longest value fits a keyless row's inline read. */
    private static boolean inline(String type) {
        var text = java.util.regex.Pattern.compile("(?:var)?char\\(([0-9]{1,3})\\)").matcher(type);
        if (text.matches()) return Integer.parseInt(text.group(1)) <= 255;
        return type.matches(
                "(?:tinyint|smallint|mediumint|int|bigint)\\([0-9]{1,3}\\)(?: unsigned)?"
                        + "|date|datetime|timestamp|time");
    }

    /** One byte range of one value of one of the selected patient's rows, in the same snapshot. */
    public byte[] chunk(
            Plan plan, int patient, List<Object> key, String column, long offset, int length) {
        patient(patient);
        // A keyless row cannot be addressed again; its values are always read inline.
        if (plan.keyless()) throw invalid();
        if (column == null
                || !plan.columns().contains(column)
                || key == null
                || key.size() != plan.keys().size()
                || offset < 0
                || offset == Long.MAX_VALUE
                || length < 1
                || length > 65_536
                || !readable(plan)) throw invalid();
        var query =
                CdsExportPatientTableReadSupport.query(
                                entityManager,
                                "select SUBSTRING(CAST(d.`"
                                        + column
                                        + "` AS BINARY),:position,:length),1 from `"
                                        + plan.table()
                                        + "` d where "
                                        + predicate(plan.table())
                                        + " and "
                                        + compare(plan, "=", "k"))
                        .setParameter("position", offset + 1)
                        .setParameter("length", length)
                        .setParameter("patient", patient)
                        .setMaxResults(2);
        bind(query, plan, "k", key);
        var found = rows(query);
        if (found.size() != 1
                || !(found.getFirst()[0] instanceof byte[] data)
                || data.length > length) throw invalid();
        return data.clone();
    }

    /**
     * Each LISTED table of this database with rows linked to the selected patient, with the exact
     * row count. A listed table without any reviewed patient link is never counted per patient.
     */
    // Identifiers come only from the pinned catalogue.
    @SuppressWarnings("java:S2077")
    public List<CdsExportListedSourceDto> listed(int patient, Runnable checkpoint) {
        patient(patient);
        var catalogue = CdsExportSourceCatalogue.current();
        var result = new ArrayList<CdsExportListedSourceDto>();
        for (String table : catalogue.tables(CdsExportSourceCatalogue.Disposition.LISTED)) {
            checkpoint.run();
            if (!linked(table)) continue;
            String engine = engine(table);
            if (engine == null) continue;
            if (!engine.equals("InnoDB")) CdsExportSourceReadLock.requirePinned();
            var found =
                    rows(
                            CdsExportPatientTableReadSupport.query(
                                            entityManager,
                                            "select count(*),1 from `"
                                                    + table
                                                    + "` d where "
                                                    + predicate(table))
                                    .setParameter("patient", patient));
            if (found.size() != 1) throw invalid();
            long count = number(found.getFirst()[0]);
            if (count > 0) result.add(new CdsExportListedSourceDto(table, count));
        }
        return List.copyOf(result);
    }

    /**
     * Each LISTED table of this database that has no reviewed patient link and holds rows, with
     * its exact row count. Such rows belong to no chart; the package lists them for the whole job.
     */
    // Identifiers come only from the pinned catalogue.
    @SuppressWarnings("java:S2077")
    public List<CdsExportListedSourceDto> listedClinicSources() {
        var result = new ArrayList<CdsExportListedSourceDto>();
        for (String table :
                CdsExportSourceCatalogue.current()
                        .tables(CdsExportSourceCatalogue.Disposition.LISTED)) {
            if (linked(table)) continue;
            String engine = engine(table);
            if (engine == null) continue;
            if (!engine.equals("InnoDB")) CdsExportSourceReadLock.requirePinned();
            var found =
                    rows(
                            CdsExportPatientTableReadSupport.query(
                                    entityManager, "select count(*),1 from `" + table + "` d"));
            if (found.size() != 1) throw invalid();
            long count = number(found.getFirst()[0]);
            if (count > 0) result.add(new CdsExportListedSourceDto(table, count));
        }
        return List.copyOf(result);
    }

    /** True for a reviewed LISTED or DOCUMENT table with a direct owner or a parent chain. */
    public static boolean linked(String table) {
        return CdsExportSourceCatalogue.current().patientScopes().containsKey(table)
                || CdsExportIndirectPatientScopeReadSupport.supports(table);
    }

    /**
     * The patient predicate on alias {@code d}: the reviewed owner columns (numeric text aliases
     * included, exactly as the source-presence check reads them) or the parent chain.
     */
    static String predicate(String table) {
        var catalogue = CdsExportSourceCatalogue.current();
        var remainder = catalogue.clinicalRemainders().get(table);
        String scope = ownership(table, catalogue);
        if (remainder == null) return scope;
        // A data-summary table with clinical remainders (admission) is otherwise access-control
        // metadata: only its rows with a stored remainder are records. The test is the one the
        // source-presence check used when such a row held the chart.
        var clauses = new ArrayList<String>();
        for (String column : remainder)
            clauses.add(
                    io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue
                            .remainderPresent(table, column));
        return "(" + scope + " and (" + String.join(" or ", clauses) + "))";
    }

    private static String ownership(String table, CdsExportSourceCatalogue.Snapshot catalogue) {
        var profile =
                catalogue.profiles().stream()
                        .filter(candidate -> candidate.tables().containsKey(table))
                        .findFirst()
                        .orElseThrow(CdsExportDataSummaryReadDao::invalid);
        var owners = catalogue.patientScopes().get(table);
        if (owners != null) {
            var clauses = new ArrayList<String>();
            for (String owner : owners) {
                var column =
                        profile.tables().get(table).columns().stream()
                                .filter(c -> c.name().equals(owner))
                                .findFirst()
                                .orElseThrow(CdsExportDataSummaryReadDao::invalid);
                String value = "d.`" + owner + "`";
                if (column.type().startsWith("varchar("))
                    value = "CAST(" + value + " AS DECIMAL(65,0))";
                clauses.add(value + "=:patient");
            }
            return "(" + String.join(" or ", clauses) + ")";
        }
        if (CdsExportIndirectPatientScopeReadSupport.supports(table))
            return CdsExportIndirectPatientScopeReadSupport.owned(table, profile);
        throw invalid();
    }

    private static String keyList(Plan plan) {
        var names = new ArrayList<String>();
        for (String key : plan.keys()) names.add("d.`" + key + "`");
        return String.join(",", names);
    }

    /** Row-value comparison of the key with bound parameters {@code prefix0..n}. */
    private static String compare(Plan plan, String operator, String prefix) {
        var names = new ArrayList<String>();
        var values = new ArrayList<String>();
        for (int i = 0; i < plan.keys().size(); i++) {
            names.add("d.`" + plan.keys().get(i) + "`");
            values.add(":" + prefix + i);
        }
        if (names.size() == 1) return names.getFirst() + operator + values.getFirst();
        if (operator.equals("="))
            return "("
                    + String.join(
                            " and ",
                            java.util.stream.IntStream.range(0, names.size())
                                    .mapToObj(i -> names.get(i) + "=" + values.get(i))
                                    .toList())
                    + ")";
        return "(" + String.join(",", names) + ")" + operator + "(" + String.join(",", values) + ")";
    }

    private static void bind(
            jakarta.persistence.Query query, Plan plan, String prefix, List<Object> key) {
        for (int i = 0; i < plan.keys().size(); i++) query.setParameter(prefix + i, key.get(i));
    }

    /** Exact key values: integers as Long, character keys as their stored text. */
    private static List<Object> key(Plan plan, Object[] row, int offset) {
        var key = new ArrayList<Object>();
        for (int i = 0; i < plan.keys().size(); i++) {
            Object value = row[offset + i];
            if (plan.integerKeys().contains(plan.keys().get(i))) key.add(number(value));
            else if (value instanceof String text && !text.isEmpty() && text.length() <= 255)
                key.add(text);
            else throw invalid();
        }
        return List.copyOf(key);
    }

    private boolean readable(Plan plan) {
        String engine = engine(plan);
        if (engine == null) return false;
        if (!engine.equals("InnoDB")) CdsExportSourceReadLock.requirePinned();
        return true;
    }

    private String engine(Plan plan) {
        return engine(plan.table());
    }

    private String engine(String table) {
        var pass = CdsExportPhysicalSchemaReadSupport.current();
        if (pass != null) {
            var found = pass.named(table);
            if (found.size() > 1) throw invalid();
            return found.isEmpty() ? null : found.getFirst().engine();
        }
        var engines =
                rows(
                        CdsExportPatientTableReadSupport.query(
                                        entityManager,
                                        "select ENGINE,TABLE_NAME from information_schema.TABLES"
                                                + " where TABLE_SCHEMA=DATABASE() and"
                                                + " TABLE_NAME=:table and TABLE_TYPE='BASE TABLE'")
                                .setParameter("table", table)
                                .setMaxResults(2));
        if (engines.size() > 1) throw invalid();
        return engines.isEmpty() ? null : (String) engines.getFirst()[0];
    }

    // Identifiers come only from the pinned catalogue plan.
    @SuppressWarnings("java:S2077")
    private void visible(Plan plan) {
        CdsExportPatientTableReadSupport.query(
                        entityManager, "select d.* from `" + plan.table() + "` d limit 0")
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(jakarta.persistence.Query query) {
        return query.getResultList();
    }

    private static long number(Object value) {
        if (!(value instanceof Number)) throw invalid();
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException malformed) {
            throw invalid();
        }
    }

    private static boolean missing(Object value) {
        if (value instanceof Boolean flag) return flag;
        long number = number(value);
        if (number != 0 && number != 1) throw invalid();
        return number == 1;
    }

    private static void patient(int id) {
        if (id < 1) throw invalid();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("CDS_EXPORT_NATIVE_SOURCE_INVALID");
    }
}
