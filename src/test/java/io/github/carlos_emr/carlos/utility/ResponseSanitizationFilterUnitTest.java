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
import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResponseSanitizationFilter}.
 *
 * <p>Verifies that stack traces are stripped from error responses while normal
 * responses and committed responses pass through unmodified.</p>
 *
 * @since 2026-04-06
 */
@Tag("unit")
@Tag("security")
@DisplayName("ResponseSanitizationFilter")
class ResponseSanitizationFilterUnitTest {

    private ResponseSanitizationFilter filter;
    private MockedStatic<CarlosProperties> carlosPropertiesMock;

    @Mock
    private CarlosProperties mockProperties;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

        filter = new ResponseSanitizationFilter();
        FilterConfig filterConfig = mock(FilterConfig.class);

        // Default: enabled
        when(mockProperties.getProperty(ResponseSanitizationFilter.ENABLED_PROPERTY, ""))
                .thenReturn("true");
        filter.init(filterConfig);
    }

    @AfterEach
    void tearDown() {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
    }

    // -------------------------------------------------------------------------
    // containsStackTrace() — static helper, tested independently
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("enabled property parsing")
    class EnabledPropertyParsing {

        @Test
        @DisplayName("should keep sanitization enabled when configured with truthy alias")
        void shouldKeepSanitizationEnabled_whenConfiguredWithTruthyAlias() {
            assertThat(ResponseSanitizationFilter.parseEnabledProperty("yes")).isTrue();
            assertThat(ResponseSanitizationFilter.parseEnabledProperty("on")).isTrue();
            assertThat(ResponseSanitizationFilter.parseEnabledProperty("1")).isTrue();
        }

        @Test
        @DisplayName("should disable sanitization when configured with falsy alias")
        void shouldDisableSanitization_whenConfiguredWithFalsyAlias() {
            assertThat(ResponseSanitizationFilter.parseEnabledProperty("no")).isFalse();
            assertThat(ResponseSanitizationFilter.parseEnabledProperty("off")).isFalse();
            assertThat(ResponseSanitizationFilter.parseEnabledProperty("0")).isFalse();
        }

        @Test
        @DisplayName("should warn and keep sanitization enabled when property is unrecognized")
        void shouldWarnAndKeepSanitizationEnabled_whenPropertyIsUnrecognized() {
            try (LogCapture capture = LogCapture.forLogger(ResponseSanitizationFilter.class)) {
                assertThat(ResponseSanitizationFilter.parseEnabledProperty("ture\r\nfalse")).isTrue();

                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("Unrecognized response.sanitization.enabled value")
                            .contains("\\r\\n");
                });
            }
        }
    }

    @Nested
    @DisplayName("containsStackTrace()")
    class ContainsStackTrace {

        @Test
        @DisplayName("should return false for null body")
        void shouldReturnFalse_forNullBody() {
            assertThat(ResponseSanitizationFilter.containsStackTrace(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for empty body")
        void shouldReturnFalse_forEmptyBody() {
            assertThat(ResponseSanitizationFilter.containsStackTrace("")).isFalse();
        }

        @Test
        @DisplayName("should return false for plain HTML error page")
        void shouldReturnFalse_forPlainHtmlErrorPage() {
            String html = "<html><body><h1>An error occurred</h1>"
                    + "<p>Please contact support.</p></body></html>";
            assertThat(ResponseSanitizationFilter.containsStackTrace(html)).isFalse();
        }

        @Test
        @DisplayName("should return true for body containing stack frame line")
        void shouldReturnTrue_forStackFrameLine() {
            String body = "Error\n\tat io.github.carlos_emr.carlos.SomeClass.method(SomeClass.java:42)";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @ParameterizedTest
        @DisplayName("should return true for stack frame line variations")
        @ValueSource(strings = {
                "Error\r\nat io.github.carlos_emr.carlos.SomeClass.method(SomeClass.java:42)",
                "Error\r\n\tat io.github.carlos_emr.carlos.SomeClass.method(SomeClass.java:42)"
        })
        void shouldReturnTrue_forStackFrameLineVariations(String body) {
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body starting with stack frame line")
        void shouldReturnTrue_forLeadingStackFrameLine() {
            String body = "at io.github.carlos_emr.carlos.SomeClass.method(SomeClass.java:42)";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body starting with whitespace before stack frame line")
        void shouldReturnTrue_forLeadingWhitespaceStackFrameLine() {
            String body = "  \tat io.github.carlos_emr.carlos.SomeClass.method(SomeClass.java:42)";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for constructor stack frame line")
        void shouldReturnTrue_forConstructorStackFrameLine() {
            String body = "Error\n\tat ca.example.SomeClass.<init>(SomeClass.java:42)";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body containing Caused by:")
        void shouldReturnTrue_forCausedByMarker() {
            String body = "java.lang.RuntimeException: Something failed\n"
                    + "Caused by: java.lang.NullPointerException";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body containing java.lang. class name")
        void shouldReturnTrue_forJavaLangClassName() {
            String body = "<pre>java.lang.NullPointerException: null</pre>";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body containing CARLOS class name")
        void shouldReturnTrue_forCarlosClassName() {
            String body = "io.github.carlos_emr.carlos.tickler.TicklerManager threw an exception";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body containing jakarta.servlet. class name")
        void shouldReturnTrue_forJakartaServletClassName() {
            String body = "jakarta.servlet.ServletException: wrapped error";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body containing org.apache. class name")
        void shouldReturnTrue_forApacheClassName() {
            String body = "org.apache.catalina.connector.Request failed";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body containing org.springframework. class name")
        void shouldReturnTrue_forSpringClassName() {
            String body = "org.springframework.web.util.NestedServletException: Request processing failed";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for body containing org.hibernate. class name")
        void shouldReturnTrue_forHibernateClassName() {
            String body = "org.hibernate.exception.ConstraintViolationException: could not execute statement";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @Test
        @DisplayName("should return true for a realistic full stack trace body")
        void shouldReturnTrue_forRealisticFullStackTrace() {
            String body = "HTTP Status 500 - Internal Server Error\n\n"
                    + "java.lang.NullPointerException\n"
                    + "\tat io.github.carlos_emr.carlos.tickler.TicklerManager.getTicklers(TicklerManager.java:123)\n"
                    + "\tat io.github.carlos_emr.carlos.tickler.pageUtil.AddTickler2Action.execute(AddTickler2Action.java:45)\n"
                    + "Caused by: java.lang.IllegalStateException: something bad\n"
                    + "\t... 42 more\n";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isTrue();
        }

        @ParameterizedTest
        @DisplayName("should return false for plain text stack trace near misses")
        @ValueSource(strings = {
                "An error occurred at line 10 of the configuration file.",
                "notjava.lang.Exception is just prose",
                "notCaused by: this is ordinary text",
                "foo_java.lang.Exception is part of an identifier",
                "1java.lang.Exception is part of a token",
                "foo_Caused by: is embedded in a larger word",
                "1Caused by: is embedded in a larger token"
        })
        void shouldReturnFalse_forPlainTextNearMisses(String body) {
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isFalse();
        }

        @Test
        @DisplayName("should complete in time for adversarial stack frame near miss")
        void shouldCompleteInTime_forAdversarialStackFrameNearMiss() {
            String body = "\nat "
                    + "a.".repeat(ResponseSanitizationFilter.MAX_CAPTURE_CHARS / 4)
                    + "method("
                    + "x".repeat(ResponseSanitizationFilter.MAX_CAPTURE_CHARS / 4);

            boolean result = assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> ResponseSanitizationFilter.containsStackTrace(body));

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should complete in time for repeated stack frame prefixes")
        void shouldCompleteInTime_forRepeatedStackFramePrefixes() {
            // Exercises the repeated-line path where each candidate exits before an open paren.
            String body = ("\nat " + "a".repeat(80) + ".method\n")
                    .repeat(ResponseSanitizationFilter.MAX_CAPTURE_CHARS / 90);

            boolean result = assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> ResponseSanitizationFilter.containsStackTrace(body));

            assertThat(result).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // doFilter() — disabled filter
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when filter is disabled")
    class DisabledFilter {

        @BeforeEach
        void disableFilter() throws Exception {
            when(mockProperties.getProperty(ResponseSanitizationFilter.ENABLED_PROPERTY, ""))
                    .thenReturn("false");
            filter = new ResponseSanitizationFilter();
            FilterConfig filterConfig = mock(FilterConfig.class);
            filter.init(filterConfig);
        }

        @Test
        @DisplayName("should pass through error response with stack trace unchanged")
        void shouldPassThroughErrorWithStackTrace_whenDisabled() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            String stackTraceBody = "java.lang.NullPointerException\n"
                    + "\tat io.github.carlos_emr.carlos.Foo.bar(Foo.java:1)";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write(stackTraceBody);
            };

            filter.doFilter(request, response, chain);

            // Stack trace must NOT be sanitized when filter is disabled
            assertThat(response.getContentAsString()).contains("NullPointerException");
        }
    }

    // -------------------------------------------------------------------------
    // doFilter() — normal pass-through scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("pass-through scenarios")
    class PassThrough {

        @Test
        @DisplayName("should pass through 200 response without modification")
        void shouldPassThrough_normalOkResponse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/index");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "<html><body>Welcome to CARLOS EMR</body></html>";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("text/html");
                res.getWriter().write(body);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo(body);
        }

        @Test
        @DisplayName("should overwrite stale Content-Length when replaying safe writer response")
        void shouldOverwriteStaleContentLength_whenReplayingSafeWriterResponse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/provider/providercontrol");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "<html><body>Provider control</body></html>";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("text/html;charset=UTF-8");
                httpRes.setContentLength(0);
                res.getWriter().write(body);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo(body);
            assertThat(response.getHeader("Content-Length"))
                    .isEqualTo(String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
        }

        @Test
        @DisplayName("should pass through 404 response without stack trace unchanged")
        void shouldPassThroughWithoutSanitization_whenStatusIs404() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/missing.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "<html><body><h1>Not Found</h1></body></html>";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(404);
                httpRes.setContentType("text/html");
                res.getWriter().write(body);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsString()).isEqualTo(body);
        }

        @Test
        @DisplayName("should pass through response when getWriter() is never called")
        void shouldPassThrough_whenWriterNeverCalled() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/index");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // Chain that sets status but writes nothing
            FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).isEmpty();
        }

        @Test
        @DisplayName("should pass through binary response written via getOutputStream() unchanged")
        void shouldPassThrough_binaryResponseViaOutputStream() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/report.pdf");
            MockHttpServletResponse response = new MockHttpServletResponse();
            // Non-text bytes including values that could corrupt if decoded as text
            byte[] binaryBody = new byte[]{0x00, 0x01, 0x02, 0x0F, 0x10, 0x1F,
                    0x20, 0x7F, (byte) 0x80, (byte) 0xFF};

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("application/pdf");
                res.getOutputStream().write(binaryBody);
            };

            filter.doFilter(request, response, chain);

            // Binary content must reach the client unchanged — no buffering, no corruption
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray()).isEqualTo(binaryBody);
        }
    }

    // -------------------------------------------------------------------------
    // doFilter() — sanitization scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("sanitization scenarios")
    class Sanitization {

        @Test
        @DisplayName("should sanitize 500 response containing a stack trace")
        void shouldSanitizeStackTrace_whenResponseStatusIs500() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/index");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String stackTraceBody = "java.lang.NullPointerException\n"
                    + "\tat io.github.carlos_emr.carlos.SomeClass.method(SomeClass.java:42)\n"
                    + "\tat org.apache.catalina.Dispatcher.invoke(Dispatcher.java:123)";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write(stackTraceBody);
            };

            filter.doFilter(request, response, chain);

            // Status preserved
            assertThat(response.getStatus()).isEqualTo(500);
            // Stack trace content replaced
            String sanitized = response.getContentAsString();
            assertThat(sanitized).doesNotContain("NullPointerException");
            assertThat(sanitized).doesNotContain("at io.github");
            assertThat(sanitized).doesNotContain("org.apache.catalina");
            // Generic page contains status code and reference ID
            assertThat(sanitized).contains("500");
            assertThat(sanitized).contains("Reference ID:");
        }

        @Test
        @DisplayName("should sanitize 404 response containing a stack trace")
        void shouldSanitizeStackTrace_whenStatusIs404() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/missing");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "org.springframework.web.servlet.NoHandlerFoundException\n"
                    + "\tat org.springframework.web.DispatcherServlet.dispatch(DispatcherServlet.java:99)";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(404);
                httpRes.setContentType("text/html");
                res.getWriter().write(body);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(404);
            String sanitized = response.getContentAsString();
            assertThat(sanitized).doesNotContain("org.springframework");
            assertThat(sanitized).contains("404");
            assertThat(sanitized).contains("Reference ID:");
        }

        @Test
        @DisplayName("should include Reference ID in sanitized response body")
        void shouldIncludeReferenceId_inSanitizedResponse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/AddTickler");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write("java.lang.RuntimeException: db error\n"
                        + "\tat io.github.carlos_emr.carlos.dao.TicklerDao.save(TicklerDao.java:55)");
            };

            filter.doFilter(request, response, chain);

            String responseBody = response.getContentAsString();
            // Reference ID: followed by a UUID-format string
            assertThat(responseBody).containsPattern(
                    "Reference ID:\\s+[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("should set content-type to text/html on sanitized response")
        void shouldSetContentTypeHtml_onSanitizedResponse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/error.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/plain");
                res.getWriter().write("java.lang.Exception\n"
                        + "\tat io.github.carlos_emr.carlos.Foo.bar(Foo.java:1)");
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentType()).contains("text/html");
        }

        @Test
        @DisplayName("should sanitize text error written through output stream")
        void shouldSanitizeTextError_whenWrittenThroughOutputStream() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/error.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/plain;charset=UTF-8");
                res.getOutputStream().write(("java.lang.IllegalStateException: failed\n"
                        + "\tat io.github.carlos_emr.carlos.ErrorPage.render(ErrorPage.java:42)")
                        .getBytes(StandardCharsets.UTF_8));
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
            assertThat(response.getContentAsString()).contains("Reference ID:");
            assertThat(response.getContentAsString()).doesNotContain("IllegalStateException");
            assertThat(response.getContentAsString()).doesNotContain("io.github.carlos_emr");
        }

        @Test
        @DisplayName("should sanitize late error after output stream was opened")
        void shouldSanitizeLateError_whenOutputStreamOpenedBeforeStatusChange() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/error.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                res.getOutputStream().write(("java.lang.IllegalStateException: failed\n"
                        + "\tat io.github.carlos_emr.carlos.ErrorPage.render(ErrorPage.java:42)")
                        .getBytes(StandardCharsets.UTF_8));
                ((HttpServletResponse) res).setStatus(500);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
            assertThat(response.getContentAsString()).contains("Reference ID:");
            assertThat(response.getContentAsString()).doesNotContain("IllegalStateException");
            assertThat(response.getContentAsString()).doesNotContain("io.github.carlos_emr");
        }

        @Test
        @DisplayName("should pass through successful PDF output stream")
        void shouldPassThroughSuccessfulPdf_whenWrittenThroughOutputStream() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/Download/report.pdf");
            MockHttpServletResponse response = new MockHttpServletResponse();
            byte[] pdfBytes = "%PDF-1.7\nbody".getBytes(StandardCharsets.UTF_8);

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("application/pdf");
                res.getOutputStream().write(pdfBytes);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray()).isEqualTo(pdfBytes);
        }

        @Test
        @DisplayName("should pass through successful HTML output stream")
        void shouldPassThroughSuccessfulHtml_whenWrittenThroughOutputStream() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/index");
            MockHttpServletResponse response = new MockHttpServletResponse();
            byte[] htmlBytes = "<html><body><input id=\"username\"></body></html>"
                    .getBytes(StandardCharsets.UTF_8);

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("text/html;charset=UTF-8");
                res.getOutputStream().write(htmlBytes);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
            assertThat(response.getContentAsByteArray()).isEqualTo(htmlBytes);
        }

        @Test
        @DisplayName("should sanitize oversized error response before passthrough")
        void shouldSanitizeOversizedErrorResponse_beforePassthrough() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/error.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String stackTraceBody = "java.lang.IllegalStateException: failed\n"
                    + "\tat io.github.carlos_emr.carlos.ErrorPage.render(ErrorPage.java:42)\n"
                    + "A".repeat(ResponseSanitizationFilter.MAX_CAPTURE_CHARS + 1);

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write(stackTraceBody);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            String sanitized = response.getContentAsString();
            assertThat(sanitized).doesNotContain("IllegalStateException");
            assertThat(sanitized).doesNotContain("io.github.carlos_emr");
            assertThat(sanitized).contains("Reference ID:");
            assertThat(response.getHeader("Content-Length"))
                    .isEqualTo(String.valueOf(response.getContentAsByteArray().length));
        }

        @Test
        @DisplayName("should drop entity headers when replacing stack trace body")
        void shouldDropEntityHeaders_whenReplacingStackTraceBody() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/Download/report.pdf");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                httpRes.setHeader("Content-Disposition", "attachment; filename=report.pdf");
                httpRes.setHeader("Content-Encoding", "gzip");
                httpRes.setHeader("ETag", "\"old\"");
                httpRes.setHeader("X-Frame-Options", "SAMEORIGIN");
                res.getWriter().write("java.lang.IllegalStateException: failed\n"
                        + "\tat io.github.carlos_emr.carlos.ErrorPage.render(ErrorPage.java:42)");
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getHeader("Content-Encoding")).isNull();
            assertThat(response.getHeader("ETag")).isNull();
            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
            assertThat(response.getContentAsString()).contains("Reference ID:");
        }

        @Test
        @DisplayName("should not log stack trace body when sanitizing error")
        void shouldNotLogStackTraceBody_whenSanitizingError() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET", "/carlos/error.jsp;jsessionid=secret123");
            request.setRequestURI("/carlos/error.jsp;jsessionid=secret123");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String body = "java.lang.IllegalStateException: demographic_no=42\n"
                    + "\tat io.github.carlos_emr.carlos.ErrorPage.render(ErrorPage.java:42)";
            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write(body);
            };

            try (LogCapture capture = LogCapture.forLogger(ResponseSanitizationFilter.class)) {
                filter.doFilter(request, response, chain);

                String logText = capture.events().stream()
                        .map(event -> event.getMessage().getFormattedMessage())
                        .reduce("", (left, right) -> left + "\n" + right);
                assertThat(logText)
                        .doesNotContain("demographic_no")
                        .doesNotContain("secret123")
                        .doesNotContain("jsessionid");
                assertThat(logText).contains("/carlos/error.jsp");
            }
        }

        @Test
        @DisplayName("should sanitize oversized error response even without stack trace prefix")
        void shouldSanitizeOversizedErrorResponse_evenWithoutStackTracePrefix() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/error.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String largeErrorBody = "<html><body>"
                    + "A".repeat(ResponseSanitizationFilter.MAX_CAPTURE_CHARS + 1)
                    + "java.lang.IllegalStateException: leaked later\n"
                    + "\tat io.github.carlos_emr.carlos.ErrorPage.render(ErrorPage.java:42)"
                    + "</body></html>";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write(largeErrorBody);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            String sanitized = response.getContentAsString();
            assertThat(sanitized).contains("Reference ID:");
            assertThat(sanitized).doesNotContain("leaked later");
            assertThat(sanitized).doesNotContain("io.github.carlos_emr");
        }

        @Test
        @DisplayName("should sanitize oversized error response even without stack trace markers")
        void shouldSanitizeOversizedErrorResponse_whenNoStackTraceMarkersExist() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/error.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String largeErrorBody = "<html><body>"
                    + "A".repeat(ResponseSanitizationFilter.MAX_CAPTURE_CHARS + 1)
                    + "</body></html>";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write(largeErrorBody);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString()).contains("Reference ID:");
            assertThat(response.getContentAsString()).doesNotContain(largeErrorBody);
        }

        @Test
        @DisplayName("should log discarded bytes after oversized error response is sanitized")
        void shouldLogDiscardedBytes_afterOversizedErrorResponseSanitized() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/error.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String stackTraceBody = "java.lang.IllegalStateException: failed\n"
                    + "\tat io.github.carlos_emr.carlos.ErrorPage.render(ErrorPage.java:42)\n"
                    + "A".repeat(ResponseSanitizationFilter.MAX_CAPTURE_CHARS + 1);

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write(stackTraceBody);
                res.getWriter().write("tail");
                res.getWriter().flush();
            };

            try (LogCapture capture = LogCapture.forLogger(ResponseSanitizationFilter.class)) {
                filter.doFilter(request, response, chain);

                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("discarded")
                            .contains("bytes after sanitized large-error response");
                });
            }
        }

        @Test
        @DisplayName("should warn when sanitized error cannot be sent after commit")
        void shouldWarn_whenSanitizedErrorCannotBeSentAfterCommit() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/error.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setStatus(500);
            response.setCommitted(true);
            String stackTraceBody = "java.lang.IllegalStateException: failed\n"
                    + "\tat io.github.carlos_emr.carlos.ErrorPage.render(ErrorPage.java:42)\n"
                    + "A".repeat(ResponseSanitizationFilter.MAX_CAPTURE_CHARS + 1);

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setContentType("text/html");
                res.getWriter().write(stackTraceBody);
            };

            try (LogCapture capture = LogCapture.forLogger(ResponseSanitizationFilter.class)) {
                filter.doFilter(request, response, chain);

                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("Cannot send sanitized error")
                            .contains("response already committed");
                });
            }
        }

        @Test
        @DisplayName("should throw when captured response cannot reset buffer before replay")
        void shouldThrow_whenCapturedResponseCannotResetBufferBeforeReplay() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/page.jsp");
            MockHttpServletResponse response = new ResetBufferFailingResponse();
            String body = "<html><body>ok</body></html>";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("text/html");
                res.getWriter().write(body);
            };

            assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Cannot reset buffer before replaying captured response");
        }
    }

    // -------------------------------------------------------------------------
    // doFilter() — web-service (/ws) 5xx partial-body leak (issue #2953)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("web-service (/ws) error responses")
    class WebServiceErrors {

        private MockHttpServletRequest wsRequest(String method) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/carlos/ws/rs/schedule/getAppointment");
            request.setRequestURI("/carlos/ws/rs/schedule/getAppointment");
            request.setServletPath("/ws");
            request.setPathInfo("/rs/schedule/getAppointment");
            return request;
        }

        @Test
        @DisplayName("should sanitize 500 partial-JSON body without stack trace on /ws route")
        void shouldSanitizePartialJsonBody_whenStatusIs500OnWebServiceRoute() throws Exception {
            MockHttpServletRequest request = wsRequest("POST");
            MockHttpServletResponse response = new MockHttpServletResponse();
            // Mid-stream Jackson failure: a clean, well-formed JSON prefix with PHI is already
            // written, then the status flips to 500. No stack-trace markers are present.
            String partialPhiJson = "{\"appointmentNo\":1234,\"demographic\":{\"firstName\":\"Jane\","
                    + "\"lastName\":\"Doe\",\"phone\":\"250-555-0143\",\"patientStatus\":\"AC\"}";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("application/json");
                res.getWriter().write(partialPhiJson);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            String sanitized = response.getContentAsString();
            assertThat(sanitized)
                    .doesNotContain("Jane")
                    .doesNotContain("Doe")
                    .doesNotContain("250-555-0143")
                    .doesNotContain("patientStatus")
                    .contains("Reference ID:");
        }

        @Test
        @DisplayName("should sanitize 500 partial-JSON body written through output stream on /ws route")
        void shouldSanitizePartialJsonBody_whenWrittenThroughOutputStreamAfterStatus500() throws Exception {
            MockHttpServletRequest request = wsRequest("POST");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String partialPhiJson = "{\"demographic\":{\"firstName\":\"Jane\",\"hin\":\"9999999999\"}";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("application/json;charset=UTF-8");
                res.getOutputStream().write(partialPhiJson.getBytes(StandardCharsets.UTF_8));
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            String sanitized = response.getContentAsString();
            assertThat(sanitized)
                    .doesNotContain("Jane")
                    .doesNotContain("9999999999")
                    .contains("Reference ID:");
        }

        @Test
        @DisplayName("should pass through 400 JSON error body on /ws route unchanged")
        void shouldPassThrough_whenStatusIs400OnWebServiceRoute() throws Exception {
            MockHttpServletRequest request = wsRequest("POST");
            MockHttpServletResponse response = new MockHttpServletResponse();
            // Legitimate REST client-error envelope — must NOT be blanked, callers depend on it.
            String errorEnvelope = "{\"error\":\"validation_failed\",\"field\":\"appointmentNo\"}";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(400);
                httpRes.setContentType("application/json");
                res.getWriter().write(errorEnvelope);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString()).isEqualTo(errorEnvelope);
        }

        @Test
        @DisplayName("should pass through 200 JSON body on /ws route unchanged")
        void shouldPassThrough_whenStatusIs200OnWebServiceRoute() throws Exception {
            MockHttpServletRequest request = wsRequest("GET");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String okJson = "{\"appointmentNo\":1234,\"status\":\"booked\"}";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("application/json");
                res.getWriter().write(okJson);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo(okJson);
        }

        @Test
        @DisplayName("should pass through 500 body without stack trace on a non-/ws route")
        void shouldPassThrough_when500WithoutStackTraceOnNonWebServiceRoute() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/provider/providercontrol");
            request.setRequestURI("/carlos/provider/providercontrol");
            request.setServletPath("/provider/providercontrol");
            MockHttpServletResponse response = new MockHttpServletResponse();
            // A normal (non web-service) 500 HTML page with no stack trace keeps its existing
            // pass-through behaviour — this change is scoped to /ws routes only.
            String htmlError = "<html><body><h1>An error occurred</h1></body></html>";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("text/html");
                res.getWriter().write(htmlError);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString()).isEqualTo(htmlError);
        }

        @Test
        @DisplayName("should sanitize 500 stack-trace body on /ws route through the full filter")
        void shouldSanitizeStackTraceBody_whenStatusIs500OnWebServiceRoute() throws Exception {
            MockHttpServletRequest request = wsRequest("POST");
            MockHttpServletResponse response = new MockHttpServletResponse();
            // A /ws 500 carrying an actual stack trace must be sanitized via the stack-trace
            // trigger, exactly as it would on any other route — verified end-to-end here.
            String stackTraceBody = "java.lang.NullPointerException\n"
                    + "\tat io.github.carlos_emr.carlos.ws.rs.ScheduleService.getAppointment(ScheduleService.java:88)";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.setContentType("application/json");
                res.getWriter().write(stackTraceBody);
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            String sanitized = response.getContentAsString();
            assertThat(sanitized)
                    .doesNotContain("NullPointerException")
                    .doesNotContain("io.github.carlos_emr")
                    .contains("Reference ID:");
        }
    }

    @Nested
    @DisplayName("sanitizationReason()")
    class SanitizationReason {

        @Test
        @DisplayName("should report stack-trace reason for any error status with markers")
        void shouldReportStackTraceReason_forAnyErrorWithMarkers() {
            String body = "java.lang.NullPointerException\n\tat io.github.carlos_emr.carlos.Foo.bar(Foo.java:1)";
            assertThat(ResponseSanitizationFilter.sanitizationReason(404, body, false))
                    .isEqualTo(ResponseSanitizationFilter.REASON_STACK_TRACE);
            assertThat(ResponseSanitizationFilter.sanitizationReason(500, body, true))
                    .isEqualTo(ResponseSanitizationFilter.REASON_STACK_TRACE);
        }

        @Test
        @DisplayName("should report web-service 5xx reason for clean /ws 5xx body")
        void shouldReportWebService5xxReason_forCleanWebService5xxBody() {
            assertThat(ResponseSanitizationFilter.sanitizationReason(500, "{\"phi\":\"x\"}", true))
                    .isEqualTo(ResponseSanitizationFilter.REASON_WEB_SERVICE_5XX);
            assertThat(ResponseSanitizationFilter.sanitizationReason(503, null, true))
                    .isEqualTo(ResponseSanitizationFilter.REASON_WEB_SERVICE_5XX);
        }

        @Test
        @DisplayName("should return null when the body must not be sanitized")
        void shouldReturnNull_whenBodyMustNotBeSanitized() {
            // Successful status, /ws 4xx without stack trace, and non-/ws 5xx without stack trace.
            assertThat(ResponseSanitizationFilter.sanitizationReason(200, "{\"phi\":\"x\"}", true)).isNull();
            assertThat(ResponseSanitizationFilter.sanitizationReason(404, "{\"error\":\"x\"}", true)).isNull();
            assertThat(ResponseSanitizationFilter.sanitizationReason(500, "plain page", false)).isNull();
        }
    }

    @Nested
    @DisplayName("shouldSanitizeErrorBody()")
    class ShouldSanitizeErrorBody {

        @Test
        @DisplayName("should sanitize web-service 5xx regardless of stack-trace content")
        void shouldSanitize_forWebService5xxWithoutStackTrace() {
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(500, "{\"phi\":\"x\"}", true)).isTrue();
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(503, "clean body", true)).isTrue();
        }

        @Test
        @DisplayName("should not sanitize web-service 4xx without stack trace")
        void shouldNotSanitize_forWebService4xxWithoutStackTrace() {
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(400, "{\"error\":\"x\"}", true)).isFalse();
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(404, "not found", true)).isFalse();
        }

        @Test
        @DisplayName("should not sanitize non-web-service 5xx without stack trace")
        void shouldNotSanitize_forNonWebService5xxWithoutStackTrace() {
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(500, "plain page", false)).isFalse();
        }

        @Test
        @DisplayName("should sanitize any error status with stack-trace markers")
        void shouldSanitize_forAnyErrorWithStackTrace() {
            String body = "java.lang.NullPointerException\n\tat io.github.carlos_emr.carlos.Foo.bar(Foo.java:1)";
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(404, body, false)).isTrue();
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(500, body, false)).isTrue();
        }

        @Test
        @DisplayName("should not sanitize successful responses")
        void shouldNotSanitize_forSuccessfulResponses() {
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(200, "{\"phi\":\"x\"}", true)).isFalse();
            assertThat(ResponseSanitizationFilter.shouldSanitizeErrorBody(302, "", true)).isFalse();
        }
    }

    @Nested
    @DisplayName("isWebServiceRequest()")
    class IsWebServiceRequest {

        @Test
        @DisplayName("should return true when servlet path is /ws")
        void shouldReturnTrue_whenServletPathIsWs() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/ws/rs/schedule/getAppointment");
            request.setServletPath("/ws");
            assertThat(ResponseSanitizationFilter.isWebServiceRequest(request)).isTrue();
        }

        @Test
        @DisplayName("should return true when context-relative request URI is under /ws/")
        void shouldReturnTrue_whenContextRelativeUriIsUnderWs() {
            // No servlet path populated — exercises the context-relative URI fallback.
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setContextPath("/carlos");
            request.setServletPath("");
            request.setRequestURI("/carlos/ws/rs/demographics/1");
            assertThat(ResponseSanitizationFilter.isWebServiceRequest(request)).isTrue();
        }

        @Test
        @DisplayName("should return false for a non-web-service route")
        void shouldReturnFalse_forNonWebServiceRoute() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/provider/providercontrol");
            request.setRequestURI("/carlos/provider/providercontrol");
            request.setServletPath("/provider/providercontrol");
            assertThat(ResponseSanitizationFilter.isWebServiceRequest(request)).isFalse();
        }

        @Test
        @DisplayName("should not match an unrelated path that merely contains the letters ws")
        void shouldReturnFalse_forUnrelatedPathContainingWs() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/news/list");
            request.setRequestURI("/carlos/news/list");
            request.setServletPath("/news/list");
            assertThat(ResponseSanitizationFilter.isWebServiceRequest(request)).isFalse();
        }

        @Test
        @DisplayName("should not match a deeper path that merely contains /ws/ as a later segment")
        void shouldReturnFalse_whenWsAppearsAsLaterPathSegment() {
            // Fallback path: servlet path empty, URI has /ws/ deep in the path but not at the
            // context-relative root. A substring match would wrongly classify this as a /ws route.
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/proxy/ws/foo");
            request.setContextPath("/carlos");
            request.setServletPath("");
            request.setRequestURI("/carlos/proxy/ws/foo");
            assertThat(ResponseSanitizationFilter.isWebServiceRequest(request)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // doFilter() — committed response (sendError / sendRedirect)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("committed responses (sendError / sendRedirect)")
    class CommittedResponses {

        @Test
        @DisplayName("should not interfere with sendError responses")
        void shouldNotInterfere_withSendErrorResponse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/secure.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // Simulate a filter calling sendError (e.g. CSRFGuard on a missing token)
            FilterChain chain = (req, res) -> ((HttpServletResponse) res).sendError(403, "Forbidden");

            filter.doFilter(request, response, chain);

            // Status set by sendError is preserved; no content was written via getWriter()
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("should not interfere with sendRedirect responses")
        void shouldNotInterfere_withSendRedirectResponse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/secure.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) ->
                    ((HttpServletResponse) res).sendRedirect("/carlos/login.jsp");

            filter.doFilter(request, response, chain);

            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/login.jsp");
        }
    }

    // -------------------------------------------------------------------------
    // doFilter() — uncaught exception from chain
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("uncaught exception from chain")
    class UncaughtException {

        @Test
        @DisplayName("should return 500 sanitized response for uncaught RuntimeException")
        void shouldReturn500SanitizedResponse_forUncaughtRuntimeException() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/Save");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                throw new RuntimeException("Database connection failed");
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            String body = response.getContentAsString();
            assertThat(body).doesNotContain("Database connection failed");
            assertThat(body).contains("Reference ID:");
        }

        @Test
        @DisplayName("should return 500 sanitized response for uncaught IOException")
        void shouldReturn500SanitizedResponse_forUncaughtIOException() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/report.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                throw new IOException("Connection reset by peer");
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            String body = response.getContentAsString();
            assertThat(body).doesNotContain("Connection reset");
            assertThat(body).contains("Reference ID:");
        }

        @Test
        @DisplayName("should return 500 sanitized response for uncaught ServletException")
        void shouldReturn500SanitizedResponse_forUncaughtServletException() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/page.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                throw new ServletException("Servlet init failed");
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            String body = response.getContentAsString();
            assertThat(body).doesNotContain("Servlet init failed");
            assertThat(body).contains("Reference ID:");
        }
    }

    // -------------------------------------------------------------------------
    // DISPLAY_ERROR — requires BOTH properties for full developer error display
    // response.sanitization.enabled=false is the gating control; DISPLAY_ERROR
    // alone does NOT bypass RSF sanitization.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when DISPLAY_ERROR is set but sanitization is still enabled")
    class DisplayErrorWithSanitizationEnabled {

        @BeforeEach
        void initWithDisplayErrorOnlyAndSanitizationOn() throws Exception {
            // response.sanitization.enabled=true (default) AND DISPLAY_ERROR=true:
            // sanitization is NOT bypassed — DISPLAY_ERROR alone is not sufficient.
            when(mockProperties.getProperty(ResponseSanitizationFilter.ENABLED_PROPERTY, ""))
                    .thenReturn("true");
            when(mockProperties.isPropertyActive(ResponseSanitizationFilter.DISPLAY_ERROR_PROPERTY))
                    .thenReturn(true);
            filter = new ResponseSanitizationFilter();
            FilterConfig filterConfig = mock(FilterConfig.class);
            filter.init(filterConfig);
        }

        @Test
        @DisplayName("should still sanitize stack trace when only DISPLAY_ERROR is set")
        void shouldStillSanitize_whenOnlyDisplayErrorIsSet()
                throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            String stackTraceBody = "java.lang.NullPointerException\n"
                    + "\tat io.github.carlos_emr.carlos.SomeClass.method(SomeClass.java:42)";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.getWriter().write(stackTraceBody);
            };

            filter.doFilter(request, response, chain);

            // RSF remains active — DISPLAY_ERROR alone does not bypass sanitization.
            assertThat(response.getContentAsString()).doesNotContain("NullPointerException");
            assertThat(response.getContentAsString()).contains("Reference ID:");
        }

        @Test
        @DisplayName("should replace stack trace body with Reference ID when only DISPLAY_ERROR is set")
        void shouldReplaceWithReferenceId_whenOnlyDisplayErrorIsSet() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.getWriter().write("java.lang.RuntimeException: sensitive detail");
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).doesNotContain("RuntimeException");
            assertThat(response.getContentAsString()).contains("Reference ID:");
        }
    }

    @Nested
    @DisplayName("when both DISPLAY_ERROR and sanitization=false are set (developer mode)")
    class DisplayErrorWithSanitizationDisabled {

        @BeforeEach
        void initWithBothPropertiesSet() throws Exception {
            // response.sanitization.enabled=false AND DISPLAY_ERROR=true:
            // both must be set — this is the correct two-property developer configuration.
            when(mockProperties.getProperty(ResponseSanitizationFilter.ENABLED_PROPERTY, ""))
                    .thenReturn("false");
            when(mockProperties.isPropertyActive(ResponseSanitizationFilter.DISPLAY_ERROR_PROPERTY))
                    .thenReturn(true);
            filter = new ResponseSanitizationFilter();
            FilterConfig filterConfig = mock(FilterConfig.class);
            filter.init(filterConfig);
        }

        @Test
        @DisplayName("should pass through stack trace unchanged when both properties are set")
        void shouldPassThroughStackTrace_whenBothPropertiesSet() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            String stackTraceBody = "java.lang.NullPointerException\n"
                    + "\tat io.github.carlos_emr.carlos.SomeClass.method(SomeClass.java:42)";

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.getWriter().write(stackTraceBody);
            };

            filter.doFilter(request, response, chain);

            // RSF is disabled by response.sanitization.enabled=false — raw details pass through.
            assertThat(response.getContentAsString()).isEqualTo(stackTraceBody);
        }

        @Test
        @DisplayName("should not replace body with Reference ID when both properties are set")
        void shouldNotReplaceWithReferenceId_whenBothPropertiesSet() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(500);
                httpRes.getWriter().write("java.lang.RuntimeException: raw developer detail");
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).contains("RuntimeException");
            assertThat(response.getContentAsString()).doesNotContain("Reference ID:");
        }
    }

    private static class ResetBufferFailingResponse extends MockHttpServletResponse {
        @Override
        public void resetBuffer() {
            throw new IllegalStateException("already committed");
        }
    }
}
