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
import java.util.Properties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return SUCCESS;
        }

        Properties properties = CarlosProperties.getInstance();
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

        RptByExampleData.QueryResult result;
        try {
            result = new RptByExampleData().execute(sql, properties, providerNo);
        } catch (QueryByExampleValidationException e) {
            request.setAttribute("queryValidationError", true);
            return SUCCESS;
        } catch (SQLTimeoutException e) {
            request.setAttribute("queryTimeout", true);
            return SUCCESS;
        } catch (SQLException | RuntimeException e) {
            request.setAttribute("queryExecutionError", true);
            return SUCCESS;
        }

        request.setAttribute("results", result.html());
        request.setAttribute("resultRowCount", result.rowCount());
        request.setAttribute("resultLimit", RptByExampleData.MAX_ROWS);
        try {
            write2Database(sql, providerNo);
        } catch (RuntimeException e) {
            request.setAttribute("queryHistoryError", true);
            RptByExampleData.audit(providerNo, sql, 0, result.rowCount(), "history_failed");
        }

        return SUCCESS;
    }

    static boolean isEnabled(Properties properties) {
        String configured = properties.getProperty(ENABLED_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return true;
        }
        String normalized = configured.trim();
        return normalized.equalsIgnoreCase("true")
                || normalized.equalsIgnoreCase("yes")
                || normalized.equalsIgnoreCase("on");
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
    private String selectedRecentSearch;

    public String getSql() {
        return sql;
    }

    @StrutsParameter
    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getSelectedRecentSearch() {
        return selectedRecentSearch;
    }

    @StrutsParameter
    public void setSelectedRecentSearch(String selectedRecentSearch) {
        this.selectedRecentSearch = selectedRecentSearch;
    }
}
