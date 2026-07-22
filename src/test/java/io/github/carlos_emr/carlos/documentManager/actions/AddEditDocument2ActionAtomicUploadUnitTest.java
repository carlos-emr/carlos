/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for atomic local upload publication in {@link AddEditDocument2Action}.
 */
@DisplayName("AddEditDocument2Action Atomic Upload Unit Tests")
@Tag("unit")
@Tag("documentManager")
class AddEditDocument2ActionAtomicUploadUnitTest extends CarlosUnitTestBase {

    @TempDir
    private Path tempDocumentDir;

    private String originalDocumentDir;

    @BeforeEach
    void setUp() {
        originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDocumentDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (originalDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
        }
    }

    @Test
    @DisplayName("should write uploaded file via temp sibling and clean it up")
    void shouldWriteUploadedFileViaTempSibling_whenPublishingCompletes() throws Exception {
        String fileName = "uploaded.pdf";
        Path targetPath = tempDocumentDir.resolve(fileName).toAbsolutePath().normalize();
        byte[] payload = "new pdf payload".getBytes(StandardCharsets.UTF_8);

        try (InputStream input = new ByteArrayInputStream(payload)) {
            File writtenFile = AddEditDocument2Action.writeLocalFile(input, fileName);

            assertThat(writtenFile.toPath()).isEqualTo(targetPath);
            assertThat(Files.readAllBytes(targetPath)).isEqualTo(payload);
            assertDirectoryContainsOnly(tempDocumentDir, fileName);
        }
    }

    @Test
    @DisplayName("should replace existing file only after full temp write completes")
    void shouldReplaceExistingFile_onlyAfterFullTempWriteCompletes() throws Exception {
        Path targetPath = tempDocumentDir.resolve("replace.pdf").toAbsolutePath().normalize();
        Files.writeString(targetPath, "old-content", StandardCharsets.UTF_8);
        byte[] replacement = "replacement payload that is longer".getBytes(StandardCharsets.UTF_8);

        try (InputStream input = new ByteArrayInputStream(replacement)) {
            AddEditDocument2Action.writeLocalFile(input, "replace.pdf");
        }

        assertThat(Files.readAllBytes(targetPath)).isEqualTo(replacement);
        assertDirectoryContainsOnly(tempDocumentDir, "replace.pdf");
    }

    @Test
    @DisplayName("should preserve existing file and cleanup temp when staging fails")
    void shouldPreserveExistingFile_whenStagingFails() throws Exception {
        Path targetPath = tempDocumentDir.resolve("existing.pdf").toAbsolutePath().normalize();
        Files.writeString(targetPath, "original", StandardCharsets.UTF_8);

        try (InputStream input = new FailingUploadInputStream()) {
            assertThatThrownBy(() -> AddEditDocument2Action.writeLocalFile(input, "existing.pdf"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("simulated write failure");
        }

        assertThat(Files.readString(targetPath, StandardCharsets.UTF_8)).isEqualTo("original");
        assertDirectoryContainsOnly(tempDocumentDir, "existing.pdf");
    }

    @Test
    @DisplayName("should not expose new destination while staging upload")
    void shouldNotExposeNewDestination_whenStagingUpload() throws Exception {
        String fileName = "new-upload.pdf";
        Path targetPath = tempDocumentDir.resolve(fileName).toAbsolutePath().normalize();

        try (InputStream input = new DestinationCheckingInputStream(targetPath)) {
            File writtenFile = AddEditDocument2Action.writeLocalFile(input, fileName, false);

            assertThat(writtenFile.toPath()).isEqualTo(targetPath);
            assertThat(Files.readString(targetPath, StandardCharsets.UTF_8)).isEqualTo("payload");
            assertDirectoryContainsOnly(tempDocumentDir, fileName);
        }
    }

    private static void assertDirectoryContainsOnly(Path directory, String fileName) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            List<String> fileNames = paths.map(path -> path.getFileName().toString()).toList();
            assertThat(fileNames).containsOnly(fileName);
            assertThat(fileNames).allMatch(name -> !name.endsWith(".tmp"));
        }
    }

    private static final class FailingUploadInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            throw new IOException("simulated write failure");
        }
    }

    private static final class DestinationCheckingInputStream extends InputStream {

        private final Path destinationPath;
        private final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        private int offset;

        private DestinationCheckingInputStream(Path destinationPath) {
            this.destinationPath = destinationPath;
        }

        @Override
        public int read() throws IOException {
            assertThat(destinationPath).doesNotExist();
            if (offset >= payload.length) {
                return -1;
            }
            return payload[offset++];
        }
    }
}
