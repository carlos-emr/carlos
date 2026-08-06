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

public class RptResultStruct {

    private static final int MIN_OUTPUT_CHARACTERS = 64;
    private static final int CLOSING_MARKUP_RESERVE = 32;

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
        if (maxOutputCharacters < MIN_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException("HTML output limit is too small");
        }
        if (maxRows < 1) {
            throw new IllegalArgumentException("Row limit must be positive");
        }

    // assuming  multiple rows in rs
        LimitedHtmlBuilder html = new LimitedHtmlBuilder(maxOutputCharacters);

        ResultSetMetaData rsmd = rs.getMetaData();

        int columns = rsmd.getColumnCount();
        String rowColor = "rowColor1";
        html.appendMarkup("<table id='results'>");
        for (int i = 0; i < columns; i++) {  // for each column in result set
            if (!html.appendMarkup("<th class='headerColor'>")
                    || !html.appendEncoded(rsmd.getColumnLabel(i + 1))) {
                html.appendClosingMarkup("</th></table>");
                return new StructuredResult(html.toString(), 0, true, false);
            }
            if (!html.appendMarkup("</th>")) {
                html.appendClosingMarkup("</th></table>");
                return new StructuredResult(html.toString(), 0, true, false);
            }
        }
        int rowCount = 0;
        boolean stopRendering = false;
        while (!stopRendering && rowCount < maxRows && rs.next()) {
            rowCount++;
            if (!html.appendMarkup("<tr class='" + rowColor + "'>")) {
                break;
            }
            for (int j = 0; j < columns; j++) {
                if (!html.appendMarkup("<td>")) {
                    stopRendering = true;
                    break;
                }
                try (Reader value = rs.getCharacterStream(j + 1)) {
                    if (value != null && !html.appendEncoded(value)) {
                        stopRendering = true;
                    }
                } catch (IOException e) {
                    throw new SQLException("Could not render query result", e);
                }
                if (stopRendering) {
                    html.appendClosingMarkup("</td>");
                    break;
                }
                if (!html.appendMarkup("</td>")) {
                    html.appendClosingMarkup("</td>");
                    stopRendering = true;
                    break;
                }
            }
            rowColor = rowColor.equals("rowColor1") ? "rowColor2" : "rowColor1";
            if (stopRendering || !html.appendMarkup("</tr>")) {
                html.appendClosingMarkup("</tr>");
                stopRendering = true;
            }
        }
        boolean rowLimitReached = !stopRendering && rowCount == maxRows && rs.next();
        html.appendClosingMarkup("</table>");
        return new StructuredResult(html.toString(), rowCount, html.isTruncated(), rowLimitReached);
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
            sb.append("</th>");
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
                    sb.append("</td>");

                }
                sb.append("</tr>");
            } while (rs.next());
        }
        if (results) {
            sb.append("</tbody></table>");
        }
        return sb.toString();
    }
}
