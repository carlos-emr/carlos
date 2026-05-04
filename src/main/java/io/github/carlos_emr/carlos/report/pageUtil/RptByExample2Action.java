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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.report.data.RptByExampleData;
import io.github.carlos_emr.carlos.services.security.SecurityManager;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.commn.dao.ReportByExamplesDao;
import io.github.carlos_emr.carlos.commn.model.ReportByExamples;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.report.bean.RptByExampleQueryBeanHandler;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action for the Query-by-Example report tool. Allows admin users to execute
 * custom SQL queries, persist them as recent searches, and display results.
 *
 * @since 2003-07-22
 */
public class RptByExample2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ReportByExamplesDao dao = SpringUtils.getBean(ReportByExamplesDao.class);


    public String execute()
            throws ServletException, IOException {
        if (request.getSession().getAttribute("user") == null) {
            response.sendRedirect(request.getContextPath() + "/logout.htm");
            return NONE;
        }

        Object userrole = request.getSession().getAttribute("userrole");
        if (userrole == null) {
            response.sendRedirect(request.getContextPath() + "/logout.htm");
            return NONE;
        }

        String roleName$ = userrole + "," + (String) request.getSession().getAttribute("user");
        if (!SecurityManager.hasPrivilege("_admin", roleName$) && !SecurityManager.hasPrivilege("_report", roleName$)) {
            throw new SecurityException("Insufficient Privileges");
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        SecUserRoleDao secUserRoleDao = SpringUtils.getBean(SecUserRoleDao.class);

        List<SecUserRole> userRoles = secUserRoleDao.findByRoleNameAndProviderNo("admin", providerNo);
        if (userRoles.isEmpty()) {
            throw new SecurityException("missing required admin privileges to run query by example");
        }

        RptByExampleQueryBeanHandler hd = new RptByExampleQueryBeanHandler();
        Collection favorites = hd.getFavoriteCollection(providerNo);
        request.setAttribute("favorites", favorites);

        if (sql != null) {
            write2Database(sql, providerNo);
        } else
            sql = "";

        RptByExampleData exampleData = new RptByExampleData();
        Properties proppies = CarlosProperties.getInstance();

        String results = exampleData.exampleReportGenerate(sql, proppies) == null ? null : exampleData.exampleReportGenerate(sql, proppies);
        String resultText = exampleData.exampleTextGenerate(sql, proppies) == null ? null : exampleData.exampleTextGenerate(sql, proppies);

        request.setAttribute("results", results);
        request.setAttribute("resultText", resultText);

        return SUCCESS;
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
