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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("EFormImageViewForPdfGenerationServlet tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormImageViewForPdfGenerationServletUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should stream a local eform background image when the current session has _eform read privilege")
    void shouldStreamLocalEformImage_withAuthenticatedEformReadSession() throws Exception {
        Path tempDir = Files.createTempDirectory("eform-image-view-servlet-test-");
        try {
            Path image = tempDir.resolve("bg.png");
            byte[] imageBytes = new byte[] {1, 2, 3, 4};
            Files.write(image, imageBytes);

            CarlosProperties mockProperties = mock(CarlosProperties.class);
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

            try (MockedStatic<CarlosProperties> carlosPropertiesMock = mockStatic(CarlosProperties.class)) {
                carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
                request.setRemoteAddr("127.0.0.1");
                request.setParameter("imagefile", "bg.png");
                installLoggedInInfo(request, "999998");
                MockHttpServletResponse response = new MockHttpServletResponse();

                new EFormImageViewForPdfGenerationServlet().doGet(request, response);

                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
                assertThat(response.getContentType()).isEqualTo("image/png");
                assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
                assertThat(response.getHeader("Content-disposition")).isEqualTo("inline; filename=\"bg.png\"");
                assertThat(response.getContentAsByteArray()).containsExactly(imageBytes);
            }
        } finally {
            deleteTree(tempDir);
        }
    }

    @Test
    @DisplayName("should stream a local eform background image for a sessionless render carrying a valid grant")
    void shouldStreamLocalEformImage_withValidRenderGrantAndNoSession() throws Exception {
        Path tempDir = Files.createTempDirectory("eform-image-view-servlet-test-");
        EFormRenderTokenService.RenderToken token = EFormRenderTokenService.getInstance().issue(4321, "999998");
        EFormRenderTokenService.getInstance().authorizeAssets(token, java.util.Set.of("bg.png"));
        try {
            Path image = tempDir.resolve("bg.png");
            byte[] imageBytes = new byte[] {9, 8, 7, 6};
            Files.write(image, imageBytes);

            CarlosProperties mockProperties = mock(CarlosProperties.class);
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            // No SecurityInfoManager privilege stubbing: a sessionless render must authorize purely
            // by the render-scoped grant, never by falling back to a privilege check.
            registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));

            try (MockedStatic<CarlosProperties> carlosPropertiesMock = mockStatic(CarlosProperties.class)) {
                carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
                request.setRemoteAddr("127.0.0.1");
                request.setParameter("imagefile", "bg.png");
                request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
                MockHttpServletResponse response = new MockHttpServletResponse();

                new EFormImageViewForPdfGenerationServlet().doGet(request, response);

                // No logged-in provider was installed, yet the render-scoped grant alone authorized
                // the fetch: image streamed with no privilege check.
                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
                assertThat(response.getContentType()).isEqualTo("image/png");
                assertThat(response.getContentAsByteArray()).containsExactly(imageBytes);
            }
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
            deleteTree(tempDir);
        }
    }

    @Test
    @DisplayName("should reject an asset that is not referenced by the render grant")
    void shouldRejectUnreferencedAsset_withValidRenderGrant() throws Exception {
        EFormRenderTokenService.RenderToken token =
                EFormRenderTokenService.getInstance().issue(4321, "999998");
        EFormRenderTokenService.getInstance().authorizeAssets(token, java.util.Set.of("bg.png"));
        try {
            registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("imagefile", "other.png");
            request.setParameter(
                    EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, token.queryValue());
            MockHttpServletResponse response = new MockHttpServletResponse();

            new EFormImageViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        } finally {
            EFormRenderTokenService.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should reject a sessionless request whose render grant is unknown or expired")
    void shouldRejectSessionlessRequest_whenRenderGrantInvalid() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "bg.png");
        request.setParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM, "never-issued-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("should reject authenticated requests without eform read privilege")
    void shouldRejectAuthenticatedRequest_whenEformReadPrivilegeMissing() throws Exception {
        Path tempDir = Files.createTempDirectory("eform-image-view-servlet-test-");
        try {
            Path image = tempDir.resolve("bg.png");
            Files.write(image, new byte[] {1, 2, 3, 4});

            CarlosProperties mockProperties = mock(CarlosProperties.class);
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);
            when(securityInfoManager.hasPrivilege(any(), eq("_prevention"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);

            try (MockedStatic<CarlosProperties> carlosPropertiesMock = mockStatic(CarlosProperties.class)) {
                carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
                request.setRemoteAddr("127.0.0.1");
                request.setParameter("imagefile", "bg.png");
                installLoggedInInfo(request, "999998");
                MockHttpServletResponse response = new MockHttpServletResponse();

                new EFormImageViewForPdfGenerationServlet().doGet(request, response);

                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
                assertThat(response.getContentAsByteArray()).isEmpty();
            }
        } finally {
            deleteTree(tempDir);
        }
    }

    @Test
    @DisplayName("should reject imagefile parameters containing NUL bytes as a bad request")
    void shouldRejectImagefile_withNullBytes() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "bg.png" + '\0' + "evil");
        installLoggedInInfo(request, "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        // A malformed imagefile is a client error (400), not an authorization failure (403) — the
        // filename is validated before the privilege check.
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    @DisplayName("should not throw when sendError fails while rejecting invalid imagefile input")
    void shouldNotThrowWhenSendErrorFails_forInvalidImagefileInput() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "../bg.png");
        installLoggedInInfo(request, "999998");
        MockHttpServletResponse response = new SendErrorFailingResponse();

        assertThatCode(() -> new EFormImageViewForPdfGenerationServlet().doGet(request, response))
                .doesNotThrowAnyException();
        // The servlet rejects the traversal-shaped imagefile with SC_BAD_REQUEST (a client error,
        // validated before the privilege check) before sendError throws; a real container records the
        // status before the write can fail, so assert the reject status rather than the mock's default 200.
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    @DisplayName("should reject requests without an authenticated session")
    void shouldRejectRequest_withoutAuthenticatedSession() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "bg.png");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("should reject non-local requests")
    void shouldRejectRequest_fromNonLocalAddress() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("10.0.0.5");
        request.setParameter("imagefile", "bg.png");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("should stream vaccine-brands.json when the session holds prevention read only")
    void shouldStreamVaccineBrands_withPreventionReadOnly() throws Exception {
        Path tempDir = Files.createTempDirectory("eform-image-view-servlet-test-");
        try {
            Path asset = tempDir.resolve("vaccine-brands.json");
            byte[] assetBytes = "[{\"brand\":\"example\"}]".getBytes();
            Files.write(asset, assetBytes);

            CarlosProperties mockProperties = mock(CarlosProperties.class);
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            // _eform is absent; _prevention READ alone is the accepted alternative for this one asset.
            SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);
            when(securityInfoManager.hasPrivilege(any(), eq("_prevention"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

            try (MockedStatic<CarlosProperties> carlosPropertiesMock = mockStatic(CarlosProperties.class)) {
                carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
                request.setRemoteAddr("127.0.0.1");
                request.setParameter("imagefile", "vaccine-brands.json");
                installLoggedInInfo(request, "999998");
                MockHttpServletResponse response = new MockHttpServletResponse();

                new EFormImageViewForPdfGenerationServlet().doGet(request, response);

                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
                assertThat(response.getContentType()).isEqualTo("application/json");
                assertThat(response.getContentAsByteArray()).containsExactly(assetBytes);
            }
        } finally {
            deleteTree(tempDir);
        }
    }

    @Test
    @DisplayName("should deny vaccine-brands.json when both eform and prevention privileges are missing")
    void shouldSend403_forVaccineBrandsWithoutPrivileges() throws Exception {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_prevention"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "vaccine-brands.json");
        installLoggedInInfo(request, "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getErrorMessage()).contains("(_eform or _prevention)");
    }

    @Test
    @DisplayName("should not authorize a non-vaccine asset by prevention privilege alone")
    void shouldSend403_forGenericAssetWithPreventionOnly() throws Exception {
        // The _prevention alternative in enforceAssetReadPrivilege is scoped to vaccine-brands.json
        // only; a generic template asset must still require _eform even when _prevention is held.
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_prevention"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "bg.png");
        installLoggedInInfo(request, "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should strip hostile characters from the Content-disposition header")
    void shouldSanitizeContentDisposition_forHostileFileName() throws Exception {
        Path tempDir = Files.createTempDirectory("eform-image-view-servlet-test-");
        try {
            // Filesystem-legal on Linux (only '/' and NUL are forbidden), so this exercises the real
            // getImageFile -> file.exists() path rather than a stubbed one. The quote, semicolon, and
            // embedded CR/LF are exactly the characters sanitizeHeaderValue must strip so the
            // Content-disposition header cannot be split or have its filename attribute escaped.
            String hostileName = "bad\"name;\r\nX.png";
            Path asset = tempDir.resolve(hostileName);
            byte[] assetBytes = new byte[] {7, 7, 7};
            Files.write(asset, assetBytes);

            CarlosProperties mockProperties = mock(CarlosProperties.class);
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

            try (MockedStatic<CarlosProperties> carlosPropertiesMock = mockStatic(CarlosProperties.class)) {
                carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
                request.setRemoteAddr("127.0.0.1");
                request.setParameter("imagefile", hostileName);
                installLoggedInInfo(request, "999998");
                MockHttpServletResponse response = new MockHttpServletResponse();

                new EFormImageViewForPdfGenerationServlet().doGet(request, response);

                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
                assertThat(response.getContentAsByteArray()).containsExactly(assetBytes);
                assertThat(response.getHeader("Content-disposition")).isEqualTo("inline; filename=\"badnameX.png\"");
            }
        } finally {
            deleteTree(tempDir);
        }
    }

    @Test
    @DisplayName("should answer bad request for an eform asset extension outside the content-type allowlist")
    void shouldSendBadRequest_forUnsupportedAssetExtension() throws Exception {
        Path tempDir = Files.createTempDirectory("eform-image-view-servlet-test-");
        try {
            Path asset = tempDir.resolve("archive.zip");
            Files.write(asset, new byte[] {1, 2, 3});

            CarlosProperties mockProperties = mock(CarlosProperties.class);
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

            try (MockedStatic<CarlosProperties> carlosPropertiesMock = mockStatic(CarlosProperties.class)) {
                carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
                request.setRemoteAddr("127.0.0.1");
                request.setParameter("imagefile", "archive.zip");
                installLoggedInInfo(request, "999998");
                MockHttpServletResponse response = new MockHttpServletResponse();

                new EFormImageViewForPdfGenerationServlet().doGet(request, response);

                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
                assertThat(response.getContentAsByteArray()).isEmpty();
            }
        } finally {
            deleteTree(tempDir);
        }
    }

    @Test
    @DisplayName("should answer not found when the requested asset file does not exist")
    void shouldSendNotFound_whenAssetFileMissing() throws Exception {
        Path tempDir = Files.createTempDirectory("eform-image-view-servlet-test-");
        try {
            // The eform image directory exists but the requested asset was never written into it.
            CarlosProperties mockProperties = mock(CarlosProperties.class);
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);

            try (MockedStatic<CarlosProperties> carlosPropertiesMock = mockStatic(CarlosProperties.class)) {
                carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
                request.setRemoteAddr("127.0.0.1");
                request.setParameter("imagefile", "missing.png");
                installLoggedInInfo(request, "999998");
                MockHttpServletResponse response = new MockHttpServletResponse();

                new EFormImageViewForPdfGenerationServlet().doGet(request, response);

                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            }
        } finally {
            deleteTree(tempDir);
        }
    }

    @Test
    @DisplayName("should log redacted type-only diagnostics when the catch-all handles an unexpected error")
    void shouldRedactUnexpectedErrors_whenCatchAllLogs() throws Exception {
        // The exception message embeds a tokenized render URL, as container/machinery exceptions
        // can. The catch-all must log type + redacted message only — attaching the raw throwable
        // would re-emit a live render token into the logs.
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), isNull()))
                .thenThrow(new RuntimeException("boom at http://127.0.0.1/x?renderToken=SECRETTOKENVALUE"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "bg.png");
        installLoggedInInfo(request, "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture logs = LogCapture.forLogger(EFormImageViewForPdfGenerationServlet.class)) {
            new EFormImageViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(logs.events()).noneMatch(event -> event.getThrown() != null);
            assertThat(logs.messages()).anyMatch(message -> message.contains("type=java.lang.RuntimeException"));
            assertThat(logs.messages()).noneMatch(message -> message.contains("SECRETTOKENVALUE"));
        }
    }

    @Test
    @DisplayName("should not attach the throwable when rejecting an invalid imagefile parameter")
    void shouldNotAttachThrowable_whenRejectingInvalidImagefile() throws Exception {
        // The wrapped FileValidationException's message can echo the caller-supplied imagefile
        // value; the rejection WARN must carry only the servlet's own message text.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "../bg.png");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture logs = LogCapture.forLogger(EFormImageViewForPdfGenerationServlet.class)) {
            new EFormImageViewForPdfGenerationServlet().doGet(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(logs.events()).noneMatch(event -> event.getThrown() != null);
        }
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

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static final class SendErrorFailingResponse extends MockHttpServletResponse {
        @Override
        public void sendError(int status, String errorMessage) throws IOException {
            // Real containers record the status before flushing the error page can fail; mirror that
            // so the status the servlet requested is observable even though the write throws.
            setStatus(status);
            throw new IOException("boom");
        }
    }
}
