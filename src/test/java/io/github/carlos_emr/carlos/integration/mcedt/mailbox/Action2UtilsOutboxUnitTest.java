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
package io.github.carlos_emr.carlos.integration.mcedt.mailbox;

import io.github.carlos_emr.CarlosProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@link Action2Utils} ONEDT_OUTBOX handling after blank-config guards
 * were added around PathValidationUtils.resolveConfiguredDirectory (which throws on a blank
 * path). A blank ONEDT_OUTBOX must degrade gracefully (empty list / no-op) instead of
 * throwing, while a configured outbox still lists its files (also covering the prior
 * listFiles()==null NPE path).
 */
@DisplayName("Action2Utils ONEDT_OUTBOX handling")
@Tag("unit")
@Tag("fast")
class Action2UtilsOutboxUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should return an empty upload list when ONEDT_OUTBOX is blank")
    void shouldReturnEmptyUploadList_whenOnedtOutboxIsBlank() {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("ONEDT_OUTBOX", "")).thenReturn("");

            List<File> result = Action2Utils.getUploadList();

            assertThat(result).isEmpty();
        }
    }

    @Test
    @DisplayName("should not throw when creating the outbox directory and ONEDT_OUTBOX is blank")
    void shouldSkipOutboxDirectoryCreation_whenOnedtOutboxIsBlank() {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("ONEDT_OUTBOX", "")).thenReturn("");

            assertThatCode(Action2Utils::createOnEDTOutboxDir).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("should list outbox files when ONEDT_OUTBOX is configured")
    void shouldReturnUploadList_whenOnedtOutboxConfigured() throws Exception {
        Files.createFile(tempDir.resolve("claim.txt"));
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("ONEDT_OUTBOX", "")).thenReturn(tempDir.toString());

            List<File> result = Action2Utils.getUploadList();

            assertThat(result).extracting(File::getName).contains("claim.txt");
        }
    }

    @Test
    @DisplayName("should read the last open back from the outbox's own .timestamp file")
    void shouldReadBackTheLastOpen_fromTheFileItWasWrittenTo() throws Exception {
        // #4577: the writer appended ".timestamp" to the directory's name (<parent>/outbox.timestamp)
        // while the reader opened <outbox>/.timestamp, so the copy window was always empty.
        Path outbox = Files.createDirectories(tempDir.resolve("outbox"));
        java.util.Date lastOpen = java.util.Date.from(java.time.Instant.parse("2026-10-15T13:41:27.315Z"));
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("ONEDT_OUTBOX", "")).thenReturn(outbox + "/");

            Action2Utils.setOutboxTimestamp(lastOpen);

            assertThat(Action2Utils.getOutboxTimestamp()).isEqualTo(lastOpen);
        }
        assertThat(outbox.resolve(".timestamp")).hasContent("2026-10-15T13:41:27.315Z");
        assertThat(tempDir.resolve("outbox.timestamp")).doesNotExist();
    }
}
