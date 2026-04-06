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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
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

        @Test
        @DisplayName("should return false for word 'at' in normal prose text")
        void shouldReturnFalse_forWordAtInNormalText() {
            // "at" in plain text (without a preceding newline) should not match the stack frame pattern
            String body = "An error occurred at line 10 of the configuration file.";
            assertThat(ResponseSanitizationFilter.containsStackTrace(body)).isFalse();
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
        void shouldPassThrough_errorWithStackTrace_whenDisabled() throws Exception {
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
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/index.jsp");
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
        @DisplayName("should pass through 404 response without stack trace unchanged")
        void shouldPassThrough_404WithoutStackTrace() throws Exception {
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
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/index.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // Chain that sets status but writes nothing
            FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).isEmpty();
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
        void shouldSanitize_500ResponseWithStackTrace() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/index.jsp");
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
        void shouldSanitize_404ResponseWithStackTrace() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/missing.do");
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
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/AddTickler.do");
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
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/Save.do");
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
}
