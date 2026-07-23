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
import io.github.carlos_emr.carlos.utility.ResponseSanitizationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Field;
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
    @DisplayName("should not inject twice across request and forward dispatches")
    void shouldNotInjectTwice_acrossRequestAndForwardDispatches() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.REQUEST);
        request.getSession(true).setAttribute("user", "123");

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
        };

        MockHttpServletResponse requestResponse = new MockHttpServletResponse();
        filter.doFilter(request, requestResponse, chain);

        assertThat(request.getAttribute(scriptInjectedRequestAttribute())).isEqualTo(Boolean.TRUE);
        assertThat(requestResponse.getContentAsString()).contains("window.__carlosLogoutActive=true;");

        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse forwardResponse = new MockHttpServletResponse();
        filter.doFilter(request, forwardResponse, chain);

        assertThat(forwardResponse.getContentAsString()).contains("<html><body>schedule</body></html>");
        assertThat(forwardResponse.getContentAsString()).doesNotContain("window.__carlosLogoutActive=true;");
    }

    private String scriptInjectedRequestAttribute() throws Exception {
        Field field = LogoutBroadcastFilter.class.getDeclaredField("SCRIPT_INJECTED_REQUEST_ATTRIBUTE");
        field.setAccessible(true);
        return (String) field.get(null);
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
    @DisplayName("should handle logout broadcasts immediately after page load")
    void shouldHandleLogoutBroadcastsImmediately_afterPageLoad() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains("BroadcastChannel");
        assertThat(content).contains(".onmessage=");
        assertThat(content).contains("window.__carlosLogoutActive=true;");
        assertThat(content).doesNotContain("var ready=");
        assertThat(content).doesNotContain("setTimeout(function(){ready=true}");
    }

    @Test
    @DisplayName("should reserve append buffer when writer is obtained before HTML content type")
    void shouldReserveAppendBuffer_whenWriterIsObtainedBeforeHtmlContentType() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            PrintWriter writer = servletResponse.getWriter();
            writer.write("<html><body>");
            servletResponse.setContentType("text/html;charset=UTF-8");
            writer.write("schedule</body></html>");
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains("<html><body>schedule</body></html>");
        assertThat(content).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
        assertThat(response.getSetBufferSizeCallCount()).isOne();
    }

    @Test
    @DisplayName("should allow Struts forward reset after downstream writer flush")
    void shouldAllowForwardReset_whenDownstreamWriterFlushesBeforeForward() throws Exception {
        String contextPath = "/carlos";
        MockHttpServletRequest request = authenticatedRequest("/encounter/IncomingEncounter");
        request.setContextPath(contextPath);
        StrictCommitTrackingResponse response = new StrictCommitTrackingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            PrintWriter writer = servletResponse.getWriter();
            writer.write("pre-forward content");
            writer.flush();
            servletResponse.flushBuffer();

            servletResponse.resetBuffer();
            writer.write("<html><body>echart</body></html>");
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).doesNotContain("pre-forward content");
        assertThat(content).contains("<html><body>echart</body></html>");
        assertThat(content).contains("window.__carlosLogoutActive=true;");
        assertThat(response.isCommitted()).isTrue();
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
        String body = "<html><body>schedule</body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            ((MockHttpServletRequest) servletRequest).getSession(true).setAttribute("user", "123");
            PrintWriter writer = servletResponse.getWriter();
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
            writer.write(body);
        };

        filter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains(body);
        assertThat(content).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should append logout script when forced reset submit creates session during rendering")
    void shouldAppendLogoutScript_whenForcedResetSubmitCreatesSessionDuringRendering() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/forcepasswordresetSubmit");
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
        assertStaticAssetFastPath("/Library/jquery/jquery-3.7.1.min.js");
        assertStaticAssetFastPath("/share/javascript/Oscar.js");
    }

    @Test
    @DisplayName("should wrap paths that only prefix collide with static asset roots")
    void shouldWrapPathPrefixCollisions_whenAuthenticatedSessionExists() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/library-old/page");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>not a static asset</body></html>");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should honor custom exclusion init parameter")
    void shouldHonorCustomExclusion_whenConfigured() throws Exception {
        FilterConfig config = mock(FilterConfig.class);
        when(config.getInitParameter("exclusions")).thenReturn("/customSkip");
        LogoutBroadcastFilter customFilter = new LogoutBroadcastFilter();
        try {
            customFilter.init(config);
            MockHttpServletRequest request = authenticatedRequest("/customSkip/child");
            TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

            FilterChain chain = (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getWriter().write("<html><body>excluded</body></html>");
            };

            customFilter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).isEqualTo("<html><body>excluded</body></html>");
            assertThat(response.getSetBufferSizeCallCount()).isZero();
        } finally {
            customFilter.destroy();
        }
    }

    /**
     * Exclusion list mirroring the production {@code web.xml} LogoutBroadcastFilter init-param.
     * The eForm document routes must be excluded so the Rich Text Letter editor never reads the
     * injected logout/session script as editable letter content (issue #3099). The {@code .jsp}
     * alias is listed explicitly because the matcher treats it as a sibling, not a child, of the
     * extensionless route.
     */
    private static final String EFORM_DOCUMENT_EXCLUSIONS =
            "/logoutPage,/status/SessionHeartbeat,/eform/efmformadd_data,/eform/efmshowform_data,"
            + "/eform/efmformrtl_templates,/eform/efmformrtl_templates.jsp";

    @Test
    @DisplayName("should not inject logout script into eForm Rich Text Letter document routes")
    void shouldNotInjectLogoutScript_intoEformDocumentRoutes() throws Exception {
        LogoutBroadcastFilter eformAwareFilter = eformExclusionFilter();
        try {
            for (String route : new String[]{
                    "/eform/efmformadd_data",
                    "/eform/efmshowform_data",
                    "/eform/efmformrtl_templates",
                    "/eform/efmformrtl_templates.jsp"}) {
                MockHttpServletRequest request = authenticatedRequest(route);
                TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

                FilterChain chain = (servletRequest, servletResponse) -> {
                    servletResponse.setContentType("text/html;charset=UTF-8");
                    servletResponse.getWriter().write("<html><body>eform document</body></html>");
                };

                eformAwareFilter.doFilter(request, response, chain);

                assertThat(response.getContentAsString())
                        .as("eForm document route %s must be returned verbatim", route)
                        .isEqualTo("<html><body>eform document</body></html>");
                assertThat(response.getContentAsString()).doesNotContain("window.__carlosLogoutActive=true;");
                assertThat(response.getSetBufferSizeCallCount())
                        .as("excluded eForm route %s must skip the injection buffer", route)
                        .isZero();
            }
        } finally {
            eformAwareFilter.destroy();
        }
    }

    @Test
    @DisplayName("should still inject logout script on normal authenticated pages when eForm routes are excluded")
    void shouldStillInjectLogoutScript_onNormalPagesWhenEformRoutesExcluded() throws Exception {
        LogoutBroadcastFilter eformAwareFilter = eformExclusionFilter();
        try {
            MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
            TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

            FilterChain chain = (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getWriter().write("<html><body>schedule</body></html>");
            };

            eformAwareFilter.doFilter(request, response, chain);

            String content = response.getContentAsString();
            assertThat(content).contains("<html><body>schedule</body></html>");
            assertThat(content).contains("window.__carlosLogoutActive=true;");
            assertThat(content).contains("fetch(cp+'/status/SessionHeartbeat?autoRefresh=true')");
        } finally {
            eformAwareFilter.destroy();
        }
    }

    /**
     * Builds a filter configured with the production eForm exclusion list.
     *
     * @return an initialized LogoutBroadcastFilter mirroring the web.xml exclusions
     * @throws ServletException if filter initialization fails
     */
    private LogoutBroadcastFilter eformExclusionFilter() throws ServletException {
        FilterConfig config = mock(FilterConfig.class);
        when(config.getInitParameter("exclusions")).thenReturn(EFORM_DOCUMENT_EXCLUSIONS);
        LogoutBroadcastFilter eformAwareFilter = new LogoutBroadcastFilter();
        eformAwareFilter.init(config);
        return eformAwareFilter;
    }

    @Test
    @DisplayName("should not inject logout script when an excluded eForm route is forwarded to its internal WEB-INF view")
    void shouldNotInjectLogoutScript_whenEformRouteForwardedToInternalView() throws Exception {
        // The eForm gate actions forward the client route (e.g. /eform/efmformadd_data) to an
        // internal /WEB-INF/jsp view. The heartbeat script is appended on the FORWARD dispatch, where
        // getServletPath() reflects the JSP target, not the excluded route — so exclusion must be
        // resolved from the original request URI (issue #3099).
        LogoutBroadcastFilter eformAwareFilter = eformExclusionFilter();
        try {
            MockHttpServletRequest request = authenticatedRequest("/eform/efmformadd_data");
            request.setServletPath("/eform/efmformadd_data");
            TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

            FilterChain forwardingChain = (servletRequest, servletResponse) -> {
                MockHttpServletRequest forwarded = (MockHttpServletRequest) servletRequest;
                forwarded.setDispatcherType(DispatcherType.FORWARD);
                forwarded.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "/carlos/eform/efmformadd_data");
                forwarded.setServletPath("/WEB-INF/jsp/eform/efmformadd_data.jsp");
                forwarded.setRequestURI("/carlos/WEB-INF/jsp/eform/efmformadd_data.jsp");
                eformAwareFilter.doFilter(forwarded, servletResponse, (nestedRequest, nestedResponse) -> {
                    nestedResponse.setContentType("text/html;charset=UTF-8");
                    nestedResponse.getWriter().write("<html><body>eform document</body></html>");
                });
            };

            eformAwareFilter.doFilter(request, response, forwardingChain);

            assertThat(response.getContentAsString())
                    .as("eForm document forwarded to its internal view must be returned verbatim")
                    .isEqualTo("<html><body>eform document</body></html>");
            assertThat(response.getContentAsString()).doesNotContain("window.__carlosLogoutActive=true;");
            assertThat(response.getSetBufferSizeCallCount())
                    .as("excluded eForm forward must skip the injection buffer on both dispatches")
                    .isZero();
        } finally {
            eformAwareFilter.destroy();
        }
    }

    @Test
    @DisplayName("should still inject logout script when a normal authenticated page is forwarded to its internal WEB-INF view")
    void shouldStillInjectLogoutScript_whenNormalPageForwardedToInternalView() throws Exception {
        LogoutBroadcastFilter eformAwareFilter = eformExclusionFilter();
        try {
            MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
            request.setServletPath("/provider/providercontrol");
            TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

            FilterChain forwardingChain = (servletRequest, servletResponse) -> {
                MockHttpServletRequest forwarded = (MockHttpServletRequest) servletRequest;
                forwarded.setDispatcherType(DispatcherType.FORWARD);
                forwarded.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "/carlos/provider/providercontrol");
                forwarded.setServletPath("/WEB-INF/jsp/provider/providercontrol.jsp");
                forwarded.setRequestURI("/carlos/WEB-INF/jsp/provider/providercontrol.jsp");
                eformAwareFilter.doFilter(forwarded, servletResponse, (nestedRequest, nestedResponse) -> {
                    nestedResponse.setContentType("text/html;charset=UTF-8");
                    nestedResponse.getWriter().write("<html><body>schedule</body></html>");
                });
            };

            eformAwareFilter.doFilter(request, response, forwardingChain);

            String content = response.getContentAsString();
            assertThat(content).contains("<html><body>schedule</body></html>");
            assertThat(content).contains("window.__carlosLogoutActive=true;");
            assertThat(content).contains("fetch(cp+'/status/SessionHeartbeat?autoRefresh=true')");
        } finally {
            eformAwareFilter.destroy();
        }
    }

    @Test
    @DisplayName("should not inject logout script when an excluded eForm forward carries a rewritten jsessionid")
    void shouldNotInjectLogoutScript_whenEformForwardCarriesRewrittenSessionId() throws Exception {
        // With URL-rewritten sessions the original request URI carries ;jsessionid=...; path parameters
        // must be stripped before matching so the excluded route still matches on the forward dispatch.
        LogoutBroadcastFilter eformAwareFilter = eformExclusionFilter();
        try {
            MockHttpServletRequest request = authenticatedRequest("/eform/efmformadd_data");
            request.setServletPath("/eform/efmformadd_data");
            TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

            FilterChain forwardingChain = (servletRequest, servletResponse) -> {
                MockHttpServletRequest forwarded = (MockHttpServletRequest) servletRequest;
                forwarded.setDispatcherType(DispatcherType.FORWARD);
                forwarded.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI,
                        "/carlos/eform/efmformadd_data;jsessionid=ABC123");
                forwarded.setServletPath("/WEB-INF/jsp/eform/efmformadd_data.jsp");
                forwarded.setRequestURI("/carlos/WEB-INF/jsp/eform/efmformadd_data.jsp");
                eformAwareFilter.doFilter(forwarded, servletResponse, (nestedRequest, nestedResponse) -> {
                    nestedResponse.setContentType("text/html;charset=UTF-8");
                    nestedResponse.getWriter().write("<html><body>eform document</body></html>");
                });
            };

            eformAwareFilter.doFilter(request, response, forwardingChain);

            assertThat(response.getContentAsString())
                    .as("excluded eForm forward with a rewritten jsessionid must be returned verbatim")
                    .isEqualTo("<html><body>eform document</body></html>");
            assertThat(response.getContentAsString()).doesNotContain("window.__carlosLogoutActive=true;");
            assertThat(response.getSetBufferSizeCallCount()).isZero();
        } finally {
            eformAwareFilter.destroy();
        }
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

        String content = response.getContentAsString();
        assertThat(content).contains("window.__carlosLogoutActive=true;");
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
    @DisplayName("should preserve AJAX login failure content length without wrapping")
    void shouldPreserveAjaxLoginFailureContentLength_withoutWrapping() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setContextPath("/carlos");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        String body = "{\"success\":false,\"error\":\"Invalid credentials.\"}";

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
    @DisplayName("should flush non-HTML writer response when injection is skipped")
    void shouldFlushNonHtmlWriterResponse_whenInjectionIsSkipped() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/ViewSchedulePageJs");
        BufferingWriterResponse response = new BufferingWriterResponse();
        String body = "function openSchedule() { return 'ready'; }\n";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/javascript;charset=UTF-8");
            servletResponse.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
            servletResponse.getWriter().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getHeader("Content-Length"))
                .isEqualTo(String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
        assertThat(response.getContentAsString()).doesNotContain("window.__carlosLogoutActive=true;");
        assertThat(response.getSetBufferSizeCallCount()).isZero();
    }

    @Test
    @DisplayName("should flush writer when logout script is appended")
    void shouldFlushWriter_whenLogoutScriptIsAppended() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        BufferingWriterResponse response = new BufferingWriterResponse();
        String body = "<html><body>schedule</body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains(body);
        assertThat(response.getContentAsString()).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should replay complete HTML when nested inside response sanitization")
    void shouldReplayCompleteHtml_whenNestedInsideResponseSanitization() throws Exception {
        ResponseSanitizationFilter sanitizationFilter = new ResponseSanitizationFilter();
        sanitizationFilter.init(mock(FilterConfig.class));
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        String body = "<html><body>schedule<script>var quickSearchTail=true;</script></body></html>";

        FilterChain chain = (servletRequest, servletResponse) ->
                filter.doFilter(servletRequest, servletResponse, (innerRequest, innerResponse) -> {
                    innerResponse.setContentType("text/html;charset=UTF-8");
                    innerResponse.setContentLength(0);
                    innerResponse.getWriter().write(body);
                });

        sanitizationFilter.doFilter(request, response, chain);

        String content = response.getContentAsString();
        assertThat(content).contains(body);
        assertThat(content).contains("quickSearchTail=true");
        assertThat(content).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getHeader("Content-Length"))
                .isEqualTo(String.valueOf(content.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    @DisplayName("should route setHeader content type through lazy HTML buffer")
    void shouldRouteSetHeaderContentType_throughLazyHtmlBuffer() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            ((HttpServletResponse) servletResponse).setHeader("Content-Type", "text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should configure HTML buffer only once")
    void shouldConfigureHtmlBufferOnlyOnce_whenContentTypeIsRepeated() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            ((HttpServletResponse) servletResponse).setHeader("Content-Type", "text/html;charset=UTF-8");
            ((HttpServletResponse) servletResponse).addHeader("Content-Type", "text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getSetBufferSizeCallCount()).isOne();
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should route addHeader content type through lazy HTML buffer")
    void shouldRouteAddHeaderContentType_throughLazyHtmlBuffer() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            ((HttpServletResponse) servletResponse).addHeader("Content-Type", "text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getLastRequestedBufferSize()).isEqualTo(HTML_INJECTION_BUFFER_SIZE_BYTES);
    }

    @Test
    @DisplayName("should preserve integer content length without large buffer for JSON response")
    void shouldPreserveIntegerContentLengthWithoutLargeBuffer_whenAuthenticatedJsonResponseRenders() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/status/SomeJsonAction");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        String body = "{\"valid\":true}";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            ((HttpServletResponse) servletResponse).setIntHeader("Content-Length",
                    body.getBytes(StandardCharsets.UTF_8).length);
            servletResponse.getWriter().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getHeader("Content-Length"))
                .isEqualTo(String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
        assertThat(response.getSetBufferSizeCallCount()).isZero();
    }

    @Test
    @DisplayName("should preserve added integer content length without large buffer for JSON response")
    void shouldPreserveAddedIntegerContentLengthWithoutLargeBuffer_whenAuthenticatedJsonResponseRenders()
            throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/status/SomeJsonAction");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        String body = "{\"valid\":true}";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            ((HttpServletResponse) servletResponse).addIntHeader("Content-Length",
                    body.getBytes(StandardCharsets.UTF_8).length);
            servletResponse.getWriter().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getHeader("Content-Length"))
                .isEqualTo(String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
        assertThat(response.getSetBufferSizeCallCount()).isZero();
    }

    @Test
    @DisplayName("should skip injection when HTML buffer cannot be enlarged after body write")
    void shouldSkipInjection_whenHtmlBufferCannotBeEnlargedAfterBodyWrite() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        LateBufferFailingResponse response = new LateBufferFailingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
            servletResponse.setContentType("text/html;charset=UTF-8");
        };

        try (LogCapture capture = LogCapture.forLogger(LogoutBroadcastFilter.class)) {
            filter.doFilter(request, response, chain);

            assertThat(response.getContentAsString()).contains("<html><body>schedule</body></html>");
            assertThat(response.getContentAsString()).doesNotContain("window.__carlosLogoutActive=true;");
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("setBufferSize rejected for logout broadcast writer injection")
                        .contains("uri=/carlos/provider/providercontrol");
                assertThat(event.getThrown()).isInstanceOf(IllegalStateException.class);
            });
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("HTML response buffer could not be enlarged")
                        .contains("uri=/carlos/provider/providercontrol")
                        .doesNotContain("sessionId=");
            });
        }
    }

    @Test
    @DisplayName("should not replay deferred content length after response is committed")
    void shouldNotReplayDeferredContentLength_whenResponseIsCommitted() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/status/SomeJsonAction");
        CommittedContentLengthTrackingResponse response = new CommittedContentLengthTrackingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setContentLength(99);
            servletResponse.getWriter().write("{}");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo("{}");
        assertThat(response.getSetContentLengthCallCount()).isZero();
        assertThat(response.getSetContentLengthLongCallCount()).isZero();
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
    @DisplayName("should skip logout script and preserve content length when response uses output stream")
    void shouldSkipLogoutScriptAndPreserveContentLength_whenResponseUsesOutputStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        HttpSession session = request.getSession(true);
        session.setAttribute("user", "123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = "<html><body>stream</body></html>".getBytes(StandardCharsets.UTF_8);

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.setContentLength(body.length);
            servletResponse.getOutputStream().write(body);
            servletResponse.flushBuffer();
        };

        try (LogCapture capture = LogCapture.forLogger(LogoutBroadcastFilter.class)) {
            filter.doFilter(request, response, chain);

            String content = response.getContentAsString();
            assertThat(content).contains("<html><body>stream</body></html>");
            assertThat(content).doesNotContain("window.__carlosLogoutActive=true;");
            assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(body.length));
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("response used ServletOutputStream")
                        .contains("uri=/provider/providercontrol")
                        .contains("contentType=text/html;charset=UTF-8");
            });
        }
    }

    @Test
    @DisplayName("should replay deferred content length only once when output stream is used")
    void shouldReplayDeferredContentLengthOnlyOnce_whenOutputStreamIsUsed() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        TrackingMockHttpServletResponse response = new TrackingMockHttpServletResponse();
        byte[] body = "<html><body>stream</body></html>".getBytes(StandardCharsets.UTF_8);

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.setContentLength(body.length);
            servletResponse.getOutputStream().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(body.length));
        assertThat(response.getSetContentLengthCallCount()).isOne();
        assertThat(response.getSetContentLengthLongCallCount()).isZero();
    }

    @Test
    @DisplayName("should append logout script through byte replay when underlying writer is unavailable")
    void shouldAppendLogoutScript_whenUnderlyingWriterIsUnavailable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        HttpSession session = request.getSession(true);
        session.setAttribute("user", "123");

        MockResponseWithUnavailableWriter response = new MockResponseWithUnavailableWriter();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.setContentLength(1);
        };

        filter.doFilter(request, response, chain);

        String content = response.getBodyAsString();
        assertThat(content).contains("window.__carlosLogoutActive=true;");
        assertThat(response.getHeader("Content-Length")).isNull();
    }

    @Test
    @DisplayName("should strip path parameters from buffer unavailable diagnostics")
    void shouldStripPathParameters_fromBufferUnavailableDiagnostics() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        request.setRequestURI("/carlos/provider/providercontrol;jsessionid=abc123");
        LateBufferFailingResponse response = new LateBufferFailingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.getWriter().write("<html><body>schedule</body></html>");
            servletResponse.setContentType("text/html;charset=UTF-8");
        };

        try (LogCapture capture = LogCapture.forLogger(LogoutBroadcastFilter.class)) {
            filter.doFilter(request, response, chain);

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("HTML response buffer could not be enlarged")
                        .contains("uri=/carlos/provider/providercontrol")
                        .doesNotContain("jsessionid")
                        .doesNotContain("abc123")
                        .doesNotContain("sessionId=");
            });
        }
    }

    @Test
    @DisplayName("should log error when script writer flush fails")
    void shouldLogError_whenScriptWriterFlushFails() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("/provider/providercontrol");
        FlushFailingResponse response = new FlushFailingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.setContentLength(42);
        };

        try (LogCapture capture = LogCapture.forLogger(LogoutBroadcastFilter.class)) {
            filter.doFilter(request, response, chain);

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("script could not be written")
                        .contains("uri=/carlos/provider/providercontrol");
                assertThat(event.getThrown()).isInstanceOf(IOException.class);
            });
            assertThat(response.getHeader("Content-Length")).isNull();
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
        private int setContentLengthCallCount;
        private int setContentLengthLongCallCount;

        @Override
        public void setBufferSize(int size) {
            setBufferSizeCallCount++;
            lastRequestedBufferSize = size;
            super.setBufferSize(size);
        }

        @Override
        public void setContentLength(int len) {
            setContentLengthCallCount++;
            super.setContentLength(len);
        }

        @Override
        public void setContentLengthLong(long len) {
            setContentLengthLongCallCount++;
            super.setContentLengthLong(len);
        }

        int getSetBufferSizeCallCount() {
            return setBufferSizeCallCount;
        }

        Integer getLastRequestedBufferSize() {
            return lastRequestedBufferSize;
        }

        int getSetContentLengthCallCount() {
            return setContentLengthCallCount;
        }

        int getSetContentLengthLongCallCount() {
            return setContentLengthLongCallCount;
        }
    }

    private static class BufferingWriterResponse extends TrackingMockHttpServletResponse {

        private final StringBuilder pending = new StringBuilder();
        private final StringBuilder flushed = new StringBuilder();
        private PrintWriter bufferingWriter;
        private ServletOutputStream bufferingOutputStream;

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
        public ServletOutputStream getOutputStream() {
            if (bufferingOutputStream == null) {
                bufferingOutputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                    }

                    @Override
                    public void write(int b) {
                        flushed.append((char) b);
                    }
                };
            }
            return bufferingOutputStream;
        }

        @Override
        public void flushBuffer() {
            // Servlet containers do not guarantee that flushBuffer() flushes an already-obtained
            // PrintWriter. The filter must flush the delegating writer explicitly.
        }

        @Override
        public String getContentAsString() {
            return flushed.toString();
        }
    }

    private static class StrictCommitTrackingResponse extends TrackingMockHttpServletResponse {

        private final StringBuilder content = new StringBuilder();
        private boolean committed;
        private PrintWriter strictWriter;
        private ServletOutputStream strictOutputStream;

        @Override
        public PrintWriter getWriter() {
            if (strictWriter == null) {
                strictWriter = new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) {
                        content.append(cbuf, off, len);
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() {
                    }
                });
            }
            return strictWriter;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (strictOutputStream == null) {
                strictOutputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                    }

                    @Override
                    public void write(int b) {
                        content.append((char) b);
                    }
                };
            }
            return strictOutputStream;
        }

        @Override
        public void flushBuffer() {
            committed = true;
        }

        @Override
        public void resetBuffer() {
            if (committed) {
                throw new IllegalStateException("Cannot reset buffer after response has been committed");
            }
            content.setLength(0);
            super.resetBuffer();
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }

        @Override
        public String getContentAsString() {
            return content.toString();
        }
    }

    private static class CommittedContentLengthTrackingResponse extends TrackingMockHttpServletResponse {

        @Override
        public boolean isCommitted() {
            return true;
        }
    }

    private static class LateBufferFailingResponse extends MockHttpServletResponse {
        @Override
        public void setBufferSize(int size) {
            throw new IllegalStateException("body already written");
        }
    }

    private static class FlushFailingResponse extends HttpServletResponseWrapper {
        FlushFailingResponse() {
            super(new MockHttpServletResponse());
        }

        @Override
        public void flushBuffer() throws IOException {
            throw new IOException("flush failed");
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

}
