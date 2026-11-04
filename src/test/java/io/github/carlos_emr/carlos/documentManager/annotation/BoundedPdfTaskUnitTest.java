/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the properties that make {@link BoundedPdfTask} worth having.
 *
 * <p>Every one of these was a real defect at some point in this feature's history, and none of
 * them is visible by reading the class: an earlier version held the executor in
 * try-with-resources, so the deadline fired but {@code close()} then parked the caller until the
 * abandoned parse finished — the timeout was inert for exactly the case it existed to bound.
 * These tests measure behaviour rather than inspect structure for that reason.
 */
@Isolated("Exercises the process-wide PDF worker semaphore")
@Execution(ExecutionMode.SAME_THREAD)
@Tag("unit")
@Tag("document")
@DisplayName("BoundedPdfTask")
class BoundedPdfTaskUnitTest {

    @Test
    @DisplayName("should release the caller at the deadline when the task ignores interruption")
    void shouldReleaseCaller_whenTaskIgnoresInterruption() throws Exception {
        long startedAt = System.nanoTime();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            assertThatThrownBy(() -> BoundedPdfTask.runWithin(1, "test-deadline", () -> {
                entered.countDown();
                try {
                    // Deliberately ignores interruption, like a CPU-bound PDF parser.
                    while (release.getCount() > 0) {
                        Thread.onSpinWait();
                    }
                    return 1;
                } finally {
                    finished.countDown();
                }
            })).isInstanceOf(IOException.class).hasMessageContaining("too long");
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            assertThat(elapsedMs)
                    .as("the caller returns at its deadline while the worker remains occupied")
                    .isLessThan(2_500L);
        } finally {
            // Even a failed timing assertion must not leave a busy-spin worker behind.
            release.countDown();
            if (entered.getCount() == 0) {
                assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("should propagate an Error rather than reporting it as a document problem")
    void shouldPropagateError_whenTaskThrowsError() {
        // An OutOfMemoryError is not a bad PDF. Reporting it as one told the clinician to check
        // their document while the JVM was failing, and hid the real cause from the logs.
        assertThatThrownBy(() -> BoundedPdfTask.runWithin(5, "test-error", () -> {
            throw new OutOfMemoryError("synthetic");
        })).isInstanceOf(OutOfMemoryError.class).hasMessage("synthetic");
    }

    @Test
    @DisplayName("should preserve the task's own IOException and RuntimeException")
    void shouldPreserveCause_whenTaskFails() {
        assertThatThrownBy(() -> BoundedPdfTask.runWithin(5, "test-io", () -> {
            throw new IOException("unreadable page");
        })).isInstanceOf(IOException.class).hasMessage("unreadable page");

        assertThatThrownBy(() -> BoundedPdfTask.runWithin(5, "test-rt", () -> {
            throw new IllegalStateException("programming error");
        })).isInstanceOf(IllegalStateException.class).hasMessage("programming error");
    }

    @Test
    @DisplayName("should return the permit when the deadline beats the worker to the task body")
    void shouldReturnPermit_whenDeadlineBeatsWorkerToTaskBody() throws Exception {
        // A zero-second deadline expires while the FutureTask is still NEW, so FutureTask.run()
        // skips the callable and its finally -- the path that used to strand the permit for the
        // life of the JVM. Driving more of these than there are permits proves the handover works:
        // before the fix, the assertion below failed with "the server is busy".
        int permits = BoundedPdfTask.maxConcurrentParses();
        for (int i = 0; i < permits * 2; i++) {
            try {
                BoundedPdfTask.runWithin(0, "deadline-race-" + i, () -> {
                    Thread.sleep(2_000);
                    return 1;
                });
            } catch (IOException expectedTimeoutOrBusy) {
                // Either outcome is fine; the point is what happens to the permit afterwards.
            }
        }

        // Retry rather than assert once: the semaphore is global and the suite runs in parallel,
        // so a permit legitimately held by another test must not be read as a leak. A genuinely
        // stranded permit never comes back and this still fails.
        long giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        Integer result = null;
        while (result == null && System.nanoTime() < giveUp) {
            try {
                result = BoundedPdfTask.runWithin(10, "after-deadline-race", () -> 42);
            } catch (IOException busy) {
                Thread.sleep(50);
            }
        }
        assertThat(result)
                .as("permits must survive deadlines that fire before the worker starts")
                .isEqualTo(42);
    }

    @Test
    @DisplayName("should wait fairly across callers, bound admission, and recover after timeout and interruption")
    void shouldWaitFairlyAndBoundAdmission_whenManySessionsShareWorkers() throws Exception {
        int capacity = BoundedPdfTask.maxConcurrentParses();
        int waitingCapacity = 2 * capacity;
        CountDownLatch occupied = new CountDownLatch(capacity);
        java.util.concurrent.Semaphore releaseWorkers = new java.util.concurrent.Semaphore(0);
        java.util.concurrent.BlockingQueue<Integer> started = new java.util.concurrent.LinkedBlockingQueue<>();
        java.util.List<CountDownLatch> releaseWaiters = new java.util.ArrayList<>();
        java.util.List<java.util.concurrent.Future<Integer>> completed = new java.util.ArrayList<>();
        ExecutorService callers = Executors.newFixedThreadPool(capacity + waitingCapacity);
        Thread interrupted = null;
        try {
            for (int i = 0; i < capacity; i++) {
                completed.add(callers.submit(() -> BoundedPdfTask.runWithin(60, "occupied-session", () -> {
                    occupied.countDown();
                    releaseWorkers.acquire();
                    return 1;
                })));
            }
            assertThat(occupied.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> BoundedPdfTask.runWithin(5, "admission-timeout", () -> 1, 1))
                    .isInstanceOf(BoundedPdfTask.BusyException.class);

            CountDownLatch sawInterrupt = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean flagPreserved = new java.util.concurrent.atomic.AtomicBoolean();
            interrupted = new Thread(() -> {
                try {
                    BoundedPdfTask.runWithin(5, "interrupted-admission", () -> 1);
                } catch (IOException expected) {
                    flagPreserved.set(Thread.currentThread().isInterrupted());
                    sawInterrupt.countDown();
                }
            });
            interrupted.start();
            awaitQueued(1);
            interrupted.interrupt();
            assertThat(sawInterrupt.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(flagPreserved).isTrue();
            interrupted.join(5000);
            awaitQueued(0);

            // Filling every waiting slot after both exits proves neither path leaked one.
            for (int i = 0; i < waitingCapacity; i++) {
                int session = i;
                CountDownLatch finish = new CountDownLatch(1);
                releaseWaiters.add(finish);
                completed.add(callers.submit(() -> BoundedPdfTask.runWithin(60, "queued-session-" + session, () -> {
                    started.add(session);
                    finish.await();
                    return 1;
                })));
                awaitQueued(i + 1);
            }
            long before = System.nanoTime();
            assertThatThrownBy(() -> BoundedPdfTask.runWithin(5, "overflow-session", () -> 1))
                    .isInstanceOf(BoundedPdfTask.BusyException.class);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).isLessThan(1000);

            // Release one worker at a time: callers must enter in their queued order.
            for (int i = 0; i < waitingCapacity; i++) {
                if (i < capacity) releaseWorkers.release();
                else releaseWaiters.get(i - capacity).countDown();
                assertThat(started.poll(5, TimeUnit.SECONDS)).isEqualTo(i);
            }
        } finally {
            if (interrupted != null) interrupted.interrupt();
            releaseWorkers.release(capacity);
            releaseWaiters.forEach(CountDownLatch::countDown);
            callers.shutdown();
            assertThat(callers.awaitTermination(35, TimeUnit.SECONDS)).isTrue();
        }
        for (java.util.concurrent.Future<Integer> future : completed) {
            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo(1);
        }
        // The caller beyond workers + waiters completes when it retries, with no capacity loss.
        assertThat(BoundedPdfTask.runWithin(5, "overflow-session-retry", () -> 42)).isEqualTo(42);
    }

    @SuppressWarnings("java:S2925") // bounded polling observes the real semaphore wait queue
    private static void awaitQueued(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (BoundedPdfTask.queuedTaskCount() != expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(BoundedPdfTask.queuedTaskCount()).isEqualTo(expected);
    }
}
