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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.apache.logging.log4j.Logger;
import org.apache.catalina.connector.ClientAbortException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("should return uncached 204 when the requested provider stamp file is missing")
    void shouldReturn204_whenRequestedProviderStampIsMissing() {
        mockRequest.setParameter("providerNo", "123456");
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        assertThat(mockResponse.getHeader("Cache-Control")).contains("no-store");
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should return 404 when the configured image path is not a directory")
    void shouldReturn404_whenConfiguredImagePathIsNotDirectory() throws Exception {
        Path configuredFile = Files.createTempFile(tempDir, "eform-images-", ".txt");
        when(mockProperties.getEformImageDirectory()).thenReturn(configuredFile.toString());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should return 404 when image directory validation fails")
    void shouldReturn404_whenImageDirectoryValidationFails() {
        String configuredDirectory = "/invalid/eform-images";
        when(mockProperties.getEformImageDirectory()).thenReturn(configuredDirectory);

        try (MockedStatic<PathValidationUtils> pathValidationUtilsMock =
                     mockStatic(PathValidationUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            pathValidationUtilsMock.when(() -> PathValidationUtils.resolveConfiguredDirectory(
                            configuredDirectory, "EFORM_IMAGES_DIR"))
                    .thenThrow(new SecurityException("not allowed"));

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            assertThat(mockResponse.getContentAsByteArray()).isEmpty();
            pathValidationUtilsMock.verify(() -> PathValidationUtils.resolveConfiguredDirectory(
                    configuredDirectory, "EFORM_IMAGES_DIR"));
        }
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

    @Test
    @DisplayName("should quietly preserve the response when the browser aborts the stamp stream")
    void shouldIgnoreClientAbortDuringStampStreaming() throws Exception {
        ClientAbortException abort = new ClientAbortException("client disconnected");
        abort.initCause(new IOException("Broken pipe"));
        assertStampStreamFailure(abort, true, false);
    }

    @Test
    @DisplayName("should quietly handle a wrapped client abort after a partial stamp response")
    void shouldIgnoreWrappedClientAbortAfterPartialStamp() throws Exception {
        assertStampStreamFailure(new IOException("stream write failed", new ClientAbortException()), true, true);
    }

    @Test
    @DisplayName("should retain HTTP500 and ERROR for a non-client IOException even with a broken-pipe message")
    void shouldReportOrdinaryIoFailureDuringStampStreaming() throws Exception {
        assertStampStreamFailure(new IOException("Broken pipe"), false, false);
    }

    private void assertStampStreamFailure(IOException failure, boolean clientAbort, boolean partial) throws Exception {
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {1, 2, 3});
        mockResponse = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    private int accepted;

                    @Override
                    public void write(int value) throws IOException {
                        if (partial && accepted++ == 0) {
                            setCommitted(true);
                            return;
                        }
                        throw failure;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                        // The action streams synchronously.
                    }
                };
            }
        };
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
        Logger logger = mock(Logger.class);
        try (MockedStatic<MiscUtils> miscUtils = mockStatic(MiscUtils.class)) {
            miscUtils.when(MiscUtils::getLogger).thenReturn(logger);

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

            if (clientAbort) {
                assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
                verify(logger, never()).error(anyString(), any(Throwable.class));
            } else {
                assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                verify(logger).error("Error serving provider signature image", failure);
            }
            assertThat(mockResponse.isCommitted()).isEqualTo(partial);
        }
    }
}
