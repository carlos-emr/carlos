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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Component test for {@link EFormExportZip#importForm(java.io.InputStream)} image handling.
 *
 * <p>A zip that contains only a supporting image (no {@code eform.properties}) drives the image
 * branch of {@code importForm} without touching the EFormUtil/DB save path, so it can be exercised
 * with a real zip + temp image folder. This pins the fixed behaviour: an image whose name already
 * exists is <em>skipped</em> (the prior file is preserved and a collision error is surfaced) rather
 * than silently overwritten, while a brand-new image is still written.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("eform")
@DisplayName("EFormExportZip.importForm image handling")
class EFormExportZipImageImportTest {

    /** {@code getImageFolder()} is the base for both the temp-extract folder and the final images. */
    @TempDir
    Path imageFolder;

    private static byte[] zipWithEntry(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("should NOT overwrite an existing image and should surface a collision error")
    void shouldSkipImage_whenImageAlreadyExists() throws Exception {
        Path existing = imageFolder.resolve("logo.png");
        Files.write(existing, "ORIGINAL".getBytes());
        byte[] zip = zipWithEntry("logo.png", "REPLACEMENT".getBytes());

        List<String> errors;
        try (MockedStatic<ImageUpload2Action> imgMock = mockStatic(ImageUpload2Action.class)) {
            imgMock.when(ImageUpload2Action::getImageFolder).thenReturn(imageFolder.toFile());
            errors = new EFormExportZip().importForm(new ByteArrayInputStream(zip));
        }

        // The pre-existing image is preserved, not clobbered by the import.
        assertThat(Files.readString(existing)).isEqualTo("ORIGINAL");
        // ...and the collision is reported back to the caller.
        assertThat(errors).anyMatch(e -> e.contains("logo.png") && e.contains("already exists"));
    }

    @Test
    @DisplayName("should write a new image when no file of that name exists yet")
    void shouldWriteImage_whenImageDoesNotExist() throws Exception {
        byte[] zip = zipWithEntry("newlogo.png", "PNG-BYTES".getBytes());

        List<String> errors;
        try (MockedStatic<ImageUpload2Action> imgMock = mockStatic(ImageUpload2Action.class)) {
            imgMock.when(ImageUpload2Action::getImageFolder).thenReturn(imageFolder.toFile());
            errors = new EFormExportZip().importForm(new ByteArrayInputStream(zip));
        }

        Path written = imageFolder.resolve("newlogo.png");
        assertThat(written).exists();
        assertThat(Files.readString(written)).isEqualTo("PNG-BYTES");
        assertThat(errors).noneMatch(e -> e.contains("already exists"));
    }
}
