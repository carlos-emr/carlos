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

import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link MeasurementManager#getFlowsheetDsHTML()} null-safety.
 *
 * <p>When {@code MEASUREMENT_DS_HTML_DIRECTORY} points at a missing/unreadable directory,
 * {@code File#listFiles()} returns {@code null}. The pre-fix code iterated it directly and threw a
 * {@link NullPointerException}; the added null guard must let the method skip that source and still
 * return normally.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MeasurementManager.getFlowsheetDsHTML null-safety")
class MeasurementFlowsheetDsHtmlUnitTest {

    @Test
    @DisplayName("should not throw when the configured flowsheet directory is missing")
    void shouldNotThrow_whenConfiguredDirectoryMissing() {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            // A configured-but-absent directory: resolveConfiguredDirectory permits the missing path,
            // and listFiles() on it returns null - the guard must skip it rather than NPE.
            when(props.getProperty("MEASUREMENT_DS_HTML_DIRECTORY"))
                    .thenReturn(System.getProperty("java.io.tmpdir") + "/carlos-missing-flowsheet-dir-xyz");

            List<String> result = MeasurementManager.getFlowsheetDsHTML();

            assertThat(result).isNotNull();
        }
    }
}
