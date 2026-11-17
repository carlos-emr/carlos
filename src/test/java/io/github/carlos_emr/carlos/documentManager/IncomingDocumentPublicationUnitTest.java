/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("unit")
@Tag("fast")
class IncomingDocumentPublicationUnitTest {
    @TempDir Path root;

    private Path source(String name, String content) throws IOException {
        return Files.writeString(root.resolve(name), content);
    }

    private void noStaging() throws IOException {
        try (var children = Files.list(root)) {
            assertThat(children.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".carlos-publication-"));
        }
    }

    @Test
    void publishesCompleteBytesAndPreservesPermissionsAndModificationTime() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path source = source("source.pdf", "complete document bytes");
        Path destination = root.resolve("stored.pdf");
        var permissions = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(source, permissions);
        FileTime modified = FileTime.fromMillis(1234567890000L);
        Files.setLastModifiedTime(source, modified);
        IncomingDocumentPublication.move(source.toFile(), destination.toFile());
        assertThat(source).doesNotExist();
        assertThat(Files.readString(destination)).isEqualTo("complete document bytes");
        assertThat(Files.getPosixFilePermissions(destination)).isEqualTo(permissions);
        assertThat(Files.getLastModifiedTime(destination)).isEqualTo(modified);
        noStaging();
    }

    @Test
    void preservesHistoricalDestinationAndSourceOnCollision() throws Exception {
        Path source = source("source.pdf", "new bytes");
        Path destination = source("stored.pdf", "historical bytes");
        assertThatThrownBy(() -> IncomingDocumentPublication.move(source.toFile(), destination.toFile()))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(source)).isEqualTo("new bytes");
        assertThat(Files.readString(destination)).isEqualTo("historical bytes");
        noStaging();
    }

    @Test
    void concurrentDistinctSourcesNeverOverwriteTheWinningPublication() throws Exception {
        Path first = source("first.pdf", "first document");
        Path second = source("second.pdf", "second document");
        Path destination = root.resolve("stored.pdf");
        CyclicBarrier ready = new CyclicBarrier(2);
        var operations = new IncomingDocumentPublication.FileOperations() {
            @Override void link(Path target, Path staged) throws IOException {
                try { ready.await(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IOException("Publication barrier failed", failure); }
                super.link(target, staged);
            }
        };
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> publish(first, destination, operations));
            var b = workers.submit(() -> publish(second, destination, operations));
            boolean firstWon = a.get(15, TimeUnit.SECONDS);
            boolean secondWon = b.get(15, TimeUnit.SECONDS);
            assertThat(firstWon).isNotEqualTo(secondWon);
            assertThat(Files.readString(destination)).isEqualTo(firstWon ? "first document" : "second document");
            assertThat(firstWon ? first : second).doesNotExist();
            assertThat(Files.readString(firstWon ? second : first)).isEqualTo(firstWon ? "second document" : "first document");
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        noStaging();
    }

    private boolean publish(Path source, Path destination, IncomingDocumentPublication.FileOperations operations) throws IOException {
        try { IncomingDocumentPublication.move(source, destination, operations); return true; }
        catch (FileAlreadyExistsException expected) { return false; }
    }

    @Test
    void failedCopyNeverPublishesPartialBytesOrRemovesSource() throws Exception {
        Path source = source("source.pdf", "complete document");
        Path destination = root.resolve("stored.pdf");
        var operations = new IncomingDocumentPublication.FileOperations() {
            @Override void copy(Path original, Path staged) throws IOException {
                Files.writeString(staged, "partial");
                throw new IOException("copy failed");
            }
        };
        assertThatThrownBy(() -> IncomingDocumentPublication.move(source, destination, operations)).isInstanceOf(IOException.class);
        assertThat(Files.readString(source)).isEqualTo("complete document");
        assertThat(destination).doesNotExist();
        noStaging();
    }

    @Test
    void failedSourceDeletionRollsBackOnlyOwnPublishedLink() throws Exception {
        Path source = source("source.pdf", "source bytes");
        Path destination = root.resolve("stored.pdf");
        var operations = new IncomingDocumentPublication.FileOperations() {
            @Override void deleteSource(Path original) throws IOException { throw new IOException("deletion denied"); }
        };
        assertThatThrownBy(() -> IncomingDocumentPublication.move(source, destination, operations)).isInstanceOf(IOException.class);
        assertThat(Files.readString(source)).isEqualTo("source bytes");
        assertThat(destination).doesNotExist();
        noStaging();
    }

    @Test
    void failedSourceDeletionNeverDeletesAReplacementDestination() throws Exception {
        Path source = source("source.pdf", "source bytes");
        Path destination = root.resolve("stored.pdf");
        var operations = new IncomingDocumentPublication.FileOperations() {
            @Override void deleteSource(Path original) throws IOException {
                Files.delete(destination);
                Files.writeString(destination, "another publisher's bytes");
                throw new IOException("deletion denied");
            }
        };
        assertThatThrownBy(() -> IncomingDocumentPublication.move(source, destination, operations)).isInstanceOf(IOException.class);
        assertThat(Files.readString(source)).isEqualTo("source bytes");
        assertThat(Files.readString(destination)).isEqualTo("another publisher's bytes");
        noStaging();
    }

    @Test
    void copyFromADifferentFilesystemProviderSucceedsWithoutCrossDeviceRename() throws Exception {
        try (var remote = FileSystems.newFileSystem(URI.create("jar:" + root.resolve("source.zip").toUri()), Map.of("create", "true"))) {
            Path source = Files.writeString(remote.getPath("/incoming.pdf"), "cross-filesystem document");
            Path destination = root.resolve("stored.pdf");
            IncomingDocumentPublication.move(source, destination, new IncomingDocumentPublication.FileOperations());
            assertThat(source).doesNotExist();
            assertThat(Files.readString(destination)).isEqualTo("cross-filesystem document");
        }
        noStaging();
    }

    @Test
    void unsupportedDestinationHardLinksPreserveSourceWithoutPartialPublication() throws Exception {
        Path source = source("source.pdf", "source bytes");
        try (var unsupported = FileSystems.newFileSystem(URI.create("jar:" + root.resolve("destination.zip").toUri()), Map.of("create", "true"))) {
            Path destination = unsupported.getPath("/stored.pdf");
            assertThatThrownBy(() -> IncomingDocumentPublication.move(source, destination, new IncomingDocumentPublication.FileOperations()))
                    .isInstanceOf(IOException.class);
            assertThat(Files.readString(source)).isEqualTo("source bytes");
            assertThat(destination).doesNotExist();
            try (var entries = Files.list(unsupported.getPath("/"))) { assertThat(entries).isEmpty(); }
        }
        noStaging();
    }

    @Test
    void interruptionAfterCopyPreservesSourceAndDoesNotPublish() throws Exception {
        Path source = source("source.pdf", "source bytes");
        Path destination = root.resolve("stored.pdf");
        var operations = new IncomingDocumentPublication.FileOperations() {
            @Override void copy(Path original, Path staged) throws IOException {
                super.copy(original, staged);
                Thread.currentThread().interrupt();
            }
        };
        try {
            assertThatThrownBy(() -> IncomingDocumentPublication.move(source, destination, operations)).isInstanceOf(IOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(Files.readString(source)).isEqualTo("source bytes");
        assertThat(destination).doesNotExist();
        noStaging();
    }

    @Test
    void interruptionAfterPublicationRollsBackBeforeSourceRemoval() throws Exception {
        Path source = source("source.pdf", "source bytes");
        Path destination = root.resolve("stored.pdf");
        var operations = new IncomingDocumentPublication.FileOperations() {
            @Override void link(Path target, Path staged) throws IOException {
                super.link(target, staged);
                Thread.currentThread().interrupt();
            }
        };
        try {
            assertThatThrownBy(() -> IncomingDocumentPublication.move(source, destination, operations)).isInstanceOf(IOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(Files.readString(source)).isEqualTo("source bytes");
        assertThat(destination).doesNotExist();
        noStaging();
    }
}
