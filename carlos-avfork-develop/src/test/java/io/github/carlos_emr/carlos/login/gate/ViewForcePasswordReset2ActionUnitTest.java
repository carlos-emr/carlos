/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login.gate;

import io.github.carlos_emr.carlos.login.Login2Action;
import io.github.carlos_emr.carlos.login.LoginCredentialCache;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Direct unit coverage for the forced-password-reset view gate.
 *
 * <p>The POST action performs the password update; this gate only decides whether the reset form
 * may render. The security-sensitive contract is method restriction plus the short-lived
 * credential-cache token that proves the old password was already validated.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("ViewForcePasswordReset2Action")
class ViewForcePasswordReset2ActionUnitTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/forcepasswordreset");
        request.setMethod("GET");
        request.addPreferredLocale(Locale.ENGLISH);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should reject POST with method not allowed")
    void shouldRejectPost_withMethodNotAllowed() throws Exception {
        request.setMethod("POST");
        request.getSession(true);

        String result = new ViewForcePasswordReset2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
    }

    @Test
    @DisplayName("should redirect to login failure when session is missing")
    void shouldRedirectToLoginFailure_whenSessionMissing() throws Exception {
        String result = new ViewForcePasswordReset2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(response.getRedirectedUrl()).contains("errormsg=");
    }

    @Test
    @DisplayName("should redirect and clear token when credential token is not a string")
    void shouldRedirectAndClearToken_whenCredentialTokenIsNotString() throws Exception {
        request.getSession(true).setAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR, 12345);
        request.getSession(false).setAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR, "retry error");

        String result = new ViewForcePasswordReset2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR)).isNull();
    }

    @Test
    @DisplayName("should redirect and clear token when credential token is stale")
    void shouldRedirectAndClearToken_whenCredentialTokenStale() throws Exception {
        request.getSession(true).setAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR, "stale-token");
        request.getSession(false).setAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR, "retry error");

        String result = new ViewForcePasswordReset2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR)).isNull();
    }

    @Test
    @DisplayName("should return success with valid credential token")
    void shouldReturnSuccess_withValidCredentialToken() throws Exception {
        String token = LoginCredentialCache.getInstance().store(
                new LoginCredentialCache.LoginCredentials("carlosdoc", "encoded", "2026", null));
        request.getSession(true).setAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR, token);

        try {
            String result = new ViewForcePasswordReset2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(LoginCredentialCache.getInstance().peek(token)).isNotNull();
        } finally {
            LoginCredentialCache.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should move retry error from session to request")
    void shouldMoveRetryError_fromSessionToRequest() throws Exception {
        String token = LoginCredentialCache.getInstance().store(
                new LoginCredentialCache.LoginCredentials("carlosdoc", "encoded", "2026", null));
        request.getSession(true).setAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR, token);
        request.getSession(false).setAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR, "retry error");

        try {
            String result = new ViewForcePasswordReset2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("errormsg")).isEqualTo("retry error");
            assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR))
                    .isNull();
        } finally {
            LoginCredentialCache.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should ignore non string retry error")
    void shouldIgnoreRetryError_whenNotString() throws Exception {
        String token = LoginCredentialCache.getInstance().store(
                new LoginCredentialCache.LoginCredentials("carlosdoc", "encoded", "2026", null));
        Object nonStringError = new Object();
        request.getSession(true).setAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR, token);
        request.getSession(false).setAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR, nonStringError);

        try {
            String result = new ViewForcePasswordReset2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("errormsg")).isNull();
            assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR))
                    .isSameAs(nonStringError);
        } finally {
            LoginCredentialCache.getInstance().invalidate(token);
        }
    }
}
