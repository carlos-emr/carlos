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

package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class NioFileManagerImplUnitTest {

    private NioFileManagerImpl nioFileManager;
    private Path outsideDir;
    private Path outsideFile;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        nioFileManager = new NioFileManagerImpl();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (outsideFile != null) {
            Files.deleteIfExists(outsideFile);
        }
        if (outsideDir != null) {
            Files.deleteIfExists(outsideDir);
        }
    }

    @Test
    void shouldDeleteTempFile_whenValidTempFileProvided() throws IOException {
        Path tempFile = Files.createFile(tempDir.resolve("valid-upload.tmp"));

        boolean deleted = nioFileManager.deleteTempFile(tempFile.toString());

        assertThat(deleted).isTrue();
        assertThat(tempFile).doesNotExist();
    }

    @Test
    void shouldThrowSecurityException_whenOutsidePathProvided() throws IOException {
        outsideDir = Path.of("target", "nio-delete-outside-" + UUID.randomUUID());
        Files.createDirectories(outsideDir);
        outsideFile = Files.createFile(outsideDir.resolve("outside.tmp"));

        assertThatThrownBy(() -> nioFileManager.deleteTempFile(outsideFile.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid temp deletion target");
        assertThat(outsideFile).exists();
    }

    @Test
    void shouldThrowSecurityException_whenDirectoryTargetProvided() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("not-a-file"));

        assertThatThrownBy(() -> nioFileManager.deleteTempFile(directory.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("regular file");
        assertThat(directory).isDirectory();
    }

    @Test
    void shouldReturnFalse_whenTempFileMissing() {
        Path missingFile = tempDir.resolve("missing-upload.tmp");

        boolean deleted = nioFileManager.deleteTempFile(missingFile.toString());

        assertThat(deleted).isFalse();
        assertThat(missingFile).doesNotExist();
    }
}
