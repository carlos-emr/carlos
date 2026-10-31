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


package io.github.carlos_emr.carlos.report.pageUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.report.data.QueryByExampleAccessDeniedException;
import io.github.carlos_emr.carlos.report.data.ReportingAccountUnavailableException;
import io.github.carlos_emr.carlos.report.data.ReportingConnectionService;
import io.github.carlos_emr.carlos.report.data.RptByExampleData;
import io.github.carlos_emr.carlos.report.data.QueryByExampleValidationException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.ReportByExamplesDao;
import io.github.carlos_emr.carlos.commn.model.ReportByExamples;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.report.bean.RptByExampleQueryBeanHandler;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action for the Query-by-Example report tool. Allows authorized report users
 * to execute custom read-only SQL queries, persist successful searches, and display results.
 *
 * <p>Queries run as the read-only reporting database account, never the application account
 * (issue #3372). When that account is not provisioned the page says so as soon as it opens, and a
 * submitted query is not run.</p>
 *
 * @since 2003-07-22
 */
public class RptByExample2Action extends ActionSupport {
    public static final String ENABLED_PROPERTY = "QUERY_BY_EXAMPLE_ENABLED";

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private ReportByExamplesDao dao = SpringUtils.getBean(ReportByExamplesDao.class);
    private final SecurityInfoManager securityInfoManager;

    public RptByExample2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    RptByExample2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String execute()
            throws ServletException, IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendRedirect(request.getContextPath() + "/logout.htm");
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();

        RptByExampleQueryBeanHandler hd = new RptByExampleQueryBeanHandler();
        Collection favorites = hd.getFavoriteCollection(providerNo);
        request.setAttribute("favorites", favorites);

        sql = sql == null ? "" : sql;
        request.setAttribute("submittedSql", sql);

        Properties properties = CarlosProperties.getInstance();
        if (!"POST".equals(request.getMethod())) {
            // Settings only, no database call: tell the user up front rather than after they type.
            if (isEnabled(properties) && !ReportingConnectionService.isConfigured(properties)) {
                request.setAttribute("queryReportingUnavailable", true);
            }
            return SUCCESS;
        }

        if (!isEnabled(properties)) {
            request.setAttribute("queryDisabled", true);
            RptByExampleData.audit(providerNo, sql, 0, 0, "disabled");
            return SUCCESS;
        }

        if (sql.isBlank()) {
            request.setAttribute("queryValidationError", true);
            RptByExampleData.audit(providerNo, sql, 0, 0, "rejected");
            return SUCCESS;
        }

        Optional<RptByExampleData.QueryResult> ran = runQuery(providerNo, properties);
        if (ran.isEmpty()) {
            return SUCCESS;
        }
        RptByExampleData.QueryResult result = ran.get();

        request.setAttribute("results", result.html());
        request.setAttribute("resultRowCount", result.rowCount());
        request.setAttribute("resultLimit", RptByExampleData.MAX_ROWS);
        request.setAttribute("resultTruncated", result.truncated());
        request.setAttribute("resultRowLimitReached", result.rowLimitReached());
        request.setAttribute("resultCharacterLimit", RptByExampleData.MAX_OUTPUT_CHARACTERS);
        try {
            write2Database(sql, providerNo);
        } catch (RuntimeException _) {
            request.setAttribute("queryHistoryError", true);
            RptByExampleData.audit(providerNo, sql, result.durationMillis(), result.rowCount(), "history_failed");
        }

        return SUCCESS;
    }

    /**
     * Runs the submitted query. When it produces no result, sets the request attribute that tells
     * the page why (rejected, no reporting account, refused by the database, timed out, failed)
     * and returns empty; each of those outcomes is already audited by {@link RptByExampleData}.
     */
    private Optional<RptByExampleData.QueryResult> runQuery(String providerNo, Properties properties) {
        try {
            return Optional.of(new RptByExampleData().execute(sql, properties, providerNo));
        } catch (QueryByExampleValidationException _) {
            request.setAttribute("queryValidationError", true);
        } catch (ReportingAccountUnavailableException _) {
            request.setAttribute("queryReportingUnavailable", true);
        } catch (QueryByExampleAccessDeniedException _) {
            request.setAttribute("queryAccessDenied", true);
        } catch (SQLTimeoutException _) {
            request.setAttribute("queryTimeout", true);
            request.setAttribute("queryTimeoutSeconds", RptByExampleData.QUERY_TIMEOUT_SECONDS);
        } catch (io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException _) {
            request.setAttribute("queryPreventionDateReview", true);
        } catch (SQLException | RuntimeException _) {
            request.setAttribute("queryExecutionError", true);
        }
        return Optional.empty();
    }

    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Locale.ROOT normalization is safe for controlled ASCII feature-flag values")
    static boolean isEnabled(Properties properties) {
        String configured = properties.getProperty(ENABLED_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return true;
        }
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    public void write2Database(String query, String providerNo) {
        if (query != null && query.compareTo("") != 0) {
            ReportByExamples r = new ReportByExamples();
            r.setProviderNo(providerNo);
            r.setQuery(query);
            r.setDate(new Date());
            dao.persist(r);


        }
    }


    private String sql;

    public String getSql() {
        return sql;
    }

    @StrutsParameter
    public void setSql(String sql) {
        this.sql = sql;
    }
}
