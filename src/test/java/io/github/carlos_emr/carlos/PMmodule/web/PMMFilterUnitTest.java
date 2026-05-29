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
package io.github.carlos_emr.carlos.PMmodule.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@Tag("security")
@DisplayName("PMMFilter unauthenticated access handling")
class PMMFilterUnitTest extends CarlosUnitTestBase {

    private static final String CONTEXT_PATH = "/carlos";

    private final PMMFilter filter = new PMMFilter();

    @Test
    @DisplayName("should redirect to logout page and stop chain when user missing")
    void shouldRedirectToLogoutPage_whenUserMissing()
            throws ServletException, IOException {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
        verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("should redirect to logout page and stop chain when user blank")
    void shouldRedirectToLogoutPage_whenUserBlank(String oscarUser)
            throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.getSession().setAttribute("user", oscarUser);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
        verify(chain, never()).doFilter(any(), any());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                CONTEXT_PATH + "/PMmodule/ProgramManager.do");
        request.setContextPath(CONTEXT_PATH);
        request.setRequestURI(CONTEXT_PATH + "/PMmodule/ProgramManager.do");
        return request;
    }
}
