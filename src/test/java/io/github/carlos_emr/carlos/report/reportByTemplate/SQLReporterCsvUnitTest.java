/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.sql.Types;
import java.util.List;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("Report template spreadsheet CSV exports")
class SQLReporterCsvUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldGuardHeadersAndData_whenGeneratingNormalOrSequencedReport(boolean sequenced) throws Exception {
        String[] headers = {"=1+1", "+header", "-header", "@header", "  =header", "\tlabel",
                "quoted,\"header\"", "multiline\r\nheader", "amount", "scientific", "nullable", "spaced amount"};
        String[] values = {"=1+1", "+1+1", "-cmd", "@SUM(A1)", "\u00A0=1+1", "\r\ntext",
                "=HYPERLINK(\"synthetic\",\"quoted,\r\nvalue\")", "Zoë,\"text\"\r\nnext", "-12.50", "-1.2E-7", null, "  -12.50"};
        ReportTemplatesDao dao = mock(ReportTemplatesDao.class);
        registerMock(ReportTemplatesDao.class, dao);
        ReportTemplates template = new ReportTemplates();
        template.setActive(1);
        when(dao.find(1)).thenReturn(template);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("templateId", "1");
        ReportObjectGeneric report = mock(ReportObjectGeneric.class);
        when(report.isSequence()).thenReturn(sequenced);
        ParameterizedSql query = new ParameterizedSql("select synthetic", List.of());
        when(report.buildParameterizedSql(any())).thenReturn(query);
        when(report.buildParameterizedSql(0, request.getParameterMap())).thenReturn(query);
        when(report.buildParameterizedSql(1, request.getParameterMap())).thenReturn(null);

        try (CachedRowSet rows = syntheticRows(headers, values);
             MockedConstruction<ReportManager> managers = mockConstruction(ReportManager.class,
                     (manager, context) -> when(manager.getReportTemplateNoParam("1")).thenReturn(report));
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), org.mockito.ArgumentMatchers.eq(10_000), org.mockito.ArgumentMatchers.eq(30), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(rows);

            assertThat(new SQLReporter().generateReport(request)).isTrue();
            String csv = (String) request.getAttribute(sequenced ? "csv-0" : "csv");
            assertThat(csv).startsWith("\"\t=1+1\",\"\t+header\"").endsWith("\r\n");
            try (CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT)) {
                var records = parser.getRecords();
                assertThat(records).hasSize(2);
                assertThat(records.get(0).toList()).containsExactly("\t=1+1", "\t+header", "\t-header",
                        "\t@header", "\t  =header", "\t\tlabel", headers[6], headers[7], "amount", "scientific", "nullable", "spaced amount");
                assertThat(records.get(1).toList()).containsExactly("\t=1+1", "\t+1+1", "\t-cmd",
                        "\t@SUM(A1)", "\t\u00A0=1+1", "\t\r\ntext", "\t" + values[6], values[7], "-12.50", "-1.2E-7", "", "  -12.50");
            }
            // CSV guards must not leak into the HTML representation of the report.
            String html = (String) request.getAttribute(sequenced ? "resultsethtml-0" : "resultsethtml");
            assertThat(html).contains("<td>=1+1</td>").doesNotContain("<td>\t=1+1</td>");
        }
    }

    private static CachedRowSet syntheticRows(String[] headers, String[] values) throws Exception {
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(headers.length);
        for (int i = 0; i < headers.length; i++) {
            metadata.setColumnName(i + 1, headers[i]);
            metadata.setColumnLabel(i + 1, headers[i]);
            metadata.setColumnType(i + 1, Types.VARCHAR);
        }
        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                rows.updateNull(i + 1);
            } else {
                rows.updateString(i + 1, values[i]);
            }
        }
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        return rows;
    }
}
