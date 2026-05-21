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
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.db.DBHandler;
import io.github.carlos_emr.carlos.report.data.RptResultStruct;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.UtilMisc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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
        when(report.getParameterizedSQL(parameters)).thenReturn(new String[]{"select 1"});

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.isBeforeFirst()).thenReturn(false);

        try (MockedConstruction<ReportManager> reportManagers = mockConstruction(ReportManager.class,
                (mock, context) -> {
                    when(mock.getReportTemplateNoParam("1")).thenReturn(report);
                });
             MockedStatic<DBHandler> dbHandler = mockStatic(DBHandler.class)) {
            dbHandler.when(() -> DBHandler.GetPreSQL(eq("select 1"), any(Object[].class))).thenReturn(resultSet);

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
        when(report.getParameterizedSQL(0, parameters)).thenReturn(new String[]{"select 1"});
        when(report.getParameterizedSQL(1, parameters)).thenReturn(null);

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.isBeforeFirst()).thenReturn(true);

        String oversizedCell = "a".repeat(SQLReporter.MAX_CSV_EXPORT_LENGTH + 1);
        try (MockedConstruction<ReportManager> reportManagers = mockConstruction(ReportManager.class,
                (mock, context) -> {
                    when(mock.getReportTemplateNoParam("1")).thenReturn(report);
                });
             MockedStatic<DBHandler> dbHandler = mockStatic(DBHandler.class);
             MockedStatic<RptResultStruct> resultStruct = mockStatic(RptResultStruct.class);
             MockedStatic<UtilMisc> utilMisc = mockStatic(UtilMisc.class)) {
            dbHandler.when(() -> DBHandler.GetPreSQL(eq("select 1"), any(Object[].class))).thenReturn(resultSet);
            resultStruct.when(() -> RptResultStruct.getStructure2(resultSet)).thenReturn("<table></table>");
            utilMisc.when(() -> UtilMisc.getArrayFromResultSet(resultSet)).thenReturn(new String[][]{{oversizedCell}});

            boolean generated = new SQLReporter().generateReport(request);

            assertThat(generated).isTrue();
            verify(reportManagers.constructed().get(0)).getReportTemplateNoParam("1");
            verify(request).setAttribute("csv-0", "");
            verify(request).setAttribute(eq("errormsg"), contains("too large to download as CSV"));
        }
    }
}
