/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.common;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ChartProgramContextUnitTest {
    @Test
    void shouldKeepEachChartsProgram_whenAnotherWindowChangesPmmSelection() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest first = chart(session, "101");
        session.setAttribute("case_program_id", "11");
        ChartProgramContext.capture(first);
        MockHttpServletRequest second = chart(session, "202");
        session.setAttribute("case_program_id", "22");
        ChartProgramContext.capture(second);
        session.setAttribute("case_program_id", "33");
        assertThat(ChartProgramContext.resolve(first)).isEqualTo("11");
        assertThat(ChartProgramContext.resolve(second)).isEqualTo("22");
        assertThat(ChartProgramContext.resolve(chart(session, "303"))).isNull();
    }

    @Test
    void shouldResolveNoProgram_whenPatientIsMissingOrSelectionInvalid() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = chart(session, "101");
        session.setAttribute("case_program_id", "");
        ChartProgramContext.capture(request);
        assertThat(ChartProgramContext.resolve(request)).isNull();
        request.removeParameter("demographicNo");
        assertThat(ChartProgramContext.resolve(request)).isNull();
    }

    private MockHttpServletRequest chart(MockHttpSession session, String patient) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter("demographicNo", patient);
        return request;
    }
}
