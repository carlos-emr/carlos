/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** What the shared error page is told about a configured report that a limit stopped. */
@Tag("unit")
@Tag("report")
@DisplayName("Report limit notice")
class ReportLimitNoticeUnitTest {

    @Test
    @DisplayName("should publish the limit, its value and its setting for a wrapped refusal")
    void shouldPublishLimitValueAndSetting_whenFailureWrapsARefusal() {
        var request = new MockHttpServletRequest();
        var refusal = new ConfiguredReportQuery.LimitExceededException(ConfiguredReportQuery.Limit.IDENTIFIERS, 100_000);
        var failure = new IllegalStateException("Clinical report could not be completed; narrow the filters or retry",
                new jakarta.servlet.ServletException("Unable to generate form report", refusal));

        assertThat(ReportLimitNotice.publish(request, failure)).isTrue();

        assertThat(request.getAttribute(ReportLimitNotice.KEY_ATTRIBUTE)).isEqualTo("report.configured.limitIdentifiers");
        assertThat(request.getAttribute(ReportLimitNotice.VALUE_ATTRIBUTE)).isEqualTo(100_000);
        assertThat(request.getAttribute(ReportLimitNotice.SETTING_ATTRIBUTE))
                .isEqualTo("configured_report.max_identifier_rows");
    }

    @Test
    @DisplayName("should leave out the setting when the limit is already at its maximum")
    void shouldOmitSetting_whenLimitIsAtItsMaximum() {
        var request = new MockHttpServletRequest();
        var refusal = new ConfiguredReportQuery.LimitExceededException(ConfiguredReportQuery.Limit.TIME,
                ConfiguredReportQuery.TIMEOUT_SECONDS_CEILING, new java.sql.SQLTimeoutException("deadline"));

        assertThat(ReportLimitNotice.publish(request, refusal)).isTrue();

        assertThat(request.getAttribute(ReportLimitNotice.KEY_ATTRIBUTE)).isEqualTo("report.configured.limitTime");
        assertThat(request.getAttribute(ReportLimitNotice.VALUE_ATTRIBUTE)).isEqualTo(300);
        assertThat(request.getAttribute(ReportLimitNotice.SETTING_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should publish nothing for a failure that is not a limit refusal")
    void shouldPublishNothing_whenFailureIsNotARefusal() {
        var request = new MockHttpServletRequest();

        assertThat(ReportLimitNotice.publish(request, new IllegalStateException("failed", new SQLException("connection refused"))))
                .isFalse();
        assertThat(ReportLimitNotice.publish(request, (Throwable) null)).isFalse();
        assertThat(ReportLimitNotice.publish(null, new ConfiguredReportQuery.LimitExceededException(
                ConfiguredReportQuery.Limit.ROWS, 10_000))).isFalse();

        assertThat(request.getAttributeNames().hasMoreElements()).isFalse();
    }

    @Test
    @DisplayName("should stop at a cause cycle instead of looping")
    void shouldStopSearching_whenCausesFormACycle() {
        var first = new IllegalStateException("first");
        var second = new IllegalStateException("second", first);
        first.initCause(second);

        assertThat((Object) ReportLimitNotice.find(first)).isNull();
    }
}
