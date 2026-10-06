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
package io.github.carlos_emr.carlos.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
 * Unit test for {@link MeasurementManager#getFlowsheetDsHTML()} null-safety and collection.
 *
 * <p>The method gathers flowsheet HTML names from a configured directory
 * ({@code MEASUREMENT_DS_HTML_DIRECTORY}). When that directory is missing/unreadable,
 * {@code File#listFiles()} returns {@code null}; the pre-fix code iterated it directly and threw a
 * {@link NullPointerException}. The guard must skip the absent source (returning normally) and,
 * when the directory does exist, collect only regular files (not subdirectories).</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MeasurementManager.getFlowsheetDsHTML")
class MeasurementFlowsheetDsHtmlUnitTest {

    @Test
    @DisplayName("should not throw and return a usable list when the configured directory is missing")
    void shouldReturnList_whenConfiguredDirectoryMissing() {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            // A configured-but-absent directory: resolveConfiguredDirectory permits the missing path,
            // and listFiles() on it returns null - the guard must skip it rather than NPE.
            when(props.getProperty("MEASUREMENT_DS_HTML_DIRECTORY"))
                    .thenReturn(System.getProperty("java.io.tmpdir") + "/carlos-missing-flowsheet-dir-xyz");

            List<String> result = MeasurementManager.getFlowsheetDsHTML();

            // The absent source contributes nothing; the method still returns a (possibly empty,
            // classpath-resource-populated) non-null list without crashing.
            assertThat(result).isNotNull();
        }
    }

    @Test
    @DisplayName("should collect regular file names from the configured directory and skip subdirectories")
    void shouldCollectRegularFiles_fromConfiguredDirectory(@TempDir Path flowsheetDir) throws IOException {
        Files.createFile(flowsheetDir.resolve("carlos-test-a.html"));
        Files.createFile(flowsheetDir.resolve("carlos-test-b.html"));
        Files.createDirectory(flowsheetDir.resolve("carlos-test-subdir")); // must be filtered out by isFile()

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("MEASUREMENT_DS_HTML_DIRECTORY")).thenReturn(flowsheetDir.toString());

            List<String> result = MeasurementManager.getFlowsheetDsHTML();

            assertThat(result)
                    .contains("carlos-test-a.html", "carlos-test-b.html")
                    .doesNotContain("carlos-test-subdir");
        }
    }
}
