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
package io.github.carlos_emr.carlos.eform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.carlos_emr.carlos.eform.data.EForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EFormExportZip#exportForms} entry-name safety.
 *
 * <p>Export entry names are derived from the eForm's name and file name. Display titles are normalized before validation; stored filenames and image paths remain strict.
 * Generated names must preserve archive content and original metadata without traversal entries.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("EFormExportZip.exportForms entry-name safety")
class EFormExportZipExportUnitTest {

    private static EForm eform(String name, String fileName, String html) {
        EForm e = new EForm();
        e.setFormName(name);
        e.setFormFileName(fileName);
        e.setFormHtml(html);
        return e;
    }

    @Test
    @DisplayName("should write contained entry names for a benign form name")
    void shouldWriteContainedEntries_forBenignFormName() throws Exception {
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        List<EForm> forms = new ArrayList<>();
        forms.add(eform("WellChild", "wellchild.html", "<html><body>hi</body></html>"));

        new EFormExportZip().exportForms(forms, zipped);

        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipped.toByteArray()))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                entryNames.add(ze.getName());
            }
        }

        assertThat(entryNames)
                .contains("WellChild/eform.properties", "WellChild/wellchild.html")
                .allSatisfy(name -> assertThat(name).doesNotContain("..").doesNotStartWith("/"));
    }

    private static Map<String, byte[]> entries(EForm... forms) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new EFormExportZip().exportForms(List.of(forms), output);
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) result.put(entry.getName(), input.readAllBytes());
        }
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"Well Baby 0/6 months", "Well Baby 0\\6 months", "../evil", "C:\\forms", "Ça \"va\" Łódź", "line\r\nbreak"})
    void shouldPreserveOriginalMetadata_whenDisplayTitleNeedsSafeExportName(String name) throws Exception {
        Map<String, byte[]> archive = entries(eform(name, "form.html", "<p>Zoë Ł</p>"));
        assertThat(archive).hasSize(2);
        String propertiesPath = archive.keySet().stream().filter(path -> path.endsWith("/eform.properties")).findFirst().orElseThrow();
        String folder = propertiesPath.substring(0, propertiesPath.indexOf('/'));
        assertThat(folder).isNotBlank().doesNotStartWith(".").doesNotContain("\\", ":", "\r", "\n");
        assertThat(archive.keySet()).allSatisfy(path -> assertThat(path.split("/")).hasSize(2));
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(archive.get(propertiesPath)));
        assertThat(properties.getProperty("form.name")).isEqualTo(name);
        assertThat(properties.getProperty("form.htmlFilename")).isEqualTo("form.html");
        assertThat(new String(archive.get(folder + "/form.html"), StandardCharsets.UTF_8)).isEqualTo("<p>Zoë Ł</p>");
    }

    @Test
    void shouldGenerateSafeHtmlFilename_whenStoredFilenameMissing() throws Exception {
        Map<String, byte[]> archive = entries(eform("Well Baby 0/6 months", null, "<p>test</p>"));
        assertThat(archive).containsKeys("WellBaby0_6months/eform.properties", "WellBaby0_6months/WellBaby0_6months.html");
    }

    @Test
    void shouldKeepBothForms_whenSanitizedFoldersCollide() throws Exception {
        Map<String, byte[]> archive = entries(eform("A/B", "form.html", "first"), eform("A\\B", "form.html", "second"));
        assertThat(archive).hasSize(4);
        assertThat(new String(archive.get("A_B/form.html"), StandardCharsets.UTF_8)).isEqualTo("first");
        assertThat(new String(archive.get("A_B-2/form.html"), StandardCharsets.UTF_8)).isEqualTo("second");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil.html", "nested/form.html", "C:\\evil.html"})
    void shouldRejectStoredPaths_whenFilenameIsNotAComponent(String fileName) {
        assertThatThrownBy(() -> entries(eform("Valid", fileName, "test"))).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRejectImageTraversal_whenHtmlReferencesPath() {
        assertThatThrownBy(() -> entries(eform("Valid", "form.html", "<img src=\"${oscar_image_path}../secret.png\">")))
                .isInstanceOf(SecurityException.class);
    }
}
