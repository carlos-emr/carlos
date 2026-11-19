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
 * Created on 2005-8-1
 */
package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.commn.dao.ReportConfigDao;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.apache.commons.lang3.time.DateFormatUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.util.SqlIdentifierValidator;

/**
 * @author yilee18
 */
public final class RptReportCreator {
    private ReportConfigDao configurationDao() {
        return SpringUtils.getBean(ReportConfigDao.class);
    }

    // select formBCAR.pg1_ethOrig as Ethnic Origin, ...
    public String getSelectField(String recordId) throws SQLException {
        StringBuilder ret = new StringBuilder();
        for (var r : configurationDao().findByReportId(RptReportConfigData.parseReportId(recordId))) {
            String caption = RptReportConfigData.text(r.getCaption());
            ret.append(ret.isEmpty() ? " " : ", ");
            ret.append(requireValidReportIdentifier(RptReportConfigData.text(r.getTableName()))).append(".")
                    .append(requireValidReportColumnIdentifier(RptReportConfigData.text(r.getName())));
            if (!caption.isEmpty()) ret.append(" as ").append(quoteSqlStringLiteral(caption));
        }
        return ret.toString();
    }

    // from formBCAR
    public String getFromTableFirst(String recordId) throws SQLException {
        List<String> tables = configurationDao().findTableNames(RptReportConfigData.parseReportId(recordId));
        return tables.isEmpty() ? "  " : RptReportConfigData.text(tables.get(0));
    }

    // from formBCAR, demographic
    public String getFromTable(String recordId) throws SQLException {
        List<String> tables = configurationDao().findTableNames(RptReportConfigData.parseReportId(recordId));
        return "  " + String.join(",", tables.stream().map(RptReportConfigData::text).toList());
    }

    // tableName: formBCAR,formBCNewBorn... how to handle??
    public String getWhereJoinClause(String tableName, boolean bDemo) {
        String ret = "";
        if (bDemo)
            ret = tableName + ".demographic_no=demographic.demographic_no";
        return ret;
    }

    /**
     * Validates a report table identifier and returns its canonical (trimmed)
     * form for emission into generated SQL. Surrounding whitespace is tolerated
     * and only a simple or single schema-qualified name ({@code schema.table})
     * is accepted, matching {@link io.github.carlos_emr.carlos.report.pageUtil.RptFormQuery#validateTableName(String)}
     * so the same {@code reportConfig.table_name} value is handled consistently
     * across the report query and SELECT-list paths.
     *
     * @param identifier String the table identifier to validate
     * @return String the trimmed, validated table identifier
     * @throws SecurityException when the value is not a valid table identifier
     */
    static String requireValidReportIdentifier(String identifier) {
        String trimmed = identifier == null ? null : identifier.trim();
        if (!SqlIdentifierValidator.isValidIdentifier(trimmed)
                || trimmed.chars().filter(ch -> ch == '.').count() > 1) {
            MiscUtils.getLogger().error("Invalid report SQL identifier rejected");
            throw new SecurityException("Invalid report SQL identifier");
        }
        return trimmed;
    }

    /**
     * Validates a column identifier emitted into the report SELECT list. The
     * column is already qualified with its table value ({@code table.column}),
     * so only a simple, undotted name is allowed here; a dotted value such as
     * {@code schema.table.column} or {@code t.col} would double-qualify into an
     * invalid multi-part reference and is rejected. Surrounding whitespace is
     * tolerated and the trimmed value is returned, matching the table-identifier
     * handling.
     *
     * @param identifier String the column identifier to validate
     * @return String the trimmed, validated column identifier
     * @throws SecurityException when the value is not a simple SQL identifier
     */
    static String requireValidReportColumnIdentifier(String identifier) {
        String trimmed = identifier == null ? null : identifier.trim();
        if (!SqlIdentifierValidator.isValidIdentifier(trimmed) || trimmed.indexOf('.') >= 0) {
            MiscUtils.getLogger().error("Invalid report column SQL identifier rejected");
            throw new SecurityException("Invalid report column SQL identifier");
        }
        return trimmed;
    }

