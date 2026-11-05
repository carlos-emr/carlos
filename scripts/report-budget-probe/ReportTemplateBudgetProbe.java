/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.lang.management.ManagementFactory;
import java.sql.ResultSet;
import java.sql.SQLTimeoutException;
import java.util.List;

import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Standalone, small-heap probe; requires a disposable MariaDB database with sequence tables. */
public final class ReportTemplateBudgetProbe {
    private ReportTemplateBudgetProbe() {
    }

    /**
     * Runs one synthetic scenario against a disposable MariaDB schema with sequence tables.
     * Requires REPORT_PROBE_JDBC_URL, REPORT_PROBE_USER and REPORT_PROBE_PASSWORD.
     *
     * @param args one scenario: 10, 10000, 100000, large-cell, timeout or stream-timeout
     * @throws Exception if configuration, JDBC access or rendering fails
     * @throws AssertionError if the expected limit or timely interruption is not observed
     */
    public static void main(String[] args) throws Exception {
        String scenario = args[0];
        BasicDataSource source = new BasicDataSource();
        source.setMaxTotal(1);
        source.setMaxWait(java.time.Duration.ofSeconds(2));
        source.setTestOnBorrow(true);
        source.setUrl(System.getenv("REPORT_PROBE_JDBC_URL"));
        source.setUsername(System.getenv("REPORT_PROBE_USER"));
        source.setPassword(System.getenv("REPORT_PROBE_PASSWORD"));
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("dataSource", source);
        SpringUtils.setBeanFactory(beans);
        // Warm up connection setup separately from measured report work.
        try (var connection = source.getConnection()) {
            if (!connection.isValid(2)) {
                throw new IllegalStateException("Probe database is unavailable");
            }
        }
        String sql = switch (scenario) {
            case "large-cell" -> "SELECT REPEAT('x', 16777216) AS sample";
            case "timeout" -> "SELECT SLEEP(5) AS slow";
            case "stream-timeout" -> "SELECT seq, REPEAT('x', 32768) AS sample, SLEEP(IF(seq = 1, 0, 0.2)) AS slow FROM seq_1_to_1000";
            case "10" -> "SELECT seq, REPEAT('é', 64) AS sample FROM seq_1_to_10";
            case "10000" -> "SELECT seq, REPEAT('é', 64) AS sample FROM seq_1_to_10000";
            case "100000" -> "SELECT seq, REPEAT('é', 64) AS sample FROM seq_1_to_100000";
            default -> throw new IllegalArgumentException("Unsupported probe scenario");
        };
        System.gc();
        long baseline = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        var heapPools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP).toList();
        heapPools.forEach(pool -> pool.resetPeakUsage());
        long started = System.nanoTime();
        String outcome;
        ReportResultBudget budget = new ReportResultBudget();
        try (ResultSet rs = LegacyJdbcQuery.getBoundedReportResultSet(new ParameterizedSql(sql, List.of()),
                ReportResultBudget.MAX_ROWS + 1, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(
                        scenario.contains("timeout") ? 1 : ReportResultBudget.TIMEOUT_SECONDS))) {
            if (scenario.equals("stream-timeout") && System.nanoTime() - started > 500_000_000L) {
                throw new AssertionError("Streaming query must return before the deadline expires");
            }
            try {
                String[] output = budget.render(rs, null);
                outcome = "complete html=" + output[0].length() + " csv=" + output[1].length();
            } catch (ReportResultBudget.LimitExceededException e) {
                outcome = "limited: " + e.getMessage();
            }
        } catch (SQLTimeoutException e) {
            outcome = "timed out";
        } finally {
            LegacyJdbcQuery.releaseThreadResources();
        }
        // A discarded physical connection must leave the one-slot pool usable.
        try (var connection = source.getConnection()) {
            if (!connection.isValid(2)) {
                throw new AssertionError("Pool returned an unusable connection after report cleanup");
            }
        }
        if (source.getNumActive() != 0) {
            throw new AssertionError("Report leaked an active pooled connection");
        }
        source.close();
        long peak = heapPools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        long millis = (System.nanoTime() - started) / 1_000_000;
        System.out.printf("scenario=%s duration_ms=%d baseline_heap=%d peak_heap=%d peak_delta=%d outcome=%s%n",
                scenario, millis, baseline, peak, peak - baseline, outcome);
        if (scenario.contains("timeout") && (!outcome.equals("timed out") || millis > 4000)) {
            throw new AssertionError("Expected cancellation within four seconds");
        }
        if ((scenario.equals("100000") || scenario.equals("large-cell")) && !outcome.startsWith("limited:")) {
            throw new AssertionError("Expected row or cell budget exhaustion");
        }
    }
}
