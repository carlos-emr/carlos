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


package io.github.carlos_emr.carlos.report.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Properties;

import org.apache.commons.codec.digest.DigestUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Validates and executes Query-by-Example SQL for authorized report users.
 * Queries run read-only with row and timeout limits, and every outcome is audited
 * without recording the submitted SQL text.
 */
public class RptByExampleData {
    public static final int MAX_ROWS = 1_000;
    public static final int MAX_OUTPUT_CHARACTERS = 1_000_000;
    public static final int QUERY_TIMEOUT_SECONDS = 15;

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public record QueryResult(String html, int rowCount, boolean truncated, boolean rowLimitReached,
            long durationMillis) {
    }

    private static final class ExecutionProgress {
        private int rowCount;

        int rowCount() {
            return rowCount;
        }

        void recordRows(int renderedRows) {
            rowCount = renderedRows;
        }
    }

    private final ConnectionProvider connectionProvider;

    public RptByExampleData() {
        this(LegacyJdbcQuery::getConnection);
    }

    RptByExampleData(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @SuppressFBWarnings(
            value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
            justification = "Runtime failures are audited and handled by the action")
    public QueryResult execute(String sql, Properties properties, String providerNo) throws SQLException {
        long startedAt = System.nanoTime();
        String outcome = "failed";
        ExecutionProgress progress = new ExecutionProgress();
        try {
            LegacyJdbcQuery.TrustedSql trustedSql = QueryByExampleSqlValidator.validate(sql, properties);
            QueryResult queryResult = executeWithConnection(trustedSql, progress);
            outcome = "success";
            return new QueryResult(queryResult.html(), queryResult.rowCount(), queryResult.truncated(),
                    queryResult.rowLimitReached(), elapsedMillis(startedAt));
        } catch (QueryByExampleValidationException e) {
            outcome = "rejected";
            throw e;
        } catch (SQLTimeoutException e) {
            outcome = "timeout";
            throw e;
        } catch (SQLException e) {
            logFailure(providerNo, sql, e.getSQLState(), e.getClass().getSimpleName());
            throw e;
        } catch (RuntimeException e) {
            logFailure(providerNo, sql, null, e.getClass().getSimpleName());
            throw e;
        } finally {
            audit(providerNo, sql, elapsedMillis(startedAt), progress.rowCount(), outcome);
        }
    }

    @SuppressFBWarnings(
            value = {"SQL_INJECTION_JDBC", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"},
            justification = "This narrow sink accepts only TrustedSql created by structural SELECT validation")
    private static PreparedStatement prepareValidatedStatement(Connection connection,
            LegacyJdbcQuery.TrustedSql trustedSql) throws SQLException {
        // codeql[java/sql-injection] -- TrustedSql is created only after structural SELECT validation.
        return connection.prepareStatement(trustedSql.sql(), ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY); // nosemgrep: java.lang.security.audit.formatted-sql-string-deepsemgrep.formatted-sql-string-deepsemgrep -- validated TrustedSql boundary
    }

    private QueryResult executeWithConnection(LegacyJdbcQuery.TrustedSql trustedSql, ExecutionProgress progress)
            throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            return executeValidatedQuery(connection, trustedSql, progress);
        }
    }

    private static QueryResult executeValidatedQuery(Connection connection, LegacyJdbcQuery.TrustedSql trustedSql,
            ExecutionProgress progress) throws SQLException {
        boolean originalReadOnly = connection.isReadOnly();
        try {
            connection.setReadOnly(true);
        } catch (SQLException | RuntimeException setupFailure) {
            restoreReadOnlyAfterFailure(connection, originalReadOnly, setupFailure);
            throw setupFailure;
        }
        QueryResult result;
        try {
            result = executeStatement(connection, trustedSql, progress);
        } catch (SQLException | RuntimeException executionFailure) {
            restoreReadOnlyAfterFailure(connection, originalReadOnly, executionFailure);
            throw executionFailure;
        }
        connection.setReadOnly(originalReadOnly);
        return result;
    }

    private static QueryResult executeStatement(Connection connection, LegacyJdbcQuery.TrustedSql trustedSql,
            ExecutionProgress progress) throws SQLException {
        try (PreparedStatement statement = prepareValidatedStatement(connection, trustedSql)) {
            statement.setMaxRows(MAX_ROWS + 1);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            return readStatementResult(statement, progress);
        }
    }

    private static QueryResult readStatementResult(PreparedStatement statement, ExecutionProgress progress)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            RptResultStruct.StructuredResult structured = RptResultStruct.getStructureWithCount(
                    resultSet, MAX_OUTPUT_CHARACTERS, MAX_ROWS);
            progress.recordRows(structured.rowCount());
            return new QueryResult(structured.html(), structured.rowCount(), structured.truncated(),
                    structured.rowLimitReached(), 0);
        }
    }

    private static void restoreReadOnlyAfterFailure(Connection connection, boolean originalReadOnly,
            Exception executionFailure) {
        try {
            connection.setReadOnly(originalReadOnly);
        } catch (SQLException | RuntimeException restoreFailure) {
            if (restoreFailure != executionFailure) {
                executionFailure.addSuppressed(restoreFailure);
            }
        }
    }

    public static void audit(String providerNo, String sql, long durationMillis, int rowCount, String outcome) {
        String query = sql == null ? "" : sql;
        String queryHash = queryHash(query);
        MiscUtils.getLogger().info(
                "Query-by-Example audit provider={} queryHash={} queryLength={} durationMs={} rowCount={} outcome={}",
                providerNo, queryHash, query.length(), durationMillis, rowCount, outcome);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static void logFailure(String providerNo, String sql, String sqlState, String exceptionType) {
        if (MiscUtils.getLogger().isWarnEnabled()) {
            MiscUtils.getLogger().warn(
                    "Query-by-Example failure provider={} queryHash={} sqlState={} exceptionType={}",
                    providerNo, queryHash(sql), sqlState, exceptionType);
        }
    }

    private static String queryHash(String sql) {
        return DigestUtils.sha256Hex(sql == null ? "" : sql);
    }
}
