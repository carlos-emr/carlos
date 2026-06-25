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

package io.github.carlos_emr.carlos.db;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Spring-managed JDBC boundary for legacy callers that still consume open
 * {@link ResultSet} instances.
 *
 * <p>This class exists as a transition layer for legacy screens whose public
 * APIs expose {@code ResultSet}. New code should use typed DAOs/services instead
 * of returning JDBC cursors.</p>
 */
public final class LegacyJdbcQuery {
    private static final int MAX_THREAD_RESOURCES_BEFORE_WARNING = 10;
    private static final int MAX_THREAD_RESOURCES_BEFORE_EXCEPTION = 50;
    private static final ThreadLocal<Deque<AutoCloseable>> THREAD_RESOURCES = new ThreadLocal<>();

    private LegacyJdbcQuery() {
    }

    /**
     * SQL text that has passed the legacy report-query validation boundary.
     * Dynamic report code must construct one of these before reaching JDBC.
     */
    public static final class TrustedSql {
        private final String sql;

        private TrustedSql(String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }

    /**
     * Owns the live {@link ResultSet} returned by legacy CAISI query paths.
     *
     * <p>The contained result set is mutable, cursor-based, and not thread-safe.
     * Callers must close this wrapper, preferably with try-with-resources; closing
     * it closes the result set and releases the statement/connection resources
     * registered for the legacy query.</p>
     *
     * @param resultSet result set cursor returned by the CAISI query
     */
    public record CaisiResult(ResultSet resultSet) implements AutoCloseable {
        public CaisiResult {
            Objects.requireNonNull(resultSet, "resultSet");
        }

        @Override
        public void close() throws SQLException {
            resultSet.close();
        }
    }

    public interface LegacyJdbcParameter {
        Object jdbcValue();
    }

    public static TrustedSql trustedSelectSql(String sql) throws SQLException {
        validateSafeSelectQuery(sql);
        return new TrustedSql(sql);
    }

    public static TrustedSql trustedReportSelectSql(String sql) throws SQLException {
        validateReportSelectQuery(sql);
        return new TrustedSql(sql);
    }

    public static ResultSet getPreparedResultSet(String sql, Object... params) throws SQLException {
        return getPreparedResultSet(sql, false, params);
    }

    public static ResultSet getPreparedResultSet(ParameterizedSql sql) throws SQLException {
        return getPreparedResultSet(trustedReportSelectSql(sql.getSql()), false, sql.getParamsArray());
    }

    public static ResultSet getPreparedResultSet(TrustedSql sql, Object... params) throws SQLException {
        return getPreparedResultSet(sql, false, params);
    }

    /**
     * Obtains a Spring-managed JDBC connection for legacy APIs that require a
     * {@link Connection} parameter.
     *
     * <p>The returned object is a proxy around a connection obtained through
     * {@link DataSourceUtils}, so it participates in Spring transaction
     * synchronization. Calling {@link Connection#close()} on the proxy releases
     * it through {@link DataSourceUtils}; callers should always use
     * try-with-resources.</p>
     *
     * @return a connection participating in Spring transaction synchronization
     * @throws SQLException if a Spring-managed JDBC connection cannot be acquired
     */
    public static Connection getConnection() throws SQLException {
        DataSource dataSource = dataSource();
        Connection connection = getRequiredConnection(dataSource);
        try {
            return registerThreadResource(releasingConnection(connection, dataSource));
        } catch (RuntimeException e) {
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw e;
        }
    }

    /**
     * Executes caller-owned legacy SQL with JDBC-bound values.
     *
     * <p>This overload intentionally does not validate the SQL text because it is
     * used by migrated legacy call sites where the SQL shape is assembled from
     * server-owned constants or separately allowlisted identifiers. New dynamic
     * SQL boundaries should prefer {@link #trustedSelectSql(String)} or
     * {@link #trustedReportSelectSql(String)} and then call the
     * {@link TrustedSql} overload.</p>
     */
    public static ResultSet getPreparedResultSet(String sql, boolean updatable, Object... params) throws SQLException { // nosemgrep: formatted-sql-string -- legacy SQL shape is owned by callers; values are bound below
        return getPreparedResultSetLegacyRaw(sql, updatable, params);
    }

