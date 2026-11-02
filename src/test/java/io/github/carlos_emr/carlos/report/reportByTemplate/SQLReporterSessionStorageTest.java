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
import io.github.carlos_emr.carlos.report.data.RptResultStruct;
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
        when(resultSet.isBeforeFirst()).thenReturn(false);
        when(resultSet.getMetaData()).thenReturn(mock(ResultSetMetaData.class));

        try (MockedConstruction<ReportManager> reportManagers = mockConstruction(ReportManager.class,
                (mock, context) -> {
                    when(mock.getReportTemplateNoParam("1")).thenReturn(report);
                });
             MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class)) {
            legacyJdbcQuery.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), org.mockito.ArgumentMatchers.eq(10_000), org.mockito.ArgumentMatchers.eq(30), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(resultSet);

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

        // Stay below the result character budget and exceed only the UTF-8 CSV byte cap.
        String oversizedCell = "€".repeat(SQLReporter.MAX_CSV_EXPORT_LENGTH / 2);
        var metadata = new javax.sql.rowset.RowSetMetaDataImpl();
        metadata.setColumnCount(1);
        metadata.setColumnName(1, "synthetic");
        metadata.setColumnLabel(1, "synthetic");
        metadata.setColumnType(1, java.sql.Types.VARCHAR);
        var resultSet = javax.sql.rowset.RowSetProvider.newFactory().createCachedRowSet();
        resultSet.setMetaData(metadata);
        resultSet.moveToInsertRow();
        resultSet.updateString(1, oversizedCell);
        resultSet.insertRow();
        resultSet.moveToCurrentRow();
        resultSet.beforeFirst();
        try (MockedConstruction<ReportManager> reportManagers = mockConstruction(ReportManager.class,
                (mock, context) -> {
                    when(mock.getReportTemplateNoParam("1")).thenReturn(report);
                });
             MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class);
             MockedStatic<RptResultStruct> resultStruct = mockStatic(RptResultStruct.class)) {
            legacyJdbcQuery.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), org.mockito.ArgumentMatchers.eq(10_000), org.mockito.ArgumentMatchers.eq(30), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(resultSet);
            resultStruct.when(() -> RptResultStruct.getStructure2(resultSet)).thenReturn("<table></table>");

            boolean generated = new SQLReporter().generateReport(request);

            assertThat(generated).isTrue();
            verify(reportManagers.constructed().get(0)).getReportTemplateNoParam("1");
            verify(request).setAttribute("csv-0", "");
            verify(request).setAttribute("errormsg", SQLReporter.CSV_EXPORT_LIMIT_MESSAGE);
        }
    }

    @Test
    void shouldKeepQueryFailureSeparate_fromCsvSizeWarning() throws Exception {
        var dao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, dao);
        ReportTemplates template = new ReportTemplates();
        template.setActive(1);
        when(dao.find(1)).thenReturn(template);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("templateId")).thenReturn("1");
        when(request.getParameterMap()).thenReturn(Map.of());
        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.buildParameterizedSql(Map.of())).thenReturn(new ParameterizedSql("select 1", List.of()));
        try (var managers = mockConstruction(ReportManager.class,
                     (manager, context) -> when(manager.getReportTemplateNoParam("1")).thenReturn(report));
             var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_000), eq(30), any(Object[].class)))
                    .thenThrow(new java.sql.SQLException("synthetic query failure"));
            assertThat(new SQLReporter().generateReport(request)).isTrue();
            verify(request).setAttribute("csv", "");
            verify(request, never()).setAttribute("errormsg", SQLReporter.CSV_EXPORT_LIMIT_MESSAGE);
        }
    }

}
