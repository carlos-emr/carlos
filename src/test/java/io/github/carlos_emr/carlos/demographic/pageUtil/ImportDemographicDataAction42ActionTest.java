/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
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
    void withUploadedFilesShouldValidateAndStoreImportUpload() throws Exception {
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
    void withUploadedFilesShouldRejectUploadOutsideAllowedTempDirectory() {
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
