/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.lang.management.ManagementFactory;
import java.sql.ResultSet;
import java.sql.SQLTimeoutException;
import java.util.List;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Standalone, small-heap probe; requires a disposable MariaDB database with sequence tables. */
public final class ReportTemplateBudgetProbe {
    private ReportTemplateBudgetProbe() {
    }

    public static void main(String[] args) throws Exception {
        String scenario = args[0];
        MysqlDataSource source = new MysqlDataSource();
        source.setURL(System.getenv("REPORT_PROBE_JDBC_URL"));
        source.setUser(System.getenv("REPORT_PROBE_USER"));
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
            case "stream-timeout" -> "SELECT seq, SLEEP(0.02) AS slow FROM seq_1_to_1000";
            default -> "SELECT seq, REPEAT('é', 64) AS sample FROM seq_1_to_" + Integer.parseInt(scenario);
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
                ReportResultBudget.MAX_ROWS + 1, scenario.contains("timeout") ? 1 : ReportResultBudget.TIMEOUT_SECONDS)) {
            try {
                String[] output = budget.render(rs, null);
                outcome = "complete html=" + output[0].length() + " csv=" + output[1].length();
            } catch (ReportResultBudget.LimitExceededException e) {
                rs.getStatement().cancel();
                outcome = "limited: " + e.getMessage();
            }
        } catch (SQLTimeoutException e) {
            outcome = "timed out";
        } finally {
            LegacyJdbcQuery.releaseThreadResources();
        }
        long peak = heapPools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        long millis = (System.nanoTime() - started) / 1_000_000;
        System.out.printf("scenario=%s duration_ms=%d baseline_heap=%d peak_heap=%d peak_delta=%d outcome=%s%n",
                scenario, millis, baseline, peak, peak - baseline, outcome);
        if (scenario.contains("timeout") && (!outcome.equals("timed out") || millis > 4000)) {
            throw new AssertionError("Expected cancellation within four seconds");
        }
        if (scenario.equals("100000") && !outcome.startsWith("limited:")) {
            throw new AssertionError("Expected row budget exhaustion");
        }
    }
}
