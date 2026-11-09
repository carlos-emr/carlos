/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CdmReportContextsUnitTest {
    private static void definitions(org.springframework.mock.web.MockHttpSession session) {
        var handler = org.mockito.Mockito.mock(RptMeasurementTypesBeanHandler.class);
        org.mockito.Mockito.when(handler.getMeasurementTypeVector()).thenReturn(new java.util.Vector<>());
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

}
