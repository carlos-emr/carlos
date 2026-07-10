/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
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
class EFormImageViewForPdfGenerationServletTest extends CarlosUnitTestBase {

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
                assertThat(response.getHeader("Content-disposition")).isEqualTo("inline; filename=\"bg.png\"");
                assertThat(response.getContentAsByteArray()).containsExactly(imageBytes);
            }
        } finally {
            deleteTree(tempDir);
        }
    }

    @Test
    @DisplayName("should reject imagefile parameters containing NUL bytes")
    void shouldRejectImagefileContainingNullBytes() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "bg.png" + '\0' + "evil");
        installLoggedInInfo(request, "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
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
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("should reject requests without an authenticated session")
    void shouldRejectRequestsWithoutAuthenticatedSession() throws Exception {
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
    void shouldRejectNonLocalRequests() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("10.0.0.5");
        request.setParameter("imagefile", "bg.png");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
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
            throw new IOException("boom");
        }
    }
}
