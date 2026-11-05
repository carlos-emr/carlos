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
import jakarta.servlet.ServletOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
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
    void shouldPublishNonHtmlWriterLength_beforeTheBodyCanCommit() throws Exception {
        for (boolean typeFirst : new boolean[] {false, true}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/text");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "x".repeat(APPEND_BUFFER_SIZE_BYTES + 1);
            filter.doFilter(request, response, (req, res) -> {
                res.setContentLength(body.length());
                if (typeFirst) {
                    res.setContentType("text/plain;charset=UTF-8");
                }
                var writer = res.getWriter();
                if (!typeFirst) {
                    res.setContentType("text/plain;charset=UTF-8");
                }
                assertThat(response.getContentLength()).isEqualTo(body.length());
                writer.write(body);
            });
            assertThat(response.getContentAsString()).isEqualTo(body);
            assertThat(response.getContentLength()).isEqualTo(body.length());
        }
    }

    @Test
    void shouldKeepMetadataLength_whenHtmlHasNoBodyChannel() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/print");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            res.setContentLength(12345);
        });
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentLength()).isEqualTo(12345);
    }

    @Test
    void shouldEncodeStreamNotice_withTheDeclaredResponseCharset() throws Exception {
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic café notice.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/print");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                res.setContentType("text/html;charset=ISO-8859-1");
                res.getOutputStream().write("body".getBytes(StandardCharsets.ISO_8859_1));
            });
            assertThat(new String(response.getContentAsByteArray(), StandardCharsets.ISO_8859_1))
                    .startsWith("body").contains("Synthetic café notice.");
        } finally {
            if (original == null) {
                properties.remove("confidentiality_statement.v1");
            } else {
                properties.put("confidentiality_statement.v1", original);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRetractPublishedLength_whenBufferResetDiscardsTheBody(boolean stream) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download");
        MockHttpServletResponse response = spy(new MockHttpServletResponse());
        filter.doFilter(request, response, (req, res) -> {
            res.setContentType(stream ? "application/octet-stream" : "text/plain;charset=UTF-8");
            res.setContentLength(4);
            if (stream) {
                res.getOutputStream().write(new byte[] {0, 1, 2, 3});
            } else {
                res.getWriter().write("abcd");
            }
            assertThat(response.getContentLength()).as("non-HTML length published before the body").isEqualTo(4);
            res.resetBuffer();
            // The published length described the discarded body; a pending one would be kept.
            // Spring's mock ignores null values, so the removal call itself is asserted here.
            verify(response).setHeader("Content-Length", null);
        });
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x-carlos-unsupported-5495", "not a charset"})
    void shouldEncodeStreamNoticeAsUtf8_whenDeclaredCharsetIsUnusable(String charset) throws Exception {
        // Tomcat 11 keeps an unsupported charset name visible through getCharacterEncoding()
        // after rejecting it, so the notice must not be the thing that fails the response.
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic café notice.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/print");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                res.setContentType("text/html");
                res.setCharacterEncoding(charset);
                res.getOutputStream().write("body".getBytes(StandardCharsets.US_ASCII));
            });
            assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                    .startsWith("body").contains("Synthetic café notice.");
        } finally {
            if (original == null) {
                properties.remove("confidentiality_statement.v1");
            } else {
                properties.put("confidentiality_statement.v1", original);
            }
        }
    }

    @Test
    void shouldDropPendingLength_onBufferResetAndOnFullReset() throws Exception {
        for (boolean fullReset : new boolean[] {false, true}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                res.setContentLength(4);
                if (fullReset) {
                    res.reset();
                } else {
                    res.resetBuffer();
                }
                res.setContentType("application/octet-stream");
                res.getOutputStream().write(new byte[] {0, 1, 2, 3});
            });
            assertThat(response.getHeader("Content-Length")).as("fullReset=%s", fullReset).isNull();
            assertThat(response.getContentAsByteArray()).containsExactly(0, 1, 2, 3);
        }
    }

    @Test
    void shouldIgnoreMalformedLengthHeader_andKeepTheBodyUnframed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            res.setContentType("application/octet-stream");
            ((jakarta.servlet.http.HttpServletResponse) res).setHeader("Content-Length", "not-a-number");
            res.getOutputStream().write(new byte[] {0, 1, 2, 3});
        });
        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getContentAsByteArray()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void shouldAppendNotice_whenContentTypeIsUpperCaseHtml() throws Exception {
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic case notice.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/print");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                res.setContentType("TEXT/HTML;charset=UTF-8");
                res.getWriter().write("<p>upper</p>");
            });
            assertThat(response.getContentAsString()).contains("<p>upper</p>", "Synthetic case notice.");
        } finally {
            if (original == null) {
                properties.remove("confidentiality_statement.v1");
            } else {
                properties.setProperty("confidentiality_statement.v1", (String) original);
            }
        }
    }

    @Test
    void shouldAppendNoticeToBothPages_whenNestedRequestSharesTheSession() throws Exception {
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic race notice.");
        try {
            MockHttpSession session = new MockHttpSession();
            MockHttpServletRequest outer = new MockHttpServletRequest("GET", "/print-a");
            MockHttpServletRequest inner = new MockHttpServletRequest("GET", "/print-b");
            outer.setSession(session);
            inner.setSession(session);
            MockHttpServletResponse outerResponse = new MockHttpServletResponse();
            MockHttpServletResponse innerResponse = new MockHttpServletResponse();
            filter.doFilter(outer, outerResponse, (req, res) -> {
                res.setContentType("text/html;charset=UTF-8");
                res.getWriter().write("<p>A</p>");
                // A second request on the same session arrives while the first is still rendering.
                filter.doFilter(inner, innerResponse, (req2, res2) -> {
                    res2.setContentType("text/html;charset=UTF-8");
                    res2.getWriter().write("<p>B</p>");
                });
            });
            assertThat(outerResponse.getContentAsString()).contains("<p>A</p>", "Synthetic race notice.");
            assertThat(innerResponse.getContentAsString()).contains("<p>B</p>", "Synthetic race notice.");
        } finally {
            if (original == null) {
                properties.remove("confidentiality_statement.v1");
            } else {
                properties.setProperty("confidentiality_statement.v1", (String) original);
            }
        }
    }

    @Test
    void shouldDeferOriginalLength_whenHtmlStreamGetsAPrivacyNotice() throws Exception {
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic length notice.");
        try {
            for (int form = 0; form < 6; form++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/print");
                MockHttpServletResponse response = new MockHttpServletResponse();
                int setter = form;
                filter.doFilter(request, response, (req, res) -> {
                    res.setContentType("text/html;charset=UTF-8");
                    var http = (jakarta.servlet.http.HttpServletResponse) res;
                    switch (setter) {
                        case 0 -> http.setContentLength(4);
                        case 1 -> http.setContentLengthLong(4);
                        case 2 -> http.setHeader("Content-Length", "4");
                        case 3 -> http.addHeader("Content-Length", "4");
                        case 4 -> http.setIntHeader("Content-Length", 4);
                        default -> http.addIntHeader("Content-Length", 4);
                    }
                    res.getOutputStream().write("body".getBytes(StandardCharsets.UTF_8));
                    assertThat(response.getContentLength()).isZero();
                });
                assertThat(response.getContentAsString()).startsWith("body").contains("Synthetic length notice.");
                assertThat(response.getHeader("Content-Length")).isNull();
            }
        } finally {
            if (original == null) {
                properties.remove("confidentiality_statement.v1");
            } else {
                properties.put("confidentiality_statement.v1", original);
            }
        }
    }

    @Test
    void shouldKeepBinaryLength_whenItIsDeclaredBeforeContentType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] payload = {0, 1, -1, 42};
        filter.doFilter(request, response, (req, res) -> {
            res.setContentLength(payload.length);
            var stream = res.getOutputStream();
            res.setContentType("application/octet-stream");
            assertThat(response.getContentLength()).isEqualTo(payload.length);
            stream.write(payload);
        });
        assertThat(response.getContentAsByteArray()).isEqualTo(payload);
        assertThat(response.getContentLength()).isEqualTo(payload.length);
    }

    @Test
    void shouldPublishStreamLength_whenContentTypeIsNeverSet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] payload = {0, 1, -1, 42};
        filter.doFilter(request, response, (req, res) -> {
            res.setContentLength(payload.length);
            var stream = res.getOutputStream();
            // An untyped stream can never be decorated: the length must be out before any
            // write or flush can commit the body, as it was before the wrapper deferred lengths.
            assertThat(response.getContentLength()).isEqualTo(payload.length);
            stream.write(payload);
        });
        assertThat(response.getContentType()).isNull();
        assertThat(response.getContentAsByteArray()).isEqualTo(payload);
        assertThat(response.getContentLength()).isEqualTo(payload.length);
    }

    @Test
    void shouldRetractUntypedStreamLength_whenTypeTurnsHtmlBeforeTheBody() throws Exception {
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic untyped notice.");
        try {
            MockHttpServletResponse response = spy(new MockHttpServletResponse());
            filter.doFilter(new MockHttpServletRequest("GET", "/print"), response, (req, res) -> {
                res.setContentLength(4);
                var stream = res.getOutputStream();
                assertThat(response.getContentLength()).isEqualTo(4);
                // The type arrives after the channel: the published length no longer describes
                // the decorated body, so it is retracted before anything is written.
                res.setContentType("text/html;charset=UTF-8");
                verify(response).setHeader("Content-Length", null);
                stream.write("body".getBytes(StandardCharsets.UTF_8));
            });
            assertThat(response.getContentAsString()).startsWith("body").contains("Synthetic untyped notice.");
        } finally {
            if (original == null) {
                properties.remove("confidentiality_statement.v1");
            } else {
                properties.put("confidentiality_statement.v1", original);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void shouldPublishNonHtmlLength_whenTypeHeaderIsSetAfterChannelSelection(boolean add, boolean useWriter)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            res.setContentLength(4);
            var writer = useWriter ? res.getWriter() : null;
            var stream = useWriter ? null : res.getOutputStream();
            var http = (jakarta.servlet.http.HttpServletResponse) res;
            if (add) {
                http.addHeader("cOnTeNt-TyPe", "text/plain;charset=UTF-8");
            } else {
                http.setHeader("cOnTeNt-TyPe", "text/plain;charset=UTF-8");
            }
            assertThat(response.getContentLength()).isEqualTo(4);
            if (useWriter) {
                writer.write("body");
            } else {
                stream.write("body".getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }
        });
        assertThat(response.getContentAsString()).isEqualTo("body");
        assertThat(response.getContentLength()).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "2,false", "0,true", "1,true", "2,true"})
    void shouldClearPublishedLength_whenContentTypeChangesToHtml(int api, boolean useWriter) throws Exception {
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic switch notice.");
        try {
            MockHttpServletResponse response = spy(new MockHttpServletResponse());
            filter.doFilter(new MockHttpServletRequest("GET", "/print"), response, (req, res) -> {
                res.setContentType("text/plain;charset=UTF-8");
                res.setContentLength(4);
                if (useWriter) {
                    res.getWriter();
                } else {
                    res.getOutputStream();
                }
                assertThat(response.getContentLength()).isEqualTo(4);
                var http = (jakarta.servlet.http.HttpServletResponse) res;
                switch (api) {
                    case 0 -> http.setContentType("text/html;charset=UTF-8");
                    case 1 -> http.setHeader("Content-Type", "text/html;charset=UTF-8");
                    default -> http.addHeader("Content-Type", "text/html;charset=UTF-8");
                }
                // Spring's mock ignores null values; real-Tomcat tests verify header removal.
                verify(response).setHeader("Content-Length", null);
                if (useWriter) {
                    res.getWriter().write("body");
                } else {
                    res.getOutputStream().write("body".getBytes(StandardCharsets.UTF_8));
                }
            });
            assertThat(response.getContentAsString()).startsWith("body").contains("Synthetic switch notice.");
            verify(response).setHeader("Content-Length", null);
        } finally {
            if (original == null) {
                properties.remove("confidentiality_statement.v1");
            } else {
                properties.put("confidentiality_statement.v1", original);
            }
        }
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
    @DisplayName("should mark the appended statement as print decoration")
    void shouldMarkAppendedStatement_asPrintDecoration() throws Exception {
        // The statement is appended to the eForm render page too, where the render browser
        // measures under print-media emulation (yesprint is display:block there). The
        // completeness gate withholds a document over UNMARKED off-page content, so without
        // carlos-print-decoration a configured confidentiality statement blocked every eForm
        // download on a packaged install. The marker is the gate's opt-in for platform
        // boilerplate: disclosed as decoration, never withheld over.
        io.github.carlos_emr.CarlosProperties props = io.github.carlos_emr.CarlosProperties.getInstance();
        props.setProperty("confidentiality_statement.v1", "Test confidentiality statement.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/eform/efmshowform_data");
            request.setServletPath("/eform/efmshowform_data");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "<html><body>form</body></html>";

            FilterChain chain = (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getWriter().write(body);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString())
                    .contains("Test confidentiality statement.")
                    .contains("class=\"yesprint carlos-print-decoration\"");
        } finally {
            props.remove("confidentiality_statement.v1");
        }
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

    @Test
    @DisplayName("should skip the statement when CSRFGuard combined its marker into the AJAX header")
    void shouldSkipStatement_whenCsrfGuardCombinedItsMarker() throws Exception {
        // jQuery sets X-Requested-With before send(); CSRFGuard's XHR hijack then calls
        // setRequestHeader again, and the XHR spec COMBINES repeated values with ", ". The
        // old exact-match check read that as a browser page request and appended the
        // confidentiality statement into the AJAX body, which the caller renders verbatim.
        io.github.carlos_emr.CarlosProperties props = io.github.carlos_emr.CarlosProperties.getInstance();
        props.setProperty("confidentiality_statement.v1", "Test confidentiality statement.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/casemgmt/viewNotes");
            request.setServletPath("/casemgmt/viewNotes");
            request.addHeader("X-Requested-With", "XMLHttpRequest, OWASP CSRFGuard Project");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "<div>note</div>";

            FilterChain chain = (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getWriter().write(body);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).isEqualTo(body);
        } finally {
            props.remove("confidentiality_statement.v1");
        }
    }

    @Test
    @DisplayName("should skip the statement when only the CSRFGuard marker is present")
    void shouldSkipStatement_whenOnlyCsrfGuardMarkerIsPresent() throws Exception {
        // carlos-ajax.js leaves the header to CSRFGuard to avoid a duplicated CSRF-TOKEN, so
        // its requests carry the marker on its own and never matched the old check at all.
        io.github.carlos_emr.CarlosProperties props = io.github.carlos_emr.CarlosProperties.getInstance();
        props.setProperty("confidentiality_statement.v1", "Test confidentiality statement.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/casemgmt/viewNotes");
            request.setServletPath("/casemgmt/viewNotes");
            request.addHeader("X-Requested-With", "OWASP CSRFGuard Project");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "<div>note</div>";

            FilterChain chain = (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getWriter().write(body);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).isEqualTo(body);
        } finally {
            props.remove("confidentiality_statement.v1");
        }
    }

    @Test
    @DisplayName("should still append the statement for an ordinary page request")
    void shouldStillAppendStatement_forOrdinaryPageRequest() throws Exception {
        // The widened AJAX test must not swallow the page case the filter exists for.
        io.github.carlos_emr.CarlosProperties props = io.github.carlos_emr.CarlosProperties.getInstance();
        props.setProperty("confidentiality_statement.v1", "Test confidentiality statement.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/casemgmt/viewNotes");
            request.setServletPath("/casemgmt/viewNotes");
            request.addHeader("X-Requested-With", "ShockwaveFlash/32.0");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getWriter().write("<html><body>notes</body></html>");
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).contains("Test confidentiality statement.");
        } finally {
            props.remove("confidentiality_statement.v1");
        }
    }

    @Test
    @DisplayName("should append through the writer when a caller probes the stream after the writer")
    void shouldAppendThroughWriter_whenCallerProbesStreamAfterWriter() throws Exception {
        // Issue #3446: GET /ws/oauth/authorize forwards to a JSP (writer), then CXF closes the
        // void JAX-RS response with getOutputStream().close() and swallows the container's
        // IllegalStateException. The wrapper used to record the stream as obtained before
        // that call failed, so the filter appended through the stream and the consent page
        // answered 500.
        io.github.carlos_emr.CarlosProperties props = io.github.carlos_emr.CarlosProperties.getInstance();
        props.setProperty("confidentiality_statement.v1", "Test confidentiality statement.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/oauth/authorize");
            request.setServletPath("/ws");
            ExclusiveChannelResponse response = new ExclusiveChannelResponse();
            String body = "<html><body>consent</body></html>";

            FilterChain chain = (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getWriter().write(body);
                try {
                    servletResponse.getOutputStream().close();
                } catch (IllegalStateException expected) {
                    // What CXF's AbstractHTTPDestination.closeResponseOutputStream does.
                }
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString())
                    .startsWith(body)
                    .contains("Test confidentiality statement.");
        } finally {
            props.remove("confidentiality_statement.v1");
        }
    }

    @Test
    @DisplayName("should append through the stream when a caller probes the writer after the stream")
    void shouldAppendThroughStream_whenCallerProbesWriterAfterStream() throws Exception {
        io.github.carlos_emr.CarlosProperties props = io.github.carlos_emr.CarlosProperties.getInstance();
        props.setProperty("confidentiality_statement.v1", "Test confidentiality statement.");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/casemgmt/viewNotes");
            request.setServletPath("/casemgmt/viewNotes");
            ExclusiveChannelResponse response = new ExclusiveChannelResponse();
            String body = "<html><body>notes</body></html>";

            FilterChain chain = (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                try {
                    servletResponse.getWriter();
                } catch (IllegalStateException expected) {
                    // A caller that tries the other channel and tolerates the refusal.
                }
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString())
                    .startsWith(body)
                    .contains("Test confidentiality statement.");
        } finally {
            props.remove("confidentiality_statement.v1");
        }
    }

    /**
     * Enforces the servlet rule a real container applies and MockHttpServletResponse does not:
     * once the writer or the output stream is obtained, asking for the other one throws.
     */
    private static class ExclusiveChannelResponse extends MockHttpServletResponse {

        private boolean writerObtained;
        private boolean streamObtained;

        @Override
        public PrintWriter getWriter() throws UnsupportedEncodingException {
            if (streamObtained) {
                throw new IllegalStateException("getOutputStream() has already been called for this response");
            }
            writerObtained = true;
            return super.getWriter();
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (writerObtained) {
                throw new IllegalStateException("getWriter() has already been called for this response");
            }
            streamObtained = true;
            return super.getOutputStream();
        }
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
