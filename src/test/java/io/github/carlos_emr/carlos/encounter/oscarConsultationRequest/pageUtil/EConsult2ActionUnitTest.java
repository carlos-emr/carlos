/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for {@link EConsult2Action}'s eConsult SSO return URL construction.
 *
 * <p>Covers the fix for issue #3018: the SSO {@code oscarReturnURL} must be built from
 * the trusted, configured {@code carlosBaseUrl} rather than from the client-controlled
 * request Host header.
 *
 * @since 2026-06-24
 */
@DisplayName("EConsult2Action Unit Tests")
@Tag("unit")
@Tag("consultation")
class EConsult2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("Builds the return URL from the configured base, not the request Host")
    void shouldBuildReturnUrl_withConfiguredBaseAndContextPath() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Keeps an explicit port from the configured base")
    void shouldBuildReturnUrl_withConfiguredPort() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com:8443", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com:8443/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Rejects an out-of-range port on a normal hostname")
    void shouldFailClosed_whenConfiguredPortOutOfRange() {
        // java.net.URI#getPort() does not range-check, so the validator must.
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com:99999", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com:70000", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Supports the root context (empty context path)")
    void shouldBuildReturnUrl_withRootContext() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "")).isEqualTo("https://emr.example.com/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com", null)).isEqualTo("https://emr.example.com/econsultSSOLogin");
    }

    @Test
    @DisplayName("Ignores any path on the configured base and uses the server context path")
    void shouldBuildReturnUrl_whenConfiguredBaseHasTrailingPath() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com/ignored", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Return URL host never reflects the request context path argument")
    void shouldBuildReturnUrl_withoutLeakingSpoofedHostFromContextPath() {
        // Even if a hostile-looking value reaches the context-path argument, the origin is
        // fixed by the configured base; the value can only ever land in the path component.
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "/attacker.example.org");

        assertThat(returnUrl).startsWith("https://emr.example.com/");
        assertThat(returnUrl).isEqualTo("https://emr.example.com/attacker.example.org/econsultSSOLogin");
    }

    @Test
    @DisplayName("Fails closed when no base URL is configured")
    void shouldFailClosed_whenBaseUrlMissing() {
        assertThat(EConsult2Action.buildSsoReturnUrl(null, "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("   ", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Rejects a configured base with a non-http(s) scheme")
    void shouldFailClosed_whenBaseUrlSchemeNotHttp() {
        assertThat(EConsult2Action.buildSsoReturnUrl("ftp://emr.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("javascript:alert(1)", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Rejects a configured base with no host")
    void shouldFailClosed_whenBaseUrlHasNoHost() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https:///econsultSSOLogin", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("not a url", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Rejects a configured base carrying credentials, query, or fragment")
    void shouldFailClosed_whenBaseUrlIsNotABareOrigin() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://user:pass@emr.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com?x=1", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com#frag", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Accepts an internal hostname containing an underscore")
    void shouldBuildReturnUrl_withUnderscoreHostname() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com", "/carlos")).isEqualTo("https://emr_dev.example.com/carlos/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:8443", "/carlos")).isEqualTo("https://emr_dev.example.com:8443/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Still rejects credentials and bad ports on an underscore hostname")
    void shouldFailClosed_whenUnderscoreHostnameIsUnsafe() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://user:pass@emr_dev.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:notaport", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com?x=1", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Rejects signed, empty, or out-of-range fallback ports")
    void shouldFailClosed_whenUnderscoreHostnamePortIsMalformed() {
        // Integer.parseInt would tolerate the leading sign; the validator must not.
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:-443", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:+443", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:99999", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Accepts an uppercase scheme regardless of default locale")
    void shouldBuildReturnUrl_withUppercaseScheme() {
        assertThat(EConsult2Action.buildSsoReturnUrl("HTTPS://emr.example.com", "/carlos")).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("HtTp://emr.example.com", "/carlos")).isEqualTo("http://emr.example.com/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Rejects a malformed multi-colon authority on an underscore hostname")
    void shouldFailClosed_whenUnderscoreHostnameAuthorityHasExtraColon() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:8080:9090", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Accepts a bracketed IPv6 configured base")
    void shouldBuildReturnUrl_withIpv6Host() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://[::1]", "/carlos")).isEqualTo("https://[::1]/carlos/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("https://[2001:db8::1]:9443", "/carlos")).isEqualTo("https://[2001:db8::1]:9443/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Flags misconfiguration when eConsult is set but the base URL is missing")
    void shouldReportMisconfigured_whenEconsultConfiguredWithoutBaseUrl() {
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("https://econsult.example.com", null)).isTrue();
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("https://econsult.example.com", "")).isTrue();
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("https://econsult.example.com", "   ")).isTrue();
    }

    @Test
    @DisplayName("Does not flag misconfiguration when the base URL is set or eConsult is unused")
    void shouldNotReportMisconfigured_whenBaseUrlPresentOrEconsultUnused() {
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("https://econsult.example.com", "https://emr.example.com")).isFalse();
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured(null, null)).isFalse();
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("", "")).isFalse();
    }

    /**
     * CARLOS direct-response contract fix for issue #3068
     * It ensures that {@code frontend()} and {@code login()} 
     * return {@link ActionSupport#NONE} immediately after a redirect. Also, if {@code sendRedirect} 
     * fails, it safely returns an {@code error} instead of just returning {@code null}.
     *
     * <p>Because the action sets up its dependencies (like the request, response, 
     * {@link SecurityInfoManager}, and {@link CarlosProperties}) the moment it is created, 
     * we must mock these dependencies <em>before</em> building the action. To make sure 
     * tests can run safely in parallel, {@code CarlosProperties} is mocked using a thread-local 
     * {@code mockStatic} instead of changing the global singleton.
     */
    @Nested
    @DisplayName("Redirect return-value contract (issue #3068)")
    class RedirectReturnValues {

        private MockedStatic<ServletActionContext> servletActionContextMock;
        private MockedStatic<CarlosProperties> carlosPropertiesMock;
        private MockHttpServletRequest mockRequest;
        private HttpServletResponse mockResponse;
        private EConsult2Action action;

        @BeforeEach
        void setUp() {
            // Provide valid, trusted URLs so both redirects build successfully; only the
            // sendRedirect outcome (success vs IOException) is varied per test.
            CarlosProperties mockProperties = mock(CarlosProperties.class);
            when(mockProperties.getProperty("frontendEconsultUrl")).thenReturn("https://frontend.example.com");
            when(mockProperties.getProperty("backendEconsultUrl")).thenReturn("https://econsult.example.com");
            when(mockProperties.getProperty("carlosBaseUrl")).thenReturn("https://emr.example.com");
            carlosPropertiesMock = mockStatic(CarlosProperties.class);
            carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

            // The constructor pulls SecurityInfoManager from SpringUtils (mocked by the base).
            registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));

            mockRequest = new MockHttpServletRequest();
            mockRequest.setContextPath("/carlos");
            mockResponse = mock(HttpServletResponse.class);

            servletActionContextMock = mockStatic(ServletActionContext.class);
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

            action = new EConsult2Action();
        }

        @AfterEach
        void tearDown() {
            if (servletActionContextMock != null) {
                servletActionContextMock.close();
            }
            if (carlosPropertiesMock != null) {
                carlosPropertiesMock.close();
            }
        }

        @Test
        @DisplayName("login() returns NONE after a successful redirect")
        void shouldReturnNone_whenLoginRedirectSucceeds() throws Exception {
            String result = action.login();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verify(mockResponse).sendRedirect(anyString());
        }

        @Test
        @DisplayName("login() returns the error result when the redirect throws IOException")
        void shouldReturnError_whenLoginRedirectThrowsIOException() throws Exception {
            doThrow(new IOException("redirect failed")).when(mockResponse).sendRedirect(anyString());

            assertThat(action.login()).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("frontend() returns NONE after a successful redirect")
        void shouldReturnNone_whenFrontendRedirectSucceeds() throws Exception {
            mockRequest.getSession().setAttribute("oneid_token", "token-value");
            mockRequest.getSession().setAttribute("oneIdEmail", "provider@example.com");

            String result = action.frontend();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verify(mockResponse).sendRedirect(anyString());
        }

        @Test
        @DisplayName("frontend() returns the error result when the redirect throws IOException")
        void shouldReturnError_whenFrontendRedirectThrowsIOException() throws Exception {
            mockRequest.getSession().setAttribute("oneid_token", "token-value");
            mockRequest.getSession().setAttribute("oneIdEmail", "provider@example.com");
            doThrow(new IOException("redirect failed")).when(mockResponse).sendRedirect(anyString());

            assertThat(action.frontend()).isEqualTo(ActionSupport.ERROR);
        }
    }
}
