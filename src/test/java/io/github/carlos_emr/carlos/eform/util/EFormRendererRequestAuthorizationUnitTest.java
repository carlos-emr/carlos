/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EForm renderer request authorization")
@Tag("unit")
@Tag("fast")
@Tag("eform")
@Tag("security")
class EFormRendererRequestAuthorizationUnitTest {

    @Test
    @DisplayName("should permit only the exact passive path bound to the renderer cookie")
    void shouldPermitOnlyExactGrantedStaticPath() {
        EFormRenderTokenService service = EFormRenderTokenService.getInstance();
        EFormRenderTokenService.RenderToken token = service.issue(77, "999998");
        try {
            EFormRenderTokenService.RenderGrant grant = service.peek(token);
            service.authorizeStaticPaths(
                    grant, java.util.Set.of("/library/eforms/printControl.js"));
            EFormRenderTokenService.RenderSession session = service.exchange(token, null);

            MockHttpServletRequest allowed = request(
                    "GET", "/carlos/library/eforms/printControl.js", session);
            assertThat(EFormRendererRequestAuthorization.permitsStaticRequest(allowed)).isTrue();

            assertThat(EFormRendererRequestAuthorization.permitsStaticRequest(
                    request("GET", "/carlos/library/eforms/editControl.js", session))).isFalse();
            assertThat(EFormRendererRequestAuthorization.permitsStaticRequest(
                    request("POST", "/carlos/library/eforms/printControl.js", session))).isFalse();
            MockHttpServletRequest remote = request(
                    "GET", "/carlos/library/eforms/printControl.js", session);
            remote.setRemoteAddr("10.0.0.8");
            assertThat(EFormRendererRequestAuthorization.permitsStaticRequest(remote)).isFalse();
        } finally {
            service.invalidate(token);
        }
    }

    @Test
    @DisplayName("should set an isolated strict HttpOnly cookie without creating an application session")
    void shouldSetRendererCookie_withoutCreatingHttpSession() {
        EFormRenderTokenService service = EFormRenderTokenService.getInstance();
        EFormRenderTokenService.RenderToken token = service.issue(77, "999998");
        try {
            EFormRenderTokenService.RenderSession session = service.exchange(token, null);
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/carlos/EFormViewForPdfGenerationServlet");
            request.setContextPath("/carlos");
            request.setSecure(true);
            MockHttpServletResponse response = new MockHttpServletResponse();

            EFormRendererRequestAuthorization.setRendererCookie(request, response, session);

            assertThat(request.getSession(false)).isNull();
            assertThat(response.getHeader("Set-Cookie"))
                    .startsWith(EFormRendererRequestAuthorization.COOKIE_NAME + "=")
                    .contains("Path=/carlos/")
                    .contains("Max-Age=120")
                    .contains("HttpOnly")
                    .contains("SameSite=Strict")
                    .contains("Secure")
                    .doesNotContain("JSESSIONID");
        } finally {
            service.invalidate(token);
        }
    }

    private static MockHttpServletRequest request(
            String method, String uri, EFormRenderTokenService.RenderSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContextPath("/carlos");
        request.setRemoteAddr("127.0.0.1");
        request.setCookies(new Cookie(
                EFormRendererRequestAuthorization.COOKIE_NAME, session.cookieValue()));
        return request;
    }
}
