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
package io.github.carlos_emr.carlos.form.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

@DisplayName("Form XML upload action security")
@Tag("unit")
@Tag("form")
@Tag("security")
class FrmXmlUpload2ActionSecurityUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        servletActionContextMock = org.mockito.Mockito.mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "post"})
    @DisplayName("should reject non-POST requests before privilege or upload processing")
    void shouldRejectRequest_whenMethodIsNotPost(String httpMethod) throws Exception {
        request.setMethod(httpMethod);

        String result = new FrmXmlUpload2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(securityInfoManager, never()).hasPrivilege(
                any(LoggedInInfo.class), any(String.class), any(String.class), nullable(String.class));
    }

    @Test
    @DisplayName("should reject direct form XML import for users with only _form read")
    void shouldRejectImport_whenUserIsFormReadOnly() {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("r"), isNull()))
                .thenReturn(true);

        FrmXmlUpload2Action action = new FrmXmlUpload2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.eform or _admin");

        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull());
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull());
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("r"), isNull());
    }

    @Test
    @DisplayName("should allow admin eForm writers to reach upload validation")
    void shouldAllowValidation_whenUserIsAdminEformWriter() throws Exception {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull()))
                .thenReturn(true);
        Path upload = Files.createFile(tempDir.resolve("admin-eform-forms.zip"));
        UploadedFile uploadedFile = uploadedFile(upload, ".hidden.zip", "application/zip");

        FrmXmlUpload2Action action = new FrmXmlUpload2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        assertThat(action.getActionErrors()).contains(PathValidationUtils.INVALID_FILENAME_MESSAGE);
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull());
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull());
    }

    @Test
    @DisplayName("should allow admin writers to reach upload validation")
    void shouldAllowValidation_whenUserIsAdminWriter() throws Exception {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull()))
                .thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);
        Path upload = Files.createFile(tempDir.resolve("admin-forms.zip"));
        UploadedFile uploadedFile = uploadedFile(upload, ".hidden.zip", "application/zip");

        FrmXmlUpload2Action action = new FrmXmlUpload2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        assertThat(action.getActionErrors()).contains(PathValidationUtils.INVALID_FILENAME_MESSAGE);
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull());
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull());
    }

    private static UploadedFile uploadedFile(Path content, String originalName, String contentType) {
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getContent()).thenReturn(content.toFile());
        when(uploadedFile.getOriginalName()).thenReturn(originalName);
        when(uploadedFile.getContentType()).thenReturn(contentType);
        return uploadedFile;
    }
}
