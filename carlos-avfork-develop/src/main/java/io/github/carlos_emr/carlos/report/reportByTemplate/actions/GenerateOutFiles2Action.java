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
//This action generates the CSV and XLS files on request


package io.github.carlos_emr.carlos.report.reportByTemplate.actions;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.report.reportByTemplate.SQLReporter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Created on December 21, 2006, 10:47 AM
 *
 * @author apavel (Paul)
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class GenerateOutFiles2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public GenerateOutFiles2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    GenerateOutFiles2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }

        // CSV is read from the POST body (a hidden form field in resultReport.jsp), not from the
        // HTTP session. The old approach stored the CSV string in the session, which accumulated
        // across report runs and eventually caused out-of-memory crashes on large result sets.
        String csv = request.getParameter("csv");
        String action = request.getParameter("getCSV");
        if (action != null) {
            // Reject null or oversized payloads before touching the response stream.
            if (!validateCsv(csv)) {
                return NONE;
            }
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"oscarReport.csv\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            try {
                response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8)); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV attachment response with text/csv UTF-8 content type, nosniff header, and size validation; not an HTML response
            } catch (Exception ioe) {
                MiscUtils.getLogger().error("Error", ioe);
            }
            // NONE tells Struts not to resolve a JSP result — required for all direct-response actions.
            return NONE;
        }
        action = request.getParameter("getXLS");
        if (action != null) {
            if (!validateCsv(csv)) {
                return NONE;
            }
            MiscUtils.getLogger().debug("Generating Spread Sheet file for the 'report by template' module ..");

            // Parse the POST'd CSV string into a 2-D array for POI cell population.
            String[][] data;
            try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)) {
                List<CSVRecord> records = parser.getRecords();
                data = new String[records.size()][];
                for (int i = 0; i < records.size(); i++) {
                    CSVRecord record = records.get(i);
                    data[i] = new String[record.size()];
                    for (int j = 0; j < record.size(); j++) {
                        data[i][j] = record.get(j);
                    }
                }
            } catch (IOException | UncheckedIOException e) {
                MiscUtils.getLogger().error("Error parsing CSV", e);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }

            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"oscarReport.xls\"");

            // HSSFWorkbook is Closeable; try-with-resources ensures the in-memory workbook
            // is released even if wb.write() throws.
            try (HSSFWorkbook wb = new HSSFWorkbook()) {
                HSSFSheet sheet = wb.createSheet("OSCAR_Report");
                for (int x = 0; x < data.length; x++) {
                    HSSFRow row = sheet.createRow((short) x);
                    for (int y = 0; y < data[x].length; y++) {
                        try {
                            double d = Double.parseDouble(data[x][y]);
                            row.createCell((short) y).setCellValue(d);
                        } catch (Exception e) {
                            row.createCell((short) y).setCellValue(data[x][y]);
                        }
                    }
                }
                wb.write(response.getOutputStream());
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error writing XLS", e);
                setServerErrorIfPossible();
            }
            return NONE;
        }
        return SUCCESS;
    }

    /**
     * Guards both the CSV and XLS export paths against null or oversized payloads.
     *
     * <p>Size is measured in UTF-8 bytes rather than Java char count because the
     * server-side limit ({@link SQLReporter#MAX_CSV_EXPORT_LENGTH}) was set in bytes
     * and multi-byte characters (e.g. accented letters, special symbols) would otherwise
     * slip through a char-count check.
     *
     * @param csv the raw CSV string from the POST parameter; may be {@code null}
     * @return {@code true} when the payload is safe to process
     */
    private boolean validateCsv(String csv) {
        if (csv == null) {
            MiscUtils.getLogger().warn("GenerateOutFiles2Action: missing CSV export payload");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        if (exceedsUtf8ByteLimit(csv, SQLReporter.MAX_CSV_EXPORT_LENGTH)) {
            MiscUtils.getLogger().warn("GenerateOutFiles2Action: CSV export payload exceeds size limit");
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return false;
        }
        return true;
    }

    private void setServerErrorIfPossible() {
        if (!response.isCommitted()) {
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private boolean exceedsUtf8ByteLimit(String value, int maxBytes) {
        // Count UTF-8 bytes without materializing a second multi-megabyte byte array.
        int bytes = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            bytes += utf8ByteLength(codePoint);
            if (bytes > maxBytes) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private int utf8ByteLength(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

}
