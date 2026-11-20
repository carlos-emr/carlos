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

import java.io.IOException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests the header at commit time, including redirects and explicit response resets. */
@Tag("unit")
@Tag("security")
class CrossOriginOpenerPolicyFilterUnitTest {
    private final CrossOriginOpenerPolicyFilter filter = new CrossOriginOpenerPolicyFilter();

    @ParameterizedTest
    @ValueSource(ints = {200, 302, 400, 403, 404, 500})
    void shouldSendOneSameOriginPolicy_whenResponseCommitsInsideChain(int status) throws Exception {
        var request = new MockHttpServletRequest("GET", "/carlos/admin/picker.html");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            var http = (HttpServletResponse) res;
            assertThat(http.getHeader(CrossOriginOpenerPolicyFilter.HEADER)).isEqualTo("same-origin");
            switch (status) {
                case 200 -> { http.getWriter().write("unchanged body"); http.flushBuffer(); }
                case 302 -> http.sendRedirect("/carlos/next");
                default -> http.sendError(status);
            }
        });
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeaders(CrossOriginOpenerPolicyFilter.HEADER)).containsExactly("same-origin");
        if (status == 200) assertThat(response.getContentAsString()).isEqualTo("unchanged body");
        if (status == 302) assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/next");
    }

    @ParameterizedTest
    @EnumSource(value = DispatcherType.class, names = {"REQUEST", "FORWARD", "ERROR", "ASYNC"})
    void shouldPreserveBinaryResponse_whenPolicyIsAppliedAcrossDispatches(DispatcherType dispatcher) throws Exception {
        var request = new MockHttpServletRequest();
        request.setDispatcherType(dispatcher);
        var response = new MockHttpServletResponse();
        byte[] bytes = {0, 1, -1, 10, 13};
        filter.doFilter(request, response, (req, res) -> {
            var http = (HttpServletResponse) res;
            http.reset();
            assertThat(http.getHeader(CrossOriginOpenerPolicyFilter.HEADER)).isEqualTo("same-origin");
            http.setContentType("application/pdf");
            http.addHeader("Set-Cookie", "session=synthetic; HttpOnly");
            http.getOutputStream().write(bytes);
            http.flushBuffer();
        });
        assertThat(response.getContentAsByteArray()).isEqualTo(bytes);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Set-Cookie")).isEqualTo("session=synthetic; HttpOnly");
        assertThat(response.getHeaders(CrossOriginOpenerPolicyFilter.HEADER)).containsExactly("same-origin");
    }

    @Test
    void shouldRetainPolicyAndRethrow_whenDownstreamFailsAfterReset() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var failure = new IOException("synthetic failure");
        jakarta.servlet.FilterChain failing = (req, res) -> {
            res.reset();
            throw failure;
        };
        assertThatThrownBy(() -> filter.doFilter(request, response, failing)).isSameAs(failure);
        assertThat(response.getHeader(CrossOriginOpenerPolicyFilter.HEADER)).isEqualTo("same-origin");
    }
}
