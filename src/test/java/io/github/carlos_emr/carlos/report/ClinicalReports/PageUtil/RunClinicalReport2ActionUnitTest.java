/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.ClinicalReports.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
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
    void shouldNameTheLimitOnTheErrorPage_whenDenominatorIsRefusedByALimit() {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_report"), eq("r"), isNull())).thenReturn(true);
        var request = new MockHttpServletRequest(); request.setParameter("numerator", "n"); request.setParameter("denominator", "d");
        var reports = mock(ClinicalReportManager.class);
        var refusal = new io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.LimitExceededException(
                io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.Limit.IDENTIFIERS, 100_000);
        var denominator = mock(Denominator.class);
        when(denominator.getDenominatorList()).thenThrow(new IllegalStateException(
                "Clinical report could not be completed; narrow the filters or retry", refusal));
        var numerator = mock(Numerator.class);
        when(reports.getDenominatorById("d")).thenReturn(denominator); when(reports.getNumeratorById("n")).thenReturn(numerator);
        try (var servlet = mockStatic(ServletActionContext.class); var manager = mockStatic(ClinicalReportManager.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            manager.when(ClinicalReportManager::getInstance).thenReturn(reports);
            // The report still fails as a whole (no zero population, no partial result) ...
            assertThatThrownBy(() -> new RunClinicalReport2Action().execute())
                    .isInstanceOf(IllegalStateException.class).hasCause(refusal);
            assertThat(request.getAttribute("denominator")).isNull();
            assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
            // ... and the error page is told which limit stopped it and which setting raises it.
            assertThat(request.getAttribute("carlosReportLimitKey")).isEqualTo("report.configured.limitIdentifiers");
            assertThat(request.getAttribute("carlosReportLimitValue")).isEqualTo(100_000);
            assertThat(request.getAttribute("carlosReportLimitSetting")).isEqualTo("configured_report.max_identifier_rows");
        }
    }
}
