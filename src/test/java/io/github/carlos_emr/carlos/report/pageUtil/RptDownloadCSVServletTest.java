/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.data.RptReportConfigData;
import io.github.carlos_emr.carlos.report.data.RptReportCreator;
import io.github.carlos_emr.carlos.report.data.RptReportItem;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Regression tests for demographic SQL construction and the report CSV servlet response contract.
 *
 * <p>These tests target the empty-demographic-filter combinations fixed in PR #1639 and
 * verify the generated SQL shape and bind-parameter ordering without requiring a database.
 * They invoke the private servlet method via reflection and capture
 * {@link ConfiguredReportQuery#read(String, Object...)} calls with a static mock.</p>
 *
 * <p>Public {@code service()} tests also verify that query failures return HTTP 500 and
 * UTF-8 payloads exceeding the export byte budget return HTTP 413 without a partial download,
 * overlapping requests retain their own attachment filenames, and buffered write failures
 * propagate while releasing the output stream.</p>
 *
 * @since 2026-04-20
 */
@Tag("unit")
@Tag("report")
class RptDownloadCSVServletTest {

    private static final Pattern MALFORMED_WHERE_CLAUSE_PATTERN =
            Pattern.compile("(?i)\\band\\s*(group by|order by|$)");

    private static Method demoReportMethod;


    private MockedStatic<ConfiguredReportQuery> configuredQueryMock;

    @BeforeAll
    static void setUpReflection() throws NoSuchMethodException {
        demoReportMethod = RptDownloadCSVServlet.class.getDeclaredMethod("demoReport", jakarta.servlet.http.HttpServletRequest.class);
        demoReportMethod.setAccessible(true);
    }

    @BeforeEach
    void setUp() {
        configuredQueryMock = mockStatic(ConfiguredReportQuery.class);
    }

    @AfterEach
    void tearDown() {
        if (configuredQueryMock != null) {
            configuredQueryMock.close();
            configuredQueryMock = null;
        }
    }

    @Test
    @DisplayName("should avoid stray conjunctions for demographicExt filter only when demographic filter is empty")
    void shouldAvoidStrayConjunctions_forDemographicExtFilterOnly() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.addParameter("last_name", "on");
        request.addParameter("prefer_language", "on");
        addFilter(request, 1, "demographicExt.prefer_language='${language}'", "language", "French");

        List<SqlCall> sqlCalls = invokeDemoReport(request);

        SqlCall subQuery = findCallContaining(
                sqlCalls,
                "from demographicExt, demographic where demographic.demographic_no=demographicExt.demographic_no");

        assertSqlShape(subQuery);
        assertThat(subQuery.params()).containsExactly("French");
    }

    @Test
    @DisplayName("should avoid stray conjunctions for AR filter only when demographic and spec filters are empty")
    void shouldAvoidStrayConjunctions_forArFilterOnly() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.addParameter("last_name", "on");
        request.addParameter("c_EDD", "on");
        addFilter(request, 1, "formBCAR.c_EDD>='${edd}'", "edd", "2026-01-01");

        List<SqlCall> sqlCalls = invokeDemoReport(request);

        SqlCall subQuery = findCallContaining(
                sqlCalls,
                "select max(ID) from formBCAR, demographic where demographic.demographic_no=formBCAR.demographic_no");

        assertSqlShape(subQuery);
        assertThat(subQuery.params()).containsExactly("2026-01-01");
    }

    @Test
    @DisplayName("should avoid stray conjunctions for demographicExt and AR filters when demographic filter is empty")
    void shouldAvoidStrayConjunctions_forDemographicExtAndArFiltersOnly() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.addParameter("last_name", "on");
        request.addParameter("prefer_language", "on");
        request.addParameter("c_EDD", "on");
        addFilter(request, 1, "demographicExt.prefer_language='${language}'", "language", "French");
        addFilter(request, 2, "formBCAR.c_EDD>='${edd}'", "edd", "2026-01-01");

        List<SqlCall> sqlCalls = invokeDemoReport(request);

        SqlCall specSubQuery = findCallContaining(
                sqlCalls,
                "from demographicExt, demographic where demographic.demographic_no=demographicExt.demographic_no");
        SqlCall arSubQuery = findCallContaining(
                sqlCalls,
                "select max(ID) from formBCAR, demographic where demographic.demographic_no=formBCAR.demographic_no");

        assertSqlShape(specSubQuery);
        assertSqlShape(arSubQuery);
        assertThat(specSubQuery.params()).containsExactly("French");
        assertThat(arSubQuery.params()).containsExactly("2026-01-01");
    }

    @Test
    @DisplayName("should preserve baseline demographic-only filter SQL and param binding")
    void shouldPreserveBaseline_whenDemographicFilterOnly() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.addParameter("last_name", "on");
        addFilter(request, 1, "demographic.last_name='${lastName}'", "lastName", "Smith");

        List<SqlCall> sqlCalls = invokeDemoReport(request);

        SqlCall query = findCallContaining(sqlCalls, "select demographic.last_name from demographic where");

        assertSqlShape(query);
        assertThat(query.params()).containsExactly("Smith");
    }

    @Test
    @DisplayName("should preserve baseline demographic and demographicExt filter binding order")
    void shouldPreserveBindingOrder_whenDemographicAndDemographicExtFiltersPresent() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.addParameter("last_name", "on");
        request.addParameter("prefer_language", "on");
        addFilter(request, 1, "demographic.last_name='${lastName}'", "lastName", "Smith");
        addFilter(request, 2, "demographicExt.prefer_language='${language}'", "language", "French");

        List<SqlCall> sqlCalls = invokeDemoReport(request);

        SqlCall subQuery = findCallContaining(
                sqlCalls,
                "from demographicExt, demographic where demographic.demographic_no=demographicExt.demographic_no");

        assertSqlShape(subQuery);
        assertThat(subQuery.params()).containsExactly("Smith", "French");
    }

    @ParameterizedTest
    @EnumSource(CsvScenario.class)
    void shouldPreserveSelectedColumnsAndCohorts_acrossCsvQueryBranches(CsvScenario scenario) throws Exception {
        MockHttpServletRequest request = branchRequest(scenario);
        List<SqlCall> calls = new ArrayList<>();
        Map<String, String> values = Map.of("demographic_no", "41", "last_name", "O'Neil",
                "first_name", "Zoë", "c_EDD", "2026-11-01");
        var row = new ConfiguredReportQuery.Row(values, List.of());
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenAnswer(call -> {
                    calls.add(new SqlCall(call.getArgument(0), extractBindParameters(call.getArguments())));
                    return new ConfiguredReportQuery.Result(List.of(), List.of(row));
                });
        configuredQueryMock.when(() -> ConfiguredReportQuery.identifiers(anyString(), any(Object[].class)))
                .thenAnswer(call -> {
                    calls.add(new SqlCall(call.getArgument(0), extractBindParameters(call.getArguments())));
                    return List.of(41, 42);
                });
        var properties = mock(CarlosProperties.class);
        when(properties.getProperty("demographicExt")).thenReturn("prefer language");
        when(properties.getProperty("demographicExt", "")).thenReturn("prefer language");
        var extensions = mock(DemographicExtDao.class);
        when(extensions.findReportValues(anyString(), anyCollection(), anyBoolean()))
                .thenReturn(List.of(new DemographicExtDao.ReportValue(1, 41, "French")));
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var settings = mockStatic(CarlosProperties.class); var spring = mockStatic(SpringUtils.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            spring.when(() -> SpringUtils.getBean(DemographicExtDao.class)).thenReturn(extensions);
            new RptDownloadCSVServlet().service(request, response);
        }

        assertThat(response.getStatus()).isEqualTo(200);
        String header = "id\tLast Name\tFirst Name" + (scenario.antenatal ? "\tEDD" : "")
                + (scenario.extension ? "\tprefer language" : "");
        String data = "1\tO'Neil\tZoë" + (scenario.antenatal ? "\t2026-11-01" : "")
                + (scenario.extension ? "\tFrench" : "");
        assertThat(response.getContentAsString()).isEqualTo(header + "\r\n" + data);
        assertBranchQueries(scenario, calls);
        if (scenario.extension) {
            List<Integer> cohort = scenario.extensionFilter ? List.of(41, 42) : List.of(41);
            verify(extensions).findReportValues("prefer_language", cohort, scenario != CsvScenario.ALL_SELECTED);
        }
    }

    private MockHttpServletRequest branchRequest(CsvScenario scenario) {
        MockHttpServletRequest request = baseRequest();
        request.getSession(true);
        request.setParameter("demoReport", "true");
        // Demographic projection order follows the legacy fixed list, not request order.
        request.setParameter("first_name", "on");
        request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.last_name='${surname}'", "surname", "O'Neil");
        if (scenario.antenatal) {
            request.setParameter("c_EDD", "on");
            addFilter(request, 2, "formBCAR.c_EDD>='${edd}'", "edd", "2026-11-01");
        }
        if (scenario.extension) {
            request.setParameter("prefer_language", "on");
        }
        if (scenario.extensionFilter) {
            addFilter(request, 3, "demographicExt.prefer_language='${language}'", "language", "French");
        }
        return request;
    }

    private void assertBranchQueries(CsvScenario scenario, List<SqlCall> calls) {
        calls.forEach(this::assertSqlShape);
        if (scenario.extensionFilter) {
            assertThat(calls.getFirst().sql()).startsWith("select distinct(demographic.demographic_no)");
            assertThat(calls.getFirst().params()).containsExactly("O'Neil", "French");
        }
        if (scenario.antenatal) {
            SqlCall ids = findCallContaining(calls, "select max(ID) from formBCAR");
            assertThat(ids.params()).containsExactly("O'Neil", "2026-11-01");
            assertThat(calls.getLast().sql()).contains("formBCAR.ID in (41,42)");
        }
        if (scenario.extensionFilter) {
            assertThat(calls.getLast().sql()).contains("demographic.demographic_no in (41,42)");
        } else if (!scenario.antenatal) {
            assertThat(calls.getLast().params()).containsExactly("O'Neil");
        }
    }

    private enum CsvScenario {
        DEMOGRAPHIC(false, false, false),
        EXTENSION(false, true, false),
        FILTERED_EXTENSION(false, true, true),
        ANTENATAL(true, false, false),
        ALL_SELECTED(true, true, false),
        ALL_FILTERED(true, true, true);

        private final boolean antenatal;
        private final boolean extension;
        private final boolean extensionFilter;

        CsvScenario(boolean antenatal, boolean extension, boolean extensionFilter) {
            this.antenatal = antenatal;
            this.extension = extension;
            this.extensionFilter = extensionFilter;
        }
    }

    @Test
    void shouldReturnFailureInsteadOfEmptyCsv_whenFormReportReadFails() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.getSession(true);
        request.setParameter("id", "5549");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var reports = mockConstruction(
                RptReportItem.class,
                (report, context) -> when(report.getReportName("5549"))
                        .thenThrow(new java.sql.SQLException("synthetic read failure")))) {
            new RptDownloadCSVServlet().service(request, response);
            assertThat(reports.constructed()).hasSize(1);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).isEqualTo("Unable to generate report");
            assertThat(response.getContentAsString()).isEmpty();
            assertThat(request.getAttribute(RptDownloadCSVServlet.REPORT_TOO_LARGE_ATTRIBUTE))
                    .as("a failure is not a report too large").isNull();
        }
    }

    @Test
    void shouldRejectUtf8ByteOversize_whenCharacterCountFits() throws Exception {
        String value = "€".repeat(RptDownloadCSVServlet.MAX_CSV_EXPORT_BYTES / 2);
        var row = new ConfiguredReportQuery.Row(java.util.Map.of("last_name", value), List.of(value));
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(new ConfiguredReportQuery.Result(List.of("last_name"), List.of(row)));
        var request = baseRequest(); request.getSession(true);
        request.setParameter("demoReport", "true"); request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new RptDownloadCSVServlet().service(request, response);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).isEmpty();
        // The error page reads this marker to say what to do (#5850).
        assertThat(request.getAttribute(RptDownloadCSVServlet.REPORT_TOO_LARGE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"row limit", "deadline"})
    void shouldAnswer413WithWhatToDo_whenTheReportIsRefusedByItsLimit(String refusal) throws Exception {
        // #5850: a row, size or time refusal used to become a 500 "Unable to generate report".
        java.sql.SQLException thrown = "row limit".equals(refusal)
                ? new ConfiguredReportQuery.LimitExceededException("Report row budget exceeded; narrow the report filters")
                : new java.sql.SQLTimeoutException("Report query deadline exceeded");
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenThrow(thrown);
        var request = baseRequest(); request.getSession(true);
        request.setParameter("demoReport", "true"); request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new RptDownloadCSVServlet().service(request, response);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getErrorMessage()).isEqualTo(RptDownloadCSVServlet.LIMIT_REFUSAL_MESSAGE);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(request.getAttribute(RptDownloadCSVServlet.REPORT_TOO_LARGE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
    }

    /** #5850: a form report's refusal arrives wrapped in a ServletException, and still answers 413. */
    @Test
    void shouldAnswer413WithWhatToDo_whenAFormReportIsRefusedByItsLimit() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.getSession(true);
        request.setParameter("id", "5549");
        var response = new org.springframework.mock.web.MockHttpServletResponse();

        try (var reports = mockConstruction(RptReportItem.class, (report, context) ->
                     when(report.getReportName(anyString())).thenReturn("FAKE form report"));
             var queries = mockConstruction(RptFormQuery.class, (query, context) ->
                     when(query.getQueryStr(anyString(), any())).thenReturn(new ParameterizedSql("select 1", List.of())));
             var configs = mockConstruction(RptReportConfigData.class, (config, context) ->
                     when(config.getAllFieldNameValue(anyString(), anyString())).thenReturn(new Vector[]{
                             new Vector<>(List.of("value")), new Vector<>(List.of("value"))}));
             var readers = mockConstruction(RptReportCreator.class, (reader, context) ->
                     when(reader.query(anyString(), any(Vector.class), any(Object[].class))).thenThrow(
                             new ConfiguredReportQuery.LimitExceededException("Report row budget exceeded; narrow the report filters")))) {
            new RptDownloadCSVServlet().service(request, response);
            assertThat(readers.constructed()).hasSize(1);
        }

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getErrorMessage()).isEqualTo(RptDownloadCSVServlet.LIMIT_REFUSAL_MESSAGE);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(request.getAttribute(RptDownloadCSVServlet.REPORT_TOO_LARGE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
    }

    /** A demographic report that fails for another reason is a 500, not a report too large. */
    @Test
    void shouldAnswer500WithoutTheMarker_whenTheDemographicReportFailsOtherwise() throws Exception {
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenThrow(new java.sql.SQLException("synthetic read failure"));
        var request = baseRequest(); request.getSession(true);
        request.setParameter("demoReport", "true"); request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();

        new RptDownloadCSVServlet().service(request, response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).isEqualTo("Unable to generate report");
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(request.getAttribute(RptDownloadCSVServlet.REPORT_TOO_LARGE_ATTRIBUTE))
                .as("a failure is not a report too large").isNull();
    }

    @Test
    void shouldKeepEachDownloadFilename_whenRequestsOverlap() throws Exception {
        RptDownloadCSVServlet servlet = new RptDownloadCSVServlet();
        MockHttpServletRequest first = baseRequest();
        first.getSession(true);
        first.setParameter("id", "1");
        MockHttpServletRequest second = baseRequest();
        second.getSession(true);
        second.setParameter("id", "2");
        var firstResponse = new org.springframework.mock.web.MockHttpServletResponse();
        var secondResponse = new org.springframework.mock.web.MockHttpServletResponse();

        try (var reports = mockConstruction(RptReportItem.class, (report, context) ->
                     when(report.getReportName(anyString())).thenAnswer(call -> "report-" + call.getArgument(0)));
             var queries = mockConstruction(RptFormQuery.class, (query, context) ->
                     when(query.getQueryStr(anyString(), any())).thenAnswer(call ->
                             new ParameterizedSql("select " + call.getArgument(0), List.of())));
             var configs = mockConstruction(RptReportConfigData.class, (config, context) ->
                     when(config.getAllFieldNameValue(anyString(), anyString())).thenReturn(new Vector[]{
                             new Vector<>(List.of("value")), new Vector<>(List.of("value"))}));
             var readers = mockConstruction(RptReportCreator.class, (reader, context) ->
                     when(reader.query(anyString(), any(Vector.class), any(Object[].class))).thenAnswer(call -> {
                         String sql = call.getArgument(0);
                         if ("select 1".equals(sql)) {
                             // Deterministically interleave a second request after the first
                             // resolves its name but before it publishes its response headers.
                             servlet.service(second, secondResponse);
                         }
                         Properties row = new Properties();
                         row.setProperty("value", sql);
                         return new Vector<>(List.of(row));
                     }))) {
            servlet.service(first, firstResponse);
            assertThat(reports.constructed()).hasSize(2);
            assertThat(queries.constructed()).hasSize(2);
            assertThat(configs.constructed()).hasSize(2);
            assertThat(readers.constructed()).hasSize(2);
        }

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
        for (var response : List.of(firstResponse, secondResponse)) {
            assertThat(response.getContentType()).isEqualTo("text/csv; charset=UTF-8");
            assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        }
        assertThat(firstResponse.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"report-1.csv\"");
        assertThat(secondResponse.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"report-2.csv\"");
        assertThat(firstResponse.getContentAsString()).contains("select 1").doesNotContain("select 2");
        assertThat(secondResponse.getContentAsString()).contains("select 2").doesNotContain("select 1");
    }

    @Test
    void shouldPropagateWriteFailure_whenClosingBufferedDownload() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.getSession(true);
        request.setParameter("demoReport", "true");
        var output = mock(jakarta.servlet.ServletOutputStream.class);
        doThrow(new java.io.IOException("synthetic output failure"))
                .when(output).write(any(byte[].class), anyInt(), anyInt());
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(output);

        assertThatThrownBy(() -> new RptDownloadCSVServlet().service(request, response))
                .isInstanceOf(java.io.IOException.class)
                .hasMessage("synthetic output failure");
        verify(output).close();
    }

    private MockHttpServletRequest baseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        return request;
    }

    private void addFilter(
            MockHttpServletRequest request,
            int serial,
            String expression,
            String variableName,
            String variableValue) {
        request.addParameter("filter_" + serial, "on");
        request.addParameter("value_" + serial, expression);
        request.addParameter("dateFormat_" + serial, "");
        request.addParameter(variableName, variableValue);
    }

    private List<SqlCall> invokeDemoReport(MockHttpServletRequest request) throws Exception {
        List<SqlCall> sqlCalls = new ArrayList<>();
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenAnswer(invocation -> captureConfiguredQueryCall(sqlCalls, invocation));
        configuredQueryMock.when(() -> ConfiguredReportQuery.identifiers(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenAnswer(invocation -> {
                    sqlCalls.add(new SqlCall(invocation.getArgument(0), extractBindParameters(invocation.getArguments())));
                    return List.of();
                });

        try {
            demoReportMethod.invoke(new RptDownloadCSVServlet(), request);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }

        return sqlCalls;
    }

    private ConfiguredReportQuery.Result captureConfiguredQueryCall(List<SqlCall> sqlCalls, InvocationOnMock invocation) {
        sqlCalls.add(new SqlCall(
                invocation.getArgument(0),
                extractBindParameters(invocation.getArguments())));
        return new ConfiguredReportQuery.Result(List.of(), List.of());
    }

    private List<Object> extractBindParameters(Object[] invocationArgs) {
        if (invocationArgs.length < 2 || invocationArgs[1] == null) {
            return List.of();
        }
        if (invocationArgs[1] instanceof Object[] params) {
            return Arrays.asList(params);
        }
        return Arrays.asList(Arrays.copyOfRange(invocationArgs, 1, invocationArgs.length));
    }

    private SqlCall findCallContaining(List<SqlCall> calls, String fragment) {
        Optional<SqlCall> match = calls.stream()
                .filter(call -> call.sql().contains(fragment))
                .findFirst();
        assertThat(match)
                .as("Expected SQL call containing fragment: %s\nCalls were: %s", fragment, calls)
                .isPresent();
        return match.get();
    }

    private void assertSqlShape(SqlCall sqlCall) {
        assertThat(sqlCall.sql()).doesNotContain("and  and");
        assertThat(sqlCall.sql()).doesNotContain("where  and");
        assertThat(MALFORMED_WHERE_CLAUSE_PATTERN.matcher(sqlCall.sql()).find()).isFalse();
        assertThat(countPlaceholders(sqlCall.sql())).isEqualTo(sqlCall.params().size());
    }

    private int countPlaceholders(String sql) {
        return (int) sql.chars().filter(ch -> ch == '?').count();
    }

    private record SqlCall(String sql, List<Object> params) {
    }
}
