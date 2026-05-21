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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("ImportDemographicDataAction42Action")
@Tag("unit")
class ImportDemographicDataAction42ActionTest {

    @Test
    void shouldValidateAndStoreImportUpload_whenUploadSourceIsAllowed() throws Exception {
        ImportDemographicDataAction42Action action = Mockito.mock(
                ImportDemographicDataAction42Action.class,
                Mockito.CALLS_REAL_METHODS
        );
        Path tempUpload = Files.createTempFile("import-demographic", ".zip");

        try {
            UploadedFile uploaded = uploadedFile("importFile", tempUpload.toFile(), "demo.zip");

            action.withUploadedFiles(List.of(uploaded));

            assertThat(action.getImportFile()).isEqualTo(tempUpload.toFile());
            assertThat(action.getImportFileFileName()).isEqualTo("demo.zip");
        } finally {
            Files.deleteIfExists(tempUpload);
        }
    }

    @Test
    void shouldRejectUpload_whenSourceIsOutsideAllowedTempDirectory() {
        ImportDemographicDataAction42Action action = Mockito.mock(
                ImportDemographicDataAction42Action.class,
                Mockito.CALLS_REAL_METHODS
        );
        File outsideUpload = new File("pom.xml").getAbsoluteFile();

        UploadedFile uploaded = uploadedFile("importFile", outsideUpload, "outside.zip");

        assertThatThrownBy(() -> action.withUploadedFiles(List.of(uploaded)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid upload source");
    }

    private static UploadedFile uploadedFile(String inputName, File file, String originalName) {
        UploadedFile uploadedFile = Mockito.mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn(inputName);
        when(uploadedFile.getAbsolutePath()).thenReturn(file.getAbsolutePath());
        when(uploadedFile.getOriginalName()).thenReturn(originalName);
        return uploadedFile;
    }
}
