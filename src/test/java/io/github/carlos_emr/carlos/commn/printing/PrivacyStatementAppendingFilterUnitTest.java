/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.printing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import java.io.PrintWriter;
import java.io.Writer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the legacy privacy statement response appender.
 *
 * @since 2026-05-18
 */
@Tag("unit")
@DisplayName("PrivacyStatementAppendingFilter")
class PrivacyStatementAppendingFilterUnitTest {

    private static final int APPEND_BUFFER_SIZE_BYTES = 1024 * 1024;

    private PrivacyStatementAppendingFilter filter;

    @BeforeEach
    void setUp() throws ServletException {
        FilterConfig config = mock(FilterConfig.class);
        when(config.getInitParameter("exclusions"))
                .thenReturn("/provider/ViewSchedulePageJs");

        filter = new PrivacyStatementAppendingFilter();
        filter.init(config);
    }

    @Test
    @DisplayName("should match excluded route when servlet path is empty")
    void shouldMatchExcludedRoute_whenServletPathIsEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/provider/ViewSchedulePageJs");
        request.setContextPath("/carlos");
        request.setServletPath("");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        String body = "function schedulePage() { return 'ready'; }\n";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/javascript;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getLastRequestedBufferSize()).isZero();
    }

    @Test
    @DisplayName("should skip web service routes by excluded prefix")
    void shouldSkipWebServiceRoutes_byExcludedPrefix() throws Exception {
        FilterConfig config = mock(FilterConfig.class);
        when(config.getInitParameter("exclusions")).thenReturn("/ws");
        PrivacyStatementAppendingFilter localFilter = new PrivacyStatementAppendingFilter();
        localFilter.init(config);
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/ws/oauth/authorize");
        request.setContextPath("/carlos");
        request.setServletPath("/ws/oauth/authorize");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        String body = "<html><body>OAuth consent</body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        localFilter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getLastRequestedBufferSize()).isZero();
    }

    @Test
    @DisplayName("should flush non-HTML writer response when statement is skipped")
    void shouldFlushNonHtmlWriterResponse_whenStatementSkipped() throws Exception {
        PrivacyStatementAppendingFilter localFilter = new PrivacyStatementAppendingFilter();
        localFilter.init(mock(FilterConfig.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/OtherJs");
        request.setServletPath("/provider/OtherJs");
        BufferingWriterResponse response = new BufferingWriterResponse();
        String body = "function otherJs() { return 'ready'; }\n";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/javascript;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        localFilter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(APPEND_BUFFER_SIZE_BYTES);
    }

    private static class TrackingMockHttpServletResponse extends MockHttpServletResponse {

        private int lastRequestedBufferSize;

        @Override
        public void setBufferSize(int size) {
            lastRequestedBufferSize = size;
            super.setBufferSize(size);
        }

        int getLastRequestedBufferSize() {
            return lastRequestedBufferSize;
        }
    }

    private static class BufferingWriterResponse extends TrackingMockHttpServletResponse {

        private final StringBuilder pending = new StringBuilder();
        private final StringBuilder flushed = new StringBuilder();
        private PrintWriter bufferingWriter;

        @Override
        public PrintWriter getWriter() {
            if (bufferingWriter == null) {
                bufferingWriter = new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) {
                        pending.append(cbuf, off, len);
                    }

                    @Override
                    public void flush() {
                        flushed.append(pending);
                        pending.setLength(0);
                    }

                    @Override
                    public void close() {
                        flush();
                    }
                });
            }
            return bufferingWriter;
        }

        @Override
        public void flushBuffer() {
            // The filter must flush the writer, not rely on container buffer flush side effects.
        }

        @Override
        public String getContentAsString() {
            return flushed.toString();
        }
    }
}
