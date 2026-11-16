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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
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
 *
 * <p>The limits are {@code carlos.properties} settings ({@code configured_report.*}); the constants
 * here are their defaults and maximums. Each is read when a query runs (see {@link ReportLimitSetting}),
 * so an operator's edit takes effect at the next restart. A breach is a {@link LimitExceededException}
 * that names its limit; nothing partial is returned.</p>
 */
public final class ConfiguredReportQuery {
    /** Default of {@code configured_report.max_rows}: rows one rendered report or export may return. */
    public static final int MAX_ROWS = 10_000;
    /** Every returned row is held in memory until the page or file is written, so the maximum stays modest. */
    public static final int MAX_ROWS_CEILING = 100_000;
    /** Default of {@code configured_report.timeout_seconds}. */
    public static final int TIMEOUT_SECONDS = 30;
    /** The packaged proxy stops waiting for a response after 300 seconds. */
    public static final int TIMEOUT_SECONDS_CEILING = 300;
    /** Default of {@code configured_report.max_identifier_rows}: an internal cohort, such as a clinical denominator. */
    public static final int MAX_IDENTIFIER_ROWS = 100_000;
    public static final int MAX_IDENTIFIER_ROWS_CEILING = 1_000_000;
    /** Default of {@code configured_report.max_output_chars}: characters of text one query may return. */
    public static final int MAX_OUTPUT_CHARACTERS = 5 * 1024 * 1024;
    public static final int MAX_OUTPUT_CHARACTERS_CEILING = 50 * 1024 * 1024;
    /** Default of {@code configured_report.max_csv_mb}: size of the form-report CSV download, in MiB of UTF-8. */
    public static final int MAX_CSV_MB = 5;
    public static final int MAX_CSV_MB_CEILING = 50;

    /** Bundle key of the sentence added to a refusal whose limit an administrator can still raise. */
    public static final String ADMINISTRATOR_CAN_RAISE_KEY = "report.limit.administratorCanRaise";
    private static final String ADMINISTRATOR_CAN_RAISE_ENGLISH =
            "An administrator can raise this limit with the setting {0} in carlos.properties.";

    /**
     * A configurable limit of the configured (form, antenatal and clinical) reports: its
     * {@code carlos.properties} setting, default, maximum, and the message a refusal shows. The English
     * text is for logs and exception messages; pages show the bundle message for {@link #messageKey()}.
     */
    public enum Limit {
        ROWS("configured_report.max_rows", MAX_ROWS, MAX_ROWS_CEILING, "report.configured.limitRows",
                "This report has more than {0} rows, the most a report may return. Narrow the report filters and run it again."),
        IDENTIFIERS("configured_report.max_identifier_rows", MAX_IDENTIFIER_ROWS, MAX_IDENTIFIER_ROWS_CEILING,
                "report.configured.limitIdentifiers",
                "This report selects more than {0} patients or records, the most a report may select. Narrow the report filters and run it again."),
        OUTPUT("configured_report.max_output_chars", MAX_OUTPUT_CHARACTERS, MAX_OUTPUT_CHARACTERS_CEILING,
                "report.configured.limitOutput",
                "This report returns more than {0} characters of text, the most a report may return. Narrow the report filters and run it again."),
        TIME("configured_report.timeout_seconds", TIMEOUT_SECONDS, TIMEOUT_SECONDS_CEILING, "report.configured.limitTime",
                "This report did not finish within {0} seconds, either because it is large or because the server was busy. Narrow the report filters, or try again later."),
        CSV("configured_report.max_csv_mb", MAX_CSV_MB, MAX_CSV_MB_CEILING, "report.configured.limitCsv",
                "This report is larger than {0} MB, the most a report download may hold. Narrow the report filters and run it again.");

        private final String setting;
        private final int fallback;
        private final int ceiling;
        private final String messageKey;
        private final String english;

        Limit(String setting, int fallback, int ceiling, String messageKey, String english) {
            this.setting = setting;
            this.fallback = fallback;
            this.ceiling = ceiling;
            this.messageKey = messageKey;
            this.english = english;
        }

        /** The limit in force: the operator's setting when it is allowed, otherwise the default. */
        public int configured() {
            return ReportLimitSetting.read(setting, fallback, ceiling);
        }

