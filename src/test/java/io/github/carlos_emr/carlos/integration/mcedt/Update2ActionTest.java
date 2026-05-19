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
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.carlos.utility.FileValidationException;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Update2Action Unit Tests")
@Tag("unit")
@Tag("mcedt")
class Update2ActionTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("toUpdateRequest should expose FileValidationException when Struts upload content is not file-backed")
    void shouldThrowFileValidationException_whenUploadedContentIsNotFileBacked() {
        Update2Action action = new Update2Action();
        action.setResourceId("123");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("content");
        when(uploadedFile.getContent()).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        action.withUploadedFiles(List.of(uploadedFile));

        assertThatThrownBy(action::toUpdateRequest)
                .isInstanceOf(FileValidationException.class)
                .hasMessage("Uploaded file content is not file-backed");
    }

    @Test
    @DisplayName("withUploadedFiles should stop at first matching content field and expose first validation error")
    void shouldStopAtFirstMatchInWithUploadedFiles_andPreserveFirstError() throws Exception {
        Update2Action action = new Update2Action();
        action.setResourceId("123");

        UploadedFile failingUpload = mock(UploadedFile.class);
        when(failingUpload.getInputName()).thenReturn("content");
        when(failingUpload.getContent()).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        UploadedFile validUpload = mock(UploadedFile.class);
        lenient().when(validUpload.getInputName()).thenReturn("other");

        action.withUploadedFiles(List.of(failingUpload, validUpload));

        assertThatThrownBy(action::toUpdateRequest)
                .isInstanceOf(FileValidationException.class)
                .hasMessage("Uploaded file content is not file-backed");
    }
}
