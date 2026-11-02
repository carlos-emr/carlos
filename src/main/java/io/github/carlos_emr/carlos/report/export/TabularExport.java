/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import io.github.carlos_emr.carlos.utility.SpreadsheetCsv;

/**
 * UTF-8, LF-delimited spreadsheet CSV using the shared formula policy.
 *
 * @since 2026-11-02
 */
public final class TabularExport implements AutoCloseable {
    private final CSVPrinter printer;

    /**
     * Starts a CSV with one fully quoted, formula-sanitized header record.
     *
     * @param output OutputStream receiving UTF-8 bytes, owned by this writer until close
     * @param headers String array of column names in output order
     * @throws IOException if the header cannot be written
     */
    public TabularExport(OutputStream output, String... headers) throws IOException {
        printer = new CSVPrinter(new OutputStreamWriter(output, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setRecordSeparator("\n").setQuoteMode(QuoteMode.ALL).get());
        row((Object[]) headers);
    }

    /**
     * Writes one fully quoted record after applying the shared spreadsheet formula policy.
     *
     * @param values Object array of cells in column order; null cells become empty strings
     * @throws IOException if the record cannot be written
     */
    public void row(Object... values) throws IOException {
        printer.printRecord(Arrays.stream(values)
                .map(v -> SpreadsheetCsv.sanitizeCell(v == null ? "" : v.toString())).toList());
    }

    /**
     * Flushes the CSV and closes the underlying output stream.
     *
     * @throws IOException if flushing or closing fails
     */
    @Override
    public void close() throws IOException {
        printer.close();
    }
}
