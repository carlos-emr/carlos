/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for this contract.
 * @since 2026-11-09
 */
@Tag("unit")
class CdmReportContextsUnitTest {
    private static void definitions(org.springframework.mock.web.MockHttpSession session) {
        var handler = org.mockito.Mockito.mock(RptMeasurementTypesBeanHandler.class);
        org.mockito.Mockito.when(handler.getMeasurementTypeVector()).thenReturn(new java.util.Vector<>());
        org.mockito.Mockito.when(handler.getMeasuringInstrcBeanVector()).thenReturn(new java.util.Vector<>());
        session.setAttribute("measurementTypes", handler);
    }
    @Test
    void shouldRestoreOriginatingGroup_whenAnotherTabSelectsADifferentGroup() {
        var first = new MockHttpServletRequest();
        definitions((org.springframework.mock.web.MockHttpSession) first.getSession());
        first.getSession().setAttribute("CDMGroup", "Group A");
        String token = CdmReportContexts.issue(first);
        var second = new MockHttpServletRequest();
        second.setSession(first.getSession());
        second.getSession().setAttribute("CDMGroup", "Group B");
        CdmReportContexts.issue(second);
        var submitted = new MockHttpServletRequest();
        submitted.setSession(first.getSession());
        submitted.setParameter(CdmReportContexts.PARAMETER, token);
        assertThat(CdmReportContexts.bind(submitted)).isTrue();
        assertThat(submitted.getAttribute("CDMGroup")).isEqualTo("Group A");
        assertThat(submitted.getSession().getAttribute("CDMGroup")).isEqualTo("Group B");
    }

    @Test
    void shouldRefuseContext_fromAnotherSession() {
        String token = CdmReportContexts.issue(new MockHttpServletRequest());
        var other = new MockHttpServletRequest();
        other.setParameter(CdmReportContexts.PARAMETER, token);
        assertThat(CdmReportContexts.bind(other)).isFalse();
    }

    @Test
    void shouldRefuseOldestForm_whenCapacityIsExceeded() {
        var request = new MockHttpServletRequest();
        String oldest = CdmReportContexts.issue(request);
        for (int i = 0; i < 64; i++) CdmReportContexts.issue(request);
        request.setParameter(CdmReportContexts.PARAMETER, oldest);
        assertThat(CdmReportContexts.bind(request)).isFalse();
    }
    @Test
    void shouldRefuseExpiredForm_withoutExpiringANewerForm() {
        var request = new MockHttpServletRequest();
        definitions((org.springframework.mock.web.MockHttpSession) request.getSession());
        String old = CdmReportContexts.issue(request, 0);
        String fresh = CdmReportContexts.issue(request, 3_599_999);
        request.setParameter(CdmReportContexts.PARAMETER, old);
        assertThat(CdmReportContexts.bind(request, 3_600_000)).isFalse();
        request.setParameter(CdmReportContexts.PARAMETER, fresh);
        assertThat(CdmReportContexts.bind(request, 3_600_000)).isTrue();
    }

    private static RptMeasurementTypesBeanHandler renderedDefinitions() {
        var handler = org.mockito.Mockito.mock(RptMeasurementTypesBeanHandler.class);
        var rows = new java.util.Vector<RptMeasurementTypesBean>();
        rows.add(new RptMeasurementTypesBean(1, "WT", "Weight", "Weight", "kg", "1"));
        var instructions = org.mockito.Mockito.mock(RptMeasuringInstructionBeanHandler.class);
        org.mockito.Mockito.when(instructions.getMeasuringInstrcVector()).thenReturn(
                new java.util.Vector<>(java.util.List.of(new RptMeasuringInstructionBean("kg"))));
        org.mockito.Mockito.when(handler.getMeasurementTypeVector()).thenReturn(rows);
        org.mockito.Mockito.when(handler.getMeasuringInstrcBeanVector()).thenReturn(
                new java.util.Vector<>(java.util.List.of(instructions)));
        return handler;
    }

    @Test
    void shouldValidateActualPostedSelectors_forValidTamperedUnknownAndDuplicateValues() {
        var request = new MockHttpServletRequest();
        var definitions = renderedDefinitions();
        String token = CdmReportContexts.issue(request, java.util.Map.of("measurementTypes", definitions));
        request.setParameter(CdmReportContexts.PARAMETER, token);
        request.setParameter("value(measurementType0)", "WT");
        request.setParameter("value(mInstrcsCheckbox00)", "kg");
        assertThat(CdmReportContexts.bind(request)).isTrue();
        request.setParameter("value(measurementType0)", "HIV");
        assertThat(CdmReportContexts.bind(request)).isFalse();
        request.setParameter("value(measurementType0)", "WT");
        request.setParameter("value(mInstrcsCheckbox00)", "unrendered instruction");
        assertThat(CdmReportContexts.bind(request)).isFalse();
        request.setParameter("value(mInstrcsCheckbox00)", "kg");
        request.setParameter("value(measurementType99)", "WT");
        assertThat(CdmReportContexts.bind(request)).isFalse();
        request.removeParameter("value(measurementType99)");
        request.setParameter("value(measurementType0)", new String[]{"WT", "WT"});
        assertThat(CdmReportContexts.bind(request)).isFalse();
    }

    @Test
    void shouldSnapshotLocalSelection_withoutReadingAnotherTabsSessionSelection() {
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute("CDMGroup", "Concurrent tab B");
        var definitions = renderedDefinitions();
        String token = CdmReportContexts.issue(request, java.util.Map.of(
                "measurementTypes", definitions, "CDMGroup", "Local tab A"));
        definitions.getMeasurementTypeVector().clear();
        request.setParameter(CdmReportContexts.PARAMETER, token);
        request.setParameter("value(measurementType0)", "WT");
        assertThat(CdmReportContexts.bind(request)).isTrue();
        assertThat(request.getAttribute("CDMGroup")).isEqualTo("Local tab A");
    }

}
