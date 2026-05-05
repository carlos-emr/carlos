/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Hashtable;
import java.util.List;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddEditDocument2Action}.
 *
 * @since 2026-04-21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddEditDocument2Action Unit Tests")
@Tag("unit")
@Tag("documentManager")
class AddEditDocument2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private ProgramManager2 mockProgramManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private AddEditDocument2Action action;
    private File tempUploadFile;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(ProgramManager2.class, mockProgramManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        action = new AddEditDocument2Action();
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
    @DisplayName("should capture Struts 7 docFile upload metadata")
    void shouldCaptureMetadata_whenStruts7DocFileUploaded() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getAbsolutePath()).thenReturn(tempUploadFile.getAbsolutePath());
        when(uploadedFile.getOriginalName()).thenReturn("echart-upload.pdf");
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo("echart-upload.pdf");
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should capture Struts 7 filedata upload metadata when docFile is absent")
    void shouldCaptureStruts7FiledataUploadMetadata_whenDocFileAbsent() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("filedata");
        when(uploadedFile.getAbsolutePath()).thenReturn(tempUploadFile.getAbsolutePath());
        when(uploadedFile.getOriginalName()).thenReturn("html5-upload.pdf");
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo("html5-upload.pdf");
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should fall back to temp filename when Struts 7 original name is null")
    void shouldFallBackToTempFilename_whenStruts7OriginalNameIsNull() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getAbsolutePath()).thenReturn(tempUploadFile.getAbsolutePath());
        when(uploadedFile.getOriginalName()).thenReturn(null);
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo(tempUploadFile.getName());
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should fall back to temp filename when Struts 7 original name is only a path")
    void shouldFallBackToTempFilename_whenStruts7OriginalNameIsOnlyAPath() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getAbsolutePath()).thenReturn(tempUploadFile.getAbsolutePath());
        when(uploadedFile.getOriginalName()).thenReturn("/");
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo(tempUploadFile.getName());
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should prefer docFile over filedata regardless of list ordering")
    void shouldPreferDocFileOverFiledata_regardlessOfListOrdering() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");
        File filedataTemp = File.createTempFile("filedata-upload", ".pdf");
        try {
            UploadedFile filedataUpload = mock(UploadedFile.class);
            when(filedataUpload.getInputName()).thenReturn("filedata");
            when(filedataUpload.getAbsolutePath()).thenReturn(filedataTemp.getAbsolutePath());
            when(filedataUpload.getOriginalName()).thenReturn("html5-upload.pdf");
            when(filedataUpload.getContentType()).thenReturn("application/pdf");

            UploadedFile docFileUpload = mock(UploadedFile.class);
            when(docFileUpload.getInputName()).thenReturn("docFile");
            when(docFileUpload.getAbsolutePath()).thenReturn(tempUploadFile.getAbsolutePath());
            when(docFileUpload.getOriginalName()).thenReturn("echart-upload.pdf");
            when(docFileUpload.getContentType()).thenReturn("application/pdf");

            // filedata appears first in the list - docFile must still win
            action.withUploadedFiles(List.of(filedataUpload, docFileUpload));

            assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
            assertThat(action.getDocFileFileName()).isEqualTo("echart-upload.pdf");
        } finally {
            Files.deleteIfExists(filedataTemp.toPath());
        }
    }

    @Test
    @DisplayName("should return write error when html5 upload file write fails")
    void shouldReturnWriteError_whenHtml5UploadFileWriteFails() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");
        Files.writeString(tempUploadFile.toPath(), "test");

        action.setDocFile(tempUploadFile);
        action.setDocFileFileName("echart-upload.pdf");
        action.setAppointmentNo("123");

        try (MockedStatic<AddEditDocument2Action> addEditDocumentActionMock = mockStatic(AddEditDocument2Action.class, CALLS_REAL_METHODS)) {
            addEditDocumentActionMock.when(() -> AddEditDocument2Action.writeLocalFile(any(InputStream.class), eq("echart-upload.pdf")))
                    .thenReturn(null);

            String result = action.html5MultiUpload();

            addEditDocumentActionMock.verify(() -> AddEditDocument2Action.writeLocalFile(any(InputStream.class), eq("echart-upload.pdf")));
            assertThat(result).isNull();
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getHeader("oscar_error")).isEqualTo(ResourceBundle.getBundle("oscarResources")
                    .getString("dms.addDocument.errorNoWrite"));
        }
    }

    @Test
    @DisplayName("should return failAdd when uploaded document is missing")
    @SuppressWarnings("unchecked")
    void shouldReturnFailAdd_whenUploadedDocumentMissing() {
        action.setMode("add");
        action.setFunction("demographic");
        action.setFunctionId("123");
        action.setDocDesc("Consult note");
        action.setDocType("Consultant Report");

        String result = action.execute2();

        assertThat(result).isEqualTo("failAdd");
        Hashtable<String, String> errors = (Hashtable<String, String>) request.getAttribute("docerrors");
        assertThat(errors).containsEntry("uploaderror", "dms.error.uploadError");
    }
}
