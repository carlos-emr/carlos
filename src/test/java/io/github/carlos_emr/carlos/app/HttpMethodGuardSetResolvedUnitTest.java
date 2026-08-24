/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Top-level regression coverage so Surefire discovers the Manage Emails mutation guard even when
 * nested test selection is unavailable or an individual class is selected with {@code -Dtest}.
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@DisplayName("HttpMethodGuardFilter setResolved protection")
class HttpMethodGuardSetResolvedUnitTest {

    private HttpMethodGuardFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        filter = new HttpMethodGuardFilter();
        FilterConfig filterConfig = mock(FilterConfig.class);
        when(filterConfig.getInitParameter("allowList")).thenReturn(null);
        filter.init(filterConfig);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    @DisplayName("should block GET with method=setResolved")
    void shouldBlockGetWithSetResolvedMethodParam() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/carlos/admin/ManageEmails");
        when(request.getParameter("method")).thenReturn("setResolved");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "GET requests are not allowed on this endpoint. Use POST.");
        verify(response).setHeader("Allow", "POST");
        verify(chain, never()).doFilter(request, response);
    }
}
