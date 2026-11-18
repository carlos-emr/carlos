/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Actual exporter/importer round trips across the former 16 MiB HTML ceiling (#6018). */
@Tag("unit")
class EFormLargeHtmlRoundTripUnitTest {
    @TempDir Path images;

    @ParameterizedTest
    @CsvSource({"html,17825792", "png,17825792", "html,52428800"})
    void shouldReimportExactExportedHtml_withSupportedRetainedSize(String extension, int bytes) throws Exception {
        String prefix="<!doctype html><p>Synthetic Ω</p>";
        String html=prefix+"x".repeat(bytes-prefix.getBytes(StandardCharsets.UTF_8).length);
        assertThat(html.getBytes(StandardCharsets.UTF_8)).hasSize(bytes);
        EForm form=new EForm();form.setFormName("Synthetic large round trip");form.setFormFileName("fixture."+extension);form.setFormHtml(html);
        ByteArrayOutputStream exported=new ByteArrayOutputStream();
        new EFormExportZip().exportForms(List.of(form),exported);
        try (var folders=mockStatic(ImageUpload2Action.class);var forms=mockStatic(EFormUtil.class)) {
            folders.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            forms.when(()->EFormUtil.formExistsInDB(any())).thenReturn(false);
            assertThat(new EFormExportZip(Runnable::run).importForm(new ByteArrayInputStream(exported.toByteArray()))).isEmpty();
            forms.verify(()->EFormUtil.saveEForm(argThat(imported->html.equals(imported.getFormHtml()))),times(1));
        }
        // A cooperating publication implementation may retain its empty lock inode.
        // Every extracted file/directory must still be gone; never permit a symlink.
        try (var staged=java.nio.file.Files.list(images.resolve("extractFolder"))) {
            for (Path retained : staged.toList()) {
                assertThat(retained.getFileName().toString()).isEqualTo(".publication.lock");
                assertThat(java.nio.file.Files.isSymbolicLink(retained)).isFalse();
                assertThat(retained).isRegularFile();
                assertThat(java.nio.file.Files.size(retained)).isZero();
            }
        }
    }
}
