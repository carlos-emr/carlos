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
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.data.RptReportCreator;
import io.github.carlos_emr.carlos.report.data.RptReportFilter;
import io.github.carlos_emr.carlos.util.SqlIdentifierValidator;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author yilee18
 */
public class RptFormQuery {

    /**
     * The filter checkbox the report filter page renders as {@code filter_<order no>}. Only the
     * tick is read from the request: the predicate and its date format come from the stored
     * report filter (#5831). The page's {@code value_<n>} and {@code dateFormat_<n>} hidden
     * fields are ignored.
     */
    static String CHECK_BOX = "filter_";
    static String VARNAME_FORMAT = "startDate\\d|endDate\\d";

    /**
     * Validates that a report table name contains only supported safe SQL
     * identifier forms. This report builder later qualifies columns with the
     * table value, so aliases, comma-separated table references, and multi-dot
     * names are rejected: only a simple name or a single schema-qualified name
     * ({@code schema.table}) is accepted, even though other legacy validators
     * may accept more. Surrounding whitespace is tolerated, matching the prior
     * regex that validated {@code tableName.trim()}, so legacy report configs
     * with incidental padding are not rejected.
     *
     * @param tableName the table name string to validate
     * @throws SecurityException if the table name contains invalid characters
     */
    static void validateTableName(String tableName) {
        String trimmed = tableName == null ? null : tableName.trim();
        if (!SqlIdentifierValidator.isValidIdentifier(trimmed)
                || trimmed.chars().filter(ch -> ch == '.').count() > 1) {
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

        // The WHERE predicates are the report's stored filter definitions; the request only says
        // which ones are ticked and supplies the ${...} placeholder values, which are bound (#5831).
        List<CheckedFilter> checked = checkedFilters(reportId, request);
        List<ParameterizedSql> vecVarValue = getQueryValueParameterized(checked, request);

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

    /** A stored report filter the user ticked: its predicate template and date format. */
    record CheckedFilter(String predicate, String dateFormat) { }

    /**
     * Thrown when a request ticks a filter the report does not define (or no longer defines,
     * for example a page opened before an administrator deleted the filter). The predicate SQL
     * only ever comes from the stored definition (#5831), so an unknown filter cannot be
     * honoured, and running the report without it would silently widen the cohort.
     */
    public static final class UnknownReportFilterException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        UnknownReportFilterException() {
            super("The report filter selection does not match this report's filters; reload the report filter page.");
        }
    }

    /**
     * Resolves the ticked {@code filter_<order no>} checkboxes to the report's stored, active
     * filter definitions, in their configured order. The request's hidden {@code value_<n>} and
     * {@code dateFormat_<n>} fields are deliberately not read: they used to carry the SQL
     * predicate text itself, which let a caller control the WHERE clause (#5831).
     *
     * <p>Where two active filters share an order number the page renders duplicate parameter
     * names and the browser submits the first, so the first stored definition wins here too.</p>
     *
     * @param reportId the configured report id
     * @param request the report filter form submission
     * @return the ticked filters, in stored order
     * @throws SQLException when the report id is invalid or the filters cannot be read
     * @throws UnknownReportFilterException when a ticked filter is not one of the report's active filters
     */
    List<CheckedFilter> checkedFilters(String reportId, HttpServletRequest request) throws SQLException {
        Map<String, CheckedFilter> defined = new LinkedHashMap<>();
        for (Object row : new RptReportFilter().getNameList(reportId, 1)) {
            String[] filter = (String[]) row;
            defined.putIfAbsent(filter[3], new CheckedFilter(filter[1], filter[5]));
        }
        Set<String> ticked = new LinkedHashSet<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith(CHECK_BOX)) {
                ticked.add(name.substring(CHECK_BOX.length()));
            }
        }
        if (!defined.keySet().containsAll(ticked)) {
            throw new UnknownReportFilterException();
        }
        List<CheckedFilter> checked = new ArrayList<>();
        defined.forEach((orderNo, filter) -> {
            if (ticked.contains(orderNo)) {
                checked.add(filter);
            }
        });
        return checked;
    }

    /**
     * Binds each ticked filter's {@code ${name}} placeholders to the request parameter of the
     * same name, converting {@code startDateN}/{@code endDateN} from the filter's date format.
     * The predicate text is the stored definition; only the placeholder values come from the request.
     */
    private List<ParameterizedSql> getQueryValueParameterized(List<CheckedFilter> checked, HttpServletRequest request) throws Exception {
        List<ParameterizedSql> ret = new ArrayList<>();
        for (CheckedFilter filter : checked) {
            String tempVal = filter.predicate();
            String dateFormat = filter.dateFormat() == null ? "" : filter.dateFormat();
            Vector vecVar = RptReportCreator.getVarVec(tempVal);
            Vector vecVarValue = new Vector();
            for (int j = 0; j < vecVar.size(); j++) {
                if (((String) vecVar.get(j)).matches(VARNAME_FORMAT) && dateFormat.length() > 1) {
                    vecVarValue.add(RptReportCreator.getDiffDateFormat(request.getParameter((String) vecVar.get(j)),
                            dateFormat, "yyyy-MM-dd"));
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
