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
    void shouldRejectUnreferencedKey() throws Exception {
        try (RenderFixture fixture = fixture("patient_name")) {
            MockHttpServletRequest request = fixture.request("different_key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormApCacheForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Test
    @DisplayName("should reject appointment-dependent AP keys without trusted appointment context")
    void shouldRejectAppointmentDependentKey() throws Exception {
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
