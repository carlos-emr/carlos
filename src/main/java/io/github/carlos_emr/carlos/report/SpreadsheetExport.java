/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Small, typed OOXML export. Strings remain strings, including formula-looking values.
 *
 * <p>The workbook is streamed, never buffered, so exports may be large. The caller's stream is
 * flushed only once the whole package, central directory included, has been written. When
 * anything fails part-way the package is abandoned: no central directory, no flush, no close. A
 * servlet response that has not yet filled its buffer therefore stays uncommitted, and the
 * caller can still reset it and report an error instead of sending a truncated workbook.
 */
public final class SpreadsheetExport {
    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Rows one worksheet holds. */
    public static final int MAX_ROWS = 1_048_576;
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    /**
     * A literal underscore that a reader could take as the start of an {@code _xHHHH_} escape.
     * The closing underscore is deliberately not required: it may be supplied by the escape this
     * writer generates for the next character (unprotected, {@code "_x0041\r"} would be written
     * {@code _x0041_x000D_} and read back as {@code "Ax000D_"}). Every such underscore is written
     * as {@code _x005F_}, which always decodes to {@code _}, so over-escaping is harmless.
     */
    private static final java.util.regex.Pattern EXCEL_ESCAPE = java.util.regex.Pattern.compile("_(?=x[0-9a-fA-F]{4})");
    /** StAX factories are thread-safe once configured; this one is never reconfigured. */
    private static final XMLOutputFactory XML_OUTPUT = XMLOutputFactory.newFactory();
    private SpreadsheetExport() { }

    /** Writes one sheet; the caller owns the response stream. Cells are Strings or finite Doubles. */
    public static void write(OutputStream output, Object[][] rows) throws IOException {
        if (rows.length > MAX_ROWS) throw new IllegalArgumentException("Too many spreadsheet rows");
        for (Object[] row : rows) {
            validateRow(row);
        }
        writeSheet(output, java.util.Arrays.asList(rows), "OSCAR_Report", null);
    }

    /**
     * Streams the same sheet as {@link #write(OutputStream, Object[][])} from rows read as they are written,
     * so the caller never holds the whole table. Each row is checked as it is read, and an invalid row abandons
     * the package part-way, as described on the class; a caller that must not send a partial workbook checks
     * every row with {@link #checkRow} first.
     */
    public static void writeRows(OutputStream output, Iterable<Object[]> rows) throws IOException {
        writeSheet(output, rows, "OSCAR_Report", null);
    }

    /**
     * Checks that a row can be written: at most 16,384 cells, each a String of at most 32,767 characters, a
     * finite Double or null.
     *
     * @throws IllegalArgumentException naming what the row breaks
     */
    public static void checkRow(Object[] row) {
        validateRow(row);
    }

    /**
     * Streams a named table with a frozen header and 22-character columns; the caller owns output.
     * Rows are validated as they are read, so an invalid row aborts the export part-way; the
     * package is then abandoned as described on the class.
     */
    public static void writeTable(OutputStream output, String sheetName, String[] headers,
                                  Iterable<Object[]> rows) throws IOException {
        if (sheetName == null || sheetName.isEmpty() || sheetName.length() > 31
                || sheetName.startsWith("'") || sheetName.endsWith("'")
                || sheetName.codePoints().anyMatch(ch -> ch < 0x20 || "[]:*?/\\".indexOf(ch) >= 0
                    || (ch >= 0xd800 && ch <= 0xdfff) || ch == 0xfffe || ch == 0xffff)) {
            throw new IllegalArgumentException("Invalid spreadsheet sheet name");
        }
        if (headers.length == 0) throw new IllegalArgumentException("Spreadsheet headers are required");
        validateRow(headers);
        writeSheet(output, rows, sheetName, headers);
    }

