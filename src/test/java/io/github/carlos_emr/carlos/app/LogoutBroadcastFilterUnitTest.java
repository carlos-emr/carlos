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

import io.github.carlos_emr.CarlosProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@link LogoutBroadcastFilter}.
 *
 * @since 2026-04-20
 */
@Tag("unit")
@DisplayName("LogoutBroadcastFilter")
class LogoutBroadcastFilterUnitTest {

    private LogoutBroadcastFilter filter;
    private MockedStatic<CarlosProperties> carlosPropertiesMock;
    private CarlosProperties carlosProperties;

    @BeforeEach
    void setUp() throws ServletException {
        carlosProperties = mock(CarlosProperties.class);

        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
        when(carlosProperties.getProperty("INACTIVITY_LIMIT_MINS")).thenReturn("60");

        filter = new LogoutBroadcastFilter();
        filter.init(mock(FilterConfig.class));
    }

    @AfterEach
    void tearDown() {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
    }

    @Test
    @DisplayName("should append logout script when response is flushed during rendering")
    void shouldAppendLogoutScript_whenResponseIsFlushedDuringRendering() throws Exception {
        String contextPath = "/carlos";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath(contextPath);
        HttpSession session = request.getSession(true);
        session.setAttribute("user", "123");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
            servletResponse.flushBuffer();
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains("<html><body>schedule</body></html>");
        assertThat(content).contains("window.__carlosLogoutActive=true;");
        assertThat(content).contains("BroadcastChannel('carlos_logout')");
        // The injected script stores the context path in a `cp` variable and concatenates it
        // onto the heartbeat URL at runtime. OWASP JS-encoding escapes the leading slash as \/.
        assertThat(content).contains("var cp='" + contextPath.replace("/", "\\/") + "';");
        assertThat(content).contains("fetch(cp+'/status/SessionHeartbeat?autoRefresh=true')");
    }

    @Test
    @DisplayName("should not append logout script when session user is missing")
    void shouldNotAppendLogoutScript_whenSessionUserIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.getSession(true);

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>login</body></html>");
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains("<html><body>login</body></html>");
        assertThat(content).doesNotContain("window.__carlosLogoutActive=true;");
    }

    @Test
    @DisplayName("should append logout script when response uses output stream")
    void shouldAppendLogoutScript_whenResponseUsesOutputStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        HttpSession session = request.getSession(true);
        session.setAttribute("user", "123");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getOutputStream().write("<html><body>stream</body></html>".getBytes(StandardCharsets.UTF_8));
            servletResponse.flushBuffer();
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains("<html><body>stream</body></html>");
        assertThat(content).contains("window.__carlosLogoutActive=true;");
    }

    @Test
    @DisplayName("should append logout script when writer is unavailable during injection")
    void shouldAppendLogoutScript_whenWriterIsUnavailableDuringInjection() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        HttpSession session = request.getSession(true);
        session.setAttribute("user", "123");

        MockResponseWithUnavailableWriter response = new MockResponseWithUnavailableWriter();

        FilterChain chain = (servletRequest, servletResponse) ->
                servletResponse.setContentType("text/html;charset=UTF-8");

        filter.doFilter(request, response, chain);

        String content = response.getBodyAsString();
        assertThat(content).contains("window.__carlosLogoutActive=true;");
    }

    /**
     * Mock response that refuses writer access so the filter must fall back to the output stream.
     *
     * @since 2026-04-20
     */
    private static class MockResponseWithUnavailableWriter extends MockHttpServletResponse {

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private final ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) throws IOException {
                body.write(b);
            }
        };

        @Override
        public PrintWriter getWriter() {
            throw new IllegalStateException("Writer unavailable");
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return outputStream;
        }

        /**
         * Returns the body written through the fallback output stream path.
         *
         * @return String response body captured by the mock output stream
         */
        String getBodyAsString() {
            return body.toString(StandardCharsets.UTF_8);
        }
    }
}
