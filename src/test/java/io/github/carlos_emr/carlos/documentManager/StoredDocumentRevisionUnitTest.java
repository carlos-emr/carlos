/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class StoredDocumentRevisionUnitTest {
    @TempDir Path directory;

    @Test void storedChildPreservesContainedNestedAndNormalizedPaths() throws Exception {
        Path root = Files.createDirectory(directory.resolve("documents"));
        Path nested = Files.createDirectory(root.resolve("historical"));
        Path source = Files.writeString(nested.resolve("scan with spaces.pdf"), "owned document");
        assertThat(StoredDocumentRevision.resolveStoredChild(root.toFile(), "historical/scan with spaces.pdf").toPath())
                .isEqualTo(source);
        assertThat(StoredDocumentRevision.resolveStoredChild(root.toFile(), "historical/../historical/scan with spaces.pdf").getCanonicalFile())
                .isEqualTo(source.toFile().getCanonicalFile());
        Files.createSymbolicLink(root.resolve("contained-alias"), nested);
        assertThat(StoredDocumentRevision.resolveStoredChild(root.toFile(), "contained-alias/scan with spaces.pdf").getCanonicalFile())
                .isEqualTo(source.toFile().getCanonicalFile());
    }

    @Test void storedChildRejectsParentTraversalAndSiblingPrefixCollision() throws Exception {
        Path root = Files.createDirectory(directory.resolve("documents"));
        Path sibling = Files.createDirectory(directory.resolve("documents-other"));
        Path privateFile = Files.writeString(sibling.resolve("private.pdf"), "another document store");
        assertThatThrownBy(() -> StoredDocumentRevision.resolveStoredChild(root.toFile(), "../documents-other/private.pdf"))
                .isInstanceOf(SecurityException.class);
        assertThat(Files.readString(privateFile)).isEqualTo("another document store");
    }

    @Test void storedChildRejectsEscapingDirectoryAndLeafSymlinks() throws Exception {
        Path root = Files.createDirectory(directory.resolve("documents"));
        Path outside = Files.createDirectory(directory.resolve("private"));
        Path privateFile = Files.writeString(outside.resolve("private.pdf"), "private bytes");
        Files.createSymbolicLink(root.resolve("linked-directory"), outside);
        Files.createSymbolicLink(root.resolve("linked.pdf"), privateFile);
        assertThatThrownBy(() -> StoredDocumentRevision.resolveStoredChild(root.toFile(), "linked-directory/private.pdf"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> StoredDocumentRevision.resolveStoredChild(root.toFile(), "linked.pdf"))
                .isInstanceOf(SecurityException.class);
        assertThat(Files.readString(privateFile)).isEqualTo("private bytes");
    }

    @Test void contentHashDetectsEqualLengthReplacementEvenWhenModificationTimeIsPreserved() throws Exception {
        Path source = directory.resolve("source.pdf");
        Files.writeString(source, "page-A,page-B,page-C");
        var time = Files.getLastModifiedTime(source);
        String observed = StoredDocumentRevision.sha256(source);
        assertThat(observed).matches("[0-9a-f]{64}");
        Files.writeString(source, "page-B,page-C,page-D");
        Files.setLastModifiedTime(source, time);
        assertThatThrownBy(() -> StoredDocumentRevision.requireMatch(source, observed))
                .isInstanceOf(StoredDocumentRevision.ConflictException.class);
    }

    @Test void unchangedSourceMatchesAndMissingOrNonregularSourcesNeverGetARevision() throws Exception {
        Path source = directory.resolve("source.pdf");
        Files.writeString(source, "abc");
        assertThat(StoredDocumentRevision.sha256(source))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        StoredDocumentRevision.requireMatch(source, StoredDocumentRevision.sha256(source));
        assertThatThrownBy(() -> StoredDocumentRevision.sha256(directory)).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> StoredDocumentRevision.sha256(directory.resolve("missing.pdf"))).isInstanceOf(java.io.IOException.class);
    }

    @Test void anOlderIntentWaitingBehindAnEditConflictsAfterItObtainsTheFairLease() throws Exception {
        Path source = directory.resolve("source.pdf");
        Files.writeString(source, "original selected pages");
        String observed = StoredDocumentRevision.sha256(source);
        CountDownLatch attempting = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<Boolean> waiting;
            try (var first = IncomingDocumentMutationLock.acquire(source.toFile(), directory.toFile())) {
                waiting = worker.submit(() -> {
                    attempting.countDown();
                    try (var second = IncomingDocumentMutationLock.acquire(source.toFile(), directory.toFile())) {
                        StoredDocumentRevision.requireMatch(second.source().toPath(), observed);
                        return false;
                    } catch (StoredDocumentRevision.ConflictException expected) { return true; }
                });
                assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (IncomingDocumentMutationLock.queuedWaiters(source.toFile()) != 1 && System.nanoTime() < deadline) {
                    java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                }
                assertThat(IncomingDocumentMutationLock.queuedWaiters(source.toFile())).isEqualTo(1);
                Files.writeString(source, "another session changed pages");
            }
            assertThat(waiting.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(Files.readString(source)).isEqualTo("another session changed pages");
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
