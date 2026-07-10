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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("EFormImageViewForPdfGenerationServlet tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormImageViewForPdfGenerationServletTest extends CarlosUnitTestBase {

    private MockedStatic<CarlosProperties> carlosPropertiesMock;
    private Path tempDir;

    @AfterEach
    void tearDown() throws Exception {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
        if (tempDir != null) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("should stream a local eform background image without a logged-in session")
    void shouldStreamLocalEformImage_withoutLoggedInSession() throws Exception {
        tempDir = Files.createTempDirectory("eform-image-view-servlet-test-");
        Path image = tempDir.resolve("bg.png");
        byte[] imageBytes = new byte[] {1, 2, 3, 4};
        Files.write(image, imageBytes);

        CarlosProperties mockProperties = mock(CarlosProperties.class);
        when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());
        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "bg.png");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("Content-disposition")).isEqualTo("inline; filename=\"bg.png\"");
        assertThat(response.getContentAsByteArray()).containsExactly(imageBytes);
    }

    @Test
    @DisplayName("should reject imagefile parameters containing NUL bytes")
    void shouldRejectImagefileContainingNullBytes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "bg.png\u0000evil");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    @DisplayName("should not throw when sendError fails while rejecting invalid imagefile input")
    void shouldNotThrowWhenSendErrorFails_forInvalidImagefileInput() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("imagefile", "../bg.png");
        MockHttpServletResponse response = new SendErrorFailingResponse();

        assertThatCode(() -> new EFormImageViewForPdfGenerationServlet().doGet(request, response))
                .doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("should reject non-local requests")
    void shouldRejectNonLocalRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/EFormImageViewForPdfGenerationServlet");
        request.setRemoteAddr("10.0.0.5");
        request.setParameter("imagefile", "bg.png");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EFormImageViewForPdfGenerationServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    private static final class SendErrorFailingResponse extends MockHttpServletResponse {
        @Override
        public void sendError(int status, String errorMessage) throws IOException {
            throw new IOException("boom");
        }
    }
}
