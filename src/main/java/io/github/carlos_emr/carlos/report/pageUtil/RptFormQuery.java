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


/*
 * Created on 2005-8-7
 */
package io.github.carlos_emr.carlos.report.pageUtil;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.data.RptReportCreator;
import io.github.carlos_emr.carlos.util.SqlIdentifierValidator;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author yilee18
 */
public class RptFormQuery {

    static String CHECK_BOX = "filter_";
    static String VALUE = "value_";
    static String DATE_FORMAT = "dateFormat_";
    static String VARNAME_FORMAT = "startDate\\d|endDate\\d";

    /**
     * Validates that a report table name contains only supported safe SQL
     * identifier forms. This report builder later qualifies columns with the
     * table value, so aliases and comma-separated table references are rejected
     * even though other legacy validators may accept them.
     *
     * @param tableName the table name string to validate
     * @throws SecurityException if the table name contains invalid characters
     */
    static void validateTableName(String tableName) {
        if (!SqlIdentifierValidator.isValidIdentifier(tableName)) {
            MiscUtils.getLogger().error("Invalid table name detected in report configuration");
            throw new SecurityException("Invalid table name in report configuration");
        }
    }

    public ParameterizedSql getQueryStr(String reportId, HttpServletRequest request) throws Exception {
        RptReportCreator reportCreator = new RptReportCreator();

        // sql:select
        String reportSql = "select " + reportCreator.getSelectField(reportId);

        // sql:from
        reportSql += " from ";
        String tableName = reportCreator.getFromTableFirst(reportId);
        validateTableName(tableName);
        boolean bDemo = tableName.indexOf("demographic") >= 0 ? true : false;
        reportSql += tableName;

        // get value param string — single call ensures vecValue and vecDateFormat stay index-aligned
        Vector[] valueParams = getValueParam(request);
        Vector vecValue = valueParams[0];
        Vector vecDateFormat = valueParams[1];
        List<ParameterizedSql> vecVarValue = getQueryValueParameterized(vecValue, vecDateFormat, request);

        for (int i = 0; i < vecVarValue.size(); i++) {
            String tempVal = vecVarValue.get(i).getSql();
            bDemo = RptReportCreator.isIncludeDemo(tempVal) ? true : bDemo;
        }

        // Combine WHERE fragments into a single parameterized WHERE clause
        ParameterizedSql whereClause = getQueryWhereParameterized(vecVarValue);

        // sql:subquery
        String subQuery = "select max(ID) from " + tableName;
        // add tablename demographic
        if (tableName.indexOf(",demographic") < 0 && bDemo) {
            subQuery += ",demographic ";
        }

        // Collect all parameters for the sub-query
        List<Object> subQueryParams = new ArrayList<>();

        // Security note (CodeQL java/Sqli #1240 false positive):
        // combinedWhere joins whereClause (ParameterizedSql with '?' placeholders and
        // bound params) with joinClause (table join conditions built from the
        // deterministic-validator-checked tableName). All user-supplied values are in subQueryParams
        // as bind parameters — no user input is concatenated into the SQL string.
        // Build WHERE clause safely by joining non-empty predicates
        String joinClause = reportCreator.getWhereJoinClause(tableName, bDemo);
        String combinedWhere = RptReportCreator.joinPredicates(whereClause.getSql(), joinClause);
        if (!combinedWhere.isEmpty()) {
            subQuery += " where " + combinedWhere;
            subQueryParams.addAll(whereClause.getParams());
        }
        subQuery += " group by " + tableName + ".demographic_no," + tableName + ".formCreated ";

        // sql:from - add tablename demographic
        if (tableName.indexOf(",demographic") < 0 && bDemo) {
            reportSql += ",demographic ";
        }

        // get subQuery result — sub-query params are now bound
        String rltSubQuery = reportCreator.getRltSubQuery(subQuery, subQueryParams.toArray());

        reportSql += " where " + tableName + ".ID in (" + rltSubQuery + ")";
        if (!joinClause.isEmpty()) {
            reportSql += " and " + joinClause;
        }

        // The final reportSql contains no '?' placeholders: the sub-query was
        // executed separately and its integer results inlined, and
        // getWhereJoinClause returns a static join fragment with no bind values.
        // The ParameterizedSql constructor enforces this invariant.
        return new ParameterizedSql(reportSql, new ArrayList<>());
    }

