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

package io.github.carlos_emr.carlos.eform.util;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("EFormBrowserRenderPageServlet unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormBrowserRenderPageServletUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should redeem a render grant repeatedly for the bound eForm within one render")
    void shouldRedeemRenderGrantRepeatedly_whenValidRenderTokenPresented() {
        // liveRenderGrant peeks the singleton EFormRenderTokenService, so the singleton IS the
        // contract under test here; the issued token is invalidated in a finally so an assertion
        // failure cannot leak a live loopback grant into the shared cache for other tests.
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(187, "999998");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());

            EFormRenderTokenService.RenderGrant grant = EFormBrowserRenderPageServlet.liveRenderGrant(request, 187);

            assertThat(grant).isNotNull();
            assertThat(grant.providerNo()).isEqualTo("999998");
            assertThat(request.getSession(false)).isNull();
            // Render-scoped, not consume-once: the eForm document and its asset-image subresources
            // redeem the same grant, so a replay succeeds until the renderer invalidates the token.
            assertThat(EFormBrowserRenderPageServlet.liveRenderGrant(request, 187))
                    .as("render-scoped grant redeems repeatedly").isNotNull();
            EFormRenderTokenService.getInstance().invalidate(token);
            assertThat(EFormBrowserRenderPageServlet.liveRenderGrant(request, 187))
                    .as("invalidated grant no longer redeems").isNull();
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should reject browser renderer requests without a render token")
    void shouldRejectBrowserRendererRequest_whenNoRenderTokenPresented() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");

        assertThat(EFormBrowserRenderPageServlet.liveRenderGrant(request, 187)).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("should reject browser renderer requests when the token is bound to a different eForm")
    void shouldRejectBrowserRendererRequest_whenTokenBoundToDifferentEform() {
        // Singleton is the contract (liveRenderGrant peeks it); invalidate in finally so a failed
        // assertion cannot leak the issued grant into the shared cache.
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(187, "999998");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());

            assertThat(EFormBrowserRenderPageServlet.liveRenderGrant(request, 999)).isNull();
            // An fdid mismatch fails closed but does not burn the render-scoped token; the eForm it was
            // actually minted for still redeems.
            assertThat(EFormBrowserRenderPageServlet.liveRenderGrant(request, 187)).isNotNull();
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should send forbidden for browser renderer requests when the token is missing")
    void shouldSendForbidden_whenBrowserRenderRequestLacksToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("fdid", "123");
        request.setParameter("browserRender", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormBrowserRenderPageServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("should reject render page requests from non-local addresses")
    void shouldRejectRenderPageRequest_fromNonLocalAddress() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("192.168.1.20");
        request.setParameter("fdid", "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormBrowserRenderPageServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("should answer bad request when the fdid parameter is missing")
    void shouldSendBadRequest_whenFdidParameterMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormBrowserRenderPageServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    @DisplayName("should answer bad request when the fdid parameter is not numeric")
    void shouldSendBadRequest_whenFdidParameterNotNumeric() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("fdid", "not-a-number");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormBrowserRenderPageServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    @DisplayName("should reject saved eForm PDF requests without an authenticated session")
    void shouldRejectSavedEformPdfRequest_whenNoAuthenticatedSessionExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("fdid", "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormBrowserRenderPageServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("should render the eForm surface with nosniff and strict CSP when the session has _eform read privilege")
    void shouldRenderEformSurface_whenSessionHasEformReadPrivilege() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("fdid", "123");
        installLoggedInInfo(request, "999998");
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Drive the full doGet instead of reflecting into the private auth helper: the DB-backed HTML
        // assembly is stubbed so the test exercises the servlet's own auth + header contract.
        try (MockedStatic<EFormRenderPdfHtmlComposer> composer = mockStatic(EFormRenderPdfHtmlComposer.class)) {
            composer.when(() -> EFormRenderPdfHtmlComposer.buildPdfHtmlForFdid(anyInt(), any(), any(), any(), any()))
                    .thenReturn("<html><body>session-ok</body></html>");
            new EFormBrowserRenderPageServlet().doGet(request, response);
        }

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        // Session (non-browser) path keeps the strict script-blocking policy.
        assertThat(response.getHeader("Content-Security-Policy")).contains("script-src 'none'");
        assertThat(response.getContentAsString()).contains("session-ok");
    }

    @Test
    @DisplayName("should send forbidden when the authenticated session lacks _eform read privilege")
    void shouldSendForbidden_whenSessionLacksEformReadPrivilege() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("fdid", "123");
        installLoggedInInfo(request, "999998");
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormBrowserRenderPageServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("should set nosniff and the browser-render CSP on a token-authorized browser render")
    void shouldSetSecurityHeaders_whenBrowserRenderAuthorized() throws Exception {
        // The singleton peek IS the contract for the browser-render auth path; invalidate in finally
        // so a live loopback grant cannot leak into the shared cache on assertion failure.
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(123, "999998");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("fdid", "123");
            request.setParameter("browserRender", "true");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            try (MockedStatic<EFormRenderPdfHtmlComposer> composer = mockStatic(EFormRenderPdfHtmlComposer.class)) {
                composer.when(() -> EFormRenderPdfHtmlComposer.buildPdfHtmlForFdid(anyInt(), any(), any(), any(), any()))
                        .thenReturn("<html><body>rendered</body></html>");
                new EFormBrowserRenderPageServlet().doGet(request, response);
            }

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("Content-Security-Policy"))
                    .contains("script-src 'self' 'unsafe-inline' 'unsafe-eval'")
                    .contains("img-src 'self' data: blob:");
            assertThat(response.getContentAsString()).contains("rendered");
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should keep scripts blocked for legacy server-side PDF rendering")
    void shouldBuildStrictCsp_whenNotBrowserRendering() {
        assertThat(EFormBrowserRenderPageServlet.buildContentSecurityPolicy(false))
                .contains("script-src 'none'")
                .contains("object-src 'none'");
    }

    @Test
    @DisplayName("should allow same-origin scripts for browser PDF rendering")
    void shouldBuildBrowserRenderCsp_whenBrowserRendering() {
        assertThat(EFormBrowserRenderPageServlet.buildContentSecurityPolicy(true))
                .contains("default-src 'self' data:")
                .contains("script-src 'self' 'unsafe-inline' 'unsafe-eval'")
                .contains("object-src 'none'")
                .contains("img-src 'self' data: blob:");
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
