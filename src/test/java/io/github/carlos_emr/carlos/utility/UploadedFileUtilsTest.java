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
package io.github.carlos_emr.carlos.utility;

import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadedFileUtils Unit Tests")
@Tag("unit")
class UploadedFileUtilsTest {

    @Mock
    private UploadedFile uploadedFile;

    @Test
    @DisplayName("should return FileValidationException when upload is null")
    void shouldThrowOnNullUpload() {
        assertThatThrownBy(() -> UploadedFileUtils.getUploadedFile(null))
                .isInstanceOf(FileValidationException.class)
                .hasMessage("Uploaded file is null");
    }

    @Test
    @DisplayName("should return file-backed content for file uploads")
    void shouldReturnFileForFileBackedUpload() throws IOException {
        File tempFile = File.createTempFile("uploaded-file-utils", ".txt");

        when(uploadedFile.getContent()).thenReturn(tempFile);
        File result = UploadedFileUtils.getUploadedFile(uploadedFile);

        assertThat(result).isEqualTo(tempFile);
    }

    @Test
    @DisplayName("should throw FileValidationException when content is not file-backed")
    void shouldThrowWhenUploadContentIsNotFileBacked() {
        when(uploadedFile.getContent()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThatThrownBy(() -> UploadedFileUtils.getUploadedFile(uploadedFile))
                .isInstanceOf(FileValidationException.class)
                .hasMessage("Uploaded file content is not file-backed");
    }

    @Test
    @DisplayName("should return null for null upload in getUploadedFileOrNull")
    void shouldReturnNullWhenUploadIsNull() {
        assertNull(UploadedFileUtils.getUploadedFileOrNull(null));
    }

    @Test
    @DisplayName("should return null when content is not file-backed in getUploadedFileOrNull")
    void shouldReturnNullWhenContentIsNotFileBackedInOrNull() {
        when(uploadedFile.getContent()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertNull(UploadedFileUtils.getUploadedFileOrNull(uploadedFile));
    }
}
