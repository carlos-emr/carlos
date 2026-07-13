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
package io.github.carlos_emr.carlos.sec;

import java.io.IOException;
import java.util.Date;
import java.util.stream.Stream;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import jakarta.servlet.ServletException;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.utility.SessionConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginFilter#inListOfExemptions(String, String, String[])}.
 *
 * <p>Validates that URL exemption matching enforces proper path boundaries
 * to prevent authentication bypass via crafted URLs (CWE-287).</p>
 *
 * @since 2026-04-08
 */
@Tag("unit")
@Tag("security")
@DisplayName("LoginFilter URL exemption matching")
class LoginFilterUnitTest extends CarlosUnitTestBase {

    private static final String CONTEXT_PATH = "/carlos";
    private LoginFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginFilter();
    }

    @Nested
    @DisplayName("Unauthenticated route handling")
    class UnauthenticatedRouteHandling {

        @Test
        @DisplayName("should redirect protected browser route when unauthenticated")
        void shouldRedirectProtectedBrowserRoute_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/provider/ViewProviderControl");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
        }

        @Test
        @DisplayName("should return 401 for AJAX route when unauthenticated")
        void shouldReturn401ForAjaxRoute_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/billing/CA/ON/ViewSearchRefDocAjax");
            request.addHeader("X-Requested-With", "XMLHttpRequest");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("Unauthorized");
        }

        @Test
        @DisplayName("should return 401 for JSON route when unauthenticated")
        void shouldReturn401ForJsonRoute_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/admin/api/status");
            request.addHeader("Accept", "application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"unauthorized\"}");
        }

        @ParameterizedTest
        @MethodSource("io.github.carlos_emr.carlos.sec.LoginFilterUnitTest#statusCodePaths")
        @DisplayName("should return 401 for status-code route when unauthenticated")
        void shouldReturn401ForStatusCodeRoute_whenUnauthenticated(String statusCodePath)
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + statusCodePath);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("Unauthorized");
        }

        @Test
        @DisplayName("should redirect mixed HTML and JSON accept route when unauthenticated")
        void shouldRedirectMixedHtmlJsonAcceptRoute_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/admin/api/status");
            request.addHeader("Accept", "text/html, application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
        }

        @Test
        @DisplayName("should redirect wildcard accept route when unauthenticated")
        void shouldRedirectWildcardAcceptRoute_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/admin/api/status");
            request.addHeader("Accept", "*/*");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
        }

        @Test
        @DisplayName("should audit remote address after successful unauthenticated rejection")
        void shouldAuditRemoteAddress_afterSuccessfulUnauthenticatedRejection()
                throws ServletException, IOException {
            try (LogCapture capture = LogCapture.forLogger(UnauthenticatedRejectionResolver.class)) {
                MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/admin/api/status");
                request.setRemoteAddr("198.51.100.27");
                request.addHeader("Accept", "application/json");
                MockHttpServletResponse response = new MockHttpServletResponse();

                filter.doFilter(request, response, new MockFilterChain());

                assertThat(response.getStatus()).isEqualTo(401);
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("Rejected unauthenticated request")
                            .contains("routeType=status-code")
                            .contains("remote=198.51.100.27")
                            .contains("acceptHint=application/json");
                });
            }
        }

        @Test
        @DisplayName("should warn without writing when response is already committed")
        void shouldWarnWithoutWriting_whenResponseAlreadyCommitted()
                throws ServletException, IOException {
            try (LogCapture capture = LogCapture.forLogger(UnauthenticatedRejectionResolver.class)) {
                MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/admin/api/status");
                request.addHeader("Accept", "application/json");
                MockHttpServletResponse response = new MockHttpServletResponse();
                response.getWriter().write("partial response");
                response.flushBuffer();

                filter.doFilter(request, response, new MockFilterChain());

                assertThat(response.isCommitted()).isTrue();
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.getRedirectedUrl()).isNull();
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("response is already committed")
                            .contains("routeType=status-code")
                            .contains("remote=203.0.113.10")
                            .contains("acceptHint=application/json");
                });
            }
        }

        @Test
        @DisplayName("should pass exempt login route when unauthenticated")
        void shouldPassExemptLoginRoute_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/index");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isSameAs(request);
            assertThat(response.getRedirectedUrl()).isNull();
        }

        @Test
        @DisplayName("should pass logout post when unauthenticated")
        void shouldPassLogoutPost_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("POST", CONTEXT_PATH + "/logout");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isSameAs(request);
            assertThat(response.getRedirectedUrl()).isNull();
        }

        @Test
        @DisplayName("should audit rejected token authentication")
        void shouldAuditRejectedTokenAuthentication_whenTokenManagerRejects()
                throws ServletException, IOException {
            String originalTokenManager = CarlosProperties.getInstance().getProperty("sec.token.manager");
            try (LogCapture capture = LogCapture.forLogger(LoginFilter.class)) {
                CarlosProperties.getInstance().setProperty("sec.token.manager",
                        RejectingTokenManager.class.getName());
                SecurityTokenManager.resetForTesting();
                MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/ws/service");
                request.setRemoteAddr("198.51.100.42");
                request.addParameter("token", "bad-token");
                MockHttpServletResponse response = new MockHttpServletResponse();
                MockFilterChain chain = new MockFilterChain();

                filter.doFilter(request, response, chain);

                assertThat(chain.getRequest()).isNull();
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("Rejected token authentication request")
                            .contains("uri=/carlos/ws/service")
                            .contains("remote=198.51.100.42");
                });
            } finally {
                if (originalTokenManager == null) {
                    CarlosProperties.getInstance().remove("sec.token.manager");
                } else {
                    CarlosProperties.getInstance().setProperty("sec.token.manager", originalTokenManager);
                }
                SecurityTokenManager.resetForTesting();
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"/forcepasswordreset", "/forcepasswordresetSubmit"})
        @DisplayName("should pass forced reset entrypoints when unauthenticated")
        void shouldPassForcedResetEntrypoints_whenUnauthenticated(String path)
                throws ServletException, IOException {
            String method = path.endsWith("Submit") ? "POST" : "GET";
            MockHttpServletRequest request = request(method, CONTEXT_PATH + path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isSameAs(request);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getRedirectedUrl()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/images/favicon.ico",
                "/library/jquery/jquery-ui-1.14.2.min.css",
                "/library/jquery/jquery-ui-1.14.2.min.js",
                "/library/jquery/jquery-3.7.1.min.js",
                "/library/jquery/jquery-compat.js",
                "/library/bootstrap/5.3.8/css/bootstrap.min.css",
                "/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js",
                "/share/css/global.css",
                "/share/css/searchBox.css",
                "/share/css/transitions.css",
                "/share/css/future-login.css",
                "/share/javascript/carlos-ajax.js",
                "/share/javascript/Oscar.js",
                "/js/global.js",
                "/css/fontawesome-all.min.css"
        })
        @DisplayName("should pass exact public asset route when unauthenticated")
        void shouldPassExactPublicAssetRoute_whenUnauthenticated(String assetPath)
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + assetPath);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isSameAs(request);
            assertThat(response.getRedirectedUrl()).isNull();
        }

        @Test
        @DisplayName("should redirect protected library action when unauthenticated")
        void shouldRedirectProtectedLibraryAction_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/library/eforms/signatureControl");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
        }

        @Test
        @DisplayName("should not pass dynamic JavaScript JSP when unauthenticated")
        void shouldNotPassDynamicJavascriptJsp_whenUnauthenticated()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/js/checkPassword.js.jsp");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/js/bootstrap",
                "/css/bootstrap",
                "/library/future-library/future-asset.min.js"
        })
        @DisplayName("should redirect non-exempt asset routes when unauthenticated")
        void shouldRedirectNonExemptAssetRoutes_whenUnauthenticated(String assetPath)
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + assetPath);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
        }
    }

    @Nested
    @DisplayName("Inactivity timeout")
    class InactivityTimeout {

        @Test
        @DisplayName("should fail closed when inactivity limit is malformed")
        void shouldFailClosed_whenInactivityLimitIsMalformed()
                throws ServletException, IOException {
            MockHttpServletRequest request = authenticatedRequest();
            MockHttpSession session = (MockHttpSession) request.getSession(false);
            session.setAttribute("last_request_time", new Date());
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            try (MockedStatic<CarlosProperties> propertiesStatic = mockStatic(CarlosProperties.class);
                 LogCapture capture = LogCapture.forLogger(LoginFilter.class)) {
                CarlosProperties properties = mock(CarlosProperties.class);
                propertiesStatic.when(CarlosProperties::getInstance).thenReturn(properties);
                when(properties.getProperty("INACTIVITY_LIMIT_MINS")).thenReturn("not-a-number");

                filter.doFilter(request, response, chain);

                assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
                assertThat(chain.getRequest()).isNull();
                assertThat(session.isInvalid()).isTrue();
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("ERROR checking for last activity")
                            .contains("not-a-number");
                });
            }
        }

        @Test
        @DisplayName("should fail closed when last request time has wrong type")
        void shouldFailClosed_whenLastRequestTimeHasWrongType()
                throws ServletException, IOException {
            MockHttpServletRequest request = authenticatedRequest();
            MockHttpSession session = (MockHttpSession) request.getSession(false);
            session.setAttribute("last_request_time", "stale-string");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            try (MockedStatic<CarlosProperties> propertiesStatic = mockStatic(CarlosProperties.class)) {
                CarlosProperties properties = mock(CarlosProperties.class);
                propertiesStatic.when(CarlosProperties::getInstance).thenReturn(properties);
                when(properties.getProperty("INACTIVITY_LIMIT_MINS")).thenReturn("60");

                filter.doFilter(request, response, chain);

                assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/logoutPage");
                assertThat(chain.getRequest()).isNull();
                assertThat(session.isInvalid()).isTrue();
            }
        }

        @Test
        @DisplayName("should not redirect loop when inactivity check fails on logout page")
        void shouldNotRedirectLoop_whenInactivityCheckFailsOnLogoutPage()
                throws ServletException, IOException {
            MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/logoutPage");
            request.getSession(true).setAttribute("user", "999998");
            request.getSession(false).setAttribute("last_request_time", "stale-string");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            try (MockedStatic<CarlosProperties> propertiesStatic = mockStatic(CarlosProperties.class);
                 LogCapture capture = LogCapture.forLogger(LoginFilter.class)) {
                CarlosProperties properties = mock(CarlosProperties.class);
                propertiesStatic.when(CarlosProperties::getInstance).thenReturn(properties);
                when(properties.getProperty("INACTIVITY_LIMIT_MINS")).thenReturn("60");

                filter.doFilter(request, response, chain);

                assertThat(response.getRedirectedUrl()).isNull();
                assertThat(chain.getRequest()).isSameAs(request);
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("timeout-redirect-exempt public page")
                            .contains("/carlos/logoutPage")
                            .doesNotContain("Failing closed");
                });
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/library/bootstrap/5.3.8/css/bootstrap.min.css",
                "/library/jquery/jquery-ui-1.14.2.min.js",
                "/share/css/future-login.css",
                "/share/javascript/Oscar.js",
                "/js/global.js",
                "/css/fontawesome-all.min.css"
        })
        @DisplayName("should not reset inactivity timer for public login assets")
        void shouldNotResetInactivityTimer_forPublicLoginAssets(String assetPath)
                throws ServletException, IOException {
            MockHttpServletRequest request = authenticatedRequest();
            request.setRequestURI(CONTEXT_PATH + assetPath);
            Date lastRequestDate = new Date(System.currentTimeMillis() - 1_000);
            request.getSession(false).setAttribute("last_request_time", lastRequestDate);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            try (MockedStatic<CarlosProperties> propertiesStatic = mockStatic(CarlosProperties.class)) {
                CarlosProperties properties = mock(CarlosProperties.class);
                propertiesStatic.when(CarlosProperties::getInstance).thenReturn(properties);
                when(properties.getProperty("INACTIVITY_LIMIT_MINS")).thenReturn("60");

                filter.doFilter(request, response, chain);

                assertThat(chain.getRequest()).isSameAs(request);
                assertThat(response.getRedirectedUrl()).isNull();
                assertThat(request.getSession(false).getAttribute("last_request_time"))
                        .isSameAs(lastRequestDate);
            }
        }
    }

    @Nested
    @DisplayName("Facility selection boundary")
    class FacilitySelectionBoundary {

        @Test
        @DisplayName("should redirect protected route when facility selection pending")
        void shouldRedirectProtectedRoute_whenFacilitySelectionPending()
                throws ServletException, IOException {
            MockHttpServletRequest request = authenticatedRequest();
            request.setRequestURI(CONTEXT_PATH + "/provider/providercontrol");
            request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/select_facility");
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("should pass select facility route when facility selection pending")
        void shouldPassSelectFacilityRoute_whenFacilitySelectionPending()
                throws ServletException, IOException {
            MockHttpServletRequest request = authenticatedRequest();
            request.setRequestURI(CONTEXT_PATH + "/select_facility");
            request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(chain.getRequest()).isSameAs(request);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/login",
                "/forcepasswordresetSubmit",
                "/mfa/loginMfa",
                "/ws/rs/status",
                "/Download/report.pdf"
        })
        @DisplayName("should redirect broad public exemptions when facility selection pending")
        void shouldRedirectBroadPublicExemptions_whenFacilitySelectionPending(String path)
                throws ServletException, IOException {
            MockHttpServletRequest request = authenticatedRequest();
            request.setRequestURI(CONTEXT_PATH + path);
            request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + "/select_facility");
            assertThat(chain.getRequest()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/library/bootstrap/5.3.8/css/bootstrap.min.css",
                "/share/css/global.css",
                "/csrfguard",
                "/status/SessionHeartbeat"
        })
        @DisplayName("should pass required support routes when facility selection pending")
        void shouldPassSupportRoutes_whenFacilitySelectionPending(String path)
                throws ServletException, IOException {
            MockHttpServletRequest request = authenticatedRequest();
            request.setRequestURI(CONTEXT_PATH + path);
            request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(chain.getRequest()).isSameAs(request);
        }
    }

    @Nested
    @DisplayName("Exact path matching")
    class ExactPathMatching {

        @Test
        @DisplayName("should match exact exempt URL")
        void shouldMatchExactExemptUrl_whenPathEqualsEntry() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/login", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match URL with extra characters appended")
        void shouldNotMatchUrl_withExtraCharactersAppended() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/login.doEvil", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should not match unrelated URL")
        void shouldNotMatchUnrelatedUrl_whenDifferentPath() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/admin/settings", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("Directory prefix matching (trailing slash)")
    class DirectoryPrefixMatching {

        @Test
        @DisplayName("should match subpath under directory exempt URL")
        void shouldMatchSubpath_underDirectoryExemptUrl() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws/someService", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should match exact directory URL")
        void shouldMatchExactDirectoryUrl_whenDirectoryEntryRequested() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws/", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should match nested subpath under directory exempt URL")
        void shouldMatchNestedSubpath_underDirectoryExemptUrl() {
            String[] exemptUrls = {"/mfa/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/mfa/verify/code", CONTEXT_PATH, exemptUrls)).isTrue();
        }
    }

    @Nested
    @DisplayName("Path boundary enforcement — prevents authentication bypass")
    class PathBoundaryEnforcement {

        @Test
        @DisplayName("should not match crafted URL appending to css/bootstrap prefix")
        void shouldNotMatchCraftedUrl_appendingToCssBootstrapPrefix() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/css/bootstrapEvil", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should not match crafted URL appending .jsp to exempt path")
        void shouldNotMatchCraftedUrl_appendingJspToExemptPath() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/css/bootstrap.jsp", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should match css/bootstrap subpath with path separator")
        void shouldMatchCssBootstrapSubpath_withPathSeparator() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/css/bootstrap/file.css", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should match images/Oscar.ico exactly")
        void shouldMatchImagePathExactly_whenImageEntryRequested() {
            String[] exemptUrls = {"/images/Oscar.ico"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/images/Oscar.ico", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match crafted URL appending to js/bootstrap prefix")
        void shouldNotMatchCraftedUrl_appendingToJsBootstrapPrefix() {
            String[] exemptUrls = {"/js/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/js/bootstrapAdmin", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should match js/bootstrap exactly")
        void shouldMatchJsBootstrapExactly_whenBootstrapEntryRequested() {
            String[] exemptUrls = {"/js/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/js/bootstrap", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match crafted URL appending to csrfguard")
        void shouldNotMatchCraftedUrl_appendingToCsrfguard() {
            String[] exemptUrls = {"/csrfguard"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/csrfguardEvil", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("Context root handling")
    class ContextRootHandling {

        @Test
        @DisplayName("should treat context root as index")
        void shouldTreatContextRoot_asIndexDo() {
            String[] exemptUrls = {"/index"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH, CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should treat context root with trailing slash as index")
        void shouldTreatContextRootWithTrailingSlash_asIndexDo() {
            String[] exemptUrls = {"/index"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/", CONTEXT_PATH, exemptUrls)).isTrue();
        }

    }

    @Nested
    @DisplayName("Empty context path")
    class EmptyContextPath {

        @Test
        @DisplayName("should match with empty context path")
        void shouldMatch_withEmptyContextPath() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    "/login", "", exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match crafted URL with empty context path")
        void shouldNotMatchCraftedUrl_withEmptyContextPath() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    "/css/bootstrapEvil", "", exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("No match scenarios")
    class NoMatchScenarios {

        @Test
        @DisplayName("should return false for empty exemption list")
        void shouldReturnFalse_forEmptyExemptionList() {
            String[] exemptUrls = {};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/login", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should return false for completely unrelated URL")
        void shouldReturnFalse_forCompletelyUnrelatedUrl() {
            String[] exemptUrls = {"/login", "/logoutPage"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/provider/dashboard", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("URI normalization — path parameters")
    class PathParameterNormalization {

        @Test
        @DisplayName("should match exempt URL when path parameter is appended")
        void shouldMatchExemptUrl_whenPathParameterAppended() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/login;jsessionid=abc123", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match non-exempt URL when path parameter is stripped")
        void shouldNotMatchNonExemptUrl_whenPathParameterStripped() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/admin/settings;jsessionid=abc123", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should match directory exempt URL with path parameter")
        void shouldMatchDirectoryExemptUrl_withPathParameter() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws/service;v=1.0", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should match exempt URL with path parameters in multiple segments")
        void shouldMatchExemptUrl_withPathParametersInMultipleSegments() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws;param1=a/service;param2=b", CONTEXT_PATH, exemptUrls)).isTrue();
        }
    }

    @Nested
    @DisplayName("URI normalization — repeated slashes")
    class RepeatedSlashNormalization {

        @Test
        @DisplayName("should match exempt URL with double slashes collapsed")
        void shouldMatchExemptUrl_withDoubleSlashesCollapsed() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "//login", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match non-exempt URL after slash collapsing")
        void shouldNotMatchNonExemptUrl_afterSlashCollapsing() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "//admin//settings", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("URI normalization — dot segment resolution")
    class DotSegmentNormalization {

        @Test
        @DisplayName("should not match non-exempt URL disguised with dot-dot traversal")
        void shouldNotMatchNonExemptUrl_disguisedWithDotDotTraversal() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws/../admin/settings", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should match exempt URL with redundant dot segment")
        void shouldMatchExemptUrl_withRedundantDotSegment() {
            String[] exemptUrls = {"/login"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/./login", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match non-exempt URL via dot-dot into exempt directory")
        void shouldNotMatchNonExemptUrl_viaDotDotIntoExemptDirectory() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/css/bootstrap/../../admin/secret", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("normalizeUri static method")
    class NormalizeUri {

        @Test
        @DisplayName("should strip path parameters")
        void shouldStripPathParameters_whenSessionIdPresent() {
            assertThat(LoginFilter.normalizeUri("/carlos/login;jsessionid=abc"))
                    .isEqualTo("/carlos/login");
        }

        @Test
        @DisplayName("should strip path parameters from multiple segments")
        void shouldStripPathParameters_fromMultipleSegments() {
            assertThat(LoginFilter.normalizeUri("/carlos/ws;param1=a/service;param2=b"))
                    .isEqualTo("/carlos/ws/service");
        }

        @Test
        @DisplayName("should collapse repeated slashes")
        void shouldCollapseRepeatedSlashes_whenUriHasDuplicateSeparators() {
            assertThat(LoginFilter.normalizeUri("//carlos///login"))
                    .isEqualTo("/carlos/login");
        }

        @Test
        @DisplayName("should resolve dot-dot segments")
        void shouldResolveDotDotSegments_whenUriContainsParentReference() {
            assertThat(LoginFilter.normalizeUri("/carlos/ws/../admin/secret"))
                    .isEqualTo("/carlos/admin/secret");
        }

        @Test
        @DisplayName("should resolve single dot segments")
        void shouldResolveSingleDotSegments_whenUriContainsCurrentReference() {
            assertThat(LoginFilter.normalizeUri("/carlos/./login"))
                    .isEqualTo("/carlos/login");
        }

        @Test
        @DisplayName("should preserve trailing slash")
        void shouldPreserveTrailingSlash_whenUriEndsWithSlash() {
            assertThat(LoginFilter.normalizeUri("/carlos/ws/"))
                    .isEqualTo("/carlos/ws/");
        }

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput_whenUriIsNull() {
            assertThat(LoginFilter.normalizeUri(null)).isNull();
        }

        @Test
        @DisplayName("should handle empty input")
        void shouldHandleEmptyInput_whenUriIsEmpty() {
            assertThat(LoginFilter.normalizeUri("")).isEmpty();
        }

        @Test
        @DisplayName("should handle combined normalization")
        void shouldHandleCombinedNormalization_whenUriHasMultipleUnsafeSegments() {
            assertThat(LoginFilter.normalizeUri("//carlos/./ws/../admin///secret;jsessionid=x"))
                    .isEqualTo("/carlos/admin/secret");
        }
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContextPath(CONTEXT_PATH);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }

    private static MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = request("GET", CONTEXT_PATH + "/provider/providercontrol");
        request.getSession(true).setAttribute("user", "999998");
        return request;
    }

    private static Stream<String> statusCodePaths() {
        return UnauthenticatedRejectionResolver.statusCodePathsForTesting();
    }

    public static final class RejectingTokenManager extends SecurityTokenManager {
        @Override
        public void requestToken(jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public boolean handleToken(jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain) {
            return false;
        }
    }
}
