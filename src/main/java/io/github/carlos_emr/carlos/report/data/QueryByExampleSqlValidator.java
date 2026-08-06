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
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.NextValExpression;
import net.sf.jsqlparser.expression.UserVariable;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Fail-closed validation for request-submitted Query-by-Example SQL.
 *
 * <p>Accepted input is one non-locking, non-output {@code SELECT} whose table
 * references are unqualified or belong to the configured application schema.
 * Comments, statement separators, set operations, write/control keywords, and
 * prohibited database functions are rejected.</p>
 *
 * @since 2026-08-06
 */
public final class QueryByExampleSqlValidator {
    public static final int MAX_SQL_CHARACTERS = 16_384;

    private static final Pattern LOCKING_SELECT = Pattern.compile(
            "\\bfor\\s+(?:update|share)\\b|\\block\\s+in\\s+share\\s+mode\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTPUT_OR_PROCEDURE_OPERATION = Pattern.compile(
            "\\b(?:into|procedure)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Reporting-safe built-ins. Anything not listed is rejected so stored functions and
     * newly introduced vendor functions cannot silently cross the validation boundary.
     */
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "abs", "acos", "adddate", "addtime", "ascii", "asin", "atan", "atan2", "avg",
            "bin", "bit_and", "bit_length", "bit_or", "bit_xor", "ceil", "ceiling", "char_length",
            "character_length", "coalesce", "concat", "concat_ws", "conv", "convert_tz", "cos", "cot",
            "count", "crc32", "curdate", "current_date", "current_time", "current_timestamp", "curtime",
            "date", "date_add", "date_format", "date_sub", "datediff", "day", "dayname", "dayofmonth",
            "dayofweek", "dayofyear", "degrees", "elt", "exp", "field", "find_in_set", "floor", "format",
            "from_base64", "from_days", "from_unixtime", "get_format", "greatest", "hex", "hour", "if",
            "ifnull", "instr", "lcase", "least", "left", "length", "ln", "localtime", "localtimestamp",
            "locate", "log", "log10", "log2", "lower", "lpad", "ltrim", "makedate", "maketime", "max",
            "md5", "microsecond", "mid", "min", "minute", "mod", "month", "monthname", "now", "nullif",
            "oct", "octet_length", "ord", "period_add", "period_diff", "pi", "pow", "power", "quarter",
            "quote", "radians", "rand", "repeat", "replace", "reverse", "right", "round", "rpad", "rtrim",
            "sec_to_time", "second", "sha", "sha1", "sha2", "sign", "sin", "soundex", "space", "sqrt",
            "std", "stddev", "stddev_pop", "stddev_samp", "str_to_date", "strcmp", "subdate", "substr",
            "substring", "substring_index", "subtime", "sum", "sysdate", "tan", "time", "time_format",
            "time_to_sec", "timediff", "timestamp", "timestampadd", "timestampdiff", "to_base64", "to_days",
            "trim", "truncate", "ucase", "unhex", "unix_timestamp", "upper", "utc_date", "utc_time",
            "utc_timestamp", "variance", "var_pop", "var_samp", "week", "weekday", "weekofyear", "year",
            "yearweek", "cume_dist", "dense_rank", "first_value", "lag", "last_value", "lead", "nth_value",
            "ntile", "percent_rank", "rank", "row_number");

    private QueryByExampleSqlValidator() {
    }

    /**
     * Validates SQL and returns the same text wrapped for the trusted JDBC boundary.
     *
     * @param sql request-submitted SQL to validate
     * @param properties application properties containing a non-blank {@code db_name}
     * @return the unchanged SQL represented as {@link LegacyJdbcQuery.TrustedSql}
     * @throws QueryByExampleValidationException if the SQL is empty, cannot be parsed,
     *         is not one allowed {@code SELECT}, or references an unapproved schema or operation
     */
    public static LegacyJdbcQuery.TrustedSql validate(String sql, Properties properties)
            throws QueryByExampleValidationException {
        if (sql == null) {
            throw new QueryByExampleValidationException("SQL query must not be empty");
        }
        if (sql.length() > MAX_SQL_CHARACTERS) {
            throw new QueryByExampleValidationException("SQL query exceeds the allowed length");
        }
        if (sql.isBlank()) {
            throw new QueryByExampleValidationException("SQL query must not be empty");
        }

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException | RuntimeException e) {
            throw new QueryByExampleValidationException("The query could not be parsed as a single SELECT", e);
        }

        if (!(statement instanceof Select select) || containsSetOperation(select)) {
            throw new QueryByExampleValidationException("Only one SELECT statement is allowed");
        }

