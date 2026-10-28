/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.common;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.FilterChain;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    static Stream<Arguments> restPrintReadRequests() {
        return Stream.of("GET", "HEAD").flatMap(method -> Stream.of("101", "00101", "2147483647")
                .map(patient -> Arguments.of(method, patient)));
    }

    @ParameterizedTest
    @MethodSource("restPrintReadRequests")
    void shouldUseRestPrintPathPatient_whenSignedContextMatchesPathDespiteConflictingQueryAliases(String method, String pathPatient) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String patient = Integer.toString(Integer.parseInt(pathPatient));
        String supplied = token(session, patient, "11");
        MockHttpServletRequest request = restPrintRequest(session, supplied, pathPatient);
        request.setMethod(method);
        request.setParameter("demographicNo", new String[]{"202", "303"});
        request.setParameter("demographic_no", "malformed query patient");
        request.setParameter(ChartProgramContext.PARAMETER, supplied, supplied);
        Object exactDraft = new Object();
        String draftKey = "caseManagementEntryForm" + patient + "." + supplied;
        session.setAttribute(draftKey, exactDraft);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verify(chain).doFilter(request, response);
        assertThat(request.getParameterValues("demographicNo")).containsExactly("202", "303");
        assertThat(request.getParameter("demographic_no")).isEqualTo("malformed query patient");
        assertThat(session.getAttribute(draftKey)).isSameAs(exactDraft);
    }

    static Stream<Arguments> restPrintMismatchedPatients() {
        return Stream.of("GET", "HEAD").flatMap(method -> Stream.of(false, true)
                .map(misleading -> Arguments.of(method, misleading)));
    }

    @ParameterizedTest
    @MethodSource("restPrintMismatchedPatients")
    void shouldRejectRestPrintTokenForAnotherPathPatient_evenWhenQueriesNameTheTokenPatient(String method, boolean misleadingQueries) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = restPrintRequest(session, token(session, "202", "11"), "101");
        request.setMethod(method);
        if (misleadingQueries) {
            request.setParameter("demographicNo", "202");
            request.setParameter("demographic_no", "00202");
        }
        assertDenied(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"blank", "forged", "foreign-login", "ambiguous", "missing-session"})
    void shouldKeepAllSignedContextChecks_forRestPrint(String invalid) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String own = token(session, "101", "11");
        MockHttpServletRequest request = restPrintRequest(session, own, "101");
        switch (invalid) {
            case "blank" -> request.setParameter(ChartProgramContext.PARAMETER, "");
            case "forged" -> request.setParameter(ChartProgramContext.PARAMETER, "forged.context");
            case "foreign-login" -> request.setParameter(ChartProgramContext.PARAMETER,
                    token(new MockHttpSession(), "101", "11"));
            case "ambiguous" -> request.setParameter(ChartProgramContext.PARAMETER, own, token(session, "101", "22"));
            case "missing-session" -> request.setSession(null);
            default -> throw new AssertionError(invalid);
        }
        assertDenied(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0/print", "-1/print", "2147483648/print", "not-a-patient/print",
            "101/print/extra", "101/Print", "101/summary", "101/print/"})
    void shouldPreserveQueryValidation_forMalformedOrNonExactRestPrintPaths(String suffix) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = restPrintRequest(session, token(session, "101", "11"), "101");
        request.setPathInfo("/rs/recordUX/" + suffix);
        request.setRequestURI("/clinic/ws" + request.getPathInfo());
        request.setParameter("demographicNo", "202");
        assertDenied(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "other-servlet"})
    void shouldPreserveQueryValidation_outsideTheExactGetCxfPrintMapping(String variation) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = restPrintRequest(session, token(session, "101", "11"), "101");
        if ("POST".equals(variation)) request.setMethod("POST");
        else request.setServletPath("/other");
        request.setParameter("demographicNo", "202");
        assertDenied(request);
    }

    private MockHttpServletRequest restPrintRequest(MockHttpSession session, String supplied, String pathPatient) {
        MockHttpServletRequest request = request(session, supplied);
        request.setMethod("GET");
        request.setContextPath("/clinic");
        request.setServletPath("/ws");
        request.setPathInfo("/rs/recordUX/" + pathPatient + "/print");
        request.setRequestURI("/clinic/ws" + request.getPathInfo());
        return request;
    }

    private void assertDenied(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getErrorMessage()).isEqualTo("Invalid chart context");
        assertThat(response.getContentAsByteArray()).isEmpty();
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
