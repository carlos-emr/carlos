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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the public login-view forwarding filter.
 *
 * <p>The filter is intentionally narrow: it renders the public login GET/HEAD view and rejects
 * mutating methods for that route. The forced-reset view also gets a pre-CSRF method guard here,
 * but GET/HEAD still pass to Struts so token checks remain in the canonical action. Provider pages
 * should not be added here because they belong behind Struts gates.</p>
 */
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
    void shouldForwardContextRootWithTrailingSlash_toLoginJsp() throws Exception {
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
    void shouldForwardCanonicalIndexPath_toLoginJsp() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/index");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestDispatcher("/WEB-INF/jsp/login/index.jsp")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should forward HEAD index path to login JSP")
    void shouldForwardHeadIndexPath_toLoginJsp() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/index");
        when(request.getMethod()).thenReturn("HEAD");
        when(request.getRequestDispatcher("/WEB-INF/jsp/login/index.jsp")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should reject non view methods for login entry paths")
    void shouldRejectNonViewMethods_forLoginEntryPaths() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/index");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Allow", "GET, HEAD");
        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(chain, never()).doFilter(request, response);
        verify(dispatcher, never()).forward(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should pass force password reset view methods through to Struts")
    void shouldPassForcePasswordResetViewMethods_throughToStruts(String method) throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/forcepasswordreset");
        when(request.getMethod()).thenReturn(method);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
        verify(response, never()).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("should reject force password reset non view methods before CSRF")
    void shouldRejectForcePasswordResetNonViewMethods_beforeCsrf() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/forcepasswordreset");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Allow", "GET, HEAD");
        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(chain, never()).doFilter(request, response);
        verify(dispatcher, never()).forward(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/closenreload", "/errorpage", "/failure", "/loginfailed", "/logoutPage", "/securityError"})
    @DisplayName("should pass public utility pages through to Struts")
    void shouldPassPublicUtilityPages_throughToStruts(String publicPath) throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos" + publicPath);
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(dispatcher, never()).forward(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/closenreload", "/errorpage", "/failure", "/loginfailed", "/logoutPage", "/securityError"})
    @DisplayName("should not enforce method guard for Struts-owned public utility pages")
    void shouldNotEnforceMethodGuard_forStrutsOwnedPublicUtilityPages(String publicPath) throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos" + publicPath);
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("should forward server root when app is deployed at root")
    void shouldForwardServerRoot_whenAppIsDeployedAtRoot() throws Exception {
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestDispatcher("/WEB-INF/jsp/login/index.jsp")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should pass through non root request")
    void shouldPassThroughNonRoot_request() throws Exception {
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/provider/providercontrol");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }
}
