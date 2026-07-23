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

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
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
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            byte[] imageBytes = new byte[] {5, 4, 3, 2, 1};
            DigitalSignature signature = new DigitalSignature();
            signature.setSignatureImage(imageBytes);
            signature.setDemographicId(111);

            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            when(manager.getDigitalSignature(42)).thenReturn(signature);
            registerMock(DigitalSignatureManager.class, manager);
            // The grant's eForm (fdid 4321) references signature 42 AND the signature belongs to
            // the same patient (111), so the fetch is authorized.
            registerMock(EFormValueDao.class, eFormValueDaoReferencing(4321, "42"));
            registerMock(EFormDataDao.class, eFormDataDaoFor(4321, 111));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
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
    @DisplayName("should stream a signature referenced with an HTML-escaped ampersand in stored markup")
    void shouldStreamSignature_whenReferenceIsHtmlEscaped() throws Exception {
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            byte[] imageBytes = new byte[] {9, 8, 7};
            DigitalSignature signature = new DigitalSignature();
            signature.setSignatureImage(imageBytes);
            signature.setDemographicId(111);

            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            when(manager.getDigitalSignature(42)).thenReturn(signature);
            registerMock(DigitalSignatureManager.class, manager);
            // Stored markup escapes the '&' as '&amp;'; the reference must still authorize the fetch.
            registerMock(EFormValueDao.class, eFormValueDaoReferencingEscaped(4321, "42"));
            registerMock(EFormDataDao.class, eFormDataDaoFor(4321, 111));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsByteArray()).containsExactly(imageBytes);
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should return 404 when a referenced signature row no longer exists")
    void shouldReturnNotFound_whenSignatureRowIsMissing() throws Exception {
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            // The eForm references signature 42, but the row is gone (e.g. deleted): must be a
            // deterministic 404, not an empty 200 fall-through.
            when(manager.getDigitalSignature(42)).thenReturn(null);
            registerMock(DigitalSignatureManager.class, manager);
            registerMock(EFormValueDao.class, eFormValueDaoReferencing(4321, "42"));
            registerMock(EFormDataDao.class, eFormDataDaoFor(4321, 111));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should reject a signature id the render's eForm does not reference")
    void shouldRejectSignature_whenNotReferencedByRenderEform() throws Exception {
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            registerMock(DigitalSignatureManager.class, manager);
            // The grant's eForm (fdid 4321) references only signature 42; a request for 99 is denied
            // so a crafted form cannot pull an unrelated patient's signature into the render.
            registerMock(EFormValueDao.class, eFormValueDaoReferencing(4321, "42"));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "99");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsByteArray()).isEmpty();
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should answer bad request for a non-numeric digitalSignatureId")
    void shouldSendBadRequest_whenDigitalSignatureIdNotNumeric() throws Exception {
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42-or-1=1");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should answer bad request for an over-range numeric digitalSignatureId")
    void shouldSendBadRequest_whenDigitalSignatureIdExceedsIntegerRange() throws Exception {
        // The \d+ pattern admits digit strings that overflow Integer; before the fix Integer.parseInt
        // threw NumberFormatException, which fell through to the generic catch and answered 500 — an
        // over-range id is a client error (400), not a server error.
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "99999999999999999999");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should answer not found when the referenced signature row has no image bytes")
    void shouldSendNotFound_whenSignatureImageBytesMissing() throws Exception {
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            // The row exists, the eForm references it, and it belongs to the right patient — but
            // the image column is empty; the pre-fix empty-200 fall-through left an untraceable
            // blank signature in the PDF.
            DigitalSignature imagelessSignature = new DigitalSignature();
            imagelessSignature.setDemographicId(111);
            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            when(manager.getDigitalSignature(42)).thenReturn(imagelessSignature);
            registerMock(DigitalSignatureManager.class, manager);
            registerMock(EFormValueDao.class, eFormValueDaoReferencing(4321, "42"));
            registerMock(EFormDataDao.class, eFormDataDaoFor(4321, 111));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should reject a referenced signature that belongs to a different patient")
    void shouldRejectSignature_whenSignatureBelongsToDifferentPatient() throws Exception {
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            DigitalSignature signature = new DigitalSignature();
            signature.setSignatureImage(new byte[] {1, 2, 3});
            signature.setDemographicId(222);

            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            when(manager.getDigitalSignature(42)).thenReturn(signature);
            registerMock(DigitalSignatureManager.class, manager);
            // The form's stored TEXT references signature 42 — text a form-filling user controls —
            // but the signature row belongs to another patient (222 vs the eForm's 111). The
            // authoritative demographic binding must deny the fetch.
            registerMock(EFormValueDao.class, eFormValueDaoReferencing(4321, "42"));
            registerMock(EFormDataDao.class, eFormDataDaoFor(4321, 111));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsByteArray()).isEmpty();
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should reject a referenced signature that carries no patient binding")
    void shouldRejectSignature_whenSignatureHasNoDemographic() throws Exception {
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            // demographicId is NOT NULL in the schema and set by every creation path; a null here
            // is an unprovable binding and must fail closed rather than stream PHI.
            DigitalSignature signature = new DigitalSignature();
            signature.setSignatureImage(new byte[] {1, 2, 3});

            DigitalSignatureManager manager = mock(DigitalSignatureManager.class);
            when(manager.getDigitalSignature(42)).thenReturn(signature);
            registerMock(DigitalSignatureManager.class, manager);
            registerMock(EFormValueDao.class, eFormValueDaoReferencing(4321, "42"));
            registerMock(EFormDataDao.class, eFormDataDaoFor(4321, 111));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsByteArray()).isEmpty();
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should log redacted type-only diagnostics when the catch-all handles an unexpected error")
    void shouldRedactUnexpectedErrors_whenCatchAllLogs() throws Exception {
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        try {
            // The exception message embeds a tokenized render URL, as container/machinery
            // exceptions can. The catch-all must log type + redacted message only — attaching the
            // raw throwable would re-emit the live token into the logs while the grant is valid.
            EFormValueDao explodingDao = mock(EFormValueDao.class);
            when(explodingDao.findByFormDataId(4321))
                    .thenThrow(new RuntimeException("boom at http://127.0.0.1/x?renderToken=SECRETTOKENVALUE"));
            registerMock(EFormValueDao.class, explodingDao);
            registerMock(DigitalSignatureManager.class, mock(DigitalSignatureManager.class));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("digitalSignatureId", "42");
            request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            try (LogCapture logs = LogCapture.forLogger(EFormSignatureViewForPdfGenerationServlet.class)) {
                new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                assertThat(logs.events()).noneMatch(event -> event.getThrown() != null);
                assertThat(logs.messages()).anyMatch(message -> message.contains("type=java.lang.RuntimeException"));
                assertThat(logs.messages()).noneMatch(message -> message.contains("SECRETTOKENVALUE"));
            }
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    private static EFormDataDao eFormDataDaoFor(int fdid, Integer demographicId) {
        EFormData eFormData = new EFormData();
        eFormData.setDemographicId(demographicId);
        EFormDataDao dao = mock(EFormDataDao.class);
        when(dao.findByFormDataId(fdid)).thenReturn(eFormData);
        return dao;
    }

    private static EFormValueDao eFormValueDaoReferencing(int fdid, String signatureId) {
        EFormValue signatureValue = new EFormValue();
        signatureValue.setVarName("signatureValue");
        signatureValue.setVarValue("/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=" + signatureId);
        EFormValueDao dao = mock(EFormValueDao.class);
        when(dao.findByFormDataId(fdid)).thenReturn(List.of(signatureValue));
        return dao;
    }

    private static EFormValueDao eFormValueDaoReferencingEscaped(int fdid, String signatureId) {
        EFormValue signatureValue = new EFormValue();
        signatureValue.setVarName("signatureValue");
        signatureValue.setVarValue("/carlos/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId=" + signatureId);
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
    void shouldRejectSignatureRequest_fromNonLocalAddress() throws Exception {
        registerMock(DigitalSignatureManager.class, mock(DigitalSignatureManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormSignatureViewForPdfGenerationServlet");
        request.setRemoteAddr("10.0.0.5");
        request.setParameter("digitalSignatureId", "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormSignatureViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }
}
