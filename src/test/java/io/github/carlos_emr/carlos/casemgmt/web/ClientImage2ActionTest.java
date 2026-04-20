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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.casemgmt.service.ClientImageManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ClientImage2Action Unit Tests")
@Tag("unit")
@Tag("casemgmt")
class ClientImage2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private ClientImageManager clientImageManager;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private ClientImage2Action action;
    private File tempUploadFile;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setParameter("method", "saveImage");
        request.getSession().setAttribute("clientId", "123");

        registerMock(ClientImageManager.class, clientImageManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_demographic"), eq("w"), isNull()))
                .thenReturn(true);

        action = new ClientImage2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempUploadFile != null) {
            Files.deleteIfExists(tempUploadFile.toPath());
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should save client image when Struts 7 uploaded file is provided")
    void shouldSaveClientImage_whenStruts7UploadedFileProvided() throws Exception {
        tempUploadFile = File.createTempFile("client-image", ".jpeg");
        byte[] expectedBytes = new byte[]{1, 2, 3, 4};
        Files.write(tempUploadFile.toPath(), expectedBytes);

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getAbsolutePath()).thenReturn(tempUploadFile.getAbsolutePath());
        when(uploadedFile.getOriginalName()).thenReturn("patient-photo.jpeg");

        action.withUploadedFiles(List.of(uploadedFile));

        String result = action.execute();

        ArgumentCaptor<ClientImage> imageCaptor = ArgumentCaptor.forClass(ClientImage.class);
        verify(clientImageManager).saveClientImage(imageCaptor.capture());

        ClientImage savedImage = imageCaptor.getValue();
        assertThat(result).isEqualTo("success");
        assertThat(savedImage.getDemographic_no()).isEqualTo(123);
        assertThat(savedImage.getImage_type()).isEqualTo("jpeg");
        assertThat(savedImage.getImage_data()).isEqualTo(expectedBytes);
        assertThat(request.getAttribute("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("should save client image when GIF is uploaded")
    void shouldSaveClientImage_whenGifUploaded() throws Exception {
        tempUploadFile = File.createTempFile("client-image", ".gif");
        byte[] expectedBytes = new byte[]{9, 8, 7, 6};
        Files.write(tempUploadFile.toPath(), expectedBytes);

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getAbsolutePath()).thenReturn(tempUploadFile.getAbsolutePath());
        when(uploadedFile.getOriginalName()).thenReturn("patient-photo.gif");

        action.withUploadedFiles(List.of(uploadedFile));

        String result = action.execute();

        ArgumentCaptor<ClientImage> imageCaptor = ArgumentCaptor.forClass(ClientImage.class);
        verify(clientImageManager).saveClientImage(imageCaptor.capture());

        ClientImage savedImage = imageCaptor.getValue();
        assertThat(result).isEqualTo("success");
        assertThat(savedImage.getImage_type()).isEqualTo("gif");
        assertThat(savedImage.getImage_data()).isEqualTo(expectedBytes);
    }

    @Test
    @DisplayName("should return error when no client is selected")
    void shouldReturnError_whenClientIdMissing() {
        request.getSession().removeAttribute("clientId");

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(action.getActionErrors()).contains("No client selected.");
        verifyNoInteractions(clientImageManager);
    }

    @Test
    @DisplayName("should return error when uploaded file extension is not supported")
    void shouldReturnError_whenUploadedFileExtensionNotSupported() throws Exception {
        tempUploadFile = File.createTempFile("client-image", ".bmp");
        Files.write(tempUploadFile.toPath(), new byte[]{5, 4, 3, 2});

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getAbsolutePath()).thenReturn(tempUploadFile.getAbsolutePath());
        when(uploadedFile.getOriginalName()).thenReturn("patient-photo.bmp");

        action.withUploadedFiles(List.of(uploadedFile));

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(action.getActionErrors()).contains("Only GIF and JPG image types are allowed for the client photo.");
        verifyNoInteractions(clientImageManager);
    }

    @Test
    @DisplayName("should return error when no image is uploaded")
    void shouldReturnError_whenNoImageUploaded() {
        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(action.getActionErrors()).contains("Please select an image to upload.");
        verifyNoInteractions(clientImageManager);
    }
}