    private static void writeSheet(OutputStream output, Iterable<Object[]> rows, String sheetName,
                                   String[] headers) throws IOException {
        UncommittedOutput target = new UncommittedOutput(output);
        ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8);
        try {
            writePackage(zip, rows, sheetName, headers);
            // close() finishes the archive (central directory) and releases the Deflater; the
            // guard keeps it from closing the caller's stream.
            zip.close();
        } catch (IOException | RuntimeException | Error failure) { // NOSONAR java:S1181 — discards the partial workbook and rethrows
            // Do not let close() finish the archive: a finished ZIP would look like a complete,
            // shorter workbook. Discard the remaining bytes and only release the Deflater.
            target.abandon();
            try {
                zip.close();
            } catch (IOException | RuntimeException cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        output.flush();
    }

    private static void writePackage(ZipOutputStream zip, Iterable<Object[]> rows, String sheetName,
                                     String[] headers) throws IOException {
        entry(zip, "[Content_Types].xml", """
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>""");
        entry(zip, "_rels/.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""");
        xmlEntry(zip, "xl/workbook.xml", xml -> workbook(xml, sheetName));
        entry(zip, "xl/_rels/workbook.xml.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>""");
        xmlEntry(zip, "xl/worksheets/sheet1.xml", xml -> worksheet(xml, rows, headers));
    }

    private static void worksheet(XMLStreamWriter xml, Iterable<Object[]> rows, String[] headers)
            throws IOException, XMLStreamException {
        xml.writeStartElement("worksheet");
        xml.writeDefaultNamespace(NS);
        if (headers != null) {
            xml.writeStartElement("sheetViews");
            xml.writeStartElement("sheetView");
            xml.writeAttribute("workbookViewId", "0");
            xml.writeEmptyElement("pane");
            xml.writeAttribute("ySplit", "1");
            xml.writeAttribute("topLeftCell", "A2");
            xml.writeAttribute("activePane", "bottomLeft");
            xml.writeAttribute("state", "frozen");
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeStartElement("cols");
            xml.writeEmptyElement("col");
            xml.writeAttribute("min", "1");
            xml.writeAttribute("max", Integer.toString(headers.length));
            xml.writeAttribute("width", "22");
            xml.writeAttribute("customWidth", "1");
            xml.writeEndElement();
        }
        xml.writeStartElement("sheetData");
        int rowNumber = 0;
        if (headers != null) writeRow(xml, headers, ++rowNumber);
        for (Object[] row : rows) {
            if (rowNumber >= MAX_ROWS) throw new IOException("Export exceeds worksheet row limit");
            validateRow(row);
            writeRow(xml, row, ++rowNumber);
        }
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private static void validateRow(Object[] row) {
        if (row.length > 16_384) throw new IllegalArgumentException("Too many spreadsheet columns");
        for (Object cell : row) {
            if (cell != null && !(cell instanceof String) && !(cell instanceof Double)) {
                throw new IllegalArgumentException("Spreadsheet cells must be strings or finite doubles");
            }
            if (cell instanceof String text && text.length() > 32_767) {
                throw new IllegalArgumentException("Spreadsheet cell text is too long");
            }
            if (cell instanceof Double number && !Double.isFinite(number)) {
                throw new IllegalArgumentException("Spreadsheet numbers must be finite");
            }
        }
    }

    private static void workbook(XMLStreamWriter xml, String sheetName) throws XMLStreamException {
        String relationships = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        xml.writeStartElement("workbook");
        xml.writeDefaultNamespace(NS);
        xml.writeNamespace("r", relationships);
        xml.writeStartElement("sheets");
        xml.writeEmptyElement("sheet");
        xml.writeAttribute("name", sheetName);
        xml.writeAttribute("sheetId", "1");
        xml.writeAttribute("r", relationships, "id", "rId1");
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private static void writeRow(XMLStreamWriter xml, Object[] row, int number) throws XMLStreamException {
        xml.writeStartElement("row");
        xml.writeAttribute("r", Integer.toString(number));
        for (int c = 0; c < row.length; c++) {
            Object value = row[c];
            xml.writeStartElement("c");
            xml.writeAttribute("r", columnName(c) + number);
            boolean numeric = value instanceof Double;
            xml.writeAttribute("t", numeric ? "n" : "inlineStr");
            if (numeric) {
                xml.writeStartElement("v");
                xml.writeCharacters(value.toString());
                xml.writeEndElement();
            } else {
                xml.writeStartElement("is");
                xml.writeStartElement("t");
                xml.writeAttribute("xml", "http://www.w3.org/XML/1998/namespace", "space", "preserve");
                xml.writeCharacters(escapeExcelText(value == null ? "" : value.toString()));
                xml.writeEndElement();
                xml.writeEndElement();
            }
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** One XML part written through StAX; a writer of part content may also fail with I/O. */
    @FunctionalInterface
    private interface XmlPart {
        void write(XMLStreamWriter xml) throws IOException, XMLStreamException;
    }

    private static void xmlEntry(ZipOutputStream zip, String name, XmlPart part) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try {
            XMLStreamWriter xml = XML_OUTPUT.createXMLStreamWriter(zip, StandardCharsets.UTF_8.name());
            xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            part.write(xml);
            xml.writeEndDocument();
            // StAX close() releases the writer and, by contract, never closes the ZIP stream. On
            // failure the writer is not closed: the whole package is abandoned by writeSheet.
            xml.close();
        } catch (XMLStreamException failure) {
            // StAX wraps a failure of the underlying stream (a client abort, a full disk); report
            // that I/O failure itself rather than a generic writer error.
            for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof IOException io) throw io;
            }
            throw new IOException("Cannot write spreadsheet", failure);
        }
        zip.closeEntry();
    }

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) name.append((char) ('A' + (n - 1) % 26));
        return name.reverse().toString();
    }

    private static String escapeExcelText(String text) {
        // OOXML encodes XML-forbidden code units as _xHHHH_. Escape literal underscores first, on
        // the original text, so the escapes generated below are never themselves escaped. Match
        // only the opening underscore and do not require the closing one (see EXCEL_ESCAPE), so
        // overlapping text such as _x0041_x0042_, and text such as _x0041 followed by U+0001
        // (whose generated _x0001_ would supply a closing underscore), both stay literal.
        String literal = EXCEL_ESCAPE.matcher(text).replaceAll("_x005F_");
        StringBuilder result = new StringBuilder();
        literal.codePoints().forEach(codePoint -> {
            if ((codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)
                    || (codePoint < 0x20 && codePoint != '\t' && codePoint != '\n')
                    || codePoint == 0xfffe || codePoint == 0xffff) {
                result.append(String.format(java.util.Locale.ROOT, "_x%04X_", codePoint));
            } else {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    /**
     * Passes package bytes to the caller's stream but never flushes or closes it: flushing a
     * servlet response commits it, and the caller owns the stream. {@link #abandon()} discards
     * every later byte, so releasing a failed {@link ZipOutputStream} cannot complete the archive.
     */
    private static final class UncommittedOutput extends FilterOutputStream {
        private boolean abandoned;

        UncommittedOutput(OutputStream target) {
            super(target);
        }

        @Override
        public void write(int value) throws IOException {
            if (!abandoned) out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (!abandoned) out.write(bytes, offset, length);
        }

        @Override
        public void flush() {
            // writeSheet flushes the caller's stream itself, once, after the archive is complete.
        }

        @Override
        public void close() {
            // The caller owns the target stream.
        }

        void abandon() {
            abandoned = true;
        }
    }
}
