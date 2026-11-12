/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.ClinicalReports.ClinicalReportManager;
import io.github.carlos_emr.carlos.report.ClinicalReports.Denominator;
import io.github.carlos_emr.carlos.report.ClinicalReports.Numerator;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * What the report page gets back for posted values it cannot run as they are: a numerator or denominator
 * id that is not in ClinicalReports.xml, and no "Fields to include" box ticked.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("report")
class RunClinicalReport2ActionPostedValuesUnitTest extends CarlosUnitTestBase {
    /** A numerator or denominator that is not in ClinicalReports.xml: the page says so, and nothing runs. */
    @Test
    void shouldShowUnknownReport_whenTheDenominatorIsNotListed() throws Exception {
        var request = runnableRequest();
        var reports = mock(ClinicalReportManager.class);
        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("clinicalReportUnknown")).isEqualTo(Boolean.TRUE);
        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    @Test
    void shouldShowUnknownReport_whenTheNumeratorIsNotListed() throws Exception {
        var request = runnableRequest();
        var reports = mock(ClinicalReportManager.class);
        when(reports.getDenominatorById("d")).thenReturn(mock(Denominator.class));
        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("clinicalReportUnknown")).isEqualTo(Boolean.TRUE);
        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    @Test
    void shouldShowUnknownReport_whenAnExtraNumeratorIsNotListed() throws Exception {
        var request = runnableRequest();
        request.setParameter("numerator3", "FAKE-gone");
        var reports = mock(ClinicalReportManager.class);
        var denominator = mock(Denominator.class);
        var numerator = mock(Numerator.class);
        when(reports.getDenominatorById("d")).thenReturn(denominator);
        when(reports.getNumeratorById("n")).thenReturn(numerator);
        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("clinicalReportUnknown")).isEqualTo(Boolean.TRUE);
        verify(denominator, never()).getDenominatorList();
    }

    /** With no "Fields to include" box ticked the browser sends no showfields; the page then gets an empty list. */
    @Test
    void shouldGiveAnEmptyFieldList_whenNoFieldIsTicked() throws Exception {
        var request = runnableRequest();
        request.setParameter("numerator1", ""); // an extra numerator left at "Select Below" is skipped
        var reports = mock(ClinicalReportManager.class);
        var denominator = mock(Denominator.class); when(denominator.getDenominatorList()).thenReturn(List.of("101"));
        when(denominator.getDenominatorName()).thenReturn("FAKE-denominator");
        var numerator = mock(Numerator.class); when(numerator.getOutputFields()).thenReturn(new String[0]);
        when(numerator.getNumeratorName()).thenReturn("FAKE-numerator");
        when(reports.getDenominatorById("d")).thenReturn(denominator); when(reports.getNumeratorById("n")).thenReturn(numerator);
        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);
        assertThat((String[]) request.getAttribute("showfields")).isEmpty();
        assertThat(request.getAttribute("clinicalReportUnknown")).isNull();
    }

    @Test
    void shouldKeepTheTickedFields_whenSomeAreTicked() {
        String[] ticked = {"lastName", "sex"};
        assertThat(RunClinicalReport2Action.chosenFields(ticked)).isSameAs(ticked);
        assertThat(RunClinicalReport2Action.chosenFields(null)).isEmpty();
    }

    private static MockHttpServletRequest runnableRequest() {
        var request = new MockHttpServletRequest("POST", "/RunClinicalReport");
        request.setParameter("numerator", "n");
        request.setParameter("denominator", "d");
        return request;
    }

    private String run(MockHttpServletRequest request, ClinicalReportManager reports) throws Exception {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_report"), eq("r"), isNull())).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class); var manager = mockStatic(ClinicalReportManager.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            manager.when(ClinicalReportManager::getInstance).thenReturn(reports);
            return new RunClinicalReport2Action().execute();
        }
    }
}