        public String setting() {
            return setting;
        }

        public String messageKey() {
            return messageKey;
        }

        /**
         * The setting an administrator can raise after a refusal at {@code value}, or null when the limit is
         * already at its maximum, so a page never promises a raise that cannot be made.
         */
        public String raisableSetting(int value) {
            return value < ceiling ? setting : null;
        }

        /** The refusal in English, naming the limit and, while it can be raised, its setting. */
        public String english(int value) {
            String text = english.replace("{0}", Integer.toString(value));
            String raisable = raisableSetting(value);
            return raisable == null ? text : text + ' ' + ADMINISTRATOR_CAN_RAISE_ENGLISH.replace("{0}", raisable);
        }
    }

    private ConfiguredReportQuery() { }

    /** Rows one rendered report may return ({@code configured_report.max_rows}). */
    public static int maxRows() {
        return Limit.ROWS.configured();
    }

    /** Seconds one query may take, including the wait for a connection ({@code configured_report.timeout_seconds}). */
    public static int timeoutSeconds() {
        return Limit.TIME.configured();
    }

    /** Identifiers one internal cohort may hold ({@code configured_report.max_identifier_rows}). */
    public static int maxIdentifierRows() {
        return Limit.IDENTIFIERS.configured();
    }

    /** Characters of text one query may return ({@code configured_report.max_output_chars}). */
    public static int maxOutputCharacters() {
        return Limit.OUTPUT.configured();
    }

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

    /**
     * A limit breach is distinct from a query/connection failure for report feedback: it names the limit
     * and its value, so the page can tell the user which one stopped the report and whether an
     * administrator can raise it.
     */
    public static class LimitExceededException extends SQLException {
        private static final long serialVersionUID = 1L;
        private final Limit limit;
        private final int value;

        public LimitExceededException(Limit limit, int value) {
            this(limit, value, null);
        }

        public LimitExceededException(Limit limit, int value, Throwable cause) {
            super(limit.english(value), cause);
            this.limit = limit;
            this.value = value;
        }

        /** Which limit refused the report. */
        public Limit limit() {
            return limit;
        }

        /** The limit's value in the unit its message names (rows, records, characters, seconds or MB). */
        public int value() {
            return value;
        }
    }

    /** One JDBC read; lets the public entry points share the deadline-to-limit translation. */
    @FunctionalInterface
    private interface BoundedRead<T> {
        T read() throws SQLException;
    }

    /**
     * The JDBC deadline (waiting for a connection, the statement, or a streaming read) is this report's
     * time limit, so it is reported as that limit instead of as a database failure.
     */
    private static <T> T withinTimeLimit(int timeoutSeconds, BoundedRead<T> query) throws SQLException {
        try {
            return query.read();
        } catch (SQLTimeoutException e) {
            throw new LimitExceededException(Limit.TIME, timeoutSeconds, e);
        }
    }

    /** Reads the first matching row only, for clinical numerator existence/output checks. */
    public static Result readFirst(String sql, Object... parameters) throws SQLException {
        return readRows(sql, true, parameters);
    }

