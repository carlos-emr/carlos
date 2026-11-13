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
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for report-by-template CSV state handling.
 */
@DisplayName("SQLReporter CSV session storage")
@Tag("unit")
@Tag("report")
class SQLReporterSessionStorageTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should avoid HttpSession CSV storage for report by template")
    void shouldAvoidHttpSessionCsvStorage_forReportByTemplate() throws Exception {
        ReportTemplatesDao reportTemplatesDao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, reportTemplatesDao);
        ReportTemplates activeTemplate = new ReportTemplates();
        activeTemplate.setActive(1);
        when(reportTemplatesDao.find(1)).thenReturn(activeTemplate);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, String[]> parameters = Map.of();
        when(request.getParameter("templateId")).thenReturn("1");
        when(request.getParameterMap()).thenReturn(parameters);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);

        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.isSequence()).thenReturn(false);
        when(report.buildParameterizedSql(parameters)).thenReturn(new ParameterizedSql("select 1", List.of()));

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);

        try (MockedConstruction<ReportManager> reportManagers = mockConstruction(ReportManager.class,
                (mock, context) -> {
                    when(mock.getReportTemplateNoParam("1")).thenReturn(report);
                });
             MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class)) {
            legacyJdbcQuery.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(ParameterizedSql.class), anyInt(), anyLong())).thenReturn(resultSet);

            boolean generated = new SQLReporter().generateReport(request);

            assertThat(generated).isTrue();
            verify(reportManagers.constructed().get(0)).getReportTemplateNoParam("1");
            verify(request).setAttribute("csv", "");
            verify(session, never()).setAttribute(eq("csv"), any());
        }
    }

    @Test
    @DisplayName("should show the report and hold back an oversized sequenced CSV (#5849, D260)")
    void shouldShowReportAndHoldBackOversizedCsv_forSequencedReport() throws Exception {
        ReportTemplatesDao reportTemplatesDao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, reportTemplatesDao);
        ReportTemplates activeTemplate = new ReportTemplates();
        activeTemplate.setActive(1);
        when(reportTemplatesDao.find(1)).thenReturn(activeTemplate);

        HttpServletRequest request = mock(HttpServletRequest.class);
        Map<String, String[]> parameters = Map.of();
        when(request.getParameter("templateId")).thenReturn("1");
        when(request.getParameterMap()).thenReturn(parameters);

        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.isSequence()).thenReturn(true);
        when(report.buildParameterizedSql(0, parameters)).thenReturn(new ParameterizedSql("select 1", List.of()));
        when(report.buildParameterizedSql(1, parameters)).thenReturn(null);

        ResultSet resultSet = mock(ResultSet.class);
        java.util.concurrent.atomic.AtomicInteger consumedRows = new java.util.concurrent.atomic.AtomicInteger();
        when(resultSet.next()).thenAnswer(ignored -> consumedRows.getAndIncrement() < 81);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(resultSet.getCharacterStream(1)).thenAnswer(ignored -> new java.io.StringReader("a".repeat(64 * 1024)));

        try (MockedConstruction<ReportManager> reportManagers = mockConstruction(ReportManager.class,
                (mock, context) -> {
                    when(mock.getReportTemplateNoParam("1")).thenReturn(report);
                });
             MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class)) {
            legacyJdbcQuery.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(ParameterizedSql.class), anyInt(), anyLong())).thenReturn(resultSet);

            boolean generated = new SQLReporter().generateReport(request);

            assertThat(generated).isTrue();
            verify(reportManagers.constructed().get(0)).getReportTemplateNoParam("1");
            verify(request).setAttribute("csv-0", "");
            // The flag names the query, so the page puts the note on that query's result (#5849).
            verify(request).setAttribute("csvWithheld-0", Boolean.TRUE);
            verify(request, never()).setAttribute(eq("csvWithheld"), any());
            verify(request).setAttribute("csvWithheldMb", 5);
            // The default is below the setting's maximum, so the note says an administrator can raise it.
            verify(request).setAttribute("csvWithheldSetting", "report_by_template.max_csv_mb");
            verify(request).setAttribute(eq("resultsethtml-0"), org.mockito.ArgumentMatchers.startsWith("<table"));
            verify(request, never()).setAttribute(eq("errormsg"), any());
            verify(resultSet).close();
            verify(report).buildParameterizedSql(1, parameters);
        }
    }

    @Test
    @DisplayName("should show a single-query report and hold back its oversized CSV (#5849, D260)")
    void shouldShowReportAndHoldBackOversizedCsv_forSingleQueryReport() throws Exception {
        ReportTemplatesDao dao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, dao);
        ReportTemplates template = new ReportTemplates();
        template.setActive(1);
        when(dao.find(1)).thenReturn(template);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setParameter("templateId", "1");
        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.buildParameterizedSql(request.getParameterMap())).thenReturn(new ParameterizedSql("select 1", List.of()));
        // 17 rows of 64 KiB: about 1.06 MiB of CSV, over a 1 MiB CSV limit, and well under the HTML limit.
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
        when(metadata.getColumnLabel(1)).thenReturn("value");
        java.util.concurrent.atomic.AtomicInteger rows = new java.util.concurrent.atomic.AtomicInteger();
        when(rs.next()).thenAnswer(ignored -> rows.getAndIncrement() < 17);
        when(rs.getCharacterStream(1)).thenAnswer(ignored -> new java.io.StringReader("a".repeat(64 * 1024)));
        var limits = new ReportResultBudget.Limits(10_000, 30, 10 * 1024 * 1024, 256, 1024 * 1024, 65_536);
        try (MockedConstruction<ReportManager> managers = mockConstruction(ReportManager.class,
                (manager, context) -> when(manager.getReportTemplateNoParam("1")).thenReturn(report));
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(ParameterizedSql.class), anyInt(), anyLong()))
                    .thenReturn(rs);
            assertThat(new SQLReporter(limits).generateReport(request)).isTrue();
            assertThat(request.getAttribute("csv")).isEqualTo("");
            assertThat(request.getAttribute("csvWithheld")).isEqualTo(Boolean.TRUE);
            assertThat(request.getAttribute("csvWithheldMb")).isEqualTo(1);
            assertThat(request.getAttribute("csvWithheldSetting")).isEqualTo("report_by_template.max_csv_mb");
            assertThat((String) request.getAttribute("resultsethtml")).startsWith("<table");
            assertThat(request.getAttribute("errormsg")).isNull();
            verify(rs).close();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Only SELECT statements are allowed", "synthetic <query> & failure"})
    @DisplayName("should retain result-page navigation for an ordinary SQL refusal")
    void shouldKeepResultPageNavigation_whenStoredSqlIsRejected(String message) {
        ReportTemplatesDao dao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, dao);
        ReportTemplates template = new ReportTemplates();
        template.setActive(1);
        when(dao.find(1)).thenReturn(template);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setParameter("templateId", "1");
        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.buildParameterizedSql(request.getParameterMap()))
                .thenReturn(new ParameterizedSql("update synthetic", List.of()));
        try (MockedConstruction<ReportManager> managers = mockConstruction(ReportManager.class,
                (manager, context) -> when(manager.getReportTemplateNoParam("1")).thenReturn(report));
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(ParameterizedSql.class), anyInt(), anyLong()))
                    .thenThrow(new java.sql.SQLException(message));
            assertThat(new SQLReporter().generateReport(request)).isTrue();
            verify(managers.constructed().getFirst()).getReportTemplateNoParam("1");
            assertThat(request.getAttribute("reportobject")).isSameAs(report);
            assertThat(request.getAttribute("csv")).isEqualTo("");
            assertThat(request.getAttribute("resultsethtml")).isEqualTo("An SQL query error has occured " + message);
            assertThat(request.getAttribute("errormsg")).isNull();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("should discard earlier subreports and their held-back notes when a later query fails")
    void shouldDiscardEarlierSubreports_whenLaterQueryFails(boolean missingParameters) throws Exception {
        ReportTemplatesDao dao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, dao);
        ReportTemplates template = new ReportTemplates();
        template.setActive(1);
        when(dao.find(1)).thenReturn(template);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setParameter("templateId", "1");
        var parameters = request.getParameterMap();
        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.isSequence()).thenReturn(true);
        when(report.buildParameterizedSql(0, parameters)).thenReturn(new ParameterizedSql("select 1", List.of()));
        when(report.buildParameterizedSql(1, parameters))
                .thenReturn(new ParameterizedSql(missingParameters ? "" : "select 2", List.of()));
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
        when(metadata.getColumnLabel(1)).thenReturn("h");
        when(rs.next()).thenReturn(true, false);
        when(rs.getCharacterStream(1)).thenReturn(new java.io.StringReader("synthetic"));
        try (MockedConstruction<ReportManager> managers = mockConstruction(ReportManager.class,
                (manager, context) -> when(manager.getReportTemplateNoParam("1")).thenReturn(report));
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(ParameterizedSql.class), anyInt(), anyLong()))
                    .thenReturn(rs).thenThrow(new java.sql.SQLException("synthetic <query> & failure"));
            // A 4-byte CSV limit holds back the first query's CSV, so its note must go with it (#5849).
            var limits = new ReportResultBudget.Limits(10_000, 30, 10 * 1024 * 1024, 256, 4, 65_536);
            assertThat(new SQLReporter(limits).generateReport(request)).isFalse();
            assertThat(managers.constructed()).hasSize(2);
            assertThat(request.getAttribute("csv-0")).isNull();
            assertThat(request.getAttribute("csvWithheld-0")).isNull();
            assertThat(request.getAttribute("csvWithheldMb")).isNull();
            assertThat(request.getAttribute("csvWithheldSetting")).isNull();
            assertThat(request.getAttribute("sql-0")).isNull();
            assertThat(request.getAttribute("resultsethtml-0")).isNull();
            assertThat(request.getAttribute("errormsg").toString())
                    .contains(missingParameters ? "Cannot find all parameters" : "synthetic <query> & failure");
            verify(report, never()).buildParameterizedSql(2, parameters);
            verify(rs).close();
        }
    }

    @Test
    void shouldShareRowsAndAbsoluteDeadline_throughSequencedJdbcCalls() throws Exception {
        ReportTemplatesDao dao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, dao);
        ReportTemplates template = new ReportTemplates();
        template.setActive(1);
        when(dao.find(1)).thenReturn(template);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setParameter("templateId", "1");
        var parameters = request.getParameterMap();
        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.buildParameterizedSql(0, parameters)).thenReturn(new ParameterizedSql("select 1", List.of()));
        when(report.buildParameterizedSql(1, parameters)).thenReturn(new ParameterizedSql("select 2", List.of()));
        ResultSet first = sequenceRows(6000);
        ResultSet second = sequenceRows(4001);
        List<Long> deadlines = new java.util.ArrayList<>();
        List<Integer> rowLimits = new java.util.ArrayList<>();
        try (MockedConstruction<ReportManager> managers = mockConstruction(ReportManager.class,
                (manager, context) -> when(manager.getReportTemplateNoParam("1")).thenReturn(report));
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(ParameterizedSql.class), anyInt(), anyLong()))
                    .thenAnswer(call -> {
                        rowLimits.add(call.getArgument(1));
                        deadlines.add(call.getArgument(2));
                        return deadlines.size() == 1 ? first : second;
                    });
            assertThat(new SQLReporter().generateSequencedReport(request)).isFalse();
            assertThat(managers.constructed()).hasSize(1);
            assertThat(rowLimits).containsExactly(10001, 4001);
            assertThat(deadlines).hasSize(2);
            assertThat(deadlines.get(1)).isEqualTo(deadlines.get(0));
            assertThat(request.getAttribute("errormsg"))
                    .isEqualTo(ReportResultBudget.Limit.ROWS.english(ReportResultBudget.MAX_ROWS));
            // #5849 (D262): the page shows the translated message for the limit, with its value.
            assertThat(request.getAttribute("errormsgKey")).isEqualTo("report.byTemplate.limitRows");
            assertThat(request.getAttribute("errormsgArg")).isEqualTo(ReportResultBudget.MAX_ROWS);
            // Below its maximum, so the page also names the setting an administrator can raise.
            assertThat(request.getAttribute("errormsgSetting")).isEqualTo("report_by_template.max_rows");
            assertThat((String) request.getAttribute("errormsg")).endsWith(
                    "An administrator can raise this limit with the setting report_by_template.max_rows in carlos.properties.");
            assertThat(request.getAttribute("resultsethtml-0")).isNull();
            assertThat(request.getAttribute("csv-0")).isNull();
            verify(report, never()).buildParameterizedSql(2, parameters);
            verify(first).close();
            verify(second).close();
        }
    }

    @Test
    @DisplayName("should show the time limit when the JDBC deadline passes (#5849)")
    void shouldShowTheTimeLimit_whenTheQueryDeadlinePasses() throws Exception {
        // The JDBC deadline, before or after a connection is acquired, is the time limit (#5849).
        ReportTemplatesDao dao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, dao);
        ReportTemplates template = new ReportTemplates();
        template.setActive(1);
        when(dao.find(1)).thenReturn(template);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setParameter("templateId", "1");
        var parameters = request.getParameterMap();
        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.buildParameterizedSql(parameters)).thenReturn(new ParameterizedSql("select 1", List.of()));
        var limits = new ReportResultBudget.Limits(10_000, 45, 10 * 1024 * 1024, 256, SQLReporter.MAX_CSV_EXPORT_LENGTH, 65_536);
        try (MockedConstruction<ReportManager> managers = mockConstruction(ReportManager.class,
                (manager, context) -> when(manager.getReportTemplateNoParam("1")).thenReturn(report));
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(ParameterizedSql.class), anyInt(), anyLong()))
                    .thenThrow(new java.sql.SQLTimeoutException("Report query deadline exceeded"));
            assertThat(new SQLReporter(limits).generateReport(request)).isFalse();
            assertThat(request.getAttribute("errormsgKey")).isEqualTo("report.byTemplate.limitTime");
            assertThat(request.getAttribute("errormsgArg")).isEqualTo(45);
            assertThat(request.getAttribute("errormsgSetting")).isEqualTo("report_by_template.timeout_seconds");
            assertThat(request.getAttribute("errormsg")).isEqualTo(ReportResultBudget.Limit.TIME.english(45));
            assertThat(request.getAttribute("csv")).isEqualTo("");
        }
    }

    @Test
    @DisplayName("should show the time limit when the connection pool fails after the deadline (#5849)")
    void shouldShowTheTimeLimit_whenThePoolFailsAfterTheDeadline() throws Exception {
        ReportTemplatesDao dao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, dao);
        ReportTemplates template = new ReportTemplates();
        template.setActive(1);
        when(dao.find(1)).thenReturn(template);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setParameter("templateId", "1");
        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.buildParameterizedSql(request.getParameterMap())).thenReturn(new ParameterizedSql("select 1", List.of()));
        var limits = new ReportResultBudget.Limits(10_000, 15, 10 * 1024 * 1024, 256, SQLReporter.MAX_CSV_EXPORT_LENGTH, 65_536);
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
        try (MockedConstruction<ReportManager> managers = mockConstruction(ReportManager.class,
                (manager, context) -> when(manager.getReportTemplateNoParam("1")).thenReturn(report));
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            // The pool waits longer (20 s by default) than this report's 15 s limit, then gives up.
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(ParameterizedSql.class), anyInt(), anyLong()))
                    .thenAnswer(ignored -> {
                        clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(20));
                        throw new java.sql.SQLException("Unable to acquire legacy JDBC connection");
                    });
            assertThat(new SQLReporter(limits, clock::get).generateReport(request)).isFalse();
            assertThat(request.getAttribute("errormsgKey")).isEqualTo("report.byTemplate.limitTime");
            assertThat(request.getAttribute("errormsgArg")).isEqualTo(15);
            assertThat(request.getAttribute("csv")).isEqualTo("");
            assertThat(request.getAttribute("resultsethtml")).isNull();
        }
    }

    private ResultSet sequenceRows(int count) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.INTEGER);
        when(metadata.getColumnLabel(1)).thenReturn("h");
        when(rs.getString(1)).thenReturn("1");
        java.util.concurrent.atomic.AtomicInteger rows = new java.util.concurrent.atomic.AtomicInteger();
        when(rs.next()).thenAnswer(ignored -> rows.getAndIncrement() < count);
        return rs;
    }

}
