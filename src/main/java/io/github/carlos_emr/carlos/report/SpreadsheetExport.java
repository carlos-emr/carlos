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
            entry(zip, "xl/workbook.xml", """
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="OSCAR_Report" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>""");
            entry(zip, "xl/_rels/workbook.xml.rels", """
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>""");
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(zip, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("worksheet");
            xml.writeDefaultNamespace(NS);
            xml.writeStartElement("sheetData");
            for (int r = 0; r < rows.length; r++) {
                xml.writeStartElement("row");
                xml.writeAttribute("r", Integer.toString(r + 1));
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    xml.writeStartElement("c");
                    xml.writeAttribute("r", columnName(c) + (r + 1));
                    boolean number = value instanceof Double;
                    xml.writeAttribute("t", number ? "n" : "inlineStr");
                    if (number) {
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
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            zip.closeEntry();
        } catch (XMLStreamException failure) {
            throw new IOException("Cannot write spreadsheet", failure);
        }
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
        // OOXML encodes XML-forbidden controls as _xHHHH_. Escape literal occurrences first.
        String literal = EXCEL_ESCAPE.matcher(text).replaceAll("_x005F_x$1_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            char ch = literal.charAt(i);
            if ((ch < 0x20 && ch != '\t' && ch != '\n') || ch == 0xfffe || ch == 0xffff) {
                result.append(String.format(java.util.Locale.ROOT, "_x%04X_", (int) ch));
            } else result.append(ch);
        }
        return result.toString();
    }
}
