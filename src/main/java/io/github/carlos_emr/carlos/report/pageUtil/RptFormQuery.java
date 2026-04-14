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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.data.RptReportCreator;
import io.github.carlos_emr.carlos.report.data.RptReportFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author yilee18
 */
public class RptFormQuery {

    static String CHECK_BOX = "filter_";
    static String VARNAME_FORMAT = "startDate\\d|endDate\\d";

    /**
     * Pattern for valid SQL table references. Allows comma-separated table names
     * with optional whitespace around commas, optional schema qualification, and
     * optional aliases (with or without AS), for example:
     * "formBCAR",
     * "schema.formBCAR",
     * "formBCAR f",
     * "schema.formBCAR AS f",
     * "formBCAR f, formBCNewBorn n".
     */
    private static final String SQL_IDENTIFIER_PATTERN = "[a-zA-Z_][a-zA-Z0-9_]*";
    private static final String SQL_TABLE_REFERENCE_PATTERN =
            SQL_IDENTIFIER_PATTERN
                    + "(\\." + SQL_IDENTIFIER_PATTERN + ")?"
                    + "(\\s+(?:(?i:as)\\s+)?" + SQL_IDENTIFIER_PATTERN + ")?";
    private static final Pattern VALID_TABLE_NAME_PATTERN = Pattern.compile(
            "^" + SQL_TABLE_REFERENCE_PATTERN + "(\\s*,\\s*" + SQL_TABLE_REFERENCE_PATTERN + ")*$");

    /**
     * Validates that a table name (or comma-separated list of table references)
     * contains only supported safe SQL identifier forms. Prevents SQL injection
     * via table name manipulation while allowing schema-qualified names and aliases.
     *
     * @param tableName the table name string to validate
     * @throws SecurityException if the table name contains invalid characters
     */
    private static void validateTableName(String tableName) {
        if (tableName == null || !VALID_TABLE_NAME_PATTERN.matcher(tableName.trim()).matches()) {
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
        // Templates loaded from the database (not HTTP params) to prevent SQL injection
        Vector[] valueParams = getValueParam(reportId, request);
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

    /**
     * Loads the WHERE clause templates and date formats for checked report
     * filters. Templates are read from the {@code reportFilter} database table
     * (trusted source) rather than from HTTP request parameters, preventing
     * user-submitted {@code value_X} parameters from injecting arbitrary SQL.
     *
     * <p>Only the {@code filter_X} checkbox parameters from the request are
     * used to determine which filters the user selected.
     *
     * @param reportId the report identifier
     * @param request  the HTTP request (used only for checkbox state)
     * @return a two-element array: {@code [0]} = WHERE templates,
     *         {@code [1]} = date format strings
     * @throws SQLException if the database lookup fails
     */
    private Vector[] getValueParam(String reportId, HttpServletRequest request) throws SQLException {
        Vector[] ret = new Vector[2];
        Vector vecValue = new Vector();
        Vector vecDateFormat = new Vector();

        // Load filter definitions from the database (trusted source) instead
        // of from user-controllable HTTP parameters — prevents SQL injection
        // via tampered value_X hidden fields.
        RptReportFilter reportFilter = new RptReportFilter();
        Vector filterList = reportFilter.getNameList(reportId, 1); // status=1 (active)

        for (int i = 0; i < filterList.size(); i++) {
            String[] filter = (String[]) filterList.get(i);
            String orderNo = filter[3]; // order_no, used as filter_X suffix

            // Only include filters that the user checked in the form
            if (request.getParameter(CHECK_BOX + orderNo) != null) {
                vecValue.add(filter[1]);     // WHERE clause template from DB
                vecDateFormat.add(filter[5]); // date_format from DB
            }
        }

        ret[0] = vecValue;
        ret[1] = vecDateFormat;
        return ret;
    }

    /**
     * Builds parameterized WHERE clause fragments from the report filter
     * templates. Each {@code ${var}} placeholder in the template is replaced
     * with a {@code ?} bind marker, and the corresponding request parameter
     * value is collected for later binding.
     *
     * @param vecValue      WHERE clause templates from the database
     * @param vecDateFormat date format strings for date conversion
     * @param request       the HTTP request (used only for variable values)
     * @return list of parameterized WHERE clause fragments
     * @throws Exception if date conversion fails
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

}
