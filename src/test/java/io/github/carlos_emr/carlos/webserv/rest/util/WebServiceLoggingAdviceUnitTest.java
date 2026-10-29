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
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.logging.log4j.Level;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebServiceLoggingAdvice}.
 *
 * <p>Pins the security-hardening contract for issue #2810: REST WebService access logging through
 * the application logger must stay at DEBUG (off in production by default) and must only carry the
 * {@code service.method} name — never the joinpoint argument values, which can hold PHI or
 * OAuth-derived context. The intentional, access-controlled OscarLog audit record is still
 * written for each invocation.</p>
 */
@Tag("unit")
@Tag("fast")
class WebServiceLoggingAdviceUnitTest {

    /** Stand-in declaring type so the logged description reads like a real REST service. */
    private interface PharmacyService {
    }

    private ProceedingJoinPoint mockJoinPoint(String methodName) {
        ProceedingJoinPoint joinpoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinpoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(PharmacyService.class);
        when(signature.getName()).thenReturn(methodName);
        return joinpoint;
    }

    private MockedStatic<PhaseInterceptorChain> stubCurrentRequest() {
        return stubCurrentRequest(mock(HttpServletRequest.class));
    }

    private MockedStatic<PhaseInterceptorChain> stubCurrentRequest(HttpServletRequest request) {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://emr.example/ws/rest/PharmacyService"));
        // Parameter values may carry PHI; they belong only in the access-controlled audit store.
        when(request.getParameterMap())
                .thenReturn(Collections.singletonMap("demographicNo", new String[] {"12345SECRET"}));

        Message message = mock(Message.class);
        when(message.get("HTTP.REQUEST")).thenReturn(request);

        MockedStatic<PhaseInterceptorChain> chain = mockStatic(PhaseInterceptorChain.class);
        chain.when(PhaseInterceptorChain::getCurrentMessage).thenReturn(message);
        return chain;
    }

    @Test
    @DisplayName("returns the proceed result and logs only the service.method name at DEBUG")
    void shouldReturnProceedResultAndLogMethodNameOnly_whenLoggingAtDebug() throws Throwable {
        ProceedingJoinPoint joinpoint = mockJoinPoint("getPharmacies");
        Object expected = new Object();
        when(joinpoint.proceed()).thenReturn(expected);

        WebServiceLoggingAdvice advice = new WebServiceLoggingAdvice();

        try (LogCapture capture = LogCapture.forLogger(WebServiceLoggingAdvice.class);
             var _ = stubCurrentRequest();
             MockedStatic<LogAction> ignoredLog = mockStatic(LogAction.class)) {

            Object result = advice.logAccess(joinpoint);

            assertThat(result).isSameAs(expected);
            verify(joinpoint).proceed();

            assertThat(capture.messages()).contains("REST WS access: PharmacyService.getPharmacies");

            // No INFO-level diagnostic logging, and no leak of parameter/argument values.
            assertThat(capture.events())
                    .noneMatch(event -> event.getLevel() == Level.INFO);
            assertThat(capture.messages())
                    .noneMatch(message -> message.contains("12345SECRET"))
                    .noneMatch(message -> message.contains("Logging access for"));
        }
    }

    @Test
    @DisplayName("writes a non-failure OscarLog audit record for a successful call")
    void shouldWriteAuditRecord_whenCallSucceeds() throws Throwable {
        ProceedingJoinPoint joinpoint = mockJoinPoint("getPharmacies");
        when(joinpoint.proceed()).thenReturn("ok");

        WebServiceLoggingAdvice advice = new WebServiceLoggingAdvice();

        try (var _ = stubCurrentRequest();
             MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {

            advice.logAccess(joinpoint);

            ArgumentCaptor<OscarLog> captor = ArgumentCaptor.forClass(OscarLog.class);
            logAction.verify(() -> LogAction.addLogSynchronous(captor.capture()));
            assertThat(captor.getValue().getAction()).isEqualTo("REST WS: PharmacyService.getPharmacies");
        }
    }

    @Test
    @DisplayName("rethrows the original throwable and still records a FAILURE audit record")
    void shouldRethrowAndRecordFailure_whenTargetThrows() throws Throwable {
        ProceedingJoinPoint joinpoint = mockJoinPoint("getPharmacies");
        RuntimeException boom = new RuntimeException("boom");
        when(joinpoint.proceed()).thenThrow(boom);

        WebServiceLoggingAdvice advice = new WebServiceLoggingAdvice();

        try (LogCapture capture = LogCapture.forLogger(WebServiceLoggingAdvice.class);
             var _ = stubCurrentRequest();
             MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {

            assertThatThrownBy(() -> advice.logAccess(joinpoint)).isSameAs(boom);

            ArgumentCaptor<OscarLog> captor = ArgumentCaptor.forClass(OscarLog.class);
            logAction.verify(() -> LogAction.addLogSynchronous(captor.capture()));
            assertThat(captor.getValue().getAction()).isEqualTo("REST WS: FAILURE: PharmacyService.getPharmacies");

            // Failure detail is logged at DEBUG, never INFO, and carries no argument values.
            List<String> messages = capture.messages();
            assertThat(capture.events()).noneMatch(event -> event.getLevel() == Level.INFO);
            assertThat(messages).noneMatch(message -> message.contains("12345SECRET"));
        }
    }

    private static final String LOGGED_IN_INFO_KEY = new LoggedInInfo().getLoggedInInfoKey();

    private static LoggedInInfo callerWithProviderNo(String providerNo) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        LoggedInInfo info = new LoggedInInfo();
        info.setLoggedInProvider(provider);
        return info;
    }

    private static HttpSession sessionWith(LoggedInInfo info) {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(LOGGED_IN_INFO_KEY)).thenReturn(info);
        return session;
    }

