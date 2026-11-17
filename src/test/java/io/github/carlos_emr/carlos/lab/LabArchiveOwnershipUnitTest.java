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
package io.github.carlos_emr.carlos.lab;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opening an archive path unsuccessfully must never grant ownership of another upload's file.
 * @since 2026-09-25
 */
@Tag("unit")
@Tag("lab")
class LabArchiveOwnershipUnitTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings = {"on.CML.Upload", "bc.PathNet.pageUtil"})
    void shouldPreserveExistingFile_whenArchiveOpenIsDenied(String packageName) throws Exception {
        Path existing = Files.writeString(root.resolve("existing.hl7"), "SYNTHETIC EXISTING UPLOAD");
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class);
                MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            CarlosProperties config = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(config);
            when(config.getProperty("DOCUMENT_DIR")).thenReturn(root.toString());
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(root.toFile());
            paths.when(() -> PathValidationUtils.resolveConfiguredDirectory(anyString(), anyString())).thenReturn(root.toFile());
            paths.when(() -> PathValidationUtils.validateGeneratedFileName(anyString())).thenAnswer(call -> call.getArgument(0));
            paths.when(() -> PathValidationUtils.validateGeneratedChildPath(anyString(), any(File.class))).thenReturn(existing.toFile());
            paths.when(() -> PathValidationUtils.validatePath(anyString(), any(File.class))).thenReturn(existing.toFile());
            files.when(() -> Files.newOutputStream(existing, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
                    .thenThrow(new AccessDeniedException("synthetic open failure"));
            Class<?> action = Class.forName("io.github.carlos_emr.carlos.lab.ca." + packageName + ".LabUpload2Action");
            Object result = ReflectionTestUtils.invokeMethod(action, "saveFile", new ByteArrayInputStream(new byte[]{1}), "synthetic.hl7");
            assertThat(result).isNull();
            files.verify(() -> Files.deleteIfExists(any(Path.class)), never());
        }
        assertThat(Files.readString(existing)).isEqualTo("SYNTHETIC EXISTING UPLOAD");
    }
}
