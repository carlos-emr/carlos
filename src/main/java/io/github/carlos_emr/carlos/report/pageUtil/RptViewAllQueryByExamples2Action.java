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
import java.util.Calendar;
import java.util.GregorianCalendar;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import io.github.carlos_emr.carlos.report.bean.RptByExampleQueryBeanHandler;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.util.DateUtils;

/**
 * Struts 2 action that loads all Report By Example queries within a date range
 * for viewing. Requires {@code _report} read privilege.
 *
 * @since 2001-01-01
 */
public class RptViewAllQueryByExamples2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    public String execute()
            throws ServletException, IOException {
        RptByExampleQueryBeanHandler hd = new RptByExampleQueryBeanHandler(startDate, endDate);
        request.setAttribute("allQueries", hd);

        return SUCCESS;
    }

    GregorianCalendar now = new GregorianCalendar();

    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);

    String sql;
    String selectedRecentSearch;
    DateUtils dateUtils = new DateUtils();
    String startDate = dateUtils.NextDay(curDay, curMonth, curYear - 1);
    String endDate = dateUtils.NextDay(curDay, curMonth, curYear);

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

    public String getStartDate() {
        return startDate;
    }

    @StrutsParameter
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    @StrutsParameter
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}
