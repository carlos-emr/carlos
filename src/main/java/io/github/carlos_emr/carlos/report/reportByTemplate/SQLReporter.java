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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.owasp.encoder.Encode;

import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * @author rjonasz
 */
public class SQLReporter implements Reporter {

    /**
     * Maximum number of UTF-8 bytes allowed for CSV export payloads transmitted via
     * POST parameter. Prevents memory exhaustion and excessive request sizes.
     */
    public static final int MAX_CSV_EXPORT_LENGTH = 5 * 1024 * 1024;

    static final String CSV_EXPORT_LIMIT_MESSAGE =
            "Warning: Report result is too large to download as CSV. Please narrow your search criteria.";

    static final String REPORT_LIMIT_MESSAGE =
            "Warning: Report result exceeds the row, size or time limit. Please narrow your search criteria.";

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
            MiscUtils.getLogger().warn("generateReport: invalid or inactive templateId '{}'", LogSafe.sanitize(templateId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            request.setAttribute("errormsg", "Error: Invalid or inactive report template.");
            return false;
        }

        ReportObject curReport = (new ReportManager()).getReportTemplateNoParam(templateId);
        Map<String, String[]> parameterMap = request.getParameterMap();

        if (curReport.isSequence()) {
            return generateSequencedReport(request);
        }

        ParameterizedSql query;

        if (curReport instanceof ReportObjectGeneric) {
            query = ((ReportObjectGeneric) curReport).buildParameterizedSql(parameterMap);
            if (query == null || query.getSql().trim().isEmpty()) {
                request.setAttribute("errormsg", "Error: Cannot find all parameters for the query.  Check the template.");
                request.setAttribute("templateid", templateId);
                return false;
            }
        } else {
            MiscUtils.getLogger().error("Report template {} uses unsupported legacy non-parameterized report type. Refusing to execute.", LogSafe.sanitize(templateId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            request.setAttribute("errormsg", "Error: This report template uses a legacy format that is no longer supported. Please contact your administrator to update the template.");
            request.setAttribute("templateid", templateId);
            return false;
        }

        String[] result = executeQuery(query, false, new ReportResultBudget());
        if (result[1] == null) {
            request.setAttribute("errormsg", result[0]);
            request.setAttribute("templateid", templateId);
            request.setAttribute("csv", "");
            return false;
        }
        String csv = result[1];

        request.setAttribute("csv", csv);
        request.setAttribute("sql", query.getSql());
        request.setAttribute("reportobject", curReport);
        request.setAttribute("resultsethtml", result[0]);

        return true;
    }

    public boolean generateSequencedReport(HttpServletRequest request) {
        String templateId = request.getParameter("templateId");

        // Validate templateId against the database before executing any query (CWE-501)
        if (resolveActiveTemplate(templateId) == null) {
            MiscUtils.getLogger().warn("generateSequencedReport: invalid or inactive templateId '{}'", LogSafe.sanitize(templateId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            request.setAttribute("errormsg", "Error: Invalid or inactive report template.");
            return false;
        }

        ReportObject curReport = (new ReportManager()).getReportTemplateNoParam(templateId);
        Map<String, String[]> parameterMap = request.getParameterMap();

        int x = 0;
        ReportResultBudget budget = new ReportResultBudget();
        if (curReport instanceof ReportObjectGeneric) {
            ReportObjectGeneric genericReport = (ReportObjectGeneric) curReport;
            ParameterizedSql query;
            while ((query = genericReport.buildParameterizedSql(x, parameterMap)) != null) {
                if (query.getSql().isEmpty()) {
                    clearSequenceOutput(request, x);
                    request.setAttribute("errormsg", "Error: Cannot find all parameters for the query.  Check the template.");
                    request.setAttribute("templateid", templateId);
                    return false;
                }
                String[] result = executeQuery(query, true, budget);
                if (result[1] == null) {
                    // Do not retain earlier successful subreports after the sequence fails.
                    clearSequenceOutput(request, x);
                    request.setAttribute("csv-" + x, "");
                    request.setAttribute("errormsg", result[0]);
                    request.setAttribute("templateid", templateId);
                    return false;
                }
                String csv = result[1];
                request.setAttribute("csv-" + x, csv);
                request.setAttribute("sql-" + x, query.getSql());
                request.setAttribute("resultsethtml-" + x, result[0]);
                x++;
            }
        } else {
            MiscUtils.getLogger().error("Report template {} uses unsupported legacy non-parameterized report type (sequenced). Refusing to execute.", LogSafe.sanitize(templateId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            request.setAttribute("errormsg", "Error: This report template uses a legacy format that is no longer supported. Please contact your administrator to update the template.");
            request.setAttribute("templateid", templateId);
            return false;
        }

        request.setAttribute("sequenceLength", x);
        request.setAttribute("reportobject", curReport);

        return true;
    }

    private void clearSequenceOutput(HttpServletRequest request, int count) {
        for (int previous = 0; previous < count; previous++) {
            request.removeAttribute("csv-" + previous);
            request.removeAttribute("sql-" + previous);
            request.removeAttribute("resultsethtml-" + previous);
        }
    }

    /** Null CSV denotes budget failure or a failed sequence; ordinary single-query errors retain result navigation. */
    private String[] executeQuery(ParameterizedSql query, boolean sequenced, ReportResultBudget budget) {
        try (ResultSet rs = openReportQuery(query, budget)) {
            return renderReport(rs, sequenced ? query.getSql() : null, budget);
        } catch (ReportResultBudget.LimitExceededException e) {
            return new String[]{e.getMessage(), null};
        } catch (SQLTimeoutException _) {
            return new String[]{REPORT_LIMIT_MESSAGE, null};
        } catch (SQLException | IOException e) {
            MiscUtils.getLogger().error("Error generating template report", e);
            return new String[]{"An SQL query error has occured " + Encode.forHtml(e.getMessage()), sequenced ? null : ""};
        }
    }

    private ResultSet openReportQuery(ParameterizedSql query, ReportResultBudget budget) throws IOException, SQLException {
        budget.startQuery();
        return LegacyJdbcQuery.getBoundedReportResultSet(query, budget.remainingRows() + 1, budget.deadlineNanos());
    }

    private String[] renderReport(ResultSet rs, String sqlOnEmpty, ReportResultBudget budget) throws IOException, SQLException {
        try {
            return budget.render(rs, sqlOnEmpty);
        } catch (IOException | SQLException e) {
            cancelReport(rs, e);
            throw e;
        }
    }

    private void cancelReport(ResultSet rs, Exception failure) {
        // Cancel before closing: a streaming cursor may still have unread rows.
        try {
            var statement = rs.getStatement();
            if (statement != null) {
                statement.cancel();
            }
        } catch (SQLException cancelFailure) {
            failure.addSuppressed(cancelFailure);
        }
    }
}
