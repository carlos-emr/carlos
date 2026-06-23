/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv;

import javax.xml.namespace.QName;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.cxf.binding.soap.Soap11;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.wss4j.common.ext.WSSecurityException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * Pins the HTTP status mapping for SOAP WS-Security failures (issue #2954). Authentication
 * failures must surface as 401, client-side malformed/invalid security as 400, and anything
 * not attributable to the request as 500 -- without CXF defaulting every fault to 500 and
 * without disclosing which check failed.
 */
@DisplayName("AuthenticationInWSS4JInterceptor fault status mapping")
@Tag("unit")
@Tag("security")
class AuthenticationInWSS4JInterceptorUnitTest {

    private static final QName RECEIVER = Soap11.getInstance().getReceiver();

    @Test
    @DisplayName("should map failed authentication to HTTP 401")
    void shouldMapFault_toHttp401WhenFailedAuthentication() {
        SoapFault fault = soapFaultCausedBy(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);

        assertThat(AuthenticationInWSS4JInterceptor.resolveFaultStatusCode(fault)).isEqualTo(401);
    }

    @Test
    @DisplayName("should map unavailable security token to HTTP 401")
    void shouldMapFault_toHttp401WhenSecurityTokenUnavailable() {
        SoapFault fault = soapFaultCausedBy(WSSecurityException.ErrorCode.SECURITY_TOKEN_UNAVAILABLE);

        assertThat(AuthenticationInWSS4JInterceptor.resolveFaultStatusCode(fault)).isEqualTo(401);
    }

    @Test
    @DisplayName("should map an expired message to HTTP 400")
    void shouldMapFault_toHttp400WhenMessageExpired() {
        SoapFault fault = soapFaultCausedBy(WSSecurityException.ErrorCode.MESSAGE_EXPIRED);

        assertThat(AuthenticationInWSS4JInterceptor.resolveFaultStatusCode(fault)).isEqualTo(400);
    }

    @Test
    @DisplayName("should map invalid security headers to HTTP 400")
    void shouldMapFault_toHttp400WhenInvalidSecurity() {
        SoapFault fault = soapFaultCausedBy(WSSecurityException.ErrorCode.INVALID_SECURITY);

        assertThat(AuthenticationInWSS4JInterceptor.resolveFaultStatusCode(fault)).isEqualTo(400);
    }

    @Test
    @DisplayName("should map a generic WS-Security failure to HTTP 500")
    void shouldMapFault_toHttp500WhenGenericFailure() {
        SoapFault fault = soapFaultCausedBy(WSSecurityException.ErrorCode.FAILURE);

        assertThat(AuthenticationInWSS4JInterceptor.resolveFaultStatusCode(fault)).isEqualTo(500);
    }

    @Test
    @DisplayName("should map a fault without a WS-Security cause to HTTP 500")
    void shouldMapFault_toHttp500WhenNoSecurityCause() {
        SoapFault fault = new SoapFault("parse error", new RuntimeException("boom"), RECEIVER);

        assertThat(AuthenticationInWSS4JInterceptor.resolveFaultStatusCode(fault)).isEqualTo(500);
    }

    @Test
    @DisplayName("should find the WS-Security error code nested deeper in the cause chain")
    void shouldMapFault_byNestedSecurityCause() {
        WSSecurityException wss = new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
        RuntimeException wrapper = new RuntimeException("wrapped", wss);
        SoapFault fault = new SoapFault("auth failed", wrapper, RECEIVER);

        assertThat(AuthenticationInWSS4JInterceptor.resolveFaultStatusCode(fault)).isEqualTo(401);
    }

    private static SoapFault soapFaultCausedBy(WSSecurityException.ErrorCode code) {
        return new SoapFault("ws-security failure", new WSSecurityException(code), RECEIVER);
    }

    // --- handleMessage() wiring -------------------------------------------------
    // These drive the actual handleMessage flow via a test seam (performSecurityCheck)
    // so the failure-path status assignment and the success/failure audit logging are
    // covered without standing up a full CXF/WSS4J SOAP pipeline.

    @Test
    @DisplayName("should skip processing when there is no HTTP request (outgoing message)")
    void shouldSkipProcessing_whenNoHttpRequest() {
        TestInterceptor interceptor = new TestInterceptor(null);
        SoapMessage message = new SoapMessage(new MessageImpl()); // no HTTP_REQUEST set

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            interceptor.handleMessage(message); // must not throw

            assertThat(interceptor.securityCheckCalls).isZero();
            logAction.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("should set HTTP 401 on the fault and log a failure when authentication fails")
    void shouldSetHttp401AndLogFailure_whenAuthenticationFails() {
        SoapFault authFailure =
                new SoapFault("auth failed",
                        new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION),
                        RECEIVER);
        TestInterceptor interceptor = new TestInterceptor(authFailure);
        ReflectionTestUtils.setField(interceptor, "oscarUsernameTokenValidator",
                mock(OscarUsernameTokenValidator.class));
        SoapMessage message = messageWithRequest(new MockHttpServletRequest());

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            SoapFault thrown =
                    catchThrowableOfType(() -> interceptor.handleMessage(message), SoapFault.class);

            assertThat(thrown).isSameAs(authFailure);
            assertThat(thrown.getStatusCode()).isEqualTo(401);
            logAction.verify(() -> LogAction.addLogSynchronous(any(OscarLog.class)), times(1));
        }
    }

    @Test
    @DisplayName("should wire the validator and log success when authentication succeeds")
    void shouldWireValidatorAndLogSuccess_whenAuthenticationSucceeds() {
        TestInterceptor interceptor = new TestInterceptor(null); // no fault -> success
        OscarUsernameTokenValidator validator = mock(OscarUsernameTokenValidator.class);
        ReflectionTestUtils.setField(interceptor, "oscarUsernameTokenValidator", validator);

        MockHttpServletRequest request = new MockHttpServletRequest();
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        request.setAttribute(loggedInInfo.getLoggedInInfoKey(), loggedInInfo);
        SoapMessage message = messageWithRequest(request);

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            interceptor.handleMessage(message);

            assertThat(interceptor.securityCheckCalls).isEqualTo(1);
            assertThat(message.get("ws-security.ut.validator")).isSameAs(validator);
            logAction.verify(() -> LogAction.addLogSynchronous(any(OscarLog.class)), times(1));
        }
    }

    private static SoapMessage messageWithRequest(HttpServletRequest request) {
        SoapMessage message = new SoapMessage(new MessageImpl());
        message.put(AbstractHTTPDestination.HTTP_REQUEST, request);
        return message;
    }

    /**
     * Replaces the real {@link org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor} processing
     * with a deterministic outcome: throw {@code faultToThrow} to simulate a WS-Security failure,
     * or return normally to simulate a successful authentication.
     */
    private static final class TestInterceptor extends AuthenticationInWSS4JInterceptor {
        private final SoapFault faultToThrow;
        private int securityCheckCalls;

        private TestInterceptor(SoapFault faultToThrow) {
            this.faultToThrow = faultToThrow;
        }

        @Override
        protected void performSecurityCheck(SoapMessage message) {
            securityCheckCalls++;
            if (faultToThrow != null) {
                throw faultToThrow;
            }
        }
    }
}
