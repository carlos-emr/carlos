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
import java.util.function.LongSupplier;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
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

    /** Value of {@link ReportTemplates#getActive()} that indicates an active template. */
    private static final int ACTIVE_STATUS = 1;

    private final ReportResultBudget.Limits limits;
    private final LongSupplier nanoTime;

    /** Runs reports under the limits set in carlos.properties (#5849); see {@link ReportResultBudget.Limits}. */
    public SQLReporter() {
        this(ReportResultBudget.Limits.from(key -> CarlosProperties.getInstance().getProperty(key)));
    }

    SQLReporter(ReportResultBudget.Limits limits) {
        this(limits, System::nanoTime);
    }

    /** Tests pass a clock to check the time limit without waiting for it. */
    SQLReporter(ReportResultBudget.Limits limits, LongSupplier nanoTime) {
        this.limits = limits;
        this.nanoTime = nanoTime;
    }

    /**
     * One query's outcome. A null {@code csv} means a limit refusal or a failed sequence; {@code html} then
     * holds the English message, and {@code refusal} (for a limit) names the translated one.
     */
    private record QueryResult(String html, String csv, boolean csvWithheld,
                               ReportResultBudget.LimitExceededException refusal) {
    }

    /**
     * Puts a refusal on the configuration page: the English text as {@code errormsg} (logs, older pages) and,
     * for a limit, the bundle key and value that the page shows in the user's language (D262).
     */
    private static void showRefusal(HttpServletRequest request, QueryResult result) {
        request.setAttribute("errormsg", result.html());
        if (result.refusal() != null) {
            request.setAttribute("errormsgKey", result.refusal().limit().key());
            request.setAttribute("errormsgArg", result.refusal().value());
        }
    }

    /**
     * The report is shown, but a CSV over its limit is held back from the CSV and XLSX downloads (D260). The flag
     * is {@code csvWithheld}, or {@code csvWithheld-N} for a sequence's query N (counting from 0), so the page puts
     * the note on that query.
     */
    private void markCsvWithheld(HttpServletRequest request, String flag) {
        request.setAttribute(flag, Boolean.TRUE);
        request.setAttribute("csvWithheldMb", limits.maxCsvBytes() / (1024 * 1024));
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

        QueryResult result = executeQuery(query, false, new ReportResultBudget(limits, nanoTime));
        if (result.csv() == null) {
            showRefusal(request, result);
            request.setAttribute("templateid", templateId);
            request.setAttribute("csv", "");
            return false;
        }

        request.setAttribute("csv", result.csv());
        if (result.csvWithheld()) {
            markCsvWithheld(request, "csvWithheld");
        }
        request.setAttribute("sql", query.getSql());
        request.setAttribute("reportobject", curReport);
        request.setAttribute("resultsethtml", result.html());

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
        ReportResultBudget budget = new ReportResultBudget(limits, nanoTime);
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
                QueryResult result = executeQuery(query, true, budget);
                if (result.csv() == null) {
                    // Do not retain earlier successful subreports after the sequence fails.
                    clearSequenceOutput(request, x);
                    request.setAttribute("csv-" + x, "");
                    showRefusal(request, result);
                    request.setAttribute("templateid", templateId);
                    return false;
                }
                request.setAttribute("csv-" + x, result.csv());
                if (result.csvWithheld()) {
                    markCsvWithheld(request, "csvWithheld-" + x);
                }
                request.setAttribute("sql-" + x, query.getSql());
                request.setAttribute("resultsethtml-" + x, result.html());
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
            request.removeAttribute("csvWithheld-" + previous);
            request.removeAttribute("sql-" + previous);
            request.removeAttribute("resultsethtml-" + previous);
        }
        request.removeAttribute("csvWithheldMb");
    }

    /** Null CSV denotes a limit refusal or a failed sequence; ordinary single-query errors retain result navigation. */
    private QueryResult executeQuery(ParameterizedSql query, boolean sequenced, ReportResultBudget budget) {
        try (ResultSet rs = openReportQuery(query, budget)) {
            ReportResultBudget.Rendered out = budget.render(rs, sequenced ? query.getSql() : null);
            return new QueryResult(out.html(), out.csv(), out.csvWithheld(), null);
        } catch (ReportResultBudget.LimitExceededException e) {
            return new QueryResult(e.getMessage(), null, false, e);
        } catch (SQLTimeoutException _) {
            // The JDBC deadline: before or after a connection is acquired, the driver's query timeout, or an abort
            // while streaming. The time limit's message names both a large report and a busy server.
            return timeLimit(budget);
        } catch (SQLException | IOException e) {
            MiscUtils.getLogger().error("Error generating template report", e);
            if (budget.pastDeadline()) {
                // Any failure once the time limit has run out, such as an exhausted connection pool that kept
                // the report waiting longer than its limit, is shown as the time limit too.
                return timeLimit(budget);
            }
            // Both destination JSPs encode error text. Keep it unencoded here so
            // diagnostic characters are displayed once, not as HTML entities.
            return new QueryResult("An SQL query error has occured " + e.getMessage(), sequenced ? null : "", false, null);
        }
    }

    private static QueryResult timeLimit(ReportResultBudget budget) {
        ReportResultBudget.LimitExceededException time = budget.timeLimit();
        return new QueryResult(time.getMessage(), null, false, time);
    }

    private ResultSet openReportQuery(ParameterizedSql query, ReportResultBudget budget) throws IOException, SQLException {
        budget.startQuery();
        return LegacyJdbcQuery.getBoundedReportResultSet(query, budget.remainingRows() + 1, budget.deadlineNanos());
    }

}