    public static ResultSet getPreparedResultSet(TrustedSql sql, boolean updatable, Object... params) throws SQLException {
        return getPreparedResultSetValidated(sql, updatable, params);
    }

    private static ResultSet getPreparedResultSetValidated(TrustedSql sql, boolean updatable, Object... params) throws SQLException {
        DataSource dataSource = dataSource();
        Connection connection = getRequiredConnection(dataSource);
        PreparedStatement ps = null;
        try {
            // codeql[java/sql-injection] -- TrustedSql is constructed only after legacy SELECT validation; values are bound below.
            ps = connection.prepareStatement(sql.sql, ResultSet.TYPE_SCROLL_SENSITIVE, // nosemgrep: java.lang.security.audit.formatted-sql-string-deepsemgrep.formatted-sql-string-deepsemgrep -- TrustedSql is constructed only after legacy SELECT validation; values are bound below
                    updatable ? ResultSet.CONCUR_UPDATABLE : ResultSet.CONCUR_READ_ONLY);
            bindParams(ps, params);
            ResultSet rs = ps.executeQuery(); // NOSONAR javasecurity:S3649 -- parameterized query boundary
            return registerThreadResource(StatementClosingResultSet.wrap(rs, ps, connection, dataSource));
        } catch (SQLException | RuntimeException e) {
            closeStatement(ps);
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw e;
        }
    }

    private static ResultSet getPreparedResultSetLegacyRaw(String sql, boolean updatable, Object... params) throws SQLException {
        DataSource dataSource = dataSource();
        Connection connection = getRequiredConnection(dataSource);
        PreparedStatement ps = null;
        try {
            // codeql[java/sql-injection] -- Raw legacy overload is restricted to caller-owned SQL shape; request-driven SQL uses TrustedSql or ParameterizedSql.
            ps = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, // nosemgrep: java.lang.security.audit.formatted-sql-string-deepsemgrep.formatted-sql-string-deepsemgrep -- raw legacy overload is restricted to caller-owned SQL shape; values are bound below
                    updatable ? ResultSet.CONCUR_UPDATABLE : ResultSet.CONCUR_READ_ONLY);
            bindParams(ps, params);
            ResultSet rs = ps.executeQuery(); // NOSONAR javasecurity:S3649 -- parameterized query boundary
            return registerThreadResource(StatementClosingResultSet.wrap(rs, ps, connection, dataSource));
        } catch (SQLException | RuntimeException e) {
            closeStatement(ps);
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw e;
        }
    }

