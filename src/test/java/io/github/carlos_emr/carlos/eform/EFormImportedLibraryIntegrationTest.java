/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormGroupDao;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/** Real DAO and legacy-library compatibility inside a caller-owned rollback transaction. */
@Tag("integration") @Tag("dao") @Tag("eform") @Transactional
class EFormImportedLibraryIntegrationTest extends CarlosTestBase {
    @Autowired private EFormDao forms;
    @Autowired private EFormGroupDao groups;
    @Test void shouldExposeImportedFormInTheActiveLibraryAndItsGroup(@TempDir Path images) throws Exception {
        String title = "FAKE imported library " + UUID.randomUUID();
        // Keep the synthetic name within the physical eform_groups.group_name width.
        String group = "FAKE " + UUID.randomUUID().toString().substring(0, 12);
        String html = "<html><body>FAKE imported content</body></html>";
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : Map.of("eform.properties", "form.name=" + title + "\nform.htmlFilename=form.html\n",
                    "form.html", html).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        assertThat(EFormUtil.formExistsInDB(title)).isFalse();
        try (var folders = mockStatic(ImageUpload2Action.class)) {
            folders.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(new EFormExportZip(Runnable::run).importForm(new ByteArrayInputStream(bytes.toByteArray()))).isEmpty();
        }
        Integer id = forms.findMaxIdForActiveForm(title);
        assertThat(id).isNotNull();
        assertThat(EFormUtil.formExistsInDB(title)).isTrue();
        assertThat(forms.find(id).getFormHtml()).isEqualTo(html);
        // The native group-creation flow first stores the explicit zero-ID marker.
        groups.addActiveFormIfMissing(group, 0);
        groups.addActiveFormIfMissing(group, id);
        assertThat(EFormUtil.getEFormGroups()).anySatisfy(row -> {
            assertThat(row.get("groupName")).isEqualTo(group);
            assertThat(row.get("count")).isEqualTo("1");
        });
        assertThat(EFormUtil.listEForms(EFormUtil.NAME, EFormUtil.CURRENT, group, ""))
                .singleElement().satisfies(row -> assertThat(row.get("formName")).isEqualTo(title));
    }
}
