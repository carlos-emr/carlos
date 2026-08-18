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
import jakarta.servlet.RequestDispatcher;
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
 * Locks section-root compatibility onto the allowlisted migrated routes without
 * reintroducing generic index resolution.
 *
 * @since 2026-04-15
 */
@Tag("unit")
@DisplayName("SectionRootCompatibilityFilter")
class SectionRootCompatibilityFilterUnitTest {

    private SectionRootCompatibilityFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        filter = new SectionRootCompatibilityFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        dispatcher = mock(RequestDispatcher.class);
    }

    @Test
    @DisplayName("should forward administration root with trailing slash")
    void shouldForwardAdministrationRootWithTrailingSlash() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/administration/");
        when(request.getRequestDispatcher("/administration/index")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should forward administration root without trailing slash")
    void shouldForwardAdministrationRootWithoutTrailingSlash() throws Exception {
        when(request.getMethod()).thenReturn("HEAD");
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/administration");
        when(request.getRequestDispatcher("/administration/index")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should pass through unsupported mutator method")
    void shouldPassThroughUnsupportedMutatorMethod() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/administration/");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("should pass through unrelated section path")
    void shouldPassThroughUnrelatedSectionPath() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRequestURI()).thenReturn("/carlos/prevention/");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(dispatcher, never()).forward(request, response);
    }
}
