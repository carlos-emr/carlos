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

import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NioFileManagerImpl copyFileToOscarDocuments")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("security")
class NioFileManagerImplCopyFileUnitTest {

    @TempDir
    Path documentDir;

    private NioFileManagerImpl fileManager;

    @BeforeEach
    void setUp() {
        fileManager = new TestableNioFileManagerImpl(documentDir);
    }

    @Test
    @DisplayName("should copy approved temp source into Oscar documents")
    void shouldCopyApprovedTempSource_whenSourceIsInAllowedTempDirectory() throws IOException {
        Path source = Files.createTempFile("copy-approved-", ".pdf");
        Files.writeString(source, "approved temp source", StandardCharsets.UTF_8);

        try {
            String result = fileManager.copyFileToOscarDocuments(source.toString());

            Path destination = documentDir.resolve(source.getFileName().toString());
            assertThat(result).isEqualTo(destination.toString());
            assertThat(Files.readString(destination)).isEqualTo("approved temp source");
            assertThat(Files.exists(source)).isFalse();
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    @DisplayName("should reject copy when source is outside approved temp directories")
    void shouldRejectCopy_whenSourceIsOutsideApprovedTempDirectories() {
        File source = new File("/etc/hostname");
        Assumptions.assumeTrue(source.exists() && source.isFile());
        Assumptions.assumeFalse(PathValidationUtils.isInAllowedTempDirectory(source));

        String result = fileManager.copyFileToOscarDocuments(source.toString());

        assertThat(result).isNull();
        assertThat(Files.exists(documentDir.resolve(source.getName()))).isFalse();
    }

    @Test
    @DisplayName("should return destination path when cleanup rejects source after successful copy")
    void shouldReturnDestinationPath_whenCleanupRejectsSourceAfterSuccessfulCopy() throws IOException {
        Path sourceDir = Files.createTempDirectory("tempDirectory-cleanup-");
        Path source = sourceDir.resolve("cleanup-fails.pdf");
        Files.writeString(source, "cleanup failure still persists copy", StandardCharsets.UTF_8);
        Assumptions.assumeTrue(PathValidationUtils.isInAllowedTempDirectory(source.toFile()));

        try {
            Assumptions.assumeTrue(sourceDir.toFile().setWritable(false, false));
            Assumptions.assumeFalse(Files.isWritable(sourceDir));

            String result = fileManager.copyFileToOscarDocuments(source.toString());

            Path destination = documentDir.resolve(source.getFileName().toString());
            assertThat(result).isEqualTo(destination.toString());
            assertThat(Files.readString(destination)).isEqualTo("cleanup failure still persists copy");
            assertThat(Files.exists(source)).isTrue();
        } finally {
            sourceDir.toFile().setWritable(true, false);
            Files.deleteIfExists(source);
            Files.deleteIfExists(sourceDir);
        }
    }

    private static class TestableNioFileManagerImpl extends NioFileManagerImpl {
        private final Path documentDir;

        TestableNioFileManagerImpl(Path documentDir) {
            this.documentDir = documentDir;
        }

        @Override
        String getDocumentDirectory() {
            return documentDir.toString();
        }
    }
}
