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

import java.lang.reflect.Method;

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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EFormViewForPdfGenerationServlet unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormViewForPdfGenerationServletUnitTest {

    @Test
    @DisplayName("should redeem a render grant repeatedly for the bound eForm within one render")
    void shouldRedeemRenderGrantRepeatedly_whenValidRenderTokenPresented() {
        String token = EFormRenderTokenService.getInstance().issue(187, "999998");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setParameter(EFormViewForPdfGenerationServlet.RENDER_TOKEN_PARAM, token);

        EFormRenderTokenService.RenderGrant grant = EFormViewForPdfGenerationServlet.redeemedRenderGrant(request, 187);

        assertThat(grant).isNotNull();
        assertThat(grant.providerNo()).isEqualTo("999998");
        assertThat(request.getSession(false)).isNull();
        // Render-scoped, not consume-once: the eForm document and its asset-image subresources
        // redeem the same grant, so a replay succeeds until the renderer invalidates the token.
        assertThat(EFormViewForPdfGenerationServlet.redeemedRenderGrant(request, 187))
                .as("render-scoped grant redeems repeatedly").isNotNull();
        EFormRenderTokenService.getInstance().invalidate(token);
        assertThat(EFormViewForPdfGenerationServlet.redeemedRenderGrant(request, 187))
                .as("invalidated grant no longer redeems").isNull();
    }

    @Test
    @DisplayName("should reject browser renderer requests without a render token")
    void shouldRejectBrowserRendererRequest_whenNoRenderTokenPresented() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");

        assertThat(EFormViewForPdfGenerationServlet.redeemedRenderGrant(request, 187)).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("should reject browser renderer requests when the token is bound to a different eForm")
    void shouldRejectBrowserRendererRequest_whenTokenBoundToDifferentEform() {
        String token = EFormRenderTokenService.getInstance().issue(187, "999998");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setParameter(EFormViewForPdfGenerationServlet.RENDER_TOKEN_PARAM, token);

        assertThat(EFormViewForPdfGenerationServlet.redeemedRenderGrant(request, 999)).isNull();
        // An fdid mismatch fails closed but does not burn the render-scoped token; the eForm it was
        // actually minted for still redeems.
        assertThat(EFormViewForPdfGenerationServlet.redeemedRenderGrant(request, 187)).isNotNull();
        EFormRenderTokenService.getInstance().invalidate(token);
    }

    @Test
    @DisplayName("should send forbidden for browser renderer requests when the token is missing")
    void shouldSendForbidden_whenBrowserRenderRequestLacksToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("fdid", "123");
        request.setParameter("browserRender", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("should reject saved eForm PDF requests without an authenticated session")
    void shouldRejectSavedEformPdfRequest_whenNoAuthenticatedSessionExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("fdid", "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("should allow saved eForm PDF requests when the authenticated session has _eform read privilege")
    void shouldAllowSavedEformPdfRequest_whenSessionHasEformReadPrivilege() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        installLoggedInInfo(request, "999998");
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(securityInfoManager);

            LoggedInInfo authorized = invokeAuthorizedEformReadRequest(request);

            assertThat(authorized).isNotNull();
            assertThat(authorized.getLoggedInProviderNo()).isEqualTo("999998");
        }
    }

    @Test
    @DisplayName("should reject saved eForm PDF requests when the authenticated session lacks _eform read privilege")
    void shouldRejectSavedEformPdfRequest_whenSessionLacksEformReadPrivilege() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
        installLoggedInInfo(request, "999998");
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(securityInfoManager);

            assertThat(invokeAuthorizedEformReadRequest(request)).isNull();
        }
    }

    @Test
    @DisplayName("should keep scripts blocked for legacy server-side PDF rendering")
    void shouldBuildStrictCsp_whenNotBrowserRendering() {
        assertThat(EFormViewForPdfGenerationServlet.buildContentSecurityPolicy(false))
                .contains("script-src 'none'")
                .contains("object-src 'none'");
    }

    @Test
    @DisplayName("should allow same-origin scripts for browser PDF rendering")
    void shouldBuildBrowserRenderCsp_whenBrowserRendering() {
        assertThat(EFormViewForPdfGenerationServlet.buildContentSecurityPolicy(true))
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

    private static LoggedInInfo invokeAuthorizedEformReadRequest(MockHttpServletRequest request) throws Exception {
        Method method = EFormViewForPdfGenerationServlet.class.getDeclaredMethod("authorizedEformReadRequest", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);
        return (LoggedInInfo) method.invoke(null, request);
    }
}
