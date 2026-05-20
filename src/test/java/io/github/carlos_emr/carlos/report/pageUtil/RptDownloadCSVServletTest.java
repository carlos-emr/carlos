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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.login.DBHelp;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;

/**
 * Regression tests for {@link RptDownloadCSVServlet#demoReport(jakarta.servlet.http.HttpServletRequest)}.
 *
 * <p>These tests target the empty-demographic-filter combinations fixed in PR #1639 and
 * verify the generated SQL shape and bind-parameter ordering without requiring a database.
 * They invoke the private servlet method via reflection and capture
 * {@link DBHelp#searchDBRecord(ParameterizedSql)} calls with a static mock.</p>
 *
 * @since 2026-04-20
 */
@Tag("unit")
@Tag("report")
class RptDownloadCSVServletTest {

    private static final Pattern MALFORMED_WHERE_CLAUSE_PATTERN =
            Pattern.compile("(?i)\\band\\s*(group by|order by|$)");

    private static Method demoReportMethod;
    private static final String CONFIGURED_FILTERS_ATTR = RptDownloadCSVServletTest.class.getName() + ".configuredFilters";

    private final ResultSet emptyResultSet = mock(ResultSet.class);

    private MockedStatic<DBHelp> dbHelpMock;

    @BeforeAll
    static void setUpReflection() throws NoSuchMethodException {
        demoReportMethod = RptDownloadCSVServlet.class.getDeclaredMethod("demoReport", jakarta.servlet.http.HttpServletRequest.class);
        demoReportMethod.setAccessible(true);
    }

    @BeforeEach
    void setUp() throws Exception {
        when(emptyResultSet.next()).thenReturn(false);
        dbHelpMock = mockStatic(DBHelp.class);
    }

    @AfterEach
    void tearDown() {
        if (dbHelpMock != null) {
            dbHelpMock.close();
            dbHelpMock = null;
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

    @Test
    @DisplayName("should close DBHelp result sets during report generation")
    void shouldCloseDbHelpResultSets() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.addParameter("last_name", "on");
        request.addParameter("c_EDD", "on");
        addFilter(request, 1, "formBCAR.c_EDD>='${edd}'", "edd", "2026-01-01");

        invokeDemoReport(request);

        verify(emptyResultSet, atLeastOnce()).close();
    }

    @Test
    @DisplayName("should reject direct CSV download when report privilege is missing")
    void shouldRejectDirectCsvDownload_withoutReportPrivilege() throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthCheckingRptDownloadCSVServlet(false).service(request, response);

        assertThat(response.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
    }

    private MockHttpServletRequest baseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.addParameter("id", "1");
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
        configuredFilters(request).add(new String[]{"", expression, "", String.valueOf(serial), "", ""});
    }

    private List<SqlCall> invokeDemoReport(MockHttpServletRequest request) throws Exception {
        List<SqlCall> sqlCalls = new ArrayList<>();
        dbHelpMock.when(() -> DBHelp.searchDBRecord(
                        org.mockito.ArgumentMatchers.any(ParameterizedSql.class)))
                .thenAnswer(invocation -> captureDbHelpCall(sqlCalls, invocation));

        try {
            demoReportMethod.invoke(new TestableRptDownloadCSVServlet(), request);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }

        return sqlCalls;
    }

    private ResultSet captureDbHelpCall(List<SqlCall> sqlCalls, InvocationOnMock invocation) {
        ParameterizedSql query = invocation.getArgument(0);
        sqlCalls.add(new SqlCall(
                query.getSql(),
                query.getParams()));
        return emptyResultSet;
    }

    private Vector configuredFilters(MockHttpServletRequest request) {
        Vector filters = (Vector) request.getAttribute(CONFIGURED_FILTERS_ATTR);
        if (filters == null) {
            filters = new Vector();
            request.setAttribute(CONFIGURED_FILTERS_ATTR, filters);
        }
        return filters;
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

    private static final class TestableRptDownloadCSVServlet extends RptDownloadCSVServlet {
        @Override
        Vector[] getConfiguredFilterValues(String reportId, jakarta.servlet.http.HttpServletRequest request) {
            return RptFormQuery.getValueParam((Vector) request.getAttribute(CONFIGURED_FILTERS_ATTR), request);
        }
    }

    private static final class AuthCheckingRptDownloadCSVServlet extends RptDownloadCSVServlet {
        private final boolean allowed;

        private AuthCheckingRptDownloadCSVServlet(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        boolean hasReportDownloadPrivilege(jakarta.servlet.http.HttpServletRequest request) {
            return allowed;
        }
    }
}
