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
import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

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

    /**
     * Reads a clinical cohort's selected identifier column without materializing unrelated columns.
     * Retains the legacy string, whitespace and SQL NULL conversion used by clinical denominators.
     *
     * @param sql validated SELECT template
     * @param column JDBC label/name of the identifier column
     * @param parameters bound query values
     * @return immutable identifiers in query order, including duplicates
     * @throws SQLException if the query, identifier budget or output budget fails
     */
    public static List<String> identifierColumn(String sql, String column, Object... parameters) throws SQLException {
        try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                LegacyJdbcQuery.trustedReportSelectSql(sql), MAX_IDENTIFIER_ROWS, TIMEOUT_SECONDS, parameters)) {
            List<String> result = new ArrayList<>();
            int index = rows.findColumn(column);
            long characters = 0;
            while (rows.next()) {
                if (result.size() == MAX_IDENTIFIER_ROWS) throw new LimitExceededException("Report identifier budget exceeded; narrow the filters");
                String value = readValue(rows, index, MAX_OUTPUT_CHARACTERS - characters);
                characters += value == null ? 0 : value.length();
                result.add(value == null ? "" : value.trim());
            }
            return List.copyOf(result);
        }
    }

    /**
     * Reads column names without consuming any result values. Callers should select no rows.
     *
     * @param sql validated metadata SELECT template
     * @param parameters bound query values
     * @return immutable physical column names in result order
     * @throws SQLException if validation, execution or metadata retrieval fails
     */
    public static List<String> columnNames(String sql, Object... parameters) throws SQLException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                LegacyJdbcQuery.trustedReportSelectSql(sql), 1, deadline, parameters)) {
            var metadata = rows.getMetaData();
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) names.add(metadata.getColumnName(i));
            // Observe exhaustion so the streaming connection can return to its pool without aborting.
            if (rows.next()) throw new SQLException("Metadata query must not return report rows");
            return List.copyOf(names);
        }
    }

    /** Reads the complete bounded result and releases all JDBC resources before returning. */
    public static Result read(String sql, Object... parameters) throws SQLException {
        return readRows(sql, false, parameters);
    }

    private static Result readRows(String sql, boolean firstOnly, Object... parameters) throws SQLException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                LegacyJdbcQuery.trustedReportSelectSql(sql), firstOnly ? 1 : MAX_ROWS + 1, deadline, parameters)) {
            var metadata = rows.getMetaData();
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) names.add(metadata.getColumnName(i));
            List<Row> result = new ArrayList<>();
            long characters = names.stream().mapToLong(String::length).sum();
            if (characters > MAX_OUTPUT_CHARACTERS) throw new LimitExceededException("Report output budget exceeded; narrow the report filters");
            while (rows.next()) {
                // The exact one-row JDBC ceiling makes this final next() observe exhaustion.
                if (firstOnly && !result.isEmpty()) break;
                if (result.size() == MAX_ROWS) throw new LimitExceededException("Report row budget exceeded; narrow the report filters");
                Map<String, String> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= names.size(); i++) {
                    String value = readValue(rows, i, MAX_OUTPUT_CHARACTERS - characters);
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
            }
            return new Result(names, result);
        }
    }

    private static String readValue(ResultSet rows, int column, long remainingCharacters) throws SQLException {
        // Connector/J cannot stream scalar values as characters; their database types are bounded.
        boolean scalar = switch (rows.getMetaData().getColumnType(column)) {
            case Types.NULL, Types.BOOLEAN, Types.BIT, Types.TINYINT, Types.SMALLINT,
                    Types.INTEGER, Types.BIGINT, Types.REAL, Types.FLOAT, Types.DOUBLE,
                    Types.NUMERIC, Types.DECIMAL, Types.DATE, Types.TIME, Types.TIMESTAMP,
                    Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE -> true;
            default -> false;
        };
        if (scalar) {
            String value = rows.getString(column);
            if (value != null && value.length() > remainingCharacters) {
                throw new LimitExceededException("Report output budget exceeded; narrow the report filters");
            }
            return value;
        }
        try (Reader reader = rows.getCharacterStream(column)) {
            if (reader == null) return null;
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (count > remainingCharacters - value.length()) {
                    throw new LimitExceededException("Report output budget exceeded; narrow the report filters");
                }
                value.append(buffer, 0, count);
            }
            return value.toString();
        } catch (IOException e) {
            throw new SQLException("Could not read report value", e);
        }
    }

}
