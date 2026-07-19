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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
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
            // The grant's eForm (fdid 4321) references signature 42, so the fetch is authorized.
            registerMock(EFormValueDao.class, eFormValueDaoReferencing(4321, "42"));

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
    @DisplayName("should reject a signature id the render's eForm does not reference")
    void shouldRejectSignature_whenNotReferencedByRenderEform() throws Exception {
        String token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            registerMock(DigitalSignatureManager.class, manager);
            // The grant's eForm (fdid 4321) references only signature 42; a request for 99 is denied
            // so a crafted form cannot pull an unrelated patient's signature into the render.
            registerMock(EFormValueDao.class, eFormValueDaoReferencing(4321, "42"));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "99");
            request.setParameter(EFormViewForPdfGenerationServlet.RENDER_TOKEN_PARAM, token);
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsByteArray()).isEmpty();
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    private static EFormValueDao eFormValueDaoReferencing(int fdid, String signatureId) {
        EFormValue signatureValue = new EFormValue();
        signatureValue.setVarName("signatureValue");
        signatureValue.setVarValue("/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=" + signatureId);
        EFormValueDao dao = mock(EFormValueDao.class);
        when(dao.findByFormDataId(fdid)).thenReturn(List.of(signatureValue));
        return dao;
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
