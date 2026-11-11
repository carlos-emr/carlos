/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.data;

import jakarta.servlet.ServletException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A report's row, size or time refusal must be told apart from a failure wherever it is wrapped
 * (#5850): the pages show a refusal as a message, the CSV download answers 413, and only a
 * failure reaches the error page.
 */
@DisplayName("Report limit refusal")
@Tag("unit")
@Tag("report")
class ReportLimitRefusalUnitTest {

    private static final String ROW_LIMIT = "Report row budget exceeded; narrow the report filters";

    @Test
    void shouldRecognizeTheRefusal_whenItIsThrownDirectly() {
        assertThat(ReportLimitRefusal.isRefusal(new ConfiguredReportQuery.LimitExceededException(ROW_LIMIT))).isTrue();
        assertThat(ReportLimitRefusal.isRefusal(new SQLTimeoutException("Report query deadline exceeded"))).isTrue();
    }

    @Test
    void shouldRecognizeTheRefusal_whenACallerWrappedIt() {
        // As the CSV servlet's form report and the clinical denominator and numerator wrap it.
        var servlet = new ServletException("Unable to generate form report",
                new ConfiguredReportQuery.LimitExceededException(ROW_LIMIT));
        var clinical = new IllegalStateException("Clinical report could not be completed; narrow the filters or retry",
                new RuntimeException(new SQLTimeoutException("Report query deadline exceeded")));
        assertThat(ReportLimitRefusal.isRefusal(servlet)).isTrue();
        assertThat(ReportLimitRefusal.isRefusal(clinical)).isTrue();
    }

    @Test
    void shouldNotTreatAFailureAsARefusal_forOtherErrors() {
        assertThat(ReportLimitRefusal.isRefusal(null)).isFalse();
        assertThat(ReportLimitRefusal.isRefusal(new SQLException("synthetic read failure"))).isFalse();
        assertThat(ReportLimitRefusal.isRefusal(new IllegalStateException("Clinical report could not be completed",
                new SQLException("synthetic read failure")))).isFalse();
    }

    @Test
    void shouldStopFollowingCauses_whenTheChainLoops() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);
        assertThat(ReportLimitRefusal.isRefusal(first)).isFalse();
    }
}
