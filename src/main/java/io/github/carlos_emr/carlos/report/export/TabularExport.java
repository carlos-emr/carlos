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

/** UTF-8, LF-delimited spreadsheet CSV using the shared formula policy. */
public final class TabularExport implements AutoCloseable {
    private final CSVPrinter printer;

    public TabularExport(OutputStream output, String... headers) throws IOException {
        printer = new CSVPrinter(new OutputStreamWriter(output, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setRecordSeparator("\n").setQuoteMode(QuoteMode.ALL).get());
        row((Object[]) headers);
    }

    public void row(Object... values) throws IOException {
        printer.printRecord(Arrays.stream(values)
                .map(v -> SpreadsheetCsv.sanitizeCell(v == null ? "" : v.toString())).toList());
    }

    @Override
    public void close() throws IOException {
        printer.close();
    }
}
