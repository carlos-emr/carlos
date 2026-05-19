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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;

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

    private LegacyJdbcQuery() {
    }

    public static ResultSet getPreparedResultSet(String sql, Object... params) throws SQLException {
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
     * @throws SQLException when the configured data source cannot provide a connection
     */
    public static Connection getConnection() throws SQLException {
        DataSource dataSource = dataSource();
        return releasingConnection(DataSourceUtils.getConnection(dataSource), dataSource);
    }

    public static ResultSet getPreparedResultSet(String sql, boolean updatable, Object... params) throws SQLException { // nosemgrep: formatted-sql-string -- legacy SQL shape is owned by callers; values are bound below
        DataSource dataSource = dataSource();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE,
                    updatable ? ResultSet.CONCUR_UPDATABLE : ResultSet.CONCUR_READ_ONLY);
            bindParams(ps, params);
            ResultSet rs = ps.executeQuery(); // NOSONAR javasecurity:S3649 -- parameterized query boundary
            return StatementClosingResultSet.wrap(rs, ps, connection, dataSource);
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
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (CallableStatement stmt = connection.prepareCall(sql.toString())) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setString(i + 1, params[i]);
                }
            }
            stmt.execute();
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
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

    public static ResultSet queryResults(String preparedSQL, DBPreparedHandlerParam[] params) throws SQLException {
        return getPreparedResultSet(preparedSQL, toObjects(params));
    }

    public static ResultSet queryResults(String preparedSQL) throws SQLException { // nosemgrep: formatted-sql-string -- admin report SQL is validated before execution
        validateSafeSelectQuery(preparedSQL);
        DataSource dataSource = dataSource();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(preparedSQL);
            return StatementClosingResultSet.wrap(rs, stmt, connection, dataSource);
        } catch (SQLException | RuntimeException e) {
            closeStatement(stmt);
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw e;
        }
    }

    public static ResultSet queryResultsPaged(String preparedSQL, String param, int offset) throws SQLException {
        return advance(queryResults(preparedSQL, param), offset);
    }

    public static ResultSet queryResultsPaged(String preparedSQL, String[] params, int offset) throws SQLException {
        return advance(queryResults(preparedSQL, params), offset);
    }

    public static ResultSet queryResultsPaged(String preparedSQL, DBPreparedHandlerParam[] params, int offset) throws SQLException {
        return advance(queryResults(preparedSQL, params), offset);
    }

    public static Object[] queryResultsCaisi(String preparedSQL, int param) throws SQLException {
        return queryResultsCaisi(preparedSQL, new Object[] { param });
    }

    public static Object[] queryResultsCaisi(String preparedSQL, String param) throws SQLException {
        return queryResultsCaisi(preparedSQL, new Object[] { param });
    }

    public static Object[] queryResultsCaisi(String preparedSQL, String[] params) throws SQLException {
        return queryResultsCaisi(preparedSQL, (Object[]) params);
    }

    static void validateSafeSelectQuery(String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("SQL query must not be empty");
        }

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!startsWithSqlWord(normalized, "select")) {
            throw new SQLException("Only SELECT statements are allowed");
        }

        if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new SQLException("Unsafe SQL detected: comment or statement separator");
        }

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

    private static Object[] toObjects(DBPreparedHandlerParam[] params) {
        Object[] values = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            DBPreparedHandlerParam param = params[i];
            if (param == null) {
                values[i] = null;
            } else if (DBPreparedHandlerParam.PARAM_STRING.equals(param.getParamType())) {
                values[i] = param.getStringValue();
            } else if (DBPreparedHandlerParam.PARAM_DATE.equals(param.getParamType())) {
                values[i] = param.getDateValue();
            } else if (DBPreparedHandlerParam.PARAM_INT.equals(param.getParamType())) {
                values[i] = param.getIntValue();
            } else if (DBPreparedHandlerParam.PARAM_TIMESTAMP.equals(param.getParamType())) {
                values[i] = param.getTimestampValue();
            }
        }
        return values;
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

    private static Object[] queryResultsCaisi(String preparedSQL, Object[] params) throws SQLException {
        DataSource dataSource = dataSource();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement(preparedSQL);
            bindParams(ps, params);
            ResultSet rs = ps.executeQuery();
            return new Object[] { rs, releasingPreparedStatement(ps, connection, dataSource) };
        } catch (SQLException | RuntimeException e) {
            closeStatement(ps);
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw e;
        }
    }

    private static PreparedStatement releasingPreparedStatement(PreparedStatement delegate, Connection connection, DataSource dataSource) {
        AtomicBoolean released = new AtomicBoolean(false);
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName())) {
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    } finally {
                        if (released.compareAndSet(false, true)) {
                            DataSourceUtils.releaseConnection(connection, dataSource);
                        }
                    }
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        };
        return (PreparedStatement) Proxy.newProxyInstance(delegate.getClass().getClassLoader(),
                new Class<?>[] { PreparedStatement.class }, handler);
    }

    private static Connection releasingConnection(Connection delegate, DataSource dataSource) {
        AtomicBoolean released = new AtomicBoolean(false);
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName())) {
                    if (released.compareAndSet(false, true)) {
                        DataSourceUtils.releaseConnection(delegate, dataSource);
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
