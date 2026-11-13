/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.annotation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("document")
class BoundedPdfSynchronousAdmissionUnitTest {
    @Test
    void synchronousMutationsAndTimedReadsShareOneBudgetAndClosingTwiceCannotInflateIt() throws Exception {
        List<BoundedPdfTask.SynchronousAdmission> held = new ArrayList<>();
        try {
            for (int i = 0; i < BoundedPdfTask.maxConcurrentParses(); i++) held.add(BoundedPdfTask.acquireSynchronousAdmission(0));
            assertThatThrownBy(() -> BoundedPdfTask.acquireSynchronousAdmission(0)).isInstanceOf(BoundedPdfTask.BusyException.class);
            AtomicBoolean ran = new AtomicBoolean();
            assertThatThrownBy(() -> BoundedPdfTask.runWithin(1, "no-admission", () -> { ran.set(true); return null; }, 0))
                    .isInstanceOf(BoundedPdfTask.BusyException.class);
            assertThat(ran).isFalse();
            held.get(0).close(); held.get(0).close();
            held.add(BoundedPdfTask.acquireSynchronousAdmission(0));
            assertThatThrownBy(() -> BoundedPdfTask.acquireSynchronousAdmission(0)).isInstanceOf(BoundedPdfTask.BusyException.class);
        } finally {
            held.forEach(BoundedPdfTask.SynchronousAdmission::close);
        }
        assertThat(BoundedPdfTask.runWithin(1, "recovered", () -> "read succeeded", 0)).isEqualTo("read succeeded");
    }

    @Test
    void anotherSessionWaitsUntilActualSynchronousMutationReleasesItsPermit() throws Exception {
        List<BoundedPdfTask.SynchronousAdmission> held = new ArrayList<>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < BoundedPdfTask.maxConcurrentParses(); i++) held.add(BoundedPdfTask.acquireSynchronousAdmission(0));
            var waiter = executor.submit(() -> {
                try (var admitted = BoundedPdfTask.acquireSynchronousAdmission(5000)) { return "admitted"; }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (BoundedPdfTask.queuedTaskCount() == 0 && System.nanoTime() < deadline) {
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            assertThat(BoundedPdfTask.queuedTaskCount()).isEqualTo(1);
            assertThat(waiter.isDone()).isFalse();
            held.get(0).close();
            assertThat(waiter.get(5, TimeUnit.SECONDS)).isEqualTo("admitted");
        } finally {
            held.forEach(BoundedPdfTask.SynchronousAdmission::close);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