    public static void procExecute(String procName, String[] params) throws SQLException {
        validateProcName(procName);
        StringBuilder sql = new StringBuilder("{call ").append(procName);
        if (params != null && params.length > 0) {
            StringBuilder prms = new StringBuilder();
            for (int i = 0; i < params.length; i++) {
                prms.append("?,");
            }
            sql.append("(").append(prms.substring(0, prms.length() - 1)).append(")");
        }
        sql.append("}");

        DataSource dataSource = dataSource();
        Connection connection = getRequiredConnection(dataSource);
        AutoCloseable connectionResource;
        try {
            connectionResource = registerThreadResource(
                    () -> DataSourceUtils.releaseConnection(connection, dataSource));
        } catch (RuntimeException e) {
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw e;
        }
        try (CallableStatement stmt = connection.prepareCall(sql.toString())) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setString(i + 1, params[i]);
                }
            }
            stmt.execute();
        } finally {
            try {
                DataSourceUtils.releaseConnection(connection, dataSource);
            } finally {
                unregisterThreadResource(connectionResource);
            }
        }
    }

    public static ResultSet queryResults(String preparedSQL, String[] params, int[] intParams) throws SQLException {
        Object[] allParams = new Object[params.length + intParams.length];
        System.arraycopy(params, 0, allParams, 0, params.length);
        for (int i = 0; i < intParams.length; i++) {
            allParams[params.length + i] = intParams[i];
        }
        return getPreparedResultSet(preparedSQL, allParams);
    }

    public static ResultSet queryResults(String preparedSQL, int param) throws SQLException {
        return getPreparedResultSet(preparedSQL, param);
    }

    public static ResultSet queryResults(String preparedSQL, int[] params) throws SQLException {
        Object[] allParams = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            allParams[i] = params[i];
        }
        return getPreparedResultSet(preparedSQL, allParams);
    }

    public static ResultSet queryResults(String preparedSQL, String param) throws SQLException {
        return getPreparedResultSet(preparedSQL, param);
    }

    public static ResultSet queryResults(String preparedSQL, String[] params) throws SQLException {
        return getPreparedResultSet(preparedSQL, (Object[]) params);
    }

    public static ResultSet queryResults(String preparedSQL, LegacyJdbcParameter[] params) throws SQLException {
        return getPreparedResultSet(preparedSQL, toObjects(params));
    }

    public static ResultSet queryResults(String preparedSQL) throws SQLException {
        String sqlState = preparedSQL == null ? "null" : "provided";
        throw new SQLException("Direct SQL execution is disabled for " + sqlState
                + " SQL; use parameterized query overloads.");
    }

    public static ResultSet queryResultsPaged(String preparedSQL, String param, int offset) throws SQLException {
        return advance(queryResults(preparedSQL, param), offset);
    }

    public static ResultSet queryResultsPaged(String preparedSQL, String[] params, int offset) throws SQLException {
        return advance(queryResults(preparedSQL, params), offset);
    }

    public static ResultSet queryResultsPaged(String preparedSQL, LegacyJdbcParameter[] params, int offset) throws SQLException {
        return advance(queryResults(preparedSQL, params), offset);
    }

    public static CaisiResult queryResultsCaisi(String preparedSQL, int param) throws SQLException {
        return queryResultsCaisi(preparedSQL, new Object[] { param });
    }

    public static CaisiResult queryResultsCaisi(String preparedSQL, String param) throws SQLException {
        return queryResultsCaisi(preparedSQL, new Object[] { param });
    }

    public static CaisiResult queryResultsCaisi(String preparedSQL, String[] params) throws SQLException {
        return queryResultsCaisi(preparedSQL, (Object[]) params);
    }

    /**
     * Strict SELECT validator for generic legacy SQL boundaries. It rejects
     * UNION, stacked statements, comments, DML/DDL/control keywords, and file
     * access functions because these call sites do not have a report-template
     * review process.
     */
    public static void validateSafeSelectQuery(String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("SQL query must not be empty");
        }

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!startsWithSqlWord(normalized, "select")) {
            throw new SQLException("Only SELECT statements are allowed");
        }

        if (containsUnsafeSqlControlToken(sql)) {
            throw new SQLException("Unsafe SQL detected: comment or statement separator");
        }

        // Defense-in-depth for obvious legacy admin-query tautologies only; JDBC
        // parameter binding and the structural checks above remain the primary SQLi controls.
        if (normalized.contains("' or '") || normalized.contains("\" or \"") || normalized.contains(" or 1=1")) {
            throw new SQLException("Potential SQL injection pattern detected");
        }

        if (containsSqlWord(normalized, "union")) {
            throw new SQLException("Unsafe SQL detected: UNION not permitted");
        }

        String[] blockedWords = {"insert", "update", "delete", "drop", "alter", "create", "truncate",
                "grant", "revoke", "exec", "execute", "call", "merge", "commit", "rollback"};
        for (String word : blockedWords) {
            if (containsSqlWord(normalized, word)) {
                throw new SQLException("Unsafe SQL detected: prohibited keyword");
            }
        }

        String[] blockedPhrases = {"into outfile", "into dumpfile", "load_file", "load data"};
        for (String phrase : blockedPhrases) {
            if (normalized.contains(phrase)) {
                throw new SQLException("Unsafe SQL detected: prohibited keyword");
            }
        }
    }

    /**
     * SELECT validator for curated report-template SQL. It still rejects stacked
     * statements, comments, and file access phrases, but intentionally allows
     * UNION because existing report templates use UNION for legitimate reporting
     * queries and are handled at the report-template boundary.
     */
    public static void validateReportSelectQuery(String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("SQL query must not be empty");
        }

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!startsWithSqlWord(normalized, "select")) {
            throw new SQLException("Only SELECT statements are allowed");
        }

        if (containsUnsafeSqlControlToken(sql)) {
            throw new SQLException("Unsafe SQL detected: comment or statement separator");
        }

        String[] blockedPhrases = {"into outfile", "into dumpfile", "load_file", "load data"};
        for (String phrase : blockedPhrases) {
            if (normalized.contains(phrase)) {
                throw new SQLException("Unsafe SQL detected: prohibited keyword");
            }
        }
    }

    private static Object[] toObjects(LegacyJdbcParameter[] params) {
        Object[] values = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            values[i] = params[i] == null ? null : params[i].jdbcValue();
        }
        return values;
    }

    /**
     * Detects statement separators and SQL comments outside quoted literals.
     *
     * <p>A single trailing semicolon is allowed for legacy eForm/report templates
     * because the bundled AP SQL historically included it. Semicolons inside
     * quoted literals, such as {@code GROUP_CONCAT(... SEPARATOR ';')}, are data
     * and are not treated as stacked statements.</p>
     */
    public static boolean containsUnsafeSqlControlToken(String sql) {
        if (sql == null) {
            return false;
        }
        return new SqlControlTokenScanner(sql).containsUnsafeControlToken();
    }

    private static final class SqlControlTokenScanner {
        private final String sql;
        private int position;
        private char quote;

        private SqlControlTokenScanner(String sql) {
            this.sql = sql;
        }

        private boolean containsUnsafeControlToken() {
            for (position = 0; position < sql.length(); position++) {
                char current = sql.charAt(position);
                char next = nextChar();
                if (insideQuotedLiteral()) {
                    skipQuotedLiteralToken(current, next);
                } else if (opensQuotedLiteral(current)) {
                    quote = current;
                } else if (isUnsafeControlToken(current, next)) {
                    return true;
                }
            }
            return false;
        }

        private char nextChar() {
            return position + 1 < sql.length() ? sql.charAt(position + 1) : '\0';
        }

        private boolean insideQuotedLiteral() {
            return quote != '\0';
        }

        private void skipQuotedLiteralToken(char current, char next) {
            if (quote != '`' && current == '\\' && next != '\0') {
                position++;
            } else if (current == quote && next == quote) {
                position++;
            } else if (current == quote) {
                quote = '\0';
            }
        }

        private static boolean opensQuotedLiteral(char current) {
            return current == '\'' || current == '"' || current == '`';
        }

        private boolean isUnsafeControlToken(char current, char next) {
            return (current == '-' && next == '-')
                    || current == '#'
                    || (current == '/' && next == '*')
                    || (current == '*' && next == '/')
                    || (current == ';' && hasNonWhitespaceAfter(position + 1));
        }

        private boolean hasNonWhitespaceAfter(int start) {
            for (int i = start; i < sql.length(); i++) {
                if (!Character.isWhitespace(sql.charAt(i))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, p);
            }
        }
    }

    private static CaisiResult queryResultsCaisi(String preparedSQL, Object[] params) throws SQLException {
        DataSource dataSource = dataSource();
        Connection connection = getRequiredConnection(dataSource);
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement(preparedSQL);
            bindParams(ps, params);
            ResultSet rs = ps.executeQuery();
            ResultSet releasingResultSet = StatementClosingResultSet.wrap(rs, ps, connection, dataSource);
            return new CaisiResult(registerThreadResource(releasingResultSet));
        } catch (SQLException | RuntimeException e) {
            closeStatement(ps);
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw e;
        }
    }

    private static Connection releasingConnection(Connection delegate, DataSource dataSource) {
        AtomicBoolean released = new AtomicBoolean(false);
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName())) {
                    if (released.compareAndSet(false, true)) {
                        try {
                            DataSourceUtils.releaseConnection(delegate, dataSource);
                        } finally {
                            unregisterThreadResource((AutoCloseable) proxy);
                        }
                    }
                    return null;
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(delegate.getClass().getClassLoader(),
                new Class<?>[] { Connection.class }, handler);
    }

    private static Connection getRequiredConnection(DataSource dataSource) throws SQLException {
        try {
            return DataSourceUtils.getConnection(dataSource);
        } catch (CannotGetJdbcConnectionException e) {
            throw new SQLException("Unable to acquire legacy JDBC connection", e);
        }
    }

    /**
     * Request-end safety net for legacy callers that still expose {@link ResultSet}
     * across layers. Explicit try-with-resources remains the primary ownership model.
     */
    public static void releaseThreadResources() {
        Deque<AutoCloseable> resources = THREAD_RESOURCES.get();
        if (resources == null || resources.isEmpty()) {
            THREAD_RESOURCES.remove();
            return;
        }

        THREAD_RESOURCES.remove();
        while (!resources.isEmpty()) {
            AutoCloseable resource = resources.removeLast();
            try {
                resource.close();
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error closing legacy JDBC resource", e);
            }
        }
    }

    private static <T extends AutoCloseable> T registerThreadResource(T resource) {
        Deque<AutoCloseable> resources = THREAD_RESOURCES.get();
        if (resources == null) {
            resources = new ArrayDeque<>();
            THREAD_RESOURCES.set(resources);
        }
        if (resources.size() >= MAX_THREAD_RESOURCES_BEFORE_EXCEPTION) {
            throw new IllegalStateException("Legacy JDBC thread resource limit exceeded; refusing to register additional resources");
        }
        resources.add(resource);
        if (resources.size() == MAX_THREAD_RESOURCES_BEFORE_WARNING + 1) {
            MiscUtils.getLogger().warn(
                    "Legacy JDBC thread resource count exceeded {}; a caller may be leaking ResultSet or Connection objects",
                    MAX_THREAD_RESOURCES_BEFORE_WARNING);
        }
        return resource;
    }

    static void unregisterThreadResource(AutoCloseable resource) {
        Deque<AutoCloseable> resources = THREAD_RESOURCES.get();
        if (resources == null) {
            return;
        }
        resources.removeIf(candidate -> candidate == resource);
        if (resources.isEmpty()) {
            THREAD_RESOURCES.remove();
        }
    }

    private static ResultSet advance(ResultSet rs, int offset) throws SQLException {
        for (int i = 1; i <= offset; i++) {
            if (!rs.next()) {
                break;
            }
        }
        return rs;
    }

    private static void validateProcName(String procName) throws SQLException {
        if (procName == null || procName.isEmpty()) {
            throw new SQLException("Stored procedure name must not be null or empty");
        }
        // Dots are intentionally allowed for legacy schema-qualified procedure names.
        if (!procName.matches("[a-zA-Z0-9_.]+")) {
            throw new SQLException(
                    "Stored procedure name contains invalid characters; only letters, digits, underscores and dots are allowed");
        }
    }

    private static boolean containsSqlWord(String sql, String word) {
        int index = sql.indexOf(word);
        while (index >= 0) {
            int before = index - 1;
            int after = index + word.length();
            boolean leftBoundary = before < 0 || !isSqlIdentifierPart(sql.charAt(before));
            boolean rightBoundary = after >= sql.length() || !isSqlIdentifierPart(sql.charAt(after));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            index = sql.indexOf(word, index + 1);
        }
        return false;
    }

    private static boolean startsWithSqlWord(String sql, String word) {
        return sql.startsWith(word)
                && (sql.length() == word.length() || !isSqlIdentifierPart(sql.charAt(word.length())));
    }

    private static boolean isSqlIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static DataSource dataSource() {
        return SpringUtils.getBean(DataSource.class);
    }

    private static void closeStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ignored) {
                // Preserve the original query exception.
                MiscUtils.getLogger().debug("Failed to close statement", ignored);
            }
        }
    }
}
