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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.enumerator.LabType;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression tests for lab upload filename validation error messages.
 *
 * @since 2026-05-26
 */
@DisplayName("LabUploadWs regression tests")
@Tag("unit")
@Tag("webservice")
@Tag("regression")
class LabUploadWsRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should omit raw filename when path validation fails")
    void shouldOmitRawFilename_whenPathValidationFails() {
        String invalidFileName = "bad.enc";
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class);
                MockedStatic<PathValidationUtils> pathValidationMock = mockStatic(PathValidationUtils.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString() + File.separator);
            pathValidationMock.when(() -> PathValidationUtils.validatePath(anyString(), any(File.class)))
                    .thenThrow(new FileValidationException("unsafe filename ../secret.pdf"));

            LabUploadWs service = new LabUploadWs() {
                @Override
                protected HttpServletRequest getHttpServletRequest() {
                    return request;
                }
            };

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "importLab",
                    invalidFileName, "lab content", LabType.CLS, "999998"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("Invalid file path")
                    .hasCauseInstanceOf(FileValidationException.class);
        }
    }
}
