/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


/** 
* Utility class for generating HTML table structures from JDBC ResultSet data.
* Provides HTML-encoded output to prevent XSS vulnerabilities.
*
* `@since` 1.0
*/

package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.Misc;
import org.owasp.encoder.Encode;

import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

public class RptResultStruct {

    private static final int MIN_OUTPUT_CHARACTERS = 64;
    private static final int CLOSING_MARKUP_RESERVE = 32;
    private static final String TABLE_END = "</table>";
    private static final String HEADER_END = "</th>";
    private static final String ROW_END = "</tr>";
    private static final String CELL_START = "<td>";
    private static final String CELL_END = "</td>";

    public record StructuredResult(String html, int rowCount, boolean truncated, boolean rowLimitReached) {
    }
    
    public static String getStructure(ResultSet rs) throws SQLException {
        return getStructureWithCount(rs).html();
    }

    /**
     * Generates an encoded HTML table and row count from a {@link ResultSet}.
     *
     * @param rs result set positioned before its first row
     * @return encoded table markup and the number of rows rendered
     * @throws SQLException if the result set cannot be read
     */
    public static StructuredResult getStructureWithCount(ResultSet rs) throws SQLException {
        return getStructureWithCount(rs, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Generates an encoded HTML table within a fixed output budget.
     *
     * @param rs result set positioned before its first row
     * @param maxOutputCharacters maximum number of rendered HTML characters
     * @return encoded table markup, rendered row count, and whether output was truncated
     * @throws SQLException if the result set cannot be read
     */
    public static StructuredResult getStructureWithCount(ResultSet rs, int maxOutputCharacters) throws SQLException {
        return getStructureWithCount(rs, maxOutputCharacters, Integer.MAX_VALUE);
    }

    /**
     * Generates an encoded HTML table within output and row budgets.
     *
     * @param rs result set positioned before its first row
     * @param maxOutputCharacters maximum number of rendered HTML characters
     * @param maxRows maximum number of rows to render
     * @return encoded table markup, rendered row count, and truncation state
     * @throws SQLException if the result set cannot be read
     */
    public static StructuredResult getStructureWithCount(ResultSet rs, int maxOutputCharacters, int maxRows)
            throws SQLException {
        validateLimits(maxOutputCharacters, maxRows);
        LimitedHtmlBuilder html = new LimitedHtmlBuilder(maxOutputCharacters);
        ResultSetMetaData rsmd = rs.getMetaData();
        int columns = rsmd.getColumnCount();
        int[] columnTypes = getColumnTypes(rsmd, columns);
        html.appendMarkup("<table id='results'>");
        if (!appendHeaders(html, rsmd, columns)) {
            html.appendClosingMarkup(TABLE_END);
            return new StructuredResult(html.toString(), 0, true, false);
        }

        int rowCount = 0;
        boolean stopRendering = false;
        String rowColor = "rowColor1";
        while (!stopRendering && rowCount < maxRows && rs.next()) {
            rowCount++;
            stopRendering = !appendRow(html, rs, columnTypes, rowColor);
            rowColor = rowColor.equals("rowColor1") ? "rowColor2" : "rowColor1";
        }
        boolean rowLimitReached = !stopRendering && rowCount == maxRows && rs.next();
        html.appendClosingMarkup(TABLE_END);
        return new StructuredResult(html.toString(), rowCount, html.isTruncated(), rowLimitReached);
    }

    private static void validateLimits(int maxOutputCharacters, int maxRows) {
        if (maxOutputCharacters < MIN_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException("HTML output limit is too small");
        }
        if (maxRows < 1) {
            throw new IllegalArgumentException("Row limit must be positive");
        }
    }

    private static boolean appendHeaders(LimitedHtmlBuilder html, ResultSetMetaData metadata, int columns)
            throws SQLException {
        for (int i = 1; i <= columns; i++) {
            if (!appendHeader(html, metadata.getColumnLabel(i))) {
                return false;
            }
        }
        return true;
    }

    private static int[] getColumnTypes(ResultSetMetaData metadata, int columns) throws SQLException {
        int[] columnTypes = new int[columns];
        for (int column = 1; column <= columns; column++) {
            columnTypes[column - 1] = metadata.getColumnType(column);
        }
        return columnTypes;
    }

    private static boolean appendHeader(LimitedHtmlBuilder html, String label) {
        if (!html.appendMarkup("<th class='headerColor'>")) {
            return false;
        }
        if (!html.appendEncoded(label) || !html.appendMarkup(HEADER_END)) {
            html.appendClosingMarkup(HEADER_END);
            return false;
        }
        return true;
    }

    private static boolean appendRow(LimitedHtmlBuilder html, ResultSet resultSet, int[] columnTypes, String rowColor)
            throws SQLException {
        if (!html.appendMarkup("<tr class='" + rowColor + "'>")) {
            return false;
        }
        for (int column = 1; column <= columnTypes.length; column++) {
            if (!appendCell(html, resultSet, column, columnTypes[column - 1])) {
                html.appendClosingMarkup(ROW_END);
                return false;
            }
        }
        if (!html.appendMarkup(ROW_END)) {
            html.appendClosingMarkup(ROW_END);
            return false;
        }
        return true;
    }

    private static boolean appendCell(LimitedHtmlBuilder html, ResultSet resultSet, int column, int columnType)
            throws SQLException {
        if (!html.appendMarkup(CELL_START)) {
            return false;
        }
        boolean complete;
        if (supportsCharacterStream(columnType)) {
            try (Reader value = resultSet.getCharacterStream(column)) {
                complete = value == null || html.appendEncoded(value);
            } catch (IOException e) {
                throw new SQLException("Could not render query result", e);
            }
        } else {
            complete = html.appendEncoded(resultSet.getString(column));
        }
        if (!complete || !html.appendMarkup(CELL_END)) {
            html.appendClosingMarkup(CELL_END);
            return false;
        }
        return true;
    }

    private static boolean supportsCharacterStream(int columnType) {
        return switch (columnType) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                    Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                    Types.CLOB, Types.NCLOB,
                    Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> true;
            default -> false;
        };
    }

    private static final class LimitedHtmlBuilder {
        private static final int READ_BUFFER_SIZE = 2_048;

        private final StringBuilder html;
        private final int contentLimit;
        private boolean truncated;

        LimitedHtmlBuilder(int maxOutputCharacters) {
            contentLimit = maxOutputCharacters - CLOSING_MARKUP_RESERVE;
            html = new StringBuilder(Math.min(maxOutputCharacters, 8_192));
        }

        boolean appendMarkup(String markup) {
            if (markup.length() > remaining()) {
                truncated = true;
                return false;
            }
            html.append(markup);
            return true;
        }

        boolean appendEncoded(String value) {
            return appendEncodedChunk(Encode.forHtml(value == null ? "" : value));
        }

        boolean appendEncoded(Reader value) throws IOException {
            char[] buffer = new char[READ_BUFFER_SIZE];
            int read;
            while ((read = value.read(buffer)) != -1) {
                if (!appendEncodedChunk(Encode.forHtml(new String(buffer, 0, read)))) {
                    return false;
                }
            }
            return true;
        }

        private boolean appendEncodedChunk(String encoded) {
            int remaining = remaining();
            if (encoded.length() <= remaining) {
                html.append(encoded);
                return true;
            }
            if (remaining > 0) {
                int contentCharacters = Math.max(0, remaining - 1);
                int safeCharacters = entitySafePrefixLength(encoded, contentCharacters);
                html.append(encoded, 0, safeCharacters).append('\u2026');
            }
            truncated = true;
            return false;
        }

        private static int entitySafePrefixLength(String encoded, int requestedLength) {
            if (requestedLength == 0) {
                return 0;
            }
            int lastEntityStart = encoded.lastIndexOf('&', requestedLength - 1);
            int lastEntityEnd = encoded.lastIndexOf(';', requestedLength - 1);
            return lastEntityStart > lastEntityEnd ? lastEntityStart : requestedLength;
        }

        void appendClosingMarkup(String markup) {
            html.append(markup);
        }

        boolean isTruncated() {
            return truncated;
        }

        private int remaining() {
            return contentLimit - html.length();
        }

        @Override
        public String toString() {
            return html.toString();
        }
    }

    //improvement over getStructure() - changed CSS naming conventions, added enterspaces for cleaner html,
//added more CSS classes for additional customization
//used by 'report by template'
/*
CSS:
 *table.reportTable {}
 *th.reportHeader{}
 *tr.reportRow1{}
 *tr.reportRow2{}
 *td.reportCell{}
 */
//~apavel (Paul)
    public static String getStructure2(ResultSet rs) throws SQLException {

    /**
    * Generates an HTML table from a ResultSet with enhanced styling for report templates.
    * Includes proper thead/tbody structure, DataTables-compatible classes, and XSS protection.
    * Each column header and cell value is HTML-encoded using OWASP Encoder.
    *
    * `@param` rs the ResultSet containing data to display; must be positioned before the first row
    * `@return` an HTML string containing a complete table with id="report2" and DataTables classes
    * `@throws` SQLException if a database access error occurs or the ResultSet is closed
    * `@since` 1.0
    */

// assuming  multiple rows in rs
        StringBuilder sb = new StringBuilder();
        boolean results = true;
        ResultSetMetaData rsmd = rs.getMetaData();

        int columns = rsmd.getColumnCount();
        String[] columnNames = new String[columns];
        sb.append("<table id=\"report2\" class=\"reportTable display compact\">");
        sb.append("<thead><tr>");
        for (int i = 0; i < columns; i++) {  // for each column in result set
            columnNames[i] = rsmd.getColumnName(i + 1);
            // put names in array
            // use i+1 or else you're going to get an exception
            //  insert headings for table
            sb.append("<th class=\"reportHeader\">");
            sb.append(Encode.forHtml(columnNames[i]));
            sb.append(HEADER_END);
        }
        sb.append("</tr></thead><tbody>");

        if (!rs.next()) {
            sb.append("</tbody></table><center><font color=\"red\">No Results</font></center>");
            results = false;
        } else {
            do {
                sb.append("<tr>");
                for (int j = 0; j < columns; j++) {
                    sb.append("<td>");
                    sb.append(Encode.forHtml(Misc.getString(rs, columnNames[j])));
                    sb.append(CELL_END);

                }
                sb.append(ROW_END);
            } while (rs.next());
        }
        if (results) {
            sb.append("</tbody></table>");
        }
        return sb.toString();
    }
}
