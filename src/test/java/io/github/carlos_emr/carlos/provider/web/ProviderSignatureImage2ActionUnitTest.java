/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("ProviderSignatureImage2Action Unit Tests")
@Tag("unit")
class ProviderSignatureImage2ActionUnitTest extends CarlosUnitTestBase {

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
    private ProviderSignatureImage2Action action;
    private Path tempDir;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
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

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_pref"), eq("r"), isNull()))
                .thenReturn(true);

        tempDir = Files.createTempDirectory("provider-signature-image-test-");
        when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

        action = new ProviderSignatureImage2Action();
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
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to delete test temp path: " + path, e);
                    }
                });
            }
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should serve the logged-in provider's own stamp with preference read access")
    void shouldServeOwnStamp_whenPreferenceReadGranted() throws Exception {
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {1, 2, 3});

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentType()).isEqualTo("image/png");
        assertThat(mockResponse.getContentAsByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("should serve the logged-in provider's own stamp in clinical flows without preference access")
    void shouldServeOwnStamp_whenRxReadGrantedForExplicitProviderRequest() throws Exception {
        mockRequest.setParameter("providerNo", "999998");
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {4, 5, 6});
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_pref"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentAsByteArray()).containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("should serve another provider's stamp when Rx read access is granted")
    void shouldServeOtherProviderStamp_whenRxReadGranted() throws Exception {
        mockRequest.setParameter("providerNo", "123456");
        Files.write(tempDir.resolve("consult_sig_123456.png"), new byte[] {7, 8, 9});
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentAsByteArray()).containsExactly(7, 8, 9);
    }

    @Test
    @DisplayName("should serve another provider's stamp when eForm read access is granted")
    void shouldServeOtherProviderStamp_whenEformReadGranted() throws Exception {
        mockRequest.setParameter("providerNo", "123456");
        Files.write(tempDir.resolve("consult_sig_123456.png"), new byte[] {4, 5, 6});
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("w"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                .thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentAsByteArray()).containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("should serve another provider's stamp when consultation read access is granted")
    void shouldServeOtherProviderStamp_whenConsultationReadGranted() throws Exception {
        mockRequest.setParameter("providerNo", "123456");
        Files.write(tempDir.resolve("consult_sig_123456.png"), new byte[] {9, 8, 7});
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("r"), isNull()))
                .thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentAsByteArray()).containsExactly(9, 8, 7);
    }

    @Test
    @DisplayName("should serve another provider's stamp when consultation write access is granted")
    void shouldServeOtherProviderStamp_whenConsultationWriteGranted() throws Exception {
        mockRequest.setParameter("providerNo", "123456");
        Files.write(tempDir.resolve("consult_sig_123456.png"), new byte[] {6, 5, 4});
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("w"), isNull()))
                .thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentAsByteArray()).containsExactly(6, 5, 4);
    }

    @Test
    @DisplayName("should forbid another provider's stamp without clinical read access")
    void shouldForbidOtherProviderStamp_withoutClinicalAccess() {
        mockRequest.setParameter("providerNo", "123456");
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("w"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                .thenReturn(false);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should throw security exception when self stamp is requested without preference or clinical access")
    void shouldThrowSecurityException_whenSelfStampRequestedWithoutPreferenceOrClinicalAccess() {
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_pref"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("w"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_pref, _rx, _con, or _eform)");
    }

    @Test
    @DisplayName("should serve the logged-in provider's own stamp in the visual editor without preference access")
    void shouldServeOwnStamp_whenAdminEformWriteGranted() throws Exception {
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {3, 2, 1});
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_pref"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("w"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_admin.eform"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_admin.eform"), eq("w"), isNull()))
                .thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentAsByteArray()).containsExactly(3, 2, 1);
    }

    @Test
    @DisplayName("should return 404 when the requested provider stamp file is missing")
    void shouldReturn404_whenRequestedProviderStampIsMissing() {
        mockRequest.setParameter("providerNo", "123456");
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should return bad request when the logged-in provider number is invalid")
    void shouldReturnBadRequest_whenLoggedInProviderNoIsInvalid() {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provider-x");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should return bad request when requested provider number is not numeric")
    void shouldReturnBadRequest_whenRequestedProviderNoIsNotNumeric() {
        mockRequest.setParameter("providerNo", "abc123");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
    }
}
