/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.oscarLab.ca.all.upload.handlers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.DefaultHandler;

@Tag("unit")
@Tag("lab")
@DisplayName("DefaultHandler unit tests")
class DefaultHandlerUnitTest {

    @Test
    @DisplayName("should not expose rejected file path in readTextFile errors")
    void shouldNotExposeRejectedFilePathInReadTextFileErrors() throws Exception {
        Path documentDir = Files.createTempDirectory("default-handler-document-dir");
        Path uploadFile = Files.createTempFile("default-handler-upload", ".txt");
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

            DefaultHandler handler = new DefaultHandler();

            assertThatThrownBy(() -> handler.readTextFile(uploadFile.toString()))
                    .isInstanceOf(IOException.class)
                    .hasMessageNotContaining(uploadFile.toString());
        } finally {
            if (previousDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
            }
            Files.deleteIfExists(uploadFile);
            Files.deleteIfExists(documentDir);
        }
    }
}
