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


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.ContentDigests;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Validates and executes Query-by-Example SQL for authorized report users.
 * Queries run read-only with row and timeout limits, and every outcome is audited
 * without recording the submitted SQL text.
 *
 * <p>Two audit records describe a run, both by query hash and never by SQL text or result value:
 * the INFO line of {@link #audit} (diagnostics only: a packaged install logs at WARN, so it is
 * usually not written) and the {@code log} table row of {@link #auditRow}, which is the audit
 * trail. {@link io.github.carlos_emr.carlos.report.pageUtil.RptByExample2Action} writes that row
 * once per run, with the run's final outcome (finding 357).</p>
 *
 * <p>Two independent boundaries decide what a query may read. The application validator
 * ({@link QueryByExampleSqlValidator}) rejects anything but one safe {@code SELECT} and names the
 * restricted tables. The database then runs the query as the read-only reporting account from
 * {@link ReportingConnectionService}, which MariaDB grants {@code SELECT} only on reportable tables
 * (issue #3372). A query that the validator lets through but the database refuses ends as
 * {@link QueryByExampleAccessDeniedException}; there is no fallback to the application account.</p>
 */
public class RptByExampleData {
    public static final int MAX_ROWS = 1_000;
    public static final int MAX_OUTPUT_CHARACTERS = 1_000_000;
    public static final int QUERY_TIMEOUT_SECONDS = 15;

    // The outcome an audit record names. Each run has exactly one; operators search the log table
    // and the INFO line for these words, so they are part of the audit contract.
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_REJECTED = "rejected";
    public static final String OUTCOME_UNAVAILABLE = "unavailable";
    public static final String OUTCOME_DENIED = "denied";
    public static final String OUTCOME_TIMEOUT = "timeout";
    public static final String OUTCOME_FAILED = "failed";
    public static final String OUTCOME_DISABLED = "disabled";
    /** The query ran and its results were shown, but its history row could not be saved. */
    public static final String OUTCOME_HISTORY_FAILED = "history_failed";

    /** Supplies the connection a validated query runs on: the reporting account in production. */
    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection(Properties properties) throws SQLException;
    }

    /**
     * Turns submitted SQL into {@link LegacyJdbcQuery.TrustedSql}. Production uses
     * {@link QueryByExampleSqlValidator#validate}; the integration tests substitute a validator
     * without the restricted-table list to show the database boundary holds on its own.
     */
    @FunctionalInterface
    interface QueryValidator {
        LegacyJdbcQuery.TrustedSql validate(String sql, Properties properties) throws SQLException;
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
    private final QueryValidator queryValidator;

    /** Runs queries as the reporting account configured in carlos.properties. */
    public RptByExampleData() {
        this(new ReportingConnectionService()::openConnection);
    }

    RptByExampleData(ConnectionProvider connectionProvider) {
        this(connectionProvider, QueryByExampleSqlValidator::validate);
    }

    RptByExampleData(ConnectionProvider connectionProvider, QueryValidator queryValidator) {
        this.connectionProvider = connectionProvider;
        this.queryValidator = queryValidator;
    }

    /**
     * Validates and executes one bounded, read-only Query-by-Example submission.
     * The outcome and elapsed time are logged at INFO in all cases; raw SQL text is not
     * included in the audit event. The audit-table row of the run is the caller's to write
     * through {@link #auditRow}, once the run's final outcome is known.
     *
     * @param sql request-submitted SQL; only one validated {@code SELECT} is permitted
     * @param properties application properties used to resolve the allowed database schema
     *        and the reporting account
     * @param providerNo requesting provider identifier used for audit metadata
     * @return the encoded, size-bounded query result
     * @throws QueryByExampleValidationException if the SQL fails structural validation
     * @throws ReportingAccountUnavailableException if the reporting account is not provisioned,
     *         is refused, or holds more than reportable {@code SELECT}; the query is not run
     * @throws QueryByExampleAccessDeniedException if the database refuses the query because it
     *         reads a table or column the reporting account is not granted
     * @throws SQLTimeoutException if execution exceeds {@link #QUERY_TIMEOUT_SECONDS}
     * @throws SQLException if the query or JDBC resource handling fails
     * @throws RuntimeException if an unexpected validation, execution, or rendering failure occurs
     */
    @SuppressFBWarnings(
            value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
            justification = "Runtime failures are audited and handled by the action")
    public QueryResult execute(String sql, Properties properties, String providerNo) throws SQLException {
        long startedAt = System.nanoTime();
        String outcome = OUTCOME_FAILED;
        ExecutionProgress progress = new ExecutionProgress();
        try {
            LegacyJdbcQuery.TrustedSql trustedSql = queryValidator.validate(sql, properties);
            RptResultStruct.StructuredResult structured = executeWithConnection(trustedSql, properties, progress);
            outcome = OUTCOME_SUCCESS;
            return new QueryResult(structured.html(), structured.rowCount(), structured.truncated(),
                    structured.rowLimitReached(), elapsedMillis(startedAt));
        } catch (QueryByExampleValidationException e) {
            outcome = OUTCOME_REJECTED;
            throw e;
        } catch (ReportingAccountUnavailableException e) {
            outcome = OUTCOME_UNAVAILABLE;
            logUnavailable(providerNo, e.getMessage());
            throw e;
        } catch (SQLTimeoutException e) {
            outcome = OUTCOME_TIMEOUT;
            throw e;
        } catch (SQLException e) {
            if (QueryByExampleAccessDeniedException.isAccessDenied(e)) {
                // The database boundary refused what the application validator allowed: a restricted
                // table or column, or a table nobody has classified yet. Worth a warning, not a stack.
                outcome = OUTCOME_DENIED;
                logDenied(providerNo, sql, e.getErrorCode());
                throw new QueryByExampleAccessDeniedException(e);
            }
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

    private RptResultStruct.StructuredResult executeWithConnection(LegacyJdbcQuery.TrustedSql trustedSql,
            Properties properties, ExecutionProgress progress)
            throws SQLException {
        try (Connection connection = connectionProvider.getConnection(properties)) {
            return executeValidatedQuery(connection, trustedSql, progress);
        }
    }

    private static RptResultStruct.StructuredResult executeValidatedQuery(Connection connection,
            LegacyJdbcQuery.TrustedSql trustedSql, ExecutionProgress progress) throws SQLException {
        boolean originalReadOnly = connection.isReadOnly();
        try {
            connection.setReadOnly(true);
        } catch (SQLException | RuntimeException setupFailure) {
            restoreReadOnlyAfterFailure(connection, originalReadOnly, setupFailure);
            throw setupFailure;
        }
        RptResultStruct.StructuredResult result;
        try {
            result = executeStatement(connection, trustedSql, progress);
        } catch (SQLException | RuntimeException executionFailure) {
            restoreReadOnlyAfterFailure(connection, originalReadOnly, executionFailure);
            throw executionFailure;
        }
        connection.setReadOnly(originalReadOnly);
        return result;
    }

    private static RptResultStruct.StructuredResult executeStatement(Connection connection,
            LegacyJdbcQuery.TrustedSql trustedSql, ExecutionProgress progress) throws SQLException {
        try (PreparedStatement statement = prepareValidatedStatement(connection, trustedSql)) {
            statement.setMaxRows(MAX_ROWS + 1);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            return readStatementResult(statement, progress);
        }
    }

    private static RptResultStruct.StructuredResult readStatementResult(PreparedStatement statement,
            ExecutionProgress progress)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            RptResultStruct.StructuredResult structured = RptResultStruct.getStructureWithCount(
                    resultSet, MAX_OUTPUT_CHARACTERS, MAX_ROWS);
            progress.recordRows(structured.rowCount());
            return structured;
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

    /**
     * Logs one outcome at INFO. Diagnostics, not the audit trail: the packaged logging
     * configuration discards INFO, so the record that is kept is the row {@link #auditRow} writes.
     * The line keeps its historical shape for tooling that reads it.
     */
    public static void audit(String providerNo, String sql, long durationMillis, int rowCount, String outcome) {
        String query = sql == null ? "" : sql;
        String queryHash = queryHash(query);
        MiscUtils.getLogger().info(
                "Query-by-Example audit provider={} queryHash={} queryLength={} durationMs={} rowCount={} outcome={}",
                providerNo, queryHash, query.length(), durationMillis, rowCount, outcome);
    }

    /**
     * The {@code data} of a run's audit row: the metadata of the INFO line, in the same
     * {@code key=value} form, without the provider (the row has a column of its own for it).
     * The SHA-256 hash stands for the query; neither the SQL text nor any result value is copied.
     *
     * @return {@code queryHash=<64 hex> queryLength=<n> durationMs=<n> rowCount=<n> outcome=<word>}
     */
    public static String auditData(String sql, long durationMillis, int rowCount, String outcome) {
        String query = sql == null ? "" : sql;
        return "queryHash=" + queryHash(query) + " queryLength=" + query.length() + " durationMs=" + durationMillis
                + " rowCount=" + rowCount + " outcome=" + outcome;
    }

    /**
     * Writes the audit-table row of one run: action {@code read}, content
     * {@code query_by_example}, the signed-in user's provider, security id and client address,
     * no content id and no patient, and {@link #auditData} as its data.
     *
     * <p>Transaction boundary: the query itself runs on the reporting account's own JDBC
     * connection, outside any Spring transaction, so there is nothing for the row to join; it is
     * still written through {@link LogAction#addLogSynchronousInNewTransaction(LoggedInInfo,
     * String, String, String, String, String)} so that it is committed on its own before the page
     * is answered and survives a rollback of anything a caller may have open. A failure to write
     * it is logged and never changes what the user is shown.</p>
     */
    public static void auditRow(LoggedInInfo loggedInInfo, String sql, long durationMillis, int rowCount,
            String outcome) {
        try {
            LogAction.addLogSynchronousInNewTransaction(loggedInInfo, LogConst.READ, LogConst.CON_QUERY_BY_EXAMPLE,
                    null, null, auditData(sql, durationMillis, rowCount, outcome));
        } catch (RuntimeException e) {
            // The exception type only: a persistence message can echo the row being written.
            MiscUtils.getLogger().error("Query-by-Example audit row not written outcome={} exceptionType={}",
                    LogSafe.sanitize(outcome), e.getClass().getSimpleName());
        }
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

    // The reason names a setting or a kind of grant, never a credential (see ReportingConnectionService).
    private static void logUnavailable(String providerNo, String reason) {
        if (MiscUtils.getLogger().isWarnEnabled()) {
            MiscUtils.getLogger().warn(
                    "Query-by-Example unavailable provider={} reason={}; see docs/query-by-example-least-privilege.md",
                    LogSafe.sanitize(providerNo), LogSafe.sanitize(reason));
        }
    }

    private static void logDenied(String providerNo, String sql, int errorCode) {
        if (MiscUtils.getLogger().isWarnEnabled()) {
            MiscUtils.getLogger().warn(
                    "Query-by-Example refused by reporting account privileges provider={} queryHash={} errorCode={}",
                    LogSafe.sanitize(providerNo), queryHash(sql), errorCode);
        }
    }

    private static String queryHash(String sql) {
        return ContentDigests.sha256Hex(sql == null ? "" : sql);
    }
}
