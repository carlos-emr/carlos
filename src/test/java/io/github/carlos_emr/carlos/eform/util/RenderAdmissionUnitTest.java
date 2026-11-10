/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import java.time.Duration;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for this contract.
 * @since 2026-11-09
 */
@Tag("unit")
class RenderAdmissionUnitTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(RenderAdmission.Kind.class)
    void shouldRefuseAFullQueue_andServeEveryAdmittedWaiterInOrder(RenderAdmission.Kind kind) throws Exception {
        var slots = new Semaphore(0);
        var admission = new RenderAdmission(slots);
        int limit = kind == RenderAdmission.Kind.CHART ? 16 : 4;
        var workers = new java.util.ArrayList<Thread>();
        var results = new java.util.ArrayList<FutureTask<Boolean>>();
        var served = java.util.Collections.synchronizedList(new java.util.ArrayList<Integer>());
        try {
            for (int i = 0; i < limit; i++) {
                int position = i;
                var task = new FutureTask<>(() -> {
                    try (var lease = admission.acquire(kind, Duration.ofSeconds(30))) {
                        if (lease == null) return false;
                        served.add(position);
                        return true;
                    }
                });
                Thread worker = new Thread(task);
                results.add(task); workers.add(worker); worker.start();
                long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (worker.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.yield();
                assertThat(worker.getState()).isEqualTo(Thread.State.TIMED_WAITING);
            }
            // A long timeout proves queue saturation refuses promptly, rather than timing out.
            var overflow = new FutureTask<>(() -> admission.acquire(kind, Duration.ofSeconds(30)));
            Thread overflowWorker = new Thread(overflow);
            workers.add(overflowWorker); overflowWorker.start();
            assertThat(overflow.get(2, java.util.concurrent.TimeUnit.SECONDS)).isNull();
            slots.release();
            for (var result : results) assertThat(result.get(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(served).containsExactlyElementsOf(java.util.stream.IntStream.range(0, limit).boxed().toList());
            assertThat(slots.availablePermits()).isEqualTo(1);
        } finally {
            for (Thread worker : workers) worker.interrupt();
            for (Thread worker : workers) { worker.join(2000); assertThat(worker.isAlive()).isFalse(); }
        }
    }

    @Test
    void shouldBoundBothClasses_withOneGlobalBudget() throws Exception {
        var slots = new Semaphore(2);
        var admission = new RenderAdmission(slots);
        try (var first = admission.acquire(RenderAdmission.Kind.CHART, Duration.ZERO);
             var second = admission.acquire(RenderAdmission.Kind.PDF, Duration.ZERO)) {
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(admission.acquire(RenderAdmission.Kind.CHART, Duration.ofMillis(10))).isNull();
            assertThat(slots.availablePermits()).isZero();
        }
        assertThat(slots.availablePermits()).isEqualTo(2);
    }

    @Test
    void shouldPermitBorrowing_andReleaseOnlyOnce() throws Exception {
        var slots = new Semaphore(2);
        var admission = new RenderAdmission(slots);
        var first = admission.acquire(RenderAdmission.Kind.CHART, Duration.ZERO);
        var second = admission.acquire(RenderAdmission.Kind.CHART, Duration.ZERO);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        first.close(); first.close(); second.close();
        assertThat(slots.availablePermits()).isEqualTo(2);
    }

    @Test
    void shouldServeWaitingPdf_whenABorrowedChartSlotIsReleased() throws Exception {
        var admission = new RenderAdmission(new Semaphore(2));
        try (var first = admission.acquire(RenderAdmission.Kind.CHART, Duration.ZERO);
             var borrowed = admission.acquire(RenderAdmission.Kind.CHART, Duration.ZERO)) {
            var task = new FutureTask<>(() -> admission.acquire(RenderAdmission.Kind.PDF, Duration.ofSeconds(5)));
            Thread worker = new Thread(task);
            worker.start();
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (worker.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.yield();
                assertThat(worker.getState()).isEqualTo(Thread.State.TIMED_WAITING);
                borrowed.close();
                try (var pdf = task.get(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    assertThat(pdf).isNotNull();
                    assertThat(admission.acquire(RenderAdmission.Kind.CHART, Duration.ZERO)).isNull();
                }
            } finally {
                worker.interrupt(); worker.join(2000);
                assertThat(worker.isAlive()).isFalse();
            }
        }
    }

    @Test
    void shouldRefusePreInterruptedAdmission_withoutTakingAnAvailableSlot() {
        var slots = new Semaphore(2);
        var admission = new RenderAdmission(slots);
        Thread.currentThread().interrupt();
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> admission.acquire(RenderAdmission.Kind.CHART, Duration.ZERO))
                    .isInstanceOf(InterruptedException.class);
            assertThat(slots.availablePermits()).isEqualTo(2);
        } finally { Thread.interrupted(); }
    }

    @Test
    void shouldRemoveInterruptedWaiter_andPermitFollowingWork() throws Exception {
        var slots = new Semaphore(0);
        var admission = new RenderAdmission(slots);
        var outcome = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try (var lease = admission.acquire(RenderAdmission.Kind.PDF, Duration.ofSeconds(5))) {
                outcome.set(new AssertionError("Interrupted waiter acquired a slot"));
            } catch (Throwable error) { outcome.set(error); }
        });
        worker.start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (worker.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.yield();
            assertThat(worker.getState()).isEqualTo(Thread.State.TIMED_WAITING);
            worker.interrupt();worker.join(2000);
            assertThat(worker.isAlive()).isFalse();assertThat(outcome.get()).isInstanceOf(InterruptedException.class);
            slots.release();
            try (var next = admission.acquire(RenderAdmission.Kind.PDF, Duration.ZERO)) { assertThat(next).isNotNull(); }
            assertThat(slots.availablePermits()).isEqualTo(1);
        } finally { worker.interrupt();worker.join(2000); }
    }
}