    static String quoteSqlStringLiteral(String value) {
        if (value == null || value.indexOf('\0') >= 0) {
            MiscUtils.getLogger().error("Invalid report SQL alias rejected");
            throw new SecurityException("Invalid report SQL alias");
        }
        // Escape backslashes first so later single-quote escaping cannot be changed
        // by MySQL's default backslash escape handling.
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    /**
     * Maximum number of {@code ${var}} placeholder replacements per template.
     * Mirrors the legacy limit in {@link #getWhereValueClause}; a typical
     * report template has fewer than 10 placeholders.
     */
    private static final int MAX_PLACEHOLDER_REPLACEMENTS = 100;

    /**
     * Joins non-empty predicate fragments with {@code " and "}, producing a
     * conjunction suitable for appending after {@code WHERE} or {@code AND}.
     * Empty or null fragments are skipped so absent filters never produce
     * stray {@code and  and} tokens.
     *
     * @param fragments predicate fragments in desired order
     * @return the joined conjunction, or the empty string if no fragment was non-empty
     */
    public static String joinPredicates(String... fragments) {
        StringBuilder sb = new StringBuilder();
        for (String f : fragments) {
            if (f == null || f.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" and ");
            sb.append(f);
        }
        return sb.toString();
    }

    // Replace the result one by one if not null
    public static String getWhereValueClause(String value, Vector vec) {
        String ret = "";
        for (int i = 0; i < MAX_PLACEHOLDER_REPLACEMENTS; i++) {
            // Use indexOf to check for template variables to avoid potential ReDoS
            int startIdx = value.indexOf("${");
            if (startIdx >= 0) {
                int endIdx = value.indexOf("}", startIdx);
                if (endIdx > startIdx + 2) {
                    // Found a complete ${...} pattern
                    String replacement = (i < vec.size() && vec.get(i) != null) ? (String) vec.get(i) : "";
                    // Check if placeholder is inside a quoted string (char before ${ is a single quote)
                    boolean inQuotedContext = startIdx > 0 && value.charAt(startIdx - 1) == '\'';
                    if (inQuotedContext) {
                        // Escape backslashes first (MySQL backslash-escape bypass), then single quotes
                        replacement = replacement.replace("\\", "\\\\").replace("'", "''");
                    } else {
                        // Unquoted numeric context: only allow digits and optional leading minus
                        if (!replacement.isEmpty() && !replacement.matches("-?\\d+(\\.\\d+)?")) {
                            MiscUtils.getLogger().warn("Non-numeric value rejected for unquoted SQL placeholder in report template");
                            replacement = "";
                        }
                    }
                    value = value.substring(0, startIdx) + replacement + value.substring(endIdx + 1);
                } else {
                    ret = value;
                    break;
                }
            } else {
                ret = value;
                break;
            }
        }
        return ret;
    }

    /**
     * Parameterized version of {@link #getWhereValueClause(String, Vector)}.
     * Replaces {@code ${var}} placeholders with {@code ?} bind markers and
     * collects the corresponding values into a parameter list.
     *
     * <p><strong>Context detection:</strong> Uses quote-parity analysis
     * ({@link #isInsideQuotedLiteral}) to determine whether each placeholder
     * falls inside a single-quoted SQL literal. This correctly handles patterns
     * like {@code like '%${name}%'} where the placeholder is not immediately
     * preceded by a quote character. For quoted contexts, any literal prefix/suffix
     * inside the enclosing quotes is folded into the bound value (e.g.,
     * {@code '%${name}%'} binds {@code "%Smith%"} and emits {@code ?}).
     * All other placeholders are treated as numeric context.
     *
     * @implNote <strong>Numeric allowlist (fail-closed):</strong> values bound
     *           into a numeric context MUST match {@code -?\d+(\.\d+)?}. Any
     *           other value — including empty, missing, or injection attempts —
     *           causes {@link IllegalArgumentException}. This matches the legacy
     *           helper's fail-closed behavior (which produced a SQL syntax error
     *           via empty-string substitution) and prevents the database's
     *           implicit string-to-numeric cast from silently coercing
     *           non-numeric input to {@code 0} and bypassing the filter.
     * @implNote <strong>Malformed templates fail loudly:</strong> a {@code ${}
     *           without a matching closing brace, or an empty {@code ${}} placeholder,
     *           throws {@link IllegalStateException} rather than silently emitting
     *           a broken SQL string. Templates are admin-configured; a broken
     *           template is a configuration bug that should surface immediately.
     * @implNote SQL-escaped single quotes ({@code ''}) within a template are
     *           correctly handled by the quote-parity scanner. Placeholders do
     *           not nest; nested {@code ${...${...}...}} forms are undefined.
     *
     * @param value the WHERE clause template containing {@code ${var}} placeholders
     * @param vec   the replacement values, in placeholder order
     * @return a {@link ParameterizedSql} with the template and bind values
     * @throws IllegalArgumentException if a numeric-context placeholder receives
     *                                  an empty, missing, or non-numeric value
     * @throws IllegalStateException    if the template is malformed or exceeds
     *                                  {@code MAX_PLACEHOLDER_REPLACEMENTS}
     */
    public static ParameterizedSql getWhereValueClauseParameterized(String value, Vector vec) {
        List<Object> params = new ArrayList<>();
        int paramIdx = 0;

        for (int i = 0; i < MAX_PLACEHOLDER_REPLACEMENTS; i++) {
            int startIdx = value.indexOf("${");
            if (startIdx < 0) {
                return new ParameterizedSql(value, params);
            }
            int endIdx = value.indexOf("}", startIdx);
            if (endIdx <= startIdx + 2) {
                throw new IllegalStateException(
                        "Malformed report template: unclosed or empty placeholder at index " + startIdx);
            }

            Object rawValue = (paramIdx < vec.size()) ? vec.get(paramIdx) : null;
            int thisIdx = paramIdx;
            paramIdx++;

            boolean inQuotedContext = isInsideQuotedLiteral(value, startIdx);
            Object boundValue;
            if (inQuotedContext) {
                boundValue = rawValue != null ? rawValue : "";
                // Find the enclosing single quotes around this placeholder
                int openQuote = value.lastIndexOf('\'', startIdx);
                int closeQuote = value.indexOf('\'', endIdx + 1);
                if (openQuote < 0 || closeQuote < 0) {
                    // Fallback: malformed template; treat as simple replacement
                    value = value.substring(0, startIdx) + "?" + value.substring(endIdx + 1);
                } else {
                    // Extract prefix and suffix inside quotes around the placeholder
                    String prefix = value.substring(openQuote + 1, startIdx);
                    String suffix = value.substring(endIdx + 1, closeQuote);
                    // Prepend/append any literal text inside quotes to the bound value
                    if (!prefix.isEmpty() || !suffix.isEmpty()) {
                        boundValue = prefix + (rawValue != null ? rawValue.toString() : "") + suffix;
                    }
                    // Replace entire 'prefix${var}suffix' (including enclosing quotes) with ?
                    value = value.substring(0, openQuote) + "?" + value.substring(closeQuote + 1);
                }
            } else {
                String strValue = rawValue != null ? rawValue.toString() : "";
                if (!strValue.matches("-?\\d+(\\.\\d+)?")) {
                    MiscUtils.getLogger().warn(
                            "Non-numeric value rejected for unquoted report placeholder at index "
                                    + thisIdx + " (length " + strValue.length() + ")");
                    throw new IllegalArgumentException(
                            "Invalid non-numeric value for numeric filter at placeholder index " + thisIdx);
                }
                boundValue = strValue;
                value = value.substring(0, startIdx) + "?" + value.substring(endIdx + 1);
            }
            params.add(boundValue);
        }
        if (value.contains("${")) {
            throw new IllegalStateException(
                    "Report template exceeds " + MAX_PLACEHOLDER_REPLACEMENTS + " placeholders");
        }
        return new ParameterizedSql(value, params);
    }

    /**
     * Determines whether the character position {@code idx} in the given SQL
     * template string lies inside a single-quoted SQL string literal.
     *
     * <p>Counts unescaped single quotes (skipping SQL-escaped {@code ''} pairs)
     * before the index. An odd count means we are inside a quoted literal.
     *
     * @param sql the SQL template string
     * @param idx the character index to test
     * @return {@code true} if the index is inside a single-quoted literal
     */
    static boolean isInsideQuotedLiteral(String sql, int idx) {
        boolean insideQuote = false;
        for (int pos = 0; pos < idx; pos++) {
            if (sql.charAt(pos) == '\'') {
                // Skip escaped '' pairs
                if (pos + 1 < sql.length() && sql.charAt(pos + 1) == '\'') {
                    pos++; // skip the second quote
                } else {
                    insideQuote = !insideQuote;
                }
            }
        }
        return insideQuote;
    }

    public static boolean isIncludeDemo(String value) {
        boolean ret = false;
        if (value.indexOf("demographic.") >= 0)
            ret = true;
        return ret;
    }

    // get ${var} vars inside the string
    public static Vector getVarVec(String value) {
        Vector ret = new Vector();
        
        // Quick exit - no templates possible
        if (!value.contains("${")) {
            return ret;
        }
        
        int pos = 0;
        while (pos < value.length()) {
            int startIdx = value.indexOf("${", pos);
            if (startIdx == -1) break;
            
            int endIdx = value.indexOf("}", startIdx + 2);
            if (endIdx == -1) break;
            
            String varName = value.substring(startIdx + 2, endIdx);
            if (!varName.isEmpty()) {
                ret.add(varName);
            }
            
            pos = endIdx + 1;
        }

        return ret;
    }

    // change date string
    public static String getDiffDateFormat(String strDate, String oDate, String nDate) throws Exception {
        String ret = strDate;
        if (strDate.length() >= oDate.length()) {
            Date a = (new SimpleDateFormat(oDate)).parse(strDate);
            ret = DateFormatUtils.format(a, nDate);
            //ret = DateFormatUtils.format(DateUtils.parseDate(strDate, new String[] { oDate }),
            // nDate);
        } else {
            MiscUtils.getLogger().debug(" getDate wrong!!!");
        }
        return ret;
    }

    /**
     * Executes a sub-query and returns a comma-separated list of integer IDs.
     */
    public String getRltSubQuery(String sql, Object... params) throws SQLException {
        List<String> ids = new ArrayList<>();
        for (Integer id : ConfiguredReportQuery.identifiers(sql, params)) ids.add(String.valueOf(id));
        return ids.isEmpty() ? "0" : String.join(",", ids);
    }

    /** Executes configured SQL with detached rows; legacy JSPs retain their Properties contract. */
    public Vector query(String sql, Vector vecFieldName, Object... params) throws SQLException {
        Vector ret = new Vector();
        for (var row : ConfiguredReportQuery.read(sql, params).rows()) {
            Properties prop = new Properties();
            for (Object field : vecFieldName) {
                String value = row.value((String) field);
                prop.setProperty((String) field, value == null ? "" : value);
            }
            ret.add(prop);
        }
        return ret;
    }
}
