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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.data.RptReportConfigData;
import io.github.carlos_emr.carlos.report.data.RptReportCreator;
import io.github.carlos_emr.carlos.report.data.RptReportFilter;
import io.github.carlos_emr.carlos.report.data.RptReportItem;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.apache.logging.log4j.Level;

/**
 * Regression tests for demographic SQL construction and the report CSV servlet response contract.
 *
 * <p>These tests target the empty-demographic-filter combinations fixed in PR #1639 and
 * verify the generated SQL shape and bind-parameter ordering without requiring a database.
 * They invoke the private servlet method via reflection and capture
 * {@link ConfiguredReportQuery#read(String, Object...)} calls with a static mock.</p>
 *
 * <p>The report filters are the stored filters of the report ({@link RptReportFilter}, stubbed
 * here from {@link #addFilter}); SQL text posted in {@code value_<n>} is ignored (#5831). The
 * servlet checks {@code _report}/{@code _admin.reporting} read itself, so requests carry a
 * logged-in user, and the security manager is a mock that allows unless a test says otherwise.</p>
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
    private MockedConstruction<RptReportFilter> storedFilterMock;
    /** The report's stored filters, in RptReportFilter#getNameList's six-column shape. */
    private final List<String[]> storedFilters = new ArrayList<>();
    private SecurityInfoManager security;
    private final LoggedInInfo user = new LoggedInInfo();

    @BeforeAll
    static void setUpReflection() throws NoSuchMethodException {
        demoReportMethod = RptDownloadCSVServlet.class.getDeclaredMethod("demoReport", jakarta.servlet.http.HttpServletRequest.class);
        demoReportMethod.setAccessible(true);
    }

    @BeforeEach
    void setUp() {
        configuredQueryMock = mockStatic(ConfiguredReportQuery.class);
        storedFilterMock = mockConstruction(RptReportFilter.class, (filters, context) ->
                when(filters.getNameList(anyString(), anyInt())).thenAnswer(call -> new Vector<>(storedFilters)));
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), eq("_report"), eq("r"), isNull())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (configuredQueryMock != null) {
            configuredQueryMock.close();
            configuredQueryMock = null;
        }
        if (storedFilterMock != null) {
            storedFilterMock.close();
            storedFilterMock = null;
        }
    }

    /** The servlet under test, with the mocked security manager. */
    private RptDownloadCSVServlet servlet() {
        return new RptDownloadCSVServlet(security);
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
        List<SqlCall> extensionCalls = new ArrayList<>();
        Map<String, String> values = Map.of("demographic_no", "41", "last_name", "O'Neil",
                "first_name", "Zoë", "c_EDD", "2026-11-01");
        var row = new ConfiguredReportQuery.Row(values, List.of());
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenAnswer(call -> {
                    String sql = call.getArgument(0);
                    if (sql.startsWith("select x.demographic_no, k.extension_key")) {
                        extensionCalls.add(new SqlCall(sql, extractBindParameters(call.getArguments())));
                        return new ConfiguredReportQuery.Result(List.of(), List.of(extensionRow("41", "prefer_language", "French")));
                    }
                    calls.add(new SqlCall(sql, extractBindParameters(call.getArguments())));
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
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            servlet().service(request, response);
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
            List<Object> parameters = new ArrayList<>(List.of("prefer_language"));
            parameters.addAll(cohort);
            assertThat(extensionCalls).singleElement().satisfies(call ->
                    assertThat(call.params()).containsExactlyElementsOf(parameters));
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"English", "  English  "})
    void shouldIncludeEveryPatientsCurrentExtension_forATwoPatientCohort(String secondValue) throws Exception {
        MockHttpServletRequest request = branchRequest(CsvScenario.EXTENSION);
        var first = new ConfiguredReportQuery.Row(Map.of("demographic_no", "41", "last_name", "FAKE",
                "first_name", "First"), List.of());
        var second = new ConfiguredReportQuery.Row(Map.of("demographic_no", "42", "last_name", "FAKE",
                "first_name", "Second"), List.of());
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenAnswer(call -> new ConfiguredReportQuery.Result(List.of(),
                        ((String) call.getArgument(0)).startsWith("select x.demographic_no, k.extension_key")
                                ? List.of(extensionRow("41", "prefer_language", "French"), extensionRow("42", "prefer_language", secondValue))
                                : List.of(first, second)));
        var properties = mock(CarlosProperties.class);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            servlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(200);
        try (var csv = org.apache.commons.csv.CSVParser.parse(response.getContentAsString(),
                org.apache.commons.csv.CSVFormat.DEFAULT.builder().setDelimiter('\t').get())) {
            var rows = csv.getRecords();
            assertThat(rows).hasSize(3);
            assertThat(rows.get(0).get(3)).isEqualTo("Preferred Language");
            assertThat(rows.get(1).get(3)).isEqualTo("French");
            assertThat(rows.get(2).get(3)).isEqualTo(secondValue == null ? "" : secondValue);
        }
    }

    @Test
    void shouldBindAllSelectedKeys_inOneBoundedExtensionRead() throws Exception {
        MockHttpServletRequest request = branchRequest(CsvScenario.EXTENSION);
        request.setParameter("custom_key", "on");
        var properties = mock(CarlosProperties.class);
        when(properties.getProperty("demographicExt")).thenReturn("custom key");
        List<SqlCall> extensionCalls = new ArrayList<>();
        var demographic = new ConfiguredReportQuery.Row(Map.of("demographic_no", "41", "last_name", "FAKE",
                "first_name", "First"), List.of());
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenAnswer(call -> {
                    String sql = call.getArgument(0);
                    if (!sql.startsWith("select x.demographic_no, k.extension_key")) {
                        return new ConfiguredReportQuery.Result(List.of(), List.of(demographic));
                    }
                    extensionCalls.add(new SqlCall(sql, extractBindParameters(call.getArguments())));
                    return new ConfiguredReportQuery.Result(List.of(), List.of(
                            extensionRow("41", "prefer_language", "French"), extensionRow("41", "custom_key", "custom value")));
                });
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            servlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Preferred Language\tcustom key", "French\tcustom value");
        assertThat(extensionCalls).singleElement().satisfies(call -> {
            assertThat(call.sql()).contains("SELECT ? AS extension_key UNION ALL SELECT ? AS extension_key", "in (?)");
            assertThat(call.params()).containsExactly("prefer_language", "custom_key", 41);
        });
    }

    @Test
    void shouldKeepPatientsAndKeysDistinct_forDigitLeadingExtensionKeys() throws Exception {
        var request = baseRequest();
        request.setParameter("demoReport", "true");
        request.setParameter("first_name", "on");
        request.setParameter("2custom", "on");
        request.setParameter("custom", "on");
        addFilter(request, 1, "demographic.first_name='FAKE'", "unused", "");
        var first = new ConfiguredReportQuery.Row(Map.of("demographic_no", "41", "first_name", "FAKE first"), List.of());
        var second = new ConfiguredReportQuery.Row(Map.of("demographic_no", "412", "first_name", "FAKE second"), List.of());
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenAnswer(call -> new ConfiguredReportQuery.Result(List.of(),
                        ((String) call.getArgument(0)).startsWith("select x.demographic_no, k.extension_key")
                                ? List.of(extensionRow("41", "2custom", "FAKE first value"), extensionRow("412", "custom", "FAKE second value"))
                                : List.of(first, second)));
        var properties = mock(CarlosProperties.class);
        when(properties.getProperty("demographicExt")).thenReturn("2custom|custom");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            servlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(200);
        try (var csv = org.apache.commons.csv.CSVParser.parse(response.getContentAsString(),
                org.apache.commons.csv.CSVFormat.DEFAULT.builder().setDelimiter('\t').get())) {
            var rows = csv.getRecords();
            assertThat(rows).hasSize(3);
            int digitKey = rows.getFirst().toList().indexOf("2custom");
            int plainKey = rows.getFirst().toList().indexOf("custom");
            assertThat(digitKey).isGreaterThanOrEqualTo(0);
            assertThat(plainKey).isGreaterThanOrEqualTo(0);
            assertThat(rows.get(1).get(digitKey)).isEqualTo("FAKE first value");
            assertThat(rows.get(1).get(plainKey)).isEmpty();
            assertThat(rows.get(2).get(digitKey)).isEmpty();
            assertThat(rows.get(2).get(plainKey)).isEqualTo("FAKE second value");
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"=1+1", "+cmd", "-cmd", "@SUM(1)", "  =1+1", "\ttext", "\rtext", "\ntext", "-5", "-0.5e2", "FAKE plain", ""})
    void shouldGuardSpreadsheetCells_whenRenderingExtensionValues(String value) throws Exception {
        var request = branchRequest(CsvScenario.EXTENSION);
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenAnswer(call -> new ConfiguredReportQuery.Result(List.of(),
                        ((String) call.getArgument(0)).startsWith("select x.demographic_no, k.extension_key")
                                ? List.of(extensionRow("41", "prefer_language", value))
                                : List.of(new ConfiguredReportQuery.Row(Map.of("demographic_no", "41", "last_name", "FAKE", "first_name", "Patient"), List.of()))));
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        servlet().service(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        try (var csv = org.apache.commons.csv.CSVParser.parse(response.getContentAsString(),
                org.apache.commons.csv.CSVFormat.DEFAULT.builder().setDelimiter('\t').get())) {
            var rows = csv.getRecords();
            assertThat(rows).hasSize(2);
            int column = rows.getFirst().toList().indexOf("Preferred Language");
            assertThat(column).isGreaterThanOrEqualTo(0);
            String expected = value.isEmpty() || value.equals("FAKE plain") || value.equals("-5") || value.equals("-0.5e2")
                    ? value : "\t" + value;
            assertThat(rows.get(1).get(column)).isEqualTo(expected);
        }
    }

    @Test
    void shouldSkipExtensionRetrieval_whenTheCohortIsEmpty() throws Exception {
        var request = branchRequest(CsvScenario.EXTENSION);
        List<SqlCall> calls = new ArrayList<>();
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenAnswer(call -> {
                    calls.add(new SqlCall(call.getArgument(0), extractBindParameters(call.getArguments())));
                    return new ConfiguredReportQuery.Result(List.of(), List.of());
                });
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));
            servlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Preferred Language");
        assertThat(calls).noneMatch(call -> call.sql().contains("from demographicExt x"));
    }

    private static ConfiguredReportQuery.Row extensionRow(String patient, String key, String value) {
        Map<String, String> values = new java.util.HashMap<>();
        values.put("demographic_no", patient);
        values.put("extension_key", key);
        values.put("value", value);
        return new ConfiguredReportQuery.Row(values, List.of());
    }

    @Test
    void shouldKeepLastDateAndIdOrderedValue_forLegacyDuplicateRows() throws Exception {
        var request = branchRequest(CsvScenario.EXTENSION);
        var demographic = new ConfiguredReportQuery.Row(Map.of("demographic_no", "41", "last_name", "FAKE",
                "first_name", "First"), List.of());
        List<SqlCall> extensionCalls = new ArrayList<>();
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenAnswer(call -> {
                    String sql = call.getArgument(0);
                    if (!sql.startsWith("select x.demographic_no, k.extension_key")) {
                        return new ConfiguredReportQuery.Result(List.of(), List.of(demographic));
                    }
                    extensionCalls.add(new SqlCall(sql, extractBindParameters(call.getArguments())));
                    // The physical ordering puts NULL dates first and the highest ID last on a date tie.
                    return new ConfiguredReportQuery.Result(List.of(), List.of(
                            extensionRow("41", "prefer_language", "null-date"),
                            extensionRow("41", "prefer_language", "earlier-date"),
                            extensionRow("41", "prefer_language", "same-date-lower-id"),
                            extensionRow("41", "prefer_language", "same-date-higher-id")));
                });
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));
            servlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\tsame-date-higher-id")
                .doesNotContain("null-date", "earlier-date", "same-date-lower-id");
        assertThat(extensionCalls).singleElement().satisfies(call ->
                assertThat(call.sql()).endsWith("order by x.date_time, x.id"));
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
            servlet().service(request, response);
            assertThat(reports.constructed()).hasSize(1);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).isEqualTo("Unable to generate report");
            assertThat(response.getContentAsString()).isEmpty();
            // An ordinary failure is not a limit refusal: the error page shows no limit notice.
            assertThat(request.getAttribute("carlosReportLimitKey")).isNull();
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
        try (LogCapture log = LogCapture.forLogger(RptDownloadCSVServlet.class)) {
            servlet().service(request, response);
            assertThat(messages(log, Level.WARN))
                    .containsExactly("Report download refused by its configured_report.max_csv_mb limit of 5");
            assertThat(messages(log, Level.ERROR)).isEmpty();
        }
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).isEmpty();
        // The refusal names the download limit, for the log and for the error page.
        assertThat(response.getErrorMessage()).startsWith("This report is larger than 5 MB, the most a report download may hold.")
                .contains("configured_report.max_csv_mb");
        assertThat(request.getAttribute("carlosReportLimitKey")).isEqualTo("report.configured.limitCsv");
        assertThat(request.getAttribute("carlosReportLimitValue")).isEqualTo(5);
        assertThat(request.getAttribute("carlosReportLimitSetting")).isEqualTo("configured_report.max_csv_mb");
    }

    @Test
    void shouldDownloadLargerCsv_whenDownloadLimitIsRaised() throws Exception {
        // About 7.5 MiB of UTF-8: refused under the default 5 MiB, downloaded once an operator raises the limit.
        String value = "\u20ac".repeat(RptDownloadCSVServlet.MAX_CSV_EXPORT_BYTES / 2);
        var row = new ConfiguredReportQuery.Row(java.util.Map.of("last_name", value), List.of(value));
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(new ConfiguredReportQuery.Result(List.of("last_name"), List.of(row)));
        var request = baseRequest(); request.getSession(true);
        request.setParameter("demoReport", "true"); request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        CarlosProperties.getInstance().setProperty("configured_report.max_csv_mb", "8");
        try {
            servlet().service(request, response);
        } finally {
            CarlosProperties.getInstance().remove("configured_report.max_csv_mb");
        }
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray().length).isGreaterThan(RptDownloadCSVServlet.MAX_CSV_EXPORT_BYTES);
        assertThat(request.getAttribute("carlosReportLimitKey")).isNull();
    }

    @Test
    void shouldNameTheLimitOnTheErrorPage_whenReportQueryIsRefused() throws Exception {
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenThrow(new ConfiguredReportQuery.LimitExceededException(ConfiguredReportQuery.Limit.ROWS, 10_000));
        var request = baseRequest(); request.getSession(true);
        request.setParameter("demoReport", "true"); request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        servlet().service(request, response);
        // Still a failed download with nothing partial; the error page now says which limit stopped it.
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(request.getAttribute("carlosReportLimitKey")).isEqualTo("report.configured.limitRows");
        assertThat(request.getAttribute("carlosReportLimitValue")).isEqualTo(10_000);
        assertThat(request.getAttribute("carlosReportLimitSetting")).isEqualTo("configured_report.max_rows");
    }

    @Test
    void shouldLogOneWarningWithoutTrace_whenADemographicReportIsRefused() throws Exception {
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenThrow(new ConfiguredReportQuery.LimitExceededException(ConfiguredReportQuery.Limit.ROWS, 10_000));
        var request = baseRequest(); request.getSession(true);
        request.setParameter("demoReport", "true"); request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (LogCapture log = LogCapture.forLogger(RptDownloadCSVServlet.class)) {
            servlet().service(request, response);

            // A refusal is the user's to fix: one warning naming the setting, no trace, no error.
            assertThat(messages(log, Level.WARN))
                    .containsExactly("Report download refused by its configured_report.max_rows limit of 10000");
            assertThat(messages(log, Level.ERROR)).isEmpty();
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
        }
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(request.getAttribute("carlosReportLimitKey")).isEqualTo("report.configured.limitRows");
    }

    @Test
    void shouldLogOneWarningWithoutTrace_whenAFormReportIsRefused() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.getSession(true);
        request.setParameter("id", "5549");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var reports = mockConstruction(
                RptReportItem.class,
                (report, context) -> when(report.getReportName("5549")).thenThrow(
                        new ConfiguredReportQuery.LimitExceededException(ConfiguredReportQuery.Limit.ROWS, 10_000)));
             LogCapture log = LogCapture.forLogger(RptDownloadCSVServlet.class)) {
            servlet().service(request, response);

            assertThat(reports.constructed()).hasSize(1);
            assertThat(messages(log, Level.WARN))
                    .containsExactly("Report download refused by its configured_report.max_rows limit of 10000");
            assertThat(messages(log, Level.ERROR)).isEmpty();
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
        }
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).isEqualTo("Unable to generate report");
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(request.getAttribute("carlosReportLimitKey")).isEqualTo("report.configured.limitRows");
        assertThat(request.getAttribute("carlosReportLimitSetting")).isEqualTo("configured_report.max_rows");
    }

    @Test
    void shouldLogOneErrorWithTrace_whenAFormReportFailsForAnotherReason() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.getSession(true);
        request.setParameter("id", "5549");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var failure = new java.sql.SQLException("synthetic read failure");
        try (var reports = mockConstruction(
                RptReportItem.class,
                (report, context) -> when(report.getReportName("5549")).thenThrow(failure));
             LogCapture log = LogCapture.forLogger(RptDownloadCSVServlet.class)) {
            servlet().service(request, response);

            assertThat(messages(log, Level.WARN)).isEmpty();
            assertThat(messages(log, Level.ERROR)).containsExactly("RptDownloadCSVServlet service() - form report failed");
            assertThat(log.events()).filteredOn(event -> event.getLevel() == Level.ERROR)
                    .singleElement().satisfies(event -> assertThat(event.getThrown()).hasRootCause(failure));
        }
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(request.getAttribute("carlosReportLimitKey")).isNull();
    }

    @Test
    void shouldLogOneErrorWithTrace_whenADemographicReportFailsForAnotherReason() throws Exception {
        var failure = new java.sql.SQLException("synthetic read failure");
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenThrow(failure);
        var request = baseRequest(); request.getSession(true);
        request.setParameter("demoReport", "true"); request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (LogCapture log = LogCapture.forLogger(RptDownloadCSVServlet.class)) {
            servlet().service(request, response);

            assertThat(messages(log, Level.WARN)).isEmpty();
            assertThat(messages(log, Level.ERROR)).containsExactly("RptDownloadCSVServlet service() - report generation failed");
            assertThat(log.events()).filteredOn(event -> event.getLevel() == Level.ERROR)
                    .singleElement().satisfies(event -> assertThat(event.getThrown()).isSameAs(failure));
        }
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).isEqualTo("Unable to generate report");
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(request.getAttribute("carlosReportLimitKey")).isNull();
    }

    // ---- #5831: the servlet's own access check and server-side filter definitions ----

    @Test
    @DisplayName("should refuse with 403 and run no query when the session has no logged-in user")
    void shouldRefuseWithForbidden_whenSessionHasNoLoggedInUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.getSession(true);
        request.setParameter("demoReport", "true");
        request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        servlet().service(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getErrorMessage()).isEqualTo("missing required sec object (_report)");
        assertThat(request.getAttribute("carlosRefusalKey")).isEqualTo("error.refusal.reportAccess");
        assertThat(response.getContentAsString()).isEmpty();
        configuredQueryMock.verifyNoInteractions();
        assertThat(storedFilterMock.constructed()).isEmpty();
    }

    @Test
    @DisplayName("should refuse with 403 and run no query when the user has neither report privilege")
    void shouldRefuseWithForbidden_whenUserLacksReportPrivileges() throws Exception {
        when(security.hasPrivilege(eq(user), eq("_report"), eq("r"), isNull())).thenReturn(false);
        var request = baseRequest();
        request.setParameter("id", "5549");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var reports = mockConstruction(RptReportItem.class)) {
            servlet().service(request, response);
            assertThat(reports.constructed()).isEmpty();
        }
        assertThat(response.getStatus()).isEqualTo(403);
        verify(security).hasPrivilege(user, "_admin.reporting", "r", null);
        configuredQueryMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should allow the download with _admin.reporting alone, as the report filter page does")
    void shouldAllowDownload_whenUserHasAdminReportingOnly() throws Exception {
        when(security.hasPrivilege(eq(user), eq("_report"), eq("r"), isNull())).thenReturn(false);
        when(security.hasPrivilege(eq(user), eq("_admin.reporting"), eq("r"), isNull())).thenReturn(true);
        var row = new ConfiguredReportQuery.Row(Map.of("last_name", "FAKE"), List.of("FAKE"));
        configuredQueryMock.when(() -> ConfiguredReportQuery.read(anyString(), any(Object[].class)))
                .thenReturn(new ConfiguredReportQuery.Result(List.of("last_name"), List.of(row)));
        var request = baseRequest();
        request.setParameter("demoReport", "true");
        request.setParameter("last_name", "on");
        addFilter(request, 1, "demographic.demographic_no=42", "unused", "");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        servlet().service(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("FAKE");
    }

    @Test
    @DisplayName("should build the WHERE clause from the stored filter, not SQL posted in value_")
    void shouldUseStoredPredicate_whenRequestPostsInjectedSql() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.addParameter("last_name", "on");
        addFilter(request, 1, "demographic.last_name='${lastName}'", "lastName", "Smith");
        // The page's hidden copy is replaced with attacker SQL; only the stored definition may run.
        request.setParameter("value_1", "1=1 UNION SELECT user_name FROM security");

        List<SqlCall> sqlCalls = invokeDemoReport(request);

        assertThat(sqlCalls).isNotEmpty().allSatisfy(call -> assertThat(call.sql())
                .doesNotContainIgnoringCase("union").doesNotContain("security"));
        SqlCall query = findCallContaining(sqlCalls, "from demographic where");
        assertThat(query.sql()).contains("demographic.last_name=?");
        assertThat(query.params()).containsExactly("Smith");
    }

    @Test
    @DisplayName("should answer 400 and run no query when a ticked filter is not one of the report's filters")
    void shouldAnswerBadRequest_whenTickedFilterIsUnknown() throws Exception {
        var request = baseRequest();
        request.setParameter("demoReport", "true");
        request.setParameter("last_name", "on");
        request.setParameter("filter_9", "on");
        request.setParameter("value_9", "1=1");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (LogCapture log = LogCapture.forLogger(RptDownloadCSVServlet.class)) {
            servlet().service(request, response);
            assertThat(messages(log, Level.ERROR)).isEmpty();
            assertThat(messages(log, Level.WARN)).singleElement().asString().contains("not this report's active filters");
        }
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).contains("reload the report filter page");
        assertThat(request.getAttribute("carlosRefusalKey")).isEqualTo("error.refusal.reportFilterSelection");
        assertThat(response.getContentAsString()).isEmpty();
        configuredQueryMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should answer 400 when a form report ticks a filter the report does not define")
    void shouldAnswerBadRequest_whenFormReportFilterIsUnknown() throws Exception {
        var request = baseRequest();
        request.setParameter("id", "7");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var reports = mockConstruction(RptReportItem.class,
                     (report, context) -> when(report.getReportName("7")).thenReturn("report-7"));
             var queries = mockConstruction(RptFormQuery.class, (query, context) ->
                     when(query.getQueryStr(anyString(), any())).thenThrow(new RptFormQuery.UnknownReportFilterException()))) {
            servlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should guard formula-like headers and cells in the form report CSV (#5846)")
    void shouldGuardFormulaCells_inFormReportCsv() throws Exception {
        var request = baseRequest();
        request.setParameter("id", "7");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var reports = mockConstruction(RptReportItem.class, (report, context) ->
                     when(report.getReportName(anyString())).thenReturn("report-7"));
             var queries = mockConstruction(RptFormQuery.class, (query, context) ->
                     when(query.getQueryStr(anyString(), any())).thenReturn(new ParameterizedSql("select 1", List.of())));
             var configs = mockConstruction(RptReportConfigData.class, (config, context) ->
                     when(config.getAllFieldNameValue(anyString(), anyString())).thenReturn(new Vector[]{
                             new Vector<>(List.of("=caption")), new Vector<>(List.of("=caption"))}));
             var readers = mockConstruction(RptReportCreator.class, (reader, context) ->
                     when(reader.query(anyString(), any(Vector.class), any(Object[].class))).thenAnswer(call -> {
                         Properties row = new Properties();
                         row.setProperty("=caption", "=HYPERLINK(\"synthetic\",\"x\")");
                         return new Vector<>(List.of(row));
                     }))) {
            servlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(200);
        try (var csv = org.apache.commons.csv.CSVParser.parse(response.getContentAsString(),
                org.apache.commons.csv.CSVFormat.DEFAULT.builder().setDelimiter('\t').get())) {
            var rows = csv.getRecords();
            assertThat(rows.get(0).get(0)).isEqualTo("\t=caption");
            assertThat(rows.get(1).get(0)).isEqualTo("\t=HYPERLINK(\"synthetic\",\"x\")");
        }
    }

    /** The messages a capture holds at one level. */
    private static List<String> messages(LogCapture log, Level level) {
        return log.events().stream().filter(event -> event.getLevel() == level)
                .map(event -> event.getMessage().getFormattedMessage()).toList();
    }

    @Test
    void shouldKeepEachDownloadFilename_whenRequestsOverlap() throws Exception {
        RptDownloadCSVServlet servlet = servlet();
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

        assertThatThrownBy(() -> servlet().service(request, response))
                .isInstanceOf(java.io.IOException.class)
                .hasMessage("synthetic output failure");
        verify(output).close();
    }

    /** A POST from a logged-in user, with the session the servlet requires. */
    private MockHttpServletRequest baseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(true), user);
        return request;
    }

    private void addFilter(
            MockHttpServletRequest request,
            int serial,
            String expression,
            String variableName,
            String variableValue) {
        // The predicate is the report's stored definition; the page still posts its copy in
        // value_<n>, which the servlet ignores (#5831).
        storedFilters.add(new String[]{"filter " + serial, expression, "", String.valueOf(serial), "", ""});
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
            demoReportMethod.invoke(servlet(), request);
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
