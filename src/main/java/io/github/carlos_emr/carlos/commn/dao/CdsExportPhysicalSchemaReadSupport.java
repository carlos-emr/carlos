/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One capture transaction's read of the whole physical schema, shared by the sibling CDS export
 * DAOs that verify a table's structure before they read it.
 *
 * <p>Each source reader verifies its table's physical structure for every patient, inside that
 * patient's read-only capture transaction. Read one table at a time that is three
 * {@code information_schema} statements and two metadata probes per table: about 1,500 statements
 * for every patient of a full export. A pass reads the same facts for every base table at once
 * (five statements), binds them to the capture transaction, and answers each table's verification
 * from them. Nothing about the verification itself changes: every reader still receives the
 * structure of its own table as the database reported it inside this patient's transaction, and
 * still checks it against its pinned contract.
 *
 * <p>Two things the per-table read did are done differently:
 *
 * <ul>
 *   <li><b>Column visibility.</b> The per-table read first ran {@code select d.* from T limit 0},
 *       which fails for an account that can see only some columns, so a hidden column could not
 *       leave the privilege-filtered catalogue looking complete. A pass is opened only when the
 *       account holds SELECT on the whole schema (or globally), which makes every column of every
 *       table visible; without that grant no pass is opened and every table is read the old way.
 *       The probe itself still runs for each table the first time the attempt meets it
 *       ({@link Pass#unprobed}).
 *   <li><b>Stability while the rows are read.</b> The per-table probe also took the table's
 *       metadata lock before its structure was read. With a pass the structure is read first, so
 *       the capture must read the schema a second time after the transaction's last source read
 *       and before it commits ({@link CdsExportSourceCatalogueDao#verifySchemaPass}). Every
 *       table read in the transaction has held its metadata lock since its first read, so its
 *       structure cannot have changed between that read and the second complete read; if the
 *       second read shows any served table differently from the first, a change happened before
 *       that table's first read, and the capture is refused.
 * </ul>
 *
 * <p>A pass belongs to one transaction on one thread. It is unbound when the transaction
 * completes, is never shared between patients, and holds only structure: table and column names,
 * types and keys, never a row or a value.
 */
public final class CdsExportPhysicalSchemaReadSupport {
    private static final Object KEY = new Object();

    /** The name reported when the two reads of a pass cannot be compared. */
    public static final String UNREADABLE = "information_schema";

    private CdsExportPhysicalSchemaReadSupport() {}

    /** One base table as {@code information_schema} reported it. */
    public record Table(
            String name,
            String engine,
            List<CdsExportTableSchemaDto.Column> columns,
            List<String> primaryKeyColumns) {
        public Table {
            columns = List.copyOf(columns);
            primaryKeyColumns = List.copyOf(primaryKeyColumns);
        }
    }

    /** The whole physical schema as one transaction read it once. */
    public static final class Pass {
        private final int caseMode;
        private final List<Table> tables;
        private final Map<String, List<Table>> folded = new HashMap<>();
        private final Set<String> probes;
        private final Map<String, List<Table>> served = new TreeMap<>();

        private Pass(int caseMode, List<Table> tables, Set<String> probes) {
            this.caseMode = caseMode;
            this.tables = List.copyOf(tables);
            this.probes = probes;
            for (Table table : this.tables)
                folded.computeIfAbsent(fold(table.name()), ignored -> new ArrayList<>())
                        .add(table);
        }

        /** MariaDB's {@code lower_case_table_names} as read with the tables. */
        public int caseMode() {
            return caseMode;
        }

        /** Every base table of the schema. */
        public List<Table> tables() {
            return tables;
        }

        /**
         * The tables a per-table {@code information_schema} read of this name would return:
         * none, one, or several where table names differ only by letter case. The catalogue
         * compares names without case, so the caller applies its own rule to none and several.
         * What is returned is remembered and compared again by the pass's second read.
         */
        public List<Table> named(String table) {
            var found = find(table);
            served.put(table, found);
            return found;
        }

        private List<Table> find(String table) {
            return List.copyOf(folded.getOrDefault(fold(Objects.requireNonNull(table)), List.of()));
        }

        /**
         * True when the column-visibility probe of this table has not yet succeeded in this
         * attempt, so the caller must run it now and then call {@link #probed}.
         */
        public boolean unprobed(String table) {
            return probes == null || !probes.contains(Objects.requireNonNull(table));
        }

        /** Records that the probe of this table succeeded. */
        public void probed(String table) {
            if (probes != null) probes.add(Objects.requireNonNull(table));
        }

        private SortedSet<String> changed(Pass again) {
            var result = new TreeSet<String>();
            if (again == null || again.caseMode != caseMode) {
                result.add(UNREADABLE);
                return result;
            }
            served.forEach(
                    (table, found) -> {
                        if (!found.equals(again.find(table))) result.add(table);
                    });
            return result;
        }

        @Override
        public String toString() {
            return "CdsExportPhysicalSchemaReadSupport.Pass[tables=" + tables.size() + "]";
        }
    }

    /**
     * Reads the schema once and binds it to the current read-only transaction until that
     * transaction completes. Returns null, binding nothing, when the account's whole-schema SELECT
     * right cannot be shown or the schema holds a table name the catalogue cannot compare by
     * plain letters; every DAO then reads its table on its own, exactly as without a pass.
     *
     * @param probes the tables whose column-visibility probe already succeeded in this attempt,
     *     updated as more succeed; null to probe every table in every pass
     */
    public static Pass open(EntityManager em, Set<String> probes) {
        if (current() != null) throw new IllegalStateException("CDS_EXPORT_SCHEMA_PASS_ACTIVE");
        // Also refuses anything but a read-only transaction.
        CdsExportPatientTableReadSupport.queryTimeoutMillis();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return null;
        Pass pass = read(em, probes);
        if (pass == null) return null;
        TransactionSynchronizationManager.bindResource(KEY, pass);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void suspend() {
                        TransactionSynchronizationManager.unbindResourceIfPossible(KEY);
                    }

                    @Override
                    public void resume() {
                        TransactionSynchronizationManager.bindResource(KEY, pass);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        TransactionSynchronizationManager.unbindResourceIfPossible(KEY);
                    }
                });
        return pass;
    }

    /** The pass bound to the current transaction, or null. */
    public static Pass current() {
        return (Pass) TransactionSynchronizationManager.getResource(KEY);
    }

    /**
     * Names every table the first read served whose structure the second complete read shows
     * differently (or not at all, or newly), in name order; {@link #UNREADABLE} alone when the
     * two reads cannot be compared. Empty when nothing served changed. The second read must be
     * made after the transaction's last source read and before it commits.
     */
    static SortedSet<String> changed(Pass first, Pass again) {
        return Collections.unmodifiableSortedSet(
                Objects.requireNonNull(first).changed(Objects.requireNonNull(again)));
    }

    /** One unbound complete read, or null when it cannot stand in for the per-table reads. */
    static Pass read(EntityManager em, Set<String> probes) {
        if (!wholeSchemaVisible(em)) return null;
        var mode =
                CdsExportPatientTableReadSupport.query(em, "select @@lower_case_table_names,1")
                        .getResultList();
        if (mode.size() != 1 || !(((Object[]) mode.getFirst())[0] instanceof Number number))
            return null;
        int caseMode = number.intValue();
        if (caseMode < 0 || caseMode > 2) return null;
        var engines = new TreeMap<String, String>();
        var base =
                rows(
                        em,
                        "select TABLE_NAME,ENGINE from information_schema.TABLES where"
                                + " TABLE_SCHEMA=DATABASE() and TABLE_TYPE='BASE TABLE'",
                        1026);
        if (base.isEmpty() || base.size() >= 1026) return null;
        for (Object[] row : base) {
            if (row.length != 2
                    || !(row[0] instanceof String name)
                    || !name.matches("[A-Za-z_][A-Za-z0-9_]*")
                    || !(row[1] instanceof String engine)
                    || engines.putIfAbsent(name, engine) != null) return null;
        }
        var columns = new HashMap<String, List<CdsExportTableSchemaDto.Column>>();
        var all =
                rows(
                        em,
                        "select"
                                + " TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,CHARACTER_SET_NAME,COLLATION_NAME,NULLIF(GENERATION_EXPRESSION,'')"
                                + " from information_schema.COLUMNS where"
                                + " TABLE_SCHEMA=DATABASE()",
                        65537);
        if (all.size() >= 65537) return null;
        for (Object[] row : all) {
            if (row.length != 7 || !(row[0] instanceof String table)) return null;
            // Views and temporary tables have columns too; only base tables are kept.
            if (!engines.containsKey(table)) continue;
            columns.computeIfAbsent(table, ignored -> new ArrayList<>())
                    .add(
                            new CdsExportTableSchemaDto.Column(
                                    (String) row[1],
                                    (String) row[2],
                                    (String) row[3],
                                    (String) row[4],
                                    (String) row[5],
                                    (String) row[6]));
        }
        var primary = new HashMap<String, List<String>>();
        var keys =
                rows(
                        em,
                        "select TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION from"
                                + " information_schema.KEY_COLUMN_USAGE where"
                                + " TABLE_SCHEMA=DATABASE() and CONSTRAINT_NAME='PRIMARY'"
                                + " order by TABLE_NAME,ORDINAL_POSITION",
                        16385);
        if (keys.size() >= 16385) return null;
        for (Object[] row : keys) {
            if (row.length != 3 || !(row[0] instanceof String table)) return null;
            if (engines.containsKey(table))
                primary.computeIfAbsent(table, ignored -> new ArrayList<>()).add((String) row[1]);
        }
        var tables = new ArrayList<Table>();
        for (var entry : engines.entrySet()) {
            var fields = new ArrayList<>(columns.getOrDefault(entry.getKey(), List.of()));
            fields.sort(Comparator.comparing(CdsExportTableSchemaDto.Column::name));
            tables.add(
                    new Table(
                            entry.getKey(),
                            entry.getValue(),
                            fields,
                            primary.getOrDefault(entry.getKey(), List.of())));
        }
        return new Pass(caseMode, tables, probes);
    }

    /**
     * Whether the connected account holds SELECT on the whole current schema, directly or
     * globally. Only then does the privilege-filtered catalogue show every column of every table.
     */
    private static boolean wholeSchemaVisible(EntityManager em) {
        String grantee =
                "concat('''',substring_index(current_user(),'@',1),'''@''',substring_index(current_user(),'@',-1),'''')";
        if (!CdsExportPatientTableReadSupport.query(
                        em,
                        "select 1,1 from information_schema.SCHEMA_PRIVILEGES where"
                                + " TABLE_SCHEMA=DATABASE() and PRIVILEGE_TYPE='SELECT' and GRANTEE="
                                + grantee)
                .setMaxResults(1)
                .getResultList()
                .isEmpty()) return true;
        return !CdsExportPatientTableReadSupport.query(
                        em,
                        "select 1,1 from information_schema.USER_PRIVILEGES where"
                                + " PRIVILEGE_TYPE='SELECT' and GRANTEE="
                                + grantee)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(EntityManager em, String statement, int limit) {
        return CdsExportPatientTableReadSupport.query(em, statement)
                .setMaxResults(limit)
                .getResultList();
    }

    // Table names here are ASCII SQL identifiers (checked when read); information_schema compares
    // them without case, as this folding does.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification =
                    "ASCII SQL identifiers folded as information_schema's case-insensitive name"
                            + " comparison folds them")
    private static String fold(String table) {
        return table.toLowerCase(Locale.ROOT);
    }
}
