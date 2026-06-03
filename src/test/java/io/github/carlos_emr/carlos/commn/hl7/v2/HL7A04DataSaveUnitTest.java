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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HL7A04Data#save()}, the A04 file writer that now uses try-with-resources.
 *
 * <p>Drives the happy path (message persisted to the configured build directory and the writer
 * closed) and append-mode behaviour against an existing file. The message/fileName fields are
 * populated internally from demographic data in production, so they are set reflectively here to
 * isolate the writer.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("HL7A04Data.save")
class HL7A04DataSaveUnitTest {

    @TempDir
    Path tempDir;

    private static void setField(HL7A04Data target, String name, Object value) throws Exception {
        Field f = HL7A04Data.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("should write the HL7 message to the build directory and return true")
    void shouldWriteMessageToBuildDirectory_whenSaved() throws Exception {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getHL7A04BuildDirectory()).thenReturn(tempDir.toString() + "/");

            HL7A04Data data = new HL7A04Data();
            String message = "MSH|^~\\&|CARLOS|FAC|RECV|DEST|20260601||ADT^A04|1|P|2.3";
            setField(data, "message", message);
            setField(data, "fileName", "a04_test.txt");

            boolean result = data.save();

            assertThat(result).isTrue();
            Path written = tempDir.resolve("a04_test.txt");
            assertThat(written).exists();
            assertThat(Files.readString(written)).isEqualTo(message);
        }
    }

    @Test
    @DisplayName("should append to an existing A04 file rather than truncate it")
    void shouldAppendToExistingFile_whenFileAlreadyExists() throws Exception {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getHL7A04BuildDirectory()).thenReturn(tempDir.toString() + "/");

            Path target = tempDir.resolve("append_test.txt");
            Files.writeString(target, "EXISTING\n");

            HL7A04Data data = new HL7A04Data();
            setField(data, "message", "APPENDED");
            setField(data, "fileName", "append_test.txt");

            assertThat(data.save()).isTrue();
            // FileWriter is opened in append mode, so prior content is preserved.
            assertThat(Files.readString(target)).isEqualTo("EXISTING\nAPPENDED");
        }
    }

    @Test
    @DisplayName("should preserve a '+' (timezone offset) in the generated A04 filename")
    void shouldPreservePlusInFilename_whenTimezoneFormatted() throws Exception {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getHL7A04BuildDirectory()).thenReturn(tempDir.toString() + "/");

            HL7A04Data data = new HL7A04Data();
            setField(data, "message", "MSH|^~\\&|CARLOS");
            // Mirrors the production name format yyyyMMddkkmmss.SSSZ + ".txt", whose RFC-822 'Z' yields
            // a '+0000'/'-0500' offset. The trusted generated name must be preserved, NOT run through a
            // user-input sanitizer that would strip the '+' and diverge from the MSH control ID.
            setField(data, "fileName", "20260601120000.123+0000.txt");

            assertThat(data.save()).isTrue();
            assertThat(tempDir.resolve("20260601120000.123+0000.txt")).exists();
        }
    }
}
