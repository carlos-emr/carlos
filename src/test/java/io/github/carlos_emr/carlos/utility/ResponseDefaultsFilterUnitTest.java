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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResponseDefaultsFilter}.
 *
 * @since 2026-05-30
 */
@Tag("unit")
@Tag("security")
@DisplayName("ResponseDefaultsFilter")
class ResponseDefaultsFilterUnitTest {

    @Test
    @DisplayName("should set Referrer-Policy same-origin before and after filter chain")
    void shouldSetReferrerPolicySameOrigin_beforeAndAfterFilterChain() throws Exception {
        ResponseDefaultsFilter filter = new ResponseDefaultsFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demographic/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            assertThat(httpResponse.getHeader("Referrer-Policy")).isEqualTo("same-origin");
            httpResponse.setHeader("Referrer-Policy", "unsafe-url");
        });

        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
    }
}
