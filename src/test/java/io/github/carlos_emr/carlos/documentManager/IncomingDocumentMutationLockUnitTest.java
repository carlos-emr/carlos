/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fast")
class IncomingDocumentMutationLockUnitTest {
    @TempDir Path root;
    private File queue;
    private String previousRoot;
    private String previousRecycle;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);

    @BeforeEach
    void configure() throws Exception {
        previousRoot = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        previousRecycle = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_RECYCLEBIN");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", root.toString());
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_RECYCLEBIN", "false");
        queue = Files.createDirectories(root.resolve("1/Fax")).toFile();
    }

    @AfterEach
    void cleanup() throws Exception {
        workers.shutdownNow();
        assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        restore("INCOMINGDOCUMENT_DIR", previousRoot);
        restore("INCOMINGDOCUMENT_RECYCLEBIN", previousRecycle);
        assertThat(IncomingDocumentMutationLock.registeredSources()).isZero();
    }

    private void restore(String name, String value) {
        if (value == null) CarlosProperties.getInstance().remove(name);
        else CarlosProperties.getInstance().setProperty(name, value);
    }

    private File pdf(String name) throws Exception {
        File file = new File(queue, name);
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < 3; i++) document.addPage(new PDPage());
            document.save(file);
        }
        return file;
    }

    private void awaitWaiters(File file, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (IncomingDocumentMutationLock.queuedWaiters(file) < count) {
            if (System.nanoTime() > deadline) throw new AssertionError("Mutation waiter never queued");
            Thread.yield();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"rotate", "rotateAll", "deletePage", "extract", "deletePdf"})
    void editWaitingBehindFilingCannotRecreateTheRemovedQueueSource(String edit) throws Exception {
        File source = pdf("source.pdf");
        byte[] original = Files.readAllBytes(source.toPath());
        Path filed = root.resolve("filed.pdf");
        java.util.concurrent.Future<?> editor;
        try (var filing = IncomingDocumentMutationLock.acquire(source, queue)) {
            editor = workers.submit(() -> {
                switch (edit) {
                    case "rotate" -> IncomingDocUtil.rotatePage("1", "Fax", "source.pdf", "1", 90);
                    case "rotateAll" -> IncomingDocUtil.rotateAlPages("1", "Fax", "source.pdf", 90);
                    case "deletePage" -> IncomingDocUtil.deletePage("1", "Fax", "source.pdf", "1");
                    case "extract" -> IncomingDocUtil.extractPage("1", "Fax", "source.pdf", "1");
                    case "deletePdf" -> IncomingDocUtil.DeletePDF("1", "Fax", "source.pdf");
                    default -> throw new AssertionError(edit);
                }
                return null;
            });
            awaitWaiters(source, 1);
            assertThat(editor.isDone()).isFalse();
            Files.move(filing.source().toPath(), filed);
        }
        assertThatThrownBy(() -> editor.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(NoSuchFileException.class);
        assertThat(source).doesNotExist();
        assertThat(Files.readAllBytes(filed)).isEqualTo(original);
        assertThat(new File(queue, "sourceE3.pdf")).doesNotExist();
        try (var files = Files.list(queue.toPath())) { assertThat(files.toList()).isEmpty(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"filing", "delete", "recycle"})
    void queuedWaiterCannotClaimAReplacementAtTheSamePathAfterTerminalRemoval(String terminal) throws Exception {
        File source = pdf("reused.pdf");
        byte[] original = Files.readAllBytes(source.toPath());
        byte[] replacement = "new patient's document at the same queue path".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.util.concurrent.Future<?> stale;
        java.util.concurrent.Future<byte[]> fresh;
        try (var owner = IncomingDocumentMutationLock.acquire(source, queue)) {
            stale = workers.submit(() -> {
                try (var lease = IncomingDocumentMutationLock.acquire(source, queue)) {
                    Files.writeString(lease.source().toPath(), "stale request must never write this");
                }
                return null;
            });
            awaitWaiters(source, 1);
            if ("filing".equals(terminal)) {
                IncomingDocumentPublication.move(owner.source(), root.resolve("filed.pdf").toFile());
                owner.sourceRemoved();
            } else {
                CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_RECYCLEBIN", String.valueOf("recycle".equals(terminal)));
                IncomingDocUtil.DeletePDF("1", "Fax", "reused.pdf");
            }
            assertThat(source).doesNotExist();
            Files.write(source.toPath(), replacement);
            // A request begun after removal deliberately addresses the new generation,
            // even while the old-generation waiter still holds a registry reference.
            fresh = workers.submit(() -> {
                try (var lease = IncomingDocumentMutationLock.acquire(source, queue)) {
                    return Files.readAllBytes(lease.source().toPath());
                }
            });
            awaitWaiters(source, 2);
        }
        assertThatThrownBy(() -> stale.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(NoSuchFileException.class);
        assertThat(fresh.get(10, TimeUnit.SECONDS)).isEqualTo(replacement);
        assertThat(Files.readAllBytes(source.toPath())).isEqualTo(replacement);
        if ("filing".equals(terminal)) assertThat(Files.readAllBytes(root.resolve("filed.pdf"))).isEqualTo(original);
        if ("recycle".equals(terminal)) assertThat(Files.readAllBytes(root.resolve("1/Fax_deleted/reused.pdf"))).isEqualTo(original);
    }

    @Test
    void queuedEditStillAppliesAfterAnOrdinaryPageReplacement() throws Exception {
        File source = pdf("edited.pdf");
        java.util.concurrent.Future<?> waiting;
        try (var owner = IncomingDocumentMutationLock.acquire(source, queue)) {
            waiting = workers.submit(() -> { IncomingDocUtil.rotatePage("1", "Fax", "edited.pdf", "2", 180); return null; });
            awaitWaiters(source, 1);
            IncomingDocUtil.rotatePage("1", "Fax", "edited.pdf", "1", 90);
        }
        waiting.get(10, TimeUnit.SECONDS);
        try (PDDocument document = Loader.loadPDF(source)) {
            assertThat(document.getPage(0).getRotation()).isEqualTo(90);
            assertThat(document.getPage(1).getRotation()).isEqualTo(180);
        }
    }

    @Test
    void sourceRemovalCannotBeMarkedByAnotherThreadOrAfterClose() throws Exception {
        File source = pdf("owned.pdf");
        var owner = IncomingDocumentMutationLock.acquire(source, queue);
        try {
            var wrongThread = workers.submit(() -> {
                assertThatThrownBy(owner::sourceRemoved).isInstanceOf(IllegalStateException.class);
                return null;
            });
            wrongThread.get(10, TimeUnit.SECONDS);
        } finally { owner.close(); }
        assertThatThrownBy(owner::sourceRemoved).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void filingWaitsForAnEditorAndMovesItsCompleteUpdatedPdfExactlyOnce() throws Exception {
        File source = pdf("source.pdf");
        Path filed = root.resolve("filed.pdf");
        CountDownLatch edited = new CountDownLatch(1), releaseEditor = new CountDownLatch(1);
        var editor = workers.submit(() -> {
            try (var lease = IncomingDocumentMutationLock.acquire(source, queue)) {
                IncomingDocUtil.rotatePage("1", "Fax", "source.pdf", "1", 90);
                edited.countDown();
                if (!releaseEditor.await(10, TimeUnit.SECONDS)) throw new AssertionError("Editor handoff timed out");
            }
            return null;
        });
        assertThat(edited.await(10, TimeUnit.SECONDS)).isTrue();
        var filer = workers.submit(() -> {
            try (var lease = IncomingDocumentMutationLock.acquire(source, queue)) {
                Files.move(lease.source().toPath(), filed);
            }
            return null;
        });
        try {
            awaitWaiters(source, 1);
            assertThat(filed).doesNotExist();
        } finally { releaseEditor.countDown(); }
        editor.get(10, TimeUnit.SECONDS);
        filer.get(10, TimeUnit.SECONDS);
        assertThat(source).doesNotExist();
        try (PDDocument document = Loader.loadPDF(filed.toFile())) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
            assertThat(document.getPage(0).getRotation()).isEqualTo(90);
        }
        assertThatThrownBy(() -> IncomingDocumentMutationLock.acquire(source, queue)).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void unrelatedQueueFilesDoNotWaitOnOneAnother() throws Exception {
        File busy = pdf("busy.pdf"), other = pdf("other.pdf");
        try (var lease = IncomingDocumentMutationLock.acquire(busy, queue)) {
            var edit = workers.submit(() -> { IncomingDocUtil.rotatePage("1", "Fax", "other.pdf", "1", 90); return null; });
            edit.get(10, TimeUnit.SECONDS);
        }
        try (PDDocument document = Loader.loadPDF(other)) { assertThat(document.getPage(0).getRotation()).isEqualTo(90); }
    }

    @Test
    void waitingSessionsAcquireInFifoOrderAndRegistryRetires() throws Exception {
        File source = pdf("fair.pdf");
        List<Integer> order = new CopyOnWriteArrayList<>();
        var owner = IncomingDocumentMutationLock.acquire(source, queue);
        java.util.concurrent.Future<?> first;
        java.util.concurrent.Future<?> second;
        try {
            first = workers.submit(() -> { try (var lease = IncomingDocumentMutationLock.acquire(source, queue)) { order.add(1); } return null; });
            awaitWaiters(source, 1);
            second = workers.submit(() -> { try (var lease = IncomingDocumentMutationLock.acquire(source, queue)) { order.add(2); } return null; });
            awaitWaiters(source, 2);
        } finally { owner.close(); }
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        assertThat(order).containsExactly(1, 2);
        assertThat(IncomingDocumentMutationLock.registeredSources()).isZero();
    }

    @Test
    void interruptedWaiterPreservesBytesAndInterruptWithoutLeakingAnEntry() throws Exception {
        File source = pdf("interrupt.pdf");
        byte[] original = Files.readAllBytes(source.toPath());
        AtomicBoolean observed = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try (var ignored = IncomingDocumentMutationLock.acquire(source, queue)) {
                throw new AssertionError("Interrupted waiter acquired a mutation lease");
            } catch (IOException expected) { observed.set(Thread.currentThread().isInterrupted()); }
        });
        try (var owner = IncomingDocumentMutationLock.acquire(source, queue)) {
            waiter.start();
            awaitWaiters(source, 1);
            waiter.interrupt();
            waiter.join(10000);
            assertThat(waiter.isAlive()).isFalse();
            assertThat(observed).isTrue();
            assertThat(IncomingDocumentMutationLock.registeredSources()).isEqualTo(1);
        } finally { waiter.interrupt(); waiter.join(10000); }
        assertThat(Files.readAllBytes(source.toPath())).isEqualTo(original);
    }

    @Test
    void admissionTimeoutAcceptsNoWorkAndPreservesSource() throws Exception {
        File source = pdf("timeout.pdf");
        byte[] original = Files.readAllBytes(source.toPath());
        try (var owner = IncomingDocumentMutationLock.acquire(source, queue)) {
            var refused = workers.submit(() -> {
                assertThatThrownBy(() -> IncomingDocumentMutationLock.acquire(source, queue, true, 10))
                        .isInstanceOf(BoundedPdfTask.BusyException.class);
                return null;
            });
            refused.get(10, TimeUnit.SECONDS);
            assertThat(IncomingDocumentMutationLock.registeredSources()).isEqualTo(1);
        }
        assertThat(Files.readAllBytes(source.toPath())).isEqualTo(original);
    }

    @Test
    void destinationReservationWaitsUntilCompletedBytesArePublished() throws Exception {
        File destination = new File(queue, "extracted.pdf");
        File complete = pdf("private-source.pdf");
        byte[] expected = Files.readAllBytes(complete.toPath());
        java.util.concurrent.Future<byte[]> reader;
        try (var publishing = IncomingDocumentMutationLock.reserveNewFile(destination, queue)) {
            reader = workers.submit(() -> {
                try (var lease = IncomingDocumentMutationLock.acquire(destination, queue)) {
                    return Files.readAllBytes(lease.source().toPath());
                }
            });
            awaitWaiters(destination, 1);
            assertThat(destination).doesNotExist();
            Files.createLink(destination.toPath(), complete.toPath());
            assertThat(reader.isDone()).isFalse();
        }
        assertThat(reader.get(10, TimeUnit.SECONDS)).isEqualTo(expected);
    }

    @Test
    void extractionWaitingForItsDestinationDoesNotOverwriteAConcurrentPublication() throws Exception {
        File source = pdf("source.pdf");
        File destination = new File(queue, "sourceE3.pdf");
        byte[] original = Files.readAllBytes(source.toPath());
        byte[] otherDocument = "another session's completed queue document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.util.concurrent.Future<?> extraction;
        try (var publisher = IncomingDocumentMutationLock.reserveNewFile(destination, queue)) {
            extraction = workers.submit(() -> { IncomingDocUtil.extractPage("1", "Fax", "source.pdf", "1"); return null; });
            awaitWaiters(destination, 1);
            Files.write(destination.toPath(), otherDocument);
        }
        assertThatThrownBy(() -> extraction.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasRootCauseInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(Files.readAllBytes(source.toPath())).isEqualTo(original);
        assertThat(Files.readAllBytes(destination.toPath())).isEqualTo(otherDocument);
        try (var files = Files.list(queue.toPath())) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("source.pdf", "sourceE3.pdf");
        }
    }

    @Test
    void refusingExistingDestinationOrEscapingPathLeavesOriginalBytesAndRegistryIntact() throws Exception {
        File existing = pdf("existing.pdf");
        byte[] original = Files.readAllBytes(existing.toPath());
        assertThatThrownBy(() -> IncomingDocumentMutationLock.reserveNewFile(existing, queue))
                .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThatThrownBy(() -> IncomingDocumentMutationLock.acquire(root.resolve("outside.pdf").toFile(), queue))
                .isInstanceOf(SecurityException.class);
        assertThat(Files.readAllBytes(existing.toPath())).isEqualTo(original);
        assertThat(IncomingDocumentMutationLock.registeredSources()).isZero();
    }
}
