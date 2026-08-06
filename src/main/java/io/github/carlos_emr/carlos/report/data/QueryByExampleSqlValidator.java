/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.data;

import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.util.TablesNamesFinder;

/** Fail-closed validation for request-submitted Query-by-Example SQL. */
public final class QueryByExampleSqlValidator {
    private static final Pattern LOCKING_SELECT = Pattern.compile(
            "\\bfor\\s+(?:update|share)\\b|\\block\\s+in\\s+share\\s+mode\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTPUT_OPERATION = Pattern.compile("\\binto\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCKED_FUNCTION = Pattern.compile(
            "(?<![a-z0-9_$])`?(?:sleep|benchmark|get_lock|release_lock|is_free_lock|load_file)`?\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private QueryByExampleSqlValidator() {
    }

    public static LegacyJdbcQuery.TrustedSql validate(String sql, Properties properties)
            throws QueryByExampleValidationException {
        if (sql == null || sql.isBlank()) {
            throw new QueryByExampleValidationException("SQL query must not be empty");
        }

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException | RuntimeException e) {
            throw new QueryByExampleValidationException("The query could not be parsed as a single SELECT", e);
        }

        if (!(statement instanceof Select select) || select instanceof SetOperationList) {
            throw new QueryByExampleValidationException("Only one SELECT statement is allowed");
        }

        String sqlWithoutStringLiterals = stripStringLiterals(sql);
        if (LOCKING_SELECT.matcher(sqlWithoutStringLiterals).find()) {
            throw new QueryByExampleValidationException("Locking SELECT statements are not allowed");
        }
        if (OUTPUT_OPERATION.matcher(sqlWithoutStringLiterals).find()) {
            throw new QueryByExampleValidationException("SELECT output operations are not allowed");
        }
        rejectBlockedFunctions(sqlWithoutStringLiterals);
        rejectOtherSchemas(statement, applicationSchema(properties));
        try {
            return LegacyJdbcQuery.trustedSelectSql(sql);
        } catch (SQLException e) {
            throw new QueryByExampleValidationException(e.getMessage(), e);
        }
    }

    static String applicationSchema(Properties properties) throws QueryByExampleValidationException {
        String configuredName = properties == null ? null : properties.getProperty("db_name");
        if (configuredName == null || configuredName.isBlank()) {
            throw new QueryByExampleValidationException("The application database schema is not configured");
        }
        String schema = configuredName.split("\\?", 2)[0].trim();
        if (schema.isEmpty()) {
            throw new QueryByExampleValidationException("The application database schema is not configured");
        }
        return unquoteIdentifier(schema);
    }

    private static void rejectOtherSchemas(Statement statement, String applicationSchema)
            throws QueryByExampleValidationException {
        Set<String> tables;
        try {
            tables = new TablesNamesFinder<Void>().getTables(statement);
        } catch (RuntimeException e) {
            throw new QueryByExampleValidationException("The query table references could not be validated", e);
        }
        for (String table : tables) {
            String normalizedTable = unquoteIdentifier(table);
            int lastDot = normalizedTable.lastIndexOf('.');
            if (lastDot > 0) {
                String qualifier = normalizedTable.substring(0, lastDot);
                if (!qualifier.equalsIgnoreCase(applicationSchema)) {
                    throw new QueryByExampleValidationException("Queries may only read the application database schema");
                }
            }
        }
    }

    private static void rejectBlockedFunctions(String sql) throws QueryByExampleValidationException {
        if (BLOCKED_FUNCTION.matcher(sql).find()) {
            throw new QueryByExampleValidationException("The query uses a prohibited database function");
        }
    }

    private static String stripStringLiterals(String sql) {
        StringBuilder stripped = new StringBuilder(sql.length());
        char quote = '\0';
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (quote == '\0') {
                if (current == '\'' || current == '"') {
                    quote = current;
                    stripped.append(' ');
                } else {
                    stripped.append(current);
                }
            } else if (current == '\\' && next != '\0') {
                stripped.append("  ");
                i++;
            } else if (current == quote && next == quote) {
                stripped.append("  ");
                i++;
            } else if (current == quote) {
                quote = '\0';
                stripped.append(' ');
            } else {
                stripped.append(' ');
            }
        }
        return stripped.toString();
    }

    private static String unquoteIdentifier(String identifier) {
        return identifier.replace("`", "").replace("\"", "").trim();
    }
}