    /** Runs a successful call through the advice and returns the single audit record it wrote. */
    private OscarLog auditRecordForSuccessfulCall(HttpServletRequest request) throws Throwable {
        ProceedingJoinPoint joinpoint = mockJoinPoint("getPharmacies");
        when(joinpoint.proceed()).thenReturn("ok");

        try (var _ = stubCurrentRequest(request);
             MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {

            assertThat(new WebServiceLoggingAdvice().logAccess(joinpoint)).isEqualTo("ok");

            ArgumentCaptor<OscarLog> captor = ArgumentCaptor.forClass(OscarLog.class);
            logAction.verify(() -> LogAction.addLogSynchronous(captor.capture()));
            return captor.getValue();
        }
    }

    @Test
    @DisplayName("records the OAuth request principal as the audit author")
    void shouldRecordRequestPrincipal_whenOAuthCallerAuthenticated() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(LOGGED_IN_INFO_KEY)).thenReturn(callerWithProviderNo("999998"));

        assertThat(auditRecordForSuccessfulCall(request).getProviderNo()).isEqualTo("999998");
    }

    @Test
    @DisplayName("records the session provider when the request carries no principal")
    void shouldRecordSessionProvider_whenNoRequestPrincipal() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = sessionWith(callerWithProviderNo("999998"));
        when(request.getSession(false)).thenReturn(session);

        assertThat(auditRecordForSuccessfulCall(request).getProviderNo()).isEqualTo("999998");
    }

    @Test
    @DisplayName("prefers the request principal over the session provider, like AbstractServiceImpl")
    void shouldPreferRequestPrincipal_overSession() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(LOGGED_IN_INFO_KEY)).thenReturn(callerWithProviderNo("999998"));
        HttpSession session = sessionWith(callerWithProviderNo("999997"));
        when(request.getSession(false)).thenReturn(session);

        assertThat(auditRecordForSuccessfulCall(request).getProviderNo()).isEqualTo("999998");
    }

    @Test
    @DisplayName("keeps N/A without a caller and never creates a session to look for one")
    void shouldKeepNotApplicableWithoutSession_whenNoCaller() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThat(auditRecordForSuccessfulCall(request).getProviderNo()).isEqualTo("N/A");
        verify(request, never()).getSession();
        verify(request, never()).getSession(true);
    }

    @Test
    @DisplayName("records the authenticated provider on the FAILURE audit record too")
    void shouldRecordProvider_onFailureRecord() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(LOGGED_IN_INFO_KEY)).thenReturn(callerWithProviderNo("999998"));
        ProceedingJoinPoint joinpoint = mockJoinPoint("getPharmacies");
        RuntimeException boom = new RuntimeException("boom");
        when(joinpoint.proceed()).thenThrow(boom);

        try (var _ = stubCurrentRequest(request);
             MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {

            assertThatThrownBy(() -> new WebServiceLoggingAdvice().logAccess(joinpoint)).isSameAs(boom);

            ArgumentCaptor<OscarLog> captor = ArgumentCaptor.forClass(OscarLog.class);
            logAction.verify(() -> LogAction.addLogSynchronous(captor.capture()));
            assertThat(captor.getValue().getAction()).isEqualTo("REST WS: FAILURE: PharmacyService.getPharmacies");
            assertThat(captor.getValue().getProviderNo()).isEqualTo("999998");
        }
    }

    @Test
    @DisplayName("keeps N/A and the successful result when the session was invalidated")
    void shouldKeepNotApplicable_whenSessionInvalidated() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(LOGGED_IN_INFO_KEY))
                .thenThrow(new IllegalStateException("getAttribute: Session already invalidated"));
        when(request.getSession(false)).thenReturn(session);

        // auditRecordForSuccessfulCall asserts the "ok" result and exactly one audit record.
        OscarLog auditRecord = auditRecordForSuccessfulCall(request);
        assertThat(auditRecord.getAction()).isEqualTo("REST WS: PharmacyService.getPharmacies");
        assertThat(auditRecord.getProviderNo()).isEqualTo("N/A");
    }

    @ParameterizedTest(name = "providerNo = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("keeps N/A when the authenticated caller has no usable provider number")
    void shouldKeepNotApplicable_whenProviderNoBlank(String providerNo) throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // WsUtils leaves the provider unset when the security record has no provider number.
        LoggedInInfo caller = providerNo == null ? new LoggedInInfo() : callerWithProviderNo(providerNo);
        when(request.getAttribute(LOGGED_IN_INFO_KEY)).thenReturn(caller);

        assertThat(auditRecordForSuccessfulCall(request).getProviderNo()).isEqualTo("N/A");
    }
}
