/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL version 2 or later.
 */
package io.github.carlos_emr.carlos.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import io.github.carlos_emr.CarlosProperties;

/**
 * The writer and three reader pages all resolve this configuration through this
 * class, so its precedence rules are pinned here rather than only through callers.
 */
@DisplayName("Antenatal configuration location")
@Tag("unit")
@Tag("decision")
class AntenatalConfigLocationUnitTest {

    private static final String FILE_NAME = AntenatalConfigLocation.RISK_FILE_NAME;

    @TempDir
    Path documentDirectory;

    private MockedStatic<CarlosProperties> properties;
    private CarlosProperties configuration;

    @BeforeEach
    void setUp() {
        configuration = mock(CarlosProperties.class);
        properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(configuration);
    }

    @AfterEach
    void tearDown() {
        properties.close();
    }

    @Test
    @DisplayName("should resolve the configured path as a directory and filename pair")
    void shouldResolvePath_forConfiguredDirectory() throws Exception {
        // No trailing separator: string concatenation produced a sibling path here,
        // which is how the writer and the readers came to disagree.
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());

        assertThat(AntenatalConfigLocation.configuredPath(FILE_NAME))
                .isEqualTo(documentDirectory.resolve(FILE_NAME));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("should report a storage failure when the document directory is unset")
    void shouldFail_forUnsetDocumentDirectory(String documentDir) {
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(documentDir);

        assertThatThrownBy(() -> AntenatalConfigLocation.configuredPath(FILE_NAME))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("DOCUMENT_DIR");
    }

    @Test
    @DisplayName("should report a storage failure when the document directory is not a usable path")
    void shouldFail_forUnusableDocumentDirectory() {
        // Build the NUL at runtime so this Java source remains a normal text file
        // and code-review tools can display its diff.
        String invalidDocumentDirectory = invalidDocumentDirectory();
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(invalidDocumentDirectory);

        assertThatThrownBy(() -> AntenatalConfigLocation.configuredPath(FILE_NAME))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("DOCUMENT_DIR");
    }

    @Test
    @DisplayName("should report no readable override for an unusable document directory")
    void shouldReturnNull_forUnusableDocumentDirectory() {
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(invalidDocumentDirectory());

        assertThat(AntenatalConfigLocation.readableOverride(FILE_NAME)).isNull();
    }

    @Test
    @DisplayName("should return the override when it is a readable regular file")
    void shouldReturnOverride_forReadableFile() throws Exception {
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        Files.writeString(documentDirectory.resolve(FILE_NAME), "<riskFactors/>");

        assertThat(AntenatalConfigLocation.readableOverride(FILE_NAME))
                .isNotNull()
                .isFile();
    }

    @Test
    @DisplayName("should keep a readable symbolic-link override available to readers")
    void shouldReturnOverride_forReadableSymbolicLink() throws Exception {
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        Path linkedConfiguration = documentDirectory.resolve("operator-managed-risks.xml");
        Files.writeString(linkedConfiguration, "<riskFactors/>");
        Path override = documentDirectory.resolve(FILE_NAME);
        try {
            Files.createSymbolicLink(override, linkedConfiguration.getFileName());
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.jupiter.api.Assumptions.abort(
                    "filesystem or platform does not support symbolic links");
        }

        File readableOverride = AntenatalConfigLocation.readableOverride(FILE_NAME);

        assertThat(readableOverride).isNotNull();
        assertThat(Files.isSymbolicLink(readableOverride.toPath())).isTrue();
        assertThat(Files.readString(readableOverride.toPath())).isEqualTo("<riskFactors/>");
    }

    @Test
    @DisplayName("should report no readable override for a dangling symbolic link")
    void shouldReturnNull_forDanglingSymbolicLink() throws Exception {
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        Path override = documentDirectory.resolve(FILE_NAME);
        try {
            Files.createSymbolicLink(override, Path.of("missing-risks.xml"));
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.jupiter.api.Assumptions.abort(
                    "filesystem or platform does not support symbolic links");
        }

        assertThat(AntenatalConfigLocation.readableOverride(FILE_NAME)).isNull();
        assertThat(Files.isSymbolicLink(AntenatalConfigLocation.configuredPath(FILE_NAME))).isTrue();
    }

    @Test
    @DisplayName("should ignore a directory sitting at the override path")
    void shouldIgnoreOverride_forDirectoryAtPath() throws Exception {
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        // A readable directory passes canRead(); feeding it to the XML parser
        // previously produced a null risk list and a NullPointerException downstream.
        Files.createDirectory(documentDirectory.resolve(FILE_NAME));

        assertThat(AntenatalConfigLocation.readableOverride(FILE_NAME)).isNull();
    }

    @Test
    @DisplayName("should report no override when the file is absent")
    void shouldReturnNull_forMissingOverride() {
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());

        assertThat(AntenatalConfigLocation.readableOverride(FILE_NAME)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("should report no override when the document directory is unset")
    void shouldReturnNull_forUnsetDocumentDirectory(String documentDir) {
        // Otherwise new File(null, name) resolves the bare filename against the JVM
        // working directory, letting an unrelated file be loaded as configuration.
        when(configuration.getProperty("DOCUMENT_DIR")).thenReturn(documentDir);

        File override = AntenatalConfigLocation.readableOverride(FILE_NAME);

        assertThat(override).isNull();
    }

    private static String invalidDocumentDirectory() {
        return "/var/lib" + Character.toString(0) + "/documents";
    }
}
