/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EFormSignatureViewForPdfGenerationServlet tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormSignatureViewForPdfGenerationServletTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should stream a signature image for a loopback render carrying a valid grant")
    void shouldStreamSignature_withValidRenderGrant() throws Exception {
        String token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            byte[] imageBytes = new byte[] {5, 4, 3, 2, 1};
            DigitalSignature signature = new DigitalSignature();
            signature.setSignatureImage(imageBytes);

            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            when(manager.getDigitalSignature(42)).thenReturn(signature);
            registerMock(DigitalSignatureManager.class, manager);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42");
            request.setParameter(EFormViewForPdfGenerationServlet.RENDER_TOKEN_PARAM, token);
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentType()).isEqualTo("image/jpeg");
            assertThat(response.getContentAsByteArray()).containsExactly(imageBytes);
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should reject a loopback signature request lacking a valid render grant")
    void shouldRejectSignatureRequest_whenNoValidRenderGrant() throws Exception {
        // No render grant: a bare loopback process may no longer enumerate PHI signatures by id.
        registerMock(DigitalSignatureManager.class, mock(DigitalSignatureManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("digitalSignatureId", "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should reject non-local signature requests")
    void shouldRejectNonLocalSignatureRequests() throws Exception {
        registerMock(DigitalSignatureManager.class, mock(DigitalSignatureManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
        request.setRemoteAddr("10.0.0.5");
        request.setParameter("digitalSignatureId", "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }
}
