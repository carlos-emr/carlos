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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.owasp.encoder.Encode;

import io.github.carlos_emr.carlos.utility.SpreadsheetCsv;

/**
 * Request-scoped budgets shared by every query of a sequenced template report.
 * Only the current cell and bounded HTML/CSV buffers are retained. JDBC may retain
 * the current row; Connector/J streaming prevents whole-result buffering.
 */
final class ReportResultBudget {
    static final int MAX_ROWS = 10_000;
    static final int MAX_HTML_BYTES = 10 * 1024 * 1024;
    static final int MAX_COLUMNS = 256;
    static final int MAX_CELL_CHARACTERS = 64 * 1024;
    static final int MAX_QUERIES = 32;
    static final int TIMEOUT_SECONDS = 30;

    private final LongSupplier nanoTime;
    private final long started;
    private int rows;
    private int queries;
    private int htmlBytes;
    private int csvBytes;

    ReportResultBudget() {
        this(System::nanoTime);
    }

    ReportResultBudget(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        started = nanoTime.getAsLong();
    }

    void checkDeadline() throws LimitExceededException {
        if (nanoTime.getAsLong() - started >= TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)) {
            throw new LimitExceededException(SQLReporter.REPORT_LIMIT_MESSAGE);
        }
    }

    int remainingRows() {
        return MAX_ROWS - rows;
    }

    int remainingSeconds() throws LimitExceededException {
        checkDeadline();
        long remaining = TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS) - (nanoTime.getAsLong() - started);
        return (int) Math.max(1, (remaining + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
    }

    void startQuery() throws LimitExceededException {
        checkDeadline();
        if (++queries > MAX_QUERIES) {
            throw new LimitExceededException(SQLReporter.REPORT_LIMIT_MESSAGE);
        }
    }

    String[] render(ResultSet rs, String sqlOnEmpty) throws SQLException, IOException {
        checkDeadline();
        LimitedOutput html = new LimitedOutput(false);
        Writer htmlWriter = new OutputStreamWriter(html, StandardCharsets.UTF_8);
        if (!rs.next()) {
            checkDeadline();
            htmlWriter.write(sqlOnEmpty == null ? "" : Encode.forHtml(sqlOnEmpty) + "<br/>");
            htmlWriter.write("The query returned no results.");
            htmlWriter.flush();
            return new String[]{html.text(), ""};
        }
        ResultSetMetaData metadata = rs.getMetaData();
        int columns = metadata.getColumnCount();
        if (columns < 1 || columns > MAX_COLUMNS) {
            throw new LimitExceededException(SQLReporter.REPORT_LIMIT_MESSAGE);
        }
        LimitedOutput csv = new LimitedOutput(true);
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(csv, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
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
                if (++rows > MAX_ROWS) {
                    throw new LimitExceededException(SQLReporter.REPORT_LIMIT_MESSAGE);
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
            return new String[]{html.text(), csv.text()};
        }
    }

    private String readCell(ResultSet rs, int column, int type) throws SQLException, IOException {
        // Connector/J cannot expose numeric/date values as character streams.
        // These scalar representations are small; large text/binary fields use a bounded reader.
        boolean stream = switch (type) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                    Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB, Types.BINARY, Types.VARBINARY,
                    Types.LONGVARBINARY, Types.BLOB -> true;
            default -> false;
        };
        if (!stream) {
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
                if (count > MAX_CELL_CHARACTERS - cell.length()) {
                    throw new LimitExceededException(SQLReporter.REPORT_LIMIT_MESSAGE);
                }
                cell.append(buffer, 0, count);
            }
            return cell.toString();
        }
    }

    private String checkedCell(String value) throws LimitExceededException {
        checkDeadline();
        if (value != null && value.length() > MAX_CELL_CHARACTERS) {
            throw new LimitExceededException(SQLReporter.REPORT_LIMIT_MESSAGE);
        }
        return value == null ? "" : value;
    }

    static final class LimitExceededException extends IOException {
        LimitExceededException(String message) {
            super(message);
        }
    }

    private final class LimitedOutput extends OutputStream {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final boolean csv;

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
                if (length > SQLReporter.MAX_CSV_EXPORT_LENGTH - csvBytes) {
                    throw new LimitExceededException(SQLReporter.CSV_EXPORT_LIMIT_MESSAGE);
                }
                csvBytes += length;
            } else {
                if (length > MAX_HTML_BYTES - htmlBytes) {
                    throw new LimitExceededException(SQLReporter.REPORT_LIMIT_MESSAGE);
                }
                htmlBytes += length;
            }
            output.write(bytes, offset, length);
        }

        String text() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
