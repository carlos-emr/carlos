/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import io.github.carlos_emr.carlos.app.CarlosExceptionMappingInterceptor;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.ClinicalReports.*;
import io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.apache.struts2.ActionInvocation;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.config.entities.ActionConfig;
import org.apache.struts2.config.entities.ExceptionMappingConfig;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("report")
class RunClinicalReport2ActionUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldRefuseReportWithoutPublishingState_whenReadPrivilegeIsMissing() {
        createAndRegisterMock(SecurityInfoManager.class);
        var request = new MockHttpServletRequest();
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action = new RunClinicalReport2Action();
            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_report)");
            assertThat(request.getAttribute("csv")).isNull();
            assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
        }
    }

    @Test
    void shouldReturnSuccessWithReportAttributes_afterIndependentCountsAreEvaluated() throws Exception {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_report"), eq("r"), isNull())).thenReturn(true);
        var request = new MockHttpServletRequest(); request.setParameter("numerator", "n"); request.setParameter("denominator", "d");
        var response = new MockHttpServletResponse();
        var reports = mock(ClinicalReportManager.class);
        var denominator = mock(Denominator.class); when(denominator.getDenominatorList()).thenReturn(List.of("101"));
        when(denominator.getDenominatorName()).thenReturn("FAKE-denominator");
        var numerator = mock(Numerator.class); when(numerator.getNumeratorName()).thenReturn("FAKE-numerator");
        when(numerator.getOutputFields()).thenReturn(new String[0]);
        when(numerator.evaluate(any(), eq("101"))).thenReturn(true);
        when(reports.getDenominatorById("d")).thenReturn(denominator); when(reports.getNumeratorById("n")).thenReturn(numerator);
        try (var servlet = mockStatic(ServletActionContext.class); var manager = mockStatic(ClinicalReportManager.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            manager.when(ClinicalReportManager::getInstance).thenReturn(reports);
            assertThat(new RunClinicalReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("numerator")).isEqualTo("1");
            assertThat(request.getAttribute("denominator")).isEqualTo("1");
            assertThat(request.getAttribute("percentage")).isEqualTo("100");
            assertThat(request.getSession().getAttribute("ClinicalReports")).isInstanceOf(java.util.ArrayList.class);
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }

    @Test
    void shouldNameTheLimitOnTheErrorPage_whenDenominatorIsRefusedByALimit() throws Exception {
        var request = reportRequest();
        var response = new MockHttpServletResponse();
        var refusal = identifierRefusal();
        try (var servlet = mockStatic(ServletActionContext.class); var manager = mockStatic(ClinicalReportManager.class);
             var log = LogCapture.forLogger(RunClinicalReport2Action.class)) {
            failingReport(servlet, manager, request, response,
                    new IllegalStateException("Clinical report could not be completed; narrow the filters or retry", refusal));
            // The report still fails as a whole (no zero population, no partial result), on the error page ...
            assertThat(new RunClinicalReport2Action().execute()).isEqualTo(ActionSupport.ERROR);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(request.getAttribute("denominator")).isNull();
            assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
            // ... which is told which limit stopped it and which setting raises it, with an incident reference.
            assertThat(request.getAttribute("carlosReportLimitKey")).isEqualTo("report.configured.limitIdentifiers");
            assertThat(request.getAttribute("carlosReportLimitValue")).isEqualTo(100_000);
            assertThat(request.getAttribute("carlosReportLimitSetting")).isEqualTo("configured_report.max_identifier_rows");
            String incidentId = (String) request.getAttribute(CarlosExceptionMappingInterceptor.INCIDENT_ID_ATTRIBUTE);
            assertThat(UUID.fromString(incidentId)).hasToString(incidentId);
            // A refusal is the user's to fix: one warning naming the setting and the incident, no trace, no error.
            assertThat(messages(log, Level.WARN)).containsExactly("Clinical report refused by its "
                    + "configured_report.max_identifier_rows limit of 100000 [incident " + incidentId + "]");
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
            assertThat(messages(log, Level.ERROR)).isEmpty();
        }
    }

    @Test
    void shouldNeitherWarnNorNameALimit_whenTheReportFailsForAnotherReason() throws Exception {
        var request = reportRequest();
        var failure = otherFailure();
        try (var servlet = mockStatic(ServletActionContext.class); var manager = mockStatic(ClinicalReportManager.class);
             var log = LogCapture.forLogger(RunClinicalReport2Action.class)) {
            failingReport(servlet, manager, request, new MockHttpServletResponse(), failure);
            assertThatThrownBy(() -> new RunClinicalReport2Action().execute()).isSameAs(failure);
            assertThat(request.getAttribute("carlosReportLimitKey")).isNull();
            assertThat(request.getAttribute(CarlosExceptionMappingInterceptor.INCIDENT_ID_ATTRIBUTE)).isNull();
            assertThat(messages(log, Level.WARN)).isEmpty();
        }
    }

    @Test
    void shouldLogNoErrorAnywhere_whenALimitRefusalPassesTheExceptionMapping() throws Exception {
        var request = reportRequest();
        var response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var manager = mockStatic(ClinicalReportManager.class);
             var actionLog = LogCapture.forLogger(RunClinicalReport2Action.class);
             var mappingLog = LogCapture.forLogger(CarlosExceptionMappingInterceptor.class)) {
            failingReport(servlet, manager, request, response,
                    new IllegalStateException("Clinical report could not be completed; narrow the filters or retry",
                            identifierRefusal()));

            assertThat(throughExceptionMapping(new RunClinicalReport2Action())).isEqualTo("error");

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(request.getAttribute(CarlosExceptionMappingInterceptor.INCIDENT_ID_ATTRIBUTE)).isNotNull();
            assertThat(request.getAttribute("carlosReportLimitKey")).isEqualTo("report.configured.limitIdentifiers");
            assertThat(messages(actionLog, Level.WARN)).hasSize(1);
            assertThat(mappingLog.events()).isEmpty();
            assertThat(actionLog.events()).allMatch(event -> event.getThrown() == null && event.getLevel() != Level.ERROR);
        }
    }

    @Test
    void shouldStillLogOneError_whenAnotherFailurePassesTheExceptionMapping() throws Exception {
        var request = reportRequest();
        var response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var manager = mockStatic(ClinicalReportManager.class);
             var actionLog = LogCapture.forLogger(RunClinicalReport2Action.class);
             var mappingLog = LogCapture.forLogger(CarlosExceptionMappingInterceptor.class)) {
            failingReport(servlet, manager, request, response, otherFailure());

            assertThat(throughExceptionMapping(new RunClinicalReport2Action())).isEqualTo("error");

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(request.getAttribute(CarlosExceptionMappingInterceptor.INCIDENT_ID_ATTRIBUTE)).isNotNull();
            assertThat(request.getAttribute("carlosReportLimitKey")).isNull();
            assertThat(messages(actionLog, Level.WARN)).isEmpty();
            assertThat(messages(actionLog, Level.ERROR)).isEmpty();
            assertThat(messages(mappingLog, Level.ERROR)).singleElement()
                    .satisfies(message -> assertThat(message).startsWith("Unhandled java.lang.IllegalStateException [incident "));
        }
    }

    /** A POST of the report form for one denominator and one numerator, with report read access. */
    private MockHttpServletRequest reportRequest() {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_report"), eq("r"), isNull())).thenReturn(true);
        var request = new MockHttpServletRequest("POST", "/RunClinicalReport");
        request.setParameter("numerator", "n");
        request.setParameter("denominator", "d");
        return request;
    }

    private static ConfiguredReportQuery.LimitExceededException identifierRefusal() {
        return new ConfiguredReportQuery.LimitExceededException(ConfiguredReportQuery.Limit.IDENTIFIERS, 100_000);
    }

    private static IllegalStateException otherFailure() {
        return new IllegalStateException("Clinical report could not be completed; narrow the filters or retry",
                new java.sql.SQLException("synthetic failure"));
    }

    /** Wires the servlet context and a report whose denominator query fails with {@code failure}. */
    private static void failingReport(MockedStatic<ServletActionContext> servlet, MockedStatic<ClinicalReportManager> manager,
                                      MockHttpServletRequest request, MockHttpServletResponse response,
                                      IllegalStateException failure) {
        var reports = mock(ClinicalReportManager.class);
        var denominator = mock(Denominator.class);
        when(denominator.getDenominatorList()).thenThrow(failure);
        when(reports.getDenominatorById("d")).thenReturn(denominator);
        when(reports.getNumeratorById("n")).thenReturn(mock(Numerator.class));
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        manager.when(ClinicalReportManager::getInstance).thenReturn(reports);
    }

    /** Runs the action inside the exception mapping the report package configures (struts-report.xml). */
    private static String throughExceptionMapping(RunClinicalReport2Action action) throws Exception {
        ActionConfig config = new ActionConfig.Builder("report", "RunClinicalReport", RunClinicalReport2Action.class.getName())
                .addExceptionMapping(new ExceptionMappingConfig.Builder("security", SecurityException.class.getName(), "securityError").build())
                .addExceptionMapping(new ExceptionMappingConfig.Builder("any", Exception.class.getName(), "error").build())
                .build();
        ActionProxy proxy = mock(ActionProxy.class);
        when(proxy.getConfig()).thenReturn(config);
        ActionInvocation invocation = mock(ActionInvocation.class);
        when(invocation.getProxy()).thenReturn(proxy);
        when(invocation.invoke()).thenAnswer(call -> action.execute());
        return new CarlosExceptionMappingInterceptor().intercept(invocation);
    }

    /** The messages a capture holds at one level. */
    private static List<String> messages(LogCapture log, Level level) {
        return log.events().stream().filter(event -> event.getLevel() == level)
                .map(event -> event.getMessage().getFormattedMessage()).toList();
    }
}
