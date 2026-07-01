/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 IHAPOIHandlerTest (upload) to JUnit 5
 * for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.oscarLab.ca.all.upload.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.IHAPOIHandler;

/**
 * Unit tests for {@link IHAPOIHandler} upload parse functionality.
 *
 * <p>Parameterized tests verifying that each IHAPOI HL7 message from the
 * test archive can be successfully parsed by the upload handler.
 * Migrated from legacy JUnit 4 IHAPOIHandlerTest (upload).
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("lab")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("IHAPOI upload handler unit tests")
class IHAPOIUploadHandlerUnitTest {

    static Stream<String> hl7MessageProvider() {
        URL url = Thread.currentThread().getContextClassLoader().getResource("IHAPOI_test_data.zip");
        if (url == null) {
            return Stream.empty();
        }

        List<String> messages = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(url.getPath())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".txt")) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(is, writer, StandardCharsets.UTF_8);
                        messages.add(writer.toString());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load IHAPOI test data", e);
        }
        return messages.stream();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should successfully parse IHAPOI HL7 message for upload")
    void shouldSuccessfullyParse_ihapoiHl7Message(String hl7Body) throws Exception {
        IHAPOIHandler handler = new IHAPOIHandler();
        java.util.Map<String, String> result = handler.parse(new ByteArrayInputStream(hl7Body.getBytes(StandardCharsets.UTF_8)));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should accept temp upload file outside document directory")
    void shouldAcceptTempUploadFile_whenOutsideDocumentDirectory() throws Exception {
        Path documentDir = Files.createTempDirectory("ihapoi-document-dir");
        Path uploadFile = Files.createTempFile("ihapoi-upload", ".txt");
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

            IHAPOIHandler handler = new IHAPOIHandler();
            Method method = IHAPOIHandler.class.getDeclaredMethod("validateAndGetFile", String.class);
            method.setAccessible(true);

            File result = (File) method.invoke(handler, uploadFile.toString());

            assertThat(result.getCanonicalFile()).isEqualTo(uploadFile.toFile().getCanonicalFile());
        } finally {
            restoreDocumentDir(previousDocumentDir);
            Files.deleteIfExists(uploadFile);
            Files.deleteIfExists(documentDir);
        }
    }

    @Test
    @DisplayName("should fail closed when document directory is missing")
    void shouldFailClosed_whenDocumentDirectoryIsMissing() throws Exception {
        Path uploadFile = Files.createTempFile("ihapoi-upload", ".txt");
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

        try {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");

            IHAPOIHandler handler = new IHAPOIHandler();
            Method method = IHAPOIHandler.class.getDeclaredMethod("validateAndGetFile", String.class);
            method.setAccessible(true);

            assertThatThrownBy(() -> method.invoke(handler, uploadFile.toString()))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(IOException.class);
        } finally {
            restoreDocumentDir(previousDocumentDir);
            Files.deleteIfExists(uploadFile);
        }
    }

    private static void restoreDocumentDir(String previousDocumentDir) {
        if (previousDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        }
    }

}
