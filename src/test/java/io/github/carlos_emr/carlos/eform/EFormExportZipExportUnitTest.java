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
 * <p>Export entry names are derived from the eForm's name and file name. A name containing a path
 * separator or {@code ..} would otherwise produce traversal-style ZIP entries (ZIP-slip for whoever
 * extracts the exported archive), so the export validates each component with
 * {@code PathValidationUtils.validatePathComponent} before building the entry.</p>
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

    @Test
    @DisplayName("should reject a form name that would produce a traversal entry")
    void shouldReject_whenFormNameContainsTraversal() {
        List<EForm> forms = new ArrayList<>();
        forms.add(eform("../evil", "evil.html", "<html></html>"));

        // validatePathComponent throws FileValidationException (a SecurityException) on the path-bearing
        // form name, before any traversal-style ZIP entry can be written.
        assertThatThrownBy(() -> new EFormExportZip().exportForms(forms, new ByteArrayOutputStream()))
                .isInstanceOf(SecurityException.class);
    }
}
