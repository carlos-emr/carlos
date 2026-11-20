/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@Isolated("Uses the shared PDF worker admission pool to verify source-lease ordering")
class StoredDocumentRenderLeaseUnitTest {
    @Test void rendererWorkersReleaseImmediatelyWhileEditorOwnsSourceAndWaitsForPdfWorker(@TempDir Path directory) throws Exception {
        Path source = Files.writeString(directory.resolve("source.pdf"), "owned source");
        int capacity = Math.max(8, 2 * Runtime.getRuntime().availableProcessors());
        var callers = Executors.newFixedThreadPool(capacity);
        CountDownLatch admitted = new CountDownLatch(capacity);
        CountDownLatch attemptSource = new CountDownLatch(1);
        List<Future<Boolean>> renderers = new ArrayList<>();
        try (var editor = IncomingDocumentMutationLock.acquire(source.toFile(), directory.toFile())) {
            for (int i = 0; i < capacity; i++) {
                renderers.add(callers.submit(() -> BoundedPdfTask.runWithin(10, "lease-order-renderer", () -> {
                    admitted.countDown(); attemptSource.await();
                    try (var ignored = IncomingDocumentMutationLock.acquireForRender(source.toFile(), directory.toFile())) {
                        return false;
                    } catch (BoundedPdfTask.BusyException expected) { return true; }
                })));
            }
            assertThat(admitted.await(10, TimeUnit.SECONDS)).isTrue();
            attemptSource.countDown();
            // The editor keeps its lease while waiting for a bounded worker. All
            // renderer attempts must return busy, never park behind that lease.
            assertThat(BoundedPdfTask.runWithin(5, "lease-order-editor", () -> "prepared")).isEqualTo("prepared");
            for (Future<Boolean> renderer : renderers) assertThat(renderer.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(IncomingDocumentMutationLock.queuedWaiters(source.toFile())).isZero();
        } finally {
            attemptSource.countDown(); callers.shutdownNow();
            assertThat(callers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        try (var renderer = IncomingDocumentMutationLock.acquireForRender(source.toFile(), directory.toFile())) {
            assertThat(renderer.source()).isEqualTo(source.toFile());
        }
        assertThat(IncomingDocumentMutationLock.registeredSources()).isZero();
    }
}
