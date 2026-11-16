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
package io.github.carlos_emr.carlos.report.pageUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ReportByExamplesDao;
import io.github.carlos_emr.carlos.commn.dao.ReportByExamplesFavoriteDao;
import io.github.carlos_emr.carlos.commn.model.ReportByExamples;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.data.QueryByExampleAccessDeniedException;
import io.github.carlos_emr.carlos.report.data.QueryByExampleValidationException;
import io.github.carlos_emr.carlos.report.data.ReportingAccountUnavailableException;
import io.github.carlos_emr.carlos.report.data.RptByExampleData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every Query by Example run leaves exactly one row in the {@code log} audit table, whatever its
 * outcome, written by the signed-in user in a transaction of its own (finding 357: the only
 * "audit" was an INFO line the packaged log level discards). The row carries the query's hash,
 * length, duration, row count and outcome, never the SQL text, a result value or a patient.
 */
@DisplayName("Query by Example run audit")
@Tag("unit")
@Tag("report")
class RptByExample2ActionAuditUnitTest extends CarlosUnitTestBase {
    /** A query whose text must never reach the audit row; the marker is what a leak would carry. */
    private static final String SQL = "select demographic_no from demographic where last_name = 'FAKE-QBE-MARKER'";
    private static final String HASH = sha256(SQL);

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private ReportByExamplesDao reportByExamplesDao;
    private String previousEnabled;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        reportByExamplesDao = mock(ReportByExamplesDao.class);
        ReportByExamplesFavoriteDao favoritesDao = mock(ReportByExamplesFavoriteDao.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ReportByExamplesDao.class, reportByExamplesDao);
        registerMock(ReportByExamplesFavoriteDao.class, favoritesDao);

        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        request.setMethod("POST");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(favoritesDao.findByProvider("999998")).thenReturn(Collections.emptyList());
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)).thenReturn(true);

        previousEnabled = CarlosProperties.getInstance().getProperty(RptByExample2Action.ENABLED_PROPERTY);
        CarlosProperties.getInstance().setProperty(RptByExample2Action.ENABLED_PROPERTY, "true");
    }

    @AfterEach
    void tearDown() {
        if (previousEnabled == null) {
            CarlosProperties.getInstance().remove(RptByExample2Action.ENABLED_PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(RptByExample2Action.ENABLED_PROPERTY, previousEnabled);
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    void shouldWriteOneSuccessRowWithTheQueryMetadataAndNoSqlText_whenTheQueryRuns() throws Exception {
        String result = runReturning(new RptByExampleData.QueryResult("<table></table>", 1, false, false, 23));

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("results")).isEqualTo("<table></table>");
        verify(reportByExamplesDao).persist(any(ReportByExamples.class));
        assertThat(theOneAuditRow()).isEqualTo(
                "queryHash=" + HASH + " queryLength=" + SQL.length() + " durationMs=23 rowCount=1 outcome=success");
    }

    @Test
    void shouldWriteOneHistoryFailedRowInsteadOfASuccessRow_whenTheHistoryCannotBeSaved() throws Exception {
        doThrow(new IllegalStateException("history unavailable")).when(reportByExamplesDao).persist(any(ReportByExamples.class));

        runReturning(new RptByExampleData.QueryResult("<table></table>", 4, false, false, 31));

        assertThat(request.getAttribute("queryHistoryError")).isEqualTo(true);
        // One row per run: the run's final outcome, not a success row followed by a second one.
        assertThat(theOneAuditRow()).isEqualTo(
                "queryHash=" + HASH + " queryLength=" + SQL.length() + " durationMs=31 rowCount=4 outcome=history_failed");
    }

    static List<Arguments> refusedAndFailedRuns() {
        return List.of(
                Arguments.of(new QueryByExampleValidationException("Only SELECT statements are allowed"), "rejected",
                        "queryValidationError"),
                Arguments.of(new ReportingAccountUnavailableException("db_reporting_username is not set"), "unavailable",
                        "queryReportingUnavailable"),
                Arguments.of(new QueryByExampleAccessDeniedException(new SQLException("SELECT command denied", "42000", 1142)),
                        "denied", "queryAccessDenied"),
                Arguments.of(new SQLTimeoutException("Query execution was interrupted"), "timeout", "queryTimeout"),
                Arguments.of(new SQLException("Unknown column", "42S22", 1054), "failed", "queryExecutionError"),
                Arguments.of(new IllegalStateException("renderer failed"), "failed", "queryExecutionError"));
    }

    @ParameterizedTest
    @MethodSource("refusedAndFailedRuns")
    void shouldWriteOneRowNamingTheOutcome_whenTheRunIsRefusedOrFails(Exception failure, String outcome,
            String pageAttribute) throws Exception {
        String result = runThrowing(failure);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(pageAttribute)).isEqualTo(true);
        assertThat(request.getAttribute("results")).isNull();
        String data = theOneAuditRow();
        assertThat(data).matches("queryHash=" + HASH + " queryLength=" + SQL.length()
                + " durationMs=\\d+ rowCount=0 outcome=" + Pattern.quote(outcome));
    }

    @Test
    void shouldWriteOneRejectedRow_whenNoSqlIsSubmitted() throws Exception {
        RptByExample2Action action = new RptByExample2Action(securityInfoManager);
        action.setSql("   ");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("queryValidationError")).isEqualTo(true);
        assertThat(theOneAuditRow()).isEqualTo(
                "queryHash=" + sha256("   ") + " queryLength=3 durationMs=0 rowCount=0 outcome=rejected");
    }

    @Test
    void shouldWriteOneDisabledRow_whenQueryByExampleIsTurnedOff() throws Exception {
        CarlosProperties.getInstance().setProperty(RptByExample2Action.ENABLED_PROPERTY, "false");
        RptByExample2Action action = new RptByExample2Action(securityInfoManager);
        action.setSql(SQL);

        try (MockedConstruction<RptByExampleData> reportData = mockConstruction(RptByExampleData.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(reportData.constructed()).isEmpty();
        }

        assertThat(request.getAttribute("queryDisabled")).isEqualTo(true);
        assertThat(theOneAuditRow()).isEqualTo(
                "queryHash=" + HASH + " queryLength=" + SQL.length() + " durationMs=0 rowCount=0 outcome=disabled");
    }

    @Test
    void shouldStillShowTheResults_whenTheAuditRowCannotBeWritten() throws Exception {
        logActionMock.when(() -> LogAction.addLogSynchronousInNewTransaction(any(LoggedInInfo.class), any(), any(), any(),
                any(), any())).thenThrow(new IllegalStateException("audit store unavailable"));

        String result = runReturning(new RptByExampleData.QueryResult("<table></table>", 1, false, false, 23));

        // The query ran and its history was saved: an audit failure must not turn that into an error page.
        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("results")).isEqualTo("<table></table>");
        assertThat(request.getAttribute("queryExecutionError")).isNull();
        assertThat(request.getAttribute("queryHistoryError")).isNull();
        verify(reportByExamplesDao).persist(any(ReportByExamples.class));
    }

    @Test
    void shouldWriteNoRow_whenThePageIsOnlyOpened() throws Exception {
        request.setMethod("GET");
        RptByExample2Action action = new RptByExample2Action(securityInfoManager);

        try (MockedConstruction<RptByExampleData> reportData = mockConstruction(RptByExampleData.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        }

        logActionMock.verifyNoInteractions();
    }

    private String runReturning(RptByExampleData.QueryResult queryResult) throws Exception {
        RptByExample2Action action = new RptByExample2Action(securityInfoManager);
        action.setSql(SQL);
        try (MockedConstruction<RptByExampleData> reportData = mockConstruction(RptByExampleData.class,
                (mock, context) -> when(mock.execute(eq(SQL), any(Properties.class), eq("999998"))).thenReturn(queryResult))) {
            String result = action.execute();
            assertThat(reportData.constructed()).hasSize(1);
            return result;
        }
    }

    private String runThrowing(Exception failure) throws Exception {
        RptByExample2Action action = new RptByExample2Action(securityInfoManager);
        action.setSql(SQL);
        try (MockedConstruction<RptByExampleData> reportData = mockConstruction(RptByExampleData.class,
                (mock, context) -> when(mock.execute(eq(SQL), any(Properties.class), eq("999998"))).thenThrow(failure))) {
            return action.execute();
        }
    }

    /**
     * The data of the single audit row the run wrote: read/query_by_example, by the signed-in
     * user, with no content id and no patient, in a transaction of its own (never the asynchronous
     * or caller-transaction variants), and no other audit call.
     */
    private String theOneAuditRow() {
        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        logActionMock.verify(() -> LogAction.addLogSynchronousInNewTransaction(eq(loggedInInfo), eq(LogConst.READ),
                eq(LogConst.CON_QUERY_BY_EXAMPLE), isNull(), isNull(), data.capture()));
        logActionMock.verifyNoMoreInteractions();
        assertThat(data.getValue()).doesNotContain("FAKE-QBE-MARKER").doesNotContain("select");
        return data.getValue();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
