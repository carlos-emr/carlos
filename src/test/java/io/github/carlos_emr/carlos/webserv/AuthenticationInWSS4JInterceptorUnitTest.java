/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv;

import javax.xml.namespace.QName;

import org.apache.cxf.binding.soap.Soap11;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.wss4j.common.ext.WSSecurityException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
