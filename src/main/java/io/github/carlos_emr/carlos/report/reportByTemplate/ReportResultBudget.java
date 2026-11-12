/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.owasp.encoder.Encode;

import io.github.carlos_emr.carlos.report.data.ReportLimitSetting;
import io.github.carlos_emr.carlos.utility.SpreadsheetCsv;

/**
 * Request-scoped budgets shared by every query of a sequenced template report, except the CSV limit,
 * which applies to each query's own download (#5849). Only the current cell and bounded HTML/CSV
 * buffers are retained. JDBC may retain the current row; Connector/J streaming prevents whole-result
 * buffering.
 */
final class ReportResultBudget {
    // Defaults (the limits #5560 set) and hard maximums of the carlos.properties settings (#5849).
    static final int MAX_ROWS = 10_000;
    static final int MAX_ROWS_CEILING = 100_000;
    static final int MAX_HTML_BYTES = 10 * 1024 * 1024;
    static final int MAX_HTML_MB_CEILING = 50;
    static final int MAX_COLUMNS = 256;
    static final int MAX_COLUMNS_CEILING = 2_000;
    static final int TIMEOUT_SECONDS = 30;
    static final int TIMEOUT_SECONDS_CEILING = 300;
    static final int MAX_CELL_CHARACTERS = 64 * 1024;
    /** One cell is held whole in memory, twice while it is encoded, so it stays far below the page limit. */
    static final int MAX_CELL_CHARACTERS_CEILING = 1024 * 1024;
    // Fixed, not configurable: a sequence is written into one template by its author.
    static final int MAX_QUERIES = 32;
    private static final int MIB = 1024 * 1024;
    /** The CSV limit can only be lowered: its downloads post the CSV back through the packaged web firewall. */
    private static final int CSV_CEILING_MB = SQLReporter.MAX_CSV_EXPORT_LENGTH / MIB;
    /** Bundle key of the sentence added to a refusal whose limit an administrator can still raise. */
    static final String ADMINISTRATOR_CAN_RAISE_KEY = "report.limit.administratorCanRaise";
    static final String ADMINISTRATOR_CAN_RAISE_ENGLISH =
            "An administrator can raise this limit with the setting {0} in carlos.properties.";

    /**
     * The limit a refusal names. Each has its own message in the resource bundle (#5849); the English
     * text here is for logs and for pages that cannot resolve the key. A limit with a setting is one an
     * administrator can change in carlos.properties, up to its ceiling (in the unit the message names).
     */
    enum Limit {
        ROWS("report.byTemplate.limitRows", "report_by_template.max_rows", MAX_ROWS_CEILING,
                "This report has more than {0} rows, the most a report may return. Narrow the search criteria and run it again."),
        COLUMNS("report.byTemplate.limitColumns", "report_by_template.max_columns", MAX_COLUMNS_CEILING,
                "This report has more than {0} columns, the most a report may show. Select fewer columns in the template and run it again."),
        CELL("report.byTemplate.limitCell", "report_by_template.max_cell_chars", MAX_CELL_CHARACTERS_CEILING,
                "A value in this report is longer than {0} characters, the most a report cell may hold. Leave out the long text column or narrow the search criteria."),
        HTML("report.byTemplate.limitHtml", "report_by_template.max_html_mb", MAX_HTML_MB_CEILING,
                "This report is larger than {0} MB, the most a report page may show. Narrow the search criteria and run it again."),
        QUERIES("report.byTemplate.limitQueries", null, MAX_QUERIES,
                "This report runs more than {0} queries, the most a report sequence may run. Split the template into smaller reports."),
        TIME("report.byTemplate.limitTime", "report_by_template.timeout_seconds", TIMEOUT_SECONDS_CEILING,
                "This report did not finish within {0} seconds, either because it is large or because the server was busy. Narrow the search criteria, or try again later.");

        private final String key;
        private final String setting;
        private final int ceiling;
        private final String english;

        Limit(String key, String setting, int ceiling, String english) {
            this.key = key;
            this.setting = setting;
            this.ceiling = ceiling;
            this.english = english;
        }

        String key() {
            return key;
        }

        /**
         * The carlos.properties setting an administrator can raise when a report was refused at {@code value},
         * or null when the limit is fixed or already at its maximum, so the page never promises a raise
         * that cannot be made.
         */
        String raisableSetting(int value) {
            return setting != null && value < ceiling ? setting : null;
        }

        String english(int value) {
            String text = english.replace("{0}", Integer.toString(value));
            String raisable = raisableSetting(value);
            return raisable == null ? text : text + ' ' + ADMINISTRATOR_CAN_RAISE_ENGLISH.replace("{0}", raisable);
        }
    }

