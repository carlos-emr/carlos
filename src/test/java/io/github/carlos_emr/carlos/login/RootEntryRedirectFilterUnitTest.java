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
package io.github.carlos_emr.carlos.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("RootEntryRedirectFilter")
class RootEntryRedirectFilterUnitTest {

    private RootEntryRedirectFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        filter = new RootEntryRedirectFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        dispatcher = mock(RequestDispatcher.class);
    }

    @Test
    @DisplayName("should forward context root with trailing slash to login JSP")
    void shouldForwardContextRootWithTrailingSlashToLoginJsp() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestDispatcher("/WEB-INF/jsp/login/index.jsp")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should forward canonical index path to login JSP")
    void shouldForwardCanonicalIndexPathToLoginJsp() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/index");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestDispatcher("/WEB-INF/jsp/login/index.jsp")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should reject non view methods for login entry paths")
    void shouldRejectNonViewMethodsForLoginEntryPaths() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/index");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Allow", "GET, HEAD");
        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(chain, never()).doFilter(request, response);
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("should forward force password reset path to JSP when credential token is valid")
    void shouldForwardForcePasswordResetPathToJspWhenCredentialTokenIsValid() throws Exception {
        HttpSession session = mock(HttpSession.class);
        String token = LoginCredentialCache.getInstance().store(
                new LoginCredentialCache.LoginCredentials("carlosdoc", "encoded", "2026", null));

        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/forcepasswordreset");
        when(request.getMethod()).thenReturn("GET");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).thenReturn(token);
        when(request.getRequestDispatcher("/WEB-INF/jsp/login/forcepasswordreset.jsp")).thenReturn(dispatcher);

        try {
            filter.doFilter(request, response, chain);

            verify(dispatcher).forward(request, response);
            verify(chain, never()).doFilter(request, response);
        } finally {
            LoginCredentialCache.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should move force password reset retry error from session to request")
    void shouldMoveForcePasswordResetRetryErrorFromSessionToRequest() throws Exception {
        HttpSession session = mock(HttpSession.class);
        String token = LoginCredentialCache.getInstance().store(
                new LoginCredentialCache.LoginCredentials("carlosdoc", "encoded", "2026", null));

        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/forcepasswordreset");
        when(request.getMethod()).thenReturn("GET");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).thenReturn(token);
        when(session.getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR)).thenReturn("retry error");
        when(request.getRequestDispatcher("/WEB-INF/jsp/login/forcepasswordreset.jsp")).thenReturn(dispatcher);

        try {
            filter.doFilter(request, response, chain);

            verify(request).setAttribute("errormsg", "retry error");
            verify(session).removeAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR);
            verify(dispatcher).forward(request, response);
            verify(chain, never()).doFilter(request, response);
        } finally {
            LoginCredentialCache.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should redirect force password reset path when credential token is missing")
    void shouldRedirectForcePasswordResetPathWhenCredentialTokenIsMissing() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/forcepasswordreset");
        when(request.getMethod()).thenReturn("GET");
        when(request.getSession(false)).thenReturn(null);
        when(request.getLocale()).thenReturn(Locale.ENGLISH);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect(anyString());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should forward login failure path to JSP")
    void shouldForwardLoginFailurePathToJsp() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/loginfailed");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestDispatcher("/WEB-INF/jsp/login/loginfailed.jsp")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should reject non view methods for public login utility paths")
    void shouldRejectNonViewMethodsForPublicLoginUtilityPaths() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/loginfailed");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Allow", "GET, HEAD");
        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(chain, never()).doFilter(request, response);
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("should pass through server root when app is not deployed at root")
    void shouldPassThroughServerRootWhenAppIsNotDeployedAtRoot() throws Exception {
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("should pass through non root request")
    void shouldPassThroughNonRootRequest() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/provider/providercontrol");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }
}
