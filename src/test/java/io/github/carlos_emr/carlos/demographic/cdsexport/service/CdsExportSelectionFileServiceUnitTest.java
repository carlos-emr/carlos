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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportSelectionFileServiceUnitTest {
    @TempDir Path root;

    @Test
    void shouldCaptureTheExactProtectedUtf8Selection_withoutLookingUpPatients() throws Exception {
        var loader = loader();
        Path file = selected("{\"version\":1,\"patients\":[28,27]}");
        assertThat(loader.capture(file)).containsExactly(27, 28);
    }

    @Test
    void shouldRefuseCoercionDuplicatesUnknownFieldsOrEmptySelection_insteadOfChangingIntent()
            throws Exception {
        for (String json :
                new String[] {
                    "{\"version\":1,\"patients\":[]}",
                    "{\"version\":1,\"patients\":[27,27]}",
                    "{\"version\":1,\"patients\":[\"27\"]}",
                    "{\"version\":1,\"patients\":[27.0]}",
                    "{\"version\":1,\"patients\":[0]}",
                    "{\"version\":1,\"patients\":[2147483648]}",
                    "{\"version\":1,\"patients\":[27],\"all\":true}",
                    "{\"version\":2,\"patients\":[27]}",
                    "{\"version\":1,\"patients\":[27],\"patients\":[28]}",
                    "{\"version\":1,\"patients\":[27]}{}"
                }) {
            assertThatThrownBy(
                            () ->
                                    CdsExportSelectionFileService.parse(
                                            json.getBytes(StandardCharsets.UTF_8)))
                    .hasMessage("CDS_EXPORT_SELECTION_FILE_INVALID");
        }
    }

    @Test
    void shouldRequireUtf8_andAcceptOnlyAnOptionalUtf8Bom() throws Exception {
        String json = "{\"version\":1,\"patients\":[27]}";
        assertThatThrownBy(
                        () ->
                                CdsExportSelectionFileService.parse(
                                        json.getBytes(StandardCharsets.UTF_16)))
                .hasMessage("CDS_EXPORT_SELECTION_FILE_INVALID");
        byte[] bytes = ("\ufeff" + json).getBytes(StandardCharsets.UTF_8);
        assertThat(CdsExportSelectionFileService.parse(bytes)).containsExactly(27);
    }

    @Test
    void shouldRefuseLinksOutsideTheRootAndWorldReadableInputs() throws Exception {
        var loader = loader();
        Path file = selected("{\"version\":1,\"patients\":[27]}");
        Path link = root.resolve("linked.json");
        Files.createSymbolicLink(link, file);
        assertThatThrownBy(() -> loader.capture(link))
                .hasMessage("CDS_EXPORT_SELECTION_FILE_INVALID");
        assertThatThrownBy(() -> loader.capture(root.getParent().resolve("outside.json")))
                .hasMessage("CDS_EXPORT_SELECTION_FILE_INVALID");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        assertThatThrownBy(() -> loader.capture(file))
                .hasMessage("CDS_EXPORT_SELECTION_FILE_INVALID");
    }

    @Test
    void shouldRefuseSharedWritableRoots_andOversizedSelectionFiles() throws Exception {
        var loader = loader();
        Path file = selected("{\"version\":1,\"patients\":[27]}");
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxrwx---"));
        assertThatThrownBy(() -> loader.capture(file))
                .hasMessage("CDS_EXPORT_SELECTION_FILE_INVALID");
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Files.write(file, new byte[262145]);
        assertThatThrownBy(() -> loader.capture(file))
                .hasMessage("CDS_EXPORT_SELECTION_FILE_INVALID");
    }

    private CdsExportSelectionFileService loader() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        var properties = new Properties();
        properties.setProperty("cds.export.selectionRoot", root.toString());
        return new CdsExportSelectionFileService(new CdsExportConfigurationService(properties));
    }

    private Path selected(String json) throws Exception {
        Path file = Files.writeString(root.resolve("selected.json"), json, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        return file;
    }
}
