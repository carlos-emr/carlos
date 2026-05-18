/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.sec;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.log.LogAction;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused tests for the shared unauthenticated rejection response contract.
 *
 * @since 2026-05-16
 */
@Tag("unit")
@DisplayName("UnauthenticatedRejectionResolver")
class UnauthenticatedRejectionResolverUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should redirect browser page request when unauthenticated")
    void shouldRedirectBrowserPageRequest_whenUnauthenticated() throws Exception {
        MockHttpServletRequest request = request("/provider/providercontrol");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture capture = LogCapture.forLogger(UnauthenticatedRejectionResolver.class)) {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
            assertSuccessfulRejectionLog(capture, "routeType=browser-page");
        }
    }

    @Test
    @DisplayName("should write text status response for AJAX request without JSON accept")
    void shouldWriteTextStatusResponse_whenAjaxRequestLacksJsonAccept() throws Exception {
        MockHttpServletRequest request = request("/billing/CA/ON/ViewSearchRefDocAjax");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertTextUnauthorized(response);
    }

    @Test
    @DisplayName("should write text status response when output stream was already obtained")
    void shouldWriteTextStatusResponse_whenOutputStreamWasAlreadyObtained() throws Exception {
        MockHttpServletRequest request = request("/billing/CA/ON/ViewSearchRefDocAjax");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream();

        try (LogCapture capture = LogCapture.forLogger(UnauthenticatedRejectionResolver.class)) {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

            assertTextUnauthorized(response);
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Response writer unavailable")
                        .contains("falling back to output stream")
                        .contains("uri=/carlos/billing/CA/ON/ViewSearchRefDocAjax");
            });
        }
    }

    @Test
    @DisplayName("should write JSON status response when output stream was already obtained")
    void shouldWriteJsonStatusResponse_whenOutputStreamWasAlreadyObtained() throws Exception {
        MockHttpServletRequest request = request("/billing/CA/ON/ViewSearchRefDocAjax");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertJsonUnauthorized(response);
    }

    @Test
    @DisplayName("should propagate body write IOException after output stream fallback")
    void shouldPropagateBodyWriteIOException_afterOutputStreamFallback() throws Exception {
        MockHttpServletRequest request = request("/billing/CA/ON/ViewSearchRefDocAjax");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        IOException writeFailure = new IOException("client disconnected");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(false);
        when(response.getWriter()).thenThrow(new IllegalStateException("writer already used"));
        when(response.getOutputStream()).thenReturn(failingOutputStream(writeFailure));

        assertThatThrownBy(() -> UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response))
                .isSameAs(writeFailure);
    }

    @ParameterizedTest
    @MethodSource("statusCodePaths")
    @DisplayName("should write text status response for generated content paths")
    void shouldWriteTextStatusResponse_whenGeneratedContentPathIsUnauthenticated(String path) throws Exception {
        MockHttpServletRequest request = request(path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture capture = LogCapture.forLogger(UnauthenticatedRejectionResolver.class)) {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

            assertTextUnauthorized(response);
            assertSuccessfulRejectionLog(capture, "routeType=status-code");
        }
    }

    @ParameterizedTest
    @MethodSource("statusCodePaths")
    @DisplayName("should write text status response for generated content child paths")
    void shouldWriteTextStatusResponse_whenGeneratedContentChildPathIsUnauthenticated(String path) throws Exception {
        MockHttpServletRequest request = request(path + "/child");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertTextUnauthorized(response);
    }

    @Test
    @DisplayName("should scrub path parameters from rejection audit log")
    void shouldScrubPathParameters_fromRejectionAuditLog() throws Exception {
        MockHttpServletRequest request = request("/Download;jsessionid=secret-token/file.pdf");
        request.setRequestURI("/carlos/Download;jsessionid=secret-token/file.pdf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture capture = LogCapture.forLogger(UnauthenticatedRejectionResolver.class)) {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

            assertTextUnauthorized(response);
            String logText = capture.events().stream()
                    .map(event -> event.getMessage().getFormattedMessage())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(logText)
                    .contains("uri=/carlos/Download/file.pdf")
                    .doesNotContain("jsessionid")
                    .doesNotContain("secret-token");
        }
    }

    @Test
    @DisplayName("should not match plural download path as generated content")
    void shouldNotMatchPluralDownloadPath_asGeneratedContent() throws Exception {
        MockHttpServletRequest request = request("/Downloads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
    }

    @Test
    @DisplayName("should write JSON status response when AJAX request accepts JSON")
    void shouldWriteJsonStatusResponse_whenAjaxRequestAcceptsJson() throws Exception {
        MockHttpServletRequest request = request("/billing/CA/ON/ViewSearchRefDocAjax");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertJsonUnauthorized(response);
    }

    @Test
    @DisplayName("should write JSON status response for multi value accept header")
    void shouldWriteJsonStatusResponse_whenAcceptHeaderContainsJsonAmongOtherTypes() throws Exception {
        MockHttpServletRequest request = request("/admin/api/status");
        request.addHeader("Accept", "application/xml;q=0.9, application/json;q=0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertJsonUnauthorized(response);
    }

    @Test
    @DisplayName("should write text status response when generated content accepts problem JSON")
    void shouldWriteTextStatusResponse_whenGeneratedContentAcceptsProblemJson() throws Exception {
        MockHttpServletRequest request = request("/Download");
        request.addHeader("Accept", "application/problem+json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertTextUnauthorized(response);
    }

    @Test
    @DisplayName("should redirect when accept header includes HTML before JSON")
    void shouldRedirect_whenAcceptHeaderIncludesHtmlBeforeJson() throws Exception {
        MockHttpServletRequest request = request("/admin/api/status");
        request.addHeader("Accept", "text/html, application/json;q=0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
    }

    @ParameterizedTest
    @MethodSource("structuredAcceptHeaders")
    @DisplayName("should write status response for structured accept headers")
    void shouldWriteStatusResponse_whenStructuredAcceptHeaderIsUnauthenticated(
            String acceptHeader,
            String expectedContentType,
            String expectedBody) throws Exception {
        MockHttpServletRequest request = request("/admin/api/status");
        request.addHeader("Accept", acceptHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentType()).isEqualTo(expectedContentType);
        assertThat(response.getContentAsString()).isEqualTo(expectedBody);
    }

    @Test
    @DisplayName("should redirect wildcard accept request when route is not otherwise status coded")
    void shouldRedirectWildcardAcceptRequest_whenRouteIsBrowserAmbiguous() throws Exception {
        MockHttpServletRequest request = request("/admin/api/status");
        request.addHeader("Accept", "*/*");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
    }

    @Test
    @DisplayName("should log and leave response unchanged when response is already committed")
    void shouldLogAndLeaveResponseUnchanged_whenResponseIsAlreadyCommitted() throws Exception {
        MockHttpServletRequest request = request("/admin/api/status");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        try (LogCapture capture = LogCapture.forLogger(UnauthenticatedRejectionResolver.class)) {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEmpty();
            logActionMock.verify(() -> LogAction.addLog("", "log in", "login",
                    "unauthenticated_rejection_committed:status-code", request.getRemoteAddr()));
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("response is already committed")
                        .contains("routeType=status-code")
                        .contains("acceptHint=application/json");
            });
        }
    }

    @Test
    @DisplayName("should swallow committed response audit failure")
    void shouldSwallowCommittedResponseAuditFailure_whenAuditLoggerFails() throws Exception {
        MockHttpServletRequest request = request("/admin/api/status");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        logActionMock.when(() -> LogAction.addLog("", "log in", "login",
                        "unauthenticated_rejection_committed:status-code", request.getRemoteAddr()))
                .thenThrow(new RuntimeException("audit unavailable"));

        try (LogCapture capture = LogCapture.forLogger(UnauthenticatedRejectionResolver.class)) {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Unable to audit committed unauthenticated rejection")
                        .contains("routeType=status-code");
            });
        }
    }

    @Test
    @DisplayName("should match status-code path children only on path boundary")
    void shouldMatchStatusCodePathChildren_onPathBoundary() {
        assertThat(UnauthenticatedRejectionResolver.matchesStatusCodePath("/Download")).isTrue();
        assertThat(UnauthenticatedRejectionResolver.matchesStatusCodePath("/Download/file.pdf")).isTrue();
        assertThat(UnauthenticatedRejectionResolver.matchesStatusCodePath("/Downloads")).isFalse();
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos" + path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static ServletOutputStream failingOutputStream(IOException failure) {
        return new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw failure;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // Not used by synchronous unit tests.
            }
        };
    }

    private static Stream<String> statusCodePaths() {
        return UnauthenticatedRejectionResolver.statusCodePathsForTesting();
    }

    private static Stream<Arguments> structuredAcceptHeaders() {
        return Stream.of(
                Arguments.of("application/json", "application/json;charset=UTF-8", "{\"error\":\"unauthorized\"}"),
                Arguments.of("application/xml", "text/plain;charset=UTF-8", "Unauthorized"),
                Arguments.of("text/xml", "text/plain;charset=UTF-8", "Unauthorized"),
                Arguments.of("text/javascript", "text/plain;charset=UTF-8", "Unauthorized"),
                Arguments.of("application/javascript", "text/plain;charset=UTF-8", "Unauthorized"),
                Arguments.of("application/pdf", "text/plain;charset=UTF-8", "Unauthorized"),
                Arguments.of("application/octet-stream", "text/plain;charset=UTF-8", "Unauthorized")
        );
    }

    private static void assertJsonUnauthorized(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"unauthorized\"}");
    }

    private static void assertTextUnauthorized(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getContentAsString()).isEqualTo("Unauthorized");
    }

    private static void assertSuccessfulRejectionLog(LogCapture capture, String routeType) {
        assertThat(capture.events()).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getMessage().getFormattedMessage())
                    .contains("Rejected unauthenticated request")
                    .contains(routeType);
        });
    }
}
