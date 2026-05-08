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

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.owasp.csrfguard.CsrfGuard;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CsrfGuardScriptInjectionFilter}.
 *
 * @since 2026-05-08
 */
@Tag("unit")
@Tag("security")
@DisplayName("CsrfGuardScriptInjectionFilter")
class CsrfGuardScriptInjectionFilterUnitTest {

    private CsrfGuardScriptInjectionFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new CsrfGuardScriptInjectionFilter();
        filter.init(mock(FilterConfig.class));
    }

    @Test
    @DisplayName("should inject CSRFGuard script for extensionless request to forwarded JSP")
    void shouldInjectCsrfguardScript_forExtensionlessRequestToForwardedJsp() throws Exception {
        // DemographicAdd is a representative extensionless Struts route from struts-demographic.xml.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demographic/DemographicAdd");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.REQUEST);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            MockHttpServletRequest forwardRequest = new MockHttpServletRequest("GET",
                    "/WEB-INF/jsp/demographic/demographicadd.jsp");
            forwardRequest.setContextPath("/carlos");
            forwardRequest.setDispatcherType(DispatcherType.FORWARD);

            filter.doFilter(forwardRequest, servletResponse, (forwardServletRequest, forwardServletResponse) -> {
                forwardServletResponse.setContentType("text/html;charset=UTF-8");
                forwardServletResponse.getWriter().write("<html><head><title>Add</title></head><body></body></html>");
            });
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        String content = response.getContentAsString();
        assertThat(content).contains("<script src=\"/carlos/csrfguard\"></script>\n</head>");
        assertThat(countOccurrences(content, "/csrfguard")).isEqualTo(1);
    }

    @Test
    @DisplayName("should not inject duplicate CSRFGuard script")
    void shouldNotInjectDuplicateCsrfguardScript_whenScriptAlreadyExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/encounter/IncomingEncounter");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><head><script src=\"/carlos/csrfguard\"></script></head>"
                    + "<body></body></html>");
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        String content = response.getContentAsString();
        assertThat(countOccurrences(content, "/csrfguard")).isEqualTo(1);
    }

    @Test
    @DisplayName("should pass through output-stream responses")
    void shouldPassThrough_whenResponseUsesOutputStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/OscarChartPrint");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/pdf");
            servletResponse.getOutputStream().write(body);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should pass through non-HTML writer responses when content type is set before writer")
    void shouldPassThrough_whenNonHtmlContentTypePrecedesWriter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clinical/JsonEndpoint");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "{\"status\":\"ok\"}";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should pass through non-HTML writer responses when content type follows writer")
    void shouldPassThrough_whenNonHtmlContentTypeFollowsWriter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clinical/JsonEndpoint");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "{\"status\":\"ok\"}";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
            PrintWriter writer = servletResponse.getWriter();
            servletResponse.setContentType("application/json;charset=UTF-8");
            writer.write(body);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
        assertThat(response.getContentLength()).isEqualTo(body.getBytes(StandardCharsets.UTF_8).length);
    }

    private void withEnabledCsrfGuard(Executable executable) throws Exception {
        CsrfGuard csrfGuard = mock(CsrfGuard.class);
        when(csrfGuard.isEnabled()).thenReturn(true);
        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenReturn(csrfGuard);
            try {
                executable.execute();
            } catch (Exception e) {
                throw e;
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }
    }

    private int countOccurrences(String content, String needle) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
