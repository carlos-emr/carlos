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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.ClinicalReports.ClinicalReportManager;
import io.github.carlos_emr.carlos.report.ClinicalReports.Denominator;
import io.github.carlos_emr.carlos.report.ClinicalReports.Numerator;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * What the report page gets back for posted values it cannot run as they are: nothing posted yet, a
 * numerator or denominator id that is not in ClinicalReports.xml, and no "Fields to include" box ticked.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("report")
@DisplayName("Running a clinical report with unusual posted values")
class RunClinicalReport2ActionPostedValuesUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should show the empty form, with no message, when nothing has been posted")
    void shouldShowTheEmptyForm_whenNothingIsPosted() throws Exception {
        var request = new MockHttpServletRequest("GET", "/RunClinicalReport");
        var reports = mock(ClinicalReportManager.class);

        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("clinicalReportUnknown")).isNull();
        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("should treat a missing or empty id as not chosen")
    void shouldTreatAnIdAsChosen_onlyWhenItHasAValue() {
        assertThat(RunClinicalReport2Action.chosen(null)).isFalse();
        assertThat(RunClinicalReport2Action.chosen("")).isFalse();
        assertThat(RunClinicalReport2Action.chosen("n")).isTrue();
    }

    @Test
    @DisplayName("should say the report is not in the list when the denominator is not listed")
    void shouldShowUnknownReport_whenTheDenominatorIsNotListed() throws Exception {
        var request = runnableRequest();
        var reports = mock(ClinicalReportManager.class);

        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("clinicalReportUnknown")).isEqualTo(Boolean.TRUE);
        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    @Test
    @DisplayName("should say the report is not in the list when the numerator is not listed")
    void shouldShowUnknownReport_whenTheNumeratorIsNotListed() throws Exception {
        var request = runnableRequest();
        var reports = mock(ClinicalReportManager.class);
        when(reports.getDenominatorById("d")).thenReturn(mock(Denominator.class));

        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("clinicalReportUnknown")).isEqualTo(Boolean.TRUE);
        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    @Test
    @DisplayName("should say the report is not in the list when only a denominator is chosen")
    void shouldShowUnknownReport_whenOnlyTheDenominatorIsChosen() throws Exception {
        var request = runnableRequest();
        request.setParameter("numerator", "");
        var reports = mock(ClinicalReportManager.class);
        when(reports.getDenominatorById("d")).thenReturn(mock(Denominator.class));

        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("clinicalReportUnknown")).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("should say the report is not in the list, and run nothing, when an extra numerator is not listed")
    void shouldShowUnknownReport_whenAnExtraNumeratorIsNotListed() throws Exception {
        var request = runnableRequest();
        request.setParameter("numerator3", "FAKE-gone");
        var reports = mock(ClinicalReportManager.class);
        var denominator = mock(Denominator.class);
        when(reports.getDenominatorById("d")).thenReturn(denominator);
        when(reports.getNumeratorById("n")).thenReturn(mock(Numerator.class));

        assertThat(run(request, reports)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("clinicalReportUnknown")).isEqualTo(Boolean.TRUE);
        verify(denominator, never()).getDenominatorList();
    }

    @Test
    @DisplayName("should give the page an empty field list when no field is ticked, skipping an unchosen extra numerator")
    void shouldGiveAnEmptyFieldList_whenNoFieldIsTicked() throws Exception {
        var request = runnableRequest();
        request.setParameter("numerator1", ""); // an extra numerator left at "Select Below"

        assertThat(run(request, listedReports())).isEqualTo(ActionSupport.SUCCESS);

        assertThat((String[]) request.getAttribute("showfields")).isEmpty();
        assertThat(request.getAttribute("clinicalReportUnknown")).isNull();
    }

    @Test
    @DisplayName("should run a listed extra numerator beside the main one")
    void shouldRunTheReport_whenAnExtraNumeratorIsListed() throws Exception {
        var request = runnableRequest();
        request.setParameter("numerator2", "n");

        assertThat(run(request, listedReports())).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("clinicalReportUnknown")).isNull();
        assertThat(request.getAttribute("max_numerator")).isEqualTo(2);
    }

    @Test
    @DisplayName("should keep the ticked fields as they were posted")
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

    /** A report manager that lists denominator "d" (one FAKE patient) and numerator "n". */
    private static ClinicalReportManager listedReports() {
        var reports = mock(ClinicalReportManager.class);
        var denominator = mock(Denominator.class);
        when(denominator.getDenominatorList()).thenReturn(List.of("101"));
        when(denominator.getDenominatorName()).thenReturn("FAKE-denominator");
        var numerator = mock(Numerator.class);
        when(numerator.getOutputFields()).thenReturn(new String[0]);
        when(numerator.getNumeratorName()).thenReturn("FAKE-numerator");
        when(reports.getDenominatorById("d")).thenReturn(denominator);
        when(reports.getNumeratorById("n")).thenReturn(numerator);
        return reports;
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
