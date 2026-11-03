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
    @DisplayName("should suppress oversized sequenced CSV export payloads")
    void shouldSuppressOversizedCsvExportPayloads_forSequencedReport() throws Exception {
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
        java.sql.Statement statement = mock(java.sql.Statement.class);
        when(resultSet.getStatement()).thenReturn(statement);
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

            assertThat(generated).isFalse();
            verify(reportManagers.constructed().get(0)).getReportTemplateNoParam("1");
            verify(request).setAttribute("csv-0", "");
            verify(request).setAttribute("errormsg", SQLReporter.CSV_EXPORT_LIMIT_MESSAGE);
            verify(statement).cancel();
            verify(resultSet).close();
            verify(report, never()).buildParameterizedSql(1, parameters);
        }
    }
    @Test
    @DisplayName("should retain result-page navigation for an ordinary SQL refusal")
    void shouldKeepResultPageNavigation_whenStoredSqlIsRejected() throws Exception {
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
                    .thenThrow(new java.sql.SQLException("Only SELECT statements are allowed"));
            assertThat(new SQLReporter().generateReport(request)).isTrue();
            assertThat(request.getAttribute("reportobject")).isSameAs(report);
            assertThat(request.getAttribute("csv")).isEqualTo("");
            assertThat(request.getAttribute("resultsethtml").toString()).contains("Only SELECT statements are allowed");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
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
                    .thenReturn(rs).thenThrow(new java.sql.SQLException("synthetic query failure"));
            assertThat(new SQLReporter().generateReport(request)).isFalse();
            assertThat(request.getAttribute("csv-0")).isNull();
            assertThat(request.getAttribute("sql-0")).isNull();
            assertThat(request.getAttribute("resultsethtml-0")).isNull();
            assertThat(request.getAttribute("errormsg").toString())
                    .contains(missingParameters ? "Cannot find all parameters" : "synthetic query failure");
            verify(report, never()).buildParameterizedSql(2, parameters);
            verify(rs).close();
        }
    }

}
