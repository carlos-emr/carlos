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
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.Agency;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.AgencyManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("PMMFilter authentication and core program context")
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

    // \u2003 (EM SPACE) pins the isBlank() contract: String.trim() only strips code points up to
    // U+0020, so a trim()-based guard would let this value through to the PMmodule handlers.
    @ParameterizedTest(name = "user=[{0}]")
    @ValueSource(strings = {"", "   ", "\t\n", "\u2003"})
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("should populate authenticated PMM context without optional CAISI security manager")
    void shouldPopulateProgramContext_whenOptionalCaisiManagerAbsent(boolean admin)
            throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.getSession().setAttribute("user", "1001");
        request.getServletContext().setAttribute("agency", new Agency());
        ProviderManager providers = mock(ProviderManager.class);
        SecUserRoleDao roles = mock(SecUserRoleDao.class);
        List<ProgramProvider> domain = List.of(new ProgramProvider());
        when(providers.getProgramDomain("1001")).thenReturn(domain);
        when(roles.hasAdminRole("1001")).thenReturn(admin);
        try (StaticWebApplicationContext context = new StaticWebApplicationContext()) {
            context.getBeanFactory().registerSingleton("providerManager", providers);
            context.getBeanFactory().registerSingleton("agencyManager", mock(AgencyManager.class));
            context.getBeanFactory().registerSingleton("secUserRoleDao", roles);
            context.refresh();
            request.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
            filter.init(new MockFilterConfig(request.getServletContext()));
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertThat(request.getSession().getAttribute("program_domain")).isSameAs(domain);
            assertThat(request.getSession().getAttribute("pmm_admin")).isEqualTo(admin);
            verify(roles).hasAdminRole("1001");
            verify(chain).doFilter(request, response);
        }
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                CONTEXT_PATH + "/PMmodule/ProgramManager.do");
        request.setContextPath(CONTEXT_PATH);
        request.setRequestURI(CONTEXT_PATH + "/PMmodule/ProgramManager.do");
        return request;
    }
}
