/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the devcontainer helper that provisions eForm paths
 * from Carlos properties during image build.
 */
@DisplayName("eForm devcontainer property directory bootstrap")
@Tag("unit")
@Tag("eform")
class EFormDevcontainerBootstrapScriptTest {

    private static final Path SCRIPT = Path.of(".devcontainer/development/scripts/bootstrap-property-directories");

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("should create the last duplicate property value to match Java Properties semantics")
    void shouldCreateLastDuplicatePropertyValue_whenPropertyIsRepeated() throws Exception {
        Path first = tempDir.resolve("first");
        Path second = tempDir.resolve("second");
        Path properties = writeProperties("""
            EFORM_IMAGES_DIR=%s
            # Later overrides should win, matching java.util.Properties.load().
            EFORM_IMAGES_DIR=%s
            """.formatted(first, second));

        CommandResult result = runBootstrap(properties, "EFORM_IMAGES_DIR");

        assertThat(result.exitCode()).isZero();
        assertThat(first).doesNotExist();
        assertThat(second).isDirectory();
        assertThat(result.stdout()).contains("Creating EFORM_IMAGES_DIR directory: " + second);
    }

    @Test
    @DisplayName("should create each requested absolute property directory")
    void shouldCreateDirectory_forEveryRequestedProperty() throws Exception {
        Path images = tempDir.resolve("images");
        Path documents = tempDir.resolve("documents");
        Path properties = writeProperties("""
            EFORM_IMAGES_DIR=%s
            DOCUMENT_DIR=%s
            """.formatted(images, documents));

        CommandResult result = runBootstrap(properties, "EFORM_IMAGES_DIR", "DOCUMENT_DIR");

        assertThat(result.exitCode()).isZero();
        assertThat(images).isDirectory();
        assertThat(documents).isDirectory();
    }

    @Test
    @DisplayName("should reject relative property paths")
    void shouldReject_whenPropertyPathIsRelative() throws Exception {
        Path properties = writeProperties("EFORM_IMAGES_DIR=relative/eform/images\n");

        CommandResult result = runBootstrap(properties, "EFORM_IMAGES_DIR");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("Property EFORM_IMAGES_DIR must be an absolute path");
        assertThat(tempDir.resolve("relative")).doesNotExist();
    }

    @Test
    @DisplayName("should fail when a requested property is absent")
    void shouldFail_whenRequestedPropertyIsAbsent() throws Exception {
        Path properties = writeProperties("DOCUMENT_DIR=" + tempDir.resolve("documents") + "\n");

        CommandResult result = runBootstrap(properties, "EFORM_IMAGES_DIR");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("Property EFORM_IMAGES_DIR is not set");
    }

    @Test
    @DisplayName("should assemble backslash-continued property values to match Java Properties semantics")
    void shouldAssembleValue_whenPropertyHasBackslashContinuation() throws Exception {
        // Java Properties allows splitting long values across lines with a trailing backslash.
        // The leading whitespace on the continuation line is stripped before joining.
        // carlos.properties documents this format in its own header comments.
        Path target = tempDir.resolve("eform").resolve("images");
        Path properties = writeProperties(
            "EFORM_IMAGES_DIR=" + tempDir.toAbsolutePath() + "/\\\n" +
            "    eform/images\n");

        CommandResult result = runBootstrap(properties, "EFORM_IMAGES_DIR");

        assertThat(result.exitCode()).isZero();
        assertThat(target).isDirectory();
    }

    private Path writeProperties(String content) throws IOException {
        Path properties = tempDir.resolve("carlos.properties");
        Files.writeString(properties, content, StandardCharsets.UTF_8);
        return properties;
    }

    private CommandResult runBootstrap(Path properties, String... propertyNames) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("sh");
        command.add(SCRIPT.toString());
        command.add(properties.toString());
        command.addAll(List.of(propertyNames));

        Process process = new ProcessBuilder(command)
            .directory(Path.of(".").toFile())
            .start();
        byte[] stdout = process.getInputStream().readAllBytes();
        byte[] stderr = process.getErrorStream().readAllBytes();
        int exitCode = process.waitFor();
        return new CommandResult(
            exitCode,
            new String(stdout, StandardCharsets.UTF_8),
            new String(stderr, StandardCharsets.UTF_8));
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
