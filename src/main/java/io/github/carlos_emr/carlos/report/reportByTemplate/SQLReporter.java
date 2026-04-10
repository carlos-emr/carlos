/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.db.DBHandler;
import io.github.carlos_emr.carlos.report.data.RptResultStruct;
import io.github.carlos_emr.carlos.util.UtilMisc;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;


/**
 * @author rjonasz
 */
public class SQLReporter implements Reporter {

    /**
     * Maximum number of characters ({@code String.length()}) of CSV data that may be
     * stored in the HTTP session. Prevents memory exhaustion when large report results
     * are generated. Value: 5,242,880 characters (5&nbsp;×&nbsp;1024&nbsp;×&nbsp;1024).
     */
    static final int MAX_CSV_SESSION_LENGTH = 5 * 1024 * 1024;

    /** Value of {@link ReportTemplates#getActive()} that indicates an active template. */
    private static final int ACTIVE_STATUS = 1;

    public SQLReporter() {
    }

    /**
     * Validates that the given templateId refers to an active report template.
     * Returns the {@link ReportTemplates} entity when valid, or {@code null} when the
     * id is missing, non-numeric, zero, or does not correspond to an active template.
     *
     * @param templateId the raw string value of the {@code templateId} HTTP parameter
     * @return the active {@link ReportTemplates} record, or {@code null} if invalid
     */
    private ReportTemplates resolveActiveTemplate(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return null;
        }
        int id = ConversionUtils.fromIntString(templateId);
        if (id <= 0) {
            return null;
        }
        ReportTemplatesDao reportTemplatesDao = SpringUtils.getBean(ReportTemplatesDao.class);
        ReportTemplates rt = reportTemplatesDao.find(id);
        if (rt == null || rt.getActive() != ACTIVE_STATUS) {
            return null;
        }
        return rt;
    }

    public boolean generateReport(HttpServletRequest request) {
        String templateId = request.getParameter("templateId");

        // Validate templateId against the database before executing any query (CWE-501)
        if (resolveActiveTemplate(templateId) == null) {
            MiscUtils.getLogger().warn("generateReport: invalid or inactive templateId '{}'", Encode.forJava(templateId));
            request.setAttribute("errormsg", "Error: Invalid or inactive report template.");
            return false;
        }

        ReportObject curReport = (new ReportManager()).getReportTemplateNoParam(templateId);
        Map parameterMap = request.getParameterMap();

        if (curReport.isSequence()) {
            return generateSequencedReport(request);
        }

        String sql;
        Object[] sqlParams;

        if (curReport instanceof ReportObjectGeneric) {
            String[] parameterizedResult = ((ReportObjectGeneric) curReport).getParameterizedSQL(parameterMap);
            if (parameterizedResult == null || parameterizedResult[0] == null || parameterizedResult[0].trim().isEmpty()) {
                request.setAttribute("errormsg", "Error: Cannot find all parameters for the query.  Check the template.");
                request.setAttribute("templateid", templateId);
                return false;
            }
            sql = parameterizedResult[0];
            sqlParams = extractParams(parameterizedResult);
        } else {
            MiscUtils.getLogger().error("Report template {} uses unsupported legacy non-parameterized report type. Refusing to execute.", templateId);
            request.setAttribute("errormsg", "Error: This report template uses a legacy format that is no longer supported. Please contact your administrator to update the template.");
            request.setAttribute("templateid", templateId);
            return false;
        }

        String[] result = executeQuery(sql, sqlParams, false);

        String csv = result[1];
        if (csv.length() > MAX_CSV_SESSION_LENGTH) {
            MiscUtils.getLogger().warn("generateReport: CSV result for template '{}' exceeds session size limit ({} chars); not storing in session", Encode.forJava(templateId), csv.length());
            request.setAttribute("errormsg", "Warning: Report result is too large to download as CSV. Please narrow your search criteria.");
            csv = "";
        }

        request.getSession().setAttribute("csv", csv); // nosemgrep: tainted-session-from-http-request -- csv is generated from executeQuery() results for a validated report template, not copied directly from request parameters
        request.setAttribute("csv", csv);
        request.setAttribute("sql", sql);
        request.setAttribute("reportobject", curReport);
        request.setAttribute("resultsethtml", result[0]);

        return true;
    }

    public boolean generateSequencedReport(HttpServletRequest request) {
        String templateId = request.getParameter("templateId");

        // Validate templateId against the database before executing any query (CWE-501)
        if (resolveActiveTemplate(templateId) == null) {
            MiscUtils.getLogger().warn("generateSequencedReport: invalid or inactive templateId '{}'", Encode.forJava(templateId));
            request.setAttribute("errormsg", "Error: Invalid or inactive report template.");
            return false;
        }

        ReportObject curReport = (new ReportManager()).getReportTemplateNoParam(templateId);
        Map parameterMap = request.getParameterMap();

        int x = 0;
        if (curReport instanceof ReportObjectGeneric) {
            ReportObjectGeneric genericReport = (ReportObjectGeneric) curReport;
            String[] parameterizedResult;
            while ((parameterizedResult = genericReport.getParameterizedSQL(x, parameterMap)) != null) {
                String sql = parameterizedResult[0];
                if (sql.isEmpty()) {
                    request.setAttribute("errormsg", "Error: Cannot find all parameters for the query.  Check the template.");
                    request.setAttribute("templateid", templateId);
                    return false;
                }
                Object[] sqlParams = extractParams(parameterizedResult);
                String[] result = executeQuery(sql, sqlParams, true);
                request.setAttribute("csv-" + x, result[1]);
                request.setAttribute("sql-" + x, sql);
                request.setAttribute("resultsethtml-" + x, result[0]);
                x++;
            }
        } else {
            MiscUtils.getLogger().error("Report template {} uses unsupported legacy non-parameterized report type (sequenced). Refusing to execute.", templateId);
            request.setAttribute("errormsg", "Error: This report template uses a legacy format that is no longer supported. Please contact your administrator to update the template.");
            request.setAttribute("templateid", templateId);
            return false;
        }

        request.setAttribute("sequenceLength", x);
        request.setAttribute("reportobject", curReport);

        return true;
    }

    /**
     * Executes a SQL query and returns the HTML and CSV representations.
     *
     * @param sql         the SQL query to execute
     * @param sqlParams   JDBC parameters for the parameterized query
     * @param showSqlOnEmpty if true, prefix the "no results" message with the SQL text
     * @return {@code String[2]} where {@code [0]} is the HTML result and {@code [1]} is the CSV
     */
    private String[] executeQuery(String sql, Object[] sqlParams, boolean showSqlOnEmpty) {
        String rsHtml = "An SQL query error has occured ";
        String csv = "";
        try (StringWriter swr = new StringWriter();
             ResultSet rs = DBHandler.GetPreSQL(sql, sqlParams)) {
            if (!rs.isBeforeFirst()) {
                rsHtml = showSqlOnEmpty
                        ? (Encode.forHtml(sql) + "<br/>The query returned no results.")
                        : "The query returned no results.";
            } else {
                rsHtml = RptResultStruct.getStructure2(rs);
                CSVPrinter csvp = new CSVPrinter(swr, CSVFormat.DEFAULT);
                String[][] data = UtilMisc.getArrayFromResultSet(rs);
                for (String[] row : data) {
                    csvp.printRecord((Object[]) row);
                }
                csvp.flush();
                csv = swr.toString();
            }
        } catch (SQLException sqe) {
            String detail = sqe.getCause() != null ? sqe.getCause().toString() : sqe.getMessage();
            rsHtml += Encode.forHtml(detail);
            MiscUtils.getLogger().error("Error", sqe);
        } catch (IOException e) {
            String detail = e.getCause() != null ? e.getCause().toString() : e.getMessage();
            rsHtml = "Error: during creation of CSV : " + Encode.forHtml(detail);
            MiscUtils.getLogger().error("Error", e);
        }
        return new String[]{rsHtml, csv};
    }

    /** Extracts parameter values from a parameterized SQL result array (index 1..n). */
    private Object[] extractParams(String[] parameterizedResult) {
        Object[] sqlParams = new Object[parameterizedResult.length - 1];
        System.arraycopy(parameterizedResult, 1, sqlParams, 0, sqlParams.length);
        return sqlParams;
    }

}