    private Vector[] getValueParam(HttpServletRequest request) {
        Vector[] ret = new Vector[2];
        String serialNo = "";
        Vector vecValue = new Vector();
        Vector vecDateFormat = new Vector();

        Enumeration varEnum = request.getParameterNames();
        while (varEnum.hasMoreElements()) {
            String name = (String) varEnum.nextElement();
            if (name.startsWith(VALUE)) {
                serialNo = name.substring(VALUE.length());
                if (request.getParameter(CHECK_BOX + serialNo) == null)
                    continue;

                vecValue.add(request.getParameter(name));
                vecDateFormat.add(request.getParameter(DATE_FORMAT + serialNo));
            }
        }
        ret[0] = vecValue;
        ret[1] = vecDateFormat;
        return ret;
    }

    // filling the var with the real date value
    private Vector getQueryValue(Vector vecValue, Vector vecDateFormat, HttpServletRequest request) throws Exception {
        Vector ret = new Vector();
        for (int i = 0; i < vecValue.size(); i++) {
            String tempVal = (String) vecValue.get(i);
            Vector vecVar = RptReportCreator.getVarVec(tempVal);
            Vector vecVarValue = new Vector();
            for (int j = 0; j < vecVar.size(); j++) {
                // conver date format if needed
                if (((String) vecVar.get(j)).matches(VARNAME_FORMAT) && ((String) vecDateFormat.get(i)).length() > 1) {
                    vecVarValue.add(RptReportCreator.getDiffDateFormat(request.getParameter((String) vecVar.get(j)),
                            (String) vecDateFormat.get(i), "yyyy-MM-dd"));
                } else {
                    vecVarValue.add(request.getParameter((String) vecVar.get(j)));
                }
            }
            ret.add(RptReportCreator.getWhereValueClause(tempVal, vecVarValue));
        }
        return ret;
    }

    /**
     * Parameterized version of {@link #getQueryValue}. Returns a list of
     * {@link ParameterizedSql} fragments, each containing a WHERE clause
     * template with {@code ?} placeholders and the corresponding bind values.
     */
    private List<ParameterizedSql> getQueryValueParameterized(Vector vecValue, Vector vecDateFormat, HttpServletRequest request) throws Exception {
        List<ParameterizedSql> ret = new ArrayList<>();
        for (int i = 0; i < vecValue.size(); i++) {
            String tempVal = (String) vecValue.get(i);
            Vector vecVar = RptReportCreator.getVarVec(tempVal);
            Vector vecVarValue = new Vector();
            for (int j = 0; j < vecVar.size(); j++) {
                if (((String) vecVar.get(j)).matches(VARNAME_FORMAT) && ((String) vecDateFormat.get(i)).length() > 1) {
                    vecVarValue.add(RptReportCreator.getDiffDateFormat(request.getParameter((String) vecVar.get(j)),
                            (String) vecDateFormat.get(i), "yyyy-MM-dd"));
                } else {
                    vecVarValue.add(request.getParameter((String) vecVar.get(j)));
                }
            }
            ret.add(RptReportCreator.getWhereValueClauseParameterized(tempVal, vecVarValue));
        }
        return ret;
    }

    /**
     * Combines a list of {@link ParameterizedSql} WHERE clause fragments into
     * a single parameterized WHERE clause joined by {@code AND}.
     * Blank/empty fragments are skipped to avoid stray conjunctions.
     */
    static ParameterizedSql getQueryWhereParameterized(List<ParameterizedSql> fragments) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (ParameterizedSql frag : fragments) {
            if (frag.getSql() == null || frag.getSql().isBlank()) {
                continue;
            }
            if (sql.length() > 0) {
                sql.append(" and ");
            }
            sql.append(frag.getSql());
            params.addAll(frag.getParams());
        }
        return new ParameterizedSql(sql.toString(), params);
    }

    public String getQueryWhere(Vector vecVarValue) {
        String ret = "";
        if (vecVarValue.size() > 0) {
            ret = (String) vecVarValue.get(0);
        }
        for (int i = 1; i < vecVarValue.size(); i++) {
            ret += " and " + (String) vecVarValue.get(i);
        }
        return ret;
    }

}
