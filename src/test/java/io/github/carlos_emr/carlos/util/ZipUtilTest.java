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
package io.github.carlos_emr.carlos.util;

import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("zip utility tests")
@Tag("unit")
@Tag("fast")
class ZipUtilTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("unzipXML should handle short entry names")
    void shouldUnzipXml_whenEntryNameIsShort() throws Exception {
        Path zipPath = tempDir.resolve("short.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zipOutputStream.putNextEntry(new ZipEntry("a"));
            zipOutputStream.write("data".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }

        boolean result = zip.unzipXML(tempDir.toString(), "short.zip");

        assertThat(result).isTrue();
        assertThat(Files.readString(tempDir.resolve("a.xml"))).isEqualTo("data");
    }

    @Test
    @DisplayName("unzipXML should block ZIP Slip entries that escape the target directory")
    void shouldBlockZipSlip_whenEntryNameEscapesTargetDirectory() throws Exception {
        // unzipXML appends ".xml" to non-.zip entries, so "../slip-evil" becomes "../slip-evil.xml",
        // which resolves OUTSIDE tempDir. PathValidationUtils.validateZipEntryPath must reject it so
        // the entry is skipped, while a benign sibling entry still extracts normally.
        Path zipPath = tempDir.resolve("evil.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zipOutputStream.putNextEntry(new ZipEntry("../slip-evil"));
            zipOutputStream.write("pwned".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("good"));
            zipOutputStream.write("safe".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }

        boolean result = zip.unzipXML(tempDir.toString(), "evil.zip");

        // Extraction completes (malicious entry skipped, not fatal).
        assertThat(result).isTrue();
        // The escaping entry was NOT written outside the extraction directory.
        assertThat(tempDir.getParent().resolve("slip-evil.xml")).doesNotExist();
        // The benign entry still extracted inside the directory.
        assertThat(Files.readString(tempDir.resolve("good.xml"))).isEqualTo("safe");
    }

    @Test
    @DisplayName("unzipXML should omit invalid file name before logging")
    void shouldOmitFileName_whenZipExtensionIsMissing() {
        try (LogCapture capture = LogCapture.forLogger(zip.class)) {
            boolean result = zip.unzipXML(tempDir.toString(), "claim\r\nforged.txt");

            assertThat(result).isFalse();
            assertThat(capture.messages()).hasSize(1);
            String logged = capture.messages().get(0);
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).doesNotContain("claim\r\nforged.txt", "claim\\r\\nforged.txt", "forged.txt");
        }
    }
}