    /**
     * The limits one report runs under. Administrators may set {@code report_by_template.max_rows},
     * {@code .timeout_seconds}, {@code .max_html_mb}, {@code .max_columns} and {@code .max_cell_chars} in
     * carlos.properties, up to their maximums, and {@code .max_csv_mb} below its 5 MiB ceiling only (a larger
     * CSV would not fit the export POST through the packaged WAF). A missing setting keeps the default; see
     * {@link ReportLimitSetting} for values that are not allowed.
     */
    record Limits(int maxRows, int timeoutSeconds, int maxHtmlBytes, int maxColumns, int maxCsvBytes,
                  int maxCellCharacters) {
        static Limits defaults() {
            return new Limits(MAX_ROWS, TIMEOUT_SECONDS, MAX_HTML_BYTES, MAX_COLUMNS, SQLReporter.MAX_CSV_EXPORT_LENGTH,
                    MAX_CELL_CHARACTERS);
        }

        /** The limits in the running server's carlos.properties, read for each report. */
        static Limits configured() {
            return from(ReportLimitSetting::configured);
        }

        static Limits from(UnaryOperator<String> settings) {
            return new Limits(
                    ReportLimitSetting.read(settings, "report_by_template.max_rows", MAX_ROWS, MAX_ROWS_CEILING),
                    ReportLimitSetting.read(settings, "report_by_template.timeout_seconds", TIMEOUT_SECONDS, TIMEOUT_SECONDS_CEILING),
                    ReportLimitSetting.read(settings, "report_by_template.max_html_mb", MAX_HTML_BYTES / MIB, MAX_HTML_MB_CEILING) * MIB,
                    ReportLimitSetting.read(settings, "report_by_template.max_columns", MAX_COLUMNS, MAX_COLUMNS_CEILING),
                    ReportLimitSetting.read(settings, "report_by_template.max_csv_mb", CSV_CEILING_MB, CSV_CEILING_MB) * MIB,
                    ReportLimitSetting.read(settings, "report_by_template.max_cell_chars", MAX_CELL_CHARACTERS, MAX_CELL_CHARACTERS_CEILING));
        }
    }

    /** One rendered query: always the HTML, and the CSV unless it went over its limit and was held back (D260). */
    record Rendered(String html, String csv, boolean csvWithheld) {
    }

    private final Limits limits;
    private final LongSupplier nanoTime;
    private final long started;
    private int rows;
    private int queries;
    private int htmlBytes;

    ReportResultBudget() {
        this(Limits.defaults(), System::nanoTime);
    }

    ReportResultBudget(Limits limits) {
        this(limits, System::nanoTime);
    }

    ReportResultBudget(LongSupplier nanoTime) {
        this(Limits.defaults(), nanoTime);
    }

    ReportResultBudget(Limits limits, LongSupplier nanoTime) {
        this.limits = limits;
        this.nanoTime = nanoTime;
        started = nanoTime.getAsLong();
    }

    void checkDeadline() throws LimitExceededException {
        if (pastDeadline()) {
            throw timeLimit();
        }
    }

    /** Whether the report's time limit has run out. */
    boolean pastDeadline() {
        return nanoTime.getAsLong() - started >= TimeUnit.SECONDS.toNanos(limits.timeoutSeconds());
    }

    /** The refusal for a report that ran out of time, including a JDBC deadline the driver reported. */
    LimitExceededException timeLimit() {
        return new LimitExceededException(Limit.TIME, limits.timeoutSeconds());
    }

    int remainingRows() {
        return limits.maxRows() - rows;
    }

    long deadlineNanos() {
        return started + TimeUnit.SECONDS.toNanos(limits.timeoutSeconds());
    }

    void startQuery() throws LimitExceededException {
        checkDeadline();
        if (++queries > MAX_QUERIES) {
            throw new LimitExceededException(Limit.QUERIES, MAX_QUERIES);
        }
    }

