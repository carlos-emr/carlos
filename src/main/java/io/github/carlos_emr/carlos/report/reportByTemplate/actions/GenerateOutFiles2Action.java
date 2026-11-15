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
//This action generates the CSV and XLSX files on request


package io.github.carlos_emr.carlos.report.reportByTemplate.actions;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.SpreadsheetExport;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportCsvDownloads;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Created on December 21, 2006, 10:47 AM
 *
 * @author apavel (Paul)
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Report by Template's "Export to CSV" and "Export to XLSX": builds the file on the server from the CSV the
 * report kept for this session ({@link ReportCsvDownloads}).
 *
 * <p>The result page posts only the result's token ({@code resultToken}), the query's index
 * ({@code resultIndex}) and the format ({@code getCSV} or {@code getXLS}). Any {@code csv} parameter, which a
 * result page from before this change posts, is ignored: a download is always what the query returned, never
 * what the browser sends. A token this session and provider no longer hold (expired, dropped for a newer
 * result, from another session, or missing) is answered with HTTP 410 and a localized page asking the user to
 * run the report again, never with an error page inside a download.</p>
 *
 * <p>A download only reads a kept result, so the route is a read: GET and POST are served (the page posts,
 * with its CSRF token, to keep the token out of URLs) and other methods are refused with 405. Both downloads,
 * and the 410 page, are {@code Cache-Control: no-store}: a report is patient data, and no browser or proxy may
 * keep a copy.</p>
 *
 * <p>Neither format copies the kept CSV: the CSV download writes its bytes as they are, and the XLSX export
 * reads them one row at a time, twice (once to check every row, once to write), so an export near the CSV
 * limit holds one row, not the whole table, however many run at once.</p>
 */
public class GenerateOutFiles2Action extends ActionSupport {
    /** Result naming the "run the report again" page, for a token this session no longer holds. */
    static final String UNAVAILABLE = "unavailable";
    private static final Pattern CANONICAL_DECIMAL = Pattern.compile("-?(?:0|[1-9]\\d*+)(?:\\.\\d++)?");
    private final SecurityInfoManager securityInfoManager;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public GenerateOutFiles2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    GenerateOutFiles2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Sends one query's kept CSV as a CSV or XLSX attachment.
     *
     * @return {@link #NONE} once the response is written (the download, or a 400, 405 or 500 status), or
     *     {@link #UNAVAILABLE}, with status 410, when this session and provider hold no such result
     * @throws SecurityException when the signed-in provider has neither {@code _admin} nor {@code _report} read
     */
    @Override
    public String execute() throws IOException {

        // The same privileges as running the report (GenerateReport2Action).
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }

        String method = request.getMethod();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            response.setHeader("Allow", "GET, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        boolean csvFormat = request.getParameter("getCSV") != null;
        if (!csvFormat && request.getParameter("getXLS") == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        HttpSession session = request.getSession(false);
        String token = request.getParameter("resultToken");
        String index = request.getParameter("resultIndex");
        Optional<ReportCsvDownloads.Csv> kept = ReportCsvDownloads.find(session, loggedInInfo.getLoggedInProviderNo(), token, index);
        if (kept.isEmpty()) {
            // Nothing written yet, so the page result is safe. No request value is logged: the token is a secret.
            MiscUtils.getLogger().info("Report by Template download refused: the result is no longer available to this session");
            response.setStatus(HttpServletResponse.SC_GONE);
            response.setHeader("Cache-Control", "no-store");
            int templateId = ConversionUtils.fromIntString(request.getParameter("templateid"));
            if (templateId > 0) {
                request.setAttribute("templateid", templateId);
            }
            return UNAVAILABLE;
        }
        ReportCsvDownloads.Csv csv = kept.get();
        if (csvFormat) {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"oscarReport.csv\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "no-store");
            try {
                csv.writeTo(response.getOutputStream()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV attachment response with text/csv UTF-8 content type and nosniff header, built from the server-kept report result; not an HTML response
            } catch (Exception ioe) {
                MiscUtils.getLogger().error("Error", ioe);
            }
            // NONE tells Struts not to resolve a JSP result — required for all direct-response actions.
            return NONE;
        }
        MiscUtils.getLogger().debug("Generating Spread Sheet file for the 'report by template' module ..");

        // First pass: every row is read and checked before a byte is written, so a CSV that cannot be parsed,
        // or a cell a workbook cannot hold, is a clean error status rather than a truncated workbook.
        try (CSVParser parser = CSVParser.parse(csv.reader(), CSVFormat.DEFAULT)) {
            long rows = 0;
            for (CSVRecord record : parser) {
                if (++rows > SpreadsheetExport.MAX_ROWS) {
                    throw new IllegalArgumentException("Too many spreadsheet rows");
                }
                SpreadsheetExport.checkRow(cells(record, rows == 1));
            }
        } catch (IOException | UncheckedIOException e) {
            MiscUtils.getLogger().error("Error parsing CSV", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().error("Error checking the XLSX rows", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return NONE;
        }

        response.setContentType(SpreadsheetExport.CONTENT_TYPE);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=\"oscarReport.xlsx\"");
        response.setHeader("Cache-Control", "no-store");

        // Second pass: the same rows, converted and written one at a time.
        try (CSVParser parser = CSVParser.parse(csv.reader(), CSVFormat.DEFAULT)) {
            Iterator<CSVRecord> records = parser.iterator();
            Iterable<Object[]> rows = () -> new Iterator<>() {
                private boolean header = true;

                @Override
                public boolean hasNext() {
                    return records.hasNext();
                }

                @Override
                public Object[] next() {
                    Object[] row = cells(records.next(), header);
                    header = false;
                    return row;
                }
            };
            SpreadsheetExport.writeRows(response.getOutputStream(), rows);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error writing XLSX", e);
            setServerErrorIfPossible();
        }
        return NONE;
    }

    /**
     * One CSV record as workbook cells: ordinary numbers become numbers, except in the first (header) row,
     * and everything else stays text.
     */
    private static Object[] cells(CSVRecord record, boolean header) {
        Object[] cells = new Object[record.size()];
        for (int i = 0; i < cells.length; i++) {
            String value = record.get(i);
            Double numeric = header ? null : exactSpreadsheetNumber(value);
            cells[i] = numeric == null ? value : numeric;
        }
        return cells;
    }

    /**
     * Keeps ordinary report numbers usable in calculations, while preserving ambiguous
     * identifiers and values beyond Excel's 15-digit precision as text. CSV has no type
     * metadata, so only canonical decimal notation is eligible for numeric conversion.
     */
    private static Double exactSpreadsheetNumber(String value) {
        // At most 15 digits plus a sign and decimal separator; bound parsing work too.
        if (value.length() > 17 || !CANONICAL_DECIMAL.matcher(value).matches()) return null;
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.precision() > 15 || (decimal.signum() == 0 && value.startsWith("-"))) return null;
        double number = decimal.doubleValue();
        return Double.isFinite(number) && BigDecimal.valueOf(number).compareTo(decimal) == 0 ? number : null;
    }

    private void setServerErrorIfPossible() {
        if (!response.isCommitted()) {
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
