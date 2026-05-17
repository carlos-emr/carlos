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

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.Level;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void shouldInjectCsrfGuardScript_forExtensionlessRequestToForwardedJsp() throws Exception {
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
    @DisplayName("should pass through unchanged when CSRFGuard is disabled")
    void shouldPassThroughUnchanged_whenCsrfGuardDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "<html><head><title>Provider</title></head><body></body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        withDisabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should skip AJAX request dispatch when request mapping is enabled")
    void shouldSkipAjaxRequestDispatch_whenRequestMappingEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clinical/AjaxEndpoint");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.REQUEST);
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should fail closed when CsrfGuard cannot initialize")
    void shouldFailClosed_whenCsrfGuardCannotInitialize() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/provider/providercontrol;jsessionid=secret-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class);
             LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenThrow(new IllegalStateException("csrf unavailable"));

            filter.doFilter(request, response, chain);

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("uri=/carlos/provider/providercontrol")
                        .doesNotContain("jsessionid")
                        .doesNotContain("secret-session");
            });
        }

        assertThat(response.getStatus()).isEqualTo(503);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should not inject duplicate CSRFGuard script")
    void shouldNotInjectDuplicateCsrfGuardScript_whenScriptAlreadyExists() throws Exception {
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
    @DisplayName("should pass through redirect responses without injection")
    void shouldPassThrough_whenDownstreamRedirects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendRedirect("/carlos/index");

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/index");
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should pass through error responses without injection")
    void shouldPassThrough_whenDownstreamSendsError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendError(403);

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should not inject script into HTML fragments")
    void shouldNotInjectScript_intoHtmlFragments() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fragment");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String fragment = "<tr><td>Partial row</td></tr>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(fragment);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(fragment);
    }

    @Test
    @DisplayName("should wrap forward dispatch even when AJAX header is present")
    void shouldWrapForwardDispatch_whenAjaxHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/WEB-INF/jsp/provider/providercontrol.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><head><title>Provider</title></head><body></body></html>");
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).contains("/carlos/csrfguard");
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

    @Test
    @DisplayName("should warn when non HTML content type contains full HTML")
    void shouldWarn_whenNonHtmlContentTypeContainsFullHtml() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clinical/JsonEndpoint");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/clinical/JsonEndpoint;jsessionid=secret-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "<!DOCTYPE html><html><head><title>Wrong type</title></head><body></body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("non-HTML Content-Type")
                        .contains("/carlos/clinical/JsonEndpoint")
                        .doesNotContain("jsessionid")
                        .doesNotContain("secret-session");
            });
        }
        assertThat(response.getContentAsString()).isEqualTo(body);
    }

    @Test
    @DisplayName("should throw when reset buffer fails before writing adjusted response")
    void shouldThrow_whenResetBufferFailsBeforeWritingAdjustedResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new ResetBufferFailingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter()
                    .write("<html><head><title>Provider</title></head><body></body></html>");
        };

        assertThatThrownBy(() -> withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot reset buffer before writing CSRF-adjusted response");
    }

    private void withEnabledCsrfGuard(Executable executable) throws Exception {
        CsrfGuard csrfGuard = mock(CsrfGuard.class);
        when(csrfGuard.isEnabled()).thenReturn(true);
        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenReturn(csrfGuard);
            try {
                executable.execute();
            } catch (Throwable t) {
                if (t instanceof Exception e) {
                    throw e;
                }
                throw new AssertionError(t);
            }
        }
    }

    private void withDisabledCsrfGuard(Executable executable) throws Exception {
        CsrfGuard csrfGuard = mock(CsrfGuard.class);
        when(csrfGuard.isEnabled()).thenReturn(false);
        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenReturn(csrfGuard);
            try {
                executable.execute();
            } catch (Throwable t) {
                if (t instanceof Exception e) {
                    throw e;
                }
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

    private static class ResetBufferFailingResponse extends MockHttpServletResponse {
        @Override
        public void resetBuffer() {
            throw new IllegalStateException("already committed");
        }
    }
}
