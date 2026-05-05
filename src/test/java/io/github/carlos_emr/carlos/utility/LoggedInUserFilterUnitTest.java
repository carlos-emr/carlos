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
package io.github.carlos_emr.carlos.utility;

import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the authenticated-session bootstrap filter.
 *
 * <p>These tests lock in the security-sensitive behavior introduced during the
 * Struts prepare/execute split: the filter must not create sessions, must
 * ignore anonymous sessions, and must derive {@link LoggedInInfo} only from an
 * already-authenticated session.</p>
 *
 * @since 2026-04-15
 */
@Tag("unit")
@DisplayName("LoggedInUserFilter")
class LoggedInUserFilterUnitTest {

    private LoggedInUserFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new LoggedInUserFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("should not create a session when no session exists")
    void shouldNotCreateSessionWhenNoSessionExists() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(request).getSession(false);
        verify(request, never()).getSession();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should ignore sessions that are not authenticated")
    void shouldIgnoreUnauthenticatedSession() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("user")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(session, never()).setAttribute(eq(new LoggedInInfo().getLoggedInInfoKey()), any());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should store derived LoggedInInfo for authenticated sessions")
    void shouldStoreDerivedLoggedInInfoForAuthenticatedSession() throws Exception {
        HttpSession session = mock(HttpSession.class);
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        Security security = new Security();
        security.setSecurityNo(7);

        when(request.getSession(false)).thenReturn(session);
        when(request.getRequestURI()).thenReturn("/carlos/provider/ViewAppointmentAdminDay");
        when(request.getLocale()).thenReturn(Locale.CANADA);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(session.getAttribute("user")).thenReturn("999998");
        when(session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER)).thenReturn(provider);
        when(session.getAttribute(SessionConstants.LOGGED_IN_SECURITY)).thenReturn(security);
        when(session.getAttribute(SessionConstants.CURRENT_FACILITY)).thenReturn(null);

        filter.doFilter(request, response, chain);

        ArgumentCaptor<LoggedInInfo> captor = ArgumentCaptor.forClass(LoggedInInfo.class);
        verify(session).setAttribute(eq(new LoggedInInfo().getLoggedInInfoKey()), captor.capture());
        assertThat(captor.getValue().getSession()).isSameAs(session);
        assertThat(captor.getValue().getLoggedInProvider()).isSameAs(provider);
        assertThat(captor.getValue().getLoggedInSecurity()).isSameAs(security);
        assertThat(captor.getValue().getLoggedInProviderNo()).isEqualTo("999998");
        assertThat(captor.getValue().getInitiatingCode()).isEqualTo("/carlos/provider/ViewAppointmentAdminDay");
        assertThat(captor.getValue().getLocale()).isEqualTo(Locale.CANADA);
        assertThat(captor.getValue().getIp()).isEqualTo("127.0.0.1");
        verify(chain).doFilter(request, response);
    }
}