        String sqlWithoutQuotedSections = stripQuotedSections(sql);
        if (LOCKING_SELECT.matcher(sqlWithoutQuotedSections).find()) {
            throw new QueryByExampleValidationException("Locking SELECT statements are not allowed");
        }
        if (OUTPUT_OR_PROCEDURE_OPERATION.matcher(sqlWithoutQuotedSections).find()) {
            throw new QueryByExampleValidationException("SELECT output operations are not allowed");
        }
        rejectUnsafeExpressionsAndOtherSchemas(statement, applicationSchema(properties));
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

    private static void rejectUnsafeExpressionsAndOtherSchemas(Statement statement, String applicationSchema)
            throws QueryByExampleValidationException {
        Set<String> tables;
        SqlSafetyVisitor visitor = new SqlSafetyVisitor();
        try {
            tables = visitor.getTables(statement);
        } catch (RuntimeException e) {
            throw new QueryByExampleValidationException("The query table references could not be validated", e);
        }
        if (visitor.hasOutputOperation()) {
            throw new QueryByExampleValidationException("SELECT output operations are not allowed");
        }
        if (visitor.hasLockingOperation()) {
            throw new QueryByExampleValidationException("Locking SELECT statements are not allowed");
        }
        if (visitor.hasSetOperation()) {
            throw new QueryByExampleValidationException("Set operations are not allowed");
        }
        if (visitor.hasVariables()) {
            throw new QueryByExampleValidationException("Session and system variables are not allowed");
        }
        if (!visitor.disallowedFunctions().isEmpty()) {
            throw new QueryByExampleValidationException("The query uses a function outside the allowed set");
        }
        for (String table : tables) {
            String normalizedTable = unquoteIdentifier(table);
            int lastDot = normalizedTable.lastIndexOf('.');
            if (lastDot > 0) {
                String qualifier = normalizedTable.substring(0, lastDot);
                if (!canonicalIdentifier(qualifier).equals(canonicalIdentifier(applicationSchema))) {
                    throw new QueryByExampleValidationException("Queries may only read the application database schema");
                }
            }
        }
    }

    private static boolean containsSetOperation(Select select) {
        if (select instanceof SetOperationList) {
            return true;
        }
        return select instanceof ParenthesedSelect parenthesedSelect
                && containsSetOperation(parenthesedSelect.getSelect());
    }

    private static String stripQuotedSections(String sql) {
        StringBuilder stripped = new StringBuilder(sql.length());
        char quote = '\0';
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (quote == '\0') {
                if (current == '\'' || current == '"' || current == '`') {
                    quote = current;
                    stripped.append(' ');
                } else {
                    stripped.append(current);
                }
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

    private static String canonicalIdentifier(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }

    private static final class SqlSafetyVisitor extends TablesNamesFinder<Void> {
        private final Set<String> disallowedFunctions = new java.util.HashSet<>();
        private boolean variables;
        private boolean outputOperation;
        private boolean lockingOperation;
        private boolean setOperation;

        @Override
        public <S> Void visit(Function function, S context) {
            List<String> nameParts = function.getMultipartName();
            String functionName = canonicalIdentifier(unquoteIdentifier(function.getName()));
            if (nameParts == null || nameParts.size() != 1 || !ALLOWED_FUNCTIONS.contains(functionName)) {
                disallowedFunctions.add(functionName);
            }
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(AnalyticExpression function, S context) {
            String functionName = canonicalIdentifier(unquoteIdentifier(function.getName()));
            if (!ALLOWED_FUNCTIONS.contains(functionName)) {
                disallowedFunctions.add(functionName);
            }
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(UserVariable variable, S context) {
            variables = true;
            return super.visit(variable, context);
        }

        @Override
        public <S> Void visit(NextValExpression sequence, S context) {
            variables = true;
            return super.visit(sequence, context);
        }

        @Override
        public <S> Void visit(PlainSelect select, S context) {
            if ((select.getIntoTables() != null && !select.getIntoTables().isEmpty())
                    || select.getIntoTempTable() != null) {
                outputOperation = true;
            }
            if (select.getForMode() != null || select.getForClause() != null
                    || select.getForUpdateTable() != null || select.isSkipLocked()
                    || select.isNoWait() || select.getWait() != null) {
                lockingOperation = true;
            }
            return super.visit(select, context);
        }

        @Override
        public <S> Void visit(SetOperationList operations, S context) {
            setOperation = true;
            return super.visit(operations, context);
        }

        Set<String> disallowedFunctions() {
            return disallowedFunctions;
        }

        boolean hasVariables() {
            return variables;
        }

        boolean hasOutputOperation() {
            return outputOperation;
        }

        boolean hasLockingOperation() {
            return lockingOperation;
        }

        boolean hasSetOperation() {
            return setOperation;
        }
    }
}
