/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.common;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Verifies rejection before a supplied chart context reaches an action. */
@Tag("unit")
@Tag("security")
class ChartProgramContextFilterUnitTest extends CarlosUnitTestBase {
    private final ChartProgramContextFilter filter = new ChartProgramContextFilter();

    @Test
    void shouldContinueLegacyRequests_whenNoWindowContextWasSupplied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", "malformed legacy patient");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getSession(false)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldContinueWithTheWindowProgram_whenEquivalentPatientAliasesAreRepeated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = token(session, "101", "11");
        MockHttpServletRequest request = request(session, token);
        request.setParameter("demographicNo", new String[]{"00101", "101"});
        request.setParameter("demographic_no", "000101");
        request.setParameter(ChartProgramContext.PARAMETER, new String[]{token, token});
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(ChartProgramContext.resolve(request)).isEqualTo("11");
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldContinueAssetRequests_whenTheValidContextHasNoPatientParameters() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = request(session, token(session, "101", "11"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-context", "00000000-0000-0000-0000-000000000000"})
    void shouldRejectInvalidOrUnknownContexts_beforeInvokingTheAction(String supplied) throws Exception {
        MockHttpServletRequest request = request(new MockHttpSession(), supplied);
        assertDenied(request);
    }

    @Test
    void shouldRejectTamperedContexts_beforeInvokingTheAction() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = token(session, "101", "11");
        String forged = (token.charAt(0) == 'A' ? "B" : "A") + token.substring(1);
        assertDenied(request(session, forged));
    }

    @Test
    void shouldRejectAnotherLoginsContext_beforeInvokingTheAction() throws Exception {
        String otherLoginToken = token(new MockHttpSession(), "101", "11");
        MockHttpSession currentLogin = new MockHttpSession();
        token(currentLogin, "101", "22");
        assertDenied(request(currentLogin, otherLoginToken));
    }

    @Test
    void shouldRejectContextsWithoutASession_beforeInvokingTheAction() throws Exception {
        String token = token(new MockHttpSession(), "101", "11");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(ChartProgramContext.PARAMETER, token);
        assertDenied(request);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void shouldRejectAmbiguousContexts_beforeInvokingTheAction() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String first = token(session, "101", "11");
        String second = token(session, "101", "22");
        MockHttpServletRequest request = request(session, first);
        request.setParameter(ChartProgramContext.PARAMETER, new String[]{first, second});
        assertDenied(request);
    }

    @Test
    void shouldRejectAnEmptyContextArray_beforeInvokingTheAction() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(ChartProgramContext.PARAMETER, new String[0]);
        assertDenied(request);
    }

    @Test
    void shouldRejectMismatchedPatients_beforeInvokingTheAction() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = request(session, token(session, "101", "11"));
        request.setParameter("demographicNo", "202");
        assertDenied(request);
    }

    @Test
    void shouldRejectConflictingPatientAliases_beforeInvokingTheAction() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = request(session, token(session, "101", "11"));
        request.setParameter("demographicNo", "101");
        request.setParameter("demographic_no", "202");
        assertDenied(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "not-a-patient", "2147483648"})
    void shouldRejectMalformedPatientAliases_beforeInvokingTheAction(String patient) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = request(session, token(session, "101", "11"));
        request.setParameter("demographic_no", patient);
        assertDenied(request);
    }

    private void assertDenied(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getErrorMessage()).isEqualTo("Invalid chart context");
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest request(MockHttpSession session, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter(ChartProgramContext.PARAMETER, token);
        return request;
    }

    private String token(MockHttpSession session, String patient, String program) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter("demographicNo", patient);
        ChartProgramContext.remember(request, program);
        return ChartProgramContext.token(request);
    }
}