    Rendered render(ResultSet rs, String sqlOnEmpty) throws SQLException, IOException {
        checkDeadline();
        ResultSetMetaData metadata = rs.getMetaData();
        int columns = metadata.getColumnCount();
        if (columns < 1 || columns > limits.maxColumns()) {
            throw new LimitExceededException(Limit.COLUMNS, limits.maxColumns());
        }
        LimitedOutput html = new LimitedOutput(false);
        LimitedOutput csv = new LimitedOutput(true);
        try (Writer htmlWriter = new OutputStreamWriter(html, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(csv, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            if (!rs.next()) {
                checkDeadline();
                htmlWriter.write(sqlOnEmpty == null ? "" : Encode.forHtml(sqlOnEmpty) + "<br/>");
                htmlWriter.write("The query returned no results.");
                htmlWriter.flush();
                return new Rendered(html.text(), "", false);
            }
            htmlWriter.write("<table id=\"report2\" class=\"reportTable display compact\"><thead><tr>");
            for (int column = 1; column <= columns; column++) {
                String label = checkedCell(metadata.getColumnLabel(column));
                htmlWriter.write("<th class=\"reportHeader\">" + Encode.forHtml(label) + "</th>");
                printer.print(SpreadsheetCsv.sanitizeCell(label));
            }
            printer.println();
            htmlWriter.write("</tr></thead><tbody>");
            do {
                checkDeadline();
                if (++rows > limits.maxRows()) {
                    throw new LimitExceededException(Limit.ROWS, limits.maxRows());
                }
                htmlWriter.write("<tr>");
                for (int column = 1; column <= columns; column++) {
                    String value = readCell(rs, column, metadata.getColumnType(column));
                    htmlWriter.write("<td>" + Encode.forHtml(value) + "</td>");
                    printer.print(SpreadsheetCsv.sanitizeCell(value));
                    // Flush bounded UTF-8 encoders before reading another cell.
                    htmlWriter.flush();
                    printer.flush();
                }
                printer.println();
                htmlWriter.write("</tr>");
            } while (rs.next());
            checkDeadline();
            htmlWriter.write("</tbody></table>");
            htmlWriter.flush();
            printer.flush();
            return new Rendered(html.text(), csv.withheld ? "" : csv.text(), csv.withheld);
        }
    }

    private String readCell(ResultSet rs, int column, int type) throws SQLException, IOException {
        // Connector/J cannot expose numeric/date values as character streams.
        // Only bounded Connector/J scalar types may use getString; unfamiliar/large types
        // must use the bounded reader or be refused by the driver.
        boolean scalar = switch (type) {
            case Types.NULL, Types.BOOLEAN, Types.BIT, Types.TINYINT, Types.SMALLINT,
                    Types.INTEGER, Types.BIGINT, Types.REAL, Types.FLOAT, Types.DOUBLE,
                    Types.NUMERIC, Types.DECIMAL, Types.DATE, Types.TIME, Types.TIMESTAMP,
                    Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE -> true;
            default -> false;
        };
        if (scalar) {
            return checkedCell(rs.getString(column));
        }
        try (Reader reader = rs.getCharacterStream(column)) {
            if (reader == null) {
                return "";
            }
            StringBuilder cell = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                checkDeadline();
                if (count > limits.maxCellCharacters() - cell.length()) {
                    throw new LimitExceededException(Limit.CELL, limits.maxCellCharacters());
                }
                cell.append(buffer, 0, count);
            }
            return cell.toString();
        }
    }

    private String checkedCell(String value) throws LimitExceededException {
        checkDeadline();
        if (value != null && value.length() > limits.maxCellCharacters()) {
            throw new LimitExceededException(Limit.CELL, limits.maxCellCharacters());
        }
        return value == null ? "" : value;
    }

    static final class LimitExceededException extends IOException {
        private final Limit limit;
        private final int value;

        LimitExceededException(Limit limit, int value) {
            super(limit.english(value));
            this.limit = limit;
            this.value = value;
        }

        /** Which limit refused the report. */
        Limit limit() {
            return limit;
        }

        /** The limit's value in the unit its message names (rows, columns, characters, MB, queries or seconds). */
        int value() {
            return value;
        }
    }

    private final class LimitedOutput extends OutputStream {
        private ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final boolean csv;
        /** The CSV went over its limit: it is held back and only the HTML goes on (D260). */
        private boolean withheld;
        private int written;

        LimitedOutput(boolean csv) {
            this.csv = csv;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            checkDeadline();
            if (csv) {
                if (withheld) {
                    return;
                }
                // Each query's export form posts only its own CSV, so the limit applies to each query. The shared
                // HTML budget still bounds memory: a query's CSV is no larger than its HTML, except for characters
                // the HTML encoder replaces with a space, which can make it up to four times larger.
                if (length > limits.maxCsvBytes() - written) {
                    // Hold back this query's CSV, free what was kept, and keep showing the report (D260).
                    withheld = true;
                    output = new ByteArrayOutputStream(0);
                    return;
                }
            } else {
                if (length > limits.maxHtmlBytes() - htmlBytes) {
                    throw new LimitExceededException(Limit.HTML, limits.maxHtmlBytes() / MIB);
                }
                htmlBytes += length;
            }
            written += length;
            output.write(bytes, offset, length);
        }

        String text() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
