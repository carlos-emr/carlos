package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("security")
@DisplayName("Email compose working directory")
class EmailComposeWorkingDirectoryUnitTest {
    @TempDir
    Path tempDirectory;

    @Test
    @DisplayName("owns generated PDFs and removes them idempotently")
    void shouldOwnAndRemoveGeneratedPdf() throws IOException {
        Path applicationRoot = tempDirectory.resolve("carlos-temp");
        Path generatedPdf = Files.writeString(tempDirectory.resolve("generated.pdf"), "patient data");
        EmailComposeWorkingDirectory workingDirectory = EmailComposeWorkingDirectory.create(applicationRoot);
        Path ownedDirectory = workingDirectory.path();
        try {
            Path ownedPdf = workingDirectory.adoptGeneratedPdf(generatedPdf);

            assertThat(EmailComposeWorkingDirectory.isActivelyOwned(ownedDirectory)).isTrue();
            assertThat(workingDirectory.owns(ownedPdf)).isTrue();
            assertThat(Files.readString(ownedPdf)).isEqualTo("patient data");
            workingDirectory.close();
            workingDirectory.close();

            assertThat(Files.exists(ownedDirectory)).isFalse();
            assertThat(EmailComposeWorkingDirectory.isActivelyOwned(ownedDirectory)).isFalse();
        } finally {
            workingDirectory.close();
        }
    }

    @Test
    @DisplayName("recognizes an active owner when the application temp root is symlinked")
    void shouldRecognizeActiveOwnerThroughSymlinkedRoot() throws IOException {
        Path applicationRoot = tempDirectory.resolve("carlos-temp");
        EmailComposeWorkingDirectory workingDirectory = EmailComposeWorkingDirectory.create(applicationRoot);
        Path rootAlias = tempDirectory.resolve("carlos-temp-alias");
        try {
            try {
                Files.createSymbolicLink(rootAlias, applicationRoot);
            } catch (IOException | UnsupportedOperationException e) {
                assumeTrue(false, "filesystem does not support symbolic links");
            }
            Path aliasedWorkingDirectory = rootAlias.resolve(workingDirectory.path().getFileName());

            assertThat(EmailComposeWorkingDirectory.isActivelyOwned(aliasedWorkingDirectory)).isTrue();
        } finally {
            workingDirectory.close();
        }
    }

    @Test
    @DisplayName("copies a durable source document without deleting it")
    void shouldNotDeleteDurableSourceDocument() throws IOException {
        Path applicationRoot = tempDirectory.resolve("carlos-temp");
        Path durableRoot = Files.createTempDirectory(
                Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
                "durable-email-source-");
        try {
            assumeTrue(
                    !PathValidationUtils.isInAllowedTempDirectory(durableRoot.toFile()),
                    "project working directory is configured as disposable temp storage");
            Path durablePdf = Files.writeString(durableRoot.resolve("source.pdf"), "source patient data");
            Path ownedPdf;
            try (EmailComposeWorkingDirectory workingDirectory =
                         EmailComposeWorkingDirectory.create(applicationRoot)) {
                ownedPdf = workingDirectory.adoptGeneratedPdf(durablePdf);
            }

            assertThat(Files.readString(durablePdf)).isEqualTo("source patient data");
            assertThat(Files.exists(ownedPdf)).isFalse();
        } finally {
            Files.deleteIfExists(durableRoot.resolve("source.pdf"));
            Files.deleteIfExists(durableRoot);
        }
    }

    @Test
    @DisplayName("deletes an internal symlink without following it")
    void shouldNotFollowSymlinkDuringCleanup() throws IOException {
        Path applicationRoot = tempDirectory.resolve("carlos-temp");
        Path externalFile = Files.writeString(tempDirectory.resolve("external.pdf"), "must remain");
        EmailComposeWorkingDirectory workingDirectory = EmailComposeWorkingDirectory.create(applicationRoot);
        try {
            Path internalSymlink;
            try {
                internalSymlink = Files.createSymbolicLink(
                        workingDirectory.path().resolve("link.pdf"),
                        externalFile);
            } catch (IOException | UnsupportedOperationException e) {
                assumeTrue(false, "filesystem does not support symbolic links");
                return;
            }
            assertThat(workingDirectory.owns(internalSymlink)).isFalse();
        } finally {
            workingDirectory.close();
        }

        assertThat(Files.readString(externalFile)).isEqualTo("must remain");
    }

    @Test
    @DisplayName("rejects symbolic-link input and unexpected file types")
    void shouldRejectUnsafeGeneratedArtifacts() throws IOException {
        Path applicationRoot = tempDirectory.resolve("carlos-temp");
        Path target = Files.writeString(tempDirectory.resolve("target.pdf"), "patient data");
        Path symlink = tempDirectory.resolve("link.pdf");
        try {
            Files.createSymbolicLink(symlink, target);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "filesystem does not support symbolic links");
        }
        Path textFile = Files.writeString(tempDirectory.resolve("generated.txt"), "patient data");

        try (EmailComposeWorkingDirectory workingDirectory =
                     EmailComposeWorkingDirectory.create(applicationRoot)) {
            assertThatThrownBy(() -> workingDirectory.adoptGeneratedPdf(symlink))
                    .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> workingDirectory.adoptGeneratedPdf(textFile))
                    .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> workingDirectory.adoptGeneratedPdf(applicationRoot.getRoot()))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Generated email attachment is not a PDF");
        }
    }

    @Test
    @DisplayName("rejects a symbolic-link application temp root")
    void shouldRejectSymbolicLinkApplicationTempRoot() throws IOException {
        Path externalDirectory = Files.createDirectory(tempDirectory.resolve("external-temp-root"));
        Path applicationRoot = tempDirectory.resolve("carlos-temp");
        try {
            Files.createSymbolicLink(applicationRoot, externalDirectory);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "filesystem does not support symbolic links");
        }

        assertThatThrownBy(() -> EmailComposeWorkingDirectory.create(applicationRoot))
                .isInstanceOf(IOException.class)
                .hasMessage("CARLOS application temp root is not a secure directory");
        try (var externalFiles = Files.list(externalDirectory)) {
            assertThat(externalFiles).isEmpty();
        }
    }

    @Test
    @DisplayName("refuses cleanup when the owned directory is replaced by a symlink")
    void shouldRefusePathEscape_whenDirectoryIsReplacedBySymlink() throws IOException {
        Path applicationRoot = tempDirectory.resolve("carlos-temp");
        Path externalDirectory = Files.createDirectory(tempDirectory.resolve("external-directory"));
        Path externalPdf = Files.writeString(externalDirectory.resolve("patient.pdf"), "must remain");
        EmailComposeWorkingDirectory workingDirectory = EmailComposeWorkingDirectory.create(applicationRoot);
        Path ownedDirectory = workingDirectory.path();
        try {
            Files.delete(ownedDirectory.resolve(EmailComposeWorkingDirectory.ACTIVE_LEASE_FILE_NAME));
            Files.delete(ownedDirectory);
            try {
                Files.createSymbolicLink(ownedDirectory, externalDirectory);
            } catch (IOException | UnsupportedOperationException e) {
                assumeTrue(false, "filesystem does not support symbolic links");
            }

            workingDirectory.close();
            assertThat(Files.readString(externalPdf)).isEqualTo("must remain");
            assertThat(EmailComposeWorkingDirectory.isActivelyOwned(ownedDirectory)).isFalse();
        } finally {
            workingDirectory.close();
            Files.deleteIfExists(ownedDirectory);
        }
    }
}
