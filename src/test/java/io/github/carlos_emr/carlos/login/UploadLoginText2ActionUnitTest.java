/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.service.AcceptableUseAgreementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Upload login text 2Action")
@Tag("unit")
@Tag("login")
@Tag("security")
class UploadLoginText2ActionUnitTest extends CarlosUnitTestBase {

    @TempDir
    private Path tempDir;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private CarlosProperties carlosProperties;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private MockedStatic<CarlosProperties> carlosPropertiesStatic;
    private MockedStatic<AcceptableUseAgreementManager> acceptableUseAgreementManagerStatic;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        carlosProperties = mock(CarlosProperties.class);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        carlosPropertiesStatic = mockStatic(CarlosProperties.class);
        carlosPropertiesStatic.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

        registerMock(PropertyDao.class, mock(PropertyDao.class));
        acceptableUseAgreementManagerStatic = mockStatic(AcceptableUseAgreementManager.class);
        acceptableUseAgreementManagerStatic.when(AcceptableUseAgreementManager::findLatestProperty)
                .thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (acceptableUseAgreementManagerStatic != null) {
            acceptableUseAgreementManagerStatic.close();
        }
        if (carlosPropertiesStatic != null) {
            carlosPropertiesStatic.close();
        }
        if (loggedInInfoStatic != null) {
            loggedInInfoStatic.close();
        }
        if (servletActionContext != null) {
            servletActionContext.close();
        }
    }

    @Test
    @DisplayName("should reject POST when admin write privilege is denied")
    void shouldRejectPost_whenAdminWriteDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(false);
        request.setMethod("POST");

        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("(_admin)");
        verify(carlosProperties, never()).getProperty("DOCUMENT_DIR");
    }

    @Test
    @DisplayName("should write fixed login text file when POST is authorized")
    void shouldWriteLoginText_whenPostAuthorized() throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        Path documentDir = Files.createDirectory(tempDir.resolve("document"));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        request.setMethod("POST");
        request.addParameter("validForever", "forever");
        request.addParameter("foreverFrom", "2026-06-10");

        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("error")).isEqualTo(false);
        assertThat(Files.readString(documentDir.resolve("OSCARloginText.txt"))).isEqualTo("welcome");
    }

    @Test
    @DisplayName("should set error when DOCUMENT_DIR is misconfigured")
    void shouldSetError_whenDocumentDirMisconfigured() throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(" ");
        request.setMethod("POST");
        request.addParameter("validForever", "forever");
        request.addParameter("foreverFrom", "2026-06-10");

        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("error")).isEqualTo(true);
    }

    @Test
    @DisplayName("should reject GET before file side effects")
    void shouldRejectGet_beforeFileSideEffects() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        request.setMethod("GET");

        String result = new UploadLoginText2Action(securityInfoManager).execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(carlosProperties, never()).getProperty("DOCUMENT_DIR");
    }
}
