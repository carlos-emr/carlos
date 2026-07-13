/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.eform.actions;

import java.io.File;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisplayImage2Action}.
 *
 * <p>Verifies the authorization matrix for eForm asset streaming, including the
 * prevention-specific exception that allows {@code vaccine-brands.json} to be
 * served to users with {@code _prevention} read access even when they do not
 * have general {@code _eform} read access.</p>
 */
@DisplayName("DisplayImage2Action Unit Tests")
@Tag("unit")
@Tag("eform")
class DisplayImage2ActionTest extends CarlosUnitTestBase {
    private static final String VACCINE_BRANDS_JSON = "[{\"name\":\"Tdap\",\"value\":\"Adacel\"}]";

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private CarlosProperties mockProperties;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<CarlosProperties> carlosPropertiesMock;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private DisplayImage2Action action;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

        tempDir = Files.createTempDirectory("display-image-test-");
        when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

        action = new DisplayImage2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (tempDir != null) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to delete test temp path: " + path, e);
                            }
                        });
            }
        }
    }

    @Nested
    @DisplayName("Authorization matrix")
    class AuthorizationMatrix {

        @Test
        @DisplayName("should stream vaccine brands when prevention read privilege is granted")
        void shouldStreamVaccineBrands_whenPreventionReadPrivilegeGranted() throws Exception {
            mockRequest.setParameter("imagefile", DisplayImage2Action.VACCINE_BRANDS_FILE);
            Files.writeString(tempDir.resolve(DisplayImage2Action.VACCINE_BRANDS_FILE), VACCINE_BRANDS_JSON, StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_prevention"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentType()).isEqualTo("application/json");
            assertThat(mockResponse.getContentAsString()).contains("Adacel");
        }

        @Test
        @DisplayName("should throw SecurityException when non-vaccine asset requested without eform privilege")
        void shouldThrowSecurityException_whenNonVaccineAssetRequestedWithoutEformPrivilege() {
            mockRequest.setParameter("imagefile", "custom.json");

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_eform");

            verify(mockSecurityInfoManager, never()).hasPrivilege(eq(mockLoggedInInfo), eq("_prevention"), eq("r"), isNull());
            assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should stream non-vaccine asset when eform read privilege is granted")
        void shouldStreamNonVaccineAsset_whenEformReadPrivilegeGranted() throws Exception {
            mockRequest.setParameter("imagefile", "custom.json");
            Files.writeString(tempDir.resolve("custom.json"), "{\"ok\":true}", StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentType()).isEqualTo("application/json");
            assertThat(mockResponse.getContentAsString()).isEqualTo("{\"ok\":true}");
        }


        @Test
        @DisplayName("should return 404 when requested asset file is missing")
        void shouldReturn404_whenRequestedAssetFileIsMissing() throws Exception {
            mockRequest.setParameter("imagefile", "consult_sig_999998.png");

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should return 404 when asset disappears after validation but before streaming")
        void shouldReturn404_whenAssetDisappearsAfterValidation() throws Exception {
            mockRequest.setParameter("imagefile", "consult_sig_999998.png");
            Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {1, 2, 3});

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            DisplayImage2Action spiedAction = spy(action);
            doThrow(new java.io.FileNotFoundException("vanished"))
                    .when(spiedAction).process(any(File.class), anyString());

            String result = spiedAction.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should write HTML assets through writer when eform read privilege is granted")
        void shouldWriteHtmlAssetsThroughWriter_whenEformReadPrivilegeGranted() throws Exception {
            mockRequest.setParameter("imagefile", "custom.html");
            Files.writeString(tempDir.resolve("custom.html"), "<html><body>form</body></html>", StandardCharsets.UTF_8);
            MockHttpServletResponse trackingResponse = spy(new MockHttpServletResponse());
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(trackingResponse);
            action = new DisplayImage2Action();

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(trackingResponse.getContentType()).startsWith("text/html");
            assertThat(trackingResponse.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
            assertThat(trackingResponse.getContentAsString()).isEqualTo("<html><body>form</body></html>");
            verify(trackingResponse).getWriter();
            verify(trackingResponse, never()).getOutputStream();
        }

        @Test
        @DisplayName("should send error when binary stream cannot be opened")
        void shouldSendError_whenBinaryStreamCannotBeOpened() throws Exception {
            mockRequest.setParameter("imagefile", "custom.json");
            Files.writeString(tempDir.resolve("custom.json"), "{\"ok\":true}", StandardCharsets.UTF_8);
            MockHttpServletResponse trackingResponse = spy(new MockHttpServletResponse());
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(trackingResponse);
            action = new DisplayImage2Action();

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);
            doThrow(new java.io.IOException("stream unavailable")).when(trackingResponse).getOutputStream();

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(trackingResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should send error when HTML writer cannot be acquired")
        void shouldSendError_whenHtmlWriterCannotBeAcquired() throws Exception {
            mockRequest.setParameter("imagefile", "custom.html");
            Files.writeString(tempDir.resolve("custom.html"), "<html><body>form</body></html>", StandardCharsets.UTF_8);
            MockHttpServletResponse trackingResponse = spy(new MockHttpServletResponse());
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(trackingResponse);
            action = new DisplayImage2Action();

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);
            doThrow(new IllegalStateException("writer unavailable")).when(trackingResponse).getWriter();

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(trackingResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should throw SecurityException when vaccine brands requested without either privilege")
        void shouldThrowSecurityException_whenVaccineBrandsRequestedWithoutEitherPrivilege() {
            mockRequest.setParameter("imagefile", DisplayImage2Action.VACCINE_BRANDS_FILE);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_prevention"), eq("r"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_eform or _prevention");
        }

        @Test
        @DisplayName("should return bad request when requested asset type is unsupported")
        void shouldReturnBadRequest_whenRequestedAssetTypeIsUnsupported() throws Exception {
            mockRequest.setParameter("imagefile", "custom.unsupported");
            Files.writeString(tempDir.resolve("custom.unsupported"), "data", StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockResponse.getErrorMessage()).isEqualTo("Unsupported eform asset type");
        }

        @Test
        @DisplayName("should throw SecurityException when requested image path traverses outside allowed directory")
        void shouldThrowSecurityException_whenRequestedImagePathTraversesOutsideAllowedDirectory() {
            mockRequest.setParameter("imagefile", "../custom.json");

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class);
        }
    }

}