    /** Bounded internal identifier sets can exceed the rendered report's row limit. */
    public static List<Integer> identifiers(String sql, Object... parameters) throws SQLException {
        int maximum = maxIdentifierRows();
        int timeout = timeoutSeconds();
        return withinTimeLimit(timeout, () -> {
            try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                    LegacyJdbcQuery.trustedReportSelectSql(sql), maximum, timeout, parameters)) {
                List<Integer> result = new ArrayList<>();
                while (rows.next()) {
                    if (result.size() == maximum) throw new LimitExceededException(Limit.IDENTIFIERS, maximum);
                    result.add(rows.getInt(1));
                }
                return List.copyOf(result);
            }
        });
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
        int maximum = maxIdentifierRows();
        int timeout = timeoutSeconds();
        int maximumCharacters = maxOutputCharacters();
        return withinTimeLimit(timeout, () -> {
            try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                    LegacyJdbcQuery.trustedReportSelectSql(sql), maximum, timeout, parameters)) {
                List<String> result = new ArrayList<>();
                int index = rows.findColumn(column);
                long characters = 0;
                while (rows.next()) {
                    if (result.size() == maximum) throw new LimitExceededException(Limit.IDENTIFIERS, maximum);
                    String value = readValue(rows, index, maximumCharacters - characters, maximumCharacters);
                    characters += value == null ? 0 : value.length();
                    result.add(value == null ? "" : value.trim());
                }
                return List.copyOf(result);
            }
        });
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
        int timeout = timeoutSeconds();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
        return withinTimeLimit(timeout, () -> {
            try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                    LegacyJdbcQuery.trustedReportSelectSql(sql), 1, deadline, parameters)) {
                List<String> names = readColumnNames(rows.getMetaData());
                // Observe exhaustion so the streaming connection can return to its pool without aborting.
                if (rows.next()) throw new SQLException("Metadata query must not return report rows");
                return List.copyOf(names);
            }
        });
    }

    /** Reads the complete bounded result and releases all JDBC resources before returning. */
    public static Result read(String sql, Object... parameters) throws SQLException {
        return readRows(sql, false, parameters);
    }

    private static Result readRows(String sql, boolean firstOnly, Object... parameters) throws SQLException {
        int maximumRows = maxRows();
        int timeout = timeoutSeconds();
        int maximumCharacters = maxOutputCharacters();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
        return withinTimeLimit(timeout, () -> {
            try (var rows = LegacyJdbcQuery.getBoundedReportResultSet(
                    LegacyJdbcQuery.trustedReportSelectSql(sql), firstOnly ? 1 : maximumRows + 1, deadline, parameters)) {
                var metadata = rows.getMetaData();
                List<String> names = readColumnNames(metadata);
                List<Row> result = new ArrayList<>();
                long characters = names.stream().mapToLong(String::length).sum();
                if (characters > maximumCharacters) throw new LimitExceededException(Limit.OUTPUT, maximumCharacters);
                while (rows.next()) {
                    // The exact one-row JDBC ceiling makes this final next() observe exhaustion.
                    if (firstOnly && !result.isEmpty()) break;
                    if (result.size() == maximumRows) throw new LimitExceededException(Limit.ROWS, maximumRows);
                    MaterializedRow row = readRow(rows, metadata, names, maximumCharacters - characters, maximumCharacters);
                    characters += row.characterCount();
                    result.add(row.row());
                }
                return new Result(names, result);
            }
        });
    }

    private static List<String> readColumnNames(ResultSetMetaData metadata) throws SQLException {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) names.add(metadata.getColumnName(i));
        return names;
    }

    private record MaterializedRow(Row row, long characterCount) { }

    private static MaterializedRow readRow(ResultSet rows, ResultSetMetaData metadata, List<String> names,
            long remainingCharacters, int maximumCharacters) throws SQLException {
        Map<String, String> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<String> values = new ArrayList<>();
        long characters = 0;
        for (int i = 1; i <= names.size(); i++) {
            String value = readValue(rows, i, remainingCharacters - characters, maximumCharacters);
            values.add(value);
            putFirstColumn(columns, metadata.getColumnLabel(i), value);
            String name = names.get(i - 1);
            putFirstColumn(columns, name, value);
            String table = metadata.getTableName(i);
            if (table != null && !table.isEmpty()) {
                putFirstColumn(columns, table + "." + name, value);
            }
            characters += value == null ? 0 : value.length();
        }
        return new MaterializedRow(new Row(columns, values), characters);
    }

    // computeIfAbsent treats stored SQL NULLs as absent and would replace the first matching column.
    @SuppressWarnings("java:S3824")
    private static void putFirstColumn(Map<String, String> columns, String name, String value) {
        if (!columns.containsKey(name)) columns.put(name, value);
    }

    /** Reads one value within what remains of the query's character limit; a refusal names the whole limit. */
    private static String readValue(ResultSet rows, int column, long remainingCharacters, int maximumCharacters)
            throws SQLException {
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
                throw new LimitExceededException(Limit.OUTPUT, maximumCharacters);
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
                    throw new LimitExceededException(Limit.OUTPUT, maximumCharacters);
                }
                value.append(buffer, 0, count);
            }
            return value.toString();
        } catch (IOException e) {
            throw new SQLException("Could not read report value", e);
        }
    }

}
