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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.service.AcceptableUseAgreementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
    private PropertyDao propertyDao;
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
        propertyDao = mock(PropertyDao.class);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        carlosPropertiesStatic = mockStatic(CarlosProperties.class);
        carlosPropertiesStatic.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

        registerMock(PropertyDao.class, propertyDao);
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

        assertThat(action.getImportFile()).isEqualTo(uploadFile.toFile().getCanonicalFile());
    }

    @ParameterizedTest
    @ValueSource(strings = {"year", "month", "weeks", "days"})
    @DisplayName("should persist duration property when period is selected")
    void shouldPersistDurationProperty_whenPeriodSelected(String period) throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        Path documentDir = Files.createDirectory(tempDir.resolve("document"));
        Property latestProperty = new Property();
        latestProperty.setValue("14 days");
        acceptableUseAgreementManagerStatic.when(AcceptableUseAgreementManager::findLatestProperty)
                .thenReturn(latestProperty);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        request.setMethod("POST");
        request.addParameter("validForever", "duration");
        request.addParameter("validDurationNumber", "30");
        request.addParameter("validDurationPeriod", period);

        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());

        String result = action.execute();

        ArgumentCaptor<Property> propertyCaptor = ArgumentCaptor.forClass(Property.class);
        verify(propertyDao).persist(propertyCaptor.capture());
        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(propertyCaptor.getValue().getName()).isEqualTo("aua_valid_duration");
        assertThat(propertyCaptor.getValue().getValue()).isEqualTo("30 " + period);
    }

    @Test
    @DisplayName("should skip AUA persist when duration input is invalid")
    void shouldSkipAuaPersist_whenDurationInvalid() throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        Path documentDir = Files.createDirectory(tempDir.resolve("document"));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        request.setMethod("POST");
        request.addParameter("validDurationNumber", "not-number");

        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("error")).isEqualTo(false);
        verify(propertyDao, never()).persist(any(Property.class));
    }

    @Test
    @DisplayName("should skip AUA persist when duration period is unsupported")
    void shouldSkipAuaPersist_whenDurationPeriodUnsupported() throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        Path documentDir = Files.createDirectory(tempDir.resolve("document"));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        request.setMethod("POST");
        request.addParameter("validDurationNumber", "30");
        request.addParameter("validDurationPeriod", "fortnight");

        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("error")).isEqualTo(false);
        verify(propertyDao, never()).persist(any(Property.class));
    }

    @Test
    @DisplayName("should skip AUA persist when duration period is missing")
    void shouldSkipAuaPersist_whenDurationPeriodMissing() throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        Path documentDir = Files.createDirectory(tempDir.resolve("document"));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        request.setMethod("POST");
        request.addParameter("validDurationNumber", "30");

        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("error")).isEqualTo(false);
        verify(propertyDao, never()).persist(any(Property.class));
    }

    @Test
    @DisplayName("should skip AUA persist when latest property is unchanged")
    void shouldSkipAuaPersist_whenLatestPropertyUnchanged() throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        Path documentDir = Files.createDirectory(tempDir.resolve("document"));
        Property latestProperty = new Property();
        latestProperty.setValue("2026-06-10");
        acceptableUseAgreementManagerStatic.when(AcceptableUseAgreementManager::findLatestProperty)
                .thenReturn(latestProperty);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        request.setMethod("POST");
        request.addParameter("validForever", "forever");
        request.addParameter("foreverFrom", "2026-06-10");

        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(propertyDao, never()).persist(any(Property.class));
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

    @Test
    @DisplayName("should return success when import file is missing")
    void shouldReturnSuccess_whenImportFileMissing() throws Exception {
        Property latestProperty = new Property();
        latestProperty.setValue("2026-06-10");
        acceptableUseAgreementManagerStatic.when(AcceptableUseAgreementManager::findLatestProperty)
                .thenReturn(latestProperty);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        request.setMethod("POST");
        request.addParameter("validForever", "forever");
        request.addParameter("foreverFrom", "2026-06-10");

        String result = new UploadLoginText2Action(securityInfoManager).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("error")).isEqualTo(false);
        verify(carlosProperties, never()).getProperty("DOCUMENT_DIR");
        verify(propertyDao, never()).persist(any(Property.class));
    }

    @Test
    @DisplayName("should preserve login text when upload read fails")
    void shouldPreserveLoginText_whenUploadReadFails() throws Exception {
        Path documentDir = Files.createDirectory(tempDir.resolve("document"));
        Path existingLoginText = documentDir.resolve("OSCARloginText.txt");
        Files.writeString(existingLoginText, "existing login text");
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "updated login text");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        request.setMethod("POST");
        request.addParameter("validDurationNumber", "1");
        request.addParameter("validDurationPeriod", "year");
        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());
        Files.delete(uploadFile);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("error")).isEqualTo(true);
        assertThat(existingLoginText).hasContent("existing login text");
        try (Stream<Path> documentFiles = Files.list(documentDir)) {
            assertThat(documentFiles)
                    .extracting(path -> path.getFileName().toString())
                    .containsExactly("OSCARloginText.txt");
        }
    }

    @Test
    @DisplayName("should accept uploaded file content from Struts binding")
    void shouldAcceptUploadedFileContent_whenUploadedFilesProvided() throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getContent()).thenReturn(uploadFile.toFile());
        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getImportFile()).isEqualTo(uploadFile.toFile().getCanonicalFile());
    }

    @Test
    @DisplayName("should ignore missing uploaded files")
    void shouldIgnoreMissingUploadedFiles_whenNoUploadsProvided() {
        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);

        action.withUploadedFiles(null);
        action.withUploadedFiles(List.of());

        assertThat(action.getImportFile()).isNull();
    }

    @Test
    @DisplayName("should clear import file when setter receives null")
    void shouldClearImportFile_whenSetterReceivesNull() throws Exception {
        Path uploadFile = Files.writeString(tempDir.resolve("login-upload.txt"), "welcome");
        UploadLoginText2Action action = new UploadLoginText2Action(securityInfoManager);
        action.setImportFile(uploadFile.toFile());

        action.setImportFile(null);

        assertThat(action.getImportFile()).isNull();
    }
}
