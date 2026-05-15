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
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.Level;
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

    private static final int HTML_INJECTION_BUFFER_SIZE_BYTES = 1024 * 1024;

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

        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

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
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should not append logout script when session user is missing")
    void shouldNotAppendLogoutScript_whenSessionUserIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.getSession(true);

        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>login</body></html>");
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains("<html><body>login</body></html>");
        assertThat(content).doesNotContain("window.__carlosLogoutActive=true;");
        assertThat(response.getSetBufferSizeCallCount()).isZero();
    }

    @Test
    @DisplayName("should not configure large buffer for anonymous public pages")
    void shouldNotConfigureLargeBuffer_whenAnonymousPublicPageRenders() throws Exception {
        assertAnonymousHtmlFastPath("/index");
        assertAnonymousHtmlFastPath("/loginfailed");
    }

    @Test
    @DisplayName("should append logout script when login creates session during rendering")
    void shouldAppendLogoutScript_whenLoginCreatesSessionDuringRendering() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setContextPath("/carlos");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            ((MockHttpServletRequest) servletRequest).getSession(true).setAttribute("user", "123");
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains("<html><body>schedule</body></html>");
        assertThat(content).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should not wrap static library or share assets")
    void shouldNotWrapStaticAssets_whenAuthenticatedSessionExists() throws Exception {
        assertStaticAssetFastPath("/library/jquery/jquery-3.7.1.min.js");
        assertStaticAssetFastPath("/share/javascript/Oscar.js");
    }

    @Test
    @DisplayName("should suppress stale content length when logout script is appended")
    void shouldSuppressStaleContentLength_whenLogoutScriptIsAppended() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.getSession(true).setAttribute("user", "123");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        String body = "<html><body>schedule</body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
            servletResponse.getWriter().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should not configure large buffer for authenticated AJAX HTML response")
    void shouldNotConfigureLargeBuffer_whenAuthenticatedAjaxHtmlResponseRenders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.getSession(true).setAttribute("user", "123");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>ajax</body></html>");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains("<html><body>ajax</body></html>");
        assertThat(response.getContentAsString()).doesNotContain("window.__carlosLogoutActive=true;");
        assertThat(response.getSetBufferSizeCallCount()).isZero();
    }

    @Test
    @DisplayName("should preserve content length without large buffer for JSON response")
    void shouldPreserveContentLengthWithoutLargeBuffer_whenAuthenticatedJsonResponseRenders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/status/SomeJsonAction");
        request.setContextPath("/carlos");
        request.getSession(true).setAttribute("user", "123");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        String body = "{\"valid\":true}";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
            servletResponse.getWriter().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getHeader("Content-Length"))
                .isEqualTo(String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
        assertThat(response.getSetBufferSizeCallCount()).isZero();
    }

    @Test
    @DisplayName("should clear deferred content length when downstream resets buffer")
    void shouldClearDeferredContentLength_whenDownstreamResetsBuffer() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/status/SomeJsonAction");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setContentLength(99);
            servletResponse.resetBuffer();
            servletResponse.getWriter().write("{}");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo("{}");
        assertThat(response.getHeader("Content-Length")).isNull();
    }

    @Test
    @DisplayName("should clear deferred content length when downstream resets response")
    void shouldClearDeferredContentLength_whenDownstreamResetsResponse() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/status/SomeJsonAction");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setContentLength(99);
            servletResponse.reset();
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.getWriter().write("{}");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo("{}");
        assertThat(response.getHeader("Content-Length")).isNull();
    }

    @Test
    @DisplayName("should warn when downstream sets malformed content length")
    void shouldWarn_whenDownstreamSetsMalformedContentLength() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/status/SomeJsonAction");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            ((HttpServletResponse) servletResponse).setHeader("Content-Length", "bad-length<script>");
            servletResponse.getWriter().write("{}");
        };

        try (LogCapture capture = LogCapture.forLogger(LogoutBroadcastFilter.class)) {
            filter.doFilter(request, response, chain);

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Ignoring malformed Content-Length header value");
            });
        }
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

    @Test
    @DisplayName("should log error when script append flush fails")
    void shouldLogError_whenScriptAppendFlushFails() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        request.setRequestURI("/carlos/provider/providercontrol;jsessionid=abc123");
        OutputStreamAppendFailingResponse response = new OutputStreamAppendFailingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getOutputStream().write("<html><body>schedule</body></html>"
                    .getBytes(StandardCharsets.UTF_8));
            response.failWrites();
        };

        try (LogCapture capture = LogCapture.forLogger(LogoutBroadcastFilter.class)) {
            filter.doFilter(request, response, chain);

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Skipping logout broadcast script injection")
                        .contains("uri=/carlos/provider/providercontrol");
            });
        }
    }

    @Test
    @DisplayName("should log error when writer and output stream append paths fail")
    void shouldLogError_whenWriterAndOutputStreamAppendPathsFail() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        MockResponseWithNoAppendOutput response = new MockResponseWithNoAppendOutput();

        FilterChain chain = (servletRequest, servletResponse) ->
                servletResponse.setContentType("text/html;charset=UTF-8");

        try (LogCapture capture = LogCapture.forLogger(LogoutBroadcastFilter.class)) {
            filter.doFilter(request, response, chain);

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("response writer was unavailable and the output stream write failed");
            });
        }
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

    private void assertStaticAssetFastPath(String path) throws Exception {
        MockHttpServletRequest request = authenticatedRequest(path);
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/javascript;charset=UTF-8");
            servletResponse.getWriter().write("window.assetLoaded=true;");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo("window.assetLoaded=true;");
        assertThat(response.getSetBufferSizeCallCount()).isZero();
    }

    private MockHttpServletRequest authenticatedRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos" + path);
        request.getSession(true).setAttribute("user", "123");
        return request;
    }

    private void assertAnonymousHtmlFastPath(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setContextPath("/carlos");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>public</body></html>");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains("<html><body>public</body></html>");
        assertThat(response.getContentAsString()).doesNotContain("window.__carlosLogoutActive=true;");
        assertThat(response.getSetBufferSizeCallCount()).isZero();
    }

    private static class TrackingMockHttpServletResponse extends MockHttpServletResponse {

        private int setBufferSizeCallCount;
        private Integer lastRequestedBufferSize;

        @Override
        public void setBufferSize(int size) {
            setBufferSizeCallCount++;
            lastRequestedBufferSize = size;
            super.setBufferSize(size);
        }

        int getSetBufferSizeCallCount() {
            return setBufferSizeCallCount;
        }

        Integer getLastRequestedBufferSize() {
            return lastRequestedBufferSize;
        }
    }

    private static class MockResponseWithNoAppendOutput extends MockHttpServletResponse {
        @Override
        public PrintWriter getWriter() {
            throw new IllegalStateException("Writer unavailable");
        }

        @Override
        public ServletOutputStream getOutputStream() {
            throw new IllegalStateException("Output stream unavailable");
        }
    }

    private static class OutputStreamAppendFailingResponse extends MockHttpServletResponse {

        private boolean failWrites;

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
                if (failWrites) {
                    throw new IOException("append failed");
                }
            }
        };

        @Override
        public ServletOutputStream getOutputStream() {
            return outputStream;
        }

        void failWrites() {
            failWrites = true;
        }
    }
}
