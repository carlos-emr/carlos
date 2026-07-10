/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.web.eform;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("EformViewForPdfGenerationServlet unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EformViewForPdfGenerationServletUnitTest {

    @Test
    @DisplayName("should allow local renderer requests when the existing authenticated session matches providerId")
    void shouldForwardToShowData_whenAuthenticatedLoopbackSessionMatchesProvider() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/eformViewForPdfGenerationServlet") {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                assertThat(path).isEqualTo("/eform/efmshowform_data");
                return dispatcher;
            }
        };
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("providerId", " 999998 ");
        request.setParameter("fdid", "250");
        installLoggedInInfo(request, "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EformViewForPdfGenerationServlet().doGet(request, response);

        verify(dispatcher).forward(argThat(forwardedRequest -> "999998".equals(forwardedRequest.getParameter("providerId"))), eq(response));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-Security-Policy")).contains("script-src 'self'");
    }

    @Test
    @DisplayName("should reject renderer requests without an authenticated session")
    void shouldRejectRendererRequest_whenNoAuthenticatedSessionExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/eformViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("providerId", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EformViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("should reject renderer requests when providerId does not match the authenticated session")
    void shouldRejectRendererRequest_whenProviderDoesNotMatchSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/eformViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("providerId", "999998");
        installLoggedInInfo(request, "111111");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EformViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private static void installLoggedInInfo(MockHttpServletRequest request, String providerNo) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        Security security = new Security();
        security.setProviderNo(providerNo);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setSession(request.getSession(true));
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLoggedInSecurity(security);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
    }
}
