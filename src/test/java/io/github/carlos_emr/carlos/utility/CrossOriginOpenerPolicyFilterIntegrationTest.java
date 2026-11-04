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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Exercises the real sanitizer/policy/defaults chain without a database fixture. */
@Tag("integration")
@Tag("security")
class CrossOriginOpenerPolicyFilterIntegrationTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRetainPolicy_whenSanitizerReplacesFailedResponse(boolean webService) throws Exception {
        var properties = mock(CarlosProperties.class);
        when(properties.getProperty(ResponseSanitizationFilter.ENABLED_PROPERTY, "")).thenReturn("true");
        try (var mockedProperties = mockStatic(CarlosProperties.class)) {
            mockedProperties.when(CarlosProperties::getInstance).thenReturn(properties);
            var sanitizer = new ResponseSanitizationFilter();
            sanitizer.init(new MockFilterConfig());
            var request = new MockHttpServletRequest("GET", webService ? "/carlos/ws/example" : "/carlos/example");
            request.setContextPath("/carlos");
            var response = new MockHttpServletResponse();
            var policy = new CrossOriginOpenerPolicyFilter();
            var defaults = new ResponseDefaultsFilter();
            FilterChain failing = (req, res) -> {
                var http = (HttpServletResponse) res;
                http.reset();
                http.getWriter().write("private diagnostic text");
                throw new IllegalStateException("synthetic endpoint failure");
            };
            sanitizer.doFilter(request, response, (req, res) ->
                    policy.doFilter(req, res, (r, s) -> defaults.doFilter(r, s, failing)));
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getHeader(CrossOriginOpenerPolicyFilter.HEADER)).isEqualTo("same-origin");
            assertThat(response.getContentAsString()).doesNotContain("private diagnostic text", "synthetic endpoint failure");
        }
    }
}
