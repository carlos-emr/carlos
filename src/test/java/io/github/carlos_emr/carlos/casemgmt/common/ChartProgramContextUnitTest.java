/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.common;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"0", "1000000000", "2147483647"})
    void shouldCaptureProgram_whenIdentifierFitsTheChartDaoRange(String program) {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        ChartProgramContext.remember(request, program);
        assertThat(ChartProgramContext.resolve(request)).isEqualTo(program);
        assertThat(request.getAttribute("case_program_id")).isEqualTo(program);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2147483648", "9223372036854775808"})
    void shouldClearCapturedProgram_whenIdentifierIsOutsideTheChartDaoRange(String program) {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        ChartProgramContext.remember(request, "11");
        ChartProgramContext.remember(request, program);
        assertThat(ChartProgramContext.resolve(request)).isNull();
        assertThat(request.getAttribute("case_program_id")).isNull();
    }

    private MockHttpServletRequest chart(MockHttpSession session, String patient) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter("demographicNo", patient);
        return request;
    }
}
