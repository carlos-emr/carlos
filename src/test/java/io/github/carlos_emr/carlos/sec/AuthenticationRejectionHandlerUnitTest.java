/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.sec;

import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for the shared unauthenticated rejection response contract.
 *
 * @since 2026-05-16
 */
@Tag("unit")
@DisplayName("AuthenticationRejectionHandler")
class AuthenticationRejectionHandlerUnitTest {

    @Test
    @DisplayName("should redirect browser page request when unauthenticated")
    void shouldRedirectBrowserPageRequest_whenUnauthenticated() throws Exception {
        MockHttpServletRequest request = request("/provider/providercontrol");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture capture = LogCapture.forLogger(AuthenticationRejectionHandler.class)) {
            AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

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

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

        assertTextUnauthorized(response);
    }

    @Test
    @DisplayName("should write text status response when output stream was already obtained")
    void shouldWriteTextStatusResponse_whenOutputStreamWasAlreadyObtained() throws Exception {
        MockHttpServletRequest request = request("/billing/CA/ON/ViewSearchRefDocAjax");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream();

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

        assertTextUnauthorized(response);
    }

    @Test
    @DisplayName("should write JSON status response when output stream was already obtained")
    void shouldWriteJsonStatusResponse_whenOutputStreamWasAlreadyObtained() throws Exception {
        MockHttpServletRequest request = request("/billing/CA/ON/ViewSearchRefDocAjax");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream();

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

        assertJsonUnauthorized(response);
    }

    @ParameterizedTest
    @MethodSource("statusCodePaths")
    @DisplayName("should write text status response for generated content paths")
    void shouldWriteTextStatusResponse_whenGeneratedContentPathIsUnauthenticated(String path) throws Exception {
        MockHttpServletRequest request = request(path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture capture = LogCapture.forLogger(AuthenticationRejectionHandler.class)) {
            AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

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

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

        assertTextUnauthorized(response);
    }

    @Test
    @DisplayName("should not match plural download path as generated content")
    void shouldNotMatchPluralDownloadPath_asGeneratedContent() throws Exception {
        MockHttpServletRequest request = request("/Downloads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

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

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

        assertJsonUnauthorized(response);
    }

    @Test
    @DisplayName("should write JSON status response for multi value accept header")
    void shouldWriteJsonStatusResponse_whenAcceptHeaderContainsJsonAmongOtherTypes() throws Exception {
        MockHttpServletRequest request = request("/admin/api/status");
        request.addHeader("Accept", "application/xml;q=0.9, application/json;q=0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

        assertJsonUnauthorized(response);
    }

    @Test
    @DisplayName("should redirect when accept header includes HTML before JSON")
    void shouldRedirect_whenAcceptHeaderIncludesHtmlBeforeJson() throws Exception {
        MockHttpServletRequest request = request("/admin/api/status");
        request.addHeader("Accept", "text/html, application/json;q=0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

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

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

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

        AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

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

        try (LogCapture capture = LogCapture.forLogger(AuthenticationRejectionHandler.class)) {
            AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEmpty();
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("response is already committed")
                        .contains("routeType=status-code")
                        .contains("acceptHint=application/json");
            });
        }
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos" + path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static Stream<String> statusCodePaths() {
        return AuthenticationRejectionHandler.STATUS_CODE_PATHS.stream();
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
