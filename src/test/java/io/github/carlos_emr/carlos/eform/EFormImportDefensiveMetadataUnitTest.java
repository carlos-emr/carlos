/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@Tag("unit")
class EFormImportDefensiveMetadataUnitTest {
    @TempDir Path images;
    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldRejectIncompleteCustomFormMetadata_beforeRegisteringOrPublishing(boolean missingName) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("eform.properties"));
            zip.write("form.name=FAKE custom metadata\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("orphan.png"));
            zip.write(new byte[]{1,2,3});
            zip.closeEntry();
        }
        var importer = new EFormExportZip(registrations -> {throw new AssertionError("Invalid metadata reached registration");}) {
            @Override public EForm createEFormFromProperties(Properties properties) {
                var form = new EForm();
                form.setFormName(missingName ? null : "FAKE custom metadata");
                form.setFormFileName(missingName ? "missing.html" : "");
                return form;
            }
        };
        try (var folder = mockStatic(ImageUpload2Action.class)) {
            folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(importer.importForm(new ByteArrayInputStream(bytes.toByteArray())))
                    .singleElement().asString().contains(missingName ? "no form name" : "no form filename");
        }
        assertThat(images.resolve("orphan.png")).doesNotExist();
        try (var entries = Files.list(images.resolve("extractFolder"))) {
            var names = entries.map(path -> path.getFileName().toString()).toList();
            if (missingName) {
                assertThat(names).containsExactly(EFormImagePublicationLock.LOCK_FILE_NAME);
            } else {
                // Without a filename the importer returns before acquiring the publication lock.
                assertThat(names).isEmpty();
            }
        }
    }
}
