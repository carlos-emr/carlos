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
 * This classes main function FluReportGenerate collects a group of patients with flu in the last specified date
 */
public class RptByExampleData {
    public static final int MAX_ROWS = 1_000;
    public static final int QUERY_TIMEOUT_SECONDS = 15;

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public record QueryResult(String html, int rowCount) {
    }

    private final ConnectionProvider connectionProvider;

    public RptByExampleData() {
        this(LegacyJdbcQuery::getConnection);
    }

    RptByExampleData(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @SuppressFBWarnings(
            value = {
                    "SQL_INJECTION_JDBC",
                    "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING",
                    "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION"
            },
            justification = "Validated dynamic SQL is intentional; runtime failures are audited and handled by the action")
    public QueryResult execute(String sql, Properties properties, String providerNo) throws SQLException {
        long startedAt = System.nanoTime();
        String outcome = "failed";
        int rowCount = 0;
        try {
            LegacyJdbcQuery.TrustedSql trustedSql = QueryByExampleSqlValidator.validate(sql, properties);
            QueryResult queryResult;
            try (Connection connection = connectionProvider.getConnection()) {
                boolean originalReadOnly = connection.isReadOnly();
                Exception executionFailure = null;
                try {
                    connection.setReadOnly(true);
                    // codeql[java/sql-injection] -- TrustedSql is created only after structural SELECT validation.
                    try (PreparedStatement statement = connection.prepareStatement(trustedSql.sql(),
                            ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) { // nosemgrep: java.lang.security.audit.formatted-sql-string-deepsemgrep.formatted-sql-string-deepsemgrep -- validated TrustedSql boundary
                        statement.setMaxRows(MAX_ROWS);
                        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        try (ResultSet resultSet = statement.executeQuery()) { // NOSONAR javasecurity:S3649 -- validated, read-only SELECT boundary
                            RptResultStruct.StructuredResult structured = RptResultStruct.getStructureWithCount(resultSet);
                            rowCount = structured.rowCount();
                            queryResult = new QueryResult(structured.html(), rowCount);
                        }
                    }
                } catch (SQLException | RuntimeException e) {
                    executionFailure = e;
                    throw e;
                } finally {
                    try {
                        connection.setReadOnly(originalReadOnly);
                    } catch (SQLException restoreFailure) {
                        if (executionFailure != null) {
                            executionFailure.addSuppressed(restoreFailure);
                        } else {
                            throw restoreFailure;
                        }
                    }
                }
            }
            outcome = "success";
            return queryResult;
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
            audit(providerNo, sql, elapsedMillis(startedAt), rowCount, outcome);
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
        MiscUtils.getLogger().warn(
                "Query-by-Example failure provider={} queryHash={} sqlState={} exceptionType={}",
                providerNo, queryHash(sql), sqlState, exceptionType);
    }

    private static String queryHash(String sql) {
        return DigestUtils.sha256Hex(sql == null ? "" : sql);
    }
}
