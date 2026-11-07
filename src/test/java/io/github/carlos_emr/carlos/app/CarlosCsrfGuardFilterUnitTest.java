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
package io.github.carlos_emr.carlos.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.owasp.csrfguard.CsrfGuard;
import org.owasp.csrfguard.CsrfValidator;
import org.owasp.csrfguard.session.LogicalSession;
import org.owasp.csrfguard.token.service.TokenService;
import org.owasp.csrfguard.token.storage.LogicalSessionExtractor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CarlosCsrfGuardFilter}.
 *
 * @since 2026-05-30
 */
@Tag("unit")
@Tag("security")
@DisplayName("CarlosCsrfGuardFilter")
class CarlosCsrfGuardFilterUnitTest {

    private CarlosCsrfGuardFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new CarlosCsrfGuardFilter();
        filter.init(mock(FilterConfig.class));
    }

    @Test
    @DisplayName("should return 503 when CSRF token generation fails")
    void shouldReturnServiceUnavailable_whenTokenGenerationFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        CsrfGuard csrfGuard = mock(CsrfGuard.class);
        LogicalSessionExtractor logicalSessionExtractor = mock(LogicalSessionExtractor.class);
        LogicalSession logicalSession = mock(LogicalSession.class);
        TokenService tokenService = mock(TokenService.class);

        when(csrfGuard.isEnabled()).thenReturn(true);
        when(csrfGuard.getLogicalSessionExtractor()).thenReturn(logicalSessionExtractor);
        when(logicalSessionExtractor.extract(request)).thenReturn(logicalSession);
        when(logicalSession.getKey()).thenReturn("session-key");
        when(csrfGuard.getTokenService()).thenReturn(tokenService);
        doThrow(new IllegalStateException("token store unavailable"))
                .when(tokenService).generateTokensIfAbsent("session-key", "GET", "/provider/providercontrol");

        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class);
             MockedConstruction<CsrfValidator> csrfValidatorMock = mockConstruction(CsrfValidator.class,
                     (validator, context) -> when(validator.isValid(
                             any(HttpServletRequest.class), any(HttpServletResponse.class))).thenReturn(true))) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenReturn(csrfGuard);

            filter.doFilter(request, response, chain);
        }

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("should return 503 when CSRFGuard cannot initialize")
    void shouldReturnServiceUnavailable_whenCsrfGuardCannotInitialize() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance)
                    .thenThrow(new IllegalStateException("csrf unavailable"));

            filter.doFilter(request, response, chain);
        }

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }
}
