/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Small, typed OOXML export. Strings remain strings, including formula-looking values. */
public final class SpreadsheetExport {
    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final java.util.regex.Pattern EXCEL_ESCAPE = java.util.regex.Pattern.compile("_x([0-9a-fA-F]{4})_");
    private SpreadsheetExport() { }

    /** Writes one sheet; the caller owns the response stream. Cells are Strings or finite Doubles. */
    public static void write(OutputStream output, Object[][] rows) throws IOException {
        if (rows.length > 1_048_576) throw new IllegalArgumentException("Too many spreadsheet rows");
        for (Object[] row : rows) {
            validateRow(row);
        }
        writeSheet(output, java.util.Arrays.asList(rows), "OSCAR_Report", null);
    }

    /** Streams a named table with a frozen header and 22-character columns; the caller owns output. */
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
        // finish(), not close(): a servlet container owns output. The ZIP deflater is still
        // released by closing the wrapper over a non-closing stream.
        try (ZipOutputStream zip = new ZipOutputStream(new java.io.FilterOutputStream(output) {
            @Override public void close() throws IOException { flush(); }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }
        }, StandardCharsets.UTF_8)) {
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
            workbook(zip, sheetName);
            entry(zip, "xl/_rels/workbook.xml.rels", """
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>""");
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(zip, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
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
                if (rowNumber >= 1_048_576) throw new IOException("Export exceeds worksheet row limit");
                validateRow(row);
                writeRow(xml, row, ++rowNumber);
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            zip.closeEntry();
        } catch (XMLStreamException failure) {
            throw new IOException("Cannot write spreadsheet", failure);
        }
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

    private static void workbook(ZipOutputStream zip, String sheetName) throws IOException, XMLStreamException {
        zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
        XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(zip, "UTF-8");
        String relationships = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        xml.writeStartDocument("UTF-8", "1.0");
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
        xml.writeEndDocument();
        xml.flush();
        zip.closeEntry();
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

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) name.append((char) ('A' + (n - 1) % 26));
        return name.reverse().toString();
    }

    private static String escapeExcelText(String text) {
        // OOXML encodes XML-forbidden code units as _xHHHH_. Escape literal occurrences first.
        String literal = EXCEL_ESCAPE.matcher(text).replaceAll("_x005F_x$1_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            char ch = literal.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < literal.length()
                    && Character.isLowSurrogate(literal.charAt(i + 1))) {
                result.append(ch).append(literal.charAt(++i));
            } else if (Character.isSurrogate(ch) || (ch < 0x20 && ch != '\t' && ch != '\n')
                    || ch == 0xfffe || ch == 0xffff) {
                result.append(String.format(java.util.Locale.ROOT, "_x%04X_", (int) ch));
            } else result.append(ch);
        }
        return result.toString();
    }
}
