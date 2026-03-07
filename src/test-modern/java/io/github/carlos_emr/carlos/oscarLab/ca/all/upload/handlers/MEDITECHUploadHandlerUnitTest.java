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
 * Migrated from legacy JUnit 4 MEDITECHHandlerTest (upload) to JUnit 5
 * for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.oscarLab.ca.all.upload.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MEDITECHHandler;

/**
 * Unit tests for {@link MEDITECHHandler} upload parse functionality.
 *
 * <p>Parameterized tests verifying that each MEDITECH HL7 message from the
 * test archive can be successfully parsed by the upload handler.
 * Migrated from legacy JUnit 4 MEDITECHHandlerTest (upload).
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("lab")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("MEDITECH upload handler unit tests")
class MEDITECHUploadHandlerUnitTest {

    static Stream<String> hl7MessageProvider() {
        URL url = Thread.currentThread().getContextClassLoader().getResource("MEDITECH_test_data.zip");
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
            throw new RuntimeException("Failed to load MEDITECH test data", e);
        }
        return messages.stream();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should successfully parse MEDITECH HL7 message for upload")
    void shouldSuccessfullyParse_meditechHl7Message(String hl7Body) throws Exception {
        MEDITECHHandler handler = new MEDITECHHandler();
        String result = handler.parse(new ByteArrayInputStream(hl7Body.getBytes(StandardCharsets.UTF_8)));
        assertThat(result).isNotNull();
    }
}
