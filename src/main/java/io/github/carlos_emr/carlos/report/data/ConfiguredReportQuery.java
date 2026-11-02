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
package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Detached, bounded results for server-configured report SQL. This compatibility exception retains
 * SELECT validation and the managed legacy datasource; it never executes SQL through the clinical
 * EntityManager or changes Query by Example's restricted reporting account.
 */
public final class ConfiguredReportQuery {
    public static final int MAX_ROWS = 10_000;
    public static final int TIMEOUT_SECONDS = 30;
    public static final int MAX_IDENTIFIER_ROWS = 100_000;
    public static final int MAX_OUTPUT_CHARACTERS = 5 * 1024 * 1024;

    private ConfiguredReportQuery() { }

    /** JDBC labels/names remain available for configured exports; SQL NULL remains null. */
    public record Row(Map<String, String> columns, List<String> values) {
        public Row {
            Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            copy.putAll(columns);
            columns = Collections.unmodifiableMap(copy);
            values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        public String value(String column) throws SQLException {
            if (!columns.containsKey(column) && column.indexOf('.') >= 0) {
                column = column.substring(column.lastIndexOf('.') + 1);
            }
            if (!columns.containsKey(column)) throw new SQLException("Unknown report column");
            return columns.get(column);
        }

        public String text(String column) throws SQLException {
            String value = value(column);
            return value == null ? "" : value.trim();
        }

        public int integer(String column) throws SQLException {
            return integerValue(value(column));
        }

        public int firstInteger() throws SQLException {
            return integerValue(values.get(0));
        }

        private static int integerValue(String value) throws SQLException {
            try {
                return value == null ? 0 : new java.math.BigDecimal(value.trim()).intValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                throw new SQLException("Report identifier is not a valid integer", e);
            }
        }
    }

    public record Result(List<String> columnNames, List<Row> rows) {
        public Result {
            columnNames = List.copyOf(columnNames);
            rows = List.copyOf(rows);
        }
    }

    /** A budget breach is distinct from a query/connection failure for report feedback. */
    public static class LimitExceededException extends SQLException {
        private static final long serialVersionUID = 1L;
        public LimitExceededException(String message) { super(message); }
    }

    /** Reads the first matching row only, for clinical numerator existence/output checks. */
    public static Result readFirst(String sql, Object... parameters) throws SQLException {
        return readRows(sql, true, parameters);
    }

    /** Bounded internal identifier sets can exceed the rendered report's 10,000-row budget. */
    public static List<Integer> identifiers(String sql, Object... parameters) throws SQLException {
        try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                LegacyJdbcQuery.trustedReportSelectSql(sql), MAX_IDENTIFIER_ROWS, TIMEOUT_SECONDS, parameters)) {
            List<Integer> result = new ArrayList<>();
            while (rows.next()) {
                if (result.size() == MAX_IDENTIFIER_ROWS) throw new LimitExceededException("Report identifier budget exceeded; narrow the filters");
                result.add(rows.getInt(1));
            }
            return List.copyOf(result);
        }
    }

    /** Reads the complete bounded result and releases all JDBC resources before returning. */
    public static Result read(String sql, Object... parameters) throws SQLException {
        return readRows(sql, false, parameters);
    }

    private static Result readRows(String sql, boolean firstOnly, Object... parameters) throws SQLException {
        try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                LegacyJdbcQuery.trustedReportSelectSql(sql), firstOnly ? 1 : MAX_ROWS, TIMEOUT_SECONDS, parameters)) {
            var metadata = rows.getMetaData();
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) names.add(metadata.getColumnName(i));
            List<Row> result = new ArrayList<>();
            long characters = names.stream().mapToLong(String::length).sum();
            while (rows.next()) {
                if (result.size() == MAX_ROWS) throw new LimitExceededException("Report row budget exceeded; narrow the report filters");
                Map<String, String> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= names.size(); i++) {
                    String value = rows.getString(i);
                    values.add(value);
                    if (!columns.containsKey(metadata.getColumnLabel(i))) columns.put(metadata.getColumnLabel(i), value);
                    if (!columns.containsKey(names.get(i - 1))) columns.put(names.get(i - 1), value);
                    String table = metadata.getTableName(i);
                    if (table != null && !table.isEmpty() && !columns.containsKey(table + "." + names.get(i - 1))) {
                        columns.put(table + "." + names.get(i - 1), value);
                    }
                    characters += value == null ? 0 : value.length();
                    if (characters > MAX_OUTPUT_CHARACTERS) throw new LimitExceededException("Report output budget exceeded; narrow the report filters");
                }
                result.add(new Row(columns, values));
                if (firstOnly) break;
            }
            return new Result(names, result);
        }
    }
}
