/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.data.DatabaseAP;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EForm renderer APCache servlet")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormApCacheForPdfGenerationServletUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should reject a key that was not discovered for the render")
    void shouldRejectApCacheKey_whenNotReferencedByTheForm() throws Exception {
        try (RenderFixture fixture = fixture("patient_name")) {
            MockHttpServletRequest request = fixture.request("different_key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormApCacheForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Test
    @DisplayName("should reject appointment-dependent AP keys without trusted appointment context")
    void shouldRejectApCacheKey_whenItRequiresAppointmentContext() throws Exception {
        String key = "renderer_test_appt_" + System.nanoTime();
        EFormLoader.addDatabaseAP(new DatabaseAP(
                key,
                "select ${appt_no}",
                "${appt_no}"));
        try (RenderFixture fixture = fixture(key)) {
            MockHttpServletRequest request = fixture.request(key);
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormApCacheForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(422);
        }
    }

    @Test
    @DisplayName("should reject a non-loopback caller even with a valid renderer cookie")
    void shouldRejectApCacheRequest_whenCallerIsNotLoopback() throws Exception {
        // This gate is the only barrier between another local process and PHI-bearing AP output.
        try (RenderFixture fixture = fixture("patient_name")) {
            MockHttpServletRequest request = fixture.request("patient_name");
            request.setRemoteAddr("10.0.0.8");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormApCacheForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Test
    @DisplayName("should reject an APCache request carrying no renderer cookie")
    void shouldRejectApCacheRequest_whenRendererCookieMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/carlos/EFormApCacheForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("key", "patient_name");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormApCacheForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("should reject a malformed APCache key before it reaches the AP loader")
    void shouldRejectApCacheKey_whenItFailsThePatternCheck() throws Exception {
        try (RenderFixture fixture = fixture("patient_name")) {
            MockHttpServletRequest request = fixture.request("patient name'; drop--");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormApCacheForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("should reject an APCache request asking for no keys at all")
    void shouldRejectApCacheRequest_whenNoKeysRequested() throws Exception {
        try (RenderFixture fixture = fixture("patient_name")) {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET", "/carlos/EFormApCacheForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setCookies(new Cookie(
                    EFormRendererRequestAuthorization.COOKIE_NAME, fixture.session().cookieValue()));
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormApCacheForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("should reject an APCache key that is not configured on this server")
    void shouldRejectApCacheKey_whenTheApIsNotConfigured() throws Exception {
        // 422, not a blank 200: an unconfigured key means the field cannot be populated, and the
        // browser must see a failure rather than render an empty clinical value.
        String key = "renderer_missing_ap_" + System.nanoTime();
        try (RenderFixture fixture = fixture(key)) {
            MockHttpServletRequest request = fixture.request(key);
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormApCacheForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(422);
        }
    }

    private static RenderFixture fixture(String allowedKey) {
        EFormRenderTokenService service = EFormRenderTokenService.getInstance();
        EFormRenderTokenService.RenderToken token = service.issue(77, "999998");
        service.authorizeApKeys(token, java.util.Set.of(allowedKey));
        return new RenderFixture(service, token, service.exchange(token, null));
    }

    private record RenderFixture(
            EFormRenderTokenService service,
            EFormRenderTokenService.RenderToken token,
            EFormRenderTokenService.RenderSession session) implements AutoCloseable {

        MockHttpServletRequest request(String key) {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET", "/carlos/EFormApCacheForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("key", key);
            request.setCookies(new Cookie(
                    EFormRendererRequestAuthorization.COOKIE_NAME, session.cookieValue()));
            return request;
        }

        @Override
        public void close() {
            service.invalidate(token);
        }
    }
}
