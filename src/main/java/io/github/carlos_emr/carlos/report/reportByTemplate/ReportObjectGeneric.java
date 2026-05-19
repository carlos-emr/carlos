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

//The main report object, data fields filled in from XML, managed by the ReportManager.java


package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/*
 * Created on December 19, 2006, 10:46 AM
 *@apavel (Paul)
 */

public class ReportObjectGeneric implements ReportObject {
    private String templateId = "";
    private String title = "";
    private String description = "";
    private String type = "";
    private int active;
    private ArrayList parameters = new ArrayList(0);
    private String uuid;

    private boolean sequence;


    public ReportObjectGeneric() {
    }

    public ReportObjectGeneric(String templateId, String title) {
        this.setTemplateId(templateId);
        this.setTitle(title);
    }

    public ReportObjectGeneric(String templateId, String title, String description) {
        this.setTemplateId(templateId);
        this.setTitle(title);
        this.setDescription(description);
    }

    public ReportObjectGeneric(String templateId, String title, String description, String type, ArrayList parameters) {
        this.setTemplateId(templateId);
        this.setTitle(title);
        this.setDescription(description);
        this.setType(type);
        this.setParameters(parameters);
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ArrayList getParameters() {
        return parameters;
    }

    public void setParameters(ArrayList parameters) {
        this.parameters = parameters;
    }

    public String getPreparedSQL(Map parameters) {
        String sql = (new ReportManager()).getSQL(this.templateId);
        int cursor1 = 0;
        while ((cursor1 = sql.indexOf("{")) != -1) {
            int cursor2 = sql.indexOf("}", cursor1);
            String paramId = sql.substring(cursor1 + 1, cursor2);

            String[] substValues = (String[]) parameters.get(paramId);
            if (substValues == null) { //if type textlist or this param isn't in the request
                substValues = (String[]) parameters.get(paramId + ":list");
                if (substValues != null) {
                    substValues[0] = substValues[0].replaceAll(" ", "");
                    substValues = StringUtils.splitToStringArray(substValues[0], ",");
                } else if (parameters.get(paramId + ":check") != null) {
                    substValues = new String[0];
                } else return "";
            }
            if (substValues.length == 1) //if one valuemnmmnm
                sql = sql.substring(0, cursor1) + substValues[0] + sql.substring(cursor2 + 1);
            else { //if multiple values
                //DynamicElement curelement = getDynamicElement(dynamicElementId);
                if (cursor1 != 0 && (sql.charAt(cursor1 - 1) == '\'' || sql.charAt(cursor1 - 1) == '\"')) {
                    sql = sql.substring(0, cursor1) + StringUtils.join(substValues, sql.charAt(cursor1 - 1) + "," + sql.charAt(cursor1 - 1)) + sql.substring(cursor2 + 1);
                } else {
                    sql = sql.substring(0, cursor1) + StringUtils.join(substValues, ",") + sql.substring(cursor2 + 1);
                }

            }
        }
        MiscUtils.getLogger().debug("<REPORT BY TEMPLATE> SQL: " + sql);
        return sql;
    }

    public String getPreparedSQL(int sequenceNo, Map parameters) {
        String sql = (new ReportManager()).getSQL(this.templateId);

        String parts[] = sql.split(";");

        if (parts.length <= sequenceNo) {
            return null;
        }
        sql = parts[sequenceNo];


        int cursor1 = 0;
        while ((cursor1 = sql.indexOf("{")) != -1) {
            int cursor2 = sql.indexOf("}", cursor1);
            String paramId = sql.substring(cursor1 + 1, cursor2);

            String[] substValues = (String[]) parameters.get(paramId);
            if (substValues == null) { //if type textlist or this param isn't in the request
                substValues = (String[]) parameters.get(paramId + ":list");
                if (substValues != null) {
                    substValues[0] = substValues[0].replaceAll(" ", "");
                    substValues = StringUtils.splitToStringArray(substValues[0], ",");
                } else if (parameters.get(paramId + ":check") != null) {
                    substValues = new String[0];
                } else return "";
            }
            if (substValues.length == 1) //if one valuemnmmnm
                sql = sql.substring(0, cursor1) + substValues[0] + sql.substring(cursor2 + 1);
            else { //if multiple values
                //DynamicElement curelement = getDynamicElement(dynamicElementId);
                if (cursor1 != 0 && (sql.charAt(cursor1 - 1) == '\'' || sql.charAt(cursor1 - 1) == '\"')) {
                    sql = sql.substring(0, cursor1) + StringUtils.join(substValues, sql.charAt(cursor1 - 1) + "," + sql.charAt(cursor1 - 1)) + sql.substring(cursor2 + 1);
                } else {
                    sql = sql.substring(0, cursor1) + StringUtils.join(substValues, ",") + sql.substring(cursor2 + 1);
                }

            }
        }
        MiscUtils.getLogger().debug("<REPORT BY TEMPLATE> SQL: " + sql);
        return sql;
    }

    /**
     * Builds a parameterized SQL query from the template and the provided HTTP request parameters.
     * <p>
     * Each <code>{paramId}</code> placeholder (and any enclosing single/double-quote characters) is
     * replaced with one or more JDBC <code>?</code> placeholders.  The corresponding values are
     * returned in the array elements starting at index&nbsp;1; element&nbsp;0 holds the SQL string.
     * <p>
     * Using this method together with {@link io.github.carlos_emr.carlos.db.LegacyJdbcQuery#getPreparedResultSet} prevents
     * SQL injection because user-supplied values never become part of the SQL text.
     *
     * @param parameters the HTTP request parameter map (from {@code request.getParameterMap()})
     * @return {@code String[]} where {@code [0]} is the parameterized SQL and {@code [1..n]} are the
     *         parameter values in the order they appear in the SQL; returns a one-element array with an
     *         empty string if a required parameter is missing from the request map or if the template
     *         SQL cannot be found for the configured templateId (an error is logged in that case)
     */
    public String[] getParameterizedSQL(Map parameters) {
        return toLegacyArray(getParameterizedSql(parameters));
    }

    public ParameterizedSql getParameterizedSql(Map parameters) {
        String sql = (new ReportManager()).getSQL(this.templateId);
        if (sql == null) {
            MiscUtils.getLogger().error("Template SQL not found for templateId: {}", LogSanitizer.sanitize(this.templateId)); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
            return new ParameterizedSql("", List.of());
        }
        return parameterizeTemplateSql(sql, parameters);
    }

    /**
     * Builds a parameterized SQL query for the specified sequence entry of a multi-statement template.
     * See {@link #getParameterizedSQL(Map)} for full documentation of the return format.
     *
     * @param sequenceNo zero-based index of the SQL statement within the template
     * @param parameters the HTTP request parameter map
     * @return {@code String[]} in the same format as {@link #getParameterizedSQL(Map)}, or {@code null}
     *         if {@code sequenceNo} is out of range
     */
    public String[] getParameterizedSQL(int sequenceNo, Map parameters) {
        ParameterizedSql parameterizedSql = getParameterizedSql(sequenceNo, parameters);
        return parameterizedSql == null ? null : toLegacyArray(parameterizedSql);
    }

    public ParameterizedSql getParameterizedSql(int sequenceNo, Map parameters) {
        String sql = (new ReportManager()).getSQL(this.templateId);
        if (sql == null) {
            MiscUtils.getLogger().error("Template SQL not found for templateId: {}", LogSanitizer.sanitize(this.templateId)); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
            return new ParameterizedSql("", List.of());
        }
        String[] parts = sql.split(";");
        if (parts.length <= sequenceNo) {
            return null;
        }
        return parameterizeTemplateSql(parts[sequenceNo], parameters);
    }

    /**
     * Replaces <code>{paramId}</code> placeholders in the given SQL with JDBC <code>?</code>
     * placeholders and collects the corresponding parameter values.
     *
     * @param sql        the SQL template fragment to parameterize
     * @param parameters the HTTP request parameter map
     * @return parameterized SQL and parameter values; returns an empty SQL string if a required
     *         parameter is missing
     */
    ParameterizedSql parameterizeTemplateSql(String sql, Map parameters) {
        List<Object> params = new ArrayList<>();

        int cursor1;
        while ((cursor1 = sql.indexOf("{")) != -1) {
            int cursor2 = sql.indexOf("}", cursor1);
            if (cursor2 == -1) {
                MiscUtils.getLogger().warn("Malformed report template: missing closing '}}' in SQL");
                return new ParameterizedSql("", List.of());
            }
            String paramId = sql.substring(cursor1 + 1, cursor2);

            String[] substValues = (String[]) parameters.get(paramId);
            if (substValues == null) {
                substValues = (String[]) parameters.get(paramId + ":list");
                if (substValues != null) {
                    if (substValues.length == 0 || substValues[0] == null) {
                        MiscUtils.getLogger().warn("Report template list parameter '{}' has no values", paramId);
                        return new ParameterizedSql("", List.of());
                    }
                    substValues[0] = substValues[0].replaceAll(" ", "");
                    substValues = StringUtils.splitToStringArray(substValues[0], ",");
                } else if (parameters.get(paramId + ":check") != null) {
                    MiscUtils.getLogger().warn("Report template checkbox parameter '{}' has no selected values", paramId);
                    return new ParameterizedSql("", List.of());
                } else {
                    MiscUtils.getLogger().warn("Report template parameter '{}' not found in request", paramId);
                    return new ParameterizedSql("", List.of());
                }
            }

            QuotedPlaceholder quotedPlaceholder = findQuotedPlaceholder(sql, cursor1, cursor2);
            int replStart = quotedPlaceholder != null ? quotedPlaceholder.start() : cursor1;
            int replEnd = quotedPlaceholder != null ? quotedPlaceholder.end() : cursor2 + 1;
            String valuePrefix = quotedPlaceholder != null ? unescapeSqlLiteralPart(quotedPlaceholder.prefix(), quotedPlaceholder.quote()) : "";
            String valueSuffix = quotedPlaceholder != null ? unescapeSqlLiteralPart(quotedPlaceholder.suffix(), quotedPlaceholder.quote()) : "";

            if (substValues.length == 0) {
                MiscUtils.getLogger().warn("Report template parameter '{}' has no values", paramId);
                return new ParameterizedSql("", List.of());
            } else if (substValues.length == 1) {
                sql = sql.substring(0, replStart) + "?" + sql.substring(replEnd);
                params.add(valuePrefix + substValues[0] + valueSuffix);
            } else {
                // Multiple values (e.g. for an IN list) – expand to ?,?,?
                StringBuilder placeholders = new StringBuilder();
                for (String v : substValues) {
                    if (placeholders.length() > 0) placeholders.append(",");
                    placeholders.append("?");
                    params.add(valuePrefix + v + valueSuffix);
                }
                sql = sql.substring(0, replStart) + placeholders + sql.substring(replEnd);
            }
        }

        MiscUtils.getLogger().debug("<REPORT BY TEMPLATE> Parameterized SQL: {}", sql);
        return new ParameterizedSql(sql, params);
    }

    private QuotedPlaceholder findQuotedPlaceholder(String sql, int placeholderStart, int placeholderEnd) {
        QuoteContext quoteContext = quoteContext(sql, placeholderStart);
        if (quoteContext == null) {
            return null;
        }

        int closeQuote = findClosingQuote(sql, placeholderEnd + 1, quoteContext.quote());
        if (closeQuote < 0) {
            return null;
        }

        return new QuotedPlaceholder(
                quoteContext.openQuote(),
                closeQuote + 1,
                sql.substring(quoteContext.openQuote() + 1, placeholderStart),
                sql.substring(placeholderEnd + 1, closeQuote),
                quoteContext.quote());
    }

    private QuoteContext quoteContext(String sql, int position) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int openQuote = -1;
        for (int i = 0; i < position; i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (inSingleQuote) {
                if (current == '\'' && next == '\'') {
                    i++;
                } else if (current == '\'') {
                    inSingleQuote = false;
                    openQuote = -1;
                }
            } else if (inDoubleQuote) {
                if (current == '"' && next == '"') {
                    i++;
                } else if (current == '"') {
                    inDoubleQuote = false;
                    openQuote = -1;
                }
            } else if (current == '\'') {
                inSingleQuote = true;
                openQuote = i;
            } else if (current == '"') {
                inDoubleQuote = true;
                openQuote = i;
            }
        }
        if (inSingleQuote) {
            return new QuoteContext('\'', openQuote);
        }
        if (inDoubleQuote) {
            return new QuoteContext('"', openQuote);
        }
        return null;
    }

    private int findClosingQuote(String sql, int after, char quote) {
        for (int i = after; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (current == quote && next == quote) {
                i++;
            } else if (current == quote) {
                return i;
            }
        }
        return -1;
    }

    private String unescapeSqlLiteralPart(String value, char quote) {
        String doubledQuote = String.valueOf(quote) + quote;
        return value.replace(doubledQuote, String.valueOf(quote));
    }

    private record QuotedPlaceholder(int start, int end, String prefix, String suffix, char quote) {
    }

    private record QuoteContext(char quote, int openQuote) {
    }

    private String[] toLegacyArray(ParameterizedSql parameterizedSql) {
        List<Object> params = parameterizedSql.getParams();
        String[] result = new String[params.size() + 1];
        result[0] = parameterizedSql.getSql();
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            result[i + 1] = param == null ? null : param.toString();
        }
        return result;
    }

    public int getActive() {
        return active;
    }

    public void setActive(int active) {
        this.active = active;
    }

    public boolean isSequence() {
        return sequence;
    }

    public void setSequence(boolean sequence) {
        this.sequence = sequence;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public void setUuid(String uuid) {
        this.uuid = uuid;

    }

}
