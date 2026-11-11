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
    private static String issue(MockHttpServletRequest request) {
        return issue(request, System.currentTimeMillis());
    }

    private static String issue(MockHttpServletRequest request, long now) {
        // Legacy-session setup is confined to this fixture. Production passes captured request-local definitions.
        var attributes = new java.util.LinkedHashMap<String, Object>();
        for (String name : new String[]{CdmReportContexts.MEASUREMENT_TYPES, CdmReportContexts.GROUP,
                CdmReportContexts.TODAY, CdmReportContexts.LAST_YEAR}) {
            Object value = request.getSession().getAttribute(name);
            if (value != null) attributes.put(name, value);
        }
        return CdmReportContexts.issue(request, attributes, now);
    }

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
        String token = issue(first);
        var second = new MockHttpServletRequest();
        second.setSession(first.getSession());
        second.getSession().setAttribute("CDMGroup", "Group B");
        issue(second);
        var submitted = new MockHttpServletRequest();
        submitted.setSession(first.getSession());
        submitted.setParameter(CdmReportContexts.PARAMETER, token);
        assertThat(CdmReportContexts.bind(submitted)).isTrue();
        assertThat(submitted.getAttribute("CDMGroup")).isEqualTo("Group A");
        assertThat(submitted.getSession().getAttribute("CDMGroup")).isEqualTo("Group B");
    }

    @Test
    void shouldRefuseContext_fromAnotherSession() {
        var original = new MockHttpServletRequest();
        definitions((org.springframework.mock.web.MockHttpSession) original.getSession());
        String token = issue(original);
        original.setParameter(CdmReportContexts.PARAMETER, token);
        assertThat(CdmReportContexts.bind(original)).isTrue();
        var other = new MockHttpServletRequest();
        other.setParameter(CdmReportContexts.PARAMETER, token);
        assertThat(CdmReportContexts.bind(other)).isFalse();
    }

    @Test
    void shouldRefuseOldestForm_whenCapacityIsExceeded() {
        var request = new MockHttpServletRequest();
        definitions((org.springframework.mock.web.MockHttpSession) request.getSession());
        String oldest = issue(request);
        String newest = oldest;
        for (int i = 0; i < 64; i++) newest = issue(request);
        request.setParameter(CdmReportContexts.PARAMETER, oldest);
        assertThat(CdmReportContexts.bind(request)).isFalse();
        request.setParameter(CdmReportContexts.PARAMETER, newest);
        assertThat(CdmReportContexts.bind(request)).isTrue();
    }
    @Test
    void shouldRefuseExpiredForm_withoutExpiringANewerForm() {
        var request = new MockHttpServletRequest();
        definitions((org.springframework.mock.web.MockHttpSession) request.getSession());
        String old = issue(request, 0);
        String fresh = issue(request, 3_599_999);
        request.setParameter(CdmReportContexts.PARAMETER, old);
        assertThat(CdmReportContexts.bind(request, 3_600_000)).isFalse();
        request.setParameter(CdmReportContexts.PARAMETER, fresh);
        assertThat(CdmReportContexts.bind(request, 3_600_000)).isTrue();
    }

    private static RptMeasurementTypesBeanHandler renderedDefinitions() {
        return renderedDefinitions("WT", "kg");
    }

    private static RptMeasurementTypesBeanHandler renderedDefinitions(String type, String instruction) {
        var handler = org.mockito.Mockito.mock(RptMeasurementTypesBeanHandler.class);
        var rows = new java.util.Vector<RptMeasurementTypesBean>();
        rows.add(new RptMeasurementTypesBean(1, type, type, type, instruction, "1"));
        var instructions = org.mockito.Mockito.mock(RptMeasuringInstructionBeanHandler.class);
        org.mockito.Mockito.when(instructions.getMeasuringInstrcVector()).thenReturn(
                new java.util.Vector<>(java.util.List.of(new RptMeasuringInstructionBean(instruction))));
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

    @Test
    void shouldPreserveIndependentForms_whenTheSessionIsSerializedAndRestored() throws Exception {
        var request = new MockHttpServletRequest();
        String first = CdmReportContexts.issue(request, java.util.Map.of(
                "measurementTypes", renderedDefinitions("WT", "kg"), "CDMGroup", "Weight",
                "today", "2026-11-09", "lastYear", "2025-11-09"));
        String second = CdmReportContexts.issue(request, java.util.Map.of(
                "measurementTypes", renderedDefinitions("HT", "cm"), "CDMGroup", "Height"));
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.ObjectOutputStream(bytes)) {
            output.writeObject(request.getSession().getAttribute(CdmReportContexts.class.getName()));
        }
        var restoredSession = new org.springframework.mock.web.MockHttpSession();
        try (var input = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
            restoredSession.setAttribute(CdmReportContexts.class.getName(), input.readObject());
        }
        var submitted = new MockHttpServletRequest();
        submitted.setSession(restoredSession);
        submitted.setParameter(CdmReportContexts.PARAMETER, first);
        submitted.setParameter("value(measurementType0)", "WT");
        submitted.setParameter("value(mInstrcsCheckbox00)", "kg");
        assertThat(CdmReportContexts.bind(submitted)).isTrue();
        assertThat(submitted.getAttribute("CDMGroup")).isEqualTo("Weight");
        assertThat(submitted.getAttribute("today")).isEqualTo("2026-11-09");
        assertThat(submitted.getAttribute("lastYear")).isEqualTo("2025-11-09");
        var other = new MockHttpServletRequest();
        other.setSession(restoredSession);
        other.setParameter(CdmReportContexts.PARAMETER, second);
        other.setParameter("value(measurementType0)", "HT");
        other.setParameter("value(mInstrcsCheckbox00)", "cm");
        assertThat(CdmReportContexts.bind(other)).isTrue();
        assertThat(other.getAttribute("CDMGroup")).isEqualTo("Height");
        other.setParameter("value(measurementType0)", "WT");
        assertThat(CdmReportContexts.bind(other)).isFalse();
        assertThat(CdmReportContexts.bind(submitted)).isTrue();
    }

    @Test
    void shouldRejectNonSerializableAttributes_whenIssuingAForm() {
        var request = new MockHttpServletRequest();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CdmReportContexts.issue(request,
                java.util.Map.of("measurementTypes", renderedDefinitions(), "unexpected", new Object())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected");
    }


    @Test
    void shouldRefuseMismatchedDefinitionVectors_withoutThrowingOrAcceptingSelectors() {
        var request = new MockHttpServletRequest();
        var definitions = renderedDefinitions();
        org.mockito.Mockito.when(definitions.getMeasuringInstrcBeanVector()).thenReturn(new java.util.Vector<>());
        String token = CdmReportContexts.issue(request, java.util.Map.of("measurementTypes", definitions));
        request.setParameter(CdmReportContexts.PARAMETER, token);
        request.setParameter("value(measurementType0)", "WT");
        assertThat(CdmReportContexts.bind(request)).isFalse();
    }

}
